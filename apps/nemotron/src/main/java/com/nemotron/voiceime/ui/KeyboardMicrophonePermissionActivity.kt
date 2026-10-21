package com.nemotron.voiceime.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Native permission entry point launched by Nemotron Keyboard for user-started dictation. */
class KeyboardMicrophonePermissionActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var actionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Nemotron Keyboard"

        val density = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((24 * density).toInt(), (24 * density).toInt(),
                (24 * density).toInt(), (24 * density).toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        content.addView(TextView(this).apply {
            text = "Dictado por voz"
            textSize = 24f
        })
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, (16 * density).toInt(), 0, (20 * density).toInt())
            text = "Nemotron Keyboard necesita permiso de micrófono para escuchar cuando tú activas el dictado."
        }
        content.addView(status)
        actionButton = Button(this)
        content.addView(actionButton)
        setContentView(content)

        if (hasMicrophonePermission()) {
            finish()
            return
        }
        // Show Android's one-tap runtime permission dialog as soon as the user
        // reaches this activity from the double-space gesture.
        requestMicrophonePermission()
    }

    private fun requestMicrophonePermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ||
            !getPreferences(MODE_PRIVATE).getBoolean(KEY_REQUESTED, false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_REQUESTED, true).apply()
            actionButton.text = "Volver a solicitar permiso"
            actionButton.setOnClickListener {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
            }
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        } else {
            showSettingsAction()
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MICROPHONE) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Listo. Vuelve al teclado y toca dos veces espacio.", Toast.LENGTH_LONG).show()
            finish()
        } else {
            status.text = "El dictado necesita el permiso Micrófono. Habilítalo para Nemotron Keyboard y vuelve a tocar dos veces espacio."
            if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                actionButton.text = "Volver a solicitar permiso"
                actionButton.setOnClickListener { requestMicrophonePermission() }
            } else {
                showSettingsAction()
            }
        }
    }

    private fun showSettingsAction() {
        status.text = "Android mantiene bloqueado el micrófono. Habilita Micrófono en Ajustes > Aplicaciones > Nemotron Keyboard > Permisos."
        actionButton.text = "Abrir ajustes de la aplicación"
        actionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val REQUEST_MICROPHONE = 4102
        private const val KEY_REQUESTED = "microphone_permission_requested"
    }
}
