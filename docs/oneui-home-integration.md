# One UI Home integrado en Nemotron

Nemotron se recompila con el código, los recursos, las bibliotecas y los componentes HOME de One UI Home extraídos del A54. La integración conserva el paquete principal `com.nemotron.voiceime2`; no reemplaza el One UI Home del sistema y no necesita su firma Samsung. La salida se firma con la misma clave debug de Nemotron.

## Qué se integró

- One UI Home 17.5.05.33 del A54: cinco DEX y aproximadamente 33,700 clases smali.
- El alias `com.sec.android.app.launcher.activities.LauncherActivity` con las categorías `MAIN` y `HOME`.
- Recursos, proveedores, servicios, receptores, assets y bibliotecas nativas de One UI Home.
- La inicialización Hilt de `LauncherApplication` encadenada con HoneyBoard y `NemotronApp`.
- Filtro en `WorkspaceViewModel.v0(PackageOperation.Added)`: el clon descarta `com.instagram.android` y `com.whatsapp` antes de pasar la instalación al manejador que añade apps al espacio de Inicio. Las aplicaciones siguen instaladas; solo se omite su alta automática en la página de inicio.
- El APK final sigue usando el nombre visible Nemotron y la firma debug de Nemotron.

La compilación no concede permisos protegidos por firma o por instalación privilegiada, como `STATUS_BAR`, `MONITOR_INPUT` y `WRITE_SECURE_SETTINGS`. Android puede negar esas funciones a una copia instalada como app de usuario. La copia también tiene almacenamiento privado distinto: no hereda ajustes internos del One UI Home del sistema, incluido “Home screen only”.

## Compilar

Se necesita el APK de One UI Home extraído del mismo teléfono, el APK Nemotron con HoneyBoard ya integrado, Apktool 2.12.1, el `framework-res.apk` del A54 registrado en Apktool con la etiqueta `A54`, Android SDK Build Tools y JDK 17.

```powershell
$env:JAVA_HOME = 'C:\ruta\a\jdk-17'
$env:ANDROID_HOME = 'C:\Users\adaredu\AppData\Local\Android\Sdk'

.\tools\merge-oneui-home.ps1 `
  -OneUIHomeApk 'C:\ruta\TouchWizHome_2017.apk' `
  -NemotronApk 'C:\ruta\NemotronKeyboard-no-custom-home.apk' `
  -OutputApk 'C:\ruta\Nemotron-OneUIHome-Integrated.apk' `
  -ApktoolJar 'C:\ruta\apktool_2.12.1.jar' `
  -AndroidSdk $env:ANDROID_HOME `
  -JavaExe "$env:JAVA_HOME\bin\java.exe" `
  -FrameworkDir 'C:\ruta\apktool-A54-frames'
```

El script remapea recursos y autoridades que colisionan, aísla clases One UI que comparten descriptores con Nemotron (incluidos los nombres de clase internos que Apktool renombra al decodificar), aplica el filtro de paquetes, fusiona el manifiesto y firma el resultado con `%USERPROFILE%\.android\debug.keystore`.

## Instalar y elegir HOME

Después de confirmar que ADB muestra el A54 como `device`:

```powershell
adb install -r 'C:\ruta\Nemotron-OneUIHome-Integrated.apk'
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.nemotron.voiceime2
```

El alias HOME integrado debe aparecer como Nemotron en Aplicaciones predeterminadas. El One UI Home original continúa instalado. Para volver, selecciona One UI Home en esa misma pantalla.

## Estado de verificación

La compilación inicial se instaló en el A54, pero falló durante el arranque por una colisión entre una interfaz de One UI (`o1.h`) y una clase de Nemotron con el mismo descriptor. El script se corrigió para basar el aislamiento en la directiva `.class` real en cada smali; la APK filtrada debe volver a instalarse y probarse antes de usarla como Home predeterminado. Mantén One UI Home del sistema como predeterminado mientras esa validación termina. El clon usa datos privados separados y no hereda el diseño/configuración guardado por el One UI Home del sistema ni sus permisos privilegiados.

## Checkpoint del 17 de octubre de 2026

Estado: integración experimental, todavía no funcional como launcher. Se instalaron compilaciones de prueba en el A54; el inicio predeterminado permanece en `com.sec.android.app.launcher`.

Cambios del script: aislamiento por descriptor `.class`, archivos de valores `oneui-*` para evitar sobrescribir recursos `hb-*` del teclado, adaptación de `widgetCategory` de `favorite` a `home_screen`, filtro de paquetes en `WorkspaceViewModel.v0`, y aislamiento de Kotlin y coroutines. Los intentos anteriores detectaron colisiones `o1.h`, incompatibilidad de `ArraysKt`, referencias de Kotlin parcialmente trasladadas y clases de coroutines ausentes. El último fallo observado fue `NoClassDefFoundError` para `ChannelFlowBuilder` en el espacio de coroutines trasladado. El aislamiento completo y sus dependencias siguen pendientes de validar.

Los directorios `app/build/oneui-home-validation*` y sus APK de prueba son archivos generados, no fuentes. Se pueden regenerar con los APK de entrada y el script. No deben publicarse como versiones funcionales. Se guardan las modificaciones y este estado en Git antes de limpiar dichos intermediarios para recuperar espacio.

El filtro actual actúa sobre altas de paquetes; todavía falta verificar la carga inicial y eliminar de la vista las entradas ya persistidas. No se ha demostrado que los iconos nativos permanezcan ocultos después de reiniciar.

Corrección posterior: el lector de directivas smali admite clases sin modificadores (.class L...;). La sustitución de descriptores limita los caracteres permitidos para no capturar el nombre de un método junto a su primer parámetro. Se recompila desde entradas limpias para validar estas correcciones. Se han eliminado APK fallidas y los primeros dos árboles generados; el código permanece en Git.

Se aislaron también los paquetes ofuscados completos y AndroidX del donante para conservar el acceso entre clases internas. Las bibliotecas anteriores dejaron de ser el primer bloqueo de arranque. El siguiente fallo confirmado procede de createWindowContext sobre un contexto sin pantalla; LauncherCompat.smali proporciona una pantalla explícita mediante DisplayManager. Se conserva la configuración de componentes y recursos propia del teclado Samsung dentro de Nemotron. Esta compatibilidad sigue en validación en el A54.

La adaptación de pantalla superó el bloqueo anterior. El siguiente fallo fue SecurityException al consultar SemRoleManager.getRoleHolders: la consulta propia del rol HOME se reemplaza por RoleManager.isRoleHeld, y las consultas adicionales conservan la llamada original con retorno de lista vacía si el sistema deniega el permiso. No se conceden permisos de sistema al clon. Sigue pendiente confirmar un arranque estable y el filtro persistente de iconos.

El A54 no es plegable: FoldStateListener.smali implementa el callback público y entrega estado no plegado, evitando el constructor oculto del framework. DeviceStateManager se obtiene con Context.getSystemService y LauncherCompat se inicializa al adjuntar el contexto de aplicación. ItemData.getHidden marca como USER las entradas APP cuyo refPackageName es com.instagram.android o com.whatsapp; SHORTCUT y DEEP_SHORTCUT conservan su estado. WorkspaceViewModel.v0 ahora omite todas las altas automáticas de paquetes para cumplir la petición de no poblar Home con nuevas instalaciones. La visibilidad y la permanencia de accesos Nemotron todavía requieren prueba en pantalla.

La detección de RAM usa ActivityManager.MemoryInfo.totalMem en KB. ReflectionUtils conserva sus tres firmas y delega en reflexión Java para evitar la inicialización incompatible de Kotlin Reflect trasladado; devuelve null si no hay un método o constructor público invocable. La prueba posterior alcanzó Launcher.onCreate y detectó la lectura protegida touch_and_hold_to_search. SettingsCompat mantiene las lecturas públicas normales y devuelve null solo cuando Settings Secure/System/Global deniegan getString, para permitir el valor predeterminado del repositorio de opciones. Continúa pendiente la comprobación visual.

El cálculo de límites usa WindowManager.getMaximumWindowMetrics en lugar de Configuration.windowConfiguration, que Android oculta a la app de usuario. Las conexiones bindService/bindServiceAsUser denegadas devuelven false; unbindService ignora solo la ausencia de registro. La última prueba confirmó que SplitScreenProxyService de SystemUI rechaza el clon. La integración privilegiada de transiciones de pantalla dividida no está disponible con la firma de Nemotron. Se validará si Home funciona con esa conexión ausente. El script también rechaza donantes con paquete incorrecto y bases que ya contengan One UI Home.

La integración experimental incorpora HiddenApiBypass 6.2 de LSPosed, con licencia Apache 2.0 y artefacto fijado por SHA-256 en tools/vendor/hiddenapibypass-6.2. La inicialización ocurre en attachBaseContext, dentro del proceso de Nemotron; no modifica la política global del teléfono ni concede permisos de firma. Se conserva por ahora el adaptador de animación como alternativa mientras se comprueba esta ruta. Las llamadas a servicios de SystemUI siguen sujetas a los permisos normales. Limpieza terminada: aproximadamente 5,4 GB libres en C y 12,4 GB en E, conservando la base 8086.
