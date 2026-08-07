package com.nemotron.voiceime.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nemotron.voiceime.dhizuku.ShizukuManager
import java.util.Locale

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
    @Volatile private var isInstagramDirectSurface = false
    @Volatile private var isInstagramExternalProfileSurface = false
    @Volatile private var lastInstagramActivityCheckAt = 0L
    @Volatile private var lastHomeActivityCheckAt = 0L
    @Volatile private var lastProfileSurfaceCheckAt = 0L
    @Volatile private var isInstagramSystemProfileSurface = false
    @Volatile private var isInstagramSystemDirectSurface = false
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
        // Instagram omite el source de algunos RecyclerViews si no se pide el
        // árbol completo. Sin él, Direct y perfiles se confunden con Inicio.
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
                refreshInstagramProfileSurfaceIfNeeded(event)
                refreshInstagramDirectSurfaceIfNeeded(event)
                updateInstagramTabFromClick(event)
                // El ViewPager de una página de perfil puede parecerse al del
                // visor de Reels. La pantalla de perfil siempre tiene
                // prioridad: allí no se bloquea por ningún desplazamiento.
                val isExcludedInstagramScroll = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                    (isProfileScrollEvent(event) || isExternalProfileSurface() ||
                        isDirectSurfaceNow())
                val isReels = isReelsViewer(event)
                val blockReels = !isExcludedInstagramScroll && isReels && shouldBlockReels()
                val blockHome = !isExcludedInstagramScroll && isHomeFeedScrollCandidate(event) &&
                    canCountHomeScroll() && isConsiderableHomeFeedScroll(event)
                val blockSearch = !isExcludedInstagramScroll && selectedInstagramTab == TAB_SEARCH &&
                    isConsiderableSearchScroll(event)
                if (blockReels || blockHome || blockSearch) {
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
            isInstagramDirectSurface = true
            isInstagramExternalProfileSurface = false
            selectedInstagramTab = TAB_OTHER
            resetHomeScrollCounter()
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
                resetHomeScrollCounter()
            }
        }.start()
    }

    /** Registra la entrada a un perfil antes del primer gesto de scroll. */
    private fun refreshInstagramProfileSurfaceIfNeeded(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        isExternalProfileSurface(forceRefresh = true)
    }

    /** Direct también usa MainTabActivity; se desarma antes de cualquier scroll. */
    private fun refreshInstagramDirectSurfaceIfNeeded(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val root = rootInActiveWindow ?: return
        try {
            updateDirectSurfaceFromTree(root)
        } finally {
            root.recycle()
        }
    }

    /** Cubre un rebind del servicio justo antes de un gesto en Mensajes. */
    private fun isDirectSurfaceNow(): Boolean {
        if (isInstagramDirectSurface) return true
        val root = rootInActiveWindow ?: return false
        try {
            updateDirectSurfaceFromTree(root)
            return isInstagramDirectSurface
        } finally {
            root.recycle()
        }
    }

    private fun updateDirectSurfaceFromTree(root: AccessibilityNodeInfo) {
        if (treeContainsDirectSurface(root)) {
            isInstagramDirectSurface = true
            resetHomeScrollCounter()
        } else if (treeContainsHomeFeedSurface(root)) {
            // Igual que el perfil: Direct conserva su estado durante el
            // desplazamiento y solo se libera al confirmar que ya es Inicio.
            isInstagramDirectSurface = false
        }
    }

    /** El toque de la barra inferior identifica la pestaña sin consultar la UI. */
    private fun updateInstagramTabFromClick(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED) return
        when (event.contentDescription?.toString()) {
            "Home" -> {
                selectedInstagramTab = TAB_HOME
                isInstagramExternalProfileSurface = false
                isInstagramDirectSurface = false
            }
            "Search and explore" -> {
                selectedInstagramTab = TAB_SEARCH
                isInstagramExternalProfileSurface = false
                isInstagramDirectSurface = false
            }
            "Reels", "Message" -> {
                selectedInstagramTab = TAB_OTHER
                isInstagramExternalProfileSurface = false
                isInstagramDirectSurface = false
            }
            "Profile" -> {
                selectedInstagramTab = TAB_OTHER
                isInstagramExternalProfileSurface = true
                isInstagramDirectSurface = false
            }
            else -> {
                if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return
                // Abrir un perfil, post o pantalla interna no debe heredar el
                // permiso de contar que obtuvo el feed de Inicio.
                selectedInstagramTab = TAB_OTHER
                resetHomeScrollCounter()
            }
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

    /** Consulta la actividad como máximo una vez por segundo durante el feed. */
    private fun canCountHomeScroll(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastHomeActivityCheckAt >= HOME_ACTIVITY_CHECK_GAP_MS &&
            ShizukuManager.hasPermission()) {
            lastHomeActivityCheckAt = now
            val top = ShizukuManager.execShellCapture(
                "dumpsys activity activities | grep -m1 topResumedActivity"
            )
            isInstagramMainTab = top?.contains("com.instagram.android/.activity.MainTabActivity") == true
            isInstagramConversationSurface = top?.contains("com.instagram.modal.") == true ||
                top?.contains("Direct") == true
            refreshSystemInstagramSurface()
        }
        if (!isInstagramMainTab || isInstagramConversationSurface || isInstagramDirectSurface ||
            isExternalProfileSurface()) {
            resetHomeScrollCounter()
            return false
        }
        if (isInstagramSystemProfileSurface || isInstagramSystemDirectSurface) {
            if (isInstagramSystemProfileSurface) isInstagramExternalProfileSurface = true
            if (isInstagramSystemDirectSurface) isInstagramDirectSurface = true
            resetHomeScrollCounter()
            return false
        }
        return true
    }

    /** La UI real decide: Inicio es la única superficie que puede contar. */
    private fun refreshSystemInstagramSurface() {
        if (!ShizukuManager.hasPermission()) {
            isInstagramSystemProfileSurface = false
            isInstagramSystemDirectSurface = false
            return
        }
        val marker = ShizukuManager.execShellCapture(
            "uiautomator dump /dev/tty 2>/dev/null | grep -m1 -E " +
                "'profile_action_bar|direct_inbox_action_bar|direct_thread_header|thread_fragment_container'"
        ).orEmpty()
        isInstagramSystemProfileSurface = marker.contains("profile_action_bar")
        isInstagramSystemDirectSurface = marker.contains("direct_inbox_action_bar") ||
            marker.contains("direct_thread_header") || marker.contains("thread_fragment_container")
    }

    /**
     * Un perfil se abre dentro de MainTabActivity, por lo que la actividad por
     * sí sola no basta para distinguirlo del feed. Instagram expone nodos
     * profile_* en esa pantalla; si aparecen, preferimos no contar nada. Es
     * deliberadamente conservador: ante duda no se bloquea un perfil.
     */
    private fun isExternalProfileSurface(forceRefresh: Boolean = false): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!forceRefresh && now - lastProfileSurfaceCheckAt < PROFILE_SURFACE_CHECK_GAP_MS) {
            return isInstagramExternalProfileSurface
        }
        lastProfileSurfaceCheckAt = now
        val root = rootInActiveWindow ?: run {
            isInstagramExternalProfileSurface = false
            return false
        }
        try {
            if (treeContainsProfileSurface(root)) {
                isInstagramExternalProfileSurface = true
            }
            return isInstagramExternalProfileSurface
        } finally {
            root.recycle()
        }
    }

    private fun treeContainsProfileSurface(root: AccessibilityNodeInfo): Boolean {
        if (PROFILE_SURFACE_RESOURCE_IDS.any { resourceId ->
                root.findAccessibilityNodeInfosByViewId(
                    "${AddictionGuard.INSTAGRAM}:id/$resourceId"
                ).isNotEmpty()
            }) return true
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var inspected = 0
        while (pending.isNotEmpty() && inspected++ < MAX_PROFILE_NODES) {
            val node = pending.removeFirst()
            if (isProfileSurfaceNode(node)) return true
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return false
    }

    private fun isProfileSurfaceNode(node: AccessibilityNodeInfo): Boolean {
        val resourceId = node.viewIdResourceName.orEmpty()
        if (PROFILE_SURFACE_RESOURCE_IDS.any(resourceId::endsWith)) return true
        val label = (node.text ?: node.contentDescription)
            ?.toString()
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return false
        return label in EXTERNAL_PROFILE_ACTIONS
    }

    /** La lista interna del perfil conserva profile_viewpager como ancestro. */
    private fun isProfileScrollEvent(event: AccessibilityEvent): Boolean {
        var node = event.source ?: return false
        repeat(MAX_PROFILE_SCROLL_ANCESTORS) {
            if (isProfileSurfaceNode(node)) {
                isInstagramExternalProfileSurface = true
                resetHomeScrollCounter()
                return true
            }
            node = node.parent ?: return false
        }
        return false
    }

    private fun treeContainsHomeFeedSurface(root: AccessibilityNodeInfo): Boolean {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var inspected = 0
        while (pending.isNotEmpty() && inspected++ < MAX_PROFILE_NODES) {
            val node = pending.removeFirst()
            if (node.viewIdResourceName.orEmpty().endsWith(HOME_FEED_RESOURCE_ID) ||
                node.contentDescription?.toString() == "Instagram Home Feed") return true
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return false
    }

    private fun treeContainsDirectSurface(root: AccessibilityNodeInfo): Boolean {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var inspected = 0
        while (pending.isNotEmpty() && inspected++ < MAX_PROFILE_NODES) {
            val node = pending.removeFirst()
            val resourceId = node.viewIdResourceName.orEmpty()
            if (DIRECT_SURFACE_RESOURCE_IDS.any(resourceId::endsWith) ||
                resourceId.contains("/id/direct_thread")) return true
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return false
    }

    private fun resetHomeScrollCounter() {
        homeScrollDistancePx = 0
        lastHomeFeedFirstItem = NO_ITEM_INDEX
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
    private fun isHomeFeedScrollCandidate(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return false
        if (event.className?.toString()?.contains("RecyclerView") != true) return false
        return event.toIndex - event.fromIndex in HOME_FEED_VISIBLE_ITEMS
    }

    private fun isConsiderableHomeFeedScroll(event: AccessibilityEvent): Boolean {
        if (!isHomeFeedScrollCandidate(event)) return false

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
        private const val EVENT_COALESCE_MS = 200L
        private const val ACTIVITY_CHECK_GAP_MS = 500L
        // Solo una comprobación de actividad por sesión continua de scroll.
        private const val HOME_ACTIVITY_CHECK_GAP_MS = 4_000L
        private const val PROFILE_SURFACE_CHECK_GAP_MS = 750L
        private const val MAX_PROFILE_NODES = 120
        private const val MAX_PROFILE_SCROLL_ANCESTORS = 12
        private const val HOME_FEED_RESOURCE_ID = "main_feed_action_bar"
        private const val ESTIMATED_POST_HEIGHT_PX = 700
        private const val NO_ITEM_INDEX = -1
        private const val TAB_UNKNOWN = 0
        private const val TAB_HOME = 1
        private const val TAB_SEARCH = 2
        private const val TAB_OTHER = 3
        private val REELS_PAGER_INDICES = 1..2
        private val HOME_FEED_VISIBLE_ITEMS = 3..7
        private val PROFILE_SURFACE_RESOURCE_IDS = setOf(
            "profile_action_bar",
            "profile_header_container",
            "profile_tabs_container",
            "profile_viewpager"
        )
        private val EXTERNAL_PROFILE_ACTIONS = setOf(
            "follow", "following", "follow back", "requested",
            "seguir", "siguiendo", "seguir también", "solicitado"
        )
        private val DIRECT_SURFACE_RESOURCE_IDS = setOf(
            "direct_inbox_action_bar",
            "inbox_refreshable_thread_list_recyclerview",
            "row_inbox_container"
        )
    }
}
