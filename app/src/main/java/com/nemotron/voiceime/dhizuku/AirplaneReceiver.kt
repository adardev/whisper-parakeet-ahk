package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AirplaneReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val enable = intent.getBooleanExtra(AutoFreezeService.EXTRA_ENABLE, false)
        Log.d(TAG, "airplane toggle → enable=$enable")
        val pendingResult = goAsync()

        Thread {
            try {
                val success = SystemAirplaneModeAction.set(ctx, enable)
                Log.d(TAG, "Airplane action result=$success enable=$enable")
            } catch (t: Throwable) {
                Log.e(TAG, "immediate airplane request failed", t)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "AirplaneReceiver"
    }
}
