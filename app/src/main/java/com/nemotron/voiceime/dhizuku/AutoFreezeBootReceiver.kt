package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

/** Restores the lock listener after a device reboot when either automation is enabled. */
class AutoFreezeBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val airplaneLockEnabled = SecureStore.isAutoAirplane(context)
        val autoFreezeEnabled = SecureStore.isAutoFreeze(context)
        if (!airplaneLockEnabled && !autoFreezeEnabled) {
            Log.d(TAG, "$action: no lock automation enabled")
            return
        }

        try {
            AutoFreezeService.start(context)
            Log.d(TAG, "$action: lock listener restored; airplaneLock=$airplaneLockEnabled")
        } catch (t: Throwable) {
            Log.e(TAG, "$action: could not restore lock listener", t)
        }
    }

    companion object {
        private const val TAG = "AutoFreezeBoot"
    }
}
