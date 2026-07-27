package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

class AutoFreezeScreenReceiver : BroadcastReceiver() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingFreeze: Runnable? = null

    override fun onReceive(ctx: Context, intent: Intent) {
        if (!SecureStore.isAutoFreezeEnabled(ctx)) return
        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku not available, skipping auto-freeze")
            return
        }

        val apps = SecureStore.getAutoFreezeApps(ctx).toList()
        if (apps.isEmpty()) return

        val action = intent.action
        Log.d(TAG, "Screen action: $action, ${apps.size} apps")

        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                pendingFreeze?.let { handler.removeCallbacks(it) }
                val freezeRunnable = Runnable {
                    val currentApps = SecureStore.getAutoFreezeApps(ctx).toList()
                    if (currentApps.isEmpty()) return@Runnable
                    Thread {
                        for (pkg in currentApps) {
                            ShizukuManager.hideApp(pkg)
                        }
                        Log.d(TAG, "Froze ${currentApps.size} apps")
                    }.start()
                }
                pendingFreeze = freezeRunnable
                handler.postDelayed(freezeRunnable, 30_000L)
                Log.d(TAG, "Scheduled freeze in 30s")
            }
            Intent.ACTION_SCREEN_ON -> {
                Thread {
                    for (pkg in apps) {
                        ShizukuManager.unhideApp(pkg)
                    }
                    Log.d(TAG, "Unfroze ${apps.size} apps")
                }.start()
            }
        }
    }

    companion object {
        private const val TAG = "AutoFreezeScreen"
        private const val FREEZE_DELAY_MS = 60_000L
    }
}
