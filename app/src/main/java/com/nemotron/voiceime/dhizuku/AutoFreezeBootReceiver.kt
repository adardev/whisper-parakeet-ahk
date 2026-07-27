package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

class AutoFreezeBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        if (SecureStore.isAutoAirplane(context)) {
            Log.d(TAG, "$action: Airplane Lock was active – user must re-enable from tile")
        }
    }

    companion object {
        private const val TAG = "AirplaneBoot"
    }
}
