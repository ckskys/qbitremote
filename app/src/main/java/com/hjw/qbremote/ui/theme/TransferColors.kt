package com.hjw.qbremote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The single upload/download color pair. Every place that tells the two
 * directions apart by color (charts, legends, speed tiles, torrent cards) must use
 * these, so users can rely on the color alone.
 */
object QbTransferColors {
    val Upload = Color(0xFF4CC38A)
    val Download = Color(0xFF5B9BF5)
}
