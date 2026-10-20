package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

class GmsTileService : AppFreezeTileService() {
    override val targetPackage: String = BANK_APPS.first()
    override val targetPackages: List<String> = BANK_APPS
    override val tileLabel: String = "Atmos"
    override val tileIconRes: Int = R.drawable.ic_dolby_tile

    override fun onAfterFreeze() {
        ShizukuManager.setAccessibilityServiceEnabled(guardService(this), enabled = true)
        ShizukuManager.unhideApp(INSTAGRAM_PACKAGE)
    }

    override fun onAfterUnfreeze() {
        ShizukuManager.setAccessibilityServiceEnabled(guardService(this), enabled = false)
        ShizukuManager.hideApp(INSTAGRAM_PACKAGE)
        ShizukuManager.stopApp(INSTAGRAM_PACKAGE)
    }

    companion object {
        val BANK_APPS = listOf(
            "com.citibanamex.banamexmobile",
            "org.microemu.android.model.common.VTUserApplicationBNRTMB",
            "com.didiglobal.cashloan",
            "com.mercadopago.wallet",
            "com.revolut.revolut",
            "mx.bancosantander.supermovil",
            "com.nu.production",
            "mx.com.bankaya.products.uberprocard"
        )

        const val INSTAGRAM_PACKAGE = "com.instagram.android"

    }

    private fun guardService(context: android.content.Context): String =
        "${context.packageName}/com.nemotron.voiceime.guard.AntiScrollAccessibilityService"
}
