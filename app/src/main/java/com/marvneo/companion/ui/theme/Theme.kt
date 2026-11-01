package com.marvneo.companion.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkScheme = darkColorScheme(
    primary = Copper,
    onPrimary = Color(0xFF241703),
    primaryContainer = CopperContainer,
    onPrimaryContainer = Color(0xFFF6DFC0),
    secondary = OxygenTeal,
    onSecondary = Color(0xFF06201D),
    tertiary = RestIndigo,
    onTertiary = Color(0xFF10131F),
    background = Ink900,
    onBackground = TextOnDark,
    surface = Ink800,
    onSurface = TextOnDark,
    surfaceVariant = Ink700,
    onSurfaceVariant = TextOnDarkMuted,
    surfaceContainer = Ink800,
    surfaceContainerHigh = Ink700,
    surfaceContainerHighest = Ink700,
    outline = Ink600,
    outlineVariant = Ink500,
    error = StatusCritical,
    onError = Color(0xFF2A0A08),
    errorContainer = Color(0xFF3A1714),
    onErrorContainer = Color(0xFFF6C9C6)
)

private val LightScheme = lightColorScheme(
    primary = CopperDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E2C8),
    onPrimaryContainer = Color(0xFF3A2A12),
    secondary = Color(0xFF2F6F68),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF4C548C),
    background = Sand50,
    onBackground = TextOnLight,
    surface = Sand100,
    onSurface = TextOnLight,
    surfaceVariant = Sand200,
    onSurfaceVariant = TextOnLightMuted,
    surfaceContainer = Sand100,
    surfaceContainerHigh = Sand200,
    surfaceContainerHighest = Sand200,
    outline = Sand300,
    outlineVariant = Sand300,
    error = Color(0xFF9B3B34),
    onError = Color.White
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)

@Composable
fun MarvNeoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Deliberately no dynamic colour: the product identity must stay stable and legible.
    val colorScheme = if (darkTheme) DarkScheme else LightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

/** Shared spacing scale (4 dp base). */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}
