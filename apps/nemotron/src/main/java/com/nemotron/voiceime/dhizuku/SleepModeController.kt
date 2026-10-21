package com.nemotron.voiceime.dhizuku

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import android.util.Log

/** Manual toggle plus a single daily activation at 23:00. */
object SleepModeController {
    private const val PREFS = "sleep_mode"
    private const val KEY_ENABLED = "enabled"
    private const val TAG = "SleepMode"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    @Synchronized
    fun toggle(context: Context): Result = applyEnabled(context, !isEnabled(context))

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean): Result = applyEnabled(context, enabled)

    @Synchronized
    fun setScheduledEnabled(context: Context, enabled: Boolean): Result =
        if (enabled) applyEnabled(context, true)
        else Result(true, isEnabled(context), "El apagado de Sleep Mode es manual")

    private fun applyEnabled(context: Context, enabled: Boolean): Result {
        if (isEnabled(context) == enabled) return Result(true, enabled, "Sin cambios")
        if (!ShizukuManager.hasPermission()) {
            return Result(false, isEnabled(context), "Shizuku no está disponible")
        }

        // Restore connectivity before sending the manual daytime watch settings.
        if (!enabled && !setAirplaneMode(false)) {
            return Result(false, isEnabled(context), "No se pudo desactivar el modo avión")
        }
        val routine = NightRoutineActions.run(context.applicationContext, night = enabled)
        if (!routine.success) return Result(false, isEnabled(context), routine.message)
        if (enabled && !setAirplaneMode(true)) {
            return Result(false, isEnabled(context), "Fit3 quedó en modo noche, pero no se pudo activar el modo avión")
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).commit()
        runCatching {
            TileService.requestListeningState(context, ComponentName(context, SleepModeTileService::class.java))
        }.onFailure { Log.w(TAG, "No se pudo actualizar el tile", it) }
        Log.i(TAG, "Sleep Mode ${if (enabled) "activado" else "desactivado"}")
        return Result(true, enabled, if (enabled) "Modo sueño activado" else "Modo sueño desactivado")
    }

    private fun setAirplaneMode(enabled: Boolean): Boolean {
        val current = ShizukuManager.isAirplaneModeOn() ?: return false
        return current == enabled ||
            (ShizukuManager.setAirplaneMode(enabled) && ShizukuManager.isAirplaneModeOn() == enabled)
    }

    data class Result(val success: Boolean, val enabled: Boolean, val message: String)
}
