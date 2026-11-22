# Filtro de aplicaciones en Ajustes → Apps

Fecha: 16 de noviembre de 2026
Dispositivo: Samsung Galaxy A54 5G (SM-A546E), Android 16 / Settings 16
Rama del repositorio: `nemotron-root`

## Alcance

El parche modifica únicamente la lista normal **Ajustes → Apps → Tus apps**.
No desinstala, desactiva, suspende ni oculta globalmente los paquetes de la
tabla siguiente. Siguen instalados y sus actividades continúan disponibles; el
filtro solo omite sus filas cuando `ManageApplications` crea la lista normal
(list type `0`). No modifica las listas de permisos especiales, batería,
almacenamiento, búsqueda del launcher ni el cajón de aplicaciones.

| Nombre mostrado | Paquete comprobado en el A54 |
| --- | --- |
| Contactos | `com.google.android.contacts` |
| Device care | `com.samsung.android.lool` |
| DFRoot | `df.root` |
| Display Assistant | `com.samsung.android.displayassistant` |
| Files (DocumentsUI) | `com.google.android.documentsui` |
| Good Lock | `com.samsung.android.goodlock` |
| KernelSU | `me.weishu.kernelsu` |
| Nemotron | `com.nemotron.core` |
| One UI Home | `com.sec.android.app.launcher` |
| Home Up | `com.samsung.android.app.homestar` |
| QuickStar | `com.samsung.android.qstuner` |
| NavStar | `com.samsung.systemui.navillera` |
| Ajustes | `com.android.settings` |
| Theme Park | `com.samsung.android.themedesigner` |
| microG Services | `com.google.android.gms` |

**Files** se identificó como Google DocumentsUI (`com.google.android.documentsui`),
que es la app de 12.87 MB mostrada en la captura. No se filtra Samsung My Files
(`com.sec.android.app.myfiles`).

## Excepción solicitada para microG Companion

Separadamente del filtro de la lista, se ejecutó `pm hide --user 0
com.android.vending` porque el usuario pidió ocultar microG Companion / Vending.
El estado de Package Manager confirmó `installed=true hidden=true`; no se
desinstaló ni se borraron sus datos. Este `pm hide` sí oculta Vending para el
usuario 0 en el resto del sistema. Se puede revertir con:

```sh
adb shell su -c 'pm unhide --user 0 com.android.vending'
```

`com.android.vending` no se agrega al filtro smali de Ajustes: la excepción usa
el estado oculto de Package Manager que pidió el usuario.

## Implementación técnica

El APK de sistema es `/system/priv-app/SecSettings/SecSettings.apk`, paquete
`com.android.settings`, versión 16. La modificación está en
`ManageApplications$ApplicationsAdapter.onRebuildComplete`: antes de entregar
las entradas al adaptador, comprueba `mListType == 0` y quita únicamente las
entradas cuyo `ApplicationInfo.packageName` coincide con la lista anterior.
Las comparaciones están centralizadas en
`shouldHideFromInstalledApps(String)`.

No se instala el candidato como actualización porque su firma no es la firma
privada de Samsung. El APK original permanece en `/system`; el candidato se
monta temporalmente sobre su ruta **solo en el namespace de montajes de
Zygote**, siguiendo el método de los parches de One UI Home y Samsung Keyboard.
La primera aplicación fue temporal: no se añadió al módulo de arranque y el
montaje desapareció al reiniciar Zygote/teléfono. El 21 de noviembre se corrigió
esa omisión en `nemotron-session-patches`: su hook de inicio ahora prepara y
valida este candidato junto con los demás APK y llama al mismo controlador.
El filtro conserva el alcance limitado a Ajustes → Apps. El candidato se sigue
montando en Zygote; `/system` no se modifica y no se instala el APK firmado con
la debug keystore como una actualización normal.

El paquete del módulo de arranque v1.4 incluye el candidato validado y el
controlador. KernelSU aceptó la actualización y la dejó en
`/data/adb/modules_update/nemotron-session-patches` (`update=true`), lista para
el siguiente arranque. No se reinició el teléfono. ADB se desconectó durante la
copia del candidato a la ubicación temporal, así que no se pudo verificar el
montaje de Ajustes en la sesión actual. Al reconectar el teléfono, el candidato
se puede copiar y montar con el controlador sin reiniciar.

El control reversible está en
`tools/kernelsu/settings-app-filter/control.sh` (el módulo lo copia como
`settings-app-filter-control.sh`):

```sh
adb shell su -c 'sh /data/local/tmp/nemotron-settings-app-filter-control.sh status'
adb shell su -c 'sh /data/local/tmp/nemotron-settings-app-filter-control.sh disable'
adb shell su -c 'sh /data/local/tmp/nemotron-settings-app-filter-control.sh enable'
```

El montaje termina al ejecutar `disable`, al reiniciar Zygote o al reiniciar el
teléfono. Para `disable`, el script desmonta el APK candidato de Zygote y de
los procesos que heredaron ese montaje, fuerza el cierre de Ajustes y verifica
que Zygote vuelva a ver la huella original. El original de `/system` no se
modifica en ningún momento.

## Reconstruir el candidato

El script `tools/kernelsu/settings-app-filter/prepare-settings-app-filter.ps1`
valida la huella del APK original del A54, decompila con Apktool, aplica el
parche smali, reconstruye, alinea y firma un candidato local con la debug
keystore. Ejemplo en PowerShell:

```powershell
$env:JAVA_HOME = 'C:\ruta\a\JDK-17'
$env:ANDROID_HOME = 'C:\ruta\a\Android\Sdk'
.\tools\kernelsu\settings-app-filter\prepare-settings-app-filter.ps1 `
  -StockSettingsApk 'C:\ruta\SecSettings.apk' `
  -OutputApk 'C:\ruta\SecSettings-candidate.apk' `
  -ApktoolJar 'C:\ruta\apktool.jar' `
  -DebugKeystore "$env:USERPROFILE\.android\debug.keystore" `
  -AndroidSdk $env:ANDROID_HOME `
  -JavaExe "$env:JAVA_HOME\bin\java.exe"
```

El comando muestra la huella del candidato. Si se firma con otra keystore o se
reconstruye el APK, actualiza `candidate_hash` en `control.sh` con esa huella.
El build debe realizarse desde el APK original que pase la validación de SHA-256.

## Verificación en el dispositivo

- Antes del filtro, Ajustes mostraba `Your apps (45)`.
- Con la versión inicial de Contactos, el encabezado bajó a `Your apps (44)` y
  `com.google.android.contacts` siguió instalado.
- Con la lista ampliada y Vending oculto a petición, el encabezado mostró
  `Your apps (29)`. Ninguno de los 15 nombres del filtro apareció en la
  jerarquía UI de esa pantalla.
- Package Manager reportó rutas instaladas para los paquetes del filtro,
  incluido `com.google.android.gms`; Vending informó `installed=true` y
  `hidden=true`.
- La ruta original conservó SHA-256
  `d2708e14c50e8ec7f142774dfb000bebda1e94bdf4f209c8323746882155a93a`.
- El candidato activo en esta sesión informó SHA-256
  `afcc203b80e8278c7000a17cd0651e37ba3b96aa9673d11fafeaee28b8137d6f`.
- Ajustes abrió la lista sin crash; el hash del APK visible desde Zygote
  coincidió con el candidato.

## Archivos fuente

- `tools/kernelsu/settings-app-filter/patch-settings-app-list.ps1`: inyección
  smali y lista de paquetes.
- `tools/kernelsu/settings-app-filter/prepare-settings-app-filter.ps1`:
  reconstrucción local del APK candidato.
- `tools/kernelsu/settings-app-filter/control.sh`: activación, estado y
  reversión del montaje temporal.

El APK original propietario de Samsung no se guarda en el repositorio. El
candidato final firmado para esta sesión está respaldado en
`artifacts/kernelsu/settings-app-filter/SecSettings-candidate.apk` mediante Git
LFS. Cada nuevo build se genera desde una copia original del dispositivo. El
candidato no debe instalarse con `adb install`.
