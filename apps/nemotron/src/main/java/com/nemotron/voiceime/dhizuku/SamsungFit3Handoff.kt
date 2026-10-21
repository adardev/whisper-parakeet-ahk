package com.nemotron.voiceime.dhizuku

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.SystemClock
import android.util.Log

/** Release Samsung's SAP bearer for one exchange; Fit3 Plugin stays enabled. */
internal object SamsungFit3Handoff {
    private const val TAG = "Fit3Handoff"
    private const val ACCESSORY = "com.samsung.accessory"
    private const val PREFS = "fit3_handoff_recovery"
    private const val RESTORE_ACCESSORY = "restore_accessory"
    private const val RESTORE_BLUETOOTH_OFF = "restore_bluetooth_off"

    @Synchronized
    fun <T> run(context: Context, action: () -> T): T {
        check(ShizukuManager.hasPermission()) { "Shizuku no está autorizado" }
        check(recoverIfNeeded(context)) { "No se pudo restaurar la conexión Samsung anterior" }
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val disabled = ShizukuManager.getDisabledPackages()
            ?: error("No se pudo consultar Samsung Accessory")
        try {
            if (ACCESSORY !in disabled) {
                check(preferences.edit().putBoolean(RESTORE_ACCESSORY, true).commit())
                check(ShizukuManager.hideApp(ACCESSORY)) { "No se pudo ceder la sesión SAP de Samsung" }
                ShizukuManager.execShellFresh(arrayOf("am", "force-stop", "--user", "0", ACCESSORY))
                // The GATT client belongs to Accessory, not Fit3 Plugin. Leave
                // the plugin and Wearable running throughout this exchange.
                Thread.sleep(2_000L)
            }
            if (!bluetoothEnabled(context)) {
                check(preferences.edit().putBoolean(RESTORE_BLUETOOTH_OFF, true).commit())
                ShizukuManager.execShellFresh(arrayOf("cmd", "bluetooth_manager", "enable"))
                check(waitForBluetooth(context, true)) { "Bluetooth no se encendió para contactar Fit3" }
            }
            Log.i(TAG, "Sesión Samsung cedida; Fit3 Plugin no se modifica")
            return action()
        } finally {
            check(recoverIfNeeded(context)) { "No se pudo restaurar Samsung Accessory después del ajuste Fit3" }
        }
    }

    /** Also called after a process restart with Shizuku ready. No periodic work. */
    @Synchronized
    fun recoverIfNeeded(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val accessoryPending = preferences.getBoolean(RESTORE_ACCESSORY, false)
        val bluetoothPending = preferences.getBoolean(RESTORE_BLUETOOTH_OFF, false)
        if (!accessoryPending && !bluetoothPending) return true
        if (!ShizukuManager.hasPermission()) return false
        var restored = true
        if (bluetoothPending) {
            // Restore an originally off adapter, unless another peripheral
            // connected during this brief exchange.
            val keepBluetooth = hasOtherConnectedDevices(context)
            if (!keepBluetooth && bluetoothEnabled(context)) {
                ShizukuManager.execShellFresh(arrayOf("cmd", "bluetooth_manager", "disable"))
            }
            if (keepBluetooth || !bluetoothEnabled(context) || waitForBluetooth(context, false)) {
                preferences.edit().remove(RESTORE_BLUETOOTH_OFF).commit()
            } else restored = false
        }
        if (accessoryPending) {
            val enabled = ShizukuManager.unhideApp(ACCESSORY) && ShizukuManager.unstopApp(ACCESSORY)
            if (enabled) {
                val result = ShizukuManager.execShellFresh(arrayOf(
                    "am", "startservice", "--user", "0", "-n",
                    "$ACCESSORY/.services.SAFrameworkService", "-a",
                    "com.samsung.accessory.api.ISAGenFrameworkManager"
                ))
                if (result?.contains("Starting service:") == true && !result.contains("Error", true)) {
                    preferences.edit().remove(RESTORE_ACCESSORY).commit()
                    Log.i(TAG, "Samsung Accessory restaurado y servicio iniciado")
                } else {
                    restored = false
                    Log.e(TAG, "No se confirmó el reinicio de Samsung Accessory: $result")
                }
            } else restored = false
        }
        return restored
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothEnabled(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    private fun waitForBluetooth(context: Context, enabled: Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 6_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (bluetoothEnabled(context) == enabled) return true
            Thread.sleep(200L)
        }
        return bluetoothEnabled(context) == enabled
    }

    @SuppressLint("MissingPermission")
    private fun hasOtherConnectedDevices(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return true
        if (manager.getConnectedDevices(BluetoothProfile.GATT).any {
            !it.name.orEmpty().contains("Fit3", true) && !it.name.orEmpty().contains("R390", true)
        }) return true
        listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP, BluetoothProfile.HEARING_AID, 4, 5, 22)
            .any { manager.adapter.getProfileConnectionState(it) != BluetoothProfile.STATE_DISCONNECTED }
    }.getOrDefault(true)
}
