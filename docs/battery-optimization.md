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
