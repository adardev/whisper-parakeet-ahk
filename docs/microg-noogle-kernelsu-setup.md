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
- The Noogle installer warned that KernelSU's per-app **Umount modules**
  setting should be disabled for microG Services, Companion, and Services
  Framework Proxy. The KernelSU global/default app profile was left unchanged;
  these three per-app settings were not verified/configured during this work.
- The microG settings UI was inspected, but Self-Check permissions, signature
  status, push messaging, location backends, Play Store API behavior, and
  third-party app compatibility have not all been validated. Do not interpret
  package installation alone as proof of full Google-account or Play Integrity
  compatibility.

## Maximum power saving and module cleanup (2026-11-05)

Samsung Device Care had suspended microG Services because it was not included
in **Maximum power saving → Essential apps**. KernelSU showed the system dialog
“App paused by Maximum power saving”; its app switch was already enabled in
that allowlist, but microG Services was not. Adding microG Services to the same
allowlist immediately changed Android's package state from `suspended=true`
to `suspended=false`. Maximum power saving itself remained enabled.

After a KernelSU **Soft restart**, Package Manager registered the active Noogle
packages from the product overlay again:

| Package | Active APK path | Verified state |
| --- | --- | --- |
| `com.google.android.gms` | `/product/priv-app/GmsCoreMG/GmsCoreMG.apk` | microG Services `0.3.17.252432`, enabled, not suspended |
| `com.google.android.gsf` | `/product/priv-app/GoogleServicesFrameworkMG/GoogleServicesFrameworkMG.apk` | Services Framework Proxy |
| `com.android.vending` | `/product/priv-app/PhoneskyMG/PhoneskyMG.apk` | microG Companion |

The microG Settings activity and launcher activity were then found and opened.
`RUN_IN_BACKGROUND` reported `allow`, and Android's device-idle allowlist
contained `com.google.android.gms`. This confirms package registration and the
Samsung suspension fix. On Self-Check, signature support, installed packages,
package signatures, and foreground location permission checks showed passing
checkmarks. Account sign-in, push messaging, background location, Play
Integrity, and all third-party apps have not been validated.

The two Zygisk modules were removed from KernelSU after checking the installed
module set: **Zygisk - LSPosed** and **ReZygisk**. No LSPosed manager or
additional LSPosed modules were installed, and Noogle's Catfish variant does
not require signature spoofing. KernelSU's **meta-overlayfs** and **Noogle
microG Catfish** remain enabled; KernelSU reported `Working [Jailbreak mode]`
after the Soft restart. No partition or boot image was modified.

## A54 follow-up: location and Google Wallet (2026-11-05)

### GPS disappearing when the Android Auto tile is off

After a reboot, Android reported the master location switch as disabled even
though Maps and BBVA both still had precise and coarse location permissions.
microG Services was not suspended and remained on Android's Doze allowlist;
Battery Saver itself was still on. This isolated the problem to the device-wide
location switch rather than an app permission or microG battery restriction.

The Android Auto tile implementation was forcing that global switch off in
`onAfterFreeze()`. That breaks every location client, including Maps and banking
apps, whenever the tile is turned off. The tile now enables location when
Android Auto is enabled for navigation, but leaves the user's location setting
unchanged when the tile is turned off. The user can still control Location
normally from Quick Settings or Android Settings.

At diagnosis time, `cmd location is-location-enabled` returned `false`; Maps
and BBVA both had `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` granted.
The updated Nemotron debug APK built successfully and installed on the A54. The
location master switch was re-enabled with Android's location service command
and verified `true`; global Battery Saver remained enabled. The code change was
installed, but a full end-to-end Maps fix and a live Auto-tile transition were
not exercised during this session.

### Google Wallet add-payment-method limitation

The phone displayed this microG response while the payment flow tried to add a
payment method:

```
Not yet supported: com.google.android.gms.pay.homescreen.additem.ADD_PAYMENT_METHOD
```

This is an unimplemented Google Play Services/Wallet action in the installed
microG stack. It is separate from GPS, location permissions, Battery Saver, and
KernelSU. Root access cannot implement the missing Wallet API. Adding a card
through a bank app's own supported card-entry flow may still work, but the
Google Wallet handoff shown here does not. No card was added and no payment was
made during diagnosis.

### DFRoot boot-start verification

DFRoot 4.7 already had **Start at Boot** enabled and its boot receiver permission
granted. After a full reboot, `run-as com.nemotron.core
/system/bin/su -c id` returned UID 0 with KernelSU context (`u:r:ksu:s0`). This
confirms temporary root was automatically reloaded by DFRoot; it does not make
root permanent or modify the boot image. After reboot, microG remained
`suspended=false`, was still on the Doze allowlist, and Battery Saver remained
enabled.

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

## Incidente del 5 de noviembre de 2026: YouTube Music y ubicación

Después de reinstalar YouTube Music 9.43.51 desde los APK extraídos de la instalación
existente (base, inglés y xxhdpi), el cierre continuó. Los registros de Android
mostraron que YouTube Music se cerraba al consultar microG:

```
SQLiteException: Can't downgrade database from version 4 to 3
```

La causa fue una incompatibilidad en la base privada de GServices de microG, no el
APK de YouTube Music. Antes de corregirla se verificó que
`/sdcard/Download/gservices.db.backup` existiera y que coincidiera byte por byte
con la base activa (245760 bytes). Se detuvo microG y se movió únicamente
`gservices.db` a `gservices.db.incompatible-v4`; no se borraron datos de cuenta.
microG generó una base nueva de 45056 bytes. Luego YouTube Music abrió y permaneció
en la pantalla de bienvenida/inicio de sesión sin repetir el error. No se probó
iniciar sesión ni reproducir audio.

La primera ejecución del módulo de reparación falló porque el `action.sh` empaquetado
contenía finales de línea CRCRLF y el shell informó `unexpected end of file`. El
script se normalizó a LF, se validó con `sh -n` y se ejecutó directamente mediante
KernelSU con capacidades efectivas completas. Se dejó marcado para desinstalar el
módulo temporal `nemotron-ytm-gservices-fix`; verificar que desaparezca al siguiente
inicio de KernelSU. El respaldo y la base apartada se conservan en el dispositivo.

### Verificación de ubicación

Se comprobó que el modo de ubicación estaba activado y que microG tenía concedidos
los permisos de ubicación fina, aproximada y en segundo plano. Maps sí recibió una
posición GPS, con 19 satélites y precisión reportada de 17 m; la pantalla de Maps
mostró el punto azul sobre Guadalajara. Esto confirma GPS funcional durante la
prueba. Las fuentes de ubicación por red de microG estaban desactivadas. Al probar
el proveedor Positon seleccionado en microG, su endpoint devolvió HTTP 404, así que
la ubicación por Wi-Fi/red no quedó verificada ni corregida. Maps mostró el punto
por GPS.

El teléfono permaneció accesible por ADB durante la verificación y llevaba doce
minutos encendido. No se hizo otro reinicio para probar el próximo arranque; el
reinicio suave iniciado previamente ya había concluido. No interpretar esta prueba
como garantía de arranque en frío con KernelSU LKM temporal.
