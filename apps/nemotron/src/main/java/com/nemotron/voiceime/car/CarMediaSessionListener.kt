package com.nemotron.voiceime.car

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/** Watches only Salt Player and YouTube Music media sessions; no polling. */
class CarMediaSessionListener : NotificationListenerService() {
    private lateinit var sessionManager: MediaSessionManager
    private val observedSessions = mutableMapOf<android.media.session.MediaSession.Token, ObservedSession>()
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        selectMusicSession(sessions.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Media notification listener connected")
        sessionManager = getSystemService(MediaSessionManager::class.java)
        runCatching {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsChanged,
                ComponentName(this, CarMediaSessionListener::class.java)
            )
        }.onFailure { Log.e(TAG, "Could not observe media-session changes", it) }
        refreshMusicSession()
    }

    override fun onListenerDisconnected() {
        clearObservedSessions()
        if (::sessionManager.isInitialized) {
            runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged) }
        }
        CarMusicSessionBridge.setController(null)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) {
        if (sbn.packageName in MUSIC_PACKAGES) refreshMusicSession()
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification) {
        if (sbn.packageName in MUSIC_PACKAGES) refreshMusicSession()
    }

    override fun onDestroy() {
        clearObservedSessions()
        if (::sessionManager.isInitialized) {
            runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged) }
        }
        CarMusicSessionBridge.setController(null)
        super.onDestroy()
    }

    private fun refreshMusicSession() {
        if (!::sessionManager.isInitialized) return
        try {
            val listener = ComponentName(this, CarMediaSessionListener::class.java)
            selectMusicSession(sessionManager.getActiveSessions(listener))
        } catch (e: SecurityException) {
            Log.e(TAG, "Media session access not granted", e)
            CarMusicSessionBridge.setController(null)
        }
    }

    private fun selectMusicSession(sessions: List<MediaController>) {
        val supported = sessions.filter { it.packageName in MUSIC_PACKAGES }
        updateObservedSessions(supported)
        val playing = supported.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        val current = CarMusicSessionBridge.controller()?.let { active ->
            supported.firstOrNull { it.sessionToken == active.sessionToken }
        }
        // Android can report an old player session in STOPPED state ahead of
        // the player the user most recently paused. Picking the first session
        // made Android Auto show (for example) stale YouTube Music metadata and
        // When both apps are paused, the previous source can remain paused and
        // keep winning merely because it is current. Use the most recently
        // updated paused/buffering session to follow the app the user just
        // switched to; retain current for timestamp ties or stopped-only
        // sessions so Play can still resume a sole source.
        val pausedOrBuffering = supported.filter {
            it.playbackState?.state == PlaybackState.STATE_PAUSED ||
                it.playbackState?.state == PlaybackState.STATE_BUFFERING
        }
        val mostRecentPaused = pausedOrBuffering.maxWithOrNull(
            compareBy<MediaController> { it.playbackState?.lastPositionUpdateTime ?: Long.MIN_VALUE }
                .thenBy { it.sessionToken == current?.sessionToken }
        )
        val selected = playing
            ?: mostRecentPaused
            ?: current
            ?: supported.firstOrNull()
        val changed = CarMusicSessionBridge.controller()?.sessionToken != selected?.sessionToken
        CarMusicSessionBridge.setController(selected)
        if (changed) {
            Log.i(TAG, "Selected Android Auto media source: ${selected?.packageName ?: "none"} " +
                "state=${selected?.playbackState?.state ?: "none"}")
        }
    }

    @Synchronized
    private fun updateObservedSessions(sessions: List<MediaController>) {
        val activeTokens = sessions.map { it.sessionToken }.toSet()
        val removed = observedSessions.keys.filter { it !in activeTokens }
        removed.forEach { token ->
            observedSessions.remove(token)?.let { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }
            }
        }
        sessions.forEach { controller ->
            val token = controller.sessionToken
            if (token in observedSessions) return@forEach
            var previousState = controller.playbackState?.state
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    val stateChanged = synchronized(this) {
                        val changed = previousState != state?.state
                        previousState = state?.state
                        changed
                    }
                    if (stateChanged) refreshMusicSession()
                }

                // Reopening a paused app or changing its queue can make it the
                // user's current source without changing PLAYING/PAUSED state.
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                    refreshMusicSession()
                }
            }
            runCatching {
                controller.registerCallback(callback)
                observedSessions[token] = ObservedSession(controller, callback)
            }.onFailure { Log.w(TAG, "Could not observe ${controller.packageName} session", it) }
        }
    }

    @Synchronized
    private fun clearObservedSessions() {
        observedSessions.values.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        observedSessions.clear()
    }

    private data class ObservedSession(
        val controller: MediaController,
        val callback: MediaController.Callback
    )

    companion object {
        private const val TAG = "CarMediaBridge"
        private const val SALT_PACKAGE = "com.salt.music"
        private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
        private val MUSIC_PACKAGES = setOf(SALT_PACKAGE, YOUTUBE_MUSIC_PACKAGE)
    }
}

/** Single active music source for Android Auto; never broadcasts to both apps. */
internal object CarMusicSessionBridge {
    private val observers = CopyOnWriteArrayList<() -> Unit>()
    @Volatile private var controller: MediaController? = null
    private var callback: MediaController.Callback? = null
    private var lastState: Int? = null
    private var lastActions: Long? = null
    private var lastSpeed: Float? = null

    fun controller(): MediaController? = controller

    fun addObserver(observer: () -> Unit) {
        observers += observer
        observer()
    }

    fun removeObserver(observer: () -> Unit) {
        observers -= observer
    }

    @Synchronized fun setController(next: MediaController?) {
        val previous = controller
        if (previous?.sessionToken == next?.sessionToken) return
        callback?.let { runCatching { previous?.unregisterCallback(it) } }
        controller = next
        lastState = null
        lastActions = null
        lastSpeed = null
        callback = next?.let { current ->
            object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    val changed = synchronized(this@CarMusicSessionBridge) {
                        val meaningful = lastState != state?.state ||
                            lastActions != state?.actions || lastSpeed != state?.playbackSpeed
                        lastState = state?.state
                        lastActions = state?.actions
                        lastSpeed = state?.playbackSpeed
                        meaningful
                    }
                    if (changed) notifyObservers()
                }

                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = notifyObservers()

                override fun onSessionDestroyed() {
                    setController(null)
                }
            }.also { current.registerCallback(it) }
        }
        notifyObservers()
    }

    private fun notifyObservers() {
        observers.forEach { observer -> runCatching(observer) }
    }
}
