package com.nemotron.voiceime.chat

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ChatClient(
    private val baseUrl: String,
    private val apiKey: String = ""
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val current = AtomicReference<EventSource?>(null)
    private val JSON_MT = "application/json; charset=utf-8".toMediaType()

    fun conversations(onComplete: (JSONArray) -> Unit, onError: (Throwable) -> Unit) {
        request("$baseUrl/api/conversations", "GET", null, { json ->
            onComplete(json.optJSONArray("conversations") ?: JSONArray())
        }, onError)
    }

    fun conversation(id: String, onComplete: (JSONObject) -> Unit, onError: (Throwable) -> Unit) {
        request("$baseUrl/api/conversations/$id", "GET", null, onComplete, onError)
    }

    fun createConversation(onComplete: (JSONObject) -> Unit, onError: (Throwable) -> Unit) {
        request("$baseUrl/api/conversations", "POST", JSONObject(), onComplete, onError)
    }

    fun deleteConversation(id: String, onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        request("$baseUrl/api/conversations/$id", "DELETE", null, { onComplete() }, onError)
    }

    fun stream(
        message: String,
        model: String,
        history: List<JSONObject>,
        conversationId: String? = null,
        incognito: Boolean = false,
        imageData: String? = null,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", "Eres adarbot, tu agente personal. Responde en espanol. CERO emojis.")
        })
        for (msg in history) { messages.put(msg) }
        val userContent: Any = if (!imageData.isNullOrBlank()) JSONArray().apply {
            put(JSONObject().apply { put("type", "text"); put("text", message) })
            put(JSONObject().apply { put("type", "image_url"); put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageData")) })
        } else message
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userContent)
        })

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            conversationId?.let { put("conversation_id", it) }
            put("incognito", incognito)
            put("save", !incognito)
        }.toString()

        val req = Request.Builder()
            .url("$baseUrl/api/chat")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MT))
            .build()

        Thread {
            try {
                http.newCall(req).execute().use { response ->
                    val obj = JSONObject(response.body?.string() ?: "{}")
                    if (!response.isSuccessful) throw RuntimeException(obj.optString("error", "HTTP ${response.code}"))
                    val answer = obj.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content", "") ?: ""
                    if (answer.isNotEmpty()) onToken(answer)
                    onComplete(answer)
                }
            } catch (e: Exception) { onError(e) }
        }.start()
    }

    fun send(
        message: String,
        model: String,
        history: List<JSONObject>,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", "Eres adarbot, tu agente personal. Responde en espanol. CERO emojis.")
        })
        for (msg in history) { messages.put(msg) }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", message)
        })

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
        }.toString()

        val req = Request.Builder()
            .url("$baseUrl/api/chat")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MT))
            .build()

        Thread {
            try {
                val resp = http.newCall(req).execute()
                val json = JSONObject(resp.body?.string() ?: "{}")
                val content = json
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "") ?: ""
                onComplete(content)
            } catch (e: Exception) {
                onError(e)
            }
        }.start()
    }

    fun cancel() {
        current.getAndSet(null)?.cancel()
    }

    private fun request(url: String, method: String, payload: JSONObject?, ok: (JSONObject) -> Unit, fail: (Throwable) -> Unit) {
        val builder = Request.Builder().url(url)
        when (method) {
            "POST" -> builder.post((payload ?: JSONObject()).toString().toRequestBody(JSON_MT))
            "DELETE" -> builder.delete()
            else -> builder.get()
        }
        Thread {
            try {
                http.newCall(builder.build()).execute().use { response ->
                    val body = JSONObject(response.body?.string() ?: "{}")
                    if (!response.isSuccessful) throw RuntimeException(body.optString("error", "HTTP ${response.code}"))
                    ok(body)
                }
            } catch (e: Exception) { fail(e) }
        }.start()
    }
}
