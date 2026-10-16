package com.hjw.qbremote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.hjw.qbremote.data.AppLanguage
import com.hjw.qbremote.data.AppTheme
import com.hjw.qbremote.data.BackendConnectionError
import com.hjw.qbremote.data.CachedDashboardServerSnapshot
import com.hjw.qbremote.data.ConnectionSettings
import com.hjw.qbremote.data.ConnectionStore
import com.hjw.qbremote.data.CachedDailyTagUploadStat
import com.hjw.qbremote.data.GlobalSpeedLimits
import com.hjw.qbremote.data.ServerBackendType
import com.hjw.qbremote.data.ServerProfile
import com.hjw.qbremote.data.TorrentRepository
import com.hjw.qbremote.data.UiPreferencePatch
import com.hjw.qbremote.data.model.AddTorrentFile
import com.hjw.qbremote.data.model.AddTorrentRequest
import com.hjw.qbremote.data.model.DailyCountryUploadStats
import com.hjw.qbremote.data.model.TorrentTracker
import com.hjw.qbremote.data.model.TransferInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(
    private val connectionStore: ConnectionStore,
    private val repository: TorrentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    internal val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val torrentListFilterState = MutableStateFlow(TorrentListFilterState())
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val torrentListDisplayState: StateFlow<TorrentListDisplayState> = combine(
        _uiState.map { it.torrents }.distinctUntilChanged(),
        torrentListFilterState,
    ) { torrents, filterState ->
        torrents to filterState
    }.mapLatest { (torrents, filterState) ->
        withContext(Dispatchers.Default) {
            buildTorrentListDisplayState(
                torrents = torrents,
                filterState = filterState,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TorrentListDisplayState(),
    )
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val serverDashboardDisplayState: StateFlow<ServerDashboardDisplayState> = _uiState
        .map(::buildServerDashboardDisplayInput)
        .distinctUntilChanged()
        .mapLatest { state ->
            withContext(Dispatchers.Default) {
                buildServerDashboardDisplayState(state)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ServerDashboardDisplayState(),
        )

    private val backgroundJobManager = BackgroundJobManager(
        scope = viewModelScope,
        getState = { _uiState.value },
        onAutoRefresh = { refresh() },
        onHomeChartRefresh = { refreshHomeDashboardChartTransferInfo() },
        onHourlyBoundaryRefresh = {
            nextServerRefreshAt.keys.toList().forEach { id -> nextServerRefreshAt[id] = 0L }
            refresh()
        },
        awaitForeground = { awaitAppForeground() },
    )
    private var dashboardCacheHydrationJob: Job? = null
    private var dashboardAggregationJob: Job? = null
    private var serverSchedulerJob: Job? = null
    private var autoConnectAttempted = false
    private var isRefreshInProgress = false
    // A refresh requested while one is running; run it right after instead of dropping it.
    private var refreshQueued = false
    private val isAppForeground = MutableStateFlow(true)
    private var hydratedDashboardScopeKey: String? = null
    private var initialSettingsLoaded = false
    // Number of connectionStore.saveUiPreferences() calls still in flight. While > 0
    // the settingsFlow collector must not overwrite uiState.settings: DataStore emits
    // on every unrelated edit (speed samples, snapshot caches), and an emission
    // computed before the pending write lands would silently revert the optimistic
    // update (classic symptom: a theme switch that randomly does not stick).
    private var pendingUiPreferenceWrites = 0
    private var initialServerProfilesLoaded = false
    private var initialDashboardCacheHydrated = false
    private var initialDashboardSnapshotsHydrated = false
    private var activeProfileRequestVersion = 0L

    private val realtimeSpeedTracker = RealtimeSpeedTracker(connectionStore)
    private val completionNotificationCoordinator = CompletionNotificationCoordinator(
        connectionStore = connectionStore,
        scope = viewModelScope,
    )

    // One refresh mutex per profile: a slow or unreachable server must not block the
    // other servers' refreshes (a full connect failure chain can take >10s).
    // Main-thread confined (viewModelScope), so a plain map is fine.
    private val serverRefreshMutexes = mutableMapOf<String, Mutex>()
    private val inFlightServerRefreshes = mutableSetOf<String>()

    private fun serverRefreshMutexFor(profileId: String): Mutex {
        return serverRefreshMutexes.getOrPut(profileId) { Mutex() }
    }
    private val cachedProfileSettings = mutableMapOf<String, ConnectionSettings>()
    private val nextServerRefreshAt = mutableMapOf<String, Long>()
    private val serverRefreshFailureStreaks = mutableMapOf<String, Int>()
    private val dashboardPersistence = DashboardPersistence(connectionStore)
    private val dashboardPreferences = ServerDashboardPreferencesController(
        uiState = _uiState,
        scope = viewModelScope,
        connectionStore = connectionStore,
        resolveProfileSettings = { profileId -> resolveProfileSettings(profileId) },
    )
    private val globalSpeedLimits = GlobalSpeedLimitController(
        uiState = _uiState,
        scope = viewModelScope,
        repository = repository,
        onLimitModeChanged = { refreshSoon() },
    )
    private val torrentActions = TorrentActionsController(
        uiState = _uiState,
        scope = viewModelScope,
        repository = repository,
        currentActiveProfileRequestVersion = { currentActiveProfileRequestVersion() },
        isActiveProfileRequestValid = { profileId, version -> isActiveProfileRequestValid(profileId, version) },
        isDetailRequestValid = { profileId, hash, version -> isDetailRequestValid(profileId, hash, version) },
        refresh = { refresh() },
        scheduleImmediateServerRefresh = { profileId -> scheduleImmediateServerRefresh(profileId) },
    )
    private var lastCheckedCustomBackgroundPath: String? = null

    init {
        viewModelScope.launch {
            connectionStore.migrateLegacyPasswordIfNeeded()
            connectionStore.cleanupLegacyGlobalChartSettingsIfNeeded()
            launch {
                completionNotificationCoordinator.initialize()
                connectionStore.settingsFlow
                    .map { settings -> settings.completionNotificationsEnabled }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        completionNotificationCoordinator.setEnabled(enabled)
                    }
            }
            launch {
                connectionStore.settingsFlow.collect { settings ->
                    if (pendingUiPreferenceWrites == 0) {
                        _uiState.update { current ->
                            current.copy(
                                settings = settings,
                                activeCapabilities = repository.capabilitiesFor(settings),
                            )
                        }
                        hydrateDashboardCacheForCurrentScope()
                        refreshCustomBackgroundAvailability(settings)
                    }
                    markInitialSettingsLoaded()
                }
            }
            launch {
                connectionStore.serverProfilesFlow.collect { profilesState ->
                    val previousActiveProfileId = _uiState.value.activeServerProfileId
                    if (profilesState.activeProfileId != previousActiveProfileId) {
                        bumpActiveProfileRequestVersion()
                    }
                    pruneCachedProfileSettingsInMemory(profilesState.profiles)
                    repository.selectProfile(profilesState.activeProfileId)
                    val dashboardPreferences = connectionStore.loadServerDashboardPreferences()
                    val availableProfileIds = profilesState.profiles.map { profile -> profile.id }
                    val availableProfileIdSet = availableProfileIds.toHashSet()
                    _uiState.update { current ->
                        current.copy(
                            serverProfiles = profilesState.profiles,
                            serverDashboardPreferences = filterDashboardPreferencesForProfiles(
                                preferences = dashboardPreferences,
                                profiles = profilesState.profiles,
                            ),
                            activeServerProfileId = profilesState.activeProfileId,
                            selectedDashboardProfileId = resolvePreferredProfileId(
                                availableIds = availableProfileIds,
                                primaryCandidate = current.selectedDashboardProfileId,
                                secondaryCandidate = profilesState.activeProfileId,
                            ),
                            pendingBackendRepair = current.pendingBackendRepair
                                ?.takeIf { pending -> pending.profileId in availableProfileIdSet },
                        )
                    }
                    seedCachedSettingsForProfile(profilesState.activeProfileId)
                    hydrateDashboardCacheForCurrentScope()
                    hydrateDashboardServerSnapshots()
                    synchronizeServerScheduler()
                    startHomeChartRefresh()
                    autoConnectIfNeeded(_uiState.value.settings)
                    markInitialServerProfilesLoaded()
                }
            }
        }
    }

    internal fun updateTorrentSearchQuery(query: String) {
        torrentListFilterState.update { current ->
            if (current.query == query) current else current.copy(query = query)
        }
    }

    internal fun updateTorrentListSortOption(sortOption: TorrentListSortOption) {
        torrentListFilterState.update { current ->
            if (current.sortOption == sortOption) current else current.copy(sortOption = sortOption)
        }
    }

    internal fun updateTorrentListSortDirection(descending: Boolean) {
        torrentListFilterState.update { current ->
            if (current.descending == descending) current else current.copy(descending = descending)
        }
    }

    internal fun updateTorrentListStateFilter(stateFilter: TorrentStateFilter) {
        torrentListFilterState.update { current ->
            if (current.stateFilter == stateFilter) current else current.copy(stateFilter = stateFilter)
        }
    }

    internal fun updateTorrentListCategoryFilter(category: String) {
        torrentListFilterState.update { current ->
            val next = if (current.categoryFilter == category) "" else category
            if (current.categoryFilter == next) current else current.copy(categoryFilter = next)
        }
    }

    internal fun updateTorrentListTagFilter(tag: String) {
        torrentListFilterState.update { current ->
            val next = if (current.tagFilter == tag) "" else tag
            if (current.tagFilter == next) current else current.copy(tagFilter = next)
        }
    }

    /** Drops state/category/tag filters; the search query and sort order stay. */
    internal fun resetTorrentListFilters() {
        torrentListFilterState.update { current ->
            clearTorrentListFilters(current).takeIf { it != current } ?: current
        }
    }

    private fun markInitialSettingsLoaded() {
        if (!initialSettingsLoaded) {
            initialSettingsLoaded = true
            maybeMarkStartupRestoreComplete()
        }
    }

    private fun markInitialServerProfilesLoaded() {
        if (!initialServerProfilesLoaded) {
            initialServerProfilesLoaded = true
            maybeMarkStartupRestoreComplete()
        }
    }

    private fun markInitialDashboardCacheHydrated() {
        if (!initialDashboardCacheHydrated) {
            initialDashboardCacheHydrated = true
            maybeMarkStartupRestoreComplete()
        }
    }

    private fun markInitialDashboardSnapshotsHydrated() {
        if (!initialDashboardSnapshotsHydrated) {
            initialDashboardSnapshotsHydrated = true
            maybeMarkStartupRestoreComplete()
        }
    }

    private fun maybeMarkStartupRestoreComplete() {
        if (
            _uiState.value.startupRestoreComplete ||
            !initialSettingsLoaded ||
            !initialServerProfilesLoaded ||
            !initialDashboardCacheHydrated ||
            !initialDashboardSnapshotsHydrated
        ) {
            return
        }
        _uiState.update { current ->
            if (current.startupRestoreComplete) current else current.copy(startupRestoreComplete = true)
        }
    }

    fun updateHost(value: String) = updateSettings { current ->
        val parsed = parseHostInputHints(value)
        current.copy(
            host = value,
            port = parsed?.port ?: current.port,
            useHttps = parsed?.useHttps ?: current.useHttps,
        )
    }
    fun updatePort(value: String) = updateSettings { it.copy(port = value.toIntOrNull() ?: 0) }
    fun updateUseHttps(value: Boolean) = updateSettings { it.copy(useHttps = value) }
    fun updateUsername(value: String) = updateSettings { it.copy(username = value) }
    fun updatePassword(value: String) = updateSettings { it.copy(password = value) }
    fun updateServerBackendType(value: ServerBackendType) = updateSettings { it.copy(serverBackendType = value) }
    fun updateRefreshSeconds(value: String) {
        val sec = value.toIntOrNull()?.coerceIn(5, 120) ?: 5
        updateSettings { it.copy(refreshSeconds = sec) }
    }

    fun updateAppLanguage(value: AppLanguage) = persistUiPreferences(
        patch = UiPreferencePatch(appLanguage = value),
        update = { it.copy(appLanguage = value) },
    )

    fun updateAppTheme(value: AppTheme) = persistUiPreferences(
        patch = UiPreferencePatch(appTheme = value),
        update = { it.copy(appTheme = value) },
    )

    fun applyCustomThemeBackground(
        imagePath: String,
        toneIsLight: Boolean,
    ) = persistUiPreferences(
        patch = UiPreferencePatch(
            appTheme = AppTheme.CUSTOM,
            customBackgroundImagePath = imagePath,
            customBackgroundToneIsLight = toneIsLight,
        ),
        update = {
            it.copy(
                appTheme = AppTheme.CUSTOM,
                customBackgroundImagePath = imagePath,
                customBackgroundToneIsLight = toneIsLight,
            )
        },
        onPersisted = {
            withContext(Dispatchers.IO) {
                cleanupStaleCustomBackgrounds(connectionStore.context, imagePath)
            }
        },
    )

    fun updateDeleteFilesDefault(value: Boolean) = persistUiPreferences(
        patch = UiPreferencePatch(deleteFilesDefault = value),
        update = { it.copy(deleteFilesDefault = value) },
    )

    fun updateDeleteFilesWhenNoSeeders(value: Boolean) = persistUiPreferences(
        patch = UiPreferencePatch(deleteFilesWhenNoSeeders = value),
        update = { it.copy(deleteFilesWhenNoSeeders = value) },
    )

    fun updateCompletionNotificationsEnabled(value: Boolean) = persistUiPreferences(
        patch = UiPreferencePatch(completionNotificationsEnabled = value),
        update = { it.copy(completionNotificationsEnabled = value) },
    )

    fun openTorrentFromNotification(profileId: String, torrentHash: String) {
        val target = completionNotificationCoordinator.createNavigationTarget(
            profileId = profileId,
            torrentHash = torrentHash,
        ) ?: return
        _uiState.update { current ->
            current.copy(notificationNavigationTarget = target)
        }
    }

    fun clearNotificationNavigationTarget() {
        _uiState.update { current ->
            current.copy(notificationNavigationTarget = null)
        }
    }

    fun dismissHomeTorrentEntryHint() = persistUiPreferences(
        patch = UiPreferencePatch(homeTorrentEntryHintDismissed = true),
        update = { it.copy(homeTorrentEntryHintDismissed = true) },
    )

    fun markAltSpeedHintSeen() {
        if (_uiState.value.settings.hasSeenAltSpeedHint) return
        persistUiPreferences(
            patch = UiPreferencePatch(hasSeenAltSpeedHint = true),
            update = { it.copy(hasSeenAltSpeedHint = true) },
        )
    }

    fun updateRefreshScene(scene: RefreshScene) {
        _uiState.update { current ->
            if (current.refreshScene == scene) current else current.copy(refreshScene = scene)
        }
    }

    internal fun setAppForeground(foreground: Boolean) {
        val wasForeground = isAppForeground.value
        isAppForeground.value = foreground
        if (!foreground) {
            viewModelScope.launch {
                realtimeSpeedTracker.withLock { realtimeSpeedTracker.flushLocked() }
                dashboardPersistence.flushThrottledPersistence()
            }
        } else if (!wasForeground) {
            nextServerRefreshAt.keys.forEach { id -> nextServerRefreshAt[id] = 0L }
        }
    }

    private suspend fun awaitAppForeground() {
        isAppForeground.first { it }
    }

    fun setDashboardReorderHold(profileId: String?) {
        val normalizedProfileId = profileId?.trim().orEmpty().ifBlank { null }
        var releaseResult = DashboardReorderHoldReleaseResult()
        _uiState.update { current ->
            if (normalizedProfileId != null) {
                if (
                    current.dashboardRefreshHoldProfileId == normalizedProfileId &&
                    current.dashboardRefreshHoldAllProfiles
                ) {
                    current
                } else {
                    current.copy(
                        dashboardRefreshHoldProfileId = normalizedProfileId,
                        dashboardRefreshHoldAllProfiles = true,
                    )
                }
            } else {
                releaseResult = releaseDashboardReorderHold(current)
                if (releaseResult.profileIdToRefreshImmediately == null) {
                    if (current.dashboardRefreshHoldAllProfiles) {
                        current.copy(dashboardRefreshHoldAllProfiles = false)
                    } else {
                        current
                    }
                } else {
                    current.copy(
                        dashboardRefreshHoldProfileId = releaseResult.nextHeldProfileId,
                        dashboardRefreshHoldAllProfiles = false,
                    )
                }
            }
        }
        val releasedProfileId = releaseResult.profileIdToRefreshImmediately ?: return
        scheduleImmediateServerRefresh(releasedProfileId)
        viewModelScope.launch {
            refreshServerSnapshotNow(
                profileId = releasedProfileId,
                showSelectedError = false,
            )
        }
    }

    fun setServerStackReorderHold(active: Boolean) {
        _uiState.update { current ->
            if (current.dashboardRefreshHoldAllProfiles == active) {
                current
            } else {
                current.copy(dashboardRefreshHoldAllProfiles = active)
            }
        }
    }

    fun prepareServerDashboardTransition(profileId: String) {
        val normalizedProfileId = profileId.trim()
        if (normalizedProfileId.isBlank()) return
        _uiState.update { current ->
            prepareServerDashboardTransitionState(current, normalizedProfileId)
        }
    }

    fun connect() {
        viewModelScope.launch {
            runCatching {
                val currentState = _uiState.value
                val targetProfileId = when {
                    !currentState.activeServerProfileId.isNullOrBlank() -> currentState.activeServerProfileId
                    currentState.settings.host.trim().isNotBlank() && currentState.settings.username.trim().isNotBlank() -> {
                        connectionStore.save(_uiState.value.settings)
                        connectionStore.serverProfilesFlow.first().activeProfileId
                    }

                    else -> null
                } ?: error("请先添加服务器。")

                val targetSettings = connectionStore.switchToServerProfile(targetProfileId)
                repository.selectProfile(targetProfileId)
                bumpActiveProfileRequestVersion()
                val capabilities = repository.capabilitiesFor(targetSettings)
                _uiState.update { current ->
                    prepareConnectingProfileState(
                        current = current,
                        settings = targetSettings,
                        profileId = targetProfileId,
                        capabilities = capabilities,
                    )
                }
                hydrateDashboardCacheForCurrentScope(force = true)
                synchronizeServerScheduler()
                scheduleImmediateServerRefresh(targetProfileId)
                refreshServerSnapshotNow(
                    profileId = targetProfileId,
                    showSelectedError = true,
                    forceSettings = targetSettings,
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connected = false,
                        errorMessage = UiMessage.Text(error.message ?: "连接服务器失败"),
                    )
                }
            }
        }
    }

    /**
     * "Test connection" in the server form. When editing with the password left
     * blank ("keep current"), the stored password is used.
     */
    fun testServerConnection(
        editingProfileId: String?,
        backendType: ServerBackendType,
        host: String,
        port: String,
        useHttps: Boolean,
        username: String,
        password: String,
        onResult: (success: Boolean, message: String) -> Unit,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                val storedPassword = if (password.isEmpty() && !editingProfileId.isNullOrBlank()) {
                    connectionStore.loadSettingsForProfile(editingProfileId)
                } else {
                    null
                }
                val settings = buildProfileSettingsDraft(
                    baseSettings = _uiState.value.settings,
                    backendType = backendType,
                    host = host,
                    port = port,
                    useHttps = useHttps,
                    username = username,
                    password = storedPassword?.password ?: password,
                    refreshSeconds = "5",
                ).copy(passwordUnreadable = storedPassword?.passwordUnreadable == true)
                repository.testConnection(settings).getOrThrow()
            }
            result.onSuccess { version -> onResult(true, version) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    onResult(false, userFacingConnectionMessage(error))
                }
        }
    }

    fun addServerProfile(
        name: String,
        backendType: ServerBackendType,
        host: String,
        port: String,
        useHttps: Boolean,
        username: String,
        password: String,
        refreshSeconds: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingServerProfile = true) }
            val result = runCatching {
                val nextSettings = buildProfileSettingsDraft(
                    baseSettings = _uiState.value.settings,
                    backendType = backendType,
                    host = host,
                    port = port,
                    useHttps = useHttps,
                    username = username,
                    password = password,
                    refreshSeconds = refreshSeconds,
                )

                val profile = connectionStore.addServerProfile(name = name, settings = nextSettings)
                val switched = connectionStore.switchToServerProfile(profile.id)
                repository.selectProfile(profile.id)
                bumpActiveProfileRequestVersion()
                val capabilities = repository.capabilitiesFor(switched)
                _uiState.update { current ->
                    prepareConnectingProfileState(
                        current = current,
                        settings = switched,
                        profileId = profile.id,
                        capabilities = capabilities,
                    )
                }
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = UiMessage.Text(error.message ?: "添加服务器失败"))
                }
            }
            _uiState.update { it.copy(isSavingServerProfile = false) }
            if (result.isSuccess) {
                hydrateDashboardCacheForCurrentScope(force = true)
                synchronizeServerScheduler()
                val profileId = _uiState.value.activeServerProfileId ?: return@launch
                scheduleImmediateServerRefresh(profileId)
                refreshServerSnapshotNow(profileId = profileId, showSelectedError = true)
            }
        }
    }

    fun updateServerProfile(
        profileId: String,
        name: String,
        backendType: ServerBackendType,
        host: String,
        port: String,
        useHttps: Boolean,
        username: String,
        password: String,
        refreshSeconds: String,
    ) {
        if (profileId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingServerProfile = true) }
            val wasActive = _uiState.value.activeServerProfileId == profileId
            val result = runCatching {
                val existingSettings = connectionStore.loadSettingsForProfile(profileId)
                    ?: error("服务器配置不存在")
                val nextSettings = buildProfileSettingsDraft(
                    baseSettings = existingSettings,
                    backendType = backendType,
                    host = host,
                    port = port,
                    useHttps = useHttps,
                    username = username,
                    password = password.ifBlank { existingSettings.password },
                    refreshSeconds = refreshSeconds,
                )
                connectionStore.updateServerProfile(
                    profileId = profileId,
                    name = name,
                    settings = nextSettings,
                    passwordOverride = password.takeIf { it.isNotBlank() },
                )
                repository.removeProfile(profileId)
                scheduleImmediateServerRefresh(profileId)
                if (wasActive) {
                    val switched = connectionStore.switchToServerProfile(profileId)
                    repository.selectProfile(profileId)
                    bumpActiveProfileRequestVersion()
                    val capabilities = repository.capabilitiesFor(switched)
                    _uiState.update { current ->
                        prepareConnectingProfileState(
                            current = current,
                            settings = switched,
                            profileId = profileId,
                            capabilities = capabilities,
                        )
                    }
                }
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = UiMessage.Text(error.message ?: "更新服务器失败"))
                }
            }
            _uiState.update { it.copy(isSavingServerProfile = false) }
            if (result.isSuccess) {
                hydrateDashboardServerSnapshots()
                synchronizeServerScheduler()
                refreshServerSnapshotNow(profileId = profileId, showSelectedError = wasActive)
            }
        }
    }

    fun deleteServerProfile(profileId: String) {
        if (profileId.isBlank()) return
        viewModelScope.launch {
            val result = runCatching {
                connectionStore.deleteServerProfile(profileId)
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = UiMessage.Text(error.message ?: "删除服务器失败"))
                }
            }
            result.getOrNull()?.let { resultValue ->
                repository.removeProfile(profileId)
                nextServerRefreshAt.remove(profileId)
                serverRefreshFailureStreaks.remove(profileId)
                hydrateDashboardServerSnapshots()

                val nextProfileId = resultValue.activeProfileId
                if (nextProfileId.isNullOrBlank()) {
                    serverSchedulerJob?.cancel()
                    serverSchedulerJob = null
                    repository.clearAllSessions()
                    bumpActiveProfileRequestVersion()
                    _uiState.update { current ->
                        current.copy(
                            activeServerProfileId = null,
                            selectedDashboardProfileId = null,
                            dashboardSessionToken = current.dashboardSessionToken + 1L,
                            connected = false,
                            isConnecting = false,
                            serverVersion = "-",
                            transferInfo = TransferInfo(),
                            torrents = emptyList(),
                            dailyTagUploadDate = "",
                            dailyTagUploadStats = emptyList(),
                            dailyCountryUploadDate = "",
                            dailyCountryUploadStats = emptyList(),
                            dashboardServerSnapshots = emptyList(),
                            dashboardAggregate = DashboardAggregateState(),
                            categoryOptions = emptyList(),
                            tagOptions = emptyList(),
                            pendingBackendRepair = null,
                            detailHash = "",
                            detailLoading = false,
                            detailProperties = null,
                            detailFiles = emptyList(),
                            detailTrackers = emptyList(),
                            detailPeers = emptyList(),
                            pendingActionKeys = emptySet(),
                        )
                    }
                } else {
                    repository.selectProfile(nextProfileId)
                    val nextSettings = resultValue.settings
                        ?: connectionStore.loadSettingsForProfile(nextProfileId)
                        ?: _uiState.value.settings
                    bumpActiveProfileRequestVersion()
                    val capabilities = repository.capabilitiesFor(nextSettings)
                    _uiState.update { current ->
                        prepareConnectingProfileState(
                            current = current,
                            settings = nextSettings,
                            profileId = nextProfileId,
                            capabilities = capabilities,
                        )
                    }
                    hydrateDashboardCacheForCurrentScope(force = true)
                    synchronizeServerScheduler()
                    scheduleImmediateServerRefresh(nextProfileId)
                    refreshServerSnapshotNow(profileId = nextProfileId, showSelectedError = false)
                }
            }
        }
    }

    fun switchServerProfile(profileId: String) {
        val normalizedProfileId = profileId.trim()
        if (normalizedProfileId.isBlank()) return
        prepareServerDashboardTransition(normalizedProfileId)
        viewModelScope.launch {
            val result = runCatching {
                val switched = connectionStore.switchToServerProfile(normalizedProfileId)
                repository.selectProfile(normalizedProfileId)
                bumpActiveProfileRequestVersion()
                _uiState.update { current ->
                    current.copy(
                        settings = switched,
                        activeServerProfileId = normalizedProfileId,
                        selectedDashboardProfileId = normalizedProfileId,
                        activeCapabilities = repository.capabilitiesFor(switched),
                        isConnecting = true,
                        pendingBackendRepair = null,
                    )
                }
                updateCachedProfileSettings(normalizedProfileId, switched)
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = UiMessage.Text(error.message ?: "切换服务器失败"))
                }
            }
            if (result.isSuccess) {
                hydrateDashboardCacheForCurrentScope(force = true)
                synchronizeServerScheduler()
                scheduleImmediateServerRefresh(normalizedProfileId)
                refreshServerSnapshotNow(profileId = normalizedProfileId, showSelectedError = true)
            }
        }
    }

    fun reorderServerProfiles(profileIds: List<String>) {
        val normalizedIds = profileIds.map { it.trim() }.filter { it.isNotBlank() }
        if (normalizedIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                connectionStore.reorderServerProfiles(normalizedIds)
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(errorMessage = UiMessage.Text(error.message ?: "调整服务器顺序失败"))
                }
            }
        }
    }

    // ---- Per-server dashboard cards (ServerDashboardPreferencesController) ----
    fun updateServerDashboardCardVisibility(
        profileId: String,
        card: DashboardChartCard,
        visible: Boolean,
        onComplete: (Boolean) -> Unit = {},
    ) = dashboardPreferences.setCardVisibility(profileId, card, visible, onComplete)

    fun updateServerDashboardCardsVisibility(
        profileId: String,
        cards: List<DashboardChartCard>,
        visible: Boolean,
        onComplete: (Boolean) -> Unit = {},
    ) = dashboardPreferences.setCardsVisibility(profileId, cards, visible, onComplete = onComplete)

    fun updateServerDashboardCardOrder(
        profileId: String,
        order: List<DashboardChartCard>,
        onComplete: (Boolean) -> Unit = {},
    ) = dashboardPreferences.setCardOrder(profileId, order, onComplete)

    fun resetServerDashboardPreferences(
        profileId: String,
        onComplete: (Boolean) -> Unit = {},
    ) = dashboardPreferences.reset(profileId, onComplete)

    fun markServerStackReorderHintSeen() = persistUiPreferences(
        patch = UiPreferencePatch(hasSeenServerStackReorderHint = true),
        update = { current -> current.copy(hasSeenServerStackReorderHint = true) },
    )

    fun markServerDashboardSwipeHintSeen() = persistUiPreferences(
        patch = UiPreferencePatch(hasSeenServerDashboardSwipeHint = true),
        update = { current -> current.copy(hasSeenServerDashboardSwipeHint = true) },
    )

    fun markServerDashboardCardHintSeen() = persistUiPreferences(
        patch = UiPreferencePatch(hasSeenServerDashboardCardHint = true),
        update = { current -> current.copy(hasSeenServerDashboardCardHint = true) },
    )

    fun exportTorrentFile(
        hash: String,
        onSuccess: (ByteArray) -> Unit,
    ) {
        val profileId = _uiState.value.activeServerProfileId?.trim().orEmpty()
        val normalizedHash = hash.trim()
        if (profileId.isBlank() || normalizedHash.isBlank()) return
        val requestVersion = currentActiveProfileRequestVersion()
        viewModelScope.launch {
            repository.exportTorrentFile(profileId, normalizedHash)
                .onSuccess { bytes -> onSuccess(bytes) }
                .onFailure { error ->
                    if (isActiveProfileRequestValid(profileId, requestVersion)) {
                        _uiState.update {
                            it.copy(errorMessage = UiMessage.Text(error.message ?: "导出种子失败"))
                        }
                    }
                }
        }
    }

    private fun autoConnectIfNeeded(settings: ConnectionSettings) {
        if (autoConnectAttempted) return
        if (settings.host.isBlank() || settings.username.isBlank()) return
        val state = _uiState.value
        if (state.serverProfiles.isNotEmpty() && state.activeServerProfileId.isNullOrBlank()) return
        autoConnectAttempted = true
        connectInternal(persistSettings = false, showErrorOnFailure = false)
    }

    private fun bumpActiveProfileRequestVersion() {
        activeProfileRequestVersion += 1
    }

    private fun currentActiveProfileRequestVersion(): Long = activeProfileRequestVersion

    private fun isActiveProfileRequestValid(
        profileId: String,
        requestVersion: Long,
    ): Boolean {
        return shouldApplyActiveProfileAsyncResult(
            requestedProfileId = profileId,
            requestVersion = requestVersion,
            activeProfileId = _uiState.value.activeServerProfileId,
            activeRequestVersion = activeProfileRequestVersion,
        )
    }

    private fun isDetailRequestValid(
        profileId: String,
        hash: String,
        requestVersion: Long,
    ): Boolean {
        val normalizedHash = hash.trim()
        return normalizedHash.isNotBlank() &&
            isActiveProfileRequestValid(profileId, requestVersion) &&
            _uiState.value.detailHash == normalizedHash
    }

    private fun connectInternal(
        persistSettings: Boolean,
        showErrorOnFailure: Boolean,
    ) {
        if (_uiState.value.isConnecting) return
        viewModelScope.launch {
            resetDailyCountryUploadTrackingState()
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
            val settings = _uiState.value.settings
            if (persistSettings) {
                connectionStore.save(settings)
            }
            _uiState.value.activeServerProfileId?.let { activeProfileId ->
                updateCachedProfileSettings(activeProfileId, settings)
            }
            hydrateDashboardCacheForCurrentScope()

            repository.connect(settings, force = true)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connected = true,
                            activeCapabilities = repository.activeCapabilities(),
                        )
                    }
                    refreshServerVersion()
                    _uiState.value.activeServerProfileId?.let(::scheduleImmediateServerRefresh)
                    refresh()
                    loadGlobalSelectionOptions()
                    startAutoRefresh()
                    startHomeChartRefresh()
                    startHourlyBoundaryRefresh()
                    // Country stats are sampled by refreshServerSnapshotNow on each
                    // server refresh; there is no separate high-frequency tracker.
                    if (!repository.activeCapabilities().supportsCountryDistribution) {
                        _uiState.update {
                            if (it.dailyCountryUploadStats.isEmpty()) it
                            else it.copy(
                                dailyCountryUploadDate = "",
                                dailyCountryUploadStats = emptyList(),
                            )
                        }
                    }
                    refreshDashboardServerSnapshotsAsync()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connected = false,
                            errorMessage = if (showErrorOnFailure) {
                                UiMessage.Text(error.message ?: "Connection failed.")
                            } else {
                                null
                            }
                        )
                    }
                    backgroundJobManager.stopAll()
                }
        }
    }

    /** Refresh now, or right after the refresh that is already running. */
    private fun refreshSoon() {
        if (isRefreshInProgress) refreshQueued = true else refresh()
    }

    fun refresh(manual: Boolean = false) {
        if (isRefreshInProgress) return
        isRefreshInProgress = true
        viewModelScope.launch {
            try {
                if (manual) {
                    _uiState.update {
                        it.copy(
                            isManualRefreshing = true,
                            errorMessage = null,
                        )
                    }
                }

                val state = _uiState.value
                val refreshAllServers = state.refreshScene == RefreshScene.DASHBOARD &&
                    state.serverProfiles.size > 1

                if (refreshAllServers) {
                    state.serverProfiles.forEach { profile ->
                        if (
                            shouldSkipRefreshForDashboardReorderHold(
                                heldProfileId = state.dashboardRefreshHoldProfileId,
                                holdAllProfiles = state.dashboardRefreshHoldAllProfiles,
                                profileId = profile.id,
                            )
                        ) {
                            return@forEach
                        }
                        if (manual) {
                            scheduleImmediateServerRefresh(profile.id)
                        } else if (System.currentTimeMillis() < (nextServerRefreshAt[profile.id] ?: 0L)) {
                            return@forEach
                        }
                        refreshServerSnapshotNow(
                            profileId = profile.id,
                            showSelectedError = manual && profile.id == state.activeServerProfileId,
                        )
                    }
                } else {
                    val activeProfileId = state.activeServerProfileId
                    if (!activeProfileId.isNullOrBlank()) {
                        if (
                            shouldSkipRefreshForDashboardReorderHold(
                                heldProfileId = state.dashboardRefreshHoldProfileId,
                                holdAllProfiles = state.dashboardRefreshHoldAllProfiles,
                                profileId = activeProfileId,
                            )
                        ) {
                            return@launch
                        }
                        if (manual) {
                            scheduleImmediateServerRefresh(activeProfileId)
                        } else if (System.currentTimeMillis() < (nextServerRefreshAt[activeProfileId] ?: 0L)) {
                            return@launch
                        }
                        refreshServerSnapshotNow(
                            profileId = activeProfileId,
                            showSelectedError = manual,
                        )
                    }
                }
            } finally {
                isRefreshInProgress = false
                if (refreshQueued) {
                    refreshQueued = false
                    refresh()
                }
                if (manual) {
                    _uiState.update {
                        if (it.isManualRefreshing) {
                            it.copy(isManualRefreshing = false)
                        } else {
                            it
                        }
                    }
                }
                detectCompletedTorrents()
            }
        }
    }

    private fun detectCompletedTorrents() {
        val state = _uiState.value
        completionNotificationCoordinator.detectCompletedTorrents(
            enabled = state.settings.completionNotificationsEnabled,
            activeProfileId = state.activeServerProfileId,
            profiles = state.serverProfiles,
            torrents = state.torrents,
        )
    }

    // ---- Torrent operations (TorrentActionsController) ----
    fun pauseTorrent(hash: String) = torrentActions.pauseTorrent(hash)
    fun resumeTorrent(hash: String) = torrentActions.resumeTorrent(hash)
    fun deleteTorrent(hash: String, deleteFiles: Boolean) = torrentActions.deleteTorrent(hash, deleteFiles)
    internal fun runBatchTorrentAction(
        hashes: Collection<String>,
        action: BatchTorrentAction,
        deleteFiles: Boolean = false,
    ) = torrentActions.runBatchTorrentAction(hashes, action, deleteFiles)
    fun reannounceTorrent(hash: String) = torrentActions.reannounceTorrent(hash)
    fun recheckTorrent(hash: String) = torrentActions.recheckTorrent(hash)
    fun loadTorrentDetail(hash: String) = torrentActions.loadTorrentDetail(hash)
    fun setFilePriority(hash: String, fileIndexes: List<Int>, priority: com.hjw.qbremote.data.model.FilePriority) =
        torrentActions.setFilePriority(hash, fileIndexes, priority)
    fun renameTorrent(hash: String, newName: String) = torrentActions.renameTorrent(hash, newName)
    fun setTorrentLocation(hash: String, location: String) = torrentActions.setTorrentLocation(hash, location)
    fun setTorrentCategory(hash: String, category: String) = torrentActions.setTorrentCategory(hash, category)
    fun setTorrentTags(hash: String, oldTags: String, newTags: String) =
        torrentActions.setTorrentTags(hash, oldTags, newTags)
    fun setTorrentSpeedLimit(hash: String, downloadLimitKb: String, uploadLimitKb: String) =
        torrentActions.setTorrentSpeedLimit(hash, downloadLimitKb, uploadLimitKb)
    fun setTorrentShareRatio(hash: String, ratio: String) = torrentActions.setTorrentShareRatio(hash, ratio)
    fun addTracker(hash: String, trackerUrl: String) = torrentActions.addTracker(hash, trackerUrl)
    fun editTracker(hash: String, tracker: com.hjw.qbremote.data.model.TorrentTracker, newUrl: String) =
        torrentActions.editTracker(hash, tracker, newUrl)
    fun removeTracker(hash: String, tracker: com.hjw.qbremote.data.model.TorrentTracker) =
        torrentActions.removeTracker(hash, tracker)

    // ---- Global speed limit dialog (GlobalSpeedLimitController) ----
    fun openGlobalSpeedLimitDialog() = globalSpeedLimits.openGlobalSpeedLimitDialog()
    fun selectGlobalSpeedLimitProfile(profileId: String) = globalSpeedLimits.selectGlobalSpeedLimitProfile(profileId)
    fun retryGlobalSpeedLimitLoad() = globalSpeedLimits.retryGlobalSpeedLimitLoad()
    fun dismissGlobalSpeedLimitDialog() = globalSpeedLimits.dismissGlobalSpeedLimitDialog()
    fun saveGlobalSpeedLimits(limits: GlobalSpeedLimits) = globalSpeedLimits.saveGlobalSpeedLimits(limits)
    fun setGlobalAlternativeSpeedMode(enabled: Boolean) = globalSpeedLimits.setAlternativeSpeedMode(enabled)
    fun refreshAltSpeedModes(profileIds: List<String>) = globalSpeedLimits.refreshAltSpeedModes(profileIds)
    fun toggleAltSpeedForAll() = globalSpeedLimits.toggleAltSpeedForAll()
    fun toggleAltSpeed(profileId: String) = globalSpeedLimits.toggleAltSpeed(profileId)

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissPendingBackendRepair() {
        _uiState.update { current ->
            current.copy(pendingBackendRepair = null)
        }
    }

    fun confirmPendingBackendRepair() {
        val pending = _uiState.value.pendingBackendRepair ?: return
        viewModelScope.launch {
            runCatching {
                val profile = _uiState.value.serverProfiles.firstOrNull { it.id == pending.profileId }
                    ?: error("服务器配置不存在")
                val existingSettings = connectionStore.loadSettingsForProfile(pending.profileId)
                    ?: error("服务器配置不存在")
                val updatedSettings = existingSettings.copy(serverBackendType = pending.detectedBackend)
                connectionStore.updateServerProfile(
                    profileId = pending.profileId,
                    name = profile.name,
                    settings = updatedSettings,
                    passwordOverride = null,
                )
                repository.removeProfile(pending.profileId)
                scheduleImmediateServerRefresh(pending.profileId)
                val isActive = _uiState.value.activeServerProfileId == pending.profileId
                if (isActive) {
                    val switched = connectionStore.switchToServerProfile(pending.profileId)
                    repository.selectProfile(pending.profileId)
                    bumpActiveProfileRequestVersion()
                    val capabilities = repository.capabilitiesFor(switched)
                    _uiState.update { current ->
                        prepareConnectingProfileState(
                            current = current,
                            settings = switched,
                            profileId = pending.profileId,
                            capabilities = capabilities,
                        )
                    }
                    hydrateDashboardCacheForCurrentScope(force = true)
                } else {
                    _uiState.update { current ->
                        current.copy(
                            pendingBackendRepair = null,
                            errorMessage = null,
                        )
                    }
                }
                hydrateDashboardServerSnapshots()
                synchronizeServerScheduler()
                refreshServerSnapshotNow(
                    profileId = pending.profileId,
                    showSelectedError = true,
                )
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        pendingBackendRepair = null,
                        errorMessage = UiMessage.Text(userFacingConnectionMessage(error)),
                    )
                }
            }
        }
    }

    fun loadGlobalSelectionOptions() {
        val profileId = _uiState.value.activeServerProfileId?.trim().orEmpty()
        if (!_uiState.value.connected || profileId.isBlank()) return
        val requestVersion = currentActiveProfileRequestVersion()
        viewModelScope.launch {
            val categoryOptions = repository.fetchCategoryOptions(profileId).getOrElse { emptyList() }
            val tagOptions = repository.fetchTagOptions(profileId).getOrElse { emptyList() }
            _uiState.update { current ->
                if (!isActiveProfileRequestValid(profileId, requestVersion)) {
                    current
                } else {
                    current.copy(
                        categoryOptions = categoryOptions,
                        tagOptions = tagOptions,
                    )
                }
            }
        }
    }

    fun handleSharedMagnet(url: String) {
        val normalized = normalizeSharedTorrentInput(url)
        if (normalized.isBlank()) return
        _uiState.update { current ->
            val merged = mergeSharedTorrentInputs(
                existing = current.sharedTorrentInput?.urls.orEmpty(),
                incoming = normalized,
            )
            current.copy(
                sharedTorrentInput = SharedTorrentInput(
                    id = (current.sharedTorrentInput?.id ?: 0L) + 1L,
                    urls = merged,
                ),
            )
        }
    }

    fun clearSharedMagnetUrl() {
        _uiState.update { it.copy(sharedTorrentInput = null) }
    }

    fun addTorrent(
        urls: String,
        files: List<AddTorrentFile>,
        autoTmm: Boolean,
        category: String,
        tags: String,
        savePath: String,
        paused: Boolean,
        skipChecking: Boolean,
        sequentialDownload: Boolean,
        firstLastPiecePrio: Boolean,
        uploadLimitKb: String,
        downloadLimitKb: String,
    ) {
        if (!_uiState.value.connected) {
            _uiState.update { it.copy(errorMessage = UiMessage.Text("请先连接服务器。")) }
            return
        }
        val profileId = _uiState.value.activeServerProfileId?.trim().orEmpty()
        if (profileId.isBlank()) {
            _uiState.update { it.copy(errorMessage = UiMessage.Text("请先选择服务器。")) }
            return
        }
        val requestVersion = currentActiveProfileRequestVersion()
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingTorrent = true, errorMessage = null) }
            runCatching {
                val request = AddTorrentRequest(
                    urls = urls.trim(),
                    files = files,
                    autoTmm = autoTmm,
                    category = category.trim(),
                    tags = tags.trim(),
                    savePath = savePath.trim(),
                    paused = paused,
                    skipChecking = skipChecking,
                    sequentialDownload = sequentialDownload,
                    firstLastPiecePrio = firstLastPiecePrio,
                    uploadLimitBytes = parseLimitKbToBytes(uploadLimitKb),
                    downloadLimitBytes = parseLimitKbToBytes(downloadLimitKb),
                )
                repository.addTorrent(profileId, request).getOrThrow()
            }.onSuccess {
                if (isActiveProfileRequestValid(profileId, requestVersion)) {
                    loadGlobalSelectionOptions()
                    refresh()
                } else {
                    scheduleImmediateServerRefresh(profileId)
                }
            }.onFailure { error ->
                if (isActiveProfileRequestValid(profileId, requestVersion)) {
                    _uiState.update { it.copy(errorMessage = UiMessage.Text(error.message ?: "添加种子失败。")) }
                }
            }
            _uiState.update { it.copy(isAddingTorrent = false) }
        }
    }

    private fun refreshServerVersion() {
        val profileId = _uiState.value.activeServerProfileId?.trim().orEmpty()
        if (profileId.isBlank()) return
        val requestVersion = currentActiveProfileRequestVersion()
        viewModelScope.launch {
            repository.fetchServerVersion(profileId)
                .onSuccess { version ->
                    if (!isActiveProfileRequestValid(profileId, requestVersion)) return@onSuccess
                    var updatedState: MainUiState? = null
                    _uiState.update { current ->
                        if (!isActiveProfileRequestValid(profileId, requestVersion)) {
                            current
                        } else {
                            current.copy(serverVersion = version.ifBlank { "-" })
                                .also { updatedState = it }
                        }
                    }
                    updatedState?.let { stateSnapshot ->
                        saveDashboardServerSnapshotForProfile(
                            profileId = profileId,
                            stateSnapshot = stateSnapshot,
                        )
                    }
                }
        }
    }

    private fun saveDashboardCache(
        stateSnapshot: MainUiState = _uiState.value,
        scopeKey: String = buildDailyUploadTrackingScopeKey(
            activeProfileId = stateSnapshot.activeServerProfileId,
            settings = stateSnapshot.settings,
        ),
    ) {
        viewModelScope.launch {
            connectionStore.saveDashboardCacheSnapshot(
                scopeKey = scopeKey,
                snapshot = buildDashboardCacheSnapshot(stateSnapshot),
            )
        }
    }

    private fun hydrateDashboardServerSnapshots() {
        dashboardAggregationJob?.cancel()
        dashboardAggregationJob = viewModelScope.launch {
            val ordered = orderedDashboardServerSnapshots(
                profiles = _uiState.value.serverProfiles,
                snapshotsById = connectionStore.loadDashboardServerSnapshots(),
            )
            val aggregate = buildDashboardAggregateWithHistory(
                snapshots = ordered,
                sampleFreshData = false,
            )
            _uiState.update { current ->
                applyDashboardSnapshotsToState(
                    current = current,
                    orderedSnapshots = ordered,
                    aggregate = aggregate,
                )
            }
            syncSelectedUiFromStoredSnapshot()
            markInitialDashboardSnapshotsHydrated()
        }
    }

    private fun scheduleImmediateServerRefresh(profileId: String) {
        nextServerRefreshAt[profileId] = 0L
        serverRefreshFailureStreaks.remove(profileId)
    }

    private fun synchronizeServerScheduler() {
        val profiles = _uiState.value.serverProfiles
        if (profiles.isEmpty()) {
            serverSchedulerJob?.cancel()
            serverSchedulerJob = null
            nextServerRefreshAt.clear()
            serverRefreshFailureStreaks.clear()
            repository.clearAllSessions()
            return
        }

        val activeIds = profiles.map { it.id }.toSet()
        nextServerRefreshAt.keys.retainAll(activeIds)
        serverRefreshFailureStreaks.keys.retainAll(activeIds)
        profiles.forEach { profile ->
            nextServerRefreshAt.putIfAbsent(profile.id, 0L)
        }
        repository.selectProfile(_uiState.value.activeServerProfileId)

        if (serverSchedulerJob?.isActive == true) return
        serverSchedulerJob = viewModelScope.launch {
            while (isActive) {
                awaitAppForeground()
                val currentProfiles = _uiState.value.serverProfiles
                if (currentProfiles.isEmpty()) break
                val now = System.currentTimeMillis()
                val dueProfileIds = selectDueServerRefreshProfileIds(
                    profiles = currentProfiles,
                    nextRefreshAtByProfileId = nextServerRefreshAt,
                    inFlightProfileIds = inFlightServerRefreshes,
                    heldProfileId = _uiState.value.dashboardRefreshHoldProfileId,
                    holdAllProfiles = _uiState.value.dashboardRefreshHoldAllProfiles,
                    now = now,
                )
                dueProfileIds.forEach { profileId ->
                    // Refresh due profiles concurrently; per-profile mutexes keep
                    // each server's refresh serialized with manual refreshes while
                    // an unreachable server no longer stalls the others.
                    inFlightServerRefreshes += profileId
                    launch {
                        try {
                            refreshServerSnapshotNow(
                                profileId = profileId,
                                showSelectedError = false,
                            )
                        } finally {
                            inFlightServerRefreshes -= profileId
                        }
                    }
                }
                delay(1_000L)
            }
        }
    }

    private suspend fun refreshServerSnapshotNow(
        profileId: String,
        showSelectedError: Boolean,
        forceSettings: ConnectionSettings? = null,
    ) {
        if (profileId.isBlank()) return
        serverRefreshMutexFor(profileId).withLock {
            val state = _uiState.value
            val profile = state.serverProfiles.firstOrNull { it.id == profileId }
            val settings = forceSettings ?: resolveProfileSettings(profileId) ?: return
            val isSelectedProfile = state.activeServerProfileId == profileId
            val selectedRequestVersion = currentActiveProfileRequestVersion()
            updateCachedProfileSettings(profileId, settings)
            if (isSelectedProfile) {
                _uiState.update { current ->
                    current.copy(
                        isConnecting = true,
                        errorMessage = if (showSelectedError) null else current.errorMessage,
                    )
                }
            }

            val result = runCatching {
                repository.connect(profileId, settings).getOrThrow()
                val serverVersion = repository.fetchServerVersion(profileId).getOrElse { "-" }
                val dashboardData = repository.fetchDashboard(profileId).getOrThrow()
                val (tagDate, tagStats) = dashboardPersistence.buildDashboardTagUploadStatsForScope(
                    scopeKey = "profile:$profileId",
                    torrents = dashboardData.torrents,
                )
                val countryStats = if (repository.capabilitiesFor(settings).supportsCountryDistribution) {
                    dashboardPersistence.buildDashboardCountryUploadStatsForScope(
                        scopeKey = "profile:$profileId",
                        torrents = dashboardData.torrents,
                        fetchPeerSnapshots = { hashes ->
                            repository.fetchCountryPeerSnapshots(profileId, hashes)
                                .getOrElse { emptyList() }
                        },
                    )
                } else {
                    DailyCountryUploadStats(
                        dateLabel = tagDate,
                        countries = emptyList(),
                    )
                }
                CachedDashboardServerSnapshot(
                    profileId = profileId,
                    profileName = profile?.name ?: settings.host,
                    backendType = profile?.backendType ?: settings.serverBackendType,
                    host = profile?.host ?: settings.host,
                    port = profile?.port ?: settings.port,
                    useHttps = profile?.useHttps ?: settings.useHttps,
                    serverVersion = serverVersion.ifBlank { "-" },
                    transferInfo = dashboardData.transferInfo,
                    torrents = dashboardData.torrents,
                    dailyTagUploadDate = tagDate,
                    dailyTagUploadStats = tagStats.map { stat ->
                        CachedDailyTagUploadStat(
                            tag = stat.tag,
                            uploadedBytes = stat.uploadedBytes,
                            torrentCount = stat.torrentCount,
                            isNoTag = stat.isNoTag,
                        )
                    },
                    dailyCountryUploadDate = countryStats.dateLabel,
                    dailyCountryUploadStats = countryStats.countries,
                    lastUpdatedAt = System.currentTimeMillis(),
                    errorMessage = "",
                    isStale = false,
                )
            }

            result.onSuccess { snapshot ->
                dashboardPersistence.persistDashboardSnapshot(snapshot, throttle = true)
                mergeDashboardSnapshot(snapshot, sampleFreshData = true)
                serverRefreshFailureStreaks.remove(profileId)
                nextServerRefreshAt[profileId] = nextServerRefreshDueAt(
                    now = System.currentTimeMillis(),
                    refreshSeconds = settings.refreshSeconds,
                )

                if (isSelectedProfile) {
                    repository.selectProfile(profileId)
                    if (isActiveProfileRequestValid(profileId, selectedRequestVersion)) {
                        syncSelectedUiFromSnapshot(
                            profileId = profileId,
                            settings = settings,
                            snapshot = snapshot,
                            connected = true,
                            selectedErrorMessage = null,
                            requestVersion = selectedRequestVersion,
                        )
                    }
                }
            }.onFailure { error ->
                // A cancelled refresh (profile switch, app backgrounded) is not a
                // server failure: don't persist a stale "cancelled" snapshot.
                if (error is CancellationException) throw error
                repository.markDisconnected(profileId)
                Log.w("QBRemote", "refreshServerSnapshotNow failed for profile=$profileId", error)
                val summaryMessage = userFacingConnectionMessage(error)
                val currentSnapshot = _uiState.value.dashboardServerSnapshots
                    .firstOrNull { it.profileId == profileId }
                    ?: dashboardPersistence.loadStoredDashboardSnapshot(profileId)
                val staleSnapshot = buildStaleDashboardServerSnapshot(
                    profileId = profileId,
                    profileName = profile?.name ?: currentSnapshot?.profileName ?: settings.host,
                    backendType = profile?.backendType ?: currentSnapshot?.backendType ?: settings.serverBackendType,
                    host = profile?.host ?: currentSnapshot?.host ?: settings.host,
                    port = profile?.port ?: currentSnapshot?.port ?: settings.port,
                    useHttps = profile?.useHttps ?: currentSnapshot?.useHttps ?: settings.useHttps,
                    previousSnapshot = currentSnapshot,
                    errorMessage = summaryMessage,
                )
                dashboardPersistence.persistDashboardSnapshot(staleSnapshot)
                mergeDashboardSnapshot(staleSnapshot, sampleFreshData = false)
                val streak = (serverRefreshFailureStreaks[profileId] ?: 0) + 1
                serverRefreshFailureStreaks[profileId] = streak
                nextServerRefreshAt[profileId] = System.currentTimeMillis() +
                    nextServerRetryDelayMs(nextRefreshIntervalMs(settings), streak)

                if (isSelectedProfile && error is BackendConnectionError.WrongBackend) {
                    maybeQueueBackendRepair(
                        profileId = profileId,
                        profileName = profile?.name ?: staleSnapshot.profileName,
                        error = error,
                    )
                }

                if (isSelectedProfile) {
                    repository.selectProfile(profileId)
                    if (isActiveProfileRequestValid(profileId, selectedRequestVersion)) {
                        syncSelectedUiFromSnapshot(
                            profileId = profileId,
                            settings = settings,
                            snapshot = staleSnapshot,
                            connected = false,
                            selectedErrorMessage = if (error is BackendConnectionError.WrongBackend) {
                                null
                            } else if (showSelectedError && !shouldSuppressRefreshError(summaryMessage)) {
                                summaryMessage
                            } else {
                                null
                            },
                            requestVersion = selectedRequestVersion,
                        )
                    }
                }
            }
        }
    }

    private suspend fun syncSelectedUiFromStoredSnapshot() {
        val profileId = _uiState.value.activeServerProfileId ?: return
        val settings = resolveProfileSettings(profileId) ?: return
        val snapshot = _uiState.value.dashboardServerSnapshots.firstOrNull { it.profileId == profileId }
            ?: dashboardPersistence.loadStoredDashboardSnapshot(profileId)
        repository.selectProfile(profileId)
        val requestVersion = currentActiveProfileRequestVersion()
        syncSelectedUiFromSnapshot(
            profileId = profileId,
            settings = settings,
            snapshot = snapshot,
            connected = repository.isConnected(profileId) && snapshot?.isStale == false,
            selectedErrorMessage = null,
            requestVersion = requestVersion,
        )
    }

    private suspend fun syncSelectedUiFromSnapshot(
        profileId: String,
        settings: ConnectionSettings,
        snapshot: CachedDashboardServerSnapshot?,
        connected: Boolean,
        selectedErrorMessage: String?,
        requestVersion: Long,
    ) {
        if (!isActiveProfileRequestValid(profileId, requestVersion)) return

        val categoryOptions = if (connected) {
            repository.fetchCategoryOptions(profileId).getOrElse { emptyList() }
        } else {
            emptyList()
        }
        val tagOptions = if (connected) {
            repository.fetchTagOptions(profileId).getOrElse { emptyList() }
        } else {
            emptyList()
        }

        _uiState.update { current ->
            if (!isActiveProfileRequestValid(profileId, requestVersion)) {
                current
            } else {
                current.copy(
                    settings = settings,
                    activeCapabilities = repository.capabilitiesFor(settings),
                    isConnecting = false,
                    connected = connected,
                    serverVersion = snapshot?.serverVersion?.ifBlank { "-" } ?: "-",
                    transferInfo = snapshot?.transferInfo ?: TransferInfo(),
                    torrents = snapshot?.torrents ?: emptyList(),
                    dailyTagUploadDate = snapshot?.dailyTagUploadDate.orEmpty(),
                    dailyTagUploadStats = snapshot?.dailyTagUploadStats?.map { stat ->
                        DailyTagUploadStat(
                            tag = stat.tag,
                            uploadedBytes = stat.uploadedBytes,
                            torrentCount = stat.torrentCount,
                            isNoTag = stat.isNoTag,
                        )
                    }.orEmpty(),
                    dailyCountryUploadDate = snapshot?.dailyCountryUploadDate.orEmpty(),
                    dailyCountryUploadStats = snapshot?.dailyCountryUploadStats.orEmpty(),
                    categoryOptions = categoryOptions,
                    tagOptions = tagOptions,
                    dashboardCacheHydrated = true,
                    hasDashboardSnapshot = snapshot != null,
                    pendingBackendRepair = current.pendingBackendRepair
                        ?.takeUnless { connected && it.profileId == profileId },
                    errorMessage = selectedErrorMessage?.let { message -> UiMessage.Text(message) },
                )
            }
        }

        if (snapshot != null && isActiveProfileRequestValid(profileId, requestVersion)) {
            saveDashboardCache()
        }

        val detailHash = _uiState.value.detailHash
        if (connected && _uiState.value.refreshScene == RefreshScene.TORRENT_DETAIL && detailHash.isNotBlank()) {
            torrentActions.refreshDetailSnapshot(profileId, detailHash, requestVersion)
        }
    }

    private suspend fun mergeDashboardSnapshot(
        snapshot: CachedDashboardServerSnapshot,
        sampleFreshData: Boolean,
    ) {
        val current = _uiState.value
        val snapshotsById = current.dashboardServerSnapshots
            .associateBy { it.profileId }
            .toMutableMap()
        snapshotsById[snapshot.profileId] = snapshot
        val ordered = orderedDashboardServerSnapshots(current.serverProfiles, snapshotsById)
        val aggregate = buildDashboardAggregateWithHistory(
            snapshots = ordered,
            sampleFreshData = sampleFreshData,
        )
        _uiState.update { latest ->
            applyDashboardSnapshotsToState(
                current = latest,
                orderedSnapshots = ordered,
                aggregate = aggregate,
            )
        }
    }

    private fun nextRefreshIntervalMs(settings: ConnectionSettings): Long {
        return settings.refreshSeconds.coerceIn(5, 120) * 1_000L
    }

    private fun refreshDashboardServerSnapshotsAsync(skipActive: Boolean = false) {
        dashboardAggregationJob?.cancel()
        dashboardAggregationJob = viewModelScope.launch {
            val profiles = _uiState.value.serverProfiles
            if (profiles.isEmpty()) {
                realtimeSpeedTracker.mutex.withLock {
                    resetHomeRealtimeSpeedSeriesStateLocked(clearPersisted = true)
                }
                _uiState.update { current ->
                    current.copy(
                        dashboardServerSnapshots = emptyList(),
                        selectedDashboardProfileId = null,
                        dashboardAggregate = DashboardAggregateState(),
                        aggregateOnlineServerCount = 0,
                    )
                }
                markInitialDashboardSnapshotsHydrated()
                return@launch
            }

            val snapshots = dashboardPersistence.loadDashboardSnapshotsMap()
            val activeProfileId = _uiState.value.activeServerProfileId
            val activeProfile = profiles.firstOrNull { it.id == activeProfileId }

            if (!skipActive && _uiState.value.connected && activeProfile != null) {
                val activeSnapshot = buildActiveDashboardServerSnapshot(activeProfile, _uiState.value)
                dashboardPersistence.persistDashboardSnapshot(activeSnapshot, snapshots)
            }

            val refreshResults = supervisorScope {
                profiles.mapNotNull { profile ->
                    if (profile.id == activeProfileId && _uiState.value.connected) {
                        null
                    } else {
                        async {
                            val previousSnapshot = snapshots[profile.id]
                            val settings = resolveProfileSettings(profile.id)
                            if (settings == null) {
                                DashboardSnapshotRefreshResult.Failure(
                                    profile = profile,
                                    error = IllegalStateException("Missing saved settings."),
                                    previousSnapshot = previousSnapshot,
                                )
                            } else {
                                // Same per-profile mutex as the scheduler: the scheduler's
                                // own refresh then reuses this live session instead of
                                // logging in to the server a second time.
                                serverRefreshMutexFor(profile.id).withLock {
                                    repository.fetchDashboardSnapshot(profile.id, settings)
                                }.fold(
                                    onSuccess = { fetched ->
                                        DashboardSnapshotRefreshResult.Fresh(
                                            profile = profile,
                                            settings = settings,
                                            fetched = fetched,
                                            previousSnapshot = previousSnapshot,
                                        )
                                    },
                                    onFailure = { error ->
                                        DashboardSnapshotRefreshResult.Failure(
                                            profile = profile,
                                            error = error,
                                            previousSnapshot = previousSnapshot,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }.awaitAll()
            }

            val pendingStatsRefreshes = mutableListOf<DashboardStatsRefreshInput>()
            refreshResults.forEach { result ->
                when (result) {
                    is DashboardSnapshotRefreshResult.Fresh -> {
                        val baseSnapshot = buildCachedDashboardSnapshotFromFetch(
                            profile = result.profile,
                            fetched = result.fetched,
                            previousSnapshot = result.previousSnapshot,
                        )
                        dashboardPersistence.persistDashboardSnapshot(baseSnapshot, snapshots)
                        pendingStatsRefreshes += DashboardStatsRefreshInput(
                            profile = result.profile,
                            settings = result.settings,
                            torrents = result.fetched.dashboardData.torrents,
                            baseSnapshot = baseSnapshot,
                        )
                    }

                    is DashboardSnapshotRefreshResult.Failure -> {
                        val staleSnapshot = buildStaleDashboardServerSnapshot(
                            profileId = result.profile.id,
                            profileName = result.profile.name,
                            backendType = result.profile.backendType,
                            host = result.profile.host,
                            port = result.profile.port,
                            useHttps = result.profile.useHttps,
                            previousSnapshot = result.previousSnapshot,
                            errorMessage = result.error.message ?: "Refresh failed.",
                        )
                        dashboardPersistence.persistDashboardSnapshot(staleSnapshot, snapshots)
                    }
                }
            }

            val ordered = orderedDashboardServerSnapshots(profiles, snapshots)
            val aggregate = buildDashboardAggregateWithHistory(
                snapshots = ordered,
                sampleFreshData = true,
            )
            _uiState.update { current ->
                applyDashboardSnapshotsToState(
                    current = current,
                    orderedSnapshots = ordered,
                    aggregate = aggregate,
                )
            }
            markInitialDashboardSnapshotsHydrated()

            if (pendingStatsRefreshes.isEmpty()) return@launch

            val enrichedSnapshots = supervisorScope {
                pendingStatsRefreshes.map { input ->
                    async {
                        enrichDashboardSnapshotStats(input)
                    }
                }.awaitAll()
            }

            if (!isActive) return@launch

            enrichedSnapshots.forEach { snapshot ->
                dashboardPersistence.persistDashboardSnapshot(snapshot, snapshots)
            }

            val orderedEnriched = orderedDashboardServerSnapshots(profiles, snapshots)
            val aggregateWithEnrichedStats = buildDashboardAggregateWithHistory(
                snapshots = orderedEnriched,
                sampleFreshData = false,
            )
            _uiState.update { current ->
                applyDashboardSnapshotsToState(
                    current = current,
                    orderedSnapshots = orderedEnriched,
                    aggregate = aggregateWithEnrichedStats,
                )
            }
            markInitialDashboardSnapshotsHydrated()
        }
    }

    private suspend fun enrichDashboardSnapshotStats(
        input: DashboardStatsRefreshInput,
    ): CachedDashboardServerSnapshot {
        val tagStats = dashboardPersistence.buildDashboardTagUploadStatsForScope(
            scopeKey = "profile:${input.profile.id}",
            torrents = input.torrents,
        )
        val countryStats = if (repository.capabilitiesFor(input.settings).supportsCountryDistribution) {
            dashboardPersistence.buildDashboardCountryUploadStatsForScope(
                scopeKey = "profile:${input.profile.id}",
                torrents = input.torrents,
                fetchPeerSnapshots = { hashes ->
                    try {
                        repository.fetchCountryPeerSnapshots(input.profile.id, hashes)
                            .getOrElse { emptyList() }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: IllegalStateException) {
                        // Session was cleared (profile removed / switched) mid-refresh.
                        emptyList()
                    }
                },
            )
        } else {
            DailyCountryUploadStats(
                dateLabel = tagStats.first,
                countries = emptyList(),
            )
        }
        return input.baseSnapshot.copy(
            dailyTagUploadDate = tagStats.first,
            dailyTagUploadStats = tagStats.second.map { stat ->
                CachedDailyTagUploadStat(
                    tag = stat.tag,
                    uploadedBytes = stat.uploadedBytes,
                    torrentCount = stat.torrentCount,
                    isNoTag = stat.isNoTag,
                )
            },
            dailyCountryUploadDate = countryStats.dateLabel,
            dailyCountryUploadStats = countryStats.countries,
            lastUpdatedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun saveDashboardServerSnapshotForProfile(
        profileId: String,
        stateSnapshot: MainUiState,
    ) {
        val normalizedProfileId = profileId.trim()
        if (normalizedProfileId.isBlank()) return
        val targetProfile = stateSnapshot.serverProfiles.firstOrNull { it.id == normalizedProfileId } ?: return
        val snapshot = buildActiveDashboardServerSnapshot(targetProfile, stateSnapshot)
        dashboardPersistence.persistDashboardSnapshot(snapshot)
    }

    private suspend fun buildDashboardAggregateWithHistory(
        snapshots: List<CachedDashboardServerSnapshot>,
        sampleFreshData: Boolean,
    ): DashboardAggregateState {
        if (snapshots.isEmpty()) {
            realtimeSpeedTracker.mutex.withLock {
                resetHomeRealtimeSpeedSeriesStateLocked(clearPersisted = true)
            }
            return DashboardAggregateState()
        }
        val scopeKey = resolveHomeRealtimeSpeedScopeKey(snapshots)
        val aggregate = buildDashboardAggregateFromSnapshots(snapshots)
        val liveServerCount = snapshots.count { !it.isStale }
        val realtimeSpeedSeries = realtimeSpeedTracker.mutex.withLock {
            ensureHomeRealtimeSpeedSeriesLoadedLocked(scopeKey)
            when {
                liveServerCount <= 0 -> {
                    clearHomeRealtimeSpeedSeriesLocked(scopeKey)
                    emptyList()
                }
                sampleFreshData -> sampleHomeRealtimeSpeedPointLocked(
                    transferInfo = aggregate.transferInfo,
                    onlineServerCount = liveServerCount,
                    scopeKey = scopeKey,
                )
                else -> realtimeSpeedTracker.series.toList()
            }
        }
        return aggregate.copy(
            chartTransferInfo = null,
            realtimeSpeedSeries = realtimeSpeedSeries,
        )
    }

    private suspend fun sampleHomeRealtimeSpeedPointLocked(
        transferInfo: TransferInfo,
        onlineServerCount: Int,
        scopeKey: String,
    ): List<RealtimeSpeedPoint> {
        return realtimeSpeedTracker.sampleLocked(transferInfo, onlineServerCount, scopeKey)
    }

    private suspend fun clearHomeRealtimeSpeedSeriesLocked(scopeKey: String) {
        realtimeSpeedTracker.clearLocked(scopeKey)
    }

    private suspend fun resetHomeRealtimeSpeedSeriesStateLocked(clearPersisted: Boolean) {
        realtimeSpeedTracker.resetLocked(clearPersisted)
    }

    private suspend fun ensureHomeRealtimeSpeedSeriesLoadedLocked(scopeKey: String) {
        realtimeSpeedTracker.ensureLoadedLocked(scopeKey)
    }

    private fun resolveHomeRealtimeSpeedScopeKey(
        snapshots: List<CachedDashboardServerSnapshot>,
    ): String {
        return realtimeSpeedTracker.resolveScopeKey(snapshots, currentDailyUploadTrackingScopeKey())
    }

    private fun maybeQueueBackendRepair(
        profileId: String,
        profileName: String,
        error: BackendConnectionError.WrongBackend,
    ) {
        _uiState.update { current ->
            current.copy(
                pendingBackendRepair = PendingBackendRepair(
                    profileId = profileId,
                    profileName = profileName.ifBlank { profileId },
                    expectedBackend = error.expected,
                    detectedBackend = error.detected,
                    detail = error.detail,
                ),
            )
        }
    }

    private fun hydrateDashboardCacheForCurrentScope(force: Boolean = false) {
        val scopeKey = currentDailyUploadTrackingScopeKey()
        if (!force && scopeKey == hydratedDashboardScopeKey && _uiState.value.dashboardCacheHydrated) {
            return
        }

        hydratedDashboardScopeKey = scopeKey
        dashboardCacheHydrationJob?.cancel()
        _uiState.update { current ->
            current.copy(
                dashboardCacheHydrated = false,
            )
        }

        dashboardCacheHydrationJob = viewModelScope.launch {
            val cache = connectionStore.loadDashboardCacheSnapshot(scopeKey)
            if (hydratedDashboardScopeKey != scopeKey) return@launch

            _uiState.update { current ->
                if (hydratedDashboardScopeKey != scopeKey) {
                    current
                } else {
                    applyDashboardCacheHydration(
                        current = current,
                        cache = cache,
                    )
                }
            }
            markInitialDashboardCacheHydrated()
        }
    }

    private fun updateSettings(update: (ConnectionSettings) -> ConnectionSettings) {
        _uiState.update { current ->
            val nextSettings = update(current.settings)
            if (nextSettings == current.settings) {
                current
            } else {
                current.copy(settings = nextSettings)
            }
        }
    }

    private fun persistUiPreferences(
        patch: UiPreferencePatch,
        update: (ConnectionSettings) -> ConnectionSettings,
        onPersisted: (suspend () -> Unit)? = null,
    ) {
        var changed = false
        _uiState.update { current ->
            val next = update(current.settings)
            if (next == current.settings) {
                current
            } else {
                changed = true
                current.copy(settings = next)
            }
        }
        if (!changed) return
        pendingUiPreferenceWrites += 1
        viewModelScope.launch {
            try {
                val result = runCatching { connectionStore.saveUiPreferences(patch) }
                if (result.isSuccess) {
                    onPersisted?.invoke()
                }
            } finally {
                pendingUiPreferenceWrites -= 1
                if (pendingUiPreferenceWrites == 0) reconcileSettingsFromStore()
            }
        }
    }

    private suspend fun reconcileSettingsFromStore() {
        // Reconcile with the store once all writes have landed, so any emission
        // skipped while writes were in flight cannot leave uiState permanently
        // ahead of (or behind) persisted state.
        val stored = runCatching { connectionStore.settingsFlow.first() }.getOrNull() ?: return
        // Re-check after the suspension: a write started while first() was pending
        // must keep the gate closed, or the stale read would revert it.
        if (pendingUiPreferenceWrites != 0) return
        _uiState.update { current ->
            if (current.settings == stored) {
                current
            } else {
                current.copy(
                    settings = stored,
                    activeCapabilities = repository.capabilitiesFor(stored),
                )
            }
        }
    }

    private suspend fun refreshCustomBackgroundAvailability(settings: ConnectionSettings) {
        val path = settings.customBackgroundImagePath
        if (settings.appTheme != AppTheme.CUSTOM || path.isBlank()) {
            lastCheckedCustomBackgroundPath = null
            _uiState.update { current ->
                if (current.customBackgroundAvailable) {
                    current
                } else {
                    current.copy(customBackgroundAvailable = true)
                }
            }
            return
        }
        if (path == lastCheckedCustomBackgroundPath) return
        lastCheckedCustomBackgroundPath = path
        val available = withContext(Dispatchers.IO) {
            runCatching { File(path).let { it.isFile && it.length() > 0L } }.getOrDefault(false)
        }
        _uiState.update { current ->
            if (current.customBackgroundAvailable == available) {
                current
            } else {
                current.copy(customBackgroundAvailable = available)
            }
        }
    }

    private fun startAutoRefresh() = backgroundJobManager.startAutoRefresh()
    private fun startHomeChartRefresh() = backgroundJobManager.startHomeChartRefresh()
    private fun startHourlyBoundaryRefresh() = backgroundJobManager.startHourlyBoundaryRefresh()

    private suspend fun refreshHomeDashboardChartTransferInfo() {
        val state = _uiState.value
        if (state.refreshScene != RefreshScene.DASHBOARD) return
        val profiles = state.serverProfiles
        if (profiles.isEmpty()) return
        val requestedProfileIds = normalizeProfileIdsForRefresh(profiles)

        val transferInfoByProfileId = supervisorScope {
            profiles.map { profile ->
                async {
                    // Chart ticks never reconnect: the server scheduler is the sole
                    // owner of reconnection (mutex + in-flight guard + backoff), so
                    // a disconnected server simply drops out of the aggregate until
                    // its session is restored.
                    if (!repository.isConnected(profile.id)) return@async null
                    val result = repository.fetchTransferInfo(profile.id)
                    result.getOrNull()?.let { transferInfo ->
                        profile.id to transferInfo
                    }
                }
            }.awaitAll()
                .filterNotNull()
                .toMap()
        }
        if (transferInfoByProfileId.isEmpty()) return
        if (state.dashboardServerSnapshots.isEmpty()) return

        val latestState = _uiState.value
        if (latestState.refreshScene != RefreshScene.DASHBOARD) return
        val latestProfileIds = normalizeProfileIdsForRefresh(latestState.serverProfiles)
        if (latestProfileIds != requestedProfileIds) return

        val chartTransferInfo = buildHomeChartTransferInfo(transferInfoByProfileId.values)
        val scopeKey = resolveHomeRealtimeSpeedScopeKey(latestState.dashboardServerSnapshots)
        val chartSeries = realtimeSpeedTracker.mutex.withLock {
            ensureHomeRealtimeSpeedSeriesLoadedLocked(scopeKey)
            sampleHomeRealtimeSpeedPointLocked(
                transferInfo = chartTransferInfo,
                onlineServerCount = transferInfoByProfileId.size.coerceAtLeast(1),
                scopeKey = scopeKey,
            )
        }

        val latestStateAfterSampling = _uiState.value
        if (latestStateAfterSampling.refreshScene != RefreshScene.DASHBOARD) return
        val latestProfileIdsAfterSampling = normalizeProfileIdsForRefresh(latestStateAfterSampling.serverProfiles)
        if (latestProfileIdsAfterSampling != requestedProfileIds) return

        _uiState.update { current ->
            current.copy(
                dashboardAggregate = applyHomeChartRefreshToAggregate(
                    aggregate = current.dashboardAggregate,
                    chartTransferInfo = chartTransferInfo,
                    chartSeries = chartSeries,
                ),
            )
        }
    }

    private fun resetDailyCountryUploadTrackingState() {
        _uiState.update {
            it.copy(
                dailyCountryUploadDate = "",
                dailyCountryUploadStats = emptyList(),
            )
        }
    }
    private fun pruneCachedProfileSettingsInMemory(profiles: List<ServerProfile>) {
        val pruned = pruneCachedProfileSettings(
            cache = cachedProfileSettings,
            profiles = profiles,
        )
        cachedProfileSettings.clear()
        cachedProfileSettings.putAll(pruned)
        val profileIds = profiles.map { it.id }.toSet()
        val trackingScopeKeys = profileIds.map { "profile:$it" }.toSet()
        dashboardPersistence.retainProfiles(profileIds, trackingScopeKeys)
    }

    private fun updateCachedProfileSettings(
        profileId: String,
        settings: ConnectionSettings,
    ) {
        val updated = cacheProfileSettings(
            cache = cachedProfileSettings,
            profileId = profileId,
            settings = settings,
        )
        if (updated === cachedProfileSettings) return
        cachedProfileSettings.clear()
        cachedProfileSettings.putAll(updated)
    }

    private suspend fun resolveProfileSettings(profileId: String): ConnectionSettings? {
        val normalizedProfileId = profileId.trim()
        if (normalizedProfileId.isBlank()) return null
        val activeProfile = _uiState.value.serverProfiles.firstOrNull { it.id == normalizedProfileId }
        val currentState = _uiState.value
        resolveActiveOrCachedProfileSettings(
            profileId = normalizedProfileId,
            activeProfileId = currentState.activeServerProfileId,
            activeProfile = activeProfile,
            currentSettings = currentState.settings,
            cachedSettings = cachedProfileSettings[normalizedProfileId],
        )?.let { resolved ->
            updateCachedProfileSettings(normalizedProfileId, resolved)
            return resolved
        }

        val loaded = connectionStore.loadSettingsForProfile(normalizedProfileId) ?: return null
        updateCachedProfileSettings(normalizedProfileId, loaded)
        return loaded
    }

    private fun currentDailyUploadTrackingScopeKey(): String {
        val state = _uiState.value
        val preferredProfileId = state.selectedDashboardProfileId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: state.activeServerProfileId
        return buildDailyUploadTrackingScopeKey(
            activeProfileId = preferredProfileId,
            settings = state.settings,
        )
    }

    private suspend fun seedCachedSettingsForProfile(profileId: String?) {
        val normalizedProfileId = profileId?.trim().orEmpty()
        if (normalizedProfileId.isBlank()) return
        val activeProfile = _uiState.value.serverProfiles.firstOrNull { it.id == normalizedProfileId }
        val currentSettings = _uiState.value.settings
        val settings = if (activeProfile != null && settingsBelongToProfile(activeProfile, currentSettings)) {
            currentSettings
        } else {
            connectionStore.loadSettingsForProfile(normalizedProfileId)
        } ?: return
        updateCachedProfileSettings(normalizedProfileId, settings)
    }

    override fun onCleared() {
        backgroundJobManager.stopAll()
        dashboardAggregationJob?.cancel()
        serverSchedulerJob?.cancel()
        // viewModelScope dies with this call; flush the throttled speed series on a
        // detached scope so the last in-memory samples still reach disk.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { realtimeSpeedTracker.withLock { realtimeSpeedTracker.flushLocked() } }
            runCatching { dashboardPersistence.flushThrottledPersistence() }
        }
        repository.clearAllSessions()
    }

    companion object {

        fun factory(
            connectionStore: ConnectionStore,
            repository: TorrentRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(connectionStore, repository) as T
            }
        }
    }
}

