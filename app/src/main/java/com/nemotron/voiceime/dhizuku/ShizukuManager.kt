package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/** Shell-backed operations supplied by a running Shizuku server. */
object ShizukuManager {

    private const val TAG = "ShizukuManager"
    private const val REQUEST_CODE = 4011
    private const val CONNECTIVITY_DESCRIPTOR = "android.net.IConnectivityManager"
    // IConnectivityManager.aidl transaction index on this device's Android 15 build.
    private const val TRANSACTION_SET_AIRPLANE_MODE = 36

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        Log.d(TAG, "Shizuku is not running", t)
        false
    }

    fun hasPermission(): Boolean = try {
        isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        Log.w(TAG, "Could not check Shizuku permission", t)
        false
    }

    fun requestPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            if (hasPermission()) true
            else {
                Shizuku.requestPermission(REQUEST_CODE)
                true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not request Shizuku permission", t)
            false
        }
    }

    fun setAirplaneMode(context: Context, enabled: Boolean): Boolean {
        if (!hasPermission()) return false
        return try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val connectivity = getService.invoke(null, "connectivity") as? IBinder ?: return false
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(CONNECTIVITY_DESCRIPTOR)
                data.writeBoolean(enabled)
                val shellConnectivity = ShizukuBinderWrapper(connectivity)
                if (!shellConnectivity.transact(TRANSACTION_SET_AIRPLANE_MODE, data, reply, 0)) {
                    Log.w(TAG, "Shizuku ConnectivityService transaction returned false")
                    return false
                }
                reply.readException()
            } finally {
                data.recycle()
                reply.recycle()
            }
            Log.d(TAG, "ConnectivityService.setAirplaneMode($enabled) completed through Shizuku")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Shizuku airplane-mode call failed", t)
            false
        }
    }
}
