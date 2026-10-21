package com.nemotron.voiceime.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log

/** Invisible launch target retained for the configured Nemotron side-key action. */
class SleepModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        runCatching { com.nemotron.voiceime.dhizuku.SleepModeActionService.start(this) }
            .onFailure { Log.e(TAG, "No se pudo iniciar Sleep Mode", it) }
        finish()
    }

    private companion object { const val TAG = "SideKeySleepMode" }
}
