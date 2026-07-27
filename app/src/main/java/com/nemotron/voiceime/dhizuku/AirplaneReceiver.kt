package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rosan.dhizuku.api.Dhizuku

class AirplaneReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val enable = intent.getBooleanExtra(AutoFreezeService.EXTRA_ENABLE, false)
        Log.d(TAG, "airplane toggle → enable=$enable")
        val pendingResult = goAsync()

        Thread {
            try {
                Dhizuku.init(ctx)
                Thread.sleep(300)
                val argument = if (enable) "enable" else "disable"
                val process = Dhizuku.newProcess(
                    arrayOf(
                        "/system/bin/sh", "-c",
                        "/system/bin/cmd connectivity airplane-mode $argument"
                    ),
                    arrayOf("PATH=/system/bin:/system/xbin:/vendor/bin"),
                    java.io.File("/")
                )
                val exit = process.waitFor()
                val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
                val err = process.errorStream.bufferedReader().use { it.readText() }.trim()
                Log.d(TAG, "cmd exit=$exit out='$out' err='$err'")
            } catch (t: Throwable) {
                Log.e(TAG, "cmd failed", t)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "AirplaneReceiver"
    }
}
