package com.nemotron.voiceime.dhizuku

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

/** Visible foreground bridge required to start Termux's terminal session from a QS tap. */
class OrcaCommandActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var commandRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Conectando con Orca…"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        })
    }

    override fun onResume() {
        super.onResume()
        if (commandRequested) return
        commandRequested = true

        // Send immediately while Android still considers this resumed Activity
        // foreground. A delay let Samsung's background-start restriction win.
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
            Log.i(TAG, "RunCommandService aceptó la solicitud de conexión Orca")
            handler.postDelayed({ finish() }, 1_000L)
        } catch (error: Throwable) {
            Log.e(TAG, "No se pudo iniciar la sesión Orca desde primer plano", error)
            Toast.makeText(this, "No se pudo iniciar Orca en Termux", Toast.LENGTH_LONG).show()
            handler.postDelayed({ finish() }, 2_000L)
        }
    }

    companion object {
        private const val TAG = "OrcaCommand"
    }
}
