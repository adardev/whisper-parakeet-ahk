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
     */
    private fun grantNotificationListener() {
        if (!ShizukuManager.hasPermission()) return
        val component = "$targetPackage/com.google.android.gearhead.notifications.SharedNotificationListenerManager\$ListenerService"
        ShizukuManager.execShell("cmd notification allow_listener $component")
    }

    companion object {
        private const val TAG = "AndroidAutoTile"
    }
}
