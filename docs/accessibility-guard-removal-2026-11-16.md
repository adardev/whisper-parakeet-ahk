# Retiro de Nemotron Guard de accesibilidad (16 nov 2026)

## Estado previo confirmado en el Galaxy A54

ADB mostró un solo servicio de accesibilidad habilitado: `Nemotron Guard`,
componente `com.nemotron.core/com.nemotron.voiceime.guard.AntiScrollAccessibilityService`.
Su preferencia interna ya estaba en `false`, así que el callback regresaba sin
ejecutar los bloqueos. Aun así el servicio permanecía registrado con Android y
estaba enlazado. Su código estaba diseñado para reaccionar a eventos de
Instagram/WhatsApp (Reels/feed/Status), cerrar esas apps y bloquear pantalla;
no tenía sondeo periódico.

## Cambios

- Se quitó el servicio de accesibilidad del manifiesto de Core y del APK.
- Se eliminaron su implementación, configuración XML, textos y el interruptor
  de Ajustes.
- Se quitaron las rutas de arranque y el helper root que podían volver a
  registrarlo. El tile Atmos conserva sus acciones de ocultar/mostrar Instagram
  y proteger las apps bancarias, sin activar accesibilidad.
- Se conservó el bloqueo de pantalla por No Molestar, el listener de
  entrenamiento de Samsung Health/Fit3 y el inicio de DNS privado; son
  componentes distintos y mantienen sus rutas actuales.
- Android rechazó `pm disable-user` para el componente con `SecurityException`.
  Se retiró el nombre de la lista segura de accesibilidad (`null`) y se apagó
  `accessibility_enabled` (`0`); el nuevo APK elimina definitivamente el
  componente del paquete.

## Instalación y verificación

Se compiló `:apps:nemotron:assembleDebug -PnemotronComponent=core` con éxito e
instaló Core v1.6 sobre la instalación existente con `adb install -r`, sin
borrar datos ni reiniciar el teléfono. Después, ADB reportó:

- `enabled_accessibility_services`: `null`.
- `accessibility_enabled`: `0`.
- `dumpsys accessibility`: cero servicios instalados, habilitados o enlazados.
- El manifiesto instalado ya no anuncia `AntiScrollAccessibilityService`.

El proceso `com.nemotron.core` siguió vivo por
`HealthWorkoutPowerSaverListener`, que está enlazado a `system` mientras la
función de entrenamiento lo requiere. Por ello la verificación confirma que
Guard ya no mantiene una conexión de accesibilidad, pero no afirma que todo el
proceso Core desaparezca ni que se libere la memoria completa del paquete.
Forzar el cierre de Core cortaría otros servicios y no se hizo.

La función anti-Reels/Status dejó de estar disponible a petición del usuario.
El ajuste de ubicación de la navegación HOME/Quickstep se documenta por
separado en `oneui-home-integration.md`; retirar esta accesibilidad no cambia
los gestos del launcher.
