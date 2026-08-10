package com.nemotron.voiceime.guard

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.dhizuku.ShizukuManager

/**
 * Bloquea la pantalla cuando se activa el modo No Molestar.
 *
 * Escucha [NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED], un
 * broadcast de sistema que solo se emite al cambiar el filtro de
 * interrupciones (DND). Es event-driven: sin polling, sin gasto de batería.
 *
 * Solo bloquea al pasar a un modo de no molestar activo (Prioritario,
 * Solo alarmas o Ninguno); al desactivar DND no hace nada. El bloqueo se
 * hace con Shizuku (apaga y bloquea la pantalla) y, una vez bloqueada,
 * desactiva el toggle de No Molestar para que al desbloquear el teléfono
 * no quede sin notificaciones. Si Shizuku no está disponible se intenta
 * de nuevo en segundo plano, sin bloquear la UI.
 */
class DndLockReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) return
        if (!SecureStore.isDndLockEnabled(ctx)) return

        val filter = try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.currentInterruptionFilter
        } catch (t: Throwable) {
            intent.getIntExtra(EXTRA_INTERRUPTION_FILTER, INTERRUPTION_FILTER_UNKNOWN)
        }
        // DND desactivado (all) o filtro desconocido → no hacer nada.
        if (filter == NotificationManager.INTERRUPTION_FILTER_ALL ||
            filter == INTERRUPTION_FILTER_UNKNOWN) return

        // La pantalla ya apagada no necesita bloquearse otra vez.
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == false) return

        Log.i(TAG, "No Molestar activado (filter=$filter) → bloqueando pantalla")
        val pendingResult = goAsync()
        Thread {
            try {
                lockScreenWithRetry()
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun lockScreenWithRetry() {
        for (attempt in 1..RETRIES) {
            if (ShizukuManager.hasPermission()) {
                if (ShizukuManager.lockScreen()) {
                    Log.i(TAG, "Pantalla bloqueada por No Molestar")
                    disableDnd()
                    return
                }
                Log.w(TAG, "lockScreen devolvió false (intento $attempt)")
            } else {
                Log.w(TAG, "Shizuku no disponible (intento $attempt), reintentando")
            }
            try {
                Thread.sleep(RETRY_DELAY_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun disableDnd() {
        // Apaga el toggle de No Molestar tras bloquear: al desbloquear el
        // teléfono el usuario no queda sin notificaciones.
        if (ShizukuManager.disableDnd()) {
            Log.i(TAG, "No Molestar desactivado tras bloquear")
        } else {
            Log.w(TAG, "No se pudo desactivar No Molestar")
        }
    }

    companion object {
        private const val TAG = "DndLock"
        private const val RETRIES = 6
        private const val RETRY_DELAY_MS = 1_500L
        // Constantes de NotificationManager no expuestas en el SDK.
        private const val EXTRA_INTERRUPTION_FILTER = "android.app.extra.INTERRUPTION_FILTER"
        private const val INTERRUPTION_FILTER_UNKNOWN = 0
    }
}
