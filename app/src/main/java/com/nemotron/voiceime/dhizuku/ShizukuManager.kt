package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.lang.reflect.Method

/** Shell-backed operations supplied by a running Shizuku server. */
object ShizukuManager {

    private const val TAG = "ShizukuManager"
    private const val REQUEST_CODE = 4011
    private const val CONNECTIVITY_DESCRIPTOR = "android.net.IConnectivityManager"
    private const val TRANSACTION_SET_AIRPLANE_MODE = 36

    private var newProcessMethod: Method? = null
    private var shellProc: Process? = null
    private var shellIn: PrintWriter? = null
    private var shellOut: BufferedReader? = null
    private var shellErr: BufferedReader? = null

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

    private fun getNewProcessMethod(): Method? {
        if (newProcessMethod == null) {
            newProcessMethod = try {
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).also { it.isAccessible = true }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to get newProcess method", t)
                null
            }
        }
        return newProcessMethod
    }

    private fun ensureShell() {
        if (shellProc != null && shellIn != null) return
        val method = getNewProcessMethod() ?: return
        try {
            val proc = method.invoke(null, arrayOf("sh"), null, null) as Process
            shellProc = proc
            shellIn = PrintWriter(proc.outputStream, true)
            shellOut = BufferedReader(InputStreamReader(proc.inputStream))
            shellErr = BufferedReader(InputStreamReader(proc.errorStream))
            Log.d(TAG, "persistent shell started")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start persistent shell", t)
            shellProc = null
            shellIn = null
        }
    }

    fun execShell(cmd: String): Boolean {
        ensureShell()
        val input = shellIn ?: return false
        try {
            input.println(cmd)
            input.flush()
            Thread.sleep(100)
            drainOutput()
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "exec failed: $cmd", t)
            closeShell()
            return false
        }
    }

    fun execShellCapture(cmd: String): String? {
        ensureShell()
        val input = shellIn ?: return null
        val out = shellOut ?: return null
        try {
            input.println(cmd)
            input.flush()
            Thread.sleep(300)
            val sb = StringBuilder()
            while (out.ready()) {
                sb.appendLine(out.readLine())
            }
            return sb.toString().trim()
        } catch (t: Throwable) {
            Log.w(TAG, "execCapture failed: $cmd", t)
            closeShell()
            return null
        }
    }

    private fun drainOutput() {
        try {
            val o = shellOut ?: return
            while (o.ready()) o.readLine()
        } catch (_: Throwable) {}
    }

    private fun exec(cmd: String): Boolean = execShell(cmd)

    private fun closeShell() {
        try { shellProc?.destroy() } catch (_: Throwable) {}
        shellProc = null
        shellIn = null
        shellOut = null
        shellErr = null
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

    fun pasteText(context: Context, text: String): Boolean {
        if (!hasPermission()) return false
        val escaped = text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace(" ", "%s")
            .replace("&", "\\&")
            .replace(";", "\\;")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace("|", "\\|")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("$", "\\$")
            .replace("`", "\\`")
            .replace("!", "\\!")
            .replace("#", "\\#")
        return try {
            exec("input text '$escaped'")
        } catch (t: Throwable) {
            Log.w(TAG, "Shizuku pasteText failed", t)
            false
        }
    }

}
