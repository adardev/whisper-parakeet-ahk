# Funciones de Nemotron Home en Samsung One UI Home

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
