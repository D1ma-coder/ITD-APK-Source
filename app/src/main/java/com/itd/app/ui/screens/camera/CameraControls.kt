package com.itd.app.ui.screens.camera

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

internal object CameraColors {
    val accent = Color.parseColor("#FFD300")
    val record = Color.parseColor("#FF3838")
    val button = Color.parseColor("#66000000")
    val pill = Color.parseColor("#8C000000")
    val selection = Color.parseColor("#38FFFFFF")
}

internal fun View.dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

internal fun View.dpInt(value: Int) = dp(value.toFloat()).toInt()

/** Translucent round button with an icon or a text glyph. */
@SuppressLint("ViewConstructor")
class CameraRoundButton(context: Context, size: Int) : View(context) {
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CameraColors.button }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(if (size >= 40) 18f else 16f)
        textAlign = Paint.Align.CENTER
    }
    var text = ""
        set(value) {
            field = value
            icon = null
            invalidate()
        }
    private var icon: Drawable? = null
    var iconSize = if (size >= 40) 20f else 16f
        set(value) {
            field = value
            invalidate()
        }

    fun setIcon(resource: Int, tint: Int) {
        icon = context.getDrawable(resource)?.mutate()?.apply { setTint(tint) }
        text = ""
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, minOf(width, height) / 2f, background)
        val drawable = icon
        if (drawable == null) {
            canvas.drawText(text, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
            return
        }
        val h = dp(iconSize)
        val w = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight * h
        val left = (cx - w / 2f).toInt()
        val top = (cy - h / 2f).toInt()
        drawable.setBounds(left, top, left + w.toInt(), top + h.toInt())
        drawable.draw(canvas)
    }
}

/** Red dot and elapsed time shown while recording. */
@SuppressLint("ViewConstructor")
class RecordingBadge(context: Context) : LinearLayout(context) {
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CameraColors.pill }
    val time = TextView(context).apply {
        text = "0:00"
        setTextColor(Color.WHITE)
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
    }

    init {
        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(CameraColors.record)
            }
        }
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpInt(10), 0, dpInt(12), 0)
        setWillNotDraw(false)
        visibility = GONE
        addView(dot, LayoutParams(dpInt(8), dpInt(8)))
        addView(time, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dpInt(7) })
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, background)
    }
}

/** One zoom preset: "1×", "2", "5"... and the live value on the selected one. */
@SuppressLint("ViewConstructor")
class ZoomChip(context: Context, val value: Float) : View(context) {
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(76, 0, 0, 0) }
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(13f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    var label = label(value)
    /** Fixed: the original animates a "myScale" property that its chip does not have, so chips stay at 0.7. */
    private val scale = 0.7f

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        canvas.drawCircle(cx, cy, width / 2f, background)
        canvas.drawText(label, cx, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        canvas.restore()
    }

    companion object {
        fun label(value: Float): String {
            if (abs(value - 1f) < 0.05f) return "1×"
            if (value < 1f) return "%.1f".format(value)
            val whole = value.toInt().toFloat()
            return if (abs(value - whole) < 0.05f) whole.toInt().toString() else "%.1f".format(value)
        }
    }
}

/** Zoom presets in a translucent pill; the one nearest to the current zoom is highlighted. */
@SuppressLint("ViewConstructor")
class ZoomSelector(context: Context) : LinearLayout(context) {
    var onSelect: ((Float) -> Unit)? = null
    private var presets: List<Float> = emptyList()
    private var zoom = 1f
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(76, 34, 34, 34) }

    init {
        orientation = HORIZONTAL
        setPadding(dpInt(3), 0, dpInt(3), 0)
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
    }

    fun setPresets(values: List<Float>) {
        presets = values
        removeAllViews()
        values.forEach { value ->
            val chip = ZoomChip(context, value)
            chip.setOnClickListener { onSelect?.invoke(value) }
            addView(chip, LayoutParams(dpInt(37), dpInt(37)).apply {
                leftMargin = dpInt(3)
                rightMargin = dpInt(3)
            })
        }
        refresh()
    }

    fun setZoom(value: Float) {
        zoom = value
        refresh()
    }

    private fun refresh() {
        val selected = presets.indices.minByOrNull { abs(presets[it] - zoom) } ?: return
        for (i in 0 until childCount) {
            val chip = getChildAt(i) as? ZoomChip ?: continue
            val active = i == selected
            val shown = if (active) zoom else presets[i]
            chip.label = if (!active || abs(shown - chip.value) <= 0.06f) ZoomChip.label(chip.value) else "%.1f×".format(shown)
            chip.textPaint.color = if (active) CameraColors.accent else Color.WHITE
            chip.invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, background)
    }
}

/** Yellow frame with ticks shown where the user tapped to focus. */
class FocusIndicator(context: Context) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CameraColors.accent
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CameraColors.accent
        style = Paint.Style.FILL
    }
    private val rect = RectF()
    private var scale = 1.35f
    private var alphaFraction = 0f
    private val hide = Runnable {
        ValueAnimator.ofFloat(alphaFraction, 0f).apply {
            duration = 200L
            addUpdateListener {
                alphaFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        postDelayed({ visibility = INVISIBLE }, 200L)
    }

    init {
        visibility = INVISIBLE
    }

    fun show(x: Float, y: Float) {
        removeCallbacks(hide)
        visibility = VISIBLE
        translationX = x - width / 2f
        translationY = y - height / 2f
        scale = 1.35f
        alphaFraction = 0f
        invalidate()
        ValueAnimator.ofFloat(scale, 1f).apply {
            duration = 200L
            addUpdateListener {
                scale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            addUpdateListener {
                alphaFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        postDelayed(hide, 800L)
    }

    override fun onDraw(canvas: Canvas) {
        val half = minOf(width, height) * scale / 2f
        val left = width / 2f - half
        val top = height / 2f - half
        val right = width / 2f + half
        val bottom = height / 2f + half
        rect.set(left, top, right, bottom)
        stroke.alpha = (alphaFraction * 255f).toInt()
        fill.alpha = (alphaFraction * 255f).toInt()
        canvas.drawRoundRect(rect, dp(4f), dp(4f), stroke)
        val tick = dp(6f)
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val w = dp(1.5f) / 2f
        canvas.drawRect(cx - w, top, cx + w, top + tick, fill)
        canvas.drawRect(cx - w, bottom - tick, cx + w, bottom, fill)
        canvas.drawRect(left, cy - w, left + tick, cy + w, fill)
        canvas.drawRect(right - tick, cy - w, right, cy + w, fill)
    }
}

/** ФОТО / ВИДЕО switch with a sliding highlight. */
@SuppressLint("ViewConstructor")
class CaptureModeSwitch(context: Context) : FrameLayout(context) {
    var onSelect: ((CaptureMode) -> Unit)? = null
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CameraColors.pill }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CameraColors.selection }
    private val photo = label("ФОТО")
    private val video = label("ВИДЕО")
    private val rect = RectF()
    private var mode = CaptureMode.PHOTO
    private var position = 0f

    init {
        setWillNotDraw(false)
        addView(photo, LayoutParams(dpInt(82), LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dpInt(2) })
        addView(video, LayoutParams(dpInt(82), LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dpInt(2) })
        photo.setOnClickListener { select(CaptureMode.PHOTO, true) }
        video.setOnClickListener { select(CaptureMode.VIDEO, true) }
        updateColors()
    }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
        isClickable = true
    }

    fun select(value: CaptureMode, fromUser: Boolean) {
        if (mode == value) return
        mode = value
        ValueAnimator.ofFloat(position, if (value == CaptureMode.VIDEO) 1f else 0f).apply {
            duration = 300L
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener {
                position = it.animatedValue as Float
                updateColors()
                invalidate()
            }
            start()
        }
        if (fromUser) onSelect?.invoke(value)
    }

    private fun updateColors() {
        val idle = Color.argb(217, 255, 255, 255)
        photo.setTextColor(if (mode == CaptureMode.PHOTO) CameraColors.accent else idle)
        video.setTextColor(if (mode == CaptureMode.VIDEO) CameraColors.accent else idle)
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, background)
        val half = (width - dpInt(2) * 2) / 2f
        val itemWidth = half - dpInt(4) * 2
        val itemHeight = (height - dpInt(4) * 2).toFloat()
        val start = (dpInt(2) + dpInt(4)).toFloat()
        val left = (dpInt(2) + half + dpInt(4) - start) * position + start
        rect.set(left, dpInt(4).toFloat(), itemWidth + left, dpInt(4) + itemHeight)
        canvas.drawRoundRect(rect, itemHeight / 2f, itemHeight / 2f, highlight)
    }
}

enum class ShutterState { Photo, VideoIdle, VideoRecording }

/** White ring with a white circle (photo), red circle (video) or red rounded square (recording). */
@SuppressLint("ViewConstructor")
class ShutterButton(context: Context) : View(context) {
    private var state = ShutterState.Photo
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG)
    private var size = dp(60f)
    private var corner = dp(30f)
    private var color = Color.WHITE
    private val rect = RectF()

    fun setState(value: ShutterState) {
        if (state == value) return
        state = value
        val recording = value == ShutterState.VideoRecording
        ValueAnimator.ofFloat(size, dp(if (recording) 26f else 60f)).apply {
            duration = 300L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                size = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        ValueAnimator.ofFloat(corner, dp(if (recording) 5f else 30f)).apply {
            duration = 300L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                corner = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        ValueAnimator.ofArgb(color, if (value == ShutterState.Photo) Color.WHITE else CameraColors.record).apply {
            duration = 300L
            addUpdateListener {
                color = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, dp(68f) / 2f - ring.strokeWidth / 2f, ring)
        inner.color = color
        val half = size / 2f
        rect.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawRoundRect(rect, corner, corner, inner)
    }
}
