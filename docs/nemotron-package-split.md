# Nemotron package split

## Packages and responsibility

The split produces three Android packages, each with its own UID and battery
statistics:

| Package | Role |
| --- | --- |
| `com.nemotron.core` | Nemotron settings, Quick Settings tiles, receivers, and background features |
| `com.nemotron.keyboard` | HoneyBoard IME and Nemotron voice recording integration |
| `com.nemotron.voiceime2` | Nemotron UI based on One UI Home, retaining its app data and Home role |

The UI application and its Home icon alias both use the visible label
`Nemotron UI`, so Samsung Settings does not list the UI as a second `Nemotron`.

The UI package is updated in place. Install the UI APK before the new keyboard:
the full installed launcher package also owns keyboard providers, and updating
it first releases those authorities. Core and Keyboard can then be installed
without provider collisions. The installer selects the new keyboard, restores
Nemotron Home as the Home activity, and checks all three packages. It does not
uninstall UI, clear app data, or reboot.

UI owns the provider authority
`com.nemotron.voiceime2.provider.routines.v3`, which the One UI Home code uses
for Samsung Routines. The embedded launcher is app-signed and cannot hold
Samsung's `ACCESS_ROUTINES` signature permission, so that protection is removed
from the two embedded Routines providers. The installer also grants
`PACKAGE_USAGE_STATS` and its `GET_USAGE_STATS` AppOp to UI because Android's
app-prediction startup otherwise crashes. HoneyBoard needs
`WRITE_SECURE_SETTINGS` on its own UID before it starts.

If a step fails after installation starts, rollback uses the original complete
UI APK captured before splitting. That APK retains the Home activity and the
previous HoneyBoard IME. The installer restores the Home role and old IME, then
removes only newly installed Core and Keyboard packages.

Separate UIDs make Android's per-app battery accounting useful for identifying
which process is active. The split does not itself guarantee any specific
battery usage.

Core is rebuilt directly from its sources. The final optimization pass removes
unrelated bytecode and assets from Keyboard and UI, retaining the Whisper model
only in Keyboard. Their APK sizes and runtime memory are measured separately;
see [component size optimization](component-size-optimization.md).

## Build

The build recompiles current Nemotron sources, merges the preserved HoneyBoard
donor into Nemotron Core, then produces Core, Keyboard, and UI APKs. It copies
the full UI input to `Nemotron-UI-Rollback.apk`; the Core integration APK is an
intermediate input, not a Home rollback. All three APKs use the same debug
certificate as the installed UI so UI can be updated without losing Home data.
Provider authorities are checked within and across the split packages.

Choose the output drive with sufficient free space. The optimization pass
requires at least 3 GB free on its output drive and a clean output directory.
APKtool 2.12.1 is checked against the official SHA-256 before use.

```powershell
$repo = 'C:\Users\adaredu\whisper-parakeet-ahk'
& "$repo\tools\build-nemotron-split.ps1" `
  -HomeReferenceApk 'E:\NemotronSlim\original\system-home.apk' `
  -OutputDirectory 'C:\Users\adaredu\NemotronBuild\SplitOutput'
```

The output directory contains:

- `Nemotron\Nemotron-Core.apk`
- `Nemotron-Keyboard\Nemotron-Keyboard.apk`
- `Nemotron-UI\Nemotron-UI.apk`
- `Nemotron-UI-Rollback.apk` (original full launcher APK)
- `Nemotron-Core-Integrated.apk` (intermediate used to produce Core/Keyboard)

The published APK snapshots are kept in separate repository folders under
`apps/nemotron/`, `apps/nemotron-keyboard/`, and `apps/nemotron-ui/`. Git LFS
stores those large binaries; ordinary Git history contains only their pointers.

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
  -CoreApk "$out\Nemotron\Nemotron-Core.apk" `
  -KeyboardApk "$out\Nemotron-Keyboard\Nemotron-Keyboard.apk" `
  -UiApk "$out\Nemotron-UI\Nemotron-UI.apk" `
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

The Fit3 tile owns both Samsung Health (`com.sec.android.app.shealth`) and its
plugin (`com.samsung.wearable.fit3plugin`). When turned off, it syncs Health,
disconnects the watch, then disables and force-stops both packages. When turned
on, it enables and unstops the plugin first, then Health, before requesting a
connection. A failed plugin pause restores Health so the tile does not claim an
off state while leaving the stack half-enabled. The separate Galaxy Wearable
tile still controls only `com.samsung.android.app.watchmanager`.

Sleep Mode's AOD exchange uses Nemotron's bounded direct BLE session; it does
not need to leave the Fit3 plugin running after the Fit3 tile is turned off.
Samsung Accessory is restored after that exchange as before. Plugin state is
therefore tied to the Fit3 tile, and the tile's off state now requires both
Health and the plugin to be disabled while the watch is disconnected.

## Current validation status

The three separate packages are installed on the A54. The size optimization on
20 October rebuilt Core directly from its sources, removed unrelated classes
and vendor assets from the other components, and confined the Whisper model to
Keyboard. Core, Keyboard, and UI APKs now occupy approximately 16, 171, and
105 MiB respectively. HOME and IME roles, widget layout, and package data were
preserved through signed updates. See [component size optimization](component-size-optimization.md)
for measurements, reproducible tools, and the limits of the checks performed.

The selected IME is
`com.nemotron.keyboard/com.samsung.android.honeyboard.service.HoneyBoardService`.
The Fit3 plugin's enabled state now follows the Fit3 Quick Settings tile; it is
disabled and force-stopped while the tile is off, then enabled before a
connection request. The existing Home/provider permission compatibility fixes
are retained. A separate unplugged screen-off sample is still required to
compare background battery use between these UIDs.
