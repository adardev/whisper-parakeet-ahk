package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.content.ComponentName
import android.service.quicksettings.TileService
import android.util.Log

/** Manually runs Nemotron's existing night/morning routines ahead of their schedule. */
object SleepModeController {
    private const val PREFS = "sleep_mode"
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

    @Synchronized
    fun toggle(context: Context): Result = setEnabled(context, !isEnabled(context))

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean): Result = applyEnabled(context, enabled, manual = true)

    /** Scheduled changes do not count as a user's manual choice. */
    @Synchronized
    fun setScheduledEnabled(context: Context, enabled: Boolean): Result = applyEnabled(context, enabled, manual = false)

    private fun applyEnabled(context: Context, enabled: Boolean, manual: Boolean): Result {
        if (!ShizukuManager.hasPermission()) return Result(false, enabled, "Shizuku no está disponible")

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldSuppression = prefs.getBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, false)
        if (manual) {
            // Persist before the asynchronous airplane broadcast/screen-off work can race us.
            prefs.edit().putBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, !enabled).commit()
        } else if (enabled) {
            // The new night begins the next suppression window.
            prefs.edit().putBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, false).commit()
        }

        // Match the scheduled routine's ordering: airplane mode goes off before
        // the morning watch handoff, and on only after the night handoff succeeds.
        if (!enabled && !setAirplaneMode(context, false)) {
            if (manual) prefs.edit().putBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, oldSuppression).commit()
            return Result(false, isEnabled(context), "No se pudo desactivar el modo avión")
        }

        val routine = NightRoutineActions.run(context.applicationContext, night = enabled)
        if (!routine.success) {
            if (manual) prefs.edit().putBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, oldSuppression).commit()
            return Result(false, isEnabled(context), routine.message)
        }

        if (enabled && !setAirplaneMode(context, true)) {
            if (manual) prefs.edit().putBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, oldSuppression).commit()
            return Result(false, isEnabled(context), "Fit3 quedó en modo noche, pero no se pudo activar el modo avión")
        }

        prefs.edit().putBoolean(KEY_ENABLED, enabled).commit()
        NightAirplaneScreenReceiver().clearPendingWork(context)
        refreshQuickSettingsTile(context)
        Log.i(TAG, "Sleep Mode ${if (enabled) "activado" else "desactivado"}")
        return Result(true, enabled, if (enabled) "Modo noche activado" else "Rutina de mañana aplicada")
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
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
        if (enabled) edit.putBoolean(KEY_MANUAL_DISABLE_UNTIL_NIGHT, false)
        edit.commit()
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
