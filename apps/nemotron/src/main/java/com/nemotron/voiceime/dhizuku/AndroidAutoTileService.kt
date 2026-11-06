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
            if (!AutoAndroidAuto.prepareForMedia(this)) {
                android.util.Log.e(TAG, "Android Auto quedó visible pero no listo para media")
            }
            setLocation(true)
            setBluetooth(true)
            setMobileData(true)
            RootShellManager.setDoubleTapToWake(true)
            RootShellManager.setDoubleTapToSleep(true)
        }.start()
    }

    override fun onAfterFreeze() {
        SecureStore.setAndroidAutoTileOn(this, false)
        Thread {
            // Keep the system-wide location preference unchanged. Turning this
            // tile off must not break Maps, banking apps, or other GPS clients.
            setBluetooth(false)
            setMobileData(false)
            RootShellManager.setDoubleTapToWake(false)
            RootShellManager.setDoubleTapToSleep(false)
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

    /** Enables location for navigation when Android Auto is enabled.
     *  Turning the tile off preserves the user's system-wide location choice. */
    private fun setLocation(enabled: Boolean) {
        if (!RootShellManager.hasPermission()) return
        if (enabled) {
            RootShellManager.execShellFresh(arrayOf("cmd", "location", "set-location-enabled", "true"))
        }
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
