package com.nemotron.voiceime.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nemotron.voiceime.R
import org.json.JSONObject

class ChatActivity : Activity() {

    private var convId: String? = null
    private var conversation: Conversation? = null
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: MessageAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var input: EditText
    private lateinit var modelChip: TextView
    private lateinit var incogBtn: ImageButton
    private lateinit var micBtn: ImageButton
    private lateinit var chat: ChatClient

    private val models = listOf("deepseek-flash", "mimo-v2.5", "nemotron")
    private var modelIndex = 0
    private var listening = false
    private var speech: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        ConversationStore.init(this)

        recycler = findViewById(R.id.recyclerMessages)
        input = findViewById(R.id.inputField)
        modelChip = findViewById(R.id.modelChip)
        micBtn = findViewById(R.id.btnMic)
        incogBtn = findViewById(R.id.btnIncognito)
        val backBtn: ImageButton = findViewById(R.id.btnBack)
        val sendBtn: ImageButton = findViewById(R.id.btnSend)
        val delBtn: ImageButton = findViewById(R.id.btnDelete)
        val titleV: TextView = findViewById(R.id.convTitle)

        window.statusBarColor = Color.parseColor("#0B0C0F")
        window.navigationBarColor = Color.parseColor("#0B0C0F")

        val base = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)
            .getString("server_url", "http://100.115.113.28:8888") ?: "http://100.115.113.28:8888"
        chat = ChatClient(base)

        convId = intent.getStringExtra("convId")
        conversation = convId?.let { ConversationStore.get(it) }
        if (conversation == null) {
            conversation = ConversationStore.create()
            convId = conversation!!.id
        }

        messages.clear()
        messages.addAll(conversation!!.messages)
        titleV.text = if (conversation!!.title == "Nuevo chat") "Hermes" else conversation!!.title

        adapter = MessageAdapter(messages)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        if (messages.isNotEmpty()) recycler.scrollToPosition(messages.size - 1)

        backBtn.setOnClickListener { goBack() }
        sendBtn.setOnClickListener { doSend() }
        input.setOnEditorActionListener { _, _, _ -> doSend(); true }

        modelChip.text = displayName(models[0])
        modelChip.setOnClickListener {
            modelIndex = (modelIndex + 1) % models.size
            modelChip.text = displayName(models[modelIndex])
        }

        micBtn.setOnClickListener {
            if (!checkMic()) return@setOnClickListener
            if (listening) {
                speech?.stopListening()
                return@setOnClickListener
            }
            startListening()
        }

        incogBtn.setOnClickListener {
            setIncognito(!isIncognito())
            updateIncognitoUi()
            Toast.makeText(
                this,
                if (isIncognito()) "Modo incognito: ON" else "Modo incognito: OFF",
                Toast.LENGTH_SHORT
            ).show()
        }
        incogBtn.setOnLongClickListener { true }

        delBtn.setOnClickListener {
            ConversationStore.delete(convId!!)
            Toast.makeText(this, "Conversacion eliminada", Toast.LENGTH_SHORT).show()
            finish()
        }

        updateIncognitoUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            speech?.destroy()
        } catch (e: Exception) {
        }
    }

    private fun goBack() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        finish()
    }

    private fun doSend() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        hideKeyboard()

        val conv = conversation ?: return

        appendUi("user", text)

        if (conv.title == "Nuevo chat") {
            conv.title = if (text.length > 32) text.substring(0, 32) else text
            titleView().text = conv.title
        }

        val history = conv.messages.map {
            JSONObject().apply { put("role", it.role); put("content", it.content) }
        }

        if (!isIncognito()) {
            conv.messages.add(ChatMessage("user", text))
            ConversationStore.save(conv)
        }

        appendUi("assistant", "")
        val bubbleIndex = messages.size - 1
        recycler.scrollToPosition(messages.size - 1)
        micBtn.isEnabled = false

        chat.stream(
            text,
            models[modelIndex],
            history,
            onToken = { token ->
                runOnUiThread {
                    if (bubbleIndex < messages.size) {
                        messages[bubbleIndex] = messages[bubbleIndex].copy(content = messages[bubbleIndex].content + token)
                        adapter.notifyItemChanged(bubbleIndex)
                        recycler.scrollToPosition(bubbleIndex)
                    }
                }
            },
            onComplete = { full ->
                runOnUiThread {
                    if (bubbleIndex < messages.size) {
                        messages[bubbleIndex] = messages[bubbleIndex].copy(content = full)
                        adapter.notifyItemChanged(bubbleIndex)
                        recycler.scrollToPosition(bubbleIndex)
                    }
                    micBtn.isEnabled = true
                    if (!isIncognito()) {
                        conv.messages.add(ChatMessage("assistant", full))
                        ConversationStore.save(conv)
                    }
                }
            },
            onError = { err ->
                runOnUiThread {
                    if (bubbleIndex < messages.size) {
                        messages[bubbleIndex] = messages[bubbleIndex].copy(content = "Error: ${err.message ?: "sin conexion al servidor"}")
                        adapter.notifyItemChanged(bubbleIndex)
                    }
                    micBtn.isEnabled = true
                }
            }
        )
    }

    private fun appendUi(role: String, content: String) {
        messages.add(ChatMessage(role, content))
        adapter.notifyItemInserted(messages.size - 1)
        recycler.scrollToPosition(messages.size - 1)
    }

    private fun titleView(): TextView = findViewById(R.id.convTitle)

    // ---- mic / speech ----

    private fun checkMic(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun startListening() {
        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speech = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {
                    listening = true
                    runOnUiThread { Toast.makeText(this@ChatActivity, "Escuchando...", Toast.LENGTH_SHORT).show() }
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    listening = false
                }
                override fun onError(error: Int) {
                    listening = false
                    runOnUiThread {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No te entendi. Intenta de nuevo."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detecto voz."
                            else -> "Error de reconocimiento ($error)"
                        }
                        Toast.makeText(this@ChatActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val r = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!r.isNullOrEmpty()) {
                        input.setText(r[0])
                        input.setSelection(r[0].length)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer.startListening(intent)
            listening = true
        } catch (e: Exception) {
            Toast.makeText(this, "Audio no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- incognito / helpers ----

    private fun isIncognito() = prefs().getBoolean("incognito", false)
    private fun setIncognito(v: Boolean) = prefs().edit().putBoolean("incognito", v).apply()
    private fun prefs() = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)

    private fun updateIncognitoUi() {
        val on = isIncognito()
        incogBtn.colorFilter = android.graphics.PorterDuffColorFilter(
            if (on) Color.parseColor("#7C83FD") else Color.parseColor("#5A5A6E"),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    private fun displayName(m: String): String = when (m) {
        "deepseek-flash" -> "DeepSeek"
        "mimo-v2.5" -> "MiMo"
        else -> "Nemotron"
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }
}