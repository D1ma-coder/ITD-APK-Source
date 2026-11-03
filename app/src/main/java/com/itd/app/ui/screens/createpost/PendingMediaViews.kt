package com.itd.app.ui.screens.createpost

import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.SubcomposeAsyncImage
import com.itd.app.R

/** Dark veil with retry, processing spinner or upload progress while an attachment is not uploaded yet. */
@Composable
private fun BoxScope.UploadOverlay(state: UploadState, onRetry: () -> Unit, shape: RoundedCornerShape) {
    val processing = state is UploadState.Processing
    val failed = state is UploadState.Error
    val visible = state is UploadState.Pending || state is UploadState.Uploading || processing || failed
    val progress by animateFloatAsState((state as? UploadState.Uploading)?.progress ?: 0f, tween(300), label = "")
    if (!visible) return
    Box(Modifier.matchParentSize().clip(shape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
        when {
            failed -> Icon(
                painterResource(R.drawable.ic_retry), "Повторить",
                Modifier.size(32.dp).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onRetry),
                tint = Color.White,
            )
            processing -> CircularProgressIndicator(Modifier.size(32.dp), Color.White, 3.dp, Color.White.copy(alpha = 0.25f))
            else -> CircularProgressIndicator({ progress }, Modifier.size(32.dp), Color.White, 3.dp, Color.White.copy(alpha = 0.25f))
        }
    }
}

@Composable
private fun BoxScope.RemoveButton(description: String, onRemove: () -> Unit) {
    Box(
        Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_close), description, Modifier.size(12.dp), tint = Color.White)
    }
}

/** Photo attachment of the editor: 200dp high at its own aspect, with its upload state. */
@Composable
fun PendingPhotoView(photo: PendingMedia, onRemove: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    Box(modifier.height(200.dp)) {
        SubcomposeAsyncImage(
            photo.uri, null,
            Modifier.height(200.dp).clip(shape).border(0.5.dp, border, shape),
            contentScale = ContentScale.FillHeight,
        )
        UploadOverlay(photo.state, onRetry, shape)
        RemoveButton("Remove photo", onRemove)
    }
}

/** Video attachment: a muted looping preview 180dp (landscape) or 250dp (portrait) high. */
@Composable
fun PendingVideoView(video: PendingMedia, onRemove: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    var aspect by remember { mutableFloatStateOf(16f / 9f) }
    val player = remember(video.uri) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(video.uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) aspect = videoSize.width.toFloat() / videoSize.height
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Box(modifier.height(if (aspect >= 1f) 180.dp else 250.dp).aspectRatio(aspect, true).clip(shape).border(0.5.dp, border, shape)) {
        AndroidView(
            { TextureView(it).apply { layoutParams = ViewGroup.LayoutParams(-1, -1); player.setVideoTextureView(this) } },
            Modifier.fillMaxSize(),
        )
        UploadOverlay(video.state, onRetry, shape)
        RemoveButton("Remove video", onRemove)
    }
}
