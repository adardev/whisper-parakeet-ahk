package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

/**
 * Si Android Auto está congelado y se conecta el coche, lo descongela solo
 * para que pueda proyectar. Al desconectar el coche lo vuelve a congelar.
 *
 * La señal de conexión la entrega [CarDetector] (accesorio USB Android Open
 * Accessory, Bluetooth A2DP o procesos de Android Auto). Solo se vuelve a
 * congelar si la propia app lo descongeló (no si el usuario lo descongeló a
 * mano): así no pisa la decisión manual del usuario.
 */
object AutoAndroidAuto {

    private const val TAG = "AutoAndroidAuto"
    const val AA_PACKAGE = "com.google.android.projection.gearhead"

    fun onCarConnected(ctx: Context) {
        if (!SecureStore.isAutoAndroidAutoEnabled(ctx)) return
        Thread {
            if (!ShizukuManager.hasPermission()) {
                Log.w(TAG, "coche conectado pero Shizuku no disponible; se reintentará al volver")
                return@Thread
            }
            if (!ShizukuManager.isAppHidden(AA_PACKAGE)) {
                Log.d(TAG, "coche conectado, Android Auto ya activo")
                return@Thread
            }
            if (ShizukuManager.unhideApp(AA_PACKAGE)) {
                SecureStore.setAutoAndroidAutoWasUnfroze(ctx, true)
                Log.i(TAG, "coche conectado → Android Auto descongelado")
            } else {
                Log.w(TAG, "no se pudo descongelar Android Auto")
            }
        }.start()
    }

    fun onCarDisconnected(ctx: Context) {
        if (!SecureStore.isAutoAndroidAutoEnabled(ctx)) return
        if (!SecureStore.wasAutoAndroidAutoUnfroze(ctx)) return
        Thread {
            if (!ShizukuManager.hasPermission()) return@Thread
            ShizukuManager.hideApp(AA_PACKAGE)
            ShizukuManager.stopApp(AA_PACKAGE)
            SecureStore.setAutoAndroidAutoWasUnfroze(ctx, false)
            Log.i(TAG, "coche desconectado → Android Auto congelado de nuevo")
        }.start()
    }

    /** Reconciliar al arrancar/volver Shizuku: si el coche está conectado y
     *  Android Auto quedó congelado (p.ej. Shizuku murió en el momento exacto
     *  de conectar), descongelarlo. */
    fun reconcile(ctx: Context) {
        if (!SecureStore.isAutoAndroidAutoEnabled(ctx)) return
        if (!CarDetector.isCarConnected()) return
        Thread {
            if (!ShizukuManager.hasPermission()) return@Thread
            if (ShizukuManager.isAppHidden(AA_PACKAGE)) {
                if (ShizukuManager.unhideApp(AA_PACKAGE)) {
                    SecureStore.setAutoAndroidAutoWasUnfroze(ctx, true)
                    Log.i(TAG, "reconcile: coche conectado → Android Auto descongelado")
                }
            }
        }.start()
    }
}
