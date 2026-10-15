package com.hjw.qbremote.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class PieLegendEntry(
    val label: String,
    val value: Long,
    val valueText: String,
)

// Muted accents shared by every dashboard chart (same family as the theme).
val DashboardPiePalette = listOf(
    Color(0xFF5FD3C7),
    Color(0xFF5B9BF5),
    Color(0xFFE0A458),
    Color(0xFFA78BFA),
    Color(0xFFE58FA8),
    Color(0xFF4CC38A),
    Color(0xFF8B94A1),
    Color(0xFFD9C36A),
)
