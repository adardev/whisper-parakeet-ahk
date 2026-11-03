package com.nemotron.voiceime.dhizuku

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore

/** Keeps Nemotron's Shizuku binder-death listener alive only while Auto is on. */
class AndroidAutoShizukuWatchdogService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SecureStore.isAndroidAutoTileOn(this)) {
            Log.i(TAG, "Auto tile is off; stopping Shizuku watchdog")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_auto_tile)
            .setContentTitle("Android Auto")
            .setContentText("Supervisando la conexión de Shizuku")
            .setSilent(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "Shizuku watchdog active while Auto tile is on")
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Android Auto watchdog",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val TAG = "AutoShizukuWatchdog"
        private const val CHANNEL_ID = "android_auto_shizuku_watchdog"
        private const val NOTIFICATION_ID = 9031

        fun start(context: Context) {
            if (!SecureStore.isAndroidAutoTileOn(context)) return
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AndroidAutoShizukuWatchdogService::class.java)
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Could not start the tile-scoped Shizuku watchdog", t)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AndroidAutoShizukuWatchdogService::class.java))
        }
    }
}
