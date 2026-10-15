package com.hjw.qbremote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TolerantJsonTest {

    private val gson = tolerantGson()

    @Test
    fun mismatchedScalarTypesAreCoercedInsteadOfFailing() {
        // Each field here used to make Gson throw, failing the whole torrent-get.
        val json = """
            {
              "hashString": "abc", "name": "外卖送到网黄家想走就没那么容易了",
              "activityDate": 1777566609, "addedDate": 1775981249,
              "comment": ["not", "a", "string"],
              "downloadLimit": "2048",
              "uploadLimit": 3.0e10,
              "isFinished": 1,
              "downloadLimited": "true",
              "seedRatioMode": "x",
              "eta": -1,
              "trackerStats": [{"id": 0, "seederCount": 1.5, "lastAnnounceResult": 12}],
              "fileStats": [{"priority": "1", "wanted": 0}],
              "peers": [{"address": "1.2.3.4", "port": "51413", "rateToClient": 2.5e3}]
            }
        """.trimIndent()
        val t = gson.fromJson(json, TransmissionTorrent::class.java)
        assertEquals("abc", t.hashString)
        assertEquals("", t.comment)
        assertEquals(2048, t.downloadLimit)
        assertEquals(Int.MAX_VALUE, t.uploadLimit)
        assertTrue(t.isFinished)
        assertTrue(t.downloadLimited)
        assertEquals(null, t.seedRatioMode)
        assertEquals(-1L, t.eta)
        assertEquals(1, t.trackerStats.single().seederCount)
        assertEquals("12", t.trackerStats.single().lastAnnounceResult)
        assertEquals(1, t.fileStats.single().priority)
        assertFalse(t.fileStats.single().wanted)
        assertEquals(51413, t.peers.single().port)
        assertEquals(2500L, t.peers.single().rateToClient)
    }

    @Test
    fun plainGsonRejectsTheSamePayload() {
        // Regression guard: this is what made the detail page fail before.
        val json = """{"hashString":"h","isFinished":1,"comment":["x"]}"""
        val failed = runCatching { com.google.gson.Gson().fromJson(json, TransmissionTorrent::class.java) }.isFailure
        assertTrue(failed)
        assertTrue(gson.fromJson(json, TransmissionTorrent::class.java).isFinished)
    }

    @Test
    fun wellFormedPayloadIsUnchanged() {
        val json = """{"hashString":"h","percentDone":0.5,"totalSize":5000000000,"status":6,"isFinished":false}"""
        val t = gson.fromJson(json, TransmissionTorrent::class.java)
        assertEquals(0.5, t.percentDone, 0.0)
        assertEquals(5_000_000_000L, t.totalSize)
        assertEquals(6, t.status)
        assertFalse(t.isFinished)
    }

    @Test
    fun serializationStillWritesNumbers() {
        assertEquals("""{"a":1,"b":true}""", gson.toJson(mapOf("a" to 1, "b" to true)))
    }
}
