package com.ipcamerasuite

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress

class CommandWebSocketServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    private val connections = mutableSetOf<WebSocket>()

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        connections.add(conn)
        Logger.info("WebSocketServer", "🔵 Yeni bağlantı: ${conn.remoteSocketAddress}")
        broadcastStatus("connected")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        connections.remove(conn)
        Logger.info("WebSocketServer", "🔴 Bağlantı kapandı: ${conn.remoteSocketAddress}, reason: $reason")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        Logger.info("WebSocketServer", "📩 [1] Mesaj alındı: $message")
        try {
            val json = JSONObject(message)
            val command = json.getString("command")
            Logger.info("WebSocketServer", "📩 [2] Komut çözüldü: $command")
            CommandBus.sendCommand(command)
            Logger.info("WebSocketServer", "📩 [3] CommandBus'a gönderildi")
        } catch (e: Exception) {
            Logger.error("WebSocketServer", "❌ [4] Mesaj işlenemedi: ${e.message}")
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Logger.error("WebSocketServer", "❌ Hata: ${ex.message}")
    }

    override fun onStart() {
        Logger.success("WebSocketServer", "✅ Sunucu başlatıldı, port: ${getPort()}")
    }

    fun broadcastStatus(event: String, data: Map<String, Any> = emptyMap()) {
        try {
            val json = JSONObject()
            json.put("event", event)
            data.forEach { (key, value) ->
                json.put(key, value)
            }
            val message = json.toString()
            val toRemove = mutableListOf<WebSocket>()
            connections.forEach { conn ->
                try {
                    conn.send(message)
                    Logger.info("WebSocketServer", "📤 [5] Mesaj yayınlandı: $event")
                } catch (e: Exception) {
                    Logger.warning("WebSocketServer", "⚠️ [6] Mesaj gönderilemedi: ${e.message}")
                    toRemove.add(conn)
                }
            }
            connections.removeAll(toRemove)
        } catch (e: Exception) {
            Logger.error("WebSocketServer", "❌ [7] broadcastStatus hatası: ${e.message}")
        }
    }

    override fun getPort(): Int = getAddress().port
}
