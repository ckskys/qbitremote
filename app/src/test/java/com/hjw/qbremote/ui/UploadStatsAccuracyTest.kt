package com.hjw.qbremote.ui

import com.hjw.qbremote.data.DailyCountryUploadTrackingSnapshot
import com.hjw.qbremote.data.DailyUploadTrackingSnapshot
import com.hjw.qbremote.data.model.CountryPeerSnapshot
import com.hjw.qbremote.data.model.TorrentInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class UploadStatsAccuracyTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 21)
    private fun at(day: LocalDate, hour: Int) =
        LocalDateTime.of(day, java.time.LocalTime.of(hour, 0)).toInstant(zone).toEpochMilli()

    // ---- daily upload ----

    @Test
    fun firstSampleOfTheDaySplitsTheOvernightGapAtMidnight() {
        // Last sample 22:00 yesterday, next 09:00 today: 9 of the 11 hours are today.
        val previous = DailyUploadTrackingSnapshot(
            date = today.minusDays(1).toString(),
            totalsByTag = mapOf("linux" to 5_000L),
            lastSeenByTorrent = mapOf("t1" to 1_000L),
            lastSampleAtMillis = at(today.minusDays(1), 22),
        )
        val (snapshot, stats) = advanceDailyUploadTrackingSnapshot(
            previousSnapshot = previous,
            today = today,
            torrents = listOf(TorrentInfo(hash = "t1", uploaded = 12_000L, tags = "linux")),
            nowMillis = at(today, 9),
            zone = zone,
        )
        assertEquals(9_000L, stats.single().uploadedBytes) // 11 000 * 9/11; yesterday's total dropped
        assertEquals(today.toString(), snapshot.date)
        assertEquals(12_000L, snapshot.lastSeenByTorrent.getValue("t1"))
        assertEquals(at(today, 9), snapshot.lastSampleAtMillis)
    }

    @Test
    fun withoutAKnownSampleTimeANewDayStartsFromScratch() {
        val previous = DailyUploadTrackingSnapshot(
            date = today.minusDays(1).toString(),
            lastSeenByTorrent = mapOf("t1" to 1_000L),
        )
        val (snapshot, stats) = advanceDailyUploadTrackingSnapshot(
            previousSnapshot = previous,
            today = today,
            torrents = listOf(TorrentInfo(hash = "t1", uploaded = 12_000L)),
            nowMillis = at(today, 9),
            zone = zone,
        )
        assertTrue(stats.isEmpty())
        assertEquals(12_000L, snapshot.lastSeenByTorrent.getValue("t1"))
    }

    @Test
    fun midnightShareHandlesOddClocks() {
        assertEquals(0.0, midnightTodayShare(0L, at(today, 9), today, zone), 0.0)
        assertEquals(0.0, midnightTodayShare(at(today, 10), at(today, 9), today, zone), 0.0)
        assertEquals(0.5, midnightTodayShare(at(today.minusDays(1), 12), at(today, 12), today, zone), 1e-9)
    }

    // ---- country distribution ----

    private fun peer(hash: String, address: String, uploaded: Long) = CountryPeerSnapshot(
        key = "$hash|$address|DE",
        peerAddress = address,
        countryCode = "DE",
        uploadedBytes = uploaded,
        torrentHash = hash,
    )

    private val torrents = listOf(TorrentInfo(hash = "h1", uploaded = 10L, uploadSpeed = 1L))

    @Test
    fun peerThatConnectedSinceTheLastSampleCountsInFull() {
        val previous = DailyCountryUploadTrackingSnapshot(
            date = today.toString(),
            peerSnapshots = mapOf(peer("h1", "1.1.1.1:1", 100L).let { it.key to it }),
            sampledTorrentHashes = listOf("h1"),
        )
        val (_, stats) = advanceDailyCountryUploadTrackingSnapshot(
            previous = previous,
            today = today,
            torrents = torrents,
            samples = listOf(peer("h1", "1.1.1.1:1", 150L), peer("h1", "2.2.2.2:2", 400L)),
            sampledHashes = setOf("h1"),
        )
        assertEquals(50L + 400L, stats.countries.single().uploadedBytes)
    }

    @Test
    fun peersOfATorrentNotSampledLastTimeAreOnlyABaseline() {
        val previous = DailyCountryUploadTrackingSnapshot(date = today.toString(), sampledTorrentHashes = emptyList())
        val (snapshot, stats) = advanceDailyCountryUploadTrackingSnapshot(
            previous = previous,
            today = today,
            torrents = torrents,
            samples = listOf(peer("h1", "2.2.2.2:2", 400L)),
            sampledHashes = setOf("h1"),
        )
        assertTrue(stats.countries.isEmpty())
        assertEquals(listOf("h1"), snapshot.sampledTorrentHashes)
    }

    @Test
    fun firstSampleOfANewDayCountsNothing() {
        val previous = DailyCountryUploadTrackingSnapshot(
            date = today.minusDays(1).toString(),
            sampledTorrentHashes = listOf("h1"),
        )
        val (_, stats) = advanceDailyCountryUploadTrackingSnapshot(
            previous = previous,
            today = today,
            torrents = torrents,
            samples = listOf(peer("h1", "2.2.2.2:2", 400L)),
            sampledHashes = setOf("h1"),
        )
        assertTrue(stats.countries.isEmpty())
    }
}
