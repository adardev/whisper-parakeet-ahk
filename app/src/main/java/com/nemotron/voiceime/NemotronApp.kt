package com.nemotron.voiceime

import android.app.Application
import android.util.Log
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.nfc.NfcAutoManager
import com.nemotron.voiceime.ui.AutoFreezeScheduler
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
            if (SecureStore.isAutoFreezeEnabled(this)) {
                AutoFreezeScheduler.start(this)
                AutoFreezeScheduler.recover(this)
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d("NemotronApp", "Shizuku binder dead, stopping NFC monitor")
        NfcAutoManager.stop()
        // No se desregistra el receiver de auto-freeze: hace falta para seguir
        // recibiendo SCREEN_ON y que el loop de reintentos descongele las apps
        // en cuanto Shizuku vuelva (en One UI 8 Shizuku muere al bloquear pantalla).
    }

    companion object {
        lateinit var instance: NemotronApp
            private set
    }
}
