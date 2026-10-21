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
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataTypes
import com.nemotron.voiceime.ui.SetupActivity
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * HealthTransferService: lee Samsung Health Data SDK y sube los datos a Firebase.
 *
 * Se inicia por solicitud del tile Fit3 o desde Ajustes, envía una vez y se detiene.
 */
class HealthTransferService : Service() {

    companion object {
        private const val TAG = "HealthTransferService"
        private const val CHANNEL_ID = "health_transfer"
        private const val NOTIF_ID = 4
        private const val ACTION_START = "com.nemotron.voiceime.health.START"
        private const val ACTION_START_AUTOMATIC = "com.nemotron.voiceime.health.START_AUTOMATIC"
        private const val ACTION_STOP = "com.nemotron.voiceime.health.STOP"
        private const val ACTION_STOP_AUTOMATIC = "com.nemotron.voiceime.health.STOP_AUTOMATIC"
        private const val PREFS = "health_transfer_state"
        private const val KEY_LAST_SUCCESSFUL_UPLOAD = "last_successful_upload_date"
        private const val KEY_LAST_AUTOMATIC_ATTEMPT = "last_automatic_attempt_date"
        private const val WAIT_TIMEOUT_SECONDS = 240L
        private class TransferWaiter {
            val latch = CountDownLatch(1)
            @Volatile var succeeded = false
                private set

            fun complete(success: Boolean) {
                succeeded = success
                latch.countDown()
            }
        }

        private val transferStateLock = Any()
        private var transferActive = false
        private var transferAutomatic = false
        private val completionWaiters = mutableListOf<TransferWaiter>()

        // Cuantos dias hacia atras leer en cada envio (datos recientes)
        // Peso/composición corporal puede tener el último registro de hace
        // varias semanas aunque el resto de datos sea reciente.
        private const val BACKFILL_DAYS = 30L

        fun start(context: Context) {
            val intent = Intent(context, HealthTransferService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        /** Automatic tile sync is limited to one successful upload per day. */
        fun startIfNeeded(context: Context) {
            val today = LocalDate.now(ZoneId.systemDefault()).toString()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastUpload = prefs.getString(KEY_LAST_SUCCESSFUL_UPLOAD, null)
            val lastAttempt = prefs.getString(KEY_LAST_AUTOMATIC_ATTEMPT, null)
            if (lastUpload == today || lastAttempt == today) {
                Log.i(TAG, "Sync automática omitida: ya hubo un intento hoy; usa Enviar ahora para reintentar")
                return
            }
            // Record the attempt before starting so a broken Health SDK/network
            // cannot create a foreground-service retry loop on every tile tap.
            prefs.edit().putString(KEY_LAST_AUTOMATIC_ATTEMPT, today).apply()
            val intent = Intent(context, HealthTransferService::class.java).setAction(ACTION_START_AUTOMATIC)
            context.startForegroundService(intent)
        }

        /** Starts a transfer and waits until the Firebase upload has finished. */
        fun startAndWait(context: Context, timeoutSeconds: Long = WAIT_TIMEOUT_SECONDS): Boolean {
            val waiter = TransferWaiter()
            synchronized(transferStateLock) { completionWaiters += waiter }
            return try {
                start(context)
                waiter.latch.await(timeoutSeconds, TimeUnit.SECONDS) && waiter.succeeded
            } finally {
                synchronized(transferStateLock) { completionWaiters -= waiter }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HealthTransferService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        /** Stop only a tile-triggered transfer; preserve an explicit manual upload. */
        fun stopAutomatic(context: Context) {
            val shouldStop = synchronized(transferStateLock) { transferActive && transferAutomatic }
            if (!shouldStop) return
            val intent = Intent(context, HealthTransferService::class.java).setAction(ACTION_STOP_AUTOMATIC)
            context.startService(intent)
        }

    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transferJob: Job? = null
    @Volatile private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        synchronized(transferStateLock) { latestStartId = startId }
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_AUTOMATIC -> {
                val shouldStop = synchronized(transferStateLock) {
                    transferActive && transferAutomatic
                }
                if (shouldStop) {
                    Log.i(TAG, "Cancelando sync automática porque se apagó el tile Fit3")
                    transferJob?.cancel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            else -> {
                startForegroundCompat()
                // Transferir UNA vez y auto-detener el servicio.
                // Asi NO queda corriendo en background (ahorro de bateria):
                // solo existe durante la transferencia.
                val shouldStartTransfer = synchronized(transferStateLock) {
                    if (transferActive) {
                        // An explicit manual send takes ownership of any
                        // currently running automatic transfer.
                        if (intent?.action == ACTION_START) transferAutomatic = false
                        false
                    } else {
                        transferActive = true
                        transferAutomatic = intent?.action == ACTION_START_AUTOMATIC
                        true
                    }
                }
                if (!shouldStartTransfer) {
                    // The Fit3 tile starts a transfer when it is enabled and
                    // calls startAndWait() again before freezing Samsung
                    // Health. Keep the first transfer alive; its completion
                    // will release every waiter. Stopping here cancels the
                    // in-flight coroutine and loses the snapshot.
                    Log.i(TAG, "Transferencia ya activa; se espera la existente")
                    return START_NOT_STICKY
                }
                transferJob?.cancel()
                transferJob = serviceScope.launch {
                    var succeeded = false
                    try {
                        transferData()
                        succeeded = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en transferencia: ${e.message}")
                    } finally {
                        val finished = synchronized(transferStateLock) {
                            transferActive = false
                            transferAutomatic = false
                            val waiters = completionWaiters.toList()
                            completionWaiters.clear()
                            waiters to latestStartId
                        }
                        finished.first.forEach { it.complete(succeeded) }
                        stopSelfResult(finished.second)
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
            Intent(this, SetupActivity::class.java),
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
        val manager = SamsungHealthManager(this, serviceScope)
        val granted = manager.grantedPermissions()
        Log.i(TAG, "Permisos Samsung Health Data SDK concedidos=${granted.size}/${manager.permissionSetForSetup().size}: $granted")
        val exercisePermission = Permission.of(DataTypes.EXERCISE, AccessType.READ)
        if (exercisePermission !in granted) {
            throw IllegalStateException("Falta permiso de lectura de ejercicio en Samsung Health")
        }
        val end = Instant.now()
        // El tile transfiere datos recientes. Leer toda la historia aquí hace
        // que la consulta tarde demasiado y el servicio pueda ser cancelado
        // antes de escribir el snapshot.
        val start = end.minusSeconds(BACKFILL_DAYS * 24L * 60L * 60L)
        Log.i(TAG, "Leyendo Samsung Health: $start -> $end")
        val payload = manager.readAllData(start, end, granted)
        Log.i(TAG, "Lectura Samsung Health terminada: ${payload.length()} tipos")
        if (!payload.has(DataTypes.EXERCISE.name)) {
            throw IllegalStateException("El snapshot no contiene datos de ejercicio")
        }

        // Documento que el servidor puede consultar desde cualquier red.
        val localDate = LocalDate.now(ZoneId.systemDefault()).toString()
        val wrapper = JSONObject()
        wrapper.put("type", "health_snapshot")
        wrapper.put("date", localDate)
        wrapper.put("device", "samsung-${Build.MODEL}")
        wrapper.put("data", payload)

        FirebaseHealthUploader(this).upload(localDate, wrapper)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_LAST_SUCCESSFUL_UPLOAD, localDate)
            .apply()
        Log.i(TAG, "Snapshot Samsung Health subido a Firebase")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
