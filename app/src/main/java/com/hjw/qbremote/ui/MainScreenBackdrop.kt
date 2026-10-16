package com.hjw.qbremote.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import com.hjw.qbremote.data.AppTheme
import kotlin.math.roundToInt

private val DarkBackgroundGradient = SolidColor(Color(0xFF0B0D10))
private val LightBackgroundGradient = SolidColor(Color(0xFFF6F7F9))

@Composable
internal fun MainScreenBackdrop(
    appTheme: AppTheme,
    customBackgroundAvailable: Boolean,
    customBackgroundImagePath: String,
    customBackgroundToneIsLight: Boolean,
) {
    // Window size in px (LocalConfiguration's screen*Dp excludes insets and lags on resize).
    val containerSize = LocalWindowInfo.current.containerSize
    val backgroundTargetSizePx = remember(containerSize) {
        (maxOf(containerSize.width, containerSize.height) * 1.18f).roundToInt().coerceAtLeast(1)
    }
    val effectiveAppTheme = resolveEffectiveAppTheme(
        appTheme = appTheme,
        customBackgroundAvailable = customBackgroundAvailable,
    )
    val appBackgroundBrush = when (effectiveAppTheme) {
        AppTheme.DARK -> DarkBackgroundGradient
        AppTheme.LIGHT -> LightBackgroundGradient
        AppTheme.CUSTOM -> DarkBackgroundGradient
    }
    val customBackgroundState = rememberCustomBackgroundImageState(
        path = customBackgroundImagePath,
        targetMaxDimensionPx = backgroundTargetSizePx,
    )
    val customBackgroundImage = customBackgroundState.image
    val showCustomBackgroundImage = effectiveAppTheme == AppTheme.CUSTOM && customBackgroundImage != null
    val customBackgroundScrim = if (customBackgroundToneIsLight) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.18f)
    }
    if (showCustomBackgroundImage) {
        customBackgroundImage.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(customBackgroundScrim),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(appBackgroundBrush),
        )
    }
}
