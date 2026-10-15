package com.hjw.qbremote.ui

import com.hjw.qbremote.ui.theme.QbNumberStyles
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import com.hjw.qbremote.R
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.ui.theme.QbTransferColors

/** qB reports 8640000 (100 days) for "no ETA"; Transmission uses negative values. */
private const val ETA_UNKNOWN_SECONDS = 8_640_000L

internal fun formatEta(etaSeconds: Long): String? {
    if (etaSeconds <= 0L || etaSeconds >= ETA_UNKNOWN_SECONDS) return null
    val hours = etaSeconds / 3600
    val minutes = (etaSeconds % 3600) / 60
    val seconds = etaSeconds % 60
    return when {
        hours >= 24 -> "${hours / 24}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/**
 * Status at a glance plus the two most-used actions, at the top of the detail page.
 * Pause/Delete used to sit at the very bottom of the Info tab, two screens down.
 */
@Composable
internal fun TorrentDetailStatusHeader(
    torrent: TorrentInfo,
    paused: Boolean,
    isPending: Boolean,
    onPauseResume: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val state = effectiveTorrentState(torrent)
    val stateStyle = torrentStateStyle(state)
    val complete = torrent.progress >= 1f
    // Animate between refreshes instead of jumping.
    val progress by animateFloatAsState(
        targetValue = torrent.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "detailProgress",
    )
    val separatorColor = MaterialTheme.colorScheme.outline
    val statusParts = buildList {
        add("${(torrent.progress * 100).toInt()}%")
        formatEta(torrent.eta)?.takeIf { !complete }?.let { add(stringResource(R.string.torrent_row_eta_fmt, it)) }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(stateStyle.progressColor, CircleShape),
                )
                Text(
                    text = buildAnnotatedString {
                        append(localizedTorrentStateLabel(state))
                        statusParts.forEach { part ->
                            withStyle(SpanStyle(color = separatorColor)) { append("  ·  ") }
                            append(part)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = stateStyle.progressColor.copy(alpha = if (complete) 0.55f else 1f),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }

        // Four key numbers in one quiet grid.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DetailStat(
                label = stringResource(R.string.detail_stat_upload),
                value = formatSpeed(torrent.uploadSpeed),
                valueColor = if (torrent.uploadSpeed > 0L) QbTransferColors.Upload else null,
                modifier = Modifier.weight(1f),
            )
            DetailStat(
                label = stringResource(R.string.detail_stat_download),
                value = formatSpeed(torrent.downloadSpeed),
                valueColor = if (torrent.downloadSpeed > 0L) QbTransferColors.Download else null,
                modifier = Modifier.weight(1f),
            )
            DetailStat(
                label = stringResource(R.string.detail_peer_ratio_label),
                value = formatRatio(torrent.ratio),
                modifier = Modifier.weight(1f),
            )
            DetailStat(
                label = stringResource(R.string.detail_stat_size),
                value = formatBytes(torrent.size),
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onPauseResume,
                enabled = !isPending,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Icon(
                    imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(if (paused) R.string.resume else R.string.pause),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Button(
                onClick = onRequestDelete,
                enabled = !isPending,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(text = stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun DetailStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    // "898.00 KB/s" -> number in mono, unit small and muted.
    val splitIndex = value.lastIndexOf(' ')
    val number = if (splitIndex > 0) value.substring(0, splitIndex) else value
    val unit = if (splitIndex > 0) value.substring(splitIndex + 1) else ""
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildAnnotatedString {
                append(number)
                if (unit.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) { append(" $unit") }
                }
            },
            style = QbNumberStyles.large,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
