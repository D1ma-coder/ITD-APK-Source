package com.itd.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Segmented control with a sliding pill under the selected tab. */
@Composable
fun PillTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val count = tabs.size.coerceAtLeast(1)
    val left by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.7f, stiffness = 400f), label = "pillLeft")
    val right by animateFloatAsState(selected + 1f, spring(dampingRatio = 0.7f, stiffness = 200f), label = "pillRight")
    val pill = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.1f)
    val track = remember(colors.background, dark) { colors.background.shifted(if (dark) 0.08f else -0.08f) }
    Row(
        modifier.clip(CircleShape).background(track.copy(alpha = 0.92f))
            .drawBehind {
                val inset = 4.dp.toPx()
                val width = size.width - inset * 2
                val height = size.height - inset * 2
                if (width <= 0f) return@drawBehind
                val step = width / count
                val start = (left * step + inset).coerceAtLeast(0f)
                val end = (right * step + inset).coerceAtMost(size.width)
                drawRoundRect(pill, Offset(start, inset), Size(end - start, height), CornerRadius(height / 2, height / 2))
            }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, title ->
            Box(
                Modifier.weight(1f).noRippleClickable { onSelect(index) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(title, color = if (index == selected) colors.onBackground else colors.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

private fun Color.shifted(delta: Float) = Color(
    (red + delta).coerceIn(0f, 1f),
    (green + delta).coerceIn(0f, 1f),
    (blue + delta).coerceIn(0f, 1f),
    alpha,
)
