package com.hjw.qbremote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hjw.qbremote.data.CachedDashboardServerSnapshot
import com.hjw.qbremote.data.model.TorrentInfo

internal data class DashboardStateSummary(
    val uploadingCount: Int = 0,
    val downloadingCount: Int = 0,
    val pausedCount: Int = 0,
    val errorCount: Int = 0,
    val checkingCount: Int = 0,
    val waitingCount: Int = 0,
)

/**
 * The status groups shown as pills on the server card. The torrent list filters
 * by the same groups ([matchesStateFilter]), so a pill's count always equals the
 * number of torrents the list shows after tapping it.
 */
internal enum class DashboardStateBucket {
    UPLOADING,
    DOWNLOADING,
    PAUSED,
    ERROR,
    CHECKING,
    WAITING,
}

internal fun dashboardStateBucket(torrent: TorrentInfo): DashboardStateBucket? {
    return when (normalizeTorrentState(effectiveTorrentState(torrent))) {
        "uploading", "forcedup", "stalledup" -> DashboardStateBucket.UPLOADING
        "downloading", "forceddl", "stalleddl", "metadl", "forcedmetadl", "allocating", "moving" ->
            DashboardStateBucket.DOWNLOADING
        "pausedup", "stoppedup", "pauseddl", "stoppeddl" -> DashboardStateBucket.PAUSED
        "error", "missingfiles" -> DashboardStateBucket.ERROR
        // "checkup" is what Transmission's verify states map to.
        "checking", "checkingdl", "checkingup", "checkingresumedata", "checkup" -> DashboardStateBucket.CHECKING
        "queueddl", "queuedup" -> DashboardStateBucket.WAITING
        else -> null
    }
}

internal data class DashboardStatusPillItem(
    val label: String,
    val count: Int,
    val accentColor: Color,
    /** List filter opened by tapping the pill. */
    val filter: TorrentStateFilter,
)

internal val HomeServerStackExpandedCardHeight = 210.dp
internal val HomeServerStackCollapsedCardHeight = 90.dp
internal val HomeServerStackExposedHeight = 60.dp
internal val DashboardCardSpacing = 10.dp
internal const val ServerCardClickSuppressionWindowMs = 140L
internal val ServerCardClickSuppressionDragThreshold = 6.dp

internal fun resolveServerCardClickSuppressionTimestamp(
    dragDistanceSinceStart: Float,
    clickSuppressionThresholdPx: Float,
    currentTimeMillis: Long,
): Long {
    return if (dragDistanceSinceStart >= clickSuppressionThresholdPx) {
        currentTimeMillis
    } else {
        0L
    }
}

internal fun shouldSuppressServerCardClick(
    lastDragFinishedAt: Long,
    currentTimeMillis: Long,
    suppressionWindowMs: Long = ServerCardClickSuppressionWindowMs,
): Boolean {
    if (lastDragFinishedAt <= 0L) return false
    return currentTimeMillis - lastDragFinishedAt <= suppressionWindowMs
}

internal enum class ServerSnapshotStatus { LIVE, CACHED_OFFLINE, CONNECTING, UNREACHABLE }

/**
 * "Cached offline" only when there is cached data to show. A server that has never
 * delivered a snapshot is "connecting" until its first attempt fails, then
 * "unreachable" (it used to say "cached offline" with nothing cached).
 */
internal fun resolveServerSnapshotStatus(snapshot: CachedDashboardServerSnapshot): ServerSnapshotStatus {
    return when {
        !snapshot.isStale -> ServerSnapshotStatus.LIVE
        snapshot.lastUpdatedAt > 0L -> ServerSnapshotStatus.CACHED_OFFLINE
        snapshot.errorMessage.isBlank() -> ServerSnapshotStatus.CONNECTING
        else -> ServerSnapshotStatus.UNREACHABLE
    }
}

internal data class WalletServerStackCardPresentation(
    val showExpandedLayout: Boolean,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val borderAlpha: Float,
)

internal fun resolveWalletServerStackCardPresentation(
    selected: Boolean,
    isDragging: Boolean,
    isSettling: Boolean,
): WalletServerStackCardPresentation {
    // Expanded only for the (projected) front card. A dragged/settling card used to
    // expand too, but a tall lifted card covers every collapsed sibling it passes
    // and then has to snap back to a header at drop time.
    val showExpandedLayout = selected
    return WalletServerStackCardPresentation(
        showExpandedLayout = showExpandedLayout,
        horizontalPadding = if (showExpandedLayout) 16.dp else 15.dp,
        verticalPadding = if (showExpandedLayout) 14.dp else 10.dp,
        borderAlpha = if (showExpandedLayout) 0.28f else 0.14f,
    )
}

internal fun buildDashboardStateSummary(torrents: List<TorrentInfo>): DashboardStateSummary {
    if (torrents.isEmpty()) return DashboardStateSummary()

    val counts = IntArray(DashboardStateBucket.entries.size)
    torrents.forEach { torrent ->
        dashboardStateBucket(torrent)?.let { counts[it.ordinal]++ }
    }
    return DashboardStateSummary(
        uploadingCount = counts[DashboardStateBucket.UPLOADING.ordinal],
        downloadingCount = counts[DashboardStateBucket.DOWNLOADING.ordinal],
        pausedCount = counts[DashboardStateBucket.PAUSED.ordinal],
        errorCount = counts[DashboardStateBucket.ERROR.ordinal],
        checkingCount = counts[DashboardStateBucket.CHECKING.ordinal],
        waitingCount = counts[DashboardStateBucket.WAITING.ordinal],
    )
}
