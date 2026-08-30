package com.nemotron.voiceime.guard

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.nemotron.voiceime.data.SecureStore
import com.nemotron.voiceime.dhizuku.CarDetector
import com.nemotron.voiceime.dhizuku.ShizukuManager

/**
 * Bloquea la pantalla cuando se activa el modo No Molestar.
 *
 * Usa un "double-check": espera 2s tras el broadcast y re-verifica que DND
 * sigue activo. Esto evita que Samsung bloquee la pantalla al abrir QS edit
 * (Samsung manda NOTIFICATION_POLICY_CHANGED temporalmente).
 */
class DndLockReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) return
        if (!SecureStore.isDndLockEnabled(ctx)) return

        val now = System.currentTimeMillis()
        if (now - lastLockAt < LOCK_COOLDOWN_MS) return

        val filter = try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.currentInterruptionFilter
        } catch (t: Throwable) {
            intent.getIntExtra(EXTRA_INTERRUPTION_FILTER, INTERRUPTION_FILTER_UNKNOWN)
        }
        if (filter == NotificationManager.INTERRUPTION_FILTER_ALL ||
            filter == INTERRUPTION_FILTER_UNKNOWN) return

        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == false) return

        Log.i(TAG, "No Molestar activado (filter=$filter) → double-check en 2s...")
        val pendingResult = goAsync()
        Thread {
            try {
                if (CarDetector.isCarConnected()) {
                    Log.i(TAG, "Coche conectado: no se bloquea la pantalla")
                    return@Thread
                }
                // Double-check: esperar 2s y re-verificar que DND sigue activo.
                // Samsung manda el broadcast temporalmente al abrir QS edit.
                Thread.sleep(DOUBLE_CHECK_DELAY_MS)
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val stillActive = nm.currentInterruptionFilter !=
                    NotificationManager.INTERRUPTION_FILTER_ALL
                if (!stillActive) {
                    Log.i(TAG, "DND ya no está activo tras double-check → no bloquear")
                    return@Thread
                }
                lastLockAt = System.currentTimeMillis()
                lockScreenWithRetry()
            } catch (t: Throwable) {
                Log.w(TAG, "Error en double-check", t)
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
        private const val LOCK_COOLDOWN_MS = 30_000L
        private const val DOUBLE_CHECK_DELAY_MS = 2_000L
        private const val EXTRA_INTERRUPTION_FILTER = "android.app.extra.INTERRUPTION_FILTER"
        private const val INTERRUPTION_FILTER_UNKNOWN = 0
        @Volatile private var lastLockAt = 0L
    }
}
