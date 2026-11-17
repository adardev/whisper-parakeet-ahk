# Sincronización de ejercicio con Fit3 y Microsoft To Do

El tile Fit3 activa una lectura inmediata y un `JobScheduler` periódico de Samsung Health mientras indica que Fit3 está activo. La lectura inmediata evita esperar hasta una hora para el primer snapshot. La periódica sigue corriendo aproximadamente cada hora, requiere conectividad y batería no baja, y ambas se cancelan cuando el tile pasa a inactivo. El estado se guarda sincrónicamente antes de programar/cancelar los trabajos y se restaura mediante `HealthBootReceiver`.

La lectura inmediata se programa únicamente al pasar de apagado a encendido. `onTileState` también se ejecuta al dibujar un tile que ya estaba activo; esos refrescos mantienen el trabajo periódico, pero no vuelven a escribir el estado ni programan otra exportación. Esto evita que abrir repetidamente el panel provoque nuevas consultas de 30 días de Samsung Health. Las transiciones del planificador están sincronizadas para que dos callbacks simultáneos no dupliquen la lectura.

Un trabajo que arranca después de apagar el tile debe salir sin intentar abrir Samsung Health. El SDK responde `3000: Samsung Health is not installed` si el proveedor está oculto por el tile. No debe usarse ese fallo posterior al apagado como evidencia de que el trabajo anterior al apagado falló.

El servidor acepta snapshots de ejercicio de hasta dos horas de antigüedad. Si un snapshot del mismo día ya demuestra que se alcanzó la meta, también acepta esa evidencia aunque tenga más de dos horas; si está atrasado y no alcanza la meta, se considera desconocido. No desmarca la tarea diaria ya completada ni habilita un cobro basado en minutos incompletos. Para cada fecha, la tarea `Ejercicio` es monotónica: un snapshot parcial posterior no revierte una finalización. El arreglo del servidor y su despliegue se documentan en `nas-backup/docs/fitia-health-snapshot-freshness.md`.

La compilación debe realizarse con Android SDK instalado; la verificación del servidor incluye sintaxis Python, lectura del snapshot de Firebase y lectura de Microsoft Graph.

## Duración diaria oficial de Samsung Health (16 nov 2026)

El snapshot ahora incluye `data.exerciseTotalDurationSeconds`, calculado por
`DataType.ExerciseType.TOTAL_DURATION` con un filtro para la fecha local actual.
Este agregado es la fuente que se debe comparar con el total diario de ejercicio
que muestra Samsung Health. No sumes los intervalos `startTime`/`endTime`: entre
sesiones y pausas pueden inflar el resultado. `sessionDetails.durationSeconds`
puede ser menor que el total agregado porque solo describe segmentos de cada
sesión.

Caso de referencia del A54: Samsung Health mostraba 2:00:11 en siete sesiones.
El cron había calculado 103.2 min desde los segmentos, y un primer arreglo
temporal subió el cálculo indebidamente a 154.0 min al contar spans completos.
Se reemplazó por el agregado oficial de Samsung Health. La tarea de ese día se
marcó por la reconciliación normal del cron, nunca editando la tarea a mano. El
servidor mantiene el fallback conservador de segmentos activos solo para
snapshots antiguos que aún no contengan el campo agregado.
