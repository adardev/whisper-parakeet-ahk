package com.nemotron.voiceime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.dhizuku.AutoAndroidAuto
import com.nemotron.voiceime.dhizuku.CarDetector
import com.nemotron.voiceime.dhizuku.RootAccessManager
import com.nemotron.voiceime.guard.DndLockReceiver
import com.nemotron.voiceime.ui.AutoFreezeScheduler
import java.util.concurrent.atomic.AtomicBoolean

/** Application bootstrap for Nemotron's KernelSU-only privileged backend. */
class NemotronApp : android.app.Application() {

    private val dndReceiver = DndLockReceiver()
    private var dndRegistered = false
    private val privilegedFeaturesInitialized = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        instance = this
        RootAccessManager.initialize(this)
        registerDndReceiver()
        CarDetector.refresh(this)
        com.nemotron.voiceime.guard.DndKeepAliveService.update(this)
        com.nemotron.voiceime.dhizuku.ConnectionExclusionManager.start(this)

        // Check KernelSU away from the main thread so startup and Quick Settings
        // tile taps never wait on the SU manager.
        Thread({
            val result = RootAccessManager.requestAccess(timeoutMs = 5_000L)
            if (result.success) initializePrivilegedFeatures()
            else Log.w("NemotronApp", "KernelSU root unavailable at startup: ${result.detail}")
        }, "Nemotron-root-init").start()
    }

    private fun registerDndReceiver() {
        if (dndRegistered) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    dndReceiver,
                    IntentFilter(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                registerReceiver(
                    dndReceiver,
                    IntentFilter(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                )
            }
            dndRegistered = true
        } catch (t: Throwable) {
            Log.w("NemotronApp", "Could not register DND receiver", t)
        }
    }

    private fun initializePrivilegedFeatures() {
        if (!privilegedFeaturesInitialized.compareAndSet(false, true)) return
        Thread({
            runCatching { com.nemotron.voiceime.dhizuku.SamsungFit3Handoff.recoverIfNeeded(this) }
                .onFailure { Log.e("NemotronApp", "Samsung connection recovery failed", it) }
            runCatching { com.nemotron.voiceime.dhizuku.HealthWorkoutPowerSaver.syncForFit3State(this) }
                .onFailure { Log.e("NemotronApp", "Fit3 workout power sync failed", it) }
        }, "Nemotron-Fit3-Recovery").start()

        try {
            com.nemotron.voiceime.dhizuku.AirplaneModeSchedule.scheduleNext(this)
            if (SecureStore.isAutoFreezeEnabled(this)) {
                AutoFreezeScheduler.start(this)
                AutoFreezeScheduler.recover(this)
            }
            com.nemotron.voiceime.guard.AddictionGuard.applyEnabled(this)
            CarDetector.refresh(this)
            AutoAndroidAuto.reconcile(this)
        } catch (t: Throwable) {
            privilegedFeaturesInitialized.set(false)
            Log.e("NemotronApp", "Root feature initialization failed", t)
        }
    }

    companion object {
        lateinit var instance: NemotronApp
            private set
    }
}
