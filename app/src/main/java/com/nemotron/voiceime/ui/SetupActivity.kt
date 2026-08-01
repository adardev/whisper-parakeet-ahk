package com.nemotron.voiceime.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.dhizuku.AppPickerActivity
import com.nemotron.voiceime.net.NemotronStreamClient
import com.nemotron.voiceime.databinding.ActivityMainBinding

class SetupActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> refreshStatus() }

    private val autoFreezePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshAutoFreezeStatus()
    }

    private val client by lazy { NemotronStreamClient(SecureStore.getApiKey(this)) }
    private var accumulated = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        setUpButtons()
        setUpAutoFreeze()
    }

    private fun setUpButtons() {
        b.btnPermissions.setOnClickListener {
            if (hasMic()) refreshStatus()
            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        b.btnTest.setOnClickListener { testVoice() }

        b.btnSideKey.setOnClickListener {
            val intents = buildList {
                add(android.content.Intent("android.intent.action.MAIN").apply {
                    setClassName("com.samsung.android.settings", "com.samsung.android.settings.sidekey.SideKeySettings")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                add(android.content.Intent("com.samsung.settings.action.SIDEKEY_SETTINGS").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                add(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            for (i in intents) {
                try { startActivity(i); return@setOnClickListener } catch (_: Throwable) {}
            }
            try {
                startActivity(android.content.Intent("android.settings.ACTION_ADVANCED_SETTINGS").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Throwable) {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }

    private fun setUpAutoFreeze() {
        val enabled = SecureStore.isAutoFreezeEnabled(this)
        b.switchAutoFreeze.isChecked = enabled
        updateAutoFreezeIcon(enabled)
        refreshAutoFreezeStatus()

        b.switchAutoFreeze.setOnCheckedChangeListener { _, isChecked ->
            SecureStore.setAutoFreezeEnabled(this, isChecked)
            updateAutoFreezeIcon(isChecked)
            refreshAutoFreezeStatus()
            AutoFreezeScheduler.toggle(this, isChecked)
        }

        b.btnAutoFreezeApps.setOnClickListener {
            val intent = Intent(this, AppPickerActivity::class.java).apply {
                putExtra(AppPickerActivity.EXTRA_MODE, AppPickerActivity.MODE_AUTO_FREEZE)
            }
            autoFreezePickerLauncher.launch(intent)
        }

        b.switchAutoFreezeInstant.isChecked = SecureStore.isAutoFreezeTestMode(this)
        b.switchAutoFreezeInstant.setOnCheckedChangeListener { _, isChecked ->
            SecureStore.setAutoFreezeTestMode(this, isChecked)
        }

        b.switchAutoFreezeDoze.isChecked = SecureStore.isAutoFreezeDozeEnabled(this)
        b.switchAutoFreezeDoze.setOnCheckedChangeListener { _, isChecked ->
            SecureStore.setAutoFreezeDozeEnabled(this, isChecked)
        }
    }

    private fun updateAutoFreezeIcon(enabled: Boolean) {
        b.tvAutoFreezeIcon.text = if (enabled) "✓" else "✗"
        b.tvAutoFreezeIcon.setBackgroundColor(
            ContextCompat.getColor(this, if (enabled) R.color.status_ok else R.color.status_bad)
        )
        b.tvAutoFreezeIcon.setTextColor(Color.WHITE)
    }

    private fun refreshAutoFreezeStatus() {
        val enabled = SecureStore.isAutoFreezeEnabled(this)
        val apps = SecureStore.getAutoFreezeApps(this)
        val stopApps = SecureStore.getStopOnUnlockApps(this)
        b.tvAutoFreezeStatus.text = when {
            !enabled -> "Desactivado"
            apps.isEmpty() && stopApps.isEmpty() -> "Activado, sin apps seleccionadas"
            else -> "Activado: ${apps.size} congelan al apagar pantalla, ${stopApps.size} se detienen al desbloquear"
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshAutoFreezeStatus()
    }

    private fun refreshStatus() {
        val ok = hasMic()
        val key = SecureStore.getApiKey(this).isNotBlank()

        setStepIcon(b.tvStep1Icon, ok, "Permiso")
        setStepIcon(b.tvStep2Icon, key, "API key")

        val global = StringBuilder()
        when {
            ok && key -> global.append("✓ TODO LISTO. Pulsa el boton lateral de Samsung para grabar.")
            else -> global.append("✗ Faltan pasos: permiso micro + API key son obligatorios.")
        }
        b.tvGlobalStatus.text = global.toString()
        b.tvGlobalStatus.setTextColor(
            if (ok && key) Color.parseColor("#2E7D32")
            else Color.parseColor("#C62828")
        )
    }

    private fun setStepIcon(tv: TextView, ok: Boolean, doneLabel: String) {
        tv.text = if (ok) "✓" else "✗"
        tv.setBackgroundColor(
            ContextCompat.getColor(this, if (ok) R.color.status_ok else R.color.status_bad)
        )
        tv.setTextColor(Color.WHITE)
    }

    private fun testVoice() {
        if (!hasMic()) {
            b.tvResult.text = "Concede permiso de microfono primero (paso 1)."
            return
        }
        val key = SecureStore.getApiKey(this)
        if (key.isBlank()) {
            b.tvResult.text = "Configura tu API key (paso 2)."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            b.tvResult.text = "SpeechRecognizer base no disponible."
            return
        }

        b.tvResult.text = "Escuchando… (habla ahora)"
        b.btnTest.isEnabled = false
        accumulated = StringBuilder()

        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() { b.tvResult.text = "Escuchando…" }
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(ba: ByteArray?) {}
            override fun onEndOfSpeech() { b.tvResult.text = "Procesando…" }

            override fun onError(errorCode: Int) {
                try { sr.destroy() } catch (_: Throwable) {}
                b.tvResult.text = "Error #$errorCode. ¿Hablaste? Intenta de nuevo."
                b.btnTest.isEnabled = true
            }

            override fun onPartialResults(p: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val raw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                try { sr.destroy() } catch (_: Throwable) {}
                if (raw.isBlank()) {
                    b.tvResult.text = "No te escuche. Intenta otra vez."
                    b.btnTest.isEnabled = true
                    return
                }
                b.tvResult.text = "Raw: $raw\n\n→ Procesando…"
                callNemotron(raw)
            }

            override fun onEvent(p0: Int, p1: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val loc = SecureStore.getLocale(this@SetupActivity)
            try {
                val parts = loc.split("_")
                val l = java.util.Locale(parts.getOrNull(0) ?: "es", parts.getOrNull(1) ?: "")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, l.toLanguageTag())
            } catch (_: Throwable) {}
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        sr.startListening(intent)
    }

    private fun callNemotron(raw: String) {
        accumulated = StringBuilder()
        client.stream(
            userText = raw,
            model = SecureStore.getModel(this),
            system = SecureStore.getSystemPrompt(this),
            onToken = { tok ->
                accumulated.append(tok)
                runOnUiThread {
                    b.tvResult.text = "Streaming:\n\n$accumulated"
                }
            },
            onComplete = { final ->
                runOnUiThread {
                    b.tvResult.text = "Resultado:\n\n$final"
                    b.btnTest.isEnabled = true
                    refreshStatus()
                }
            },
            onError = { t ->
                runOnUiThread {
                    b.tvResult.text = "Error: ${t.message}"
                    b.btnTest.isEnabled = true
                }
            }
        )
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
