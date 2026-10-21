# Samsung Health Data SDK → Firebase

Nemotron reads Samsung Health through the official Samsung Health Data SDK and writes one snapshot per day to
Firebase Firestore:

```text
/health/{anonymousFirebaseUid}/snapshots/{yyyy-mm-dd}
```

Firebase Authentication must have **Anonymous** sign-in enabled. The app uses
the dedicated Firebase project (`samsung-health-adardev`) and stores the anonymous refresh token in Android
encrypted preferences. The Web API key can be changed from Settings if needed.

Add this match to the Firestore rules (the NAS uses Admin SDK, so it bypasses
client rules):

```text
match /health/{uid}/snapshots/{date} {
  allow create, update: if request.auth != null && request.auth.uid == uid;
  allow read: if false;
  allow delete: if false;
}
```

Before connecting, Samsung Health must be installed and its developer mode for
Samsung Health Data SDK must be enabled. On the phone: Samsung Health → ⋮ →
Settings → About Samsung Health → tap the version repeatedly → Developer mode
(Samsung Health Data SDK) → enable data reading. Then use the app's Samsung
Health setup action to approve the requested read permissions.

The NAS/server should read the data with Firebase Admin credentials. The old
Health Connect, LAN webhook, and Syncthing health-file paths are no longer used.

Enabling the Fit3 tile may trigger at most one automatic transfer attempt per
local day. When disabling it, the tile now syncs and waits for Firebase's success
response before freezing Samsung Health or checking whether Bluetooth can be
disabled. If the SDK read, network upload, or timeout fails, the tile stays
enabled and Bluetooth stays on. A failed automatic attempt can be retried by
toggling off, which forces a fresh transfer, or through **Enviar ahora**.
The read only requests data types whose Samsung Health Data SDK permissions are
currently granted, and optional streams use short timeouts. Firebase requests
have bounded call timeouts and two upload attempts so a slow endpoint cannot
keep the foreground transfer alive in a long retry loop.
# Battery and connection lifecycle (19 Oct 2026)

`HealthDataService.getStore()` binds to Samsung Health. The previous code used
the SDK's default coroutine scope, which the app never cancelled; the A54 showed
Nemotron still bound to `PrivilegedHealthService` more than 30 minutes after a
health operation. Stores now receive the setup Activity's lifecycle scope or
the one-shot transfer service scope, so finishing either operation cancels the
SDK client. Automatic sync is limited to one attempt per day, and the off-toggle
performs a confirmed sync before freezing the Health provider.

Recovery check on the A54 (19 Oct 2026): Samsung Health and Fit3 Plugin had been
disabled, but Bluetooth was still on. Re-enabling them did not erase Health
records. The SDK returned 9 exercise records for the prior 30 days, and
Firestore confirmed the snapshot upload for 2026-10-19 (`last_successful_upload_date`).
The tile-off path was changed to await that same success before freezing Health
or checking Bluetooth. After recovery, the packages were left enabled
and Bluetooth on so the user can continue using the watch.

## Fit3 tile contract (20 Oct 2026)

The tile controls Samsung Health and Fit3 Plugin together, even when the watch
temporarily loses its Bluetooth connection. A disconnected watch must not change
an intended off tap into another reconnect attempt.

- On: enable and unstop `com.samsung.wearable.fit3plugin` first, then
  `com.sec.android.app.shealth`; enable Bluetooth when needed and send the
  SDK's `REQUEST_MANAGER_CAPABILITY` to the plugin's exported
  `MobileConnectionReceiver`. Its main-process Application initializes
  Samsung's HostManager. No Activity is launched and Quick Settings stays as-is.
  Confirm the Fit3 GATT connection within 25 seconds; otherwise report that
  the watch is still disconnected.
- Off: wait for the Health SDK read and confirmed Firebase upload, request Fit3
  disconnection, then disable and stop Samsung Health and Fit3 Plugin. If Fit3
  was the only connected device, turn Bluetooth off and verify the link went
  down. Preserve Bluetooth when another device is connected.
- Galaxy Wearable remains controlled by its separate tile. Sleep Mode uses a
  bounded direct BLE exchange and leaves plugin state as the Fit3 tile set it.
- The pre-disconnect sync wait is bounded at 60 seconds. An unsuccessful upload
  is reported and local Health data is preserved; it does not trap the tile on.
- The busy guard is shared between tile service instances to prevent overlapping
  taps from starting multiple transfers or opposing connection requests.

Package toggles now use fresh Shizuku commands with an explicit user (`0`) and
check the disabled-package list after enable/disable. Group tiles based on
`AppFreezeTileService` inspect every target package when refreshing their state.

A54 verification with the installed Core APK on 20 Oct 2026:

- 19:45:43: Firebase upload succeeded; an off tap with Bluetooth already off
  disabled Samsung Health successfully.
- 19:46:23: an on tap re-enabled Health and confirmed Fit3 connected.
- 19:47:45: Firebase confirmed the next upload before disconnection.
- 19:47:54: the connected-watch off tap confirmed Bluetooth off, Fit3 link down,
  and Samsung Health disabled. Fit3 Plugin was still enabled in this historical
  build; the current Fit3 tile now pauses it after disconnection.

The branch retaining Bluetooth for a second connected device was not exercised
in this session. The other tiles were inspected in code; their individual
device behaviors have not all been verified.

## Cold Fit3 plugin reconnection — 21 October 2026

The first plugin-freezing build enabled both packages and sent
`ACTION_HM_REQUEST_CONNECT` to `SAPHolder$ExternalDisconnectListener`. The
receiver requires Samsung's `ACCESS_UNIFIED_HOST_MANAGER` permission, which
shell/Shizuku does not hold. `Broadcast completed: result=0` was therefore
not evidence of delivery or connection. At 11:44:57 it completed without
starting the plugin; the tile subsequently logged `conectado=false`.

`MainInfoActivity` is exported without that permission. Opening it at 11:48:00
started HostManager under the Samsung plugin UID, and Samsung confirmed
`onAccessoryConnected` at 11:48:06. Its dashboard displayed the paired Fit3 and
its 100% battery level. The first working reconnect build used this public entry
point, rather than the protected CONNECT broadcast. The
separate Galaxy Wearable package can remain disabled. Only Core was rebuilt
and updated; UI and Keyboard were not updated in this change.

Verification with the updated Core APK using the real Fit3 TileService through
`cmd statusbar click-tile`:

- 11:50:22: Firebase confirmed the Health snapshot upload before disconnection.
- 11:50:31: Fit3 was the only Bluetooth device; Bluetooth and its link were
  confirmed off, both Health and Fit3 Plugin were disabled, and the plugin had
  no running processes.
- 11:51:00: an on tap restored the packages and enabled Bluetooth.
- 11:51:03: the public plugin activity started successfully and Quick Settings
  collapsed.
- 11:51:06: the tile confirmed the live GATT link (`conectado=true`).
- 11:51:07: Samsung confirmed `onAccessoryConnected` and connection state `2`.
  The dashboard again displayed the Fit3 at 100% battery. Galaxy Wearable
  remained disabled throughout this cycle. The watch was left connected.

### Verified background startup

To remove the visible plugin dashboard, the current source sends the Health
Connectivity SDK capability request explicitly to `MobileConnectionReceiver`.
The installed Samsung APK declares that receiver exported without a sender
permission, in its default process. Its Application's `onCreate` invokes
`HostManagerApplication.init` (including `NSHostManager.init`) and
`HMApplication.init`. The receiver's capability request path does not launch an
Activity. This initializes Samsung's backend without invoking its protected
CONNECT receiver or opening a window.

After reconnecting ADB, the background request started HostManager at 12:34:22
and Samsung confirmed its SAP session at 12:34:30 without launching an Activity.
The plain tile worker was subsequently found in `do_freezer_trap`: Samsung had
frozen Core before it could publish the successful connection and clear the
tile's busy state.

The tile now starts itself as a temporary `connectedDevice` foreground service
only for the explicit connection/disconnection operation. It uses a deferred,
low-priority notification, returns `START_NOT_STICKY`, removes the notification,
and stops its started service in `finally` after refreshing the tile. No Activity,
overlay, permanent worker, periodic poll or additional wakelock was added.

The final Core build compiled and was installed in place. A real tile cycle
confirmed:

- 12:41:03: Health's snapshot was uploaded to Firebase.
- 12:41:12: Bluetooth/link off, Health and Fit3 Plugin disabled, plugin processes
  absent.
- 12:41:34: the on tap started the bounded operation.
- 12:41:37: the background SDK receiver was invoked.
- 12:41:40: Core confirmed `conectado=true`; Samsung confirmed SAP connection
  state `2` in the same second.
- The Quick Settings hierarchy showed `Bluetooth, adarlpz's Fit3` and the Fit3
  tile without its busy subtitle. The underlying resumed Activity remained
  Nemotron UI; no plugin/Watchmanager Activity was launched.
- The Fit3 TileService finished with `startRequested=false` and no foreground
  notification `9025`. A normal QS binding may remain while the panel is visible.
  The separate DND keep-alive feature retains its existing connected-watch rule.

The watch was left connected. Galaxy Wearable remained disabled and neither
the UI nor Keyboard APK was changed.
