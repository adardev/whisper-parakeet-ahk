package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore

/** Tile para congelar/descongelar Android Auto. */
class AndroidAutoTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.google.android.projection.gearhead"
    override val tileLabel: String = "Auto"
    override val tileIconRes: Int = R.drawable.ic_auto_tile

    /**
     * Registra si el tile está "encendido" (Android Auto activo, no congelado).
     * El watchdog de Shizuku usa este estado: solo avisa/revive mientras el tile
     * de Auto esté encendido.
     */
    override fun onTileState(granted: Boolean, hidden: Boolean) {
        SecureStore.setAndroidAutoTileOn(this, granted && !hidden)
    }
}
