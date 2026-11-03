package com.itd.app.ui.screens.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itd.app.R
import java.io.File

private val CaptionBarColor = Color(0xFF1C1C1C)

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

@Composable
private fun BackIcon() {
    Icon(
        painterResource(R.drawable.itd_ic_chevron_left), "Назад", Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
    )
}

@Composable
private fun SendIcon() {
    Icon(painterResource(R.drawable.ic_send), "Отправить", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
}

/** Back, caption field and send, as in the photo preview and the video trim screen. */
@Composable
private fun CaptionBar(onBack: () -> Unit, onSend: (String) -> Unit, modifier: Modifier) {
    var caption by remember { mutableStateOf("") }
    Row(modifier = modifier, horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        GlassCircleButton(CaptionBarColor, opaque = true, onClick = onBack) { BackIcon() }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                .background(CaptionBarColor.copy(alpha = 0.95f), CircleShape).glassRim(),
            contentAlignment = Alignment.BottomStart,
        ) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.TopStart) {
                val style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, lineHeight = 22.sp)
                BasicTextField(
                    caption, { caption = it }, Modifier.fillMaxWidth(), textStyle = style,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), maxLines = 5,
                    decorationBox = { field ->
                        if (caption.isEmpty()) {
                            Text(
                                "Добавить подпись...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 17.sp, lineHeight = 22.sp,
                            )
                        }
                        field()
                    },
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        GlassCircleButton(CaptionBarColor, opaque = true, onClick = { onSend(caption) }) { SendIcon() }
    }
}

/** A taken photo with a caption bar; back retakes. */
@Composable
fun PhotoCaptionPreview(file: File, onSend: (String) -> Unit, onRetake: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.TopStart) {
        AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        CaptionBar(
            onRetake, onSend,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(8.dp, 8.dp),
        )
    }
}

/** Caption bar over the video trim preview; reports its height so the trim slider stays above it. */
@Composable
fun VideoCaptionBar(onBack: () -> Unit, onSend: (String) -> Unit, onHeight: (Int) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        CaptionBar(
            onBack, onSend,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { onHeight(it.height) }
                .navigationBarsPadding().imePadding().padding(8.dp, 8.dp),
        )
    }
}
