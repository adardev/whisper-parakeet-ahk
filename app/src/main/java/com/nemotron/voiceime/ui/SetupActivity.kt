package com.nemotron.voiceime.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
import com.nemotron.voiceime.dhizuku.ShizukuManager
import rikka.shizuku.Shizuku
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        setUpButtons()
        setUpAutoFreeze()
        setUpShizuku()
    }

    private fun setUpShizuku() {
        refreshShizukuStatus()

        b.btnShizukuPermission.setOnClickListener {
            if (!ShizukuManager.isAvailable()) {
                Toast.makeText(this, "Shizuku unavailable: open the Shizuku app and start the server", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            // Forzar diálogo siempre para que aparezca en lista de Authorized apps
            val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
                runOnUiThread {
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    Toast.makeText(
                        this,
                        if (granted) "Shizuku authorized ✓ (it should now appear in the list)" else "Shizuku denied: freeze/doze will not work",
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshShizukuStatus()
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            ShizukuManager.requestPermission()
        }
    }

    private fun refreshShizukuStatus() {
        val available = ShizukuManager.isAvailable()
        val granted = ShizukuManager.hasPermission()
        val icon = if (granted) "✓" else if (available) "○" else "✗"
        val text = when {
            !available -> "Shizuku is not running: open Shizuku and tap \"Start\""
            !granted -> "Shizuku is running without permission: tap \"Request permission\""
            else -> "Shizuku authorized ✓"
        }
        b.tvShizukuIcon.text = icon
        b.tvShizukuIcon.setBackgroundColor(
            ContextCompat.getColor(this, if (granted) R.color.status_ok else if (available) R.color.status_bad else R.color.status_bad)
        )
        b.tvShizukuIcon.setTextColor(Color.WHITE)
        b.tvShizukuStatus.text = text
        b.btnShizukuPermission.text = if (!granted) "Request Shizuku permission" else "Request Shizuku permission again"
        b.btnShizukuPermission.isEnabled = available
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

        b.btnEscolomos.setOnClickListener { launchAppShortcut() }

        b.btnAddShortcuts.setOnClickListener {
            startActivity(Intent(this, ShortcutPickerActivity::class.java))
        }

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
            if (isChecked) ShizukuManager.requestPermission()
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
            if (isChecked) ShizukuManager.requestPermission()
            SecureStore.setAutoFreezeDozeEnabled(this, isChecked)
        }

        b.btnAutoFreezeDozeExempt.setOnClickListener {
            val intent = Intent(this, AppPickerActivity::class.java).apply {
                putExtra(AppPickerActivity.EXTRA_MODE, AppPickerActivity.MODE_DOZE_EXEMPT)
            }
            autoFreezePickerLauncher.launch(intent)
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
            !enabled -> "Disabled"
            apps.isEmpty() && stopApps.isEmpty() -> "Enabled, no apps selected"
            else -> "Enabled: ${apps.size} freeze when the screen turns off, ${stopApps.size} stop when unlocked"
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshAutoFreezeStatus()
        refreshShizukuStatus()
    }

    private fun refreshStatus() {
        val ok = hasMic()

        setStepIcon(b.tvStep1Icon, ok, "Permission")

        val global = StringBuilder()
        when {
            ok -> global.append("✓ ALL SET. Press the Samsung side key to record.")
            else -> global.append("✗ Missing steps: microphone permission is required.")
        }
        b.tvGlobalStatus.text = global.toString()
        b.tvGlobalStatus.setTextColor(
            if (ok) Color.parseColor("#2E7D32")
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
            b.tvResult.text = "Grant microphone permission first (step 1)."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            b.tvResult.text = "Speech recognition is not available on this device."
            return
        }

        b.tvResult.text = "Listening… (speak now)"
        b.btnTest.isEnabled = false

        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() { b.tvResult.text = "Listening…" }
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(ba: ByteArray?) {}
            override fun onEndOfSpeech() { b.tvResult.text = "Processing…" }

            override fun onError(errorCode: Int) {
                try { sr.destroy() } catch (_: Throwable) {}
                b.tvResult.text = "Error #$errorCode. Did you speak? Try again."
                b.btnTest.isEnabled = true
            }

            override fun onPartialResults(p: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val raw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                try { sr.destroy() } catch (_: Throwable) {}
                if (raw.isBlank()) {
                    b.tvResult.text = "I could not hear you. Try again."
                    b.btnTest.isEnabled = true
                    return
                }
                b.tvResult.text = "Result:\n\n$raw"
                b.btnTest.isEnabled = true
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

    private fun requestAppShortcuts() {
        val apps = listOf(
            Triple("Escolomos", "com.ceti.escolomos", "com.ceti.escolomos.MainActivity"),
            Triple("Ingeniería Virtual", "com.ceti.ingenieriavirtual", "com.ceti.ingenieriavirtual.MainActivity"),
            Triple("Obsidian", "md.obsidiao", "md.obsidiao.MainActivity"),
            Triple("Classroom", "com.google.android.apps.classroom", "com.google.android.apps.classroom.classroomflutter.MainActivity"),
            Triple("WhatsApp Business", "com.whatsapp.w4b", "com.whatsapp.Main"),
            Triple("WhatsApp", "com.whatsapp", "com.whatsapp.Main"),
            Triple("Instagram", "com.instagram.android", "com.instagram.android.activity.MainTabActivity"),
            Triple("Proton Pass", "proton.android.past", "proton.android.past.ui.MainActivity"),
        )

        if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, "This launcher does not support pinned shortcuts", Toast.LENGTH_SHORT).show()
            return
        }

        var added = 0
        var delay = 300L
        for ((label, pkg, activity) in apps) {
            val appInfo = try { packageManager.getApplicationInfo(pkg, 0) } catch (_: Throwable) { null }
            if (appInfo == null) continue

            // Renderiza el icono nativo a bitmap (soporta adaptive icons sin depender de
            // recursos de otro paquete, evitando que el launcher crashee).
            val drawable = packageManager.getApplicationIcon(pkg)
            val size = 108
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            val icon = androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap)

            val intent = Intent(this, ShortcutActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                putExtra(ShortcutActivity.EXTRA_PACKAGE, pkg)
                putExtra(ShortcutActivity.EXTRA_ACTIVITY, activity)
            }

            val shortcut = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, "shortcut_$pkg")
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(icon)
                .setIntent(intent)
                .build()

            android.os.Handler(Looper.getMainLooper()).postDelayed({
                androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
            }, delay)
            delay += 900
            added++
        }

        Toast.makeText(
            this,
            "Confirming $added shortcuts… Accept each dialog on screen.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun launchAppShortcut() {
        val targetPackage = "com.ceti.escolomos"
        val targetActivity = "com.ceti.escolomos.MainActivity"
        // Shizuku: am start funciona aunque la app esté oculta/congelada.
        if (ShizukuManager.hasPermission()) {
            Thread {
                ShizukuManager.launchApp(targetPackage, targetActivity)
            }.start()
        } else {
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Grant Shizuku permission to open frozen apps",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
