package com.nemotron.voiceime.dhizuku

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log

/** Keeps Samsung Health enabled for Health Connect while preparing one-shot Fit3 settings transfers. */
internal object Fit3TileAutomation {
    private const val TAG = "Fit3TileAutomation"
    private val packages = listOf("com.sec.android.app.shealth")

    fun prepareConnection(context: Context): Boolean {
        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku no está autorizado")
            return false
        }
        // Only Samsung Health is controlled here. Fit3 Plugin and Galaxy Wearable
        // are left to their always-active service and their dedicated user tile.
        val frozen = ShizukuManager.stillDisabled(packages)
        for (pkg in packages) {
            if (pkg in frozen && !ShizukuManager.unhideApp(pkg)) {
                Log.e(TAG, "no se pudo activar $pkg")
                return false
            }
            if (!ShizukuManager.unstopApp(pkg)) {
                Log.e(TAG, "no se pudo quitar force-stop de $pkg")
                return false
            }
        }

        if (!ShizukuManager.ensureHealthConnectPermissions("com.sec.android.app.shealth")) {
            Log.e(TAG, "No se pudieron asegurar todos los permisos de Health Connect antes de preparar la conexión")
            return false
        }

        if (frozen.isNotEmpty()) requestTileRefresh(context)
        return true
    }

    /** After sending settings, stop Samsung Health without disabling its Health Connect grants. */
    fun freezeAfterSetting(context: Context): Boolean {
        if (!ShizukuManager.hasPermission()) return false
        if (!ShizukuManager.ensureHealthConnectPermissions("com.sec.android.app.shealth")) {
            Log.e(TAG, "No se pudieron asegurar todos los permisos de Health Connect antes de pausar Samsung Health")
            return false
        }
        var ok = true
        for (pkg in packages) {
            if (pkg in ShizukuManager.stillDisabled(listOf(pkg))) {
                if (!ShizukuManager.unhideApp(pkg)) ok = false
            }
            if (!ShizukuManager.stopApp(pkg)) ok = false
        }
        requestTileRefresh(context)
        return ok
    }

    private fun requestTileRefresh(context: Context) {
        runCatching {
            TileService.requestListeningState(
                context,
                ComponentName(context, Fit3TileService::class.java)
            )
        }
    }

}
