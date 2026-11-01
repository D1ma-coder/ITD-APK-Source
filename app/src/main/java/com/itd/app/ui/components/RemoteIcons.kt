package com.itd.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.itd.app.util.LogTag
import java.net.URL
import java.nio.ByteBuffer
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A post action icon published on the CDN; [tint] is set when the SVG carries its own colors. */
class RemoteIcon(val image: ImageBitmap, val tint: Color?)

object RemoteIcons {
    val like: MutableState<RemoteIcon?> = mutableStateOf(null)
    val liked: MutableState<RemoteIcon?> = mutableStateOf(null)
    val comment: MutableState<RemoteIcon?> = mutableStateOf(null)
    val share: MutableState<RemoteIcon?> = mutableStateOf(null)

    /** Default color of the "liked" heart. */
    val LikedColor = Color(0xFFFF0040)

    private val FILL = Regex("fill\\s*[:=]\\s*[\"']?\\s*([^\"';>\\s]+)", RegexOption.IGNORE_CASE)
    private val NO_COLOR = setOf("none", "transparent", "currentcolor")

    @Volatile
    private var lastRefreshAt = 0L

    /** Refreshes all icons at most every 30 minutes. */
    fun refresh(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (lastRefreshAt != 0L && now - lastRefreshAt < 1_800_000) return
        lastRefreshAt = now
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { load(appContext, "like.svg", like) }
        scope.launch { load(appContext, "liked.svg", liked) }
        scope.launch { load(appContext, "comment.svg", comment) }
        scope.launch { load(appContext, "share.svg", share) }
    }

    private suspend fun load(context: Context, name: String, target: MutableState<RemoteIcon?>) {
        repeat(3) {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { URL("https://cdn.xn--d1ah4a.com/public/assets/icons/$name").readBytes() }.getOrNull()
            } ?: return@repeat
            val request = ImageRequest.Builder(context)
                .data(ByteBuffer.wrap(bytes))
                .size(72)
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
            val result = Coil.imageLoader(context).execute(request)
            if (result !is SuccessResult) {
                LogTag.UI.tree().w("icon %s failed to decode", name)
                return
            }
            val bitmap = result.drawable.toBitmap()
            val svg = String(bytes)
            val colored = FILL.findAll(svg).any { it.groupValues[1].trim().lowercase(Locale.ROOT) !in NO_COLOR }
            target.value = RemoteIcon(bitmap.asImageBitmap(), if (colored) dominantColor(bitmap) else null)
            return
        }
    }

    private fun dominantColor(bitmap: Bitmap): Color? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val counts = HashMap<Int, Int>()
        for (pixel in pixels) {
            if (pixel ushr 24 < 230) continue
            val opaque = pixel or 0xFF000000.toInt()
            counts[opaque] = (counts[opaque] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key?.let(::Color)
    }
}
