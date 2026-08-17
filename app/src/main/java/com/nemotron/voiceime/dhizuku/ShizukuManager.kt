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
        // Un shell persistente cuya vida depende del binder de Shizuku: los
        // procesos creados con newProcess mueren con Shizuku (p.ej. One UI 8 lo
        // mata al bloquear la pantalla, o al conectar el teléfono al coche).
        //
        // NOTA: NO se usa Process.isAlive() aquí. ShizukuRemoteProcess.isAlive()
        // llama a exitValue() que lanza IllegalArgumentException("process
        // hasn't exited") para un proceso VIVO, así que isAlive() nunca funciona
        // y rompería todos los comandos. La detección de un proceso muerto se
        // hace con input.checkError() tras escribir (EPIPE) y con onBinderDead().
        if (shellProc != null && shellIn != null) return
        closeShell()
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
                if (input.checkError()) {
                    Log.w(TAG, "shell pipe broken: $cmd")
                    closeShell()
                    return false
                }
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
                if (input.checkError()) {
                    Log.w(TAG, "capture pipe broken: $cmd")
                    closeShell()
                    return null
                }
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

    /** El binder de Shizuku ha muerto: descarta el shell persistente para
     *  no reutilizar un proceso sin vida y que se vuelva a crear limpio. */
    fun onBinderDead() {
        synchronized(shellLock) {
            closeShell()
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

    /** Apaga y bloquea la pantalla (equivale a pulsar el botón de encendido). */
    fun lockScreen(): Boolean {
        if (!hasPermission()) return false
        return execShell("input keyevent 26")
    }

    /** Desactiva el modo No Molestar (vuelve al filtro "todos"). */
    fun disableDnd(): Boolean {
        if (!hasPermission()) return false
        return execShell("cmd notification set_dnd off")
    }

    /** Verifica si el NFC está encendido. */
    fun isNfcEnabled(): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("dumpsys nfc | grep mState") ?: return false
        return out.contains("mState=on")
    }

    /** Enciende/apaga el NFC. */
    fun setNfcEnabled(enabled: Boolean): Boolean {
        if (!hasPermission()) return false
        return execShell(if (enabled) "cmd nfc enable-nfc" else "cmd nfc disable-nfc")
    }

    /** Verifica si la app tiene un proceso corriendo. */
    fun isProcessRunning(packageName: String): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("pidof $packageName") ?: return false
        return out.isNotBlank()
    }

    private const val ANDROID_AUTO_PKG = "com.google.android.projection.gearhead"

    /** True si Android Auto está activo (proyección al coche). Los procesos de
     *  Android Auto llevan sufijo (:car, :shared, ...) y el proceso base no
     *  siempre existe, por eso se listan las variantes más comunes. */
    fun isAndroidAutoActive(): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture(
            "pidof $ANDROID_AUTO_PKG " +
                "$ANDROID_AUTO_PKG:car $ANDROID_AUTO_PKG:shared " +
                "$ANDROID_AUTO_PKG:watchdog $ANDROID_AUTO_PKG:provider"
        ) ?: return false
        return out.isNotBlank()
    }

    /** Lanza la app como si fuera del home screen. */
    fun launchApp(packageName: String): Boolean {
        if (!hasPermission()) return false
        return execShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    /** Lanza una actividad concreta de la app. */
    fun launchApp(packageName: String, activity: String): Boolean {
        if (!hasPermission()) return false
        return execShell("am start -n $packageName/$activity")
    }

    /** Exime a [packageName] de doze para que Samsung no congele su proceso. */
    fun exemptFromDoze(packageName: String): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("dumpsys deviceidle whitelist +$packageName")
        execShell("am set-standby-bucket $packageName active")
        Log.d(TAG, "doze whitelist $packageName → $out")
        return out?.contains("Added") == true
    }

    /** Fuerza doze profundo de inmediato (con pantalla apagada). */
    fun forceIdle(): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("cmd deviceidle force-idle") ?: return false
        Log.d(TAG, "force-idle → $out")
        return out.contains("forced")
    }

    /** Revierte el doze forzado. */
    fun unforceIdle(): Boolean {
        if (!hasPermission()) return false
        val out = execShellCapture("cmd deviceidle unforce") ?: return false
        Log.d(TAG, "unforce-idle → $out")
        return true
    }

}
