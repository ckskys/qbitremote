package com.hjw.qbremote.ui

import com.hjw.qbremote.data.ConnectionSettings
import com.hjw.qbremote.data.ServerBackendType
import com.hjw.qbremote.data.defaultServerDashboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Reset cards" shows [defaultServerDashboardPreferencesForBackend] immediately and then
 * stores [defaultServerDashboardPreferences]; a newly added server also starts from the
 * latter. They used to disagree (reset dropped the tracker-site card for qBittorrent),
 * so the screen flipped after a reset.
 */
class DashboardDefaultsConsistencyTest {

    @Test
    fun storedDefaultsMatchWhatTheResetShows() {
        ServerBackendType.entries.forEach { backend ->
            val stored = defaultServerDashboardPreferences(ConnectionSettings(serverBackendType = backend))
            val shown = defaultServerDashboardPreferencesForBackend(backend)
            assertEquals(backend.name, shown.visibleCards, stored.visibleCards)
            assertEquals(backend.name, shown.cardOrder, stored.cardOrder)
        }
    }
}
