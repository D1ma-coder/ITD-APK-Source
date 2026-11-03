@file:Suppress("DEPRECATION")

package com.itd.app.ui.screens.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.util.Size
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.itd.app.util.LogTag

enum class CameraViewState { IDLE, OPENING, OPEN, FLIPPING_CLOSE, FLIPPING_RECREATE_ST, FLIPPING_OPEN }

/**
 * Camera preview: a TextureView fed by [CameraGlThread], with the camera opened on [executor]'s thread.
 * Rounded corners come from the outline, not from the shader.
 */
class ItdCameraView(context: Context, private val executor: CameraExecutor, private val cornerRadius: Float) :
    FrameLayout(context), TextureView.SurfaceTextureListener {

    interface Listener {
        fun onCameraOpened()
        fun onCameraError(message: String)
        fun onFlipFinished()
    }

    var listener: Listener? = null
    @Volatile var state = CameraViewState.IDLE
    private var glThread: CameraGlThread? = null
    var session: CameraSession? = null
        private set
    private var front = false
    @Volatile private var outputTexture: SurfaceTexture? = null
    @Volatile private var cameraTexture: SurfaceTexture? = null

    val maxZoom: Float get() = session?.maxZoom ?: 1f
    val sensorOrientation: Int get() = session?.sensorOrientation ?: 0

    init {
        val textureView = TextureView(context)
        textureView.surfaceTextureListener = this
        textureView.isOpaque = true
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (cornerRadius > 0f) {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        }
    }

    /** Opens the camera of the current facing for [texture], unless one is open or opening. */
    fun open(texture: SurfaceTexture, width: Int, height: Int) {
        if (session != null) return
        if (state != CameraViewState.IDLE && state != CameraViewState.FLIPPING_OPEN) {
            LogTag.CAMERA.tree().w("open ignored in state %s", state)
            return
        }
        val flipping = state == CameraViewState.FLIPPING_OPEN
        if (!flipping) state = CameraViewState.OPENING
        LogTag.CAMERA.tree().d("opening camera, state %s", state)
        val appContext = context.applicationContext
        val front = front
        executor.post {
            val opened = openCamera(appContext, front, width, height)
            executor.postMain { onOpened(texture, flipping, opened) }
        }
    }

    private fun openCamera(context: Context, front: Boolean, width: Int, height: Int): CameraSession? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            LogTag.CAMERA.tree().e("no camera permission")
            return null
        }
        val info = Camera.CameraInfo()
        val wanted = if (front) Camera.CameraInfo.CAMERA_FACING_FRONT else Camera.CameraInfo.CAMERA_FACING_BACK
        val id = (0 until Camera.getNumberOfCameras()).firstOrNull {
            Camera.getCameraInfo(it, info)
            info.facing == wanted
        }
        if (id == null) {
            LogTag.CAMERA.tree().e("no camera facing %s", wanted)
            return null
        }
        var camera: Camera? = null
        for (attempt in 0 until 3) {
            try {
                camera = Camera.open(id)
                break
            } catch (failure: Throwable) {
                LogTag.CAMERA.tree().w("Camera.open attempt %d: %s", attempt, failure.message)
                if (attempt < 2) {
                    try {
                        Thread.sleep(200L)
                    } catch (_: InterruptedException) {
                    }
                }
            }
        }
        if (camera == null) {
            LogTag.CAMERA.tree().e("Camera.open failed")
            return null
        }
        return try {
            val parameters = camera.parameters
            val preview = chooseSize(parameters.supportedPreviewSizes, width, height, 1920)
            val picture = chooseSize(parameters.supportedPictureSizes, 1920, 1080, 4096)
            LogTag.CAMERA.tree().d("preview %dx%d picture %dx%d", preview.width, preview.height, picture.width, picture.height)
            try {
                CameraSession(context, executor, front, info, camera, Size(preview.width, preview.height), Size(picture.width, picture.height))
            } catch (failure: Throwable) {
                LogTag.CAMERA.tree().e(failure, "session creation failed")
                try {
                    camera.release()
                } catch (_: Throwable) {
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun onOpened(texture: SurfaceTexture, flipping: Boolean, opened: CameraSession?) {
        if (state != CameraViewState.OPENING && state != CameraViewState.FLIPPING_OPEN) {
            LogTag.CAMERA.tree().w("camera opened in state %s, closing", state)
            opened?.release()
            return
        }
        if (opened == null) {
            state = CameraViewState.IDLE
            listener?.onCameraError("Камера недоступна")
            if (flipping) listener?.onFlipFinished()
            return
        }
        session = opened
        texture.setDefaultBufferSize(opened.previewSize.width, opened.previewSize.height)
        glThread?.let {
            it.sensorOrientation = opened.sensorOrientation
            it.mirror = opened.front
            it.previewWidth = opened.previewSize.width
            it.previewHeight = opened.previewSize.height
            it.send(CameraGlThread.MSG_LAYOUT)
        }
        opened.previewListener = CameraSession.PreviewListener { _, started ->
            if (started) {
                state = CameraViewState.OPEN
                listener?.onCameraOpened()
            } else {
                LogTag.CAMERA.tree().e("Camera1Session error message=Не удалось открыть камеру")
                state = CameraViewState.IDLE
                listener?.onCameraError("Не удалось открыть камеру")
            }
            if (flipping) listener?.onFlipFinished()
        }
        opened.startPreview(texture)
    }

    /** Switches between the back and the front camera: close, new texture, open. */
    fun flip() {
        if (state != CameraViewState.OPEN) {
            LogTag.CAMERA.tree().w("flip ignored in state %s", state)
            return
        }
        val current = session
        val gl = glThread
        if (current == null || gl == null || width <= 0 || height <= 0) {
            LogTag.CAMERA.tree().w("flip ignored: %dx%d session=%s gl=%s", width, height, current, gl)
            return
        }
        state = CameraViewState.FLIPPING_CLOSE
        front = !front
        session = null
        current.release {
            if (state != CameraViewState.FLIPPING_CLOSE) {
                LogTag.CAMERA.tree().w("flip interrupted in state %s", state)
                listener?.onFlipFinished()
                return@release
            }
            state = CameraViewState.FLIPPING_RECREATE_ST
            gl.recreateTexture { texture -> post { onFlipTexture(texture) } }
        }
    }

    private fun onFlipTexture(texture: SurfaceTexture) {
        if (state == CameraViewState.FLIPPING_RECREATE_ST) {
            cameraTexture = texture
            state = CameraViewState.FLIPPING_OPEN
            open(texture, width, height)
        } else {
            LogTag.CAMERA.tree().w("flip texture in state %s", state)
            listener?.onFlipFinished()
        }
    }

    /** Activity paused: the camera is freed, the GL surface stays. */
    fun pause() {
        LogTag.CAMERA.tree().d("pause in state %s", state)
        session?.release()
        session = null
        state = CameraViewState.IDLE
    }

    /** Activity resumed: reopen on the kept camera texture, or start GL if the surface is new. */
    fun resume() {
        if (state != CameraViewState.IDLE) {
            LogTag.CAMERA.tree().d("resume ignored in state %s", state)
            return
        }
        if (width <= 0 || height <= 0) return
        val camera = cameraTexture
        val output = outputTexture
        if (camera != null) {
            open(camera, width, height)
        } else if (output != null && glThread == null) {
            startGl(output, width, height)
        }
    }

    fun setFlashMode(mode: FlashMode) {
        session?.setFlash(mode)
    }

    fun setZoom(value: Float) {
        session?.setZoom(value)
    }

    private fun startGl(output: SurfaceTexture, width: Int, height: Int) {
        glThread = CameraGlThread(context, output, width, height) { texture ->
            post {
                cameraTexture = texture
                open(texture, this.width, this.height)
            }
        }.also { it.start() }
    }

    private fun close() {
        session?.release()
        session = null
        glThread?.send(CameraGlThread.MSG_RELEASE)
        glThread = null
        cameraTexture = null
        state = CameraViewState.IDLE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        close()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) glThread?.relayout(w, h)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        LogTag.CAMERA.tree().d("surface available %dx%d", width, height)
        outputTexture = surface
        if (glThread == null) startGl(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        LogTag.CAMERA.tree().d("surface size %dx%d", width, height)
        glThread?.relayout(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        LogTag.CAMERA.tree().d("surface destroyed")
        outputTexture = null
        close()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    companion object {
        /**
         * The supported size closest to 1920 on the long side and to the view's aspect, preferring 16:9 and 4:3
         * landscape sizes no larger than [maxSide].
         */
        fun chooseSize(sizes: List<Camera.Size>, width: Int, height: Int, maxSide: Int): Camera.Size {
            val long = maxOf(width, height).toFloat()
            val short = minOf(width, height).toFloat()
            val aspect = if (short > 0f) long / short else 16f / 9f
            val small = sizes.filter { maxOf(it.width, it.height) <= maxSide }.ifEmpty { sizes }
            val standard = small.filter {
                it.width > it.height &&
                    (Math.abs(it.width.toFloat() / it.height - 16f / 9f) < 0.05f || Math.abs(it.width.toFloat() / it.height - 4f / 3f) < 0.05f)
            }.ifEmpty { small }
            return standard.minByOrNull {
                val side = maxOf(it.width, it.height)
                Math.abs(side - 1920) / 1920f + Math.abs(side.toFloat() / minOf(it.width, it.height) - aspect)
            } ?: sizes.first()
        }
    }
}
