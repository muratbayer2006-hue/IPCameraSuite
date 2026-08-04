package com.ipcamerasuite.data.repository

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ipcamerasuite.Logger
import com.ipcamerasuite.MJPEGServer
import java.io.ByteArrayOutputStream
import java.net.NetworkInterface
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CameraRepositoryImpl : CameraRepository {
    private var camera: Camera? = null
    private var currentCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var maxZoom = 1.0f
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val isAnalysisEnabled = AtomicBoolean(false)
    private var frameCount = 0
    private var cameraProvider: ProcessCameraProvider? = null

    private var mjpegServer: MJPEGServer? = null
    private val isStreaming = AtomicBoolean(false)

    override fun startCamera(
        cameraSelector: CameraSelector,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onSuccess: (maxZoom: Float) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView
        currentCameraSelector = cameraSelector

        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                provider.unbindAll()
                camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)

                val cameraInfo = camera!!.cameraInfo
                maxZoom = cameraInfo.zoomState.value?.maxZoomRatio ?: 1.0f
                onSuccess(maxZoom)
                Logger.camera("CameraRepository", "Kamera başlatıldı, max zoom: ${String.format("%.1f", maxZoom)}x")
            } catch (e: Exception) {
                onError(e)
                Logger.error("CameraRepository", "Kamera başlatılamadı: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    override fun setZoomRatio(zoom: Float) {
        camera?.cameraControl?.setZoomRatio(zoom)
        Logger.info("CameraRepository", "Zoom ayarlandı: ${String.format("%.1f", zoom)}x")
    }

    override fun getZoomRatio(): Float {
        return camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1.0f
    }

    override fun getMaxZoom(): Float {
        return maxZoom
    }

    override fun switchCamera(): CameraSelector {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        return currentCameraSelector
    }

    override fun releaseCamera() {
        stopStream()
        disableImageAnalysis()
        camera = null
        cameraExecutor.shutdown()
        Logger.system("CameraRepository", "Kamera serbest bırakıldı")
    }

    override fun enableImageAnalysis() {
        if (isAnalysisEnabled.get()) {
            Logger.warning("CameraRepository", "Analiz zaten aktif")
            return
        }

        val view = previewView
        val owner = lifecycleOwner
        if (view == null || owner == null) {
            Logger.error("CameraRepository", "PreviewView veya LifecycleOwner başlatılmamış")
            return
        }

        try {
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy)
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(view.context)
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    provider.bindToLifecycle(owner, currentCameraSelector, imageAnalysis)
                    isAnalysisEnabled.set(true)
                    Logger.success("ImageAnalysis", "Analiz başlatıldı")
                } catch (e: Exception) {
                    Logger.error("ImageAnalysis", "Analiz başlatılamadı: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(view.context))

        } catch (e: Exception) {
            Logger.error("CameraRepository", "Analiz başlatılamadı: ${e.message}")
        }
    }

    override fun disableImageAnalysis() {
        if (!isAnalysisEnabled.get()) return
        try {
            cameraProvider?.unbind(imageAnalysis)
            imageAnalysis = null
            isAnalysisEnabled.set(false)
            Logger.success("ImageAnalysis", "Analiz durduruldu")
        } catch (e: Exception) {
            Logger.error("ImageAnalysis", "Analiz durdurulamadı: ${e.message}")
        }
    }

    override fun isAnalysisEnabled(): Boolean = isAnalysisEnabled.get()

    override fun startStream(port: Int, onError: (Throwable) -> Unit) {
        if (isStreaming.get()) {
            Logger.warning("CameraRepository", "Zaten yayın aktif")
            return
        }

        try {
            Logger.info("CameraRepository", "MJPEG sunucusu başlatılıyor, port: $port")
            mjpegServer = MJPEGServer(port)
            mjpegServer?.start()
            isStreaming.set(true)
            Logger.success("CameraRepository", "MJPEG sunucusu başlatıldı, port: $port")

            if (!isAnalysisEnabled.get()) {
                enableImageAnalysis()
            }
        } catch (e: Exception) {
            onError(e)
            Logger.error("CameraRepository", "Stream başlatılamadı: ${e.message}")
        }
    }

    override fun stopStream() {
        if (!isStreaming.get()) return
        try {
            mjpegServer?.stop()
            mjpegServer = null
            isStreaming.set(false)
            Logger.success("CameraRepository", "Stream durduruldu")
        } catch (e: Exception) {
            Logger.error("CameraRepository", "Stream durdurulamadı: ${e.message}")
        }
    }

    override fun isStreaming(): Boolean = isStreaming.get()

    override fun getStreamUrl(): String? {
        if (!isStreaming.get() || mjpegServer == null) return null
        val ip = getLocalIpAddress()
        return if (ip != null) {
            "http://$ip:${mjpegServer?.getPort()}/stream.mjpg"
        } else {
            null
        }
    }

    override fun getFrameCount(): Int = frameCount

    override fun getCameraSelector(): CameraSelector = currentCameraSelector

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == false) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("CameraRepository", "IP adresi alınamadı: ${e.message}")
        }
        return null
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            // Orijinal bitmap'i oluştur
            val bitmap = imageProxyToBitmap(imageProxy)

            if (bitmap != null) {
                // Dönüşüm bilgisi
                val rotation = imageProxy.imageInfo.rotationDegrees
                var rotatedBitmap = bitmap
                if (rotation != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rotation.toFloat())
                    rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    // Orijinal bitmap'i geri dönüştür
                    if (rotatedBitmap != bitmap) {
                        bitmap.recycle()
                    }
                }

                val jpegBytes = bitmapToJpeg(rotatedBitmap, quality = 80)
                val sizeKB = jpegBytes.size / 1024
                frameCount++
                if (frameCount % 10 == 0) {
                    Logger.info("ImageAnalysis", "JPEG dönüştürüldü: ${frameCount}.kare, boyut: ${sizeKB}KB, rotation: ${rotation}°")
                }
                if (isStreaming.get() && mjpegServer != null) {
                    mjpegServer?.broadcastFrame(jpegBytes)
                }
                // Döndürülmüş bitmap'i geri dönüştür
                if (rotatedBitmap != bitmap) {
                    rotatedBitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Logger.error("ImageAnalysis", "Görüntü işlenemedi: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}
