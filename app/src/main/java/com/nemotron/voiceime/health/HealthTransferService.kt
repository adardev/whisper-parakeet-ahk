package com.nemotron.voiceime.health

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nemotron.voiceime.R
import com.nemotron.voiceime.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant

/**
 * HealthTransferService: lee Samsung Health Data SDK y sube los datos a Firebase.
 *
 * Se activa con un broadcast receiver en BOOT_COMPLETED y se mantiene vivo
 * enviando datos cada INTERVAL_MINUTES minutos.
 */
class HealthTransferService : Service() {

    companion object {
        private const val TAG = "HealthTransferService"
        private const val CHANNEL_ID = "health_transfer"
        private const val NOTIF_ID = 4
        private const val ACTION_START = "com.nemotron.voiceime.health.START"
        private const val ACTION_STOP = "com.nemotron.voiceime.health.STOP"

        // Cuantos dias hacia atras leer en cada envio (datos recientes)
        private const val BACKFILL_DAYS = 30L

        fun start(context: Context) {
            val intent = Intent(context, HealthTransferService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, HealthTransferService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transferJob: Job? = null
    private val transferLock = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundCompat()
                // Transferir UNA vez y auto-detener el servicio.
                // Asi NO queda corriendo en background (ahorro de bateria):
                // solo existe durante la transferencia.
                if (!transferLock.compareAndSet(false, true)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                transferJob?.cancel()
                transferJob = serviceScope.launch {
                    try {
                        transferData()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en transferencia: ${e.message}")
                    } finally {
                        transferLock.set(false)
                        stopSelf()
                    }
                }
            }
        }
        // No sticky: si Android lo mata, no se relanza (el Fit3 tile lo reinicia)
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Health Transfer",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Samsung Health")
            .setContentText("Subiendo datos de salud a Firebase...")
            .setSmallIcon(R.drawable.ic_fit3_tile)
            .setOngoing(false)
            .setContentIntent(launchIntent)
            .build()
    }

    private suspend fun transferData() {
        val manager = SamsungHealthManager(this)
        val granted = manager.grantedPermissions()
        if (granted.isEmpty()) {
            Log.w(TAG, "Sin permisos de Samsung Health Data SDK, no se transfiere")
            return
        }
        val end = Instant.now()
        // El tile transfiere datos recientes. Leer toda la historia aquí hace
        // que la consulta tarde demasiado y el servicio pueda ser cancelado
        // antes de escribir el snapshot.
        val start = end.minusSeconds(BACKFILL_DAYS * 24L * 60L * 60L)
        Log.i(TAG, "Leyendo Samsung Health: $start -> $end")
        val payload = manager.readAllData(start, end)
        Log.i(TAG, "Lectura Samsung Health terminada: ${payload.length()} tipos")

        // Documento que el servidor puede consultar desde cualquier red.
        val wrapper = JSONObject()
        wrapper.put("type", "health_snapshot")
        wrapper.put("date", end.toString().substring(0, 10))
        wrapper.put("device", "samsung-${Build.MODEL}")
        wrapper.put("data", payload)

        FirebaseHealthUploader(this).upload(end.toString().substring(0, 10), wrapper)
        Log.i(TAG, "Snapshot Samsung Health subido a Firebase")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
