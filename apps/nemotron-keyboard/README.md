# Nemotron Keyboard

- Package: `com.nemotron.keyboard`
- APK: `Nemotron-Keyboard.apk`
- Integrador, adaptación smali y guía de compilación: [`build/`](build).
- Empaquetado: `tools/build-nemotron-split.ps1` y `tools/split-nemotron-packages.ps1`.
- Modelo Whisper: `assets/whisper/ggml-tiny.bin`; es el único componente que lo empaqueta.
- Limpieza de memoria: `tools/patch-keyboard-memory.ps1` conecta los eventos del teclado con `KeyboardMemoryController`, compilado desde las fuentes compartidas de Nemotron.
- Dictado: el doble espacio solicita `RECORD_AUDIO` solo al iniciar la grabación. Si falta, Nemotron Keyboard abre su pantalla de permiso; no hay un permiso Android separado de micrófono "en segundo plano" para este flujo.
- Port al Samsung Keyboard original: [`build/stock-host/`](build/stock-host/) y [`build/prepare-stock-keyboard-root-candidate.ps1`](build/prepare-stock-keyboard-root-candidate.ps1) preparan un APK de HoneyBoard de sistema con doble espacio para dictado en español, salida en minúsculas y autocapitalización subordinada a la preferencia de HoneyBoard. El montaje diseñado es temporal en el namespace de Zygote; consulta [la bitácora del A54](../../docs/samsung-keyboard-nemotron-port-2026-11-16.md) para el estado de activación.

La implementación base del teclado procede del APK propietario Samsung HoneyBoard, que no se distribuye como código fuente aquí. Esta carpeta contiene las modificaciones de Nemotron y los pasos reproducibles para integrarlas con un APK donante local.
