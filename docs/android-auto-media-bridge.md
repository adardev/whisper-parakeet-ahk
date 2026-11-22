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
`314b2b99f1bfa0c240a7c638fa45e53763fd26258cc3a8df808db32498d35204` and was
verified with `apksigner` v3. It is not installed through Package Manager, so
the phone APK, Package Manager signature state, and phone-side Waze interface
remain stock.

The Android Auto map action strip no longer publishes the dynamic Home/Work
shortcut or Saved Places/bookmark. Search and Settings are retained, as is any
independent host-provided action. Waze's separate destination suggestions also
omit Work. In the initial start state, the screen now emits a map-only template
whenever the Start State model is present or loading; this keeps the Drive now
destination panel from being restored by later loading/data updates while
leaving the map action strip available.

For route alternatives, the screen invokes the existing **Go now** callback
once when Waze enters the selected-route state. It suppresses every subsequent
render of that route-preview screen instance as well, so a later model update
cannot put the preview back. This uses Waze's selected default/best route for
any destination rather than special-casing Work.

The candidate, expected hash, and updated controller were staged under
`/data/adb`. Activation was initially configured at boot, but has since been
removed during recovery after template validation crashes. The installed base
APK and its signature remain stock. The owner reported that an earlier
iteration briefly hid Drive now but a later update restored it; the current
candidate's panel and route-preview behavior have not been validated on the
car display.

The initial map-only versions caused successive Android Auto template errors:
the first omitted the required Pane/ItemList; the Pane workaround then failed
on an empty row title; and the MapTemplate attempt was rejected as a nested
content template. The current candidate keeps Waze's supported parent
MapWithContentTemplate and supplies a ListTemplate with a single-space row,
omitting the Drive now header and destinations while retaining the map action
strip. The candidate has SHA-256
`314b2b99f1bfa0c240a7c638fa45e53763fd26258cc3a8df808db32498d35204`; it was
not launched in a fresh Android Auto session and its rendering remains
unverified.

To inspect the staged/active state:

```sh
su -c 'sh /data/adb/modules/nemotron-session-patches/waze-control.sh status'
```

If the mount needs to be manually reapplied later (this restarts Waze):

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

### Recovery after Android Auto black screen (22 Nov 2026)

The A54's crash buffer showed repeated `com.waze` Car App template validation
failures from the v3-v5 iterations: the `MapTemplate.Builder` rejected a
simultaneous Pane and ItemList, the Pane workaround rejected an empty row
title, and the nested MapTemplate was rejected by
`ContentTemplateConstraints`. Those failures explain the black Waze surface
and Samsung's app-error dialog. The current device session was restored by
unmounting the candidate and verifying both the installed Waze base APK and
the Zygote-visible APK hash equal the stock hash
`3ea14a2669439b240bb59aaa28b7a8557829d125cf103ebde196bc5606c0aefc`.

Because v6 had not yet been opened by the Android Auto host, its rendering was
unknown. I removed Waze auto-activation from the KernelSU boot hook and from
the candidate installer. Other boot patches are left intact. Waze remains
stock across reboot; a future candidate can be staged without activation and
enabled manually only for a parked-car test. This recovery does not claim the
Drive now removal or route-preview bypass is working.

### Toolbar-only candidate (22 Nov 2026)

Following that recovery, the user clarified that the remaining requested
change is limited to two map-toolbar actions: remove the dynamic Home/Work
shortcut and Saved Places, while keeping Search and Settings. A new candidate
was built from the stock Waze base with only the ActionStrip builder changed;
it does not alter CarApp templates, Drive now/start state, suggestions, route
preview behavior, or phone-side Waze. Candidate SHA-256:
`c6a1d6a543f2819851f5097df533113aa9d1ebb5592512623212a7a86cb48c5b`.

The APK passed Apktool build, zipalign, and `apksigner` v3 verification. It is
staged in KernelSU storage and was temporarily mounted in Zygote at 13:26 CST;
the installed Waze APK remains stock. A later physical car-display photo
confirmed Search and Settings remained while the Home/Work and Saved Places
actions were absent, but also showed that Drive now still appeared. Waze
candidate auto-activation remains disabled at boot. The dedicated builder is
`tools/kernelsu/waze-aa-patch/build-waze-aa-toolbar-patch.ps1`.

### Fullscreen startup map candidate (22 Nov 2026)

The user clarified that the startup suggestions pane must be absent completely,
so a loading list is not an acceptable replacement. The v3 candidate checks
for Waze's start-state suggestion model and emits a top-level
`NavigationTemplate` for that state. It preserves Waze's toolbar ActionStrip,
map-control ActionStrip, and pan-mode listener, while bypassing the
`MapWithContentTemplate` that creates the left pane. Other map states,
navigation, and route previews retain Waze's original template path. This
uses the NavigationTemplate that Waze already constructs elsewhere in its Car
App implementation; Android's Car App Library defines it as a map-first
navigation template with action strips.

Candidate SHA-256:
`ec1ce014bf61be3410c24d9d89fd3ff05bf9807c9b5`. It passed Apktool, zipalign,
and `apksigner` v3 verification. With the car confirmed parked, I unmounted
toolbar-only v1, verified the stock hash, and mounted v3. Zygote now reports
the v3 hash, Waze has restarted (PID observed), and no new crash is present in
the crash buffer. Auto-activation at boot remains off. A visual check of the
head-unit map and centered location marker is still pending.

### Waze patch: consolidated experiment log and current candidate (22 Nov 2026)

This is the authoritative summary of the Waze Android Auto changes. It supersedes earlier statements in this document that described v6 as the current or successful map-only candidate. Do not use the experimental `build-waze-aa-patch.ps1` to rebuild the final APK; it contains the abandoned template and route experiments. The maintained candidate builder is `build-waze-aa-toolbar-patch.ps1`.

#### Requested behavior

1. Keep Waze as the default Android Auto navigation app.
2. Remove Home/Work and Saved Places from the map toolbar, retaining Search and Settings.
3. Omit the entire **Drive now** startup pane. The map should occupy the available view without the pane shifting the location marker.
4. After selecting a destination, start Waze's currently recommended route immediately. Do not show the route alternatives / **Go now** step.
5. Leave Waze installed normally. No Package Manager install, uninstall, disable, data clear, or signature replacement is part of this patch.

#### Base and patch mechanism

- Device: Samsung SM-A546E (A54), Waze 5.27.0.1, versionCode 1030748.
- Installed base APK SHA-256: `3ea14a2669439b240bb59aaa28b7a8557829d125cf103ebde196bc5606c0aefc`.
- Candidates are privately bind-mounted over Waze's `base.apk` inside Zygote's mount namespace by KernelSU. The installed APK/signature and phone-side Waze remain stock.
- Active final candidate as of 22 Nov, 13:53 CST: SHA-256 `a9cfb44509e275e85ed344c387176607a6da370da494b35b3435eaeca80fb760`.
- The patch is currently session-mounted only. Waze autostart was deliberately removed from the boot hook after the crashes below. Do not claim v4 survives reboot.

#### Failed approaches and exact errors

1. **Waze trip-suggestion preference/database edit:** set `Trip Suggestions Enabled` to 0 in its local DB and preference file. It did not remove Drive now. After Android Auto reconnected Waze restored the value to 1. The experiment was rolled back; DB/WAL/SHM and preference backups were retained under `/data/local/tmp/` with the `before-drive-now-20261122` suffix.
2. **MapTemplate with Pane and ItemList both set:** Android Auto crashed Waze with `IllegalStateException: Either Pane or Item List must be set but not both` from `MapTemplate.Builder.build`.
3. **Pane with blank title:** crashed with `IllegalArgumentException: The title cannot be null or empty`.
4. **MapTemplate nested inside MapWithContentTemplate:** crashed with `IllegalArgumentException: MapTemplate is not allowed as content within the parent template`, thrown by `ContentTemplateConstraints`.
5. **Single-space list-row candidate v6:** built and staged, but was not opened by Android Auto. It was unverified and is not the final approach.
6. **Loading ListTemplate candidate v2:** would still occupy a content-pane area, so it could not meet the user's requirement to remove the component and preserve the map's full view. It was superseded before activation.

The repeated template validation crashes left an Android Auto black surface and Samsung's app-error dialog. They were actual Waze crashes, not harmless logs.

#### Recovery and proven toolbar change

After the crashes, the active mount was removed and the installed and Zygote-visible hashes were both verified as stock (`3ea14a…c0aefc`). Waze auto-activation was removed from the KernelSU boot hook and from the Waze installer. Other KernelSU patches were left intact. That prevents an unverified Waze candidate from being re-mounted automatically at boot.

Toolbar-only candidate v1, SHA-256 `c6a1d6a543f2819851f5097df533113aa9d1ebb5592512623212a7a86cb48c5b`, changed only Waze's map ActionStrip builder. It omitted the dynamic Home/Work action and skipped Saved Places; Search and Settings stayed. A physical car-screen photo confirmed those two buttons were gone. The photo also showed Drive now remained, proving toolbar removal alone was insufficient. V1 was then replaced by the fullscreen candidate.

#### Fullscreen map change that removed the startup pane

Fullscreen candidate v3, SHA-256 `ec1ce014bf61be3410c24d9d89fd3ff05bf9807c9b5e3ed12b54b104059055b5`, branches only when Waze's initial suggestion model is present. It emits Waze's top-level `NavigationTemplate` instead of the `MapWithContentTemplate` that creates the left pane. It passes through Waze's existing toolbar ActionStrip, map-control ActionStrip, and PanModeListener. Other map content, search, route preview, and active navigation follow the stock code path. The build passed Apktool, zipalign, and `apksigner` v3 checks. The user reported the fullscreen map/layout was working and asked to add only the remaining route-start behavior.

This avoids the earlier invalid nested MapTemplate. The Android Car App Library documents NavigationTemplate as a map-oriented top-level template and lists its action-strip APIs: [NavigationTemplate.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/NavigationTemplate.Builder). MapWithContentTemplate instead requires supported content such as List, Pane, Grid, or Message: [MapWithContentTemplate.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/MapWithContentTemplate.Builder).

#### Direct route-start change (current v4)

Candidate v4 combines the proven fullscreen startup map and toolbar changes with one isolated route-preview callback change. In Waze's route-preview screen, when the state is the selected-route model (`com.waze.car_lib.r.au`), it invokes the same `onStartNavigation` callback that backs Waze's **Go now** action, then returns without publishing the route-preview screen. A per-screen boolean guard allows the callback to fire once despite template refreshes. The callback starts Waze's currently selected/recommended route; the patch does not choose a hard-coded route or destination. Other search/map/navigation states are not changed.

- v4 candidate SHA-256: `a9cfb44509e275e85ed344c387176607a6da370da494b35b3435eaeca80fb760`.
- Built from the stock Waze base; Apktool, zipalign, and `apksigner` v3 verification succeeded.
- The car was confirmed parked before replacing v3. The controller first restored and verified stock, then mounted v4.
- Current checks: installed base hash is stock; Zygote hash matches v4; `com.waze` process is running; crash buffer contains no new crash after activation (the newest Waze crashes are from 13:02–13:03, before v3/v4).
- The direct-start behavior still needs one in-car interaction check: search/select a destination and confirm navigation begins immediately on Waze's recommended route, without the alternatives / **Go now** screen. Do not mark this behavior user-verified until that happens.

#### Current controls

Inspect mount hashes:

```sh
su -c 'sh /data/adb/modules/nemotron-session-patches/waze-control.sh status'
```

Manually enable staged candidate (restarts Waze; only while parked):

```sh
su -c 'sh /data/adb/modules/nemotron-session-patches/waze-control.sh enable'
```

Restore the stock Waze mount and restart Waze:

```sh
su -c 'sh /data/adb/modules/nemotron-session-patches/waze-control.sh disable'
```

Waze auto-activation at boot remains off until the final in-car behavior has been confirmed. No reboot was performed. The proprietary APKs and generated screenshots/videos are intentionally not tracked; the builders, installer/controller, and this experiment log are tracked.