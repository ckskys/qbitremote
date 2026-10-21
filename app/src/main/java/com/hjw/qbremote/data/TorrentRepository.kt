package com.hjw.qbremote.data

import kotlinx.coroutines.withTimeoutOrNull
import com.hjw.qbremote.data.model.CountryPeerSample
import com.hjw.qbremote.data.model.AddTorrentRequest
import com.hjw.qbremote.data.model.TorrentTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TorrentRepository {
    private data class SessionEntry(
        var settings: ConnectionSettings,
        var backend: TorrentBackend,
        var connected: Boolean = false,
    )

    private val mutex = Mutex()
    private val sessions = linkedMapOf<String, SessionEntry>()
    private var selectedProfileId: String? = null

    fun capabilitiesFor(backendType: ServerBackendType): ServerCapabilities {
        return defaultCapabilitiesFor(backendType)
    }

    fun capabilitiesFor(settings: ConnectionSettings): ServerCapabilities {
        return capabilitiesFor(settings.serverBackendType)
    }

    fun selectProfile(profileId: String?) {
        selectedProfileId = profileId?.trim()?.takeIf(String::isNotBlank)
    }

    fun activeCapabilities(): ServerCapabilities {
        val selectedId = selectedProfileId
        val selectedBackend = selectedId?.let { sessions[it]?.backend }
        return selectedBackend?.capabilities ?: defaultCapabilitiesFor(ServerBackendType.QBITTORRENT)
    }

    fun isConnected(profileId: String): Boolean {
        return sessions[profileId]?.connected == true
    }

    /**
     * Marks a session as stale so the next [connect] performs a real login again.
     * Call this when a fetch on the profile failed for reasons that may indicate a
     * dead session (server restart, network change).
     */
    fun markDisconnected(profileId: String) {
        sessions[profileId]?.connected = false
    }

    suspend fun connect(
        profileId: String,
        settings: ConnectionSettings,
        force: Boolean = false,
    ): Result<Unit> {
        require(profileId.isNotBlank()) { "Profile id cannot be blank." }
        storedPasswordUnreadableError(settings)?.let { return Result.failure(it) }
        var reusableSession = false
        val entry = mutex.withLock {
            val existing = sessions[profileId]
            when {
                existing == null -> {
                    SessionEntry(
                        settings = settings,
                        backend = createBackend(settings.serverBackendType),
                    ).also { sessions[profileId] = it }
                }

                existing.settings.serverBackendType != settings.serverBackendType -> {
                    existing.backend.clearSession()
                    SessionEntry(
                        settings = settings,
                        backend = createBackend(settings.serverBackendType),
                    ).also { sessions[profileId] = it }
                }

                else -> {
                    // Skip the full login handshake when the session is already live
                    // and nothing changed. The polling loop calls connect() on every
                    // cycle; re-logging in each time hammers the server (and risks
                    // qBittorrent's failed-auth IP ban on flaky links). A session that
                    // died server-side self-heals via the 401/403 relogin in
                    // executeWithRetry or via markDisconnected() on fetch failures.
                    reusableSession = !force && existing.connected && existing.settings == settings
                    existing.settings = settings
                    existing
                }
            }
        }

        if (reusableSession) {
            return Result.success(Unit)
        }

        return entry.backend.connect(settings)
            .onSuccess { entry.connected = true }
            .onFailure { entry.connected = false }
    }

    suspend fun connect(settings: ConnectionSettings, force: Boolean = false): Result<Unit> {
        val profileId = selectedProfileId
            ?: throw IllegalStateException("No selected server profile.")
        return connect(profileId, settings, force)
    }

    fun clearSession(profileId: String) {
        val removed = sessions.remove(profileId)
        removed?.backend?.clearSession()
        if (selectedProfileId == profileId) {
            selectedProfileId = null
        }
    }

    fun clearSession() {
        val profileId = selectedProfileId ?: return
        clearSession(profileId)
    }

    fun clearAllSessions() {
        sessions.values.forEach { it.backend.clearSession() }
        sessions.clear()
        selectedProfileId = null
    }

    fun removeProfile(profileId: String) {
        clearSession(profileId)
    }

    // -- delegated operations --

    private suspend inline fun <T> withBackend(
        profileId: String? = null,
        crossinline block: suspend TorrentBackend.() -> T,
    ): T {
        val entry = profileId?.let { requireEntry(it) } ?: requireSelectedEntry()
        return entry.backend.block()
    }

    suspend fun fetchTransferInfo() = withBackend { fetchTransferInfo() }
    suspend fun fetchTransferInfo(profileId: String) = withBackend(profileId) { fetchTransferInfo() }

    suspend fun fetchDashboard() = withBackend { fetchDashboard() }
    suspend fun fetchDashboard(profileId: String) = withBackend(profileId) { fetchDashboard() }

    /** One-shot fetch on a throwaway session; for callers with no live repository (background worker). */
    suspend fun fetchDashboardSnapshot(settings: ConnectionSettings): Result<DashboardSnapshotFetchResult> {
        storedPasswordUnreadableError(settings)?.let { return Result.failure(it) }
        return createBackend(settings.serverBackendType).fetchDashboardSnapshot(settings)
    }

    /** Fetch through the profile's persistent session, logging in only if it is not already live. */
    suspend fun fetchDashboardSnapshot(
        profileId: String,
        settings: ConnectionSettings,
    ): Result<DashboardSnapshotFetchResult> {
        return runCatching {
            connect(profileId, settings).getOrThrow()
            DashboardSnapshotFetchResult(
                serverVersion = fetchServerVersion(profileId).getOrElse { "-" },
                dashboardData = fetchDashboard(profileId).getOrThrow(),
            )
        }.onFailure { error ->
            if (error !is CancellationException) markDisconnected(profileId)
        }
    }

    suspend fun pauseTorrent(hash: String) = withBackend { pauseTorrent(hash) }
    suspend fun pauseTorrent(profileId: String, hash: String) = withBackend(profileId) { pauseTorrent(hash) }

    suspend fun resumeTorrent(hash: String) = withBackend { resumeTorrent(hash) }
    suspend fun resumeTorrent(profileId: String, hash: String) = withBackend(profileId) { resumeTorrent(hash) }

    suspend fun deleteTorrent(hash: String, deleteFiles: Boolean) = withBackend { deleteTorrent(hash, deleteFiles) }
    suspend fun deleteTorrent(profileId: String, hash: String, deleteFiles: Boolean) =
        withBackend(profileId) { deleteTorrent(hash, deleteFiles) }

    suspend fun reannounceTorrent(hash: String) = withBackend { reannounceTorrent(hash) }
    suspend fun reannounceTorrent(profileId: String, hash: String) = withBackend(profileId) { reannounceTorrent(hash) }

    suspend fun recheckTorrent(hash: String) = withBackend { recheckTorrent(hash) }
    suspend fun recheckTorrent(profileId: String, hash: String) = withBackend(profileId) { recheckTorrent(hash) }

    suspend fun fetchServerVersion() = withBackend { fetchServerVersion() }
    suspend fun fetchServerVersion(profileId: String) = withBackend(profileId) { fetchServerVersion() }

    suspend fun fetchTorrentDetail(hash: String) = withBackend { fetchTorrentDetail(hash) }
    suspend fun fetchTorrentDetail(profileId: String, hash: String) = withBackend(profileId) { fetchTorrentDetail(hash) }

    suspend fun fetchTorrentTrackers(hash: String) = withBackend { fetchTorrentTrackers(hash) }
    suspend fun fetchTorrentTrackers(profileId: String, hash: String) = withBackend(profileId) { fetchTorrentTrackers(hash) }

    suspend fun addTracker(hash: String, trackerUrl: String) = withBackend { addTracker(hash, trackerUrl) }
    suspend fun addTracker(profileId: String, hash: String, trackerUrl: String) =
        withBackend(profileId) { addTracker(hash, trackerUrl) }

    suspend fun editTracker(hash: String, tracker: TorrentTracker, newUrl: String) =
        withBackend { editTracker(hash, tracker, newUrl) }
    suspend fun editTracker(profileId: String, hash: String, tracker: TorrentTracker, newUrl: String) =
        withBackend(profileId) { editTracker(hash, tracker, newUrl) }

    suspend fun removeTracker(hash: String, tracker: TorrentTracker) = withBackend { removeTracker(hash, tracker) }
    suspend fun removeTracker(profileId: String, hash: String, tracker: TorrentTracker) =
        withBackend(profileId) { removeTracker(hash, tracker) }

    suspend fun exportTorrentFile(hash: String) = withBackend { exportTorrentFile(hash) }
    suspend fun exportTorrentFile(profileId: String, hash: String) = withBackend(profileId) { exportTorrentFile(hash) }

    suspend fun fetchCategoryOptions() = withBackend { fetchCategoryOptions() }
    suspend fun fetchTagOptions() = withBackend { fetchTagOptions() }
    suspend fun fetchCategoryOptions(profileId: String) = withBackend(profileId) { fetchCategoryOptions() }
    suspend fun fetchTagOptions(profileId: String) = withBackend(profileId) { fetchTagOptions() }

    suspend fun fetchCountryPeerSnapshots(hashes: List<String>): Result<CountryPeerSample> = withBackend { fetchCountryPeerSnapshots(hashes) }
    suspend fun fetchCountryPeerSnapshots(profileId: String, hashes: List<String>): Result<CountryPeerSample> =
        withBackend(profileId) { fetchCountryPeerSnapshots(hashes) }

    suspend fun renameTorrent(hash: String, name: String) = withBackend { renameTorrent(hash, name) }
    suspend fun renameTorrent(profileId: String, hash: String, name: String) =
        withBackend(profileId) { renameTorrent(hash, name) }

    suspend fun setTorrentLocation(hash: String, location: String) = withBackend { setTorrentLocation(hash, location) }
    suspend fun setTorrentLocation(profileId: String, hash: String, location: String) =
        withBackend(profileId) { setTorrentLocation(hash, location) }

    suspend fun setTorrentCategory(hash: String, category: String) = withBackend { setTorrentCategory(hash, category) }
    suspend fun setTorrentCategory(profileId: String, hash: String, category: String) =
        withBackend(profileId) { setTorrentCategory(hash, category) }

    suspend fun setTorrentTags(hash: String, oldTags: String, newTags: String) =
        withBackend { setTorrentTags(hash, oldTags, newTags) }
    suspend fun setTorrentTags(profileId: String, hash: String, oldTags: String, newTags: String) =
        withBackend(profileId) { setTorrentTags(hash, oldTags, newTags) }

    suspend fun setTorrentSpeedLimit(hash: String, downloadLimitBytes: Long, uploadLimitBytes: Long) =
        withBackend { setTorrentSpeedLimit(hash, downloadLimitBytes, uploadLimitBytes) }
    suspend fun setTorrentSpeedLimit(
        profileId: String,
        hash: String,
        downloadLimitBytes: Long,
        uploadLimitBytes: Long,
    ) = withBackend(profileId) { setTorrentSpeedLimit(hash, downloadLimitBytes, uploadLimitBytes) }

    suspend fun fetchGlobalSpeedLimits(profileId: String) =
        withBackend(profileId) { fetchGlobalSpeedLimits() }
    suspend fun setGlobalSpeedLimits(profileId: String, limits: GlobalSpeedLimits) =
        withBackend(profileId) { setGlobalSpeedLimits(limits) }
    suspend fun fetchAlternativeSpeedMode(profileId: String) =
        withBackend(profileId) { fetchAlternativeSpeedMode() }
    suspend fun setAlternativeSpeedMode(profileId: String, enabled: Boolean) =
        withBackend(profileId) { setAlternativeSpeedMode(enabled) }

    suspend fun setTorrentShareRatio(hash: String, ratioLimit: Double) =
        withBackend { setTorrentShareRatio(hash, ratioLimit) }
    suspend fun setTorrentShareRatio(profileId: String, hash: String, ratioLimit: Double) =
        withBackend(profileId) { setTorrentShareRatio(hash, ratioLimit) }

    suspend fun fetchTorrentPeers(profileId: String, hash: String) =
        withBackend(profileId) { fetchTorrentPeers(hash) }
    suspend fun setFilePriority(
        profileId: String,
        hash: String,
        fileIndexes: List<Int>,
        priority: com.hjw.qbremote.data.model.FilePriority,
    ) = withBackend(profileId) { setFilePriority(hash, fileIndexes, priority) }

    suspend fun addTorrent(request: AddTorrentRequest) = withBackend { addTorrent(request) }
    suspend fun addTorrent(profileId: String, request: AddTorrentRequest) =
        withBackend(profileId) { addTorrent(request) }

    // -- internal helpers --

    private fun requireSelectedEntry(): SessionEntry {
        val profileId = selectedProfileId
            ?: throw IllegalStateException("No selected server profile.")
        return requireEntry(profileId)
    }

    private fun requireEntry(profileId: String): SessionEntry {
        return sessions[profileId]
            ?: throw IllegalStateException("Server session is not connected.")
    }

    // Fail fast instead of logging in with "": an empty-password login would fail
    // with a misleading "wrong password" and repeated attempts can trip qB's IP ban.
    private fun storedPasswordUnreadableError(settings: ConnectionSettings): Throwable? {
        if (!settings.passwordUnreadable || settings.password.isNotEmpty()) return null
        return BackendConnectionError.StoredPasswordUnreadable(settings.serverBackendType)
    }

    /** Logs in on a throwaway session and returns the server version (settings form "Test"). */
    suspend fun testConnection(settings: ConnectionSettings): Result<String> {
        storedPasswordUnreadableError(settings)?.let { return Result.failure(it) }
        val backend = createBackend(settings.serverBackendType)
        return try {
            // A test should answer quickly: the normal connect path retries every
            // candidate URL with 8 s timeouts, which made an unreachable host take ~30 s.
            withTimeoutOrNull(CONNECTION_TEST_TIMEOUT_MS) {
                backend.connect(settings).map { backend.fetchServerVersion().getOrElse { "-" } }
            } ?: Result.failure(BackendConnectionError.TestTimedOut(CONNECTION_TEST_TIMEOUT_MS / 1000))
        } finally {
            backend.clearSession()
        }
    }

    private fun createBackend(type: ServerBackendType): TorrentBackend {
        return when (type) {
            ServerBackendType.QBITTORRENT -> QbRepository()
            ServerBackendType.TRANSMISSION -> TransmissionBackend()
        }
    }

    private companion object {
        const val CONNECTION_TEST_TIMEOUT_MS = 12_000L
    }
}
