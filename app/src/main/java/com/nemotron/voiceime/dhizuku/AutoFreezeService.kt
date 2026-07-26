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
import android.os.IBinder
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

class AutoFreezeService : Service() {

    private var receiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        register()
        Log.d(TAG, "started")
    }

    override fun onDestroy() {
        unregister()
        super.onDestroy()
        Log.d(TAG, "destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Auto-Freeze", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Nemotron")
                .setContentText("Auto-freeze activo")
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Nemotron")
                .setContentText("Auto-freeze activo")
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
                        val apps = SecureStore.getAutoFreezeApps(ctx)
                        if (apps.isEmpty()) return
                        Log.d(TAG, "SCREEN_OFF → freeze ${apps.size} apps")
                        Thread {
                            for (pkg in apps) {
                                try { DhizukuManager.hideAppRaw(ctx, pkg) }
                                catch (t: Throwable) { Log.e(TAG, "freeze $pkg failed", t) }
                            }
                            Log.d(TAG, "auto-freeze done")
                        }.start()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        val apps = SecureStore.getAutoFreezeApps(ctx)
                        if (apps.isEmpty()) return
                        Log.d(TAG, "USER_PRESENT → unfreeze ${apps.size} apps")
                        Thread {
                            for (pkg in apps) {
                                try { DhizukuManager.unhideAppRaw(ctx, pkg) }
                                catch (t: Throwable) { Log.e(TAG, "unfreeze $pkg failed", t) }
                            }
                            Log.d(TAG, "auto-unfreeze done")
                        }.start()
                    }
                }
            }
        }
        registerReceiver(receiver, filter)
    }

    private fun unregister() {
        receiver?.let { try { unregisterReceiver(it) } catch (_: Throwable) {} }
        receiver = null
    }

    companion object {
        private const val TAG = "AutoFreezeService"
        private const val CHANNEL_ID = "auto_freeze"
        private const val NOTIF_ID = 7777

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AutoFreezeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoFreezeService::class.java))
        }
    }
}
