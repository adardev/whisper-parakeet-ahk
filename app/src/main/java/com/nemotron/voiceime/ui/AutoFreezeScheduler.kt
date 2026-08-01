package com.nemotron.voiceime.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.nemotron.voiceime.dhizuku.AutoFreezeScreenReceiver
import com.nemotron.voiceime.dhizuku.ShizukuManager

object AutoFreezeScheduler {

    private const val TAG = "AutoFreezeScheduler"
    private var receiver: AutoFreezeScreenReceiver? = null

    fun toggle(ctx: Context, enabled: Boolean) {
        if (enabled) start(ctx) else stop(ctx)
    }

    fun start(ctx: Context) {
        if (receiver != null) return
        ShizukuManager.exemptFromDoze(ctx.packageName)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val r = AutoFreezeScreenReceiver()
        ctx.applicationContext.registerReceiver(r, filter)
        receiver = r
        Log.d(TAG, "Auto-freeze receiver registered")
    }

    fun stop(ctx: Context) {
        val r = receiver ?: return
        try {
            ctx.applicationContext.unregisterReceiver(r)
        } catch (_: Throwable) {}
        receiver = null
        Log.d(TAG, "Auto-freeze receiver unregistered")
    }

    fun recover(ctx: Context) {
        receiver?.recover(ctx)
    }
}
