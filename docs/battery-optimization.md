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

- `:app:compileDebugKotlin` and `:app:assembleDebug` succeeded with JDK 17.
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
