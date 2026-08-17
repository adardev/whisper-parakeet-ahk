package com.nemotron.voiceime.guard

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.dhizuku.ShizukuManager
import com.nemotron.voiceime.dhizuku.DnsEnforcer
import java.util.concurrent.ConcurrentHashMap

/**
 * Bloqueo anti-adicción sin gasto de batería.
 *
 * No hace polls: un AccessibilityService es event-driven, así que solo trabaja
 * cuando hay cambios de UI en Instagram o WhatsApp. En reposo o pantalla
 * apagada no consume nada.
 *
 * - Instagram Reels: se detecta el SeekBar o el desplazamiento del visor → bloquea la pantalla y cierra la app.
 * - Instagram Inicio: al acumular un desplazamiento considerable del feed
 *   principal → bloquea la pantalla y cierra la app. Las demás pestañas no se bloquean.
 * - WhatsApp Status: al abrirse la reproducción de un Status → bloquea la pantalla y cierra la app.
 */
object AddictionGuard {

    private const val TAG = "AddictionGuard"

    const val INSTAGRAM = "com.instagram.android"
    const val WHATSAPP = "com.whatsapp"

    private val lastBlocked = ConcurrentHashMap<String, Long>()

    /** Timestamp (System.currentTimeMillis) hasta el cual se ignora la detección
     *  tras un bloqueo. Evita el bucle infinito cuando Instagram restaura Reels
     *  al reabrir tras force-stop. */
    private val gracePeriodUntil = ConcurrentHashMap<String, Long>()

    /** Último evento (elapsedRealtime) recibido por el servicio de accesibilidad. */
    @Volatile
    var lastEventAt: Long = 0L

    private var healThread: Thread? = null

    fun isEnabled(ctx: Context): Boolean = SecureStore.isAddictionGuardEnabled(ctx)

    fun isServiceNeeded(ctx: Context): Boolean =
        isEnabled(ctx) || SecureStore.isDnsEnforcerEnabled(ctx)

    /** True si se está reproduciendo un Status (activity StatusPlayback en pantalla). */
    fun isWhatsAppStatus(event: AccessibilityEvent): Boolean =
        event.className?.toString()?.contains("StatusPlayback", ignoreCase = true) == true ||
            event.source?.className?.toString()?.contains("StatusPlayback", ignoreCase = true) == true

    /** True si estamos en el período de gracia tras un bloqueo previo. */
    fun isInGracePeriod(pkg: String): Boolean {
        val until = gracePeriodUntil[pkg] ?: return false
        if (System.currentTimeMillis() < until) return true
        gracePeriodUntil.remove(pkg)
        return false
    }

    /** Bloquea la pantalla y, con Shizuku, también hace force-stop de la app. */
    fun block(service: AccessibilityService, pkg: String) {
        val now = System.currentTimeMillis()
        if (now - (lastBlocked[pkg] ?: 0L) < BLOCK_COOLDOWN_MS) return
        lastBlocked[pkg] = now
        gracePeriodUntil[pkg] = now + BLOCK_GRACE_PERIOD_MS

        Log.i(TAG, "Anti-adicción: bloqueando pantalla y cerrando $pkg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        if (ShizukuManager.hasPermission()) {
            Thread { ShizukuManager.stopApp(pkg) }.start()
        }
    }

    // ── Activación del servicio de accesibilidad (WRITE_SECURE_SETTINGS) ──

    fun applyEnabled(ctx: Context) {
        setAccessibilityServiceEnabled(ctx, isServiceNeeded(ctx))
        if (isServiceNeeded(ctx)) {
            startSelfHeal(ctx)
            DnsEnforcer.startMonitoring(ctx)
        } else {
            DnsEnforcer.stopMonitoring(ctx)
        }
    }

    // ── Auto-reparación: One UI deja el servicio "activo pero no enlazado".
    // Sin esto, el guard se muere solo. No afecta la detección del feed. ─────

    fun startSelfHeal(ctx: Context) {
        if (healThread != null) return
        healThread = Thread({ healLoop(ctx) }, "GuardSelfHeal").apply { start() }
    }

    fun stopSelfHeal() {
        healThread?.interrupt()
        healThread = null
    }

    private fun healLoop(ctx: Context) {
        while (!Thread.currentThread().isInterrupted) {
            try {
                Thread.sleep(HEAL_INTERVAL_MS)
                if (!isEnabled(ctx)) return
                // Con pantalla apagada no se puede ver el feed/status: no hace falta reparar.
                if (!isScreenOn(ctx)) continue
                if (!isA11yActive(ctx)) {
                    setAccessibilityServiceEnabled(ctx, true)
                    continue
                }
                val top = topPackage() ?: continue
                if (top != INSTAGRAM && top != WHATSAPP) continue
                val now = android.os.SystemClock.elapsedRealtime()
                val last = lastEventAt
                if (last == 0L || now - last > EVENT_TIMEOUT_MS) {
                    Log.w(TAG, "self-heal: servicio sin eventos con $top → rebind")
                    setAccessibilityServiceEnabled(ctx, false)
                    Thread.sleep(400)
                    setAccessibilityServiceEnabled(ctx, true)
                }
            } catch (_: InterruptedException) {
                return
            } catch (t: Throwable) {
                Log.w(TAG, "self-heal error", t)
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

    fun isScreenOn(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return true
        return pm.isInteractive
    }

    fun isA11yActive(ctx: Context): Boolean {
        val comp = ComponentName(ctx, AntiScrollAccessibilityService::class.java).flattenToString()
        return Settings.Secure
            .getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.contains(comp) == true
    }

    fun setAccessibilityServiceEnabled(ctx: Context, enabled: Boolean) {
        try {
            val resolver = ctx.contentResolver
            val comp = ComponentName(ctx, AntiScrollAccessibilityService::class.java).flattenToString()
            val current = Settings.Secure
                .getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val list = current.split(':').filter { it.isNotBlank() }.toMutableList()
            val removed = list.remove(comp)
            if (enabled && !removed) list.add(comp)
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                list.joinToString(":")
            )
            Settings.Secure.putInt(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                if (list.isNotEmpty()) 1 else 0
            )
        } catch (t: Throwable) {
            Log.w(TAG, "no se pudo cambiar el servicio de accesibilidad", t)
        }
    }

    private const val BLOCK_COOLDOWN_MS = 1_500L
    private const val BLOCK_GRACE_PERIOD_MS = 30_000L
    private const val HEAL_INTERVAL_MS = 20_000L
    private const val EVENT_TIMEOUT_MS = 10_000L
}
