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
        // DND keep-alive solo corre con Fit3 activo: actualizar al congelar
        try {
            com.nemotron.voiceime.guard.DndKeepAliveService.update(applicationContext)
        } catch (_: Throwable) {}
    }

    /** Sincroniza y espera la subida antes de congelar Samsung Health. */
    override fun onBeforeFreeze(): Long {
        try {
            val ok = com.nemotron.voiceime.health.HealthTransferService
                .startAndWait(applicationContext)
            Log.d(TAG, "Sync antes de congelar: terminado=$ok")
        } catch (_: Throwable) {}
        return 0L
    }

    /** Al descongelar: enciende Bluetooth si esta apagado y arranca sync de salud. */
    override fun onAfterUnfreeze() {
        ensureBluetoothOn()
        // Samsung Health puede perder sus permisos de Health Connect cuando
        // One UI vuelve a habilitar el paquete. Intentamos restaurar todos
        // los permisos de salud que el propio paquete declara usando el
        // shell de Shizuku. Esto no toca permisos normales ni concede nada a
        // nuestra app: el destinatario es exclusivamente Samsung Health.
        restoreSamsungHealthConnectPermissions()
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

    private fun restoreSamsungHealthConnectPermissions() {
        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "No se pueden restaurar permisos de Health Connect: Shizuku no autorizado")
            return
        }

        val pkg = targetPackages.firstOrNull { it == SAMSUNG_HEALTH_PACKAGE }
            ?: SAMSUNG_HEALTH_PACKAGE
        val dump = ShizukuManager.execShellFresh(arrayOf("dumpsys", "package", pkg))
        if (dump.isNullOrBlank()) {
            Log.w(TAG, "No se pudo inspeccionar $pkg para restaurar Health Connect")
            return
        }

        // Samsung Health declara los permisos como android.permission.health.*.
        // Se obtiene la lista del dispositivo para que funcione con distintas
        // versiones de Android/Health Connect sin mantener una lista obsoleta.
        val permissions = Regex("android\\.permission\\.health\\.[A-Z0-9_]+")
            .findAll(dump)
            .map { it.value }
            .distinct()
            .filter { it.contains("READ_") || it.contains("WRITE_") }
            .toList()

        if (permissions.isEmpty()) {
            Log.w(TAG, "$pkg no declara permisos android.permission.health.*")
            return
        }

        var granted = 0
        for (permission in permissions) {
            val result = ShizukuManager.execShellFresh(
                arrayOf("sh", "-c", "pm grant $pkg $permission 2>&1")
            ).orEmpty()
            // pm normalmente no imprime nada si tuvo éxito. Los errores se
            // dejan en log para poder detectar restricciones de One UI.
            if (result.isBlank()) {
                granted++
            } else {
                Log.w(TAG, "Health Connect no permitió $permission: $result")
            }
        }
        Log.i(TAG, "Health Connect: solicitados=${permissions.size}, enviados=$granted para $pkg")
    }

    companion object {
        private const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
        private const val TAG = "Fit3TileService"
    }
}
