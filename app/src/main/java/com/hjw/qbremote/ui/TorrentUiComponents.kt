package com.hjw.qbremote.ui

import androidx.compose.foundation.shape.CircleShape
import com.hjw.qbremote.ui.theme.QbTransferColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hjw.qbremote.R
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TorrentTracker

@Composable
internal fun TrackerInfoCard(
    tracker: TorrentTracker,
    displayUrl: String = tracker.url.ifBlank { "-" },
    allowMutation: Boolean = false,
    onCopy: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val status = trackerStatusLabel(tracker.status)
    val statusColor = trackerStatusColor(tracker.status)
    val message = tracker.message.trim().ifBlank {
        stringResource(R.string.tracker_message_ok)
    }

    // A plain row: the caller groups trackers into one tonal list.
    run {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(statusColor, CircleShape),
                )
                Text(
                    text = "$status · $message",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = displayUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.tracker_stats_fmt,
                    tracker.numPeers,
                    tracker.numSeeds,
                    tracker.numLeeches,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onCopy != null || (allowMutation && (onEdit != null || onDelete != null))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onCopy != null) {
                        TextButton(onClick = onCopy) {
                            Text(stringResource(R.string.copy))
                        }
                    }
                    if (allowMutation && onEdit != null) {
                        TextButton(onClick = onEdit) {
                            Text(stringResource(R.string.edit))
                        }
                    }
                    if (allowMutation && onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text(
                                text = stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun trackerStatusLabel(status: Int): String {
    return when (status) {
        0 -> stringResource(R.string.tracker_status_disabled)
        1 -> stringResource(R.string.tracker_status_not_contacted)
        2 -> stringResource(R.string.tracker_status_working)
        3 -> stringResource(R.string.tracker_status_updating)
        4 -> stringResource(R.string.tracker_status_not_working)
        else -> stringResource(R.string.state_unknown)
    }
}

internal fun trackerStatusColor(status: Int): Color {
    return when (status) {
        0 -> Color(0xFF9E9E9E)
        1 -> Color(0xFF90A4AE)
        2 -> Color(0xFF4CAF50)
        3 -> Color(0xFFFFC107)
        4 -> Color(0xFFE53935)
        else -> Color(0xFF607D8B)
    }
}

internal fun parseTags(input: String): List<String> {
    return input
        .split(',', ';', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

internal fun matchesTorrentSearch(torrent: TorrentInfo, query: String): Boolean {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return true

    return listOf(
        torrent.name,
        torrent.hash,
        torrent.category,
        torrent.tags,
        torrent.savePath,
        torrent.tracker,
    ).any { field ->
        field.lowercase().contains(normalizedQuery)
    }
}

internal fun toggleTag(current: String, option: String): String {
    val tags = parseTags(current).toMutableList()
    val idx = tags.indexOfFirst { it.equals(option, ignoreCase = false) }
    if (idx >= 0) {
        tags.removeAt(idx)
    } else {
        tags.add(option)
    }
    return tags.joinToString(",")
}

internal data class TorrentStateStyle(
    val borderColor: Color,
    val progressColor: Color,
    val tagContainer: Color,
    val tagContent: Color,
)

@Composable
internal fun torrentStateStyle(state: String): TorrentStateStyle {
    val normalized = normalizeTorrentState(state)
    val base = when (normalized) {
        "error", "missingfiles" -> Color(0xFFE5676B)
        "downloading", "stalleddl", "forceddl" -> QbTransferColors.Download
        "uploading", "stalledup", "forcedup" -> QbTransferColors.Upload
        "pauseddl", "pausedup", "stoppeddl", "stoppedup" -> Color(0xFF7A828E)
        "queueddl", "queuedup", "checkingdl", "checkingup", "checkingresumedata", "metadl", "forcedmetadl", "allocating", "moving" -> Color(0xFFE0A458)
        else -> Color(0xFF8B94A1)
    }
    return TorrentStateStyle(
        borderColor = base,
        progressColor = base,
        tagContainer = base.copy(alpha = 0.14f),
        tagContent = base,
    )
}

@Composable
internal fun TorrentMetaChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .background(containerColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun localizedTorrentStateLabel(state: String): String {
    return when (normalizeTorrentState(state)) {
        "downloading", "stalleddl" -> stringResource(R.string.state_downloading)
        "uploading", "stalledup" -> stringResource(R.string.state_seeding)
        "pauseddl", "pausedup" -> stringResource(R.string.state_paused)
        "error", "missingfiles" -> stringResource(R.string.state_error)
        "queueddl", "queuedup" -> stringResource(R.string.state_queued)
        "metadl", "forcedmetadl" -> stringResource(R.string.state_metadata)
        "checkingdl", "checkingup", "checkingresumedata" -> stringResource(R.string.state_checking)
        "allocating", "moving" -> stringResource(R.string.state_preparing)
        "stoppeddl", "stoppedup" -> stringResource(R.string.state_stopped)
        "forceddl", "forcedup" -> stringResource(R.string.state_forced)
        "unknown", "" -> stringResource(R.string.state_unknown)
        else -> stringResource(R.string.state_unknown)
    }
}

internal fun isPausedState(state: String): Boolean {
    return normalizeTorrentState(state) in setOf("pauseddl", "pausedup", "stoppeddl", "stoppedup")
}

internal fun effectiveTorrentState(torrent: TorrentInfo): String {
    val normalized = normalizeTorrentState(torrent.state)
    if (normalized.isNotBlank() && normalized != "unknown") return normalized
    if (torrent.uploadSpeed > 0L) return "uploading"
    if (torrent.downloadSpeed > 0L) return "downloading"
    if (torrent.progress >= 1f && (torrent.uploaded > 0L || torrent.downloaded > 0L || torrent.size > 0L)) {
        return "stalledup"
    }
    if (torrent.progress > 0f || torrent.downloaded > 0L) return "stalleddl"
    return if (normalized.isBlank()) "unknown" else normalized
}

internal fun normalizeTorrentState(state: String): String {
    return state.trim().lowercase()
}

internal fun compactTagsLabel(tags: String, noTagsText: String): String {
    val normalizedTags = tags
        .split(',', ';', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "-" && !it.equals("null", ignoreCase = true) }

    if (normalizedTags.isEmpty()) return noTagsText
    if (normalizedTags.size <= 2) return normalizedTags.joinToString(",")

    val preview = normalizedTags.take(2).joinToString(",")
    return "$preview +${normalizedTags.size - 2}"
}

