package com.nemotron.voiceime.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.nemotron.voiceime.record.VoiceRecordService

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        if (!hasMic() || apiKeyMissing()) {
            val i = Intent(this, SetupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(i)
            finish()
            overridePendingTransition(0, 0)
            return
        }

        val action = if (VoiceRecordService.isRunning) VoiceRecordService.ACTION_STOP
                     else VoiceRecordService.ACTION_START

        val intent = Intent(this, VoiceRecordService::class.java).apply { this.action = action }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        finish()
        overridePendingTransition(0, 0)
    }

    private fun hasMic(): Boolean =
        checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun apiKeyMissing(): Boolean =
        com.nemotron.voiceime.data.SecureStore.getApiKey(this).isBlank()
}
