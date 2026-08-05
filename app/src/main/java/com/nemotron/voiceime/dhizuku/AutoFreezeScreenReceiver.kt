package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

class AutoFreezeScreenReceiver : BroadcastReceiver() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingFreeze: Runnable? = null
    private var retryUnfreeze: Runnable? = null
    private var unfreezeAttempts = 0

    override fun onReceive(ctx: Context, intent: Intent) {
        if (!SecureStore.isAutoFreezeEnabled(ctx)) return

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                cancelPendingFreeze()
                applyDozeExempt(ctx)
                forceDoze(ctx)
                val apps = SecureStore.getAutoFreezeApps(ctx).toList()
                if (apps.isEmpty()) return
                val delay = if (SecureStore.isAutoFreezeTestMode(ctx)) 0L else FREEZE_DELAY_MS
                val freezeRunnable = Runnable {
                    pendingFreeze = null
                    if (isScreenOn(ctx)) {
                        Log.d(TAG, "Screen already on, skipping freeze")
                        return@Runnable
                    }
                    doFreeze(ctx, apps)
                }
                pendingFreeze = freezeRunnable
                if (delay == 0L) {
                    handler.post(freezeRunnable)
                    Log.d(TAG, "Test mode: freezing immediately")
                } else {
                    handler.postDelayed(freezeRunnable, delay)
                    Log.d(TAG, "Scheduled freeze in ${delay / 1000}s")
                }
            }
            Intent.ACTION_SCREEN_ON -> {
                cancelPendingFreeze()
                unforceDoze(ctx)
                unfreeze(ctx)
            }
        }
    }

    /** Call this when Shizuku comes back to heal apps frozen while it was down. */
    fun recover(ctx: Context) {
        if (!SecureStore.isAutoFreezeEnabled(ctx)) return
        if (!isScreenOn(ctx)) return
        unfreeze(ctx)
    }

    private fun cancelPendingFreeze() {
        pendingFreeze?.let { handler.removeCallbacks(it) }
        pendingFreeze = null
    }

    private fun unfreeze(ctx: Context) {
        retryUnfreeze?.let { handler.removeCallbacks(it) }
        retryUnfreeze = null
        unfreezeAttempts = 0
        runUnfreezeAttempt(ctx)
    }

    private fun runUnfreezeAttempt(ctx: Context) {
        Thread {
            val apps = SecureStore.getAutoFreezeApps(ctx).toList()
            if (apps.isEmpty()) return@Thread
            val remaining = tryUnfreeze(apps)
            handler.post {
                if (remaining.isEmpty()) {
                    Log.d(TAG, "All auto-freeze apps are active, scheduling stop in ${STOP_DELAY_MS / 1000}s")
                    handler.postDelayed({ doStopOnUnlock(ctx) }, STOP_DELAY_MS)
                    return@post
                }
                unfreezeAttempts++
                if (unfreezeAttempts > MAX_RETRIES) {
                    Log.w(TAG, "Gave up unfreezing after $MAX_RETRIES attempts: $remaining")
                    return@post
                }
                // No reintentar si Shizuku sigue muerto: el binder listener lo revive solo.
                if (!ShizukuManager.hasPermission()) {
                    Log.d(TAG, "Shizuku unavailable, waiting for binder instead of retrying")
                    return@post
                }
                val runnable = Runnable {
                    retryUnfreeze = null
                    runUnfreezeAttempt(ctx)
                }
                retryUnfreeze = runnable
                handler.postDelayed(runnable, RETRY_DELAY_MS)
            }
        }.start()
    }

    private fun tryUnfreeze(apps: List<String>): Set<String> {
        if (!ShizukuManager.hasPermission()) return apps.toSet()
        val failed = mutableSetOf<String>()
        for (pkg in apps) {
            if (!ShizukuManager.unhideApp(pkg)) failed.add(pkg)
        }
        val still = ShizukuManager.stillDisabled(apps)
        failed.addAll(still)
        Log.d(TAG, "Unfreeze: still disabled=$still")
        return failed
    }

    private fun doStopOnUnlock(ctx: Context) {
        Thread {
            val stopApps = SecureStore.getStopOnUnlockApps(ctx).toList()
            if (stopApps.isEmpty()) return@Thread
            for (attempt in 1..STOP_RETRIES) {
                if (!ShizukuManager.hasPermission()) {
                    Log.w(TAG, "Stop on unlock attempt $attempt/$STOP_RETRIES skipped: Shizuku unavailable")
                } else {
                    val ok = stopApps.all { ShizukuManager.stopApp(it) }
                    if (ok) {
                        Log.d(TAG, "Stopped on unlock: $stopApps")
                        return@Thread
                    }
                    Log.w(TAG, "Stop on unlock attempt $attempt/$STOP_RETRIES returned failure")
                }
                try {
                    Thread.sleep(STOP_RETRY_DELAY_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }.start()
    }

    private fun doFreeze(ctx: Context, apps: List<String>) {
        Thread {
            if (!ShizukuManager.hasPermission()) {
                Log.w(TAG, "Freeze skipped: Shizuku unavailable")
                return@Thread
            }
            for (pkg in apps) ShizukuManager.hideApp(pkg)
            val stopApps = SecureStore.getStopOnUnlockApps(ctx).toList()
            for (pkg in stopApps) ShizukuManager.stopApp(pkg)
            Log.d(TAG, "Froze ${apps.size} apps, stopped on lock: $stopApps")
        }.start()
    }

    private fun applyDozeExempt(ctx: Context) {
        val exempt = SecureStore.getDozeExemptApps(ctx).toMutableSet()
        exempt.add(ctx.packageName)
        if (exempt.isEmpty()) return
        Thread {
            if (!ShizukuManager.hasPermission()) return@Thread
            for (pkg in exempt) ShizukuManager.exemptFromDoze(pkg)
            Log.d(TAG, "Doze whitelist applied: $exempt")
        }.start()
    }

    private fun forceDoze(ctx: Context) {
        if (!SecureStore.isAutoFreezeDozeEnabled(ctx)) return
        Thread {
            if (!ShizukuManager.hasPermission()) return@Thread
            ShizukuManager.forceIdle()
        }.start()
    }

    private fun unforceDoze(ctx: Context) {
        if (!SecureStore.isAutoFreezeDozeEnabled(ctx)) return
        Thread {
            if (!ShizukuManager.hasPermission()) return@Thread
            ShizukuManager.unforceIdle()
        }.start()
    }

    private fun isScreenOn(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isInteractive
    }

    companion object {
        private const val TAG = "AutoFreezeScreen"
        private const val FREEZE_DELAY_MS = 30_000L
        private const val STOP_DELAY_MS = 3_000L
        private const val STOP_RETRIES = 2
        private const val STOP_RETRY_DELAY_MS = 5_000L
        private const val RETRY_DELAY_MS = 15_000L
        private const val MAX_RETRIES = 6
    }
}
