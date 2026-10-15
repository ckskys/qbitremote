package com.hjw.qbremote.ui

internal enum class AppPage {
    DASHBOARD,
    SERVER_DASHBOARD,
    TORRENT_LIST,
    TORRENT_DETAIL,
    SETTINGS,
}

/** Navigation depth: deeper pages slide in from the right, going back slides left. */
internal fun pageDepth(page: AppPage): Int = when (page) {
    AppPage.DASHBOARD -> 0
    AppPage.SETTINGS, AppPage.SERVER_DASHBOARD -> 1
    AppPage.TORRENT_LIST -> 2
    AppPage.TORRENT_DETAIL -> 3
}
