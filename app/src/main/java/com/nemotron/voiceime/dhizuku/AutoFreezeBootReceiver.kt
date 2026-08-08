package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.ui.AutoFreezeScheduler

class AutoFreezeBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (SecureStore.isAutoFreezeEnabled(ctx)) {
            Log.d("AutoFreezeBoot", "Auto-freeze enabled, registering receiver")
            AutoFreezeScheduler.start(ctx)
        }
        if (com.nemotron.voiceime.guard.AddictionGuard.isServiceNeeded(ctx)) {
            com.nemotron.voiceime.guard.AddictionGuard.applyEnabled(ctx)
        }
    }
}
