package com.nemotron.voiceime.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nemotron.voiceime.R
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.dhizuku.AppPickerActivity
import com.nemotron.voiceime.dhizuku.RootShellManager
import com.nemotron.voiceime.dhizuku.RootAccessManager
import com.nemotron.voiceime.databinding.ActivityMainBinding

class SetupActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

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
        setUpRootAccess()
    }

    private fun setUpRootAccess() {
        refreshRootStatus()
        b.btnKernelSUPermission.setOnClickListener {
            b.btnKernelSUPermission.isEnabled = false
            b.tvKernelSUStatus.text = "Waiting for KernelSU approval…"
            Thread {
                showRootResult(RootAccessManager.requestAccess())
            }.start()
        }
    }

    private fun refreshRootStatus() {
        b.tvKernelSUStatus.text = "Checking KernelSU root access…"
        Thread {
            showRootResult(RootAccessManager.requestAccess(timeoutMs = 5_000L))
        }.start()
    }

    private fun showRootResult(result: RootAccessManager.Result) {
        runOnUiThread {
            val granted = result.success
            b.btnKernelSUPermission.isEnabled = true
            b.tvKernelSUIcon.text = if (granted) "✓" else "✗"
            b.tvKernelSUIcon.setBackgroundColor(
                ContextCompat.getColor(this, if (granted) R.color.status_ok else R.color.status_bad)
            )
            b.tvKernelSUIcon.setTextColor(Color.WHITE)
            b.tvKernelSUStatus.text = if (granted) {
                "KernelSU root active ✓ (${result.detail})"
            } else {
                "KernelSU root unavailable: ${result.detail}"
            }
            b.btnKernelSUPermission.text = if (granted) {
                "Verify KernelSU root access"
            } else {
                "Request KernelSU root access"
            }
        }
    }

    private fun setUpButtons() {
        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

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
            if (isChecked) RootShellManager.requestPermission()
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

        b.btnStopBackgroundServices.setOnClickListener {
            val intent = Intent(this, AppPickerActivity::class.java).apply {
                putExtra(AppPickerActivity.EXTRA_MODE, AppPickerActivity.MODE_STOP_SERVICES)
            }
            autoFreezePickerLauncher.launch(intent)
        }

        b.switchAutoFreezeInstant.isChecked = SecureStore.isAutoFreezeTestMode(this)
        b.switchAutoFreezeInstant.setOnCheckedChangeListener { _, isChecked ->
            SecureStore.setAutoFreezeTestMode(this, isChecked)
        }

        b.switchAutoFreezeDoze.isChecked = SecureStore.isAutoFreezeDozeEnabled(this)
        b.switchAutoFreezeDoze.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) RootShellManager.requestPermission()
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
        val stopServices = SecureStore.getStopOnScreenOffServices(this)
        b.tvAutoFreezeStatus.text = when {
            !enabled -> "Disabled"
            apps.isEmpty() && stopApps.isEmpty() && stopServices.isEmpty() -> "Enabled, no apps or services selected"
            else -> "Enabled: ${apps.size} apps freeze on screen off, ${stopServices.size} services stop on screen off, ${stopApps.size} apps stop when unlocked"
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAutoFreezeStatus()
        refreshRootStatus()
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

            // Instagram usa la marca de Nemotron en el atajo; las otras apps
            // conservan su icono propio.
            val drawable = packageManager.getApplicationIcon(
                if (pkg == "com.instagram.android") packageName else pkg
            )
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
        // Root: am start works even when the app is hidden/frozen.
        if (RootShellManager.hasPermission()) {
            Thread {
                RootShellManager.launchApp(targetPackage, targetActivity)
            }.start()
        } else {
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Grant KernelSU root access to open frozen apps",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

}
