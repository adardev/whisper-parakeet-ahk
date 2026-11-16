# Offline battery/CPU optimization continuation — 16 November 2026

This work follows `battery-audit-2026-11-16.md`. The phone was disconnected for
this phase; the outputs are candidates, not a claim of installed or measured
battery savings. The existing feature set, sync thresholds, account state,
navigation gestures, launcher layout and keyboard settings are preserved.

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

Pending device checks: install new Core, compare the installed Keyboard base,
validate dictation/case behavior and icon rendering, and compare normal-use CPU
and discharge over a representative interval. The offline work did not change
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
future builds. Both candidates still require device installation and normal-use
verification. The local UI base/version concern above still applies.

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
