package com.marvneo.companion.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryElectric,
    secondary = AccentAmber,
    tertiary = OxygenCyan,
    background = DarkBackground,
    surface = SurfaceCard,
    surfaceVariant = SurfaceCardElevated,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderSubtle
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryElectric,
    secondary = AccentAmber,
    background = TextPrimary,
    surface = TextPrimary,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground,
    onBackground = DarkBackground,
    onSurface = DarkBackground
)

@Composable
fun MarvNeoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Supports Android 12-16 dynamic Monet wallpaper colors
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
