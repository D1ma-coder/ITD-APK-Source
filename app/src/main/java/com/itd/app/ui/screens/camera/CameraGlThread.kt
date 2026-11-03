package com.itd.app.ui.screens.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.itd.app.R
import com.itd.app.util.LogTag
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

/**
 * Draws camera frames from an OES texture onto the preview TextureView, cropping them centre-wise
 * to fill the view and compensating the sensor orientation (Telegram's single-camera path).
 */
class CameraGlThread(
    private val context: Context,
    private val outputTexture: SurfaceTexture,
    width: Int,
    height: Int,
    private val onCameraTexture: (SurfaceTexture) -> Unit,
) : HandlerThread("itd_camera_gl") {
    @Volatile var viewWidth = width
    @Volatile var viewHeight = height
    @Volatile var sensorOrientation = 0
    @Volatile var mirror = false
    @Volatile var previewWidth = 0
    @Volatile var previewHeight = 0
    var handler: Handler? = null
        private set

    private val egl = EGLContext.getEGL() as EGL10
    private var display: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var config: EGLConfig? = null
    private var program = 0
    private var positionLocation = -1
    private var textureCoordLocation = -1
    private var mvpLocation = -1
    private var stLocation = -1
    private var samplerLocation = -1
    private val texture = IntArray(1)
    private var cameraTexture: SurfaceTexture? = null
    private val vertices = floatBuffer(floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, -1f, 1f, 0f, 1f, 1f, 0f))
    private var textureCoords = floatBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))
    private val mvpMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val stMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    @Volatile private var ready = false
    private var framesAvailable = 0L
    private var framesDrawn = 0L

    override fun onLooperPrepared() {
        handler = object : Handler(looper) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MSG_INIT -> {
                        val ok = initGl()
                        LogTag.CAMERA.tree().d("GL init %s", ok)
                        if (ok) {
                            ready = true
                            cameraTexture?.let(onCameraTexture)
                        }
                    }
                    MSG_DRAW -> draw()
                    MSG_RELEASE -> {
                        releaseGl()
                        quit()
                    }
                    MSG_LAYOUT -> updateTextureCoords()
                    MSG_RECREATE_TEXTURE -> if (ready) {
                        @Suppress("UNCHECKED_CAST")
                        recreateCameraTexture(msg.obj as (SurfaceTexture) -> Unit)
                    }
                }
            }
        }.also { it.sendEmptyMessage(MSG_INIT) }
    }

    fun send(what: Int) {
        handler?.sendEmptyMessage(what)
    }

    /** Resize of the view or a new camera: recompute the crop on the GL thread. */
    fun relayout(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        send(MSG_LAYOUT)
    }

    fun recreateTexture(callback: (SurfaceTexture) -> Unit) {
        handler?.let { it.sendMessage(it.obtainMessage(MSG_RECREATE_TEXTURE, callback)) }
    }

    private fun initGl(): Boolean {
        val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY).also { display = it }
        if (display == EGL10.EGL_NO_DISPLAY) {
            LogTag.CAMERA.tree().e("eglGetDisplay failed: %s", GLUtils.getEGLErrorString(egl.eglGetError()))
            return false
        }
        if (!egl.eglInitialize(display, IntArray(2))) {
            LogTag.CAMERA.tree().e("eglInitialize failed: %s", GLUtils.getEGLErrorString(egl.eglGetError()))
            return false
        }
        val count = IntArray(1)
        val configs = arrayOfNulls<EGLConfig>(1)
        val attributes = intArrayOf(
            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 0, EGL10.EGL_DEPTH_SIZE, 0, EGL10.EGL_STENCIL_SIZE, 0,
            EGL10.EGL_NONE,
        )
        if (!egl.eglChooseConfig(display, attributes, configs, 1, count) || count[0] == 0) {
            LogTag.CAMERA.tree().e("eglChooseConfig failed")
            return false
        }
        config = configs[0]
        eglContext = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE))
        if (eglContext == null || eglContext == EGL10.EGL_NO_CONTEXT) {
            LogTag.CAMERA.tree().e("eglCreateContext failed: %s", GLUtils.getEGLErrorString(egl.eglGetError()))
            return false
        }
        eglSurface = egl.eglCreateWindowSurface(display, config, outputTexture, null)
        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
            LogTag.CAMERA.tree().e("eglCreateWindowSurface failed: %s", GLUtils.getEGLErrorString(egl.eglGetError()))
            return false
        }
        if (!egl.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) {
            LogTag.CAMERA.tree().e("eglMakeCurrent failed: %s", GLUtils.getEGLErrorString(egl.eglGetError()))
            return false
        }
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, readRaw(R.raw.itd_camera_vert))
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, readRaw(R.raw.itd_camera_frag))
        if (vertexShader == 0 || fragmentShader == 0) {
            LogTag.CAMERA.tree().e("shader compilation failed")
            return false
        }
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            LogTag.CAMERA.tree().e("program link failed: %s", GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
            return false
        }
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        textureCoordLocation = GLES20.glGetAttribLocation(program, "aTextureCoord")
        mvpLocation = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        stLocation = GLES20.glGetUniformLocation(program, "uSTMatrix")
        samplerLocation = GLES20.glGetUniformLocation(program, "sTexture")
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glDisable(GLES20.GL_BLEND)
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(stMatrix, 0)
        cameraTexture = newCameraTexture()
        return true
    }

    private fun newCameraTexture() = SurfaceTexture(texture[0]).apply {
        setOnFrameAvailableListener({
            framesAvailable++
            handler?.let {
                it.removeMessages(MSG_DRAW)
                it.sendEmptyMessage(MSG_DRAW)
            }
        }, handler)
    }

    private fun draw() {
        if (!ready) return
        val surfaceTexture = cameraTexture ?: return
        try {
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(stMatrix)
            framesDrawn++
            GLES20.glViewport(0, 0, viewWidth, viewHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0])
            GLES20.glUniform1i(samplerLocation, 0)
            vertices.position(0)
            GLES20.glVertexAttribPointer(positionLocation, 3, GLES20.GL_FLOAT, false, 12, vertices)
            GLES20.glEnableVertexAttribArray(positionLocation)
            textureCoords.position(0)
            GLES20.glVertexAttribPointer(textureCoordLocation, 2, GLES20.GL_FLOAT, false, 8, textureCoords)
            GLES20.glEnableVertexAttribArray(textureCoordLocation)
            GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(stLocation, 1, false, stMatrix, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionLocation)
            GLES20.glDisableVertexAttribArray(textureCoordLocation)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            if (!egl.eglSwapBuffers(display, eglSurface) && framesDrawn <= 5) {
                LogTag.CAMERA.tree().e("eglSwapBuffers failed: %s", GLUtils.getEGLErrorString(egl.eglGetError()))
            }
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR && framesDrawn <= 5) {
                LogTag.CAMERA.tree().e("GL error 0x%s", Integer.toHexString(error))
            }
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().e(failure, "draw failed")
        }
    }

    /** Texture coordinates that crop the camera frame to the view, like centerCrop. */
    private fun updateTextureCoords() {
        if (!ready || previewWidth <= 0 || previewHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return
        Matrix.setIdentityM(mvpMatrix, 0)
        if (mirror) Matrix.scaleM(mvpMatrix, 0, -1f, 1f, 1f)
        val rotated = sensorOrientation == 90 || sensorOrientation == 270
        val frameWidth = (if (rotated) previewHeight else previewWidth).toFloat()
        val frameHeight = (if (rotated) previewWidth else previewHeight).toFloat()
        val width = viewWidth.toFloat()
        val height = viewHeight.toFloat()
        val scale = maxOf(width / frameWidth, height / frameHeight)
        val scaledWidth = frameWidth * scale
        val scaledHeight = frameHeight * scale
        val halfX = 0.5f - (if (scaledWidth > width) (scaledWidth - width) / (scaledWidth * 2f) else 0f)
        val halfY = 0.5f - (if (scaledHeight > height) (scaledHeight - height) / (scaledHeight * 2f) else 0f)
        val left = 0.5f - halfX
        val top = 0.5f - halfY
        val right = halfX + 0.5f
        val bottom = halfY + 0.5f
        textureCoords = floatBuffer(floatArrayOf(left, top, right, top, left, bottom, right, bottom))
    }

    /** Flipping the camera: a fresh texture so the old camera's last frame does not linger. */
    private fun recreateCameraTexture(callback: (SurfaceTexture) -> Unit) {
        try {
            cameraTexture?.setOnFrameAvailableListener(null)
            cameraTexture?.release()
        } catch (failure: Throwable) {
            LogTag.CAMERA.tree().w(failure, "release of camera texture failed")
        }
        cameraTexture = null
        if (texture[0] != 0) {
            cameraTexture = try {
                newCameraTexture()
            } catch (failure: Throwable) {
                LogTag.CAMERA.tree().e(failure, "camera texture recreation failed")
                null
            }
        }
        cameraTexture?.let(callback) ?: LogTag.CAMERA.tree().e("no camera texture after recreation")
    }

    private fun releaseGl() {
        try {
            cameraTexture?.setOnFrameAvailableListener(null)
            cameraTexture?.release()
        } catch (_: Throwable) {
        }
        cameraTexture = null
        if (texture[0] != 0) {
            GLES20.glDeleteTextures(1, texture, 0)
            texture[0] = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        if (eglSurface != null && eglSurface != EGL10.EGL_NO_SURFACE) egl.eglDestroySurface(display, eglSurface)
        if (eglContext != null && eglContext != EGL10.EGL_NO_CONTEXT) egl.eglDestroyContext(display, eglContext)
        if (display != null && display != EGL10.EGL_NO_DISPLAY) {
            egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
            egl.eglTerminate(display)
        }
        eglSurface = null
        eglContext = null
        display = null
    }

    private fun readRaw(id: Int) = context.resources.openRawResource(id).bufferedReader().use { it.readText() }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] != 0) return shader
        LogTag.CAMERA.tree().e("shader compile failed: %s", GLES20.glGetShaderInfoLog(shader))
        GLES20.glDeleteShader(shader)
        return 0
    }

    companion object {
        const val MSG_INIT = 0
        const val MSG_DRAW = 1
        const val MSG_RELEASE = 2
        const val MSG_LAYOUT = 3
        const val MSG_RECREATE_TEXTURE = 4
        private const val EGL_OPENGL_ES2_BIT = 4
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098

        private fun floatBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(values)
                position(0)
            }
    }
}
