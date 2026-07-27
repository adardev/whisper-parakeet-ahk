package com.nemotron.voiceime.nfc

import android.util.Log
import com.nemotron.voiceime.NemotronApp
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.dhizuku.ShizukuManager

object ShizukuScreenMonitor {

    private const val TAG = "ScreenMonitor"
    private const val POLL_MS = 1000L

    private var running = false
    private var pollThread: Thread? = null
    private var airplaneOnByUs = false

    fun start() {
        if (running) return
        if (!ShizukuManager.hasPermission()) return
        running = true
        pollThread = Thread({
            while (running && !Thread.currentThread().isInterrupted) {
                try {
                    val awake = isScreenOn()
                    val autoAirplane = SecureStore.isAutoAirplane(NemotronApp.instance)
                    if (autoAirplane) {
                        if (!awake && !airplaneOnByUs) {
                            airplaneOnByUs = true
                            ShizukuManager.setAirplaneMode(NemotronApp.instance, true)
                            Log.d(TAG, "screen OFF → airplane ON")
                        } else if (awake && airplaneOnByUs) {
                            airplaneOnByUs = false
                            ShizukuManager.setAirplaneMode(NemotronApp.instance, false)
                            Log.d(TAG, "screen ON → airplane OFF")
                        }
                    } else {
                        airplaneOnByUs = false
                    }
                    Thread.sleep(POLL_MS)
                } catch (_: InterruptedException) {
                    break
                } catch (t: Throwable) {
                    Log.w(TAG, "poll error", t)
                    Thread.sleep(3000)
                }
            }
        }, "screen-monitor").apply {
            isDaemon = true
            start()
        }
        Log.d(TAG, "started")
    }

    fun stop() {
        running = false
        pollThread?.interrupt()
        pollThread = null
        Log.d(TAG, "stopped")
    }

    private fun isScreenOn(): Boolean {
        val out = ShizukuManager.execShellCapture("dumpsys power | grep mWakefulness") ?: return true
        return out.contains("Awake")
    }
}
