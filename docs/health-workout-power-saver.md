# Ahorro de energía durante ejercicios de Samsung Health

Nemotron Core puede desactivar temporalmente el Ahorro de energía durante un
entrenamiento iniciado en Samsung Health y restaurar el estado previo al
terminar.

La opción **Battery saver for GPS workouts** guarda la preferencia, pero solo
concede acceso al listener mientras la Galaxy Fit3 está conectada. Al apagar o
perder la conexión de la pulsera, Core restaura el estado previo del ahorro,
revoca el acceso y deja de recibir las notificaciones de Health. El tile Fit3,
los eventos de conexión Bluetooth y el arranque de Core vuelven a sincronizar
este estado. Si la preferencia está apagada, el listener permanece desactivado.

Cuando la pulsera está conectada, el listener escucha el canal de notificaciones
`Ongoing workouts` de Samsung Health y filtra por el título de la actividad.
Solo acepta entrenamientos de ruta: Walk/Walking, Run/Running,
Bike/Biking/Cycling y Hike/Hiking, con sus nombres habituales en español.
Samsung documenta GPS para caminar, correr y andar en bicicleta; también admite
rutas GPX para Running, Bike y Hiking. Por eso se incluye senderismo. No se
incluyen Backpacking ni otros deportes al aire libre sin confirmar su ruta en
Samsung Health. Bicicleta fija, pesas y demás actividades quedan ignoradas. Al aparecer una notificación
persistente de una actividad aceptada, Core lee `global low_power`; solo si vale
`1`, guarda ese estado y escribe `0`. Samsung Health actualiza la misma
notificación con el texto `Workout finished` al concluir; Core detecta esa
actualización y devuelve `low_power` a `1`. Si el ahorro ya estaba apagado antes
de entrenar, Core lo deja apagado. Shizuku concede el acceso a notificaciones y
ejecuta los cambios.

El listener reconcilia sus notificaciones activas al reconectarse, para cerrar
un estado pendiente si Core se reinicia mientras Health termina el ejercicio.
La automatización no observa la pantalla ni sondea en bucle.

## Prueba en A54

El 27 de octubre de 2026 se inició un entrenamiento corto de Walking con
`low_power=1`. Samsung Health publicó la notificación persistente a las 12:28:22;
Core cambió `low_power` a `0` a las 12:28:23 y desapareció el aviso de ahorro en
la pantalla del ejercicio. Al finalizar, Health actualizó el texto a
`Workout finished`; Core restauró `low_power=1` a las 12:29:01. La entrada de
prueba se eliminó de Samsung Health.

El 27 de octubre de 2026 también se probaron en el Galaxy A54 **Bike indoor**,
**Peso**, **Corporal** y **Bike**. Con Ahorro de energía encendido (`low_power=1`),
Bike indoor, Peso y Corporal lo dejaron en `1`. En la sesión de Corporal,
Samsung mostró como notificación el ejercicio interno `Push-ups`; tampoco se
confundió con una actividad de ruta. Al iniciar Bike, Core lo cambió a `0` y,
al finalizar, volvió a `1`. Se borraron las sesiones guardadas durante las
pruebas. En Bike apareció el aviso del sistema para activar Location Accuracy;
se rechazó para no alterar ese ajuste, así que esta prueba confirmó el cambio
de Ahorro, pero no una posición GPS real.

La primera prueba reveló que `cmd power set-mode` no cambiaba este estado en el
A54. El control usa `settings put global low_power`, cuya lectura posterior
confirma cada cambio antes de continuar.
