# One UI Home status bar settling delay — 22 November 2026

## Behavior

One UI Home keeps Android's status bar visible only on its normal Home screen.
The launcher waits 100 ms after the existing screen-state, animation, and focus
checks succeed before enabling the launcher exception. Apps and app-switch
gestures remain immersive. This keeps the bar from appearing during a transition
while removing most of the previous 800 ms wait after arriving at Home.

The behavior is based on the v80 implementation in commit `86b58fd`. Its source
already contains the 100 ms settling delay; the APK that had been running on the
phone was the earlier v80 build with an 800 ms delay. The source does not change
Android's battery saver, navigation mode, or stock launcher installation.

## Candidate and installation

- Package: `com.sec.android.app.launcher`
- Candidate SHA-256: `406a11a4484c338a2fa2419cc9ca9df25668d6ca9070f828ec4f679342e834c5`
- Stock system APK SHA-256: `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`
- Runtime: KernelSU bind mount in Zygote's mount namespace; the APK under
  `/system/priv-app/TouchWizHome_2017/TouchWizHome_2017.apk` remains stock.
- KernelSU Session Patches v1.9.1 (version code 11) packages this candidate along
  with the existing HoneyBoard, Settings app-list filter, and guarded SystemUI
  candidate boot hooks.

The v1.9 update was accepted by KernelSU and reports `update=true`, so it is
staged for the next natural boot. The phone was not rebooted for this update.

## Verification on the Galaxy A54

- At 80 ms after returning Home, the policy had already selected
  `immersive.status=apps,-com.sec.android.app.launcher`; after settling, Window
  Manager reported the status-bar inset visible.
- In YouTube Music, the policy changed to `immersive.status=apps` and Window
  Manager reported the status-bar inset hidden.
- The Zygote-visible launcher APK hash matched the candidate hash above. The
  stock APK hash under `/system` was unchanged.
- No device reboot was performed.

## Open launcher folders (24 November 2026)

The user reported that opening a Home folder hid the status bar and could leave
the immersive policy stuck. `HoneyScreenManager` exposes `isOpenFolderMode()`;
the previous helper only accepted `isNormalHomescreen()` after transitions and
animations had ended, so folder mode was treated as a state that should hide the
bar. The helper now recognizes open folders explicitly and keeps the launcher
exception during folder transitions and while a folder is open. Apps, Recents,
and app-switch gestures continue to use the base immersive policy.

The v87 candidate was rebuilt from the exact active v100 ms candidate, changing
only `HomeOnlyStatusBar` behavior. SHA-256:
`3a21d458abd33c29f1f531b0822c2020ade72d6ecfb9ad4808e4cad159e3ab19`.
KernelSU Session Patches is updated to v1.9.2 (version code 12) so this
folder-aware candidate is mounted in Zygote after startup. The live update does
not require a phone reboot.

On the A54, the v87 Zygote and launcher hashes matched. With a folder open,
`policy_control` remained `immersive.status=apps,-com.sec.android.app.launcher`
and Window Manager reported the status-bar inset visible. Opening WhatsApp
changed the policy to `immersive.status=apps` and hid the inset; returning Home
restored the launcher exception and visible inset. The phone was left on Home;
no reboot or SystemUI restart was performed.

## Rebuild

Build a stock-derived candidate with
`apps/nemotron-ui/build/prepare-stock-oneui-root-candidate.ps1` using the
committed 100 ms `HomeOnlyStatusBar.smali`, then package it with
`tools/kernelsu/nemotron-session-patches/build-update.ps1`. The package builder
checks the candidate and Settings APK hashes before creating the module ZIP.
