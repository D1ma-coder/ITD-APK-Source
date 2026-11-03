@file:Suppress("DEPRECATION")

package com.itd.app.ui.screens.camera

import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Build
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import com.itd.app.util.LogTag
import java.io.File
import java.io.FileOutputStream

enum class FlashMode { OFF, ON, AUTO }

/**
 * One opened camera (the Camera1 API, as in Telegram): preview parameters, photo, video and focus.
 * Every operation runs on [executor]'s worker thread and reports back on the main thread.
 */
class CameraSession(
    private val context: Context,
    val executor: CameraExecutor,
    val front: Boolean,
    private val info: Camera.CameraInfo,
    private val camera: Camera,
    val previewSize: Size,
    private val pictureSize: Size,
) {
    /** Told on the main thread whether the preview started; set by the view that opened the camera. */
    var previewListener: PreviewListener? = null
    val sensorOrientation = info.orientation
    private var flashMode = FlashMode.OFF
    private var zoom = 1f
    private val maxZoomIndex: Int
    val maxZoom: Float
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    @Volatile var recording = false
    @Volatile var previewing = false
    @Volatile var released = false

    init {
        val parameters = try {
            camera.parameters
        } catch (_: Throwable) {
            null
        }
        if (parameters == null || !parameters.isZoomSupported) {
            maxZoomIndex = 0
            maxZoom = 1f
        } else {
            maxZoomIndex = parameters.maxZoom
            val ratios = parameters.zoomRatios
            maxZoom = if (ratios != null && ratios.size > maxZoomIndex) ratios[maxZoomIndex] / 100f else 1f
        }
        LogTag.CAMERA.tree().d("zoom supported=%s ratios=%s", parameters?.isZoomSupported, parameters?.zoomRatios?.joinToString(","))
    }

    fun interface PreviewListener {
        fun onPreview(session: CameraSession, started: Boolean)
    }

    /** Applies preview/picture sizes, focus, flash (torch while recording) and zoom. */
    fun configure() {
        if (released) return
        try {
            val parameters = camera.parameters
            parameters.setPreviewSize(previewSize.width, previewSize.height)
            parameters.setPictureSize(pictureSize.width, pictureSize.height)
            parameters.pictureFormat = android.graphics.ImageFormat.JPEG
            parameters.jpegQuality = 100
            if (parameters.supportedFocusModes?.contains(FOCUS_CONTINUOUS) == true) parameters.focusMode = FOCUS_CONTINUOUS
            val flash = if (recording && flashMode != FlashMode.OFF) "torch" else flashMode.parameter()
            if (parameters.supportedFlashModes?.contains(flash) == true) parameters.flashMode = flash
            if (maxZoomIndex > 0) {
                val progress = (zoom - 1f).coerceAtLeast(0f) / (maxZoom - 1f).coerceAtLeast(1.0E-4f)
                parameters.zoom = (progress * maxZoomIndex).toInt().coerceIn(0, maxZoomIndex)
            }
            camera.parameters = parameters
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "configure failed")
        }
    }

    fun startPreview(texture: SurfaceTexture) = executor.post {
        if (released) return@post
        try {
            camera.setPreviewTexture(texture)
            val rotation = when ((context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            camera.setDisplayOrientation(
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) (360 - (info.orientation + rotation) % 360) % 360
                else (info.orientation - rotation + 360) % 360,
            )
            configure()
            camera.startPreview()
            previewing = true
            LogTag.CAMERA.tree().d("preview started %s", previewSize)
            executor.postMain { previewListener?.onPreview(this, true) }
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "Camera1Session error message=Не удалось открыть камеру")
            executor.postMain { previewListener?.onPreview(this, false) }
        }
    }

    fun setFlash(mode: FlashMode) = executor.post {
        if (flashMode != mode) {
            flashMode = mode
            if (previewing) configure()
        }
    }

    fun setZoom(value: Float) = executor.post {
        zoom = value.coerceIn(1f, maxZoom)
        if (previewing) configure()
    }

    /** Tap to focus: x and y are 0..1 in the preview. */
    fun focus(x: Float, y: Float) = executor.post {
        if (!previewing || recording) return@post
        try {
            val parameters = camera.parameters
            val centerX = ((x - 0.5f) * 2000f).toInt()
            val centerY = ((y - 0.5f) * 2000f).toInt()
            val area = Camera.Area(
                Rect(
                    (centerX - 100).coerceIn(-1000, 1000), (centerY - 100).coerceIn(-1000, 1000),
                    (centerX + 100).coerceIn(-1000, 1000), (centerY + 100).coerceIn(-1000, 1000),
                ),
                1000,
            )
            if (parameters.maxNumFocusAreas > 0) parameters.focusAreas = listOf(area)
            if (parameters.maxNumMeteringAreas > 0) parameters.meteringAreas = listOf(area)
            if (parameters.supportedFocusModes?.contains("auto") == true) parameters.focusMode = "auto"
            camera.parameters = parameters
            camera.cancelAutoFocus()
            camera.autoFocus { _, _ -> executor.post { resumeContinuousFocus() } }
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "focus failed")
        }
    }

    private fun resumeContinuousFocus() {
        if (released || !previewing) return
        try {
            val parameters = camera.parameters
            if (parameters.supportedFocusModes?.contains(FOCUS_CONTINUOUS) == true) parameters.focusMode = FOCUS_CONTINUOUS
            camera.parameters = parameters
        } catch (_: Throwable) {
        }
    }

    fun takePicture(rotation: Int, file: File, callback: (File?) -> Unit) = executor.post {
        if (!previewing || released || recording) {
            executor.postMain { callback(null) }
            return@post
        }
        try {
            val parameters = camera.parameters
            parameters.setRotation(rotation)
            val flash = flashMode.parameter()
            if (parameters.supportedFlashModes?.contains(flash) == true) parameters.flashMode = flash
            camera.parameters = parameters
            LogTag.CAMERA.tree().d("takePicture flash=%s", flashMode)
            camera.takePicture(null, null) { data, _ -> executor.post { savePicture(file, data, callback) } }
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "takePicture failed")
            executor.postMain { callback(null) }
        }
    }

    private fun savePicture(file: File, data: ByteArray, callback: (File?) -> Unit) {
        try {
            FileOutputStream(file).use { it.write(data) }
            LogTag.CAMERA.tree().d("photo saved %s (%d bytes)", file.name, data.size)
            executor.postMain { callback(file) }
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "photo save failed")
            executor.postMain { callback(null) }
        }
        if (!released) {
            try {
                camera.startPreview()
            } catch (failure: Throwable) {
                LogTag.CAMERA.tree().e(failure, "preview restart failed")
            }
        }
    }

    fun startRecording(file: File, callback: (Boolean) -> Unit) = executor.post {
        if (!previewing || released || recording) {
            executor.postMain { callback(false) }
            return@post
        }
        try {
            recording = true
            configure()
            val created = createRecorder(file)
            recorder = created
            recordingFile = file
            created.start()
            LogTag.CAMERA.tree().d("recording %s flash=%s", file.name, flashMode)
            executor.postMain { callback(true) }
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "recording start failed")
            releaseRecorder()
            recording = false
            try {
                camera.reconnect()
            } catch (_: Throwable) {
            }
            configure()
            try {
                camera.startPreview()
            } catch (_: Throwable) {
            }
            executor.postMain { callback(false) }
        }
    }

    fun stopRecording(callback: (File?) -> Unit) = executor.post {
        val active = recorder
        val file = recordingFile
        if (active == null) {
            executor.postMain { callback(null) }
            return@post
        }
        try {
            active.stop()
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "recorder stop failed")
        }
        releaseRecorder()
        recording = false
        try {
            camera.reconnect()
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "camera reconnect failed")
        }
        configure()
        try {
            camera.startPreview()
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "preview restart failed")
        }
        LogTag.CAMERA.tree().d("recording saved %s", file?.name)
        executor.postMain { callback(file) }
    }

    /** Stops everything and frees the camera; [then] runs on the main thread afterwards. */
    fun release(then: (() -> Unit)? = null) {
        released = true
        executor.post {
            try {
                if (recording) {
                    try {
                        recorder?.stop()
                    } catch (_: Throwable) {
                    }
                    releaseRecorder()
                    recording = false
                }
                try {
                    camera.stopPreview()
                } catch (_: Throwable) {
                }
                try {
                    camera.release()
                } catch (_: Throwable) {
                }
            } catch (failure: Throwable) {
                LogTag.CAMERA.tree().e(failure, "release failed")
            }
            if (then != null) executor.postMain(then)
        }
    }

    private fun createRecorder(file: File): MediaRecorder {
        camera.unlock()
        val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        recorder.setCamera(camera)
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setOutputFile(file.absolutePath)
        recorder.setVideoEncodingBitRate(10_000_000)
        recorder.setVideoFrameRate(30)
        recorder.setVideoSize(previewSize.width, previewSize.height)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioSamplingRate(48_000)
        recorder.setAudioEncodingBitRate(128_000)
        recorder.setAudioChannels(1)
        val orientation = if (front) (sensorOrientation + 180) % 360 else sensorOrientation
        recorder.setOrientationHint(orientation)
        LogTag.CAMERA.tree().d(
            "recorder %s %s", file.name,
            if (orientation == 90 || orientation == 270) "${previewSize.height}x${previewSize.width} (portrait)"
            else "${previewSize.width}x${previewSize.height} (landscape)",
        )
        recorder.prepare()
        return recorder
    }

    private fun releaseRecorder() {
        recorder?.let {
            try {
                it.reset()
            } catch (_: Throwable) {
            }
            try {
                it.release()
            } catch (_: Throwable) {
            }
        }
        recorder = null
        recordingFile = null
    }

    private fun FlashMode.parameter() = when (this) {
        FlashMode.OFF -> "off"
        FlashMode.ON -> "on"
        FlashMode.AUTO -> "auto"
    }

    companion object {
        private const val FOCUS_CONTINUOUS = "continuous-picture"
    }
}
