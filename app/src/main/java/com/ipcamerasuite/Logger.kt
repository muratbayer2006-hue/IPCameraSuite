package com.ipcamerasuite

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object Logger {
    enum class Level(val tag: String) {
        SYSTEM("SYS"), INFO("INF"), SUCCESS("SUC"), WARNING("WRN"),
        ERROR("ERR"), TEST("TST"), ASSERT("AST"), NETWORK("NET"),
        CAMERA("CAM"), VIDEO("VID"), STREAM("STR"), PERFORMANCE("PRF"),
        MEMORY("MEM"), SECURITY("SEC")
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logListeners = mutableListOf<(String) -> Unit>()
    private var logFile: File? = null
    private val lock = ReentrantLock()
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val logsDir = File(context.filesDir, "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            logFile = File(logsDir, "ipcamera_$timestamp.log")
            if (logFile!!.exists()) {
                logFile!!.delete()
            }
            logFile!!.createNewFile()
            isInitialized = true
            system("Logger", "Log dosyası oluşturuldu: ${logFile!!.absolutePath}")
        } catch (e: Exception) {
            Log.e("IPCameraSuite", "Logger başlatılamadı: ${e.message}")
        }
    }

    fun addListener(listener: (String) -> Unit) {
        logListeners.add(listener)
    }

    fun log(level: Level, module: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedMessage = "[$timestamp] [${level.tag}] [$module] $message"

        // Android Logcat
        Log.d("IPCameraSuite", formattedMessage)

        // UI listener'lar
        logListeners.forEach { it(formattedMessage) }

        // Dosyaya yaz
        writeToFile(formattedMessage)
    }

    private fun writeToFile(message: String) {
        if (!isInitialized || logFile == null) return
        lock.withLock {
            try {
                FileOutputStream(logFile, true).use { fos ->
                    fos.write((message + "\n").toByteArray())
                    fos.flush()
                }
            } catch (e: Exception) {
                Log.e("IPCameraSuite", "Dosyaya log yazılamadı: ${e.message}")
            }
        }
    }

    // Kullanım kolaylığı metodları
    fun system(module: String, msg: String) = log(Level.SYSTEM, module, msg)
    fun info(module: String, msg: String) = log(Level.INFO, module, msg)
    fun success(module: String, msg: String) = log(Level.SUCCESS, module, msg)
    fun warning(module: String, msg: String) = log(Level.WARNING, module, msg)
    fun error(module: String, msg: String) = log(Level.ERROR, module, msg)
    fun test(module: String, msg: String) = log(Level.TEST, module, msg)
    fun camera(module: String, msg: String) = log(Level.CAMERA, module, msg)
    fun network(module: String, msg: String) = log(Level.NETWORK, module, msg)

    fun getLogFile(): File? = logFile
}
