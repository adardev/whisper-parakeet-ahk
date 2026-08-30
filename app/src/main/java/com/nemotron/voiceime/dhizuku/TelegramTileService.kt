package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

class TelegramTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.stably.orca.mobile"
    override val targetPackages: List<String> = listOf(targetPackage)
    override val tileLabel: String = "Orca"
    override val tileIconRes: Int = R.drawable.ic_orca_tile
}
