package com.nemotron.voiceime.dhizuku

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

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

        val (targetPackage, targetActivity) = when (tileComponent?.className) {
            "com.nemotron.voiceime.dhizuku.AndroidAutoTileService" ->
                "com.google.android.projection.gearhead" to null
            "com.nemotron.voiceime.dhizuku.TelegramTileService" ->
                "org.telegram.messenger" to null
            "com.nemotron.voiceime.dhizuku.GmsTileService" ->
                "com.google.android.gms" to null
            "com.nemotron.voiceime.dhizuku.SyncthingTileService" ->
                "com.github.catfriend1.syncthingfork" to null
            "com.nemotron.voiceime.dhizuku.WalletTileService" ->
                "com.google.android.apps.walletnfcrel" to null
            "com.nemotron.voiceime.dhizuku.WatchManagerTileService" ->
                "com.samsung.android.app.watchmanager" to
                    "com.samsung.android.app.watchmanager.setupwizard.SetupWizardWelcomeActivity"
            "com.nemotron.voiceime.dhizuku.Fit3TileService" ->
                "com.samsung.wearable.fit3plugin" to null
            else -> null to null
        }

        Log.d(TAG, "Target package: $targetPackage")

        if (targetPackage != null) {
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

    companion object {
        private const val TAG = "TilePreferences"
    }
}
