package com.hjw.qbremote.ui

import androidx.compose.runtime.Immutable
import com.hjw.qbremote.R
import com.hjw.qbremote.data.model.TorrentInfo

internal enum class TorrentListSortOption {
    ADDED_TIME,
    UPLOAD_SPEED,
    DOWNLOAD_SPEED,
    SHARE_RATIO,
    TOTAL_UPLOADED,
    TOTAL_DOWNLOADED,
    TORRENT_SIZE,
    ACTIVITY_TIME,
    SEEDERS,
    LEECHERS,
    CROSS_SEED_COUNT,
}

internal enum class TorrentStateFilter(
    val labelKey: Int,
    /** Group of the matching server-card pill, if this filter is one of those groups. */
    val dashboardBucket: DashboardStateBucket? = null,
    /**
     * Pill-only filters ("Uploading" overlaps "Seeding") stay out of the list's
     * filter row unless they are the active one, i.e. reached from a pill.
     */
    val inFilterRow: Boolean = true,
) {
    ALL(R.string.filter_state_all),
    DOWNLOADING(R.string.filter_state_downloading, DashboardStateBucket.DOWNLOADING),
    UPLOADING(R.string.status_uploading, DashboardStateBucket.UPLOADING, inFilterRow = false),
    SEEDING(R.string.filter_state_seeding),
    PAUSED(R.string.filter_state_paused, DashboardStateBucket.PAUSED),
    COMPLETED(R.string.filter_state_completed),
    CHECKING(R.string.filter_state_checking, DashboardStateBucket.CHECKING),
    WAITING(R.string.status_waiting, DashboardStateBucket.WAITING, inFilterRow = false),
    ERROR(R.string.filter_state_error, DashboardStateBucket.ERROR),
}

@Immutable
internal data class TorrentListFilterState(
    val query: String = "",
    val sortOption: TorrentListSortOption = TorrentListSortOption.UPLOAD_SPEED,
    val descending: Boolean = true,
    val stateFilter: TorrentStateFilter = TorrentStateFilter.ALL,
    val categoryFilter: String = "",
    val tagFilter: String = "",
)

@Immutable
internal data class TorrentListBaseSnapshot(
    val torrents: List<TorrentInfo> = emptyList(),
    val crossSeedCounts: Map<String, Int> = emptyMap(),
)

@Immutable
internal data class VisibleTorrentItem(
    val identityKey: String,
    val torrent: TorrentInfo,
    val crossSeedCount: Int,
)

@Immutable
internal data class TorrentListDisplayState(
    val torrentListBaseSnapshot: TorrentListBaseSnapshot = TorrentListBaseSnapshot(),
    val torrentListFilterState: TorrentListFilterState = TorrentListFilterState(),
    val visibleTorrentItems: List<VisibleTorrentItem> = emptyList(),
    val emptyState: TorrentListEmptyState = TorrentListEmptyState.NONE,
)

private data class CrossSeedGroupKey(
    val savePath: String,
    val size: Long,
    val uniqueIdentity: String = "",
)

internal fun torrentIdentityKey(torrent: TorrentInfo): String {
    return torrent.hash.ifBlank {
        "${torrent.name}|${torrent.addedOn}|${torrent.savePath}|${torrent.size}"
    }
}

internal fun buildCrossSeedCountMap(torrents: List<TorrentInfo>): Map<String, Int> {
    val grouped = torrents.groupBy { crossSeedGroupKey(it) }
    val result = mutableMapOf<String, Int>()

    torrents.forEach { torrent ->
        val key = crossSeedGroupKey(torrent)
        val groupCount = grouped[key]?.size ?: 1
        result[torrentIdentityKey(torrent)] = (groupCount - 1).coerceAtLeast(0)
    }
    return result
}

private fun crossSeedGroupKey(torrent: TorrentInfo): CrossSeedGroupKey {
    val normalizedPath = torrent.savePath.trim().lowercase()
    val normalizedSize = torrent.size.coerceAtLeast(0L)
    if (normalizedPath.isBlank() || normalizedSize <= 0L) {
        return CrossSeedGroupKey(
            savePath = "__invalid__",
            size = -1L,
            uniqueIdentity = torrent.hash.ifBlank { torrentIdentityKey(torrent) },
        )
    }
    return CrossSeedGroupKey(
        savePath = normalizedPath,
        size = normalizedSize,
    )
}

internal fun sortTorrentList(
    torrents: List<TorrentInfo>,
    sortOption: TorrentListSortOption,
    descending: Boolean,
    crossSeedCounts: Map<String, Int>,
): List<TorrentInfo> {
    val comparator = when (sortOption) {
        TorrentListSortOption.ADDED_TIME ->
            compareBy<TorrentInfo> { it.addedOn }
        TorrentListSortOption.UPLOAD_SPEED ->
            compareBy<TorrentInfo> { it.uploadSpeed }
                .thenBy { it.addedOn }
        TorrentListSortOption.DOWNLOAD_SPEED ->
            compareBy<TorrentInfo> { it.downloadSpeed }
                .thenBy { it.addedOn }
        TorrentListSortOption.SHARE_RATIO ->
            compareBy<TorrentInfo> { it.ratio }
                .thenBy { it.addedOn }
        TorrentListSortOption.TOTAL_UPLOADED ->
            compareBy<TorrentInfo> { it.uploaded }
                .thenBy { it.addedOn }
        TorrentListSortOption.TOTAL_DOWNLOADED ->
            compareBy<TorrentInfo> { it.downloaded }
                .thenBy { it.addedOn }
        TorrentListSortOption.TORRENT_SIZE ->
            compareBy<TorrentInfo> { it.size }
                .thenBy { it.addedOn }
        TorrentListSortOption.ACTIVITY_TIME ->
            compareBy<TorrentInfo> { it.lastActivity }
                .thenBy { it.addedOn }
        TorrentListSortOption.SEEDERS ->
            compareBy<TorrentInfo> { it.seeders }
                .thenBy { it.addedOn }
        TorrentListSortOption.LEECHERS ->
            compareBy<TorrentInfo> { it.leechers }
                .thenBy { it.addedOn }
        TorrentListSortOption.CROSS_SEED_COUNT ->
            compareBy<TorrentInfo> { crossSeedCounts[torrentIdentityKey(it)] ?: 0 }
                .thenBy { it.addedOn }
    }
    val finalComparator = if (descending) comparator.reversed() else comparator
    return torrents.sortedWith(finalComparator)
}

internal fun matchesStateFilter(torrent: TorrentInfo, filter: TorrentStateFilter): Boolean {
    if (filter == TorrentStateFilter.ALL) return true
    filter.dashboardBucket?.let { bucket -> return dashboardStateBucket(torrent) == bucket }
    return when (filter) {
        TorrentStateFilter.SEEDING -> normalizeTorrentState(effectiveTorrentState(torrent)) in
            setOf("uploading", "stalledup", "queuedup", "forcedup", "pausedup", "stoppedup")
        // COMPLETED
        else -> torrent.progress >= 1f && dashboardStateBucket(torrent).let {
            it != DashboardStateBucket.CHECKING && it != DashboardStateBucket.ERROR
        }
    }
}

internal fun buildTorrentListDisplayState(
    torrents: List<TorrentInfo>,
    filterState: TorrentListFilterState,
): TorrentListDisplayState {
    val crossSeedCounts = buildCrossSeedCountMap(torrents)
    val filteredTorrents = run {
        var result = torrents
        val query = filterState.query.trim()
        if (query.isNotBlank()) {
            result = result.filter { torrent -> matchesTorrentSearch(torrent = torrent, query = query) }
        }
        if (filterState.stateFilter != TorrentStateFilter.ALL) {
            result = result.filter { torrent -> matchesStateFilter(torrent, filterState.stateFilter) }
        }
        if (filterState.categoryFilter.isNotBlank()) {
            val cat = filterState.categoryFilter.trim().lowercase()
            result = result.filter { torrent -> torrent.category.trim().lowercase() == cat }
        }
        if (filterState.tagFilter.isNotBlank()) {
            val tag = filterState.tagFilter.trim().lowercase()
            result = result.filter { torrent ->
                torrent.tags.split(',', ';', '|').any { it.trim().lowercase() == tag }
            }
        }
        result
    }
    val visibleItems = sortTorrentList(
        torrents = filteredTorrents,
        sortOption = filterState.sortOption,
        descending = filterState.descending,
        crossSeedCounts = crossSeedCounts,
    ).map { torrent ->
        VisibleTorrentItem(
            identityKey = torrentIdentityKey(torrent),
            torrent = torrent,
            crossSeedCount = crossSeedCounts[torrentIdentityKey(torrent)] ?: 0,
        )
    }
    return TorrentListDisplayState(
        torrentListBaseSnapshot = TorrentListBaseSnapshot(
            torrents = torrents,
            crossSeedCounts = crossSeedCounts,
        ),
        torrentListFilterState = filterState,
        visibleTorrentItems = visibleItems,
        emptyState = resolveTorrentListEmptyState(
            hasTorrents = torrents.isNotEmpty(),
            visibleCount = visibleItems.size,
            filterState = filterState,
        ),
    )
}
