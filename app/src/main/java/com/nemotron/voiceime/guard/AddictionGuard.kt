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

    fun isEnabled(ctx: Context): Boolean = SecureStore.isAddictionGuardEnabled(ctx)

    fun isServiceNeeded(ctx: Context): Boolean =
        isEnabled(ctx) || SecureStore.isDnsEnforcerEnabled(ctx)

    /** True si se está reproduciendo un Status (activity StatusPlayback en pantalla). */
    fun isWhatsAppStatus(event: AccessibilityEvent): Boolean =
        event.className?.toString()?.contains("StatusPlayback", ignoreCase = true) == true ||
            event.source?.className?.toString()?.contains("StatusPlayback", ignoreCase = true) == true

    /** Bloquea la pantalla y, con Shizuku, también hace force-stop de la app. */
    fun block(service: AccessibilityService, pkg: String) {
        val now = System.currentTimeMillis()
        if (now - (lastBlocked[pkg] ?: 0L) < BLOCK_COOLDOWN_MS) return
        lastBlocked[pkg] = now

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
            DnsEnforcer.startMonitoring(ctx)
        } else {
            DnsEnforcer.stopMonitoring(ctx)
        }
    }

    fun isA11yActive(ctx: Context): Boolean {
        val comp = ComponentName(ctx, AntiScrollAccessibilityService::class.java).flattenToString()
        return Settings.Secure
            .getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.contains(comp) == true
    }

    fun setAccessibilityServiceEnabled(ctx: Context, enabled: Boolean) {
        val comp = ComponentName(ctx, AntiScrollAccessibilityService::class.java).flattenToString()
        val resolver = ctx.contentResolver
        val current = Settings.Secure
            .getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val list = current.split(':').filter { it.isNotBlank() }.toMutableSet()
        val wasEnabled = comp in list
        val remainingServices = list.size - if (wasEnabled) 1 else 0
        val desiredAccessibilityEnabled = enabled ||
            (if (wasEnabled) remainingServices else list.size) > 0
        val accessibilityEnabled = runCatching {
            Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        }.getOrDefault(false)

        // Launcher and keyboard startup can both reach this code. Rewriting an
        // unchanged secure setting can make Samsung reconnect the service.
        if (wasEnabled == enabled && accessibilityEnabled == desiredAccessibilityEnabled) return

        var servicesUpdated = true
        if (wasEnabled != enabled) {
            if (enabled) list.add(comp) else list.remove(comp)
            servicesUpdated = runCatching {
                Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    list.joinToString(":")
                )
            }.getOrDefault(false)
        }
        val flagUpdated = if (accessibilityEnabled == desiredAccessibilityEnabled) true else runCatching {
            Settings.Secure.putInt(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                if (desiredAccessibilityEnabled) 1 else 0
            )
        }.getOrDefault(false)

        if (!servicesUpdated || !flagUpdated) {
            val shellUpdated = ShizukuManager.setAccessibilityServiceEnabled(comp, enabled)
            if (enabled && shellUpdated) {
                ShizukuManager.execShell("settings put secure accessibility_enabled 1")
            }
            if (!shellUpdated) {
                Log.w(TAG, "no se pudo cambiar el servicio de accesibilidad; se requiere permiso Shizuku")
            }
        }
    }

    private const val BLOCK_COOLDOWN_MS = 1_500L
}
