package com.nemotron.voiceime.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.nemotron.voiceime.chat.AssistActivity

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        if (!hasMic()) {
            val i = Intent(this, SetupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(i)
            finish()
            overridePendingTransition(0, 0)
            return
        }

        // Samsung's side-key route enters this invisible activity. Always
        // forward it to the same assistant overlay, even when ChatActivity is
        // already visible, instead of toggling the legacy recorder behind it.
        startActivity(Intent(this, AssistActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
        overridePendingTransition(0, 0)
    }

    private fun hasMic(): Boolean =
        checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
