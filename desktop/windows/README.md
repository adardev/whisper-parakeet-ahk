# Handy separado para AutoHotkey

Este componente no usa la interfaz ni el proceso de Handy. Mantiene un único modelo Nemotron 3.5 ASR Streaming en memoria, recibe audio del micrófono, hace streaming y devuelve la transcripción a AutoHotkey por `127.0.0.1:17841`.

El modelo ahora se carga bajo demanda al iniciar el primer dictado. Después de que AutoHotkey lo cierre por inactividad, el endpoint `/shutdown` libera el modelo y detiene el servidor para no mantener memoria ocupada.

El paquete incluye `handy-native.exe`, un helper nativo Rust que captura audio y llama directamente a la API C de `transcribe.dll`; el proceso Python queda como fallback para instalaciones antiguas.

`Ctrl+Space` inicia/detiene. `Escape` detiene sin pegar. El modelo configurado es `es-ES`; se puede cambiar con `HANDY_LANGUAGE=en-US`.

Archivos principales:

- `transcriber.py`: captura y motor de transcripción.
- `HandySeparate.ahk`: hotkey y pegado en la ventana activa.
- `start-server.ps1`: arranque aislado con sus propias DLL y Python.

El modelo y las DLL se descargan con `install.ps1`.
