# Tile Orca para Termux

El tile `Orca` habilita Termux y abre su terminal al activarlo; no escribe ni ejecuta comandos automáticamente. Al apagarlo, congela Termux:

- Toque corto para activar: descongela Termux, colapsa Quick Settings y abre la actividad de Termux en una sola llamada root. El prompt queda listo para que el usuario escriba manualmente:

```bash
ssh -J serveo.net adaredu@adardev-orca-20261020-f9c7e2
```

El relay de Serveo debe estar activo en el PC y la clave SSH debe estar configurada antes de pulsar el tile.

El toque corto que activa Orca abre Termux. El siguiente toque lo oculta y fuerza su cierre; no vuelve a ejecutar ni escribe el comando.

Después de instalar el APK, añade `Orca` desde la edición de Quick Settings. Para conectarte, escribe el comando de arriba en la terminal y pulsa Enter.
