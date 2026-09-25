# Handy separado para AutoHotkey

Este componente no usa la interfaz ni el proceso de Handy. Usa un modelo Nemotron 3.5 ASR Streaming local, recibe audio del micrófono, hace streaming y devuelve la transcripción a AutoHotkey por `127.0.0.1:17841`.

El servidor se inicia bajo demanda al usar la transcripción por primera vez. El modelo no se carga al arrancar AutoHotkey: se carga en el primer dictado y se descarga después de cinco minutos sin uso. Al descargarse el modelo también se cierra el servidor, por lo que no mantiene ocupada la memoria cuando no se está dictando.

`Ctrl+Space` inicia/detiene. `Escape` detiene sin pegar. El modelo configurado es `es-ES`; se puede cambiar con `HANDY_LANGUAGE=en-US`.

Archivos principales:

- `transcriber.py`: captura y motor de transcripción.
- `HandySeparate.ahk`: hotkey y pegado en la ventana activa.
- `start-server.ps1`: arranque aislado con sus propias DLL y Python.

El modelo y las DLL se descargan con `install.ps1`.
