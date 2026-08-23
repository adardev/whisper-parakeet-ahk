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
 * Tile para Syncthing (Fork y normal):
 * - Tap: habilitar/deshabilitar ambas apps (sin abrir)
 * - Long press: abrir las apps
 */
class SyncthingTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private val targetPackages = listOf(
        "com.github.catfriend1.syncthingfork"
    )

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

        val frozen = targetPackages.any { ShizukuManager.isAppHidden(it) }
        Log.d(TAG, "Currently frozen: $frozen")

        Thread {
            targetPackages.forEach { pkg ->
                if (frozen) {
                    ShizukuManager.unhideApp(pkg)
                } else {
                    ShizukuManager.hideApp(pkg)
                    ShizukuManager.stopApp(pkg)
                }
            }
            handler.post { try { updateTileState() } catch (_: Throwable) {} }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.label = "Syncthing"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_syncthing_tile)
        tile.state = if (targetPackages.any { ShizukuManager.isAppHidden(it) }) {
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
