package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Asks the NAS to query Uber's approved Driver API and persist a daily snapshot. */
internal object UberSnapshotClient {
    private const val TAG = "UberSnapshot"
    private const val DEFAULT_SERVER = "https://adarlpz-2.tail4988cb.ts.net"
    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(4))
        .readTimeout(Duration.ofSeconds(12))
        .writeTimeout(Duration.ofSeconds(4))
        .callTimeout(Duration.ofSeconds(15))
        .build()

    fun capture(context: Context, action: String) {
        val server = context.getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)
            .getString("server_url", DEFAULT_SERVER)
            ?.trim()?.trimEnd('/')
            ?.takeIf { it.startsWith("https://") }
            ?: DEFAULT_SERVER
        val payload = JSONObject()
            .put("action", action)
            .put("eventId", UUID.randomUUID().toString())
            .put("deviceCapturedAt", Instant.now().toString())
        val request = Request.Builder()
            .url("$server/api/uber/snapshot")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Snapshot $action not confirmed: HTTP ${response.code}")
                    return
                }
                val result = runCatching { JSONObject(body) }.getOrNull()
                if (result?.optBoolean("valid", false) == true &&
                    result.optBoolean("stored", false)) {
                    Log.i(TAG, "Snapshot $action stored for ${result.optString("date")}")
                } else {
                    Log.w(TAG, "Snapshot $action unavailable or not stored")
                }
            }
        } catch (t: Throwable) {
            // Snapshot failure must not prevent the tile from changing Uber's
            // package state. The server's no-valid-snapshot rule prevents a charge.
            Log.w(TAG, "Snapshot $action could not reach the NAS", t)
        }
    }
}
