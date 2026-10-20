# Nemotron package split

## Packages and responsibility

The split produces three Android packages, each with its own UID and battery
statistics:

| Package | Role |
| --- | --- |
| `com.nemotron.core` | Nemotron settings, Quick Settings tiles, receivers, and background features |
| `com.nemotron.keyboard` | HoneyBoard IME and Nemotron voice recording integration |
| `com.nemotron.voiceime2` | Nemotron UI based on One UI Home, retaining its app data and Home role |

The UI package is updated in place. Install the UI APK before the new keyboard:
the full installed launcher package also owns keyboard providers, and updating
it first releases those authorities. Core and Keyboard can then be installed
without provider collisions. The installer selects the new keyboard, restores
Nemotron Home as the Home activity, and checks all three packages. It does not
uninstall UI, clear app data, or reboot.

If a step fails after installation starts, rollback uses the original complete
UI APK captured before splitting. That APK retains the Home activity and the
previous HoneyBoard IME. The installer restores the Home role and old IME, then
removes only newly installed Core and Keyboard packages.

Separate UIDs make Android's per-app battery accounting useful for identifying
which process is active. The split does not itself guarantee any specific
battery usage.

## Build

The build recompiles current Nemotron sources, merges the preserved HoneyBoard
donor into Nemotron Core, then produces Core, Keyboard, and UI APKs. It copies
the full UI input to `Nemotron-UI-Rollback.apk`; the Core integration APK is an
intermediate input, not a Home rollback. All three APKs use the same debug
certificate as the installed UI so UI can be updated without losing Home data.
Provider authorities are checked within and across the split packages.

Use C: by default because it has the most free space on this PC. The build
requires at least 2 GB free on its output drive and a clean output directory.
APKtool 2.12.1 is checked against the official SHA-256 before use.

```powershell
$repo = 'C:\Users\adaredu\whisper-parakeet-ahk-recovered'
& "$repo\tools\build-nemotron-split.ps1" `
  -OutputDirectory 'C:\Users\adaredu\NemotronBuild\SplitOutput'
```

The output directory contains:

- `Nemotron-Core.apk`
- `Nemotron-Keyboard.apk`
- `Nemotron-UI.apk`
- `Nemotron-UI-Rollback.apk` (original full launcher APK)
- `Nemotron-Core-Integrated.apk` (intermediate used to produce Core/Keyboard)

## Install

The installer checks that Nemotron currently owns the Home role and its
integrated keyboard is selected. It refuses to overwrite existing split Core
or Keyboard packages, then updates UI in place, installs Core and Keyboard,
selects the new IME, starts Nemotron Home, and verifies the resulting roles.

```powershell
$repo = 'C:\Users\adaredu\whisper-parakeet-ahk-recovered'
$out = 'C:\Users\adaredu\NemotronBuild\SplitOutput'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& "$repo\tools\install-nemotron-split.ps1" `
  -CoreApk "$out\Nemotron-Core.apk" `
  -KeyboardApk "$out\Nemotron-Keyboard.apk" `
  -UiApk "$out\Nemotron-UI.apk" `
  -RollbackApk "$out\Nemotron-UI-Rollback.apk" `
  -Adb $adb `
  -Serial 'RZCWB0BHEQX'
```

If wireless debugging exposes multiple transports for the same phone, pass the
physical device serial explicitly.

## Sleep Mode and Fit3 reconnection

Sleep Mode ignores a delayed or duplicated night activation after a daytime
wake, until the next 23:00 boundary. Turning it off remains authoritative even
if the Fit3 handoff or Shizuku operation fails.

The Fit3 Plugin (`com.samsung.wearable.fit3plugin`) must remain enabled and
unstopped. Nemotron does not freeze or force-stop it. The separate Galaxy
Wearable tile controls `com.samsung.android.app.watchmanager`; when turned on,
it clears that package's force-stop state and enables Bluetooth if needed for
reconnection. The Samsung Health tile manages only `com.sec.android.app.shealth`,
requiring Firebase confirmation before freezing it. The scheduled Fit3
settings handoff also leaves the plugin, Galaxy Wearable, Samsung Accessory,
and Bluetooth alone.

## A54 verification — 20 October 2026

- Installed `com.nemotron.core`, `com.nemotron.keyboard`, and updated
  `com.nemotron.voiceime2` in place.
- `com.nemotron.voiceime2` owns the Android Home role and its launcher activity
  starts successfully.
- Default IME is
  `com.nemotron.keyboard/com.samsung.android.honeyboard.service.HoneyBoardService`.
- The Fit3 plugin is enabled (`enabled=1`) and not stopped (`stopped=false`). It
  was re-enabled without changing its APK or Bluetooth state.
- `/data` had 5.0 GB free after install. The PC build and APK outputs are on C:,
  which had about 25 GiB free; D: was left untouched.

The device install confirms package separation and Home/IME selection. A
separate unplugged screen-off sample is still required to compare background
battery use between these UIDs.
