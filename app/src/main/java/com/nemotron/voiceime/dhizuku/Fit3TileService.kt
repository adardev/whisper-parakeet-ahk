package com.nemotron.voiceime.dhizuku

import android.util.Log
import com.nemotron.voiceime.R

/**
 * Tile para congelar/descongelar Galaxy Fit3 Plugin + Samsung Health.
 * Al congelar también fuerza el cierre de Samsung Accessory Service
 * (dueño de la conexión GATT del Fit3) para desconectar SOLO el reloj,
 * sin apagar el Bluetooth.
 * Al descongelar enciende el Bluetooth si esta apagado (para reconectar el Fit3).
 */
class Fit3TileService : AppFreezeTileService() {
    override val targetPackage: String = "com.samsung.wearable.fit3plugin"
    override val targetPackages: List<String> = listOf(
        "com.samsung.wearable.fit3plugin",
        "com.sec.android.app.shealth" // Samsung Health
    )
    override val tileLabel: String = "Fit3"
    override val tileIconRes: Int = R.drawable.ic_fit3_tile

    override fun onAfterFreeze() {
        ShizukuManager.stopApp("com.samsung.accessory")
    }

    /** Al descongelar: enciende Bluetooth si esta apagado, para reconectar el Fit3. */
    override fun onAfterUnfreeze() {
        ensureBluetoothOn()
    }

    private fun ensureBluetoothOn() {
        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku permission not granted, no puedo encender Bluetooth")
            return
        }
        // Consulta el estado de Bluetooth via settings global (0=off, 1=on)
        val state = runCatching {
            ShizukuManager.execShellFresh(arrayOf("settings", "get", "global", "bluetooth_on"))
        }.getOrNull()?.trim()
        Log.d(TAG, "Bluetooth bluetooth_on=$state")
        // Devuelve "0" (off) o "1" (on)
        val isOn = state == "1"
        if (!isOn) {
            Log.d(TAG, "Bluetooth apagado, encendiendo...")
            ShizukuManager.execShellFresh(arrayOf("cmd", "bluetooth_manager", "enable"))
            Log.d(TAG, "Bluetooth encendido")
        } else {
            Log.d(TAG, "Bluetooth ya esta encendido")
        }
    }

    companion object {
        private const val TAG = "Fit3TileService"
    }
}