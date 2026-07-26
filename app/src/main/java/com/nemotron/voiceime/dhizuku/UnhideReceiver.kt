package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

class UnhideReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pkg = intent.getStringExtra("package") ?: return
        Log.d("UnhideReceiver", "unhide: $pkg")
        Thread {
            val result = DhizukuManager.unhideAppRaw(ctx, pkg)
            Log.d("UnhideReceiver", "result: $result")
        }.start()
    }
}
