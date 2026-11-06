# Prototipo de Recientes para Nemotron

Estado: instalado en KernelSU el 5 de noviembre a las 23:45, con mapeo idmap
validado contra el framework del A54; **pendiente de activación y no validado
en ejecución**. La animación al icono sigue pendiente.

El usuario autorizó instalarlo y prohibió reiniciar el teléfono. Se instaló con
`ksud module install` y se respetó esa condición. KernelSU muestra
`id=nemotron-recents`, `enabled=true`, `update=true`; los archivos están en
`/data/adb/modules_update/nemotron-recents`. El instalador meta-overlayfs reportó
éxito, aunque también emitió dos advertencias `chcon: invalid context: 0644`:
no tratar la instalación como prueba de permisos o contextos correctos al montar.
La ruta `/product/overlay/NemotronRecents` aún no existe en el sistema activo;
el recurso y el componente de Recientes siguen en Samsung. No se ordenó reinicio
normal, reinicio suave de Android ni reinicio de SystemUI.

## Evidencia del A54

El rol HOME es `com.nemotron.voiceime2`, pero `dumpsys activity recents` muestra
`mRecentsUid=10146` y el componente de `com.sec.android.app.launcher`. El servicio
Quickstep de Nemotron y su `com.android.quickstep.RecentsActivity` sí existen.
`tools/inspect-home-animation.ps1` obtiene estos datos sin modificarlos.

La inspección local de `services.jar` del firmware instalado confirma que
`RecentTasks.onSystemReadyLocked()` lee `config_recentsComponentName`. La API
Samsung `updateActiveRecents(int)` recibe un ID de tarea y opera sobre esa tarea;
no sustituye el componente de Recientes.

`dumpsys package permissions` confirma estas protecciones en el teléfono:

- `MANAGE_ACTIVITY_TASKS`: `signature|recents`.
- `READ_FRAME_BUFFER`: `signature|recents|role`.
- `CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS`: `signature|privileged|recents`.

Esto respalda usar la designación de Recientes; no prueba que la concesión efectiva
ya haya ocurrido. Debe comprobarse después del cambio y del arranque.

Se copió el APK a `/data/local/tmp/NemotronRecents-inspect.apk` y se ejecutó como
root (sin registrar ni habilitar el overlay):

```sh
/system/bin/idmap2 create \
  --target-apk-path /system/framework/framework-res.apk \
  --overlay-apk-path /data/local/tmp/NemotronRecents-inspect.apk \
  --idmap-path /data/local/tmp/NemotronRecents-inspect.idmap \
  --policy public --policy product
/system/bin/idmap2 dump --idmap-path /data/local/tmp/NemotronRecents-inspect.idmap
```

El resultado mapeó `0x0104040b -> 0x7f010000` para
`string/config_recentsComponentName`. No se usó `--ignore-overlayable`.
Esto valida las reglas del recurso para la política product, pero no prueba el
montaje del módulo, el escaneo de paquetes ni la animación. El recurso activo y
el componente de Recientes permanecieron en Samsung durante esta comprobación.

## Construcción

```powershell
./tools/kernelsu/nemotron-recents/build.ps1 -OutputDirectory "$env:TEMP/nemotron-recents-build"
```

El ZIP incluye un APK estático en `system/product/overlay` y `module.prop`.
No incluye scripts de arranque ni reinicios automáticos. Necesita KernelSU y un
metamódulo que monte esa ruta. La firma debug no es una firma de plataforma:
se debe comprobar en el dispositivo que el overlay se acepte desde la partición
montada; compilarlo no demuestra que Android vaya a activarlo.

## Prueba pendiente

Requiere activar el módulo y reiniciar Android para que el servicio de tareas
vuelva a leer la configuración. En este A54 el root LKM llega tarde al arranque,
y la transición GMS original/microG ya causó cierres y un segundo reinicio. Por eso
no se ordenó otro reinicio. La instalación posterior quedó pendiente de activación.

Antes de activarlo: respaldar las bases y preferencias de Nemotron, registrar el
HOME actual y preparar acceso ADB/root para desactivar el módulo si falla. Después:
comprobar el recurso, `mRecentsComponent`, UID, conexión Quickstep de SystemUI,
ausencia de errores de permisos, y grabar un lanzamiento desde un shortcut seguido
del gesto de Inicio. Verificar que la ventana converge al icono correcto y que los
shortcuts, widgets, posiciones y teclado se conservan. Comprobar además microG y
YouTube Music tras el arranque. No declarar éxito por cambiar solo el recurso.

Si se instala y no funciona, desactivar `nemotron-recents` en KernelSU y reiniciar
Android de forma controlada para restaurar Recientes Samsung. El overlay no cambia
los datos de Nemotron ni el APK original de One UI.
