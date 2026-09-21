#!/usr/bin/env bash
set -euo pipefail
BASE='http://127.0.0.1:17841'
status="$(curl -fsS "$BASE/status")"
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
