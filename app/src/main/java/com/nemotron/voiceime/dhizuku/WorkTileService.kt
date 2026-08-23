package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

class WorkTileService : AppFreezeTileService() {
    override val targetPackage: String = WORK_APPS.first()
    override val targetPackages: List<String> = WORK_APPS
    override val tileLabel: String = "Work"
    override val tileIconRes: Int = R.drawable.ic_work_tile

    companion object {
        val WORK_APPS = listOf(
            "com.ceti.escolomos",
            "com.ceti.ingenieriavirtual",
            "md.obsidiao",
            "com.google.android.apps.classroom",
            "com.whatsapp.w4b",
            "proton.android.past"
        )
    }
}
