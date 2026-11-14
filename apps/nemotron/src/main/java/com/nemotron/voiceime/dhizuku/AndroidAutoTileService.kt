package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore

/** Tile para congelar/descongelar Android Auto. */
class AndroidAutoTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.google.android.projection.gearhead"
    override val tileLabel: String = "Auto"
    override val tileIconRes: Int = R.drawable.ic_auto_tile

    override fun onAfterUnfreeze() {
        // The package is confirmed enabled before this hook is called. Persist
        // the tile state before starting the service so its cold-start guard
        // cannot race with publishTileState's main-thread callback.
        SecureStore.setAndroidAutoTileOn(this, true)
        Thread {
            val context = this
            // These are independent: toggling system radios/settings does not
            // depend on the session-only APK mount. Run both at once so all
            // secondary work finishes sooner after the tile state changes.
            val optionsTask = Thread({
                if (!RootShellManager.setAndroidAutoTileOptions(enabled = true)) {
                    android.util.Log.e(TAG, "Falló una opción agrupada; reintentando las opciones por separado")
                    setLocation(true)
                    setBluetooth(true)
                    setMobileData(true)
                    RootShellManager.setDoubleTapToWake(true)
                    RootShellManager.setDoubleTapToSleep(true)
                }
            }, "Nemotron-AA-options-on")
            val patchTask = Thread({
                // Keep media listener setup after the patch has restarted AA.
                // The patch remains session-only and version/hash checked.
                if (!AndroidAutoRailPatch.enable(context)) {
                    android.util.Log.e(TAG, "No se pudo aplicar el parche de la barra de Android Auto")
                }
            }, "Nemotron-AA-patch-on")
            optionsTask.start()
            patchTask.start()
            patchTask.join()
            if (!AutoAndroidAuto.prepareTileMedia(this)) {
                android.util.Log.e(TAG, "Android Auto quedó visible pero no listo para media")
            }
            optionsTask.join()
        }.start()
    }

    override fun onAfterFreeze() {
        SecureStore.setAndroidAutoTileOn(this, false)
        Thread {
            // Restore the three independent side effects concurrently. Do not
            // block publication of the inactive tile state while these run.
            val optionsTask = Thread({
                if (!RootShellManager.setAndroidAutoTileOptions(enabled = false)) {
                    android.util.Log.e(TAG, "Falló una opción agrupada; reintentando las opciones por separado")
                    setLocation(false)
                    setBluetooth(false)
                    setMobileData(false)
                    RootShellManager.setDoubleTapToWake(false)
                    RootShellManager.setDoubleTapToSleep(false)
                }
            }, "Nemotron-AA-options-off")
            val patchTask = Thread({ AndroidAutoRailPatch.disable(this) }, "Nemotron-AA-patch-off")
            val mediaTask = Thread({ AutoAndroidAuto.setCarMediaBridgeEnabled(this, false) }, "Nemotron-AA-media-off")
            optionsTask.start()
            patchTask.start()
            mediaTask.start()
        }.start()
    }

    /**
     * Registra si el tile está "encendido" (Android Auto activo, no congelado).
     * An unavailable root check is not evidence that the tile was switched off.
     */
    override fun onTileState(granted: Boolean, hidden: Boolean) {
        // Keep the last confirmed state when a root query fails.
        if (!granted) {
            android.util.Log.d(TAG, "Root unavailable; keeping last confirmed Android Auto tile state")
            return
        }
        val enabled = !hidden
        SecureStore.setAndroidAutoTileOn(this, enabled)
        android.util.Log.d(TAG, "Confirmed Android Auto tile state: enabled=$enabled")
    }

    /** Follows the Android Auto tile state for the system-wide location switch. */
    private fun setLocation(enabled: Boolean) {
        if (!RootShellManager.hasPermission()) return
        RootShellManager.execShellFresh(
            arrayOf("cmd", "location", "set-location-enabled", enabled.toString())
        )
    }

    /** Enciende/apaga Bluetooth según el estado de Android Auto. */
    private fun setBluetooth(enabled: Boolean) {
        if (!RootShellManager.hasPermission()) return
        val cmd = if (enabled) "enable" else "disable"
        RootShellManager.execShellFresh(arrayOf("cmd", "bluetooth_manager", cmd))
    }

    /** Enciende/apaga datos móviles según el estado de Android Auto. */
    private fun setMobileData(enabled: Boolean) {
        if (!RootShellManager.hasPermission()) return
        val cmd = if (enabled) "enable" else "disable"
        RootShellManager.execShellFresh(arrayOf("svc", "data", cmd))
    }

    companion object {
        private const val TAG = "AndroidAutoTile"
    }
}
