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
    private lateinit var modelBadge: ImageView
    private lateinit var screenshotPill: View
    private lateinit var chat: ChatClient
    private var conversationId: String? = null
    private var speech: SpeechRecognizer? = null
    private var pendingScreenshot: String? = null
    private var pendingBitmap: Bitmap? = null
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
        modelChip = findViewById(R.id.assistModelChip)
        modelBadge = findViewById(R.id.assistModelBadge)
        screenshotPill = findViewById(R.id.assistScreenshotPill)
        val previewWrap = findViewById<View>(R.id.assistPreviewWrap)
        val preview = findViewById<ImageView>(R.id.assistPreview)
        preview.setOnClickListener { showScreenshotPreview() }
        findViewById<ImageButton>(R.id.assistPreviewRemove).setOnClickListener {
            haptic(it)
            preview.setImageDrawable(null)
            previewWrap.visibility = View.GONE
            pendingScreenshot = null
            pendingBitmap?.recycle()
            pendingBitmap = null
            status.text = ""
            screenshotPill.visibility = View.VISIBLE
        }
        status = findViewById(R.id.assistStatus)
        panel = findViewById(R.id.assistPanel)
        // Cierra al tocar el fondo. Los hijos manejan sus propios clics; no
        // usamos un OnTouchListener que intercepte el ACTION_UP del botón de
        // captura y termine el overlay accidentalmente.
        findViewById<View>(R.id.assistRoot).setOnClickListener { finish() }
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
        sendButton = findViewById(R.id.assistSend)
        sendButton.setOnClickListener { haptic(it); send() }
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
        // La acción pertenece a toda la pastilla, no solo al icono. Así el
        // toque sobre el texto o el borde no cae en el listener del fondo.
        screenshotPill.isClickable = true
        screenshotPill.isFocusable = true
        screenshotPill.setOnClickListener { haptic(it); requestScreenCapture() }
        findViewById<ImageButton>(R.id.assistScreenshot).apply {
            isClickable = false
            isFocusable = false
        }
        micButton.setOnClickListener { haptic(it); if (speech != null) { speech?.stopListening(); speech = null; setMicListening(false) } else listen() }
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
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            window.decorView.postDelayed({ listen() }, 280)
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 42)
        }
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val imeBottom = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            val navBottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            // Gravity.BOTTOM: un desplazamiento positivo levanta el panel por encima del teclado.
            val offset = if (imeBottom > 0) imeBottom + dp(10) else navBottom + dp(10)
            window.attributes = window.attributes.apply { y = offset }
            view.onApplyWindowInsets(insets)
        }
    }

    private fun send() {
        val text = input.text.toString().trim().ifEmpty {
            if (pendingScreenshot != null) "Analiza esta captura de pantalla." else return
        }
        input.setText(""); status.text = "Pensando..."
        val id = conversationId
        val done: (String) -> Unit = { answer -> runOnUiThread { status.text = answer.ifEmpty { "Listo" }.take(72) } }
        val fail: (Throwable) -> Unit = { e -> runOnUiThread { status.text = "Sin conexión: ${e.message ?: "error"}" } }
        val start: (String) -> Unit = { cid ->
            conversationId = cid
            val image = pendingScreenshot
            pendingScreenshot = null
            chat.stream(text, models[modelIndex], emptyList(), cid, false, image, {}, done, fail)
        }
        if (id != null) start(id) else chat.createConversation({ runOnUiThread { start(it.optString("id")) } }, fail)
    }

    private fun requestScreenCapture() {
        if (!ShizukuManager.isAvailable()) {
            status.visibility = View.VISIBLE
            status.text = "Inicia Shizuku para capturar sin compartir pantalla"
            return
        }
        if (!ShizukuManager.hasPermission()) {
            status.visibility = View.VISIBLE
            status.text = "Autoriza la captura en Shizuku..."
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            listener = Shizuku.OnRequestPermissionResultListener { _, result ->
                Shizuku.removeRequestPermissionResultListener(listener)
                runOnUiThread {
                    if (result == PackageManager.PERMISSION_GRANTED) requestScreenCapture()
                    else status.text = "Permiso de captura denegado"
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
                    throw IllegalStateException("screencap no creó el archivo")
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ?: throw IllegalStateException("No se pudo capturar la pantalla")
                val bytes = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 72, bytes)
                val encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                runOnUiThread {
                    pendingBitmap?.recycle()
                    pendingBitmap = bitmap
                    pendingScreenshot = encoded
                    status.text = "Captura adjunta"
                    findViewById<ImageView>(R.id.assistPreview).setImageBitmap(bitmap)
                    findViewById<View>(R.id.assistPreviewWrap).visibility = View.VISIBLE
                    screenshotPill.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "No se pudo capturar: ${e.message ?: "permiso de Shizuku"}" }
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
                    status.text = "Captura adjunta"
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
        modelBadge.setImageResource(icon)
        sendButton.setColorFilter(modelColor(models[modelIndex]))
    }

    private fun modelColor(model: String): Int = when (model) {
        "nemotron" -> Color.parseColor("#70D45C")
        "mimo-v2.5" -> Color.parseColor("#FF965F")
        else -> Color.parseColor("#8B7CFF")
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
                addView(ImageView(context).apply {
                    setImageResource(modelIcon(model)); layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                })
                addView(TextView(context).apply {
                    text = when (model) { "mimo-v2.5" -> "MiMo · Xiaomi"; "deepseek-flash" -> "DeepSeek"; else -> "Nemotron · NVIDIA" }
                    textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(12), 0, 0, 0)
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

    private fun openFullChat() {
        val target = Intent(this, ChatActivity::class.java).apply {
            conversationId?.let { putExtra("convId", it) }
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
            override fun onReadyForSpeech(p: Bundle?) { runOnUiThread { status.text = "Te escucho..." } }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) { speech = null; runOnUiThread { setMicListening(false); status.text = "No te escuché" } }
            override fun onResults(b: Bundle?) { speech = null; runOnUiThread { setMicListening(false) }; val r = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (!r.isNullOrEmpty()) { input.setText(r[0]); send() } }
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
        })
    }

    private fun setMicListening(active: Boolean) {
        micButton.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) Color.parseColor("#2F80FF") else Color.parseColor("#171B24"))
            if (active) setStroke(dp(2), Color.parseColor("#9BC4FF"))
        }
        micButton.setColorFilter(if (active) Color.WHITE else Color.parseColor("#C9C9D6"))
        micButton.contentDescription = if (active) "Detener grabación" else "Hablar con adarbot"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            window.decorView.postDelayed({ listen() }, 250)
        }
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
        when {
            requestCode == 700 && resultCode == RESULT_OK -> {
                val name = data?.data?.lastPathSegment ?: "archivo seleccionado"
                input.setText("[Adjunto: $name] ")
                input.setSelection(input.length())
                input.requestFocus()
            }
            requestCode == 701 && resultCode == RESULT_OK && data != null -> captureScreen(resultCode, data)
        }
    }

    override fun onDestroy() { speech?.destroy(); releaseCapture(); pendingBitmap?.recycle(); pendingBitmap = null; super.onDestroy() }

    private fun haptic(view: android.view.View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
