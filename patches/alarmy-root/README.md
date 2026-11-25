# Alarmy Root UI Lock (4.17.3)

Patch local para impedir crear, duplicar o editar alarmas en Alarmy sin desinstalar la app, reemplazar globalmente su APK ni tocar los datos de las alarmas.

## Versión del patch

La v1.1 sigue activa hasta el próximo arranque. La v1.2 introdujo un `finish()`
temprano en `AddAlarmActivity`; se retiró porque no era el comportamiento pedido
y podía interrumpir la apertura normal.
La v1.3 deshabilita directamente el clic de la tarjeta y quita su listener. El
ZIP v1.3 reemplaza la actualización pendiente de Magisk; no reinicié el
teléfono, así que el cambio todavía no está activo ni verificado en pantalla.

## Resultado solicitado

- Ocultar e inutilizar el botón flotante `+` para crear alarmas.
- Quitar `Duplicate alarm` de los menús de alarma.
- Evitar que tocar la tarjeta de una alarma abra el editor.
- Conservar intacta la alarma configurada y sus días: el patch no escribe en la base de datos.

La protección es una barrera de interfaz/flujo de la app. No impide que alguien con root modifique la base de datos o quite el módulo.

## Compatibilidad del artefacto aplicado

Se preparó para el SM-G532M conectado durante el trabajo:

| Campo | Valor |
|---|---|
| App | Alarmy, paquete `droom.sleepIfUCan` |
| Versión | 4.17.3, `versionCode=41710` |
| Android | 6.0.1, API 23 |
| ABI | ARM de 32 bits |
| Root | Magisk 30.6, con Zygisk disponible |
| APK instalado original | SHA-256 `4568eebab6b2517cbe57dc94121b2baff5638f0367026980e67c83a6735f3d8a` |
| APK parcheado v1.1 actualmente montado | SHA-256 `3bf934a5c0ad640c2730b84bd9c71551fc56a75b0975dd46dd9d6aea947adc31` |
| APK parcheado v1.3 (sin clic de edición en la tarjeta) | SHA-256 `c5702363c328db853ebf0f1ad517c52d38937c36dd6e303787d6c4c46e841744` |

Estos hashes fijan la versión que se probó. El `service.sh` se niega a montar si no coinciden; una actualización de Alarmy requiere revisar y rehacer el parche, no cambiar los hashes a ciegas.

## Qué se cambió en el APK

El archivo [`alarmy-4.17.3.patch`](alarmy-4.17.3.patch) contiene el diff de ocho archivos del árbol decodificado de Apktool. Está basado en el APK original con el hash indicado arriba.

1. `res/layout/fragment_main.xml` y `res/layout-v21/fragment_main.xml`: el `FloatingActionMenu` `alarmListFabMenu` queda con `visibility="gone"`, `alpha="0.0"`, `enabled="false"` y `clickable="false"`.
2. `res/menu/menu_popup.xml` y `res/menu/menu_popup_undo.xml`: se elimina la entrada `itemDuplicate`.
3. `smali/droom/sleepIfUCan/view/fragment/AlarmListFragment.smali`:
   - `q0()V`, que reactivaba/mostraba el menú flotante, pasa a ser no-op.
   - `l0()V` fuerza el menú a `GONE` al configurar la pantalla.
   - `onActivityCreated(Bundle)` vuelve a ocultar el menú después de inicializar el fragmento. Esta segunda defensa es necesaria porque la librería FloatingActionMenu volvía a dibujar el FAB aunque solo se cambiara el XML.
4. `smali_classes4/droom/sleepIfUCan/view/activity/AddAlarmActivity.smali`, método `i0()V`: conserva la barrera existente que bloquea el flujo de alta si no hay ID de alarma; los IDs existentes no se bloquean aquí.
5. `res/layout/layout_alarm_list_alarm_view.xml`: el `CardView` de cada alarma queda con `clickable="false"` y `focusable="false"`, sin el foreground de pulsación.
6. `smali_classes4/droom/sleepIfUCan/view/adapter/z$b.smali`, método `i()V`: pasa a ser no-op y deja de asignar el listener `z$b.b(View)` que abría `AddAlarmActivity` al tocar la tarjeta. Los listeners del checkbox y del menú de tres puntos se conservan.
7. `AddAlarmActivity.onCreate(Bundle)` conserva su flujo original completo. No se llama a `finish()` ni se interrumpe la apertura normal de Alarmy.

El diff guarda el código smali/XML concreto para revisión. No se suben el APK de Alarmy, el keystore ni la base de datos privada.

## Cómo se aplicó con root

El paquete instalado y su firma se dejaron intactos. El módulo Magisk monta el APK reconstruido sobre la ruta original **solo dentro de los espacios de montaje de `system_server` y Zygote**, mediante `nsenter` y bind mount. Esto permite que Package Manager/Android y los procesos nuevos vean el APK parcheado mientras la ruta global de `/data/app` conserva el original.

`service.sh` realiza estas verificaciones antes del montaje:

- Encuentra `/data/app/droom.sleepIfUCan-*/base.apk`.
- Verifica el hash del APK original y el del APK parcheado.
- Busca los PID de `system_server` y Zygote.
- Revisa el hash visto desde cada namespace, retira un montaje anterior de esta versión si aplica, monta y vuelve a verificar.
- Borra el `base.odex` obsoleto de Alarmy para evitar que ART ejecute código viejo.
- Fuerza el cierre de Alarmy y envía el evento `BOOT_COMPLETED` a `droom.sleepIfUCan/.internal.AlarmInitReceiver` para que reprograme las alarmas existentes.
- Escribe el resultado en `/data/adb/alarmy-root-patch.log`.

El módulo local se llama `alarmy_root_patch` y contiene `module.prop`, `service.sh` y el archivo generado `alarmy.apk`. En este repo se guarda el código del módulo, pero no el APK propietario. Para crear un ZIP instalable, el usuario debe construir el APK desde su propia copia legítima y colocarla como `alarmy.apk` junto a estos archivos.

### Reconstrucción (resumen)

Requisitos usados: Apktool 2.12.1, Java 17, Android Build Tools (`zipalign`/`apksigner`) y una clave de firma local. Nunca subir al repo APK, keystore, contraseñas o datos del usuario.

1. Decodificar una copia del APK original verificado con Apktool.
2. Aplicar el diff versionado con `git apply --unidiff-zero --directory=<árbol-decodificado> alarmy-4.17.3.patch` (o revisar/aplicar los cambios equivalentes manualmente si Apktool varía el formato).
3. Reconstruir con `apktool b`.
4. Alinear con `zipalign` y firmar con `apksigner` usando una clave local. La firma reconstruida no coincide con la firma del APK de tienda; por eso no se instala encima con `adb install -r`.
5. Guardar el resultado como `alarmy.apk` dentro del módulo Magisk, confirmar que su SHA-256 coincide con `EXPECTED_PATCHED` de `service.sh` y comprimir los archivos del módulo. Instalar/actualizar el módulo desde Magisk requiere privilegios root y un reinicio para la aplicación estándar de módulos; durante esta sesión se montó/activó sin reiniciar.

El APK final no se incluye en GitHub. La v1.3 está en
`%LOCALAPPDATA%\Temp\alarmy-no-edit-v1\alarmy-v13.apk` durante esta sesión.
Magisk debe procesar el ZIP v1.3 en el próximo arranque. La alarma configurada
no se editó ni eliminó; `dumpsys alarm` siguió mostrando
`droom.sleepIfUCan.ALARM_ALERT`. No se reinició el teléfono; la v1.1 continúa
activa hasta que se procese la actualización pendiente.

## Validación realizada

- Se confirmó que el módulo estaba habilitado y que el montaje expuesto a `system_server` y Zygote coincidía con el hash esperado.
- Se abrió Alarmy y se verificó visualmente que desapareciera `+`.
- Se abrió el menú de una alarma: quedaron `Delete`, `Preview alarm` y `Skip next alarm`; `Duplicate alarm` ya no apareció.
- La v1.3 se reconstruyó desde el APK stock verificado, aplicando el diff. `apksigner` verificó la firma; SHA-256: `c5702363c328db853ebf0f1ad517c52d38937c36dd6e303787d6c4c46e841744`.
- El diff completo aplica sin conflictos al árbol stock de Alarmy 4.17.3 con `--unidiff-zero`.
- La versión anterior añadía `finish()` a `AddAlarmActivity.onCreate`, lo que no correspondía al objetivo. La v1.3 elimina ese cambio y deshabilita el clic de la tarjeta en XML y adaptador.
- La verificación funcional de la tarjeta queda pendiente hasta aplicar v1.3; el patch no altera ni elimina la alarma actual.
- Se ejecutó el cierre y el broadcast de inicialización que usa el script; el evento de alarma seguía programado.
- No se reinició el teléfono en esta sesión. Por tanto, la persistencia después de un reinicio está configurada por `service.sh`, pero todavía **no está validada en un arranque real**.

## Problemas encontrados y resolución

1. **Xposed clásico v89 en Android 6/ARM32:** provocó fallos repetidos de Zygote/ART (`ClassLinker::InitWithoutImage`). Se desactivó y no forma parte de la solución final.
2. **Solo `visibility="gone"` en XML:** la librería del FAB lo volvía a mostrar. Se añadió alpha cero, desactivación, `q0()` no-op y ocultamiento desde `l0()` y `onActivityCreated()`.
3. **Instalación directa con `adb install -r`:** falló con `INSTALL_FAILED_UPDATE_INCOMPATIBLE` por la firma diferente. No se usa para el despliegue final.
4. **Desinstalación temporal durante el diagnóstico:** se intentó `pm uninstall -k`; la instalación parcheada fue rechazada y se restauró el APK original. `-k` conservó los datos. Luego se comprobó que la alarma seguía activa. La ruta final es systemless y no reemplaza la instalación global.
5. **Montaje solo en Zygote / código ART viejo:** observar el APK nuevo no bastaba porque `system_server` y `base.odex` conservaban código previo. La versión final monta en ambos namespaces y borra el odex obsoleto antes del relanzamiento.

## Desactivar o retirar

Desactivar el módulo desde Magisk y reiniciar restaura la vista original. No borres ni edites la base de datos de Alarmy para retirar este patch. Como no se probó un reinicio, si Alarmy no arranca correctamente, deshabilita el módulo desde Magisk/Recovery; el APK instalado original no fue reemplazado.

## Archivos de este directorio

- `alarmy-4.17.3.patch`: cambios de APK en formato diff.
- `module.prop`: metadatos del módulo Magisk.
- `service.sh`: verificación, montaje root temporal por namespace y rescheduling al arranque.
- `README.md`: alcance, construcción, despliegue, validación y registro de fallos.
