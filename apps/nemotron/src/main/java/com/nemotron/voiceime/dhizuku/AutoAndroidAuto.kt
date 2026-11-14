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
        return prepareTileMedia(ctx)
    }

    /** Called by the tile after its root batch has already unhidden and unstopped AA. */
    fun prepareTileMedia(ctx: Context): Boolean {
        if (!RootShellManager.hasPermission()) return false
        val aaComponent = "$AA_PACKAGE/com.google.android.gearhead.notifications.SharedNotificationListenerManager\$ListenerService"
        val bridgeComponent = "${ctx.packageName}/com.nemotron.voiceime.car.CarMediaSessionListener"
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val script = """
            cmd notification allow_listener '$aaComponent' >/dev/null 2>&1; aa_rc=${'$'}?
            cmd notification allow_listener '$bridgeComponent' 0 >/dev/null 2>&1; bridge_rc=${'$'}?
            listeners=${'$'}(settings get secure enabled_notification_listeners)
            aa_on=0; bridge_on=0
            case ":${'$'}listeners:" in *":$aaComponent:"*) aa_on=1;; esac
            case ":${'$'}listeners:" in *":$bridgeComponent:"*) bridge_on=1;; esac
            printf '__NEMO_MEDIA__%s|%s|%s|%s\n' ${'$'}aa_rc ${'$'}bridge_rc ${'$'}aa_on ${'$'}bridge_on
        """.trimIndent()
        val result = RootAccessManager.runRoot(arrayOf("sh", "-c", script), timeoutMs = 8_000L)
        val marker = result.detail.lineSequence().firstOrNull { it.startsWith("__NEMO_MEDIA__") }
        val fields = marker?.removePrefix("__NEMO_MEDIA__")?.split('|')
        val success = result.success && fields?.size == 4
        Log.i(TAG, "Android Auto tile media setup success=$success aaCmd=${fields?.getOrNull(0)} bridgeCmd=${fields?.getOrNull(1)} aaListener=${fields?.getOrNull(2)} bridge=${fields?.getOrNull(3)} elapsed=${android.os.SystemClock.elapsedRealtime() - startedAt}ms")
        // Listener denial is logged, but must not undo the successful package
        // unhide/unstop or make the tile report itself as off.
        return success
    }

    /** Enables the Salt session proxy only while the Android Auto tile is enabled. */
    fun setCarMediaBridgeEnabled(ctx: Context, enabled: Boolean) {
        if (!RootShellManager.hasPermission()) return
        val component = "${ctx.packageName}/com.nemotron.voiceime.car.CarMediaSessionListener"
        val command = if (enabled) "allow_listener" else "disallow_listener"
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val script = """
            cmd notification $command '$component' 0 >/dev/null 2>&1; rc=${'$'}?
            listeners=${'$'}(settings get secure enabled_notification_listeners)
            bridge_on=0
            case ":${'$'}listeners:" in *":$component:"*) bridge_on=1;; esac
            printf '__NEMO_BRIDGE__%s|%s\n' ${'$'}rc ${'$'}bridge_on
        """.trimIndent()
        val result = RootAccessManager.runRoot(arrayOf("sh", "-c", script), timeoutMs = 8_000L)
        val fields = result.detail.lineSequence()
            .firstOrNull { it.startsWith("__NEMO_BRIDGE__") }
            ?.removePrefix("__NEMO_BRIDGE__")
            ?.split('|')
        val registered = fields?.getOrNull(1) == if (enabled) "1" else "0"
        Log.i(TAG, "Salt media bridge enabled=${enabled && result.success && fields?.getOrNull(0) == "0" && registered} elapsed=${android.os.SystemClock.elapsedRealtime() - startedAt}ms")
    }
}
