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
        if (intent.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
            val airplaneModeOn = intent.getBooleanExtra("state", true)
            if (!airplaneModeOn && isMorningWakeWindow() && isScreenInteractive(context)) {
                preferences(context).edit()
                    .putInt(KEY_AWAKE_DAY, todayKey())
                    .putBoolean(KEY_PENDING, false)
                    .apply()
                Log.i(TAG, "despertar matutino detectado; no se reactivará el modo avión al bloquear")
            }
            return
        }
        if (intent.action == Intent.ACTION_SCREEN_ON) {
            preferences(context).edit().putBoolean(KEY_PENDING, false).apply()
            return
        }
        if (intent.action != Intent.ACTION_SCREEN_OFF || !isNightWindow()) return
        if (hasWokenUpToday(context)) {
            preferences(context).edit().putBoolean(KEY_PENDING, false).apply()
            Log.d(TAG, "despertar matutino ya detectado; se respeta el modo avión apagado")
            return
        }

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
        if (hasWokenUpToday(context)) {
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

    /** Desde las 05:00, apagar manualmente el modo avión con la pantalla activa cuenta como despertar. */
    private fun isMorningWakeWindow(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in MORNING_WAKE_HOUR until 8
    }

    private fun hasWokenUpToday(context: Context): Boolean =
        isMorningWakeWindow() &&
            preferences(context).getInt(KEY_AWAKE_DAY, -1) == todayKey()

    private fun todayKey(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.YEAR) * 400 + calendar.get(Calendar.DAY_OF_YEAR)
    }

    private fun isScreenInteractive(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val TAG = "AirplaneScreenOff"
        const val PREFS = "airplane_screen_off"
        const val KEY_PENDING = "pending_night_activation"
        const val KEY_AWAKE_DAY = "awake_day"
        const val MORNING_WAKE_HOUR = 5
    }
}
