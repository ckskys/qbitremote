package com.hjw.qbremote.ui

import com.hjw.qbremote.data.CachedDashboardServerSnapshot
import com.hjw.qbremote.data.ConnectionStore
import com.hjw.qbremote.data.DailyCountryUploadTrackingSnapshot
import com.hjw.qbremote.data.DailyUploadTrackingSnapshot
import com.hjw.qbremote.data.model.CountryPeerSample
import com.hjw.qbremote.data.model.TorrentInfo
import java.time.LocalDate

/**
 * Disk persistence for dashboard snapshots and the daily tag/country upload
 * trackers. These change on every server refresh, so the latest values live in
 * memory and are written at most every [THROTTLED_PERSIST_INTERVAL_MS] per key
 * (pending values are flushed when the app goes to the background). Split out of
 * MainViewModel.
 */
internal class DashboardPersistence(
    private val connectionStore: ConnectionStore,
) {
    private val dashboardSnapshotPersistence =
        ThrottledPersistence<CachedDashboardServerSnapshot>(THROTTLED_PERSIST_INTERVAL_MS)
    private val dailyUploadTrackingPersistence =
        ThrottledPersistence<DailyUploadTrackingSnapshot>(THROTTLED_PERSIST_INTERVAL_MS)
    private val dailyCountryTrackingPersistence =
        ThrottledPersistence<DailyCountryUploadTrackingSnapshot>(THROTTLED_PERSIST_INTERVAL_MS)

    suspend fun flushThrottledPersistence() {
        dashboardSnapshotPersistence.drainPending().values.forEach { snapshot ->
            connectionStore.saveDashboardServerSnapshot(snapshot)
        }
        dailyUploadTrackingPersistence.drainPending().forEach { (scopeKey, snapshot) ->
            connectionStore.saveDailyUploadTrackingSnapshot(scopeKey = scopeKey, snapshot = snapshot)
        }
        dailyCountryTrackingPersistence.drainPending().forEach { (scopeKey, snapshot) ->
            connectionStore.saveDailyCountryUploadTrackingSnapshot(scopeKey = scopeKey, snapshot = snapshot)
        }
    }

    suspend fun loadDashboardSnapshotsMap(): MutableMap<String, CachedDashboardServerSnapshot> {
        return connectionStore.loadDashboardServerSnapshots().toMutableMap()
    }

    suspend fun loadStoredDashboardSnapshot(
        profileId: String,
    ): CachedDashboardServerSnapshot? {
        val normalizedProfileId = profileId.trim()
        if (normalizedProfileId.isBlank()) return null
        return connectionStore.loadDashboardServerSnapshots()[normalizedProfileId]
    }

    /**
     * [throttle] is for the periodic refresh path: the snapshot is only an offline
     * cache (live UI state is in memory), so rewriting the whole snapshot JSON on
     * every 5s refresh of every server is wasted I/O. Any unthrottled write (state
     * change, failure, user action) resets the window so the next periodic write
     * lands immediately.
     */
    suspend fun persistDashboardSnapshot(
        snapshot: CachedDashboardServerSnapshot,
        snapshots: MutableMap<String, CachedDashboardServerSnapshot>? = null,
        throttle: Boolean = false,
    ) {
        val normalizedProfileId = snapshot.profileId.trim()
        if (normalizedProfileId.isBlank()) return
        val normalizedSnapshot = if (normalizedProfileId == snapshot.profileId) {
            snapshot
        } else {
            snapshot.copy(profileId = normalizedProfileId)
        }
        snapshots?.set(normalizedSnapshot.profileId, normalizedSnapshot)
        if (throttle) {
            if (!dashboardSnapshotPersistence.offer(normalizedProfileId, normalizedSnapshot)) return
        } else {
            dashboardSnapshotPersistence.wroteUnthrottled(normalizedProfileId, normalizedSnapshot)
        }
        connectionStore.saveDashboardServerSnapshot(normalizedSnapshot)
    }

    suspend fun buildDashboardTagUploadStatsForScope(
        scopeKey: String,
        torrents: List<TorrentInfo>,
    ): Pair<String, List<DailyTagUploadStat>> {
        val today = LocalDate.now()
        val previousSnapshot = dailyUploadTrackingPersistence.cached(scopeKey)
            ?: connectionStore.loadDailyUploadTrackingSnapshot(scopeKey)?.also { loaded ->
                dailyUploadTrackingPersistence.remember(scopeKey, loaded)
            }
        val (updatedSnapshot, stats) = advanceDailyUploadTrackingSnapshot(
            previousSnapshot = previousSnapshot,
            today = today,
            torrents = torrents,
            nowMillis = System.currentTimeMillis(),
        )
        if (dailyUploadTrackingPersistence.offer(scopeKey, updatedSnapshot)) {
            connectionStore.saveDailyUploadTrackingSnapshot(
                scopeKey = scopeKey,
                snapshot = updatedSnapshot,
            )
        }
        return updatedSnapshot.date.ifBlank { today.toString() } to stats
    }

    suspend fun buildDashboardCountryUploadStatsForScope(
        scopeKey: String,
        torrents: List<TorrentInfo>,
        fetchPeerSnapshots: suspend (List<String>) -> CountryPeerSample,
    ): com.hjw.qbremote.data.model.DailyCountryUploadStats {
        val snapshot = dailyCountryTrackingPersistence.cached(scopeKey)
            ?: connectionStore.loadDailyCountryUploadTrackingSnapshot(scopeKey)?.also { loaded ->
                dailyCountryTrackingPersistence.remember(scopeKey, loaded)
            }
        val today = LocalDate.now()
        val sample = fetchPeerSnapshots(
            resolveActiveCountryUploadHashes(
                previous = snapshot,
                today = today,
                torrents = torrents,
            ),
        )
        val (updatedSnapshot, stats) = advanceDailyCountryUploadTrackingSnapshot(
            previous = snapshot,
            today = today,
            torrents = torrents,
            samples = sample.peers,
            sampledHashes = sample.sampledHashes,
        )
        if (dailyCountryTrackingPersistence.offer(scopeKey, updatedSnapshot)) {
            connectionStore.saveDailyCountryUploadTrackingSnapshot(
                scopeKey = scopeKey,
                snapshot = updatedSnapshot,
            )
        }
        return stats
    }

    /** Drops in-memory state of deleted servers so it can't be flushed back to disk. */
    fun retainProfiles(profileIds: Set<String>, trackingScopeKeys: Set<String>) {
        dashboardSnapshotPersistence.retainKeys { it in profileIds }
        dailyUploadTrackingPersistence.retainKeys { it in trackingScopeKeys }
        dailyCountryTrackingPersistence.retainKeys { it in trackingScopeKeys }
    }

    private companion object {
        const val THROTTLED_PERSIST_INTERVAL_MS = 30_000L
    }
}
