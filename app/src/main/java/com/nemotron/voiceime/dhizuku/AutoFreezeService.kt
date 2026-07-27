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
    private var pendingFreeze: Runnable? = null
    private var airplaneWasEnabledByUs = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        register()
        Log.d(TAG, "started")
    }

    override fun onDestroy() {
        cancelPendingFreeze()
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
                        val airplane = SecureStore.isAutoAirplane(ctx)
                        if (apps.isEmpty() && !airplane) return
                        cancelPendingFreeze()
                        Log.d(TAG, "SCREEN_OFF → freeze now")

                        if (airplane) {
                            // Do not turn it off on unlock if the user had
                            // already enabled airplane mode before locking.
                            if (!DhizukuManager.isAirplaneModeOn(ctx)) {
                                airplaneWasEnabledByUs = true
                                val intent = Intent(ctx, AirplaneReceiver::class.java).apply {
                                    action = ACTION_AIRPLANE
                                    putExtra(EXTRA_ENABLE, true)
                                }
                                ctx.sendBroadcast(intent)
                                Log.d(TAG, "airplane ON requested")
                            }
                        }

                        val r = Runnable {
                            pendingFreeze = null
                            val currentApps = SecureStore.getAutoFreezeApps(ctx)
                            if (currentApps.isEmpty()) return@Runnable

                            Thread {
                                for (pkg in currentApps) {
                                    try { DhizukuManager.hideAppRaw(ctx, pkg) }
                                    catch (t: Throwable) { Log.e(TAG, "freeze $pkg failed", t) }
                                }
                                Log.d(TAG, "frozen ${currentApps.size} apps")
                            }.start()
                        }
                        pendingFreeze = r
                        if (DELAY_MS > 0) handler.postDelayed(r, DELAY_MS) else r.run()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        cancelPendingFreeze()

                        if (airplaneWasEnabledByUs) {
                            airplaneWasEnabledByUs = false
                            val intent = Intent(ctx, AirplaneReceiver::class.java).apply {
                                action = ACTION_AIRPLANE
                                putExtra(EXTRA_ENABLE, false)
                            }
                            ctx.sendBroadcast(intent)
                            Log.d(TAG, "airplane OFF requested")
                        }

                        val apps = SecureStore.getAutoFreezeApps(ctx)
                        if (apps.isEmpty()) return
                        val tileFrozen = DhizukuManager.isCurrentlyFrozen(ctx)
                        val tileApps = SecureStore.getFrozenApps(ctx)
                        val toUnfreeze = if (tileFrozen) apps.filter { it !in tileApps } else apps.toList()
                        if (toUnfreeze.isEmpty()) {
                            Log.d(TAG, "USER_PRESENT → skip unfreeze (tile active, all in tile list)")
                            return
                        }
                        val skipped = apps.size - toUnfreeze.size
                        Log.d(TAG, "USER_PRESENT → unfreeze ${toUnfreeze.size} apps${if (skipped > 0) " (skipped $skipped tile-frozen)" else ""}")
                        Thread {
                            for (pkg in toUnfreeze) {
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

    private fun cancelPendingFreeze() {
        pendingFreeze?.let { handler.removeCallbacks(it) }
        pendingFreeze = null
    }

    private fun unregister() {
        receiver?.let { try { unregisterReceiver(it) } catch (_: Throwable) {} }
        receiver = null
    }

    companion object {
        private const val TAG = "AutoFreezeService"
        private const val CHANNEL_ID = "auto_freeze"
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
