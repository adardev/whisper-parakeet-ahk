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

        val launchTargets: List<Pair<String, String?>> =
            when (tileComponent?.className) {
                "com.nemotron.voiceime.dhizuku.AndroidAutoTileService" ->
                    listOf("com.google.android.projection.gearhead" to null)
                "com.nemotron.voiceime.dhizuku.TelegramTileService" ->
                    listOf("org.telegram.messenger" to null)
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

    companion object {
        private const val TAG = "TilePreferences"
    }
}
