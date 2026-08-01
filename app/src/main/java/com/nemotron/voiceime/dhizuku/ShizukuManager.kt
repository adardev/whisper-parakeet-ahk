package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.lang.reflect.Method

/** Shell-backed operations supplied by a running Shizuku server. */
object ShizukuManager {

    private const val TAG = "ShizukuManager"
    private const val REQUEST_CODE = 4011

    private var newProcessMethod: Method? = null
    private var shellProc: Process? = null
    private var shellIn: PrintWriter? = null
    private var shellOut: BufferedReader? = null
    private var shellErr: BufferedReader? = null
    private val shellLock = Any()

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
            val proc = method.invoke(null, arrayOf("sh"), arrayOf("PATH=/system/bin:/system/xbin:/vendor/bin"), null) as Process
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
        synchronized(shellLock) {
            ensureShell()
            val input = shellIn ?: return false
            try {
                input.println(cmd)
                input.flush()
                drainOutput()
                return true
            } catch (t: Throwable) {
                Log.w(TAG, "exec failed: $cmd", t)
                closeShell()
                return false
            }
        }
    }

    fun execShellCapture(cmd: String): String? {
        synchronized(shellLock) {
            ensureShell()
            val input = shellIn ?: return null
            val out = shellOut ?: return null
            try {
                val marker = "__NEMO_${System.nanoTime()}__"
                input.println("$cmd; echo $marker")
                input.flush()
                val sb = StringBuilder()
                val deadline = System.currentTimeMillis() + 5000L
                while (System.currentTimeMillis() < deadline) {
                    if (!out.ready()) {
                        Thread.sleep(10)
                        continue
                    }
                    val line = out.readLine() ?: break
                    if (line.contains(marker)) break
                    sb.appendLine(line)
                }
                return sb.toString().trim()
            } catch (t: Throwable) {
                Log.w(TAG, "execCapture failed: $cmd", t)
                closeShell()
                return null
            }
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

    fun pasteText(context: Context, @Suppress("UNUSED_PARAMETER") text: String): Boolean {
        if (!hasPermission()) return false
        return try {
            exec("input keyevent 279")
        } catch (t: Throwable) {
            Log.w(TAG, "Shizuku pasteText failed", t)
            false
        }
    }

    fun hideApp(packageName: String): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("pm disable-user $packageName") ?: return false
        Log.d(TAG, "pm disable-user $packageName → $out")
        return out.contains("new state")
    }

    fun unhideApp(packageName: String): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("pm enable $packageName") ?: return false
        Log.d(TAG, "pm enable $packageName → $out")
        return out.contains("new state")
    }

    fun isAppHidden(packageName: String): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("pm list packages -d") ?: return false
        return out.contains(packageName)
    }

    /** Returns which of [packages] are still in disabled state (freeze persists). */
    fun stillDisabled(packages: Collection<String>): Set<String> {
        if (packages.isEmpty()) return emptySet()
        if (!hasPermission()) return packages.toSet()
        val out = execShellCapture("pm list packages -d") ?: return packages.toSet()
        val disabled = out.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .mapTo(mutableSetOf()) { it.removePrefix("package:") }
        return packages.filterTo(mutableSetOf()) { it in disabled }
    }

    fun stopApp(packageName: String): Boolean {
        if (!hasPermission()) return false
        return execShell("am force-stop $packageName")
    }

}
