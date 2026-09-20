package com.nemotron.voiceime.chat

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
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
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import com.nemotron.voiceime.R
import com.nemotron.voiceime.dhizuku.ShizukuManager
import rikka.shizuku.Shizuku

class AssistActivity : Activity() {
    private lateinit var panel: View
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var micButton: ImageButton
    private lateinit var sendButton: ImageButton
    private lateinit var modelChip: TextView
    private lateinit var voiceBars: VoiceBarsView
    private lateinit var screenshotPill: View
    private lateinit var chat: ChatClient
    private var conversationId: String? = null
    private var speech: SpeechRecognizer? = null
    private var sendAfterSpeech = false
    private var pendingScreenshot: String? = null
    private var pendingBitmap: Bitmap? = null
    private var pendingFileUri: android.net.Uri? = null
    private var pendingFileName: String? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val models = listOf("deepseek-flash", "mimo-v2.5", "nemotron")
    private var modelIndex = 0

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
        val base = if (savedUrl.isNullOrBlank() || !savedUrl.startsWith("https://adarlpz-2.tail4988cb.ts.net")) {
            prefs.edit().putString("server_url", "https://adarlpz-2.tail4988cb.ts.net").apply()
            "https://adarlpz-2.tail4988cb.ts.net"
        } else savedUrl
        chat = ChatClient(base)
        modelIndex = prefs.getInt("model_index", 0).coerceIn(0, models.lastIndex)
        input = findViewById(R.id.assistInput)
        voiceBars = findViewById(R.id.assistVoiceBars)
        modelChip = findViewById(R.id.assistModelChip)
        screenshotPill = findViewById(R.id.assistScreenshotPill)
        val previewWrap = findViewById<View>(R.id.assistPreviewWrap)
        val preview = findViewById<ImageView>(R.id.assistPreview)
        preview.setOnClickListener { showScreenshotPreview() }
        findViewById<ImageButton>(R.id.assistPreviewRemove).setOnClickListener {
            haptic(it)
            preview.setImageDrawable(null)
            preview.visibility = View.GONE
            findViewById<View>(R.id.assistFileIcon).visibility = View.GONE
            findViewById<TextView>(R.id.assistFileName).visibility = View.GONE
            previewWrap.visibility = View.GONE
            pendingScreenshot = null
            pendingBitmap?.recycle()
            pendingBitmap = null
            pendingFileUri = null
            pendingFileName = null
            status.text = ""
            screenshotPill.visibility = View.VISIBLE
        }
        status = findViewById(R.id.assistStatus)
        panel = findViewById(R.id.assistPanel)
        // Cierra al tocar el fondo. Los hijos manejan sus propios clics; no
        // usamos un OnTouchListener que intercepte el ACTION_UP del botón de
        // captura y termine el overlay accidentalmente.
        findViewById<View>(R.id.assistRoot).setOnClickListener { finish() }
        var dragStartY = 0f
        var panelStartHeight = 0
        var isDragging = false
        val dragThreshold = dp(160)
        val minPanelHeight = dp(100)
        panel.post { panelStartHeight = panel.height }
        panel.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.rawY
                    panelStartHeight = panel.height
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = dragStartY - event.rawY
                    if (!isDragging && dy > dp(8)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val newH = (panelStartHeight + dy.toInt()).coerceAtLeast(minPanelHeight)
                        val lp = panel.layoutParams
                        lp.height = newH
                        panel.layoutParams = lp
                        screenshotPill.alpha = (1f - (dy.toFloat() / dp(50))).coerceIn(0f, 1f)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        val grew = panel.height - panelStartHeight
                        if (grew > dragThreshold) {
                            haptic(view); openFullChat()
                        } else {
                            val anim = android.animation.ValueAnimator.ofInt(panel.height, panelStartHeight)
                            anim.duration = 220
                            anim.interpolator = android.view.animation.DecelerateInterpolator()
                            anim.addUpdateListener { v ->
                                val lp = panel.layoutParams
                                lp.height = v.animatedValue as Int
                                panel.layoutParams = lp
                            }
                            anim.start()
                            screenshotPill.animate().alpha(1f).setDuration(220).start()
                        }
                    } else {
                        val dy = dragStartY - event.rawY
                        if (dy < -dp(48)) { haptic(view); openFullChat() }
                    }
                    isDragging = false
                    true
                }
                else -> true
            }
        }
        sendButton = findViewById(R.id.assistSend)
        sendButton.setOnClickListener {
            haptic(it)
            val detected = input.text.toString().trim()
            if (speech != null) {
                // Ask Android for the final transcript. The full chat opens only
                // after that transcript is available, so it never opens empty.
                sendAfterSpeech = true
                speech?.stopListening()
                setMicListening(false)
                window.decorView.postDelayed({
                    if (sendAfterSpeech) {
                        sendAfterSpeech = false
                        val recognizer = speech
                        speech = null
                        recognizer?.cancel()
                        recognizer?.destroy()
                        val finalText = input.text.toString().trim().ifBlank { detected }
                        openFullChat(finalText.ifBlank { null }, pendingScreenshot, voiceInput = true)
                    }
                }, 900L)
            } else if (detected.isNotEmpty() || pendingScreenshot != null) {
                send()
            }
        }
        sendButton.setOnLongClickListener { haptic(it); showModelPicker(it); true }
        findViewById<ImageButton>(R.id.assistAttach).setOnClickListener { haptic(it); showAttachmentMenu(it) }
        micButton = findViewById(R.id.assistMic)
        modelChip.setOnClickListener {
            haptic(it)
            modelIndex = (modelIndex + 1) % models.size
            prefs.edit().putInt("model_index", modelIndex).apply()
            updateModelChip()
        }
        updateModelChip()
        screenshotPill.isClickable = true
        screenshotPill.isFocusable = true
        screenshotPill.setOnClickListener { haptic(it); requestScreenCapture() }
        findViewById<ImageButton>(R.id.assistScreenshot).apply {
            isClickable = false
            isFocusable = false
        }
        micButton.setOnClickListener { haptic(it); if (speech != null) cancelListening() else listen() }
        input.setOnEditorActionListener { _, _, _ -> send(); true }
        // El asistente de voz abre limpio: el teclado solo aparece cuando el usuario toca el campo.
        findViewById<android.view.View>(android.R.id.content).requestFocus()
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Typing takes over from voice: cancel silently so recognition
                // cannot inject or send the partial transcript later.
                cancelListening()
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                input.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
            }
        }
        input.setOnTouchListener { _, _ ->
            cancelListening()
            false
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            window.decorView.postDelayed({ listen() }, 280)
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 42)
        }
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val imeBottom = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            // The overlay window is fullscreen, so explicitly lift its bottom controls
            // above the IME instead of letting the keyboard cover them.
            val lift = if (imeBottom > 0) imeBottom.toFloat() else 0f
            panel.translationY = -lift
            screenshotPill.translationY = -lift
            previewWrap.translationY = -lift
            view.onApplyWindowInsets(insets)
        }
    }

    private fun send(overrideText: String? = null) {
        val text = (overrideText ?: input.text.toString()).trim().ifEmpty {
            if (pendingScreenshot != null) "Analyze this screenshot." else return
        }
        openFullChat(text, pendingScreenshot)
    }

    private fun requestScreenCapture() {
        if (!ShizukuManager.isAvailable()) {
            status.visibility = View.VISIBLE
            status.text = "Start Shizuku to capture without screen sharing"
            return
        }
        if (!ShizukuManager.hasPermission()) {
            status.visibility = View.VISIBLE
            status.text = "Authorize capture in Shizuku..."
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            listener = Shizuku.OnRequestPermissionResultListener { _, result ->
                Shizuku.removeRequestPermissionResultListener(listener)
                runOnUiThread {
                    if (result == PackageManager.PERMISSION_GRANTED) requestScreenCapture()
                    else status.text = "Capture permission denied"
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            ShizukuManager.requestPermission()
            return
        }
        status.visibility = View.VISIBLE
        val file = java.io.File(getExternalFilesDir(null), "adarbot_capture.png")
        Thread {
            try {
                file.parentFile?.mkdirs()
                file.delete()
                // Un proceso nuevo evita que una cola vieja del shell
                // persistente deje la captura sin escribir.
                ShizukuManager.execShellFresh(
                    arrayOf("screencap", "-p", file.absolutePath),
                    10000L
                )
                if (!file.exists() || file.length() < 128L) {
                    throw IllegalStateException("screencap did not create file")
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ?: throw IllegalStateException("Could not capture screen")
                val bytes = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 72, bytes)
                val encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                runOnUiThread {
                    pendingBitmap?.recycle()
                    pendingBitmap = bitmap
                    pendingScreenshot = encoded
                    status.text = "Screenshot attached"
                    findViewById<ImageView>(R.id.assistPreview).setImageBitmap(bitmap)
                    findViewById<View>(R.id.assistPreviewWrap).visibility = View.VISIBLE
                    screenshotPill.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Capture failed: ${e.message ?: "Shizuku permission"}" }
            } finally {
                file.delete()
            }
        }.start()
    }

    private fun captureScreen(resultCode: Int, data: Intent) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val raw = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                raw.copyPixelsFromBuffer(plane.buffer)
                val bitmap = Bitmap.createBitmap(raw, 0, 0, width, height)
                raw.recycle()
                val bytes = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 72, bytes)
                bitmap.recycle()
                pendingScreenshot = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                runOnUiThread {
                    status.text = "Screenshot attached"
                    pendingBitmap = bitmap
                    findViewById<ImageView>(R.id.assistPreview).setImageBitmap(bitmap)
                    findViewById<View>(R.id.assistPreviewWrap).visibility = View.VISIBLE
                }
            } finally {
                image.close()
                releaseCapture()
            }
        }, Handler(Looper.getMainLooper()))
        virtualDisplay = projection?.createVirtualDisplay(
            "adarbot-screen", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null
        )
    }

    private fun releaseCapture() {
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        projection?.stop(); projection = null
    }

    private fun showScreenshotPreview() {
        val bitmap = pendingBitmap ?: return
        lateinit var dialog: Dialog
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            setOnClickListener { dialog.dismiss() }
        }
        dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(image)
            setOnShowListener {
                window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
                window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun displayName(model: String): String = when (model) {
        "deepseek-flash" -> "DeepSeek"
        "mimo-v2.5" -> "MiMo"
        else -> "Nemotron"
    }

    private fun modelIcon(model: String): Int = when (model) {
        "deepseek-flash" -> R.drawable.ic_model_deepseek
        "mimo-v2.5" -> R.drawable.ic_model_mimo
        else -> R.drawable.ic_model_nemotron
    }

    private fun updateModelChip() {
        val icon = modelIcon(models[modelIndex])
        modelChip.text = displayName(models[modelIndex])
        modelChip.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
        modelChip.compoundDrawablePadding = dp(5)
        sendButton.setColorFilter(modelColor(models[modelIndex]))
    }

    private fun modelColor(model: String): Int = when (model) {
        "nemotron" -> Color.parseColor("#76B900")
        "mimo-v2.5" -> Color.parseColor("#FF6900")
        else -> Color.parseColor("#4D6BFE")
    }

    private fun showModelPicker(anchor: View) {
        val menu = AdarbotPopupSurface.menu(this, 8, 8)
        val popup = AdarbotPopupSurface.popup(menu, dp(230))
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

    private fun openFullChat(
        draft: String? = null,
        screenshot: String? = null,
        voiceInput: Boolean = false
    ) {
        val target = Intent(this, ChatActivity::class.java).apply {
            conversationId?.let { putExtra("convId", it) }
            draft?.takeIf { it.isNotBlank() }?.let { putExtra("draft", it) }
            screenshot?.let { putExtra("pendingImageData", it) }
            putExtra("autoReadResponse", voiceInput)
            // A swipe-up means the user explicitly expanded the assistant;
            // open the full chat ready for typing.
            putExtra("focusInput", draft.isNullOrBlank())
        }
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
        setMicListening(true)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { runOnUiThread { status.text = "Listening..." } }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) { runOnUiThread { voiceBars.setLevel(v) } }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {
                // Android ends a recognition segment after silence. Keep the
                // overlay recording until the user explicitly sends/cancels.
                restartRecognition(recognizer)
            }
            override fun onError(e: Int) {
                if (speech !== recognizer) return
                if (sendAfterSpeech) {
                    speech = null
                    sendAfterSpeech = false
                    recognizer.destroy()
                    runOnUiThread {
                        setMicListening(false)
                        val finalText = input.text.toString().trim()
                        openFullChat(finalText.ifBlank { null }, pendingScreenshot, voiceInput = true)
                    }
                } else {
                    runOnUiThread { status.text = "Listening..." }
                    restartRecognition(recognizer)
                }
            }
            override fun onResults(b: Bundle?) {
                val r = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val resultText = r?.firstOrNull()?.trim().orEmpty()
                if (resultText.isNotEmpty()) input.setText(resultText)
                if (sendAfterSpeech) {
                    speech = null
                    sendAfterSpeech = false
                    recognizer.destroy()
                    runOnUiThread {
                        setMicListening(false)
                        val finalText = resultText.ifBlank { input.text.toString().trim() }
                        openFullChat(finalText.ifBlank { null }, pendingScreenshot, voiceInput = true)
                    }
                } else {
                    restartRecognition(recognizer)
                }
            }
            override fun onPartialResults(b: Bundle?) {
                val r = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!r.isNullOrEmpty()) runOnUiThread { input.setText(r[0]); input.setSelection(input.length()) }
            }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        startRecognition(recognizer)
    }

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private fun startRecognition(recognizer: SpeechRecognizer) {
        try { recognizer.startListening(recognitionIntent()) } catch (_: Exception) { }
    }

    private fun restartRecognition(recognizer: SpeechRecognizer) {
        window.decorView.postDelayed({
            if (speech === recognizer) startRecognition(recognizer)
        }, 120L)
    }

    private fun cancelListening(clearPartial: Boolean = true) {
        val recognizer = speech ?: return
        sendAfterSpeech = false
        speech = null
        recognizer.cancel()
        recognizer.destroy()
        // Tapping the microphone is a cancel action: discard partial speech.
        if (clearPartial) input.setText("")
        setMicListening(false)
    }

    private fun setMicListening(active: Boolean) {
        voiceBars.visibility = if (active) View.VISIBLE else View.GONE
        input.visibility = if (active) View.GONE else View.VISIBLE
        micButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) Color.parseColor("#2F80FF") else Color.parseColor("#171B24"))
            if (active) setStroke(dp(2), Color.parseColor("#9BC4FF"))
        }
        micButton.setColorFilter(if (active) Color.WHITE else Color.parseColor("#C9C9D6"))
        micButton.contentDescription = if (active) "Stop recording" else "Talk to adarbot"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            window.decorView.postDelayed({ listen() }, 250)
        }
    }

    private fun showAttachmentMenu(anchor: View) {
        val menu = AdarbotPopupSurface.menu(this)
        lateinit var popup: PopupWindow
        fun addAction(icon: Int, label: String, kind: String) {
            menu.addView(attachmentRow(icon, label) {
                popup.dismiss()
                launchAttachment(kind)
            })
        }
        fun addActionDisabled(icon: Int, label: String) {
            menu.addView(attachmentRow(icon, label, enabled = false) {})
        }
        val visionModels = setOf("deepseek-flash", "mimo-v2.5")
        val hasVision = models[modelIndex] in visionModels
        if (hasVision) {
            addAction(R.drawable.ic_camera, "Camera", "camera")
            addAction(R.drawable.ic_gallery, "Photos", "gallery")
        } else {
            addActionDisabled(R.drawable.ic_camera, "Camera")
            addActionDisabled(R.drawable.ic_gallery, "Photos")
        }
        addAction(R.drawable.ic_file, "Files", "file")
        popup = AdarbotPopupSurface.popup(menu, dp(190))
        popup.showAsDropDown(anchor, -dp(12), -dp(170))
    }

    private fun attachmentRow(icon: Int, label: String, enabled: Boolean = true, click: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(10)); isClickable = enabled
        alpha = if (enabled) 1f else 0.35f
        addView(android.widget.ImageView(context).apply {
            setImageResource(icon); setColorFilter(if (enabled) Color.parseColor("#8FC1FF") else Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        })
        addView(TextView(context).apply {
            text = label; textSize = 15f; setTextColor(if (enabled) Color.WHITE else Color.GRAY)
            setPadding(dp(12), 0, 0, 0)
        })
        if (enabled) setOnClickListener { haptic(this); click() }
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
        when {
            requestCode == 700 && resultCode == RESULT_OK -> {
                val uri = data?.data
                val type = uri?.let { contentResolver.getType(it) }.orEmpty()
                val bitmap = when {
                    uri != null && type.startsWith("image/") -> contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    else -> data?.extras?.get("data") as? Bitmap
                }
                if (bitmap != null) {
                    val bytes = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 72, bytes)
                    pendingBitmap?.recycle()
                    pendingBitmap = bitmap
                    pendingScreenshot = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                    pendingFileUri = null
                    pendingFileName = null
                    val preview = findViewById<ImageView>(R.id.assistPreview)
                    preview.setImageBitmap(bitmap)
                    preview.visibility = View.VISIBLE
                    findViewById<View>(R.id.assistFileIcon).visibility = View.GONE
                    findViewById<TextView>(R.id.assistFileName).visibility = View.GONE
                    findViewById<View>(R.id.assistPreviewWrap).visibility = View.VISIBLE
                    screenshotPill.visibility = View.GONE
                    input.setText("")
                } else {
                    val name = resolveFileName(uri)
                    pendingFileUri = uri
                    pendingFileName = name
                    pendingBitmap?.recycle()
                    pendingBitmap = null
                    pendingScreenshot = null
                    findViewById<ImageView>(R.id.assistPreview).visibility = View.GONE
                    findViewById<TextView>(R.id.assistFileName).apply { text = name; visibility = View.VISIBLE }
                    findViewById<View>(R.id.assistFileIcon).visibility = View.VISIBLE
                    findViewById<View>(R.id.assistPreviewWrap).visibility = View.VISIBLE
                    screenshotPill.visibility = View.GONE
                    input.setText("")
                }
                input.requestFocus()
            }
            requestCode == 701 && resultCode == RESULT_OK && data != null -> captureScreen(resultCode, data)
        }
    }

    private fun resolveFileName(uri: android.net.Uri?): String {
        if (uri == null) return "file"
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx) ?: "file"
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    override fun onDestroy() { speech?.destroy(); releaseCapture(); pendingBitmap?.recycle(); pendingBitmap = null; pendingFileUri = null; pendingFileName = null; super.onDestroy() }

    private fun haptic(view: android.view.View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
