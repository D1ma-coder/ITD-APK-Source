package com.itd.app.ui.screens.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.itd.app.R
import com.itd.app.ui.components.GlassCircleButton
import com.itd.app.ui.components.RichTextEditor
import com.itd.app.ui.components.RichTextState
import com.itd.app.ui.components.glassRim
import java.io.File

private val CaptionBarColor = Color(0xFF1C1C1C)

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
    val caption = remember { RichTextState() }
    Row(modifier = modifier, horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        GlassCircleButton(CaptionBarColor, opaque = true, onClick = onBack) { BackIcon() }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                .background(CaptionBarColor.copy(alpha = 0.95f), CircleShape).glassRim(),
            contentAlignment = Alignment.BottomStart,
        ) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.TopStart) {
                RichTextEditor(
                    caption, Modifier.fillMaxWidth(),
                    TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, lineHeight = 22.sp),
                    placeholder = {
                        Text(
                            "Добавить подпись...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 17.sp, lineHeight = 22.sp,
                        )
                    },
                    maxLines = 5,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        GlassCircleButton(CaptionBarColor, opaque = true, onClick = { onSend(caption.trimmed().first) }) { SendIcon() }
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
