package com.hjw.qbremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hjw.qbremote.R
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.ui.theme.qbGlassStrongContainerColor
import kotlinx.coroutines.launch

private val SwipeActionShape = RoundedCornerShape(12.dp)
private val SwipePauseColor = Color(0xFF6D7A8C)
private val SwipeResumeColor = Color(0xFF2FB67C)
private val SwipeDeleteColor = Color(0xFFD9534F)

/**
 * Swipe right to pause/resume, swipe left to delete (asks for confirmation).
 * The card springs back after either swipe; the swipe is a shortcut, not a dismissal.
 */
@Composable
internal fun SwipeableTorrentRow(
    torrent: TorrentInfo,
    enabled: Boolean,
    onPauseResume: (TorrentInfo) -> Unit,
    onRequestDelete: (TorrentInfo) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()
    val paused = isPausedState(effectiveTorrentState(torrent))
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        gesturesEnabled = enabled,
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> onPauseResume(torrent)
                SwipeToDismissBoxValue.EndToStart -> onRequestDelete(torrent)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeActionBackground(
                    color = if (paused) SwipeResumeColor else SwipePauseColor,
                    icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    label = stringResource(if (paused) R.string.resume else R.string.pause),
                    alignment = Alignment.CenterStart,
                )
                SwipeToDismissBoxValue.EndToStart -> SwipeActionBackground(
                    color = SwipeDeleteColor,
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.delete),
                    alignment = Alignment.CenterEnd,
                )
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        // Opaque backing: the card's glass container is translucent, so the action
        // color would otherwise show through the card while it is dragged.
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background, SwipeActionShape)) {
            content()
        }
    }
}

@Composable
private fun SwipeActionBackground(
    color: Color,
    icon: ImageVector,
    label: String,
    alignment: Alignment,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, SwipeActionShape)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Pinned bar shown while the torrent list is in multi-select mode. */
@Composable
internal fun TorrentSelectionBar(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Opaque: the bar is pinned over the list and a translucent one let
            // the card text underneath show through.
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.exit_selection))
        }
        Text(
            text = stringResource(R.string.torrent_selection_count, selectedCount),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onSelectAll, enabled = !allVisibleSelected) {
            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.select_all))
        }
        val hasSelection = selectedCount > 0
        IconButton(onClick = onResume, enabled = hasSelection) {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.resume))
        }
        IconButton(onClick = onPause, enabled = hasSelection) {
            Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.pause))
        }
        IconButton(onClick = onDelete, enabled = hasSelection) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = if (hasSelection) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
    }
}

/** Delete confirmation for one or more torrents from the list. */
@Composable
internal fun ConfirmDeleteTorrentsDialog(
    count: Int,
    deleteFiles: Boolean,
    onDeleteFilesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PanelShape,
        containerColor = qbGlassStrongContainerColor(),
        title = {
            Text(
                if (count == 1) {
                    stringResource(R.string.delete_torrent_title)
                } else {
                    stringResource(R.string.delete_torrents_title_fmt, count)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.delete_torrent_desc))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteFiles, onCheckedChange = onDeleteFilesChange)
                    Text(stringResource(R.string.delete_files))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
