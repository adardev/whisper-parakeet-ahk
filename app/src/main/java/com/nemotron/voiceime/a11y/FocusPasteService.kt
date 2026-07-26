package com.nemotron.voiceime.a11y

import android.accessibilityservice.AccessibilityService
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

class FocusPasteService : AccessibilityService() {

    private var lastFocused: AccessibilityNodeInfo? = null

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

    companion object {
        private const val TAG = "FocusPasteService"

        @Volatile private var instance: FocusPasteService? = null

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
