package com.hjw.qbremote.ui

import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import com.hjw.qbremote.ui.theme.QbNumberStyles
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import com.hjw.qbremote.ui.theme.QbTransferColors
import com.hjw.qbremote.data.model.FilePriority
import com.hjw.qbremote.data.model.TorrentPeer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hjw.qbremote.R
import com.hjw.qbremote.data.model.TorrentFileInfo
import com.hjw.qbremote.data.model.TorrentTracker

// Torrent detail Trackers / Peers / Files tabs.

@Composable
internal fun TorrentDetailTrackersTab(
    detailLoading: Boolean,
    detailTrackers: List<TorrentTracker>,
    hasMutableTrackers: Boolean,
    trackersPasskeyVisible: Boolean,
    isPending: Boolean,
    supportsTrackerMutation: Boolean,
    onTogglePasskeyVisibility: () -> Unit,
    onCopyTracker: (TorrentTracker) -> Unit,
    onEditTracker: (TorrentTracker, String) -> Unit,
    onDeleteTracker: (TorrentTracker) -> Unit,
) {
    if (detailLoading && detailTrackers.isEmpty()) {
        Text(
            stringResource(R.string.loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.detail_tracker_count,
                detailTrackers.size,
                detailTrackers.size,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasMutableTrackers) {
            TextButton(onClick = onTogglePasskeyVisibility) {
                Icon(
                    painter = painterResource(
                        id = if (trackersPasskeyVisible) R.drawable.ic_password_hidden else R.drawable.ic_password_visible,
                    ),
                    contentDescription = stringResource(
                        if (trackersPasskeyVisible) R.string.detail_hide_passkey else R.string.detail_show_passkey,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (detailTrackers.isEmpty()) {
        Text(
            text = stringResource(R.string.no_tracker_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else DetailListGroup {
        detailTrackers.forEachIndexed { index, tracker ->
            if (index > 0) DetailListDivider()
            val trackerUrl = tracker.url
            val isMutableTracker = isMutableTrackerUrl(trackerUrl)
            val canMutateTracker =
                !isPending &&
                    supportsTrackerMutation &&
                    isMutableTracker &&
                    trackerUrl.isNotBlank()
            TrackerInfoCard(
                tracker = tracker,
                displayUrl = when {
                    !isMutableTracker -> trackerUrl.ifBlank { "-" }
                    trackersPasskeyVisible -> trackerUrl.ifBlank { "-" }
                    else -> maskTrackerUrl(trackerUrl)
                },
                allowMutation = supportsTrackerMutation && isMutableTracker,
                onCopy = if (!isPending && isMutableTracker && trackerUrl.isNotBlank()) {
                    { onCopyTracker(tracker) }
                } else {
                    null
                },
                onEdit = if (canMutateTracker) {
                    { onEditTracker(tracker, trackerUrl) }
                } else {
                    null
                },
                onDelete = if (canMutateTracker) {
                    { onDeleteTracker(tracker) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
internal fun TorrentDetailPeersTab(
    peerOverviewItems: List<Pair<String, String>>,
    peers: List<TorrentPeer>,
    detailLoading: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DetailListGroup {
            peerOverviewItems.forEachIndexed { index, (label, value) ->
                if (index > 0) DetailListDivider()
                DetailValueRow(label = label, value = value)
            }
        }
        // The connected peers themselves (the tab used to show only the counts).
        Text(
            text = pluralStringResource(R.plurals.detail_peers_connected_fmt, peers.size, peers.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
        if (peers.isEmpty() && !detailLoading) {
            Text(
                text = stringResource(R.string.detail_peers_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (peers.isNotEmpty()) {
            DetailListGroup {
                peers.take(MAX_LISTED_PEERS).forEachIndexed { index, peer ->
                    if (index > 0) DetailListDivider()
                    TorrentPeerRow(peer)
                }
            }
        }
    }
}

@Composable
private fun TorrentPeerRow(peer: TorrentPeer) {
    val idle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (peer.countryCode.isNotBlank()) {
                Text(
                    text = peer.countryCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(
                text = peer.address,
                modifier = Modifier.weight(1f),
                style = QbNumberStyles.small,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${(peer.progress * 100).toInt()}%",
                style = QbNumberStyles.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = peer.client.ifBlank { "-" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "↑ ${formatSpeed(peer.uploadSpeed)}",
                style = QbNumberStyles.small,
                fontSize = 11.sp,
                color = if (peer.uploadSpeed > 0L) QbTransferColors.Upload else idle,
            )
            Text(
                text = "↓ ${formatSpeed(peer.downloadSpeed)}",
                style = QbNumberStyles.small,
                fontSize = 11.sp,
                color = if (peer.downloadSpeed > 0L) QbTransferColors.Download else idle,
            )
        }
    }
}

@Composable
private fun DetailValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TorrentDetailFilesTab(
    detailLoading: Boolean,
    detailFiles: List<TorrentFileInfo>,
    fileBrowserPath: List<String>,
    fileBrowserSelection: TorrentFileBrowserSelection,
    onOpenRoot: () -> Unit,
    onOpenPathSegment: (Int) -> Unit,
    onOpenDirectory: (TorrentFileTreeNode) -> Unit,
    onSetFilePriority: (List<Int>, FilePriority) -> Unit = { _, _ -> },
    actionsEnabled: Boolean = true,
) {
    if (detailLoading) {
        Text(
            stringResource(R.string.loading_files),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (detailFiles.isEmpty()) {
        Text(
            stringResource(R.string.no_file_details),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        // Breadcrumb as quiet text; the current folder is the only bright segment.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            item {
                FileBreadcrumb(
                    text = stringResource(R.string.detail_files_root),
                    current = fileBrowserPath.isEmpty(),
                    onClick = onOpenRoot,
                )
            }
            fileBrowserPath.forEachIndexed { index, segment ->
                item {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp),
                    )
                }
                item {
                    FileBreadcrumb(
                        text = segment,
                        current = index == fileBrowserPath.lastIndex,
                        onClick = { onOpenPathSegment(index) },
                    )
                }
            }
        }
        DetailListGroup {
          fileBrowserSelection.node.children.forEachIndexed { index, node ->
            if (index > 0) DetailListDivider()
            TorrentFileBrowserNodeCard(
                node = node,
                onOpenDirectory = {
                    if (node.isDirectory) {
                        onOpenDirectory(node)
                    }
                },
                actionsEnabled = actionsEnabled,
                onSetPriority = { priority -> onSetFilePriority(node.fileIndexes, priority) },
            )
          }
        }
    }
}

@Composable
private fun FileBreadcrumb(text: String, current: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
private fun TorrentFileBrowserNodeCard(
    node: TorrentFileTreeNode,
    onOpenDirectory: () -> Unit,
    actionsEnabled: Boolean = true,
    onSetPriority: (FilePriority) -> Unit = {},
) {
    val complete = node.progress >= 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = node.isDirectory, onClick = onOpenDirectory)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (node.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
            contentDescription = stringResource(
                if (node.isDirectory) R.string.detail_files_folder_chip else R.string.detail_files_file_chip,
            ),
            tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (node.isDirectory) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (node.isDirectory) {
                    pluralStringResource(
                        R.plurals.detail_files_folder_summary,
                        node.fileCount,
                        node.fileCount,
                        formatBytes(node.size),
                        formatPercent(node.progress),
                    )
                } else {
                    stringResource(
                        R.string.detail_files_file_summary,
                        formatBytes(node.size),
                        formatPercent(node.progress),
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!complete) {
                LinearProgressIndicator(
                    progress = { node.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = QbTransferColors.Download,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
        if (node.fileIndexes.isNotEmpty()) {
            FilePriorityMenu(
                priority = node.priority,
                enabled = actionsEnabled,
                onSelect = onSetPriority,
            )
        }
        if (node.isDirectory) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun filePriorityLabel(priority: FilePriority?): String = when (priority) {
    FilePriority.SKIP -> stringResource(R.string.file_priority_skip)
    FilePriority.NORMAL -> stringResource(R.string.file_priority_normal)
    FilePriority.HIGH -> stringResource(R.string.file_priority_high)
    null -> stringResource(R.string.file_priority_mixed)
}

/** Priority chip with a menu; on a folder it applies to every file inside. */
@Composable
private fun FilePriorityMenu(
    priority: FilePriority?,
    enabled: Boolean,
    onSelect: (FilePriority) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TorrentMetaChip(
            text = filePriorityLabel(priority),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = when (priority) {
                FilePriority.SKIP -> MaterialTheme.colorScheme.outline
                FilePriority.HIGH -> Color(0xFFE0A458)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            onClick = if (enabled) ({ expanded = true }) else null,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FilePriority.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(filePriorityLabel(option)) },
                    onClick = {
                        expanded = false
                        if (option != priority) onSelect(option)
                    },
                )
            }
        }
    }
}
private const val MAX_LISTED_PEERS = 100
