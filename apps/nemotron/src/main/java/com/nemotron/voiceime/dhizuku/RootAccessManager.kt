package com.nemotron.voiceime.dhizuku

import android.util.Log
import android.content.Context
import java.io.ByteArrayOutputStream

/** KernelSU/Magisk-style su access. The SU manager remains responsible for consent. */
object RootAccessManager {
    private const val TAG = "NemotronRoot"
    private const val PREFS = "nemotron_root_access"
    private const val KEY_GRANTED = "kernelsu_granted"
    @Volatile private var appContext: Context? = null
    @Volatile private var cachedRootAccess = false

    data class Result(val success: Boolean, val detail: String)

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        cachedRootAccess = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GRANTED, false)
    }

    private fun cacheResult(granted: Boolean) {
        cachedRootAccess = granted
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_GRANTED, granted)?.apply()
    }

    /** Runs a deliberately harmless identity check; first use may show the SU manager prompt. */
    fun requestAccess(timeoutMs: Long = 20_000L): Result {
        val result = runRoot(arrayOf("id"), timeoutMs)
        val granted = result.success && Regex("(?:^|\\s)uid=0(?:\\(|\\s|$)").containsMatchIn(result.detail)
        cacheResult(granted)
        return result.copy(success = granted)
    }

    /** Cached authorization state; never starts a slow SU request from a tile callback. */
    fun hasRootAccess(): Boolean = cachedRootAccess

    /** Execute argv as root with each argument safely single-quoted for su -c. */
    fun runRoot(args: Array<String>, timeoutMs: Long = 10_000L): Result {
        if (args.isEmpty()) return Result(false, "No command supplied")
        return try {
            val command = "exec " + args.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
            val process = ProcessBuilder("/system/bin/su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val outputBytes = ByteArrayOutputStream()
            val reader = Thread({ process.inputStream.use { it.copyTo(outputBytes) } }, "Nemotron-root-output")
            reader.isDaemon = true
            reader.start()
            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                reader.join(500L)
                return Result(false, "Root command timed out")
            }
            reader.join(1_000L)
            val output = outputBytes.toString(Charsets.UTF_8.name()).trim()
            val ok = process.exitValue() == 0
            Result(ok, output.ifBlank { "su exit ${process.exitValue()}" })
        } catch (t: Throwable) {
            Log.w(TAG, "Root request failed", t)
            Result(false, t.message ?: t.javaClass.simpleName)
        }
    }

    /** Open a persistent root shell for multi-command file and state operations. */
    fun openRootShell(): Process = ProcessBuilder("/system/bin/su", "-c", "sh").start()
}
