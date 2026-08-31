package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Siempre UNA conexión activa entre WiFi y Datos móviles.
 * - Enciendes WiFi → apaga datos móviles
 * - Apagas WiFi → enciende datos móviles
 * - Enciendes datos → apaga WiFi
 * - Apagas datos → enciende WiFi
 *
 * Usa un receiver + un chequeo periódico (polling ligero) para no depender
 * solo de los broadcasts del sistema.
 */
object ConnectionExclusionManager {

    private const val TAG = "ConnExclusion"
    private const val POLL_INTERVAL_MS = 5_000L

    @Volatile private var lastWifiOn = false
    @Volatile private var lastMobileOn = false
    @Volatile private var started = false

    private val handler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private var receiver: BroadcastReceiver? = null

    fun start(ctx: Context) {
        if (started) return
        started = true
        val appCtx = ctx.applicationContext

        // Receiver para respuesta rápida
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                check(c)
            }
        }.also { r ->
            val filter = android.content.IntentFilter().apply {
                addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
                addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                appCtx.registerReceiver(r, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                appCtx.registerReceiver(r, filter)
            }
        }

        // Polling como respaldo
        pollRunnable = object : Runnable {
            override fun run() {
                check(appCtx)
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }.also { handler.postDelayed(it, POLL_INTERVAL_MS) }

        // Estado inicial
        check(appCtx)
        Log.d(TAG, "ConnectionExclusion iniciado")
    }

    fun stop(ctx: Context) {
        if (!started) return
        started = false
        pollRunnable?.let { handler.removeCallbacks(it) }
        pollRunnable = null
        receiver?.let { r ->
            try { ctx.applicationContext.unregisterReceiver(r) } catch (_: Throwable) {}
        }
        receiver = null
        Log.d(TAG, "ConnectionExclusion detenido")
    }

    fun check(ctx: Context) {
        val wifiOn = isWifiOn(ctx)
        val mobileOn = isMobileDataOn(ctx)

        if (wifiOn != lastWifiOn || mobileOn != lastMobileOn) {
            Log.d(TAG, "estado → wifi=$wifiOn mobile=$mobileOn (prev wifi=$lastWifiOn mobile=$lastMobileOn)")
        }

        when {
            // Ambos encendidos → apagar el que se acaba de encender
            wifiOn && mobileOn -> when {
                !lastWifiOn && lastMobileOn -> exec("svc", "data", "disable")
                lastWifiOn && !lastMobileOn -> exec("svc", "wifi", "disable")
                else -> exec("svc", "data", "disable")
            }
            // Ambos apagados → encender el que se acaba de apagar
            !wifiOn && !mobileOn -> when {
                lastWifiOn && !lastMobileOn -> exec("svc", "data", "enable")
                !lastWifiOn && lastMobileOn -> exec("svc", "wifi", "enable")
                else -> exec("svc", "wifi", "enable")
            }
        }

        lastWifiOn = wifiOn
        lastMobileOn = mobileOn
    }

    private fun exec(vararg args: String) {
        if (!ShizukuManager.hasPermission()) return
        Log.d(TAG, "ejecutando: ${args.joinToString(" ")}")
        ShizukuManager.execShellFresh(arrayOf(*args))
    }

    private fun isWifiOn(ctx: Context): Boolean = try {
        val wm = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wm.isWifiEnabled
    } catch (t: Throwable) {
        false
    }

    private fun isMobileDataOn(ctx: Context): Boolean = try {
        Settings.Global.getInt(ctx.contentResolver, "mobile_data", 0) == 1
    } catch (t: Throwable) {
        false
    }
}