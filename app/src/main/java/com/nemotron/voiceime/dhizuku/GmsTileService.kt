package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

/** Tile para congelar/descongelar Google Play Services (GMS). */
class GmsTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.google.android.gms"
    override val tileLabel: String = "GMS"
    override val tileIconRes: Int = R.drawable.ic_gms_tile
}
