# Offline battery/CPU optimization continuation — 16 November 2026

This work follows `battery-audit-2026-11-16.md`. Optimizations are described
with their measured limits. Sync thresholds, account state, navigation
gestures, launcher layout and keyboard settings are preserved. Per the user's
instruction, the unused AI chat client is excluded from the standalone Core
package.

## Changes and evidence

| Path | Removed work | Validation |
| --- | --- | --- |
| SecureStore | Reopening Android Keystore and encrypted preference metadata on every sensitive-setting read | Successful encrypted preferences cached once per process using the application context; temporary fallback is not cached, so keystore recovery remains possible |
| ChatClient | A new thread per HTTP request and a new connection pool per screen | Shared OkHttp dispatcher/pool; response bodies closed on success and failure; MockWebServer proves connection reuse across instances |
| ChatClient cancellation | A previously unused SSE reference that did not cancel the actual HTTP request | Tracks the real answer Call; test verifies cancellation does not affect another client |
| ConversationStore | Rewriting the complete history on unchanged list refresh, empty cleanup or no-op deletion | Filesystem tests verify unchanged modification time and exact history content, plus real new messages and draft cleanup |
| NemotronStreamClient | An old stream continuing after a new query, and callbacks sharing completion state | One request generation per stream; previous source cancelled; a local SSE test proves only the new answer completes |
| KeyboardDictationFeedback | Enqueuing another main-thread message for every RMS callback already delivered on the main thread | Direct update on main; retains queued dispatch when called from another thread |
| ShortcutPickerActivity | Repeated row lookups and icon loads while scrolling | Row holder and bounded 32-entry ConstantState cache; separate mutable Drawable per use; recycled icons cleared on load failure |
| Standalone Core build | AI chat client classes and five chat/assistant layouts were packaged despite having no manifest activity or references outside the chat package | Core-specific source/resource build excludes them; the integrated source build remains unchanged |

No refresh interval or query history range was shortened. Encrypted preference
caching retains the same file names, encryption and fallback behavior; it does
not change stored data. Conversation reconciliation still preserves local
titles, pinned state and message history when remote lists arrive.

## Build and tests

Run with JDK 17 and the configured Android SDK:

```powershell
.\gradlew.bat :apps:nemotron:testDebugUnitTest :apps:nemotron:assembleDebug -PnemotronComponent=core
```

The initial JVM suite contains seven transport/storage tests across `ChatClientTest`,
`ConversationStoreTest` and `NemotronStreamClientTest`. A real local HTTP/SSE
server exercises connection reuse, HTTP errors and replaced/cancelled requests;
temporary files verify persistence behavior. Android logging is a no-op for
these JVM tests. These tests do not simulate Android lifecycle, Keystore,
Samsung Health or launcher rendering; those remain device checks.

## Keyboard candidate and reproducibility

`tools/refresh-keyboard-runtime.ps1` accepts an explicit Core build and Keyboard
base APK, checks package/signers, updates only `SecureStore*` and
`KeyboardDictationFeedback*`, rebuilds, aligns and signs the result. It compares
every non-DEX, non-signature archive entry against the base byte-for-byte and
rejects changed or unexpected entries. It never installs an APK or changes the
input files. Use a new output directory on every run.

This phase used Apktool 2.12.1 from the project's official GitHub release:
https://github.com/iBotPeaches/Apktool/releases/tag/v2.12.1

Tool SHA-256: `66cf4524a4a45a7f56567d08b2c9b6ec237bcdd78cee69fd4a59c8a0243aeafa`.

Prepared output:
`C:\Users\adaredu\work\offline-keyboard-runtime-20261116\Nemotron-Keyboard-candidate.apk`.
The accompanying `verification.txt` records the input/output hashes and
**5785 preserved non-code entries**. It is based on the local 14 Nov Keyboard
APK. Compare that base with the currently installed Keyboard before installing;
an older base must not replace later fixes.

## UI/Home assessment and remaining verification

The local UI base APK contains a legacy ShortcutActivity/ShizukuManager path;
it is not sufficient evidence of the currently installed UI version. No UI APK
was rebuilt or installed from that base. Fetch and compare the installed base
when the phone reconnects before touching its vendor DEX files. Core's shared
picker source improvement does not imply a changed vendor Home APK.

Pending device checks: compare normal-use CPU and discharge over a representative
interval, and check Keyboard dictation/case during normal use. The offline work did not change
the NAS, Beeminder rules, Google/microG packages or kernel modules.

## Follow-up: authentication, rendering and dictation cleanup

Further source inspection identified additional repeated work:

- `FirebaseHealthUploader` now shares a synchronized, single-entry credential
  cache across uploader instances. It keys the value to the Firebase API key
  and refresh token, uses a monotonic clock and the server-reported lifetime,
  and refreshes at least a minute before expiry. Missing lifetime disables the
  cache. Firebase/Uber HTTP 401 responses invalidate it. Credentials, anonymous
  sign-in behavior, destination UID and snapshot payloads remain unchanged.
- `MarkdownRenderer` reuses compiled expressions and the fixed math-command
  map across rows. The HTML transformation is unchanged and can be tested on
  the JVM separately from Android's Spanned conversion.
- `MessageAdapter` updates just the old/new speech-button rows when narration
  changes. A payload update avoids rerendering every message's Markdown; full
  binds still apply message/role/format changes. Recycled animations are cancelled.
- `VoiceRecordService` clears pending restart/finalization callbacks when it
  cleans up or is destroyed, and resets its recording flags on destruction.

The full JVM suite now has **13 passing tests**, including three HTML-format
checks and three expiry/concurrency/failure-cache checks. This validates token
cache mechanics, not a live Firebase sign-in on Android.

The latest Keyboard candidate is
`C:\Users\adaredu\work\offline-keyboard-background-20261116\Nemotron-Keyboard-candidate.apk`.
Its SHA-256 is `c0dd72b467a58a6252e9cd613e6dd0a71110311221a8a6c2987de70101a97452`.
The runtime refresh script also invokes
`tools/patch-keyboard-callback-cleanup.ps1`, which modifies only the two known
dictation lifecycle methods and rejects an unexpected register/field layout.
The lifecycle patch is idempotent. An independent comparison verified that
`VoiceRecordService` differs from its base only in `cleanup` and `onDestroy`.

Strict archive comparison now also covers non-signature `META-INF` library
metadata: **5844 non-code/non-signature entries are byte-identical** to the base.
The script's verification uses the same narrower signature exclusion for
future builds. The Keyboard candidate was installed over an identical SHA-256
base and matching signer/package/version; Android accepted the in-place update
and retained package data. Dictation still needs ordinary-use confirmation.
The local UI base/version concern above still applies; UI was not replaced.

## Fit3 panel-render scheduling regression

Source inspection found an avoidable source of Health work: the Fit3 tile's
`onTileState` calls `HealthSyncScheduler.enable` when an already-active tile is
rendered. Previously, every such call scheduled job 3404, even though enabling
had already been persisted. Repeatedly opening Quick Settings could therefore
request repeated 30-day exports, in addition to the intended hourly job.

`enable` now records the prior enabled state under a synchronized transition.
It schedules the immediate export only for a false-to-true transition; its
periodic-job reconciliation still runs so a missing persisted periodic job can
be restored. `disable` writes preferences only when needed and still cancels
both jobs, including a stale job left by an interrupted earlier transition.

Four tests execute the production scheduler against mocked Android boundaries:
100 already-active renders schedule exactly one immediate job; a real off/on
cycle schedules another; restoring an already-enabled configuration only
restores the periodic job; disabling cancels stale jobs even if the stored
enabled flag was already false. The complete JVM suite now has **17 passing
tests**. Mockito is a test-only dependency; it is not packaged in the APK.
The Core build succeeds. On-device upload counts and battery impact remain
unverified until the phone is available again.

## Media callback and Health SDK resource audit

`CarMediaSessionListener` previously queried all active media sessions when a
supported player's metadata changed, even though the selected controller's
metadata is already forwarded by `CarMusicSessionBridge`. Source membership,
playback-state changes and notification events still reconcile player
selection; the redundant metadata-only query was removed. Two tests execute
the production listener/bridge with mocked media sessions: artwork updates
reach the bridge without another `getActiveSessions` call, and playback changes
still switch the one selected destination from Salt to YouTube Music. Real car
controls and artwork rendering still require a device/car check.

Inspection of the bundled Samsung Health Data SDK 1.1.0 bytecode found that
each store constructor creates a four-worker executor and a device-manager
single-worker executor. The public store interface has no close operation, and
its constructor does not install executor shutdown on caller-scope completion.
Merely passing an export coroutine's scope cannot establish executor cleanup.
An initial uncommitted scope-lifetime change was therefore replaced with a
process-wide store pool; it was not installed or published.

`SamsungHealthManager` now obtains one store from `HealthStorePool`, whose
factory receives only the application context. A process owns one SDK
store/executor set, rather than creating a set per setup screen or export. No
private SDK field is modified. Each read/permission request remains separate:
the inspected SDK suspend adapters attach cancellation to the individual
future. The periodic job and explicit foreground transfer retain their checks,
date range, payload and upload path. Live Android binding and resource counts
remain device checks, not JVM-test results.

The review also considered coalescing the immediate and periodic Health
exports. Joining a reading that has already queried exercise could miss a
newly completed session that motivated the later request, so the current
implementation preserves separate fresh reads. Removing the repeated exports
caused by panel rendering remains the verified scheduling improvement. The
full suite contains **21 passing JVM tests** after the two media regressions
and two resource-pool regressions. The latter verify that 32 concurrent export
requests construct one store using the application context and that a failed
creation can recover on the next request. The factory seam avoids binding to
Samsung Health or creating its real executor pools in host tests.

## Sleep Mode toast and overnight drain evidence

The ADB battery capture covers only 4 h 41 min, with 1 h 41 min screen-off;
it is not an overnight baseline. Core's Sleep Mode action had two launches,
one scheduled alarm, and about 4 seconds actual partial wakelock during that
window. This does not account for recurring all-night drain. Android records
include system-UID motion wakes and Wi-Fi/IMS activity, but that shared UID and
daytime sample cannot identify a package-level night culprit. Do not change
or disable unrelated packages based on it.

Sleep Mode no longer shows its success/status toast after activation or
deactivation. The action result remains in logcat; only a thrown action error
may show a generic failure toast. No other notification channel or toggle was
changed.

`tools/capture-idle-battery.ps1` collects read-only bedtime/morning samples of
BatteryStats, alarms, sensors, device idle, power and radio services. It
requires one authorized ADB device, writes private logs outside Git under
`%LOCALAPPDATA%\NemotronBatteryAudit`, does not reset counters, and exits after
one capture. An uncontaminated pair is still required to identify and then
target the overnight wake source.

## Core package separation

The previously installed Core DEX included `com.nemotron.voiceime.chat` even
though package-manager activity discovery returned only `SetupAliasV2` and
`TilePreferencesActivity`. The separate package contained 1,240 chat classes;
the chat code had no references from outside its package. The standalone Core
build now excludes that package and its five associated layouts. The build
completed and the update installed in place with the same package UID 10352;
the setup entry and Sleep Mode, Fit3, Android Auto and Work tile services
remain declared. The installed APK has no chat/assistant DEX classes or those
layouts. This removes unused packaged code; it is not evidence of a battery
gain because those classes were not running before removal.

The toast-only Core build compiled with JDK 17 and installed at 10:36. The
follow-up Core build, with the AI chat code excluded, also compiled and
installed in place at 10:50 on Nov 16; Android retained UID 10352 and version
1.6. Its SHA-256 is
`dec81ca606e0abef981712f5dc60f8962ec7f3887fc069e89c9916f49e4b2c21`. The
previous Core APK was pulled to the local work directory before replacement.
