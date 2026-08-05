package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

/** Tile para congelar/descongelar Android Auto. */
class AndroidAutoTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.google.android.projection.gearhead"
    override val tileLabel: String = "Android Auto"
    override val tileIconRes: Int = R.drawable.ic_auto_tile
}
