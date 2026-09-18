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
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.nemotron.voiceime.R

class AssistActivity : Activity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var chat: ChatClient
    private var conversationId: String? = null
    private var speech: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_assist)
        window.setDimAmount(0.18f)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.attributes = window.attributes.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM
        }

        val base = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)
            .getString("server_url", "http://100.115.113.28:8888") ?: "http://100.115.113.28:8888"
        chat = ChatClient(base)
        input = findViewById(R.id.assistInput)
        status = findViewById(R.id.assistStatus)
        findViewById<ImageButton>(R.id.assistClose).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.assistExpand).setOnClickListener {
            val target = if (conversationId == null) {
                Intent(this, ChatsListActivity::class.java)
            } else {
                Intent(this, ChatActivity::class.java).putExtra("convId", conversationId)
            }
            startActivity(target)
            finish()
        }
        findViewById<ImageButton>(R.id.assistSend).setOnClickListener { send() }
        findViewById<ImageButton>(R.id.assistMic).setOnClickListener { listen() }
        input.setOnEditorActionListener { _, _, _ -> send(); true }
        input.requestFocus()
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val imeBottom = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            window.attributes = window.attributes.apply { y = imeBottom }
            view.onApplyWindowInsets(insets)
        }
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText(""); status.text = "Pensando..."
        val id = conversationId
        val done: (String) -> Unit = { answer -> runOnUiThread { status.text = answer.ifEmpty { "Listo" }.take(72) } }
        val fail: (Throwable) -> Unit = { e -> runOnUiThread { status.text = "Sin conexión: ${e.message ?: "error"}" } }
        val start: (String) -> Unit = { cid ->
            conversationId = cid
            chat.stream(text, "deepseek-flash", emptyList(), cid, false, {}, done, fail)
        }
        if (id != null) start(id) else chat.createConversation({ runOnUiThread { start(it.optString("id")) } }, fail)
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 42); return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this); speech = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { runOnUiThread { status.text = "Te escucho..." } }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) { runOnUiThread { status.text = "No te escuché" } }
            override fun onResults(b: Bundle?) { val r = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (!r.isNullOrEmpty()) { input.setText(r[0]); send() } }
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
        })
    }

    override fun onDestroy() { speech?.destroy(); super.onDestroy() }
}
