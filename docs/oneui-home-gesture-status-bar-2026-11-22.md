# One UI Home status bar: gestures and app launches

## Required behavior

Show the status bar on the normal launcher Home screen. Hide it in immersive
apps, Recents, and while switching between apps. Show it as soon as the Home
gesture selects Home, and hide it before an app launched from a Home icon begins
its opening animation. Preserve the existing back gesture and Recents behavior.

## Home gesture timing and the v83 cleanup fix

One UI Home knows the gesture destination in
`RecentsAnimationManager.setEndTarget(GestureEndTarget)`, before Home regains
window focus. `patch-oneui-behavior.ps1` hooks that method and calls
`HomeOnlyStatusBar.onHomeGestureTargetChanged()` with `GestureEndTarget.isHome()`.
This enables the Home exception as soon as Home is selected.

Samsung later calls `setEndTarget(null)` while cleaning up a completed
transition. Treating this cleanup as a non-Home destination hid the bar again
and caused a brief flash. The hook ignores null targets. Explicit app and
Recents targets still revoke the Home exception. `HomeOnlyStatusBar` retains
that decision through the transition and clears the temporary latch after Home
is stable. Horizontal Quick Switch remains guarded separately.

## App opening from Home: the v84 fix

The supplied `mira.mp4` showed a separate regression: when an app opened from a
normal Home icon, its first frames briefly used the wrong top inset and shifted
the content. `Launcher.onPause()` was too late to correct this reliably.

Home icon taps go through the One UI implementation of
`HoneySystemController.startActivity`. The patch now clears the launcher
exception immediately before each direct launch, and before its callback/task
launch branch. It sets an `appLaunchActive` latch so delayed Home-state
callbacks cannot restore the status bar over the incoming app. The latch clears
when One UI Home regains window focus or when a new Home gesture selects Home.
Recents task launches do not use this Home icon path, so their existing
behavior is preserved.

## Build and live verification

- Stock Samsung `TouchWizHome_2017.apk` SHA-256:
  `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`.
- Current runtime candidate v84 SHA-256:
  `5404c7e10cff0dbd8276326f855f9f976b508725c8085b1f43312eea10a60df5`.
- Candidate verified with APK Signature Scheme v3. It is mounted by root; it
  is not installed through Package Manager. The APK at `/system` remains stock.
- Live A54 verification: Zygote and the Launcher process both report the v84
  hash; `/system` still reports the stock hash.
- Opened YouTube Music by tapping its Home icon. The screen recording shows the
  app entering without a status-bar flash or the delayed downward content shift.
  During the app, `policy_control` is `immersive.status=apps`.
- Swiped back to Home. The status bar was visible there and
  `policy_control` became `immersive.status=apps,-com.sec.android.app.launcher`.
- The Home gesture was repeated after the app launch. The exception returned
  correctly. No System UI or phone restart and no battery-setting change was
  made.

## KernelSU persistence

KernelSU Session Patches v1.6 (version code 7) contains the v84 candidate and
boot controller. It also retains the Settings app-list filter and the existing
keyboard boot support. KernelSU accepted the module and reports
`update=true`; it is staged for the next natural reboot. The phone was not
rebooted. At boot, the module validates the candidate hash, stages the APK, and
reapplies the Zygote mount.

The One UI controller labels the validated candidate
`u:object_r:system_file:s0`. On this A54, labeling a staged APK as `apk_data_file`
is denied from the KernelSU shell; `system_file` succeeds. The Settings and
HoneyBoard controllers in the v1.6 source use the accepted label as well.

Generated APKs and module ZIPs are local build artifacts and are not committed.

## Rebuild

Build the candidate from the matching stock APK with
`apps/nemotron-ui/build/prepare-stock-oneui-root-candidate.ps1`. Package the
boot-persistent module with
`tools/kernelsu/nemotron-session-patches/build-update.ps1`, supplying the
matching Settings candidate as `-SettingsCandidateApk` and Samsung Phone
candidate as `-DialerCandidateApk`. The build checks all candidate hashes
before creating the ZIP.
