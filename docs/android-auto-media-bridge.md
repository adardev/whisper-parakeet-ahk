# Nemotron Android Auto media bridge

Nemotron exposes one Android Auto media source through `CarMediaBrowserService`.
The source mirrors whichever supported app is currently playing: Salt Player or
YouTube Music. Its single media session forwards play, pause, stop, seek, next,
and previous to only that selected app's existing Android media session; neither
player APK is changed.

`CarMediaSessionListener` obtains active sessions through Android's notification
listener permission. It watches media-session state changes instead of polling,
and ignores position-only updates to avoid repeatedly rebuilding the car session.
The Android Auto tile enables this listener when turned on and disables it when
turned off. Its Android Auto service label is a single blank space and its service
icon is a fully transparent PNG. The bridge removes only the source display icon
from mirrored metadata, while preserving track data and album art.

## Validation

- Android Auto's media-browser service query finds
  `com.nemotron.core/com.nemotron.voiceime.car.CarMediaBrowserService`.
- The A54's notification-listener settings include the bridge listener.
- With Salt playing, Android reports the `CarMediaBridge` session with Salt's
  current title, artist, playback state, and transport actions.
- The Salt-only bridge build was installed and appeared in Android's active media
  sessions. The newer build that switches between Salt and YouTube Music and uses
  the transparent service icon compiled and installed successfully. Android package discovery
  finds the service and the listener is enabled on the A54.
- The final head-unit check is still pending: at install verification time the car
  projection was not active, so the new source could not be selected from the car.

Android Auto may cache its available media sources. After installation, refresh
the media source list from Android Auto while parked if Nemotron does not appear.

## A54 fixes (21 Nov 2026)

The tile setup now reports media readiness only when both
`cmd notification allow_listener` commands succeed and Android confirms both
listeners enabled. Previously a successful root shell alone was treated as
success, even when notification access was missing.

The mirrored session now advertises the play, pause, stop, play-from-media-ID,
and play-from-search actions implemented by the bridge callbacks, in addition
to the selected player's existing seek/next/previous actions. It preserves the
source playback state, position, speed, and buffered position. This lets Android
Auto render controls that match the commands the proxy can forward.

The corrected Nemotron APK compiled and installed on the A54. An isolated
service check confirmed `CarMediaBridge` starts without crashing and publishes
its stopped session with the expected action set. No media was started. The
head-unit transport controls still need an in-car check with the Android Auto
tile on and a supported player active.

Google Maps remains installed, enabled, and launchable on the phone. Only its
Android Auto projection service,
`com.google.android.apps.maps/com.google.android.apps.gmm.car.GmmCarProjectionService`,
is disabled through Package Manager. This removes its normal Android Auto
navigation projection without disabling or uninstalling the phone app. Waze was
already checked in Android Auto's Customize launcher list. The Android Auto
package was restored to its prior hidden/stopped state after inspecting that
list. A car-display check is still needed to confirm Waze becomes the selected
navigation app there.

## A54 media routing and Waze navigation (22 Nov 2026)

The selector now prefers a session that is actually playing. If neither
supported player is playing, it chooses the most recently updated paused or
buffering session, then keeps the current session or falls back to the first
supported session. Android can list an old stopped YouTube Music session before
paused Salt Player. Selecting the first supported session sent the car's
next/previous commands to the wrong player and mirrored stale metadata. The
listener now also re-evaluates the source when session metadata changes, so
switching between paused apps can update the car source.

The browser service logs each forwarded transport command and its destination
package, which makes future routing issues diagnosable from logcat without
guessing which player received a wheel-button event. The APK compiled and was
installed successfully. The owner then confirmed on the connected car that
Next skips the current Salt Player track when Salt is selected, and skips the
YouTube Music track after switching to YouTube Music. No player APK or media
library is modified.

Maps' main, limited, and widescreen Android Auto projection components were
disabled because the main component had become enabled again since the earlier
check. The phone's Maps package remains enabled. A Package Manager query for
the Android Auto navigation category now returns Waze's
`FreeMapAppActivity`/`WazeCarAppService`; the Maps app is no longer a matching
navigation projection. The Android Auto package and its media listener were
returned to their prior hidden/stopped and disabled states after this check.

Android Auto remembers the selected navigation app from the car display; the
phone has no active Android `MAP`/`NAVIGATION` default-app role to set. Select
Waze once from the car's Android Auto launcher/navigation picker to make it the
last-used navigation app. The head unit was not connected during this update,
so that final on-car selection and the steering-wheel button press still need
physical verification. Google Maps remains available for normal phone use.

### Follow-up verification while connected

The owner confirmed that the car's Next control now skips the active track in
Salt Player, then skips the active track in YouTube Music after switching to
that player. This supersedes the earlier pending transport-control check.

The phone-side Waze **Show trip suggestions** switch was turned off, but that
did not remove **Drive now** from Android Auto. Waze has a separate in-car
setting: **Waze on car screen > Settings > Privacy > Personalisation > Trip
suggestions > Show trip suggestions**. The in-car toggle still needs to be
changed and checked after a fresh Android Auto connection. Waze's [official
instructions](https://support.google.com/waze/answer/9747181?hl=en) note that
planned drives and calendar events can remain visible even with trip
suggestions disabled.

On the fresh connection, Android reports Waze's `WazeCarAppService` bound to
Android Auto. The car projection uses a private/secure virtual display; ADB
captures it as black and cannot inspect its controls or reliably inject taps.
The in-car setting therefore remains pending direct verification on the head
unit. Do not patch or disable Waze's car service as a workaround, since that
would remove navigation rather than only dismissing this panel.
