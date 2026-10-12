package com.nemotron.voiceime.dhizuku

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log

/** Briefly unfreezes the Fit3 tile's packages so the band can receive a BLE setting change. */
internal object Fit3TileAutomation {
    private const val TAG = "Fit3TileAutomation"
    private val packages = listOf("com.samsung.wearable.fit3plugin", "com.sec.android.app.shealth")

    fun prepareConnection(context: Context): Boolean {
        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku no está autorizado")
            return false
        }

        // Match the Fit3 tile: if any target package is frozen, unfreeze both.
        val frozen = ShizukuManager.stillDisabled(packages)
        for (pkg in packages) {
            if (pkg in frozen && !ShizukuManager.unhideApp(pkg)) {
                Log.e(TAG, "no se pudo activar $pkg")
                return false
            }
        }

        val bluetooth = ShizukuManager.execShellFresh(arrayOf("settings", "get", "global", "bluetooth_on"))?.trim()
        if (bluetooth != "1") {
            if (ShizukuManager.execShellFresh(arrayOf("cmd", "bluetooth_manager", "enable")) == null) {
                Log.e(TAG, "no se pudo encender Bluetooth")
                return false
            }
            // Bluetooth enable is asynchronous; give the adapter a short head start.
            Thread.sleep(1_500)
        }
        val enabled = ShizukuManager.execShellFresh(arrayOf("settings", "get", "global", "bluetooth_on"))?.trim() == "1"
        if (!enabled) Log.e(TAG, "Bluetooth sigue apagado; no se envió el cambio AOD")
        if (frozen.isNotEmpty()) requestTileRefresh(context)
        return enabled
    }

    /** After sending the watch settings, restore the tile to its battery-saving off state. */
    fun freezeAfterSetting(context: Context): Boolean {
        if (!ShizukuManager.hasPermission()) return false
        var ok = true
        for (pkg in packages) {
            if (!ShizukuManager.stillDisabled(listOf(pkg)).contains(pkg)) {
                if (!ShizukuManager.hideApp(pkg)) ok = false
            }
            if (!ShizukuManager.stopApp(pkg)) ok = false
        }
        ShizukuManager.stopApp("com.samsung.accessory")
        disableBluetoothIfNoConnectedDevices()
        runCatching { com.nemotron.voiceime.guard.DndKeepAliveService.update(context.applicationContext) }
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

    private fun disableBluetoothIfNoConnectedDevices() {
        repeat(8) { attempt ->
            Thread.sleep(if (attempt == 0) 1_200L else 800L)
            val dump = ShizukuManager.execShellFresh(arrayOf("dumpsys", "bluetooth_manager"), timeoutMs = 8_000L)
            if (dump.isNullOrBlank()) {
                Log.w(TAG, "no se pudo comprobar si hay otros periféricos; Bluetooth se conserva")
                return
            }
            val gattDevices = Regex("Connection<[^>]*device:\\s*([^,>]+)")
                .findAll(dump).map { it.groupValues[1].trim().uppercase() }.toSet()
            val profileDevices = Regex("Devices \\((\\d+) /")
                .findAll(dump).sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
            if (gattDevices.isEmpty() && profileDevices == 0) {
                if (ShizukuManager.execShellFresh(arrayOf("settings", "get", "global", "bluetooth_on"))?.trim() == "1") {
                    ShizukuManager.execShellFresh(arrayOf("cmd", "bluetooth_manager", "disable"))
                }
                return
            }
        }
        Log.i(TAG, "Bluetooth se conserva porque hay otros dispositivos conectados")
    }
}
