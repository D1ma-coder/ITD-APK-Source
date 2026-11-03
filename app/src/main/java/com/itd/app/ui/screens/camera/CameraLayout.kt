package com.itd.app.ui.screens.camera

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.itd.app.R
import com.itd.app.ui.theme.ItdTheme
import com.itd.app.util.LogTag
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The camera screen: a 16:9 preview under the status bar with black bands around it, flash, zoom and
 * recording badge over the preview, mode switch and shutter row below, and the photo/video previews on top.
 */
@SuppressLint("ViewConstructor")
class CameraLayout(
    private val activity: ComponentActivity,
    private val component: CameraComponent,
    val cameraView: ItdCameraView,
) : FrameLayout(activity) {
    private val frame = FrameLayout(activity)
    private val trimPreview = VideoTrimPreview(activity).apply { visibility = GONE }
    private val flashButton = CameraRoundButton(activity, 36)
    private val recordingBadge = RecordingBadge(activity)
    private val zoomSelector = ZoomSelector(activity)
    private val focusIndicator = FocusIndicator(activity)
    private val modeSwitch = CaptureModeSwitch(activity)
    private val shutter = ShutterButton(activity)
    private val flipButton = CameraRoundButton(activity, 40)
    private var photoPreview: ComposeView? = null
    private var captionBar: ComposeView? = null
    private val previewRect = RectF()
    private var insetTop = 0
    private var insetBottom = 0
    private val bands = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    init {
        setWillNotDraw(false)
        setOnApplyWindowInsetsListener { _, insets ->
            val bars = WindowInsetsCompat.toWindowInsetsCompat(insets)
                .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (insetTop != bars.top || insetBottom != bars.bottom) {
                insetTop = bars.top
                insetBottom = bars.bottom
                if (width > 0 && height > 0) relayout(width, height)
            }
            insets
        }
        frame.clipChildren = false
        addView(frame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        (cameraView.parent as? ViewGroup)?.removeView(cameraView)
        frame.addView(cameraView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        flashButton.iconSize = 20f
        flashButton.setIcon(R.drawable.itd_ic_flash_off, Color.WHITE)
        flashButton.setOnClickListener { component.toggleFlash() }
        frame.addView(flashButton, LayoutParams(dpInt(36), dpInt(36), Gravity.TOP or Gravity.END).apply {
            topMargin = dpInt(16)
            rightMargin = dpInt(16)
        })
        frame.addView(recordingBadge, LayoutParams(LayoutParams.WRAP_CONTENT, dpInt(28), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dpInt(14)
        })
        zoomSelector.onSelect = component::setZoom
        frame.addView(zoomSelector, LayoutParams(LayoutParams.WRAP_CONTENT, dpInt(43), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dpInt(14)
        })
        frame.addView(focusIndicator, LayoutParams(dpInt(76), dpInt(76)))

        modeSwitch.onSelect = component::setMode
        addView(modeSwitch, LayoutParams(dpInt(168), dpInt(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dpInt(108)
        })
        modeSwitch.visibility = if (component.videoEnabled) VISIBLE else GONE

        val bottomBar = FrameLayout(activity)
        addView(bottomBar, LayoutParams(LayoutParams.MATCH_PARENT, dpInt(80), Gravity.BOTTOM).apply { bottomMargin = dpInt(16) })
        val close = CameraRoundButton(activity, 40).apply { text = "✕" }
        close.setOnClickListener { component.back() }
        bottomBar.addView(close, LayoutParams(dpInt(40), dpInt(40), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dpInt(24) })
        shutter.setOnClickListener { component.shutter() }
        bottomBar.addView(shutter, LayoutParams(dpInt(80), dpInt(80), Gravity.CENTER))
        flipButton.setIcon(R.drawable.itd_ic_flip, Color.WHITE)
        flipButton.setOnClickListener { component.flip() }
        bottomBar.addView(flipButton, LayoutParams(dpInt(40), dpInt(40), Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dpInt(24) })

        frame.setOnTouchListener(PreviewTouch())
        addView(trimPreview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        trimPreview.setCaptionMode(component.videoCaption)
        trimPreview.listener = object : VideoTrimPreview.Listener {
            override fun onBack() = component.discardVideo()
            override fun onDone(file: File, caption: String) = component.sendVideo(file, caption)
        }
    }

    /** Connects the views to the component's state for the activity's lifetime. */
    fun bind() {
        val scope = activity.lifecycleScope
        scope.launch { component.photo.collect(::showPhoto) }
        scope.launch {
            component.mode.collect { mode ->
                shutter.setState(shutterState(component.recording.value, mode))
                modeSwitch.select(mode, false)
            }
        }
        scope.launch {
            component.recording.collect { recording ->
                shutter.setState(shutterState(recording, component.mode.value))
                recordingBadge.visibility = if (recording) VISIBLE else GONE
                modeSwitch.alpha = if (recording) 0.4f else 1f
            }
        }
        scope.launch {
            combine(component.recording, component.flipping) { recording, flipping -> recording || flipping }.collect { busy ->
                flipButton.alpha = if (busy) 0.4f else 1f
                flipButton.isEnabled = !busy
            }
        }
        scope.launch {
            component.recordingMs.collect {
                val seconds = (it / 1000).toInt()
                recordingBadge.time.text = "%d:%02d".format(seconds / 60, seconds % 60)
            }
        }
        scope.launch {
            component.flash.collect { mode ->
                when (mode) {
                    FlashMode.OFF -> flashButton.setIcon(R.drawable.itd_ic_flash_off, Color.WHITE)
                    FlashMode.ON -> flashButton.setIcon(R.drawable.itd_ic_flash_on, CameraColors.accent)
                    FlashMode.AUTO -> flashButton.setIcon(R.drawable.itd_ic_flash_auto, CameraColors.accent)
                }
                flashButton.alpha = 1f
            }
        }
        scope.launch {
            component.zoomPresets.collect { presets ->
                LogTag.CAMERA.tree().d("zoom presets %s", presets)
                zoomSelector.setPresets(presets)
                zoomSelector.visibility = if (presets.size >= 2 && component.facing.value == CameraFacing.BACK) VISIBLE else GONE
            }
        }
        scope.launch { component.zoom.collect(zoomSelector::setZoom) }
        scope.launch {
            component.facing.collect { facing ->
                zoomSelector.visibility = if (facing == CameraFacing.BACK && component.zoomPresets.value.size >= 2) VISIBLE else GONE
                flashButton.visibility = if (facing == CameraFacing.BACK) VISIBLE else GONE
            }
        }
        scope.launch { component.video.collect(::showVideo) }
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> trimPreview.pausePlayback()
                Lifecycle.Event.ON_RESUME -> trimPreview.resumePlayback()
                else -> Unit
            }
        })
    }

    private fun shutterState(recording: Boolean, mode: CaptureMode) = when {
        recording -> ShutterState.VideoRecording
        mode == CaptureMode.VIDEO -> ShutterState.VideoIdle
        else -> ShutterState.Photo
    }

    /** A taken photo replaces the live camera with its caption preview; the camera pauses meanwhile. */
    private fun showPhoto(file: File?) {
        val shown = photoPreview != null
        photoPreview?.let {
            removeView(it)
            it.disposeComposition()
        }
        photoPreview = null
        if (shown) component.resume()
        if (file == null) return
        component.pause()
        photoPreview = ComposeView(context).apply {
            setContent {
                ItdTheme(darkTheme = true) { PhotoCaptionPreview(file, component::sendPhoto, component::retakePhoto) }
            }
        }
        addView(photoPreview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** A recorded video opens the trim preview, with a caption bar when the caller wants a caption. */
    private fun showVideo(file: File?) {
        if (file != null) {
            if (component.videoCaption) {
                component.pause()
                captionBar?.let {
                    removeView(it)
                    it.disposeComposition()
                }
                captionBar = ComposeView(context).apply {
                    setContent {
                        ItdTheme(darkTheme = true) {
                            VideoCaptionBar(component::discardVideo, trimPreview::finish, trimPreview::setSliderClearance)
                        }
                    }
                }
                addView(captionBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }
            trimPreview.show(file)
        } else {
            trimPreview.hide()
            if (component.videoCaption) {
                captionBar?.let {
                    removeView(it)
                    it.disposeComposition()
                }
                captionBar = null
                component.resume()
            }
        }
    }

    /** Places the 16:9 preview under the status bar, shrinking it to leave room for the controls. */
    private fun relayout(width: Int, height: Int) {
        val top = dpInt(8) + insetTop
        var previewHeight = (width * 16f / 9f).toInt()
        val available = (height - insetBottom - dpInt(if (component.videoEnabled) 156 else 96) - dpInt(16) - top).coerceAtLeast(dpInt(200))
        if (previewHeight > available) previewHeight = available
        val bottom = top + previewHeight
        previewRect.set(0f, top.toFloat(), width.toFloat(), bottom.toFloat())
        (modeSwitch.layoutParams as LayoutParams).bottomMargin = dpInt(108) + insetBottom
        modeSwitch.layoutParams = modeSwitch.layoutParams
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !== frame && child !== modeSwitch && child is FrameLayout) {
                val params = child.layoutParams as LayoutParams
                params.bottomMargin = dpInt(16) + insetBottom
                child.layoutParams = params
            }
        }
        cameraView.layoutParams = (cameraView.layoutParams as LayoutParams).apply {
            this.width = width
            this.height = previewHeight
            leftMargin = 0
            topMargin = top
        }
        flashButton.layoutParams = (flashButton.layoutParams as LayoutParams).apply {
            topMargin = dpInt(16) + top
            rightMargin = dpInt(16)
        }
        recordingBadge.layoutParams = (recordingBadge.layoutParams as LayoutParams).apply { topMargin = dpInt(14) + top }
        zoomSelector.layoutParams = (zoomSelector.layoutParams as LayoutParams).apply { bottomMargin = dpInt(14) + (height - bottom) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (previewRect.isEmpty) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (previewRect.top > 0f) canvas.drawRect(0f, 0f, w, previewRect.top, bands)
        if (previewRect.bottom < h) canvas.drawRect(0f, previewRect.bottom, w, h, bands)
        if (previewRect.left > 0f) canvas.drawRect(0f, previewRect.top, previewRect.left, previewRect.bottom, bands)
        if (previewRect.right < w) canvas.drawRect(previewRect.right, previewRect.top, w, previewRect.bottom, bands)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout(w, h)
    }

    /** Pinch to zoom; a tap without movement focuses there and shows the focus frame. */
    private inner class PreviewTouch : OnTouchListener {
        private var secondPointer = -1
        private var startDistance = 0f
        private var startZoom = 1f
        private var downX = 0f
        private var downY = 0f
        private var moved = false

        private fun distance(event: MotionEvent): Float {
            if (event.pointerCount < 2) return 0f
            val dx = event.getX(0) - event.getX(1)
            val dy = event.getY(0) - event.getY(1)
            return sqrt(dy * dy + dx * dx)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    moved = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) {
                    secondPointer = event.getPointerId(1)
                    startDistance = distance(event)
                    startZoom = component.zoom.value
                }
                MotionEvent.ACTION_MOVE -> {
                    if (secondPointer != -1 && event.pointerCount >= 2 && startDistance > 0f) {
                        component.setZoom(distance(event) / startDistance * startZoom)
                        moved = true
                    } else if (abs(event.x - downX) > dpInt(8) || abs(event.y - downY) > dpInt(8)) {
                        moved = true
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> if (event.pointerCount <= 2) secondPointer = -1
                MotionEvent.ACTION_UP -> {
                    if (!moved && secondPointer == -1) {
                        val x = (event.x / view.width).coerceIn(0f, 1f)
                        val y = (event.y / view.height).coerceIn(0f, 1f)
                        component.cameraView?.session?.focus(x, y)
                        focusIndicator.show(event.x, event.y)
                    }
                    secondPointer = -1
                }
            }
            return true
        }
    }
}
