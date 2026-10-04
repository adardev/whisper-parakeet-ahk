package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import java.util.Calendar

/** Reintenta el modo avión al bloquear la pantalla durante el horario nocturno. */
class NightAirplaneScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON) {
            preferences(context).edit().putBoolean(KEY_PENDING, false).apply()
            return
        }
        if (intent.action != Intent.ACTION_SCREEN_OFF || !isNightWindow()) return

        preferences(context).edit().putBoolean(KEY_PENDING, true).apply()
        val pendingResult = goAsync()
        Thread {
            try { applyPending(context, screenMustRemainOff = false) }
            finally { pendingResult.finish() }
        }.start()
    }

    /** Reintenta solo cuando Shizuku vuelve, sin crear alarmas ni bucles de sondeo. */
    fun onShizukuAvailable(context: Context) {
        if (!preferences(context).getBoolean(KEY_PENDING, false)) return
        Thread { applyPending(context, screenMustRemainOff = true) }.start()
    }

    private fun applyPending(context: Context, screenMustRemainOff: Boolean) {
        val prefs = preferences(context)
        if (!prefs.getBoolean(KEY_PENDING, false)) return
        if (!isNightWindow()) {
            prefs.edit().putBoolean(KEY_PENDING, false).apply()
            return
        }
        if (screenMustRemainOff) {
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (power?.isInteractive != false) {
                prefs.edit().putBoolean(KEY_PENDING, false).apply()
                return
            }
        }

        when (ShizukuManager.isAirplaneModeOn()) {
            null -> Log.w(TAG, "bloqueo nocturno pendiente: Shizuku aún no permite consultar el modo avión")
            true -> {
                prefs.edit().putBoolean(KEY_PENDING, false).apply()
                Log.d(TAG, "bloqueo nocturno: modo avión ya activo")
            }
            false -> if (ShizukuManager.setAirplaneMode(true)) {
                prefs.edit().putBoolean(KEY_PENDING, false).apply()
                Log.i(TAG, "bloqueo nocturno: modo avión activado")
            } else {
                Log.w(TAG, "bloqueo nocturno sigue pendiente; no se pudo activar")
            }
        }
    }

    private fun isNightWindow(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 8
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val TAG = "AirplaneScreenOff"
        const val PREFS = "airplane_screen_off"
        const val KEY_PENDING = "pending_night_activation"
    }
}
