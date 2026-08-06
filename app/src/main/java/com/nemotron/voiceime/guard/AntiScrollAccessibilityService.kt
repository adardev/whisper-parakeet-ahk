package com.nemotron.voiceime.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad anti-adicción. Event-driven: no hace polls.
 * Recibe eventos SOLO de Instagram y WhatsApp (filtrado por packageNames),
 * así que en reposo y con pantalla apagada no consume batería.
 *
 * Reacción instantánea: al primer evento del visor de Reels (SeekBar) o del
 * Status de WhatsApp se dispara el cierre. El cooldown interno evita spam.
 */
class AntiScrollAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.packageNames = arrayOf(AddictionGuard.INSTAGRAM, AddictionGuard.WHATSAPP)
        serviceInfo = info
        Log.i(TAG, "onServiceConnected eventTypes=${serviceInfo.eventTypes}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!AddictionGuard.isEnabled(this)) return
        val pkg = event.packageName?.toString() ?: return

        // Heartbeat para el auto-reparador (no bloquea nada).
        AddictionGuard.lastEventAt = android.os.SystemClock.elapsedRealtime()

        when (pkg) {
            AddictionGuard.INSTAGRAM -> {
                // El visor de Reels manda SeekBar (progreso de video) al instante.
                if (event.className?.toString()?.contains("SeekBar", ignoreCase = true) == true) {
                    AddictionGuard.block(this, AddictionGuard.INSTAGRAM)
                }
            }
            AddictionGuard.WHATSAPP -> {
                if (AddictionGuard.isWhatsAppStatus(event)) {
                    AddictionGuard.block(this, AddictionGuard.WHATSAPP)
                }
            }
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "AntiScroll"
    }
}
