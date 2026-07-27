package com.nemotron.voiceime.net

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

/**
 * Cliente NVIDIA NIM (OpenAI-compatible) con streaming SSE para Nemotron 3.5.
 *
 * Endpoint: https://integrate.api.nvidia.com/v1/chat/completions  (stream=true)
 * Modelo:   nvidia/llama-3.3-nemotron-super-49b-v1 (Llama 3.3 Nemotron Super 49B v1)
 *
 * La cuenta gratuita de build.nvidia.com da 1000 credits (~ varios miles requests).
 *
 * Formato SSE: cada linea data: {JSON delta con choices[0].delta.content}
 * Fin:  data: [DONE]
 */
class NemotronStreamClient(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val current = AtomicReference<EventSource?>(null)
    @Volatile private var completed = false

    /**
     * Inicia una solicitud streaming.
     * @param userText texto transcribido por SpeechRecognizer
     * @param model modelo Nemotron
     * @param system system prompt del usuario
     * @param onToken se llama en hilo de OkHttp por cada token recibido.
     * @param onComplete texto final armado (hilo OkHttp).
     * @param onError (hilo OkHttp).
     */
    fun stream(
        userText: String,
        model: String,
        system: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (!isConfigured) {
            onError(IllegalStateException("Sin API key"))
            return
        }

        val body = buildJson(userText, model, system).toString()
        completed = false
        Log.d(TAG, "stream start model=$model len=${userText.length}")

        val req = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(body.toRequestBody(JSON_MT))
            .build()

        val factory = EventSources.createFactory(http)
        current.set(factory.newEventSource(req, object : EventSourceListener() {
            val sb = StringBuilder()

            override fun onOpen(eventSource: EventSource, response: Response) {}

            override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    if (!completed) {
                        completed = true
                        onComplete(sb.toString())
                    }
                    es.cancel()
                    current.set(null)
                    return
                }
                try {
                    val obj = JSONObject(data)
                    val delta: String = obj
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content", "")
                        ?: ""
                    if (delta.isNotEmpty()) {
                        sb.append(delta)
                        onToken(delta)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "parse fail: $data", t)
                }
            }

            override fun onClosed(es: EventSource) {
                if (current.get() === es) current.set(null)
            }

            override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                val th = t ?: RuntimeException("unknown")
                Log.e(TAG, "stream failure code=${response?.code}", th)
                val msg = when (response?.code) {
                    401 -> "API key invalida o expirada"
                    429 -> "Limite de uso alcanzado (quota NVIDIA)"
                    in 500..599 -> "Servidor NVIDIA NIM tuvo un problema (${response?.code ?: '?'})"
                    else -> th.message ?: "Error desconocido"
                }
                onError(RuntimeException(msg, th))
                current.set(null)
            }
        }))
    }

    fun cancel() {
        current.getAndSet(null)?.cancel()
    }

    private fun buildJson(userText: String, model: String, system: String): JSONObject {
        val messages = JSONArray()
        if (system.isNotBlank()) {
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", system)
            })
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userText)
        })
        // Nemotron 3 usa enable_thinking=false para modo no-razonamiento (rápido y directo):
        //   - ideal para transcripción de voz a texto
        //   - menos tokens, menos latencia
        // Referencia: build.nvidia.com/nvidia/nemotron-3-nano-30b-a3b
        val chatTemplateKwargs = JSONObject().apply {
            put("enable_thinking", false)
        }
        return JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("stream", true)
            put("temperature", 0.2)        // poca creatividad = transcripción precisa
            put("top_p", 0.7)
            put("max_tokens", 1024)
            put("chat_template_kwargs", chatTemplateKwargs)
        }
    }

    companion object {
        private const val TAG = "NemotronClient"
        const val DEFAULT_BASE_URL = "https://integrate.api.nvidia.com/v1"
        private val JSON_MT = "application/json; charset=utf-8".toMediaType()
    }
}
