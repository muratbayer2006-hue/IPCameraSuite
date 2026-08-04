package com.ipcamerasuite.data.repository

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

interface CameraRepository {
    fun startCamera(
        cameraSelector: CameraSelector,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onSuccess: (maxZoom: Float) -> Unit,
        onError: (Throwable) -> Unit
    )
    fun setZoomRatio(zoom: Float)
    fun getZoomRatio(): Float
    fun getMaxZoom(): Float
    fun switchCamera(): CameraSelector
    fun releaseCamera()

    // ImageAnalysis
    fun enableImageAnalysis()
    fun disableImageAnalysis()
    fun isAnalysisEnabled(): Boolean

    // MJPEG Streaming
    fun startStream(port: Int, onError: (Throwable) -> Unit)
    fun stopStream()
    fun isStreaming(): Boolean
    fun getStreamUrl(): String?

    // Test için
    fun getFrameCount(): Int
    fun getCameraSelector(): CameraSelector
}
