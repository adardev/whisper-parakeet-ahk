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

        if (!RootShellManager.hasPermission()) {
            Log.w(TAG, "KernelSU permission not granted")
            return
        }

        Thread {
            try {
                val states = RootShellManager.getPackageStates(targetPackages)
                if (states == null) {
                    Log.w(TAG, "Could not query package state")
                    return@Thread
                }
                val installedPackages = targetPackages.filter { it in states }
                if (installedPackages.isEmpty()) {
                    Log.w(TAG, "Syncthing package is not installed")
                    return@Thread
                }
                val frozen = installedPackages.any { pkg ->
                    states[pkg]?.let { it.hidden || it.disabled } == true
                }
                Log.d(TAG, "Currently frozen states: $states")
                installedPackages.forEach { pkg ->
                    if (frozen) {
                        check(RootShellManager.unhideApp(pkg)) { "Could not restore $pkg" }
                    } else {
                        RootShellManager.hideApp(pkg)
                        RootShellManager.stopApp(pkg)
                    }
                }
            } finally {
                handler.post { try { updateTileState() } catch (_: Throwable) {} }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.label = "Syncthing"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_syncthing_tile)
        // Keep the last rendered state while the root query runs. Marking the
        // tile unavailable on every panel open causes a visible gray flash.
        Thread {
            val states = RootShellManager.getPackageStates(targetPackages)
            handler.post {
                val current = qsTile ?: return@post
                current.state = when {
                    states == null -> Tile.STATE_UNAVAILABLE
                    targetPackages.any { pkg -> states[pkg]?.let { it.hidden || it.disabled } == true } -> Tile.STATE_INACTIVE
                    else -> Tile.STATE_ACTIVE
                }
                current.updateTile()
            }
        }.start()
    }

    companion object {
        private const val TAG = "SyncthingTileService"
    }
}
