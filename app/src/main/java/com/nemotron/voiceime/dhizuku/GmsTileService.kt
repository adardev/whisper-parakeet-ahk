package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

class GmsTileService : AppFreezeTileService() {
    override val targetPackage: String = BANK_APPS.first()
    override val targetPackages: List<String> = BANK_APPS
    override val tileLabel: String = "GMS"
    override val tileIconRes: Int = R.drawable.ic_gms_tile

    override fun onAfterFreeze() {
        ShizukuManager.setAccessibilityServiceEnabled(GUARD_SERVICE, enabled = true)
    }

    override fun onAfterUnfreeze() {
        ShizukuManager.setAccessibilityServiceEnabled(GUARD_SERVICE, enabled = false)
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

        private const val GUARD_SERVICE =
            "com.nemotron.voiceime2/com.nemotron.voiceime.guard.AntiScrollAccessibilityService"
    }
}
