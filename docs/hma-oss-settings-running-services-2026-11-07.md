# HMA-OSS y el cierre de Ajustes en “Running services”

**Fecha:** 7 de noviembre de 2026  
**Dispositivo verificado:** Samsung SM-A546E (Galaxy A54)  
**Rama del repositorio:** `nemotron-root`

## Síntoma

Al abrir **Opciones de desarrollador → Running services** (Servicios en ejecución), Ajustes se cerraba. La pantalla enumera procesos y servicios y consulta los metadatos de sus aplicaciones para mostrar nombres y etiquetas.

## Causa observada

HMA-OSS estaba aplicando la plantilla `Hide_Good_Lock` al paquete `com.android.settings`. La plantilla contiene paquetes que tenían procesos o servicios activos. Al ocultar uno de esos paquetes a Ajustes, la consulta de metadatos devolvía datos incompletos y `RunningState` de Ajustes fallaba al leer una matriz nula.

Los registros de Android mostraban repetidamente:

```text
Process: com.android.settings
FATAL EXCEPTION: RunningState:Background
java.lang.NullPointerException: Attempt to get length of null array
  at com.android.settings.applications.RunningState$ProcessItem.ensureLabel(...)
```

La inspección de servicios activos confirmó servicios de microG (`com.google.android.gms`) y el servicio de teclado Nemotron (`com.nemotron.keyboard`). Esos procesos, y otras apps que Ajustes necesita enumerar, no deben quedar ocultos para `com.android.settings` mientras usa esta pantalla.

## Cambio aplicado en el teléfono

Se conservó la plantilla `Hide_Good_Lock` y sus selecciones; en **HMA-OSS → Manage apps → Settings → Template config → additional apps visible** se añadieron excepciones de visibilidad para Ajustes:

```text
com.google.android.gms
com.nemotron.keyboard
com.sec.android.app.launcher
com.nemotron.voiceime2
com.nemotron.core
com.samsung.android.displayassistant
org.frknkrc44.hma_oss
```

En el archivo runtime de HMA-OSS estas entradas aparecen en `scope.com.android.settings.extraOppositeAppList`. La plantilla se mantiene aplicada a Ajustes en modo blacklist. Por tanto, estas apps siguen seleccionadas en la plantilla, pero HMA-OSS las deja visibles específicamente para Ajustes. No se desactivó ni desinstaló HMA-OSS, microG ni los componentes Nemotron.

La configuración se guardó y verificó en el dispositivo en:

```text
/data/misc/hide_my_applist_hvtnqhzmokzliyoh/config.json
```

Este JSON contiene estado local del teléfono y no se versiona en Git. La lista anterior es la referencia documentada del ajuste aplicado.

## Verificación realizada

1. Se abrió **Running services** desde Opciones de desarrollador.
2. La pantalla cargó y mostró el estado de memoria y la lista de procesos/servicios, incluidos HMA-OSS y Nemotron Keyboard.
3. Se volvió a abrir la pantalla desde Opciones de desarrollador. La actividad `com.android.settings/.SubSettings` quedó en primer plano y no apareció un nuevo crash de `RunningState`.
4. Los registros de crash aún conservaban fallos históricos de las 12:17, 12:20 y 12:23. El más reciente era anterior a la verificación exitosa; los registros viejos no se borraron.

## Diagnóstico si vuelve a ocurrir

Revisar el crash buffer y confirmar que el stack vuelve a mencionar `RunningState$ProcessItem.ensureLabel`. Inspeccionar los servicios activos (`dumpsys activity services`) y permitir que `com.android.settings` vea los paquetes propietarios activos que estén ocultos por la plantilla. Guardar la configuración de HMA-OSS y repetir la apertura de **Running services**.

No conviene quitar excepciones al azar: una excepción hace visible ese paquete para Ajustes y cambiarla puede reintroducir el fallo. Si se quiere revertir este arreglo, editar la lista **additional apps visible** de Ajustes en HMA-OSS y retirar las entradas añadidas; esto restaura la configuración previa y puede hacer que el crash reaparezca.

## Alcance

Este commit documenta una configuración runtime aplicada al A54; no contiene un parche de HMA-OSS ni hace que el ajuste se replique automáticamente en otro teléfono. En una instalación nueva se deben revisar los servicios activos del dispositivo y configurar las excepciones equivalentes en HMA-OSS.
