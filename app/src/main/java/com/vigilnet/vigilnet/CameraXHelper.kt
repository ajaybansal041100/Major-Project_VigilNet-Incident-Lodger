package com.vigilnet.vigilnet

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object CameraXHelper {

    private var cameraExecutor: ExecutorService? = null

    fun getCameraProvider(context: Context, onReady: (ProcessCameraProvider) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            onReady(cameraProviderFuture.get())
        }, ContextCompat.getMainExecutor(context))
    }

    fun initExecutor() {
        if (cameraExecutor == null) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
    }

    fun shutdownExecutor() {
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }
}
