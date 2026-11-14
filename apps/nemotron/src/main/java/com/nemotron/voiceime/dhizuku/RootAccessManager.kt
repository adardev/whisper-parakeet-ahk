package com.nemotron.voiceime.dhizuku

import android.os.SystemClock
import android.util.Log
import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/** KernelSU/Magisk-style su access. The SU manager remains responsible for consent. */
object RootAccessManager {
    private const val TAG = "NemotronRoot"
    private const val PREFS = "nemotron_root_access"
    private const val KEY_GRANTED = "kernelsu_granted"
    private const val IME_STATE_PREFIX = "hidden_ime_state_"
    @Volatile private var appContext: Context? = null
    @Volatile private var cachedRootAccess = false

    data class Result(val success: Boolean, val detail: String)
    data class HiddenImeState(
        val enabledComponents: Set<String>,
        val defaultBeforeHide: String,
        val defaultAfterHide: String
    )

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

    fun saveHiddenImeState(packageName: String, state: HiddenImeState) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putStringSet(IME_STATE_PREFIX + packageName, state.enabledComponents)
            ?.putString(IME_STATE_PREFIX + packageName + "_default_before", state.defaultBeforeHide)
            ?.putString(IME_STATE_PREFIX + packageName + "_default_after", state.defaultAfterHide)
            ?.apply()
    }

    fun getHiddenImeState(packageName: String): HiddenImeState? {
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return null
        val key = IME_STATE_PREFIX + packageName
        val components = prefs.getStringSet(key, null)?.toSet()?.takeIf { it.isNotEmpty() } ?: return null
        return HiddenImeState(
            components,
            prefs.getString(key + "_default_before", "").orEmpty(),
            prefs.getString(key + "_default_after", "").orEmpty()
        )
    }

    fun clearHiddenImeState(packageName: String) {
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        val key = IME_STATE_PREFIX + packageName
        prefs.edit().remove(key).remove(key + "_default_before").remove(key + "_default_after").apply()
    }

    /** Execute argv as root with each argument safely single-quoted for su -c. */
    fun runRoot(args: Array<String>, timeoutMs: Long = 10_000L): Result {
        if (args.isEmpty()) return Result(false, "No command supplied")
        val startedAt = android.os.SystemClock.elapsedRealtime()
        return try {
            val command = "exec " + args.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
            val process = ProcessBuilder("/system/bin/su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val outputBytes = ByteArrayOutputStream()
            val readFailure = AtomicReference<IOException?>(null)
            val reader = Thread({
                try {
                    process.inputStream.use { it.copyTo(outputBytes) }
                } catch (error: IOException) {
                    // Killing/timing out su can close this pipe while copyTo
                    // is reading. Some KernelSU builds also close the Java
                    // stream as soon as a successfully exited su process is
                    // reaped; the caller validates its own output markers.
                    readFailure.set(error)
                }
            }, "Nemotron-root-output")
            reader.isDaemon = true
            reader.start()
            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                reader.join(500L)
                Log.w(TAG, "Root command timed out after ${SystemClock.elapsedRealtime() - startedAt} ms")
                return Result(false, "Root command timed out")
            }
            reader.join(1_000L)
            val output = outputBytes.toString(Charsets.UTF_8.name()).trim()
            val failure = readFailure.get()
            val exitCode = process.exitValue()
            // Exit status is authoritative. A stream-close IOException after a
            // normal exit is harmless when the reader has drained and exited;
            // higher-level commands still validate their completion markers.
            val ok = exitCode == 0 && !reader.isAlive
            if (!ok) {
                Log.w(TAG, "Root command failed: exit=$exitCode readerAlive=${reader.isAlive} readError=${failure?.message} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms output=${output.take(240)}")
            } else {
                Log.d(TAG, "Root command completed in ${SystemClock.elapsedRealtime() - startedAt}ms (output=${output.length} chars)")
            }
            Result(ok, output.ifBlank {
                when {
                    failure != null -> "Root output read failed: ${failure.message}"
                    reader.isAlive -> "Root output reader did not finish"
                    else -> "su exit $exitCode"
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Root request failed", t)
            Result(false, t.message ?: t.javaClass.simpleName)
        }
    }

    /** Open a persistent root shell for multi-command file and state operations. */
    fun openRootShell(): Process = ProcessBuilder("/system/bin/su", "-c", "sh").start()
}
