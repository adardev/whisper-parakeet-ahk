package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

class AirplaneTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        // Re-installing the app can stop the foreground listener while the
        // Quick Settings tile still appears active. Restore it when SystemUI
        // starts listening to this tile again.
        if (SecureStore.isAutoAirplane(this)) {
            AutoFreezeService.start(this)
            Log.d(TAG, "Airplane Lock active → listener restored")
        }
        handler.post { try { updateTileState() } catch (_: Throwable) {} }
    }

    override fun onClick() {
        super.onClick()
        if (ShizukuManager.isAvailable() && !ShizukuManager.hasPermission()) {
            Log.d(TAG, "Requesting Shizuku permission for Airplane Lock")
            ShizukuManager.requestPermission()
            return
        }
        val newEnabled = !SecureStore.isAutoAirplane(this)
        SecureStore.setAutoAirplane(this, newEnabled)

        // The tile controls the automation, not the current system airplane
        // state. Keep the receiver service alive even when no apps are picked.
        if (newEnabled || SecureStore.isAutoFreeze(this)) {
            AutoFreezeService.start(this)
        } else {
            AutoFreezeService.stop(this)
        }
        Log.d(TAG, "Automatic airplane on screen off: $newEnabled")
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val enabled = SecureStore.isAutoAirplane(this)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Airplane Lock"
        tile.subtitle = if (enabled) "On screen off" else "Manual"
        tile.updateTile()
    }

    companion object {
        private const val TAG = "AirplaneTileService"
    }
}
