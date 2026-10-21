# Sleep Mode y Fit3: correcciones del 21 de octubre

## Actualización posterior: comunicación AOD recuperada

El fallo SAP descrito abajo se resolvió después de consultar el historial: `6a6616e` eliminó la cesión de conexión implementada en `8a467cd`. Se recuperó con `SamsungFit3Handoff`, limitado a Samsung Accessory, sin pausar Fit3 Plugin ni Wearable y sin modificar Health. El APK instalado confirmó dos lecturas AOD encendido a las 10:32:07/08 y dos lecturas AOD apagado a las 10:32:22/23; Accessory se restauró automáticamente en ambos casos. Véase [fit3-aod.md](fit3-aod.md) para la evidencia y la recuperación tras una interrupción.

## Fallos identificados en código

- Sleep Mode devolvía error sin cambiar el modo avión ni su preferencia si la Fit3 no confirmaba AOD. La misma dependencia bloqueaba el apagado manual.
- La alarma ejecutaba el intercambio BLE en un hilo de BroadcastReceiver; podía exceder la vida del receptor. En un arranque en frío también consultaba Shizuku sin esperar su conexión asíncrona.
- Los tres paquetes heredaban la programación nocturna; la programación ahora pertenece solamente a Core. UI/Keyboard cancelan su copia al ejecutar el código actualizado.
- NightRoutineActions deshabilitaba Health al terminar incluso cuando estaba habilitado antes. Esto cambiaba la dirección del siguiente toque Fit3: intentaba conectar una pulsera que ya estaba conectada.
- El apagado Fit3 abandonaba la desconexión ante cualquier error de Health/Firebase y podía esperar cuatro minutos sin explicación visible.

## Comportamiento corregido

Sleep Mode conserva la activación diaria a las 23:00 y el apagado manual. El ajuste Fit3 se intenta, pero su falta de confirmación no bloquea el cambio del teléfono. La operación utiliza un servicio temporal no persistente, con espera máxima de cinco segundos por Shizuku y wakelock limitado a 60 segundos. No arranca al bloquear la pantalla ni se reinicia al terminar. El tile indica que está aplicando ajustes y los errores manuales se muestran.

La rutina conserva el estado previo de Samsung Health. Fit3 considera tanto el estado de Health como la conexión GATT real para decidir si debe desconectar. Antes de desconectar intenta subir datos durante un máximo de 60 segundos. Si falla, avisa que Firebase quedó pendiente, detiene la transferencia y continúa el apagado solicitado: deshabilitar Health no borra su base de datos. No se afirma que un dato ya esté sincronizado si la subida no fue confirmada.

El plugin Fit3 y Wearable no se deshabilitan. Se mantiene la solicitud de desconexión al receptor Samsung existente y la verificación del enlace. Bluetooth solo se apaga si Fit3 era el único dispositivo conectado; si hay otros dispositivos y Samsung no confirma la desconexión, se informa el fallo y se conserva Bluetooth.

## Estado de comprobación

Compilación e instalación de Core completadas por USB en el A54, conservando datos. `dumpsys alarm` confirma la alarma exacta de Core a las 23:00 del 21 de octubre. La entrega nocturna de esta versión sigue pendiente.

Comprobación real, hora local del teléfono:

- 10:17:37: Sleep Mode activado; modo avión confirmado en 1 y preferencia `enabled=true`. Apagado manual posterior confirmado a las 10:18:09.
- La Fit3 no contestó el intercambio SAP/AOD en ninguno de los dos sentidos. El modo del teléfono sí cambió; los ajustes de pantalla de la pulsera siguen sin confirmarse y no deben darse por corregidos.
- 10:18:18: subida a Firebase confirmada. Samsung aceptó el broadcast pero no soltó la conexión; a las 10:18:27, al ser Fit3 el único dispositivo, Bluetooth quedó apagado y el enlace desconectado, con Samsung Health deshabilitado. Plugin y Wearable no se deshabilitaron.
- 10:19:21: encendido y reconexión Fit3 confirmados.

## Estado visual de Sleep Mode y toques duplicados

La versión inicial mostraba `STATE_UNAVAILABLE` durante el intercambio con la pulsera: el icono quedaba gris hasta que terminaba la espera. Además, la exclusión de toques se establecía al entrar al servicio, dejando una ventana entre el toque y `onStartCommand` en la que podían encolarse dos toggles opuestos.

Ahora se reserva la operación de forma atómica en el mismo toque y se transporta un destino explícito (`enabled`) al servicio. El tile muestra ese destino con el subtítulo de operación en curso; al terminar vuelve al estado confirmado, incluido un error si falló. La marca `ACTIVE_TILE` permite solicitar la actualización al finalizar aun si se cerró el panel. No se añade sondeo periódico.

Prueba de la actualización a las 10:21:38: dos clics consecutivos por `cmd statusbar click-tile`; el panel muestra `state=2` y «Activando: enviando ajustes…», con una sola sesión de aplicación iniciada. A las 10:21:54 se confirma `requested=true success=true`, preferencia activada y modo avión en 1. El tile conserva `state=2` y «Activo hasta apagarlo» después de terminar el servicio temporal. No hubo otra acción hasta el apagado manual de prueba a las 10:22:37, que restaura modo avión a 0 y muestra «Desactivando…».
