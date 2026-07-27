package com.nemotron.voiceime

import android.app.Application
import android.util.Log
import com.nemotron.voiceime.nfc.NfcAutoManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class NemotronApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuProvider.enableMultiProcessSupport(true)
        instance = this

        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        Log.d("NemotronApp", "Shizuku binder received")
        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NfcAutoManager.start()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d("NemotronApp", "Shizuku binder dead, stopping NFC monitor")
        NfcAutoManager.stop()
    }

    companion object {
        lateinit var instance: NemotronApp
            private set
    }
}
