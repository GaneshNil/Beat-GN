package com.marvneo.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marvneo.companion.ui.theme.Spacing

/**
 * A single metric. [value] is a pre-formatted string because "no data" must be distinguishable
 * from "zero" — callers pass "—" rather than 0 when nothing has been received.
 */
@Composable
fun MetricTile(
    title: String,
    value: String,
    unit: String? = null,
    caption: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Which bar labels stay visible for [count] bars.
 *
 * Up to seven bars keep every label. With more bars the labels are thinned to roughly six evenly
 * spaced positions plus the last one, so a 28–31 day month cannot render 31 colliding labels.
 * The final label is dropped if it would sit next to the previous one.
 */
internal fun sparkLabelIndices(count: Int): Set<Int> {
    if (count <= 7) return (0 until count).toSet()
    val every = kotlin.math.ceil(count / 6.0).toInt()
    val indices = ((0 until count) step every).toMutableSet()
    val last = count - 1
    indices.add(last)
    val previous = indices.filter { it != last }.maxOrNull()
    if (previous != null && previous != 0 && last - previous <= every / 2) {
        indices.remove(previous)
    }
    return indices
}

/** Horizontal mini bar chart. Values are averages per day; nulls render as empty slots. */
@Composable
fun SparkBars(
    values: List<Double?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    heightDp: Int = 96
) {
    val max = values.filterNotNull().maxOrNull() ?: 0.0
    val labelIndices = sparkLabelIndices(values.size)
    if (values.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minimumWidth = 44.dp * LocalDensity.current.fontScale
        val cellWidth = maxOf(maxWidth / values.size, minimumWidth)
        val scrolls = cellWidth * values.size > maxWidth
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEachIndexed { index, value ->
                val label = labels.getOrElse(index) { "" }
                val reading = value?.let { kotlin.math.round(it).toInt() }
                val fraction = if (value == null || max <= 0.0) 0f else (value / max).toFloat()
                Column(
                    modifier = Modifier.width(cellWidth).semantics {
                        contentDescription = "$label, ${reading?.let { "$it BPM" } ?: "no reading"}"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.height(heightDp.dp), contentAlignment = Alignment.BottomCenter) {
                        if (value != null) {
                            Box(
                                modifier = Modifier
                                    .width(minOf(24.dp, cellWidth - 6.dp))
                                    .height((heightDp * fraction).dp.coerceAtLeast(2.dp))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accent.copy(alpha = 0.75f))
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (scrolls || index in labelIndices) label else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics { }
                    )
                }
            }
        }
    }
}

/** Compact key/value line with a coloured dot, used in the watch summary. */
@Composable
fun DotRow(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.8f))
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
