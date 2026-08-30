package com.nemotron.voiceime.dhizuku

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore

/**
 * Tile para activar/desactivar Airplane Mode al apagar la pantalla.
 * - Tap: activar/desactivar la función
 */
class AirplaneTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private val tileLabel = "Airplane"
    private val tileIconRes = R.drawable.ic_auto_tile

    override fun onStartListening() {
        super.onStartListening()
        handler.post { try { updateTileState() } catch (_: Throwable) {} }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        handler.post { try { updateTileState() } catch (_: Throwable) {} }
    }

    override fun onClick() {
        super.onClick()
        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku not granted")
            return
        }
        val ctx = applicationContext ?: return
        val current = SecureStore.isAirplaneModeEnabled(ctx)
        SecureStore.setAirplaneModeEnabled(ctx, !current)
        Log.d(TAG, "Airplane mode: ${!current}")
        handler.post { try { updateTileState() } catch (_: Throwable) {} }
    }

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        val tile = qsTile ?: return
        val ctx = applicationContext ?: return
        val enabled = SecureStore.isAirplaneModeEnabled(ctx)
        val granted = ShizukuManager.hasPermission()
        tile.label = tileLabel
        tile.icon = Icon.createWithResource(this, tileIconRes)
        tile.state = when {
            !granted -> Tile.STATE_UNAVAILABLE
            enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "AirplaneTile"
    }
}
