package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

/** Tile Orca: toque para activar/desactivar Termux; long-press para conectar por SSH. */
class TelegramTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.termux"
    override val targetPackages: List<String> = listOf(targetPackage)
    override val tileLabel: String = "Orca"
    override val tileIconRes: Int = R.drawable.ic_orca_tile
}
