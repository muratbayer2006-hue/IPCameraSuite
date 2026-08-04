package com.ipcamerasuite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CommandBus {
    private val _command = MutableStateFlow<String?>(null)
    val command: StateFlow<String?> = _command.asStateFlow()

    fun sendCommand(cmd: String) {
        Logger.info("CommandBus", "🚀 [8] Komut alındı: $cmd")
        _command.value = cmd
    }

    fun consumeCommand(): String? {
        val cmd = _command.value
        _command.value = null
        if (cmd != null) {
            Logger.info("CommandBus", "🔄 [9] Komut tüketildi: $cmd")
        }
        return cmd
    }
}
