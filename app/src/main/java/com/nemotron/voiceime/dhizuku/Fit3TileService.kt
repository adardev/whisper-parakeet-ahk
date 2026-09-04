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
        // Detener el servicio de salud: con el Fit3 congelado no se sincroniza
        // (ahorro de bateria, no queda corriendo en background).
        try {
            com.nemotron.voiceime.health.HealthTransferService.stop(applicationContext)
        } catch (_: Throwable) {}
        // DND keep-alive solo corre con Fit3 activo: actualizar al congelar
        try {
            com.nemotron.voiceime.guard.DndKeepAliveService.update(applicationContext)
        } catch (_: Throwable) {}
    }

    /** Al descongelar: enciende Bluetooth si esta apagado y arranca sync de salud. */
    override fun onAfterUnfreeze() {
        ensureBluetoothOn()
        // Arranca el servicio de salud: transfiere una vez y se auto-detiene.
        try {
            com.nemotron.voiceime.health.HealthTransferService.start(applicationContext)
        } catch (_: Throwable) {}
        // Re-evaluar DND keep-alive ahora que Fit3 esta activo
        try {
            com.nemotron.voiceime.guard.DndKeepAliveService.update(applicationContext)
        } catch (_: Throwable) {}
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