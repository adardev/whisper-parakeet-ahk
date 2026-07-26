package com.nemotron.voiceime.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.app.Service
import com.nemotron.voiceime.data.SecureStore

class FocusPasteService : AccessibilityService() {

    private var lastFocused: AccessibilityNodeInfo? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var autoFreezeEnabled = false

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
        autoFreezeEnabled = SecureStore.isAutoFreeze(this)
        Log.d(TAG, "connected, autoFreeze=$autoFreezeEnabled")
        if (autoFreezeEnabled) registerScreenReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenReceiver()
        instance = null
    }

    // ---------- Auto-freeze on screen off/on ------------------------------

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        val apps = com.nemotron.voiceime.data.SecureStore.getAutoFreezeApps(ctx)
                        if (apps.isEmpty()) return
                        Log.d(TAG, "SCREEN_OFF → freeze ${apps.size} auto-freeze apps")
                        Thread {
                            try {
                                for (pkg in apps) {
                                    com.nemotron.voiceime.dhizuku.DhizukuManager.hideAppRaw(ctx, pkg)
                                }
                                Log.d(TAG, "auto-freeze done")
                            } catch (t: Throwable) {
                                Log.e(TAG, "auto-freeze failed", t)
                            }
                        }.start()
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        val apps = com.nemotron.voiceime.data.SecureStore.getAutoFreezeApps(ctx)
                        if (apps.isEmpty()) return
                        Log.d(TAG, "USER_PRESENT → unfreeze ${apps.size} auto-freeze apps")
                        Thread {
                            try {
                                for (pkg in apps) {
                                    com.nemotron.voiceime.dhizuku.DhizukuManager.unhideAppRaw(ctx, pkg)
                                }
                                Log.d(TAG, "auto-unfreeze done")
                            } catch (t: Throwable) {
                                Log.e(TAG, "auto-unfreeze failed", t)
                            }
                        }.start()
                    }
                }
            }
        }
        registerReceiver(screenReceiver, filter)
        Log.d(TAG, "Screen receiver registered")
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Throwable) {}
        }
        screenReceiver = null
    }

    fun setAutoFreeze(enabled: Boolean) {
        autoFreezeEnabled = enabled
        Log.d(TAG, "autoFreeze: $enabled")
        if (enabled) registerScreenReceiver() else unregisterScreenReceiver()
    }

    fun isAutoFreezeEnabled(): Boolean = autoFreezeEnabled

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

        fun setAutoFreezeEnabled(context: Context, enabled: Boolean) {
            SecureStore.setAutoFreeze(context, enabled)
            instance?.setAutoFreeze(enabled)
        }

        fun isAutoFreezeEnabled(): Boolean = instance?.isAutoFreezeEnabled() ?: false

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
