package com.nemotron.voiceime.dhizuku

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

/**
 * Activity que se abre al hacer long-press en un tile.
 * Detecta qué tile fue presionado y abre la app correspondiente.
 */
class TilePreferencesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate")
        Log.d(TAG, "Intent action: ${intent?.action}")

        // Obtener el componente del tile que fue presionado
        val tileComponent = intent?.getParcelableExtra<ComponentName>(
            "android.intent.extra.COMPONENT_NAME"
        )
        Log.d(TAG, "Tile component: ${tileComponent?.className}")

        val launchTargets: List<Pair<String, String?>> =
            when (tileComponent?.className) {
                "com.nemotron.voiceime.dhizuku.AndroidAutoTileService" ->
                    listOf("com.google.android.projection.gearhead" to null)
                "com.nemotron.voiceime.dhizuku.TelegramTileService" -> {
                    runTermuxOrcaCommand()
                    emptyList()
                }
                "com.nemotron.voiceime.dhizuku.GmsTileService" ->
                    listOf("com.google.android.gms" to null)
                "com.nemotron.voiceime.dhizuku.SyncthingTileService" ->
                    listOf(
                        "com.github.catfriend1.syncthingfork" to null,
                        "com.github.catfriend1.syncthingforl" to
                            "com.nutomic.syncthingandroid.onboarding.OnboardingActivity"
                    )
                "com.nemotron.voiceime.dhizuku.WalletTileService" ->
                    listOf("com.google.android.apps.walletnfcrel" to null)
                "com.nemotron.voiceime.dhizuku.WatchManagerTileService" ->
                    listOf(
                        "com.samsung.android.app.watchmanager" to
                            "com.samsung.android.app.watchmanager.setupwizard.SetupWizardWelcomeActivity"
                    )
                "com.nemotron.voiceime.dhizuku.Fit3TileService" ->
                    listOf("com.samsung.wearable.fit3plugin" to null)
                "com.nemotron.voiceime.dhizuku.WorkTileService" ->
                    listOf(
                        "com.ceti.escolomos" to "com.ceti.escolomos.MainActivity",
                        "com.ceti.ingenieriavirtual" to "com.ceti.ingenieriavirtual.MainActivity",
                        "md.obsidiao" to "md.obsidiao.MainActivity",
                        "com.google.android.apps.classroom" to
                            "com.google.android.apps.classroom.classroomflutter.MainActivity",
                        "com.whatsapp.w4b" to "com.whatsapp.Main",
                        "proton.android.past" to "proton.android.past.ui.MainActivity"
                    )
                else -> emptyList()
            }

        Log.d(TAG, "Target packages: $launchTargets")

        launchTargets.forEach { (targetPackage, targetActivity) ->
            if (targetActivity != null && ShizukuManager.hasPermission()) {
                Thread {
                    ShizukuManager.launchApp(targetPackage, targetActivity)
                }.start()
            } else {
                val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                }
            }
        }

        // Delay para que dé tiempo a abrir la app antes de cerrar
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 500)
    }

    private fun runTermuxOrcaCommand() {
        try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra(
                    "com.termux.RUN_COMMAND_ARGUMENTS",
                    arrayOf("-lc", "exec ssh -J serveo.net adaredu@adardev-orca-20260928-2")
                )
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            }
            startService(intent)
        } catch (error: Throwable) {
            Log.e(TAG, "No se pudo ejecutar Orca en Termux", error)
            Toast.makeText(this, "Activa el permiso de Termux para Nemotron", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "TilePreferences"
    }
}
