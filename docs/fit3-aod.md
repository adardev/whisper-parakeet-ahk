# Fit3 AOD y Sleep Mode

## Corrección vigente del 21 de octubre de 2026

El único horario automático vigente es la activación a las 23:00. El apagado es manual; no hay activación por bloqueo de pantalla ni apagado a las 08:00. Sleep Mode envía AOD y Tocar para activar apagados; el apagado manual del modo los enciende. Levantar para activar permanece apagado en ambos estados.

El historial identifica la regresión: `8a467cd` implementó `SamsungFit3Handoff`, que liberaba la sesión SAP de Samsung antes del intercambio BLE. `6a6616e`, al separar los paquetes, eliminó esa cesión y el encendido temporal de Bluetooth, conservando el codec. El reloj no contestaba otra negociación SAP mientras Samsung Accessory mantenía la suya.

La cesión se recupera pausando solamente `com.samsung.accessory` durante el envío. Un `finally` habilita, desbloquea y vuelve a iniciar Accessory. Una marca persistida antes de la pausa permite recuperar su estado en el siguiente arranque con Shizuku si el proceso murió durante el intercambio. No se añade sondeo periódico. Desde el cambio del tile Fit3, el plugin se mantiene pausado cuando el tile está apagado; esta sesión BLE directa no depende de dejarlo activo.

Si Bluetooth estaba apagado, se enciende para enviar y se restaura al terminar, conservándolo si se conectó otro periférico. Se mantienen las dos lecturas de confirmación; una escritura BLE no cuenta como éxito.

Prueba aislada en el A54 del 21 de octubre, con Fit3 Plugin habilitado en esa versión: negociación SAP a las 10:28:10, MTU 500; dos lecturas `AOD=true RAISE_TO_WAKE=false TOUCH_TO_WAKE=true` a las 10:28:10/12; dos lecturas `AOD=false RAISE_TO_WAKE=false TOUCH_TO_WAKE=false` a las 10:28:33. Samsung Accessory se restauró después.

La corrección integrada compiló y se instaló conservando datos. Prueba por el tile real: a las 10:32:07/08 la Fit3 confirmó AOD encendido al apagar Sleep Mode; a las 10:32:22/23 confirmó AOD apagado al encenderlo. En ambos casos las dos lecturas coincidieron, `fit3AodConfirmed=true`, Accessory se restauró automáticamente y su marca de recuperación quedó vacía. En esa versión de la app, Fit3 Plugin siguió habilitado. La entrega de la alarma nocturna sigue pendiente de las 23:00; comparte esta misma operación verificada.

## Descripción histórica de la implementación inicial (reemplazada)

Los horarios y las rutinas por pantalla descritos a continuación ya no están activos. Se conservan aquí como contexto del protocolo inicial.

At 23:00 Nemotron turns off Always On Display and Touch to wake on the paired
Galaxy Fit3 over SAP/BLE; at 08:00 it turns both on. Raise to wake is explicitly
kept off in both states, including manual tests and temporary night pauses. It reads back the full
settings reply twice, including fragmented replies, and a BLE write by itself
is not counted as success. The manual night and morning test buttons call only
these same display actions. They do not change Airplane mode.
At the same scheduled boundaries, the separate airplane-mode routine keeps
its existing behavior; it only changes that setting when its current state
differs from the scheduled state.
Before a BLE change Nemotron temporarily unfreezes the Fit3 tile packages and
turns Bluetooth on if needed. After sending the settings at either boundary,
it freezes the Fit3 tile again and turns Bluetooth off only if no other
peripherals remain connected.

During the night, manually disabling airplane mode with the screen on pauses
the watch's night settings too: AOD and Touch to wake are enabled temporarily.
The next screen-off sends both settings off again before restoring airplane
mode, even if airplane mode was already enabled manually. Watch actions from
these broadcasts run in order, so a quick lock cannot leave an older daytime
request running after the night request. Failed night restoration remains
pending for the existing Shizuku-available retry, without polling.
Between 05:00 and 08:00, disabling airplane mode still means the user is awake
for the day: both watch settings remain enabled after locking the phone.

The small codec uses protocol details inspected at commit
`1ac39f063fe5d0bcae72c71acf40d40218752365` of
[Fit3-App](https://github.com/yuriyurin/Fit3-App): service 11 (settings),
message 6 / parameter 9 for AOD, parameter 10 for Raise to wake (always off),
parameter 11 for Touch to wake, and message 32 for the full settings readback.
Only capability negotiation, these three settings messages, and SAP framing are
implemented here; there is no scan, persistent connection, reconnect loop, or
periodic poll. Each BLE operation has a 30-second bound.

The Samsung Accessory GATT client may occupy the Fit3 connection. Nemotron
briefly hands off the connection and restores the Samsung packages afterward.
When the watch does not answer Nemotron's SAP session, Nemotron reports that
failure instead of claiming AOD changed.
