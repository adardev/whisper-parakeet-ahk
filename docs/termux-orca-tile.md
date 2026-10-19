# Tile Orca para Termux

El tile `Orca` es un interruptor de Termux:

- Toque corto: si Termux está congelado, lo habilita; si está habilitado, lo congela. No abre Termux.
- Con Termux ya habilitado, mantener pulsado inicia la conexión SSH en una Activity puente en primer plano:

```bash
ssh -J serveo.net adaredu@adardev-orca-20260928-2
```

## Configuración de Termux

En Termux, activa los comandos externos:

```bash
mkdir -p ~/.termux
printf 'allow-external-apps=true\n' >> ~/.termux/termux.properties
```

Después reinicia Termux completamente. Orca solicita a Android ese permiso mediante Shizuku al descongelar Termux, sin abrir Termux; no hace falta concederlo manualmente desde Ajustes.

El relay de Serveo debe estar activo en el PC y la clave SSH debe estar configurada antes de pulsar el tile.

El toque corto nunca abre Termux. Si está habilitado, lo congela; si está congelado, lo habilita. El gesto largo inicia SSH. Así, conectar y alternar el estado son acciones independientes.

Después de instalar el APK, añade `Orca` desde la edición de Quick Settings. La build integrada de 19 de octubre de 2026 quedó instalada en el A54. Se pulsó el tile por ADB: Termux pasó a paquete deshabilitado y no se abrió su interfaz.
