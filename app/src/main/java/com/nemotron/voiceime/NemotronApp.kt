package com.nemotron.voiceime

import android.app.Application

class NemotronApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: NemotronApp
            private set
    }
}
