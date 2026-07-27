package com.nemotron.voiceime.dhizuku

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.nemotron.voiceime.data.SecureStore

class HailTileService : TileService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        handler.post { try { updateTileState() } catch (_: Throwable) {} }
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "onClick")

        if (!ShizukuManager.hasPermission()) {
            Log.w(TAG, "Shizuku permission not granted")
            return
        }

        val apps = SecureStore.getFrozenApps(this).toList()
        if (apps.isEmpty()) {
            Log.d(TAG, "No apps configured, opening AppPicker")
            val intent = Intent(this, AppPickerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
            return
        }

        val currentlyFrozen = apps.all { ShizukuManager.isAppHidden(it) }
        Log.d(TAG, "Currently frozen: $currentlyFrozen")

        Thread {
            for (pkg in apps) {
                if (currentlyFrozen) {
                    ShizukuManager.unhideApp(pkg)
                } else {
                    ShizukuManager.hideApp(pkg)
                }
            }
            handler.post { try { updateTileState() } catch (_: Throwable) {} }
        }.start()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val apps = SecureStore.getFrozenApps(this).toList()

        if (apps.isEmpty()) {
            tile.label = "Freeze"
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = "Tap to configure"
            tile.updateTile()
            return
        }

        val frozen = apps.all { ShizukuManager.isAppHidden(it) }
        tile.state = if (frozen) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Freeze"
        tile.subtitle = if (frozen) "Frozen (${apps.size})" else "Active (${apps.size})"
        tile.updateTile()
    }

    companion object {
        private const val TAG = "HailTileService"
    }
}
