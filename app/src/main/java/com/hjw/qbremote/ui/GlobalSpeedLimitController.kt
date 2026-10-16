package com.hjw.qbremote.ui

import com.hjw.qbremote.data.model.TransferInfo
import com.hjw.qbremote.data.GlobalSpeedLimits
import com.hjw.qbremote.data.TorrentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Global (server-wide) speed-limit dialog: open, switch server, load, save. Split out of MainViewModel. */
private const val ALT_MODE_PARALLELISM = 4

internal class GlobalSpeedLimitController(
    private val uiState: MutableStateFlow<MainUiState>,
    private val scope: CoroutineScope,
    private val repository: TorrentRepository,
    // Re-poll the servers right away so cards showing the active limits update
    // with the switch instead of on the next refresh tick.
    private val onLimitModeChanged: () -> Unit = {},
) {
    fun openGlobalSpeedLimitDialog() {
        val state = uiState.value
        val profileId = state.activeServerProfileId?.trim().orEmpty()
            .ifBlank { state.serverProfiles.firstOrNull()?.id?.trim().orEmpty() }
        if (profileId.isBlank()) return
        uiState.update { current ->
            current.copy(
                globalSpeedLimitDialogVisible = true,
                globalSpeedLimitProfileId = profileId,
                globalSpeedLimits = null,
                globalSpeedLimitLoadFailed = false,
            )
        }
        loadGlobalSpeedLimits(profileId)
    }

    fun selectGlobalSpeedLimitProfile(profileId: String) {
        val normalizedProfileId = profileId.trim()
        if (normalizedProfileId.isBlank()) return
        if (uiState.value.globalSpeedLimitProfileId == normalizedProfileId) return
        uiState.update { current ->
            current.copy(
                globalSpeedLimitProfileId = normalizedProfileId,
                globalSpeedLimits = null,
                globalSpeedLimitLoadFailed = false,
            )
        }
        loadGlobalSpeedLimits(normalizedProfileId)
    }

    fun retryGlobalSpeedLimitLoad() {
        val profileId = uiState.value.globalSpeedLimitProfileId
        if (profileId.isBlank()) return
        loadGlobalSpeedLimits(profileId)
    }

    fun dismissGlobalSpeedLimitDialog() {
        uiState.update { current ->
            current.copy(
                globalSpeedLimitDialogVisible = false,
                globalSpeedLimitProfileId = "",
                globalSpeedLimits = null,
                globalSpeedLimitLoading = false,
                globalSpeedLimitSaving = false,
                globalSpeedLimitLoadFailed = false,
            )
        }
    }

    fun saveGlobalSpeedLimits(limits: GlobalSpeedLimits) {
        val profileId = uiState.value.globalSpeedLimitProfileId
        if (profileId.isBlank()) return
        scope.launch {
            uiState.update { it.copy(globalSpeedLimitSaving = true) }
            runCatching {
                repository.setGlobalSpeedLimits(profileId, limits).getOrThrow()
                val readback = repository.fetchGlobalSpeedLimits(profileId).getOrNull()
                if (readback != null && !limits.hasSameConfiguredLimits(readback)) {
                    error("全局限速保存未生效")
                }
            }.onSuccess {
                uiState.update { it.copy(globalSpeedLimitSaving = false) }
                dismissGlobalSpeedLimitDialog()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                uiState.update {
                    it.copy(
                        globalSpeedLimitSaving = false,
                        errorMessage = UiMessage.Text(error.message ?: "保存全局限速失败"),
                    )
                }
            }
        }
    }

    /** Switch normal/alternative limits right away (independent of the Save button). */
    fun setAlternativeSpeedMode(enabled: Boolean) {
        val profileId = uiState.value.globalSpeedLimitProfileId
        if (profileId.isBlank()) return
        val previous = uiState.value.globalSpeedLimits
        // Optimistic: flip the selector immediately, revert if the server refuses.
        uiState.update { current ->
            if (current.globalSpeedLimitProfileId != profileId) {
                current
            } else {
                current.copy(globalSpeedLimits = current.globalSpeedLimits?.copy(alternativeModeEnabled = enabled))
            }
        }
        scope.launch {
            runCatching {
                repository.setAlternativeSpeedMode(profileId, enabled).getOrThrow()
                repository.fetchGlobalSpeedLimits(profileId).getOrNull()
            }.onSuccess { readback ->
                val confirmed = readback?.alternativeModeEnabled ?: enabled
                val limits = fetchEffectiveLimits(listOf(profileId))
                uiState.update {
                    it.withEffectiveLimits(limits).copy(altSpeedModes = it.altSpeedModes + (profileId to confirmed))
                }
                onLimitModeChanged()
                if (readback != null) {
                    uiState.update { current ->
                        if (current.globalSpeedLimitProfileId != profileId) {
                            current
                        } else {
                            // Only the mode changed on the server; keep any unsaved edits elsewhere intact.
                            current.copy(
                                globalSpeedLimits = current.globalSpeedLimits
                                    ?.copy(alternativeModeEnabled = readback.alternativeModeEnabled),
                            )
                        }
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                uiState.update { current ->
                    val reverted = if (current.globalSpeedLimitProfileId == profileId) previous else current.globalSpeedLimits
                    current.copy(
                        globalSpeedLimits = reverted,
                        errorMessage = UiMessage.Text(error.message ?: "切换限速模式失败"),
                    )
                }
            }
        }
    }

    /** Re-read the mode of every given server; unreachable ones drop out of the map. */
    fun refreshAltSpeedModes(profileIds: List<String>) {
        val ids = profileIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        scope.launch {
            val results = supervisorScope {
                val gate = Semaphore(ALT_MODE_PARALLELISM)
                ids.map { id ->
                    // runCatching: a profile without a live session throws instead of returning a failure.
                    async { id to gate.withPermit { runCatching { repository.fetchAlternativeSpeedMode(id).getOrNull() }.getOrNull() } }
                }.awaitAll()
            }
            uiState.update { current ->
                val known = current.serverProfiles.map { it.id }.toSet()
                val next = current.altSpeedModes.toMutableMap()
                results.forEach { (id, mode) ->
                    // Never overwrite a server that is mid-switch with a stale read.
                    if (id in current.altSpeedTogglingIds) return@forEach
                    if (mode == null) next.remove(id) else next[id] = mode
                }
                // The caller asks every server; ids it no longer lists were removed.
                val keep = ids.toSet() + current.altSpeedTogglingIds
                current.copy(altSpeedModes = next.filterKeys { it in known && it in keep })
            }
        }
    }

    /** One tap on the home card: if any server still uses normal limits, switch all to alternative; else back. */
    fun toggleAltSpeedForAll() {
        val modes = uiState.value.altSpeedModes
        if (modes.isEmpty()) return
        val target = !modes.values.all { it }
        switchAltSpeed(modes.keys.toList(), target)
    }

    fun toggleAltSpeed(profileId: String) {
        val current = uiState.value.altSpeedModes[profileId] ?: return
        switchAltSpeed(listOf(profileId), !current)
    }

    private fun switchAltSpeed(profileIds: List<String>, enabled: Boolean) {
        val ids = profileIds.filter { it !in uiState.value.altSpeedTogglingIds }
        if (ids.isEmpty()) return
        uiState.update { it.copy(altSpeedTogglingIds = it.altSpeedTogglingIds + ids) }
        scope.launch {
            val results = supervisorScope {
                val gate = Semaphore(ALT_MODE_PARALLELISM)
                ids.map { id ->
                    async {
                        id to gate.withPermit {
                            runCatching {
                                repository.setAlternativeSpeedMode(id, enabled).isSuccess &&
                                    repository.fetchAlternativeSpeedMode(id).getOrNull() == enabled
                            }.getOrDefault(false)
                        }
                    }
                }.awaitAll()
            }
            val failed = results.count { !it.second }
            val switched = results.filter { it.second }.map { it.first }
            val limits = fetchEffectiveLimits(switched)
            uiState.update { current ->
                val next = current.altSpeedModes.toMutableMap()
                results.forEach { (id, ok) -> if (ok) next[id] = enabled }
                current.withEffectiveLimits(limits).copy(
                    altSpeedModes = next,
                    altSpeedTogglingIds = current.altSpeedTogglingIds - ids.toSet(),
                    errorMessage = if (failed > 0) {
                        UiMessage.Text("限速模式切换失败：${failed}/${ids.size} 台服务器未生效")
                    } else {
                        current.errorMessage
                    },
                )
            }
            if (failed < ids.size) onLimitModeChanged()
            if (failed > 0) refreshAltSpeedModes(ids)
        }
    }

    /**
     * The limits the server applies right now (one cheap transfer-info read each),
     * so the cards change in the same frame as the turtle instead of on the next
     * full refresh, which polls every server and can take several seconds.
     */
    private suspend fun fetchEffectiveLimits(profileIds: List<String>): Map<String, Pair<Long, Long>> {
        if (profileIds.isEmpty()) return emptyMap()
        return supervisorScope {
            val gate = Semaphore(ALT_MODE_PARALLELISM)
            profileIds.map { id ->
                async {
                    gate.withPermit {
                        runCatching { repository.fetchTransferInfo(id).getOrNull() }.getOrNull()
                            ?.let { id to (it.downloadRateLimit to it.uploadRateLimit) }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    private fun MainUiState.withEffectiveLimits(limits: Map<String, Pair<Long, Long>>): MainUiState {
        if (limits.isEmpty()) return this
        fun TransferInfo.patched(id: String) = limits[id]?.let { (down, up) ->
            copy(downloadRateLimit = down, uploadRateLimit = up)
        } ?: this
        val active = activeServerProfileId
        return copy(
            dashboardServerSnapshots = dashboardServerSnapshots.map { snapshot ->
                if (snapshot.profileId in limits) {
                    snapshot.copy(transferInfo = snapshot.transferInfo.patched(snapshot.profileId))
                } else {
                    snapshot
                }
            },
            transferInfo = if (active != null && active in limits) transferInfo.patched(active) else transferInfo,
        )
    }

    private fun loadGlobalSpeedLimits(profileId: String) {
        scope.launch {
            uiState.update {
                it.copy(
                    globalSpeedLimitLoading = true,
                    globalSpeedLimitLoadFailed = false,
                )
            }
            repository.fetchGlobalSpeedLimits(profileId)
                .onSuccess { limits ->
                    uiState.update { current ->
                        if (current.globalSpeedLimitProfileId != profileId) {
                            current
                        } else {
                            current.copy(
                                globalSpeedLimits = limits,
                                globalSpeedLimitLoading = false,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    uiState.update { current ->
                        if (current.globalSpeedLimitProfileId != profileId) {
                            current
                        } else {
                            current.copy(
                                globalSpeedLimitLoading = false,
                                globalSpeedLimitLoadFailed = true,
                            )
                        }
                    }
                }
        }
    }
}
