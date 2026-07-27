package com.nemotron.voiceime.dhizuku

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserHandle
import android.provider.Settings
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
            if (hideAppRaw(context, pkg)) success++
        }
        Log.d(TAG, "freezeAll: $success/${apps.size}")
        return success
    }

    fun unfreezeAll(context: Context): Int {
        val apps = SecureStore.getFrozenApps(context)
        if (apps.isEmpty()) return 0
        var success = 0
        for (pkg in apps) {
            if (unhideAppRaw(context, pkg)) success++
        }
        Log.d(TAG, "unfreezeAll: $success/${apps.size}")
        return success
    }

    fun isCurrentlyFrozen(context: Context): Boolean {
        val apps = SecureStore.getFrozenApps(context)
        if (apps.isEmpty()) return false
        return apps.all { isCurrentlyFrozen(context, it) }
    }

    fun setGlobalSetting(context: Context, setting: String, value: String): Boolean {
        val dpm = getDpm(context) ?: return false
        return try {
            val method = dpm.javaClass.getMethod(
                "setGlobalSetting",
                ComponentName::class.java,
                String::class.java,
                String::class.java
            )
            val admin = try {
                val m = dpm.javaClass.getMethod("getDeviceOwnerComponent")
                m.invoke(dpm) as? ComponentName
            } catch (_: Throwable) {
                ComponentName("com.rosan.dhizuku", "com.rosan.dhizuku.server.DhizukuDAReceiver")
            }
            val result = method.invoke(dpm, admin, setting, value)
            Log.d(TAG, "setGlobalSetting($setting, $value) = $result")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "setGlobalSetting($setting, $value) failed: ${t.message}", t)
            false
        }
    }

    /** Uses the ConnectivityService command that was confirmed working on this device. */
    fun setAirplaneMode(context: Context, enabled: Boolean): Boolean {
        return try {
            Dhizuku.init(context)
            if (!Dhizuku.isPermissionGranted()) {
                Log.w(TAG, "Cannot change airplane mode: Dhizuku permission is not granted")
                return false
            }

            val argument = if (enabled) "enable" else "disable"
            val process = Dhizuku.newProcess(
                arrayOf("/system/bin/cmd", "connectivity", "airplane-mode", argument),
                null,
                java.io.File("/")
            )
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val error = process.errorStream.bufferedReader().use { it.readText() }.trim()
            Log.d(TAG, "airplane-mode $argument: exit=$exitCode out='$output' err='$error'")

            if (exitCode != 0) return false

            // ConnectivityService updates the global setting asynchronously.
            repeat(10) {
                if (isAirplaneModeOn(context) == enabled) return true
                Thread.sleep(100)
            }
            Log.w(TAG, "ConnectivityService returned success but state is not updated")
            false
        } catch (t: Throwable) {
            Log.e(TAG, "Could not change airplane mode", t)
            false
        }
    }

    /** Immediate toggle used by the diagnostic QS tile. */
    fun setAirplaneModeImmediate(context: Context, enabled: Boolean): Boolean {
        return try {
            Dhizuku.init(context)
            Thread.sleep(300)
            if (!Dhizuku.isPermissionGranted()) {
                Log.w(TAG, "Cannot change airplane mode: Dhizuku permission is not granted")
                return false
            }

            val argument = if (enabled) "enable" else "disable"
            val process = Dhizuku.newProcess(
                arrayOf(
                    "/system/bin/sh", "-c",
                    "/system/bin/cmd connectivity airplane-mode $argument"
                ),
                arrayOf("PATH=/system/bin:/system/xbin:/vendor/bin"),
                java.io.File("/")
            )
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val error = process.errorStream.bufferedReader().use { it.readText() }.trim()
            Log.d(TAG, "immediate airplane-mode $argument: exit=$exitCode out='$output' err='$error'")
            exitCode == 0
        } catch (t: Throwable) {
            Log.e(TAG, "Could not immediately change airplane mode", t)
            false
        }
    }

    fun isAirplaneModeOn(context: Context): Boolean = try {
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) == 1
    } catch (t: Throwable) {
        Log.w(TAG, "Could not read airplane mode", t)
        false
    }
}
