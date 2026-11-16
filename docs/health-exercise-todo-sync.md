# Sincronización de ejercicio con Fit3 y Microsoft To Do

El tile Fit3 activa una lectura inmediata y un `JobScheduler` periódico de Samsung Health mientras indica que Fit3 está activo. La lectura inmediata evita esperar hasta una hora para el primer snapshot. La periódica sigue corriendo aproximadamente cada hora, requiere conectividad y batería no baja, y ambas se cancelan cuando el tile pasa a inactivo. El estado se guarda sincrónicamente antes de programar/cancelar los trabajos y se restaura mediante `HealthBootReceiver`.

La lectura inmediata se programa únicamente al pasar de apagado a encendido. `onTileState` también se ejecuta al dibujar un tile que ya estaba activo; esos refrescos mantienen el trabajo periódico, pero no vuelven a escribir el estado ni programan otra exportación. Esto evita que abrir repetidamente el panel provoque nuevas consultas de 30 días de Samsung Health. Las transiciones del planificador están sincronizadas para que dos callbacks simultáneos no dupliquen la lectura.

Un trabajo que arranca después de apagar el tile debe salir sin intentar abrir Samsung Health. El SDK responde `3000: Samsung Health is not installed` si el proveedor está oculto por el tile. No debe usarse ese fallo posterior al apagado como evidencia de que el trabajo anterior al apagado falló.

El servidor acepta snapshots de ejercicio de hasta dos horas de antigüedad. Si un snapshot del mismo día ya demuestra que se alcanzó la meta, también acepta esa evidencia aunque tenga más de dos horas; si está atrasado y no alcanza la meta, se considera desconocido. No desmarca la tarea diaria ya completada ni habilita un cobro basado en minutos incompletos. Para cada fecha, la tarea `Ejercicio` es monotónica: un snapshot parcial posterior no revierte una finalización. El arreglo del servidor y su despliegue se documentan en `nas-backup/docs/fitia-health-snapshot-freshness.md`.

La compilación debe realizarse con Android SDK instalado; la verificación del servidor incluye sintaxis Python, lectura del snapshot de Firebase y lectura de Microsoft Graph.
