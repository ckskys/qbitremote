package com.hjw.qbremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hjw.qbremote.R
import com.hjw.qbremote.ui.theme.qbGlassSheetColor

/**
 * Grouped, multi-select torrent-list filter panel.
 *
 * One section per [TorrentFilterGroup] ("状态 / 分类 / 标签 / 服务器"), every section led by an
 * "全部" row that means "this section is unrestricted". Selecting a row applies immediately —
 * there is no "apply" button — and the counts come from [buildTorrentFilterPanelState], so a
 * row only counts the torrents the *other* sections and the search query leave visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TorrentListFilterSheet(
    sheetState: SheetState,
    panelState: TorrentFilterPanelState,
    onToggleOption: (TorrentFilterGroup, String) -> Unit,
    onClearGroup: (TorrentFilterGroup) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = qbGlassSheetColor(),
        shape = PanelShape,
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.torrent_filter_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        R.string.torrent_filter_visible_count_fmt,
                        panelState.visibleCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.torrent_filter_clear))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // BoxWithConstraints instead of `LazyColumn(Modifier.weight(1f, fill = false))`:
            // weight only works inside a Column whose height is bounded, and this column is
            // wrap-content, so the list relied on an ambiguous measurement path (a bottom sheet
            // measures content with max height = the sheet's own limit). Capping the list at a
            // fraction of the available height keeps short lists shrink-wrapped while long ones
            // scroll internally, and a Column + verticalScroll is not an option here because it
            // would compose every row of all four sections at once.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // A bottom sheet may measure its content with an unbounded max height; falling
                // back to the window height keeps the list from being measured with infinity
                // (which a lazy list rejects). LocalWindowInfo (px) rather than
                // LocalConfiguration.screenHeightDp: the latter excludes insets and lags behind
                // window-size changes.
                val windowHeight = with(LocalDensity.current) {
                    LocalWindowInfo.current.containerSize.height.toDp()
                }
                val availableHeight = if (maxHeight.value.isFinite()) maxHeight else windowHeight
                val maxListHeight = availableHeight * 0.72f
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight),
                ) {
                    TorrentFilterGroup.entries.forEach { group ->
                        filterGroupSection(
                            group = group,
                            options = panelState.groupOptions(group),
                            onToggleOption = onToggleOption,
                            onClearGroup = onClearGroup,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.filterGroupSection(
    group: TorrentFilterGroup,
    options: TorrentFilterGroupOptions,
    onToggleOption: (TorrentFilterGroup, String) -> Unit,
    onClearGroup: (TorrentFilterGroup) -> Unit,
) {
    item(key = "torrent_filter_header_${group.name}") {
        FilterGroupHeader(title = stringResource(group.titleRes()))
    }
    item(key = "torrent_filter_all_${group.name}") {
        TorrentFilterOptionRow(
            label = stringResource(R.string.filter_state_all),
            count = options.allCount,
            selected = options.allSelected,
            // "全部" means "this section is unrestricted", so it is mutually exclusive with the
            // section's individual rows: picking it clears them. RadioButton is the role TalkBack
            // announces that exclusivity with ("radio button, selected"), while the rows below are
            // Checkbox-shaped because several of them can be on at once.
            role = Role.RadioButton,
            onClick = { onClearGroup(group) },
        )
    }
    if (options.options.isEmpty()) {
        item(key = "torrent_filter_empty_${group.name}") {
            FilterGroupPlaceholder(text = stringResource(group.emptyRes()))
        }
    } else {
        options.options.forEach { option ->
            item(key = "torrent_filter_option_${group.name}_${option.key}") {
                TorrentFilterOptionRow(
                    label = option.displayLabel(),
                    count = option.count,
                    selected = option.selected,
                    onClick = { onToggleOption(group, option.key) },
                )
            }
        }
    }
    item(key = "torrent_filter_divider_${group.name}") {
        HorizontalDivider(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * One tappable row: a leading check marker, the label, and the full-width-bracketed count.
 * A selected row fills with a light primary tint, which is what makes the current
 * selection readable at a glance.
 */
@Composable
private fun TorrentFilterOptionRow(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    role: Role = Role.Checkbox,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(
                color = if (selected) primary.copy(alpha = 0.14f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            // toggleable (not clickable) so TalkBack announces "selected / not selected" for the
            // whole row instead of just reading the label; it also merges the marker + text into
            // one accessibility node.
            .toggleable(
                value = selected,
                role = role,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 12.dp)
            .heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FilterSelectionMarker(selected = selected, color = primary)
        Text(
            text = stringResource(R.string.torrent_filter_row_fmt, label, count),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Square checkbox-style marker: a filled primary square with a check when selected, a hollow
 * outlined square otherwise. A square (5dp corners, well under half the 20dp edge) reads as
 * "multi-select" rather than the "single choice" a circle implies.
 */
@Composable
private fun FilterSelectionMarker(selected: Boolean, color: Color) {
    val shape = RoundedCornerShape(5.dp)
    if (selected) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(color = color, shape = shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                // The row itself carries the selected state for TalkBack; a described icon would
                // be read out a second time.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(width = 1.5.dp, color = MaterialTheme.colorScheme.outline, shape = shape),
        )
    }
}

@Composable
private fun FilterGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun FilterGroupPlaceholder(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
    )
}

private fun TorrentFilterGroup.titleRes(): Int = when (this) {
    TorrentFilterGroup.STATE -> R.string.torrent_filter_group_state
    TorrentFilterGroup.CATEGORY -> R.string.torrent_filter_group_category
    TorrentFilterGroup.TAG -> R.string.torrent_filter_group_tag
    TorrentFilterGroup.TRACKER -> R.string.torrent_filter_group_tracker
}

private fun TorrentFilterGroup.emptyRes(): Int = when (this) {
    TorrentFilterGroup.STATE -> R.string.torrent_filter_group_empty
    TorrentFilterGroup.CATEGORY -> R.string.torrent_filter_group_empty
    TorrentFilterGroup.TAG -> R.string.torrent_filter_no_tag
    TorrentFilterGroup.TRACKER -> R.string.torrent_filter_group_empty
}

/** Resource labels win over raw text; a key with neither still renders its key. */
@Composable
private fun TorrentFilterOption.displayLabel(): String {
    labelRes?.let { return stringResource(it) }
    return label.ifBlank { key }
}
