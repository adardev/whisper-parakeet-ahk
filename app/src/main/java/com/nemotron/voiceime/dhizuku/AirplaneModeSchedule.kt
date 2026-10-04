package com.nemotron.voiceime.dhizuku

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/** Programa el siguiente cambio diario: activar a las 23:00 y desactivar a las 08:00. */
object AirplaneModeSchedule {
    private const val TAG = "AirplaneSchedule"
    const val ACTION_TOGGLE = "com.nemotron.voiceime.AIRPLANE_SCHEDULE_TOGGLE"
    const val EXTRA_ENABLE = "enable"
    private const val REQUEST_CODE = 2308

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val next = nextBoundaryMillis()
        val pendingIntent = pendingIntent(context, null)
        alarmManager.cancel(pendingIntent)
        val actionIntent = pendingIntent(context, next.second)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() && ShizukuManager.hasPermission()) {
                // Shizuku shell can grant the alarm app-op without sending the user through Settings.
                ShizukuManager.execShellFresh(
                    arrayOf("cmd", "appops", "set", context.packageName, "SCHEDULE_EXACT_ALARM", "allow")
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "permiso de alarmas exactas no concedido; se programa una alarma flexible")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.first, actionIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.first, actionIntent)
            }
            Log.i(TAG, "próximo cambio: ${if (next.second) "activar" else "desactivar"} a ${Calendar.getInstance().apply { timeInMillis = next.first }.time}")
        } catch (e: SecurityException) {
            Log.e(TAG, "no se pudo programar la alarma", e)
        }
    }

    private fun nextBoundaryMillis(): Pair<Long, Boolean> {
        val now = Calendar.getInstance()
        val todayEight = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayEleven = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val tomorrowEight = (todayEight.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }

        // Solo escoger un límite futuro: después de las 23:00, la siguiente
        // alarma es mañana a las 08:00, nunca la hora 23:00 ya pasada.
        return when {
            todayEight.timeInMillis > now.timeInMillis -> todayEight.timeInMillis to false
            todayEleven.timeInMillis > now.timeInMillis -> todayEleven.timeInMillis to true
            else -> tomorrowEight.timeInMillis to false
        }
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
