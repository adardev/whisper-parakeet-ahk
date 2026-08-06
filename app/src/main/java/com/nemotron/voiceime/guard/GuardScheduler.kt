package com.nemotron.voiceime.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.nemotron.voiceime.dhizuku.ShizukuManager

/**
 * Mantiene el servicio de accesibilidad activo SOLO mientras Instagram o
 * WhatsApp están en primer plano (y la pantalla encendida). Con pantalla
 * apagada o usando otras apps, el servicio está desactivado del todo.
 *
 * Para saber cuándo se abre IG/WA sin un servicio siempre-encendido, un
 * watcher ligero consulta la app en primer plano (dumpsys window) cada 2s
 * SOLO con pantalla encendida. Es el único mecanismo posible sin polling
 * permanente.
 */
object GuardScheduler {

    private const val TAG = "GuardScheduler"
    private const val POLL_MS = 2_000L
    private const val REBIND_GRACE_MS = 3_000L

    private var receiver: GuardScreenReceiver? = null
    private var watcher: Thread? = null
    @Volatile private var running = false
    private var lastDebug = 0L

    fun start(ctx: Context) {
        if (receiver == null) {
            val r = GuardScreenReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ctx.applicationContext.registerReceiver(r, filter)
            receiver = r
            Log.d(TAG, "screen gate registrado")
        }
        if (AddictionGuard.isScreenOn(ctx)) startWatcher(ctx)
    }

    fun stop(ctx: Context) {
        stopWatcher()
        val r = receiver ?: return
        try {
            ctx.applicationContext.unregisterReceiver(r)
        } catch (_: Throwable) {}
        receiver = null
        AddictionGuard.setAccessibilityServiceEnabled(ctx, false)
        Log.d(TAG, "guard detenido, accesibilidad desactivada")
    }

    /** Con pantalla apagada: para el watcher y desactiva el servicio. */
    fun onScreenOff(ctx: Context) {
        stopWatcher()
        AddictionGuard.setAccessibilityServiceEnabled(ctx, false)
        Log.d(TAG, "screen off → accesibilidad desactivada")
    }

    /** Con pantalla encendida: vigila si se abre IG/WhatsApp. */
    fun onScreenOn(ctx: Context) {
        startWatcher(ctx)
        Log.d(TAG, "screen on → vigilando primer plano")
    }

    private fun startWatcher(ctx: Context) {
        if (running) return
        running = true
        watcher = Thread({ loop(ctx) }, "GuardWatcher").apply { start() }
    }

    private fun stopWatcher() {
        running = false
        watcher?.interrupt()
        watcher = null
    }

    private fun loop(ctx: Context) {
        var fgSince = 0L
        while (running && !Thread.currentThread().isInterrupted) {
            try {
                if (!AddictionGuard.isEnabled(ctx)) {
                    stopWatcher()
                    AddictionGuard.setAccessibilityServiceEnabled(ctx, false)
                    return
                }
                val top = topPackage()
                if (top == null) {
                    // No se pudo determinar el primer plano (transición de ventana,
                    // shell ocupado). Nunca desactivar por desconocido: se mantiene el estado.
                    Thread.sleep(POLL_MS)
                    continue
                }
                val desired = top == AddictionGuard.INSTAGRAM || top == AddictionGuard.WHATSAPP
                val active = AddictionGuard.isA11yActive(ctx)
                val now = android.os.SystemClock.elapsedRealtime()
                if (desired) {
                    if (!active) {
                        Log.d(TAG, "$top en primer plano → activando accesibilidad")
                        AddictionGuard.setAccessibilityServiceEnabled(ctx, true)
                        fgSince = now
                    } else {
                        if (fgSince == 0L) fgSince = now
                        // Watchdog: si IG/WA llevan rato en primer plano y el servicio
                        // no ha recibido NINGÚN evento (bug del ROM que lo deja colgado),
                        // se fuerza un rebind. Si ya llegaron eventos, funciona bien.
                        val last = AddictionGuard.lastEventAt
                        val noEventSinceFg = last == 0L || last < fgSince
                        if (now - fgSince > REBIND_GRACE_MS && noEventSinceFg) {
                            Log.w(TAG, "sin eventos desde que abrió IG (last=$last) → rebind")
                            AddictionGuard.setAccessibilityServiceEnabled(ctx, false)
                            Thread.sleep(400)
                            AddictionGuard.setAccessibilityServiceEnabled(ctx, true)
                            fgSince = now
                        }
                    }
                } else {
                    fgSince = 0L
                    if (active) {
                        Log.d(TAG, "$top en primer plano → desactivando accesibilidad")
                        AddictionGuard.setAccessibilityServiceEnabled(ctx, false)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "watcher error", t)
            }
            try {
                Thread.sleep(POLL_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun topPackage(): String? {
        if (!ShizukuManager.hasPermission()) return null
        val line = ShizukuManager.execShellCapture("dumpsys window | grep -m1 mCurrentFocus") ?: return null
        if (line.isBlank()) return null
        val m = Regex("mCurrentFocus=Window\\{[^}]*\\s([^\\s}]+)").find(line) ?: return null
        val comp = m.groupValues[1]
        val slash = comp.indexOf('/')
        return if (slash > 0) comp.substring(0, slash) else null
    }
}

class GuardScreenReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!AddictionGuard.isEnabled(ctx)) return
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> GuardScheduler.onScreenOff(ctx)
            Intent.ACTION_SCREEN_ON -> GuardScheduler.onScreenOn(ctx)
        }
    }
}
