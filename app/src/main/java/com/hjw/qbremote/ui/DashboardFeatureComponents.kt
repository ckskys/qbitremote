package com.hjw.qbremote.ui
import com.hjw.qbremote.ui.theme.QbNumberStyles
import androidx.compose.foundation.shape.CircleShape
import com.hjw.qbremote.ui.theme.QbTransferColors
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hjw.qbremote.R
import com.hjw.qbremote.data.CachedDashboardServerSnapshot
import com.hjw.qbremote.data.ConnectionSettings
import com.hjw.qbremote.data.ServerBackendType
import com.hjw.qbremote.data.ServerDashboardPreferences
import com.hjw.qbremote.data.ServerProfile
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TransferInfo
import com.hjw.qbremote.ui.theme.qbGlassCardColors
import com.hjw.qbremote.ui.theme.qbGlassChipColor
import com.hjw.qbremote.ui.theme.qbGlassOutlineColor
import com.hjw.qbremote.ui.theme.qbGlassSubtleContainerColor

@Composable
internal fun ServerOverviewCard(
    overviewTitle: String,
    backendType: ServerBackendType,
    serverProfiles: List<ServerProfile>,
    activeProfileId: String?,
    serverVersion: String,
    transferInfo: TransferInfo,
    torrents: List<TorrentInfo>,
    torrentCount: Int,
    showTotals: Boolean,
    showEntryHint: Boolean,
    onCardClick: (() -> Unit)?,
    onDismissEntryHint: () -> Unit,
    onOpenTorrentList: () -> Unit,
    onOpenFilteredTorrentList: (TorrentStateFilter) -> Unit,
    onSwitchServerProfile: (String) -> Unit,
    onEditServerProfile: (String) -> Unit,
    onRequestDeleteServerProfile: (String) -> Unit,
    swipeActionsEnabled: Boolean,
    showSwipeHint: Boolean,
    onDismissSwipeHint: () -> Unit,
    altSpeed: AltSpeedControl = AltSpeedControl(),
) {
    val stateSummary = remember(torrents) { buildDashboardStateSummary(torrents) }
    val activeProfile = remember(serverProfiles, activeProfileId) {
        serverProfiles.firstOrNull { it.id == activeProfileId }
    }
    val uploadLimitText = formatRateLimit(
        value = transferInfo.uploadRateLimit,
        unlimitedLabel = stringResource(R.string.limit_unlimited),
    )
    val downloadLimitText = formatRateLimit(
        value = transferInfo.downloadRateLimit,
        unlimitedLabel = stringResource(R.string.limit_unlimited),
    )
    val statusUploadingLabel = stringResource(R.string.status_uploading)
    val statusDownloadingLabel = stringResource(R.string.status_downloading)
    val statusPausedLabel = stringResource(R.string.status_paused)
    val statusErrorLabel = stringResource(R.string.status_error)
    val statusCheckingLabel = stringResource(R.string.status_checking)
    val statusWaitingLabel = stringResource(R.string.status_waiting)
    val statusTotalLabel = stringResource(R.string.status_total_torrents)
    val statusPills = remember(
        stateSummary,
        torrentCount,
        statusUploadingLabel,
        statusDownloadingLabel,
        statusPausedLabel,
        statusErrorLabel,
        statusCheckingLabel,
        statusWaitingLabel,
        statusTotalLabel,
    ) {
        listOf(
            DashboardStatusPillItem(
                label = statusUploadingLabel,
                count = stateSummary.uploadingCount,
                accentColor = Color(0xFF3BBA6F),
                filter = TorrentStateFilter.UPLOADING,
            ),
            DashboardStatusPillItem(
                label = statusDownloadingLabel,
                count = stateSummary.downloadingCount,
                accentColor = Color(0xFF3990FF),
                filter = TorrentStateFilter.DOWNLOADING,
            ),
            DashboardStatusPillItem(
                label = statusPausedLabel,
                count = stateSummary.pausedCount,
                accentColor = Color(0xFF8D98A8),
                filter = TorrentStateFilter.PAUSED,
            ),
            DashboardStatusPillItem(
                label = statusErrorLabel,
                count = stateSummary.errorCount,
                accentColor = Color(0xFFE1493D),
                filter = TorrentStateFilter.ERROR,
            ),
            DashboardStatusPillItem(
                label = statusCheckingLabel,
                count = stateSummary.checkingCount,
                accentColor = Color(0xFFE1A22B),
                filter = TorrentStateFilter.CHECKING,
            ),
            DashboardStatusPillItem(
                label = statusWaitingLabel,
                count = stateSummary.waitingCount,
                accentColor = Color(0xFFA674E8),
                filter = TorrentStateFilter.WAITING,
            ),
            DashboardStatusPillItem(
                label = statusTotalLabel,
                count = torrentCount,
                accentColor = Color(0xFF11A9B5),
                filter = TorrentStateFilter.ALL,
            ),
        )
    }
    val actionWidth = 104.dp
    val actionWidthPx = with(LocalDensity.current) { 104.dp.toPx() }
    var revealOffset by rememberSaveable(activeProfileId) { mutableFloatStateOf(0f) }
    val animatedRevealOffset by animateFloatAsState(
        targetValue = revealOffset,
        label = "serverOverviewRevealOffset",
    )
    LaunchedEffect(activeProfile?.id, swipeActionsEnabled) {
        revealOffset = 0f
    }
    val showActionRail = swipeActionsEnabled &&
        activeProfile != null &&
        animatedRevealOffset <= -(actionWidthPx * 0.42f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape),
    ) {
        if (showActionRail) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(actionWidth),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ServerOverviewActionButton(
                        iconRes = R.drawable.ic_action_edit,
                        description = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            revealOffset = 0f
                            onEditServerProfile(activeProfile.id)
                        },
                    )
                    ServerOverviewActionButton(
                        iconRes = R.drawable.ic_action_delete,
                        description = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            revealOffset = 0f
                            onRequestDeleteServerProfile(activeProfile.id)
                        },
                    )
                }
            }
        }

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = animatedRevealOffset }
                .pointerInput(activeProfile?.id) {
                    if (activeProfile == null || !swipeActionsEnabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (showSwipeHint) {
                                onDismissSwipeHint()
                            }
                            revealOffset = (revealOffset + dragAmount).coerceIn(-actionWidthPx, 0f)
                        },
                        onDragEnd = {
                            revealOffset = if (revealOffset <= -(actionWidthPx * 0.45f)) {
                                -actionWidthPx
                            } else {
                                0f
                            }
                        },
                    )
                }
                .then(
                    if (onCardClick != null) {
                        Modifier.clickable {
                            if (revealOffset < 0f) {
                                revealOffset = 0f
                            } else {
                                onCardClick()
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            shape = PanelShape,
            border = BorderStroke(1.dp, qbGlassOutlineColor(defaultAlpha = 0.28f)),
            colors = qbGlassCardColors(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(id = backendOverviewIconRes(backendType)),
                        contentDescription = overviewTitle,
                        modifier = Modifier.size(34.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = overviewTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.server_version_fmt, serverVersion),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val altMode = activeProfileId?.let { altSpeed.modes[it] }
                    if (altMode != null) {
                        AltSpeedToggle(
                            state = if (altMode) AltSpeedState.ON else AltSpeedState.OFF,
                            busy = activeProfileId in altSpeed.togglingIds,
                            onClick = { altSpeed.onToggle(activeProfileId) },
                        )
                    }
                    TextButton(onClick = onOpenTorrentList) {
                        Text(
                            text = stringResource(R.string.dashboard_open_torrents),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (serverProfiles.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        items(serverProfiles, key = { it.id }) { profile ->
                            ServerProfileSummaryCard(
                                profile = profile,
                                active = profile.id == activeProfileId,
                                addressText = buildServerAddressText(
                                    ConnectionSettings(
                                        host = profile.host,
                                        port = profile.port,
                                        useHttps = profile.useHttps,
                                    ),
                                ),
                                summaryText = if (profile.id == activeProfileId) {
                                    stringResource(
                                        R.string.server_summary_speed_fmt,
                                        formatSpeed(transferInfo.uploadSpeed),
                                        formatSpeed(transferInfo.downloadSpeed),
                                    )
                                } else {
                                    stringResource(R.string.server_profile_saved)
                                },
                                onSwitch = {
                                    revealOffset = 0f
                                    onSwitchServerProfile(profile.id)
                                },
                                onEdit = {
                                    revealOffset = 0f
                                    onEditServerProfile(profile.id)
                                },
                                onDelete = {
                                    revealOffset = 0f
                                    onRequestDeleteServerProfile(profile.id)
                                },
                            )
                        }
                    }
                }

                if (altSpeed.showHint && activeProfileId?.let { it in altSpeed.modes } == true) {
                    AltSpeedHintCard(onDismiss = altSpeed.onDismissHint)
                }

                if (showEntryHint) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DashboardEntryHintBubble(
                            text = stringResource(R.string.dashboard_open_torrents_hint),
                            dismissDescription = stringResource(R.string.dismiss_hint),
                            onDismiss = onDismissEntryHint,
                        )
                    }
                }
                if (showSwipeHint) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DashboardEntryHintBubble(
                            text = stringResource(R.string.dashboard_swipe_hint),
                            dismissDescription = stringResource(R.string.dismiss_hint),
                            onDismiss = onDismissSwipeHint,
                        )
                    }
                }

                DashboardSecondaryStatsBlock(
                    uploadSpeedText = formatSpeed(transferInfo.uploadSpeed),
                    downloadSpeedText = formatSpeed(transferInfo.downloadSpeed),
                    uploadLimitText = uploadLimitText,
                    downloadLimitText = downloadLimitText,
                    showTotals = showTotals,
                    totalDownloadedText = formatBytes(transferInfo.downloadedTotal),
                    totalUploadedText = formatBytes(transferInfo.uploadedTotal),
                )
                // Wraps instead of scrolling sideways: a pill cut off at the card
                // edge ("Error 0") gave no hint that the row could be scrolled.
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    statusPills.forEach { pill ->
                        key(pill.label) {
                            DashboardStatusPill(
                                label = pill.label,
                                count = pill.count,
                                accentColor = pill.accentColor,
                                onClick = { onOpenFilteredTorrentList(pill.filter) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MultiServerDashboardSection(
    aggregate: DashboardAggregateState,
    snapshots: List<CachedDashboardServerSnapshot>,
    draggingProfileId: String?,
    settlingProfileId: String?,
    draggingOffsetY: () -> Float,
    settlingOffsetY: () -> Float,
    draggingTargetIndex: Int,
    dragSession: VerticalReorderSession<String>?,
    showReorderHint: Boolean,
    onDismissReorderHint: () -> Unit,
    onStartDrag: (String) -> Unit,
    onDragDelta: (String, Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onOpenServerDashboard: (String) -> Unit,
    onOpenGlobalSpeedLimits: () -> Unit,
    altSpeed: AltSpeedControl = AltSpeedControl(),
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DashboardAggregateOverviewCard(
            aggregate = aggregate,
            onOpenGlobalSpeedLimits = onOpenGlobalSpeedLimits,
            altSpeed = altSpeed,
        )
        WalletServerCardStack(
            snapshots = snapshots,
            draggingProfileId = draggingProfileId,
            settlingProfileId = settlingProfileId,
            draggingOffsetY = draggingOffsetY,
            settlingOffsetY = settlingOffsetY,
            draggingTargetIndex = draggingTargetIndex,
            dragSession = dragSession,
            showReorderHint = showReorderHint,
            onDismissReorderHint = onDismissReorderHint,
            onStartDrag = onStartDrag,
            onDragDelta = onDragDelta,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
            onOpenServerDashboard = onOpenServerDashboard,
        )
    }
}

@Composable
private fun DashboardAggregateOverviewCard(
    aggregate: DashboardAggregateState,
    onOpenGlobalSpeedLimits: () -> Unit,
    altSpeed: AltSpeedControl = AltSpeedControl(),
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        colors = qbGlassCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: one quiet label line, speed-limit action tucked at the top right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_server_speed_title) + " · " +
                        pluralStringResource(
                            R.plurals.dashboard_server_speed_online_count,
                            aggregate.totalServerCount,
                            aggregate.totalServerCount,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (altSpeed.modes.isNotEmpty()) {
                    val modes = altSpeed.modes.values
                    AltSpeedToggle(
                        state = when {
                            modes.all { it } -> AltSpeedState.ON
                            modes.any { it } -> AltSpeedState.PARTIAL
                            else -> AltSpeedState.OFF
                        },
                        busy = altSpeed.togglingIds.isNotEmpty(),
                        onClick = altSpeed.onToggleAll,
                    )
                }
                TextButton(onClick = onOpenGlobalSpeedLimits) {
                    Text(
                        text = stringResource(R.string.global_speed_limit_action),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (altSpeed.showHint && altSpeed.modes.isNotEmpty()) {
                Box(modifier = Modifier.padding(end = 12.dp)) {
                    AltSpeedHintCard(onDismiss = altSpeed.onDismissHint)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AggregateSpeedFigure(
                    label = stringResource(R.string.sort_upload_speed),
                    value = formatSpeed(aggregate.transferInfo.uploadSpeed),
                    accent = QbTransferColors.Upload,
                    modifier = Modifier.weight(1f),
                )
                AggregateSpeedFigure(
                    label = stringResource(R.string.sort_download_speed),
                    value = formatSpeed(aggregate.transferInfo.downloadSpeed),
                    accent = QbTransferColors.Download,
                    modifier = Modifier.weight(1f),
                )
            }
            InlineRealtimeSpeedChart(
                aggregate = aggregate,
                modifier = Modifier.padding(end = 12.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun AggregateSpeedFigure(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val parts = splitSpeedDisplayParts(value)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accent, CircleShape),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = parts.value,
                style = QbNumberStyles.display,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (parts.unit.isNotEmpty()) {
                Text(
                    text = " " + parts.unit,
                    style = QbNumberStyles.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
    }
}

@Composable
internal fun ServerDashboardCardManagerSheet(
    availableCards: List<DashboardChartCard>,
    preferences: ServerDashboardPreferences,
    onToggleCard: (DashboardChartCard, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_manage_cards_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        availableCards.forEach { card ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dashboardChartCardLabel(card),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = preferences.visibleCards.contains(card.storageKey),
                    onCheckedChange = { checked -> onToggleCard(card, checked) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.dashboard_manage_cards_reset))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
internal fun backendLabel(backendType: ServerBackendType): String {
    return when (backendType) {
        ServerBackendType.QBITTORRENT -> stringResource(R.string.backend_qbittorrent)
        ServerBackendType.TRANSMISSION -> stringResource(R.string.backend_transmission)
    }
}

private fun backendOverviewIconRes(backendType: ServerBackendType): Int {
    return when (backendType) {
        ServerBackendType.QBITTORRENT -> R.drawable.ic_backend_qbittorrent
        ServerBackendType.TRANSMISSION -> R.drawable.ic_backend_transmission
    }
}

@Composable
private fun ServerOverviewActionButton(
    iconRes: Int,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ServerProfileSummaryCard(
    profile: ServerProfile,
    active: Boolean,
    addressText: String,
    summaryText: String,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.width(214.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
            } else {
                qbGlassOutlineColor(defaultAlpha = 0.28f)
            },
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
            } else {
                qbGlassSubtleContainerColor()
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = addressText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (active) {
                        stringResource(R.string.server_profile_active)
                    } else {
                        stringResource(R.string.server_profile_tap_to_connect)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = summaryText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSwitch) {
                    Text(
                        text = if (active) {
                            stringResource(R.string.dashboard_open_torrents)
                        } else {
                            stringResource(R.string.connect)
                        },
                    )
                }
                Row {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@Composable
internal fun DashboardEntryHintBubble(
    text: String,
    dismissDescription: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = qbGlassChipColor(),
                shape = RoundedCornerShape(14.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = dismissDescription }
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_action_close),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun DashboardSecondaryStatsBlock(
    uploadSpeedText: String,
    downloadSpeedText: String,
    uploadLimitText: String,
    downloadLimitText: String,
    showTotals: Boolean,
    totalDownloadedText: String,
    totalUploadedText: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DashboardSpeedMetricPanel(
            title = stringResource(R.string.upload),
            directionGlyph = "↑",
            speedText = uploadSpeedText,
            limitText = uploadLimitText,
            totalText = totalUploadedText,
            showTotal = showTotals,
            accentColor = QbTransferColors.Upload,
        )
        DashboardSpeedMetricPanel(
            title = stringResource(R.string.download),
            directionGlyph = "↓",
            speedText = downloadSpeedText,
            limitText = downloadLimitText,
            totalText = totalDownloadedText,
            showTotal = showTotals,
            accentColor = QbTransferColors.Download,
        )
    }
}

private data class SpeedDisplayParts(
    val value: String,
    val unit: String,
)

@Composable
private fun RowScope.DashboardSpeedMetricPanel(
    title: String,
    directionGlyph: String,
    speedText: String,
    limitText: String,
    totalText: String,
    showTotal: Boolean,
    accentColor: Color,
) {
    val speedParts = remember(speedText) { splitSpeedDisplayParts(speedText) }
    val speedDescription = if (directionGlyph == "↑") {
        stringResource(R.string.global_up_fmt, speedText)
    } else {
        stringResource(R.string.global_down_fmt, speedText)
    }
    val totalDescription = if (showTotal) {
        if (directionGlyph == "↑") {
            stringResource(R.string.global_total_up_fmt, totalText)
        } else {
            stringResource(R.string.global_total_down_fmt, totalText)
        }
    } else {
        ""
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .semantics {
                contentDescription = listOf(speedDescription, totalDescription)
                    .filter { it.isNotBlank() }
                    .joinToString("，")
            }
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = directionGlyph,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = speedParts.value,
                style = QbNumberStyles.display.copy(fontSize = 24.sp, lineHeight = 30.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (speedParts.unit.isNotBlank()) {
                Text(
                    text = speedParts.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.limit_value_fmt, limitText),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showTotal) {
            Text(
                text = stringResource(R.string.total_value_fmt, totalText),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
            )
        }
    }
}

private fun splitSpeedDisplayParts(speedText: String): SpeedDisplayParts {
    val normalized = speedText.trim()
    val splitIndex = normalized.lastIndexOf(' ')
    return if (splitIndex in 1 until normalized.lastIndex) {
        SpeedDisplayParts(
            value = normalized.substring(0, splitIndex),
            unit = normalized.substring(splitIndex + 1),
        )
    } else {
        SpeedDisplayParts(value = normalized, unit = "")
    }
}

@Composable
private fun DashboardStatusPill(
    label: String,
    count: Int,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accentColor, RoundedCornerShape(99.dp)),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun formatRateLimit(value: Long, unlimitedLabel: String): String {
    return if (value <= 0L) {
        unlimitedLabel
    } else {
        formatSpeed(value)
    }
}

