package com.nemotron.voiceime.dhizuku

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.nemotron.voiceime.R

/** Orca tile: show Termux when enabled; disabling it hides and stops Termux. */
class TelegramTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.termux"
    override val targetPackages: List<String> = listOf(targetPackage)
    override val tileLabel: String = "Orca"
    override val tileIconRes: Int = R.drawable.ic_orca_tile

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Open Termux without typing or submitting anything into its terminal. */
    override fun onAfterUnfreeze() {
        val launch = RootShellManager.execShellFresh(
            arrayOf(
                "sh", "-c",
                "cmd statusbar collapse; exec am start -W -n $TERMUX_PACKAGE/$TERMUX_ACTIVITY"
            ),
            timeoutMs = 8_000L
        )
        if (launch == null || launch.contains("Error", ignoreCase = true) ||
            launch.contains("Exception", ignoreCase = true)) {
            Log.e(TAG, "No se pudo abrir Termux desde Orca: ${launch.orEmpty()}")
            showError("No se pudo abrir Termux")
            return
        }
        Log.i(TAG, "Termux abierto sin escribir ni ejecutar comandos")
    }

    private fun showError(message: String) {
        mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_ACTIVITY = "com.termux.app.TermuxActivity"
        const val TAG = "OrcaTile"
    }
}
