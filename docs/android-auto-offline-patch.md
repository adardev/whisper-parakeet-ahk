# Android Auto: barra inferior

Estado al 6 de noviembre de 2026: probado con el teléfono y Desktop Head Unit (DHU) a 800 × 480, incluido un ciclo de apagado y encendido del tile de Android Auto. La barra dejó de verse y la proyección ocupó prácticamente toda la altura. Falta verificar la pantalla física del Suzuki.

El parche corresponde exclusivamente a Android Auto `18.1.164344` (`181164344`). Cambia a 1 dp cuatro dimensiones en `resources.arsc`: `car_rail_height`, `cielo_rail_height`, `floating_rail_space_height` y `rail_height`. La tabla difiere en cuatro bytes; los otros 4,947 archivos no correspondientes a firmas permanecieron iguales. El valor 0 dp hizo fallar la proyección porque la pantalla virtual exige una altura positiva; no se debe usar.

`tools/android-auto/patch-resources.ps1` reproduce la modificación sobre una copia local del APK original. Produce un APK sin firmar; la copia de prueba se firmó localmente con una clave de depuración. Ni APK, claves, capturas ni datos personales se incluyen aquí. Las capturas de DHU pueden contener la ubicación del usuario.

## Aplicación temporal

El tile de Nemotron activa `AndroidAutoRailPatch` al encenderse y limpia el montaje al apagarse. El controlador `android_auto_rail_control.sh` verifica las huellas SHA-256 del APK original y del candidato antes de montar el candidato sobre el APK base únicamente dentro del espacio de montaje de zygote64. El sistema y PackageManager siguen viendo el APK original. Al apagar el tile, se desmontan las instancias heredadas asociadas a ese candidato. Si falta el candidato o cambia la versión original, el parche se omite y se usa Android Auto original.

El candidato probado está en `/data/local/tmp/nemotron-aa-18_1-rail1dp.apk`, SHA-256 `6e14eb65f4df0293352ed5da478ada2445341b776956746693e75da205d17cd8`. El original tiene SHA-256 `f3e90ea898469991785dbf91500e1d50db4c637e0e67483065ee0813e3b59c73`. Android puede limpiar archivos temporales; si desaparece el candidato hay que prepararlo de nuevo. Una actualización de Android Auto necesita un parche revisado para la nueva versión.

El montaje no es persistente: un reinicio o reinicio de zygote lo elimina. El root de DFRoot también es temporal y Start at Boot está desactivado. Tras reiniciar, iniciar root y apagar y encender el tile para volver a aplicarlo. No se agregó ningún módulo ni script de arranque para este parche.

Una instalación normal del APK firmado localmente falló con `INSTALL_FAILED_UPDATE_INCOMPATIBLE` porque Android Auto es una aplicación de sistema firmada por Google. Se restauró el paquete original y su acceso a notificaciones; no repetir la desinstalación para evitar ese control. No modificar `/product/overlay/config/config.xml`: el intento anterior causó fallos de arranque.

Los respaldos privados de los módulos KernelSU y de los datos de Android Auto se encuentran en `Documents/NemotronPrivateBackups/`; no se suben al repositorio. Los APK originales están en `Documents/AndroidAutoOfflinePatch/original-splits/`.


## Waze horizontal framing

Waze's landscape marker position and the reversible Keep map north up adjustment are documented in [waze-android-auto-centering-2026-11-06.md](waze-android-auto-centering-2026-11-06.md). The setting was changed on-device; its physical-head-unit result remains unverified.
