package com.hjw.qbremote.ui

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import com.hjw.qbremote.ui.theme.QbNumberStyles
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.hjw.qbremote.R
import com.hjw.qbremote.data.ServerCapabilities
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.ui.theme.qbGlassStrongContainerColor

// Torrent detail "Info" tab: grouped list rows, each opening a focused edit dialog.

@Composable
internal fun TorrentDetailInfoTab(
    torrent: TorrentInfo,
    capabilities: ServerCapabilities,
    isPending: Boolean,
    magnetUri: String,
    categoryOptions: List<String>,
    tagOptions: List<String>,
    paused: Boolean,
    renameText: String,
    locationText: String,
    categoryTextInput: String,
    tagsTextInput: String,
    downloadLimitText: String,
    uploadLimitText: String,
    ratioText: String,
    onRenameTextChange: (String) -> Unit,
    onLocationTextChange: (String) -> Unit,
    onCategoryTextChange: (String) -> Unit,
    onTagsTextChange: (String) -> Unit,
    onDownloadLimitTextChange: (String) -> Unit,
    onUploadLimitTextChange: (String) -> Unit,
    onRatioTextChange: (String) -> Unit,
    onCopyHash: () -> Unit,
    onCopyMagnet: () -> Unit,
    onExportTorrent: () -> Unit,
    onRename: () -> Unit,
    onSetLocation: () -> Unit,
    onSetCategory: () -> Unit,
    onSetTags: () -> Unit,
    onSetSpeedLimit: () -> Unit,
    onSetShareRatio: () -> Unit,
    onReannounce: () -> Unit,
    onRecheck: () -> Unit,
    onApplyCategory: (String) -> Unit,
    onApplyTags: (String) -> Unit,
) {
    // Settings read as a list; tapping a row opens a focused editor instead of
    // keeping a text field + button pair inline for every property.
    var editing by remember(torrent.hash) { mutableStateOf<TorrentInfoEditField?>(null) }
    val actionsEnabled = !isPending && torrent.hash.isNotBlank()
    val unlimited = stringResource(R.string.limit_unlimited)
    val speedLimitSummary = if (downloadLimitText.isBlank() && uploadLimitText.isBlank()) {
        unlimited
    } else {
        "↓ ${downloadLimitText.ifBlank { "∞" }}  ↑ ${uploadLimitText.ifBlank { "∞" }} KB/s"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailListGroup {
            DetailListRow(
                label = stringResource(R.string.detail_hash_label),
                value = torrent.hash.ifBlank { "-" },
                monoValue = true,
                trailingIcon = Icons.Outlined.ContentCopy,
                trailingDescription = stringResource(R.string.copy),
                enabled = actionsEnabled,
                onClick = onCopyHash,
            )
            DetailListDivider()
            DetailListRow(
                label = stringResource(R.string.detail_magnet_label),
                value = magnetUri.ifBlank { "-" },
                monoValue = true,
                trailingIcon = Icons.Outlined.ContentCopy,
                trailingDescription = stringResource(R.string.copy),
                enabled = !isPending && magnetUri.isNotBlank(),
                onClick = onCopyMagnet,
            )
        }

        DetailListGroup {
            if (capabilities.supportsRename) {
                DetailListRow(
                    label = stringResource(R.string.detail_section_name),
                    value = torrent.name,
                    enabled = !isPending,
                    onClick = {
                        onRenameTextChange(torrent.name)
                        editing = TorrentInfoEditField.Name
                    },
                )
                DetailListDivider()
            }
            DetailListRow(
                label = stringResource(R.string.detail_section_path),
                value = locationText.ifBlank { "-" },
                enabled = !isPending,
                onClick = { editing = TorrentInfoEditField.Path },
            )
            if (capabilities.supportsCategories) {
                DetailListDivider()
                DetailListRow(
                    label = stringResource(R.string.detail_section_category),
                    value = torrent.category.ifBlank { stringResource(R.string.no_category) },
                    enabled = !isPending,
                    onClick = {
                        onCategoryTextChange(torrent.category)
                        editing = TorrentInfoEditField.Category
                    },
                )
            }
            DetailListDivider()
            DetailListRow(
                label = stringResource(R.string.detail_section_tags),
                value = torrent.tags.ifBlank { stringResource(R.string.no_tags) },
                enabled = !isPending,
                onClick = {
                    onTagsTextChange(torrent.tags)
                    editing = TorrentInfoEditField.Tags
                },
            )
            if (capabilities.supportsPerTorrentSpeedLimit) {
                DetailListDivider()
                DetailListRow(
                    label = stringResource(R.string.detail_section_speed_limit),
                    value = speedLimitSummary,
                    enabled = !isPending,
                    onClick = { editing = TorrentInfoEditField.SpeedLimit },
                )
            }
            if (capabilities.supportsShareRatio) {
                DetailListDivider()
                DetailListRow(
                    label = stringResource(R.string.detail_section_ratio),
                    value = ratioText.ifBlank { "-" },
                    enabled = !isPending,
                    onClick = { editing = TorrentInfoEditField.Ratio },
                )
            }
        }

        val primaryActions = torrentDetailPrimaryActions(capabilities)
        if (primaryActions.isNotEmpty()) {
            DetailListGroup {
                primaryActions.forEachIndexed { index, action ->
                    if (index > 0) DetailListDivider()
                    DetailListRow(
                        label = when (action) {
                            TorrentDetailPrimaryAction.ExportTorrent -> stringResource(R.string.detail_export_torrent)
                            TorrentDetailPrimaryAction.Reannounce -> stringResource(R.string.detail_reannounce)
                            TorrentDetailPrimaryAction.Recheck -> stringResource(R.string.detail_recheck)
                        },
                        value = "",
                        trailingIcon = when (action) {
                            TorrentDetailPrimaryAction.ExportTorrent -> Icons.Outlined.FileDownload
                            TorrentDetailPrimaryAction.Reannounce -> Icons.Outlined.Campaign
                            TorrentDetailPrimaryAction.Recheck -> Icons.AutoMirrored.Outlined.FactCheck
                        },
                        trailingDescription = null,
                        enabled = actionsEnabled,
                        onClick = when (action) {
                            TorrentDetailPrimaryAction.ExportTorrent -> onExportTorrent
                            TorrentDetailPrimaryAction.Reannounce -> onReannounce
                            TorrentDetailPrimaryAction.Recheck -> onRecheck
                        },
                    )
                }
            }
        }
    }

    when (editing) {
        TorrentInfoEditField.Name -> DetailEditDialog(
            title = stringResource(R.string.detail_section_name),
            confirmText = stringResource(R.string.detail_action_change),
            onDismiss = { editing = null },
            onConfirm = {
                editing = null
                onRename()
            },
        ) {
            DetailEditField(
                value = renameText,
                onValueChange = onRenameTextChange,
                label = stringResource(R.string.detail_new_name_label),
                singleLine = false,
            )
        }

        TorrentInfoEditField.Path -> DetailEditDialog(
            title = stringResource(R.string.detail_section_path),
            confirmText = stringResource(R.string.detail_action_change),
            onDismiss = { editing = null },
            onConfirm = {
                editing = null
                onSetLocation()
            },
        ) {
            Text(
                text = stringResource(R.string.detail_set_path_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DetailEditField(
                value = locationText,
                onValueChange = onLocationTextChange,
                label = stringResource(R.string.detail_save_path_label),
            )
        }

        TorrentInfoEditField.Category -> DetailEditDialog(
            title = stringResource(R.string.detail_section_category),
            confirmText = stringResource(R.string.detail_action_change),
            onDismiss = { editing = null },
            onConfirm = {
                editing = null
                onSetCategory()
            },
        ) {
            if (categoryOptions.isNotEmpty()) {
                DetailOptionPills(
                    options = categoryOptions,
                    isSelected = { it == categoryTextInput },
                    onToggle = { option ->
                        onCategoryTextChange(if (option == categoryTextInput) "" else option)
                    },
                )
            }
            DetailEditField(
                value = categoryTextInput,
                onValueChange = onCategoryTextChange,
                label = stringResource(R.string.detail_category_label),
            )
        }

        TorrentInfoEditField.Tags -> DetailEditDialog(
            title = stringResource(R.string.detail_section_tags),
            confirmText = stringResource(R.string.detail_action_change),
            onDismiss = { editing = null },
            onConfirm = {
                editing = null
                onSetTags()
            },
        ) {
            if (tagOptions.isNotEmpty()) {
                val selectedTags = parseTags(tagsTextInput)
                DetailOptionPills(
                    options = tagOptions,
                    isSelected = { it in selectedTags },
                    onToggle = { option -> onTagsTextChange(toggleTag(tagsTextInput, option)) },
                )
            }
            DetailEditField(
                value = tagsTextInput,
                onValueChange = onTagsTextChange,
                label = stringResource(R.string.detail_tags_label),
            )
        }

        TorrentInfoEditField.SpeedLimit -> DetailEditDialog(
            title = stringResource(R.string.detail_section_speed_limit),
            confirmText = stringResource(R.string.detail_action_apply),
            onDismiss = { editing = null },
            onConfirm = {
                editing = null
                onSetSpeedLimit()
            },
        ) {
            DetailEditField(
                value = downloadLimitText,
                onValueChange = onDownloadLimitTextChange,
                label = stringResource(R.string.detail_download_kb_label),
                keyboardType = KeyboardType.Number,
            )
            DetailEditField(
                value = uploadLimitText,
                onValueChange = onUploadLimitTextChange,
                label = stringResource(R.string.detail_upload_kb_label),
                keyboardType = KeyboardType.Number,
            )
        }

        TorrentInfoEditField.Ratio -> DetailEditDialog(
            title = stringResource(R.string.detail_section_ratio),
            confirmText = stringResource(R.string.detail_action_apply),
            onDismiss = { editing = null },
            onConfirm = {
                editing = null
                onSetShareRatio()
            },
        ) {
            DetailEditField(
                value = ratioText,
                onValueChange = onRatioTextChange,
                label = stringResource(R.string.detail_ratio_label),
                keyboardType = KeyboardType.Decimal,
            )
        }

        null -> Unit
    }
}

private enum class TorrentInfoEditField { Name, Path, Category, Tags, SpeedLimit, Ratio }

@Composable
internal fun DetailListGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

@Composable
internal fun DetailListDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 14.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    )
}

@Composable
private fun DetailListRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    monoValue: Boolean = false,
    trailingIcon: ImageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
    trailingDescription: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = if (monoValue) QbNumberStyles.small else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = trailingIcon,
            contentDescription = trailingDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DetailEditDialog(
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PanelShape,
        containerColor = qbGlassStrongContainerColor(),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DetailEditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailOptionPills(
    options: List<String>,
    isSelected: (String) -> Boolean,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            key(option) {
                val selected = isSelected(option)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        )
                        .clickable { onToggle(option) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
