# Handy separado para Arch Linux

Usa el mismo modelo multilingual streaming que Windows, ubicado en `desktop/models/`, y el mismo servidor Python. Funciona con X11 y Wayland.

## Instalación

Desde la raíz del repositorio:

```bash
sudo pacman -S --needed python python-pip curl git-lfs base-devel
./desktop/linux/install.sh
```

Para X11, instala `xdotool`; para Wayland, instala `wtype`:

```bash
# X11
sudo pacman -S --needed xdotool
# Wayland
sudo pacman -S --needed wtype
```

## Uso

Terminal 1:

```bash
./desktop/linux/start-server.sh
```

En otra terminal o como atajo global:

```bash
./desktop/linux/toggle-transcription.sh
```

La primera ejecución del atajo inicia automáticamente el servidor y la captura; la siguiente la detiene y escribe el resultado en la aplicación activa. El servidor descarga el modelo y se cierra después de cinco minutos sin uso.

Puedes asignar `toggle-transcription.sh` a `Ctrl+Space` desde los atajos de tu entorno de escritorio. Si prefieres mantener el servidor siempre disponible al iniciar sesión:

```bash
mkdir -p ~/.config/systemd/user
cp desktop/linux/handy-separate.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now handy-separate.service
```
