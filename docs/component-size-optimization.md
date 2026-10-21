# Component size and keyboard memory — 20 October 2026

The earlier split changed manifests and UIDs but retained the merged APK payload
in all three packages. ZIP inspection confirmed three identical Whisper models,
HoneyBoard native libraries in Core/UI, and unused vendor/Core bytecode.

## Installed APK sizes

Values below are bytes divided by 1,048,576 (MiB), not Settings' combined
app/data/cache size and not RAM usage.

| Component | Before | Installed now |
| --- | ---: | ---: |
| Core | 179.03 | 16.26 |
| Keyboard | 179.04 | 171.06 |
| UI | 238.45 | 104.52 |
| Total | 596.52 | 291.84 |

The phone's original system Home APK is 61,522,662 bytes (58.67 MiB); its
system HoneyBoard APK is 137,588,276 bytes (131.21 MiB). The UI resource table
still contains merged resource IDs to preserve compatibility; it is not yet
as small as the untouched system Home APK.

## Ownership and build changes

- Core builds directly from sources with `:apps:nemotron:assembleDebug -PnemotronComponent=core`.
  Its Application extends `android.app.Application`, it has no model/native
  libraries, and its manifest excludes the Keyboard voice-recording service.
- Whisper belongs only to Keyboard. Its 77,691,713-byte asset was preserved
  byte for byte. SHA-256 before and after:
  `be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21`.
  No extracted model file was present in Core or UI private files either.
- UI excludes Whisper and HoneyBoard-only assets/native libraries. Reference
  system APK inventories prevent removing assets also belonging to Home.
- A conservative DEX dependency pass removed 6,445 classes from Keyboard and
  36,829 from UI in the initial pass. It retains complete Samsung donor class
  groups, manifest roots, resource classes, Kotlin runtime, reflection names,
  Android support classes, Parcelizers, and their static dependencies.
- `tools/optimize-nemotron-components.ps1` reproduces the separation from signed
  component inputs. `tools/build-nemotron-split.ps1` invokes it for future builds.
  The model ownership and output signing certificate are checked before output
  is accepted. Host builds use one worker and bounded JVM memory.

## Keyboard RAM

`KeyboardMemoryController` schedules one cleanup 30 seconds after the input
window hides, cancels it when shown or destroyed, and holds only a weak service
reference. It invokes Samsung's existing hidden-view trim path, including its
one-minute throttle. It adds no periodic loop, wake lock, or background service.

The pre-update snapshot was 185,713 KiB PSS, 268,780 KiB RSS, 37,227 KiB swap
PSS, and 1,080 views after several hours of use. The updated keyboard showed
193,569 KiB PSS while visible, 155,910 KiB immediately after hiding, and
154,474 KiB after cleanup, with 421 views. The 30-second callback and Samsung
component cleanup were confirmed in Logcat. These different sessions do not
establish a guaranteed RAM percentage reduction. The measured cleanup delta
within the updated session was approximately 1.4 MiB.

## Device checks and limits

All APKs updated in place with the same signer and package IDs. HOME remained
`com.nemotron.voiceime2` and the selected IME remained `com.nemotron.keyboard`.
Core settings, the launcher, its existing widgets, and the Helium search widget
opened; the keyboard showed and hid without new Nemotron crashes. The Home
layout was visually checked and its databases/preferences were preserved.
The exact 23:00 Sleep Mode alarm remained scheduled and the disconnected-Fit3
DND foreground service remained absent.

The private launcher app-picker Activity could not be invoked directly from
ADB because it is not exported; that route was not verified. The Settings
search-key probe encountered an already unavailable
`com.android.settings.intelligence` activity. No system package state was
changed. Full dictation, all optional keyboard panels, and long-term battery
usage were not measured in this size-optimization session.

Local outputs: `E:\NemotronSlim\output`. Rollback APKs and private configuration
backups are under `E:\NemotronSlim\original`; these contain user data and are
not repository artifacts.
