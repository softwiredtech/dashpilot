#!/usr/bin/env bash
# Syncs the web-retro dash-app into the Android assets folder.
# Run from anywhere inside the dashpilot repo.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$REPO_ROOT/dash-apps/web-retro"
DST="$REPO_ROOT/dashpilot-android/app/src/main/assets/web-retro"

if [ ! -d "$SRC" ]; then
  echo "Error: source not found at $SRC" >&2
  exit 1
fi

mkdir -p "$DST"

rsync -av --delete \
  --exclude='.DS_Store' \
  --exclude='.gitignore' \
  --exclude='*backup*' \
  "$SRC/" "$DST/"

echo "Done. Synced web-retro -> $DST"
