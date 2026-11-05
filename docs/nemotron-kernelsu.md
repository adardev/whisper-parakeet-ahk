# Nemotron Core with KernelSU

Nemotron Core (`com.nemotron.core`) can request root from the installed SU
manager. In Setup, the **Request KernelSU root access** button executes only
`id` as a harmless check. KernelSU owns the per-app Superuser decision; the app
does not silently grant itself root. The button reports success only after the
result identifies UID 0.

The `RootAccessManager` also exposes an argv-based command helper for future
root-backed Core operations. It quotes each argument before calling
`/system/bin/su -c`, merges output, and enforces a timeout. It does not change
existing Shizuku operations or make them run as root automatically.

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
