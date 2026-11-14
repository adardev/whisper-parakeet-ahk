# Nemotron Song Search tile

Nemotron Core now provides its own **Song Search** Quick Settings tile. It is
independent of Google's Quick Settings tile, so it stays in the panel when the
Google app's process exits. Tapping it sends Google's supported
`com.google.android.googlequicksearchbox.MUSIC_SEARCH` action directly to
`com.google.android.googlequicksearchbox`; Android resolves this on the A54 to
`.MusicSearchGatewayInternal`, which starts Google's song-recognition flow.

Nemotron does not record audio itself or implement a separate recognition
backend. Google handles the microphone session and recognition, so the Google
app must remain installed and enabled and its microphone permission/service
must work. If Android has disabled or hidden the Google package, the tile shows
“Google app no disponible”.

The tile is declared by
`apps/nemotron/src/main/java/com/nemotron/voiceime/dhizuku/SongSearchTileService.kt`
and its icon by `apps/nemotron/src/main/res/drawable/ic_song_search_tile.xml`.
The Android 14+ path uses an immutable activity `PendingIntent` with
`startActivityAndCollapse`; older supported Android versions use the
`Intent` overload. Its non-toggle state stays inactive after the action.

On November 14, 2026, the Core APK was built as version 1.6 (`versionCode` 11)
and installed on the Galaxy A54. The tile was added to Quick Settings. Device
package-manager checks confirmed that Nemotron advertises the QS service and
that Google's `MUSIC_SEARCH` intent resolves. Do not invoke the action in an
automated smoke test because it starts microphone-based song recognition; test
it by tapping the tile when ready to listen.
