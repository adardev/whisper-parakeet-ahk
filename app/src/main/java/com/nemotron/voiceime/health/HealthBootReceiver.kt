package com.nemotron.voiceime.health

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver: reinicia HealthTransferService cuando el dispositivo arranca.
 */
class HealthBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "android.intent.action.MY_PACKAGE_REPLACED"
        ) {
            Log.d("HealthBootReceiver", "Boot completado, iniciando HealthTransferService")
            HealthTransferService.start(context)
        }
    }
}