package com.hjw.qbremote.ui

import com.hjw.qbremote.data.model.TorrentInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tapping a status pill opens the list filtered by it; both must count the same torrents. */
class DashboardStatePillFilterTest {

    private val torrents = listOf(
        // qBittorrent 4.x and 5.x spellings, plus Transmission's mapped states.
        "uploading", "stalledUP", "forcedUP", "queuedUP", "pausedUP", "stoppedUP",
        "downloading", "stalledDL", "metaDL", "forcedDL", "queuedDL", "pausedDL", "stoppedDL",
        "checkingUP", "checkingDL", "checkingResumeData", "checkUP", "moving",
        "error", "missingFiles", "allocating",
    ).mapIndexed { index, state ->
        TorrentInfo(hash = "h$index", state = state, progress = if (state.endsWith("UP")) 1f else 0.5f)
    }

    private fun listCount(filter: TorrentStateFilter) =
        buildTorrentListDisplayState(
            torrents,
            TorrentListFilterState(
                stateFilters = if (filter == TorrentStateFilter.ALL) emptySet() else setOf(filter),
            ),
        ).visibleTorrentItems.size

    @Test
    fun pillCountsMatchTheFilteredList() {
        val summary = buildDashboardStateSummary(torrents)
        assertEquals(summary.uploadingCount, listCount(TorrentStateFilter.UPLOADING))
        assertEquals(summary.downloadingCount, listCount(TorrentStateFilter.DOWNLOADING))
        assertEquals(summary.pausedCount, listCount(TorrentStateFilter.PAUSED))
        assertEquals(summary.errorCount, listCount(TorrentStateFilter.ERROR))
        assertEquals(summary.checkingCount, listCount(TorrentStateFilter.CHECKING))
        assertEquals(summary.waitingCount, listCount(TorrentStateFilter.WAITING))
        assertEquals(torrents.size, listCount(TorrentStateFilter.ALL))
    }

    @Test
    fun qbittorrent5StoppedStatesCountAsPaused() {
        val summary = buildDashboardStateSummary(torrents)
        assertEquals(4, summary.pausedCount)
        assertEquals(4, summary.checkingCount)
        // stalledDL used to be missed by the list's "Downloading" filter (typo "stalldl").
        assertEquals(6, summary.downloadingCount)
    }

    /** Selecting several states OR-s them, and the pill row's count is a subset of it. */
    @Test
    fun multipleSelectedStatesAreOredTogether() {
        val summary = buildDashboardStateSummary(torrents)
        val both = buildTorrentListDisplayState(
            torrents,
            TorrentListFilterState(
                stateFilters = setOf(TorrentStateFilter.DOWNLOADING, TorrentStateFilter.ERROR),
            ),
        ).visibleTorrentItems.size

        assertEquals(summary.downloadingCount + summary.errorCount, both)
    }
}
