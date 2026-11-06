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
            val hidden = RootShellManager.isAppHidden(AA_PACKAGE)
            if (prepareForMedia(ctx)) {
                if (hidden) {
                    SecureStore.setAutoAndroidAutoWasUnfroze(ctx, true)
                }
                Log.i(TAG, "coche conectado → Android Auto visible, habilitado, sin force-stop y listener multimedia listo")
            } else {
                Log.w(TAG, "coche conectado pero Android Auto no quedó listo para iniciar media")
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
            val hidden = RootShellManager.isAppHidden(AA_PACKAGE)
            if (prepareForMedia(ctx)) {
                if (hidden) {
                    SecureStore.setAutoAndroidAutoWasUnfroze(ctx, true)
                }
                Log.i(TAG, "reconcile: coche conectado → Android Auto listo para media")
            }
        }.start()
    }

    /** Clear pm hide and force-stop left by the tile before the car UI/media host starts. */
    fun prepareForMedia(ctx: Context): Boolean {
        if (!RootShellManager.hasPermission()) return false
        if (!RootShellManager.unhideApp(AA_PACKAGE)) return false
        if (!RootShellManager.unstopApp(AA_PACKAGE)) return false

        val component = "$AA_PACKAGE/com.google.android.gearhead.notifications.SharedNotificationListenerManager\$ListenerService"
        fun isListenerEnabled(): Boolean = RootShellManager.execShellFresh(
            arrayOf("settings", "get", "secure", "enabled_notification_listeners")
        )?.split(':')?.any { it.trim().equals(component, ignoreCase = true) } == true

        if (!isListenerEnabled()) {
            RootShellManager.execShellFresh(arrayOf("cmd", "notification", "allow_listener", component))
        }
        val listenerReady = isListenerEnabled()
        Log.i(TAG, "Android Auto media listener habilitado=$listenerReady")
        setCarMediaBridgeEnabled(ctx, true)
        // A media-listener permission issue must be visible in logs but should
        // not undo the package unhide/unstop needed to start the car session.
        return true
    }

    /** Enables the Salt session proxy only while the Android Auto tile is enabled. */
    fun setCarMediaBridgeEnabled(ctx: Context, enabled: Boolean) {
        if (!RootShellManager.hasPermission()) return
        val component = "${ctx.packageName}/com.nemotron.voiceime.car.CarMediaSessionListener"
        val command = if (enabled) "allow_listener" else "disallow_listener"
        RootShellManager.execShellFresh(arrayOf("cmd", "notification", command, component, "0"))
        val registered = RootShellManager.execShellFresh(
            arrayOf("settings", "get", "secure", "enabled_notification_listeners")
        )?.split(':')?.any { it.trim().equals(component, ignoreCase = true) } == true
        Log.i(TAG, "Salt media bridge enabled=${enabled && registered}")
    }
}
