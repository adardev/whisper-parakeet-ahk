# Sleep Mode: horario y restauración de Fit3

## Funcionamiento (26 de octubre de 2026)

Sleep Mode solo se activa automáticamente cada día a las 23:00, usando la zona horaria local. No hay apagado automático por la mañana: el modo avión y Sleep Mode permanecen como están hasta que el usuario los cambie. El apagado manual restaura los ajustes diurnos de Fit3, incluido Always On Display (AOD), y la conectividad del teléfono.

La activación nocturna se reprograma al iniciar Core, después de reiniciar el teléfono, al cambiar la hora o la zona horaria y al actualizar la aplicación. Al programarla también se cancela la antigua alarma de las 08:00 y cualquier reintento antiguo de apagado. El receptor descarta alarmas atrasadas y nunca permite que una alarma programada desactive Sleep Mode. Si falla la activación nocturna, se agenda un solo reintento diez minutos después. Una acción manual nueva cancela ese reintento pendiente.

La acción del tile, el atajo lateral y la activación nocturna ejecutan el cambio mediante el servicio en primer plano de Sleep Mode. La app no usa el estado de la pantalla para inferir que el usuario despertó ni hace cambios automáticos por la mañana.

La entrega real de la alarma de las 23:00 y la sincronización BLE con Fit3 deben confirmarse en el dispositivo después de instalar esta versión; la existencia de la alarma programada no demuestra por sí sola que el reloj haya aceptado el cambio.

## Orca y Termux

Al tocar Orca cuando Termux está congelado, Nemotron confirma que Termux se habilitó, concede con Shizuku el permiso Android `RUN_COMMAND` y abre una Activity puente desde el tile para solicitar la conexión SSH en primer plano. Así Android no bloquea el inicio del servicio de Termux desde segundo plano. Si no se confirma el permiso o la habilitación, omite el comando y deja el motivo en Logcat. El siguiente toque congela Termux.

Termux debe permitir comandos externos (`allow-external-apps=true`). Para abrir automáticamente la sesión en primer plano, Termux también necesita permiso para mostrarse sobre otras aplicaciones en Android 10 o posterior. Si falta un requisito, el servicio `OrcaTile` deja el detalle en Logcat.
