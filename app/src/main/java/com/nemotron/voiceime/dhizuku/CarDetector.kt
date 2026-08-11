package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Estado de conexión con el coche.
 *
 * Se marca como conectado cuando llega un accesorio USB Android Open Accessory
 * (Android Auto por cable), un perfil A2DP de Bluetooth se conecta, o cuando
 * los procesos de Android Auto están corriendo. Combinar las tres señales
 * permite detectar el coche incluso si Android Auto está congelado (no corre).
 */
object CarDetector {

    private const val TAG = "CarDetector"

    @Volatile
    private var carConnected = false

    /** True si el coche está conectado según cualquier señal disponible. */
    fun isCarConnected(): Boolean =
        carConnected || ShizukuManager.isAndroidAutoActive()

    fun setCarConnected(connected: Boolean) {
        carConnected = connected
        Log.d(TAG, "car state = $connected")
    }

    /** Re-deriva el estado al arrancar el proceso o al volver Shizuku. */
    fun refresh(ctx: Context) {
        Thread {
            var connected = false
            try {
                val um = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager
                connected = um?.accessoryList?.isNotEmpty() == true
            } catch (_: Throwable) {}
            if (!connected) connected = ShizukuManager.isAndroidAutoActive()
            carConnected = connected
            Log.d(TAG, "refreshed car state = $carConnected")
        }.start()
    }
}
