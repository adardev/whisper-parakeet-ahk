# Recuperación de UI y destino de Core

Al entregar el cambio del plugin Fit3 se instaló por error la entrada integrada
del módulo `apps/nemotron`. Ese APK usa `com.nemotron.voiceime2`, el mismo
identificador de Nemotron UI, y reemplazó el launcher con una app sin actividad
HOME. El cambio de Fit3 debía instalarse en `com.nemotron.core`.

Se recuperó UI reinstalando `apps/nemotron-ui/Nemotron-UI.apk` con `adb install -r`,
sin desinstalar ni borrar datos. Antes se respaldaron las bases y preferencias
privadas en el directorio local `AppData/Local/NemotronRecovery`, fuera del repo.
Las bases OneUI.db y HomeOnly.db seguían presentes. Se restauraron el permiso de
estadísticas de uso, el enlace de widgets y la selección de HOME.

ADB confirmó que el rol HOME, la resolución de Inicio y la actividad visible
son de `com.nemotron.voiceime2`. Se inspeccionó una captura del escritorio
recuperado con sus iconos, carpetas y widgets existentes. El teclado seleccionado
continuó siendo `com.nemotron.keyboard`.

Se recompiló e instaló Core con `-PnemotronComponent=core`, comprobando antes
que el APK declara `com.nemotron.core` y no contiene el modelo Whisper. El APK
de Core ocupa 17,069,688 bytes. El cambio Fit3 quedó en Core; el APK de UI
restaurado corresponde al artefacto existente y su código no fue modificado.

Ahora el módulo compila Core por defecto. La entrada integrada requiere
`-PnemotronComponent=integrated`, que el script de ensamblado completo pasa
explícitamente. No debe instalarse esa entrada directamente. Los valores
desconocidos de la propiedad fallan durante la configuración de Gradle.
