package com.nemotron.voiceime.dhizuku

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.nemotron.voiceime.R

/** Tile Orca: abre Termux y ejecuta la conexión SSH del relay. */
class TelegramTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra(
                    "com.termux.RUN_COMMAND_PATH",
                    "/data/data/com.termux/files/usr/bin/bash"
                )
                putExtra(
                    "com.termux.RUN_COMMAND_ARGUMENTS",
                    arrayOf("-lc", "exec ssh -J serveo.net adaredu@adardev-orca-20260928")
                )
                putExtra(
                    "com.termux.RUN_COMMAND_WORKDIR",
                    "/data/data/com.termux/files/home"
                )
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                // Termux espera este extra como String, no como Int.
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            }
            startService(intent)
        } catch (error: Throwable) {
            Log.e(TAG, "No se pudo ejecutar Orca en Termux", error)
            Toast.makeText(this, "Activa el permiso de Termux para Nemotron", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            label = "Orca"
            icon = Icon.createWithResource(this@TelegramTileService, R.drawable.ic_orca_tile)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    companion object {
        private const val TAG = "OrcaTileService"
    }
}
