package com.nemotron.voiceime.health

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Actualiza la copia de Samsung Health solo mientras el tile Fit3 está activo. */
class HealthSnapshotJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transfer: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!getSharedPreferences(HealthSyncScheduler.PREFERENCES, MODE_PRIVATE)
                .getBoolean(HealthSyncScheduler.ENABLED, false)) {
            jobFinished(params, false)
            return false
        }
        transfer = scope.launch {
            try {
                val manager = SamsungHealthManager(this@HealthSnapshotJobService, scope)
                if (manager.grantedPermissions().isEmpty()) {
                    Log.w(TAG, "Sync periódico omitido: Samsung Health sin permisos")
                    return@launch
                }
                val end = Instant.now()
                val start = end.minusSeconds(BACKFILL_DAYS * 24L * 60L * 60L)
                val data = manager.readAllData(start, end)
                val date = LocalDate.now(ZoneId.systemDefault()).toString()
                val snapshot = JSONObject()
                    .put("type", "health_snapshot")
                    .put("date", date)
                    .put("device", "samsung-${android.os.Build.MODEL}")
                    .put("data", data)
                FirebaseHealthUploader(this@HealthSnapshotJobService).upload(date, snapshot)
                Log.i(TAG, "Sync periódico Samsung Health completado: $date")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sync periódico Samsung Health falló", e)
            } finally {
                withContext(Dispatchers.Main) { jobFinished(params, false) }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        transfer?.cancel()
        transfer = null
        return false // La siguiente ejecución periódica vuelve a intentarlo.
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HealthSnapshotJob"
        private const val BACKFILL_DAYS = 30L
    }
}
