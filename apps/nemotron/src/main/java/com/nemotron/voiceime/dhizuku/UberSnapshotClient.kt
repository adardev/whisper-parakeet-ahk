package com.nemotron.voiceime.dhizuku

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nemotron.voiceime.health.FirebaseHealthUploader
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Durable Uber tile-event queue; no earnings or OAuth secrets are stored locally. */
internal object UberSnapshotClient {
    private const val TAG = "UberSnapshot"
    private const val DEFAULT_SERVER = "https://adarlpz-2.tail4988cb.ts.net"
    private const val PREFS = "uber_snapshot_events"
    private const val QUEUE_KEY = "pending"
    private const val JOB_ID = 3407
    private val queueLock = Any()
    private val deliveryLock = Any()
    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(4))
        .readTimeout(Duration.ofSeconds(12))
        .writeTimeout(Duration.ofSeconds(4))
        .callTimeout(Duration.ofSeconds(15))
        .build()

    /** Called after a confirmed tile state transition, from its worker thread. */
    fun capture(context: Context, action: String, wallet: JSONObject? = null) {
        if (action !in setOf("enabled", "disabled")) return
        val event = JSONObject()
            .put("action", action)
            .put("eventId", UUID.randomUUID().toString())
            .put("deviceCapturedAt", Instant.now().toString())
        wallet?.let { event.put("wallet", it) }
        val saved = synchronized(queueLock) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val pending = readQueue(prefs)
            pending.put(event)
            // Commit before networking: a process kill cannot discard this toggle.
            if (!prefs.edit().putString(QUEUE_KEY, pending.toString()).commit()) {
                Log.e(TAG, "Could not persist tile event $action")
                false
            } else {
                true
            }
        }
        if (saved) scheduleRetry(context)
    }

    /** Returns true while at least one event still needs server confirmation. */
    fun flushPending(context: Context): Boolean = synchronized(deliveryLock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (synchronized(queueLock) { readQueue(prefs).length() == 0 }) return@synchronized false

        val server = context.getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)
            .getString("server_url", DEFAULT_SERVER)
            ?.trim()?.trimEnd('/')
            ?.takeIf { it.startsWith("https://") }
            ?: DEFAULT_SERVER

        val idToken = try {
            FirebaseHealthUploader(context).freshIdToken()
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase auth unavailable; tile events remain queued", t)
            return@synchronized true
        }

        while (true) {
            val pendingEvent = synchronized(queueLock) {
                val queue = readQueue(prefs)
                if (queue.length() == 0) null else queue.optJSONObject(0)
            }
            val event = pendingEvent
            if (event == null) {
                val stillHasEvents = synchronized(queueLock) { readQueue(prefs).length() > 0 }
                if (!stillHasEvents) return@synchronized false
                Log.w(TAG, "Discarding malformed Uber tile queue entry")
                synchronized(queueLock) {
                    val queue = readQueue(prefs)
                    val cleaned = removeAt(queue, 0)
                    prefs.edit().putString(QUEUE_KEY, cleaned.toString()).commit()
                }
                continue
            }
            val eventId = event.optString("eventId")
            try {
                val request = Request.Builder()
                    .url("$server/api/uber/snapshot")
                    .post(event.toString().toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer $idToken")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 401) FirebaseHealthUploader.invalidateCachedAuth()
                    val body = response.body?.string().orEmpty()
                    val result = runCatching { JSONObject(body) }.getOrNull()
                    if (!response.isSuccessful || result?.optBoolean("stored", false) != true) {
                        Log.w(TAG, "Tile event ${event.optString("action")} not confirmed: HTTP ${response.code}")
                        return@synchronized true
                    }
                    val removed = synchronized(queueLock) {
                        val queue = readQueue(prefs)
                        val index = (0 until queue.length()).firstOrNull {
                            queue.optJSONObject(it)?.optString("eventId") == eventId
                        }
                        if (index == null) true else {
                            val remaining = removeAt(queue, index)
                            prefs.edit().putString(QUEUE_KEY, remaining.toString()).commit()
                        }
                    }
                    if (!removed) {
                        Log.e(TAG, "Server stored $eventId, but local queue cleanup failed")
                        return@synchronized true
                    }
                    if (result.optBoolean("valid", false)) {
                        Log.i(TAG, "Earnings snapshot ${event.optString("action")} stored for ${result.optString("date")}")
                    } else {
                        Log.i(TAG, "Tile event ${event.optString("action")} stored; earnings source unavailable")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Tile event $eventId could not reach the NAS; it remains queued", t)
                return@synchronized true
            }
        }
        false
    }

    fun scheduleRetry(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, UberSnapshotJobService::class.java)
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(0L)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setPersisted(true)
            .build()
        if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            Log.w(TAG, "Could not schedule delivery retry for pending Uber tile events")
        }
    }

    fun hasPendingEvents(context: Context): Boolean = synchronized(queueLock) {
        readQueue(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).length() > 0
    }

    private fun readQueue(prefs: android.content.SharedPreferences): JSONArray = try {
        JSONArray(prefs.getString(QUEUE_KEY, "[]"))
    } catch (_: Exception) {
        JSONArray()
    }

    private fun removeAt(source: JSONArray, index: Int): JSONArray = JSONArray().apply {
        for (i in 0 until source.length()) if (i != index) put(source.get(i))
    }
}
