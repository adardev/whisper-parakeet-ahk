package com.nemotron.voiceime.dhizuku

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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

    /** Override to false when a subclass needs work before freezing packages. */
    open val supportsAtomicPackageToggle: Boolean get() = true

    private val handler = Handler(Looper.getMainLooper())
    private val clickInProgress = AtomicBoolean(false)
    private val tileRefreshInProgress = AtomicBoolean(false)
    private val refreshGeneration = AtomicInteger(0)

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

        if (!clickInProgress.compareAndSet(false, true)) {
            Log.w(TAG, "Ignorado toque duplicado mientras $tileLabel sigue procesando")
            return
        }
        publishOptimisticToggle()

        Thread {
            val actionStartedAt = android.os.SystemClock.elapsedRealtime()
            var fastStatePublished = false
            try {
                if (!RootShellManager.requestPermission()) {
                    Log.w(TAG, "KernelSU root is not authorized")
                    return@Thread
                }
                if (supportsAtomicPackageToggle) {
                    val fast = if (alwaysUnfreezeOnClick) {
                        RootShellManager.setPackagesFrozenFast(targetPackages, frozen = false)
                    } else {
                        RootShellManager.togglePackagesFast(targetPackages)
                    }
                    if (fast != null) {
                        Log.i(TAG, "$tileLabel package action completed in ${android.os.SystemClock.elapsedRealtime() - actionStartedAt}ms in one root batch: frozen=${fast.frozen}, packages=${fast.states.size}")
                        publishTileState(granted = true, hidden = fast.frozen)
                        fastStatePublished = true
                        if (fast.frozen) onAfterFreeze() else onAfterUnfreeze()
                        return@Thread
                    }
                }
                val packageStates = RootShellManager.getPackageStates(targetPackages)
                    ?: throw IllegalStateException("No se pudo consultar el estado de $tileLabel")
                Log.d(TAG, "Currently frozen states: $packageStates")
                val installedPackages = targetPackages.filter { it in packageStates }
                if (installedPackages.isEmpty()) {
                    throw IllegalStateException("No hay paquetes instalados para el tile $tileLabel")
                }
                val anyFrozen = installedPackages.any { pkg ->
                    packageStates[pkg]?.let { it.hidden || it.disabled } == true
                }
                if (anyFrozen || alwaysUnfreezeOnClick) {
                    val fast = RootShellManager.setPackagesFrozenFast(installedPackages, frozen = false)
                    val allEnabled = fast?.let { result ->
                        !result.frozen && installedPackages.all { pkg ->
                            result.states[pkg]?.let { !it.hidden && !it.disabled && it.stopped != true } == true
                        }
                    } ?: installedPackages.all { pkg ->
                        RootShellManager.unhideApp(pkg) && RootShellManager.unstopApp(pkg)
                    }
                    if (allEnabled) {
                        Log.i(TAG, "$tileLabel unfreeze completed in ${android.os.SystemClock.elapsedRealtime() - actionStartedAt}ms")
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
                    val fast = RootShellManager.setPackagesFrozenFast(installedPackages, frozen = true)
                    val allHidden = fast?.let { result ->
                        result.frozen && installedPackages.all { pkg -> result.states[pkg]?.hidden == true }
                    } ?: installedPackages.all { pkg ->
                        val hidden = RootShellManager.hideApp(pkg)
                        val stopped = RootShellManager.stopApp(pkg)
                        Log.i(TAG, "$tileLabel: $pkg hidden=$hidden forceStopped=$stopped")
                        hidden
                    }
                    if (allHidden) {
                        Log.i(TAG, "$tileLabel freeze completed in ${android.os.SystemClock.elapsedRealtime() - actionStartedAt}ms")
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
                if (!fastStatePublished) {
                    handler.post { try { updateTileState() } catch (_: Throwable) {} }
                }
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
        val states = RootShellManager.getPackageStates(targetPackages)
            ?: throw IllegalStateException("No se pudo consultar el estado de $tileLabel")
        return targetPackages.any { pkg -> states[pkg]?.let { it.hidden || it.disabled } == true }
    }

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        if (qsTile == null) return
        // isAppHidden puede tardar hasta el timeout si la shell persistente está
        // roto (KernelSU muerto al conectar al coche/bloquear pantalla). Hacerlo
        // en background evita que el main thread se congele y ANR.
        if (!tileRefreshInProgress.compareAndSet(false, true)) return
        val generation = refreshGeneration.get()
        Thread({
            try {
                val state = runCatching { isTileFrozen() }
                val hidden = state.getOrDefault(false)
                val granted = RootShellManager.hasTilePermission() && state.isSuccess
                handler.post {
                    var refreshAgain = false
                    try {
                        if (refreshGeneration.get() != generation || clickInProgress.get()) {
                            refreshAgain = !clickInProgress.get()
                            return@post
                        }
                        try { onTileState(granted, hidden) } catch (_: Throwable) {}
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
                        if (refreshAgain) handler.post { updateTileState() }
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

    /** Reflect the tap immediately; the final root read corrects it on failure. */
    @SuppressLint("MissingPermission")
    private fun publishOptimisticToggle() {
        refreshGeneration.incrementAndGet()
        if (!RootShellManager.hasTilePermission()) return
        val currentState = qsTile?.state ?: return
        val expectedHidden = when {
            alwaysUnfreezeOnClick -> false
            currentState == Tile.STATE_ACTIVE -> true
            currentState == Tile.STATE_INACTIVE -> false
            else -> return
        }
        handler.post {
            qsTile?.let { tile ->
                tile.label = tileLabel
                tile.icon = createTileIcon()
                tile.state = if (expectedHidden) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                tile.updateTile()
            }
        }
    }

    companion object {
        private const val TAG = "AppFreezeTileService"
    }
}
