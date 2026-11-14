package org.johnbrenof.dowtube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Rose,
    onPrimary = White,
    primaryContainer = RoseDeep,
    onPrimaryContainer = White,
    secondary = Rose,
    onSecondary = White,
    secondaryContainer = Color.Transparent,
    onSecondaryContainer = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Black,
    onSurfaceVariant = TextMutedDark,
    surfaceContainerLow = Black,
    surfaceContainer = Black,
    surfaceContainerHigh = Black,
    outline = DividerDark,
    outlineVariant = DividerDark,
    error = Rose,
    onError = White,
    errorContainer = Color.Transparent,
    onErrorContainer = Rose,
)

private val LightColorScheme = lightColorScheme(
    primary = RoseDeep,
    onPrimary = White,
    primaryContainer = Rose,
    onPrimaryContainer = White,
    secondary = RoseDeep,
    onSecondary = White,
    secondaryContainer = Color.Transparent,
    onSecondaryContainer = RoseDeep,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    surfaceVariant = White,
    onSurfaceVariant = TextMutedLight,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = White,
    outline = DividerLight,
    outlineVariant = DividerLight,
    error = RoseDeep,
    onError = White,
    errorContainer = Color.Transparent,
    onErrorContainer = RoseDeep,
)

@Composable
fun DowTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = DowTubeTypography,
        content = content,
    )
}
