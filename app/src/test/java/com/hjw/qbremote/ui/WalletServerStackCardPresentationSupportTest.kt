package com.hjw.qbremote.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletServerStackCardPresentationSupportTest {

    @Test
    fun resolveWalletServerStackCardPresentation_draggingCardOutsideFrontSlotStaysCollapsed() {
        // A lifted card must not grow to full height: it would cover every
        // collapsed sibling it passes and snap back to a header on drop.
        val presentation = resolveWalletServerStackCardPresentation(
            selected = false,
            isDragging = true,
            isSettling = false,
        )

        assertFalse(presentation.showExpandedLayout)
    }

    @Test
    fun resolveWalletServerStackCardPresentation_settlingCardOutsideFrontSlotStaysCollapsed() {
        val presentation = resolveWalletServerStackCardPresentation(
            selected = false,
            isDragging = false,
            isSettling = true,
        )

        assertFalse(presentation.showExpandedLayout)
    }

    @Test
    fun resolveWalletServerStackCardPresentation_draggingCardHeadingForFrontSlotIsExpanded() {
        val presentation = resolveWalletServerStackCardPresentation(
            selected = true,
            isDragging = true,
            isSettling = false,
        )

        assertTrue(presentation.showExpandedLayout)
        assertEquals(16.dp, presentation.horizontalPadding)
        assertEquals(14.dp, presentation.verticalPadding)
        assertEquals(0.28f, presentation.borderAlpha)
    }

    @Test
    fun resolveWalletServerStackCardPresentation_idleSecondaryCardStaysCollapsed() {
        val presentation = resolveWalletServerStackCardPresentation(
            selected = false,
            isDragging = false,
            isSettling = false,
        )

        assertFalse(presentation.showExpandedLayout)
        assertEquals(15.dp, presentation.horizontalPadding)
        assertEquals(10.dp, presentation.verticalPadding)
        assertEquals(0.14f, presentation.borderAlpha)
    }
}
