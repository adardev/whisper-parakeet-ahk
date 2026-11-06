package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.util.Log
import com.nemotron.voiceime.R
import java.io.File

/** Applies the version-checked Android Auto APK only in app mount namespaces. */
object AndroidAutoRailPatch {
    private const val TAG = "AndroidAutoRailPatch"
    private val lock = Any()

    fun enable(context: Context): Boolean = run(context, "enable")
    fun disable(context: Context): Boolean = run(context, "disable")

    private fun run(context: Context, action: String): Boolean = synchronized(lock) {
        if (!RootShellManager.hasPermission()) return@synchronized false
        try {
            val file = File(context.filesDir, "android_auto_rail_control.sh")
            context.resources.openRawResource(R.raw.android_auto_rail_control).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val output = RootShellManager.execShellFresh(
                arrayOf("sh", file.absolutePath, action), timeoutMs = 20_000L
            )
            Log.i(TAG, "$action: ${output ?: "failed"}")
            output != null
        } catch (error: Exception) {
            Log.e(TAG, "$action failed", error)
            false
        }
    }
}
