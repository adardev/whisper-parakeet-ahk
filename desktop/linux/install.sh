#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
VENV="$ROOT/.venv"

command -v python >/dev/null || { echo 'Falta python.'; exit 1; }
command -v git-lfs >/dev/null && git -C "$ROOT" lfs pull --include='desktop/models/*.gguf' || true

python -m venv "$VENV"
"$VENV/bin/python" -m pip install --upgrade pip
"$VENV/bin/python" -m pip install --only-binary=:all: numpy sounddevice 'transcribe-cpp==0.2.3'

echo
echo 'Motor instalado.'
echo 'Dependencias opcionales para pegar texto:'
echo '  X11:    sudo pacman -S xdotool xclip'
echo '  Wayland: sudo pacman -S wtype wl-clipboard'
echo
echo "Inicia con: $ROOT/desktop/linux/start-server.sh"
