package com.nemotron.voiceime.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad anti-adicción. Siempre activo mientras el guard
 * esté ON, pero event-driven: solo recibe eventos de Instagram/WhatsApp
 * (packageNames), así que en reposo consume cero batería.
 *
 * Detección de Reels (sin depender de que el video esté reproduciendo):
 * - SeekBar (progreso de video) → Reels/feed fullscreen reproduciendo.
 * - ViewPager sostenido (2 eventos en <2.5s) → visor de Reels abierto.
 * WhatsApp: al abrirse StatusPlayback → force-stop.
 */
class AntiScrollAccessibilityService : AccessibilityService() {

    private var lastVP = 0L

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
        val cls = event.className?.toString().orEmpty()

        val now = SystemClock.elapsedRealtime()
        AddictionGuard.lastEventAt = now
        if (now - lastLog > 1_500L) {
            lastLog = now
            Log.i(TAG, "EV pkg=$pkg cls=$cls")
        }

        when (pkg) {
            AddictionGuard.INSTAGRAM -> {
                val isReels = cls.contains("SeekBar", ignoreCase = true) ||
                    (cls.contains("ViewPager", ignoreCase = true) && viewPagerBurst(now))
                if (isReels) AddictionGuard.block(this, AddictionGuard.INSTAGRAM)
            }
            AddictionGuard.WHATSAPP -> {
                if (AddictionGuard.isWhatsAppStatus(event)) {
                    AddictionGuard.block(this, AddictionGuard.WHATSAPP)
                }
            }
        }
    }

    /** True si hay un segundo evento de ViewPager en <2.5s (visor de Reels activo). */
    private fun viewPagerBurst(now: Long): Boolean {
        if (now - lastVP < VP_BURST_MS) {
            lastVP = 0
            return true
        }
        lastVP = now
        return false
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "AntiScroll"
        private const val VP_BURST_MS = 2_500L
        private var lastLog = 0L
    }
}
