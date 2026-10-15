package com.hjw.qbremote.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThrottledPersistenceSupportTest {
    private var now = 1_000L
    private val persistence = ThrottledPersistence<String>(minWriteIntervalMs = 30_000L, clock = { now })

    @Test
    fun firstOfferWritesAndLaterOffersWithinWindowAreHeldBack() {
        assertTrue(persistence.offer("a", "v1"))

        now += 5_000L
        assertFalse(persistence.offer("a", "v2"))

        assertEquals("v2", persistence.cached("a"))
        assertEquals(mapOf("a" to "v2"), persistence.drainPending())
        assertTrue(persistence.drainPending().isEmpty())
    }

    @Test
    fun offerAfterWindowWritesAgainAndClearsPending() {
        assertTrue(persistence.offer("a", "v1"))
        now += 5_000L
        assertFalse(persistence.offer("a", "v2"))

        now += 30_000L
        assertTrue(persistence.offer("a", "v3"))

        assertTrue(persistence.drainPending().isEmpty())
    }

    @Test
    fun keysAreThrottledIndependently() {
        assertTrue(persistence.offer("a", "v1"))
        assertTrue(persistence.offer("b", "v1"))
        assertFalse(persistence.offer("a", "v2"))
    }

    @Test
    fun unthrottledWriteDropsPendingAndReopensTheWindow() {
        assertTrue(persistence.offer("a", "fresh-1"))
        assertFalse(persistence.offer("a", "fresh-2"))

        persistence.wroteUnthrottled("a", "stale")

        // The held-back value must not later overwrite the newer unthrottled write.
        assertTrue(persistence.drainPending().isEmpty())
        assertTrue(persistence.offer("a", "fresh-3"))
    }

    @Test
    fun rememberDoesNotOverrideNewerInMemoryValue() {
        persistence.offer("a", "newer")
        persistence.remember("a", "loaded-from-disk")

        assertEquals("newer", persistence.cached("a"))
    }

    @Test
    fun retainKeysDropsStateForRemovedKeys() {
        persistence.offer("keep", "v1")
        persistence.offer("drop", "v1")
        persistence.offer("drop", "v2")

        persistence.retainKeys { it == "keep" }

        assertNull(persistence.cached("drop"))
        assertTrue(persistence.drainPending().isEmpty())
        assertTrue(persistence.offer("drop", "v3"))
    }
}
