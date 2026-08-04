package com.ipcamerasuite

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import com.ipcamerasuite.presentation.viewmodel.CameraViewModel
import com.ipcamerasuite.ui.theme.IPCameraSuiteTheme
import java.net.NetworkInterface
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val logMessages = mutableStateListOf<String>()
    private var webSocketServer: CommandWebSocketServer? = null
    private val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logger.addListener { message ->
            runOnUiThread {
                logMessages.add(message)
                if (logMessages.size > 200) {
                    logMessages.removeAt(0)
                }
            }
        }

        setContent {
            IPCameraSuiteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: CameraViewModel = viewModel()
                    val uiState by viewModel.uiState.collectAsState()
                    var hasCameraPermission by remember { mutableStateOf(false) }
                    var isWebSocketRunning by remember { mutableStateOf(false) }
                    var serverPort by remember { mutableStateOf(8081) }

                    // Log alanı yükseklik oranı (%5 ile %60 arası)
                    var logHeightFraction by remember { mutableStateOf(0.15f) }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { granted ->
                            hasCameraPermission = granted
                            if (granted) {
                                Logger.success("MainActivity", "Kamera izni verildi")
                            } else {
                                Logger.warning("MainActivity", "Kamera izni reddedildi")
                            }
                        }
                    )

                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            hasCameraPermission = true
                            Logger.info("MainActivity", "Kamera izni zaten verilmiş")
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }

                    // Komut dinleyici
                    LaunchedEffect(Unit) {
                        CommandBus.command.collect { cmd ->
                            if (cmd != null) {
                                when (cmd) {
                                    "start_stream" -> viewModel.toggleStream()
                                    "stop_stream" -> if (viewModel.isStreaming()) viewModel.toggleStream()
                                    "switch_camera" -> viewModel.toggleCamera()
                                    "zoom_in" -> {
                                        val current = uiState.zoomRatio
                                        if (current < uiState.maxZoom) viewModel.setZoom(current + 0.1f)
                                    }
                                    "zoom_out" -> {
                                        val current = uiState.zoomRatio
                                        if (current > 1.0f) viewModel.setZoom(current - 0.1f)
                                    }
                                    "toggle_analysis" -> viewModel.toggleAnalysis()
                                    "test_camera" -> {
                                        testScope.launch {
                                            try {
                                                val result = TestRunner.runCameraTest(viewModel.getRepository())
                                                withContext(Dispatchers.Main) {
                                                    webSocketServer?.broadcastStatus("test_result", mapOf("test" to "camera", "result" to result))
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Logger.error("MainActivity", "Kamera testi hatası: ${e.message}")
                                                    webSocketServer?.broadcastStatus("test_result", mapOf("test" to "camera", "result" to """{"status":"FAIL","error":"${e.message}"}"""))
                                                }
                                            }
                                        }
                                    }
                                    "test_stream" -> {
                                        testScope.launch {
                                            try {
                                                val result = TestRunner.runStreamTest(viewModel.getRepository(), 3)
                                                withContext(Dispatchers.Main) {
                                                    webSocketServer?.broadcastStatus("test_result", mapOf("test" to "stream", "result" to result))
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Logger.error("MainActivity", "Stream testi hatası: ${e.message}")
                                                    webSocketServer?.broadcastStatus("test_result", mapOf("test" to "stream", "result" to """{"status":"FAIL","error":"${e.message}"}"""))
                                                }
                                            }
                                        }
                                    }
                                    else -> Logger.warning("MainActivity", "Bilinmeyen komut: $cmd")
                                }
                                CommandBus.consumeCommand()
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Kamera önizleme alanı (kalan alan - log alanı)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f - logHeightFraction)
                        ) {
                            if (hasCameraPermission) {
                                key(uiState.isBackCamera) {
                                    CameraPreview(
                                        modifier = Modifier.fillMaxSize(),
                                        viewModel = viewModel,
                                        cameraSelector = viewModel.getCameraSelector()
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Kamera izni bekleniyor...",
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                            }

                            // Yayın bağlantı URL'si (ekranın en üstünde)
                            if (uiState.isStreaming && uiState.streamUrl != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "📡 Bağlanmak için: ${uiState.streamUrl}",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            // Kamera Değiştirme Butonu (sağ alt)
                            FloatingActionButton(
                                onClick = { viewModel.toggleCamera() },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            ) {
                                Text(if (uiState.isBackCamera) "📷 ÖN" else "📷 ARKA")
                            }

                            // Zoom Butonları (sol alt)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FloatingActionButton(
                                    onClick = {
                                        if (uiState.zoomRatio < uiState.maxZoom)
                                            viewModel.setZoom(uiState.zoomRatio + 0.1f)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) { Text("+") }
                                FloatingActionButton(
                                    onClick = {
                                        if (uiState.zoomRatio > 1.0f)
                                            viewModel.setZoom(uiState.zoomRatio - 0.1f)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) { Text("-") }
                            }
                        }

                        // 3 Buton (yan yana, log alanının hemen üzerinde)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // WebSocket Butonu
                            Button(
                                onClick = {
                                    Logger.info("MainActivity", "WebSocket butonuna tıklandı")
                                    if (isWebSocketRunning) {
                                        webSocketServer?.stop()
                                        webSocketServer = null
                                        isWebSocketRunning = false
                                        Logger.info("MainActivity", "WebSocket sunucusu durduruldu")
                                    } else {
                                        startWebSocketServer(serverPort)
                                        isWebSocketRunning = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isWebSocketRunning)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    if (isWebSocketRunning) "WS DURDUR" else "WS BAŞLAT",
                                    fontSize = 12.sp
                                )
                            }

                            // Yayın Butonu
                            Button(
                                onClick = { viewModel.toggleStream() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.isStreaming)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    if (uiState.isStreaming) "YAYIN DURDUR" else "YAYIN BAŞLAT",
                                    fontSize = 12.sp
                                )
                            }

                            // Analiz Butonu
                            Button(
                                onClick = { viewModel.toggleAnalysis() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.isAnalysisEnabled)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    if (uiState.isAnalysisEnabled) "ANALİZ DURDUR" else "ANALİZ BAŞLAT",
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // WebSocket IP Bilgisi (küçük metin, butonların altında)
                        if (isWebSocketRunning) {
                            Text(
                                text = "WS: ${getLocalIpAddress()}:$serverPort",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }

                        // Sürüklenebilir Ayırıcı (tutamaç)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .background(Color.Transparent)
                                .pointerInput(Unit) {
                                    var previousY = 0f
                                    var totalHeight = 0f
                                    detectVerticalDragGestures(
                                        onDragStart = { offset ->
                                            previousY = offset.y
                                            totalHeight = this.size.height.toFloat()
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            if (totalHeight > 0) {
                                                val deltaFraction = -dragAmount / totalHeight
                                                logHeightFraction = (logHeightFraction + deltaFraction).coerceIn(0.05f, 0.6f)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Tutamaç çizgisi (görsel ipucu)
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .background(
                                        Color.Gray.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }

                        // Canlı Log alanı (sürüklenerek büyütülüp küçültülebilir)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(logHeightFraction)
                                .background(Color.Black.copy(alpha = 0.05f))
                        ) {
                            Text(
                                "Canlı Log",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(8.dp)
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                reverseLayout = true
                            ) {
                                items(logMessages.reversed()) { log ->
                                    Text(
                                        text = log,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Logger.system("MainActivity", "Uygulama başlatıldı")
        Logger.info("MainActivity", "Logger sistemi aktif")
    }

    private fun startWebSocketServer(port: Int) {
        try {
            webSocketServer = CommandWebSocketServer(port)
            webSocketServer?.start()
            Logger.success("MainActivity", "WebSocket sunucusu başlatıldı: ${getLocalIpAddress()}:$port")
        } catch (e: Exception) {
            Logger.error("MainActivity", "WebSocket sunucusu başlatılamadı: ${e.message}")
        }
    }

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
            Logger.error("MainActivity", "IP adresi alınamadı: ${e.message}")
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketServer?.stop()
        webSocketServer = null
        testScope.cancel()
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel,
    cameraSelector: CameraSelector
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.getRepository().releaseCamera()
            Logger.system("CameraPreview", "Kamera serbest bırakıldı")
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { view -> previewView = view }
    )

    LaunchedEffect(cameraSelector) {
        val view = previewView
        if (view != null) {
            val repository = viewModel.getRepository()
            repository.startCamera(
                cameraSelector = cameraSelector,
                lifecycleOwner = lifecycleOwner,
                previewView = view,
                onSuccess = { maxZoom -> viewModel.onCameraReady(maxZoom) },
                onError = { error -> viewModel.onCameraError(error) }
            )
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.zoomRatio) {
        if (previewView != null) {
            viewModel.getRepository().setZoomRatio(uiState.zoomRatio)
        }
    }
}
