package com.nemotron.voiceime.dhizuku

import android.util.Log
import com.nemotron.voiceime.R

/** Samsung Health sync control. Fit3 Plugin stays active; Galaxy Wearable has its own tile. */
class Fit3TileService : AppFreezeTileService() {
    override val targetPackage: String = SAMSUNG_HEALTH_PACKAGE
    override val tileLabel: String = "Samsung Health"
    override val tileIconRes: Int = R.drawable.ic_fit3_tile

    /** Require Firebase to confirm the exercise upload before freezing Health. */
    override fun onBeforeFreeze(): Long = try {
        Log.i(TAG, "Sincronizando ejercicio con Firebase antes de congelar Samsung Health")
        if (com.nemotron.voiceime.health.HealthTransferService.startAndWait(applicationContext)) {
            0L
        } else {
            Log.e(TAG, "No se confirmó la subida; Samsung Health permanece activa")
            -1L
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Falló la sincronización previa; Samsung Health permanece activa", t)
        -1L
    }

    /** Restore Health Connect permissions and queue its normal daily sync. */
    override fun onAfterUnfreeze() {
        restoreSamsungHealthConnectPermissions()
        try {
            com.nemotron.voiceime.health.HealthTransferService.startIfNeeded(applicationContext)
        } catch (t: Throwable) {
            Log.e(TAG, "No se pudo iniciar la transferencia al activar Samsung Health", t)
        }
    }

    private fun restoreSamsungHealthConnectPermissions() {
        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "No se pueden restaurar permisos de Health Connect: Shizuku no autorizado")
            return
        }
        val pkg = SAMSUNG_HEALTH_PACKAGE
        val dump = ShizukuManager.execShellFresh(arrayOf("dumpsys", "package", pkg))
        if (dump.isNullOrBlank()) {
            Log.w(TAG, "No se pudo inspeccionar $pkg para restaurar Health Connect")
            return
        }
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
        val commands = permissions.joinToString("; ") { permission ->
            "pm grant $pkg $permission 2>&1 || echo __NEMO_GRANT_FAILED__:$permission"
        }
        val result = ShizukuManager.execShellFresh(
            arrayOf("sh", "-c", commands), timeoutMs = 10_000L
        ).orEmpty()
        val failures = result.lineSequence()
            .filter { it.contains("__NEMO_GRANT_FAILED__:") }
            .toList()
        failures.forEach { Log.w(TAG, "Health Connect no permitió $it") }
        Log.i(TAG, "Health Connect: solicitados=${permissions.size}, enviados=${permissions.size - failures.size} para $pkg")
    }

    companion object {
        private const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
        private const val TAG = "SamsungHealthTileService"
    }
}
