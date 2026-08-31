package com.nemotron.voiceime

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.dhizuku.AutoAndroidAuto
import com.nemotron.voiceime.dhizuku.CarConnectionReceiver
import com.nemotron.voiceime.dhizuku.CarDetector
import com.nemotron.voiceime.dhizuku.ShizukuManager
import com.nemotron.voiceime.guard.DndLockReceiver
import com.nemotron.voiceime.ui.AutoFreezeScheduler
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class NemotronApp : Application() {

    private val dndReceiver = DndLockReceiver()
    private var dndRegistered = false
    private val carReceiver = CarConnectionReceiver()
    private var carReceiverRegistered = false

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
        registerCarReceiver()
        CarDetector.refresh(this)
        com.nemotron.voiceime.guard.DndKeepAliveService.update(this)
        registerConnectionExclusion()
        // Si Shizuku ya estaba corriendo al arrancar la app, el binderListener
        // no se dispara. Comprobar pasado un momento para no perder la
        // inicialización (auto-freeze, guard, detección de coche).
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (ShizukuManager.hasPermission()) {
                if (SecureStore.isAutoFreezeEnabled(this)) {
                    AutoFreezeScheduler.start(this)
                    AutoFreezeScheduler.recover(this)
                }
                if (com.nemotron.voiceime.guard.AddictionGuard.isServiceNeeded(this)) {
                    com.nemotron.voiceime.guard.AddictionGuard.applyEnabled(this)
                }
                CarDetector.refresh(this)
                AutoAndroidAuto.reconcile(this)
            }
        }, 2500L)
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

    /**
     * Registra el broadcast de conexión del coche (accesorio USB Android Open
     * Accessory) mientras el proceso vive. También está declarado en el manifest
     * para que funcione aunque el proceso esté muerto. No se usa Bluetooth A2DP
     * como señal de "coche": los auriculares lo dispararían en falso.
     */
    private fun registerCarReceiver() {
        if (carReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(android.hardware.usb.UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
                addAction(android.hardware.usb.UsbManager.ACTION_USB_ACCESSORY_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(carReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(carReceiver, filter)
            }
            carReceiverRegistered = true
            Log.d("NemotronApp", "CarConnectionReceiver registrado")
        } catch (t: Throwable) {
            Log.w("NemotronApp", "No se pudo registrar CarConnectionReceiver", t)
        }
    }

    private var connExclusionReceiver: BroadcastReceiver? = null

    /** Registra el receiver de exclusión WiFi/Datos móviles (una sola conexión). */
    private fun registerConnectionExclusion() {
        if (connExclusionReceiver != null) return
        try {
            val filter = IntentFilter().apply {
                addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
                addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
            }
            val r = com.nemotron.voiceime.dhizuku.ConnectionExclusionReceiver()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(r, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(r, filter)
            }
            connExclusionReceiver = r
            Log.d("NemotronApp", "ConnectionExclusionReceiver registrado")
        } catch (t: Throwable) {
            Log.w("NemotronApp", "No se pudo registrar ConnectionExclusionReceiver", t)
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        Log.d("NemotronApp", "Shizuku binder received")
        registerDndReceiver()
        shizukuDeadNotified = false
        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (SecureStore.isAutoFreezeEnabled(this)) {
                AutoFreezeScheduler.start(this)
                AutoFreezeScheduler.recover(this)
            }
            if (com.nemotron.voiceime.guard.AddictionGuard.isServiceNeeded(this)) {
                com.nemotron.voiceime.guard.AddictionGuard.applyEnabled(this)
            }
            CarDetector.refresh(this)
            AutoAndroidAuto.reconcile(this)
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d("NemotronApp", "Shizuku binder dead")
        // Descartar el shell persistente: sus procesos hijos mueren con Shizuku
        // (p.ej. al conectar al coche / bloquear pantalla en One UI 8). Sin esto,
        // execShellCapture reutilizaría un proceso muerto y bloquearía hasta el
        // timeout, congelando el main thread de la app.
        ShizukuManager.onBinderDead()
        // No se desregistra el receiver de auto-freeze: hace falta para seguir
        // recibiendo SCREEN_ON y que el loop de reintentos descongele las apps
        // en cuanto Shizuku vuelva (en One UI 8 Shizuku muere al bloquear pantalla).

        // Watchdog: solo actúa si el tile de Android Auto está encendido.
        if (SecureStore.isAndroidAutoTileOn(this)) {
            // Intentar reiniciar Shizuku abriendo la app del fork (que tiene
            // auto-start). Si está en background el am start puede fallar; la
            // notificación es el respaldo.
            try {
                Runtime.getRuntime().exec(arrayOf(
                    "am", "start", "-n",
                    "moe.shizuku.privileged.api/moe.shizuku.manager.MainActivity",
                    "--activity-clear-task"
                ))
                Log.d("NemotronApp", "Watchdog: intentando reiniciar Shizuku via am start")
            } catch (t: Throwable) {
                Log.w("NemotronApp", "am start falló", t)
            }
            // Notificación de respaldo (si am start no funciona por restricciones de background)
            notifyShizukuDead()
        }
    }

    private var shizukuDeadNotified = false

    private fun notifyShizukuDead() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (nm.getNotificationChannel(WATCHDOG_CHANNEL) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(WATCHDOG_CHANNEL, "Watchdog Shizuku", NotificationManager.IMPORTANCE_HIGH)
                    )
                }
            }
            val openShizuku = PendingIntent.getActivity(
                this, 0,
                packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thedjchi/Shizuku")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif: Notification = NotificationCompat.Builder(this, WATCHDOG_CHANNEL)
                .setSmallIcon(com.nemotron.voiceime.R.drawable.ic_auto_tile)
                .setContentTitle("Shizuku se detuvo")
                .setContentText("Android Auto está activo pero Shizuku murió (cambio de USB). Toca para reiniciarlo.")
                .setContentIntent(openShizuku)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            if (!shizukuDeadNotified) {
                nm.notify(WATCHDOG_NOTIF_ID, notif)
                shizukuDeadNotified = true
            }
        } catch (t: Throwable) {
            Log.w("NemotronApp", "No se pudo notificar muerte de Shizuku", t)
        }
    }

    companion object {
        lateinit var instance: NemotronApp
            private set
        private val intentFilter = IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        private const val WATCHDOG_CHANNEL = "nemotron_shizuku_watchdog"
        private const val WATCHDOG_NOTIF_ID = 9022
    }
}
