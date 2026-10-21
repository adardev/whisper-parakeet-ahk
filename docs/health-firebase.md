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
  `com.sec.android.app.shealth`; enable Bluetooth when needed and request the
  Fit3 connection through Samsung's Host Manager.
- Off: wait for the Health SDK read and confirmed Firebase upload, request Fit3
  disconnection, then disable and stop Samsung Health and Fit3 Plugin. If Fit3
  was the only connected device, turn Bluetooth off and verify the link went
  down. Preserve Bluetooth when another device is connected.
- Galaxy Wearable remains controlled by its separate tile. Sleep Mode uses a
  bounded direct BLE exchange and leaves plugin state as the Fit3 tile set it.
- An unsuccessful sync leaves Health enabled so the user can retry without
  interrupting the transfer. The existing transfer wait is bounded at 240 seconds.
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
