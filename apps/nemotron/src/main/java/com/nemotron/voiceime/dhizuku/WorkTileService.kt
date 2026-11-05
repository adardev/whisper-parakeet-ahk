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
 * Tile "Work" para apps de trabajo:
 * - Tap: congelar/descongelar todas juntas
 * - Long press: abrir las apps
 */
class WorkTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private val targetPackages = listOf(
        "com.ceti.escolomos",
        "com.ceti.ingenieriavirtual",
        "md.obsidiao",
        "com.google.android.apps.classroom",
        "com.whatsapp.w4b",
        "proton.android.past",
        "com.google.android.apps.docs",
        "com.microsoft.teams"
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
        Log.d(TAG, "onClick: Work")

        if (!RootShellManager.hasPermission()) {
            Log.w(TAG, "KernelSU permission not granted")
            return
        }

        Thread {
            try {
                val hidden = RootShellManager.getHiddenPackages(targetPackages)
                if (hidden == null) {
                    Log.w(TAG, "Could not query hidden package state")
                    return@Thread
                }
                val frozen = targetPackages.any { it in hidden }
                Log.d(TAG, "Currently hidden: $hidden")
                targetPackages.forEach { pkg ->
                    if (frozen && pkg in hidden) {
                        RootShellManager.unhideApp(pkg)
                    } else if (!frozen && pkg !in hidden) {
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
        tile.label = "Work"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_work_tile)
        tile.state = Tile.STATE_UNAVAILABLE
        tile.updateTile()
        Thread {
            val hidden = RootShellManager.getHiddenPackages(targetPackages)
            handler.post {
                val current = qsTile ?: return@post
                current.state = when {
                    hidden == null -> Tile.STATE_UNAVAILABLE
                    targetPackages.any { it in hidden } -> Tile.STATE_INACTIVE
                    else -> Tile.STATE_ACTIVE
                }
                current.updateTile()
            }
        }.start()
    }

    companion object {
        private const val TAG = "WorkTileService"
    }
}
