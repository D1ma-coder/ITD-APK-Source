package com.itd.app.ui.screens.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.itd.app.R
import com.itd.app.ui.theme.ItdTheme
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val videoEnabled = intent.getBooleanExtra(VIDEO_ENABLED, true)
        val photoPreview = intent.getBooleanExtra(PHOTO_PREVIEW, false)
        val videoCaption = intent.getBooleanExtra(VIDEO_CAPTION, false)
        setContent { ItdTheme(darkTheme = true) {
            CameraScreen(videoEnabled, photoPreview, videoCaption, cameraExecutor, ::finishCapture, ::finish)
        } }
    }

    private fun finishCapture(uri: Uri, mode: String, caption: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_MODE, mode).putExtra(RESULT_URI, uri)
            .putExtra(RESULT_CAPTION, caption).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        finish()
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
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

@Composable
private fun CameraScreen(videoEnabled: Boolean, photoPreview: Boolean, videoCaption: Boolean, cameraExecutor: ExecutorService,
    onCaptured: (Uri, String, String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = ContextCompat.getMainExecutor(context)
    var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCameraPermission = it[Manifest.permission.CAMERA] == true
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissions.launch(if (videoEnabled) arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) else arrayOf(Manifest.permission.CAMERA))
    }
    var frontCamera by remember { mutableStateOf(false) }
    var videoMode by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var capturedMode by remember { mutableStateOf("photo") }
    var caption by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val recorder = remember { Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))).build() }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    DisposableEffect(hasCameraPermission, frontCamera, videoMode, lifecycleOwner) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        if (hasCameraPermission) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) try {
                    provider = future.get()
                    val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    camera = if (videoMode) provider!!.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
                        else provider!!.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                    error = null
                } catch (failure: Exception) { error = "Не удалось сделать фото" }
            }, mainExecutor)
        }
        onDispose {
            disposed = true
            provider?.unbind(preview, imageCapture, videoCapture)
        }
    }
    LaunchedEffect(flashMode, videoMode, camera) {
        imageCapture.flashMode = flashMode
        camera?.cameraControl?.enableTorch(videoMode && flashMode == ImageCapture.FLASH_MODE_ON)
    }
    DisposableEffect(Unit) { onDispose { recording?.close() } }
    fun accept(uri: Uri, mode: String) {
        if (mode == "photo" && photoPreview || mode == "video" && videoCaption) { capturedUri = uri; capturedMode = mode }
        else onCaptured(uri, mode, "")
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission && capturedUri == null) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        capturedUri?.let { uri ->
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                if (capturedMode == "photo") AsyncImage(uri, null, Modifier.fillMaxWidth().weight(1f))
                else com.itd.app.ui.components.MediaPlayer(uri.toString(), Modifier.fillMaxWidth().weight(1f))
                OutlinedTextField(caption, { caption = it }, modifier = Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Добавить подпись...") })
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { runCatching { context.contentResolver.delete(uri, null, null) }; capturedUri = null; caption = "" }) { Text("Удалить") }
                    Button(onClick = { onCaptured(uri, capturedMode, caption) }) { Text("Отправить") }
                }
            }
        }
        if (capturedUri == null) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), "Закрыть", tint = Color.White) }
                    IconButton(onClick = { flashMode = when (flashMode) { ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO; ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON; else -> ImageCapture.FLASH_MODE_OFF } }) {
                        Icon(painterResource(when (flashMode) { ImageCapture.FLASH_MODE_AUTO -> R.drawable.itd_ic_flash_auto; ImageCapture.FLASH_MODE_ON -> R.drawable.itd_ic_flash_on; else -> R.drawable.itd_ic_flash_off }), null, tint = Color.White)
                    }
                    IconButton(onClick = { frontCamera = !frontCamera }, enabled = recording == null) { Icon(painterResource(R.drawable.itd_ic_flip), "Переключить камеру", tint = Color.White) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (!hasCameraPermission) Text("Нужен доступ к камере", color = Color.White)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = { if (recording == null) videoMode = false }) { Text("ФОТО", color = if (!videoMode) MaterialTheme.colorScheme.primary else Color.White) }
                        if (videoEnabled) TextButton(onClick = { if (recording == null) videoMode = true }) { Text("ВИДЕО", color = if (videoMode) MaterialTheme.colorScheme.primary else Color.White) }
                    }
                    Box(Modifier.size(76.dp).border(4.dp, Color.White, CircleShape).padding(8.dp)
                        .background(if (videoMode) Color.Red else Color.White, CircleShape).clickable(enabled = hasCameraPermission && camera != null) {
                            if (videoMode) {
                                val active = recording
                                if (active != null) { active.stop(); recording = null }
                                else {
                                    val file = File(File(context.cacheDir, "camera_photos").apply { mkdirs() }, "ITD_${System.currentTimeMillis()}.mp4")
                                    val pending = recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        @Suppress("MissingPermission")
                                        pending.withAudioEnabled()
                                    }
                                    recording = pending.start(mainExecutor) { event ->
                                        if (event is VideoRecordEvent.Finalize) {
                                            recording = null
                                            if (event.hasError()) { file.delete(); error = "Не удалось сохранить видео" }
                                            else accept(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), "video")
                                        }
                                    }
                                }
                            } else {
                                val file = File(File(context.cacheDir, "camera_photos").apply { mkdirs() }, "ITD_${System.currentTimeMillis()}.jpg")
                                imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), cameraExecutor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            mainExecutor.execute { accept(uri, "photo") }
                                        }
                                        override fun onError(exception: ImageCaptureException) { mainExecutor.execute { error = exception.message } }
                                    })
                            }
                        })
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}
