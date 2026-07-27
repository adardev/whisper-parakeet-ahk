package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class UnhideReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pkg = intent.getStringExtra("package") ?: return
        Log.d("UnhideReceiver", "unhide: $pkg")
        Thread {
            if (ShizukuManager.hasPermission()) {
                val result = ShizukuManager.unhideApp(pkg)
                Log.d("UnhideReceiver", "result: $result")
            }
        }.start()
    }
}
