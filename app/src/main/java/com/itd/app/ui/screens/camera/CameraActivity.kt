package com.itd.app.ui.screens.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Full-screen camera for photos and short videos; returns the file and an optional caption. */
class CameraActivity : ComponentActivity() {
    private lateinit var executor: CameraExecutor
    private lateinit var component: CameraComponent
    private lateinit var cameraView: ItdCameraView
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            component.startRecording()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this, "Для записи видео нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Разрешите доступ к микрофону в настройках, чтобы записывать видео со звуком", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            } catch (_: Exception) {
            }
        }
    }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executor = CameraExecutor()
        window.setFormat(PixelFormat.TRANSLUCENT)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        component = CameraComponent(
            applicationContext,
            videoEnabled = intent.getBooleanExtra(VIDEO_ENABLED, true),
            photoPreview = intent.getBooleanExtra(PHOTO_PREVIEW, false),
            videoCaption = intent.getBooleanExtra(VIDEO_CAPTION, false),
            onPhoto = { uri, caption -> finishWith("photo", uri, caption) },
            onVideo = { uri, caption -> finishWith("video", uri, caption) },
            onClose = ::finish,
            requestMicrophone = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
        )
        val root = FrameLayout(this)
        setContentView(root)
        val corner = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics)
        cameraView = ItdCameraView(this, executor, corner)
        component.cameraView = cameraView
        val layout = CameraLayout(this, component, cameraView)
        root.addView(layout, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        layout.bind()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        onBackPressedDispatcher.addCallback(this) { component.back() }
        val missing = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }

    private fun finishWith(mode: String, uri: Uri, caption: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_MODE, mode).putExtra(RESULT_URI, uri).putExtra(RESULT_CAPTION, caption))
        finish()
    }

    override fun onResume() {
        super.onResume()
        component.resume()
    }

    override fun onPause() {
        super.onPause()
        component.pause()
    }

    override fun onDestroy() {
        component.destroy()
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val VIDEO_ENABLED = "itd.camera.video_enabled"
        const val PHOTO_PREVIEW = "itd.camera.photo_preview"
        const val VIDEO_CAPTION = "itd.camera.video_caption"
        const val RESULT_MODE = "itd.camera.result_mode"
        const val RESULT_URI = "itd.camera.result_uri"
        const val RESULT_CAPTION = "itd.camera.result_caption"
    }
}
