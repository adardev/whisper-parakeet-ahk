package com.nemotron.voiceime.dhizuku

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import com.nemotron.voiceime.R

/** Tile para congelar/descongelar Galaxy Wearable. */
class WatchManagerTileService : AppFreezeTileService() {
    override val targetPackage: String = "com.samsung.android.app.watchmanager"
    override val tileLabel: String = "Galaxy Wearable"
    override val tileIconRes: Int = 0

    /** Clear Package Manager's force-stop bit and turn Bluetooth on for reconnect. */
    override fun onAfterUnfreeze() {
        val pkg = targetPackage
        if (!ShizukuManager.unstopApp(pkg)) {
            android.util.Log.e("WatchManagerTile", "No se pudo quitar force-stop de $pkg")
        }
        val bluetooth = ShizukuManager.execShellFresh(
            arrayOf("settings", "get", "global", "bluetooth_on"), timeoutMs = 2_000L
        )?.trim()
        if (bluetooth == "0") {
            ShizukuManager.execShellFresh(
                arrayOf("cmd", "bluetooth_manager", "enable"), timeoutMs = 3_000L
            )
        }
    }

    override fun createTileIcon(): Icon {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size * 0.36f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val textWidth = paint.measureText("Wear")
        val x = size / 2f
        val y = size * 0.65f
        canvas.drawText("Wear", x, y, paint)
        return Icon.createWithBitmap(bitmap)
    }
}
