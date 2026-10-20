# Nemotron package split

## Packages and responsibility

The split produces three Android packages, each with its own UID and battery
statistics:

| Package | Role |
| --- | --- |
| `com.nemotron.core` | Nemotron settings, Quick Settings tiles, receivers, and background features |
| `com.nemotron.keyboard` | HoneyBoard IME and Nemotron voice recording integration |
| `com.nemotron.voiceime2` | Nemotron UI, based on One UI Home, with the existing Home data and role |

The UI APK is updated in place. The installer does not uninstall it, change
the HOME role, disable One UI Home, or reboot. It selects the new keyboard only
after all three APKs are installed. If verification fails, it reinstalls the
integrated rollback APK, restores the previous keyboard, and removes only the
new Core and Keyboard packages.

Separate UIDs make Android's per-app battery accounting useful for identifying
which process is active. The split does not itself guarantee any specific
battery usage.

## Build

The build recompiles current Nemotron sources, merges the preserved HoneyBoard
donor, then produces the Core, Keyboard, and UI APKs. It signs all three with
the same debug certificate as the installed UI so the UI can be updated without
losing its Home data. Provider authorities are checked for duplicates within
each APK and across all three packages; the HOME activity, app-picker
activities, and keyboard service are also checked before the APKs are accepted.

The output directory may contain saved PNG screenshots, but no old APK or
temporary build artifacts, and it needs at least 2 GB free for staged APKtool
rebuilds. Keep the integrated build input as the rollback APK.

```powershell
$repo = 'C:\Users\adaredu\whisper-parakeet-ahk'
& "$repo\tools\build-nemotron-split.ps1"
```

Outputs under `D:\NemotronSplit`:

- `Nemotron-Core.apk`
- `Nemotron-Keyboard.apk`
- `Nemotron-UI.apk`
- `Nemotron-Split-Core-Current.apk` (integrated rollback APK)

## Install

First confirm the A54 is online. The installer confirms that Nemotron UI still
owns Home and the integrated build owns the current keyboard. It refuses to
replace already-installed Core or Keyboard packages, and never removes the UI
package.

```powershell
& 'C:\Users\adaredu\whisper-parakeet-ahk\tools\install-nemotron-split.ps1' `
  -CoreApk 'D:\NemotronSplit\Nemotron-Core.apk' `
  -KeyboardApk 'D:\NemotronSplit\Nemotron-Keyboard.apk' `
  -UiApk 'D:\NemotronSplit\Nemotron-UI.apk' `
  -RollbackApk 'D:\NemotronSplit\Nemotron-Split-Core-Current.apk'
```

If wireless debugging exposes multiple transports for the same phone, the
installer identifies aliases by `ro.serialno` and selects the canonical TLS
transport. Pass `-Serial` explicitly if more than one physical device is online.

## Sleep Mode and Fit3 reconnection

Sleep Mode now ignores a delayed or duplicated night activation after a daytime
wake, until the next 23:00 boundary. Turning it off remains authoritative even
if the Fit3 handoff or Shizuku operation fails.

The Fit3 Plugin (`com.samsung.wearable.fit3plugin`) is kept enabled and is never
disabled or force-stopped by Nemotron. The dedicated Galaxy Wearable tile alone
freezes/unfreezes `com.samsung.android.app.watchmanager`; when turned on, it
clears that package's force-stop state and enables Bluetooth if needed for
reconnection. The Samsung Health tile manages only
`com.sec.android.app.shealth`, requiring Firebase confirmation before freezing
it. The scheduled Fit3 settings handoff also leaves the plugin, Galaxy
Wearable, Samsung Accessory, and Bluetooth alone.

## Validation status

On 20 October 2026, ADB confirmed the A54 still resolves HOME and holds the
Home role at `com.nemotron.voiceime2`, with its integrated HoneyBoard IME
selected. The new Core and Keyboard package IDs were not installed. The Fit3
plugin was found disabled and force-stopped, then explicitly re-enabled and
unstopped; Bluetooth was off at that time. No new split APK was installed.

The build has not completed: `D:\NemotronSplit` contains old generated APKtool
outputs and currently has about 328 MB free, below the 2 GB build requirement.
Do not treat the split or Fit3 reconnection as installed or device-verified
until the old outputs are cleared, a build completes, and the phone and watch
are tested.
