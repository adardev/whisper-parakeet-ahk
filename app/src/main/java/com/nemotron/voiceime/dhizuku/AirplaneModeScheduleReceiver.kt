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
                        val current = ShizukuManager.isAirplaneModeOn()
                        if (current == null) {
                            Log.w(TAG, "no se pudo leer el modo avión; no se cambia")
                        } else if (current == enable) {
                            Log.i(TAG, "el modo avión ya está ${if (enable) "activado" else "desactivado"}; sin cambios")
                        } else if (!ShizukuManager.setAirplaneMode(enable)) {
                            Log.w(TAG, "Shizuku no pudo cambiar el modo avión")
                        } else {
                            Log.i(TAG, "modo avión ${if (enable) "activado" else "desactivado"} por horario")
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
