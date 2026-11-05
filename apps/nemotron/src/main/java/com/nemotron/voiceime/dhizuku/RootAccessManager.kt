package com.nemotron.voiceime.dhizuku

import android.util.Log

/** KernelSU/Magisk-style su access. The SU manager remains responsible for consent. */
object RootAccessManager {
    private const val TAG = "NemotronRoot"

    data class Result(val success: Boolean, val detail: String)

    /** Runs a deliberately harmless identity check; first use may show the SU manager prompt. */
    fun requestAccess(timeoutMs: Long = 20_000L): Result {
        val result = runRoot(arrayOf("id"), timeoutMs)
        return result.copy(success = result.success && Regex("(?:^|\\s)uid=0(?:\\(|\\s|$)").containsMatchIn(result.detail))
    }

    /** Execute argv as root with each argument safely single-quoted for su -c. */
    fun runRoot(args: Array<String>, timeoutMs: Long = 10_000L): Result {
        if (args.isEmpty()) return Result(false, "No command supplied")
        return try {
            val command = "exec " + args.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
            val process = ProcessBuilder("/system/bin/su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result(false, "Root command timed out")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val ok = process.exitValue() == 0
            Result(ok, output.ifBlank { "su exit ${process.exitValue()}" })
        } catch (t: Throwable) {
            Log.w(TAG, "Root request failed", t)
            Result(false, t.message ?: t.javaClass.simpleName)
        }
    }
}
