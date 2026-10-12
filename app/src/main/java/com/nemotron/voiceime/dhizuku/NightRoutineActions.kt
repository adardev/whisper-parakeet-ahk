package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.util.Log

/** Shared by the daily AOD schedule and the manual night/morning buttons. */
object NightRoutineActions {
    data class Result(val success: Boolean, val message: String)

    @Synchronized
    fun run(context: Context, night: Boolean): Result {
        if (!ShizukuManager.hasPermission()) {
            return Result(false, "Shizuku no está autorizado para activar el tile Fit3.")
        }
        val aod = try {
            if (!Fit3TileAutomation.prepareConnection(context)) {
                Fit3AodController.Result(false, "No se pudo activar el tile Fit3 y Bluetooth para enviar los ajustes.")
            } else {
                SamsungFit3Handoff().run { Fit3AodController.setEnabled(context, !night) }
            }
        } catch (e: Exception) {
            Fit3AodController.Result(false, "Fit3: ${e.message ?: "no se pudo abrir la conexión"}")
        } finally {
            // The requested tile state is OFF after the send, at both schedule boundaries.
            Fit3TileAutomation.freezeAfterSetting(context)
        }
        Log.i("NightRoutineActions", "night=$night fit3AodConfirmed=${aod.confirmed}")
        return Result(aod.confirmed, aod.message)
    }

    /** Galaxy Wearable keeps the Fit3 SAP/GATT session; briefly hand that link to Nemotron. */
    private class SamsungFit3Handoff {
        private val packages = listOf("com.samsung.wearable.fit3plugin", "com.samsung.accessory")

        fun <T> run(action: () -> T): T {
            if (!ShizukuManager.hasPermission()) throw IllegalStateException("Shizuku no está autorizado")
            val alreadyDisabled = ShizukuManager.stillDisabled(packages)
            val disabledHere = mutableListOf<String>()
            var restoreError: String? = null
            try {
                for (pkg in packages) {
                    if (pkg in alreadyDisabled) continue
                    check(ShizukuManager.hideApp(pkg)) { "no se pudo pausar $pkg" }
                    disabledHere += pkg
                }
                // disable-user may leave an already running accessory process alive;
                // stop it so the next short SAP session starts with a free GATT link.
                for (pkg in packages) {
                    check(ShizukuManager.execShell("am force-stop $pkg")) { "no se pudo detener $pkg" }
                }
                // Let Android release Samsung's existing BLE bearer before Nemotron connects.
                Thread.sleep(2_000)
                return action()
            } finally {
                for (pkg in disabledHere.asReversed()) {
                    if (!ShizukuManager.unhideApp(pkg)) restoreError = pkg
                }
                if (restoreError != null) Log.e("NightRoutineActions", "no se pudo restaurar $restoreError")
            }
        }
    }
}
