package com.nemotron.voiceime.dhizuku

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.nemotron.voiceime.R

/**
 * Tile para Google Wallet:
 * - Tap: activar/desactivar NFC
 * - Long press: abre Google Wallet (via QS_TILE_PREFERENCES → TilePreferencesActivity)
 * - Sin service en background.
 */
class WalletTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

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
        Log.d(TAG, "onClick: toggle NFC")

        if (!RootShellManager.hasPermission()) {
            Log.w(TAG, "KernelSU permission not granted")
            return
        }

        val enabled = RootShellManager.isNfcEnabled()
        Log.d(TAG, "NFC currently: $enabled")

        Thread {
            val changed = RootShellManager.setNfcEnabled(!enabled)
            if (!changed) Log.e(TAG, "Could not change NFC to ${!enabled}")
            handler.post { try { updateTileState() } catch (_: Throwable) {} }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.label = "NFC"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_wallet_tile)
        tile.state = if (RootShellManager.isNfcEnabled()) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "WalletTileService"
    }
}
