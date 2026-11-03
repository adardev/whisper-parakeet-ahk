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
        AndroidAutoShizukuWatchdogService.start(this)
        Thread {
            grantNotificationListener()
            setLocation(true)
            setBluetooth(true)
            setMobileData(true)
            ShizukuManager.setDoubleTapToWake(true)
            ShizukuManager.setDoubleTapToSleep(true)
        }.start()
    }

    override fun onAfterFreeze() {
        SecureStore.setAndroidAutoTileOn(this, false)
        AndroidAutoShizukuWatchdogService.stop(this)
        Thread {
            setLocation(false)
            setBluetooth(false)
            setMobileData(false)
            ShizukuManager.setDoubleTapToWake(false)
            ShizukuManager.setDoubleTapToSleep(false)
        }.start()
    }

    /**
     * Registra si el tile está "encendido" (Android Auto activo, no congelado).
     * El watchdog de Shizuku usa este estado: solo avisa/revive mientras el tile
     * de Auto esté encendido.
     */
    override fun onTileState(granted: Boolean, hidden: Boolean) {
        // A failed Shizuku binder is not evidence that the tile is off. Quick
        // Settings can refresh this service while Shizuku is restarting; do
        // not erase the last confirmed state, or Nemotron's binder-death
        // watchdog will disarm itself before it can recover Shizuku.
        if (!granted) {
            android.util.Log.d(TAG, "Shizuku unavailable; keeping last confirmed Android Auto tile state")
            return
        }
        val enabled = !hidden
        SecureStore.setAndroidAutoTileOn(this, enabled)
        android.util.Log.d(TAG, "Confirmed Android Auto tile state: enabled=$enabled")
    }

    /**
     * Re-otorga el permiso de NotificationListener a Android Auto.
     * Al descongelar con pm enable, el sistema revoca este permiso.
     * Usa argv directo para que el '$' del nombre del componente no se expanda.
     */
    private fun grantNotificationListener() {
        if (!ShizukuManager.hasPermission()) return
        val component = "$targetPackage/com.google.android.gearhead.notifications.SharedNotificationListenerManager\$ListenerService"
        ShizukuManager.execShellFresh(arrayOf("cmd", "notification", "allow_listener", component))
    }

    /** Activa/desactiva la ubicación según el estado de Android Auto.
     *  3 = alta precisión (on), 0 = off. */
    private fun setLocation(enabled: Boolean) {
        if (!ShizukuManager.hasPermission()) return
        val mode = if (enabled) "3" else "0"
        ShizukuManager.execShellFresh(arrayOf("settings", "put", "secure", "location_mode", mode))
    }

    /** Enciende/apaga Bluetooth según el estado de Android Auto. */
    private fun setBluetooth(enabled: Boolean) {
        if (!ShizukuManager.hasPermission()) return
        val cmd = if (enabled) "enable" else "disable"
        ShizukuManager.execShellFresh(arrayOf("cmd", "bluetooth_manager", cmd))
    }

    /** Enciende/apaga datos móviles según el estado de Android Auto. */
    private fun setMobileData(enabled: Boolean) {
        if (!ShizukuManager.hasPermission()) return
        val cmd = if (enabled) "enable" else "disable"
        ShizukuManager.execShellFresh(arrayOf("svc", "data", cmd))
    }

    companion object {
        private const val TAG = "AndroidAutoTile"
    }
}
