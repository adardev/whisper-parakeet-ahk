# One UI Home status bar and immersive Quick Settings — 2026-11-21

## Scope

This note records the current One UI Home Zygote patch work, the reduced Home status-bar delay candidate, and the exploratory SystemUI hook for opening Quick Settings with one swipe from an immersive app. It does not claim that an unverified build is active.

## One UI Home status-bar behavior

The live Zygote/launcher image was checked at SHA-256 `f961a9a156b40f464503d2fdd72baa42602bfaed878ddb9d20c146963cee1b14`; the stock APK at `/system` remains `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`. The active build had a Home status-bar delay of 800 ms.

`HomeOnlyStatusBar.smali` now uses a 100 ms delay (`0x64`) instead of 800 ms (`0x320`). Existing guards still defer showing the bar during app focus, launcher transition, or animation states; the change only reduces the Home reveal delay. A candidate was rebuilt from the stock Samsung APK with the repo's stock-derived patch workflow and passed APK v3 signature verification:

- Candidate: `oneui-home-statusbar-100ms-20261121.apk`
- SHA-256: `406a11a4484c338a2fa2419cc9ca9df25668d6ca9070f828ec4f679342e834c5`
- Source stock SHA-256: `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`

This 100 ms candidate was built but not activated on the phone in this session. The phone remains on its existing v80 image. No device reboot was performed.

## Immersive Quick Settings investigation

On the tested phone, swiping from the top edge inside an immersive app requests a transient status bar but does not expand Quick Settings on the first swipe. The patcher in `build/systemui-quick-settings-immersive/patch-systemui.ps1` hooks the SystemUI transient-status-bar callback and asks the existing `CommandQueue` to expand the settings panel.

The first experiment retained a fullscreen guard and did not open the panel. A second candidate removed that guard and directly invokes the existing panel expansion API:

- SystemUI v2 candidate SHA-256: `FF77A58640918042E8E903D2A76DA804B2D1F825E4A2D67E5D6E5586CF7668B3`
- State: compiled and signed; not confirmed as active or working on the phone.

The candidate still needs a safe on-device verification before being treated as complete. No further SystemUI restarts were done for this note. The patcher is retained as source so the build can be reproduced; generated APKs and screen recordings are intentionally excluded.

## Gemini Home shortcut

Gemini's Home shortcut was identified as item `id=9660` in `HomeOnly.db`, component `com.google.android.apps.bard/.shellapp.BardEntryPointActivity`. The Gemini package itself is installed. The current launcher context menu offers `Disable`, not a clearly safe `Remove` action. No database row was deleted and Gemini was not disabled; the requested Home-only shortcut removal remains pending rather than risking a package-wide disable.

## Reproduction and validation

Use `prepare-stock-oneui-root-candidate.ps1` with the stock APK and local Android build tools to reproduce the Home candidate. Use `systemui-quick-settings-immersive/patch-systemui.ps1` against a decoded matching SystemUI APK to reproduce the Quick Settings candidate. Compare the output hashes before deployment. Deployment requires verifying the exact live candidate and a rollback path; this note makes no claim that v2 was deployed.
