package com.hjw.qbremote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QbResponseSupportTest {

    @Test
    fun loginOkTextIsSuccess() {
        assertTrue(isQbLoginSuccess(httpSuccessful = true, httpCode = 200, bodyText = "Ok."))
    }

    @Test
    fun qb52EmptyNoContentLoginIsSuccess() {
        assertTrue(isQbLoginSuccess(httpSuccessful = true, httpCode = 204, bodyText = ""))
    }

    @Test
    fun emptyOkLoginIsNotSuccess() {
        // A 200 with an empty body is ambiguous (e.g. a proxy), only 204 means success.
        assertFalse(isQbLoginSuccess(httpSuccessful = true, httpCode = 200, bodyText = ""))
    }

    @Test
    fun failsTextIsNotSuccess() {
        assertFalse(isQbLoginSuccess(httpSuccessful = true, httpCode = 200, bodyText = "Fails."))
        assertFalse(isQbLoginSuccess(httpSuccessful = false, httpCode = 403, bodyText = ""))
    }

    @Test
    fun legacyAddRepliesAreParsed() {
        assertEquals(QbAddTorrentOutcome.Added, parseQbAddTorrentResult("Ok."))
        assertEquals(QbAddTorrentOutcome.Added, parseQbAddTorrentResult(""))
        assertTrue(parseQbAddTorrentResult("Fails.") is QbAddTorrentOutcome.Failed)
    }

    @Test
    fun qb52JsonSuccessIsNotMistakenForFailure() {
        val reply = """{"added_torrent_ids":["f7e482795d50172d56dd0e6af60897b4489d1057"],""" +
            """"failure_count":0,"pending_count":0,"success_count":1}"""
        assertEquals(QbAddTorrentOutcome.Added, parseQbAddTorrentResult(reply))
    }

    @Test
    fun qb52JsonPendingMagnetCountsAsAdded() {
        val reply = """{"added_torrent_ids":[],"failure_count":0,"pending_count":1,"success_count":0}"""
        assertEquals(QbAddTorrentOutcome.Added, parseQbAddTorrentResult(reply))
    }

    @Test
    fun qb52JsonAllFailedIsFailure() {
        val reply = """{"added_torrent_ids":[],"failure_count":2,"pending_count":0,"success_count":0}"""
        val outcome = parseQbAddTorrentResult(reply)
        assertTrue(outcome is QbAddTorrentOutcome.Failed)
        assertTrue((outcome as QbAddTorrentOutcome.Failed).message.contains("2"))
    }

    @Test
    fun qb52JsonPartialFailureReportsCounts() {
        val reply = """{"added_torrent_ids":["a"],"failure_count":1,"pending_count":0,"success_count":1}"""
        val outcome = parseQbAddTorrentResult(reply)
        assertTrue(outcome is QbAddTorrentOutcome.Failed)
        val message = (outcome as QbAddTorrentOutcome.Failed).message
        assertTrue(message.contains("成功 1") && message.contains("失败 1"))
    }

    @Test
    fun malformedJsonIsFailure() {
        assertTrue(parseQbAddTorrentResult("{not json") is QbAddTorrentOutcome.Failed)
    }
}
