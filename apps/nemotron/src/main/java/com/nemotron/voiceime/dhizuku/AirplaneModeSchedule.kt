package com.nemotron.voiceime.dhizuku

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/** One daily activation at 23:00. Sleep Mode is switched off manually. */
object AirplaneModeSchedule {
    private const val TAG = "AirplaneSchedule"
    const val ACTION_TOGGLE = "com.nemotron.voiceime.FIT3_AOD_SCHEDULE_TOGGLE"
    const val EXTRA_ENABLE = "enable"
    private const val REQUEST_CODE = 2308

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        // Only Core owns automation after the three-package split. Cancel
        // legacy copies when an older UI/Keyboard process starts.
        if (context.packageName != "com.nemotron.core") {
            alarmManager.cancel(pendingIntent(context, null))
            return
        }
        val next = nextActivationMillis()
        val pendingIntent = pendingIntent(context, null)
        alarmManager.cancel(pendingIntent)
        val actionIntent = pendingIntent(context, true)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() && ShizukuManager.hasPermission()) {
                // Shizuku shell can grant the alarm app-op without sending the user through Settings.
                ShizukuManager.execShellFresh(
                    arrayOf("cmd", "appops", "set", context.packageName, "SCHEDULE_EXACT_ALARM", "allow")
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "permiso de alarmas exactas no concedido; se programa una alarma flexible")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, actionIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, actionIntent)
            }
            Log.i(TAG, "próxima activación Sleep Mode: ${Calendar.getInstance().apply { timeInMillis = next }.time}")
        } catch (e: SecurityException) {
            Log.e(TAG, "no se pudo programar la alarma", e)
        }
    }

    private fun nextActivationMillis(): Long {
        val now = Calendar.getInstance()
        val todayEleven = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (todayEleven.timeInMillis <= now.timeInMillis) todayEleven.add(Calendar.DAY_OF_YEAR, 1)
        return todayEleven.timeInMillis
    }

    private fun pendingIntent(context: Context, enable: Boolean?): PendingIntent {
        val intent = Intent(context, AirplaneModeScheduleReceiver::class.java).setAction(ACTION_TOGGLE)
        if (enable != null) intent.putExtra(EXTRA_ENABLE, enable)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
