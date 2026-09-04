package com.nemotron.voiceime.health

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver: NO inicia HealthTransferService automaticamente.
 *
 * El servicio solo debe correr cuando el tile Fit3 esta ACTIVO (pulsera
 * descongelada) para ahorrar bateria. Este receiver solo existe para
 * limpiar cualquier servicio residual tras una reinstalacion.
 */
class HealthBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Limpiar el servicio residual de una instalacion previa
            Log.d("HealthBootReceiver", "App reemplazada, deteniendo servicio residual")
            try { HealthTransferService.stop(context) } catch (_: Throwable) {}
        }
        // BOOT_COMPLETED / QUICKBOOT: no hacer nada — el tile Fit3 controla el servicio
    }
}