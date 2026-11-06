package com.nemotron.voiceime.car

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat

/** One Android Auto source that follows the currently playing Salt or YouTube Music session. */
class CarMediaBrowserService : MediaBrowserServiceCompat() {
    private lateinit var session: MediaSessionCompat
    private var lastBrowsePackage: String? = null
    private val refreshObserver: () -> Unit = { refreshMirroredSession() }

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, TAG).apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = dispatch { it.transportControls.play() }
                override fun onPause() = dispatch { it.transportControls.pause() }
                override fun onStop() = dispatch { it.transportControls.stop() }
                override fun onSkipToNext() = dispatch { it.transportControls.skipToNext() }
                override fun onSkipToPrevious() = dispatch { it.transportControls.skipToPrevious() }
                override fun onSeekTo(pos: Long) = dispatch { it.transportControls.seekTo(pos) }
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) =
                    dispatch { it.transportControls.play() }
                override fun onPlayFromSearch(query: String?, extras: Bundle?) =
                    dispatch { it.transportControls.play() }
            })
            isActive = true
        }
        sessionToken = session.sessionToken
        CarMusicSessionBridge.addObserver(refreshObserver)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) =
        BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        if (parentId != ROOT_ID) {
            result.sendResult(emptyList())
            return
        }
        result.sendResult(listOf(MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(CURRENT_ID)
                .setTitle("Continuar reproducción")
                .setSubtitle(" ")
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )))
    }

    override fun onDestroy() {
        CarMusicSessionBridge.removeObserver(refreshObserver)
        if (::session.isInitialized) {
            session.isActive = false
            session.release()
        }
        super.onDestroy()
    }

    private fun dispatch(action: (MediaController) -> Unit): Unit {
        val source = CarMusicSessionBridge.controller()
        if (source == null) {
            Log.w(TAG, "Ignored car media command: no active supported music session")
            return
        }
        runCatching { action(source) }
            .onFailure { Log.e(TAG, "Could not forward car control to active music app", it) }
    }

    private fun refreshMirroredSession() {
        if (!::session.isInitialized) return
        val source = CarMusicSessionBridge.controller()
        val sourceMetadata = source?.metadata
        val state = source?.playbackState
        val metadata = sourceMetadata?.let {
            MediaMetadataCompat.Builder(MediaMetadataCompat.fromMediaMetadata(it)).build()
        } ?: MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, " ")
            .build()
        // Keep the source artwork untouched so Android Auto can render the song cover.
        session.setMetadata(metadata)

        val actions = state?.actions ?: (PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
        val compatState = state?.let(PlaybackStateCompat::fromPlaybackState)
            ?: PlaybackStateCompat.Builder().setActions(actions)
                .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 0f).build()
        session.setPlaybackState(compatState)
        if (source?.packageName != lastBrowsePackage) {
            lastBrowsePackage = source?.packageName
            notifyChildrenChanged(ROOT_ID)
        }
        session.setQueue(listOf(MediaSessionCompat.QueueItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(CURRENT_ID)
                .setTitle(sourceMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: " ")
                .setSubtitle(sourceMetadata?.getString(MediaMetadata.METADATA_KEY_ARTIST))
                .build(), 1L
        )))
    }

    companion object {
        private const val TAG = "CarMediaBridge"
        private const val ROOT_ID = "nemotron_media_root"
        private const val CURRENT_ID = "active_music_session"
    }
}
