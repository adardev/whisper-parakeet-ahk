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
                        if (!enable) setAirplaneModeIfNeeded(false)
                        if (!NightRoutineActions.run(context, enable).success) {
                            Log.w(TAG, "no se pudo cambiar el AOD de la Fit3 en el límite programado")
                        }
                        // The daily airplane-mode schedule stays an independent action.
                        // The manual Fit3 test buttons only exercise the watch settings.
                        if (enable) setAirplaneModeIfNeeded(true)
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

        fun setAirplaneModeIfNeeded(enabled: Boolean) {
            when (ShizukuManager.isAirplaneModeOn()) {
                null -> Log.w(TAG, "no se pudo leer el modo avión")
                enabled -> Unit
                else -> {
                    val changed = ShizukuManager.setAirplaneMode(enabled) &&
                        ShizukuManager.isAirplaneModeOn() == enabled
                    if (!changed) Log.w(TAG, "no se pudo cambiar el modo avión a $enabled")
                }
            }
        }
    }
}
