package com.hjw.qbremote.ui

import com.hjw.qbremote.data.CachedDashboardServerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayFixesSupportTest {

    @Test
    fun ratioLimitInputText_showsTheLimitNotTheCurrentRatio() {
        assertEquals("2.00", ratioLimitInputText(2.0))
        assertEquals("-1", ratioLimitInputText(-1.0))
        // Global limit (-2) and unknown both leave the field empty.
        assertEquals("", ratioLimitInputText(-2.0))
        assertEquals("", ratioLimitInputText(null))
    }

    @Test
    fun serverSnapshotStatus_onlySaysCachedOfflineWhenThereIsACache() {
        val base = CachedDashboardServerSnapshot(profileId = "p")

        assertEquals(ServerSnapshotStatus.LIVE, resolveServerSnapshotStatus(base))
        assertEquals(
            ServerSnapshotStatus.CONNECTING,
            resolveServerSnapshotStatus(base.copy(isStale = true)),
        )
        assertEquals(
            ServerSnapshotStatus.UNREACHABLE,
            resolveServerSnapshotStatus(base.copy(isStale = true, errorMessage = "timeout")),
        )
        assertEquals(
            ServerSnapshotStatus.CACHED_OFFLINE,
            resolveServerSnapshotStatus(base.copy(isStale = true, lastUpdatedAt = 1_000L, errorMessage = "timeout")),
        )
    }
}
