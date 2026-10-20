package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.content.ComponentName
import android.service.quicksettings.TileService
import android.util.Log

/** Manually runs Nemotron's existing night/morning routines ahead of their schedule. */
object SleepModeController {
    private const val PREFS = "sleep_mode"
    private const val AIRPLANE_PREFS = "airplane_screen_off"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MANUAL_DISABLE_UNTIL_NIGHT = "manual_disable_until_night"
    private const val KEY_EXPECTED_AIRPLANE = "expected_airplane_state"
    private const val KEY_EXPECTED_AIRPLANE_AT = "expected_airplane_state_at"
    private const val TAG = "SleepMode"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    /** A manual morning stop must survive screen-off retries until the next 23:00 schedule. */
    fun isManualDisableSuppressed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, false)

    /** Whether global screen broadcasts are currently needed for Sleep Mode. */
    fun shouldMonitorScreenEvents(context: Context): Boolean {
        if (isManualDisableSuppressed(context)) return false
        if (isEnabled(context)) return true
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour >= 23) return true
        if (hour >= 8) return false
        val today = todayKey()
        val recordedWakeDay = context.getSharedPreferences(AIRPLANE_PREFS, Context.MODE_PRIVATE)
            .getInt("awake_day", -1)
        return recordedWakeDay != today
    }

    @Synchronized
    fun toggle(context: Context): Result = setEnabled(context, !isEnabled(context))

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean): Result = applyEnabled(context, enabled)

    /** Applies the same persisted state transition at the scheduled boundary. */
    @Synchronized
    fun setScheduledEnabled(context: Context, enabled: Boolean): Result {
        // A delayed/duplicated 23:00 alarm must not undo a daytime wake or
        // manual OFF choice. The regular next-night alarm is allowed at 23:00.
        if (enabled && hasWokenToday(context) && java.util.Calendar.getInstance()
                .get(java.util.Calendar.HOUR_OF_DAY) < 23) {
            disableUntilNight(context)
            Log.i(TAG, "se ignoró una activación programada tardía después de despertar")
            return Result(false, false, "Sleep Mode sigue apagado hasta las 23:00")
        }
        return applyEnabled(context, enabled)
    }

    private fun applyEnabled(context: Context, enabled: Boolean): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldSuppression = prefs.getBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, false)
        if (!enabled) {
            // A daytime/manual OFF is authoritative even if Shizuku, Bluetooth,
            // or the Fit3 routine fails. Never let the next screen-off undo it.
            disableUntilNight(context)
        }
        if (!ShizukuManager.hasPermission()) return Result(false, isEnabled(context), "Shizuku no está disponible")

        if (enabled) {
            // A new night starts a fresh suppression window. Restore the prior
            // daytime state if the requested night routine cannot be applied.
            prefs.edit().putBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, false).commit()
        }

        // Match the scheduled routine's ordering: airplane mode goes off before
        // the morning watch handoff, and on only after the night handoff succeeds.
        if (!enabled && !setAirplaneMode(context, false)) {
            consumeExpectedAirplaneChange(context, false)
            return Result(false, false, "Sleep Mode quedó apagado, pero no se pudo desactivar el modo avión")
        }

        val routine = NightRoutineActions.run(context.applicationContext, night = enabled)
        if (!routine.success) {
            if (enabled) prefs.edit().putBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, oldSuppression).commit()
            return Result(false, isEnabled(context), routine.message)
        }

        if (enabled && !setAirplaneMode(context, true)) {
            prefs.edit().putBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, oldSuppression).commit()
            return Result(false, isEnabled(context), "Fit3 quedó en modo noche, pero no se pudo activar el modo avión")
        }

        prefs.edit().putBoolean(KEY_ENABLED, enabled).commit()
        if (enabled) {
            NightAirplaneScreenReceiver().clearPendingWork(context)
            NightAirplaneScreenReceiver.syncRegistration(context)
        }
        refreshQuickSettingsTile(context)
        Log.i(TAG, "Sleep Mode ${if (enabled) "activado" else "desactivado"}")
        return Result(true, enabled, if (enabled) "Modo noche activado" else "Rutina de mañana aplicada")
    }

    /** Leaves the daytime choice in force until the next scheduled 23:00 activation. */
    fun disableUntilNight(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, true)
            .commit()
        NightAirplaneScreenReceiver().clearPendingWork(context)
        NightAirplaneScreenReceiver.syncRegistration(context)
        refreshQuickSettingsTile(context)
    }

    /** Prevent the automatic screen receiver from treating our own airplane toggle as user input. */
    fun consumeExpectedAirplaneChange(context: Context, enabled: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expected = prefs.getInt(KEY_EXPECTED_AIRPLANE, -1)
        val expectedAt = prefs.getLong(KEY_EXPECTED_AIRPLANE_AT, 0L)
        prefs.edit().remove(KEY_EXPECTED_AIRPLANE).remove(KEY_EXPECTED_AIRPLANE_AT).apply()
        return expected == (if (enabled) 1 else 0) && System.currentTimeMillis() - expectedAt <= 15_000L
    }

    fun markScheduledState(context: Context, enabled: Boolean) {
        if (enabled && hasWokenToday(context) && java.util.Calendar.getInstance()
                .get(java.util.Calendar.HOUR_OF_DAY) < 23) {
            disableUntilNight(context)
            Log.i(TAG, "se ignoró estado nocturno tardío después del despertar")
            return
        }
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
        if (enabled) edit.putBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, false)
        edit.commit()
        NightAirplaneScreenReceiver.syncRegistration(context)
        refreshQuickSettingsTile(context)
    }

    private fun refreshQuickSettingsTile(context: Context) {
        runCatching {
            TileService.requestListeningState(
                context,
                ComponentName(context, SleepModeTileService::class.java)
            )
        }.onFailure { error -> Log.w(TAG, "No se pudo solicitar la actualización del tile", error) }
    }

    private fun todayKey(): Int {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.YEAR) * 400 + calendar.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun hasWokenToday(context: Context): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour !in NightAirplaneScreenReceiver.MORNING_WAKE_HOUR until 23) return false
        return context.getSharedPreferences(AIRPLANE_PREFS, Context.MODE_PRIVATE)
            .getInt(NightAirplaneScreenReceiver.KEY_AWAKE_DAY, -1) == todayKey()
    }

    private fun setAirplaneMode(context: Context, enabled: Boolean): Boolean {
        val current = ShizukuManager.isAirplaneModeOn()
        if (current == enabled) return true
        if (current == null) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_EXPECTED_AIRPLANE, if (enabled) 1 else 0)
            .putLong(KEY_EXPECTED_AIRPLANE_AT, System.currentTimeMillis())
            .commit()
        val confirmed = ShizukuManager.setAirplaneMode(enabled) && ShizukuManager.isAirplaneModeOn() == enabled
        if (!confirmed) prefs.edit().remove(KEY_EXPECTED_AIRPLANE).remove(KEY_EXPECTED_AIRPLANE_AT).apply()
        return confirmed
    }

    data class Result(val success: Boolean, val enabled: Boolean, val message: String)
}
