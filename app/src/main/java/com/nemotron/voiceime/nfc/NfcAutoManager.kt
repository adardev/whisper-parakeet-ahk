package com.nemotron.voiceime.nfc

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.nemotron.voiceime.dhizuku.ShizukuManager

/**
 * Auto-activa NFC cuando Wallet está en primer plano.
 *
 * Mecanismo principal: [NfcAccessibilityService] (event-driven, cero CPU).
 * Fallback: polling ligero SOLO si la accesibilidad no está habilitada,
 * y SOLO con la pantalla encendida.
 */
object NfcAutoManager {

    private const val TAG = "NfcAuto"
    private const val POLL_INTERVAL_MS = 5000L

    private val WALLET_PACKAGES = setOf(
        "com.google.android.apps.walletnfcrel"
    )

    private val WALLET_ACTIVITIES = setOf(
        "com.google.commerce.tapandpay.android.wallet.WalletActivity"
    )

    private var running = false
    private var nfcEnabledByUs = false
    private var screenOn = true
    private var pollThread: Thread? = null
    private var screenReceiver: android.content.BroadcastReceiver? = null

    /** Inicia el manejo de NFC. [context] se usa para el receiver de pantalla. */
    fun start(context: Context) {
        if (running) return
        if (!ShizukuManager.hasPermission()) return
        running = true
        screenOn = isScreenOn(context)

        registerScreenReceiver(context)

        // Solo pollear si la accesibilidad (mecanismo principal) no está activa
        if (!isAccessibilityEnabled(context)) {
            pollThread = Thread({ pollLoop() }, "nfc-auto-poll").apply {
                isDaemon = true
                start()
            }
            Log.d(TAG, "fallback polling started (accessibility off)")
        } else {
            Log.d(TAG, "using accessibility service (no polling)")
        }
    }

    fun stop(context: Context) {
        running = false
        pollThread?.interrupt()
        pollThread = null
        screenReceiver?.let {
            try { context.applicationContext.unregisterReceiver(it) } catch (_: Throwable) {}
        }
        screenReceiver = null
        Log.d(TAG, "stopped")
    }

    /** Llamado por el AccessibilityService cuando cambia la ventana en primer plano. */
    fun onForegroundChanged(packageName: String) {
        val isWallet = packageName in WALLET_PACKAGES
        updateNfcState(isWallet)
    }

    private fun registerScreenReceiver(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        screenOn = true
                        Log.d(TAG, "screen ON")
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        // Al apagar la pantalla no se puede usar Wallet; si lo activamos, lo apagamos
                        if (nfcEnabledByUs) {
                            Log.d(TAG, "screen off, disabling NFC")
                            ShizukuManager.execShell("cmd nfc disable-nfc")
                            nfcEnabledByUs = false
                        }
                        Log.d(TAG, "screen OFF")
                    }
                }
            }
        }
        context.applicationContext.registerReceiver(receiver, filter)
        screenReceiver = receiver
    }

    private fun pollLoop() {
        while (running && !Thread.currentThread().isInterrupted) {
            try {
                if (screenOn) {
                    val output = ShizukuManager.execShellCapture("dumpsys activity top | grep ACTIVITY")
                    val fg = parseForegroundTop(output)
                    if (fg != null) {
                        updateNfcState(fg in WALLET_PACKAGES)
                    }
                }
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            } catch (t: Throwable) {
                Log.w(TAG, "poll error", t)
                Thread.sleep(3000)
            }
        }
    }

    private fun updateNfcState(isWallet: Boolean) {
        if (isWallet && !nfcEnabledByUs) {
            Log.d(TAG, "Wallet detected, enabling NFC")
            if (ShizukuManager.execShell("cmd nfc enable-nfc")) {
                nfcEnabledByUs = true
            }
        } else if (!isWallet && nfcEnabledByUs) {
            Log.d(TAG, "Wallet left foreground, disabling NFC")
            if (ShizukuManager.execShell("cmd nfc disable-nfc")) {
                nfcEnabledByUs = false
            }
        }
    }

    private fun parseForegroundTop(output: String?): String? {
        if (output.isNullOrBlank()) return null
        val match = Regex("ACTIVITY ([^ ]+)/").find(output)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(context.packageName + "/" + NfcAccessibilityService::class.java.name)
    }

    private fun isScreenOn(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isInteractive
    }
}
