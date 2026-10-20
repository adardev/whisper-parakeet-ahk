package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.util.Log

/** Shared by the daily AOD schedule and the manual night/morning buttons. */
object NightRoutineActions {
    data class Result(val success: Boolean, val message: String)

    @Synchronized
    fun run(context: Context, night: Boolean): Result {
        if (!ShizukuManager.hasPermission()) {
            return Result(false, "Shizuku no está autorizado para activar el tile Galaxy Wearable.")
        }
        val aod = try {
            if (!Fit3TileAutomation.prepareConnection(context)) {
                Fit3AodController.Result(false, "No se pudo preparar Samsung Health para enviar los ajustes.")
            } else {
                Fit3AodController.setEnabled(context, !night)
            }
        } catch (e: Exception) {
            Fit3AodController.Result(false, "Fit3: ${e.message ?: "no se pudo abrir la conexión"}")
        } finally {
            // Restore only Samsung Health's configured tile state. Never freeze
            // or force-stop Fit3 Plugin, Galaxy Wearable, or Samsung Accessory.
            Fit3TileAutomation.freezeAfterSetting(context)
        }
        Log.i("NightRoutineActions", "night=$night fit3AodConfirmed=${aod.confirmed}")
        return Result(aod.confirmed, aod.message)
    }
}
