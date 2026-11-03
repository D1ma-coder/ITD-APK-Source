package com.itd.app.ui.screens.camera

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

/** Camera work runs on its own thread; results come back on the main thread. */
class CameraExecutor {
    @Volatile
    var running = true
    val thread = HandlerThread("itd_camera_worker").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    fun post(block: () -> Unit) {
        if (!running) return
        worker.post { block() }
    }

    fun postMain(block: () -> Unit) {
        main.post { block() }
    }

    fun shutdown() {
        running = false
        thread.quitSafely()
    }
}
