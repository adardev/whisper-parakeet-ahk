package com.nemotron.voiceime.car

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.util.concurrent.atomic.AtomicInteger

class CarMediaSessionListenerTest {
    private lateinit var service: CarMediaSessionListener
    private lateinit var manager: MediaSessionManager
    private lateinit var salt: MediaController
    private lateinit var youtube: MediaController

    private fun player(pkg: String, state: Int): MediaController {
        val controller = mock(MediaController::class.java)
        `when`(controller.packageName).thenReturn(pkg)
        `when`(controller.sessionToken).thenReturn(mock(MediaSession.Token::class.java))
        setState(controller, state)
        return controller
    }

    private fun setState(controller: MediaController, state: Int): PlaybackState {
        val playback = mock(PlaybackState::class.java)
        `when`(playback.state).thenReturn(state)
        `when`(controller.playbackState).thenReturn(playback)
        return playback
    }

    private fun callbacks(controller: MediaController): List<MediaController.Callback> {
        val captor = ArgumentCaptor.forClass(MediaController.Callback::class.java)
        verify(controller, atLeastOnce()).registerCallback(captor.capture())
        return captor.allValues
    }

    @Before fun connect() {
        CarMusicSessionBridge.setController(null)
        manager = mock(MediaSessionManager::class.java)
        salt = player("com.salt.music", PlaybackState.STATE_PLAYING)
        youtube = player("com.google.android.apps.youtube.music", PlaybackState.STATE_PAUSED)
        service = mock(CarMediaSessionListener::class.java, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS))
        doReturn(manager).`when`(service).getSystemService(MediaSessionManager::class.java)
        `when`(manager.getActiveSessions(any(ComponentName::class.java))).thenReturn(listOf(salt, youtube))
        service.onListenerConnected()
    }

    @After fun disconnect() { service.onDestroy(); CarMusicSessionBridge.setController(null) }

    @Test fun metadataStillReachesBridgeWithoutRequeryingAllSessions() {
        assertSame(salt, CarMusicSessionBridge.controller())
        val updates = AtomicInteger()
        val observer: () -> Unit = { updates.incrementAndGet(); Unit }
        CarMusicSessionBridge.addObserver(observer)
        try {
            val before = updates.get()
            callbacks(salt).forEach { it.onMetadataChanged(mock(MediaMetadata::class.java)) }
            assertEquals(before + 1, updates.get())
            verify(manager, times(1)).getActiveSessions(any(ComponentName::class.java))
            assertSame(salt, CarMusicSessionBridge.controller())
        } finally { CarMusicSessionBridge.removeObserver(observer) }
    }

    @Test fun playbackChangesStillSelectExactlyOneActiveDestination() {
        setState(salt, PlaybackState.STATE_PAUSED)
        val playing = setState(youtube, PlaybackState.STATE_PLAYING)
        // The first callback observes source selection, not metadata mirroring.
        callbacks(youtube).first().onPlaybackStateChanged(playing)
        assertSame(youtube, CarMusicSessionBridge.controller())
        verify(manager, times(2)).getActiveSessions(any(ComponentName::class.java))
        verify(salt).unregisterCallback(any(MediaController.Callback::class.java))
    }
}
