package com.itd.app.ui.screens.camera

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import com.itd.app.R
import com.itd.app.util.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** The recorded video looping between the trim handles, with back and done buttons (or a caption bar above). */
class VideoTrimPreview(context: Context) : FrameLayout(context) {
    interface Listener {
        fun onBack()
        fun onDone(file: File, caption: String)
    }

    var listener: Listener? = null
    private val buttonColor = Color.parseColor("#66000000")
    private val textureView = TextureView(context)
    val slider = VideoTrimSlider(context)
    private val back = button(R.drawable.itd_ic_chevron_left, Color.WHITE)
    private val done = button(R.drawable.itd_ic_checkmark, Color.parseColor("#FFD300"))
    private val progress = ProgressBar(context).apply {
        isIndeterminate = true
        indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        visibility = INVISIBLE
    }
    private var busy = false
    private val sliderMargin = dpInt(74)
    private var file: File? = null
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var startMs = 0L
    private var endMs = 0L
    private var durationMs = 0L
    private var videoWidth = 0
    private var videoHeight = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playback: Job? = null

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                surface = Surface(texture)
                play()
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = fitVideo()

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                surface?.release()
                surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
        addView(slider, LayoutParams(LayoutParams.MATCH_PARENT, dpInt(60), Gravity.BOTTOM).apply {
            leftMargin = dpInt(20)
            rightMargin = dpInt(20)
            bottomMargin = dpInt(74)
        })
        slider.onTrimChange = { start, end, _ ->
            startMs = start
            endMs = end
            seek(start)
        }
        slider.onSeek = ::seek
        addView(back, LayoutParams(dpInt(40), dpInt(40), Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = dpInt(24)
            bottomMargin = dpInt(16)
        })
        back.setOnClickListener { if (file != null) listener?.onBack() }
        addView(done, LayoutParams(dpInt(40), dpInt(40), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dpInt(24)
            bottomMargin = dpInt(16)
        })
        done.setOnClickListener { finish("") }
        addView(progress, LayoutParams(dpInt(48), dpInt(48), Gravity.CENTER))
    }

    private fun button(icon: Int, tint: Int) = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(buttonColor)
        }
        isClickable = true
        isFocusable = true
        val image = ImageView(context)
        image.setImageDrawable(context.getDrawable(icon)?.mutate()?.apply { setTint(tint) })
        image.scaleType = ImageView.ScaleType.CENTER_INSIDE
        addView(image, LayoutParams(dpInt(20), dpInt(20), Gravity.CENTER))
    }

    /** With a caption bar the bar's send button finishes, so back/done are hidden. */
    fun setCaptionMode(caption: Boolean) {
        back.visibility = if (caption) GONE else VISIBLE
        done.visibility = if (caption) GONE else VISIBLE
        if (!caption) setSliderBottomMargin(sliderMargin)
    }

    /** Keeps the slider above the caption bar, whose height is [clearance]. */
    fun setSliderClearance(clearance: Int) = setSliderBottomMargin(clearance - dpInt(24))

    private fun setSliderBottomMargin(margin: Int) {
        val params = slider.layoutParams as LayoutParams
        if (params.bottomMargin != margin) {
            params.bottomMargin = margin
            slider.layoutParams = params
        }
    }

    fun show(video: File) {
        file = video
        startMs = 0L
        val duration = VideoTrimmer.durationMs(video)
        endMs = duration
        durationMs = duration
        slider.setVideo(video)
        play()
        visibility = VISIBLE
    }

    fun hide() {
        visibility = GONE
        playback?.cancel()
        releasePlayer()
    }

    fun pausePlayback() {
        try {
            player?.pause()
        } catch (_: Throwable) {
        }
    }

    fun resumePlayback() {
        try {
            if (visibility == VISIBLE) player?.start()
        } catch (_: Throwable) {
        }
    }

    /** Trims if the range was changed, then hands the file over. */
    fun finish(caption: String) {
        val source = file ?: return
        if (busy) return
        busy = true
        done.isEnabled = false
        back.isEnabled = false
        progress.visibility = VISIBLE
        scope.launch {
            var result = source
            try {
                if (startMs > 100 || durationMs - endMs > 100) {
                    val trimmed = File(source.parentFile ?: source.parentFile, "TRIM_${UUID.randomUUID()}.mp4")
                    VideoTrimmer.trim(source, startMs, endMs, trimmed)
                    try {
                        source.delete()
                    } catch (_: Throwable) {
                    }
                    result = trimmed
                }
            } catch (failure: Throwable) {
                LogTag.CAMERA.tree().e(failure, "trim failed, sending the full video")
            }
            withContext(Dispatchers.Main) {
                progress.visibility = INVISIBLE
                done.isEnabled = true
                back.isEnabled = true
                busy = false
                listener?.onDone(result, caption)
            }
        }
    }

    private fun seek(positionMs: Long) {
        val active = player ?: return
        try {
            active.seekTo(positionMs.toInt())
            if (!active.isPlaying) active.start()
        } catch (_: Throwable) {
        }
    }

    private fun play() {
        val video = file ?: return
        val target = surface ?: return
        releasePlayer()
        val created = MediaPlayer()
        try {
            created.setDataSource(video.absolutePath)
            created.setSurface(target)
            created.setOnPreparedListener {
                durationMs = it.duration.toLong()
                videoWidth = it.videoWidth
                videoHeight = it.videoHeight
                fitVideo()
                it.start()
                playback?.cancel()
                playback = scope.launch { followPlayback() }
            }
            created.setOnCompletionListener { seek(startMs) }
            created.setOnVideoSizeChangedListener { _, width, height ->
                videoWidth = width
                videoHeight = height
                fitVideo()
            }
            created.prepareAsync()
            player = created
        } catch (_: Throwable) {
            created.release()
        }
    }

    /** Moves the playhead and loops back to the trim start near the trim end. */
    private suspend fun followPlayback() {
        while (true) {
            val active = player ?: return
            try {
                val position = active.currentPosition.toLong()
                if (durationMs > 0) {
                    val fraction = position.toFloat() / durationMs
                    withContext(Dispatchers.Main) { slider.setPlayheadProgress(fraction) }
                }
                if (position >= endMs - 50 && endMs > startMs) withContext(Dispatchers.Main) { seek(startMs) }
            } catch (_: Throwable) {
                return
            }
            delay(41L)
        }
    }

    /** Letterboxes the video inside the TextureView. */
    private fun fitVideo() {
        val width = textureView.width.toFloat()
        val height = textureView.height.toFloat()
        if (width <= 0f || height <= 0f || videoWidth <= 0 || videoHeight <= 0) return
        val aspect = videoWidth.toFloat() / videoHeight
        var scaleX = 1f
        var scaleY = 1f
        if (aspect > width / height) scaleY = width / aspect / height else scaleX = aspect * height / width
        textureView.setTransform(Matrix().apply { postScale(scaleX, scaleY, width / 2f, height / 2f) })
    }

    private fun releasePlayer() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Throwable) {
        }
        player = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        playback?.cancel()
        releasePlayer()
        scope.cancel()
    }
}
