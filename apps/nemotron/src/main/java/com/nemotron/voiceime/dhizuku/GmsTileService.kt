package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

class GmsTileService : AppFreezeTileService() {
    override val targetPackage: String = BANK_APPS.first()
    override val targetPackages: List<String> = BANK_APPS
    override val tileLabel: String = "Atmos"
    override val tileIconRes: Int = R.drawable.ic_dolby_tile

    override fun onAfterFreeze() {
        RootShellManager.unhideApp(INSTAGRAM_PACKAGE)
    }

    override fun onAfterUnfreeze() {
        RootShellManager.hideApp(INSTAGRAM_PACKAGE)
        RootShellManager.stopApp(INSTAGRAM_PACKAGE)
    }

    companion object {
        val BANK_APPS = listOf(
            "com.citibanamex.banamexmobile",
            "com.didiglobal.cashloan",
            "com.mercadopago.wallet",
            "mx.bancosantander.supermovil",
            "com.nu.production"
        )

        const val INSTAGRAM_PACKAGE = "com.instagram.android"

    }
}
