# Android Auto: barra inferior

Estado al 6 de noviembre de 2026: probado con el teléfono y Desktop Head Unit (DHU) a 800 × 480, incluido un ciclo de apagado y encendido del tile de Android Auto. La barra dejó de verse y la proyección ocupó prácticamente toda la altura. Falta verificar la pantalla física del Suzuki.

El parche corresponde exclusivamente a Android Auto `18.1.164344` (`181164344`). Cambia a 1 dp cuatro dimensiones en `resources.arsc`: `car_rail_height`, `cielo_rail_height`, `floating_rail_space_height` y `rail_height`. La tabla difiere en cuatro bytes; los otros 4,947 archivos no correspondientes a firmas permanecieron iguales. El valor 0 dp hizo fallar la proyección porque la pantalla virtual exige una altura positiva; no se debe usar.

`tools/android-auto/patch-resources.ps1` reproduce la modificación sobre una copia local del APK original. Produce un APK sin firmar; la copia de prueba se firmó localmente con una clave de depuración. Ni APK, claves, capturas ni datos personales se incluyen aquí. Las capturas de DHU pueden contener la ubicación del usuario.

## Aplicación temporal

El tile de Nemotron activa `AndroidAutoRailPatch` al encenderse y limpia el montaje al apagarse. El APK candidato se empaqueta como recurso de Nemotron desde el artefacto versionado y se vuelve a copiar a `/data/local/tmp` antes de cada activación; así, que Android limpie ese directorio ya no deja el parche permanentemente ausente. Nemotron comprueba el SHA-256 del recurso antes de copiarlo, y el controlador `android_auto_rail_control.sh` verifica tanto el original como el candidato antes de montar el APK solo dentro del espacio de montaje de zygote64. El sistema y PackageManager siguen viendo el APK original. Al apagar el tile, se desmontan las instancias heredadas asociadas a ese candidato. Si cambia la versión original, el parche se omite y se usa Android Auto original.

El candidato probado está en `artifacts/android-auto-rail/android-auto-18.1.164344-rail-1dp.apk`, SHA-256 `6e14eb65f4df0293352ed5da478ada2445341b776956746693e75da205d17cd8`. El original tiene SHA-256 `f3e90ea898469991785dbf91500e1d50db4c637e0e67483065ee0813e3b59c73`. Una actualización de Android Auto necesita un parche revisado para la nueva versión.

El montaje no es persistente: un reinicio o reinicio de zygote lo elimina. El root de DFRoot también es temporal y Start at Boot está desactivado. Tras reiniciar, iniciar root y apagar y encender el tile para volver a aplicarlo. No se agregó ningún módulo ni script de arranque para este parche.

## Recuperación del candidato y opciones del tile — 14 de noviembre de 2026

Android limpiaba `/data/local/tmp`, donde el controlador esperaba encontrar el APK parcheado. Nemotron ahora empaqueta el candidato del artefacto versionado, valida su SHA-256, lo extrae a su almacenamiento privado y lo vuelve a copiar al directorio temporal antes de cada activación. El controlador vuelve a validar la huella antes del montaje. La app ocupa unos 35 MB adicionales por llevar este recurso; solo se copia al activar Android Auto.

El cambio de ubicación, Bluetooth, datos móviles y los dos gestos se despacha en un solo proceso root, en paralelo. Cada comando devuelve su propio código; si alguno falla, Nemotron vuelve a intentar las acciones separadas. Las opciones se ejecutan concurrentemente con el montaje o desmontaje del parche para reducir el tiempo total. Al encender, la configuración del puente de medios espera a que el montaje termine, porque el parche reinicia Android Auto. El build Core probado usa `versionCode 9`.

En el A54, Logcat midió el montaje del parche en 1,651 ms al encender; al apagar, el desmontaje/restauración tardó 1,150 ms. En paralelo, los cinco ajustes tardaron 433 ms y el puente de medios 427 ms. Estos son tiempos por tarea y se solapan; no deben sumarse para estimar el tiempo total. Tras el ciclo real desde Quick Settings, el tile quedó apagado y se verificó que Android Auto estaba oculto/detenido y que ubicación, Bluetooth, datos móviles y ambos gestos estaban apagados.

Una instalación normal del APK firmado localmente falló con `INSTALL_FAILED_UPDATE_INCOMPATIBLE` porque Android Auto es una aplicación de sistema firmada por Google. Se restauró el paquete original y su acceso a notificaciones; no repetir la desinstalación para evitar ese control. No modificar `/product/overlay/config/config.xml`: el intento anterior causó fallos de arranque.

Los respaldos privados de los módulos KernelSU y de los datos de Android Auto se encuentran en `Documents/NemotronPrivateBackups/`; no se suben al repositorio. Los APK originales están en `Documents/AndroidAutoOfflinePatch/original-splits/`.


## Waze horizontal framing

Waze's landscape marker position and the reversible Keep map north up adjustment are documented in [waze-android-auto-centering-2026-11-06.md](waze-android-auto-centering-2026-11-06.md). The setting was changed on-device; its physical-head-unit result remains unverified.
