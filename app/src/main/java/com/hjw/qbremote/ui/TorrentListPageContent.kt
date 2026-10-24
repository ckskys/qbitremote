package com.hjw.qbremote.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
    filterChips: List<TorrentFilterChip>,
    onRemoveFilter: (TorrentFilterGroup, String) -> Unit,
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
        }
        if (!selection.active && filterChips.isNotEmpty()) {
            stickyHeader(key = "torrent_filter_summary") {
                TorrentFilterSummaryRow(
                    chips = filterChips,
                    onRemoveFilter = onRemoveFilter,
                    onClearFilters = onClearFilters,
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

/**
 * Number of leading sticky headers of the torrent list, i.e. the index of its first row.
 * Scroll restoration and "scroll to first torrent" both need this to land on a torrent.
 */
internal fun torrentListHeaderCount(
    selectionActive: Boolean,
    showSearchBar: Boolean,
    hasFilterSummary: Boolean,
): Int = when {
    selectionActive -> 1
    showSearchBar && hasFilterSummary -> 2
    showSearchBar || hasFilterSummary -> 1
    else -> 0
}

/**
 * The applied-filter summary: one removable chip per selected value plus a "clear all"
 * action. Only rendered while at least one section restricts the list.
 */
@Composable
private fun TorrentFilterSummaryRow(
    chips: List<TorrentFilterChip>,
    onRemoveFilter: (TorrentFilterGroup, String) -> Unit,
    onClearFilters: () -> Unit,
) {
    val summaryDescription = stringResource(R.string.torrent_filter_summary_label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
            .semantics { contentDescription = summaryDescription },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { chip ->
            FilterSummaryChip(
                label = chip.displayLabel(),
                onRemove = { onRemoveFilter(chip.group, chip.key) },
            )
        }
        TextButton(onClick = onClearFilters) {
            Text(stringResource(R.string.torrent_list_clear_filters))
        }
    }
}

@Composable
private fun FilterSummaryChip(label: String, onRemove: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .background(
                color = primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50),
            )
            // No vertical padding: the 48dp remove button is what sets the chip height, which
            // keeps the row an even 48dp tall instead of 48dp + padding.
            .padding(start = 12.dp, end = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = onRemove,
            // 48dp touch target (the accessibility floor the settings rows are held to as well);
            // the glyph stays 18dp so the chip does not look heavy.
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.torrent_filter_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Resource labels win over raw text; a key with neither still renders its key. */
@Composable
private fun TorrentFilterChip.displayLabel(): String {
    labelRes?.let { return stringResource(it) }
    return label.ifBlank { key }
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
