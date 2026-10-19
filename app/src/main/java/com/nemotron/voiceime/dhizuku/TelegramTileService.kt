package com.nemotron.voiceime.dhizuku

import android.util.Log
import com.nemotron.voiceime.R

/** Tile Orca: toque para activar/desactivar Termux; long-press para conectar por SSH. */
class TelegramTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.termux"
    override val targetPackages: List<String> = listOf(targetPackage)
    override val tileLabel: String = "Orca"
    override val tileIconRes: Int = R.drawable.ic_orca_tile

    /** Prepare Termux for the long-press SSH action without opening its UI. */
    override fun onAfterUnfreeze() {
        val result = ShizukuManager.execShellFresh(
            arrayOf("pm", "grant", packageName, TERMUX_RUN_COMMAND_PERMISSION)
        )
        val state = ShizukuManager.execShellFresh(arrayOf("dumpsys", "package", packageName))
        if (state?.contains("$TERMUX_RUN_COMMAND_PERMISSION: granted=true") == true) {
            Log.i(TAG, "Termux enabled; RUN_COMMAND permission is ready")
        } else {
            Log.w(TAG, "Termux enabled but RUN_COMMAND permission was not granted: ${result.orEmpty()}")
        }
    }

    private companion object {
        const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        const val TAG = "OrcaTile"
    }
}
