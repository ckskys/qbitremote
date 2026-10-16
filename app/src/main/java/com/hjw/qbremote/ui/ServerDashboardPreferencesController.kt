package com.hjw.qbremote.ui

import com.hjw.qbremote.data.ConnectionSettings
import com.hjw.qbremote.data.ConnectionStore
import com.hjw.qbremote.data.ServerDashboardPreferences
import com.hjw.qbremote.data.defaultServerDashboardPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Per-server dashboard card visibility / order / reset. Split out of MainViewModel;
 * every change persists through ConnectionStore and is mirrored into ui state.
 */
internal class ServerDashboardPreferencesController(
    private val uiState: MutableStateFlow<MainUiState>,
    private val scope: CoroutineScope,
    private val connectionStore: ConnectionStore,
    private val resolveProfileSettings: suspend (String) -> ConnectionSettings?,
) {
    fun setCardVisibility(
        profileId: String,
        card: DashboardChartCard,
        visible: Boolean,
        onComplete: (Boolean) -> Unit,
    ) = setCardsVisibility(profileId, listOf(card), visible, failureMessage = "更新图表显示失败", onComplete)

    fun setCardsVisibility(
        profileId: String,
        cards: List<DashboardChartCard>,
        visible: Boolean,
        // Bulk changes (e.g. undo of a hide) fail quietly; single toggles report the error.
        failureMessage: String? = null,
        onComplete: (Boolean) -> Unit,
    ) {
        val normalizedCards = cards.distinct()
        if (normalizedCards.isEmpty()) {
            if (profileId.isNotBlank()) onComplete(true)
            return
        }
        persist(profileId, failureMessage, onComplete) { _ ->
            { current ->
                val visibleCards = current.visibleCards.toMutableList()
                normalizedCards.forEach { card ->
                    if (visible) {
                        if (card.storageKey !in visibleCards) visibleCards += card.storageKey
                    } else {
                        visibleCards.remove(card.storageKey)
                    }
                }
                current.copy(visibleCards = visibleCards.distinct())
            }
        }
    }

    fun setCardOrder(
        profileId: String,
        order: List<DashboardChartCard>,
        onComplete: (Boolean) -> Unit,
    ) = persist(profileId, failureMessage = "更新图表排序失败", onComplete) { _ ->
        { current -> current.copy(cardOrder = order.joinToString(",") { it.storageKey }) }
    }

    /** Back to the same defaults a newly added server gets. */
    fun reset(
        profileId: String,
        onComplete: (Boolean) -> Unit,
    ) = persist(profileId, failureMessage = "恢复图表设置失败", onComplete) { settings ->
        { _ -> defaultServerDashboardPreferences(settings) }
    }

    private fun persist(
        profileId: String,
        failureMessage: String?,
        onComplete: (Boolean) -> Unit,
        buildTransform: (ConnectionSettings) -> (ServerDashboardPreferences) -> ServerDashboardPreferences,
    ) {
        if (profileId.isBlank()) return
        scope.launch {
            val fallbackSettings = resolveProfileSettings(profileId) ?: uiState.value.settings
            runCatching {
                connectionStore.updateServerDashboardPreferences(
                    profileId,
                    fallbackSettings,
                    buildTransform(fallbackSettings),
                )
            }.onSuccess { preferences ->
                uiState.update { current ->
                    current.copy(serverDashboardPreferences = current.serverDashboardPreferences + (profileId to preferences))
                }
                onComplete(true)
            }.onFailure { error ->
                if (failureMessage != null) {
                    uiState.update { current ->
                        current.copy(errorMessage = UiMessage.Text(error.message ?: failureMessage))
                    }
                }
                onComplete(false)
            }
        }
    }
}
