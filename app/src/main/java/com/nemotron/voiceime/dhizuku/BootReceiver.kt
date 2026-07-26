package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (SecureStore.isAutoFreeze(ctx) && SecureStore.getAutoFreezeApps(ctx).isNotEmpty()) {
            Log.d("BootReceiver", "Starting AutoFreezeService")
            AutoFreezeService.start(ctx)
        }
    }
}
