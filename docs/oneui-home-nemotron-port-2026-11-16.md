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
