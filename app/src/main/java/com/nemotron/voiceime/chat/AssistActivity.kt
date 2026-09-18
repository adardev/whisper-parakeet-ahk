package com.nemotron.voiceime.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.nemotron.voiceime.R

class AssistActivity : Activity() {
    private lateinit var panel: View
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var chat: ChatClient
    private var conversationId: String? = null
    private var speech: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_assist)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.setDimAmount(0.18f)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.attributes = window.attributes.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.FILL
        }

        val prefs = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", null)
        val base = if (savedUrl.isNullOrBlank() || savedUrl.startsWith("http://100.115.113.28")) {
            prefs.edit().putString("server_url", "http://192.168.0.2:8888").apply()
            "http://192.168.0.2:8888"
        } else savedUrl
        chat = ChatClient(base)
        input = findViewById(R.id.assistInput)
        status = findViewById(R.id.assistStatus)
        panel = findViewById(R.id.assistPanel)
        findViewById<View>(R.id.assistRoot).apply {
            isClickable = true
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) finish()
                true
            }
        }
        var downY = 0f
        panel.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = event.rawY; true }
                MotionEvent.ACTION_UP -> {
                    if (downY - event.rawY > dp(48)) { haptic(view); openFullChat() }
                    true
                }
                else -> true
            }
        }
        findViewById<ImageButton>(R.id.assistSend).setOnClickListener { haptic(it); send() }
        findViewById<ImageButton>(R.id.assistAttach).setOnClickListener { haptic(it); showAttachmentMenu(it) }
        findViewById<ImageButton>(R.id.assistMic).setOnClickListener { haptic(it); listen() }
        input.setOnEditorActionListener { _, _, _ -> send(); true }
        // El asistente de voz abre limpio: el teclado solo aparece cuando el usuario toca el campo.
        findViewById<android.view.View>(android.R.id.content).requestFocus()
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                input.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
            }
        }
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val imeBottom = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            // Gravity.BOTTOM: un desplazamiento positivo levanta el panel por encima del teclado.
            window.attributes = window.attributes.apply { y = if (imeBottom > 0) imeBottom + dp(10) else 0 }
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

    private fun openFullChat() {
        val target = if (conversationId == null) Intent(this, ChatsListActivity::class.java)
        else Intent(this, ChatActivity::class.java).putExtra("convId", conversationId)
        panel.animate()
            .translationY(-panel.height.toFloat())
            .scaleX(0.96f).scaleY(0.96f)
            .alpha(0.2f)
            .setDuration(220)
            .withEndAction { startActivity(target); finish() }
            .start()
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

    private fun showAttachmentMenu(anchor: View) {
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#171B24")); cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#294A78"))
            }
        }
        menu.addView(attachmentRow(R.drawable.ic_camera, "Cámara") { launchAttachment("camera") })
        menu.addView(attachmentRow(R.drawable.ic_gallery, "Fotos") { launchAttachment("gallery") })
        menu.addView(attachmentRow(R.drawable.ic_file, "Archivos") { launchAttachment("file") })
        PopupWindow(menu, dp(190), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(18).toFloat()
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            isOutsideTouchable = true
            showAsDropDown(anchor, -dp(12), -dp(170))
        }
    }

    private fun attachmentRow(icon: Int, label: String, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(10)); isClickable = true
        addView(android.widget.ImageView(context).apply {
            setImageResource(icon); setColorFilter(Color.parseColor("#8FC1FF"))
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        })
        addView(TextView(context).apply {
            text = label; textSize = 15f; setTextColor(Color.WHITE)
            setPadding(dp(12), 0, 0, 0)
        })
        setOnClickListener { haptic(this); click() }
    }

    private fun launchAttachment(kind: String) {
        val intent = when (kind) {
            "camera" -> Intent("android.media.action.IMAGE_CAPTURE")
            "gallery" -> Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            else -> Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }
        }
        startActivityForResult(intent, 700)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 700 && resultCode == RESULT_OK) {
            val name = data?.data?.lastPathSegment ?: "archivo seleccionado"
            input.setText("[Adjunto: $name] ")
            input.setSelection(input.length())
            input.requestFocus()
        }
    }

    override fun onDestroy() { speech?.destroy(); super.onDestroy() }

    private fun haptic(view: android.view.View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
