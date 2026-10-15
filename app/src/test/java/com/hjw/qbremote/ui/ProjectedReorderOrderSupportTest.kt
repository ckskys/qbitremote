package com.hjw.qbremote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectedReorderOrderSupportTest {
    private val ids = listOf("front", "middle", "back")

    private fun session(startIndex: Int) = buildHomeServerStackReorderSession(
        orderedProfileIds = ids,
        startIndex = startIndex,
        exposedStepPx = 100f,
        edgeSlackPx = 0f,
    )

    @Test
    fun withoutDragTheOrderIsUnchanged() {
        assertEquals(ids, resolveProjectedReorderOrder(ids, dragSession = null, draggingTargetIndex = -1))
    }

    @Test
    fun draggingFrontCardToTheBackPromotesTheNextCardToFront() {
        val projected = resolveProjectedReorderOrder(ids, session(startIndex = 0), draggingTargetIndex = 2)

        assertEquals(listOf("middle", "back", "front"), projected)
    }

    @Test
    fun draggingBackCardToFrontMakesItTheFrontCard() {
        val projected = resolveProjectedReorderOrder(ids, session(startIndex = 2), draggingTargetIndex = 0)

        assertEquals(listOf("back", "front", "middle"), projected)
    }

    @Test
    fun staleSessionFallsBackToCurrentOrder() {
        val changed = listOf("front", "back", "middle")

        assertEquals(changed, resolveProjectedReorderOrder(changed, session(startIndex = 0), draggingTargetIndex = 2))
    }
}
