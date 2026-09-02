package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

class UberDriverTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.ubercab.driver"
    override val targetPackages: List<String> = listOf(targetPackage)
    override val tileLabel: String = "Driver"
    override val tileIconRes: Int = R.drawable.ic_driver_tile
}
