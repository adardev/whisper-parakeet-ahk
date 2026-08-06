package com.nemotron.voiceime.dhizuku

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.nemotron.voiceime.R

/**
 * Tile para Google Wallet:
 * - Tap: abrir Wallet + encender NFC (si no está corriendo)
 * - Tap de nuevo: cerrar Wallet + apagar NFC
 * - Sin service en background: todo bajo demanda.
 */
class WalletTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private val walletPackage = "com.google.android.apps.walletnfcrel"
    private val walletActivity = "com.google.commerce.tapandpay.android.wallet.WalletActivity"

    override fun onStartListening() {
        super.onStartListening()
        handler.post { try { updateTileState() } catch (_: Throwable) {} }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        handler.post { try { updateTileState() } catch (_: Throwable) {} }
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "onClick: Wallet")

        val running = isWalletRunning()

        Thread {
            if (running) {
                // ON → OFF: cerrar Wallet + apagar NFC
                stopApp()
                disableNfc()
            } else {
                // OFF → ON: abrir Wallet + encender NFC
                enableNfc()
                launchWallet()
            }
            handler.post { try { updateTileState() } catch (_: Throwable) {} }
        }.start()
    }

    private fun launchWallet() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(walletPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                // Fallback: activity explícita
                val explicit = Intent().apply {
                    setClassName(walletPackage, walletActivity)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(explicit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch Wallet", e)
        }
    }

    private fun stopApp() {
        if (ShizukuManager.hasPermission()) {
            ShizukuManager.stopApp(walletPackage)
        } else {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(walletPackage)
        }
    }

    private fun enableNfc() {
        if (ShizukuManager.hasPermission()) {
            ShizukuManager.execShell("cmd nfc enable-nfc")
        }
    }

    private fun disableNfc() {
        if (ShizukuManager.hasPermission()) {
            ShizukuManager.execShell("cmd nfc disable-nfc")
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.label = "Wallet"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_wallet_tile)
        tile.state = if (isWalletRunning()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun isWalletRunning(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val runningProcesses = am.runningAppProcesses ?: return false
        return runningProcesses.any { it.processName.contains(walletPackage) }
    }

    companion object {
        private const val TAG = "WalletTileService"
    }
}
