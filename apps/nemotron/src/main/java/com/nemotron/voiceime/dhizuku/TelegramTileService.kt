package com.nemotron.voiceime.dhizuku

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.nemotron.voiceime.R

/** Tile Orca: al activarlo abre Termux, escribe SSH y pulsa Enter; al apagarlo congela Termux. */
class TelegramTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.termux"
    override val targetPackages: List<String> = listOf(targetPackage)
    override val tileLabel: String = "Orca"
    override val tileIconRes: Int = R.drawable.ic_orca_tile

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Open Termux and type the SSH command through Shizuku's shell input events. */
    override fun onAfterUnfreeze() {
        // Quick Settings can stay above the launched activity when the tile is
        // tapped. Collapse it first so input events go to Termux, not the shade.
        ShizukuManager.execShellFresh(arrayOf("cmd", "statusbar", "collapse"))
        try {
            Thread.sleep(300L)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            showError("Se interrumpió la apertura de Termux")
            return
        }

        val launch = ShizukuManager.execShellFresh(
            arrayOf("am", "start", "-W", "-n", "$TERMUX_PACKAGE/$TERMUX_ACTIVITY")
        )
        if (launch == null || launch.contains("Error", ignoreCase = true)) {
            Log.e(TAG, "No se pudo abrir Termux: ${launch.orEmpty()}")
            showError("No se pudo abrir Termux")
            return
        }

        try {
            // Wait for both the activity focus and Termux's terminal input view.
            var focused = false
            for (attempt in 0 until 12) {
                val currentFocus = ShizukuManager.execShellFresh(
                    arrayOf("sh", "-c", "dumpsys window | grep -m 1 mCurrentFocus")
                ).orEmpty()
                if (currentFocus.contains("com.termux", ignoreCase = true)) {
                    focused = true
                    break
                }
                Thread.sleep(250L)
            }
            if (!focused) {
                Log.e(TAG, "Termux no obtuvo el foco: ActivityManager=$launch")
                showError("Termux abrió, pero no quedó al frente")
                return
            }
            Thread.sleep(1_000L)

            val typed = ShizukuManager.execShellFresh(
                arrayOf("input", "text", SSH_COMMAND_FOR_INPUT)
            )
            if (typed == null || typed.contains("Error", ignoreCase = true)) {
                Log.e(TAG, "No se pudo escribir el comando SSH en Termux: ${typed.orEmpty()}")
                showError("No se pudo escribir el comando en Termux")
                return
            }

            Thread.sleep(1_250L)
            val entered = ShizukuManager.execShellFresh(arrayOf("input", "keyevent", "66"))
            if (entered == null) {
                Log.e(TAG, "No se pudo pulsar Enter en Termux")
                showError("No se pudo ejecutar el comando en Termux")
                return
            }
            Log.i(TAG, "Termux abierto; comando SSH escrito y Enter enviado")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.e(TAG, "Se interrumpió el envío del comando Orca", error)
            showError("Se interrumpió el comando de Orca")
        }
    }

    private fun showError(message: String) {
        mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_ACTIVITY = "com.termux.app.TermuxActivity"
        const val SSH_COMMAND_FOR_INPUT = "ssh%s-J%sserveo.net%sadaredu@adardev-orca-20261020-f9c7e2"
        const val TAG = "OrcaTile"
    }
}
