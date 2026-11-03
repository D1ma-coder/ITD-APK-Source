package com.itd.app.ui.screens.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.itd.app.util.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class CaptureMode { PHOTO, VIDEO }

enum class CameraFacing { BACK, FRONT }

/** State and actions of the camera screen; the views in [CameraLayout] only render it. */
class CameraComponent(
    private val context: Context,
    val videoEnabled: Boolean,
    private val photoPreview: Boolean,
    val videoCaption: Boolean,
    private val onPhoto: (Uri, String) -> Unit,
    private val onVideo: (Uri, String) -> Unit,
    private val onClose: () -> Unit,
    private val requestMicrophone: () -> Unit,
) : ItdCameraView.Listener {
    private val _mode = MutableStateFlow(CaptureMode.PHOTO)
    val mode = _mode.asStateFlow()
    private val _facing = MutableStateFlow(CameraFacing.BACK)
    val facing = _facing.asStateFlow()
    private val _flash = MutableStateFlow(FlashMode.OFF)
    val flash = _flash.asStateFlow()
    private val _zoom = MutableStateFlow(1f)
    val zoom = _zoom.asStateFlow()
    private val minZoom = MutableStateFlow(1f)
    private val maxZoom = MutableStateFlow(1f)
    private val _zoomPresets = MutableStateFlow<List<Float>>(emptyList())
    val zoomPresets = _zoomPresets.asStateFlow()
    private val _recording = MutableStateFlow(false)
    val recording = _recording.asStateFlow()
    private val _flipping = MutableStateFlow(false)
    val flipping = _flipping.asStateFlow()
    private val _recordingMs = MutableStateFlow(0L)
    val recordingMs = _recordingMs.asStateFlow()
    /** A recorded video waiting in the trim preview. */
    private val _video = MutableStateFlow<File?>(null)
    val video = _video.asStateFlow()
    /** A taken photo waiting in the caption preview. */
    private val _photo = MutableStateFlow<File?>(null)
    val photo = _photo.asStateFlow()
    val error = MutableStateFlow<String?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var cameraView: ItdCameraView? = null
        set(value) {
            if (field === value) return
            field?.listener = null
            field = value
            value?.listener = this
        }
    private var timer: Job? = null
    private var recordingStartedAt = 0L

    override fun onCameraOpened() {
        val view = cameraView ?: return
        error.value = null
        val max = view.maxZoom
        LogTag.CAMERA.tree().d("camera opened, max zoom %s presets %s", max, presets(max))
        maxZoom.value = max
        _zoomPresets.value = presets(max)
        view.setFlashMode(_flash.value)
        if (_zoom.value > 1f) view.setZoom(_zoom.value)
    }

    override fun onCameraError(message: String) {
        error.value = message
    }

    override fun onFlipFinished() {
        _flipping.value = false
    }

    private fun presets(max: Float) = listOf(1f, 2f, 5f, 10f).filter { it <= max + 0.05f }

    fun setMode(mode: CaptureMode) {
        if (videoEnabled || mode != CaptureMode.VIDEO) _mode.value = mode
    }

    fun toggleFlash() {
        val next = when (_flash.value) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
        _flash.value = next
        cameraView?.setFlashMode(next)
    }

    fun setZoom(value: Float) {
        val view = cameraView
        if (view == null) {
            LogTag.CAMERA.tree().w("zoom without camera view")
            return
        }
        val zoom = value.coerceIn(minZoom.value, maxZoom.value)
        LogTag.CAMERA.tree().d("zoom %s in %s..%s (was %s)", zoom, minZoom.value, maxZoom.value, _zoom.value)
        _zoom.value = zoom
        view.setZoom(zoom)
    }

    fun flip() {
        if (_recording.value || _flipping.value) return
        val view = cameraView ?: return
        _flipping.value = true
        _facing.value = if (_facing.value == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
        view.flip()
        _zoom.value = 1f
    }

    /** The shutter: a photo, or start/stop of a recording. */
    fun shutter() {
        when (_mode.value) {
            CaptureMode.PHOTO -> takePhoto()
            CaptureMode.VIDEO -> if (!_recording.value) startRecording() else stopRecording()
        }
    }

    private fun takePhoto() {
        val view = cameraView ?: return
        val file = File(capturesDir(), "IMG_${timestamp()}_${UUID.randomUUID()}.jpg")
        val session = view.session
        if (session == null) onPhotoTaken(null) else session.takePicture(view.sensorOrientation, file, ::onPhotoTaken)
    }

    private fun onPhotoTaken(file: File?) {
        when {
            file == null -> error.value = "Не удалось сделать фото"
            !photoPreview -> onPhoto(Uri.fromFile(file), "")
            else -> _photo.value = file
        }
    }

    fun startRecording() {
        if (_recording.value) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicrophone()
            return
        }
        val view = cameraView ?: return
        val file = File(capturesDir(), "VID_${timestamp()}_${UUID.randomUUID()}.mp4")
        val session = view.session
        if (session == null) onRecordingStarted(false) else session.startRecording(file, ::onRecordingStarted)
    }

    private fun onRecordingStarted(started: Boolean) {
        if (!started) {
            error.value = "Не удалось начать запись"
            return
        }
        _recording.value = true
        recordingStartedAt = System.currentTimeMillis()
        timer?.cancel()
        timer = scope.launch {
            while (true) {
                _recordingMs.value = System.currentTimeMillis() - recordingStartedAt
                delay(500L)
            }
        }
    }

    fun stopRecording() {
        val view = cameraView ?: return
        if (!_recording.value) return
        val session = view.session
        if (session == null) onRecordingStopped(null) else session.stopRecording(::onRecordingStopped)
    }

    private fun onRecordingStopped(file: File?) {
        timer?.cancel()
        timer = null
        _recordingMs.value = 0L
        _recording.value = false
        if (file == null) error.value = "Не удалось сохранить видео" else _video.value = file
    }

    /** Video trimmed (and maybe captioned) in the preview: the result goes back to the caller. */
    fun sendVideo(file: File, caption: String) {
        _video.value = null
        onVideo(Uri.fromFile(file), caption)
    }

    fun sendPhoto(caption: String) {
        val file = _photo.value ?: return
        _photo.value = null
        onPhoto(Uri.fromFile(file), caption)
    }

    fun retakePhoto() {
        _photo.value?.let { delete(it) }
        _photo.value = null
    }

    fun discardVideo() {
        _video.value?.let { delete(it) }
        _video.value = null
    }

    /** Back: leaves a preview first, then stops a recording, then closes the camera. */
    fun back() {
        when {
            _photo.value != null -> retakePhoto()
            _video.value != null -> discardVideo()
            _recording.value -> stopRecording()
            else -> onClose()
        }
    }

    fun pause() {
        if (_recording.value) stopRecording()
        cameraView?.pause()
    }

    fun resume() {
        cameraView?.resume()
    }

    fun destroy() {
        timer?.cancel()
        cameraView = null
        scope.cancel()
    }

    private fun capturesDir() = File(context.cacheDir, "camera-captures").apply { if (!exists()) mkdirs() }

    private fun timestamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun delete(file: File) {
        try {
            file.delete()
        } catch (_: Throwable) {
        }
    }
}
