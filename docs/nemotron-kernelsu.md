# Nemotron Core with KernelSU

Nemotron Core (`com.nemotron.core`) can request root from the installed SU
manager. In Setup, the **Request KernelSU root access** button executes only
`id` as a harmless check. KernelSU owns the per-app Superuser decision; the app
does not silently grant itself root. The button reports success only after the
result identifies UID 0.

After the user grants root, all privileged Nemotron Core operations use
KernelSU root exclusively. There is no Shizuku provider, binder, permission
path, or fallback. Fresh commands use an argv-based helper that quotes
arguments before calling `/system/bin/su -c`, merges output, and enforces a
timeout. Long file/state operations use a persistent root shell. Quick
Settings package tiles use `pm hide` and `pm unhide`, then force-stop the
selected package where appropriate. On restore, Core verifies the package is
enabled for user 0 and runs `pm enable --user 0` to repair packages left
disabled by older tile builds. This does not clear app data or reset granted
permissions. Group tiles calculate their state from both `hidden` and enabled
status, and skip target packages not installed on this device. Fit3 keeps its
connection-aware flow while restoring Health, Fit3 Plugin, and Accessory.

This is a runtime grant for the Core package only. Nemotron Keyboard and
Nemotron UI are separate packages and do not receive root from this setting.
DFRoot's KernelSU LKM can stop working after a reboot unless its own start-on-
boot option is configured. This integration does not modify boot images,
partitions, or DFRoot's boot behavior.

## Verification on Galaxy A54 5G

- Device: Samsung SM-A546E, Android 16, KernelSU Samsung LKM manager.
- KernelSU listed `com.nemotron.core` in Superuser; its Superuser switch was
  enabled for this request.
- The Setup check returned `uid=0(root)` in context `u:r:ksu:s0`.
- Core debug build completed and was installed through ADB for the check.
- Device `pm help` exposes `hide` and `unhide`; a direct KernelSU check changed
  `com.nemotron.keyboard` from `hidden=false` to `hidden=true` and back, while
  `enabled=1` remained unchanged.
- An instrumented temporary Quick Settings tile used the app's root backend to
  hide and unhide the Nemotron Keyboard package. Logs confirmed both
  `pm hide` (`hidden=true`) and `pm unhide` (`hidden=false`), and both dumps
  confirmed `enabled=1`. The temporary tile was removed from the release APK.
