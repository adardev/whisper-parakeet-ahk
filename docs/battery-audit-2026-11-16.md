# Battery audit and CPU reductions — 16 November 2026

## Device evidence

Read-only ADB capture from the A54 before the changes, over 4 h 41 min on
battery (including 1 h 41 min with the screen off): Android estimated total
drain at 1057 mAh. These are accumulated model estimates, not daily percentages
or a controlled before/after experiment.

| Component | UID | Estimated total |
| --- | --- | --- |
| Nemotron Core | 10352 | 2.65 mAh |
| Nemotron Keyboard | 10353 | 7.38 mAh |
| Nemotron UI | 10421 | 9.12 mAh |
| All three | | 19.15 mAh |

All three account for about 1.81% of the device's estimated drain in this
window under their own UIDs. Work requested through root or system services can
be charged to UID 0 or shared system UID 1000 instead, so this percentage does
not bound Nemotron's total indirect contribution. The same capture attributes 306 mAh to the display, 364 to CPU, 36.9
to Wi-Fi and 6.51 to sensors. Those component estimates should not be added
to the application totals; they describe the same energy from another view.

Google's app UID accounted for 248 mAh, including 216 mAh attributed to display
time during about 1 h 55 min foreground use. Shared system UID 1000 accounted
for 142 mAh, System UI 36.4, Fitia 32.8, Microsoft To Do 17.6 and Helium 17.1.
Shared UID 1000 cannot be attributed to one package. This capture does not
establish abnormal idle drain by any of those apps.

Core accumulated about 36.5 seconds CPU and 4.1 seconds actual partial wake
locks. Keyboard accumulated about 58.8 seconds CPU and 31 ms actual partial
wake locks; six keyboard jobs used 66 ms total. None of these readings supports
disabling its existing work just to lower a counter. Sensorservice listed
grip, significant motion and step-counter sensors active; no Nemotron sensor
connection was identified in the query.

### Overnight attribution limit

This capture is not a controlled overnight run: it spans 4 h 41 min and only
1 h 41 min screen-off. It cannot identify the process responsible for the
user's recurring night-time 1–2% drain. In this window Core's Sleep Mode
service launched twice and accumulated 4.1 seconds of actual partial wakelock;
Core had one scheduled Sleep Mode alarm. That is evidence against a long-running
Nemotron Sleep Mode wakelock in this particular window, not proof about other
nights. Android also logged system-UID motion alarms and Wi-Fi/IMS activity;
the shared system UID and this non-night interval do not identify a specific
package as the overnight cause. Battery percentage graphs alone cannot make
that attribution.

`tools/capture-idle-battery.ps1 -Phase bedtime` and then
`tools/capture-idle-battery.ps1 -Phase morning` save read-only ADB snapshots in
`%LOCALAPPDATA%\NemotronBatteryAudit`. Compare the two `batterystats`, alarm,
sensor, power and idle records; no battery stats are reset and no setting is
changed. The script requires exactly one authorized ADB device and does not
run persistently. Capture at bedtime after Sleep Mode/radios have reached the
intended state, then immediately after waking before opening apps or toggling
radios. Until such a paired interval is available, the responsible package is
unknown and no third-party app should be disabled based on this evidence.

Sleep Mode no longer emits the result/status toast after a tile action. The
service still records the outcome in Android logs, refreshes the Quick Settings
tile and releases its temporary wakelock; an exception while starting or
executing the action can still show the generic failure toast.

## Applied code changes

- `HealthSnapshotJobService` passes its already-read permission set to the
  Health reader, avoiding unsupported/ungranted requests while retaining all
  granted data types and the 30-day history.
- `SamsungHealthManager` caches reflected field lists and session methods
  within a transfer. Every record still uses its own values, including all
  exercise sessions and durations; only repeated metadata lookups are removed.
- `AssistGlowView` stops its pulse when visibility is lost and restarts when
  visible. It reuses its drawing bounds instead of allocating them each frame.
- `ChatsListActivity` permits only one list fetch at a time, including the
  overlapping creation/resume calls, and ignores rendering after destruction.
- `CarMediaBrowserService` reuses mirrored metadata/queue while the Android
  media objects and source stay unchanged. State changes still propagate;
  real metadata/source changes still update artwork and queue. No Android Auto
  APK patch, player or navigation setting was replaced.

These changes ship in Core. UI/Home and Keyboard remain their existing APKs;
they were audited separately rather than given speculative vendor-code edits.
The source project also has an immediate Fit3 snapshot scheduler from the
previous sync repair; its hourly periodic schedule remains unchanged.

## Runtime compiler and validation

Android accepted `cmd package compile` requests for all three apps, but verbose
results reported `actualCompilerFilter=verify` even when `speed` was requested;
UI/Keyboard were skipped. Therefore this audit claims no CPU saving from AOT
compilation. No global ART properties were changed to force it.

`:apps:nemotron:assembleDebug -PnemotronComponent=core` completed successfully.
The updated Core APK installed with `adb install -r`. Its exported setup alias
opened successfully; Android still selected `com.nemotron.voiceime2` as HOME
and the Nemotron HoneyBoard service as the input method. Device data and layout
were preserved. Full car playback and Health sync were not exercised in this
short battery check. A full normal discharge window is required to measure
actual savings; no extra hours of battery life are promised from these fixes.

Raw private device capture: `C:\Users\adaredu\work\battery-before-20261116.txt`.
That raw log stays outside Git; the aggregate results above are the repository
record. Do not reset BatteryStats before the next comparison.
