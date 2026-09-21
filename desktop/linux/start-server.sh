#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
export HANDY_LANGUAGE="${HANDY_LANGUAGE:-es-ES}"
exec "$ROOT/.venv/bin/python" "$ROOT/desktop/windows/transcriber.py"
