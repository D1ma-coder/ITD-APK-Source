package com.itd.app.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itd.app.data.model.UserPin

/** Display name; ИТД НУКСТА subscribers get a glowing gradient. */
@Composable
fun UserName(
    name: String,
    hasNuksta: Boolean,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.SemiBold,
) {
    if (!hasNuksta) {
        Text(name, modifier, MaterialTheme.colorScheme.onSurface, fontSize, fontWeight = fontWeight, overflow = TextOverflow.Ellipsis, maxLines = 1)
        return
    }
    val dark = isSystemInDarkTheme()
    val brush = Brush.linearGradient(
        if (dark) listOf(Color(0xFFE0F7FA), Color(0xFF4FC3F7)) else listOf(Color(0xFF26C6DA), Color(0xFF0288D1)),
    )
    val glow = if (dark) Color(0x334FC3F7) else Color(0x3326C6DA)
    Box(
        modifier.drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    isAntiAlias = true
                    color = glow.toArgb()
                    maskFilter = BlurMaskFilter(16.sp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    textSize = fontSize.toPx()
                    typeface = Typeface.DEFAULT_BOLD
                }
                canvas.nativeCanvas.drawText(name, 0f, size.height * 0.78f + 2.sp.toPx(), paint)
            }
        },
    ) {
        Text(
            name,
            fontSize = fontSize,
            fontWeight = fontWeight,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = TextStyle(brush = brush),
        )
    }
}

/** Badge image shown next to a user's name. */
@Composable
fun PinIcon(pin: UserPin, size: Dp, modifier: Modifier = Modifier) {
    val url = pin.url ?: return
    AsyncImage(url, pin.name, modifier.size(size))
}
