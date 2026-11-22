# Samsung Phone: keypad negro y montaje persistente

El parche conserva el APK original de Samsung en `/system` y monta el APK
modificado únicamente en el namespace de Zygote con KernelSU. Cambia la vista
de teclado telefónico para que el fondo sea negro. No desinstala ni deshabilita
Samsung Phone y no modifica ajustes de batería.

## APK y alcance

- Paquete: `com.samsung.android.dialer`.
- APK del sistema: `/system/priv-app/SamsungDialer/SamsungDialer.apk`.
- APK stock esperado: SHA-256
  `77cd0151f57712887522f947a8bebcf7fc668ef53adb89c3104b00f1d8112285`.
- Candidato anterior que se vuelve a usar: `SamsungDialer-fullscreen.apk`,
  SHA-256
  `f90284b44047fb60817cb2911fbb3cb1aba9ee009fa272db920827b8793e60e5`.
- Controlador: `tools/kernelsu/nemotron-session-patches/dialer-control.sh`.

El montaje se valida contra ambos hashes antes de activarse. El APK stock de
`/system` permanece intacto; el controlador rechaza una versión del sistema o
un candidato que no coincidan con los hashes conocidos. Para retirar el parche
sin reiniciar:

```sh
su -c 'sh /data/adb/modules/nemotron-session-patches/dialer-control.sh disable'
```

## Aplicación y persistencia

El candidato se conserva en `/data/adb/nemotron-session-patches/`, que no se
borra al reiniciar. El builder de KernelSU también lo incorpora al ZIP de
actualización (pasándolo como `-DialerCandidateApk`). `boot-completed.sh` lo copia de forma verificada a
`/data/local/tmp`, ajusta el contexto de archivo mediante el controlador y
activa el bind mount cuando Android termina de arrancar. El módulo incluye el
controlador y el builder de actualizaciones ya lo empaqueta.

La comprobación del 22 de noviembre de 2026 encontró el controlador y el
candidato temporal anterior, pero el APK candidato ya no estaba en
`/data/local/tmp`; por eso Zygote y Phone habían vuelto al hash stock. Se
restauró el mismo APK anterior, se incorporó al almacén persistente y se añadió
la llamada al hook de arranque tanto al módulo instalado como a la actualización
pendiente de KernelSU. Se activó en la sesión actual sin reiniciar el teléfono.

Verificación ADB:

```sh
su -c 'sh /data/adb/modules/nemotron-session-patches/dialer-control.sh status'
```

`zygote` y `dialer[PID]` deben reportar el hash del candidato. `system` debe
seguir reportando el hash stock. La ejecución del hook se registra en
`/data/local/tmp/nemotron-session-patches.log`.

El arranque real no se probó porque el teléfono no se reinició durante la
aplicación. La configuración persistente está instalada para el próximo
arranque.
