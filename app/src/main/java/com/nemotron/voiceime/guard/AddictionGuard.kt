package com.nemotron.voiceime.guard

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.dhizuku.ShizukuManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Bloqueo anti-adicción sin gasto de batería.
 *
 * No hace polls: un AccessibilityService es event-driven, así que solo trabaja
 * cuando hay cambios de UI en Instagram o WhatsApp. En reposo o pantalla
 * apagada no consume nada.
 *
 * - Instagram Reels: se detecta el SeekBar del visor de Reels en pantalla
 *   (el feed normal no emite ese tipo de eventos) → cierra la app.
 * - WhatsApp Status: al abrirse la reproducción de un Status → cierra la app.
 */
object AddictionGuard {

    private const val TAG = "AddictionGuard"

    const val INSTAGRAM = "com.instagram.android"
    const val WHATSAPP = "com.whatsapp"

    private val lastBlocked = ConcurrentHashMap<String, Long>()

    fun isEnabled(ctx: Context): Boolean = SecureStore.isAddictionGuardEnabled(ctx)

    /** True si se está reproduciendo un Status (activity StatusPlayback en pantalla). */
    fun isWhatsAppStatus(event: AccessibilityEvent): Boolean =
        event.className?.toString()?.contains("StatusPlayback", ignoreCase = true) == true ||
            event.source?.className?.toString()?.contains("StatusPlayback", ignoreCase = true) == true

    /** Sale de la app: force-stop por Shizuku si está, si no te lleva al home. */
    fun block(service: AccessibilityService, pkg: String) {
        val now = System.currentTimeMillis()
        if (now - (lastBlocked[pkg] ?: 0L) < BLOCK_COOLDOWN_MS) return
        lastBlocked[pkg] = now

        Log.i(TAG, "Anti-adicción: cerrando $pkg")
        if (ShizukuManager.hasPermission()) {
            Thread { ShizukuManager.stopApp(pkg) }.start()
        } else {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    // ── Activación del servicio de accesibilidad (WRITE_SECURE_SETTINGS) ──

    /** Aplica el estado: servicio activo solo si el guard está on y la pantalla encendida. */
    fun applyEnabled(ctx: Context) {
        setAccessibilityServiceEnabled(ctx, isEnabled(ctx) && isScreenOn(ctx))
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
}
