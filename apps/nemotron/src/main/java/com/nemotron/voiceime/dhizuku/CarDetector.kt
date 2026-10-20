package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.SystemClock
import android.util.Log
import kotlin.concurrent.thread

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
    @Volatile
    private var refreshInFlight = false
    @Volatile
    private var lastRefreshAt = 0L
    @Volatile
    private var lastRefreshHadPermission = false
    private val refreshLock = Any()

    /** True si el coche está conectado según cualquier señal disponible. */
    fun isCarConnected(): Boolean =
        carConnected || ShizukuManager.isAndroidAutoActive()

    fun setCarConnected(connected: Boolean) {
        carConnected = connected
        Log.d(TAG, "car state = $connected")
    }

    /** Re-deriva el estado al arrancar el proceso o al volver Shizuku. */
    fun refresh(ctx: Context) {
        val hasPermission = ShizukuManager.hasPermission()
        val now = SystemClock.elapsedRealtime()
        synchronized(refreshLock) {
            if (refreshInFlight) return
            // onCreate, binderReceived and the delayed recovery callback can
            // all request this refresh during one startup. Re-query at most
            // every 30 s, but allow a fresh query when Shizuku becomes ready.
            if (now - lastRefreshAt < REFRESH_COOLDOWN_MS &&
                (lastRefreshHadPermission || !hasPermission)) return
            refreshInFlight = true
            lastRefreshAt = now
            lastRefreshHadPermission = hasPermission
        }
        thread(name = "Nemotron-car-state", priority = Thread.MIN_PRIORITY) {
            var connected = false
            try {
                val um = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager
                connected = um?.accessoryList?.isNotEmpty() == true
            } catch (_: Throwable) {}
            try {
                if (!connected && hasPermission) connected = ShizukuManager.isAndroidAutoActive()
                carConnected = connected
                Log.d(TAG, "refreshed car state = $carConnected")
            } finally {
                synchronized(refreshLock) { refreshInFlight = false }
            }
        }
    }

    private const val REFRESH_COOLDOWN_MS = 30_000L
}
