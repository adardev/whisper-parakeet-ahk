package com.nemotron.voiceime.dhizuku

import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.rosan.dhizuku.api.Dhizuku

/** Immediate system airplane-mode tile used to compare against the automatic tile. */
class SystemAirplaneTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val newState = !DhizukuManager.isAirplaneModeOn(this)
        Log.d(TAG, "Immediate airplane tile: setting system mode to $newState")
        Thread {
            val success = toggleAirplaneMode(newState)
            Log.d(TAG, "Immediate airplane tile result=$success")
            handler.post { updateTileState() }
        }.start()
    }

    private fun toggleAirplaneMode(enabled: Boolean): Boolean {
        if (toggleThroughConnectivityService(enabled)) return true

        return try {
            Settings.Global.putInt(
                contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                if (enabled) 1 else 0
            )
            sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING)
                putExtra("state", enabled)
            })
            Log.d(TAG, "System airplane mode broadcast sent: $enabled")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Could not toggle immediate airplane mode", t)
            false
        }
    }

    /**
     * Calls ConnectivityService through Dhizuku's binder wrapper. This keeps
     * the privileged call in the device-owner identity instead of relying on
     * shell commands or a protected broadcast.
     */
    private fun toggleThroughConnectivityService(enabled: Boolean): Boolean {
        return try {
            Dhizuku.init(this)
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val connectivity = getService.invoke(null, "connectivity") as? IBinder ?: return false
            val wrappedBinder = Dhizuku.binderWrapper(connectivity)
            val stub = Class.forName("android.net.IConnectivityManager\$Stub")
            val service = stub.getMethod("asInterface", IBinder::class.java).invoke(null, wrappedBinder)
                ?: return false
            val interfaceClass = Class.forName("android.net.IConnectivityManager")
            interfaceClass.getMethod("setAirplaneMode", Boolean::class.javaPrimitiveType)
                .invoke(service, enabled)
            Log.d(TAG, "ConnectivityService.setAirplaneMode($enabled) completed")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "ConnectivityService direct call unavailable", t)
            false
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val enabled = DhizukuManager.isAirplaneModeOn(this)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Airplane Now"
        tile.subtitle = if (enabled) "ON" else "OFF"
        tile.updateTile()
    }

    companion object {
        private const val TAG = "SystemAirplaneTile"
    }
}
