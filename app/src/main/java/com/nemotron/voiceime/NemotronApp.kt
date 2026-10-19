package com.nemotron.voiceime

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
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.app.NotificationCompat
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.dhizuku.AutoAndroidAuto
import com.nemotron.voiceime.dhizuku.CarDetector
import com.nemotron.voiceime.dhizuku.NightAirplaneScreenReceiver
import com.nemotron.voiceime.dhizuku.ShizukuManager
import com.nemotron.voiceime.guard.DndLockReceiver
import com.nemotron.voiceime.ui.AutoFreezeScheduler
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

class NemotronApp : com.samsung.android.honeyboard.app.HoneyBoardApplication() {

    private val dndReceiver = DndLockReceiver()
    private var dndRegistered = false
    private val nightAirplaneScreenReceiver = NightAirplaneScreenReceiver()
    private var nightAirplaneScreenReceiverRegistered = false
    private val shizukuFeaturesInitialized = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        ShizukuProvider.enableMultiProcessSupport(true)
        instance = this
        com.nemotron.voiceime.dhizuku.AirplaneModeSchedule.scheduleNext(this)

        com.nemotron.voiceime.guard.AddictionGuard.applyEnabled(this)
        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        registerDndReceiver()
        registerNightAirplaneScreenReceiver()
        CarDetector.refresh(this)
        com.nemotron.voiceime.guard.DndKeepAliveService.update(this)
        com.nemotron.voiceime.dhizuku.ConnectionExclusionManager.start(this)
        // Si Shizuku ya estaba corriendo al arrancar la app, el binderListener
        // no se dispara. Comprobar pasado un momento para no perder la
        // inicialización (auto-freeze, guard, detección de coche).
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // BinderReceived normally performs this setup. Keep the delayed
            // fallback for Shizuku versions that are already bound at startup,
            // but let an atomic gate ensure it cannot repeat the same work.
            initializeShizukuFeatures()
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

    private fun registerNightAirplaneScreenReceiver() {
        if (nightAirplaneScreenReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(nightAirplaneScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(nightAirplaneScreenReceiver, filter)
            }
            nightAirplaneScreenReceiverRegistered = true
            Log.d("NemotronApp", "screen-off receiver for night airplane mode registered")
        } catch (t: Throwable) {
            Log.w("NemotronApp", "could not register night airplane screen-off receiver", t)
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        Log.d("NemotronApp", "Shizuku binder received")
        registerDndReceiver()
        shizukuDeadNotified = false
        initializeShizukuFeatures()
    }

    private fun initializeShizukuFeatures() {
        if (!ShizukuManager.hasPermission()) return
        if (!shizukuFeaturesInitialized.compareAndSet(false, true)) return
        try {
            com.nemotron.voiceime.dhizuku.AirplaneModeSchedule.scheduleNext(this)
            NightAirplaneScreenReceiver().onShizukuAvailable(this)
            if (SecureStore.isAutoFreezeEnabled(this)) {
                AutoFreezeScheduler.start(this)
                AutoFreezeScheduler.recover(this)
            }
            com.nemotron.voiceime.guard.AddictionGuard.applyEnabled(this)
            CarDetector.refresh(this)
            AutoAndroidAuto.reconcile(this)
        } catch (t: Throwable) {
            shizukuFeaturesInitialized.set(false)
            Log.e("NemotronApp", "Shizuku feature initialization failed", t)
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d("NemotronApp", "Shizuku binder dead")
        // Descartar el shell persistente: sus procesos hijos mueren con Shizuku
        // (p.ej. al conectar al coche / bloquear pantalla en One UI 8). Sin esto,
        // execShellCapture reutilizaría un proceso muerto y bloquearía hasta el
        // timeout, congelando el main thread de la app.
        ShizukuManager.onBinderDead()
        // A new binder connection is a new initialization cycle. The listener
        // and delayed fallback are then allowed to race safely again.
        shizukuFeaturesInitialized.set(false)
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
