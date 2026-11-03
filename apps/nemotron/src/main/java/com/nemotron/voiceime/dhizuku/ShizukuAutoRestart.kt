package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.content.Intent
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

/** Requests the Shizuku fork's authenticated, headless start receiver. */
object ShizukuAutoRestart {
    private const val TAG = "ShizukuAutoRestart"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val START_ACTION = "$SHIZUKU_PACKAGE.START"
    private const val START_RECEIVER = "moe.shizuku.manager.receiver.ManualStartReceiver"

    /** Returns true only when an authenticated start request was dispatched. */
    fun requestStart(context: Context): Boolean {
        if (!SecureStore.isAndroidAutoTileOn(context)) {
            Log.d(TAG, "Android Auto tile is off; not requesting Shizuku start")
            return false
        }
        val token = SecureStore.getShizukuStartAuthToken(context)
        if (token.isBlank()) {
            Log.w(TAG, "No Shizuku start auth token configured; silent restart unavailable")
            return false
        }
        return try {
            val intent = Intent(START_ACTION)
                .setClassName(SHIZUKU_PACKAGE, START_RECEIVER)
                .putExtra("auth", token)
            context.sendBroadcast(intent)
            Log.i(TAG, "Dispatched Shizuku headless start request while Android Auto is on")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Could not dispatch Shizuku's headless start request", t)
            false
        }
    }
}
