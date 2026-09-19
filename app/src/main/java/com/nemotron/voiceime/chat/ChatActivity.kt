package com.nemotron.voiceime.chat

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.HapticFeedbackConstants
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.PopupWindow
import android.view.Gravity
import android.view.ViewGroup
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nemotron.voiceime.R
import org.json.JSONObject
import org.json.JSONArray

class ChatActivity : Activity() {

    private var convId: String? = null
    private var conversation: Conversation? = null
    private var incognitoMode = false
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: MessageAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var input: EditText
    private lateinit var welcomeView: TextView
    private lateinit var modelChip: TextView
    private lateinit var micBtn: ImageButton
    private lateinit var deleteBtn: ImageButton
    private lateinit var incognitoHomeBtn: ImageButton
    private lateinit var chat: ChatClient
    private lateinit var connectionDot: View

    private val models = listOf("deepseek-flash", "mimo-v2.5", "nemotron")
    private var modelIndex = 0
    private var listening = false
    private var speech: SpeechRecognizer? = null
    private var pendingImageData: String? = null
    private var pendingImageBitmap: Bitmap? = null
    private val thinkingLabels = listOf(
        "conectando con adarbot…",
        "adarbot está pensando…",
        "preparando respuesta…"
    )
    private val thinkingHandler = Handler(Looper.getMainLooper())
    private var thinkingIndex = -1
    private var thinkingStep = 0
    private val thinkingRunnable = object : Runnable {
        override fun run() {
            if (thinkingIndex !in messages.indices) return
            messages[thinkingIndex] = messages[thinkingIndex].copy(
                content = thinkingLabels[thinkingStep % thinkingLabels.size]
            )
            adapter.notifyItemChanged(thinkingIndex)
            recycler.scrollToPosition(thinkingIndex)
            thinkingStep++
            thinkingHandler.postDelayed(this, 700L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        ConversationStore.init(this)
        ConversationStore.purgeEmpty()

        recycler = findViewById(R.id.recyclerMessages)
        input = findViewById(R.id.inputField)
        welcomeView = findViewById(R.id.welcomeView)
        modelChip = findViewById(R.id.modelChip)
        micBtn = findViewById(R.id.btnMic)
        incognitoHomeBtn = findViewById(R.id.btnIncognitoHome)
        findViewById<ImageButton>(R.id.btnAttach).setOnClickListener { haptic(it); showAttachmentMenu(it) }
        val backBtn: ImageButton = findViewById(R.id.btnBack)
        findViewById<ImageButton>(R.id.btnMenuChat).setOnClickListener {
            haptic(it)
            showDrawer()
        }
        val sendBtn: ImageButton = findViewById(R.id.btnSend)
        deleteBtn = findViewById(R.id.btnDelete)
        val titleV: TextView = findViewById(R.id.convTitle)
        val attachmentPreview = findViewById<View>(R.id.chatAttachmentPreview)
        val attachmentImage = findViewById<ImageView>(R.id.chatAttachmentImage)
        attachmentImage.setOnClickListener { showImagePreview(pendingImageBitmap) }
        findViewById<ImageButton>(R.id.chatAttachmentRemove).setOnClickListener {
            haptic(it)
            attachmentPreview.visibility = View.GONE
            attachmentImage.setImageDrawable(null)
            pendingImageData = null
            pendingImageBitmap?.recycle()
            pendingImageBitmap = null
        }
        connectionDot = findViewById(R.id.connectionDot)

        window.statusBarColor = Color.parseColor("#090E17")
        window.navigationBarColor = Color.parseColor("#090E17")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val contentRoot = findViewById<View>(android.R.id.content)
        contentRoot.setOnApplyWindowInsetsListener { view, insets ->
            val bottom = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottom)
            view.onApplyWindowInsets(insets)
        }

        val prefs = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", null)
        val base = if (savedUrl.isNullOrBlank() || !savedUrl.startsWith("https://adarlpz-2.tail4988cb.ts.net")) {
            prefs.edit().putString("server_url", "https://adarlpz-2.tail4988cb.ts.net").apply()
            "https://adarlpz-2.tail4988cb.ts.net"
        } else savedUrl
        chat = ChatClient(base)

        convId = intent.getStringExtra("convId")
        incognitoMode = intent.getBooleanExtra("incognito", false)
        conversation = if (incognitoMode) {
            Conversation("incognito_${System.currentTimeMillis()}", "Chat incógnito", System.currentTimeMillis())
        } else convId?.let { ConversationStore.get(it) }
        // Un chat nuevo no se persiste hasta que el usuario envía el primer mensaje.
        if (intent.getStringExtra("convId").isNullOrBlank()) backBtn.visibility = View.GONE
        intent.getStringExtra("draft")?.takeIf { it.isNotBlank() }?.let { draft ->
            window.decorView.postDelayed({ input.setText(draft); doSend() }, 220)
        }

        messages.clear()
        conversation?.let { messages.addAll(it.messages) }
        titleV.text = if (incognitoMode) "adarbot" else conversation?.let { if (it.title == "Nuevo chat") "adarbot" else it.title } ?: "adarbot"
        connectionDot.visibility = if (conversation == null && !incognitoMode) View.VISIBLE else View.GONE
        chat.conversations({ runOnUiThread { connectionDot.setBackgroundResource(R.drawable.bg_connection_online) } }, { runOnUiThread { connectionDot.setBackgroundResource(R.drawable.bg_connection_offline) } })
        updateIncognitoUi()

        adapter = MessageAdapter(messages)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        showWelcomeIfEmpty()
        if (messages.isNotEmpty()) recycler.scrollToPosition(messages.size - 1)

        if (!incognitoMode) convId?.let { id ->
            chat.conversation(id, { remote ->
                val remoteMessages = mutableListOf<ChatMessage>()
                val arr = remote.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    remoteMessages.add(ChatMessage(m.optString("role"), m.optString("content"), m.optLong("created_at")))
                }
                runOnUiThread {
                    messages.clear(); messages.addAll(remoteMessages)
                    showWelcomeIfEmpty()
                    adapter.notifyDataSetChanged()
                    conversation?.messages?.clear(); conversation?.messages?.addAll(remoteMessages)
                    if (messages.isNotEmpty()) recycler.scrollToPosition(messages.size - 1)
                }
            }, {})
        }

        backBtn.setOnClickListener { haptic(it); goBack() }
        incognitoHomeBtn.setOnClickListener {
            haptic(it)
            if (messages.isEmpty()) toggleIncognitoMode()
        }
        sendBtn.setOnClickListener { haptic(it); doSend() }
        input.setOnEditorActionListener { _, _, _ -> doSend(); true }

        modelChip.text = displayName(models[0])
        modelChip.setOnClickListener {
            haptic(it)
            modelIndex = (modelIndex + 1) % models.size
            modelChip.text = displayName(models[modelIndex])
        }

        micBtn.setOnClickListener {
            haptic(it)
            if (!checkMic()) return@setOnClickListener
            if (listening) {
                speech?.stopListening()
                listening = false
                setMicListening(false)
                return@setOnClickListener
            }
            startListening()
        }

        deleteBtn.setOnClickListener {
            haptic(it)
            convId?.let { id -> ConversationStore.delete(id) }
            Toast.makeText(this, "Conversacion eliminada", Toast.LENGTH_SHORT).show()
            finish()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        thinkingHandler.removeCallbacks(thinkingRunnable)
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
        val text = input.text.toString().trim().ifEmpty {
            if (pendingImageData != null) "Analiza esta imagen." else return
        }
        val existing = conversation
        if (existing == null && !incognitoMode) {
            chat.createConversation({ json ->
                val id = json.optString("id").ifBlank { System.currentTimeMillis().toString() }
                runOnUiThread {
                    conversation = Conversation(id, if (text.length > 32) text.substring(0, 32) else text, System.currentTimeMillis())
                    convId = id
                    updateIncognitoUi()
                    doSend()
                }
            }, {
                runOnUiThread {
                    conversation = Conversation(System.currentTimeMillis().toString(), "Nuevo chat", System.currentTimeMillis())
                    updateIncognitoUi()
                    doSend()
                }
            })
            return
        }
        val conv = existing ?: conversation ?: return
        input.setText("")
        hideKeyboard()
        welcomeView.visibility = View.GONE

        appendUi("user", text)
        updateIncognitoUi()

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

        appendUi("assistant", thinkingLabels[0])
        val bubbleIndex = messages.size - 1
        thinkingIndex = bubbleIndex
        thinkingStep = 1
        thinkingHandler.removeCallbacks(thinkingRunnable)
        thinkingHandler.postDelayed(thinkingRunnable, 700L)
        recycler.scrollToPosition(messages.size - 1)
        micBtn.isEnabled = false
        val imageData = pendingImageData
        pendingImageData = null
        findViewById<View>(R.id.chatAttachmentPreview).visibility = View.GONE

        chat.stream(
            text,
            models[modelIndex],
            history,
            conv.id,
            isIncognito(),
            imageData = imageData,
            onToken = { token ->
                runOnUiThread {
                    stopThinking()
                    if (bubbleIndex < messages.size) {
                        val current = messages[bubbleIndex].content
                        val prefix = if (current in thinkingLabels) "" else current
                        messages[bubbleIndex] = messages[bubbleIndex].copy(content = prefix + token)
                        adapter.notifyItemChanged(bubbleIndex)
                        recycler.scrollToPosition(bubbleIndex)
                    }
                }
            },
            onComplete = { full ->
                runOnUiThread {
                    stopThinking()
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
                    stopThinking()
                    if (bubbleIndex < messages.size) {
                        messages[bubbleIndex] = messages[bubbleIndex].copy(content = "Error: ${err.message ?: "sin conexion al servidor"}")
                        adapter.notifyItemChanged(bubbleIndex)
                    }
                    micBtn.isEnabled = true
                }
            }
        )
    }

    private fun stopThinking() {
        thinkingHandler.removeCallbacks(thinkingRunnable)
        thinkingIndex = -1
    }

    private fun appendUi(role: String, content: String) {
        messages.add(ChatMessage(role, content))
        adapter.notifyItemInserted(messages.size - 1)
        recycler.scrollToPosition(messages.size - 1)
    }

    private fun showWelcomeIfEmpty() {
        welcomeView.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
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
            setMicListening(true)
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
                    runOnUiThread { setMicListening(false) }
                }
                override fun onError(error: Int) {
                    listening = false
                    runOnUiThread {
                        setMicListening(false)
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
                    runOnUiThread { setMicListening(false) }
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
            setMicListening(false)
            Toast.makeText(this, "Audio no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setMicListening(active: Boolean) {
        micBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) Color.parseColor("#2F80FF") else Color.TRANSPARENT)
            if (active) setStroke(dp(2), Color.parseColor("#9BC4FF"))
        }
        micBtn.setColorFilter(if (active) Color.WHITE else Color.parseColor("#C9C9D6"))
        micBtn.contentDescription = if (active) "Detener grabación" else "Escribir por voz"
    }

    private fun showAttachmentMenu(anchor: View) {
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111C2D"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#34547E"))
            }
        }
        menu.addView(attachmentRow(R.drawable.ic_camera, "Cámara") { launchAttachment("camera") })
        menu.addView(attachmentRow(R.drawable.ic_gallery, "Fotos") { launchAttachment("gallery") })
        menu.addView(attachmentRow(R.drawable.ic_file, "Archivos") { launchAttachment("file") })
        PopupWindow(menu, dp(190), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(18).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            showAsDropDown(anchor, -dp(12), -dp(170))
        }
    }

    private fun attachmentRow(icon: Int, label: String, click: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(10))
        isClickable = true
        addView(ImageView(context).apply {
            setImageResource(icon)
            setColorFilter(Color.parseColor("#8FC1FF"))
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
            else -> Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        }
        startActivityForResult(intent, 701)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 701 && resultCode == RESULT_OK) {
            val uri = data?.data
            val type = uri?.let { contentResolver.getType(it) }.orEmpty()
            if (uri != null && type.startsWith("image/")) {
                val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    val bytes = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 72, bytes)
                    pendingImageBitmap?.recycle()
                    pendingImageBitmap = bitmap
                    pendingImageData = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                    findViewById<ImageView>(R.id.chatAttachmentImage).setImageBitmap(bitmap)
                    findViewById<View>(R.id.chatAttachmentPreview).visibility = View.VISIBLE
                    input.setText("")
                }
            } else {
                val name = uri?.lastPathSegment ?: "archivo seleccionado"
                input.setText("[Adjunto: $name] ")
            }
            input.requestFocus()
        }
    }

    private fun showImagePreview(bitmap: Bitmap?) {
        if (bitmap == null) return
        val image = ImageView(this).apply { setImageBitmap(bitmap); scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.BLACK) }
        Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(image)
            show()
            window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    // ---- incognito / helpers ----

    private fun isIncognito() = incognitoMode
    private fun prefs() = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)

    private fun toggleIncognitoMode() {
        incognitoMode = !incognitoMode
        conversation = if (incognitoMode) {
            Conversation("incognito_${System.currentTimeMillis()}", "Chat incógnito", System.currentTimeMillis())
        } else null
        convId = null
        titleView().text = "adarbot"
        updateIncognitoUi()
        showWelcomeIfEmpty()
    }

    private fun updateIncognitoUi() {
        val show = conversation == null || incognitoMode
        incognitoHomeBtn.visibility = if (show) View.VISIBLE else View.GONE
        deleteBtn.visibility = View.GONE
        incognitoHomeBtn.isEnabled = messages.isEmpty()
        incognitoHomeBtn.alpha = if (messages.isEmpty()) 1f else 0.78f
        if (incognitoMode) {
            incognitoHomeBtn.setImageResource(R.drawable.ic_ghost_filled)
            incognitoHomeBtn.setColorFilter(Color.parseColor("#79A9FF"))
            incognitoHomeBtn.contentDescription = "Modo incógnito activo"
        } else {
            incognitoHomeBtn.setImageResource(R.drawable.ic_ghost)
            incognitoHomeBtn.setColorFilter(Color.parseColor("#8FC1FF"))
            incognitoHomeBtn.contentDescription = "Activar modo incógnito"
        }
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

    private fun haptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun showDrawer() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#090E17")) }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundResource(R.drawable.bg_drawer_panel)
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply { text = "adarbot"; textSize = 26f; setTextColor(Color.parseColor("#E8F1FF")); setTypeface(null, android.graphics.Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        val close = ImageButton(this).apply { setImageResource(R.drawable.ic_close); setColorFilter(Color.WHITE); background = ColorDrawable(Color.TRANSPARENT) }
        top.addView(close, LinearLayout.LayoutParams(dp(52), dp(52))); panel.addView(top)
        lateinit var drawer: Dialog
        panel.addView(drawerRow(R.drawable.ic_plus, "Nuevo chat") { drawer.dismiss(); startActivity(Intent(this, ChatActivity::class.java)) })
        panel.addView(TextView(this).apply { text = "adarbot"; textSize = 14f; setTextColor(Color.parseColor("#8394B1")); setPadding(dp(14), dp(28), 0, dp(8)) })
        panel.addView(drawerRow(R.drawable.ic_mic, "Nemotron: voz y atajos") {
            drawer.dismiss()
            startActivity(Intent(this, com.nemotron.voiceime.ui.SetupActivity::class.java))
        })
        panel.addView(drawerRow(R.drawable.ic_server, "adarbot y servidor") { drawer.dismiss(); showAdarbotInfo() })
        val chats = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0) }
        fun renderChats(items: List<Conversation>) {
            chats.removeAllViews()
            chats.addView(TextView(this).apply { text = "Conversaciones"; textSize = 14f; setTextColor(Color.parseColor("#777B8A")); setPadding(dp(14), dp(10), 0, dp(6)) })
            items.forEach { c ->
                chats.addView(drawerConversationRow(c,
                    open = { drawer.dismiss(); startActivity(Intent(this, ChatActivity::class.java).putExtra("convId", c.id)) },
                    pin = { c.pinned = !c.pinned; ConversationStore.save(c); renderChats(ConversationStore.list()) },
                    rename = { renameConversation(c) { renderChats(ConversationStore.list()) } }
                ))
            }
        }
        renderChats(ConversationStore.list())
        panel.addView(ScrollView(this).apply {
            addView(chats, ViewGroup.LayoutParams(-1, -2))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = true
            scrollBarDefaultDelayBeforeFade = 500
            scrollBarFadeDuration = 240
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            verticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                verticalScrollbarThumbDrawable = getDrawable(R.drawable.bg_scrollbar_thumb)
            }
        })
        root.addView(panel, FrameLayout.LayoutParams(-1, -1))
        drawer = Dialog(this, R.style.Theme_AdarbotDrawer).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(root)
            setCanceledOnTouchOutside(true)
        }
        close.setOnClickListener { drawer.dismiss() }
        drawer.show()
        drawer.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#090E17")))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        panel.post { panel.translationX = -panel.width.toFloat(); panel.animate().translationX(0f).setDuration(260L).start() }
        chat.conversations({ arr ->
            val remote = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    add(Conversation(
                        item.optString("id"), item.optString("title", "Chat"),
                        item.optLong("created_at", System.currentTimeMillis()),
                        mutableListOf(), item.optString("source"), item.optString("display_name"),
                        item.optString("chat_id"), item.optBoolean("pinned", false)
                    ))
                }
            }
            runOnUiThread {
                ConversationStore.replaceRemote(remote)
                renderChats(ConversationStore.list())
            }
        }, { })
    }

    private fun drawerRow(icon: Int, label: String, click: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; isClickable = true; setPadding(dp(14), dp(13), dp(14), dp(13))
        setBackgroundResource(R.drawable.bg_drawer_action)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) }
        addView(ImageView(context).apply { setImageResource(icon); setColorFilter(Color.parseColor("#E8EBF5")); layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)) })
        addView(TextView(context).apply { text = label; textSize = 18f; setTextColor(Color.WHITE); setPadding(dp(18), 0, 0, 0) })
        setOnClickListener { haptic(this); click() }
    }

    private fun drawerConversationRow(c: Conversation, open: () -> Unit, pin: () -> Unit, rename: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(6), dp(8)); setBackgroundResource(R.drawable.bg_drawer_conversation)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(4), 0, dp(4)) }
        addView(TextView(context).apply { text = c.title; textSize = 18f; setTextColor(Color.WHITE); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setOnClickListener { haptic(this); open() } })
        addView(ImageButton(context).apply { setImageResource(if (c.pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin); setColorFilter(Color.parseColor("#8FC1FF")); background = ColorDrawable(Color.TRANSPARENT); contentDescription = "Fijar conversación"; setOnClickListener { haptic(this); pin() } }, LinearLayout.LayoutParams(dp(38), dp(38)))
        addView(ImageButton(context).apply { setImageResource(R.drawable.ic_rename); setColorFilter(Color.parseColor("#B9C9E8")); background = ColorDrawable(Color.TRANSPARENT); contentDescription = "Renombrar conversación"; setOnClickListener { haptic(this); rename() } }, LinearLayout.LayoutParams(dp(38), dp(38)))
    }

    private fun renameConversation(c: Conversation, done: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            setBackgroundResource(R.drawable.bg_adarbot_info_dialog)
        }
        card.addView(TextView(this).apply {
            text = "renombrar conversación"; textSize = 22f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#E8F1FF")); setPadding(0, 0, 0, dp(14))
        })
        val field = EditText(this).apply {
            hint = c.title; setSingleLine(); textSize = 17f
            setTextColor(Color.parseColor("#E8F1FF")); setHintTextColor(Color.parseColor("#8294B2"))
            setBackgroundResource(R.drawable.bg_input); setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        card.addView(field, LinearLayout.LayoutParams(-1, -2))
        val actions = LinearLayout(this).apply { gravity = Gravity.END; setPadding(0, dp(18), 0, 0) }
        lateinit var dialog: Dialog
        fun action(label: String, click: () -> Unit) = TextView(this).apply {
            text = label; textSize = 15f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#DDEBFF"))
            setPadding(dp(18), dp(11), dp(18), dp(11)); setBackgroundResource(R.drawable.bg_drawer_action)
            setOnClickListener { haptic(this); click() }
        }
        actions.addView(action("cancelar") { dialog.dismiss() }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, dp(8), 0) })
        actions.addView(action("guardar") {
            field.text.toString().trim().takeIf { it.isNotEmpty() }?.let { c.title = it; ConversationStore.save(c); done() }
            dialog.dismiss()
        })
        card.addView(actions)
        dialog = Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE); setContentView(card) }
        dialog.show()
        dialog.window?.apply { setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); setLayout((resources.displayMetrics.widthPixels * .86f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT); setDimAmount(.58f); addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
    }

    private fun showAdarbotInfo() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            setBackgroundResource(R.drawable.bg_adarbot_info_dialog)
        }
        card.addView(TextView(this).apply {
            text = "adarbot"
            textSize = 27f
            setTextColor(Color.parseColor("#E8F1FF"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "Agente personal conectado a tu NAS"
            textSize = 15f
            setTextColor(Color.parseColor("#99ABC9"))
            setPadding(0, dp(4), 0, dp(24))
        })
        fun info(label: String, value: String): TextView = TextView(this).apply {
            text = "$label\n$value"
            textSize = 15f
            setTextColor(Color.parseColor("#E6EEFF"))
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            setBackgroundResource(R.drawable.bg_drawer_conversation)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) }
        }
        val server = prefs().getString("server_url", "https://adarlpz-2.tail4988cb.ts.net") ?: "https://adarlpz-2.tail4988cb.ts.net"
        card.addView(info("Servidor NAS", server))
        val sync = info("Sincronización", "Comprobando conexión…")
        card.addView(sync)
        card.addView(info("Acceso remoto", "HTTPS público · sin VPN"))
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "1.0" }
        card.addView(info("Versión", "adarbot $version"))
        val close = TextView(this).apply {
            text = "Cerrar"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#DCEAFF"))
            setPadding(0, dp(13), 0, dp(13))
            setBackgroundResource(R.drawable.bg_drawer_action)
        }
        card.addView(close, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, 0) })
        val dialog = Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE); setContentView(card) }
        close.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.58f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        chat.conversations({ arr ->
            runOnUiThread {
                sync.text = "Sincronización\nActiva · ${arr.length()} conversaciones disponibles"
            }
        }, { error ->
            runOnUiThread {
                sync.text = "Sincronización\nSin conexión · ${error.message ?: "revisa el NAS"}"
            }
        })
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
