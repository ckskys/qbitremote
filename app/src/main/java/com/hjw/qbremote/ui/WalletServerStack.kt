package com.hjw.qbremote.ui

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawBehind
import com.hjw.qbremote.ui.theme.QbNumberStyles
import androidx.compose.foundation.shape.CircleShape
import com.hjw.qbremote.ui.theme.QbTransferColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.hjw.qbremote.R
import com.hjw.qbremote.data.CachedDashboardServerSnapshot
import kotlin.math.abs

// Home screen server card stack: wallet-style stacked cards with long-press reorder.

private val WalletServerCardHeightAnimationSpec = spring<Dp>(
    dampingRatio = 1f,
    stiffness = 900f,
)

@Composable
internal fun WalletServerCardStack(
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
) {
    val orderedSnapshots = snapshots
    val paletteIndexByProfileId = remember(orderedSnapshots) {
        orderedSnapshots
            .map { it.profileId }
            .distinct()
            .sorted()
            .mapIndexed { index, profileId -> profileId to index }
            .toMap()
    }
    val serverStackGestureKey = remember(orderedSnapshots) {
        orderedSnapshots.joinToString(separator = "|") { it.profileId }
    }
    // Where every card will sit if the drag is dropped now. The front-card
    // presentation and z-order follow this so the stack stays a valid stack
    // mid-drag and the drop commit changes nothing visually.
    val projectedIndexByProfileId = remember(orderedSnapshots, dragSession, draggingTargetIndex) {
        resolveProjectedReorderOrder(
            items = orderedSnapshots.map { it.profileId },
            dragSession = dragSession,
            draggingTargetIndex = draggingTargetIndex,
        ).withIndex().associate { (index, profileId) -> profileId to index }
    }
    val stackHeight = if (orderedSnapshots.isEmpty()) {
        0.dp
    } else {
        HomeServerStackExpandedCardHeight + HomeServerStackExposedHeight * (orderedSnapshots.size - 1)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.server_stack_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.server_stack_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Animated so the stack below slides up instead of jumping when the
            // hint goes away (it is dismissed right after the first reorder).
            AnimatedVisibility(
                visible = showReorderHint,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DashboardEntryHintBubble(
                        text = stringResource(R.string.server_stack_reorder_hint),
                        dismissDescription = stringResource(R.string.dismiss_hint),
                        onDismiss = onDismissReorderHint,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stackHeight),
        ) {
            orderedSnapshots.withIndex().toList().asReversed().forEach { (index, snapshot) ->
                key(snapshot.profileId) {
                    val isSettlingCard = settlingProfileId == snapshot.profileId
                    val projectedIndex = projectedIndexByProfileId[snapshot.profileId] ?: index
                    WalletServerStackCard(
                        snapshot = snapshot,
                        gestureKey = serverStackGestureKey,
                        stackedIndex = index,
                        paletteIndex = paletteIndexByProfileId[snapshot.profileId] ?: index,
                        cardHeight = HomeServerStackExpandedCardHeight,
                        collapsedCardHeight = HomeServerStackCollapsedCardHeight,
                        exposedHeight = HomeServerStackExposedHeight,
                        selected = projectedIndex == 0,
                        zOrderIndex = projectedIndex,
                        stackCount = orderedSnapshots.size,
                        isDragging = draggingProfileId == snapshot.profileId && !isSettlingCard,
                        isSettling = isSettlingCard,
                        dragOffsetY = { if (draggingProfileId == snapshot.profileId && !isSettlingCard) draggingOffsetY() else 0f },
                        settlingOffsetY = { if (isSettlingCard) settlingOffsetY() else 0f },
                        siblingOffsetY = calculateServerStackSiblingOffset(
                            profileId = snapshot.profileId,
                            draggingProfileId = draggingProfileId,
                            draggingTargetIndex = draggingTargetIndex,
                            dragSession = dragSession,
                        ),
                        animateSiblingOffset = dragSession != null,
                        onDragStart = { onStartDrag(snapshot.profileId) },
                        onDragDelta = { deltaY -> onDragDelta(snapshot.profileId, deltaY) },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                        onClick = { onOpenServerDashboard(snapshot.profileId) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun WalletServerStackCard(
    snapshot: CachedDashboardServerSnapshot,
    gestureKey: String,
    stackedIndex: Int,
    paletteIndex: Int,
    cardHeight: Dp,
    collapsedCardHeight: Dp,
    exposedHeight: Dp,
    selected: Boolean,
    stackCount: Int,
    // Draw order slot; during a drag this is the projected slot, not the original.
    zOrderIndex: Int = stackedIndex,
    isDragging: Boolean,
    isSettling: Boolean,
    dragOffsetY: () -> Float,
    settlingOffsetY: () -> Float,
    siblingOffsetY: Float,
    animateSiblingOffset: Boolean,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onClick: () -> Unit,
) {
    val palette = remember(snapshot.profileId, paletteIndex) {
        walletCardPalette(paletteIndex)
    }
    val stateLabel = when (resolveServerSnapshotStatus(snapshot)) {
        ServerSnapshotStatus.LIVE -> stringResource(R.string.server_snapshot_live_state)
        ServerSnapshotStatus.CACHED_OFFLINE -> stringResource(R.string.server_snapshot_stale_state)
        ServerSnapshotStatus.CONNECTING -> stringResource(R.string.server_snapshot_connecting_state)
        ServerSnapshotStatus.UNREACHABLE -> stringResource(R.string.server_snapshot_unreachable_state)
    }
    val exposedStepPx = with(LocalDensity.current) { exposedHeight.toPx() }
    val cornerShape = RoundedCornerShape(16.dp)
    val collapsedShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )
    val uploadLimitText = formatRateLimit(
        value = snapshot.transferInfo.uploadRateLimit,
        unlimitedLabel = stringResource(R.string.limit_unlimited),
    )
    val downloadLimitText = formatRateLimit(
        value = snapshot.transferInfo.downloadRateLimit,
        unlimitedLabel = stringResource(R.string.limit_unlimited),
    )
    val serverNameTextStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
    // Opaque tonal layers: deeper cards in the stack sit one step darker. Must stay
    // opaque (even over a custom background) or stacked cards ghost through.
    val colorScheme = MaterialTheme.colorScheme
    val cardContainerColor = when (stackCount - 1 - stackedIndex) {
        0 -> colorScheme.surfaceContainerHigh
        1 -> lerp(colorScheme.surfaceContainer, colorScheme.surfaceContainerHigh, 0.5f)
        else -> colorScheme.surfaceContainer
    }
    val isLive = resolveServerSnapshotStatus(snapshot) == ServerSnapshotStatus.LIVE
    val clickSuppressionThresholdPx = with(LocalDensity.current) {
        ServerCardClickSuppressionDragThreshold.toPx()
    }
    val presentation = remember(selected, isDragging, isSettling) {
        resolveWalletServerStackCardPresentation(
            selected = selected,
            isDragging = isDragging,
            isSettling = isSettling,
        )
    }
    val cardShape = if (presentation.showExpandedLayout) cornerShape else collapsedShape
    var isPressed by remember(snapshot.profileId) { mutableStateOf(false) }
    var lastDragFinishedAt by remember(snapshot.profileId) { mutableLongStateOf(0L) }
    var dragDistanceSinceStart by remember(snapshot.profileId) { mutableFloatStateOf(0f) }
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)
    val latestOnClick by rememberUpdatedState(onClick)
    val animatedSiblingOffset by animateFloatAsState(
        targetValue = siblingOffsetY,
        animationSpec = if (animateSiblingOffset) {
            ReorderSiblingOffsetAnimationSpec
        } else {
            snap()
        },
        label = "walletServerSiblingOffset",
    )
    // Position comes from the original slot only; movement during a drag is
    // carried by the sibling/drag offsets. (`selected` may already reflect the
    // projected front card, so it must not feed into the base position.)
    val baseTranslationY = (stackCount - 1 - stackedIndex) * exposedStepPx
    val displayHeight by animateDpAsState(
        targetValue = if (presentation.showExpandedLayout) cardHeight else collapsedCardHeight,
        animationSpec = WalletServerCardHeightAnimationSpec,
        label = "walletServerCardHeight",
    )
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed && !isDragging) 0.985f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "walletServerPressedScale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(displayHeight)
            .graphicsLayer {
                translationY = baseTranslationY + when {
                    isDragging || isSettling -> 0f
                    // Hard zero once the reorder session is gone (same fix as the
                    // dashboard cards): animateFloatAsState snaps one frame late, so
                    // right after the order commit the new base position plus the
                    // stale sibling offset drew every card one slot off for a frame.
                    !animateSiblingOffset -> 0f
                    else -> animatedSiblingOffset
                }
                shadowElevation = 0f
            }
            .pointerInput(snapshot.profileId, gestureKey) {
                if (stackCount < 2) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isPressed = false
                        dragDistanceSinceStart = 0f
                        latestOnDragStart()
                    },
                    onDragEnd = {
                        isPressed = false
                        lastDragFinishedAt = resolveServerCardClickSuppressionTimestamp(
                            dragDistanceSinceStart = dragDistanceSinceStart,
                            clickSuppressionThresholdPx = clickSuppressionThresholdPx,
                            currentTimeMillis = SystemClock.elapsedRealtime(),
                        )
                        latestOnDragEnd()
                    },
                    onDragCancel = {
                        isPressed = false
                        lastDragFinishedAt = resolveServerCardClickSuppressionTimestamp(
                            dragDistanceSinceStart = dragDistanceSinceStart,
                            clickSuppressionThresholdPx = clickSuppressionThresholdPx,
                            currentTimeMillis = SystemClock.elapsedRealtime(),
                        )
                        latestOnDragCancel()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDistanceSinceStart += kotlin.math.abs(dragAmount.y)
                        latestOnDragDelta(dragAmount.y)
                    },
                )
            }
            .pointerInput(snapshot.profileId, gestureKey, lastDragFinishedAt) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = {
                        if (
                            shouldSuppressServerCardClick(
                                lastDragFinishedAt = lastDragFinishedAt,
                                currentTimeMillis = SystemClock.elapsedRealtime(),
                            )
                        ) {
                            return@detectTapGestures
                        }
                        isPressed = false
                        latestOnClick()
                    }
                )
            }
            .zIndex(
                // Only the card under the finger floats above the stack. A settling
                // card already uses its final slot's z-order, so clearing the drag
                // state after the settle animation doesn't flip the overlap.
                if (isDragging) {
                    (stackCount + 1).toFloat()
                } else {
                    (stackCount - zOrderIndex).toFloat()
                },
            ),
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = when {
                        isDragging -> dragOffsetY()
                        isSettling -> settlingOffsetY()
                        else -> 0f
                    }
                    scaleX = when {
                        isDragging -> 1f
                        presentation.showExpandedLayout -> 1f
                        else -> 0.992f
                    } * pressedScale
                    scaleY = when {
                        isDragging -> 1f
                        presentation.showExpandedLayout -> 1f
                        else -> 0.992f
                    } * pressedScale
                    shadowElevation = when {
                        isDragging -> 0f
                        selected -> ReorderSelectedShadow
                        else -> ReorderCollapsedShadow
                    }
                    shape = cardShape
                    clip = true
                },
            shape = cardShape,
            border = BorderStroke(
                if (isDragging) 2.dp else 1.dp,
                if (isDragging) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                },
            ),
            colors = CardDefaults.outlinedCardColors(
                containerColor = cardContainerColor,
            ),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        color = palette.accent,
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                    )
                }
                .padding(
                    horizontal = presentation.horizontalPadding,
                    vertical = presentation.verticalPadding,
                ),
        ) {
            if (presentation.showExpandedLayout) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = snapshot.profileName.ifBlank { backendLabel(snapshot.backendType) },
                                style = serverNameTextStyle,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = backendLabel(snapshot.backendType),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            WalletCardStatusLabel(label = stateLabel, live = isLive)
                            WalletCardSpeedLine(
                                uploadSpeed = snapshot.transferInfo.uploadSpeed,
                                downloadSpeed = snapshot.transferInfo.downloadSpeed,
                            )
                        }
                    }
                    
                    WalletCardMetricLine(
                        label = stringResource(R.string.sort_total_uploaded),
                        value = formatBytes(snapshot.transferInfo.uploadedTotal),
                    )
                    WalletCardMetricLine(
                        label = stringResource(R.string.sort_total_downloaded),
                        value = formatBytes(snapshot.transferInfo.downloadedTotal),
                    )
                    WalletCardMetricLine(
                        label = stringResource(R.string.upload_limit_kb_label),
                        value = uploadLimitText,
                    )
                    WalletCardMetricLine(
                        label = stringResource(R.string.download_limit_kb_label),
                        value = downloadLimitText,
                    )
                    WalletCardMetricLine(
                        label = stringResource(R.string.free_space_label),
                        value = formatBytes(snapshot.transferInfo.freeSpaceOnDisk),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = snapshot.profileName.ifBlank { backendLabel(snapshot.backendType) },
                        style = serverNameTextStyle,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        WalletCardStatusLabel(label = stateLabel, live = isLive)
                        WalletCardSpeedLine(
                            uploadSpeed = snapshot.transferInfo.uploadSpeed,
                            downloadSpeed = snapshot.transferInfo.downloadSpeed,
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun WalletCardMetricLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = QbNumberStyles.small,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun WalletCardStatusLabel(
    label: String,
    live: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = if (live) QbTransferColors.Upload else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun WalletCardSpeedLine(
    uploadSpeed: Long,
    downloadSpeed: Long,
) {
    val idle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "↑ " + formatSpeed(uploadSpeed),
            style = QbNumberStyles.small,
            color = if (uploadSpeed > 0L) QbTransferColors.Upload else idle,
            maxLines = 1,
        )
        Text(
            text = "↓ " + formatSpeed(downloadSpeed),
            style = QbNumberStyles.small,
            color = if (downloadSpeed > 0L) QbTransferColors.Download else idle,
            maxLines = 1,
        )
    }
}

private data class WalletCardPalette(
    val accent: Color,
)

// Muted accents: the card itself stays a neutral tone, the server identity is a
// thin stripe on the leading edge.
private val WalletCardAccents = listOf(
    Color(0xFF5FD3C7),
    Color(0xFF5B9BF5),
    Color(0xFFE0A458),
    Color(0xFFA78BFA),
    Color(0xFFE58FA8),
    Color(0xFF4CC38A),
)

private fun walletCardPalette(
    stackedIndex: Int,
): WalletCardPalette {
    return WalletCardPalette(accent = WalletCardAccents[stackedIndex % WalletCardAccents.size])
}
