package com.marvneo.companion.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marvneo.companion.ui.theme.PrimaryElectric

/**
 * Liquid Glassmorphism Card Container.
 * Features frosted glass translucency, specular border reflections, and smooth depth.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    glowColor: Color = PrimaryElectric,
    glowAlpha: Float = 0.08f,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassGradient = Brush.linearGradient(
        colors = listOf(
            Color(0x28FFFFFF),
            Color(0x121A2A44),
            Color(0x180D1526)
        )
    )

    val borderGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.35f),
            glowColor.copy(alpha = 0.25f),
            Color.White.copy(alpha = 0.08f)
        )
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(glassGradient)
            .border(BorderStroke(1.dp, borderGradient), shape)
    ) {
        // Specular ambient glow layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
        ) {
            content()
        }
    }
}

/**
 * Liquid Glassmorphism Pill Badge.
 */
@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    color: Color = PrimaryElectric,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

/**
 * Radar Pulse Wave Animation for Device Discovery.
 */
@Composable
fun RadarPulseIndicator(
    modifier: Modifier = Modifier,
    color: Color = PrimaryElectric
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarScale"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAlpha"
    )

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(36.dp * waveScale)
                .clip(CircleShape)
                .background(color.copy(alpha = waveAlpha))
        )
        // Center solid core
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}
