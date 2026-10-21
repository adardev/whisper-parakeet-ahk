# Nemotron Keyboard

- Package: `com.nemotron.keyboard`
- APK: `Nemotron-Keyboard.apk`
- Integrador, adaptación smali y guía de compilación: [`build/`](build).
- Empaquetado: `tools/build-nemotron-split.ps1` y `tools/split-nemotron-packages.ps1`.
- Modelo Whisper: `assets/whisper/ggml-tiny.bin`; es el único componente que lo empaqueta.
- Limpieza de memoria: `tools/patch-keyboard-memory.ps1` conecta los eventos del teclado con `KeyboardMemoryController`, compilado desde las fuentes compartidas de Nemotron.

La implementación base del teclado procede del APK propietario Samsung HoneyBoard, que no se distribuye como código fuente aquí. Esta carpeta contiene las modificaciones de Nemotron y los pasos reproducibles para integrarlas con un APK donante local.
