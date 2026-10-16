package com.hjw.qbremote.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.animation.animateColorAsState
import com.hjw.qbremote.ui.theme.QbNumberStyles
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import com.hjw.qbremote.ui.theme.QbTransferColors
import com.hjw.qbremote.data.model.FilePriority
import com.hjw.qbremote.data.model.TorrentPeer
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hjw.qbremote.R
import com.hjw.qbremote.data.ServerCapabilities
import com.hjw.qbremote.data.model.TorrentFileInfo
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TorrentProperties
import com.hjw.qbremote.data.model.TorrentTracker
import com.hjw.qbremote.ui.theme.qbGlassCardColors
import com.hjw.qbremote.ui.theme.qbGlassOutlineColor

@Composable
internal fun TorrentCard(
    torrent: TorrentInfo,
    crossSeedCount: Int,
    isPending: Boolean,
    onOpenDetails: () -> Unit,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val effectiveState = effectiveTorrentState(torrent)
    val stateLabel = localizedTorrentStateLabel(effectiveState)
    val stateStyle = torrentStateStyle(effectiveState)
    val complete = torrent.progress >= 1f
    val noCategory = stringResource(R.string.no_category)
    val noTags = stringResource(R.string.no_tags)
    val categoryText = normalizeCategoryLabel(category = torrent.category, noCategoryText = noCategory)
    val tagsText = compactTagsLabel(tags = torrent.tags, noTagsText = noTags)
    // Second line: state · size · ETA while downloading, ratio otherwise.
    val statusText = if (complete) stateLabel else "$stateLabel ${(torrent.progress * 100).toInt()}%"
    val tailText = formatEta(torrent.eta).takeIf { !complete }
        ?.let { stringResource(R.string.torrent_row_eta_fmt, it) }
        ?: stringResource(R.string.torrent_row_ratio_fmt, formatRatio(torrent.ratio))
    // Third, faint line only when there is something filed: category, tags, cross-seeds.
    val filingText = buildList {
        if (categoryText != noCategory) add(categoryText)
        if (tagsText != noTags) add(tagsText)
        if (crossSeedCount > 0) add(stringResource(R.string.torrent_cross_seed_chip_fmt, crossSeedCount))
    }.joinToString("  ·  ")
    val separatorColor = MaterialTheme.colorScheme.outline
    val idleSpeedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    val cardShape = RoundedCornerShape(12.dp)
    // Clip before combinedClickable so the ripple follows the rounded corners.
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .combinedClickable(
                enabled = !isPending,
                onClick = onOpenDetails,
                onLongClick = onLongClick,
            ),
        shape = cardShape,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, qbGlassOutlineColor())
        },
        colors = qbGlassCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = torrent.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
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
                                append(statusText)
                                listOf(formatBytes(torrent.size), tailText).forEach { part ->
                                    withStyle(SpanStyle(color = separatorColor)) { append("  ·  ") }
                                    append(part)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (filingText.isNotEmpty()) {
                        Text(
                            text = filingText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "↑ " + formatSpeed(torrent.uploadSpeed),
                        style = QbNumberStyles.small,
                        color = if (torrent.uploadSpeed > 0L) QbTransferColors.Upload else idleSpeedColor,
                        maxLines = 1,
                    )
                    Text(
                        text = "↓ " + formatSpeed(torrent.downloadSpeed),
                        style = QbNumberStyles.small,
                        color = if (torrent.downloadSpeed > 0L) QbTransferColors.Download else idleSpeedColor,
                        maxLines = 1,
                    )
                }
            }

            // Progress only matters until the torrent is complete.
            if (!complete) {
                val animatedProgress by animateFloatAsState(
                    targetValue = torrent.progress.coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 450),
                    label = "torrentCardProgress",
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = stateStyle.progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
internal fun TorrentOperationDetailCard(
    torrent: TorrentInfo,
    crossSeedCount: Int,
    isPending: Boolean,
    capabilities: ServerCapabilities,
    detailLoading: Boolean,
    detailProperties: TorrentProperties?,
    detailFiles: List<TorrentFileInfo>,
    detailTrackers: List<TorrentTracker>,
    detailPeers: List<TorrentPeer>,
    magnetUri: String,
    categoryOptions: List<String>,
    tagOptions: List<String>,
    deleteFilesDefault: Boolean,
    deleteFilesWhenNoSeeders: Boolean,
    onCopyHash: () -> Unit,
    onCopyMagnet: (String) -> Unit,
    onExportTorrent: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    onSetLocation: (String) -> Unit,
    onSetCategory: (String) -> Unit,
    onSetTags: (String, String) -> Unit,
    onSetSpeedLimit: (String, String) -> Unit,
    onSetShareRatio: (String) -> Unit,
    onReannounce: () -> Unit,
    onRecheck: () -> Unit,
    onCopyTracker: (TorrentTracker) -> Unit,
    onEditTracker: (TorrentTracker, String) -> Unit,
    onDeleteTracker: (TorrentTracker) -> Unit,
    onSetFilePriority: (List<Int>, FilePriority) -> Unit,
) {
    var showDeleteDialog by remember(torrent.hash) { mutableStateOf(false) }
    var deleteFilesChecked by remember(torrent.hash) { mutableStateOf(false) }
    var renameText by remember(torrent.hash) { mutableStateOf(torrent.name) }
    var locationText by remember(torrent.hash) {
        mutableStateOf(detailProperties?.savePath?.takeIf { it.isNotBlank() } ?: torrent.savePath)
    }
    var categoryTextInput by remember(torrent.hash) { mutableStateOf(torrent.category) }
    var tagsTextInput by remember(torrent.hash) { mutableStateOf(torrent.tags) }
    var downloadLimitText by remember(torrent.hash) { mutableStateOf("") }
    var uploadLimitText by remember(torrent.hash) { mutableStateOf("") }
    // Pre-fill the torrent's ratio *limit*. (It used to show the current share
    // ratio, so pressing Apply set the limit to whatever ratio it had right now.)
    var ratioText by remember(torrent.hash) { mutableStateOf(ratioLimitInputText(torrent.ratioLimit)) }
    var selectedTab by rememberSaveable(torrent.hash) { mutableIntStateOf(0) }
    var trackersPasskeyVisible by rememberSaveable(torrent.hash) { mutableStateOf(false) }
    var editingTracker by remember(torrent.hash) { mutableStateOf<TorrentTracker?>(null) }
    var editingTrackerUrl by remember(torrent.hash) { mutableStateOf("") }
    var pendingDeleteTracker by remember(torrent.hash) { mutableStateOf<TorrentTracker?>(null) }
    var fileBrowserPath by rememberSaveable(torrent.hash) { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(torrent.hash, detailProperties?.downloadLimit, detailProperties?.uploadLimit) {
        val dl = detailProperties?.downloadLimit ?: 0L
        val up = detailProperties?.uploadLimit ?: 0L
        downloadLimitText = if (dl > 0L) (dl / 1024L).toString() else ""
        uploadLimitText = if (up > 0L) (up / 1024L).toString() else ""
    }

    val fileTree = remember(torrent.hash, detailFiles) { buildTorrentFileTree(detailFiles) }
    val fileBrowserSelection = remember(fileTree, fileBrowserPath) {
        resolveTorrentFileBrowserSelection(
            root = fileTree,
            pathSegments = fileBrowserPath,
        )
    }
    LaunchedEffect(fileBrowserSelection.pathSegments) {
        if (fileBrowserSelection.pathSegments != fileBrowserPath) {
            fileBrowserPath = fileBrowserSelection.pathSegments
        }
    }

    val effectiveState = effectiveTorrentState(torrent)
    val paused = isPausedState(effectiveState)
    val requestDelete = {
        deleteFilesChecked = deleteFilesDefault ||
            (deleteFilesWhenNoSeeders && torrent.seeders <= 0)
        showDeleteDialog = true
    }
    val peerOverviewItems = listOf(
        stringResource(R.string.detail_peer_seeders_label) to
            stringResource(R.string.detail_peer_seeders_value, torrent.seeders, torrent.numComplete),
        stringResource(R.string.detail_peer_leechers_label) to
            stringResource(R.string.detail_peer_leechers_value, torrent.leechers, torrent.numIncomplete),
        stringResource(R.string.detail_peer_cross_seed_label) to
            stringResource(R.string.detail_peer_cross_seed_value, crossSeedCount),
        stringResource(R.string.detail_peer_ratio_label) to formatRatio(torrent.ratio),
        stringResource(R.string.detail_peer_activity_label) to if (isActiveJustNow(torrent.lastActivity)) {
            stringResource(R.string.active_just_now)
        } else {
            formatActiveAgo(torrent.lastActivity)
        },
    )
    val hasMutableTrackers = remember(detailTrackers) {
        detailTrackers.any { tracker -> isMutableTrackerUrl(tracker.url) }
    }

    // The page itself is the surface now: no wrapping card, sections carry their own tone.
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = torrent.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            TorrentDetailStatusHeader(
                torrent = torrent,
                paused = paused,
                isPending = isPending,
                onPauseResume = { if (paused) onResume() else onPause() },
                onRequestDelete = requestDelete,
            )

            // Tabs are backed by a pager so they can also be swiped between.
            val pagerState = rememberPagerState(initialPage = selectedTab) { 4 }
            val tabScope = rememberCoroutineScope()
            LaunchedEffect(pagerState.currentPage) { selectedTab = pagerState.currentPage }
            DetailSegmentedTabs(
                labels = listOf(
                    stringResource(R.string.tab_info),
                    stringResource(R.string.tab_trackers),
                    stringResource(R.string.tab_peers),
                    stringResource(R.string.tab_files),
                ),
                selectedIndex = pagerState.currentPage,
                onSelect = { index -> tabScope.launch { pagerState.animateScrollToPage(index) } },
            )

            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
            // Each tab emits several children; a pager page is not a Column, so
            // without this they were drawn on top of each other (empty-looking tabs).
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
            when (page) {
                0 -> {
                    TorrentDetailInfoTab(
                        torrent = torrent,
                        capabilities = capabilities,
                        isPending = isPending,
                        magnetUri = magnetUri,
                        categoryOptions = categoryOptions,
                        tagOptions = tagOptions,
                        paused = paused,
                        renameText = renameText,
                        locationText = locationText,
                        categoryTextInput = categoryTextInput,
                        tagsTextInput = tagsTextInput,
                        downloadLimitText = downloadLimitText,
                        uploadLimitText = uploadLimitText,
                        ratioText = ratioText,
                        onRenameTextChange = { renameText = it },
                        onLocationTextChange = { locationText = it },
                        onCategoryTextChange = { categoryTextInput = it },
                        onTagsTextChange = { tagsTextInput = it },
                        onDownloadLimitTextChange = { downloadLimitText = it },
                        onUploadLimitTextChange = { uploadLimitText = it },
                        onRatioTextChange = { ratioText = it },
                        onCopyHash = onCopyHash,
                        onCopyMagnet = { onCopyMagnet(magnetUri) },
                        onExportTorrent = onExportTorrent,
                        onRename = { onRename(renameText.trim()) },
                        onSetLocation = { onSetLocation(locationText.trim()) },
                        onSetCategory = { onSetCategory(categoryTextInput.trim()) },
                        onSetTags = { onSetTags(torrent.tags, tagsTextInput.trim()) },
                        onSetSpeedLimit = { onSetSpeedLimit(downloadLimitText, uploadLimitText) },
                        onSetShareRatio = { onSetShareRatio(ratioText.trim()) },
                        onReannounce = onReannounce,
                        onRecheck = onRecheck,
                        onApplyCategory = { option -> onSetCategory(option) },
                        onApplyTags = { next -> onSetTags(torrent.tags, next) },
                    )
                }
                1 -> {
                    TorrentDetailTrackersTab(
                        detailLoading = detailLoading,
                        detailTrackers = detailTrackers,
                        hasMutableTrackers = hasMutableTrackers,
                        trackersPasskeyVisible = trackersPasskeyVisible,
                        isPending = isPending,
                        supportsTrackerMutation = capabilities.supportsTrackerMutation,
                        onTogglePasskeyVisibility = {
                            trackersPasskeyVisible = !trackersPasskeyVisible
                        },
                        onCopyTracker = onCopyTracker,
                        onEditTracker = { tracker, trackerUrl ->
                            editingTracker = tracker
                            editingTrackerUrl = trackerUrl
                        },
                        onDeleteTracker = { tracker ->
                            pendingDeleteTracker = tracker
                        },
                    )
                }
                2 -> {
                    TorrentDetailPeersTab(
                        peerOverviewItems = peerOverviewItems,
                        peers = detailPeers,
                        detailLoading = detailLoading,
                    )
                }
                3 -> {
                    TorrentDetailFilesTab(
                        detailLoading = detailLoading,
                        detailFiles = detailFiles,
                        fileBrowserPath = fileBrowserPath,
                        fileBrowserSelection = fileBrowserSelection,
                        onOpenRoot = { fileBrowserPath = emptyList() },
                        onOpenPathSegment = { index ->
                            fileBrowserPath = fileBrowserPath.take(index + 1)
                        },
                        onOpenDirectory = { node ->
                            if (node.isDirectory) {
                                fileBrowserPath = node.pathSegments
                            }
                        },
                        onSetFilePriority = onSetFilePriority,
                        actionsEnabled = !isPending,
                    )
                }
            }
            }
            }
        }
    }

    TorrentDeleteDialog(
        visible = showDeleteDialog,
        deleteFilesChecked = deleteFilesChecked,
        isPending = isPending,
        onDeleteFilesCheckedChange = { deleteFilesChecked = it },
        onDismiss = { showDeleteDialog = false },
        onConfirm = {
            showDeleteDialog = false
            onDelete(deleteFilesChecked)
        },
    )

    val editingTrackerState = editingTracker
    TorrentEditTrackerDialog(
        tracker = editingTrackerState,
        trackerUrl = editingTrackerUrl,
        isPending = isPending,
        onTrackerUrlChange = { editingTrackerUrl = it },
        onDismiss = { editingTracker = null },
        onConfirm = { tracker ->
            editingTracker = null
            onEditTracker(tracker, editingTrackerUrl.trim())
        },
    )

    val deleteTrackerState = pendingDeleteTracker
    TorrentDeleteTrackerDialog(
        tracker = deleteTrackerState,
        isPending = isPending,
        onDismiss = { pendingDeleteTracker = null },
        onConfirm = { tracker ->
            pendingDeleteTracker = null
            onDeleteTracker(tracker)
        },
    )
}

@Composable
private fun DetailSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val segmentColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                animationSpec = tween(durationMillis = 180),
                label = "detailTabSegment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(segmentColor)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal enum class TorrentDetailPrimaryAction {
    ExportTorrent,
    Reannounce,
    Recheck,
}

internal fun torrentDetailPrimaryActions(capabilities: ServerCapabilities): List<TorrentDetailPrimaryAction> =
    buildList {
        if (capabilities.supportsExportTorrent) add(TorrentDetailPrimaryAction.ExportTorrent)
        if (capabilities.supportsReannounce) add(TorrentDetailPrimaryAction.Reannounce)
        if (capabilities.supportsRecheck) add(TorrentDetailPrimaryAction.Recheck)
    }

/** Text for the ratio-limit input: blank when the global limit applies or it is unknown. */
/**
 * Activity within the last few seconds reads better as "just now" than "0s".
 * A timestamp slightly in the future (server clock ahead of the phone) counts too.
 */
internal fun isActiveJustNow(lastActivitySeconds: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (lastActivitySeconds <= 0L) return false
    return (nowMillis / 1000L) - lastActivitySeconds <= 4L
}

internal fun ratioLimitInputText(ratioLimit: Double?): String {
    return when {
        ratioLimit == null || !ratioLimit.isFinite() || ratioLimit <= -2.0 -> ""
        ratioLimit < 0.0 -> "-1"
        else -> formatRatio(ratioLimit)
    }
}

