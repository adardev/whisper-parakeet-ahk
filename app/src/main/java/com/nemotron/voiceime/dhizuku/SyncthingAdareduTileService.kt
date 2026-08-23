package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

class SyncthingAdareduTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.github.catfriend1.syncthingforl"
    override val targetPackages: List<String> = listOf(targetPackage)
    override val tileLabel: String = "Sincthing"
    override val tileIconRes: Int = R.drawable.ic_syncthing_tile
}
