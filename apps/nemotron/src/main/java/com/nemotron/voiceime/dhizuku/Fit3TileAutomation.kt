package com.nemotron.voiceime.dhizuku

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log

/** Briefly unfreezes Samsung Health for a settings transfer; never controls Fit3 Plugin or Wearable. */
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
        }

        if (frozen.isNotEmpty()) requestTileRefresh(context)
        return true
    }

    /** After sending watch settings, restore Samsung Health to its configured off state. */
    fun freezeAfterSetting(context: Context): Boolean {
        if (!ShizukuManager.hasPermission()) return false
        var ok = true
        for (pkg in packages) {
            if (!ShizukuManager.stillDisabled(listOf(pkg)).contains(pkg)) {
                if (!ShizukuManager.hideApp(pkg)) ok = false
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
