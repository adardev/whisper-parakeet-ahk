# Prototipo de Recientes para Nemotron

## Estado en vivo después de iniciar DFRoot (16 nov 2026)

El usuario inició DFRoot manualmente y pidió explícitamente que no se vuelva a
reiniciar el teléfono. ADB confirmó root activo (`uid=0`). El módulo
`nemotron-recents` ahora aparece `enabled=false`, pero KernelSU aún informa
`mount=true`: desactivarlo no retiró el montaje de esta sesión. No se intentó
un reinicio, soft reboot, reinicio de SystemUI ni deshabilitar One UI Home.

Comprobaciones de solo lectura:

- HOME sigue asignado a `com.nemotron.voiceime2` y el modo de navegación sigue
  en gestos (`navigation_mode=2`).
- `cmd package resolve-activity` inicialmente devolvía `ResolverActivity` pese
  al rol HOME. Se fijó el componente Nemotron con `cmd package set-home-activity`
  (sin reinicio); ahora la resolución HOME devuelve
  `com.nemotron.voiceime2/com.sec.android.app.launcher.activities.LauncherActivity`.
  `KEYCODE_HOME` abrió esa actividad correctamente.
- Prueba por entrada táctil ADB: el gesto de Atrás desde el borde izquierdo
  cerró Ajustes y regresó a Nemotron. Dos deslizamientos ascendentes desde el
  borde inferior no cerraron Ajustes; Inicio por gesto no está verificado y no
  debe afirmarse que la navegación esté completamente funcional.
- `config_recentsComponentName` todavía resuelve a
  `com.nemotron.voiceime2/com.android.quickstep.RecentsActivity`.
- `dumpsys activity recents` muestra `mRecentsUid=10421` y ese componente, pero
  la actividad integrada falla porque Nemotron no recibe
  `MANAGE_ACTIVITY_TASKS` (`signature|recents`). Un `pm grant` no puede conceder
  ese permiso protegido.
- Aunque `/system/app/NemotronHome/NemotronHome.apk` existe en el montaje de
  OverlayFS, Package Manager resuelve Nemotron desde `/data/app` y su
  `pkgFlags` no incluyen `SYSTEM` ni `UPDATED_SYSTEM_APP`. Esto explica que el
  cambio de recurso por sí solo no convierta la instalación activa en un
  paquete de sistema ni habilite el acceso especial de Recents.
- El logcat conserva el cierre de `com.nemotron.voiceime2` al registrar
  `TaskStackChangeListener`: `TaskSnapshotManager.registerTaskSnapshotListener`
  falla en `getTaskSnapshotManager()` con `MANAGE_ACTIVITY_TASKS` y
  `READ_FRAME_BUFFER`. La fuente AOSP usa el UID de Recents como excepción a
  estos permisos; en este Samsung el fallo persiste pese a
  `mRecentsUid=10421`, por lo que la hipótesis de escaneo tardío del paquete es
  probable, pero todavía no está demostrada como causa única.
- No aparece un `TouchInteractionService` enlazado de Samsung ni de Nemotron en
  la consulta actual. Por tanto, Inicio está seleccionado pero los gestos y
  Recientes no están validados en este estado.
- `cmd activity help` solo ofrece `cmd activity restart` para reiniciar el
  sistema de usuario; no hay un comando de shell expuesto para refrescar en vivo
  el componente Recents que ActivityTaskManager ya cargó. No se ejecutó ese
  reinicio porque el usuario lo prohibió.
- One UI Home sigue instalado y habilitado (`/system/priv-app/TouchWizHome_2017`);
  no se deshabilitó. Hacerlo sin Quickstep autorizado podría romper Atrás,
  Inicio, Recientes y ajustes de navegación.
- En la captura actual de `ps -A` no aparece proceso de One UI Home ni un
  proceso `nemotron.guard`; solo estaba activo `com.nemotron.core`. Deshabilitar
  el paquete Samsung ahora no liberaría RAM de un proceso que no está corriendo.
- Accesibilidad: `enabled_accessibility_services=null`,
  `accessibility_enabled=0`, `installedServiceCount=0`, y ningún servicio
  enlazado. El servicio de guardia de Nemotron ya no se ejecuta.

**No declarar terminado el reemplazo de One UI Home.** No se debe volver a
habilitar `nemotron-recents` ni desactivar One UI Home hasta resolver el permiso
de Recents y validar los gestos. La verificación de cambios que requieren volver
a leer la configuración del sistema queda pendiente; no debe iniciarse un
reinicio sin autorización explícita del usuario.

## Auditoría de estado del dispositivo (16 nov 2026)

La consulta por ADB a las 12:45 no encontró `nemotron-recents` en
`/data/adb/modules` ni en `modules_update`; las notas de instalación del 5 de
noviembre que aparecen abajo eran históricas en ese momento. A las 13:02 se
instaló el ZIP experimental en `modules_update` con `update=true`. El arranque
siguiente terminó, pero `su` ya no estaba disponible porque el root DFRoot es
temporal; Android seguía usando el componente de Recientes Samsung y SystemUI
seguía enlazado a Samsung Quickstep. La activación y gestos con Nemotron siguen
sin verificar. El rol HOME sí permanece en `com.nemotron.voiceime2`.

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
