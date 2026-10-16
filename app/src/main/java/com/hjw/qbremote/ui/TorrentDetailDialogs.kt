package com.hjw.qbremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hjw.qbremote.R
import com.hjw.qbremote.data.model.TorrentTracker
import com.hjw.qbremote.ui.theme.qbGlassStrongContainerColor

// Confirmation and edit dialogs used by the torrent detail page.

@Composable
internal fun TorrentDeleteDialog(
    visible: Boolean,
    deleteFilesChecked: Boolean,
    isPending: Boolean,
    onDeleteFilesCheckedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PanelShape,
        containerColor = qbGlassStrongContainerColor(),
        title = { Text(stringResource(R.string.delete_torrent_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.delete_torrent_desc))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = deleteFilesChecked,
                        onCheckedChange = onDeleteFilesCheckedChange,
                    )
                    Text(stringResource(R.string.delete_files))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isPending,
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun TorrentEditTrackerDialog(
    tracker: TorrentTracker?,
    trackerUrl: String,
    isPending: Boolean,
    onTrackerUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (TorrentTracker) -> Unit,
) {
    if (tracker == null) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PanelShape,
        containerColor = qbGlassStrongContainerColor(),
        title = { Text(stringResource(R.string.detail_tracker_edit_title)) },
        text = {
            OutlinedTextField(
                value = trackerUrl,
                onValueChange = onTrackerUrlChange,
                label = { Text(stringResource(R.string.detail_tracker_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isPending,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(tracker) },
                enabled = !isPending && trackerUrl.trim().isNotBlank(),
            ) {
                Text(stringResource(R.string.server_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun TorrentDeleteTrackerDialog(
    tracker: TorrentTracker?,
    isPending: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (TorrentTracker) -> Unit,
) {
    if (tracker == null) {
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PanelShape,
        containerColor = qbGlassStrongContainerColor(),
        title = { Text(stringResource(R.string.detail_tracker_delete_title)) },
        text = { Text(stringResource(R.string.detail_tracker_delete_desc)) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(tracker) },
                enabled = !isPending,
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
