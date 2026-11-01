package com.marvneo.companion.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens for the Marv Neo companion.
 *
 * Design intent: a graphite instrument panel with a single warm signal accent (copper/amber).
 * Colour is used semantically, never decoratively:
 *  - Primary  = brand / interactive (warm copper)
 *  - Heart    = heart-rate data
 *  - Oxygen   = SpO2 data
 *  - Motion   = steps / activity
 *  - Rest     = sleep / recovery
 *  - Positive / Warning / Critical = connection + measurement state only
 *
 * No gradients-as-decoration, no neon, no rainbow palettes.
 */

// ---- Neutral surfaces (dark, the default) ----
val Ink900 = Color(0xFF0E1113)      // app background
val Ink800 = Color(0xFF15191C)      // surface
val Ink700 = Color(0xFF1C2226)      // elevated surface / inputs
val Ink600 = Color(0xFF262E33)      // outline / dividers
val Ink500 = Color(0xFF37424A)      // strong outline

// ---- Neutral surfaces (light) ----
val Sand50 = Color(0xFFF7F4EF)
val Sand100 = Color(0xFFF0EBE4)
val Sand200 = Color(0xFFE2DBD1)
val Sand300 = Color(0xFFCFC6B9)

// ---- Text ----
val TextOnDark = Color(0xFFEDF1F3)
val TextOnDarkMuted = Color(0xFFA7B1B7)
val TextOnDarkFaint = Color(0xFF7A868D)
val TextOnLight = Color(0xFF1B1F22)
val TextOnLightMuted = Color(0xFF5A646A)
val TextOnLightFaint = Color(0xFF7E888D)

// ---- Brand / interaction ----
val Copper = Color(0xFFE0A458)        // primary accent, dark theme
val CopperDeep = Color(0xFFB87B33)    // primary accent, light theme (AA on sand)
val CopperContainer = Color(0xFF3A2C1B)

// ---- Semantic data colours ----
val HeartCoral = Color(0xFFE4695C)
val OxygenTeal = Color(0xFF5FB3A8)
val MotionGreen = Color(0xFF8FBF74)
val RestIndigo = Color(0xFF8B93CC)
val PressureSteel = Color(0xFF8FA6B2)

// ---- Status ----
val StatusPositive = Color(0xFF79B87A)
val StatusWarning = Color(0xFFD9A03C)
val StatusCritical = Color(0xFFD9615A)

// ---- Feature accents (kept for source compatibility with existing call sites) ----
val PrimaryElectric = Copper
val AccentAmber = StatusWarning
val HeartRateCrimson = HeartCoral
val OxygenCyan = OxygenTeal
val BloodOxygenBlue = OxygenTeal
val SleepViolet = RestIndigo
val StepsEmerald = MotionGreen

// ---- Legacy surface aliases ----
val DarkBackground = Ink900
val SurfaceCard = Ink800
val SurfaceCardElevated = Ink700
val BorderSubtle = Ink600
val SurfaceGlassHighlight = Color(0x1FFFFFFF)

val TextPrimary = TextOnDark
val TextSecondary = TextOnDarkMuted
val TextTertiary = TextOnDarkFaint
