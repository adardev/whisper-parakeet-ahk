# Tile SSH para Termux

El tile `SSH` abre Termux y ejecuta:

```bash
ssh -J serveo.net adaredu@adardev-orca-20260928
```

## Configuración de Termux

En Termux, activar comandos externos:

```bash
mkdir -p ~/.termux
printf 'allow-external-apps=true\n' >> ~/.termux/termux.properties
```

En Android, conceder a Nemotron el permiso adicional **Run commands in Termux environment**.

El relay de Serveo debe estar activo en el PC antes de pulsar el tile. La clave SSH debe estar configurada en Windows; el tile no guarda contraseñas ni claves privadas.

Después de instalar el APK, añadir `SSH` desde la edición de Quick Settings.
