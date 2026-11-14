package com.nemotron.voiceime.dhizuku

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.nemotron.voiceime.R

/** Persistent Nemotron entry point for Google's built-in song recognition. */
class SongSearchTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(ACTION_MUSIC_SEARCH)
            .setPackage(GOOGLE_SEARCH_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (packageManager.resolveActivity(intent, 0) == null) {
            Toast.makeText(this, "Google app no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (error: Exception) {
            android.util.Log.e(TAG, "Could not open Google's music search", error)
            Toast.makeText(this, "No se pudo abrir Song Search", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        tile.label = LABEL
        tile.icon = Icon.createWithResource(this, R.drawable.ic_song_search_tile)
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private companion object {
        const val TAG = "SongSearchTile"
        const val LABEL = "Song Search"
        const val GOOGLE_SEARCH_PACKAGE = "com.google.android.googlequicksearchbox"
        const val ACTION_MUSIC_SEARCH = "com.google.android.googlequicksearchbox.MUSIC_SEARCH"
        const val REQUEST_CODE = 4092
    }
}
