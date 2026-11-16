# One UI Quickstep for Nemotron

This KernelSU overlay points Android's privileged Recents component to the
Samsung One UI Home `RecentsActivity`. Nemotron UI can remain the selected HOME
launcher; Samsung One UI Home stays installed and enabled to provide the system
Quickstep/gesture service.

The module does **not** patch, replace, rename, or uninstall Samsung's APK. This
avoids invalidating Samsung's package signature and preserves its privileged
permissions. It is the root-level integration relevant to the current gesture
failure: ActivityTaskManager currently has Nemotron cached as Recents even
though the live framework resource now resolves to Samsung.

## Build

```powershell
./tools/kernelsu/oneui-quickstep/build.ps1 -OutputDirectory "$env:TEMP/oneui-quickstep-build"
```

The script compiles a static resource overlay, signs the APK with the local
Android debug key, verifies the APK signature, and packages a KernelSU ZIP. It
includes no boot scripts and does not restart Android components.

## Activation requirements

ActivityTaskManager reads `config_recentsComponentName` during system startup;
changing the overlay in a running session does not replace its cached
`mRecentsComponent`. Therefore, building or installing this overlay alone cannot
repair gestures immediately. Activation requires a normal Android reboot after
the module is enabled. Do not disable One UI Home. After startup, verify:

- `cmd overlay lookup android android:string/config_recentsComponentName`
  returns Samsung's `com.sec.android.app.launcher/com.android.quickstep.RecentsActivity`.
- `dumpsys activity` reports Samsung for `mRecentsComponent` and its UID.
- `SystemUI` has bound Samsung's `globalgesture.TouchInteractionService`.
- HOME, Back, and Recents gestures work with Nemotron UI still selected as HOME.

Do not enable this alongside another active overlay that targets the same
framework resource. The experimental `nemotron-recents` module must remain
disabled. The existing fabricated `com.android.shell:NemotronRecentsRestore`
overlay currently points to the same Samsung component; disable it if it
conflicts with the KernelSU overlay after installation.
