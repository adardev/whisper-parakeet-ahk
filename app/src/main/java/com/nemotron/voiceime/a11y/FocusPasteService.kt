package com.nemotron.voiceime.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.app.Service
import android.os.Handler
import android.os.Looper
import android.graphics.Path
import android.graphics.Rect

class FocusPasteService : AccessibilityService() {

    private var lastFocused: AccessibilityNodeInfo? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        if (ev.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            val src = try { rootInActiveWindow } catch (_: Throwable) { null } ?: return
            val focused = src.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                lastFocused = focused
                Log.d(TAG, "focused editable: ${focused.className}")
            }
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "connected")
        pendingAirplaneRequest?.let { enabled ->
            pendingAirplaneRequest = null
            requestSystemAirplaneTile(enabled)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // ---------- Paste ----------------------------------------------------------

    private fun hasFocusedInput(): Boolean {
        val node = lastFocused ?: return false
        return try {
            node.isEditable
        } catch (_: Throwable) { false }
    }

    private fun doPaste(text: String): Boolean {
        val node = lastFocused ?: return false
        return try {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (ok) true else performPasteAction()
        } catch (t: Throwable) {
            Log.w(TAG, "doPaste fail", t)
            performPasteAction()
        }
    }

    private fun performPasteAction(): Boolean {
        val node = lastFocused ?: return false
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (_: Throwable) {
            false
        }
    }

    /** Opens the locked-device QS panel and presses Samsung's native tile. */
    private fun requestSystemAirplaneTile(enabled: Boolean): Boolean {
        handler.postDelayed(waitForKeyguard@{
            if (enabled) {
                // Wait until the lock transition is complete before opening
                // SystemUI, then reset the stale grey indicator left by Dhizuku.
                try {
                    Settings.Global.putInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not reset stale airplane indicator", t)
                }
            }
            handler.postDelayed(openQuickSettings@{
                if (!performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)) {
                    Log.w(TAG, "Could not open Quick Settings for airplane lock")
                    return@openQuickSettings
                }
                handler.postDelayed(clickAirplaneTile@{
                    val tile = findSystemAirplaneTile(rootInActiveWindow)
                    if (tile == null) {
                        Log.w(TAG, "Native airplane tile was not found in Quick Settings")
                        return@clickAirplaneTile
                    }
                    val clicked = tile.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Native airplane tile click=$clicked")
                    if (!clicked) tapTile(tile)
                    handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, CLOSE_PANEL_DELAY_MS)
                }, OPEN_QS_DELAY_MS)
            }, if (enabled) RESET_INDICATOR_DELAY_MS else 0L)
        }, if (enabled) KEYGUARD_SETTLE_DELAY_MS else 0L)
        return true
    }

    private fun findSystemAirplaneTile(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val description = node.contentDescription?.toString()?.lowercase().orEmpty()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val matchesAirplane = description.contains("airplane mode") ||
                description.contains("modo avión") || description.contains("modo avion") ||
                text == "airplane mode" || text == "modo avión" || text == "modo avion"
            if (matchesAirplane) {
                var clickable: AccessibilityNodeInfo? = node
                while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                if (clickable != null) return clickable
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
        }
        return null
    }

    private fun tapTile(tile: AccessibilityNodeInfo) {
        val bounds = Rect()
        tile.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            Log.w(TAG, "Native airplane tile has no screen bounds")
            return
        }
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Native airplane tile gesture completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Native airplane tile gesture cancelled")
            }
        }, null)
        Log.d(TAG, "Native airplane tile gesture dispatched=$dispatched")
    }

    companion object {
        private const val TAG = "FocusPasteService"

        @Volatile private var instance: FocusPasteService? = null
        @Volatile private var pendingAirplaneRequest: Boolean? = null
        private const val OPEN_QS_DELAY_MS = 700L
        private const val CLOSE_PANEL_DELAY_MS = 500L
        private const val RESET_INDICATOR_DELAY_MS = 350L
        private const val TAP_DURATION_MS = 80L
        private const val KEYGUARD_SETTLE_DELAY_MS = 3_000L

        fun paste(ctx: Context, text: String): Boolean {
            val svc = instance ?: return false

            if (svc.hasFocusedInput()) {
                return try {
                    svc.doPaste(text)
                } catch (t: Throwable) {
                    Log.w(TAG, "paste in focused fail", t)
                    false
                }
            }

            val cm = ctx.getSystemService(Service.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("nemotron", text))
            return true
        }

        /**
         * Requests a real native QS click. If the service is not running yet,
         * enable it and complete the request from onServiceConnected().
         */
        fun requestSystemAirplaneTile(context: Context, enabled: Boolean): Boolean {
            instance?.let { return it.requestSystemAirplaneTile(enabled) }
            pendingAirplaneRequest = enabled
            return enableSelf(context)
        }

        fun enableSelf(context: Context): Boolean {
            if (isRunning()) return true
            try {
                val cn = ComponentName(context, FocusPasteService::class.java).flattenToString()
                val current = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()

                val newServices = if (current.isBlank()) cn
                else "$current:$cn"

                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newServices
                )
                Settings.Secure.putInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    1
                )
                Log.d(TAG, "enableSelf OK")
                return true
            } catch (t: Throwable) {
                Log.w(TAG, "enableSelf fail", t)
                return false
            }
        }

        fun disableSelf(context: Context) {
            try {
                val cn = ComponentName(context, FocusPasteService::class.java).flattenToString()
                val current = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()

                val newServices = current.split(":")
                    .filter { it.isNotBlank() && it != cn }
                    .joinToString(":")

                Settings.Secure.putString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newServices
                )
                Log.d(TAG, "disableSelf OK")
            } catch (t: Throwable) {
                Log.w(TAG, "disableSelf fail", t)
            }
        }

        fun isRunning(): Boolean = instance != null

        fun isAccessibilityEnabled(context: Context): Boolean {
            val target = context.packageName + "/" + FocusPasteService::class.java.name
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = android.text.TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                val entry = splitter.next()
                if (entry.equals(target, true)) return true
            }
            return false
        }
    }
}
