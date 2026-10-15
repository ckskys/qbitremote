package com.hjw.qbremote.ui

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
internal class GlobalSpeedLimitController(
    private val uiState: MutableStateFlow<MainUiState>,
    private val scope: CoroutineScope,
    private val repository: TorrentRepository,
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
