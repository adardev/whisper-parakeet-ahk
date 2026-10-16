package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AirplaneModeScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AirplaneModeSchedule.ACTION_TOGGLE -> {
                val enable = intent.getBooleanExtra(AirplaneModeSchedule.EXTRA_ENABLE, false)
                val pendingResult = goAsync()
                Thread {
                    try {
                        val alreadyEnabled = SleepModeController.isEnabled(context)
                        if (alreadyEnabled == enable) {
                            Log.i(TAG, "límite ${if (enable) "23:00" else "08:00"}: Sleep Mode ya está ${if (enable) "activo" else "apagado"}; sin cambios")
                        } else {
                            val result = SleepModeController.setEnabled(context, enable)
                            if (!result.success) Log.w(TAG, "rutina programada pendiente: ${result.message}")
                        }
                    } finally {
                        AirplaneModeSchedule.scheduleNext(context)
                        pendingResult.finish()
                    }
                }.start()
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> AirplaneModeSchedule.scheduleNext(context)
        }
    }

    private companion object {
        const val TAG = "AirplaneSchedule"
    }
}
