# Noogle recovery after KernelSU late load

## Problem

On the Galaxy A54, DFRoot loads KernelSU as an LKM after Android has already
scanned system packages. Noogle's OverlayFS can then expose microG at
`/product/priv-app/GmsCoreMG/GmsCoreMG.apk` while Package Manager still reports
the previous Google Play Services path. This mismatch can leave microG clients
crashing after startup.

## Recovery module

`tools/kernelsu/noogle-bootfix` is a small KernelSU module. Its
`boot-completed.sh` waits eight seconds, checks that the microG APK exists, and
compares `pm path com.google.android.gms` with the expected path. It only runs
`ksud soft-reboot` if Package Manager reports a different path. A marker keyed
to `/proc/sys/kernel/random/boot_id` prevents repeated restarts during the same
Linux boot. It does not open UI or change the Noogle module.

The module logs checks to `/data/local/tmp/nemotron-noogle-bootfix.log`.

## Verification and limits

Installed and enabled on the A54 under KernelSU. A KernelSU soft restart ran
the hook and logged the expected microG path, so no additional restart was
triggered. A full power-off/cold boot was not tested because this device uses
temporary LKM root and a cold boot could remove the active root session. Check
the log after the next planned reboot before treating cold-boot recovery as
verified.

## YouTube Music database issue

YouTube Music 9.43.51 currently crashes when it queries microG's GServices
provider: `SQLiteException: Can't downgrade database from version 4 to 3`.
The affected file is microG's private `gservices.db`; a copy was saved to
`/sdcard/Download/gservices.db.backup`. No app data or accounts were cleared.

The active KernelSU shell profile returned UID 0 but had an empty effective
capability set, so it could not read or rename that app-private database. The
database repair remains pending until a root session with filesystem access is
available. Do not clear all microG data as a workaround: that can remove
microG's stored account state.

### Estado tras la reparación del 5 de noviembre de 2026

Consulta el apartado **Incidente del 5 de noviembre de 2026: YouTube Music y
ubicación** en `microg-noogle-kernelsu-setup.md`. La base GServices incompatible
fue apartada tras confirmar un respaldo idéntico; YouTube Music abrió sin el crash
de SQLite. Maps mostró la ubicación GPS. El proveedor Positon respondió HTTP 404,
por lo que ubicación de red sigue pendiente. No se hizo reinicio adicional para
probar arranque en frío.
