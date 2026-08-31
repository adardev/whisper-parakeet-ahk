package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore

/** Tile para congelar/descongelar Android Auto. */
class AndroidAutoTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.google.android.projection.gearhead"
    override val tileLabel: String = "Auto"
    override val tileIconRes: Int = R.drawable.ic_auto_tile

    override fun onAfterUnfreeze() {
        Thread {
            grantNotificationListener()
            setLocation(true)
        }.start()
    }

    override fun onAfterFreeze() {
        Thread {
            setLocation(false)
        }.start()
    }

    /**
     * Registra si el tile está "encendido" (Android Auto activo, no congelado).
     * El watchdog de Shizuku usa este estado: solo avisa/revive mientras el tile
     * de Auto esté encendido.
     */
    override fun onTileState(granted: Boolean, hidden: Boolean) {
        SecureStore.setAndroidAutoTileOn(this, granted && !hidden)
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

    companion object {
        private const val TAG = "AndroidAutoTile"
    }
}
