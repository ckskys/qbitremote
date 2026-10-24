package com.hjw.qbremote.ui

import com.hjw.qbremote.R
import com.hjw.qbremote.data.model.TorrentInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentListFilterSupportTest {

    private fun torrent(
        hash: String,
        state: String = "downloading",
        progress: Float = 0.5f,
        category: String = "",
        tags: String = "",
        tracker: String = "",
        name: String = hash,
    ) = TorrentInfo(
        hash = hash,
        name = name,
        state = state,
        progress = progress,
        category = category,
        tags = tags,
        tracker = tracker,
    )

    // -----------------------------------------------------------------------
    // Section aggregation
    // -----------------------------------------------------------------------

    @Test
    fun stateOptionsSkipAllAndPillOnlyStatesUnlessSelected() {
        val options = buildTorrentFilterPanelState(torrents = listOf(torrent("a")), filterState = TorrentListFilterState())
            .states

        val keys = options.options.map { option -> option.key }
        assertFalse(TorrentStateFilter.ALL.name in keys)
        assertFalse(TorrentStateFilter.UPLOADING.name in keys)
        assertFalse(TorrentStateFilter.WAITING.name in keys)
        assertTrue(TorrentStateFilter.DOWNLOADING.name in keys)
        assertTrue(TorrentStateFilter.SEEDING.name in keys)
        assertTrue(options.allSelected)
        assertEquals(1, options.allCount)
    }

    @Test
    fun selectedPillOnlyStateBecomesAVisibleOption() {
        val options = buildTorrentFilterPanelState(
            torrents = listOf(torrent("a", state = "uploading", progress = 1f)),
            filterState = TorrentListFilterState(stateFilters = setOf(TorrentStateFilter.UPLOADING)),
        ).states

        val uploading = options.options.first { option -> option.key == TorrentStateFilter.UPLOADING.name }
        assertTrue(uploading.selected)
        assertEquals(R.string.status_uploading, uploading.labelRes)
        assertEquals(1, uploading.count)
        assertFalse(options.allSelected)
        // Order follows the enum, so Uploading sits before Seeding.
        val keys = options.options.map { option -> option.key }
        assertTrue(
            keys.indexOf(TorrentStateFilter.UPLOADING.name) <
                keys.indexOf(TorrentStateFilter.SEEDING.name),
        )
    }

    @Test
    fun categoriesAggregateUncategorizedAsBlankKeyAndDedupeCaseInsensitively() {
        val options = buildTorrentFilterPanelState(
            torrents = listOf(
                torrent("a", category = "Movies"),
                torrent("b", category = "  movies "),
                torrent("c", category = ""),
            ),
            filterState = TorrentListFilterState(),
        ).categories

        assertEquals(listOf("movies", ""), options.options.map { option -> option.key })
        assertEquals("Movies", options.options.first().label)
        assertEquals(2, options.options.first().count)
        assertEquals(R.string.torrent_filter_uncategorized, options.options.last().labelRes)
        assertEquals(1, options.options.last().count)
        assertEquals(3, options.allCount)
    }

    @Test
    fun tagsSplitOnEverySeparatorAndKeepFirstDisplayForm() {
        val options = buildTorrentFilterPanelState(
            torrents = listOf(
                torrent("a", tags = "ISO;linux, Movie "),
                torrent("b", tags = "iso|MOVIE"),
                torrent("c", tags = ""),
            ),
            filterState = TorrentListFilterState(),
        ).tags

        assertEquals(listOf("iso", "linux", "movie"), options.options.map { option -> option.key })
        assertEquals(listOf("ISO", "linux", "Movie"), options.options.map { option -> option.label })
        assertEquals(listOf(2, 1, 2), options.options.map { option -> option.count })
    }

    @Test
    fun trackersAggregateHostsAndTheNoTrackerRow() {
        val options = buildTorrentFilterPanelState(
            torrents = listOf(
                torrent("a", tracker = "https://tracker.example.org:443/announce?passkey=x"),
                torrent("b", tracker = "udp://tracker.example.org:1337/announce"),
                torrent("c", tracker = "http://open.tracker.net/announce"),
                torrent("d", tracker = ""),
            ),
            filterState = TorrentListFilterState(),
        ).trackers

        assertEquals(
            listOf("open.tracker.net", "tracker.example.org", ""),
            options.options.map { option -> option.key },
        )
        assertEquals("tracker.example.org", options.options[1].label)
        assertEquals(2, options.options[1].count)
        assertEquals(R.string.torrent_filter_no_tracker, options.options.last().labelRes)
        assertEquals(1, options.options.last().count)
    }

    @Test
    fun trackerHostParsingFallsBackToRawTextWhenTheUrlIsNotParseable() {
        assertEquals("tracker.example.org", torrentTrackerFilterKey("https://tracker.example.org/announce"))
        assertEquals("tracker.example.org", torrentTrackerFilterKey("udp://tracker.example.org:1337"))
        assertEquals("tracker.example.org", torrentTrackerFilterKey("tracker.example.org/announce"))
        assertEquals("weird tracker", torrentTrackerFilterKey("  weird tracker  "))
        assertEquals("", torrentTrackerFilterKey("   "))
    }

    @Test
    fun selectedButVanishedValuesStayListedWithZeroCount() {
        val options = buildTorrentFilterPanelState(
            torrents = listOf(torrent("a", category = "linux")),
            filterState = TorrentListFilterState(categoryFilters = setOf("deleted-category")),
        ).categories

        val vanished = options.options.first { option -> option.key == "deleted-category" }
        assertTrue(vanished.selected)
        assertEquals(0, vanished.count)
        assertFalse(options.allSelected)
    }

    // -----------------------------------------------------------------------
    // Counts respect the other sections plus the query
    // -----------------------------------------------------------------------

    @Test
    fun optionCountsIgnoreTheirOwnSectionButRespectTheOtherSections() {
        val torrents = listOf(
            torrent("a", category = "movies", tags = "iso", state = "downloading"),
            torrent("b", category = "movies", tags = "iso", state = "uploading", progress = 1f),
            torrent("c", category = "linux", tags = "iso", state = "downloading"),
        )
        val panel = buildTorrentFilterPanelState(
            torrents = torrents,
            filterState = TorrentListFilterState(tagFilters = setOf("iso")),
        )

        // Category counts see the selected tag but not the (empty) category section.
        val movies = panel.categories.options.first { option -> option.key == "movies" }
        val linux = panel.categories.options.first { option -> option.key == "linux" }
        assertEquals(2, movies.count)
        assertEquals(1, linux.count)

        // State counts also see the selected tag.
        val downloading = panel.states.options.first { option ->
            option.key == TorrentStateFilter.DOWNLOADING.name
        }
        assertEquals(2, downloading.count)
    }

    @Test
    fun optionCountsRespectTheSearchQuery() {
        val torrents = listOf(
            torrent("a", name = "Ubuntu ISO", category = "linux"),
            torrent("b", name = "Debian ISO", category = "linux"),
        )
        val panel = buildTorrentFilterPanelState(
            torrents = torrents,
            filterState = TorrentListFilterState(query = "ubuntu"),
        )

        assertEquals(1, panel.categories.options.single().count)
        assertEquals(1, panel.categories.allCount)
        assertEquals(1, panel.visibleCount)
    }

    @Test
    fun allCountIgnoresTheOwnSectionOnly() {
        val torrents = listOf(
            torrent("a", category = "movies", state = "downloading"),
            torrent("b", category = "linux", state = "downloading"),
            torrent("c", category = "linux", state = "uploading", progress = 1f),
        )
        val panel = buildTorrentFilterPanelState(
            torrents = torrents,
            filterState = TorrentListFilterState(categoryFilters = setOf("linux")),
        )

        // Category "全部" ignores the category selection -> the whole list.
        assertEquals(3, panel.categories.allCount)
        // State "全部" keeps the category selection -> only the linux torrents.
        assertEquals(2, panel.states.allCount)
        assertEquals(2, panel.visibleCount)
    }

    // -----------------------------------------------------------------------
    // Selection semantics
    // -----------------------------------------------------------------------

    @Test
    fun emptySectionSetDoesNotRestrictTheList() {
        val torrents = listOf(
            torrent("a", category = "movies", tags = "iso"),
            torrent("b", category = "linux", tags = "iso"),
        )

        assertEquals(2, applyTorrentListFilters(torrents, TorrentListFilterState()).size)
        assertEquals(
            2,
            applyTorrentListFilters(
                torrents,
                TorrentListFilterState(
                    stateFilters = emptySet(),
                    categoryFilters = emptySet(),
                    tagFilters = emptySet(),
                    trackerFilters = emptySet(),
                ),
            ).size,
        )
    }

    @Test
    fun sameSectionSelectionsAreOredAndSectionsAreAnded() {
        val torrents = listOf(
            torrent("a", category = "movies", tags = "iso"),
            torrent("b", category = "linux", tags = "iso"),
            torrent("c", category = "linux", tags = "movie"),
            torrent("d", category = "other", tags = "iso"),
        )
        val state = TorrentListFilterState(
            categoryFilters = setOf("movies", "linux"),
            tagFilters = setOf("iso"),
        )

        assertEquals(listOf("a", "b"), applyTorrentListFilters(torrents, state).map { it.hash })
    }

    @Test
    fun trackerFilterMatchesTheParsedHostIgnoringCase() {
        val torrents = listOf(
            torrent("a", tracker = "https://Tracker.Example.org/announce"),
            torrent("b", tracker = ""),
        )

        assertEquals(
            listOf("a"),
            applyTorrentListFilters(
                torrents,
                TorrentListFilterState(trackerFilters = setOf("tracker.example.org")),
            ).map { it.hash },
        )
        assertEquals(
            listOf("b"),
            applyTorrentListFilters(torrents, TorrentListFilterState(trackerFilters = setOf("")))
                .map { it.hash },
        )
    }

    @Test
    fun uncategorizedSelectionMatchesOnlyBlankCategories() {
        val torrents = listOf(
            torrent("a", category = "  "),
            torrent("b", category = "linux"),
        )

        assertEquals(
            listOf("a"),
            applyTorrentListFilters(torrents, TorrentListFilterState(categoryFilters = setOf("")))
                .map { it.hash },
        )
    }

    // -----------------------------------------------------------------------
    // Toggle / clear helpers
    // -----------------------------------------------------------------------

    @Test
    fun togglingAValueAddsThenRemovesIt() {
        val start = TorrentListFilterState()
        val added = toggleTorrentCategoryFilter(start, "Movies")
        assertEquals(setOf("movies"), added.categoryFilters)
        assertEquals(start, toggleTorrentCategoryFilter(added, "Movies"))
    }

    @Test
    fun togglingAStateAllRowClearsTheSection() {
        val state = TorrentListFilterState(
            stateFilters = setOf(TorrentStateFilter.SEEDING, TorrentStateFilter.ERROR),
        )

        assertEquals(emptySet<TorrentStateFilter>(), toggleTorrentStateFilter(state, TorrentStateFilter.ALL).stateFilters)
    }

    @Test
    fun togglingAnUnknownStateKeyLeavesTheStateUntouched() {
        val state = TorrentListFilterState(stateFilters = setOf(TorrentStateFilter.SEEDING))

        assertEquals(state, toggleTorrentFilterOption(state, TorrentFilterGroup.STATE, "NOT_A_STATE"))
    }

    @Test
    fun trackerToggleAcceptsUrlsAndSelectsNoTrackerWithBlankInput() {
        val withHost = toggleTorrentTrackerFilter(TorrentListFilterState(), "https://tracker.example.org/announce")
        assertEquals(setOf("tracker.example.org"), withHost.trackerFilters)

        val withNone = toggleTorrentTrackerFilter(TorrentListFilterState(), "")
        assertEquals(setOf(""), withNone.trackerFilters)
    }

    @Test
    fun clearingOneGroupKeepsTheOthers() {
        val state = TorrentListFilterState(
            stateFilters = setOf(TorrentStateFilter.SEEDING),
            categoryFilters = setOf("movies"),
            tagFilters = setOf("iso"),
            trackerFilters = setOf("tracker.example.org"),
        )

        val cleared = clearTorrentFilterGroup(state, TorrentFilterGroup.CATEGORY)
        assertEquals(emptySet<String>(), cleared.categoryFilters)
        assertEquals(state.stateFilters, cleared.stateFilters)
        assertEquals(state.tagFilters, cleared.tagFilters)
        assertEquals(state.trackerFilters, cleared.trackerFilters)
    }

    @Test
    fun selectingTheDashboardPillFilterReplacesTheWholeStateSection() {
        val state = TorrentListFilterState(
            stateFilters = setOf(TorrentStateFilter.SEEDING, TorrentStateFilter.ERROR),
        )

        assertEquals(setOf(TorrentStateFilter.PAUSED), selectTorrentStateFilter(state, TorrentStateFilter.PAUSED).stateFilters)
        assertEquals(emptySet<TorrentStateFilter>(), selectTorrentStateFilter(state, TorrentStateFilter.ALL).stateFilters)
    }

    @Test
    fun selectedCountSumsEverySection() {
        assertEquals(0, torrentFilterSelectedCount(TorrentListFilterState()))
        assertEquals(
            4,
            torrentFilterSelectedCount(
                TorrentListFilterState(
                    stateFilters = setOf(TorrentStateFilter.SEEDING),
                    categoryFilters = setOf("movies"),
                    tagFilters = setOf("iso"),
                    trackerFilters = setOf("tracker.example.org"),
                ),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Summary chips
    // -----------------------------------------------------------------------

    @Test
    fun summaryChipsFollowSectionOrderAndUseDisplayForms() {
        val torrents = listOf(
            torrent(
                "a",
                category = "Movies",
                tags = "ISO",
                tracker = "https://tracker.example.org/announce",
            ),
        )
        val chips = buildTorrentFilterChips(
            torrents = torrents,
            filterState = TorrentListFilterState(
                stateFilters = setOf(TorrentStateFilter.SEEDING),
                categoryFilters = setOf("movies"),
                tagFilters = setOf("iso"),
                trackerFilters = setOf("tracker.example.org"),
            ),
        )

        assertEquals(
            listOf(
                TorrentFilterGroup.STATE,
                TorrentFilterGroup.CATEGORY,
                TorrentFilterGroup.TAG,
                TorrentFilterGroup.TRACKER,
            ),
            chips.map { chip -> chip.group },
        )
        assertEquals(R.string.filter_state_seeding, chips[0].labelRes)
        assertEquals("Movies", chips[1].label)
        assertEquals("ISO", chips[2].label)
        assertEquals("tracker.example.org", chips[3].label)
        assertNull(chips[3].labelRes)
    }

    @Test
    fun summaryChipsLabelTheSyntheticRows() {
        val chips = buildTorrentFilterChips(
            filterState = TorrentListFilterState(
                categoryFilters = setOf(""),
                trackerFilters = setOf(""),
            ),
        )

        assertEquals(R.string.torrent_filter_uncategorized, chips[0].labelRes)
        assertEquals(R.string.torrent_filter_no_tracker, chips[1].labelRes)
    }

    @Test
    fun summaryChipsAreEmptyWithoutSelections() {
        assertTrue(buildTorrentFilterChips(filterState = TorrentListFilterState()).isEmpty())
    }

    // -----------------------------------------------------------------------
    // Display state wiring
    // -----------------------------------------------------------------------

    @Test
    fun buildTorrentListDisplayStateUsesEverySection() {
        val torrents = listOf(
            torrent(
                "a",
                name = "Ubuntu",
                state = "uploading",
                progress = 1f,
                category = "linux",
                tags = "iso",
                tracker = "https://tracker.example.org/announce",
            ),
            torrent(
                "b",
                name = "Ubuntu Server",
                state = "downloading",
                category = "linux",
                tags = "iso",
                tracker = "https://tracker.example.org/announce",
            ),
            torrent(
                "c",
                name = "Debian",
                state = "downloading",
                category = "linux",
                tags = "iso",
                tracker = "https://other.example.org/announce",
            ),
        )

        val displayState = buildTorrentListDisplayState(
            torrents = torrents,
            filterState = TorrentListFilterState(
                query = "ubuntu",
                stateFilters = setOf(TorrentStateFilter.SEEDING),
                categoryFilters = setOf("linux"),
                tagFilters = setOf("iso"),
                trackerFilters = setOf("tracker.example.org"),
            ),
        )

        assertEquals(listOf("a"), displayState.visibleTorrentItems.map { item -> item.torrent.hash })
    }
}
