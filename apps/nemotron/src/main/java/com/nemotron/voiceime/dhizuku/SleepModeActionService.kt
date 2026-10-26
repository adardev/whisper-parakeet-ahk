package com.nemotron.voiceime.dhizuku

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.nemotron.voiceime.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Exists only while applying an explicit tap or the daily 23:00 alarm. */
class SleepModeActionService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Cambios de Sleep Mode", NotificationManager.IMPORTANCE_LOW
        ))
        startForeground(9024, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_sleep_mode_tile)
            .setContentTitle("Sleep Mode")
            .setContentText("Aplicando ajustes de sueño…")
            .setOngoing(true).build())
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nemotron:SleepAction")
                .apply { acquire(60_000L) }
        }
        val scheduled = intent.getBooleanExtra(SCHEDULED, false)
        val enabled = intent.getBooleanExtra(ENABLED, true)
        val retry = intent.getBooleanExtra(AirplaneModeSchedule.EXTRA_RETRY, false)
        applying.set(true)
        requestedEnabled = enabled
        worker.execute {
            try {
                // The Shizuku provider binds asynchronously on a cold start.
                // Wait only for this action; no watchdog or background loop.
                val deadline = SystemClock.elapsedRealtime() + 5_000L
                while (!ShizukuManager.hasPermission() && SystemClock.elapsedRealtime() < deadline) {
                    Thread.sleep(100)
                }
                // Carry the target chosen at the tap, rather than evaluating
                // another toggle later when the worker eventually starts.
                val result = if (scheduled) {
                    SleepModeController.setScheduledEnabled(this, enabled)
                } else {
                    SleepModeController.setEnabled(this, enabled)
                }
                Log.i(TAG, "scheduled=$scheduled retry=$retry requested=$enabled success=${result.success}: ${result.message}")
                if (result.success) {
                    AirplaneModeSchedule.cancelRetry(this)
                } else if (!retry) {
                    AirplaneModeSchedule.scheduleRetry(this, enabled)
                }
                if (!scheduled || !result.success) mainHandler.post {
                    Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falló el cambio de Sleep Mode", e)
                if (!retry) runCatching { AirplaneModeSchedule.scheduleRetry(this, enabled) }
                mainHandler.post {
                    Toast.makeText(applicationContext, "No se pudo cambiar Sleep Mode", Toast.LENGTH_LONG).show()
                }
            } finally {
                requestedEnabled = null
                applying.set(false)
                mainHandler.post {
                    android.service.quicksettings.TileService.requestListeningState(
                        this, android.content.ComponentName(this, SleepModeTileService::class.java)
                    )
                    stopSelfResult(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        worker.shutdownNow()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SleepModeAction"
        private const val CHANNEL = "sleep_mode_action"
        private const val SCHEDULED = "scheduled"
        private const val ENABLED = "enabled"
        private val applying = AtomicBoolean(false)
        @Volatile var requestedEnabled: Boolean? = null
            private set
        val isApplying: Boolean get() = applying.get()
        fun start(
            context: Context,
            scheduled: Boolean = false,
            enabled: Boolean? = null,
            retry: Boolean = false,
        ): Boolean {
            // Reserve synchronously, before Android starts the service. Two
            // taps in that gap used to enqueue two opposite toggle actions.
            if (!applying.compareAndSet(false, true)) return false
            val target = enabled ?: (scheduled || !SleepModeController.isEnabled(context))
            if (!retry) AirplaneModeSchedule.cancelRetry(context)
            requestedEnabled = target
            try {
                context.startForegroundService(Intent(context, SleepModeActionService::class.java)
                    .putExtra(SCHEDULED, scheduled)
                    .putExtra(AirplaneModeSchedule.EXTRA_RETRY, retry)
                    .putExtra(ENABLED, target))
            } catch (e: Exception) {
                requestedEnabled = null
                applying.set(false)
                throw e
            }
            return true
        }
    }
}
