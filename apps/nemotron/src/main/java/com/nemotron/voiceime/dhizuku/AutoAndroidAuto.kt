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
            if (!RootShellManager.hasPermission()) {
                Log.w(TAG, "coche conectado pero KernelSU no disponible; se reintentará al volver")
                return@Thread
            }
            RootShellManager.setDoubleTapToWake(true)
            RootShellManager.setDoubleTapToSleep(true)
            if (!RootShellManager.isAppHidden(AA_PACKAGE)) {
                Log.d(TAG, "coche conectado, Android Auto ya activo")
                return@Thread
            }
            if (RootShellManager.unhideApp(AA_PACKAGE)) {
                SecureStore.setAutoAndroidAutoWasUnfroze(ctx, true)
                Log.i(TAG, "coche conectado → Android Auto descongelado")
            } else {
                Log.w(TAG, "no se pudo descongelar Android Auto")
            }
        }.start()
    }

    fun onCarDisconnected(ctx: Context) {
        if (!SecureStore.isAutoAndroidAutoEnabled(ctx)) return
        val wasUnfroze = SecureStore.wasAutoAndroidAutoUnfroze(ctx)
        Thread {
            if (!RootShellManager.hasPermission()) return@Thread
            RootShellManager.setDoubleTapToWake(false)
            RootShellManager.setDoubleTapToSleep(false)
            if (wasUnfroze) {
                RootShellManager.hideApp(AA_PACKAGE)
                RootShellManager.stopApp(AA_PACKAGE)
                SecureStore.setAutoAndroidAutoWasUnfroze(ctx, false)
                Log.i(TAG, "coche desconectado → Android Auto congelado de nuevo")
            }
        }.start()
    }

    /** Reconciliar al arrancar/volver KernelSU: si el coche está conectado y
     *  Android Auto quedó congelado (p.ej. KernelSU murió en el momento exacto
     *  de conectar), descongelarlo. */
    fun reconcile(ctx: Context) {
        if (!SecureStore.isAutoAndroidAutoEnabled(ctx)) return
        if (!CarDetector.isCarConnected()) return
        Thread {
            if (!RootShellManager.hasPermission()) return@Thread
            RootShellManager.setDoubleTapToWake(true)
            RootShellManager.setDoubleTapToSleep(true)
            if (RootShellManager.isAppHidden(AA_PACKAGE)) {
                if (RootShellManager.unhideApp(AA_PACKAGE)) {
                    SecureStore.setAutoAndroidAutoWasUnfroze(ctx, true)
                    Log.i(TAG, "reconcile: coche conectado → Android Auto descongelado")
                }
            }
        }.start()
    }
}
