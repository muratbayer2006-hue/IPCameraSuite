package com.ipcamerasuite.presentation.viewmodel

import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import com.ipcamerasuite.Logger
import com.ipcamerasuite.data.repository.CameraRepository
import com.ipcamerasuite.data.repository.CameraRepositoryImpl
import com.ipcamerasuite.domain.model.CameraState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraViewModel : ViewModel() {
    private val repository: CameraRepository = CameraRepositoryImpl()

    private val _uiState = MutableStateFlow(CameraState())
    val uiState: StateFlow<CameraState> = _uiState.asStateFlow()

    fun onCameraReady(maxZoom: Float) {
        _uiState.value = _uiState.value.copy(
            isCameraReady = true,
            maxZoom = maxZoom
        )
        Logger.info("CameraViewModel", "Kamera hazır, max zoom: ${String.format("%.1f", maxZoom)}x")
    }

    fun onCameraError(error: Throwable) {
        _uiState.value = _uiState.value.copy(
            isCameraReady = false,
            errorMessage = error.message
        )
        Logger.error("CameraViewModel", "Kamera hatası: ${error.message}")
    }

    fun toggleCamera() {
        val newSelector = repository.switchCamera()
        val isBack = newSelector == CameraSelector.DEFAULT_BACK_CAMERA
        _uiState.value = _uiState.value.copy(
            isBackCamera = isBack,
            zoomRatio = 1.0f
        )
        Logger.camera("CameraViewModel", "${if (isBack) "Arka" else "Ön"} kameraya geçildi")
    }

    fun setZoom(zoom: Float) {
        val newZoom = zoom.coerceIn(1.0f, _uiState.value.maxZoom)
        _uiState.value = _uiState.value.copy(zoomRatio = newZoom)
        repository.setZoomRatio(newZoom)
        Logger.info("CameraViewModel", "Zoom: ${String.format("%.1f", newZoom)}x")
    }

    fun getCameraSelector(): CameraSelector {
        return if (_uiState.value.isBackCamera) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }

    fun getRepository(): CameraRepository = repository

    fun enableImageAnalysis() {
        repository.enableImageAnalysis()
        _uiState.value = _uiState.value.copy(isAnalysisEnabled = true)
    }

    fun disableImageAnalysis() {
        repository.disableImageAnalysis()
        _uiState.value = _uiState.value.copy(isAnalysisEnabled = false)
    }

    fun isAnalysisEnabled(): Boolean = repository.isAnalysisEnabled()

    fun toggleAnalysis() {
        if (isAnalysisEnabled()) {
            disableImageAnalysis()
        } else {
            enableImageAnalysis()
        }
    }

    fun toggleStream(port: Int = 8080) {
        if (repository.isStreaming()) {
            repository.stopStream()
            _uiState.value = _uiState.value.copy(
                isStreaming = false,
                streamUrl = null
            )
            Logger.info("CameraViewModel", "Stream durduruldu")
        } else {
            repository.startStream(port) { error ->
                Logger.error("CameraViewModel", "Stream başlatılamadı: ${error.message}")
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message
                )
            }
            val url = repository.getStreamUrl()
            _uiState.value = _uiState.value.copy(
                isStreaming = true,
                streamUrl = url
            )
            if (url != null) {
                Logger.success("CameraViewModel", "Stream başlatıldı: $url")
            }
        }
    }

    fun getStreamUrl(): String? = repository.getStreamUrl()
    fun isStreaming(): Boolean = repository.isStreaming()

    override fun onCleared() {
        super.onCleared()
        repository.releaseCamera()
    }
}
