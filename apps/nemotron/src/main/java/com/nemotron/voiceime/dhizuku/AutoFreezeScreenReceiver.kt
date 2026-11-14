package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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
                val apps = SecureStore.getAutoFreezeApps(ctx).toList()
                val services = SecureStore.getStopOnScreenOffServices(ctx).toList()
                if (apps.isEmpty() && services.isEmpty()) {
                    forceDoze(ctx)
                    return
                }
                val delay = if (SecureStore.isAutoFreezeTestMode(ctx)) 0L else FREEZE_DELAY_MS
                val freezeRunnable = Runnable {
                    pendingFreeze = null
                    if (isScreenOn(ctx)) {
                        Log.d(TAG, "Screen already on, skipping freeze")
                        return@Runnable
                    }
                    doFreeze(ctx, apps, services)
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
                unfreeze(ctx)
            }
        }
    }

    /** Call this when KernelSU comes back to heal apps frozen while it was down. */
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
            val startedAt = SystemClock.elapsedRealtime()
            // Undo deep idle before changing package state. Both operations used
            // to race on separate threads after SCREEN_ON.
            if (SecureStore.isAutoFreezeDozeEnabled(ctx) && RootShellManager.hasPermission()) {
                RootShellManager.unforceIdle()
            }
            val apps = SecureStore.getAutoFreezeApps(ctx).toList()
            if (apps.isEmpty()) return@Thread
            val remaining = tryUnfreeze(apps)
            Log.i(TAG, "Wake unfreeze completed in ${SystemClock.elapsedRealtime() - startedAt}ms; remaining=$remaining")
            handler.post {
                if (remaining.isEmpty()) {
                    Log.d(TAG, "All auto-freeze apps are active; applying separate stop-on-unlock selections in ${STOP_DELAY_MS / 1000}s")
                    handler.postDelayed({ doStopOnUnlock(ctx) }, STOP_DELAY_MS)
                    return@post
                }
                unfreezeAttempts++
                if (unfreezeAttempts > MAX_RETRIES) {
                    Log.w(TAG, "Gave up unfreezing after $MAX_RETRIES attempts: $remaining")
                    return@post
                }
                // No reintentar si KernelSU sigue muerto: el binder listener lo revive solo.
                if (!RootShellManager.hasPermission()) {
                    Log.d(TAG, "KernelSU unavailable, waiting for binder instead of retrying")
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
        if (!RootShellManager.hasPermission()) return apps.toSet()
        // Unhide, enable and clear PackageManager's force-stop bit for the full
        // list in one root invocation. The old per-app path omitted `pm unstop`.
        val batch = RootShellManager.unfreezeAutoFreezeApps(apps)
        if (batch != null) {
            val remaining = batch
            Log.i(TAG, "Batched wake unfreeze confirmed for ${apps.size - remaining.size}/${apps.size} apps")
            return remaining
        }

        Log.w(TAG, "Batched wake unfreeze unavailable; using verified per-app fallback")
        val failed = mutableSetOf<String>()
        for (pkg in apps) {
            if (!RootShellManager.unhideApp(pkg) || !RootShellManager.unstopApp(pkg)) failed.add(pkg)
        }
        val states = RootShellManager.getPackageStates(apps)
        for (pkg in apps) {
            val state = states?.get(pkg)
            if (state == null || state.hidden || state.disabled || state.stopped != false) failed.add(pkg)
        }
        Log.d(TAG, "Unfreeze fallback: remaining=$failed")
        return failed
    }

    private fun doStopOnUnlock(ctx: Context) {
        Thread {
            // Apps in the auto-freeze list are already stopped on screen-off
            // and must not be killed again shortly after they become available.
            val autoFreezeApps = SecureStore.getAutoFreezeApps(ctx)
            val stopApps = SecureStore.getStopOnUnlockApps(ctx).filterNot { it in autoFreezeApps }
            if (stopApps.isEmpty()) return@Thread
            for (attempt in 1..STOP_RETRIES) {
                if (!RootShellManager.hasPermission()) {
                    Log.w(TAG, "Stop on unlock attempt $attempt/$STOP_RETRIES skipped: KernelSU unavailable")
                } else {
                    val ok = stopApps.all { RootShellManager.stopApp(it) }
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

    private fun doFreeze(ctx: Context, apps: List<String>, services: List<String>) {
        Thread {
            if (!RootShellManager.hasPermission()) {
                Log.w(TAG, "Freeze skipped: KernelSU unavailable")
                return@Thread
            }
            // Con Android Auto conectado no se congelan apps: congelar GMS
            // rompería Android Auto y el SpeechRecognizer de Nemotron.
            if (CarDetector.isCarConnected()) {
                Log.d(TAG, "Coche conectado: se omite el auto-freeze")
                return@Thread
            }
            if (services.isNotEmpty()) RootShellManager.stopServices(services)
            for (pkg in apps) RootShellManager.hideApp(pkg)
            val stopApps = SecureStore.getStopOnUnlockApps(ctx).toList()
            for (pkg in stopApps) RootShellManager.stopApp(pkg)
            Log.d(TAG, "Froze ${apps.size} apps, stopped ${services.size} selected services, stopped on lock: $stopApps")
            // Freeze selected apps before entering deep idle. If force-idle is
            // applied at screen-off, Android can suspend this delayed work.
            forceDoze(ctx)
        }.start()
    }

    private fun applyDozeExempt(ctx: Context) {
        val exempt = SecureStore.getDozeExemptApps(ctx)
        if (exempt.isEmpty()) return
        Thread {
            if (!RootShellManager.hasPermission()) return@Thread
            for (pkg in exempt) RootShellManager.exemptFromDoze(pkg)
            Log.d(TAG, "Doze whitelist applied: $exempt")
        }.start()
    }

    private fun forceDoze(ctx: Context) {
        if (!SecureStore.isAutoFreezeDozeEnabled(ctx)) return
        Thread {
            if (!RootShellManager.hasPermission()) return@Thread
            // El doze profundo en el coche mataría procesos (incluido KernelSU y
            // la proyección de Android Auto); se omite con el coche conectado.
            if (CarDetector.isCarConnected()) {
                Log.d(TAG, "Coche conectado: se omite force-idle")
                return@Thread
            }
            RootShellManager.forceIdle()
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
