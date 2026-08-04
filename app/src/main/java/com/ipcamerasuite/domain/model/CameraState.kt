package com.ipcamerasuite.domain.model

data class CameraState(
    val isBackCamera: Boolean = true,
    val zoomRatio: Float = 1.0f,
    val maxZoom: Float = 1.0f,
    val isCameraReady: Boolean = false,
    val errorMessage: String? = null,
    val streamUrl: String? = null,
    val isStreaming: Boolean = false,
    val isAnalysisEnabled: Boolean = false // WebSocket için eklendi
)
