package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

class UberDriverTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.ubercab.driver"
    override val targetPackages: List<String> = listOf(targetPackage)
    override val tileLabel: String = "Driver"
    override val tileIconRes: Int = R.drawable.ic_driver_tile
    override val supportsAtomicPackageToggle: Boolean = false
    private var beforeFreezeWallet: org.json.JSONObject? = null

    // Read only on a confirmed tile transition. Uber's wallet lives inside its
    // single CarbonActivity, so the reader opens that activity and navigates
    // to Billetera before saving the tile event.
    override fun onAfterUnfreeze() {
        val wallet = UberWalletReader.read()
        UberSnapshotClient.capture(applicationContext, "enabled", wallet)
        UberSnapshotClient.flushPending(applicationContext)
        UberWalletReader.returnHome()
    }

    override fun onBeforeFreeze(): Long {
        beforeFreezeWallet = UberWalletReader.read()
        return 0L
    }

    override fun onAfterFreeze() {
        UberSnapshotClient.capture(applicationContext, "disabled", beforeFreezeWallet)
        UberSnapshotClient.flushPending(applicationContext)
        beforeFreezeWallet = null
        UberWalletReader.returnHome()
    }
}
