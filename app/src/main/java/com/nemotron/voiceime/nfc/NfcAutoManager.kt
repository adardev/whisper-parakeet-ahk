package com.nemotron.voiceime.nfc

import android.util.Log
import com.nemotron.voiceime.dhizuku.ShizukuManager

object NfcAutoManager {

    private const val TAG = "NfcAuto"
    private const val POLL_INTERVAL_MS = 1500L

    private val WALLET_PACKAGES = setOf(
        "com.google.android.apps.walletnfcrel"
    )

    private val WALLET_ACTIVITIES = setOf(
        "com.google.commerce.tapandpay.android.wallet.WalletActivity"
    )

    private var running = false
    private var nfcEnabledByUs = false
    private var pollThread: Thread? = null

    fun start() {
        if (running) return
        if (!ShizukuManager.hasPermission()) return
        running = true
        pollThread = Thread({ pollLoop() }, "nfc-auto-poll").apply {
            isDaemon = true
            start()
        }
        Log.d(TAG, "started")
    }

    fun stop() {
        running = false
        pollThread?.interrupt()
        pollThread = null
        Log.d(TAG, "stopped")
    }

    private fun pollLoop() {
        while (running && !Thread.currentThread().isInterrupted) {
            try {
                val output = ShizukuManager.execShellCapture("dumpsys activity activities | grep ResumedActivity")
                Log.d(TAG, "poll: ${output?.take(120)}")
                val fg = parseForeground(output)
                val pkg = fg?.substringBefore("/")
                val activity = fg?.substringAfter("/", "")
                val isWallet = (pkg != null && pkg in WALLET_PACKAGES) ||
                    (activity != null && activity in WALLET_ACTIVITIES)

                if (isWallet && !nfcEnabledByUs) {
                    Log.d(TAG, "Wallet detected ($fg), enabling NFC")
                    ShizukuManager.execShell("cmd nfc enable-nfc")
                    nfcEnabledByUs = true
                } else if (!isWallet && nfcEnabledByUs) {
                    Log.d(TAG, "Wallet left foreground, disabling NFC")
                    ShizukuManager.execShell("cmd nfc disable-nfc")
                    nfcEnabledByUs = false
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

    private fun parseForeground(output: String?): String? {
        if (output.isNullOrBlank()) return null
        val match = Regex("u0 ([^ }]+)").find(output)
        return match?.groupValues?.getOrNull(1)?.trim()
    }
}
