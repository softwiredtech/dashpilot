#!/usr/bin/env bash
# Syncs the web-ambient dash-app into the Android assets folder.
# Run from anywhere inside the dashpilot repo.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$REPO_ROOT/dash-apps/web-ambient"
DST="$REPO_ROOT/dashpilot-android/app/src/main/assets/web-ambient"

if [ ! -d "$SRC" ]; then
  echo "Error: source not found at $SRC" >&2
  exit 1
fi

mkdir -p "$DST"

rsync -av --delete \
  --exclude='.DS_Store' \
  --exclude='.gitignore' \
  "$SRC/" "$DST/"

echo "Done. Synced web-ambient -> $DST"
