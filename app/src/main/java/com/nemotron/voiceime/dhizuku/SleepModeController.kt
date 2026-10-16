package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.content.ComponentName
import android.service.quicksettings.TileService
import android.util.Log

/** Manually runs Nemotron's existing night/morning routines ahead of their schedule. */
object SleepModeController {
    private const val PREFS = "sleep_mode"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_EXPECTED_AIRPLANE = "expected_airplane_state"
    private const val KEY_EXPECTED_AIRPLANE_AT = "expected_airplane_state_at"
    private const val TAG = "SleepMode"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    @Synchronized
    fun toggle(context: Context): Result = setEnabled(context, !isEnabled(context))

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean): Result {
        if (!ShizukuManager.hasPermission()) return Result(false, enabled, "Shizuku no está disponible")

        // Match the scheduled routine's ordering: airplane mode goes off before
        // the morning watch handoff, and on only after the night handoff succeeds.
        if (!enabled && !setAirplaneMode(context, false)) {
            return Result(false, isEnabled(context), "No se pudo desactivar el modo avión")
        }

        val routine = NightRoutineActions.run(context.applicationContext, night = enabled)
        if (!routine.success) return Result(false, isEnabled(context), routine.message)

        if (enabled && !setAirplaneMode(context, true)) {
            return Result(false, isEnabled(context), "Fit3 quedó en modo noche, pero no se pudo activar el modo avión")
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).commit()
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).commit()
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
