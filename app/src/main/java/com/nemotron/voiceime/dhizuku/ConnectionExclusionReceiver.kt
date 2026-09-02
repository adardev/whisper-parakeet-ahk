package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
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
 * Event-driven sin polling:
 * - WiFi: broadcast WIFI_STATE_CHANGED
 * - Datos móviles: ContentObserver sobre el setting mobile_data (detecta
 *   el toggle directamente, aunque la red no llegue a conectar).
 */
object ConnectionExclusionManager {

    private const val TAG = "ConnExclusion"

    @Volatile private var lastWifiOn = false
    @Volatile private var lastMobileOn = false
    @Volatile private var started = false

    private var wifiReceiver: BroadcastReceiver? = null
    private var mobileObserver: ContentObserver? = null

    fun start(ctx: Context) {
        if (started) return
        started = true
        val appCtx = ctx.applicationContext

        // WiFi: broadcast (llega de forma fiable)
        wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                check(c)
            }
        }.also { r ->
            val filter = IntentFilter().apply {
                addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                appCtx.registerReceiver(r, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                appCtx.registerReceiver(r, filter)
            }
        }

        // Datos móviles: ContentObserver sobre mobile_data (detecta el toggle)
        mobileObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                check(appCtx)
            }
        }.also { obs ->
            appCtx.contentResolver.registerContentObserver(
                Settings.Global.getUriFor("mobile_data"), false, obs
            )
        }

        check(appCtx)
        Log.d(TAG, "ConnectionExclusion iniciado (broadcast + ContentObserver)")
    }

    fun stop(ctx: Context) {
        if (!started) return
        started = false
        wifiReceiver?.let { r ->
            try { ctx.applicationContext.unregisterReceiver(r) } catch (_: Throwable) {}
        }
        wifiReceiver = null
        mobileObserver?.let { obs ->
            try { ctx.contentResolver.unregisterContentObserver(obs) } catch (_: Throwable) {}
        }
        mobileObserver = null
        Log.d(TAG, "ConnectionExclusion detenido")
    }

    fun check(ctx: Context) {
        // Si el modo avión está activo, forzar WiFi y datos OFF
        if (isAirplaneModeOn(ctx)) {
            if (isWifiOn(ctx)) exec("svc", "wifi", "disable")
            if (isMobileDataOn(ctx)) exec("svc", "data", "disable")
            lastWifiOn = false
            lastMobileOn = false
            return
        }

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

    private fun isAirplaneModeOn(ctx: Context): Boolean = try {
        Settings.Global.getInt(ctx.contentResolver, "airplane_mode_on", 0) == 1
    } catch (t: Throwable) {
        false
    }
}