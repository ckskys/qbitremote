package com.hjw.qbremote.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hjw.qbremote.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreenTopBar(
    currentPage: AppPage,
    connected: Boolean,
    isManualRefreshing: Boolean,
    sortOption: TorrentListSortOption,
    sortDescending: Boolean,
    showSearchBar: Boolean,
    showSortMenu: Boolean,
    onSortMenuVisibilityChange: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
    onScrollToTop: () -> Unit,
    onOpenServerSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCardManager: () -> Unit,
    onRefreshOrConnect: () -> Unit,
    onSortOptionSelected: (TorrentListSortOption) -> Unit,
    onSortDirectionSelected: (Boolean) -> Unit,
    onToggleSearchBar: () -> Unit,
    onOpenAddTorrent: () -> Unit,
    // Server name inside a server's pages; null shows the app name.
    title: String? = null,
) {
    val openDrawerDescription = stringResource(R.string.menu_open_drawer)
    val backDescription = stringResource(R.string.back)
    val manageServersDescription = stringResource(R.string.menu_manage_servers)
    val sortDescription = stringResource(R.string.menu_sort)
    val searchDescription = stringResource(R.string.menu_search)
    val addTorrentDescription = stringResource(R.string.menu_add_torrent)
    val compactTorrentTopBar = currentPage == AppPage.TORRENT_LIST
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        navigationIcon = {
            TextButton(
                modifier = Modifier.semantics {
                    contentDescription = if (currentPage == AppPage.DASHBOARD) {
                        openDrawerDescription
                    } else {
                        backDescription
                    }
                },
                onClick = {
                    if (currentPage == AppPage.DASHBOARD) {
                        onOpenDrawer()
                    } else {
                        onBack()
                    }
                },
                contentPadding = if (compactTorrentTopBar) {
                    PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                } else {
                    ButtonDefaults.TextButtonContentPadding
                },
            ) {
                Icon(
                    imageVector = if (currentPage == AppPage.DASHBOARD) {
                        Icons.Filled.Menu
                    } else {
                        Icons.AutoMirrored.Filled.ArrowBack
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
        title = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (compactTorrentTopBar) {
                            Modifier.offset(x = (-4).dp)
                        } else {
                            Modifier
                        }
                    )
                    .pointerInput(currentPage) {
                        detectTapGestures(
                            onDoubleTap = {
                                onScrollToTop()
                            },
                        )
                    },
            ) {
                TopBrandTitle(
                    modifier = Modifier.fillMaxWidth(),
                    compact = compactTorrentTopBar,
                    title = title,
                )
            }
        },
        actions = {
            // Icon actions instead of text buttons: a lighter bar that leaves the
            // title room (each keeps its label as the accessibility description).
            when (currentPage) {
                AppPage.DASHBOARD -> {
                    TopBarIconAction(
                        icon = Icons.Outlined.Dns,
                        description = manageServersDescription,
                        onClick = onOpenServerSheet,
                    )
                    TopBarIconAction(
                        icon = Icons.Outlined.Settings,
                        description = stringResource(R.string.menu_settings),
                        onClick = onOpenSettings,
                    )
                }

                AppPage.SERVER_DASHBOARD -> {
                    TopBarIconAction(
                        icon = Icons.Outlined.DashboardCustomize,
                        description = stringResource(R.string.dashboard_manage_cards_action),
                        onClick = onOpenCardManager,
                    )
                }

                AppPage.SETTINGS -> {
                    TopBarIconAction(
                        icon = if (connected) Icons.Outlined.Refresh else Icons.Outlined.Link,
                        description = if (connected) {
                            stringResource(R.string.refresh)
                        } else {
                            stringResource(R.string.connect)
                        },
                        enabled = !isManualRefreshing,
                        onClick = onRefreshOrConnect,
                    )
                }

                AppPage.TORRENT_DETAIL -> {
                    TopBarIconAction(
                        icon = Icons.Outlined.Refresh,
                        description = stringResource(R.string.refresh),
                        onClick = onRefreshOrConnect,
                    )
                }

                AppPage.TORRENT_LIST -> {
                    TopBarIconAction(
                        icon = if (showSearchBar) Icons.Outlined.SearchOff else Icons.Outlined.Search,
                        description = if (showSearchBar) {
                            stringResource(R.string.menu_collapse)
                        } else {
                            searchDescription
                        },
                        onClick = onToggleSearchBar,
                    )
                    Box {
                        TopBarIconAction(
                            icon = Icons.AutoMirrored.Outlined.Sort,
                            description = sortDescription,
                            onClick = { onSortMenuVisibilityChange(true) },
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { onSortMenuVisibilityChange(false) },
                        ) {
                            TorrentListSortOption.entries.forEach { option ->
                                val isSelected = option == sortOption
                                DropdownMenuItem(
                                    text = {
                                        val prefix = if (isSelected) "✓ " else ""
                                        Text("$prefix${torrentListSortLabel(option)}")
                                    },
                                    onClick = {
                                        onSortOptionSelected(option)
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    val prefix = if (sortDescending) "✓ " else ""
                                    Text("${prefix}${stringResource(R.string.sort_descending)}")
                                },
                                onClick = {
                                    onSortDirectionSelected(true)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    val prefix = if (!sortDescending) "✓ " else ""
                                    Text("${prefix}${stringResource(R.string.sort_ascending)}")
                                },
                                onClick = {
                                    onSortDirectionSelected(false)
                                },
                            )
                        }
                    }
                    TopBarIconAction(
                        icon = Icons.Outlined.Add,
                        description = addTorrentDescription,
                        onClick = onOpenAddTorrent,
                    )
                }
            }
        },
    )
}

@Composable
private fun TopBarIconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun torrentListSortLabel(option: TorrentListSortOption): String {
    return when (option) {
        TorrentListSortOption.ADDED_TIME -> stringResource(R.string.sort_added_time)
        TorrentListSortOption.UPLOAD_SPEED -> stringResource(R.string.sort_upload_speed)
        TorrentListSortOption.DOWNLOAD_SPEED -> stringResource(R.string.sort_download_speed)
        TorrentListSortOption.SHARE_RATIO -> stringResource(R.string.sort_share_ratio)
        TorrentListSortOption.TOTAL_UPLOADED -> stringResource(R.string.sort_total_uploaded)
        TorrentListSortOption.TOTAL_DOWNLOADED -> stringResource(R.string.sort_total_downloaded)
        TorrentListSortOption.TORRENT_SIZE -> stringResource(R.string.sort_torrent_size)
        TorrentListSortOption.ACTIVITY_TIME -> stringResource(R.string.sort_activity_time)
        TorrentListSortOption.SEEDERS -> stringResource(R.string.sort_seeders)
        TorrentListSortOption.LEECHERS -> stringResource(R.string.sort_leechers)
        TorrentListSortOption.CROSS_SEED_COUNT -> stringResource(R.string.sort_cross_seed_count)
    }
}
