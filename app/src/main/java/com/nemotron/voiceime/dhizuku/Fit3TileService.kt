package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

/**
 * Tile para congelar/descongelar Galaxy Fit3 Plugin.
 * Al congelar también fuerza el cierre de Samsung Accessory Service
 * (dueño de la conexión GATT del Fit3) para desconectar SOLO el reloj,
 * sin apagar el Bluetooth.
 */
class Fit3TileService : AppFreezeTileService() {
    override val targetPackage: String = "com.samsung.wearable.fit3plugin"
    override val tileLabel: String = "Fit3"
    override val tileIconRes: Int = R.drawable.ic_fit3_tile

    override fun onAfterFreeze() {
        ShizukuManager.stopApp("com.samsung.accessory")
    }
}
