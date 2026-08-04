package com.ipcamerasuite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("CommandReceiver", "Broadcast alındı (manifest receiver)")
        if (intent == null || intent.action != "com.ipcamerasuite.COMMAND") {
            Log.d("CommandReceiver", "Intent null veya action eşleşmiyor")
            return
        }
        val command = intent.getStringExtra("command")
        Log.d("CommandReceiver", "Komut: $command")
        if (!command.isNullOrEmpty()) {
            CommandBus.sendCommand(command)
        }
    }
}
