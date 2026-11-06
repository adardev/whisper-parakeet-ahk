# KernelSU recovery — 2026-11-06

## Incident

The experimental `nemotron-android-auto-no-rail` module v0.3 added a custom
`/product/overlay/config/config.xml` while attempting to register its RRO. The
Galaxy A54 then crashed in `SystemServer.startBootstrapServices` with:

```
Required services extension package is missing, config_servicesExtensionPackage
had defined with android.ext.services, but can not find the package info on the
system image
```

The failure is from the module's custom product overlay configuration, not an
Android Auto APK replacement. Android Auto's original APK/signature and user
data were not replaced or cleared. The proposed rail overlay did not work.

## Recovery performed

- Booted Android Safe Mode and connected ADB.
- `su` and `/data/adb` were inaccessible from that session; the KernelSU and
  DFRoot activities were blocked by Samsung Safe Mode.
- Disabled only DFRoot for user 0 with:
  `pm disable-user --user 0 df.root`.
- Rebooted once after disabling DFRoot. ADB verified Android completed boot;
  KernelSU's manager activity resolved again, `su` was unavailable, and the
  `nemotron` overlay did not appear in `cmd overlay list`.
- DFRoot remains disabled so its Start on Boot behavior cannot load KernelSU
  and reapply the faulty module. Root is currently inactive. Do not enable
  DFRoot or perform another root session until the faulty module has been
  disabled/removed with KernelSU's **Disable KSU Modules** recovery option.
- ADB disconnected again before DFRoot could be reconfigured. The exact device
  state must be rechecked before any further action.

## Known KernelSU modules from prior device documentation

This is a last-known inventory, not a fresh live `ksud module list`:

| Module ID | Version / note | Local source or documentation |
| --- | --- | --- |
| `meta-overlayfs` | 1.3.1; required by the prior setup | `docs/microg-noogle-kernelsu-setup.md` |
| `noogle-microg` | Noogle Catfish v2026.10.26 | `docs/microg-noogle-kernelsu-setup.md` |
| `nemotron-noogle-bootfix` | 1.0 | `tools/kernelsu/noogle-bootfix/` |
| `nemotron-recents` | experimental; pending activation | `tools/kernelsu/nemotron-recents/` |
| `nemotron-android-auto-no-rail` | experimental v0.3; **unsafe, do not install** | `tools/kernelsu/android-auto-no-rail/` |

The original installed module directories could not be copied from the phone
because root was unavailable. The archive below backs up the repository-side
module sources and existing KernelSU documentation only; it is not a device
module backup.

## Recovery archive

`backups/kernelsu-recovery-2026-11-06.zip` is a local archive of `tools/kernelsu`
and the KernelSU/microG recovery documentation as it existed during this
incident. It contains the faulty module source for forensic/recovery reference.

## Safe next steps

1. Keep DFRoot disabled until ADB/root status is confirmed.
2. If DFRoot is used again, configure its **Disable KSU Modules** option before
   starting root; keep **Start on Boot** off during recovery.
3. With root available and modules disabled, back up the actual
   `/data/adb/modules` and `/data/adb/modules_update` directories before
   changing any module.
4. Disable/remove only `nemotron-android-auto-no-rail`; preserve the other
   modules unless the live inventory shows a reason to change them.
5. Do not wipe KernelSU or DFRoot app data as a recovery step. Clearing it can
   erase the safety settings and does not remove KernelSU module files.


## Estado posterior a la recuperación

Root se recuperó usando la opción de DFRoot para desactivar módulos durante la recuperación. Se retiró el módulo defectuoso `nemotron-android-auto-no-rail`; no volver a instalarlo. Se habilitaron `meta-overlayfs`, `noogle-microg`, `nemotron-noogle-bootfix` y `nemotron-recents`. Se anuló la desinstalación pendiente de `nemotron-recents`; su activación requiere el siguiente inicio de módulos. DFRoot quedó habilitado, con Start at Boot y Auto Soft Reboot desactivados. El estado descrito anteriormente como root inactivo refleja únicamente la etapa inicial de recuperación.

Se respaldaron las carpetas reales `/data/adb/modules` y `/data/adb/modules_update` en el teléfono y en `Documents/NemotronPrivateBackups/nemotron-ksu-modules-20261106.tar.gz`. Ambas copias coincidieron en SHA-256 `cad0b057c6107a16e7b841e9eed21e3579175519971dc62435e0230af61d508c`. El respaldo es privado y no debe incluirse en Git. También se respaldaron por separado los datos de Android Auto. El parche temporal posterior se documenta en `docs/android-auto-offline-patch.md` y no requiere el módulo defectuoso.
