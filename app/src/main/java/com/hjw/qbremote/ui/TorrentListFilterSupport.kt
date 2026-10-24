package com.hjw.qbremote.ui

import androidx.compose.runtime.Immutable
import com.hjw.qbremote.R
import com.hjw.qbremote.data.model.TorrentInfo
import java.net.URI
import java.util.Locale

/**
 * Pure logic behind the grouped multi-select torrent-list filter panel.
 *
 * The four sections ("状态 / 分类 / 标签 / 服务器") are aggregated from the torrent list
 * itself, every row carries its match count under the *other* sections' selections plus
 * the search query, and the "全部" row of a section counts the list with that section
 * left unrestricted.
 *
 * Selection semantics live in [TorrentListFilterState]: one set per section, an empty
 * set means "this section is not restricted" (i.e. 全部), values inside one section are
 * OR-ed and the four sections are AND-ed.
 *
 * Keys stored in the state sets:
 *  - state:    [TorrentStateFilter.name]
 *  - category: lower-cased trimmed category, `""` = 未分类
 *  - tag:      lower-cased trimmed tag, `""` unused (blank tags are dropped)
 *  - tracker:  lower-cased tracker host, `""` = 没有Tracker
 */

/** The four sections of the filter panel, in display order. */
internal enum class TorrentFilterGroup {
    STATE,
    CATEGORY,
    TAG,
    TRACKER,
}

/**
 * One selectable row of the filter panel.
 *
 * [key] is the stable identity stored in the matching [TorrentListFilterState] set;
 * [labelRes] wins over [label] when set (state rows and the synthetic rows use a resource).
 */
@Immutable
internal data class TorrentFilterOption(
    val key: String,
    val count: Int = 0,
    val selected: Boolean = false,
    val label: String = "",
    val labelRes: Int? = null,
)

/** The "全部" row plus its option rows for one section. */
@Immutable
internal data class TorrentFilterGroupOptions(
    /** Hits with this section left unrestricted, i.e. the "全部" row count. */
    val allCount: Int = 0,
    /** True when this section has no selected value, so "全部" is the active row. */
    val allSelected: Boolean = true,
    val options: List<TorrentFilterOption> = emptyList(),
)

/** Everything the panel needs to render, already aggregated and counted. */
@Immutable
internal data class TorrentFilterPanelState(
    val states: TorrentFilterGroupOptions = TorrentFilterGroupOptions(),
    val categories: TorrentFilterGroupOptions = TorrentFilterGroupOptions(),
    val tags: TorrentFilterGroupOptions = TorrentFilterGroupOptions(),
    val trackers: TorrentFilterGroupOptions = TorrentFilterGroupOptions(),
    /** Torrents matching the search query and all four sections. */
    val visibleCount: Int = 0,
) {
    fun groupOptions(group: TorrentFilterGroup): TorrentFilterGroupOptions = when (group) {
        TorrentFilterGroup.STATE -> states
        TorrentFilterGroup.CATEGORY -> categories
        TorrentFilterGroup.TAG -> tags
        TorrentFilterGroup.TRACKER -> trackers
    }
}

/** One removable chip of the "already selected" summary row. */
@Immutable
internal data class TorrentFilterChip(
    val group: TorrentFilterGroup,
    val key: String,
    val label: String = "",
    val labelRes: Int? = null,
)

// ---------------------------------------------------------------------------
// Per-torrent matching (same section OR, cross section AND)
// ---------------------------------------------------------------------------

internal fun torrentMatchesStateFilters(
    torrent: TorrentInfo,
    filters: Set<TorrentStateFilter>,
): Boolean {
    if (filters.isEmpty() || TorrentStateFilter.ALL in filters) return true
    return filters.any { filter -> matchesStateFilter(torrent, filter) }
}

internal fun torrentMatchesCategoryFilters(torrent: TorrentInfo, filters: Set<String>): Boolean {
    if (filters.isEmpty()) return true
    val key = torrentCategoryFilterKey(torrent.category)
    return filters.any { filter -> normalizeFilterKey(filter) == key }
}

internal fun torrentMatchesTagFilters(torrent: TorrentInfo, filters: Set<String>): Boolean {
    if (filters.isEmpty()) return true
    val keys = torrentTagFilterKeys(torrent.tags)
    return filters.any { filter -> normalizeFilterKey(filter) in keys }
}

internal fun torrentMatchesTrackerFilters(torrent: TorrentInfo, filters: Set<String>): Boolean {
    if (filters.isEmpty()) return true
    val key = torrentTrackerFilterKey(torrent.tracker)
    return filters.any { filter -> normalizeFilterKey(filter) == key }
}

/** Cross-section AND, search query excluded. */
internal fun matchesTorrentListFilters(
    torrent: TorrentInfo,
    filterState: TorrentListFilterState,
): Boolean {
    return torrentMatchesStateFilters(torrent, filterState.stateFilters) &&
        torrentMatchesCategoryFilters(torrent, filterState.categoryFilters) &&
        torrentMatchesTagFilters(torrent, filterState.tagFilters) &&
        torrentMatchesTrackerFilters(torrent, filterState.trackerFilters)
}

/** Search query plus all four sections. */
internal fun matchesTorrentListFilter(
    torrent: TorrentInfo,
    filterState: TorrentListFilterState,
): Boolean {
    if (!matchesTorrentSearch(torrent, filterState.query)) return false
    return matchesTorrentListFilters(torrent, filterState)
}

internal fun applyTorrentListFilters(
    torrents: List<TorrentInfo>,
    filterState: TorrentListFilterState,
): List<TorrentInfo> = torrents.filter { torrent -> matchesTorrentListFilter(torrent, filterState) }

/** True when at least one section restricts the list (the search query is not a filter). */
internal fun hasActiveTorrentListFilters(filterState: TorrentListFilterState): Boolean {
    return filterState.stateFilters.isNotEmpty() ||
        filterState.categoryFilters.isNotEmpty() ||
        filterState.tagFilters.isNotEmpty() ||
        filterState.trackerFilters.isNotEmpty()
}

/** Number of selected values across all four sections, for a panel badge. */
internal fun torrentFilterSelectedCount(filterState: TorrentListFilterState): Int {
    return filterState.stateFilters.size +
        filterState.categoryFilters.size +
        filterState.tagFilters.size +
        filterState.trackerFilters.size
}

// ---------------------------------------------------------------------------
// Selection keys
// ---------------------------------------------------------------------------

/** Lower-cased, trimmed category key; `""` for an uncategorised torrent. */
internal fun torrentCategoryFilterKey(category: String): String =
    category.trim().lowercase(Locale.US)

/** Lower-cased, de-duplicated tag keys; blank tags are dropped. */
internal fun torrentTagFilterKeys(tags: String): List<String> =
    parseTags(tags).map { tag -> tag.lowercase(Locale.US) }.distinct()

/**
 * Tracker host for the "服务器" section, lower-cased; `""` when the torrent has no
 * tracker. Parsing mirrors the dashboard's tracker-site handling: the `URI` host first,
 * then a scheme/port/path strip, and finally the raw text when nothing parses.
 */
internal fun torrentTrackerFilterKey(tracker: String): String =
    torrentTrackerFilterHost(tracker).lowercase(Locale.US)

/** Display form of the tracker host (original case); `""` when the torrent has no tracker. */
internal fun torrentTrackerFilterHost(tracker: String): String {
    val trimmed = tracker.trim()
    if (trimmed.isBlank()) return ""
    val direct = runCatching { URI(trimmed).host.orEmpty().trim() }.getOrDefault("")
    if (direct.isNotBlank()) return direct
    val stripped = trimmed
        .substringAfter("://", missingDelimiterValue = trimmed)
        .substringBefore('/')
        .substringBefore(':')
        .trim()
    // Unparseable input falls back to the raw text.
    return stripped.ifBlank { trimmed }
}

/** Normalises any raw value into the key stored in the matching filter set. */
internal fun normalizeTorrentFilterKey(group: TorrentFilterGroup, value: String): String =
    when (group) {
        TorrentFilterGroup.STATE -> value.trim()
        TorrentFilterGroup.CATEGORY, TorrentFilterGroup.TAG -> normalizeFilterKey(value)
        TorrentFilterGroup.TRACKER -> torrentTrackerFilterKey(value)
    }

private fun normalizeFilterKey(value: String): String = value.trim().lowercase(Locale.US)

// ---------------------------------------------------------------------------
// Panel aggregation with counts
// ---------------------------------------------------------------------------

internal fun buildTorrentFilterPanelState(
    torrents: List<TorrentInfo>,
    filterState: TorrentListFilterState,
): TorrentFilterPanelState {
    val rows = torrents
        .filter { torrent -> matchesTorrentSearch(torrent, filterState.query) }
        .map { torrent ->
            TorrentFilterRowMatch(
                torrent = torrent,
                state = torrentMatchesStateFilters(torrent, filterState.stateFilters),
                category = torrentMatchesCategoryFilters(torrent, filterState.categoryFilters),
                tag = torrentMatchesTagFilters(torrent, filterState.tagFilters),
                tracker = torrentMatchesTrackerFilters(torrent, filterState.trackerFilters),
            )
        }
    val stateOptions = TorrentStateFilter.entries
        .filter { filter -> filter != TorrentStateFilter.ALL }
        // Pill-only states (Uploading/Waiting) stay hidden unless already selected.
        .filter { filter -> filter.inFilterRow || filter in filterState.stateFilters }
        .map { filter ->
            TorrentFilterOption(
                key = filter.name,
                count = rows.count { row ->
                    row.matchesExcept(TorrentFilterGroup.STATE) &&
                        matchesStateFilter(row.torrent, filter)
                },
                selected = filter in filterState.stateFilters,
                labelRes = filter.labelKey,
            )
        }

    return TorrentFilterPanelState(
        states = TorrentFilterGroupOptions(
            allCount = rows.count { row -> row.matchesExcept(TorrentFilterGroup.STATE) },
            allSelected = filterState.stateFilters.isEmpty(),
            options = stateOptions,
        ),
        categories = buildTextGroup(
            group = TorrentFilterGroup.CATEGORY,
            catalog = buildCategoryCatalog(torrents),
            selectedKeys = filterState.categoryFilters,
            rows = rows,
            optionMatches = { torrent, key -> torrentCategoryFilterKey(torrent.category) == key },
            placeholderLabelRes = R.string.torrent_filter_uncategorized,
        ),
        tags = buildTextGroup(
            group = TorrentFilterGroup.TAG,
            catalog = buildTagCatalog(torrents),
            selectedKeys = filterState.tagFilters,
            rows = rows,
            optionMatches = { torrent, key -> key in torrentTagFilterKeys(torrent.tags) },
            placeholderLabelRes = null,
        ),
        trackers = buildTextGroup(
            group = TorrentFilterGroup.TRACKER,
            catalog = buildTrackerCatalog(torrents),
            selectedKeys = filterState.trackerFilters,
            rows = rows,
            optionMatches = { torrent, key -> torrentTrackerFilterKey(torrent.tracker) == key },
            placeholderLabelRes = R.string.torrent_filter_no_tracker,
        ),
        visibleCount = rows.count { row -> row.matchesAll() },
    )
}

/**
 * Builds one category/tag/tracker section.
 *
 * The roster comes from the whole torrent list (not from the currently visible rows), so
 * rows do not disappear while narrowing down; a value that is selected but no longer
 * present is kept with a count of 0 so it can still be deselected.
 */
private fun buildTextGroup(
    group: TorrentFilterGroup,
    catalog: Map<String, String>,
    selectedKeys: Set<String>,
    rows: List<TorrentFilterRowMatch>,
    optionMatches: (TorrentInfo, String) -> Boolean,
    placeholderLabelRes: Int?,
): TorrentFilterGroupOptions {
    val selected = selectedKeys
        .map { key -> normalizeTorrentFilterKey(group, key) }
        .toSet()
    val keys = (catalog.keys + selected)
        .filter { key -> key.isNotEmpty() }
        .distinct()
        .sortedWith(
            compareBy<String> { key -> catalogSortLabel(catalog, key) }
                .thenBy { key -> key },
        )
    val options = keys.map { key ->
        TorrentFilterOption(
            key = key,
            count = rows.count { row -> row.matchesExcept(group) && optionMatches(row.torrent, key) },
            selected = key in selected,
            label = catalog[key] ?: key,
        )
    }
    // Uncategorised / "没有Tracker" rows carry an empty key and therefore sort last.
    val placeholder = if (("" in catalog.keys || "" in selected) && placeholderLabelRes != null) {
        listOf(
            TorrentFilterOption(
                key = "",
                count = rows.count { row -> row.matchesExcept(group) && optionMatches(row.torrent, "") },
                selected = "" in selected,
                labelRes = placeholderLabelRes,
            ),
        )
    } else {
        emptyList()
    }
    return TorrentFilterGroupOptions(
        allCount = rows.count { row -> row.matchesExcept(group) },
        allSelected = selected.isEmpty(),
        options = options + placeholder,
    )
}

/** One torrent with its per-section match flags, so every count is a single pass. */
private data class TorrentFilterRowMatch(
    val torrent: TorrentInfo,
    val state: Boolean,
    val category: Boolean,
    val tag: Boolean,
    val tracker: Boolean,
) {
    fun matchesAll(): Boolean = state && category && tag && tracker

    fun matchesExcept(group: TorrentFilterGroup): Boolean = when (group) {
        TorrentFilterGroup.STATE -> category && tag && tracker
        TorrentFilterGroup.CATEGORY -> state && tag && tracker
        TorrentFilterGroup.TAG -> state && category && tracker
        TorrentFilterGroup.TRACKER -> state && category && tag
    }
}

/** category key -> display form (first occurrence wins, case-insensitive). */
private fun buildCategoryCatalog(torrents: List<TorrentInfo>): Map<String, String> {
    val catalog = linkedMapOf<String, String>()
    torrents.forEach { torrent ->
        val display = torrent.category.trim()
        val key = display.lowercase(Locale.US)
        if (key !in catalog) catalog[key] = display
    }
    return catalog
}

/** tag key -> display form (first occurrence wins, case-insensitive). */
private fun buildTagCatalog(torrents: List<TorrentInfo>): Map<String, String> {
    val catalog = linkedMapOf<String, String>()
    torrents.forEach { torrent ->
        parseTags(torrent.tags).forEach { tag ->
            val key = tag.lowercase(Locale.US)
            if (key.isNotEmpty() && key !in catalog) catalog[key] = tag
        }
    }
    return catalog
}

/** tracker host key -> display host (first occurrence wins, case-insensitive). */
private fun buildTrackerCatalog(torrents: List<TorrentInfo>): Map<String, String> {
    val catalog = linkedMapOf<String, String>()
    torrents.forEach { torrent ->
        val display = torrentTrackerFilterHost(torrent.tracker)
        val key = display.lowercase(Locale.US)
        if (key !in catalog) catalog[key] = display
    }
    return catalog
}

// ---------------------------------------------------------------------------
// Selected-filter summary (list page chips)
// ---------------------------------------------------------------------------

internal fun buildTorrentFilterChips(
    torrents: List<TorrentInfo> = emptyList(),
    filterState: TorrentListFilterState,
): List<TorrentFilterChip> {
    val categoryCatalog = buildCategoryCatalog(torrents)
    val tagCatalog = buildTagCatalog(torrents)
    val trackerCatalog = buildTrackerCatalog(torrents)
    val chips = mutableListOf<TorrentFilterChip>()

    TorrentStateFilter.entries
        .filter { filter -> filter != TorrentStateFilter.ALL && filter in filterState.stateFilters }
        .forEach { filter ->
            chips += TorrentFilterChip(
                group = TorrentFilterGroup.STATE,
                key = filter.name,
                labelRes = filter.labelKey,
            )
        }

    filterState.categoryFilters
        .map { key -> normalizeTorrentFilterKey(TorrentFilterGroup.CATEGORY, key) }
        .distinct()
        .sortedWith(
            compareBy<String> { key -> if (key.isEmpty()) 1 else 0 }
                .thenBy { key -> catalogSortLabel(categoryCatalog, key) }
                .thenBy { key -> key },
        )
        .forEach { key ->
            chips += TorrentFilterChip(
                group = TorrentFilterGroup.CATEGORY,
                key = key,
                label = categoryCatalog[key] ?: key,
                labelRes = if (key.isEmpty()) R.string.torrent_filter_uncategorized else null,
            )
        }

    filterState.tagFilters
        .map { key -> normalizeTorrentFilterKey(TorrentFilterGroup.TAG, key) }
        .distinct()
        .sortedWith(
            compareBy<String> { key -> catalogSortLabel(tagCatalog, key) }
                .thenBy { key -> key },
        )
        .forEach { key ->
            chips += TorrentFilterChip(
                group = TorrentFilterGroup.TAG,
                key = key,
                label = tagCatalog[key] ?: key,
            )
        }

    filterState.trackerFilters
        .map { key -> normalizeTorrentFilterKey(TorrentFilterGroup.TRACKER, key) }
        .distinct()
        .sortedWith(
            compareBy<String> { key -> if (key.isEmpty()) 1 else 0 }
                .thenBy { key -> catalogSortLabel(trackerCatalog, key) }
                .thenBy { key -> key },
        )
        .forEach { key ->
            chips += TorrentFilterChip(
                group = TorrentFilterGroup.TRACKER,
                key = key,
                label = trackerCatalog[key] ?: key,
                labelRes = if (key.isEmpty()) R.string.torrent_filter_no_tracker else null,
            )
        }

    return chips
}

private fun catalogSortLabel(catalog: Map<String, String>, key: String): String =
    catalog[key].orEmpty().lowercase(Locale.US)

// ---------------------------------------------------------------------------
// Selection updates
// ---------------------------------------------------------------------------

/**
 * Adds or removes one value of [group]. Selecting a section's "全部" row clears the whole
 * section; an unknown state key leaves the state untouched.
 */
internal fun toggleTorrentFilterOption(
    current: TorrentListFilterState,
    group: TorrentFilterGroup,
    key: String,
): TorrentListFilterState {
    return when (group) {
        TorrentFilterGroup.STATE -> {
            val filter = TorrentStateFilter.entries.firstOrNull { candidate ->
                candidate.name == normalizeTorrentFilterKey(group, key)
            } ?: return current
            if (filter == TorrentStateFilter.ALL) {
                current.copy(stateFilters = emptySet())
            } else {
                current.copy(stateFilters = current.stateFilters.toggled(filter))
            }
        }

        TorrentFilterGroup.CATEGORY -> current.copy(
            categoryFilters = current.categoryFilters
                .toggled(normalizeTorrentFilterKey(group, key)),
        )

        TorrentFilterGroup.TAG -> current.copy(
            tagFilters = current.tagFilters.toggled(normalizeTorrentFilterKey(group, key)),
        )

        TorrentFilterGroup.TRACKER -> current.copy(
            trackerFilters = current.trackerFilters.toggled(normalizeTorrentFilterKey(group, key)),
        )
    }
}

internal fun toggleTorrentStateFilter(
    current: TorrentListFilterState,
    filter: TorrentStateFilter,
): TorrentListFilterState =
    toggleTorrentFilterOption(current, TorrentFilterGroup.STATE, filter.name)

internal fun toggleTorrentCategoryFilter(
    current: TorrentListFilterState,
    category: String,
): TorrentListFilterState =
    toggleTorrentFilterOption(current, TorrentFilterGroup.CATEGORY, category)

internal fun toggleTorrentTagFilter(
    current: TorrentListFilterState,
    tag: String,
): TorrentListFilterState = toggleTorrentFilterOption(current, TorrentFilterGroup.TAG, tag)

/** [tracker] may be a tracker URL or an already resolved host; `""` selects "没有Tracker". */
internal fun toggleTorrentTrackerFilter(
    current: TorrentListFilterState,
    tracker: String,
): TorrentListFilterState =
    toggleTorrentFilterOption(current, TorrentFilterGroup.TRACKER, tracker)

/** Replaces the state section with exactly [filter] (used by the dashboard status pills). */
internal fun selectTorrentStateFilter(
    current: TorrentListFilterState,
    filter: TorrentStateFilter,
): TorrentListFilterState = current.copy(
    stateFilters = if (filter == TorrentStateFilter.ALL) emptySet() else setOf(filter),
)

internal fun clearTorrentFilterGroup(
    current: TorrentListFilterState,
    group: TorrentFilterGroup,
): TorrentListFilterState = when (group) {
    TorrentFilterGroup.STATE -> current.copy(stateFilters = emptySet())
    TorrentFilterGroup.CATEGORY -> current.copy(categoryFilters = emptySet())
    TorrentFilterGroup.TAG -> current.copy(tagFilters = emptySet())
    TorrentFilterGroup.TRACKER -> current.copy(trackerFilters = emptySet())
}

private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value
