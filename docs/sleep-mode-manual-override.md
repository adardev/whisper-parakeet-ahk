# Sleep Mode: apagado manual y tile Orca

## Funcionamiento vigente (20 de octubre de 2026)

Sleep Mode se puede encender manualmente a cualquier hora. Cada día a las 23:00 se activa automáticamente; si ya está activo, no repite la rutina. Permanece activo hasta que el usuario lo apaga desde el tile, el atajo lateral o la actividad de Nemotron.

Se eliminó `NightAirplaneScreenReceiver`, su registro en `NemotronApp` y los reintentos al recuperar Shizuku. Encender o apagar la pantalla y cambiar manualmente el modo avión no ejecutan rutinas de sueño ni detectan que el usuario despertó. El modo avión sigue formando parte de la activación explícita del modo sueño.

La única alarma es la activación de las 23:00 en la zona horaria local. Al programarla se cancela el PendingIntent anterior con el mismo identificador, reemplazando cualquier alarma de las 08:00. El receptor también ignora las solicitudes antiguas de apagado que ya estuvieran en tránsito. Reiniciar el dispositivo, actualizar la app o cambiar la hora reprograma la siguiente activación nocturna; nunca ejecuta un apagado matutino.

El apagado manual restaura la conectividad y los ajustes diurnos de Fit3. Si una operación falla, el tile refleja que no terminó y permite reintentar. No hay observación de pantalla, detección de despertar, apagado automático ni reactivación al bloquear.

Comprobación en el A54 el 20 de octubre: APK Core actualizado conservando sus datos; `dumpsys alarm` muestra la alarma exacta para las 23:00; el receptor eliminado no aparece registrado; al apagar y encender la pantalla, `airplane_mode_on` permaneció en `0`. La entrega real de la alarma nocturna queda pendiente de las 23:00.

## Orca y Termux

Al tocar Orca cuando Termux está congelado, Nemotron confirma que Termux se habilitó, concede con Shizuku el permiso Android `RUN_COMMAND` y abre una Activity puente desde el tile para solicitar la conexión SSH en primer plano. Así Android no bloquea el inicio del servicio de Termux desde segundo plano. Si no se confirma el permiso o la habilitación, omite el comando y deja el motivo en Logcat. El siguiente toque congela Termux.

Termux debe permitir comandos externos (`allow-external-apps=true`). Para abrir automáticamente la sesión en primer plano, Termux también necesita permiso para mostrarse sobre otras aplicaciones en Android 10 o posterior. Si falta un requisito, el servicio `OrcaTile` deja el detalle en Logcat.

El APK integrado se genera en `E:\NemotronTemp\Nemotron-Sleep-Orca-Integrated.apk`; conserva One UI Home y HoneyBoard integrados y usa la misma firma debug que la instalación anterior. La compilación local se completó; la instalación y la prueba en el Galaxy requieren ADB conectado.
