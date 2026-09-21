#!/usr/bin/env bash
set -euo pipefail
BASE='http://127.0.0.1:17841'
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
SOUND_DIR="$ROOT/desktop/linux/sounds"
LOG_FILE="$RUNTIME_DIR/handy-separate-toggle.log"

# KGlobalAccel launches desktop actions without a terminal.  Preserve errors
# from the actual typing/audio commands so a failed delivery is diagnosable.
exec >>"$LOG_FILE" 2>&1
printf '%s toggle invoked\n' "$(date -Is)"

sound() {
  command -v paplay >/dev/null || return 0
  # Use the normal 100% sample volume.  The system's active sink (for example
  # the HDMI device currently used by YouTube) receives the same feedback.
  nohup paplay --volume=65536 "$1" >/dev/null 2>&1 &
}

if ! status="$(curl -fsS "$BASE/status" 2>/dev/null)"; then
  nohup "$ROOT/desktop/linux/start-server.sh" >"${XDG_RUNTIME_DIR:-/tmp}/handy-separate.log" 2>&1 &
  for _ in $(seq 1 100); do
    if status="$(curl -fsS "$BASE/status" 2>/dev/null)"; then
      break
    fi
    sleep 0.1
  done
  [[ -n "${status:-}" ]] || {
    echo 'No se pudo iniciar el servidor de dictado.' >&2
    exit 1
  }
fi
recording_pattern='"recording"[[:space:]]*:[[:space:]]*true'
if [[ "$status" =~ $recording_pattern ]]; then
  text="$(curl -fsS "$BASE/stop")"
  sound "$SOUND_DIR/marimba_stop.wav"
  if [[ -n "$text" ]]; then
    if command -v wl-copy >/dev/null && command -v ydotool >/dev/null; then
      # Paste the complete result at once.  ydotool is the verified virtual
      # keyboard KWin receives for Super+S, so this also works without
      # plasma-desktop.
      printf '%s' "$text" | wl-copy
      sleep 0.12
      YDOTOOL_SOCKET="${YDOTOOL_SOCKET:-$RUNTIME_DIR/ydotool.socket}" \
        ydotool key --key-delay 20 29:1 47:1 47:0 29:0
    elif command -v ydotool >/dev/null; then
      YDOTOOL_SOCKET="${YDOTOOL_SOCKET:-$RUNTIME_DIR/ydotool.socket}" \
        ydotool type --key-delay=0 --escape=0 "$text"
    elif [[ -n "${WAYLAND_DISPLAY:-}" ]] && command -v wtype >/dev/null; then
      wtype -- "$text"
    elif [[ -n "${WAYLAND_DISPLAY:-}" ]] && command -v wl-copy >/dev/null && command -v ydotool >/dev/null; then
      printf '%s' "$text" | wl-copy
      # Give the Wayland clipboard owner time to publish the selection before
      # emitting Ctrl+V. Windows follows the same clipboard-then-paste flow.
      sleep 0.15
      YDOTOOL_SOCKET="${YDOTOOL_SOCKET:-$RUNTIME_DIR/ydotool.socket}" \
        ydotool key --key-delay 35 29:1 47:1 47:0 29:0
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
  sound "$SOUND_DIR/marimba_start.wav"
fi
