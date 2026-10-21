# Tile Orca para Termux

El tile `Orca` habilita Termux y ejecuta la conexión SSH al activarlo; al apagarlo, congela Termux:

- Toque corto para activar: descongela Termux, abre su terminal, escribe el comando SSH en el prompt y pulsa Enter mediante eventos de entrada de Shizuku. No muestra una pantalla puente ni usa `RUN_COMMAND`:

```bash
ssh -J serveo.net adaredu@adardev-orca-20261020-f9c7e2
```

El relay de Serveo debe estar activo en el PC y la clave SSH debe estar configurada antes de pulsar el tile.

El toque corto que activa Orca inicia la conexión. El siguiente toque la apaga y congela Termux; no vuelve a ejecutar el comando.

Después de instalar el APK, añade `Orca` desde la edición de Quick Settings. Al activar Orca, Termux debe permitir comandos externos y el relay/llave SSH deben estar listos para que la sesión conecte.
