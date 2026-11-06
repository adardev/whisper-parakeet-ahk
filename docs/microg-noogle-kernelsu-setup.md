# microG on Galaxy A54 with temporary KernelSU

This records the microG installation performed on 2026-11-05 for the Samsung
Galaxy A54 5G (SM-A546E), Android 16. It describes the observed device state;
it is not a claim that every Google-dependent app or account flow has been
validated.

## Root and safety boundary

- KernelSU Manager reported `Working [Jailbreak mode]`, LKM, manager
  `samsung-v2.2 (32687-5)`, kernel `5.15.189-android13-3-33470412`, SELinux
  Enforcing.
- Root is the temporary DFRoot/KernelSU LKM session. The bootloader was not
  unlocked, and no boot, init_boot, recovery, or other partition image was
  flashed or modified during this work.
- Android was restarted using KernelSU's **Soft restart** twice to apply the
  metamodule and microG overlay. The Linux kernel uptime remained continuous.
- A normal device reboot can end this temporary root session. While KernelSU
  is not active, its systemless modules cannot mount; the original system
  Google APKs remain on their untouched partitions. The module files remain
  installed in KernelSU until disabled or removed.

## Modules installed

1. **meta-overlayfs** `1.3.1`, required by this KernelSU version to mount
   systemless changes.
   - ZIP SHA-256:
     `279D85A6A35724DFCF8AA1137A9630C7E989E154B94543517C539EE70F9B8811`
2. **Noogle microG Catfish** `v2026.10.26`, KernelSU module ID
   `noogle-microg`.
   - ZIP SHA-256:
     `8309B4F0D40DEDC20008D77A38807A40EE148187F0B5CA42814DB4238EB0F155`
   - Upstream: [SelfRef/noogle-magisk](https://github.com/SelfRef/noogle-magisk)

The selected Catfish variant carries Google's signing blocks in the system
microG APKs, so it does not use the FakeGApps/Vector signature-spoofing route.
The tradeoff is that microG cannot be updated as a normal user APK from
F-Droid; updates need a compatible Noogle module. The upstream project says
Catfish was tested on an Android 15 emulator; this physical Samsung Android 16
installation therefore needs further compatibility checks.

## Verified package state

After applying Noogle, Android Package Manager reported these APK paths and
versions:

| Android package | Installed component | Verified path | Version / state |
| --- | --- | --- | --- |
| `com.google.android.gms` | microG Services (`GmsCoreMG`) | `/product/priv-app/GmsCoreMG/GmsCoreMG.apk` | `0.3.17.252432` |
| `com.google.android.gsf` | Services Framework Proxy | `/product/priv-app/GoogleServicesFrameworkMG/GoogleServicesFrameworkMG.apk` | Mounted by Noogle |
| `com.android.vending` | microG Companion (`PhoneskyMG`) | `/product/priv-app/PhoneskyMG/PhoneskyMG.apk` | `0.3.17.40226`; enabled for user 0 |

The microG Settings activity was opened successfully. Package Manager also
found its launcher activity. **microG Companion has no launcher activity**;
tapping its Play Store-like icon redirects to microG Services settings. It is
not the Play Store catalog and cannot be opened standalone. It supplies
Play-Store-related APIs to apps; use a separate storefront such as Aurora
Store to install apps. A Noogle `playful-catfish` variant is the alternative
if keeping the ROM's actual Play Store is desired.

Companion was initially reported as disabled (`enabled=3`) for user 0. It was
enabled with `pm enable --user 0 com.android.vending`; Package Manager then
reported `enabled=1`, and `pm path` continued to point to `PhoneskyMG.apk`.
This changes only the per-user enabled state; it does not install or restore
Google's Play Store APK.

## Other changes and observations

- The Noogle installer removed Google APK updates and scheduled first-boot
  data clearing for the switched packages `com.google.android.gms`,
  `com.google.android.gsf`, and `com.android.vending`. Treat old local data and
  cached account tokens for those components as cleared. A Google account
  verification screen appeared later; no credentials were entered and no
  sign-in was completed as part of this work.
- At the user's request, `dev.degoogle` and `inc.whew.android.fakegapps` were
  removed for user 0. FakeGApps is not required by this Catfish variant.
  KernelSU, ReZygisk, LSPosed, meta-overlayfs, and Noogle were not removed.
- Samsung displayed **“App paused by Maximum power saving”** for microG
  Services. Maximum power saving was not disabled or changed. Background
  operation while Samsung keeps the app paused has not been verified.
- The Noogle installer warned that KernelSU's per-app **Umount modules**
  setting should be disabled for microG Services, Companion, and Services
  Framework Proxy. The KernelSU global/default app profile was left unchanged;
  these three per-app settings were not verified/configured during this work.
- The microG settings UI was inspected, but Self-Check permissions, signature
  status, push messaging, location backends, Play Store API behavior, and
  third-party app compatibility have not all been validated. Do not interpret
  package installation alone as proof of full Google-account or Play Integrity
  compatibility.

## Recovery

Noogle is a systemless overlay; the stock Google APK files were not modified.
With KernelSU active, disable or uninstall `noogle-microg` from KernelSU
Manager and soft-restart Android to reveal the stock system apps. Keep
meta-overlayfs installed while other modules depend on it. If a regular full
reboot occurs before the temporary LKM is started again, the systemless
overlay is not mounted and the original system APKs are used.

## Upstream references

- [Noogle README: variants, Companion, KernelSU notes, and troubleshooting](https://github.com/SelfRef/noogle-magisk#readme)
- [KernelSU App Profile: per-app module unmount behavior](https://github.com/tiann/KernelSU/blob/main/website/docs/guide/app-profile.md)
- [KernelSU metamodule guide](https://github.com/tiann/KernelSU/blob/main/website/docs/guide/metamodule.md)
