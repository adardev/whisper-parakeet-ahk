package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

/**
 * Tile "Salud" para congelar/descongelar Samsung Health (com.sec.android.app.shealth).
 * Sirve como acceso rapido para pausar/reanudar el registro de salud.
 */
class SHealthTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.sec.android.app.shealth"
    override val tileLabel: String = "Salud"
    override val tileIconRes: Int = R.drawable.ic_fit3_tile
}