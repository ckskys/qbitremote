package com.hjw.qbremote.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Built-in themes use opaque tonal layers (card = surfaceContainer, raised element
// = surfaceContainerHigh) and near-invisible hairlines instead of outlines; the
// translucent "glass" values below apply only to the custom-background theme.
private const val HAIRLINE_ALPHA = 0.06f

@Composable
fun qbGlassCardColors(): CardColors {
    return CardDefaults.outlinedCardColors(
        containerColor = qbGlassContainerColor(),
    )
}

@Composable
fun qbGlassContainerColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    val themeState = LocalQbThemeState.current
    if (!themeState.isCustomTheme) {
        return colorScheme.surfaceContainer
    }
    return if (themeState.customBackgroundToneIsLight) {
        colorScheme.surfaceContainerHigh.copy(alpha = 0.62f)
    } else {
        colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
    }
}

@Composable
fun qbGlassStrongContainerColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    val themeState = LocalQbThemeState.current
    if (!themeState.isCustomTheme) {
        return colorScheme.surfaceContainerHigh
    }
    return if (themeState.customBackgroundToneIsLight) {
        colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
    } else {
        colorScheme.surfaceContainerHighest.copy(alpha = 0.82f)
    }
}

/**
 * Bottom sheets hold forms and lists over a busy dashboard, so they stay opaque in
 * the built-in themes: at 95% alpha the bright server cards behind showed through
 * the form fields. Custom-background themes keep a light glass effect.
 */
@Composable
fun qbGlassSheetColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    val themeState = LocalQbThemeState.current
    if (!themeState.isCustomTheme) {
        return colorScheme.surfaceContainer
    }
    return colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
}

@Composable
fun qbGlassSubtleContainerColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    val themeState = LocalQbThemeState.current
    if (!themeState.isCustomTheme) {
        return colorScheme.surfaceContainerHigh
    }
    return if (themeState.customBackgroundToneIsLight) {
        colorScheme.surfaceContainer.copy(alpha = 0.50f)
    } else {
        colorScheme.surfaceContainer.copy(alpha = 0.66f)
    }
}

@Composable
fun qbGlassHoleColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    val themeState = LocalQbThemeState.current
    if (!themeState.isCustomTheme) {
        return colorScheme.surfaceContainer
    }
    return if (themeState.customBackgroundToneIsLight) {
        colorScheme.surfaceContainerHighest.copy(alpha = 0.78f)
    } else {
        colorScheme.surfaceContainerHighest.copy(alpha = 0.88f)
    }
}

@Composable
fun qbGlassOutlineColor(defaultAlpha: Float = 0.45f): Color {
    val colorScheme = MaterialTheme.colorScheme
    val themeState = LocalQbThemeState.current
    if (!themeState.isCustomTheme) {
        return colorScheme.onSurface.copy(alpha = HAIRLINE_ALPHA)
    }
    return if (themeState.customBackgroundToneIsLight) {
        colorScheme.outline.copy(alpha = 0.32f)
    } else {
        colorScheme.outline.copy(alpha = 0.24f)
    }
}

@Composable
fun qbGlassChipColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    val themeState = LocalQbThemeState.current
    if (!themeState.isCustomTheme) {
        return colorScheme.surfaceContainerHigh
    }
    return if (themeState.customBackgroundToneIsLight) {
        colorScheme.primaryContainer.copy(alpha = 0.44f)
    } else {
        colorScheme.primaryContainer.copy(alpha = 0.56f)
    }
}

@Composable
fun qbGlassEmptyStateColor(): Color {
    val colorScheme = MaterialTheme.colorScheme
    val themeState = LocalQbThemeState.current
    if (!themeState.isCustomTheme) {
        return colorScheme.surfaceContainer
    }
    return if (themeState.customBackgroundToneIsLight) {
        colorScheme.surfaceContainer.copy(alpha = 0.26f)
    } else {
        colorScheme.surfaceContainer.copy(alpha = 0.38f)
    }
}
