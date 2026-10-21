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
                // Ignore a legacy 08:00 alarm already queued before this update.
                if (!enable) {
                    AirplaneModeSchedule.scheduleNext(context)
                    return
                }
                // A BLE handshake can exceed the lifetime of a broadcast.
                // Schedule tomorrow first and hand this single action to a
                // bounded service, rather than leaving a receiver thread alive.
                AirplaneModeSchedule.scheduleNext(context)
                runCatching { SleepModeActionService.start(context, scheduled = true) }
                    .onFailure { Log.e(TAG, "No se pudo iniciar Sleep Mode programado", it) }
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
