# Status bar on the Home gesture (One UI Home)

## Goal

Show the status bar as soon as Samsung's gesture handler has selected **Home** as
the destination. Keep it hidden in immersive apps, Recents, and app-to-app swipe
gestures. The normal Home screen is the only destination where this patch allows
the launcher exception.

## Root cause and fix

Waiting for `Launcher.onWindowFocusChanged(true)` revealed the status bar too
late: it ran after the Home animation had started. One UI Home already knows the
gesture destination earlier, in
`RecentsAnimationManager.setEndTarget(GestureEndTarget)`. The patch in
`apps/nemotron-ui/build/patch-oneui-behavior.ps1` calls
`HomeOnlyStatusBar.onHomeGestureTargetChanged()` there, using
`GestureEndTarget.isHome()`.

One UI Home also calls `setEndTarget(null)` while cleaning up a completed
transition. Treating that cleanup as a non-Home destination immediately hid the
bar again and caused the brief flash. The hook now ignores a null target. An
explicit `APP` or `RECENTS` target still revokes the launcher exception.

`HomeOnlyStatusBar` keeps the Home gesture state through the transition and
reasserts the immersive policy from the existing screen-state callbacks. Once
Home is stable, it clears the temporary gesture latch while leaving the normal
Home exception enabled. The separate external-app gesture guard remains in
place, so horizontal quick-switch gestures do not reveal the bar.

## Build and live result

- Source APK: Samsung stock `TouchWizHome_2017.apk`, SHA-256
  `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`.
- Runtime candidate: `oneui-home-homegesture-null-guard-20261121.apk`, SHA-256
  `fc48b7df73308343eddfa7694d3bfa634d5a7818f004c96476867e72ae6a882e`.
- Signature: candidate verified with APK Signature Scheme v3. It is a
  root-mounted runtime candidate; it is not installed through Package Manager.
- The system APK remains stock. KernelSU mounts the candidate in Zygote's mount
  namespace and restarts only `com.sec.android.app.launcher` to refresh it.
- Live check on the A54: Zygote and Launcher both reported the candidate hash;
  `/system` continued to report the stock hash.
- Gesture check: when WhatsApp was foreground, policy was
  `immersive.status=apps`; after the Home gesture it became
  `immersive.status=apps,-com.sec.android.app.launcher`. Two repeated
  WhatsApp-to-Home cycles retained that result after the transition cleanup.
- No phone reboot, System UI restart, battery-setting change, or change to the
  back gesture was made.

## KernelSU persistence

KernelSU Session Patches v1.5 (version code 6) was packaged with the v83 One UI
candidate, Settings app-list filter candidate, and boot controller. The boot
hook also retains support for the existing HoneyBoard candidate in the
candidate store. KernelSU accepted the ZIP and reported
`nemotron-session-patches` with `update=true`. This means the package is staged
for the next natural reboot; the phone was deliberately not rebooted. The boot
hook validates candidate hashes, prepares the One UI APK, and reapplies its
Zygote mount. The APK under `/system` remains untouched.

The One UI controller labels the verified candidate `u:object_r:system_file:s0`.
On this A54, `chcon` to `apk_data_file` is denied from the KernelSU shell, while
the `system_file` label succeeds. The candidate hash is checked before mounting.

The v1.5 module archive also carries the existing Settings filter and keyboard
boot support. Generated APKs and ZIPs are local build artifacts and are not
committed.

## Rebuild

Build the runtime candidate from the matching stock APK with
`apps/nemotron-ui/build/prepare-stock-oneui-root-candidate.ps1`, then package the
boot-persistent module with
`tools/kernelsu/nemotron-session-patches/build-update.ps1`. Supply the matching
Settings candidate as `-SettingsCandidateApk`; the build script verifies both
candidate hashes before creating the ZIP.
