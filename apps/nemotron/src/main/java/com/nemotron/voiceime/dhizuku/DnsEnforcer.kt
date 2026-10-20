package com.nemotron.voiceime.dhizuku

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

/** Keeps Android Private DNS on the configured hostname while the service is alive. */
object DnsEnforcer {
    private const val TAG = "DnsEnforcer"
    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val PRIVATE_DNS_MODE = "private_dns_mode"
    private const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
    private const val ENFORCE_COOLDOWN_MS = 1500L
    private val HOSTNAME = Regex("[a-z0-9](?:[a-z0-9.-]{0,253}[a-z0-9])?")
    private val lock = Any()
    private var observer: ContentObserver? = null
    private var lastEnforceAt = 0L

    fun enforce(context: Context): Boolean {
        if (!SecureStore.isDnsEnforcerEnabled(context) || !ShizukuManager.hasPermission()) return false
        val hostname = SecureStore.getDnsHostname(context)
        if (!HOSTNAME.matches(hostname)) {
            Log.w(TAG, "DNS hostname rejected: $hostname")
            return false
        }
        val mode = ShizukuManager.execShellCapture("settings get global private_dns_mode").orEmpty()
        val current = ShizukuManager.execShellCapture("settings get global private_dns_specifier").orEmpty()
        if (mode.trim() == "hostname" && current.trim() == hostname) return true

        val result = ShizukuManager.execShellCapture(
            "settings put global private_dns_mode hostname; " +
                "settings put global private_dns_specifier '$hostname'; " +
                "settings get global private_dns_mode; " +
                "settings get global private_dns_specifier"
        ).orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val changed = result.takeLast(2) == listOf("hostname", hostname)
        if (changed) {
            Log.i(TAG, "Private DNS restaurado a $hostname")
            ShizukuManager.stopApp(SETTINGS_PACKAGE)
        } else {
            Log.w(TAG, "No se pudo confirmar el DNS protegido: $result")
        }
        return changed
    }

    fun startMonitoring(context: Context) {
        if (!SecureStore.isDnsEnforcerEnabled(context)) return
        synchronized(lock) {
            if (observer != null) return
            val appContext = context.applicationContext
            val handler = Handler(Looper.getMainLooper())
            observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    synchronized(lock) {
                        if (now - lastEnforceAt < ENFORCE_COOLDOWN_MS) return
                        lastEnforceAt = now
                    }
                    Thread { enforce(appContext) }.start()
                }
            }.also { registered ->
                appContext.contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(PRIVATE_DNS_MODE), false, registered
                )
                appContext.contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(PRIVATE_DNS_SPECIFIER), false, registered
                )
            }
        }
        Thread { enforce(context) }.start()
    }

    fun stopMonitoring(context: Context) {
        synchronized(lock) {
            observer?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
            observer = null
        }
    }
}
