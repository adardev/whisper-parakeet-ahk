# One UI Home integrado en Nemotron

Nemotron se recompila con el código, los recursos, las bibliotecas y los componentes HOME de One UI Home extraídos del A54. La integración conserva el paquete principal `com.nemotron.voiceime2`; no reemplaza el One UI Home del sistema y no necesita su firma Samsung. La salida se firma con la misma clave debug de Nemotron.

## Qué se integró

- One UI Home 17.5.05.33 del A54: cinco DEX y aproximadamente 33,700 clases smali.
- El alias `com.sec.android.app.launcher.activities.LauncherActivity` con las categorías `MAIN` y `HOME`.
- Recursos, proveedores, servicios, receptores, assets y bibliotecas nativas de One UI Home.
- La inicialización Hilt de `LauncherApplication` encadenada con HoneyBoard y `NemotronApp`.
- `WorkspaceViewModel.v0(PackageOperation.Added)` omite las altas automáticas de cualquier aplicación nueva en Inicio. `ItemData.getHidden()` marca como ocultas las entradas nativas APP de Instagram y WhatsApp por su componente o paquete de referencia; conserva SHORTCUT y DEEP_SHORTCUT. La comprobación visual de estas políticas sigue pendiente mientras se resuelve el arranque.
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
adb shell pm grant com.nemotron.voiceime2 android.permission.PACKAGE_USAGE_STATS
adb shell appops set com.nemotron.voiceime2 GET_USAGE_STATS allow
adb shell am start -n com.nemotron.voiceime2/com.sec.android.app.launcher.activities.LauncherActivity
adb logcat -d -s AndroidRuntime:E
```

Comprobar primero que la interfaz abre y permanece estable. Solo entonces elegir el rol HOME:

```powershell
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

ADB confirmó Hidden API exemptions enabled durante attachBaseContext. Se restaura la referencia al BackProgressAnimator original del framework; los adaptadores de animación anteriores dejan de estar conectados. El radio de las esquinas se calcula con un contexto asociado a pantalla y se añaden getDisplay/createWindowContext a LauncherApplication para mantener esa asociación en llamadas internas. Esta nueva configuración todavía requiere prueba de arranque.

Las pruebas alcanzaron la inflación del escritorio. Se detectaron tipos de vista no sincronizados entre DEX y XML (FlexboxLayout y TabLayout); el script ahora remapea también los XML donantes y aísla juntos los miembros de cada paquete que colisiona. La consulta del usuario activo usa UserHandle.myUserId para limitarse al perfil propio. Para las predicciones de apps, el A54 aceptó pm grant PACKAGE_USAGE_STATS y appops GET_USAGE_STATS allow. Se retiran del código activo los adaptadores vacíos del animador, ya respaldados en el historial, y se prepara una reconstrucción completa desde las APK de entrada.

### Reconstrucción en Windows: conservación de clases
La reconstrucción limpia detectó una colisión entre clases como Y0/b e y0/b. El integrador conserva los alias de archivos de Apktool (por ejemplo y0.1), valida destinos duplicados y compara los fallbacks por descriptor real. La compilación previa falló al abrir y no se considera funcional. El filtro de iconos nativos consulta también component, porque reference_package_name está vacío en las entradas APP del A54. Se conservan los accesos directos.


La reconstrucción con alias conservados compiló con 33.692 clases del donante y 142 fallbacks. El arranque superó la clase Y0.b ausente; apareció una SecurityException en ActivityManager.semGetCurrentUser. Se reemplaza también esa variante Samsung por UserHandle.myUserId. Continúa la comprobación en dispositivo.


La corrección semGetCurrentUser se aplica tanto a las clases del launcher como a la base HoneyBoard. Tras reinstalar, el proceso permanece activo sin AndroidRuntime fatal en el intervalo observado. La pantalla quedó bloqueada con PIN; la validación visual está pendiente de desbloqueo y no se ha cambiado el HOME predeterminado.


### Pulsación larga sobre iconos
El A54 confirmó un cierre SecurityException READ_FRAME_BUFFER al crear QuickOptionBlurBackground. Ese fondo ahora captura solo la jerarquía de vistas de la Activity mediante Canvas y View.draw, sin SemWindowManager.screenshot. QuickOptionCapture devuelve null si no hay Activity, tamaño válido o memoria suficiente; la ruta existente de BitmapDrawable admite un bitmap nulo. No cambia las acciones del menú. La corrección se conserva en merge-oneui-home.ps1 y tools/oneui-compat/QuickOptionCapture.smali; pendiente de comprobar instalada.


### Apertura, reactivación y widgets
Registro del A54: la apertura fallaba en TaskSnapshotManager por MANAGE_ACTIVITY_TASKS y READ_FRAME_BUFFER al preparar la transición. patch-oneui-behavior.ps1 devuelve ActivityOptions.makeBasic conservando el encaminamiento de lanzamiento; ContextReflection usa startActivity público para el propio perfil. PackageEventOperator omite altas automáticas y sustituye solo extraAddedOperation de Changed por NoAutoAdd, conservando actualizaciones y retiradas. El host de widgets ya existía pero tenía cero widgets vinculados: se ejecutó adb shell appwidget grantbind --package com.nemotron.voiceime2 --user 0. La copia de HomeOnly.db contiene accesos deepshortcut de Instagram/WhatsApp publicados por Nemotron, sin entradas APP de esos paquetes; OneUI.db tiene ambas entradas APP con hidden=2. No confundir los accesos que deben conservarse con iconos nativos. Pendiente de validar los nuevos cambios instalados.


### Resultados en A54 y selección tras actualizar
La APK corregida abrió Obsidian desde APP e Instagram desde el DEEP_SHORTCUT de Nemotron; no se registró AndroidRuntime fatal en esas pruebas. Se añadió Device care mediante el selector nativo: dumpsys appwidget pasó de cero a un widget vinculado al host com.nemotron.voiceime2 (SMGlanceWidgetProvider). La app auxiliar com.nemotron.launcherprobe se instaló y se deshabilitó/reactivó: OneUI.db y HomeOnly.db registran cero entradas para su componente. Ambas bases tienen cero APP visibles de Instagram/WhatsApp y HomeOnly conserva sus dos DEEP_SHORTCUT de Nemotron. La app auxiliar se retirará al recuperar ADB.

El usuario pidió retirar las alternativas Samsung. El sistema negó cambiar individualmente sus componentes. Al deshabilitar los paquetes completos, Recientes no abrió y el selector de teclado produjo NullPointerException dentro de InputMethodMenuController.getMenuItems, provocando un reinicio de system_server. Se reactivaron ambos paquetes y se volvió a seleccionar Nemotron. NO deshabilitar esos paquetes en este firmware. tools/install-oneui-nemotron.ps1 selecciona explícitamente teclado e Inicio de Nemotron después de cada instalación, conserva las dependencias Samsung y concede la vinculación de widgets. La selección final y el selector de teclado requieren comprobarse tras reconectar ADB.


### Una sola página y bloqueo persistente de iconos nativos
Se confirmó que HomeOnly renderizaba APP con hidden=2 y que el arranque volvía a crear Health, Instagram y WhatsApp. El filtro de getHidden no era suficiente. La política home-only-policy.sql se aplica en Room.onOpen únicamente para HomeOnly.db: conserva una lista de IDs/componentes nativos existentes, bloquea nuevas inserciones APP y la recolocación automática de APP desasignadas, retira las tres entradas nativas y limita la creación a una página. SHORTCUT/DEEP_SHORTCUT y widgets se conservan. Los accesos de páginas secundarias se trasladan a una carpeta existente de la primera página antes de retirar páginas realmente vacías. La primera página del A54 tiene todas las celdas ocupadas por apps y tres widgets, por eso se usa una carpeta. Esta política limita también nuevas inserciones manuales de tipo APP; las incorporaciones nuevas deben hacerse con accesos de Nemotron. Se corrigió además SearchBackupReceiver para modificar su propio componente, evitando la SecurityException contra el paquete Samsung original. Pendiente de comprobar instalada y tras varios arranques.


### Corrección de alcance: cero iconos Instagram/WhatsApp y sin carpetas
La última instrucción del usuario exige que no aparezcan Instagram/WhatsApp en Inicio y rechaza trasladar sus entradas a carpetas. Se retira por completo la reubicación; ahora se retiran también los SHORTCUT/DEEP_SHORTCUT de esos dos destinos (identificados por intent, no solo por título), se bloquea su reinserción y se eliminan páginas secundarias vacías. Las apps continúan instaladas. Los registros APP nuevos se rechazan antes de insertarse en HomeOnly.db, incluyendo la sincronización de arranque. Esto actúa sobre el almacenamiento del launcher Nemotron; no altera el manifiesto de las apps instaladas ni cambia el PackageManager global de Android. El respaldo previo del layout queda local en app/build/launcher-inspection/HomeOnly-before-single-page.db. Los accesos retirados pueden recrearse explícitamente si se cambia esta política, pero con esta petición no se muestran en Inicio.


### Causa confirmada de los iconos fantasma en memoria
La captura y el log del A54 mostraron que los triggers sí dejaban HomeOnly.db con una página y cero registros de los destinos, pero WorkspaceViewModel.u(ItemSupplier,boolean) seguía ejecutando addRemainPage/addRemainItem con AppItem sin persistir. El log mostró Health, Instagram, la app de prueba y WhatsApp creados en memoria pese al rechazo SQL. Se añade una salida temprana para AppItem en esa rutina antes de calcular posición o crear páginas; widgets y otros elementos conservan su ruta. WorkspaceViewModel.s(), el chequeo de alta automática, devuelve false. Pendiente de verificar nuevamente instalada; las conclusiones basadas solo en SQL no bastan.


### Añadir aplicaciones manualmente y páginas opcionales
Aclaración final del usuario: puede haber más páginas si él añade iconos; solo debe bloquearse el alta automática. Se eliminan nemotron_one_page y nemotron_no_target_shortcut y se deja de borrar páginas vacías o accesos manuales en cada arranque. El bloqueo de AppItem automático permanece en addRemainItem y en los eventos de paquetes.

Los ajustes nativos incorporan Añadir aplicaciones, que abre ShortcutPickerActivity. La lista ya no está limitada a tres apps: enumera las actividades MAIN/LAUNCHER instaladas (incluidas desactivadas), ordena por nombre, elimina paquetes duplicados y usa el componente activo cuando existe. El usuario pulsa + y confirma el diálogo nativo de fijar acceso; entonces aparece el icono y puede crearse otra página si no cabe. Los futuros paquetes no se añaden por instalarse. El cambio está tanto en Kotlin como en la adaptación smali reproducible (InstalledApps y patch-oneui-behavior.ps1). Las capturas de widgets y los respaldos del layout permanecen locales y no se publican por contener datos personales. Pendiente de comprobar el menú y la adición instalada.


Cada adición manual usa un ID de acceso nuevo, con marca de tiempo, para que Android vuelva a mostrar la confirmación aunque un acceso antiguo del mismo paquete siga registrado como fijado. Esto permite recrear un icono retirado y añadir duplicados de forma explícita.


El controlador SettingsFragment.f ocultaba claves de preferencias desconocidas. La clave nemotron_add_apps ahora se reconoce explícitamente, queda visible y tiene un listener que abre la actividad del selector dentro del mismo paquete. No se depende únicamente de insertar una entrada XML.

### Accesos manuales: apertura directa y diagnóstico del cierre (17 octubre 2026)

El menú **Añadir aplicaciones** se comprobó visualmente en los ajustes nativos del A54. El usuario confirmó que el filtrado y las adiciones manuales funcionan. El problema posterior es la animación de retorno: los accesos publicados por Nemotron se identificaban como accesos del paquete del launcher, y `ShortcutActivity` abría la app con `ShizukuManager.launchApp` (`am start`), creando una apertura independiente del icono.

Cambios reproducibles en `tools/patch-oneui-behavior.ps1` y `tools/oneui-compat/ShortcutCloseTarget.smali`:

- `ShellTransitionManager.startShellTransitionForShortcut`: intercepta únicamente accesos propios de Nemotron. Para apps habilitadas del perfil actual resuelve el intent de lanzamiento real y abre desde el contexto del icono. Conserva el flujo anterior como alternativa si no puede abrirse directamente.
- La apertura conserva `Intent.sourceBounds`, calculado con la posición y tamaño reales de la vista, y usa `ActivityOptions.makeScaleUpAnimation`. No fija coordenadas del A54 en el parche.
- `attachCookie`: conserva el ID del elemento con el mecanismo original `ObjectWrapper`/`ActivityOptionsCompat.setLaunchCookie`, también en las opciones básicas de las aplicaciones nativas. No restaura las llamadas a snapshots que requieren permisos de firma y causaban crashes.
- `CloseTarget.DefaultImpls.isPackageAndUserMatchedItem`: reconoce el paquete destino y el perfil de los accesos propios. Admite IDs antiguos `shortcut_paquete` y los nuevos `shortcut_paquete_marcaTemporal`; no convierte enlaces profundos de terceros en aplicaciones.
- Adaptadores nativos de workspace, dock y carpetas: el valor del destino puede incluir el componente real de la app en vez de un componente nulo. Se conserva el comportamiento original de accesos ajenos.
- No se cambian los filtros de instalaciones, los triggers, las páginas ni las posiciones de los iconos del usuario.

**Resultado medido y límite pendiente:** la APK instalada registró `I/NemotronCloseTarget: direct icon launch` al abrir WhatsApp y la actividad visible fue `com.whatsapp/.home.ui.HomeActivity`. No se registró el paso por `ShortcutActivity` en esa apertura. Sin embargo, al cerrar mediante gesto, el registro fue `HoneySpace.AppCloseAnimationDelegate` del proceso del **One UI original** (PID 2427 en esa prueba), con `target-null`; Nemotron era otro proceso (PID 32454). Por tanto, esto confirma la corrección de la ruta de apertura, **no confirma una animación de cierre equivalente a la nativa**.

`adb shell cmd overlay lookup android android:string/config_recentsComponentName` devolvió `com.sec.android.app.launcher/com.android.quickstep.RecentsActivity`. El rol HOME seleccionado es `com.nemotron.voiceime2`, pero eso no cambia el componente de Recientes del framework. Los permisos `CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS` y `MANAGE_ACTIVITY_TASKS` están restringidos a firma/privilegio/Recientes en este firmware. `cmd overlay help` indica que fabricar un overlay requiere root. No se ha modificado esa configuración ni se han deshabilitado las dependencias Samsung. El retorno exacto por gestos sigue pendiente de una integración admitida con ese componente; no se publica como solucionado.

Las escalas de animación usadas temporalmente para observar el cierre se restauraron a sus valores previos: `animator_duration_scale=0.5`, `transition_animation_scale=0.5`, `window_animation_scale=0.5`. Capturas, bases de datos y registros con contenido personal permanecen locales en `app/build`.

### Comprobación con scrcpy y pruebas shell adicionales

A petición del usuario se grabó `app/build/oneui-repro/gesture-before.mp4` con scrcpy, sin audio ni ventana adicional. FFprobe informó **1080 × 2340, 339 fotogramas, 19.455467 segundos**. El video es de frecuencia variable; el promedio es aproximadamente 17.4 fps y no debe interpretarse el límite de captura como frecuencia constante. Se extrajeron 24 fotogramas entre 3.7 y 4.7 segundos: la ventana de WhatsApp se reduce hacia el centro, mientras su icono permanece en el extremo derecho del dock. El resultado sigue siendo incorrecto.

La prueba reproducible `tools/oneui-animation-probe/AnimationProbe.java` usa `app_process` con UID **2000**, equivalente al privilegio shell usado por Shizuku en este dispositivo. El firmware expone `registerRemoteAnimationForNextActivityStart` y `registerRemoteAnimationsForDisplay`; no expone `startRecentsActivity` en `IActivityTaskManager`. El registro puntual para el siguiente inicio se aceptó sin excepción, pero el gesto de Inicio **no invocó el runner**. No se registró un controlador global de pantalla ni se sustituyó el reproductor de transiciones de SystemUI.

También se intentó iniciar explícitamente el `TouchInteractionService` ya incluido en el APK integrado. El proceso de Nemotron falló al registrar `TaskSnapshotManager` con `SecurityException: requires MANAGE_ACTIVITY_TASKS, READ_FRAME_BUFFER`. Se detuvo ese servicio experimental, se restauraron la actividad HOME y el rol HOME de Nemotron y se seleccionó nuevamente su teclado. La comprobación posterior abrió WhatsApp y regresó a Nemotron sin un nuevo error de AndroidRuntime. **No se dejó el servicio experimental activo.**

El framework sigue configurado para `com.sec.android.app.launcher/com.android.quickstep.RecentsActivity`. `adb root` devuelve `adbd cannot run as root in production builds`, y no se encontró `su` disponible en el PATH del dispositivo. La integración privilegiada de Recientes no se ha aplicado. Estos resultados documentan el bloqueo real del dispositivo actual; no constituyen una corrección de la animación ni justifican publicar una APK como si el cierre estuviera resuelto.

Referencias de implementación: [ActivityOptions de AOSP](https://android.googlesource.com/platform/frameworks/base/+/8693eabf20e798634427e812d69018232a926191/core/java/android/app/ActivityOptions.java) declara el permiso de control de transiciones remotas; [implementación original de registro por pantalla](https://android.googlesource.com/platform/frameworks/base/+/966759f60af5c25ce5241c0470753992db641858%5E%21/) muestra la comprobación de ese permiso. Para las APIs efectivamente presentes y el comportamiento de Samsung se usa la salida del dispositivo, no se asume compatibilidad a partir de esas fuentes históricas.


### Prototipo de retorno visual mediante Shizuku (en comprobación)
Se añade GestureReturnController, un controlador propio que obtiene una captura de la tarea con IWindowManager mediante Shizuku y dibuja su retorno a las coordenadas del icono. No se ha sustituido Recientes del sistema. MONITOR_INPUT fue denegado también a shell, por lo que se prueba la observación local del touchscreen con getevent y una alternativa basada en la reanudación de Inicio para entradas inyectadas por scrcpy/ADB. Las capturas permanecen en memoria. El gesto original conserva su funcionamiento. Este apartado describe un prototipo: falta validar la animación instalada mediante video.

build-oneui-java-compat.ps1 compila el controlador con JDK/SDK 36, excluye los stubs del framework del DEX y lo incorpora a classes23. rebuild-oneui-compat.ps1 permite actualizar únicamente ese DEX sobre una copia de la APK integrada y vuelve a alinear y firmar el resultado. Es una actualización de compatibilidad, no un sustituto del proceso completo de integración. Se guarda este estado antes de limpiar clases generadas obsoletas.
