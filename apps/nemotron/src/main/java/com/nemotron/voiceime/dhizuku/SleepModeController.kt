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
    fun setScheduledEnabled(context: Context, enabled: Boolean): Result {
        if (!enabled) {
            Log.w(TAG, "Se rechaza la desactivación automática; solo el usuario puede apagar Sleep Mode")
            return Result(false, isEnabled(context), "La desactivación de Sleep Mode es manual")
        }
        return applyEnabled(context, true)
    }

    private fun applyEnabled(context: Context, enabled: Boolean): Result {
        val wasEnabled = isEnabled(context)
        if (!RootShellManager.hasPermission()) {
            return Result(false, wasEnabled, "KernelSU no está disponible")
        }

        // Restore connectivity before sending the manual daytime watch settings.
        if (!enabled && !setAirplaneMode(false)) {
            return Result(false, wasEnabled, "No se pudo desactivar el modo avión")
        }
        // Watch settings are optional: an unavailable watch must not prevent
        // the phone's sleep mode (or its manual off action) from working.
        val routine = NightRoutineActions.run(context.applicationContext, night = enabled)
        if (!routine.success) Log.w(TAG, "Ajustes Fit3 pendientes: ${routine.message}")
        if (enabled && !setAirplaneMode(true))
            return Result(false, wasEnabled, "No se pudo activar el modo avión")

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).commit()
        runCatching {
            TileService.requestListeningState(context, ComponentName(context, SleepModeTileService::class.java))
        }.onFailure { Log.w(TAG, "No se pudo actualizar el tile", it) }
        Log.i(TAG, "Sleep Mode ${if (enabled) "activado" else "desactivado"}")
        val message = if (enabled) "Modo sueño activado" else "Modo sueño desactivado"
        return Result(routine.success, enabled, if (routine.success) message else "$message. ${routine.message}")
    }

    private fun setAirplaneMode(enabled: Boolean): Boolean {
        val current = RootShellManager.isAirplaneModeOn() ?: return false
        return current == enabled ||
            (RootShellManager.setAirplaneMode(enabled) && RootShellManager.isAirplaneModeOn() == enabled)
    }

    data class Result(val success: Boolean, val enabled: Boolean, val message: String)
}
