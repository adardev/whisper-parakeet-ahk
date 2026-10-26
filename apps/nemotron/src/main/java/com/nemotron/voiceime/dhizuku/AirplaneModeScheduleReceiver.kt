package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AirplaneModeScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AirplaneModeSchedule.ACTION_TOGGLE -> {
                val enable = intent.getBooleanExtra(AirplaneModeSchedule.EXTRA_ENABLE, true)
                AirplaneModeSchedule.scheduleNext(context)
                if (!AirplaneModeSchedule.isScheduledActionCurrentWindow(enable)) {
                    Log.i(TAG, "se ignora alarma Sleep Mode atrasada; enable=$enable")
                    return
                }
                AirplaneModeSchedule.cancelRetry(context)
                runCatching {
                    SleepModeActionService.start(context, scheduled = true, enabled = enable)
                }.onFailure {
                    Log.e(TAG, "No se pudo iniciar Sleep Mode programado", it)
                    AirplaneModeSchedule.scheduleRetry(context, enable)
                }
            }
            AirplaneModeSchedule.ACTION_RETRY -> {
                val enable = intent.getBooleanExtra(AirplaneModeSchedule.EXTRA_ENABLE, true)
                runCatching {
                    SleepModeActionService.start(context, scheduled = true, enabled = enable, retry = true)
                }.onFailure { Log.e(TAG, "No se pudo reintentar Sleep Mode", it) }
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
