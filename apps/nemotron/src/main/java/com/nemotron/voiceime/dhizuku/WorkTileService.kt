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
                val states = RootShellManager.getPackageStates(targetPackages)
                if (states == null) {
                    Log.w(TAG, "Could not query Work package state")
                    return@Thread
                }
                val installedPackages = targetPackages.filter { it in states }
                if (installedPackages.isEmpty()) {
                    Log.w(TAG, "No Work packages are installed")
                    return@Thread
                }
                val frozen = installedPackages.any { pkg ->
                    states[pkg]?.let { it.hidden || it.disabled } == true
                }
                Log.d(TAG, "Work state: $states")
                if (frozen) {
                    // Older Work tile builds disabled packages. Restore both
                    // hidden and disabled states so the apps are actually usable.
                    installedPackages.forEach { pkg ->
                        if (!RootShellManager.unhideApp(pkg)) {
                            Log.w(TAG, "Could not enable Work app $pkg")
                        }
                    }
                } else {
                    installedPackages.forEach { pkg ->
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
        private const val TAG = "WorkTileService"
    }
}
