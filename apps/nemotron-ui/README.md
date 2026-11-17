# Nemotron UI

- Package: `com.nemotron.voiceime2`
- APK: `Nemotron-UI.apk`
- Integración, adaptadores smali, compatibilidad Java, probe de diagnóstico y dependencia auxiliar: [`build/`](build).
- Empaquetado: `tools/build-nemotron-split.ps1` y `tools/split-nemotron-packages.ps1`.

Esta carpeta contiene las modificaciones reproducibles sobre Samsung One UI Home. El código fuente original de One UI Home no es parte del repositorio; para reconstruir la APK se necesita el APK donante compatible indicado por el script.

El port de las funciones de Nemotron Home al APK Samsung original, la compilación de sesión root y la verificación en el Galaxy A54 están documentados en [`docs/oneui-home-nemotron-port-2026-11-16.md`](../../docs/oneui-home-nemotron-port-2026-11-16.md).

El ajuste de opacidad de las tarjetas **Searched apps** y **Quick panel** de Finder está en [`build/patch-finder-opaque-cards.ps1`](build/patch-finder-opaque-cards.ps1) y se aplica al generar el candidato Samsung.
