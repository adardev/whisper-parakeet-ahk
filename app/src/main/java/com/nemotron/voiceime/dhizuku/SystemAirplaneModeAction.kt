package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.rosan.dhizuku.api.Dhizuku

/** Privileged airplane-mode action used by Airplane Lock. */
object SystemAirplaneModeAction {

    fun set(context: Context, enabled: Boolean): Boolean {
        if (ShizukuManager.setAirplaneMode(context, enabled)) return true
        if (setThroughConnectivityService(context, enabled)) return true

        return try {
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                if (enabled) 1 else 0
            )
            context.sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING)
                putExtra("state", enabled)
            })
            Log.d(TAG, "System airplane mode broadcast sent: $enabled")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Could not toggle immediate airplane mode", t)
            false
        }
    }

    private fun setThroughConnectivityService(context: Context, enabled: Boolean): Boolean {
        return try {
            Dhizuku.init(context)
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val connectivity = getService.invoke(null, "connectivity") as? IBinder ?: return false

            val data = android.os.Parcel.obtain()
            val reply = android.os.Parcel.obtain()
            try {
                data.writeInterfaceToken(CONNECTIVITY_DESCRIPTOR)
                data.writeBoolean(enabled)
                if (!Dhizuku.remoteTransact(connectivity, TRANSACTION_SET_AIRPLANE_MODE, data, reply, 0)) {
                    Log.w(TAG, "Dhizuku remoteTransact returned false")
                    return false
                }
                reply.readException()
            } finally {
                data.recycle()
                reply.recycle()
            }
            Log.d(TAG, "ConnectivityService.setAirplaneMode($enabled) completed through Dhizuku")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "ConnectivityService direct call unavailable", t)
            false
        }
    }

    private const val TAG = "SystemAirplaneTile"
    private const val CONNECTIVITY_DESCRIPTOR = "android.net.IConnectivityManager"
    private const val TRANSACTION_SET_AIRPLANE_MODE = 36
}
