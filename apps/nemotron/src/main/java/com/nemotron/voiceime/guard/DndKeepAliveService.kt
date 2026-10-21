package com.nemotron.voiceime.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore

/**
 * Servicio foreground mínimo que mantiene vivo el proceso de la app
 * mientras DND lock está activo. Sin esto, Samsung Freecess mata el
 * proceso y el DndLockReceiver deja de recibir broadcasts.
 */
class DndKeepAliveService : Service() {
    private val bluetoothReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val adapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                adapterState != BluetoothAdapter.STATE_OFF && adapterState != BluetoothAdapter.STATE_TURNING_OFF) return
            if (!isFit3Connected(context)) {
                Log.i(TAG, "Fit3 sin conexión; se detiene el servicio DND")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nemotron Guard")
            .setContentText("Fit3 conectada — bloqueo de No Molestar disponible")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIF_ID, notification)
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED).apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bluetoothReceiver, filter)
        }
        Log.d(TAG, "DndKeepAliveService iniciado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isFit3Connected(this)) {
            Log.i(TAG, "No se reinicia DND: la Fit3 no está conectada")
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(bluetoothReceiver) }
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

        private const val FIT3_PREFERENCES = "fit3_tile_preferences"
        private const val FIT3_ADDRESS_KEY = "paired_fit3_address"
        private const val KNOWN_FIT3_ADDRESS = "74:19:0A:49:64:DD"

        /** Only pin the DND listener while the paired Fit3 GATT link is live. */
        fun isFit3Connected(ctx: Context): Boolean = runCatching {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED) return false
            val savedAddress = ctx.getSharedPreferences(FIT3_PREFERENCES, Context.MODE_PRIVATE)
                .getString(FIT3_ADDRESS_KEY, KNOWN_FIT3_ADDRESS) ?: KNOWN_FIT3_ADDRESS
            val connected = ctx.getSystemService(BluetoothManager::class.java)
                ?.getConnectedDevices(BluetoothProfile.GATT).orEmpty()
            connected.any { device ->
                device.address.equals(savedAddress, ignoreCase = true) ||
                    device.name?.contains("Galaxy Fit3", ignoreCase = true) == true
            }
        }.onFailure { Log.w(TAG, "No se pudo comprobar la conexión GATT de Fit3", it) }
            .getOrDefault(false)

        /** Start the listener only while its connected watch can use it. */
        fun update(ctx: Context) {
            val dndLock = SecureStore.isDndLockEnabled(ctx)
            val guardOn = SecureStore.isAddictionGuardEnabled(ctx)
            if (!dndLock || guardOn) {
                stop(ctx)
                return
            }

            if (!isFit3Connected(ctx)) {
                Log.i(TAG, "Fit3 desconectada; DndKeepAliveService queda apagado")
                stop(ctx)
                return
            }

            if (guardOn) {
                stop(ctx)
                return
            }
            start(ctx)
        }
    }
}
