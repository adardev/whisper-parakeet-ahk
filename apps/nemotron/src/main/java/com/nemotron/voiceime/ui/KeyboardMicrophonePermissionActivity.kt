package com.nemotron.voiceime.ui

import android.Manifest
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding

/** Permission entry point launched by Nemotron Keyboard when dictation is first used. */
class KeyboardMicrophonePermissionActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var grantButton: Button

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "Micrófono permitido. Vuelve al teclado y toca dos veces espacio.", Toast.LENGTH_LONG).show()
            finish()
        } else {
            showDeniedState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Nemotron Keyboard"

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((24 * resources.displayMetrics.density).toInt())
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
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0,
                (20 * resources.displayMetrics.density).toInt())
        }
        content.addView(status)
        grantButton = Button(this).apply {
            text = "Permitir micrófono"
            setOnClickListener {
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        content.addView(grantButton)
        setContentView(content)

        if (hasMicrophonePermission()) {
            status.text = "El permiso de micrófono ya está concedido. Regresa al teclado y toca dos veces espacio para dictar."
            grantButton.text = "Listo"
            grantButton.setOnClickListener { finish() }
        } else {
            status.text = "Android bloqueó el micrófono para Nemotron Keyboard. Se necesita este permiso para convertir tu voz en texto cuando actives el dictado. No existe un permiso separado de micrófono en segundo plano para este uso."
        }
    }

    private fun showDeniedState() {
        status.text = "El permiso de micrófono sigue denegado; el dictado no puede escuchar sin él. Puedes volver a solicitarlo o habilitarlo en Ajustes de la aplicación."
        grantButton.text = if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            "Volver a solicitar permiso"
        } else {
            "Abrir ajustes de la aplicación"
        }
        grantButton.setOnClickListener {
            if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
