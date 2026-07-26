package com.nemotron.voiceime.dhizuku

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import com.nemotron.voiceime.data.SecureStore

object DhizukuManager {

    private const val TAG = "DhizukuManager"

    private var dpm: DevicePolicyManager? = null

    private fun getDpm(context: Context): DevicePolicyManager? {
        if (dpm != null) return dpm
        dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        Log.d(TAG, "DPM obtained: $dpm")
        return dpm
    }

    fun isDhizukuAvailable(): Boolean = try {
        Dhizuku.init()
        Log.d(TAG, "Dhizuku available")
        true
    } catch (t: Throwable) {
        Log.w(TAG, "Dhizuku not available", t)
        false
    }

    fun isPermissionGranted(): Boolean = try {
        val granted = Dhizuku.isPermissionGranted()
        Log.d(TAG, "isPermissionGranted: $granted")
        granted
    } catch (t: Throwable) {
        Log.e(TAG, "isPermissionGranted failed", t)
        false
    }

    fun requestPermission(activity: android.app.Activity, onResult: (Boolean) -> Unit) {
        Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
            override fun onRequestPermission(grantResult: Int) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Dhizuku permission: granted=$granted")
                onResult(granted)
            }
        })
    }

    fun setDelegatedScopes() {
        try {
            val scopes = Dhizuku.getDelegatedScopes()
            if (!scopes.contains(DevicePolicyManager.DELEGATION_PACKAGE_ACCESS)) {
                Dhizuku.setDelegatedScopes(arrayOf(DevicePolicyManager.DELEGATION_PACKAGE_ACCESS))
                Log.d(TAG, "Set delegated scope: DELEGATION_PACKAGE_ACCESS")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "setDelegatedScopes failed", t)
        }
    }

    fun hideApp(context: Context, packageName: String): Boolean {
        val result = hideAppRaw(context, packageName)
        if (result) SecureStore.addFrozenApp(context, packageName)
        return result
    }

    fun unhideApp(context: Context, packageName: String): Boolean {
        val result = unhideAppRaw(context, packageName)
        if (result) SecureStore.removeFrozenApp(context, packageName)
        return result
    }

    fun hideAppRaw(context: Context, packageName: String): Boolean {
        val dpm = getDpm(context) ?: return false
        return try {
            val method = dpm.javaClass.getMethod(
                "setApplicationHidden",
                ComponentName::class.java,
                String::class.java,
                Boolean::class.java
            )
            val result = method.invoke(dpm, null, packageName, true) as Boolean
            Log.d(TAG, "hideAppRaw($packageName) = $result")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "hideAppRaw($packageName) failed: ${t.message}", t)
            false
        }
    }

    fun unhideAppRaw(context: Context, packageName: String): Boolean {
        val dpm = getDpm(context) ?: return false
        return try {
            val method = dpm.javaClass.getMethod(
                "setApplicationHidden",
                ComponentName::class.java,
                String::class.java,
                Boolean::class.java
            )
            val result = method.invoke(dpm, null, packageName, false) as Boolean
            Log.d(TAG, "unhideAppRaw($packageName) = $result")
            result
        } catch (t: Throwable) {
            Log.e(TAG, "unhideAppRaw($packageName) failed: ${t.message}", t)
            false
        }
    }

    fun isCurrentlyFrozen(context: Context, packageName: String): Boolean {
        val dpm = getDpm(context) ?: return false
        return try {
            val method = dpm.javaClass.getMethod(
                "isApplicationHidden",
                ComponentName::class.java,
                String::class.java
            )
            val result = method.invoke(dpm, null, packageName) as Boolean
            result
        } catch (t: Throwable) {
            Log.e(TAG, "isCurrentlyFrozen($packageName) failed: ${t.message}", t)
            false
        }
    }

    fun freezeAll(context: Context): Int {
        val apps = SecureStore.getFrozenApps(context)
        if (apps.isEmpty()) return 0
        var success = 0
        for (pkg in apps) {
            if (hideApp(context, pkg)) success++
        }
        Log.d(TAG, "freezeAll: $success/${apps.size}")
        return success
    }

    fun unfreezeAll(context: Context): Int {
        val apps = SecureStore.getFrozenApps(context)
        if (apps.isEmpty()) return 0
        var success = 0
        for (pkg in apps) {
            if (unhideApp(context, pkg)) success++
        }
        Log.d(TAG, "unfreezeAll: $success/${apps.size}")
        return success
    }

    fun isCurrentlyFrozen(context: Context): Boolean {
        val apps = SecureStore.getFrozenApps(context)
        if (apps.isEmpty()) return false
        return apps.all { isCurrentlyFrozen(context, it) }
    }
}
