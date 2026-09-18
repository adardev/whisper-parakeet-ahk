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

    fun stream(
        message: String,
        model: String,
        history: List<JSONObject>,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", "Eres Hermes, tu agente personal. Responde en espanol. CERO emojis.")
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

        val factory = EventSources.createFactory(http)
        current.set(factory.newEventSource(req, object : EventSourceListener() {
            val sb = StringBuilder()
            override fun onOpen(eventSource: EventSource, response: Response) {}
            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    onComplete(sb.toString())
                    es.cancel()
                    current.set(null)
                    return
                }
                try {
                    val obj = JSONObject(data)
                    val delta = obj
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content", "") ?: ""
                    if (delta.isNotEmpty()) {
                        sb.append(delta)
                        onToken(delta)
                    }
                } catch (t: Throwable) {
                    Log.w("ChatClient", "parse fail", t)
                }
            }
            override fun onClosed(es: EventSource) {
                if (current.get() === es) current.set(null)
            }
            override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                onError(t ?: RuntimeException("Error desconocido"))
                current.set(null)
            }
        }))
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
            put("content", "Eres Hermes, tu agente personal. Responde en espanol. CERO emojis.")
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
}
