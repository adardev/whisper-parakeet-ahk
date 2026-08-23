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
        Log.d(TAG, "onClick: $tileLabel ($targetPackage)")

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

    /** Acciones extra tras congelar (override opcional). */
    open fun onAfterFreeze() {}

    /** Acciones extra tras descongelar (override opcional). */
    open fun onAfterUnfreeze() {}

    /** Genera el icono del tile. Override para iconos custom. */
    open fun createTileIcon(): Icon = Icon.createWithResource(this, tileIconRes)

    /** Hook con el estado calculado del tile (antes de actualizarlo). */
    open fun onTileState(granted: Boolean, hidden: Boolean) {}

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        val tile = qsTile ?: return
        // isAppHidden puede tardar hasta el timeout si el shell persistente está
        // roto (Shizuku muerto al conectar al coche/bloquear pantalla). Hacerlo
        // en background evita que el main thread se congele y ANR.
        Thread {
            val hidden = try { ShizukuManager.isAppHidden(targetPackage) } catch (_: Throwable) { false }
            val granted = ShizukuManager.hasPermission()
            try { onTileState(granted, hidden) } catch (_: Throwable) {}
            handler.post {
                val t = qsTile ?: return@post
                t.label = tileLabel
                t.icon = createTileIcon()
                t.state = when {
                    !granted -> Tile.STATE_UNAVAILABLE
                    hidden -> Tile.STATE_INACTIVE
                    else -> Tile.STATE_ACTIVE
                }
                t.updateTile()
            }
        }.start()
    }

    companion object {
        private const val TAG = "AppFreezeTileService"
    }
}
