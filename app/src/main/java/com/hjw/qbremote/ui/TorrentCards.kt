package com.hjw.qbremote.ui

import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.hjw.qbremote.R
import com.hjw.qbremote.data.ServerCapabilities
import com.hjw.qbremote.data.model.TorrentFileInfo
import com.hjw.qbremote.data.model.TorrentInfo
import com.hjw.qbremote.data.model.TorrentProperties
import com.hjw.qbremote.data.model.TorrentTracker
import com.hjw.qbremote.ui.theme.qbGlassCardColors
import com.hjw.qbremote.ui.theme.qbGlassOutlineColor
import com.hjw.qbremote.ui.theme.qbGlassStrongContainerColor
import com.hjw.qbremote.ui.theme.qbGlassSubtleContainerColor

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

@Composable
private fun TorrentDetailInfoTab(
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
private fun DetailListGroup(content: @Composable () -> Unit) {
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
private fun DetailListDivider() {
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

@Composable
private fun TorrentDeleteDialog(
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
private fun TorrentEditTrackerDialog(
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
private fun TorrentDeleteTrackerDialog(
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

@Composable
private fun TorrentDetailTrackersTab(
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
private fun TorrentDetailPeersTab(
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
            text = stringResource(R.string.detail_peers_connected_fmt, peers.size),
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

private const val MAX_LISTED_PEERS = 100

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
private fun TorrentDetailFilesTab(
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

internal data class TorrentFileTreeNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val progress: Float,
    val fileCount: Int,
    val orderIndex: Int,
    val pathSegments: List<String>,
    val children: List<TorrentFileTreeNode> = emptyList(),
    // Backend file indexes under this node (one for a file, all descendants for a
    // folder) and their priorities; null priority means the files disagree.
    val fileIndexes: List<Int> = emptyList(),
    val priority: FilePriority? = null,
)

internal data class TorrentFileBrowserSelection(
    val node: TorrentFileTreeNode,
    val pathSegments: List<String>,
)

private class MutableTorrentFileTreeNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val orderIndex: Int,
    val pathSegments: List<String>,
) {
    val children = linkedMapOf<String, MutableTorrentFileTreeNode>()
    var size: Long = 0L
    var weightedProgress: Double = 0.0
    var fileCount: Int = 0
    val fileIndexes = mutableListOf<Int>()
    val priorities = mutableSetOf<FilePriority>()
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

internal fun buildTorrentFileTree(files: List<TorrentFileInfo>): TorrentFileTreeNode {
    val root = MutableTorrentFileTreeNode(
        name = "",
        fullPath = "",
        isDirectory = true,
        orderIndex = Int.MIN_VALUE,
        pathSegments = emptyList(),
    )
    files.forEachIndexed { fallbackIndex, file ->
        val cleanPath = file.name.trim().replace('\\', '/')
        if (cleanPath.isBlank()) return@forEachIndexed
        val segments = cleanPath.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return@forEachIndexed
        val clampedProgress = file.progress.coerceIn(0f, 1f).toDouble()
        val weightedProgress = file.size.toDouble() * clampedProgress
        val fileIndex = if (file.index >= 0) file.index else fallbackIndex
        val filePriority = FilePriority.fromQbValue(file.priority)
        var current = root
        current.size += file.size
        current.weightedProgress += weightedProgress
        current.fileCount += 1
        current.fileIndexes += fileIndex
        current.priorities += filePriority
        segments.forEachIndexed { index, segment ->
            val isFile = index == segments.lastIndex
            val currentPathSegments = segments.take(index + 1)
            val fullPath = currentPathSegments.joinToString("/")
            val child = current.children.getOrPut(segment) {
                MutableTorrentFileTreeNode(
                    name = segment,
                    fullPath = fullPath,
                    isDirectory = !isFile,
                    orderIndex = if (file.index >= 0) file.index else fallbackIndex,
                    pathSegments = currentPathSegments,
                )
            }
            if (isFile) {
                child.size = file.size
                child.weightedProgress = weightedProgress
                child.fileCount = 1
            } else {
                child.size += file.size
                child.weightedProgress += weightedProgress
                child.fileCount += 1
            }
            child.fileIndexes += fileIndex
            child.priorities += filePriority
            current = child
        }
    }
    return root.toImmutableNode()
}

private fun MutableTorrentFileTreeNode.toImmutableNode(): TorrentFileTreeNode {
    val safeSize = size.coerceAtLeast(0L)
    val progress = if (safeSize > 0L) {
        (weightedProgress / safeSize.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    return TorrentFileTreeNode(
        name = name,
        fullPath = fullPath,
        isDirectory = isDirectory,
        size = safeSize,
        progress = progress,
        fileCount = fileCount.coerceAtLeast(if (isDirectory) 0 else 1),
        orderIndex = orderIndex,
        pathSegments = pathSegments,
        fileIndexes = fileIndexes.toList(),
        priority = priorities.singleOrNull(),
        children = children.values
            .sortedWith(
                compareBy<MutableTorrentFileTreeNode> { !it.isDirectory }
                    .thenBy { it.orderIndex }
                    .thenBy { it.name.lowercase() },
            )
            .map { it.toImmutableNode() },
    )
}

private fun resolveTorrentFileTreeNode(
    root: TorrentFileTreeNode,
    pathSegments: List<String>,
): TorrentFileTreeNode? {
    var current = root
    pathSegments.forEach { segment ->
        current = current.children.firstOrNull { it.name == segment } ?: return null
    }
    return current
}

internal fun resolveTorrentFileBrowserSelection(
    root: TorrentFileTreeNode,
    pathSegments: List<String>,
): TorrentFileBrowserSelection {
    val resolvedNode = resolveTorrentFileTreeNode(root, pathSegments)
    return if (resolvedNode != null) {
        TorrentFileBrowserSelection(
            node = resolvedNode,
            pathSegments = pathSegments,
        )
    } else {
        TorrentFileBrowserSelection(
            node = root,
            pathSegments = emptyList(),
        )
    }
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
