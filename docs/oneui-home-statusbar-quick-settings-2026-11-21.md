# One UI Home status bar, Gemini shortcut, and immersive Quick Settings — 2026-11-21

## Home status bar timing and startup persistence

The Home-only status-bar hook keeps the bar hidden during app focus and external Quick Switch gestures. When One UI Home reports the normal Home state, regains window focus, and is no longer transitioning, it now changes the immersive exception synchronously in that callback. This removes the extra main-loop turn that previously delayed the status bar relative to Home's Live notification animation. The external-gesture guard is cleared only when Home regains focus; Recents/app transitions remain immersive.

The stock Samsung APK at `/system` has SHA-256 `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`. The current live Zygote and launcher APK have SHA-256 `193b3d20f286c44b14284000d8406fab5e0b8e9a7275dc8718dfa2ddd5d8b96a`.

- Candidate: `oneui-home-instant-focus-20261121.apk`, rebuilt from stock and verified with APK Signature Scheme v3.
- Applied in this session with the KernelSU Zygote mount controller. Only One UI Home was refreshed; neither the phone nor System UI was restarted.
- After returning Home, `policy_control` was observed as `immersive.status=apps,-com.sec.android.app.launcher`; `/system` stayed stock.
- KernelSU module v1.2 was installed with the candidate embedded. KernelSU reported `update=true`, so the module update is staged for the next natural reboot. The phone was not rebooted. Its boot hook prefers the embedded v81 candidate, verifies its hash, and then applies the Zygote mount; HoneyBoard and System UI keep their existing candidate-store fallback. The unique v81 filename avoids replacing an older APK owned by another SELinux context in `/data/local/tmp`.
- `build-update.ps1` recreates the KernelSU module ZIP from the APK candidate. The APK itself is generated locally and is not committed to the repo.

## Gemini Home shortcut removal

The Home icon was stored in more than one launcher layout database, which is why deleting only the first row caused it to return. The `Gemini` app shortcut rows were removed from:

- `HomeOnly.db`: one Home-grid shortcut.
- `OneUI.db`: one Home-page shortcut and one shortcut inside a Home folder.

The matching component was `com.google.android.apps.bard/.shellapp.BardEntryPointActivity`. The launcher's `nemotron_native_allowlist` rows were left intact. Gemini's package remains installed and enabled; no package hide, disable, or uninstall was performed. A fresh Home capture confirmed the Gemini icon is gone.

Consistent pre-edit backups are on the phone at `/data/local/tmp/HomeOnly-gemini-backup-2221.db` and `/data/local/tmp/OneUI-gemini-backup-2221.db`.

To edit the app-private databases while SELinux stayed Enforcing, a live KernelSU policy grant was added for its root domain to traverse the relevant data directories and read/write `app_data_file`. That policy addition is live-only and will be cleared by the next reboot; it was not added to a boot module. An attempted `adb_data_file` grant did not provide access to the installed module store, so the persistent candidate store remains unchanged.

## Immersive Quick Settings investigation

On the tested phone, a top-edge swipe inside an immersive app requests a transient status bar but does not expand Quick Settings on the first swipe. The patcher in `build/systemui-quick-settings-immersive/patch-systemui.ps1` hooks the SystemUI transient-status-bar callback and asks the existing `CommandQueue` to expand the settings panel.

The first experiment retained a fullscreen guard and did not open the panel. A second candidate removed that guard and directly invokes the existing panel expansion API:

- SystemUI v2 candidate SHA-256: `FF77A58640918042E8E903D2A76DA804B2D1F825E4A2D67E5D6E5586CF7668B3`
- State: compiled and signed; not confirmed as active or working on the phone.

No SystemUI restart was done for this test. The patcher is kept as source; generated APKs and recordings are excluded.

## Reproduction

Use `prepare-stock-oneui-root-candidate.ps1` with the stock APK and local Android build tools to reproduce the One UI Home candidate. Use `systemui-quick-settings-immersive/patch-systemui.ps1` against a decoded matching SystemUI APK to reproduce the Quick Settings candidate. Compare hashes before deployment and verify the matching KernelSU mount controller state.
