package com.nemotron.voiceime.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad anti-adicción. Event-driven: no hace polls.
 * Recibe eventos SOLO de Instagram y WhatsApp (filtrado por packageNames),
 * así que en reposo y con pantalla apagada no consume batería.
 *
 * En Instagram bloquea al abrir Reels y también cuenta el desplazamiento
 * sostenido del feed de Inicio. Explore y perfiles se ignoran. El cooldown
 * interno evita spam al cerrar.
 */
class AntiScrollAccessibilityService : AccessibilityService() {

    private var homeScrollDistancePx = 0
    private var lastHomeScrollAt = 0L
    private var lastHomeFeedFirstItem = NO_ITEM_INDEX

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED
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
                val isReels = isReelsViewer(event)
                if (isReels || isConsiderableHomeFeedScroll(event)) {
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

    /** El visor de Reels expone un SeekBar con el progreso del video. */
    private fun isReelsViewer(event: AccessibilityEvent): Boolean =
        event.className?.toString()?.contains("SeekBar", ignoreCase = true) == true

    /**
     * Instagram no adjunta el sourceId ni el árbol de la ventana al servicio.
     * El feed de Inicio sí expone un RecyclerView con entre 3 y 7 tarjetas
     * visibles; esa es la firma que recibimos en los eventos reales del teléfono.
     */
    private fun isConsiderableHomeFeedScroll(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return false
        if (event.className?.toString()?.contains("RecyclerView") != true) return false
        val visibleItems = event.toIndex - event.fromIndex
        if (visibleItems !in HOME_FEED_VISIBLE_ITEMS) return false

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastHomeScrollAt > SCROLL_SESSION_GAP_MS) {
            homeScrollDistancePx = 0
            lastHomeFeedFirstItem = NO_ITEM_INDEX
        }
        lastHomeScrollAt = now

        // Algunos RecyclerViews de Instagram mandan scrollDeltaY=0. Como
        // respaldo, medimos cuántas tarjetas cambiaron en el borde superior.
        val pixelDelta = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            kotlin.math.abs(event.scrollDeltaY)
        } else {
            0
        }
        val firstItem = event.fromIndex
        val itemDelta = if (firstItem >= 0 && lastHomeFeedFirstItem >= 0) {
            kotlin.math.abs(firstItem - lastHomeFeedFirstItem) * ESTIMATED_POST_HEIGHT_PX
        } else {
            0
        }
        if (firstItem >= 0) lastHomeFeedFirstItem = firstItem

        val delta = maxOf(pixelDelta, itemDelta)
        if (delta == 0) return false
        homeScrollDistancePx += delta
        if (homeScrollDistancePx < HOME_SCROLL_LIMIT_PX) return false

        homeScrollDistancePx = 0
        return true
    }

    companion object {
        private const val TAG = "AntiScroll"
        private const val HOME_SCROLL_LIMIT_PX = 2_000
        private const val SCROLL_SESSION_GAP_MS = 4_000L
        private const val ESTIMATED_POST_HEIGHT_PX = 700
        private const val NO_ITEM_INDEX = -1
        private val HOME_FEED_VISIBLE_ITEMS = 3..7
    }
}
