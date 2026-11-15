package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

class UberDriverTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.ubercab.driver"
    override val targetPackages: List<String> = listOf(targetPackage)
    override val tileLabel: String = "Driver"
    override val tileIconRes: Int = R.drawable.ic_driver_tile

    // The API read is performed by the NAS so its Uber client secret and
    // refresh token never live in the APK. These hooks run on the tile worker.
    override fun onAfterUnfreeze() {
        UberSnapshotClient.capture(applicationContext, "enabled")
    }

    override fun onAfterFreeze() {
        UberSnapshotClient.capture(applicationContext, "disabled")
    }
}
