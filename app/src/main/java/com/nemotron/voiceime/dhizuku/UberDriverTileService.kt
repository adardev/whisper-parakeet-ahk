package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

class UberDriverTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.ubercab.driver"
    override val targetPackages: List<String> = listOf(targetPackage)
    override val tileLabel: String = "Uber"
    override val tileIconRes: Int = R.drawable.ic_uber_tile
}
