# Sleep Mode: apagado manual y tile Orca

## Apagado manual durante la noche

Al apagar Sleep Mode desde su tile, el atajo lateral o la actividad de Nemotron, se guarda una pausa manual para el resto del día. Los eventos de pantalla apagada y los reintentos que ya estuvieran en cola respetan esa pausa y no vuelven a activar el modo avión ni la rutina nocturna. La pausa se limpia al iniciar la rutina programada de las 23:00; la alarma de las 08:00 puede terminar la rutina matutina sin borrar la elección manual.

Si se vuelve a activar Sleep Mode manualmente, se elimina la pausa. Si la operación manual falla, se restaura el valor anterior para no mostrar una elección que no se aplicó.

## Orca y Termux

Al tocar Orca cuando Termux está congelado, Nemotron confirma que Termux se habilitó, concede con Shizuku el permiso Android `RUN_COMMAND` y abre una Activity puente desde el tile para solicitar la conexión SSH en primer plano. Así Android no bloquea el inicio del servicio de Termux desde segundo plano. Si no se confirma el permiso o la habilitación, omite el comando y deja el motivo en Logcat. El siguiente toque congela Termux.

Termux debe permitir comandos externos (`allow-external-apps=true`). Para abrir automáticamente la sesión en primer plano, Termux también necesita permiso para mostrarse sobre otras aplicaciones en Android 10 o posterior. Si falta un requisito, el servicio `OrcaTile` deja el detalle en Logcat.

El APK integrado se genera en `E:\NemotronTemp\Nemotron-Sleep-Orca-Integrated.apk`; conserva One UI Home y HoneyBoard integrados y usa la misma firma debug que la instalación anterior. La compilación local se completó; la instalación y la prueba en el Galaxy requieren ADB conectado.
