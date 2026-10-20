package com.nemotron.voiceime.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.nemotron.voiceime.dhizuku.ShizukuManager

/**
 * Actividad transparente lanzada desde los atajos del home screen.
 * Abre la app destino (vía Shizuku si hace falta) y se cierra.
 */
class ShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent?.getStringExtra(EXTRA_PACKAGE) ?: ""
        val activity = intent?.getStringExtra(EXTRA_ACTIVITY) ?: ""
        Log.d(TAG, "open pkg=$pkg activity=$activity")

        if (pkg.isNotBlank() && activity.isNotBlank()) {
            if (ShizukuManager.hasPermission()) {
                Thread {
                    ShizukuManager.launchApp(pkg, activity)
                }.start()
            } else {
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    }
                } catch (_: Throwable) {}
            }
        }

        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "ShortcutActivity"
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_ACTIVITY = "activity"
    }
}
