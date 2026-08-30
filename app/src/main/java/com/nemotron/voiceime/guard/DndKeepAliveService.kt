package com.nemotron.voiceime.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore

/**
 * Servicio foreground mínimo que mantiene vivo el proceso de la app
 * mientras DND lock está activo. Sin esto, Samsung Freecess mata el
 * proceso y el DndLockReceiver deja de recibir broadcasts.
 */
class DndKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nemotron Guard")
            .setContentText("No Molestar activo — pantalla se bloquea automáticamente")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIF_ID, notification)
        Log.d(TAG, "DndKeepAliveService iniciado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "DndKeepAliveService destruido")
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Nemotron Guard",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene vivo el servicio de No Molestar"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "DndKeepAlive"
        private const val CHANNEL_ID = "dnd_keep_alive"
        private const val NOTIF_ID = 7777

        fun start(ctx: Context) {
            val intent = Intent(ctx, DndKeepAliveService::class.java)
            ctx.startForegroundService(intent)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DndKeepAliveService::class.java))
        }

        /** Inicia o detiene el servicio según los switches de Nemotron.
         *  Solo se necesita si DND lock está activo y el guard NO está on
         *  (el guard mantiene vivo el proceso con su servicio de accesibilidad,
         *  así que el keep-alive es redundante cuando el guard corre). */
        fun update(ctx: Context) {
            val dndLock = SecureStore.isDndLockEnabled(ctx)
            val guardOn = SecureStore.isAddictionGuardEnabled(ctx)
            if (dndLock && !guardOn) {
                start(ctx)
            } else {
                stop(ctx)
            }
        }
    }
}
