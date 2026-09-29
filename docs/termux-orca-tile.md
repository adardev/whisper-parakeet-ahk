# Tile Orca para Termux

El tile `Orca` abre Termux y ejecuta:

```bash
ssh -J serveo.net adaredu@adardev-orca-20260928-2
```

## Configuración de Termux

En Termux, activa los comandos externos:

```bash
mkdir -p ~/.termux
printf 'allow-external-apps=true\n' >> ~/.termux/termux.properties
```

Después reinicia Termux completamente. En Android, concede a Nemotron el permiso **Run commands in Termux environment**.

El relay de Serveo debe estar activo en el PC y la clave SSH debe estar configurada antes de pulsar el tile.

Después de instalar el APK, añade `Orca` desde la edición de Quick Settings.
