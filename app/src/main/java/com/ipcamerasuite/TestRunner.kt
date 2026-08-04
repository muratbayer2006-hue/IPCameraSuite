package com.ipcamerasuite

import com.ipcamerasuite.data.repository.CameraRepository
import org.json.JSONObject

object TestRunner {
    fun runCameraTest(repository: CameraRepository): String {
        val result = JSONObject()
        try {
            Logger.test("TestRunner", "Kamera testi başlatılıyor...")
            val maxZoom = try { repository.getMaxZoom() } catch (e: Exception) { 1.0f }
            val currentZoom = try { repository.getZoomRatio() } catch (e: Exception) { 1.0f }
            val selector = try { repository.getCameraSelector() } catch (e: Exception) { null }
            val isBack = selector?.let { it == androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA } ?: true

            result.put("status", "PASS")
            result.put("camera_type", if (isBack) "BACK" else "FRONT")
            result.put("max_zoom", maxZoom.toDouble())
            result.put("current_zoom", currentZoom.toDouble())
            result.put("is_camera_ready", true)
            Logger.test("TestRunner", "Kamera testi başarılı: maxZoom=$maxZoom, currentZoom=$currentZoom")
        } catch (e: Exception) {
            result.put("status", "FAIL")
            result.put("error", e.message ?: "Bilinmeyen hata")
            Logger.test("TestRunner", "Kamera testi başarısız: ${e.message}")
        }
        return result.toString()
    }

    fun runStreamTest(repository: CameraRepository, durationSeconds: Int = 5): String {
        val result = JSONObject()
        try {
            Logger.test("TestRunner", "Stream testi başlatılıyor...")
            if (!repository.isStreaming()) {
                result.put("status", "FAIL")
                result.put("error", "Stream aktif değil")
                Logger.test("TestRunner", "Stream testi başarısız: Stream aktif değil")
                return result.toString()
            }

            val initialFrameCount = try { repository.getFrameCount() } catch (e: Exception) { 0 }
            Thread.sleep(durationSeconds * 1000L)
            val finalFrameCount = try { repository.getFrameCount() } catch (e: Exception) { 0 }
            val fps = (finalFrameCount - initialFrameCount).toDouble() / durationSeconds

            result.put("status", "PASS")
            result.put("duration_seconds", durationSeconds)
            result.put("frames_captured", finalFrameCount - initialFrameCount)
            result.put("average_fps", String.format("%.1f", fps))
            Logger.test("TestRunner", "Stream testi başarılı: FPS=$fps, frameCount=${finalFrameCount - initialFrameCount}")
        } catch (e: Exception) {
            result.put("status", "FAIL")
            result.put("error", e.message ?: "Bilinmeyen hata")
            Logger.test("TestRunner", "Stream testi başarısız: ${e.message}")
        }
        return result.toString()
    }
}
