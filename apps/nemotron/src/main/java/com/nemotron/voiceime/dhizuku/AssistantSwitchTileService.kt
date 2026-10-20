package com.nemotron.voiceime.dhizuku

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.nemotron.voiceime.R

/** Quick Settings tile that switches the system assistant between Gemini and Tootsie. */
class AssistantSwitchTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (!ShizukuManager.hasPermission()) {
            Toast.makeText(this, "Activa Shizuku para alternar el asistente", Toast.LENGTH_LONG).show()
            openDefaultAssistantSettings()
            return
        }

        qsTile?.state = Tile.STATE_UNAVAILABLE
        qsTile?.updateTile()
        Thread {
            val current = ShizukuManager.getDefaultAssistantPackage()
            val next = if (current == TOOTSIE_PACKAGE) GEMINI_PACKAGE else TOOTSIE_PACKAGE
            val changed = ShizukuManager.setDefaultAssistantPackage(next)
            handler.post {
                refreshTile()
                if (!changed) {
                    Log.w(TAG, "Role switch failed: current=$current, requested=$next")
                    Toast.makeText(this, "No se pudo cambiar el asistente; abre Ajustes", Toast.LENGTH_LONG).show()
                    openDefaultAssistantSettings()
                } else {
                    val name = if (next == GEMINI_PACKAGE) "Gemini" else "Tootsie"
                    Toast.makeText(this, "Asistente predeterminado: $name", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun refreshTile() {
        val tile = qsTile ?: return
        val current = ShizukuManager.getDefaultAssistantPackage()
        tile.label = "Gemini"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = null
        tile.icon = Icon.createWithResource(this, R.drawable.ic_assistant_switch_tile)
        tile.state = if (current == GEMINI_PACKAGE) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun openDefaultAssistantSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    SETTINGS_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Could not open default app settings", error)
            Toast.makeText(this, "Abre Ajustes > Aplicaciones > Apps predeterminadas", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "AssistantSwitchTile"
        private const val SETTINGS_REQUEST_CODE = 4071
        private const val GEMINI_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val TOOTSIE_PACKAGE = "com.adarbot.app"
    }
}
