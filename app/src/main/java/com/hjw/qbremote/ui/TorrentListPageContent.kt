package com.hjw.qbremote.ui

import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hjw.qbremote.R
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.ui.theme.qbGlassStrongContainerColor

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.torrentListPageContent(
    showContent: Boolean,
    showRestorePlaceholder: Boolean,
    showSearchBar: Boolean,
    animatePlacement: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterState: TorrentListFilterState,
    onStateFilterChange: (TorrentStateFilter) -> Unit,
    onCategoryFilterChange: (String) -> Unit,
    onTagFilterChange: (String) -> Unit,
    categoryOptions: List<String>,
    tagOptions: List<String>,
    visibleItems: List<VisibleTorrentItem>,
    emptyState: TorrentListEmptyState,
    onClearFilters: () -> Unit,
    isPendingAction: (String) -> Boolean,
    onOpenDetails: (TorrentInfo) -> Unit,
    onOpenConnection: () -> Unit,
    selection: TorrentListSelection = TorrentListSelection(),
) {
    if (showContent) {
        if (selection.active) {
            stickyHeader(key = "torrent_selection_bar") {
                TorrentSelectionBar(
                    selectedCount = selection.selectedHashes.size,
                    allVisibleSelected = visibleItems.isNotEmpty() &&
                        visibleItems.all { it.torrent.hash in selection.selectedHashes },
                    onExit = selection.onExit,
                    onSelectAll = { selection.onSelectAll(visibleItems.map { it.torrent.hash }) },
                    onPause = selection.onPauseSelected,
                    onResume = selection.onResumeSelected,
                    onDelete = selection.onDeleteSelected,
                )
            }
        } else if (showSearchBar) {
            stickyHeader(key = "torrent_search_bar") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(qbGlassStrongContainerColor())
                        .padding(bottom = 8.dp),
                    label = { Text(stringResource(R.string.search_torrent_label)) },
                    placeholder = { Text(stringResource(R.string.search_torrent_placeholder)) },
                    singleLine = true,
                )
            }
            stickyHeader(key = "torrent_filter_chips") {
                FilterChipRow(
                    filterState = filterState,
                    onStateFilterChange = onStateFilterChange,
                    onCategoryFilterChange = onCategoryFilterChange,
                    onTagFilterChange = onTagFilterChange,
                    categoryOptions = categoryOptions,
                    tagOptions = tagOptions,
                )
            }
        }
        items(
            items = visibleItems,
            key = { item -> item.torrent.hash.ifBlank { item.identityKey } },
        ) { item ->
            val itemModifier = if (animatePlacement) {
                Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 380f,
                    ),
                )
            } else {
                Modifier
            }
            val hash = item.torrent.hash
            val pending = isPendingAction(hash)
            SwipeableTorrentRow(
                torrent = item.torrent,
                // No swipe actions while multi-selecting or while an action runs.
                enabled = !selection.active && !pending,
                onPauseResume = selection.onSwipePauseResume,
                onRequestDelete = selection.onSwipeDelete,
                modifier = itemModifier,
            ) {
                TorrentCard(
                    torrent = item.torrent,
                    crossSeedCount = item.crossSeedCount,
                    isPending = pending,
                    selected = selection.active && hash in selection.selectedHashes,
                    onOpenDetails = {
                        if (selection.active) {
                            selection.onToggle(hash)
                        } else {
                            onOpenDetails(item.torrent)
                        }
                    },
                    onLongClick = { selection.onToggle(hash) },
                )
            }
        }
        if (visibleItems.isEmpty()) {
            item(key = "torrent_list_empty") {
                TorrentListEmptyMessage(emptyState = emptyState, onClearFilters = onClearFilters)
            }
        }
    } else if (showRestorePlaceholder) {
        item {
            PageRestorePlaceholder()
        }
    } else {
        item {
            NeedConnectionCard(
                onOpenConnection = onOpenConnection,
            )
        }
    }
}

@Composable
private fun FilterChipRow(
    filterState: TorrentListFilterState,
    onStateFilterChange: (TorrentStateFilter) -> Unit,
    onCategoryFilterChange: (String) -> Unit,
    onTagFilterChange: (String) -> Unit,
    categoryOptions: List<String>,
    tagOptions: List<String>,
) {
    // Borderless pills: neutral when idle, inverted (light on dark) when selected.
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.onSurface,
        selectedLabelColor = MaterialTheme.colorScheme.surface,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Row 1: State filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TorrentStateFilter.entries.forEach { stateFilter ->
                if (!stateFilter.inFilterRow && filterState.stateFilter != stateFilter) return@forEach
                FilterChip(
                    selected = filterState.stateFilter == stateFilter,
                    onClick = { onStateFilterChange(stateFilter) },
                    label = { Text(stringResource(stateFilter.labelKey)) },
                    colors = chipColors,
                    border = null,
                    shape = CircleShape,
                )
            }
        }
        // Row 2: Category filters
        if (categoryOptions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categoryOptions.forEach { category ->
                    FilterChip(
                        selected = filterState.categoryFilter == category,
                        onClick = { onCategoryFilterChange(category) },
                        label = { Text(category) },
                        colors = chipColors,
                        border = null,
                        shape = CircleShape,
                    )
                }
            }
        }
        // Row 3: Tag filters
        if (tagOptions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tagOptions.forEach { tag ->
                    FilterChip(
                        selected = filterState.tagFilter == tag,
                        onClick = { onTagFilterChange(tag) },
                        label = { Text(tag) },
                        colors = chipColors,
                        border = null,
                        shape = CircleShape,
                    )
                }
            }
        }
    }
}

internal fun shouldAnimateTorrentPlacement(
    previousKeys: List<String>,
    currentKeys: List<String>,
): Boolean {
    if (previousKeys.size != currentKeys.size) return false
    if (previousKeys == currentKeys) return false
    if (buildTorrentPlacementKeyCountMap(previousKeys) != buildTorrentPlacementKeyCountMap(currentKeys)) {
        return false
    }
    return true
}

private fun buildTorrentPlacementKeyCountMap(keys: List<String>): Map<String, Int> {
    val counts = linkedMapOf<String, Int>()
    keys.forEach { key ->
        counts[key] = (counts[key] ?: 0) + 1
    }
    return counts
}

/** Multi-select state and callbacks for the torrent list (hoisted in MainScreen). */
internal data class TorrentListSelection(
    val selectedHashes: Set<String> = emptySet(),
    val onToggle: (String) -> Unit = {},
    val onSelectAll: (List<String>) -> Unit = {},
    val onExit: () -> Unit = {},
    val onPauseSelected: () -> Unit = {},
    val onResumeSelected: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onSwipePauseResume: (TorrentInfo) -> Unit = {},
    val onSwipeDelete: (TorrentInfo) -> Unit = {},
) {
    val active: Boolean get() = selectedHashes.isNotEmpty()
}

/** Says why the list is empty; when filters hide everything, offers to clear them. */
@Composable
private fun TorrentListEmptyMessage(
    emptyState: TorrentListEmptyState,
    onClearFilters: () -> Unit,
) {
    val message = when (emptyState) {
        TorrentListEmptyState.SEARCH_NO_RESULTS -> R.string.torrent_list_empty_search
        TorrentListEmptyState.FILTER_NO_RESULTS -> R.string.torrent_list_empty_filter
        TorrentListEmptyState.SEARCH_FILTER_NO_RESULTS -> R.string.torrent_list_empty_search_filter
        TorrentListEmptyState.NONE, TorrentListEmptyState.NO_TORRENTS -> R.string.no_torrent_data
    }
    val filtersActive = emptyState == TorrentListEmptyState.FILTER_NO_RESULTS ||
        emptyState == TorrentListEmptyState.SEARCH_FILTER_NO_RESULTS
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (filtersActive) {
            TextButton(onClick = onClearFilters) {
                Text(stringResource(R.string.torrent_list_clear_filters))
            }
        }
    }
}
