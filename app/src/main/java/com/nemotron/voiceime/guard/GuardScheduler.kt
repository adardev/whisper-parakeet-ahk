package com.nemotron.voiceime.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Mantiene el servicio de accesibilidad SOLO mientras la pantalla está encendida
 * (único momento en que se puede estar viendo Reels/Status). Con pantalla
 * apagada se desactiva del todo: no corre ningún servicio ni gasta nada.
 */
object GuardScheduler {

    private const val TAG = "GuardScheduler"
    private var receiver: GuardScreenReceiver? = null

    fun start(ctx: Context) {
        if (receiver != null) return
        val r = GuardScreenReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ctx.applicationContext.registerReceiver(r, filter)
        receiver = r
        Log.d(TAG, "screen gate registrado")
    }

    fun stop(ctx: Context) {
        val r = receiver ?: return
        try {
            ctx.applicationContext.unregisterReceiver(r)
        } catch (_: Throwable) {}
        receiver = null
        Log.d(TAG, "screen gate eliminado")
    }
}

class GuardScreenReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!AddictionGuard.isEnabled(ctx)) return
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("GuardScreen", "screen off → desactivando guard")
                AddictionGuard.setAccessibilityServiceEnabled(ctx, false)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d("GuardScreen", "screen on → activando guard")
                AddictionGuard.setAccessibilityServiceEnabled(ctx, true)
            }
        }
    }
}
