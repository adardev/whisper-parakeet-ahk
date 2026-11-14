package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.util.Log
import com.nemotron.voiceime.R
import java.io.File
import java.security.MessageDigest

/** Applies the version-checked Android Auto APK only in app mount namespaces. */
object AndroidAutoRailPatch {
    private const val TAG = "AndroidAutoRailPatch"
    private const val CANDIDATE_NAME = "nemotron-aa-18_1-rail1dp.apk"
    private const val CANDIDATE_SHA256 = "6e14eb65f4df0293352ed5da478ada2445341b776956746693e75da205d17cd8"
    private const val CANDIDATE_LENGTH = 35_538_663L
    private const val DEVICE_CANDIDATE = "/data/local/tmp/$CANDIDATE_NAME"
    private val lock = Any()

    fun enable(context: Context): Boolean = run(context, "enable")
    fun disable(context: Context): Boolean = run(context, "disable")

    private fun run(context: Context, action: String): Boolean = synchronized(lock) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        if (!RootShellManager.hasPermission()) return@synchronized false
        try {
            if (action == "enable" && !stageCandidate(context)) return@synchronized false
            val file = File(context.filesDir, "android_auto_rail_control.sh")
            context.resources.openRawResource(R.raw.android_auto_rail_control).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val output = RootShellManager.execShellFresh(
                arrayOf("sh", file.absolutePath, action), timeoutMs = 20_000L
            )
            Log.i(TAG, "$action: ${output ?: "failed"}")
            Log.i(TAG, "$action completed in ${android.os.SystemClock.elapsedRealtime() - startedAt}ms")
            output != null
        } catch (error: Exception) {
            Log.e(TAG, "$action failed", error)
            false
        }
    }

    /** Restore the verified APK after Android clears /data/local/tmp. */
    private fun stageCandidate(context: Context): Boolean {
        val cached = File(context.filesDir, CANDIDATE_NAME)
        if (!cached.isFile || cached.length() != CANDIDATE_LENGTH) {
            val temporary = File(context.filesDir, "$CANDIDATE_NAME.tmp")
            try {
                context.resources.openRawResource(R.raw.android_auto_rail_candidate).use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                if (temporary.length() != CANDIDATE_LENGTH || sha256(temporary) != CANDIDATE_SHA256) {
                    temporary.delete()
                    Log.e(TAG, "Bundled Android Auto candidate has an unexpected SHA-256")
                    return false
                }
                cached.delete()
                if (!temporary.renameTo(cached)) {
                    temporary.delete()
                    Log.e(TAG, "Could not cache the Android Auto candidate")
                    return false
                }
            } catch (error: Exception) {
                temporary.delete()
                Log.e(TAG, "Could not extract the Android Auto candidate", error)
                return false
            }
        }
        val copied = RootShellManager.execShellFresh(
            arrayOf("cp", "-f", cached.absolutePath, DEVICE_CANDIDATE), timeoutMs = 30_000L
        ) != null
        if (!copied) Log.e(TAG, "Could not stage the Android Auto candidate on device")
        return copied
    }

    private fun sha256(file: File): String? {
        if (!file.isFile) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } catch (error: Exception) {
            Log.e(TAG, "Could not hash Android Auto candidate", error)
            null
        }
    }
}
