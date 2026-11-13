package org.johnbrenof.dowtube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentRed,
    onPrimary = Color.White,
    primaryContainer = RedContainerDark,
    onPrimaryContainer = OnRedContainerDark,
    secondary = AccentRedDark,
    onSecondary = Color.White,
    secondaryContainer = RedContainerDark,
    onSecondaryContainer = OnRedContainerDark,
    background = BgBlack,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDarkHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDarkHigh,
    surfaceContainerHigh = SurfaceDarkHigher,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val LightColorScheme = lightColorScheme(
    primary = AccentRedLight,
    onPrimary = Color.White,
    primaryContainer = RedContainerLight,
    onPrimaryContainer = OnRedContainerLight,
    secondary = AccentRed,
    onSecondary = Color.White,
    secondaryContainer = RedContainerLight,
    onSecondaryContainer = OnRedContainerLight,
    background = BgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceLightHigh,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceLightHigh,
    surfaceContainerHigh = SurfaceLightHigher,
    outline = BorderLight,
    outlineVariant = BorderLight,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun DowTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DowTubeTypography,
        content = content,
    )
}
