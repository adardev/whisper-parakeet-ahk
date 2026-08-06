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

        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku permission not granted")
            return
        }

        val enabled = ShizukuManager.isNfcEnabled()
        Log.d(TAG, "NFC currently: $enabled")

        Thread {
            ShizukuManager.setNfcEnabled(!enabled)
            Thread.sleep(300)
            handler.post { try { updateTileState() } catch (_: Throwable) {} }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.label = "NFC"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_wallet_tile)
        tile.state = if (ShizukuManager.isNfcEnabled()) {
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
