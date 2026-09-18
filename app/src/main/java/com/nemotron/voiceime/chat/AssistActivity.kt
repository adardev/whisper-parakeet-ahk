package com.nemotron.voiceime.chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class AssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = Intent(this, ChatsListActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("fromAssist", true)
        }
        startActivity(i)
        finish()
    }
}