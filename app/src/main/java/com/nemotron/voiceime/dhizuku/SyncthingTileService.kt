package com.nemotron.voiceime.dhizuku

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.nemotron.voiceime.R

/**
 * Tile para Syncthing:
 * - Tap: habilitar/deshabilitar (sin abrir)
 * - Long press: abrir la app
 */
class SyncthingTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private val targetPackage = "com.github.catfriend1.syncthingfork"

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
        Log.d(TAG, "onClick: Syncthing")

        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku permission not granted")
            return
        }

        val frozen = ShizukuManager.isAppHidden(targetPackage)
        Log.d(TAG, "Currently frozen: $frozen")

        Thread {
            if (frozen) {
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
        tile.label = "Syncthing"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_syncthing_tile)
        tile.state = if (ShizukuManager.isAppHidden(targetPackage)) {
            Tile.STATE_INACTIVE
        } else {
            Tile.STATE_ACTIVE
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "SyncthingTileService"
    }
}
