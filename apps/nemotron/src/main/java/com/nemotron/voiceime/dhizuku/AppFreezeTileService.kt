package com.nemotron.voiceime.dhizuku

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

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

    /** Algunos tiles son acciones (p. ej. conectar), no toggles de congelación. */
    open val alwaysUnfreezeOnClick: Boolean get() = false

    private val handler = Handler(Looper.getMainLooper())
    private val clickInProgress = AtomicBoolean(false)
    private val tileRefreshInProgress = AtomicBoolean(false)

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
        if (!clickInProgress.compareAndSet(false, true)) {
            Log.w(TAG, "Ignorado toque duplicado mientras $tileLabel sigue procesando")
            return
        }
        handler.post {
            qsTile?.let { tile ->
                tile.state = Tile.STATE_UNAVAILABLE
                tile.updateTile()
            }
        }

        Thread {
            try {
                val disabled = ShizukuManager.getDisabledPackages()
                    ?: throw IllegalStateException("No se pudo consultar el estado de $tileLabel")
                Log.d(TAG, "Currently frozen: $disabled")
                val anyFrozen = targetPackages.any { it in disabled }
                if (anyFrozen || alwaysUnfreezeOnClick) {
                    val allEnabled = targetPackages.all { pkg -> ShizukuManager.unhideApp(pkg) }
                    if (allEnabled) {
                        publishTileState(granted = true, hidden = false)
                        onAfterUnfreeze()
                    } else {
                        Log.w(TAG, "$tileLabel: no se ejecutan acciones posteriores porque no se confirmó la activación de todos los paquetes")
                    }
                } else {
                    // Sincronizar antes de congelar el proveedor que necesita el
                    // hook (p. ej. Samsung Health).
                    val beforeFreezeDelayMs = try {
                        onBeforeFreeze()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Falló la acción previa a congelar $tileLabel", t)
                        -1L
                    }
                    if (beforeFreezeDelayMs < 0) {
                        Log.w(TAG, "$tileLabel permanece activo porque no terminó la acción previa")
                        return@Thread
                    }
                    if (beforeFreezeDelayMs > 0) Thread.sleep(beforeFreezeDelayMs)
                    var allHidden = true
                    for (pkg in targetPackages) {
                        val hidden = ShizukuManager.hideApp(pkg)
                        allHidden = allHidden && hidden
                        val stopped = ShizukuManager.stopApp(pkg)
                        Log.i(TAG, "$tileLabel: $pkg disabled=$hidden forceStopped=$stopped")
                    }
                    if (allHidden) {
                        publishTileState(granted = true, hidden = true)
                        onAfterFreeze()
                    } else {
                        Log.w(TAG, "$tileLabel: no se ejecutan acciones posteriores porque no se confirmó el apagado")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Falló el cambio de estado de $tileLabel", t)
            } finally {
                clickInProgress.set(false)
                handler.post { try { updateTileState() } catch (_: Throwable) {} }
            }
        }.start()
    }

    /** Acciones extra tras congelar (override opcional). */
    open fun onAfterFreeze() {}

    /**
     * Acciones antes de congelar. Devuelve milisegundos para esperar antes
     * de ocultar las apps; sirve para terminar una sincronización.
     */
    open fun onBeforeFreeze(): Long = 0L

    /** Acciones extra tras descongelar (override opcional). */
    open fun onAfterUnfreeze() {}

    /** Genera el icono del tile. Override para iconos custom. */
    open fun createTileIcon(): Icon = Icon.createWithResource(this, tileIconRes)

    /** Hook con el estado calculado del tile (antes de actualizarlo). */
    open fun onTileState(granted: Boolean, hidden: Boolean) {}

    /** Override when a tile controls a group of packages rather than one app. */
    open fun isTileFrozen(): Boolean {
        val disabled = ShizukuManager.getDisabledPackages()
            ?: throw IllegalStateException("No se pudo consultar el estado de $tileLabel")
        return targetPackages.any { it in disabled }
    }

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        if (qsTile == null) return
        // isAppHidden puede tardar hasta el timeout si el shell persistente está
        // roto (Shizuku muerto al conectar al coche/bloquear pantalla). Hacerlo
        // en background evita que el main thread se congele y ANR.
        if (!tileRefreshInProgress.compareAndSet(false, true)) return
        Thread({
            try {
                val hidden = try { isTileFrozen() } catch (_: Throwable) { false }
                val granted = ShizukuManager.hasPermission()
                try { onTileState(granted, hidden) } catch (_: Throwable) {}
                handler.post {
                    try {
                        val t = qsTile ?: return@post
                        t.label = tileLabel
                        t.icon = createTileIcon()
                        t.state = when {
                            !granted -> Tile.STATE_UNAVAILABLE
                            hidden -> Tile.STATE_INACTIVE
                            else -> Tile.STATE_ACTIVE
                        }
                        t.updateTile()
                    } finally {
                        tileRefreshInProgress.set(false)
                    }
                }
            } catch (t: Throwable) {
                tileRefreshInProgress.set(false)
                Log.w(TAG, "$tileLabel: no se pudo actualizar el tile", t)
            }
        }, "Nemotron-tile-$tileLabel").start()
    }

    private fun refreshTileStateSoon() {
        handler.post { updateTileState() }
    }

    private fun publishTileState(granted: Boolean, hidden: Boolean) {
        handler.post {
            try { onTileState(granted, hidden) } catch (_: Throwable) {}
            val tile = qsTile ?: return@post
            tile.label = tileLabel
            tile.icon = createTileIcon()
            tile.state = when {
                !granted -> Tile.STATE_UNAVAILABLE
                hidden -> Tile.STATE_INACTIVE
                else -> Tile.STATE_ACTIVE
            }
            tile.updateTile()
        }
    }

    companion object {
        private const val TAG = "AppFreezeTileService"
    }
}
