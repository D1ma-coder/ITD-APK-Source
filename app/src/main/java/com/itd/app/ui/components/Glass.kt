package com.itd.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Thin glass rim: a 1dp ring with a light top edge fading to a darker bottom edge. */
@Composable
fun Modifier.glassRim(cornerRadius: Dp = 50.dp): Modifier {
    val dark = (LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val highlight = Color(if (dark) 0x28FFFFFF else 0x40FFFFFF)
    val edge = Color(if (dark) 0x14FFFFFF else 0x20000000)
    return drawWithCache {
        val stroke = 1f * density
        val radius = minOf(cornerRadius.value * density, size.minDimension / 2f)
        val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius))) }
        val inner = Path().apply {
            addRoundRect(
                RoundRect(stroke, stroke, size.width - stroke, size.height - stroke, CornerRadius((radius - stroke).coerceAtLeast(0f))),
            )
        }
        val ring = Path().apply { op(outer, inner, PathOperation.Difference) }
        val brush = Brush.verticalGradient(listOf(highlight, Color.Transparent, edge), endY = size.height)
        onDrawWithContent {
            drawContent()
            drawPath(ring, brush)
        }
    }
}

/** 44dp round glass button of the caption bars. */
@Composable
fun GlassCircleButton(
    color: Color,
    opaque: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.size(44.dp).clip(CircleShape)
            .background(color.copy(alpha = if (opaque) 0.95f else 0.97f), CircleShape)
            .glassRim()
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

