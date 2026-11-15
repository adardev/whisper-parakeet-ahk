package com.nemotron.voiceime.dhizuku

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

/** Retries persisted tile events only when Android reports network connectivity. */
class UberSnapshotJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var delivery: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        delivery = scope.launch {
            val retry = try {
                UberSnapshotClient.flushPending(this@UberSnapshotJobService)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Uber tile event retry failed", e)
                true
            }
            withContext(Dispatchers.Main) {
                if (!retry) {
                    val scheduler = getSystemService(JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
                    scheduler.cancel(JOB_ID)
                }
                jobFinished(params, retry)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        delivery?.cancel()
        delivery = null
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UberSnapshotJob"
        private const val JOB_ID = 3407
    }
}
