# Nemotron Core

- Paquete de la APK separada: `com.nemotron.core`.
- APK publicada: `Nemotron-Core.apk`.
- Código Android mantenido: `build.gradle.kts`, `src/`, `libs/` y `proguard-rules.pro` en esta carpeta.
- Módulo Gradle: `:apps:nemotron`.

Compilación de Core desde la raíz: `./gradlew :apps:nemotron:assembleDebug -PnemotronComponent=core`. El ensamblado de las tres APK continúa con `tools/build-nemotron-split.ps1`. Core no empaqueta modelos ni recursos de HoneyBoard; el modelo de Whisper pertenece a `apps/nemotron-keyboard/assets/`.

Core también es el destino por defecto si se omite la propiedad. `-PnemotronComponent=integrated` queda reservado a la entrada intermedia del ensamblado: usa el identificador de UI y **no debe instalarse en el teléfono**. Para actualizar los tiles se compila e instala únicamente `com.nemotron.core`.

El módulo conserva las funciones de Core y las piezas de integración que comparte con las otras APK. El teclado Samsung y One UI Home son componentes propietarios: su implementación original no está disponible como código fuente en este repositorio.
