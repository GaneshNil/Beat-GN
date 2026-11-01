package com.marvneo.companion.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marvneo.companion.ui.theme.Spacing

/** Base surface: flat, quiet, one radius and one hairline border. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)

    Box(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base
    ) {
        Surface(
            color = Color.Transparent,
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                content = content
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.sm))
            trailing()
        }
    }
}

/** Small label + value pair used in lists and device info. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    monospace: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

enum class NoticeTone { INFO, WARNING, CRITICAL, POSITIVE }

/** Inline explanatory block. Used for honest capability statements. */
@Composable
fun InlineNotice(
    title: String,
    message: String,
    tone: NoticeTone = NoticeTone.INFO,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val accent = when (tone) {
        NoticeTone.INFO -> MaterialTheme.colorScheme.primary
        NoticeTone.WARNING -> com.marvneo.companion.ui.theme.StatusWarning
        NoticeTone.CRITICAL -> MaterialTheme.colorScheme.error
        NoticeTone.POSITIVE -> com.marvneo.companion.ui.theme.StatusPositive
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(Spacing.md)
    ) {
        BulletDot(color = accent)
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BulletDot(color: Color = MaterialTheme.colorScheme.primary, size: Int = 8) {
    Box(
        modifier = Modifier
            .padding(top = 6.dp)
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xl)
        )
        if (action != null) {
            Spacer(Modifier.height(Spacing.md))
            action()
        }
    }
}

@Composable
fun Badge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50.dp),
        color = if (filled) color else color.copy(alpha = 0.14f),
        border = if (filled) null else BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) MaterialTheme.colorScheme.surface else color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
