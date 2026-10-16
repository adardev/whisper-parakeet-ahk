package com.nemotron.voiceime.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.nemotron.voiceime.dhizuku.SleepModeController

/** Invisible launch target for assigning Sleep Mode to Samsung's Side key double press. */
class SleepModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread {
            val result = SleepModeController.toggle(applicationContext)
            Log.i(TAG, result.message)
            runOnUiThread {
                if (!result.success) Log.w(TAG, result.message)
                finish()
                overridePendingTransition(0, 0)
            }
        }.start()
    }

    private companion object { const val TAG = "SleepModeShortcut" }
}
