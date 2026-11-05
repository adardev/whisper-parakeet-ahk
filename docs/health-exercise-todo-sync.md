# Sincronización de ejercicio con Fit3 y Microsoft To Do

El tile Fit3 inicia un `JobScheduler` periódico de Samsung Health mientras el tile indica que Fit3 está activo. El trabajo corre aproximadamente cada hora, requiere conectividad y batería no baja, y se cancela cuando el tile pasa a inactivo. El estado se conserva al reiniciar y se restaura mediante `HealthBootReceiver`.

El servidor acepta snapshots de ejercicio de hasta dos horas de antigüedad. Un snapshot atrasado se considera desconocido: no desmarca la tarea diaria ya completada y no habilita un cobro basado en minutos incompletos. Para cada fecha, la tarea `Ejercicio` es monotónica: un snapshot parcial posterior no revierte una finalización.

La compilación debe realizarse con Android SDK instalado; la verificación del servidor incluye sintaxis Python, lectura del snapshot de Firebase y lectura de Microsoft Graph.
