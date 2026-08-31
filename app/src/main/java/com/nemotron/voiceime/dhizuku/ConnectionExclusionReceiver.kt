package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log

/**
 * Siempre UNA conexión activa entre WiFi y Datos móviles.
 * - Enciendes WiFi → apaga datos móviles
 * - Apagas WiFi → enciende datos móviles
 * - Enciendes datos → apaga WiFi
 * - Apagas datos → enciende WiFi
 */
class ConnectionExclusionReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val wifiOn = isWifiOn(ctx)
        val mobileOn = isMobileDataOn(ctx)

        Log.d(TAG, "onReceive ${intent.action} → wifi=$wifiOn mobile=$mobileOn (prev wifi=$lastWifiOn mobile=$lastMobileOn)")

        when {
            // Ambos encendidos → apagar el que se acaba de encender
            wifiOn && mobileOn -> when {
                !lastWifiOn && lastMobileOn -> ShizukuManager.execShellFresh(arrayOf("svc", "data", "disable"))
                lastWifiOn && !lastMobileOn -> ShizukuManager.execShellFresh(arrayOf("svc", "wifi", "disable"))
                else -> ShizukuManager.execShellFresh(arrayOf("svc", "data", "disable"))
            }
            // Ambos apagados → encender el que se acaba de apagar
            !wifiOn && !mobileOn -> when {
                lastWifiOn && !lastMobileOn -> ShizukuManager.execShellFresh(arrayOf("svc", "data", "enable"))
                !lastWifiOn && lastMobileOn -> ShizukuManager.execShellFresh(arrayOf("svc", "wifi", "enable"))
                else -> ShizukuManager.execShellFresh(arrayOf("svc", "wifi", "enable"))
            }
        }

        lastWifiOn = wifiOn
        lastMobileOn = mobileOn
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

    companion object {
        private const val TAG = "ConnExclusion"
        @Volatile var lastWifiOn = false
        @Volatile var lastMobileOn = false
    }
}