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
radios. The responsible package remains unknown; no third-party app should be
disabled based on an unassigned system UID.

### Extended ADB history snapshot (11:24, 16 November)

A later read-only `dumpsys batterystats` snapshot extended the device history
from 19:03 on 15 November through 11:24 on 16 November. It accumulated 14 h 10
on battery, 10 h 38 screen-off, 12 h 20 in full idle, and a longest full-idle
period of 8 h 47. Between 00:53 and the first screen-on event at 09:40, the
screen stayed off and the displayed battery level moved from 74% to 73% by
08:26: about one displayed percentage point over that 8 h 47 interval. Battery
level is rounded, so this is an approximate single-night result, not a
repeatable before/after test. The accumulated screen-off discharge across the
whole 14 h history was 7 percentage points/310 mAh; that includes earlier
screen use and USB reconnects and should not be read as the overnight-only
loss.

In the same model estimate, Core used 11.3 mAh total (0.73 mAh screen-off),
Keyboard 13.4 mAh (2.03 mAh screen-off), and UI 15.3 mAh (1.05 mAh screen-off).
Together the three were about 40 mAh total and 3.8 mAh screen-off. Their
screen-off actual partial wakelocks were about 3.8 seconds for Core, 21 ms for
Keyboard and none attributed to UI. These estimates are small relative to the
device's computed 1,325 mAh over the full history, but remain model estimates.
No Nemotron process explains the observed one-point loss during the longest
idle interval.

System UID 1000 was estimated at 229 mAh total and 32.3 mAh screen-off, with
15 m 18 s of actual screen-off partial wakelock. `Prevent Sleep by
AssistantDelay` accounts for 16 m 30 s across the entire history and 1,977
acquisitions in the aggregate summary; Android still entered the 8 h 47 full
idle interval. This UID is shared by system services, and the aggregate lock
duration is not tied to that night alone, so it does not identify a package to
disable or justify a system change. Samsung Health was estimated at 116 mAh
total, 9.09 mAh screen-off and 4.5 seconds of actual screen-off partial
wakelock. Its package was unfrozen and the hourly snapshot job was scheduled
when the capture was taken. Its use is being monitored, but this snapshot
does not show it as the cause of the longest idle interval's battery loss.

The raw snapshot is kept outside Git at
`C:\Users\adaredu\work\battery-current-20261116.txt`. BatteryStats was not
reset. A separate bedtime/morning capture remains useful for future comparable
nights and narrower wake-source attribution.

Sleep Mode no longer emits the result/status toast after a tile action. The
service still records the outcome in Android logs, refreshes the Quick Settings
tile and releases its temporary wakelock; an exception while starting or
executing the action can still show the generic failure toast.

### ADB follow-up (12:45 CST, 16 November)

A second read-only `dumpsys batterystats --charged` capture, taken while the
phone was connected for development, reported 36–40 percentage points of
discharge since its last full charge, with 32 points while the screen was on
and 8 while off. This is a charge-cycle aggregate, not an overnight interval;
it cannot attribute the user's recurring night loss by itself.

The strongest app-specific screen-off lead in this aggregate is WhatsApp's
`com.whatsapp:backupdb` wakelock: 59.5 seconds and approximately 0.47 mAh in
Android's model. This may include any screen-off time since the last charge,
not only sleep hours. System UID 1000 accumulated 16m05s on
`Prevent Sleep by AssistantDelay` (1,979 acquisitions); it is shared-system
accounting and the total is not aligned to one overnight window. Neither item
is proof of the cause. Nemotron Core's `Nemotron:SleepAction` remained short
(3.8 seconds across two acquisitions); Keyboard's WorkManager wake lock was
28ms. These readings do not support changing radio policy or disabling a
system package.

The one-charge model also charges 20.8 mAh to One UI Home (UID 10146); its UID
was classified in Android's `fgs` process state for 17h15m. On the live service inspection, that UID
was hosting Samsung Quickstep bound by SystemUI for gesture/Recents. This is a
candidate for follow-up only after Recents can be replaced and gestures
verified; killing or disabling it now would risk breaking navigation. Current
evidence still shows Android entering deep idle and about one displayed
percentage point over the previously measured 8h47 idle interval. Keep the
paired bedtime/morning capture to correlate timestamps before changing system
scheduling or services.

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
