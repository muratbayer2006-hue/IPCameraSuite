package com.ipcamerasuite

import android.app.Application
import android.os.Environment

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Logger'ı application context ile başlat
        Logger.init(applicationContext)
    }
}
