package com.nemotron.voiceime.dhizuku

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/** Daily Sleep Mode activation at 23:00; daytime exit is manual only. */
object AirplaneModeSchedule {
    private const val TAG = "AirplaneSchedule"
    const val ACTION_TOGGLE = "com.nemotron.voiceime.FIT3_AOD_SCHEDULE_TOGGLE"
    const val ACTION_RETRY = "com.nemotron.voiceime.SLEEP_MODE_RETRY"
    const val EXTRA_ENABLE = "enable"
    const val EXTRA_RETRY = "retry"
    private const val REQUEST_ENABLE = 2308
    private const val REQUEST_DISABLE = 2309
    private const val REQUEST_RETRY = 2310
    private const val RETRY_DELAY_MS = 10 * 60 * 1000L

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        // Cancel the legacy 08:00 disable alarm and any retry created by an
        // older build. Sleep Mode may only activate automatically at 23:00.
        alarmManager.cancel(pendingIntent(context, false, REQUEST_DISABLE))
        cancelRetry(context)
        // Only Core owns automation after the three-package split.
        if (context.packageName != "com.nemotron.core") {
            alarmManager.cancel(pendingIntent(context, true, REQUEST_ENABLE))
            return
        }
        scheduleDaily(context, 23, 0, true, REQUEST_ENABLE, "activación")
    }

    /** One bounded retry handles a transient Shizuku or BLE failure. */
    fun scheduleRetry(context: Context, enabled: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (!enabled) {
            cancelRetry(context)
            Log.w(TAG, "se descarta el reintento de apagado automático; Sleep Mode solo se desactiva manualmente")
            return
        }
        val intent = Intent(context, AirplaneModeScheduleReceiver::class.java)
            .setAction(ACTION_RETRY)
            .putExtra(EXTRA_ENABLE, enabled)
            .putExtra(EXTRA_RETRY, true)
        val retry = PendingIntent.getBroadcast(
            context, REQUEST_RETRY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val at = System.currentTimeMillis() + RETRY_DELAY_MS
        try {
            alarmManager.cancel(retry)
            scheduleAlarm(alarmManager, context, at, retry)
            Log.i(TAG, "reintento Sleep Mode en 10 minutos; enable=$enabled")
        } catch (e: SecurityException) {
            Log.e(TAG, "no se pudo programar el reintento de Sleep Mode", e)
        }
    }

    fun cancelRetry(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(retryPendingIntent(context))
    }

    /** Only a delayed 23:00 activation may run during the overnight window. */
    fun isScheduledActionCurrentWindow(enabled: Boolean): Boolean {
        if (!enabled) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 8
    }

    private fun scheduleDaily(
        context: Context,
        hour: Int,
        minute: Int,
        enabled: Boolean,
        requestCode: Int,
        label: String,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = nextDailyMillis(hour, minute)
        val alarm = pendingIntent(context, enabled, requestCode)
        try {
            alarmManager.cancel(alarm)
            scheduleAlarm(alarmManager, context, at, alarm)
            Log.i(TAG, "próxima $label Sleep Mode: ${Calendar.getInstance().apply { timeInMillis = at }.time}")
        } catch (e: SecurityException) {
            Log.e(TAG, "no se pudo programar la $label de Sleep Mode", e)
        }
    }

    private fun scheduleAlarm(alarmManager: AlarmManager, context: Context, at: Long, alarm: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms() && ShizukuManager.hasPermission()) {
            // Shizuku shell can grant the alarm app-op without sending the user through Settings.
            ShizukuManager.execShellFresh(
                arrayOf("cmd", "appops", "set", context.packageName, "SCHEDULE_EXACT_ALARM", "allow")
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "permiso de alarmas exactas no concedido; se programa una alarma flexible")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm)
        }
    }

    private fun nextDailyMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis
    }

    private fun pendingIntent(context: Context, enabled: Boolean, requestCode: Int): PendingIntent {
        val intent = Intent(context, AirplaneModeScheduleReceiver::class.java)
            .setAction(ACTION_TOGGLE)
            .putExtra(EXTRA_ENABLE, enabled)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun retryPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AirplaneModeScheduleReceiver::class.java).setAction(ACTION_RETRY)
        return PendingIntent.getBroadcast(
            context, REQUEST_RETRY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
