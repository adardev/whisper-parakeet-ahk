package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import java.util.Calendar
import java.util.concurrent.Executors

/** Reintenta el modo avión al bloquear la pantalla durante el horario nocturno. */
class NightAirplaneScreenReceiver : BroadcastReceiver() {
    fun clearPendingWork(context: Context) {
        preferences(context).edit()
            .putBoolean(KEY_PENDING, false)
            .putBoolean(KEY_TEMPORARY_FIT3, false)
            .apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
            val airplaneModeOn = intent.getBooleanExtra("state", true)
            if (SleepModeController.consumeExpectedAirplaneChange(context, airplaneModeOn)) return
            if (SleepModeController.isManualDisableSuppressed(context)) return
            if (!airplaneModeOn && isWakeWindow() && isScreenInteractive(context)) {
                val prefs = preferences(context)
                val firstWakeDetection = prefs.getInt(KEY_AWAKE_DAY, -1) != todayKey()
                prefs.edit()
                    .putInt(KEY_AWAKE_DAY, todayKey())
                    .putBoolean(KEY_PENDING, false)
                    .putBoolean(KEY_TEMPORARY_FIT3, false)
                    .apply()
                // Once the user wakes, screen-off events must not re-arm the
                // night routine if its 08:00 alarm or Fit3 handoff was missed.
                SleepModeController.disableUntilNight(context)
                if (firstWakeDetection) {
                    Log.i(TAG, "despertar diurno detectado; restaurando ajustes diurnos de Fit3")
                    val pendingResult = goAsync()
                    fit3Actions.execute {
                        try {
                            val result = NightRoutineActions.run(context.applicationContext, night = false)
                            if (!result.success) {
                                Log.w(TAG, "no se pudieron restaurar los ajustes diurnos de Fit3: ${result.message}")
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                } else {
                    Log.d(TAG, "despertar ya registrado hoy; no se vuelve a contactar la Fit3")
                }
            } else if (!airplaneModeOn && isNightWindow() && isScreenInteractive(context)) {
                // Outside the morning wake window this is only a pause until the next lock.
                if (isNightWindow()) SleepModeController.markScheduledState(context, true)
                preferences(context).edit()
                    .putBoolean(KEY_TEMPORARY_FIT3, true)
                    .putBoolean(KEY_PENDING, false)
                    .apply()
                val pendingResult = goAsync()
                fit3Actions.execute {
                    try {
                        // A quick lock may have arrived before this queued action starts.
                        if (isNightWindow() && isScreenInteractive(context)) {
                            // An older queued lock may have just cleared the flag.
                            preferences(context).edit().putBoolean(KEY_TEMPORARY_FIT3, true).apply()
                            val result = NightRoutineActions.run(context.applicationContext, night = false)
                            if (!result.success) Log.w(TAG, "pausa temporal de Fit3 no confirmada: ${result.message}")
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            return
        }
        if (intent.action == Intent.ACTION_SCREEN_ON) {
            preferences(context).edit().putBoolean(KEY_PENDING, false).apply()
            return
        }
        if (intent.action != Intent.ACTION_SCREEN_OFF) return
        if (SleepModeController.isManualDisableSuppressed(context)) {
            preferences(context).edit().putBoolean(KEY_PENDING, false).apply()
            Log.i(TAG, "pantalla apagada: se respeta la desactivación manual de Sleep Mode hasta las 23:00")
            return
        }
        // Screen-off is only an activation trigger during the actual night
        // window. A stale enabled flag after a missed/failed 08:00 routine must
        // never turn airplane mode back on during the day.
        if (!isNightWindow()) return
        if (hasWokenUpToday(context)) {
            preferences(context).edit().putBoolean(KEY_PENDING, false).apply()
            syncRegistration(context)
            Log.d(TAG, "despertar matutino ya detectado; se respeta el modo avión apagado")
            return
        }

        preferences(context).edit().putBoolean(KEY_PENDING, true).apply()
        val pendingResult = goAsync()
        fit3Actions.execute {
            try { applyPending(context, screenMustRemainOff = false) }
            finally { pendingResult.finish() }
        }
    }

    /** Reintenta solo cuando Shizuku vuelve, sin crear alarmas ni bucles de sondeo. */
    fun onShizukuAvailable(context: Context) {
        if (!preferences(context).getBoolean(KEY_PENDING, false)) return
        fit3Actions.execute { applyPending(context, screenMustRemainOff = true) }
    }

    private fun applyPending(context: Context, screenMustRemainOff: Boolean) {
        val prefs = preferences(context)
        if (!prefs.getBoolean(KEY_PENDING, false)) return
        if (SleepModeController.isManualDisableSuppressed(context) || !(isNightWindow() || SleepModeController.isEnabled(context))) {
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

        // Send watch settings before enabling airplane mode. Also restore them if airplane
        // mode was already re-enabled manually; its state alone does not confirm Fit3 state.
        var fit3NightConfirmed = true
        if (prefs.getBoolean(KEY_TEMPORARY_FIT3, false)) {
            val result = NightRoutineActions.run(context.applicationContext, night = true)
            fit3NightConfirmed = result.success
            if (result.success && prefs.getBoolean(KEY_PENDING, false)) {
                prefs.edit().putBoolean(KEY_TEMPORARY_FIT3, false).apply()
            } else if (!result.success) {
                Log.w(TAG, "reactivación nocturna de Fit3 pendiente: ${result.message}")
            }
        }
        // Screen-on or another manual pause may have superseded this lock while BLE ran.
        if (!prefs.getBoolean(KEY_PENDING, false)) return

        when (ShizukuManager.isAirplaneModeOn()) {
            null -> Log.w(TAG, "bloqueo nocturno pendiente: Shizuku aún no permite consultar el modo avión")
            true -> {
                if (fit3NightConfirmed) prefs.edit().putBoolean(KEY_PENDING, false).apply()
                if (fit3NightConfirmed) SleepModeController.markScheduledState(context, true)
                Log.d(TAG, "bloqueo nocturno: modo avión ya activo")
            }
            false -> if (ShizukuManager.setAirplaneMode(true)) {
                if (fit3NightConfirmed) prefs.edit().putBoolean(KEY_PENDING, false).apply()
                if (fit3NightConfirmed) SleepModeController.markScheduledState(context, true)
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

    /** Desde las 05:00 hasta la siguiente noche, apagar el modo avión cuenta como despertar. */
    private fun isWakeWindow(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in MORNING_WAKE_HOUR until 23
    }

    private fun hasWokenUpToday(context: Context): Boolean =
        isWakeWindow() && preferences(context).getInt(KEY_AWAKE_DAY, -1) == todayKey()

    private fun todayKey(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.YEAR) * 400 + calendar.get(Calendar.DAY_OF_YEAR)
    }

    private fun isScreenInteractive(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        const val TAG = "AirplaneScreenOff"
        const val PREFS = "airplane_screen_off"
        const val KEY_PENDING = "pending_night_activation"
        const val KEY_AWAKE_DAY = "awake_day"
        const val KEY_TEMPORARY_FIT3 = "temporary_fit3_day_settings"
        const val MORNING_WAKE_HOUR = 5
        private val registrationLock = Any()
        private var registeredReceiver: NightAirplaneScreenReceiver? = null

        /** Avoid waking the launcher/IME for screen broadcasts outside Sleep Mode. */
        fun syncRegistration(context: Context) {
            val app = context.applicationContext
            val shouldRegister = SleepModeController.shouldMonitorScreenEvents(app)
            synchronized(registrationLock) {
                val current = registeredReceiver
                if (shouldRegister && current == null) {
                    val receiver = NightAirplaneScreenReceiver()
                    val filter = android.content.IntentFilter().apply {
                        addAction(Intent.ACTION_SCREEN_OFF)
                        addAction(Intent.ACTION_SCREEN_ON)
                        addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                    }
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                        } else {
                            @Suppress("DEPRECATION")
                            app.registerReceiver(receiver, filter)
                        }
                        registeredReceiver = receiver
                        Log.i(TAG, "screen receiver registrado mientras Sleep Mode lo requiere")
                    } catch (error: Throwable) {
                        Log.w(TAG, "no se pudo registrar el receptor de pantalla", error)
                    }
                } else if (!shouldRegister && current != null) {
                    try {
                        app.unregisterReceiver(current)
                    } catch (error: IllegalArgumentException) {
                        Log.w(TAG, "el receptor de pantalla ya estaba desconectado", error)
                    } finally {
                        registeredReceiver = null
                        Log.i(TAG, "screen receiver retirado; Sleep Mode no necesita observar la pantalla")
                    }
                }
                Unit
            }
        }

        // Keep temporary restore and the following lock action in broadcast order.
        val fit3Actions = Executors.newSingleThreadExecutor { task -> Thread(task, "NightFit3Actions") }
    }
}
