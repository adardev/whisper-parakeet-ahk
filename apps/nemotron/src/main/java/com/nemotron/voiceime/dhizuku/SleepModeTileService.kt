package com.nemotron.voiceime.dhizuku

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nemotron.voiceime.R

/** Sleep Mode remains active until the user switches it off. */
class SleepModeTileService : TileService() {
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
        if (SleepModeActionService.isApplying) return
        runCatching { SleepModeActionService.start(this) }.onFailure {
            android.util.Log.e(TAG, "No se pudo iniciar Sleep Mode", it)
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val pending = SleepModeActionService.requestedEnabled
        val enabled = pending ?: SleepModeController.isEnabled(this)
        tile.label = "Sleep Mode"
        tile.subtitle = if (enabled) "Se apaga a las 08:00" else "Activación diaria: 23:00"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_sleep_mode_tile)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (pending != null) tile.subtitle = if (pending) "Activando: enviando ajustes…" else "Desactivando…"
        tile.updateTile()
    }

    private companion object { const val TAG = "SleepModeTile" }
}
