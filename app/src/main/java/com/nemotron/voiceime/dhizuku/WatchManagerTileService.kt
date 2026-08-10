package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

/** Tile para congelar/descongelar Galaxy Wearable. */
class WatchManagerTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.samsung.android.app.watchmanager"
    override val tileLabel: String = "Galaxy Wearable"
    override val tileIconRes: Int = R.drawable.ic_watch_tile
}
