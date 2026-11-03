package com.itd.app.ui.screens.qr

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors

/**
 * Full-screen back camera that reports the QR code it sees and where it is on screen
 * (null when it lost it). Frames are ignored while [paused].
 */
@Composable
fun QrScanner(onCode: (String) -> Unit, onRect: (Rect?) -> Unit, paused: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val code by rememberUpdatedState(onCode)
    val rect by rememberUpdatedState(onRect)
    val frozen by rememberUpdatedState(paused)
    DisposableEffect(context, previewView, owner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        val controller = LifecycleCameraController(context)
        controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, ContextCompat.getMainExecutor(context)) { result ->
                if (frozen) return@MlKitAnalyzer
                val qr = result.getValue(scanner)?.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                if (qr == null) {
                    rect(null)
                    return@MlKitAnalyzer
                }
                qr.boundingBox?.let { box ->
                    rect(Rect(Offset(box.left.toFloat(), box.top.toFloat()), Size(box.width().toFloat(), box.height().toFloat())))
                }
                qr.rawValue?.let(code)
            },
        )
        previewView.controller = controller
        controller.bindToLifecycle(owner)
        onDispose {
            controller.unbind()
            previewView.controller = null
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView({ previewView }, modifier)
}
