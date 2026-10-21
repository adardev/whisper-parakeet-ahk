package com.nemotron.voiceime.dhizuku

import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nemotron.voiceime.R

/** Sleep Mode remains active until the user switches it off. */
class SleepModeTileService : TileService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        tile.updateTile()
        Thread {
            val result = SleepModeController.toggle(applicationContext)
            handler.post {
                refreshTile()
                if (!result.success) android.util.Log.w(TAG, result.message)
            }
        }.start()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val enabled = SleepModeController.isEnabled(this)
        tile.label = "Sleep Mode"
        tile.subtitle = if (enabled) "Activo hasta apagarlo" else "Activación diaria: 23:00"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_sleep_mode_tile)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private companion object { const val TAG = "SleepModeTile" }
}
