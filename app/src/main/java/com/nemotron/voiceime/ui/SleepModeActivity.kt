package com.nemotron.voiceime.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.nemotron.voiceime.dhizuku.SleepModeController

/** Invisible launch target retained for the configured Nemotron side-key action. */
class SleepModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        Thread {
            val result = SleepModeController.toggle(applicationContext)
            if (!result.success) Log.w(TAG, result.message)
            runOnUiThread {
                finish()
                overridePendingTransition(0, 0)
            }
        }.start()
    }

    private companion object { const val TAG = "SideKeySleepMode" }
}
