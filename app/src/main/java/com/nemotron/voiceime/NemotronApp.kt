package com.nemotron.voiceime

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.guard.DndLockReceiver
import com.nemotron.voiceime.ui.AutoFreezeScheduler
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class NemotronApp : Application() {

    private val dndReceiver = DndLockReceiver()
    private var dndRegistered = false

    override fun onCreate() {
        super.onCreate()
        ShizukuProvider.enableMultiProcessSupport(true)
        instance = this

        if (com.nemotron.voiceime.guard.AddictionGuard.isServiceNeeded(this)) {
            com.nemotron.voiceime.guard.AddictionGuard.applyEnabled(this)
        }
        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        registerDndReceiver()
    }

    /**
     * El broadcast de No Molestar no llega de forma fiable por manifest en
     * Android 12+; hay que registrarlo dinámicamente mientras el proceso vive.
     */
    private fun registerDndReceiver() {
        if (dndRegistered) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(dndReceiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(dndReceiver, intentFilter)
            }
            dndRegistered = true
            Log.d("NemotronApp", "DndLockReceiver registrado dinámicamente")
        } catch (t: Throwable) {
            Log.w("NemotronApp", "No se pudo registrar DndLockReceiver", t)
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        Log.d("NemotronApp", "Shizuku binder received")
        registerDndReceiver()
        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (SecureStore.isAutoFreezeEnabled(this)) {
                AutoFreezeScheduler.start(this)
                AutoFreezeScheduler.recover(this)
            }
            if (com.nemotron.voiceime.guard.AddictionGuard.isServiceNeeded(this)) {
                com.nemotron.voiceime.guard.AddictionGuard.applyEnabled(this)
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d("NemotronApp", "Shizuku binder dead")
        // No se desregistra el receiver de auto-freeze: hace falta para seguir
        // recibiendo SCREEN_ON y que el loop de reintentos descongele las apps
        // en cuanto Shizuku vuelva (en One UI 8 Shizuku muere al bloquear pantalla).
    }

    companion object {
        lateinit var instance: NemotronApp
            private set
        private val intentFilter = IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
    }
}
