package com.nemotron.voiceime.dhizuku

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.nemotron.voiceime.R
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tile para Google Wallet:
 * - Tap: activar/desactivar NFC
 * - Long press: abre Google Wallet (via QS_TILE_PREFERENCES → TilePreferencesActivity)
 * - Sin service en background.
 */
class WalletTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private val operationInProgress = AtomicBoolean(false)
    private val refreshVersion = AtomicInteger(0)

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

        if (!operationInProgress.compareAndSet(false, true)) return
        val requestedEnabled = when (qsTile?.state) {
            Tile.STATE_ACTIVE -> false
            Tile.STATE_INACTIVE -> true
            else -> null
        }
        requestedEnabled?.let(::publishState)

        Thread {
            try {
                val enabled = RootShellManager.isNfcEnabled()
                val desired = requestedEnabled ?: !enabled
                Log.d(TAG, "NFC currently: $enabled; requested=$desired")
                val changed = enabled == desired || RootShellManager.setNfcEnabled(desired)
                if (!changed) Log.e(TAG, "Could not change NFC to $desired")
            } finally {
                operationInProgress.set(false)
                updateTileState()
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        val version = refreshVersion.incrementAndGet()
        Thread {
            val enabled = RootShellManager.isNfcEnabled()
            handler.post {
                if (refreshVersion.get() != version || operationInProgress.get()) return@post
                publishState(enabled)
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun publishState(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.label = "NFC"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_wallet_tile)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        private const val TAG = "WalletTileService"
    }
}
