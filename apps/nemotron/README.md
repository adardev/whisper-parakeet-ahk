# Nemotron Core

- Paquete de la APK separada: `com.nemotron.core`.
- APK publicada: `Nemotron-Core.apk`.
- Código Android mantenido: `build.gradle.kts`, `src/`, `libs/` y `proguard-rules.pro` en esta carpeta.
- Módulo Gradle: `:apps:nemotron`.

Compilación desde la raíz del repositorio: `./gradlew :apps:nemotron:assembleDebug`. El ensamblado de las tres APK continúa con `tools/build-nemotron-split.ps1`.

El módulo conserva las funciones de Core y las piezas de integración que comparte con las otras APK. El teclado Samsung y One UI Home son componentes propietarios: su implementación original no está disponible como código fuente en este repositorio.
