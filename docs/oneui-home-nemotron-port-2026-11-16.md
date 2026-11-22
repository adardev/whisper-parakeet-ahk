# Funciones de Nemotron Home en Samsung One UI Home

## Transición de Recientes y fondo negro — v55

El reproductor de la transición flotante elimina el inset superior en el
encuadre de salida cuando está activa la política inmersiva de Nemotron.
Conserva los otros tres insets y el encuadre de las tarjetas de v51. La
medida se aplica en los dos caminos del reproductor, sin una lista de apps.
Al volver a Recientes, `RecentsActivity` colorea `recentContainer` de negro.

En el A54 se grabó el gesto desde tarjetas de WhatsApp, Helium, YouTube Music,
Excel, Spark y Gemini. Las tomas muestran una ampliación continua del
encabezado hasta la ventana abierta. Se midió el fondo antes y después:
RGB (22, 22, 22) en v54 y valores entre 0 y 1 por canal en v55.

Se grabaron también Fitia y Microsoft To Do con el proceso abierto. Tras
el gesto volvió al frente la actividad esperada en ambas y el encabezado
se mantuvo estable en los fotogramas revisados. WhatsApp volvió a su
actividad en dos toques consecutivos con su tarjeta centrada cada vez.
Estas tomas cubren las actividades y estados observados durante la prueba.

El APK activo y el almacenado para el arranque tienen SHA-256
`c629ad6b739e0646fd6bebcfd164334a0103e1d0a23bb2a01ea54517d7c8f2a2`.
El respaldo de v51 está en `/data/adb/nemotron-session-patches/backup-before-v55/`.
El teléfono no se reinició. YouTube está registrado pero no tiene un APK
activo para este usuario.

En YouTube Music se confirmó una apertura en frío desde la tarjeta, sin PID
antes de soltar y con un PID nuevo después. La grabación de v55 contiene
0.633 s de pantalla casi negra tras la expansión. v56 probó conservar la
ventana inicial de Samsung cuando el proceso no estaba presente. El tramo
oscuro duró 0.618 s, por lo que se restauró v55. Android registró tiempos
hasta el primer dibujo de 528 ms y 534 ms, respectivamente. El lanzamiento
inicia el proceso, pero la vista previa deja de cubrir la pantalla durante
la espera del primer dibujo.

Los eventos `viewroot_draw_event` confirman que Android dibujó una ventana
inicial en ambas versiones antes de ocultar Recientes. Pasaron 424 ms en
v55 y 423 ms en v56 desde que Recientes dejó de dibujar hasta que la
actividad reportó su primer fotograma. En el video de v56, el fotograma a
1.9 s es negro. Crear la ventana inicial no garantiza que su contenido
visible cubra el relevo de Recientes.

Como control, se lanzó la misma actividad en frío con `am start` desde
Inicio, sin pasar por Recientes. No había PID antes de arrancar. El video
mostró 0.914 s casi negros con el mismo umbral de detección. Por tanto,
el tramo oscuro también ocurre fuera del gesto. La prueba de Recientes
no demuestra una regresión exclusiva del parche de la transición.

También se probó Helium en frío desde Recientes. El proceso estaba ausente
antes del gesto y volvió con PID 12256. No se detectó un tramo casi negro
con los mismos parámetros. La cabecera amplió su tamaño sin salto visible;
la app todavía cargó el contenido de la cuadrícula de pestañas después.

Con el One UI Home original y la misma política inmersiva, se repitió
el gesto de YouTube Music en frío. No había PID antes de soltar y el PID
13589 quedó al frente después. La grabación contiene 0.599 s casi negros
con los mismos parámetros de detección. El tramo existe también en el
launcher original con la política inmersiva activa. Se restauró v55.

Con YouTube Music ya abierto, el control con One UI Home original mostró
el indicador de batería durante la expansión y el encabezado quedó
brevemente más arriba antes de asentarse. El PID se mantuvo durante el
gesto. En la toma equivalente de v55 no aparece el indicador y el
encabezado conserva su posición al llegar a pantalla completa.

También se verificó el toque sobre la tarjeta de YouTube Music con v55.
La misma tarea volvió al primer plano y los fotogramas muestran el
encabezado estable al asentarse la ventana, sin indicador de estado.

## Ruta consistente al soltar una tarjeta inmersiva — 20 nov 2026

La primera versión condicionaba la supresión de la ventana temporal a que
`ActivityManager` reportara el proceso como activo. Ese inventario puede variar
entre aperturas y hacía que el gesto de una misma tarjeta usara rutas distintas.
La nueva versión elimina esa condición: cuando `policy_control` coincide con
la excepción inmersiva de Nemotron y la tarea tiene `topActivity`,
`LaunchTaskHelper` conserva la transición nativa de Samsung y deshabilita su
ventana inicial en todos los casos. No cambia escalas ni tiempos de animación,
la política inmersiva, el tamaño de las tarjetas ni otras apps fuera de esa
política.

Se reconstruyó el candidato desde One UI Home stock y se verificó su firma v3.
Quedó montado en Zygote y One UI Home, con el mismo hash en el almacén de
KernelSU para el siguiente arranque: `daece206d90759502928922844b0899342e224870154be9ce08f62a804adaeb7`.
La versión anterior está respaldada en
`/data/adb/nemotron-session-patches/backup-before-v51/`. No se reinició el
teléfono. Las tarjetas de Fitia y To Do abrieron sus actividades, incluso al
repetir To Do desde Recientes. Esto confirma que no hay una lista de paquetes
permitidos en el parche. No confirma que el salto visual haya desaparecido:
la captura de WhatsApp quedó cubierta por el aviso de almacenamiento lleno y
las capturas finales de Fitia/To Do solo muestran la pantalla ya abierta. La
comprobación visual del gesto sigue pendiente.

## Reanudación de tarjeta inmersiva en Recientes — 20 nov 2026

El salto al soltar una tarjeta dependía de si la app ya estaba abierta. La
causa era un bloque añadido a `TaskListViewModel` que desactivaba la ventana
inicial de Samsung solo para procesos activos; la primera apertura fría seguía
la transición nativa y las siguientes tomaban una ruta distinta. Se retiró ese
bloque para que todas las tarjetas usen las opciones y la transición originales
de Samsung. No se cambiaron escalas, duración de animaciones, insets ni el
tamaño/aspecto de las capturas.

El candidato se reconstruyó desde el APK stock con el parche de Nemotron y
conserva la firma de prueba v3 que usa el montaje de KernelSU. Recientes abrió
con el nuevo launcher y no se reinició el teléfono. La captura del gesto cálido
no fue concluyente: Android mostró el aviso de almacenamiento casi lleno antes
de que terminara la prueba. El APK y los hashes del controlador, el almacén y
`boot-completed.sh` quedaron actualizados para reaplicarse al arrancar; el APK
anterior y los controladores están respaldados en
`/data/adb/nemotron-session-patches/backup-before-v49/`.

## Deslizar una tarjeta de Recientes hacia abajo (19 nov 2026)

La apertura por gesto utiliza también `LaunchTaskHelper`; recibe la misma
opción que la apertura por toque para evitar la ventana temporal de captura
cuando está activa la política inmersiva. `RecentsActivity` oculta su propia
barra de estado mientras se usa esa política, evitando que aparezca durante
el gesto; la actividad de Inicio conserva su barra. La política y las tres
escalas de animación permanecen intactas.

Se grabó el gesto con WhatsApp después de actualizar la captura de Recientes:
la app se abrió sin el salto ni la barra fugaz. Se comprobó visualmente que
Inicio conserva la barra. El candidato activo y preparado para el siguiente
arranque de módulos tiene SHA-256
`a7da12047301a222f8b47fa3df38133c0c59a116ad68ce8e8f70c19c38ab1462`.
No se reinició para probar la persistencia. Se guardó el candidato anterior en
`/data/adb/nemotron-session-patches/backup-before-recents-gesture`.

## Recientes y modo inmersivo (19 nov 2026)

Al abrir una tarjeta, el sistema podía mostrar una captura temporal generada
con espacio para la barra superior y sustituirla por la ventana inmersiva real.
La captura inspeccionada de WhatsApp tenía unos 95 px vacíos arriba; en el
video se veía un salto al cambiar entre ambas. Las ventanas reportaban el mismo
tamaño, por lo que cambiar solamente el recorte de la transición no resolvió
todos los casos.

El parche de `TaskListViewModel` conserva las opciones y la animación original
de Samsung y solicita no usar la ventana de captura temporal al pulsar una
tarjeta, únicamente si `policy_control` coincide con la política inmersiva de
Nemotron. Así se espera a la ventana real. La barra sigue oculta en las apps;
Inicio conserva su excepción. Una app que tarde en dibujarse puede tardar más
en aparecer. La ruta de gestos y los ajustes globales no se alteran.

Se comprobaron aperturas de Samsung Health y WhatsApp mediante grabación, sin
la superposición de posiciones observada antes, y se verificó la versión final
con WhatsApp. El candidato activo tiene SHA-256
`8a1b14c1fae6b24d6774a14583ac1d55bd179df958db5eb3f66894e820e68892`.
Se actualizó el almacén y el módulo de sesión para el siguiente inicio de
KernelSU; no se reinició para comprobarlo. El candidato anterior y sus
controles están en `/data/adb/nemotron-session-patches/backup-before-immersive-recents`.

## Transición al abrir una tarjeta de Recientes (19 nov 2026)

El parche anterior sustituía `ShellTransitionManager.getActivityLaunchOptions`
por `ActivityOptions.makeBasic()`. La ruta Samsung `LaunchTaskHelper` llama a
`makeShellTransitionOptions`, que utiliza ese método al abrir una tarea de
Recientes. Se conserva ahora la implementación Samsung cuando `-StockHost`
compila el launcher privilegiado; la adaptación para el clon sigue separada.

El candidato activo en esta sesión tiene SHA-256
`3c44050a7a7eded337539cba4b7491aa7944494ce292fd780475e4f5b375dea8`.
Se verificó que One UI Home y Recientes abren, y se grabaron aperturas de
Samsung Health y WhatsApp: sus tarjetas se amplían hacia pantalla completa.
Las escalas de animación se mantuvieron en 0.5. El módulo de sesión y su
almacén se actualizaron con ese hash; su ejecución en el próximo arranque
depende de que KernelSU y sus módulos estén activos. No se reinició para
comprobar la persistencia. La versión anterior y sus controles se guardaron en
`/data/adb/nemotron-session-patches/backup-recents-20261119`.

## Qué se portó

El candidato se construye desde el APK Samsung One UI Home 17.5.05.33 del
Galaxy A54. Conserva su paquete, manifiesto, actividades, permisos, providers,
servicio Quickstep y clases de compatibilidad de AndroidX. Sobre esa base se
portan las modificaciones propias del launcher Nemotron:

- **Añadir aplicaciones:** preferencia en los ajustes de Inicio, selector con
  nombres e iconos reales y confirmación nativa de Samsung. La elección se
  guarda en `nemotron_launcher/manual_apps`; no coloca iconos sin confirmación.
- **Escritorio bajo control del usuario:** las instalaciones nuevas no crean
  iconos automáticamente; se mantienen los filtros y la política de las bases
  `OneUI.db` y `HomeOnly.db` para aplicaciones ocultas y elementos elegidos.
- **Accesos y gestos:** los accesos fijados abren su actividad real y se asocian
  con el destino de cierre de Samsung para que el retorno conserve la
  transición hacia el icono. Se mantiene el `TouchInteractionService` de
  Samsung para Inicio, Atrás, Recientes y Quickstep.
- **Trabajo en segundo plano:** se conserva la política Nemotron de altas de
  paquetes y se alargan a 300 segundos los intervalos de limpieza o purga Rx
  que existen en este build.

El teclado, los tiles de Nemotron Core y sus servicios son funciones de otras
aplicaciones; no pertenecen al launcher y no se insertan en One UI Home.

## Cómo se ejecuta

`build/prepare-stock-oneui-root-candidate.ps1` crea un APK de ejecución basado
en el original. Como Samsung exige su propia firma para instalar o actualizar
One UI Home, el candidato **no se instala**: se enlaza temporalmente al APK de
Samsung en el espacio de montajes de Zygote cuando KernelSU está activo. El APK
de `/system` y su firma permanecen originales. El control reversible está en
`apps/nemotron/src/main/res/raw/nemotron_oneui_home_control.sh`:

```sh
sh /data/local/tmp/nemotron-oneui-home-control.sh status
sh /data/local/tmp/nemotron-oneui-home-control.sh enable
sh /data/local/tmp/nemotron-oneui-home-control.sh disable
```

`enable` comprueba las huellas del APK original y del candidato antes de montar.
`disable` retira el enlace de Zygote y de los procesos que lo heredaron, y
comprueba que Samsung vuelve a ser visible. El montaje dura la sesión root; no
se instaló un módulo persistente ni se reinició el teléfono.

## Verificación en el A54

- Samsung Home abrió desde frío con el candidato y permaneció activo; no hubo
  fatal ni ANR en las comprobaciones.
- Ajustes de Inicio mostró **Añadir aplicaciones**. El selector y la hoja
  nativa de pin de Samsung abrieron correctamente. Se probó BBVA y se retiraron
  los iconos usados para la prueba al terminar.
- Los gestos Atrás y Inicio regresaron a Samsung Home y SystemUI mantuvo su
  enlace a `com.sec.android.app.launcher/.globalgesture.TouchInteractionService`.
- Se ejecutó el ciclo `disable` → verificación del original → `enable`. Al
  terminar, el proceso Samsung y Zygote ven el candidato; `/system` conserva la
  huella original.
- Huella original: `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`.
- Huella del candidato de esta sesión: `857f05b77c784fb9712bf7af6575d80556f2944e7839d7728c590b9a603cc419`.

La prueba corresponde a esta sesión root y a la versión instalada del A54; no
demuestra persistencia después de reiniciar o de cerrar KernelSU.

## Distribución Nemotron guardada como referencia

La distribución confirmada por el usuario el 16 de noviembre de 2026 usa el
modo `HomeOnly`, una cuadrícula de cuatro columnas por seis filas y fondo negro.
Al migrarla a One UI Home, se deben conservar los accesos de Nemotron como
`deepshortcut` y las apps normales como elementos `app`; no se deben sustituir
unos por otros.

| Posición | Contenido |
| --- | --- |
| Fila 1, columnas 1–2 | Kindle nativo, Obsidian nativo |
| Filas 1–2, columnas 3–4 | Widget de calorías de Fitia |
| Fila 2, columnas 1–2 | Shortcut de Salud de Nemotron, Excel nativo |
| Fila 3, columna 1 | BBVA nativo |
| Fila 3, columnas 2–4 | Barra de búsqueda |
| Filas 4–6, columnas 1–3 | Widget de Microsoft To Do |
| Filas 4–6, columna 4 | Carpeta con Uber, Maps, Find y Waze; debajo YT Music y Salt Player |
| Dock, de izquierda a derecha | Phone, Spark, Discord, shortcut de WhatsApp |

Los widgets se añaden desde el selector de One UI Home, porque Android los
vincula al host de widgets del launcher que los creó. El usuario confirmó que
ya volvió a colocar Fitia, búsqueda y Microsoft To Do en One UI Home. No se
deben borrar ni reconstruir las bases de Nemotron para migrar iconos.

La lista de apps nuevas sigue siendo manual. Kindle y Excel son iconos nativos;
el acceso de Salud es el shortcut de Nemotron. La excepción de la barra de
estado del modo inmersivo es únicamente `com.sec.android.app.launcher`, así que
la barra queda visible en One UI Home y oculta en las demás apps. La barra de
navegación no se modifica.

## Registro técnico línea por línea por archivo

El commit `385ad9b` contiene la implementación del port y de la excepción
inmersiva. El diff del commit conserva cada línea fuente anterior y nueva; esta
sección explica el propósito de cada archivo que forma el parche.

| Archivo | Cambio y responsabilidad |
| --- | --- |
| `apps/nemotron-ui/build/prepare-stock-oneui-root-candidate.ps1` | Toma el APK de One UI Home del A54 como donante, valida herramientas/entradas, decompila, adapta los espacios de nombres, aplica los parches smali, compila el helper Java, agrega `classes7.dex`, alinea y firma el candidato temporal. Comprueba el resultado y calcula SHA-256. El candidato no se instala con Package Manager. |
| `apps/nemotron-ui/build/patch-oneui-behavior.ps1` | Inserta la preferencia **Añadir aplicaciones** en ajustes de Inicio; redirige el clic al selector; mantiene en el escritorio solo apps elegidas a mano durante los eventos de paquete; adapta los accesos fijados y su destino de retorno; conserva el Quickstep de Samsung. El modo `-StockHost` selecciona adaptadores compatibles con el APK Samsung. |
| `apps/nemotron-ui/build/oneui-compat/NemotronAppPicker.java` | Consulta actividades `ACTION_MAIN`/`CATEGORY_LAUNCHER`, ordena por nombre, usa `ShortcutManager.requestPinShortcut` con el icono real de la app y guarda los paquetes seleccionados en `nemotron_launcher/manual_apps`. Samsung presenta su confirmación nativa de fijado. |
| `apps/nemotron-ui/build/oneui-compat/AddAppsPreferenceClick-stock.smali` | Implementa el listener de la preferencia de ajustes y muestra el selector compilado dentro del candidato. |
| `apps/nemotron-ui/build/oneui-compat/InstalledApps-stock.smali` | Lee y actualiza el conjunto `manual_apps`; el filtro de la lista y el receptor de cambios consultan esta selección para evitar añadir apps nuevas al escritorio por su cuenta. |
| `apps/nemotron-ui/build/oneui-compat/LauncherCompat-stock.smali` | Proporciona la clase de compatibilidad que requiere el código Samsung ya modificado, sin agregar un componente de manifiesto. |
| `apps/nemotron/src/main/res/raw/nemotron_oneui_home_control.sh` | Control reversible `status`, `enable`, `disable` para el montaje de sesión. Verifica el hash del APK de sistema y del candidato; retira el montaje de Zygote/procesos para volver al APK Samsung original. |
| `apps/nemotron/src/main/java/com/nemotron/voiceime/dhizuku/ImmersiveStatusBarPolicy.kt` | Persiste `immersive.status=apps,-com.sec.android.app.launcher` en `Settings.Global`. Al desactivar, restaura el valor global anterior. No crea workers ni toca la barra de navegación. |
| `apps/nemotron/src/main/res/xml/preferences.xml` | Explica en la opción de Core que la barra se oculta fuera de One UI Home, aplica a apps actuales y futuras y requiere root KernelSU. |
| `apps/nemotron-ui/README.md` | Enlaza la documentación del port desde la carpeta de Nemotron UI. |
| `docs/oneui-home-nemotron-port-2026-11-16.md` | Registra montaje, hashes, pruebas, layout confirmado, distinción entre iconos nativos y shortcuts, límite del host de widgets y política inmersiva. |

### Diferencia entre icono nativo y shortcut

Un icono nativo es un elemento `app` que referencia directamente la actividad
de Kindle, Excel u otra app. One UI muestra **Seleccionar** y **Desinstalar**.
Un shortcut de Nemotron es un elemento `deepshortcut` dirigido a
`com.nemotron.core` con un `shortcut_id`; conserva la acción de Nemotron y One
UI muestra **Seleccionar** y **Quitar**. Salud se conserva como shortcut de
Nemotron; Kindle y Excel son nativos. WhatsApp del dock es un shortcut de
Nemotron. No se deben convertir estos elementos entre tipos al editar la base.

### Root, montaje y seguridad de arranque

- El acceso de esta sesión provino de KernelSU. No se instaló un módulo para
  sustituir One UI Home ni se modificó `/system`.
- El APK Samsung original tuvo SHA-256
  `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`.
- El candidato de sesión tuvo SHA-256
  `857f05b77c784fb9712bf7af6575d80556f2944e7839d7728c590b9a603cc419`.
- El control de montaje compara ambos hashes antes de actuar. `disable` restaura
  la vista del APK del sistema. El montaje no se configuró como persistente en
  arranque y las comprobaciones de esta sesión no reiniciaron el teléfono.
- One UI Home mantuvo el rol HOME y el servicio Samsung
  `com.sec.android.app.launcher/.globalgesture.TouchInteractionService`; Core
  y el teclado quedaron como paquetes separados.

### Compilación y aplicación de la política

Se compiló Core con `:apps:nemotron:assembleDebug` usando Java 17. El build
terminó correctamente; el APK `com.nemotron.core` versión `1.6` (versionCode
11) se instaló con `adb install -r`. Luego se escribió la política global y se
leyó de vuelta para verificar que el valor completo era
`immersive.status=apps,-com.sec.android.app.launcher`. El estado actual se
puede revisar con:

```sh
adb shell settings get global policy_control
```

### Paquetes Nemotron independientes

En el A54, `com.nemotron.voiceime2` está etiquetado **Nemotron UI**;
`com.nemotron.core` es Core y `com.nemotron.keyboard` es el teclado. El método
de entrada seleccionado es
`com.nemotron.keyboard/com.samsung.android.honeyboard.service.HoneyBoardService`.
Desinstalar Nemotron UI no equivale a quitar Core, el teclado ni el parche de
One UI Home. El código fuente y estas instrucciones permanecen versionados en
GitHub aunque se quite ese paquete del teléfono. En esta actualización se
desinstaló `com.nemotron.voiceime2`; se confirmó que Core, el teclado activo,
One UI Home como HOME y el montaje KernelSU de la sesión siguen disponibles.

### Tarjetas opacas de Finder (16 nov 2026)

La captura del usuario identifica dos tarjetas del propio Finder de One UI Home:
**Searched apps** y **Quick panel**. La jerarquía de accesibilidad confirma que
ambas son vistas `card_layout` del paquete `com.sec.android.app.launcher` y usan
`@drawable/search_card_background`. El fondo del bloqueo también se ve en la
captura; no forma parte de este cambio.

Se añadió `apps/nemotron-ui/build/patch-finder-opaque-cards.ps1` y se integró en
`prepare-stock-oneui-root-candidate.ps1`. El script conserva radio, tamaños y
contenido Samsung, y fija los cuatro drawables `search_card_background`,
`_dark`, `_light` y `_medium` en `#ff242629`, cuyo canal alfa es `ff` (100 %).
Así se cubren las variantes que Finder selecciona para sus tarjetas. El APK
candidato compilado y firmado para la sesión está en
`C:\Users\adaredu\work\oneui-finder-opaque-root-candidate.apk`, SHA-256
`a2eed8c69d9dbaa9dd19b6dbf3241971ef01d1888aa90bbba65275ffe476a0e6`.
Apktool volvió a decodificar el candidato y confirmó el color opaco en los
cuatro drawables.

El candidato quedó activo en esta sesión KernelSU mediante el montaje que ya
usaba One UI Home. Se quitó el bind anterior, se instaló el nuevo APK candidato
en la ruta fuente y se volvió a montar sobre One UI Home; el hash de Zygote y
del proceso del launcher coincide con el candidato. `/system` conserva la huella
original. La captura posterior muestra opacas ambas tarjetas; no hubo fatal ni
ANR. [Captura de Finder](images/finder-opaque-2026-11-16.png).

El `su -c` normal de ADB entrega UID 0 sin capacidades de montaje. El controlador
`nemotron_oneui_home_control.sh` ahora detecta si no puede entrar al namespace y
reintenta esa operación con `su -mm`; la ejecución quedó comprobada con
`status` → `disable` → `enable` → `status`. El APK actualizado de Nemotron Core
(versión 1.6, versionCode 11) se instaló para sincronizar el hash y el
controlador. El montaje sigue siendo de sesión: no es un módulo persistente y
puede requerir volver a activarlo después de reiniciar o cerrar KernelSU. No
requiere AOD, no cambia el fondo y no modifica la pantalla de bloqueo.

### Limpieza de la segunda página (16 nov 2026)

La base `HomeOnly.db` contenía seis accesos en una segunda página. Se respaldó la base antes de la limpieza y se aplicó la política desde One UI Home al abrirla: elimina los elementos y grupos de páginas posteriores a la primera. Esta política también elimina cualquier contenido que se coloque en páginas adicionales cuando vuelva a abrirse la base; la página principal, dock y widgets existentes se conservan. No desinstala ni deshabilita paquetes.

Tras abrir One UI Home se comprobó la integridad de la base y que solo quedaba la página `id=3`, con sus accesos y widgets. El APK de sesión activo tiene SHA-256 `91065022a24e092a9174b503d26850aa99ff78c3971dd4e12b4fc53db11e84a2`; el APK Samsung en `/system` conserva su hash original. El montaje es temporal y debe reactivarse tras reiniciar. El respaldo local anterior a la limpieza está en `C:\Users\adaredu\work\homeonly-before-page2-clean-20261116.db` junto con sus archivos WAL y SHM.

## Apertura normal de apps con barra inmersiva (19 nov 2026)

El video `Downloads/hola.mp4` mostró una falla distinta a la transición de
Recientes: al tocar el icono de WhatsApp en Inicio, One UI mostraba primero la
ventana temporal con espacio para la status bar y luego la reemplazaba por la
ventana inmersiva, duplicando brevemente el encabezado y desplazando el
contenido. El fix de `TaskListViewModel` solo cubría tarjetas de Recientes y no
arreglaba este camino de apertura normal.

Se aplicó el arreglo anterior en `HoneySystemController.startActivity`: cuando
`Settings.Global.policy_control` coincide exactamente con
`immersive.status=apps,-com.sec.android.app.launcher`, One UI Home pasa
`ActivityOptionsCompat.setDisableStartingWindow(true)` al inicio. Esto omite la
ventana temporal que causaba el salto; la animación nativa de la app sigue
activa. Para cualquier otra política conserva el `startActivity` original. No
se tocaron las escalas globales (siguen en `0.5`) ni el ahorro de batería.

Se reconstruyó el candidato desde el mismo One UI Home parcheado que ya tenía
las tarjetas opacas y Recientes negro. El candidato activo tiene SHA-256
`f3d48edbfd63cf0cbfcdbf2df56890740d6a2bcc5ef366a536ee932ddbec129a`. Se
montó con KernelSU en el namespace de Zygote; `/system` conserva el APK stock.
La grabación ADB posterior del toque en WhatsApp ya muestra el cambio directo
al contenido estable, sin el encabezado duplicado. El launcher sigue vivo y
su proceso usa el hash del candidato.

Para que sobreviva al siguiente arranque, se actualizaron el APK guardado en
`/data/adb/nemotron-session-patches`, el hash esperado en el control y el hook
`boot-completed.sh`. No se reinició el teléfono. Los backups previos están en
`/data/adb/nemotron-session-patches/backup-app-launch-20261119/`.
## Margen de tarjeta al volver desde Recientes (19 nov 2026)

Las capturas del usuario mostraron que, al deslizar una tarea hacia arriba desde
Recientes, el nombre de WhatsApp chocaba con el título dentro de la tarjeta y el
contenido daba un salto. El diagnóstico fue `RecentStyler.getSceneTopMargin`:
el parche inmersivo devolvía cero y eliminaba el espacio que Samsung reserva
entre la etiqueta de tarea y la captura. Se mantiene el ajuste de insets del
status bar, pero `ImmersiveRecentsInsets.forSceneTopMargin` ahora conserva el
margen calculado por Samsung. Se conservan también `setDisableStartingWindow`
en la apertura de iconos y en las dos rutas de lanzamiento desde Recientes.

El nuevo candidato combinado quedó montado en Zygote con SHA-256
`1b6ddd46127e4b96eb7902030e46713bd77806676306a37f00193dd79bfff8e7`; el APK
stock de `/system` sigue intacto. El nuevo hash se guardó en el almacén y en el
hook de KernelSU para el próximo arranque. Las escalas de animación no se
modificaron y no se reinició el teléfono.
### Ajuste posterior: respetar los insets de la captura de Recientes

La prueba en el teléfono mostró que preservar solo el margen superior aún
recortaba el título de WhatsApp. Se retiró también la sustitución de los insets
de la captura: `forScene` y `forSceneTopMargin` ahora devuelven los valores de
Samsung sin alterarlos. `RecentsActivity` sigue ocultando su propia status bar
bajo la política inmersiva, y las rutas de toque/deslizamiento siguen
suprimiendo la ventana temporal de inicio. Candidato actualizado:
`3ad8fd00fb54413c686fcc56972d2af9387329093c42ea8a35953c57567fd56d`.
### Insets superiores de la captura inmersiva

La captura de Recientes todavía recortaba el título porque la ventana
fullscreen de Recents recibe top inset cero, aunque WhatsApp conserva un espacio
interno equivalente a la status bar. `forScene` ahora obtiene la dimensión real
`android:dimen/status_bar_height` y la usa para posicionar la captura; la ventana
de Recents permanece fullscreen, así que la barra no aparece durante el gesto.
El valor observado en este A54 es 95 px. Candidato activo: SHA-256
`5e171d3735beb3c0da334dc1a20d0e9a44daf3f2769e142bf78224b380563796`.
# Reanudación inmersiva desde Recientes — 2026-11-19

El video `hola (1).mp4` mostró que el salto visual ocurría al reanudar una app
que seguía viva desde Recientes. El parche anterior desactivaba la ventana de
inicio para cualquier tarea inmersiva, incluida una app ya activa. Eso dejaba
que la transición de Samsung mezclara la captura de Recientes con el primer
frame de la app y hacía visible el cambio de posición del encabezado.

`RunningTaskLaunch.smali` comprueba los procesos activos mediante
`ActivityManager.getRunningAppProcesses()`. En `TaskListViewModel` y
`LaunchTaskHelper`, One UI Home conserva ahora la ventana/captura de Samsung
cuando el proceso de destino sigue vivo. Solo las tareas cuyo proceso no está
activo usan `setDisableStartingWindow`, que evita la barra incorrecta al iniciar
una tarea fría. El inicio desde icono sigue la misma regla.

La prueba en el A54 reanudó WhatsApp desde Recientes con el gesto de deslizar.
El launcher permaneció vivo, la actividad de WhatsApp quedó en primer plano y
no apareció una nueva excepción de verificación en `AndroidRuntime`. El
teléfono no se reinició; solo se reinició el proceso de One UI Home para heredar
el bind mount de KernelSU.

El candidato activo tiene SHA-256
`70069c364b79cba37b25f76fed515efa13655fd7ce1d7aa16cd70cc41fbac916`. El APK
anterior se guardó antes de reemplazarlo en
`/data/adb/nemotron-session-patches/backup-warm-resume-v5-20261119/`.

## Reapertura desde el icono con la app viva — 2026-11-19

El fallo al abrir una app por segunda vez desde el icono no era el mismo camino
que la apertura fría ni el gesto de Recientes. One UI enviaba el intent del
launcher a `.Main`, mientras el registro vivo de WhatsApp era
`.home.ui.HomeActivity`. Android llevaba la tarea al frente mostrando antes su
`SnapshotStartingWindow`, que provocaba el salto del encabezado. Los primeros
intentos tampoco eran válidos: la consulta de atajo usaba el paquete de destino
en vez del componente activo, y una versión temporal reutilizaba registros smali
con tipos incompatibles; Android rechazó esa clase con `VerifyError`.

El arreglo final obtiene la actividad superior de la tarea activa con
`ActivityManager.getRunningTasks`, comprueba que pertenece al paquete del icono
y la reabre con `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP` y un extra
opaco. Así Android entrega el intent a la actividad viva como
`LAUNCH_SINGLE_TASK` y no presenta la captura vieja. Si la app no tiene proceso
vivo o no se encuentra una actividad, se conserva `LauncherApps.startShortcut`
original para la apertura fría. La inyección del método también quedó habilitada
para la compilación `-StockHost`.

La primera versión solo cubría atajos fijados; Kindle y algunas apps seguían
usando `LauncherApps.startMainActivity` o el inicio directo en la actividad
del launcher. La versión v24 intercepta ambos brazos de la transición nativa
de apertura, conserva las rutas frías de Samsung y, cuando detecta un proceso y
una actividad viva, resuelve dinámicamente la actividad superior del paquete.
No usa una lista fija de paquetes, así que la misma lógica aplica a las apps
instaladas ahora y a futuras apps que se abran desde One UI Home.

En el A54 se verificó la ruta fría y la reapertura con YouTube Music y Excel.
También se probó reapertura con Kindle, Discord, Teléfono y WhatsApp. En las
reaperturas capturadas, ActivityTaskManager registró el intent dirigido a la
actividad viva con `NEW_TASK | SINGLE_TOP`, `has extras` y resultado de tarea
existente; no apareció `SnapshotStartingWindow` en esos registros. El A54 no
se reinició ni se bloqueó. El candidato activo v25 tiene SHA-256
`35c547b5a1f35d9d1975fcaef0c5f274a4ea7b11e4116ca24441f93c0b6af782`.
v25 excluye explícitamente el paquete del launcher de la reutilización de
actividad: Home y Recientes no deben interceptarse como si fueran una app
abierta. El APK y los hashes de `oneui-control.sh`, `boot-completed.sh` e
`install-update.sh` se actualizaron para volver a aplicarlo al próximo arranque.
La prueba reveló que v24 también intentaba traer la propia actividad `.Launcher`
al frente durante la transición de Recientes. v25 omite el paquete
`com.sec.android.app.launcher` en las dos rutas dinámicas. Se confirmó con
`KEYCODE_APP_SWITCH`: `RecentsActivity` queda al frente y se muestran las
tareas; no hubo crash ni relanzamientos repetidos de `.Launcher`. El teléfono
no se reinició.

Para que al deslizar una tarjeta hacia abajo el contenido coincida mejor con
la ventana inmersiva de destino, v26 reduce a la mitad el inset superior que
RecentStyler aplica a `getSceneInsets`, `getStandardInsets` y `getLayoutInsets`.
El inset completo acortaba la vista previa unos 48 px en el A54. La reserva de
media altura añade ese espacio a la tarjeta, sin alterar la velocidad de las
animaciones ni mostrar la status bar. En el A54 se comprobó la nueva proporción
en Recientes y el gesto de deslizar abrió Excel y YouTube Music a pantalla
completa; al volver a abrir Recientes, `RecentsActivity` seguía operativa. v26
tiene SHA-256
`13107bde5f2c81b811802cd0880946d59a4ab35c5679cec054ea75efad345236` y queda
configurado para aplicarse en el próximo arranque. No se reinició el teléfono.

### Ajuste de tamaño de las tarjetas para el gesto hacia pantalla completa

La evidencia del 20 de noviembre confirma que la tarjeta seleccionada quedaba
pequeña dentro de Recientes y que, al soltar el gesto, debía recorrer demasiado
espacio hasta ocupar la pantalla. v27 ya eliminaba el inset superior de la
escena, pero no cambiaba la escala del preview. Se probó v28 con el margen
superior de la escena en cero; se descartó porque acercaba el título de la
actividad al nombre de la tarea y empeoraba la composición. v27 se restauró
inmediatamente.

v29 aumenta de `0.631/0.65` a `0.78` la escala de los estilos Stack y TiltStack
para teléfonos, en X e Y a la vez. Mantiene la proporción de la tarea y conserva
el margen de Samsung entre el nombre de la tarea y el contenido. El inset
superior inmersivo sigue en cero, la velocidad global de las animaciones no se
modifica y la barra de estado de Recientes sigue oculta.

En el A54, sin reiniciar, se verificó que Recientes abrió con las tarjetas más
grandes; deslizar la tarjeta de Ajustes hacia abajo abrió su actividad de uso
de batería a pantalla completa, y Recientes volvió a abrir después. El APK v29
tiene SHA-256
`4f035d2175baae72746e31655de12c0b1dafa58fedb33cc06f937e143408f7e5`. Se guardó
una copia de v27 en `/data/adb/nemotron-session-patches/backup-pre-v28.apk`.

### Centrado dinámico de escenas por tarea

Las capturas de Maps y Gemini mostraron que el escalado de v29 no bastaba: el
espacio libre cambiaba según la tarea. v30 y v31 intentaron centrar usando
registros temporales que ya tenían tipos vivos en `createStyleData`; ART rechazó
esas versiones y se restauró v29 en cada intento. No se dejó ninguna versión
inestable montada.

v32 conserva v29 como base y centra el `RectF` devuelto por
`RecentStyler.getSceneCoordinate` dentro de los límites disponibles, midiendo el
centro real de cada escena. El método usa registros nuevos (`v46`–`v48`) para no
pisar el `PointF` que Samsung mantiene vivo. Las pruebas mostraron que este
centrado aún dejaba una banda superior visible durante el arrastre.

v33 conserva ese centrado dinámico y desplaza la escena un 3.125% hacia arriba
dentro de sus límites. El título de la tarea se mueve con su `RectF`, reduciendo
el espacio superior sin cambiar escalas ni tiempos globales de animación. El
helper solo actúa cuando la política inmersiva está activa.

En el A54 se verificó la firma v3 del APK. Recientes abrió sin un nuevo
`VerifyError`; las tarjetas de Maps y Gemini quedaron más arriba, y el gesto
descendente abrió Maps a pantalla completa con la barra superior oculta. v33
tiene SHA-256
`a1f5fe8f2ed8dfe563a3169f22c5da69919c43efe2bf6b1d146c5f56ffa13d7d`. Ese hash
está en el almacén KernelSU, `oneui-control.sh`, `boot-completed.sh`,
`install-update.sh` y el controlador integrado en Nemotron para reaplicarlo al
arrancar. Se conservó v32 en
`/data/adb/nemotron-session-patches/backup-before-v33.apk`; el teléfono no se
reinició.

### Normalización del inset superior para todas las tareas (20 nov 2026)

Excel y YouTube Music exponían valores distintos para el inset superior de su
captura. Restar la altura de la barra a ese valor dejaba un residuo solo en
algunas tareas. La variante v58 pone a cero únicamente el inset superior que
`RecentStyler` usa para posicionar las tarjetas cuando la política inmersiva
está activa; mantiene intactos los insets izquierdo, derecho e inferior y las
coordenadas de contenido por tarea. No usa listas de paquetes ni cambia la
escala global de animación.

En el A54 se comprobó que Recientes sigue abriendo, y que al seleccionar las
tareas de Excel y YouTube Music ambas vuelven a primer plano. El APK stock en
`/system` permanece intacto y no hubo reinicio. v58 está montada por KernelSU y
su SHA-256 es `f3c102d82d9212b4176f107e07761b78008be4c4f93577c23cc4108753b90ff8`.
El candidato, los hashes de arranque y el controlador del módulo se actualizaron
para reaplicarse después de un reinicio.

### Corrección por tarea del salto al abrir desde Recientes (20 nov 2026)

La diferencia persistente entre Excel y YouTube Music estaba en la salida
compartida de la animación: `PlayerImpl.calculateInset()` combina los insets de
la escena con `RemoteAnimationTarget.contentInsets`. La corrección anterior
restaba una altura fija de status bar; como las apps reportan insets superiores
distintos, algunas quedaban bien y otras conservaban un desplazamiento.

v59 conserva la geometría nativa de cada tarjeta y deja en cero solo el inset
superior de la salida de transición mientras `immersive.status=apps,-com.sec.android.app.launcher`
está activa. Los insets izquierdo, derecho e inferior no cambian, tampoco se
fuerza una escala o una proporción común. El cambio no contiene excepciones por
paquete, así que usa la misma regla para tareas presentes y futuras.

En pruebas en el A54, el gesto descendente desde la tarjeta centrada devolvió
Excel, YouTube Music y WhatsApp a primer plano; también se repitió YouTube Music
y WhatsApp desde Recientes en caliente. Se comprobó que Recientes sigue
respondiendo y que Excel conserva la proporción de pantalla. No se reinició el
teléfono ni se cambió `policy_control`; `/system` conserva su hash stock
`b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`.

El candidato v59 tiene SHA-256
`3764113cb781aea44ba0f79149ab366f5a88e2e1a283033cdef2f855bdeda58d`. El
controlador activo informa ese hash en Zygote y en el proceso de One UI Home.
El almacén KernelSU y los hashes del arranque se actualizaron para restaurar
v59 tras un reinicio.

### Inset superior dinámico por tarea (20 nov 2026, v61)

La prueba repetida mostró que conservar sin cambios `RemoteAnimationTarget.contentInsets.top` deja saltos distintos según el valor reportado por cada app. v61 resta el alto de `status_bar_height` del inset superior de la tarea y lo limita a cero; conserva cualquier margen adicional de la app. El ancho, alto y proporción nativos de la tarjeta quedan intactos. La misma regla se aplica a todas las tareas, sin listas por paquete.

En el A54, sin reiniciar, se inició el gesto desde Recientes para Excel, YouTube Music, WhatsApp y Gemini. Sin embargo, la grabación de Excel disponible se queda mayormente en la pila de Recientes y no demuestra por sí sola la expansión completa; después el usuario confirmó que Excel aún muestra el salto aunque YouTube Music se ve bien. Por tanto, v61 no queda validada como arreglo universal y esta diferencia sigue pendiente. Recientes siguió abriendo y el hash del APK stock en `/system` permaneció `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`.

El candidato v61 tiene SHA-256 `ddafd6177e734d6495ebd33dac3eaf35718c9d9db176c801e48314b9e69f92e4`. El montaje de Zygote, el almacén KernelSU y los hashes de los scripts de arranque apuntan a v61. No se reinició el teléfono; por eso la reactivación quedó configurada, pero no se validó con un arranque real. La sesión ADB inalámbrica se perdió antes de repetir la comparación visual final; el próximo diagnóstico debe registrar Excel y YouTube Music en la misma secuencia de Recientes y confirmar el cuadro al soltar, sin asumir que abrir la actividad equivale a corregir su geometría.

### Normalización al renovar cada tarjeta de Recientes (20 nov 2026, v62 candidato)

El video `C:\Users\adaredu\Downloads\errores.mp4` y las capturas adjuntas muestran que la tarjeta de YouTube Music cambia de recorte al abrir la app y regresar a Recientes. La ruta de transición ya ajustaba `RemoteAnimationTarget.contentInsets`, pero la conversión de cada `TaskSnapshot` a `ThumbnailData` seguía incorporando el inset superior original. Por eso una tarjeta podía verse completa tras actualizarse y volver a producir el salto al abrirla.

El candidato v62 normaliza también los insets de contenido durante `ThumbnailData$Companion.getInsets()`, antes de fusionar el inset del recorte de cámara. Usa la misma política inmersiva y la dimensión `status_bar_height` en tiempo de ejecución, sin listas por paquete ni escalado de la tarjeta. Así el recorte se vuelve a corregir en cada actualización de la captura, además de la transición en vivo.

v62 compiló desde el APK stock y pasó la verificación de firma v3. SHA-256: `9410f55e05c7bb6ee10af3f5b2a1e8abadfabe3ab3dd57f7c635f683bcf79363`. Después se montó con KernelSU en Zygote y en One UI Home, sin reiniciar; el APK de `/system` permanece stock (`b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`).

La comprobación en el A54 incluyó retornos repetidos desde Recientes con Excel, YouTube Music, WhatsApp y Spark. En Spark, la tarea volvió a primer plano desde la tarjeta y la tarjeta siguió en proporción vertical al volver a Recientes. En WhatsApp, las capturas centradas antes y después de reabrir fueron idénticas. YouTube Music se probó tras desplazar el contenido para forzar una captura nueva y volver a abrir desde esa tarjeta. El ADB de Gemini abrió la actividad de Google y su teclado, pero la navegación automatizada saltó a otra tarea al volver a Recientes; no se considera una verificación concluyente de su segundo ciclo.

Esto respalda que la actualización de `TaskSnapshot` se normaliza al crear cada tarjeta, pero no demuestra todavía que el gesto quede perfecto en todas las apps instaladas. La grabación original muestra que el recorte superior de la tarjeta cambia al regresar a Recientes, y la anomalía visible ocurre al soltar el gesto cuando la captura y el destino de la transición tienen geometrías distintas. La verificación que falta es grabar el dedo/gesto y el instante de soltar en cada ciclo; abrir una actividad por ADB no sustituye esa comprobación visual. No se detectó `VerifyError` ni una excepción fatal de One UI Home en el tramo de logcat revisado.

### Geometría única tras fusionar insets (21 nov 2026, v63)

El seguimiento del video `C:\Users\adaredu\Downloads\errores.mp4` apuntó a dos cálculos que podían divergir según el orden en que Samsung renueva una tarjeta: el inset de escena de Recientes y los insets de la captura del `TaskSnapshot`. v63 hace que `forScene()` reste la altura de barra en tiempo de ejecución y conserve cualquier margen de contenido adicional. También mueve `forSnapshotContentInsets()` al final de `getInsets()`, después de que Samsung combine el inset de contenido y el recorte de cámara. El parche no incluye excepciones por app ni cambia el tamaño o escala de las tarjetas.

Se compiló desde el candidato v62, se alineó y verificó con firma v3. SHA-256: `2490b4bdd881b8d401c16364374493275c9041c028711d49d83af2cf63e9578a`. El montaje en caliente quedó activo tanto en Zygote como en One UI Home; `/system` mantiene el APK de Samsung sin modificar. Se actualizaron el candidato guardado en KernelSU y los hashes del controlador y del script de arranque para volver a aplicar v63 tras reiniciar. No se reinició el teléfono.

La prueba ADB recorrió Recientes y soltó la tarjeta de YouTube Music por segunda vez; además abrió desde Recientes Excel, WhatsApp, Gemini, Fitia, Instagram, Discord, Kindle y Maps. Las nueve actividades probadas terminaron en primer plano a pantalla completa y sin crash de One UI Home. Las capturas de YouTube Music, Excel, WhatsApp y Gemini confirmaron que el contenido ocupa la ventana completa y conserva su proporción vertical. Además se grabó un `screenrecord` de 8 s durante un gesto ADB repetido en YouTube Music; los cuadros muestreados muestran la tarjeta creciendo progresivamente hasta ocupar la pantalla, sin un salto de recorte al soltar. La reconexión inalámbrica se interrumpió una vez al sintetizar un gesto largo; se reconectó y se cerró el panel rápido con la tecla atrás. El gesto se generó por ADB, no con el dedo. La prueba cubre apps representativas, no cada paquete instalado. Por eso no se afirma que el comportamiento haya sido demostrado en absolutamente todas las apps; el ajuste es común a las tareas y no está limitado por paquete.

El controlador volvió a informar el hash v63 para Zygote y `com.sec.android.app.launcher`; el hash del APK stock sigue siendo `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`. El buffer conserva errores anteriores a v63, entre 19:02 y 19:06: `SQLiteFullException` en `Room/WorkManager` dentro de One UI Home (y errores `binder` asociados). A las 00:11 del 21 nov, el proceso activo seguía usando v63; las nueve pruebas posteriores terminaron en sus apps y no apareció `VerifyError`. No atribuyo esos crashes previos al ajuste de insets.

### Seguimiento visual del refresco de miniaturas (21 nov 2026)

Al revisar el video completo `C:\Users\adaredu\Downloads\errores.mp4` (81.1 s, 1920×1200, 30 fps) cuadro por cuadro, se confirmó que el problema no es solo el gesto al soltar: la tarjeta visible en Recientes cambia su origen vertical después de abrir la app y volver. En las capturas de WhatsApp de la misma sesión, antes de abrir el título dentro de la tarjeta aparece aproximadamente 100 px debajo del borde; al volver aparece a unos 40 px. La imagen persistida del snapshot renovado coincide con este segundo estado. Esto reproduce el patrón descrito por el usuario: el recorte de la tarjeta cambia y el siguiente lanzamiento se comporta distinto.

Se compararon los `.proto` de `TaskSnapshot` de WhatsApp (tarea 26442) y YouTube Music (26408), leídos del almacenamiento del sistema después de la prueba. Ambos describen tareas de 1080×2340; los campos `inset_left/top/right/bottom` y `letterbox_inset_*` están ausentes, por lo que son cero en protobuf, y ambos snapshots tienen el mismo estado de ventana. La especificación AOSP asigna esos campos a los insets del snapshot y los campos 14/15 al ancho/alto de tarea. Por tanto, en estas dos capturas `forSnapshotContentInsets()` no cambia el rectángulo: el valor superior ya es cero. El v63 no corrige el cambio de origen que se ve entre la miniatura vieja y la renovada.

La evidencia actual apunta al cálculo de `RecentStyler`/`InsetsData` y a cómo se encuadra un snapshot renovado en la tarjeta, no a una diferencia de proporción entre apps. Esto descarta como explicación suficiente la hipótesis anterior de un inset superior distinto en los `.proto`; todavía falta registrar los valores efectivos de `sceneInsets`, `standardInsets`, `layoutInsets` y las coordenadas antes y después de recalcular la tarjeta. La conexión ADB inalámbrica se perdió durante esa repetición y no se reinició el teléfono. El arreglo permanece pendiente; v63 sigue montada como última versión conocida y no se afirma que el fallo esté corregido.

En el smali de v63 se localizó la ruta de render: `RecentStyler` forma el área de escena a partir de `WindowBounds` y `InsetsData`, y `getSceneCoordinate()` coloca allí el rectángulo escalado. `TaskSceneExtensionKt.getTaskSceneData()` copia directamente `ThumbnailData.insets` al modelo que pinta la tarjeta; `TaskSceneViewKt.getInsets()` también lee ese campo directamente. v63 normaliza el rectángulo al convertir un `TaskSnapshot` nuevo, antes de crear `ThumbnailData`, pero no hay aún evidencia de qué `TaskSceneData` ni qué `InsetsData` se usan en el instante del salto. La siguiente captura diagnóstica debe registrar juntos el inset de `ThumbnailData`, los cuatro campos de `InsetsData`, el rectángulo final de escena y la escala, en una apertura inicial y otra tras volver de la misma app. No se añadirá una regla nueva por paquete ni se cambiará la escala global sin esa comparación.

### Candidato de normalización al consumir cada tarjeta (21 nov 2026, v64 local)

Para cubrir tanto miniaturas retenidas en memoria como snapshots recién renovados, el candidato local v64 mueve la normalización del constructor de `ThumbnailData` a los puntos donde One UI Home consume el recorte: `TaskSceneData.getInsets()` y `TaskSceneViewKt.getInsets()`. La rutina trabaja sobre una copia del `Rect`, de modo que volver a leer el mismo objeto no reste varias veces la altura de la barra; si el inset ya es cero, devuelve el rectángulo original y no crea una copia. No altera la escala, los límites ni la proporción de la tarjeta y se activa solo mientras `policy_control` mantiene el modo inmersivo de apps con One UI Home excluido.

El candidato se compiló desde el árbol decodificado de v63, el paquete sigue siendo `com.sec.android.app.launcher`, `zipalign` verificó el APK y `apksigner` confirmó la firma v3. SHA-256: `5e3f216560fdb73be07dd6f526e408c2426437f1384e393d8e6ff9b098658b19`. La verificación estática encontró una sola ruta de lectura en cada punto parcheado y cero errores de sintaxis PowerShell. El candidato está en `C:\Users\adaredu\work\nemotron-candidates\v64-readtime-optimized.apk`; todavía no se instaló ni se montó porque ADB inalámbrico está desconectado. v63 continúa como la versión activa conocida y el arreglo no está validado en ejecución. La siguiente prueba debe comparar WhatsApp y YouTube Music antes de abrir, después de volver, y al repetir la apertura, además de al menos una app adicional.

### Recorte repetible al reabrir tareas desde Recientes (21 nov 2026, v66 candidato)

La comparación de capturas confirmó que el snapshot renovado de WhatsApp y YouTube Music informa `insets.top = 0`. La normalización anterior restaba el alto de la barra a ese cero y, por tanto, no corregía nada después de que Samsung refrescaba la miniatura. v66 aplica una regla idempotente en los getters de `TaskSceneData` y `TaskSceneView`: con la política inmersiva activa, usa `max(insets.top, status_bar_height)` y conserva intactos los otros tres lados. Trabaja sobre una copia del `Rect`, no muta el snapshot cacheado, no cambia la escala y no depende del paquete de la app.

El APK se reconstruyó desde todos los DEX (`apktool b -f`) después de detectar que una compilación incremental había conservado una llamada diagnóstica vieja y hacía cerrar One UI Home. El APK limpio se decodificó otra vez: no contiene los hooks de diagnóstico, y `apksigner` verificó la firma v3. SHA-256 de v66: `91235933c98db7523cd23fc0754dc4dff594d0d263efcc914bada8c0183f5f4b`.

En el A54, v66 se montó en Zygote y One UI Home sin reiniciar. Se repitieron los ciclos de abrir Recientes, iniciar una tarjeta con el gesto descendente y regresar a Recientes para WhatsApp y YouTube Music. Las capturas resultantes conservaron el rectángulo vertical de aproximadamente 680×1477 px en la pantalla de 1080×2340, con el contenido en el encuadre esperado y sin una zona vacía superior añadida al refrescarse. One UI Home permaneció en ejecución en los ciclos observados. Esto verifica visualmente retornos repetidos de esas tareas; no demuestra cada app ni todos los gestos hechos con el dedo.

La prueba se hizo en un montaje temporal durante la sesión. La conexión ADB del A54 se perdió antes de copiar v66 al almacén y al módulo KernelSU; los scripts locales `boot-completed.sh`, `oneui-control.sh` e `install-update.sh` ya señalan el hash nuevo, pero el teléfono todavía conserva v63 como candidato persistente de arranque. No se reinició el teléfono. Para que v66 sobreviva un reinicio, hace falta volver a conectar y autorizar el A54 y copiar el APK validado al almacén del módulo; mientras tanto, no se debe dar por hecha la persistencia.

### Persistencia KernelSU de v66 tras reconectar ADB (21 nov 2026)

Al recuperar la conexión inalámbrica del SM-A546E, se verificó que v66 seguía montada en Zygote y One UI Home. Se copió el APK a `/data/adb/nemotron-session-patches`, y se actualizaron `boot-completed.sh`, `oneui-control.sh` e `install-update.sh` en el almacén y en el módulo KernelSU. El hash `91235933c98db7523cd23fc0754dc4dff594d0d263efcc914bada8c0183f5f4b` coincide entre el candidato persistente, Zygote y el proceso del launcher; `/system` sigue con el APK stock `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54a8e93a18d9da1`. v63 se conserva en `backup-before-v66-20261121`. No se reinició el teléfono, así que la ruta de arranque quedó configurada pero no se probó mediante un reinicio real.

### Insets coherentes al renovar una tarea (21 nov 2026, v67)

Al revisar la salida de v66 se encontró una contradicción concreta: la escena
inmersiva y `PlayerImpl.calculateInset()` dejaban el recorte superior en cero,
pero `forSnapshotContentInsets()` elevaba el recorte del snapshot a la altura de
la status bar (95 px en este A54). Cuando Samsung actualizaba la miniatura, la
tarjeta y el destino de la animación quedaban en marcos distintos. v67 mantiene
en cero el inset superior en los getters de `TaskSceneData` y `TaskSceneViewKt`,
y conserva los insets izquierdo, derecho e inferior. La copia solo se crea si
el valor superior necesita corregirse; no se muta el snapshot cacheado ni se
introducen excepciones por app.

El APK v67 (`v67-canonical-insets.apk`) quedó alineado y verificado con firma
APK v3; SHA-256 `703934a0bc048ad5f14432ccb94fa3ee800be7ed5e0205e3b7d9695c5fb0b467`.
Se montó con KernelSU sin reiniciar. El mismo hash coincide en Zygote, el
proceso de One UI Home y el candidato del almacén; `/system` conserva el APK
Samsung original `b97823026a11786674f49ab0b763b055a539426e6f1221d5d54e8a93a18d9da1`.
Los controladores y el hook de arranque se actualizaron en el almacén y el
módulo KernelSU. El respaldo v66 está en
`/data/adb/nemotron-session-patches/backup-before-v67-20261121/`.

En las comprobaciones ADB, tres gestos consecutivos desde la tarjeta centrada
de YouTube Music devolvieron la actividad a pantalla completa y Recientes siguió
abriendo; no apareció `VerifyError` ni se cerró One UI Home. WhatsApp también
se abrió desde una tarjeta centrada, pero durante las repeticiones la pila
visual alternó entre WhatsApp y YouTube Music, por lo que no cuento esos ciclos
como validación repetible de WhatsApp ni de todas las apps. La geometría durante
un gesto real con el dedo y la cobertura universal todavía requieren revisión
visual; no se declara resuelto sin esa confirmación. No se reinició el teléfono.
