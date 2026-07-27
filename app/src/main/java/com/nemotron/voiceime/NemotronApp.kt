package com.nemotron.voiceime

import android.app.Application
import rikka.shizuku.ShizukuProvider

class NemotronApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuProvider.enableMultiProcessSupport(true)
        instance = this
    }

    companion object {
        lateinit var instance: NemotronApp
            private set
    }
}
