package com.nemotron.voiceime.health

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log

/** Sincronización de baja frecuencia, habilitada exclusivamente por el tile Fit3. */
object HealthSyncScheduler {
    const val PREFERENCES = "fit3_health_sync"
    const val ENABLED = "enabled"
    private const val JOB_ID = 3403
    private const val TAG = "HealthSyncScheduler"
    private const val INTERVAL_MILLIS = 60L * 60L * 1000L
    private const val FLEX_MILLIS = 15L * 60L * 1000L

    fun enable(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, true).apply()
        schedule(context)
    }

    fun disable(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, false).apply()
        (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler)
            .cancel(JOB_ID)
    }

    fun schedule(context: Context) {
        if (!context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(ENABLED, false)) return
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, HealthSnapshotJobService::class.java)
        )
            .setPeriodic(INTERVAL_MILLIS, FLEX_MILLIS)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .build()
        val result = scheduler.schedule(job)
        if (result != JobScheduler.RESULT_SUCCESS) {
            Log.w(TAG, "No se pudo programar sync de Samsung Health")
        }
    }
}
