# Nemotron battery work — 18 October 2026

## Change

`AntiScrollAccessibilityService` no longer subscribes to every
`TYPE_WINDOW_CONTENT_CHANGED` event or to Android Settings. It no longer asks
Shizuku to run `dumpsys activity` or `uiautomator dump` from scroll callbacks.
The guard uses window-state, scroll, click, and tab-selection events, and only
reads the accessibility tree on service connection or a window transition.
It also stopped requesting unimportant views, which reduced the trees Android
has to provide. The existing feed, Search, Reels, Direct, profile, and WhatsApp
Status checks remain event-driven.

The One UI merge script no longer invokes the obsolete Java build step for the
removed custom icon-return animation. The launcher keeps its native transition
path.

## Verification and limits

- `:apps:nemotron:compileDebugKotlin` and `:apps:nemotron:assembleDebug` succeeded with JDK 17.
- The A54 reported Nemotron as both the HOME role holder and active input
  method. HoneyBoard's statistics jobs were not running at the time of the
  snapshot.
- The integrated Nemotron + HoneyBoard + One UI Home APK was installed on the
  A54. The HOME role and HoneyBoard input method remained selected, and the
  launcher stayed running without a reboot.
- A 30-second idle sample of `/proc/<pid>/stat` showed 10 CPU ticks, about
  0.33% of one core. The phone was connected over USB; this is an idle CPU
  sample, not a battery-percentage or mAh comparison, and no valid pre-patch
  baseline was captured.
- During the first integrated install, an interrupted Apktool build left an
  invalid DEX and the app crashed. One UI Home was temporarily restored as the
  HOME role, then Nemotron was reinstalled after all 23 DEX files passed
  `dexdump` validation. The merge script now forces DEX regeneration so a
  partial file cannot be reused after a failed build.
- Build and tool caches are on E: under `E:\NemotronTemp`; 13.1 GB remained
  free after installation. Several failed-build decode trees remain there;
  automatic recursive cleanup was blocked, so they were left untouched.

## BBVA compatibility: make the accessibility guard opt-in — 18 October 2026

The A54 showed Nemotron's `AntiScrollAccessibilityService` enabled globally.
Although its event filter only accepts Instagram and WhatsApp, BBVA rejected
access while that service was enabled; the user confirmed BBVA allowed access
after it was paused. `SecureStore` had hard-coded the anti-addiction guard to
`true`, and its Settings row was not a switch, so Nemotron re-enabled the
service on app startup. DNS enforcement was also incorrectly treated as a
reason to keep Accessibility enabled.

The guard is now a real opt-in switch in Settings and migrates the old forced
state to off once. DNS monitoring starts from `NemotronApp` independently, so
it no longer requires the global Accessibility service. The integrated APK
(Nemotron + HoneyBoard + One UI Home) was rebuilt and installed on the A54.
Verification showed Nemotron as HOME and the selected IME, Samsung's standalone
keyboard disabled, and no enabled or bound Nemotron Accessibility service. The
device was not rebooted. The Instagram/WhatsApp anti-scroll guard remains off
until explicitly enabled; enabling it may again cause banking apps to refuse
access. BBVA was not reopened after this install, as the user had already
confirmed the temporary pause restored access.

## DND keep-alive state check — 18 October 2026

An A54 idle snapshot showed `DndKeepAliveService` running while DND lock was
enabled, the anti-scroll guard was off, and the Fit3 plugin appeared in
`pm list packages -d` (frozen). The service is intended only while Fit3 is
unfrozen. The old check treated a missing Shizuku response as “Fit3 is active,”
which could start or retain the foreground service without a confirmed need.

`ShizukuManager` now distinguishes a valid disabled-package list from a failed
query, parses exact package names, and treats a command timeout as failure.
`DndKeepAliveService.update()` exits before querying Shizuku when DND lock is
off or the guard is already active. It keeps the service only when Shizuku
confirms Fit3 is unfrozen; unknown state stops the service because Shizuku is
also required for the lock action itself.

Before the change, Android's accumulated estimate for Nemotron was 14.4 mAh
(10.2 mAh attributed to CPU), including 7.64 mAh of estimated background use
and 4 h 59 m in the background. The device's battery-statistics period was
5 h 34 m on battery, with 2 h 17 m interactive. Android groups the integrated
launcher and keyboard under this one UID, so this total cannot isolate either
feature. These are system estimates over the existing statistics period, not
a controlled daily measurement.

`:apps:nemotron:compileDebugKotlin` and `:apps:nemotron:assembleDebug` succeeded. The two updated
classes were transferred into the existing integrated One UI + HoneyBoard
tree, rebuilt with Apktool, zip-aligned, and signed with Nemotron's existing
debug key. `apksigner` verified it; `aapt` confirmed the Nemotron package,
HoneyBoard IME service, One UI `LauncherActivity` HOME alias, and all 23 DEX
files. It was installed over ADB at 22:44 on 18 October. Verification showed
Nemotron remained the default Home app and keyboard. `DndKeepAliveService` was
no longer running after install, while the HoneyBoard IME remained bound.

On the previous build, matched 180-second screen-off samples measured 0.16 s
CPU (0.09% of one core) before installation. The integrated build measured
0.12 s (0.07%) immediately after installation, with the same 85% battery
level. The 0.04-second difference is too small to establish a battery saving.
Both 3-minute samples showed a 5 mAh gauge-counter step, below the precision
needed to attribute that change to Nemotron. A separate 60-second screen-off
sample was noisier at 0.41 s (0.68%); this variation shows why one-minute
samples are not a reliable daily drain estimate.

The device was in forced Doze for those samples (`mForceIdle=true`). That
diagnostic override was removed (`mForceIdle=false`), restoring natural Doze.
In a subsequent 180-second screen-off sample in natural Doze, the process
accumulated zero CPU ticks; the battery remained at 85% and its 4265 mAh
counter reading was unchanged. The integrated build's 180-second sample while
forced Doze also showed no active `DndKeepAliveService`.

The HoneyBoard WorkManager database contains 40 one-time context-session
records: 30 `ContextSessionSeparateWorker` and 10
`ContextSessionMaintainWorker`. All 40 are `CANCELLED`, have a zero interval,
and none is enqueued, running, or periodic. The main Nemotron and One UI
WorkManager databases have no work records. JobScheduler history for IDs 3113,
3114, 3117, and 3152 corresponds to short HoneyBoard context-session work;
across 14 starts it used 249 ms realtime, including 113 ms background and
98 ms of partial wakelock time. The latest run took 17 ms. No running
background job or service was present at the screen-off check. These jobs
therefore are not a repeating idle workload.

Nemotron had one scheduled alarm for `FIT3_AOD_SCHEDULE_TOGGLE` at 23:00,
the existing daily Fit3 AOD schedule. During a natural-Doze sample spanning
that alarm, Wi-Fi ADB disconnected before the ending CPU and charge readings,
so that window is incomplete. The prior 180-second natural-Doze sample
completed with zero CPU ticks and an unchanged charge counter. `mForceIdle`
was false at the final connected check; no forced-Doze override was left on.

The battery-statistics estimate was 14.6 mAh for the shared UID since 17:13,
with 4.10 mAh attributed to screen-off/doze CPU and 101 ms of screen-off
wakelock time. This is about 0.08% of the 5000 mAh nominal capacity for the
whole recorded screen-off CPU estimate, not a daily measurement. The UID
combines keyboard, launcher, and Nemotron components. Short samples cannot
verify a full-day percentage; keep the device in normal use for a day before
claiming it restored the previous ~0.4% daily figure.

## Deduplicate Shizuku startup recovery — 18 October 2026

`NemotronApp` receives Shizuku's binder callback and also runs a 2.5-second
fallback because some Shizuku versions do not notify listeners when the binder
was already available at process start. Both paths previously repeated the
same alarm scheduling, night-mode setup, auto-freeze recovery, accessibility/DNS
setup, car detection, and Android Auto reconciliation when they raced.

The paths now share an atomic per-process gate. A successful setup runs once
for each Shizuku binder lifetime; binder death clears the gate so the next
connection can recover normally. This removes duplicate startup work while
keeping the fallback and features intact. It is a bounded startup optimization,
not evidence by itself that daily drain has been fixed. The source compiled;
the updated NemotronApp smali and its generated callback classes were grafted
into the existing integrated Home + HoneyBoard APK, rebuilt with all 23 DEX
files, zip-aligned, and signed. The signer SHA-256 matched the installed
Nemotron build. The APK was installed without rebooting. Android still reports
Nemotron as both the HOME role holder and selected HoneyBoard input method;
Doze remained natural (`mForceIdle=false`).

A 60-second screen-on idle sample after installation recorded 0 CPU ticks for
the Nemotron process (below the 10 ms/tick measurement resolution). The
previous 60-second sample on the earlier build recorded 7 ticks (0.07 s, about
0.12% of one core). This small difference is consistent with idle and cannot
establish a battery saving. Android continues to aggregate launcher, keyboard,
and Nemotron under the same UID; a full-day battery comparison is still needed
to quantify any change in charge use.

## Crash loop after partial DEX update — 19 October 2026

The A54 then showed repeated Nemotron process crashes. `logcat` and
`ApplicationExitInfo` identified the same exception each time:
`NoClassDefFoundError` for
`ShizukuManager$getDisabledPackages$1`, called by
`AutoAndroidAuto.reconcile()`. The earlier integrated APK update replaced the
NemotronApp smali family without also bringing over the newly compiled
ShizukuManager lambda classes. This was an incomplete DEX integration, not a
HoneyBoard periodic worker or an Android Doze issue. Android fell back to
Samsung Home and Google TTS after the repeated process failures.

The repair copied the complete compiled `ShizukuManager*` family (including
both missing generated classes), the DND service class family, and the current
NemotronApp family into the integrated tree, then rebuilt all 23 DEX files,
aligned and signed the APK. It was installed without rebooting. The launcher
opened successfully; Nemotron was restored as HOME and as the selected input
method. `ApplicationExitInfo` then showed only the expected process stop from
the package update, with no new crash entry in the post-install check. Doze
remained unforced (`mForceIdle=false`). The Samsung system launcher remains
available as a recovery option.

## Screen-off audit and removal of idle chat redraws — 19 October 2026

The A54 was in natural Doze (`mWakefulness=Dozing`) with Nemotron running as
the integrated Home and selected keyboard. The live process showed 0.0% CPU
in the instantaneous `top` sample and about 301–325 MB resident memory. No
`DndKeepAliveService`, voice-recording service, or health-transfer service was
running. Five Quick Settings services were bound by System UI; these are tile
bindings, not foreground work. The shared UID's current BatteryStats estimate
was 6.02 mAh total, including 2.32 mAh while backgrounded; screen-off/doze
CPU was estimated at 0.389 mAh. These figures cover the current BatteryStats
window, not a full day, and aggregate Nemotron, One UI Home, and HoneyBoard.

A 60-second screen-off process sample accumulated 187 CPU ticks. The device's
kernel reports 100 ticks/second, so that sample was about 1.87 CPU seconds
(3.1% of one core for that minute). USB charging was connected, which makes
the interval unsuitable for measuring actual battery decline. `top` showed
all threads asleep at the later instant; the short sample does not identify a
steady repeating worker or prove that the same rate persists across a day.
This is materially different from the earlier three-minute zero-tick sample,
so the process needs a longer unplugged sample before attributing daily drain
to any one component.

Source review found one avoidable periodic UI task in `ChatActivity`: while a
response was pending, a Handler rewrote and scrolled to the same single
“adarbot is thinking…” label every 700 ms. `onPause()` stopped remote polling
but did not stop that redraw loop. The label is now left static until a token
arrives, removing the needless main-thread/UI work even if the app is paused.
This is a targeted reduction; it does not establish that the integrated Home
or keyboard caused the screen-off CPU sample. No persistent service was
disabled because the live audit did not find one active and redundant.

### Follow-up idle sample — 19 October 2026

After installing the chat redraw change, the A54 was locked with the display
off and in natural Doze. Nemotron's process accumulated **0 CPU ticks over 60
seconds** at a 100 Hz kernel tick rate. This is below the 10 ms accounting
resolution for process CPU time. The phone was connected to USB, so this sample
confirms the app was idle during that minute but cannot quantify battery
discharge.

The package had no repeating alarm. Its only app alarm was the configured
`FIT3_AOD_SCHEDULE_TOGGLE` for 23:00. Two recent HoneyBoard WorkManager jobs
started but were cancelled while waiting to bind `SystemJobService`; each used
about 10 ms before cancellation. No persistent foreground service was found.
These scheduler entries do not explain ongoing CPU drain in the observed idle
minute. Battery percentage rose from 92% to 93% while USB was connected, so it
cannot be used as a discharge comparison.

### Unplugged Doze sample — 19 October 2026

ADB was moved to Wi-Fi and the USB cable was disconnected. With the screen
off, `mForceIdle=true`, and deviceidle at `IDLE`, Nemotron's CPU time increased
by 37 ticks over five minutes (**0.37 CPU seconds**, or about 0.12% of one
core during the interval). The battery stayed at 94%, and Android's charge
counter stayed at 4,700,000 µAh; its displayed resolution is too coarse to
measure a few minutes of low idle drain. A separate two-minute transition into
idle had a one-off 1.68 CPU seconds, but the following five-minute sample did
not repeat that rate. Per-thread sampling during the intervening minute showed
0.06 CPU seconds total, with the largest thread delta 40 ms (`RxSchedulerPurg`).

The process remained alive as the selected Home/keyboard package, with no
new crash record. BatteryStats continued aggregating Nemotron, Home, and
HoneyBoard under UID `u0a421`; its background-time counter is not equivalent
to CPU-active time. The unplugged sample shows low CPU activity at idle, but
is still too short and too coarse to validate a full-day battery percentage.

### Device Care attribution and retained crash notice — 19 October 2026

The A54's Device Care screen showed Nemotron at 1.5% and the stale crash
message. At the same time, `dumpsys batterystats --charged` reported 14.8 mAh
for the shared UID: 14.5 mAh attributed to screen use and 0.305 mAh to CPU.
The screen-off/doze CPU estimate was 0.140 mAh; total process CPU since the
current stats window began was about 5.2 seconds. This confirms the UID has
consumed energy, while showing that most of the estimate is display use with
Nemotron/Home in the foreground, not persistent background CPU work. Android
does not separate the integrated Home, HoneyBoard keyboard, and Nemotron app
inside this UID.

`ApplicationExitInfo` and DropBox still contain the earlier crashes from
07:30–07:33, caused by a missing generated
`ShizukuManager$getDisabledPackages$1` class in an incomplete DEX graft. The
complete generated class family was included in the later integrated APK
installed at 09:10. Since that install, exit-info records package-update stops
only and no crash; Device Care's crash label is retained history, not a new
crash in the current build.

### Device Care follow-up and receiver reduction — 19 October 2026

The A54 still reports battery use for Nemotron, so the report is not dismissed.
The latest live BatteryStats snapshot now shows 28.1 mAh for shared UID
`u0a421`, including 27.8 mAh attributed to screen and 0.311 mAh to CPU; the
screen-off/doze CPU estimate remains 0.140 mAh. The sample window is only about
22 minutes and includes foreground Home use, so it cannot be compared directly
with One UI Home's full-day 0.1% report. Android continues combining Home,
HoneyBoard, and Nemotron in this UID.

Code review found two avoidable event sources in the integrated application:
Nemotron registered `CarConnectionReceiver` dynamically even though the same
USB receiver already exists in the manifest, and its network policy listened
to the deprecated broad `CONNECTIVITY_ACTION` in addition to Wi-Fi state and
the `mobile_data` setting observer. The dynamic duplicate USB registration was
removed. The network policy now listens to Wi-Fi state and airplane-mode state;
mobile-data changes remain covered by the setting observer. These changes
reduce duplicate event handling and preserve the existing car and radio
policies. A post-install idle comparison is still needed; this edit alone does
not prove a daily battery reduction.

The updated integrated APK was rebuilt from Gradle, merged with HoneyBoard and
One UI Home, signature-verified with the existing Nemotron debug certificate,
and installed on the A54. Android retained Nemotron as HOME and as the active
keyboard. The newest exit-info entry is the expected `PACKAGE UPDATED` stop;
the post-install logcat check showed no runtime exception. During a 30-second
idle interval after installation, the Nemotron process accrued 0 CPU ticks.
BatteryStats showed 64.2 mAh for the shared UID (63.6 screen, 0.562 CPU), with
0.140 mAh in screen-off/doze CPU. The screenshot's 1.9% is therefore accepted
as Device Care's reported usage; these counters do not support calling it zero
or proving a full-day reduction. The short CPU sample only says the process was
idle in that interval.

### DND service tied to a live Fit3 connection — 20 October 2026

The A54 reported `DndKeepAliveService` as an active foreground app even with
Do Not Disturb off and the Fit3 disconnected. It restarted after the user
stopped it because the service returned `START_STICKY`. Its start condition
checked whether the Fit3 plugin package was enabled, not whether the watch's
GATT connection was live. Split HOME/keyboard packages also made an old role
check fail and left the service running.

The service now checks the saved Fit3 Bluetooth address against live GATT
connections. It starts only while Fit3 is connected (and the accessibility
guard is off), returns `START_NOT_STICKY`, and stops on Fit3 ACL disconnect or
Bluetooth shutdown. The Fit3 tile reevaluates it after each connection toggle.
This keeps the DND receiver available only while the connected watch makes that
Nemotron feature relevant. The existing DND lock remains enabled in app settings.

After installing the Core update twice on the A54, Bluetooth reported no GATT
connections and `dumpsys activity services com.nemotron.core` no longer listed
`DndKeepAliveService`. Application startup logged that the disconnected Fit3
keeps the service stopped. This verifies the disconnected case; a reconnect
cycle and its subsequent ACL disconnect were not exercised in this check.

### Earlier active background service and Orca tile correction — 19 October 2026

A later live service dump found `DndKeepAliveService` running as a foreground
service even while Nemotron held both HOME and selected-IME roles. Those system
bindings already keep the integrated process available for its dynamic DND
receiver. `DndKeepAliveService.update()` now stops the extra foreground service
when both roles are held, while retaining its fallback for other configurations.

The same check found `AntiScrollAccessibilityService` in Android's enabled
accessibility list while `nemotron_prefs.xml` says the guard preference is
false. I cleared the enabled accessibility list during the device check; the
current list is empty and Nemotron Guard is off. This also disables Shizuku's
accessibility pairing helper, which Android marks as a restricted setting.

The installed APK had an older Orca tile implementation that opened Termux on
unfreeze and always selected that path. The new build removes that forced path;
unfreeze only enables Termux and prepares its RunCommand permission, while a
short tap on enabled Termux freezes it without opening its UI. The integrated
APK was rebuilt, signature-verified, and installed on the A54. A QS tile click
from ADB disabled Termux, and the app interface did not open.

After the update, Android retained Nemotron as HOME and the selected keyboard.
`DndKeepAliveService` no longer appears among active services; HoneyBoard stays
system-bound. Device Care later showed 2.3% for Nemotron, which is accepted as
the reported cumulative value. It does not isolate the period after this fix or
prove a lower daily total. Two 30-second samples after installation, with the
screen on and Samsung Health in front, counted 35 and 44 Nemotron CPU ticks
(0.35 s and 0.44 s at 100 ticks/s). The DND foreground service was absent in
both checks; HoneyBoard remained bound as the active keyboard. These are short
awake-screen samples, not a controlled screen-off baseline. A comparable idle
and full-day sample is still needed to quantify the battery effect.

### Screen-off CPU check — 19 October 2026

After Device Care showed 2.3% for Nemotron, a 50-second screen-off sample of
the Nemotron process recorded 8 user ticks and 5 system ticks at 100
ticks/second (0.13 seconds of CPU total). The phone was woken after the sample.
The process remained the system-bound HoneyBoard IME; `DndKeepAliveService`
was not active. No active Nemotron WorkManager jobs were listed, and the only
pending Nemotron alarm was the one-shot 23:00 airplane-mode schedule. This
short interval shows low idle CPU at measurement time. It cannot erase the
day's cumulative percentage or replace a full screen-off/day-long comparison.

### Fit3 tile and automatic health-transfer reduction

Code review found that turning the Fit3 tile off called `startAndWait()`, which
waited up to 240 seconds for both Samsung Health reads and Firebase retries and
refused to freeze the apps on any failure. Enabling the tile also started a
30-day/25-stream read and a six-attempt Firestore upload each time. The tile now
freezes immediately without depending on network success; automatic uploads
are limited to one attempt per local day, read only currently granted streams,
and have bounded Firebase timeouts/two upload attempts. Bluetooth connection
checks after a toggle are also bounded and the tile state refreshes before
those checks finish. Manual **Enviar ahora** remains available after a failed
automatic attempt. Device installation is pending because ADB was offline at
the time of this code change.

### Accessibility binding and live battery snapshot — 19 October 2026

The A54's live settings had `addiction_guard_enabled=false`, but Android still
listed and bound `AntiScrollAccessibilityService`. Its event set included
clicks, selections, window changes, and scrolls. I removed the sole enabled
accessibility component and turned the accessibility master switch off.
Verification showed an empty enabled and bound service list. HoneyBoard
remained bound as the selected input method. The optional guard stays available
in Nemotron and is currently off, matching its saved preference.

The same live audit showed no Nemotron foreground service and no running
Nemotron job. The only app alarm was the next daily Sleep Mode boundary. Two
recent HoneyBoard WorkManager jobs had been cancelled while waiting to bind
`SystemJobService`; the scheduler did not report a running job. No recurring
polling workload was visible in these snapshots.

BatteryStats had just reset its on-battery window while the phone was charging:
it showed about one second on battery and zero measured discharge. Its UID
estimate was 0.0546 mAh, of which 0.0489 mAh was screen attribution and 0.0057
mAh CPU attribution, almost entirely while plugged in. This is not a valid
discharge or daily-use measurement. Android still combines Nemotron, One UI
Home, and HoneyBoard under UID `u0a421`, so Device Care's cumulative percentage
cannot separate their contributions. A screen-off, unplugged sample is needed
to quantify drain after clearing the stale accessibility binding.

### One UI RxJava purge interval and post-install idle check — 19 October 2026

Thread inspection found One UI's bundled RxJava purge worker (`RxSchedulerPurg`).
Its scheduler defaulted to waking once per second and scanning scheduled
executors. The reproducible One UI patch retains queue cleanup and changes its
default interval to 300 seconds in builds that include this RxJava2 worker,
bounding cleanup delay to five minutes. The integrated APK was rebuilt,
verified with the same debug certificate as the installed APK, and installed
at 17:22. No source or data was deleted. The current installed UI APK instead
contains RxJava3 without that RxJava2 purge worker, so the patch skips it.

Post-install checks resolved HOME to the integrated One UI `LauncherActivity`,
kept HoneyBoard selected, showed no enabled or bound accessibility service, and
showed no Nemotron foreground service. Natural idle was restored after each
screen-off sample (`mForceIdle=false`, `mScreenOn=true` after waking).

The first 60-second screen-off sample immediately after the package update
counted 376 CPU ticks (3.76 seconds), consistent with a noisy process-start
window and not a steady-state result. After startup settled, a verified
60-second screen-off sample counted 40 ticks (0.40 CPU seconds, about 0.67% of
one core). The largest thread delta was `pool-8-thread-1` at 0.19 seconds; the
RxJava purge thread remained below the 10 ms display resolution. A previous
60-second screen-off sample before this patch counted 39 ticks (0.39 seconds),
so the total idle CPU sample does not show a measurable reduction yet. The
purge interval change reduces that worker's wake frequency, but its measured
CPU contribution was already small. The phone remained USB-powered, so neither
sample measures battery discharge. A further CPU reduction needs tracing the
`pool-8-thread-1` workload; a controlled daily battery comparison still needs
an unplugged interval.

### Keep HoneyBoard statistics jobs out of the split keyboard package — 24 October 2026

The packaged `Nemotron-Keyboard.apk` still contained two HoneyBoard services
that schedule periodic key-input and Keys Cafe statistics jobs. The earlier
One UI merge patch disabled these jobs only in its own integrated build; the
three-package split rebuilds Keyboard from the separate Core input, so that
patch did not reach the keyboard APK. The split step now applies the same
job-cancellation patch to both statistics scheduler services before signing
Keyboard. The live A54 check also showed its `RxSchedulerPurg` thread active;
the packaged RxJava purge default was still one second. The split step now
changes that interval to 60 seconds, matching the launcher patch and retaining
cancelled-task cleanup. ADB confirmed both daily statistics jobs were active
(IDs 1 and 2, each requiring idle and battery-not-low); I cancelled them and
verified there were no remaining scheduled job records. I rebuilt the current
Keyboard APK with both fixes, signed it with the existing debug key, and
installed it on the A54 at 13:40. Pulling the installed APK back from the
device produced the same SHA-256 as the build (`9e27639f…cb384495`); its
signer matches the previous package. Android still reports HoneyBoard as the
default input method, and no statistics job records appeared after the update.
The packaged DEX contains the 60-second RxJava purge interval. A controlled
post-install battery sample has not been taken, so the amount saved remains
unmeasured.

### Nemotron Home background policy and clipboard history bridge — 24 October 2026

The installed Home package had no active services, scheduled jobs, or alarms in
the inspection snapshot. Android also had not exempted it from Doze. I set
`RUN_IN_BACKGROUND` and `RUN_ANY_IN_BACKGROUND` to `ignore` for
`com.nemotron.voiceime2` to prevent background execution while keeping normal
Home foreground launches available. The device reported both app-op modes as
`ignore`, and Android still resolves Nemotron Home as the HOME activity. This
policy may delay future Home background work such as widget or refresh jobs; a
screen-off battery comparison is still needed to measure the effect.

#### RxJava 3 IO cleanup interval — 24 October 2026

Static inspection of the bundled Home DEX found RxJava 3's IO scheduler using
its default 60-second cached-worker eviction interval. The cleanup task wakes
to check expired idle IO workers even when there is nothing to evict. The
reproducible Home patch now changes that default to 300 seconds, while still
respecting an explicit `rx3.io-keep-alive-time` system-property override. This
reduces idle maintenance wakeups from about 60 to 12 per hour when that
scheduler is initialized; cached IO workers can remain allocated for up to
five minutes instead of one minute.

The rebuilt Home APK passed zip alignment and APK signature verification, has
all 23 DEX files, and uses the same signing certificate as the previous APK.
It was installed on the A54 and the installed APK hash matched the build.
The phone was USB-powered during the follow-up, so this is a targeted scheduler
reduction, not a measured battery-life result.

### Selective background service stop on screen off — 24 October 2026

Nemotron Core's Auto Freeze settings now list currently running service
components from `dumpsys activity services`. Selected components receive an
`am stopservice` request after the existing screen-off delay; the app verifies
all selected components against one post-stop service snapshot to avoid a
separate system dump for each selection. This stops only the selected service
when Android is not keeping it alive for an active binding. The picker marks
actively bound services as unavailable, and the screen-off path skips them.
For example, `com.sec.imsservice/...ImsUpdateService` is bound by the A54
telephony stack and must not be force-stopped with its package.

The Fit3 tile now also requests a stop for Samsung's
`com.samsung.accessory/.services.SAFrameworkService` after disconnecting Fit3.
Samsung can keep this component bound even after `am stopservice` reports
success. If no connected GATT, audio, HID, or PAN peripheral remains, Nemotron
disables and force-stops the Samsung Accessory package so SAP cannot restart
while Fit3 is off. The Fit3-on path enables and unstops Samsung Accessory
before enabling Bluetooth or asking Samsung Wearable to reconnect. When another
Bluetooth peripheral is connected, Nemotron preserves the shared package and
only requests a stop for the SAP component.

Nemotron Keyboard is split from Samsung's original package, so the system's
clipboard service sends history writes to the original package authority while
Nemotron's panel reads its separate database. A bridge now copies the current
primary clip into Nemotron's provider when the clipboard panel requests history,
including text and content URIs. The keyboard APK was rebuilt and installed
with the existing signing key, and the temporary diagnostic receiver was
removed from the final installed build. Functional verification is still
pending: the first test session reached the lock screen, and after the user
unlocked it ADB disconnected before the copy-and-panel check could run.
