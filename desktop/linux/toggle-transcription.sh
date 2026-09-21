#!/usr/bin/env bash
set -euo pipefail
BASE='http://127.0.0.1:17841'
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"

if ! status="$(curl -fsS "$BASE/status" 2>/dev/null)"; then
  nohup "$ROOT/desktop/linux/start-server.sh" >"${XDG_RUNTIME_DIR:-/tmp}/handy-separate.log" 2>&1 &
  for _ in $(seq 1 100); do
    if status="$(curl -fsS "$BASE/status" 2>/dev/null)"; then
      break
    fi
    sleep 0.1
  done
  [[ -n "${status:-}" ]] || { echo 'No se pudo iniciar el servidor de dictado.' >&2; exit 1; }
fi
if [[ "$status" =~ '"recording"[[:space:]]*:[[:space:]]*true' ]]; then
  text="$(curl -fsS "$BASE/stop")"
  if [[ -n "$text" ]]; then
    if [[ -n "${WAYLAND_DISPLAY:-}" ]] && command -v wtype >/dev/null; then
      wtype -- "$text"
    elif command -v xdotool >/dev/null; then
      xdotool type --clearmodifiers --delay 0 -- "$text"
    elif command -v wl-copy >/dev/null; then
      printf '%s' "$text" | wl-copy
      echo 'Texto copiado al portapapeles; pégalo con Ctrl+V.' >&2
    elif command -v xclip >/dev/null; then
      printf '%s' "$text" | xclip -selection clipboard
      echo 'Texto copiado al portapapeles; pégalo con Ctrl+V.' >&2
    else
      printf '%s\n' "$text"
    fi
  fi
else
  curl -fsS "$BASE/start" >/dev/null
fi
