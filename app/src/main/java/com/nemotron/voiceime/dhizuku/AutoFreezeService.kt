package com.nemotron.voiceime.dhizuku

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

class AutoFreezeService : Service() {

    private var receiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAirplaneEnable: Runnable? = null
    private var airplaneWasEnabledByUs = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        register()
        Log.d(TAG, "started")
    }

    override fun onDestroy() {
        cancelPendingAirplaneEnable()
        unregister()
        super.onDestroy()
        Log.d(TAG, "destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startForeground(NOTIF_ID, buildNotification()) } catch (_: Throwable) {}
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Airplane Lock", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Nemotron")
                .setContentText("Airplane Lock activo")
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Nemotron")
                .setContentText("Airplane Lock activo")
                .setOngoing(true)
                .build()
        }
    }

    private fun register() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        if (!SecureStore.isAutoAirplane(ctx)) return
                        cancelPendingAirplaneEnable()
                        val enable = Runnable {
                            pendingAirplaneEnable = null
                            if (!SecureStore.isAutoAirplane(ctx)) return@Runnable
                            airplaneWasEnabledByUs = true
                            ctx.sendBroadcast(airplaneIntent(ctx, true))
                            Log.d(TAG, "SCREEN_OFF → airplane ON requested after $DELAY_MS ms")
                        }
                        pendingAirplaneEnable = enable
                        handler.postDelayed(enable, DELAY_MS)
                        Log.d(TAG, "SCREEN_OFF → airplane ON scheduled in $DELAY_MS ms")
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        cancelPendingAirplaneEnable()
                        if (airplaneWasEnabledByUs) {
                            airplaneWasEnabledByUs = false
                            ctx.sendBroadcast(airplaneIntent(ctx, false))
                            Log.d(TAG, "USER_PRESENT → airplane OFF requested")
                        }
                    }
                }
            }
        }
        registerReceiver(receiver, filter)
    }

    private fun airplaneIntent(context: Context, enabled: Boolean) =
        Intent(context, AirplaneReceiver::class.java).apply {
            action = ACTION_AIRPLANE
            putExtra(EXTRA_ENABLE, enabled)
        }

    private fun cancelPendingAirplaneEnable() {
        pendingAirplaneEnable?.let { handler.removeCallbacks(it) }
        pendingAirplaneEnable = null
    }

    private fun unregister() {
        receiver?.let { try { unregisterReceiver(it) } catch (_: Throwable) {} }
        receiver = null
    }

    companion object {
        private const val TAG = "AutoFreezeService"
        private const val CHANNEL_ID = "airplane_lock"
        private const val NOTIF_ID = 7777
        private const val DELAY_MS = 30_000L
        const val ACTION_AIRPLANE = "com.nemotron.voiceime.AIRPLANE_TOGGLE"
        const val EXTRA_ENABLE = "enable"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AutoFreezeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoFreezeService::class.java))
        }
    }
}
