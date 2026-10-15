package com.hjw.qbremote.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hjw.qbremote.R
import com.hjw.qbremote.data.AppTheme

// Minimal dark/light system: near-black (or near-white) tonal layers instead of
// outlines, one accent, and colour reserved for meaning (transfer direction,
// state). See the redesign board: background < card < raised element.
private val QbDarkColors = darkColorScheme(
    primary = Color(0xFF5FD3C7),
    onPrimary = Color(0xFF062B27),
    primaryContainer = Color(0xFF173430),
    onPrimaryContainer = Color(0xFFBFF1EB),
    secondary = Color(0xFF8B94A1),
    onSecondary = Color(0xFF0B0D10),
    secondaryContainer = Color(0xFF1C2027),
    onSecondaryContainer = Color(0xFFECEFF3),
    tertiary = Color(0xFFE0A458),
    onTertiary = Color(0xFF2A1B06),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFECEFF3),
    surface = Color(0xFF0B0D10),
    onSurface = Color(0xFFECEFF3),
    surfaceVariant = Color(0xFF1C2027),
    onSurfaceVariant = Color(0xFF8B94A1),
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Color(0xFF101317),
    surfaceContainer = Color(0xFF14171C),
    surfaceContainerHigh = Color(0xFF1C2027),
    surfaceContainerHighest = Color(0xFF23272F),
    surfaceBright = Color(0xFF2A2F38),
    outline = Color(0xFF3A414C),
    outlineVariant = Color(0xFF22262D),
    error = Color(0xFFE5676B),
    onError = Color(0xFF2D0A0B),
    inverseSurface = Color(0xFFECEFF3),
    inverseOnSurface = Color(0xFF14171C),
)

private val QbLightColors = lightColorScheme(
    primary = Color(0xFF0E8C80),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5F2EE),
    onPrimaryContainer = Color(0xFF053B35),
    secondary = Color(0xFF5E6672),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEFF2),
    onSecondaryContainer = Color(0xFF14171C),
    tertiary = Color(0xFFB7791F),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF14171C),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFEDEFF2),
    onSurfaceVariant = Color(0xFF5E6672),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F2F5),
    surfaceContainerHighest = Color(0xFFE8EBEF),
    surfaceBright = Color(0xFFFFFFFF),
    outline = Color(0xFFC9CED6),
    outlineVariant = Color(0xFFE3E6EA),
    error = Color(0xFFC9393E),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF14171C),
    inverseOnSurface = Color(0xFFECEFF3),
)

/** UI font: Geist (bundled, variable). CJK falls back to the system font. */
@OptIn(ExperimentalTextApi::class)
val QbSansFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            resId = R.font.geist,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** Numbers only (speeds, sizes, ratios): monospaced so values don't jitter as they change. */
@OptIn(ExperimentalTextApi::class)
val QbMonoFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold).map { weight ->
        Font(
            resId = R.font.geist_mono,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

private fun sans(size: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0, line: Int? = null) =
    TextStyle(
        fontFamily = QbSansFamily,
        fontWeight = weight,
        fontSize = size.sp,
        letterSpacing = tracking.sp,
        lineHeight = (line ?: (size * 1.4).toInt()).sp,
    )

// One family, five working sizes: 28 display numbers (QbNumberStyles), 18 page
// title, 15 card title, 14 body, 12 secondary, 11 labels.
private val QbTypography = Typography(
    displaySmall = sans(28, FontWeight.Medium, -0.6),
    headlineSmall = sans(22, FontWeight.SemiBold, -0.3),
    titleLarge = sans(18, FontWeight.SemiBold, -0.2),
    titleMedium = sans(15, FontWeight.SemiBold),
    titleSmall = sans(14, FontWeight.SemiBold),
    bodyLarge = sans(15),
    bodyMedium = sans(14),
    bodySmall = sans(12),
    labelLarge = sans(14, FontWeight.Medium),
    labelMedium = sans(12, FontWeight.Medium),
    labelSmall = sans(11, FontWeight.Normal, 0.2),
)

/** Numeric text styles (Geist Mono) for speeds, sizes and other changing values. */
object QbNumberStyles {
    val display = TextStyle(fontFamily = QbMonoFamily, fontWeight = FontWeight.Medium, fontSize = 28.sp, letterSpacing = (-0.8).sp)
    val large = TextStyle(fontFamily = QbMonoFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val medium = TextStyle(fontFamily = QbMonoFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val small = TextStyle(fontFamily = QbMonoFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp)
}

data class QbThemeState(
    val appTheme: AppTheme = AppTheme.DARK,
    val customBackgroundToneIsLight: Boolean = false,
) {
    val isCustomTheme: Boolean
        get() = appTheme == AppTheme.CUSTOM

    val usesDarkPalette: Boolean
        get() = when (appTheme) {
            AppTheme.DARK -> true
            AppTheme.LIGHT -> false
            AppTheme.CUSTOM -> !customBackgroundToneIsLight
        }
}

val LocalQbThemeState = staticCompositionLocalOf { QbThemeState() }

@Composable
fun QBRemoteTheme(
    appTheme: AppTheme = AppTheme.DARK,
    customBackgroundToneIsLight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val themeState = QbThemeState(
        appTheme = appTheme,
        customBackgroundToneIsLight = customBackgroundToneIsLight,
    )
    val baseScheme = if (themeState.usesDarkPalette) QbDarkColors else QbLightColors
    val colorScheme = if (themeState.isCustomTheme) {
        glassColorScheme(
            base = baseScheme,
            useLightGlass = customBackgroundToneIsLight,
        )
    } else {
        baseScheme
    }

    CompositionLocalProvider(LocalQbThemeState provides themeState) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = QbTypography,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (themeState.isCustomTheme) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.background
                },
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                content()
            }
        }
    }
}

private fun glassColorScheme(
    base: ColorScheme,
    useLightGlass: Boolean,
): ColorScheme {
    return if (useLightGlass) {
        base.copy(
            background = Color.Transparent,
            surface = Color(0x73FFFFFF),
            surfaceVariant = Color(0x5EFFFFFF),
            surfaceContainerLowest = Color(0x40FFFFFF),
            surfaceContainerLow = Color(0x54FFFFFF),
            surfaceContainer = Color(0x66FFFFFF),
            surfaceContainerHigh = Color(0x80FFFFFF),
            surfaceContainerHighest = Color(0x94FFFFFF),
            surfaceBright = Color(0xA3FFFFFF),
            surfaceDim = Color(0x52FFFFFF),
            primaryContainer = Color(0x6BFFFFFF),
            secondaryContainer = Color(0x62FFFFFF),
            tertiaryContainer = Color(0x58FFFFFF),
            outline = Color(0x82FFFFFF),
        )
    } else {
        base.copy(
            background = Color.Transparent,
            surface = Color(0x66070D16),
            surfaceVariant = Color(0x5C1A2535),
            surfaceContainerLowest = Color(0x40050A12),
            surfaceContainerLow = Color(0x520A1320),
            surfaceContainer = Color(0x64101A29),
            surfaceContainerHigh = Color(0x7A172334),
            surfaceContainerHighest = Color(0x8F1D2B3E),
            surfaceBright = Color(0x9924364C),
            surfaceDim = Color(0x4D09111B),
            primaryContainer = Color(0x5C113544),
            secondaryContainer = Color(0x54172D49),
            tertiaryContainer = Color(0x4A1A3A38),
            outline = Color(0x8CD8E6F7),
        )
    }
}
