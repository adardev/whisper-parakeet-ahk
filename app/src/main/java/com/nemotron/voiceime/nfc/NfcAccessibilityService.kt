package com.nemotron.voiceime.nfc

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.nemotron.voiceime.dhizuku.ShizukuManager

/**
 * Detecta cuándo Wallet está en primer plano por EVENTOS (cero polling).
 * Al entrar Wallet → activa NFC. Al salir → lo desactiva si lo activó la app.
 */
class NfcAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        NfcAutoManager.onForegroundChanged(pkg)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("NfcAccessibility", "connected")
    }
}
