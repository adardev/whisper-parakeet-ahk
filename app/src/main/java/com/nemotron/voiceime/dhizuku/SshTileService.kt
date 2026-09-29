package com.nemotron.voiceime.dhizuku

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.nemotron.voiceime.R

/** Quick Settings tile que abre Termux y ejecuta la conexión SSH del relay. */
class SshTileService : TileService() {

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
            val command = "exec ssh -J serveo.net adaredu@adardev-orca-20260928"
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                component = ComponentName(
                    "com.termux",
                    "com.termux.app.RunCommandService"
                )
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", 0)
            }
            startService(intent)
        } catch (error: Throwable) {
            Toast.makeText(this, "No se pudo abrir Termux: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            label = "SSH"
            icon = Icon.createWithResource(this@SshTileService, R.drawable.ic_ssh_tile)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
