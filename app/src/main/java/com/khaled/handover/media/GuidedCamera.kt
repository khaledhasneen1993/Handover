package com.khaled.handover.media

import android.content.Context
import android.graphics.BitmapFactory
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executor

/** Lifecycle-bound CameraX adapter: UI never opens camera hardware itself. */
class GuidedCamera(private val context: Context) {
    private var capture: ImageCapture? = null
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private var generation = 0
    private val mainExecutor: Executor get() = ContextCompat.getMainExecutor(context)
    fun bind(owner: LifecycleOwner, previewView: PreviewView, onError: (Throwable)->Unit) {
        val request = ++generation
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (request != generation) return@addListener
            try {
                val p = future.get(); provider = p
                p.unbindAll()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            } catch (t: Throwable) { onError(t) }
        }, mainExecutor)
    }
    fun flash(enabled: Boolean) { capture?.flashMode = if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }
    fun take(target: File, onSuccess: (File)->Unit, onError: (Throwable)->Unit) {
        val c = capture ?: return onError(IllegalStateException("Camera is not ready"))
        c.takePicture(ImageCapture.OutputFileOptions.Builder(target).build(), mainExecutor, object: ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) = onSuccess(target)
            override fun onError(exception: androidx.camera.core.ImageCaptureException) = onError(exception)
        })
    }
    fun close() { generation++; provider?.unbindAll(); capture = null; camera = null }
}
