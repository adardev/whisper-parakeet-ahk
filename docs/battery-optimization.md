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
- The phone was at 100% and connected over USB while inspected, so this pass
  does not claim a measured battery-percentage or mAh reduction.
- The updated source APK has not been installed. Rebuilding the integrated APK
  requires merging the A54 HoneyBoard donor into the base APK before merging
  One UI Home. The first integration attempt used the unmerged base and stopped
  at the expected missing HoneyBoard scheduler classes. The existing installed
  Nemotron launcher and keyboard were left in place.
- Build and tool caches are placed on E: under `E:\NemotronTemp`; the temporary
  decode tree from the failed integration is also there and can be removed
  after the integrated build is completed.
