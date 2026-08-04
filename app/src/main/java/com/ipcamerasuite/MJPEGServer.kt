package com.ipcamerasuite

import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MJPEGServer(private val port: Int) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var lastFrame: ByteArray? = null
    private val lock = Any()
    private var serverThread: Thread? = null

    fun broadcastFrame(jpeg: ByteArray) {
        synchronized(lock) {
            lastFrame = jpeg
        }
    }

    fun start() {
        if (isRunning.get()) return
        try {
            serverSocket = ServerSocket(port)
            isRunning.set(true)
            Logger.success("MJPEGServer", "Sunucu başlatıldı, port: $port")

            serverThread = thread {
                while (isRunning.get()) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        clientSocket?.let {
                            thread {
                                handleClient(it)
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Logger.error("MJPEGServer", "İstemci bağlantı hatası: ${e.message}")
                        }
                    }
                }
                Logger.info("MJPEGServer", "Ana döngü sonlandı")
            }
        } catch (e: Exception) {
            Logger.error("MJPEGServer", "Sunucu başlatılamadı: ${e.message}")
            isRunning.set(false)
        }
    }

    private fun handleClient(socket: java.net.Socket) {
        try {
            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            // HTTP isteğini oku (iyileştirilmiş)
            val buffer = ByteArray(4096)
            var totalRead = 0
            var found = false

            while (!found && totalRead < buffer.size) {
                val read = input.read(buffer, totalRead, buffer.size - totalRead)
                if (read < 0) break
                totalRead += read
                val request = String(buffer, 0, totalRead, Charsets.US_ASCII)
                if (request.contains("\r\n\r\n")) {
                    found = true
                }
            }

            // HTTP header
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: multipart/x-mixed-replace; boundary=--myboundary\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Expires: 0\r\n" +
                    "Connection: close\r\n\r\n"
            output.write(header.toByteArray())
            output.flush()

            // MJPEG akışı
            while (isRunning.get() && !socket.isClosed) {
                val frame: ByteArray? = synchronized(lock) {
                    lastFrame
                }
                if (frame != null) {
                    val chunk = "--myboundary\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${frame.size}\r\n\r\n"
                    output.write(chunk.toByteArray())
                    output.write(frame)
                    output.write("\r\n".toByteArray())
                    output.flush()
                    Thread.sleep(50) // istemcilere nefes aldırmak için
                } else {
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            // Bağlantı kopmuş, sessizce kapat
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun stop() {
        if (!isRunning.get()) return
        isRunning.set(false)
        try {
            serverSocket?.close()
            serverThread?.interrupt()
            serverSocket = null
            Logger.success("MJPEGServer", "Sunucu durduruldu")
        } catch (e: Exception) {
            Logger.error("MJPEGServer", "Sunucu durdurulamadı: ${e.message}")
        }
    }

    fun isRunning(): Boolean = isRunning.get()

    fun getPort(): Int = serverSocket?.localPort ?: port
}
