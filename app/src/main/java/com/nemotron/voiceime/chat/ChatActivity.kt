package com.nemotron.voiceime.chat

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
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
import android.speech.tts.TextToSpeech
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
import android.view.animation.DecelerateInterpolator
import android.view.animation.AnimationUtils
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.Locale
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
    private lateinit var sendBtn: ImageButton
    private lateinit var deleteBtn: ImageButton
    private lateinit var incognitoHomeBtn: ImageButton
    private lateinit var chat: ChatClient
    private var textToSpeech: TextToSpeech? = null

    private val models = listOf("deepseek-flash", "mimo-v2.5", "nemotron")
    private var modelIndex = 0
    private var listening = false
    private var speech: SpeechRecognizer? = null
    private var pendingImageData: String? = null
    private var pendingImageBitmap: Bitmap? = null
    private var remoteRefreshInFlight = false
    private var sending = false
    private var lastRemoteSignature = ""
    private val remoteRefreshHandler = Handler(Looper.getMainLooper())
    private val remoteRefreshLoop = object : Runnable {
        override fun run() {
            refreshRemoteMessages()
            remoteRefreshHandler.postDelayed(this, 1200L)
        }
    }
    // Estado estable: alternar frases parecía un error de conexión.
    private val thinkingLabels = listOf("adarbot está pensando…")
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
        sendBtn = findViewById(R.id.btnSend)
        deleteBtn = findViewById(R.id.btnDelete)
        val titleV: TextView = findViewById(R.id.convTitle)
        val attachmentPreview = findViewById<View>(R.id.chatAttachmentPreview)
        val attachmentImage = findViewById<ImageView>(R.id.chatAttachmentImage)
        attachmentImage.setOnClickListener { showImagePreview(pendingImageBitmap) }
        findViewById<ImageButton>(R.id.chatAttachmentRemove).setOnClickListener {
            haptic(it)
            attachmentPreview.animate().alpha(0f).translationY(dp(10).toFloat()).setDuration(140).withEndAction {
                attachmentPreview.visibility = View.GONE
                attachmentPreview.alpha = 1f
                attachmentPreview.translationY = 0f
            }.start()
            attachmentImage.setImageDrawable(null)
            pendingImageData = null
            pendingImageBitmap?.recycle()
            pendingImageBitmap = null
        }

        window.statusBarColor = Color.parseColor("#090E17")
        window.navigationBarColor = Color.parseColor("#090E17")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        findViewById<View>(android.R.id.content).autoInsets()

        val prefs = getSharedPreferences("hermes_chat", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", null)
        val base = if (savedUrl.isNullOrBlank() || !savedUrl.startsWith("https://adarlpz-2.tail4988cb.ts.net")) {
            prefs.edit().putString("server_url", "https://adarlpz-2.tail4988cb.ts.net").apply()
            "https://adarlpz-2.tail4988cb.ts.net"
        } else savedUrl
        chat = ChatClient(base)
        modelIndex = prefs.getInt("model_index", 0).coerceIn(0, models.lastIndex)

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
        messages.removeAll { it.role == "assistant" && it.content in thinkingLabels }
        titleV.text = if (incognitoMode) "adarbot" else conversation?.title ?: "Nuevo chat"
        updateIncognitoUi()

        adapter = MessageAdapter(messages, ::copyMessage, ::speakMessage, ::showMessageActions)
        textToSpeech = TextToSpeech(this) { }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.layoutAnimation = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_message_enter)
        recycler.adapter = adapter
        showWelcomeIfEmpty()
        if (messages.isNotEmpty()) recycler.scrollToPosition(messages.size - 1)

        refreshRemoteMessages()

        backBtn.setOnClickListener { haptic(it); goBack() }
        incognitoHomeBtn.setOnClickListener {
            haptic(it)
            if (messages.isEmpty()) toggleIncognitoMode()
        }
        sendBtn.setOnClickListener { haptic(it); doSend() }
        input.setOnEditorActionListener { _, _, _ -> doSend(); true }

        updateModelChip()
        modelChip.setOnClickListener {
            haptic(it)
            modelIndex = (modelIndex + 1) % models.size
            prefs.edit().putInt("model_index", modelIndex).apply()
            updateModelChip()
        }
        sendBtn.setOnLongClickListener { haptic(it); showModelPicker(it); true }

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
            val id = convId
            if (id.isNullOrBlank()) return@setOnClickListener
            ConversationStore.delete(id)
            chat.deleteConversation(id, { runOnUiThread { finish() } }, { runOnUiThread { finish() } })
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        remoteRefreshHandler.removeCallbacks(remoteRefreshLoop)
        thinkingHandler.removeCallbacks(thinkingRunnable)
        try {
            speech?.destroy()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        remoteRefreshHandler.removeCallbacks(remoteRefreshLoop)
        remoteRefreshHandler.post(remoteRefreshLoop)
    }

    override fun onPause() {
        remoteRefreshHandler.removeCallbacks(remoteRefreshLoop)
        super.onPause()
    }

    private fun refreshRemoteMessages() {
        val id = convId ?: return
        if (incognitoMode || remoteRefreshInFlight) return
        remoteRefreshInFlight = true
        chat.conversation(id, { remote ->
            val remoteMessages = parseRemoteMessages(remote)
            runOnUiThread {
                remoteRefreshInFlight = false
                val merged = mergeRemoteMessages(remoteMessages)
                val signature = merged.joinToString("|") { "${it.role}:${it.ts}:${it.content}" }
                if (signature == lastRemoteSignature) return@runOnUiThread
                lastRemoteSignature = signature
                val wasAtBottom = !recycler.canScrollVertically(1)
                adapter.replaceMessages(merged)
                recycler.scheduleLayoutAnimation()
                conversation?.messages?.clear()
                conversation?.messages?.addAll(merged)
                if (!sending && !incognitoMode) conversation?.let { ConversationStore.save(it) }
                showWelcomeIfEmpty()
                if (wasAtBottom && merged.isNotEmpty()) recycler.smoothScrollToPosition(merged.lastIndex)
            }
        }, {
            runOnUiThread { remoteRefreshInFlight = false }
        })
    }

    private fun parseRemoteMessages(remote: JSONObject): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        val arr = remote.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            result.add(ChatMessage(
                m.optString("role"),
                m.optString("content"),
                m.optLong("created_at", System.currentTimeMillis()),
                m.optString("model").ifBlank { remote.optString("model").ifBlank { null } }
            ))
        }
        return result
    }

    private fun mergeRemoteMessages(remote: List<ChatMessage>): List<ChatMessage> {
        val cleanRemote = remote.filterNot { it.role == "assistant" && it.content in thinkingLabels }
        // Fuera de un envío, el servidor es la fuente de verdad. No mezclar
        // mensajes viejos del cache local: eso causaba usuarios duplicados y
        // respuestas fuera de orden.
        if (!sending) return cleanRemote

        // Durante el envío sí conservamos temporalmente el mensaje local y el
        // indicador de pensamiento hasta que el servidor confirme la respuesta.
        val merged = cleanRemote.toMutableList()
        messages.filter { it.role == "user" && it.content.isNotBlank() }
            .filter { local -> merged.none { it.role == local.role && it.content == local.content } }
            .forEach { merged.add(it) }
        messages.filter { it.role == "assistant" && it.content in thinkingLabels }
            .filter { pending -> merged.none { it.role == pending.role && it.content == pending.content } }
            .forEach { merged.add(it) }
        return merged
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
        sending = true
        setSendingUi(true)
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
            conv.messages.add(ChatMessage("user", text, model = models[modelIndex]))
            ConversationStore.save(conv)
        }

        appendUi("assistant", thinkingLabels[0])
        val bubbleIndex = messages.size - 1
        messages[bubbleIndex] = messages[bubbleIndex].copy(model = models[modelIndex])
        adapter.notifyItemChanged(bubbleIndex)
        thinkingIndex = bubbleIndex
        thinkingStep = 1
        thinkingHandler.removeCallbacks(thinkingRunnable)
        thinkingHandler.postDelayed(thinkingRunnable, 700L)
        recycler.scrollToPosition(messages.size - 1)
        micBtn.isEnabled = false
        val imageData = pendingImageData
        pendingImageData = null
        val previewToHide = findViewById<View>(R.id.chatAttachmentPreview)
        previewToHide.animate().alpha(0f).translationY(dp(10).toFloat()).setDuration(120).withEndAction {
            previewToHide.visibility = View.GONE
            previewToHide.alpha = 1f
            previewToHide.translationY = 0f
        }.start()

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
                        messages[bubbleIndex] = messages[bubbleIndex].copy(content = full, model = models[modelIndex])
                        adapter.notifyItemChanged(bubbleIndex)
                        recycler.scrollToPosition(bubbleIndex)
                    }
                    micBtn.isEnabled = true
                    sending = false
                    setSendingUi(false)
                    sendBtn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (!isIncognito()) {
                        conv.messages.add(ChatMessage("assistant", full, model = models[modelIndex]))
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
                    sending = false
                    setSendingUi(false)
                }
            }
        )
    }

    private fun stopThinking() {
        thinkingHandler.removeCallbacks(thinkingRunnable)
        thinkingIndex = -1
    }

    private fun setSendingUi(active: Boolean) {
        sendBtn.isEnabled = !active
        sendBtn.contentDescription = if (active) "adarbot está pensando" else "Enviar"
        sendBtn.animate().cancel()
        if (active) {
            sendBtn.animate().scaleX(0.82f).scaleY(0.82f).alpha(0.7f).setDuration(140).start()
        } else {
            sendBtn.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setInterpolator(DecelerateInterpolator()).setDuration(190).start()
        }
    }

    private fun appendUi(role: String, content: String) {
        messages.add(ChatMessage(role, content))
        adapter.notifyItemInserted(messages.size - 1)
        recycler.scheduleLayoutAnimation()
        recycler.scrollToPosition(messages.size - 1)
    }

    private fun copyMessage(message: ChatMessage) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Mensaje de adarbot", message.content))
        Toast.makeText(this, "Mensaje copiado", Toast.LENGTH_SHORT).show()
    }

    private fun speakMessage(message: ChatMessage) {
        textToSpeech?.stop()
        textToSpeech?.language = Locale("es", "MX")
        textToSpeech?.speak(message.content, TextToSpeech.QUEUE_FLUSH, null, "adarbot-message")
    }

    private fun showMessageActions(anchor: View, message: ChatMessage) {
        lateinit var popup: PopupWindow
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111C2D"))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.parseColor("#34547E"))
            }
        }
        fun action(icon: Int, description: String, callback: () -> Unit): View = ImageButton(this).apply {
            setImageResource(icon)
            setColorFilter(Color.parseColor("#9BC4FF"))
            background = ColorDrawable(Color.TRANSPARENT)
            contentDescription = description
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { haptic(it); callback() }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(44))
        }
        menu.addView(action(R.drawable.ic_copy, "Copiar mensaje") {
            copyMessage(message)
            popup.dismiss()
        })
        menu.addView(action(R.drawable.ic_volume, "Escuchar mensaje") {
            speakMessage(message)
            popup.dismiss()
        })
        val modelLabel = TextView(this).apply {
            text = message.model?.let { displayName(it) } ?: "Modelo no disponible"
            setTextColor(Color.parseColor("#A9C9FF"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(dp(118), dp(44))
        }
        menu.addView(modelLabel)
        popup = PopupWindow(menu, dp(258), dp(58), true).apply {
            elevation = dp(18).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
        }
        menu.post {
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)
            popup.showAtLocation(anchor, Gravity.TOP or Gravity.START, dp(12), (location[1] - dp(70)).coerceAtLeast(dp(70)))
        }
    }

    private fun showWelcomeIfEmpty() {
        val show = messages.isEmpty()
        if (show && welcomeView.visibility != View.VISIBLE) {
            welcomeView.alpha = 0f
            welcomeView.visibility = View.VISIBLE
            welcomeView.animate().alpha(1f).setDuration(220).start()
        } else if (!show && welcomeView.visibility == View.VISIBLE) {
            welcomeView.animate().alpha(0f).setDuration(140).withEndAction {
                welcomeView.visibility = View.GONE
                welcomeView.alpha = 1f
            }.start()
        }
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
                    findViewById<View>(R.id.chatAttachmentPreview).apply {
                        alpha = 0f
                        translationY = dp(12).toFloat()
                        visibility = View.VISIBLE
                        animate().alpha(1f).translationY(0f).setInterpolator(DecelerateInterpolator()).setDuration(190).start()
                    }
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
        titleView().text = if (incognitoMode) "adarbot" else "Nuevo chat"
        updateIncognitoUi()
        showWelcomeIfEmpty()
    }

    private fun updateIncognitoUi() {
        val show = conversation == null || incognitoMode
        incognitoHomeBtn.visibility = if (show) View.VISIBLE else View.GONE
        deleteBtn.visibility = if (!show && !convId.isNullOrBlank()) View.VISIBLE else View.GONE
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

    private fun modelIcon(m: String): Int = when (m) {
        "deepseek-flash" -> R.drawable.ic_model_deepseek
        "mimo-v2.5" -> R.drawable.ic_model_mimo
        else -> R.drawable.ic_model_nemotron
    }

    private fun updateModelChip() {
        val icon = modelIcon(models[modelIndex])
        modelChip.text = displayName(models[modelIndex])
        modelChip.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
        modelChip.compoundDrawablePadding = dp(5)
        sendBtn.setColorFilter(modelColor(models[modelIndex]))
    }

    private fun modelColor(model: String): Int = when (model) {
        "nemotron" -> Color.parseColor("#76B900")
        "mimo-v2.5" -> Color.parseColor("#FF6900")
        else -> Color.parseColor("#4D6BFE")
    }

    private fun showModelPicker(anchor: View) {
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundResource(R.drawable.bg_drawer_panel)
        }
        val popup = PopupWindow(menu, dp(230), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(20).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }
        models.forEachIndexed { index, model ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = if (index == modelIndex) getDrawable(R.drawable.bg_drawer_action) else ColorDrawable(Color.TRANSPARENT)
                addView(View(context).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(modelColor(model))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(12), dp(12))
                })
                addView(TextView(context).apply {
                    text = when (model) { "mimo-v2.5" -> "MiMo · Xiaomi"; "deepseek-flash" -> "DeepSeek"; else -> "Nemotron · NVIDIA" }
                    textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(14), 0, 0, 0)
                })
            }
            row.setOnClickListener {
                haptic(it); modelIndex = index
                getSharedPreferences("hermes_chat", Context.MODE_PRIVATE).edit().putInt("model_index", modelIndex).apply()
                updateModelChip(); popup.dismiss()
            }
            menu.addView(row, LinearLayout.LayoutParams(-1, dp(48)))
        }
        popup.showAsDropDown(anchor, -dp(185), -dp(190))
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
        val drawerConnectionDot = View(this).apply {
            setBackgroundResource(R.drawable.bg_connection_offline)
            layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(8) }
        }
        top.addView(drawerConnectionDot)
        val drawerTitle = TextView(this).apply {
            text = "adarbot"
            textSize = 26f
            setTextColor(Color.parseColor("#E8F1FF"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        top.addView(drawerTitle)
        val close = ImageButton(this).apply { setImageResource(R.drawable.ic_close); setColorFilter(Color.WHITE); background = ColorDrawable(Color.TRANSPARENT) }
        top.addView(close, LinearLayout.LayoutParams(dp(52), dp(52))); panel.addView(top)
        lateinit var drawer: Dialog
        panel.addView(drawerRow(R.drawable.ic_plus, "Nuevo chat") { drawer.dismiss(); startActivity(Intent(this, ChatActivity::class.java)) })
        panel.addView(TextView(this).apply { text = "adarbot"; textSize = 14f; setTextColor(Color.parseColor("#8394B1")); setPadding(dp(14), dp(28), 0, dp(8)) })
        panel.addView(drawerRow(R.drawable.ic_mic, "Nemotron: voz y atajos") {
            drawer.dismiss()
            startActivity(Intent(this, com.nemotron.voiceime.ui.SetupActivity::class.java))
        })
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
        chat.conversations(
            { runOnUiThread { drawerConnectionDot.setBackgroundResource(R.drawable.bg_connection_online) } },
            { runOnUiThread { drawerConnectionDot.setBackgroundResource(R.drawable.bg_connection_offline) } }
        )
        panel.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
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
        fun closeDrawerAnimated() {
            if (!drawer.isShowing) return
            panel.animate()
                .translationX(-panel.width.toFloat())
                .alpha(0.96f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { drawer.dismiss() }
                .start()
        }
        close.setOnClickListener { haptic(it); closeDrawerAnimated() }
        drawerTitle.setOnClickListener {
            haptic(it)
            closeDrawerAnimated()
            it.postDelayed({ showAdarbotInfo() }, 220L)
        }
        var downX = 0f
        val swipeToClose = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val distance = event.rawX - downX
                    if (distance < 0f) view.translationX = distance.coerceAtLeast(-view.width.toFloat())
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    val distance = event.rawX - downX
                    if (distance < -panel.width * 0.22f) {
                        closeDrawerAnimated()
                    } else {
                        view.animate().translationX(0f).setDuration(180L)
                            .setInterpolator(DecelerateInterpolator()).start()
                    }
                    true
                }
                else -> false
            }
        }
        panel.setOnTouchListener(swipeToClose)
        drawer.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#090E17")))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        panel.post { panel.translationX = -panel.width.toFloat(); panel.alpha = 1f; panel.animate().translationX(0f).setDuration(260L).setInterpolator(DecelerateInterpolator()).start() }
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
