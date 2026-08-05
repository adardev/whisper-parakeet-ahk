package com.nemotron.voiceime.dhizuku

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Tile de Quick Settings: freeze/unfreeze toggle.
 * - Tap: congelar/descongelar
 * - Long press: abrir la app
 */
abstract class AppFreezeTileService : TileService() {

    abstract val targetPackage: String
    abstract val tileLabel: String
    abstract val tileIconRes: Int

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
        Log.d(TAG, "onClick: $tileLabel ($targetPackage)")

        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku permission not granted")
            return
        }

        val currentlyFrozen = ShizukuManager.isAppHidden(targetPackage)
        Log.d(TAG, "Currently frozen: $currentlyFrozen")

        Thread {
            if (currentlyFrozen) {
                ShizukuManager.unhideApp(targetPackage)
            } else {
                ShizukuManager.hideApp(targetPackage)
                ShizukuManager.stopApp(targetPackage)
            }
            handler.post { try { updateTileState() } catch (_: Throwable) {} }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.label = tileLabel
        tile.icon = Icon.createWithResource(this, tileIconRes)
        tile.state = if (ShizukuManager.isAppHidden(targetPackage)) {
            Tile.STATE_INACTIVE
        } else {
            Tile.STATE_ACTIVE
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "AppFreezeTileService"
    }
}
