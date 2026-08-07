package com.nemotron.voiceime.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nemotron.voiceime.dhizuku.ShizukuManager

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
    private var searchScrollDistancePx = 0
    private var lastSearchScrollAt = 0L
    @Volatile private var isInstagramMainTab = false
    @Volatile private var isInstagramConversationSurface = false
    @Volatile private var lastInstagramActivityCheckAt = 0L
    @Volatile private var selectedInstagramTab = TAB_UNKNOWN

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED or
            AccessibilityEvent.TYPE_VIEW_CLICKED or
            AccessibilityEvent.TYPE_VIEW_SELECTED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        // Agrupa ráfagas de cambios visuales sin afectar los gestos largos.
        info.notificationTimeout = EVENT_COALESCE_MS
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
                refreshInstagramScreenIfNeeded(event)
                updateInstagramTabFromClick(event)
                val isReels = isReelsViewer(event)
                if ((isReels && shouldBlockReels()) ||
                    (selectedInstagramTab == TAB_HOME && isConsiderableHomeFeedScroll(event)) ||
                    (selectedInstagramTab == TAB_SEARCH && isConsiderableSearchScroll(event))) {
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

    /**
     * Solo se consulta la actividad cuando cambia de ventana, nunca en cada
     * scroll. DirectThreadActivity desarma el contador y evita bloquear chats.
     */
    private fun refreshInstagramScreenIfNeeded(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val windowClass = event.className?.toString().orEmpty()
        // Los Reels compartidos por DM viven en ModalActivity, no en el visor
        // principal. Se desarma sin esperar a la consulta por Shizuku.
        if (windowClass.contains("com.instagram.modal.") || windowClass.contains("Direct")) {
            isInstagramConversationSurface = true
            isInstagramMainTab = false
            selectedInstagramTab = TAB_OTHER
            homeScrollDistancePx = 0
            lastHomeFeedFirstItem = NO_ITEM_INDEX
            return
        }
        if (windowClass.contains(".activity.MainTabActivity")) {
            isInstagramConversationSurface = false
            isInstagramMainTab = true
            selectedInstagramTab = TAB_UNKNOWN
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastInstagramActivityCheckAt < ACTIVITY_CHECK_GAP_MS) return
        lastInstagramActivityCheckAt = now
        if (!ShizukuManager.hasPermission()) return
        Thread {
            val top = ShizukuManager.execShellCapture(
                "dumpsys activity activities | grep -m1 topResumedActivity"
            ) ?: return@Thread
            isInstagramMainTab = top.contains("com.instagram.android/.activity.MainTabActivity")
            isInstagramConversationSurface = top.contains("com.instagram.modal.") ||
                top.contains("Direct")
            if (!isInstagramMainTab) {
                homeScrollDistancePx = 0
                lastHomeFeedFirstItem = NO_ITEM_INDEX
            }
        }.start()
    }

    /** El toque de la barra inferior identifica la pestaña sin consultar la UI. */
    private fun updateInstagramTabFromClick(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED) return
        Log.i(TAG, "Instagram tab event=${event.eventType} desc=${event.contentDescription}")
        when (event.contentDescription?.toString()) {
            "Home" -> selectedInstagramTab = TAB_HOME
            "Search and explore" -> selectedInstagramTab = TAB_SEARCH
            "Reels", "Message", "Profile" -> selectedInstagramTab = TAB_OTHER
        }
    }

    /** Se ejecuta solo cuando aparece un Reel, nunca en el scroll normal. */
    private fun shouldBlockReels(): Boolean {
        if (!ShizukuManager.hasPermission()) return !isInstagramConversationSurface
        val top = ShizukuManager.execShellCapture(
            "dumpsys activity activities | grep -m1 topResumedActivity"
        ) ?: return !isInstagramConversationSurface
        isInstagramConversationSurface = top.contains("com.instagram.modal.") || top.contains("Direct")
        isInstagramMainTab = top.contains("com.instagram.android/.activity.MainTabActivity")
        return !isInstagramConversationSurface
    }

    /**
     * Según la versión de Instagram, Reels expone un SeekBar o un ViewPager.
     * En este último, los avances verticales de Reels usan los índices 1 y 2.
     */
    private fun isReelsViewer(event: AccessibilityEvent): Boolean {
        if (event.className?.toString()?.contains("SeekBar", ignoreCase = true) == true) return true
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return false
        if (event.className?.toString()?.contains("ViewPager") != true) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || event.scrollDeltaY <= 0) return false
        return event.toIndex in REELS_PAGER_INDICES
    }

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

    /** La cuadrícula de Search solo suma desplazamiento vertical real. */
    private fun isConsiderableSearchScroll(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return false
        if (event.className?.toString()?.contains("RecyclerView") != true) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val delta = kotlin.math.abs(event.scrollDeltaY)
        if (delta == 0) return false

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastSearchScrollAt > SCROLL_SESSION_GAP_MS) searchScrollDistancePx = 0
        lastSearchScrollAt = now
        searchScrollDistancePx += delta
        if (searchScrollDistancePx < SEARCH_SCROLL_LIMIT_PX) return false

        searchScrollDistancePx = 0
        return true
    }

    companion object {
        private const val TAG = "AntiScroll"
        private const val HOME_SCROLL_LIMIT_PX = 5_000
        private const val SEARCH_SCROLL_LIMIT_PX = 0
        private const val SCROLL_SESSION_GAP_MS = 4_000L
        private const val EVENT_COALESCE_MS = 100L
        private const val ACTIVITY_CHECK_GAP_MS = 500L
        private const val ESTIMATED_POST_HEIGHT_PX = 700
        private const val NO_ITEM_INDEX = -1
        private const val TAB_UNKNOWN = 0
        private const val TAB_HOME = 1
        private const val TAB_SEARCH = 2
        private const val TAB_OTHER = 3
        private val REELS_PAGER_INDICES = 1..2
        private val HOME_FEED_VISIBLE_ITEMS = 3..7
    }
}
