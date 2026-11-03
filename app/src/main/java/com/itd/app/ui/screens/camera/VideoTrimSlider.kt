package com.itd.app.ui.screens.camera

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.media.MediaMetadataRetriever
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Filmstrip of the recorded video with two handles that set the kept range, and a playhead. */
class VideoTrimSlider(context: Context) : FrameLayout(context) {
    private enum class Drag { NONE, LEFT_HANDLE, RIGHT_HANDLE, TAP }

    /** (start, end, final) whenever a handle moves; final is true when the finger lifts. */
    var onTrimChange: ((Long, Long, Boolean) -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null
    var durationMs = 0L
        private set
    var trimStartMs = 0L
        private set
    var trimEndMs = 0L
        private set
    private val handleWidth = dpInt(14)
    private val stripHeight = dpInt(50)
    private val borderHeight = dp(1.5f).toInt()
    private val handleRadius = dpInt(9)
    private val minimumMs = 500L
    private val gripWidth = dpInt(3)
    private val gripHeight = dpInt(12)
    private val playheadWidth = dpInt(3)
    private val playheadInset = dpInt(4)
    private val idleColor = Color.WHITE
    private val activeColor = Color.parseColor("#FFD300")
    private val strip = FrameLayout(context)
    private val shadeLeft = View(context)
    private val shadeRight = View(context)
    private val borderTop = View(context)
    private val borderBottom = View(context)
    private val playhead = View(context)
    private val leftHandle = FrameLayout(context)
    private val rightHandle = FrameLayout(context)
    private val leftShape = handleShape(true)
    private val rightShape = handleShape(false)
    private val leftGrip = grip()
    private val rightGrip = grip()
    private val thumbnails = ArrayList<ImageView>()
    private var drag = Drag.NONE
    private var dragStartMs = 0L
    private var dragEndMs = 0L
    private var downX = 0f
    private var panning = false
    private var playheadProgress = 0f
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var thumbnailJob: Job? = null
    private var color = -1

    init {
        val shade = Color.argb(140, 0, 0, 0)
        strip.clipToOutline = true
        strip.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, handleRadius.toFloat())
            }
        }
        strip.setBackgroundColor(Color.BLACK)
        shadeLeft.setBackgroundColor(shade)
        shadeRight.setBackgroundColor(shade)
        borderTop.setBackgroundColor(Color.WHITE)
        borderBottom.setBackgroundColor(Color.WHITE)
        playhead.setBackgroundColor(Color.WHITE)
        clipChildren = false
        addView(strip, LayoutParams(LayoutParams.MATCH_PARENT, stripHeight))
        strip.addView(shadeLeft, LayoutParams(0, 0))
        strip.addView(shadeRight, LayoutParams(0, 0))
        addView(borderTop, LayoutParams(0, borderHeight))
        addView(borderBottom, LayoutParams(0, borderHeight))
        addView(playhead, LayoutParams(playheadWidth, stripHeight - dpInt(4) * 2, Gravity.START or Gravity.CENTER_VERTICAL))
        leftHandle.background = leftShape
        rightHandle.background = rightShape
        addView(leftHandle, LayoutParams(handleWidth, stripHeight))
        addView(rightHandle, LayoutParams(handleWidth, stripHeight))
        leftHandle.addView(leftGrip, LayoutParams(gripWidth, gripHeight))
        rightHandle.addView(rightGrip, LayoutParams(gripWidth, gripHeight))
    }

    private fun handleShape(left: Boolean): ShapeDrawable {
        val r = handleRadius.toFloat()
        val radii = if (left) floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r) else floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        return ShapeDrawable(RoundRectShape(radii, null, null)).apply { paint.color = idleColor }
    }

    private fun grip() = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = gripWidth / 2f
            setColor(Color.parseColor("#343436"))
        }
    }

    /** A new video: full range, playhead at the start, filmstrip loaded in the background. */
    fun setVideo(file: File) {
        val duration = VideoTrimmer.durationMs(file)
        durationMs = duration
        trimStartMs = 0L
        trimEndMs = duration
        playheadProgress = 0f
        requestLayout()
        invalidate()
        onTrimChange?.invoke(0L, durationMs, true)
        thumbnailJob?.cancel()
        thumbnailJob = scope.launch { loadThumbnails(file) }
    }

    private suspend fun loadThumbnails(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (duration <= 0) return
            val frames = ArrayList<Bitmap?>()
            val steps = maxOf(1, FRAMES - 1)
            for (i in 0 until FRAMES) {
                frames += try {
                    retriever.getFrameAtTime(duration * 1000 * i / steps, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (_: Throwable) {
                    null
                }
            }
            withContext(Dispatchers.Main) { showThumbnails(frames.filterNotNull()) }
        } catch (_: Throwable) {
        } finally {
            retriever.release()
        }
    }

    private fun showThumbnails(frames: List<Bitmap>) {
        thumbnails.forEach { strip.removeView(it) }
        thumbnails.clear()
        frames.forEachIndexed { index, bitmap ->
            val image = ImageView(context)
            image.setImageBitmap(bitmap)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            strip.addView(image, index, LayoutParams(0, 0))
            thumbnails += image
        }
        requestLayout()
    }

    fun setPlayheadProgress(progress: Float) {
        playheadProgress = progress.coerceIn(0f, 1f)
        updatePlayhead()
    }

    private fun setPanning(value: Boolean) {
        if (panning == value) return
        panning = value
        val target = if (value || !(durationMs <= 0 || (trimStartMs <= 10 && trimEndMs >= durationMs - 10))) activeColor else idleColor
        if (target == color) return
        ValueAnimator.ofArgb(color, target).apply {
            duration = 180L
            addUpdateListener {
                val c = it.animatedValue as Int
                leftShape.paint.color = c
                rightShape.paint.color = c
                leftHandle.invalidate()
                rightHandle.invalidate()
                borderTop.setBackgroundColor(c)
                borderBottom.setBackgroundColor(c)
            }
            color = target
            start()
        }
    }

    private fun xOf(fraction: Float) = (width - handleWidth) * fraction + handleWidth / 2f

    private fun updatePlayhead() {
        if (durationMs <= 0) {
            playhead.visibility = INVISIBLE
            return
        }
        playhead.translationX = xOf(playheadProgress) - playheadWidth / 2f
        val start = trimStartMs.toFloat() / durationMs
        val end = trimEndMs.toFloat() / durationMs
        playhead.visibility = if (playheadProgress >= start - 0.001f && playheadProgress <= end + 0.001f) VISIBLE else INVISIBLE
    }

    private fun layoutHandles() {
        if (durationMs <= 0) return
        val top = (height - stripHeight) / 2
        val bottom = top + stripHeight
        val left = (xOf(trimStartMs.toFloat() / durationMs) - handleWidth / 2f).roundToInt()
        val right = (xOf(trimEndMs.toFloat() / durationMs) - handleWidth / 2f).roundToInt()
        leftHandle.layout(left, top, left + handleWidth, bottom)
        rightHandle.layout(right, top, right + handleWidth, bottom)
        val gripTop = (stripHeight - gripHeight) / 2
        val gripLeft = (handleWidth - gripWidth) / 2
        leftGrip.layout(gripLeft, gripTop, gripLeft + gripWidth, gripTop + gripHeight)
        rightGrip.layout(gripLeft, gripTop, gripLeft + gripWidth, gripTop + gripHeight)
        val inner = left + handleWidth
        if (right > inner) {
            borderTop.layout(inner, top, right, top + borderHeight)
            borderBottom.layout(inner, bottom - borderHeight, right, bottom)
            borderTop.visibility = VISIBLE
            borderBottom.visibility = VISIBLE
        } else {
            borderTop.visibility = INVISIBLE
            borderBottom.visibility = INVISIBLE
        }
        shadeLeft.layout(0, 0, left + handleWidth, stripHeight)
        shadeRight.layout(right, 0, width, stripHeight)
        updatePlayhead()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (width == 0 || height == 0) return
        val stripTop = (height - stripHeight) / 2
        strip.layout(0, stripTop, width, stripTop + stripHeight)
        if (thumbnails.isNotEmpty()) {
            val step = width.toFloat() / thumbnails.size
            thumbnails.forEachIndexed { i, image -> image.layout((i * step).roundToInt(), 0, ((i + 1) * step).roundToInt(), stripHeight) }
        }
        layoutHandles()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return false
        val x = event.x
        val y = event.y
        val slop = dpInt(24)
        val leftRect = RectF((leftHandle.left - slop).toFloat(), leftHandle.top.toFloat(), (leftHandle.right + slop).toFloat(), leftHandle.bottom.toFloat())
        val rightRect = RectF((rightHandle.left - slop).toFloat(), rightHandle.top.toFloat(), (rightHandle.right + slop).toFloat(), rightHandle.bottom.toFloat())
        val inLeft = leftRect.contains(x, y)
        val inRight = rightRect.contains(x, y)
        drag = when {
            inLeft && inRight ->
                if (abs(x - (leftHandle.left + handleWidth / 2f)) <= abs(x - (rightHandle.left + handleWidth / 2f))) Drag.LEFT_HANDLE else Drag.RIGHT_HANDLE
            inLeft -> Drag.LEFT_HANDLE
            inRight -> Drag.RIGHT_HANDLE
            y >= strip.top && y <= strip.bottom && x >= strip.left && x <= strip.right -> Drag.TAP
            else -> Drag.NONE
        }
        downX = x
        if (drag == Drag.LEFT_HANDLE) {
            dragStartMs = trimStartMs
            setPanning(true)
        } else if (drag == Drag.RIGHT_HANDLE) {
            dragEndMs = trimEndMs
            setPanning(true)
        }
        return drag != Drag.NONE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return drag != Drag.NONE
            MotionEvent.ACTION_UP -> {
                when (drag) {
                    Drag.LEFT_HANDLE, Drag.RIGHT_HANDLE -> {
                        setPanning(false)
                        onTrimChange?.invoke(trimStartMs, trimEndMs, true)
                    }
                    Drag.TAP -> {
                        val track = width - handleWidth
                        if (track > 0 && durationMs > 0) {
                            val fraction = ((event.x - handleWidth / 2f) / track).coerceIn(0f, 1f)
                            onSeek?.invoke((durationMs * fraction).toLong().coerceIn(trimStartMs, trimEndMs))
                        }
                    }
                    Drag.NONE -> Unit
                }
                drag = Drag.NONE
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val track = width - handleWidth
                if (track > 0 && durationMs > 0) {
                    val deltaMs = ((event.x - downX) / track * durationMs).toLong()
                    if (drag == Drag.LEFT_HANDLE) {
                        val start = (dragStartMs + deltaMs).coerceIn(0L, trimEndMs - minimumMs)
                        if (start != trimStartMs) {
                            trimStartMs = start
                            layoutHandles()
                            onTrimChange?.invoke(trimStartMs, trimEndMs, false)
                        }
                    } else if (drag == Drag.RIGHT_HANDLE) {
                        val end = (dragEndMs + deltaMs).coerceIn(trimStartMs + minimumMs, durationMs)
                        if (end != trimEndMs) {
                            trimEndMs = end
                            layoutHandles()
                            onTrimChange?.invoke(trimStartMs, trimEndMs, false)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (drag == Drag.LEFT_HANDLE || drag == Drag.RIGHT_HANDLE) {
                    setPanning(false)
                    onTrimChange?.invoke(trimStartMs, trimEndMs, true)
                }
                drag = Drag.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        thumbnailJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val FRAMES = 8
    }
}
