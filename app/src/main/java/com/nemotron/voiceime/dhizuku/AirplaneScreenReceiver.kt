package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

/**
 * Cuando airplane mode está activado (tile ON), al apagar la pantalla
 * activa Airplane Mode. Al encender la pantalla lo desactiva.
 */
class AirplaneScreenReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val enabled = SecureStore.isAirplaneModeEnabled(ctx)
        Log.d(TAG, "onReceive: ${intent.action} airplaneFlag=$enabled")
        if (!enabled) return

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Pantalla apagada → activando Airplane Mode")
                setAirplaneMode(ctx, true)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Pantalla encendida → desactivando Airplane Mode")
                setAirplaneMode(ctx, false)
            }
        }
    }

    private fun setAirplaneMode(ctx: Context, enabled: Boolean) {
        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku no disponible")
            return
        }
        val value = if (enabled) "1" else "0"
        ShizukuManager.execShellFresh(arrayOf("settings", "put", "global", "airplane_mode_on", value))
        ShizukuManager.execShellFresh(arrayOf(
            "am", "broadcast", "-a", "android.intent.action.AIRPLANE_MODE",
            "--ez", "state", enabled.toString()
        ))
        Log.d(TAG, "Airplane Mode → $enabled")
    }

    companion object {
        private const val TAG = "AirplaneScreen"
    }
}
