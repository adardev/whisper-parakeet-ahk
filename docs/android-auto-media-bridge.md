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
did not remove **Drive now** from Android Auto. Waze exposes a separate in-car
setting at **Waze on car screen > Settings > Privacy > Personalisation > Trip
suggestions > Show trip suggestions**. Waze's [official instructions](https://support.google.com/waze/answer/9747181?hl=en)
note that planned drives and calendar events can remain visible even with trip
suggestions disabled.

Root inspection found Waze's persisted car preference at
`config_values(preferences, CarPlay, Trip Suggestions Enabled)` in
`waze_config.db`, mirrored as `CarPlay.Trip Suggestions Enabled` in Waze's
native preferences file. Both initially read `1`. Before the experiment, the
database, WAL, SHM, and preferences file were backed up under `/data/local/tmp/`
with the `before-drive-now-20261122` suffix. Setting the two local copies to
`0` did not remove **Drive now**. On the next Android Auto connection Waze
reloaded the database value to `1`; the text preference was restored to `1` as
well. The experiment was rolled back and is not an active fix. The local
database passed `PRAGMA integrity_check` during the check. The phone-side
**Back** key also did not dismiss the head-unit panel.

The separate in-car **Trip suggestions** setting remains the correct next
step, but has not yet been turned off or verified. The car projection uses a
private/secure virtual display; ADB captures it as black and cannot inspect its
controls or reliably inject taps. Do not disable Waze's car service as a
workaround, since that would remove navigation rather than only dismissing the
panel.

### Waze Android Auto action strip and route preview patch (22 Nov 2026)

The Waze Android Auto Car App source was rebuilt from the installed Waze
5.27.0.1 base APK (versionCode 1030748; source SHA-256
`3ea14a2669439b240bb59aaa28b7a8557829d125cf103ebde196bc5606c0aefc`). The
Zygote-private candidate is SHA-256
`e653e1066c349bff72194fa40f328cc3c60fd7316df6be58788276fedfae1e9d` and was
verified with `apksigner` v3. It is not installed through Package Manager, so
the phone APK, Package Manager signature state, and phone-side Waze interface
remain stock.

The Android Auto map action strip no longer publishes the dynamic Home/Work
shortcut, Saved Places/bookmark, or Search action. Settings is retained, as is
any independent host-provided action. Waze's separate destination suggestions
also omit Work. The route alternatives screen now invokes the existing **Go
now** callback once when route results are ready, using Waze's already selected
default/best route; this bypasses the extra route-preview step for any
destination, rather than special-casing Work.

The candidate, expected hash, updated controller, and persistent
`service.d` boot hook are staged under `/data/adb`. The hook enables the private
Zygote mount after boot without changing Package Manager's view. At staging
time the Zygote and installed base APK still both report the original stock
hash, and Waze is running, so this build has **not yet been activated or
verified on the car display**. Activation force-stops Waze so its next
Android Auto service process inherits the mount; do that only while parked and
with no active navigation. No reboot was performed.

To inspect the staged/active state:

```sh
su -c 'sh /data/adb/modules/nemotron-session-patches/waze-control.sh status'
```

Once parked, activate with:

```sh
su -c 'sh /data/adb/modules/nemotron-session-patches/waze-control.sh enable'
```

Rollback to stock and restart Waze with:

```sh
su -c 'sh /data/adb/modules/nemotron-session-patches/waze-control.sh disable'
```

The build and staging scripts are in `tools/kernelsu/waze-aa-patch/`. The
proprietary Waze APK candidate is device-local and is intentionally not tracked
in Git.
