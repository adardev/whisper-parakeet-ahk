package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Detecta la conexión del coche por USB (Android Auto usa el protocolo
 * Android Open Accessory: el teléfono entra en modo accesorio y llega
 * USB_ACCESSORY_ATTACHED / DETACHED). No requiere permisos de runtime.
 *
 * Al conectar: marca el coche como conectado y descongela Android Auto si
 * estaba congelado. Al desconectar: lo vuelve a congelar.
 */
class CarConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                Log.i(TAG, "Accesorio USB conectado (coche)")
                CarDetector.setCarConnected(true)
                AutoAndroidAuto.onCarConnected(ctx)
            }
            UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                Log.i(TAG, "Accesorio USB desconectado")
                CarDetector.setCarConnected(false)
                AutoAndroidAuto.onCarDisconnected(ctx)
            }
        }
    }

    companion object {
        private const val TAG = "CarConnection"
    }
}
