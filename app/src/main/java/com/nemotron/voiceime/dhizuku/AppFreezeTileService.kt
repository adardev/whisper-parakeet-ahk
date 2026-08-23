package com.nemotron.voiceime.dhizuku

import android.annotation.SuppressLint
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
 *
 * updateTileState NO ejecuta shell commands para evitar que Samsung
 * se confunda al escanear tiles en el QS edit (Add a control).
 */
abstract class AppFreezeTileService : TileService() {

    abstract val targetPackage: String
    abstract val tileLabel: String
    abstract val tileIconRes: Int

    open val targetPackages: List<String> get() = listOf(targetPackage)

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
        Log.d(TAG, "onClick: $tileLabel")

        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku permission not granted")
            return
        }

        Thread {
            val disabled = try {
                ShizukuManager.stillDisabled(targetPackages)
            } catch (_: Throwable) { emptySet<String>() }
            Log.d(TAG, "Currently frozen: $disabled")
            val anyFrozen = disabled.isNotEmpty()
            if (anyFrozen) {
                for (pkg in targetPackages) ShizukuManager.unhideApp(pkg)
                onAfterUnfreeze()
            } else {
                for (pkg in targetPackages) {
                    ShizukuManager.hideApp(pkg)
                    ShizukuManager.stopApp(pkg)
                }
                onAfterFreeze()
            }
            handler.post { try { updateTileState() } catch (_: Throwable) {} }
        }.start()
    }

    open fun onAfterFreeze() {}
    open fun onAfterUnfreeze() {}
    open fun createTileIcon(): Icon = Icon.createWithResource(this, tileIconRes)
    open fun onTileState(granted: Boolean, hidden: Boolean) {}

    /** NO ejecuta shell — solo muestra estado por defecto para no romper QS edit. */
    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        val tile = qsTile ?: return
        val granted = ShizukuManager.hasPermission()
        handler.post {
            val t = qsTile ?: return@post
            t.label = tileLabel
            t.icon = createTileIcon()
            t.state = if (granted) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
            t.updateTile()
        }
    }

    companion object {
        private const val TAG = "AppFreezeTileService"
    }
}
