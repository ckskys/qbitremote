package com.hjw.qbremote.ui

import com.hjw.qbremote.data.model.TorrentInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentListEmptyStateSupportTest {

    @Test
    fun buildTorrentListDisplayState_marksNoTorrentsWhenBaseListIsEmpty() {
        val displayState = buildTorrentListDisplayState(
            torrents = emptyList(),
            filterState = TorrentListFilterState(),
        )

        assertEquals(TorrentListEmptyState.NO_TORRENTS, displayState.emptyState)
    }

    @Test
    fun buildTorrentListDisplayState_marksSearchNoResultsWhenOnlyQueryFiltersEverything() {
        val displayState = buildTorrentListDisplayState(
            torrents = listOf(TorrentInfo(hash = "a", name = "Ubuntu")),
            filterState = TorrentListFilterState(query = "debian"),
        )

        assertEquals(TorrentListEmptyState.SEARCH_NO_RESULTS, displayState.emptyState)
    }

    @Test
    fun buildTorrentListDisplayState_marksFilterNoResultsWhenOnlyFiltersHideEverything() {
        val displayState = buildTorrentListDisplayState(
            torrents = listOf(TorrentInfo(hash = "a", name = "Ubuntu", state = "pauseddl", progress = 0.1f)),
            filterState = TorrentListFilterState(stateFilters = setOf(TorrentStateFilter.SEEDING)),
        )

        assertEquals(TorrentListEmptyState.FILTER_NO_RESULTS, displayState.emptyState)
    }

    @Test
    fun buildTorrentListDisplayState_marksSearchFilterNoResultsWhenBothAreActive() {
        val displayState = buildTorrentListDisplayState(
            torrents = listOf(
                TorrentInfo(hash = "a", name = "Ubuntu", category = "linux", state = "downloading"),
            ),
            filterState = TorrentListFilterState(
                query = "debian",
                categoryFilters = setOf("movies"),
            ),
        )

        assertEquals(TorrentListEmptyState.SEARCH_FILTER_NO_RESULTS, displayState.emptyState)
    }

    /** A tracker-only selection is a filter too: the empty state must not report "no results". */
    @Test
    fun buildTorrentListDisplayState_countsTrackerFiltersAsActiveFilters() {
        val displayState = buildTorrentListDisplayState(
            torrents = listOf(
                TorrentInfo(hash = "a", name = "Ubuntu", tracker = "https://tracker.example.org/announce"),
            ),
            filterState = TorrentListFilterState(trackerFilters = setOf("other.example.net")),
        )

        assertEquals(TorrentListEmptyState.FILTER_NO_RESULTS, displayState.emptyState)
    }

    @Test
    fun clearTorrentListFilters_preservesQueryAndSortState() {
        val current = TorrentListFilterState(
            query = "ubuntu",
            sortOption = TorrentListSortOption.ADDED_TIME,
            descending = false,
            stateFilters = setOf(TorrentStateFilter.ERROR),
            categoryFilters = setOf("linux"),
            tagFilters = setOf("iso"),
            trackerFilters = setOf("tracker.example.org"),
        )

        assertEquals(
            TorrentListFilterState(
                query = "ubuntu",
                sortOption = TorrentListSortOption.ADDED_TIME,
                descending = false,
            ),
            clearTorrentListFilters(current),
        )
    }

    @Test
    fun emptySectionSetsAreNotActiveFilters() {
        assertFalse(hasActiveTorrentListFilters(TorrentListFilterState(query = "debian")))
        assertFalse(hasActiveTorrentListFilters(TorrentListFilterState()))
        assertTrue(hasActiveTorrentListFilters(TorrentListFilterState(categoryFilters = setOf("linux"))))
        assertTrue(hasActiveTorrentListFilters(TorrentListFilterState(tagFilters = setOf("iso"))))
        assertTrue(
            hasActiveTorrentListFilters(
                TorrentListFilterState(trackerFilters = setOf("tracker.example.org")),
            ),
        )
    }
}
