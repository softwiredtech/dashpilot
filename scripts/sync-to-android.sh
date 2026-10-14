#!/usr/bin/env bash
# Syncs dash-apps folders into the Android assets directory.
# Usage:
#   ./scripts/sync-to-android.sh                      # sync all dash-apps
#   ./scripts/sync-to-android.sh web-vanilla web-retro # sync specific folders

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APPS_DIR="$REPO_ROOT/dash-apps"
ASSETS_DIR="$REPO_ROOT/dashpilot-android/app/src/main/assets"
DRAWABLES_DIR="$REPO_ROOT/dashpilot-android/app/src/main/res/drawable"
RAW_DIR="$REPO_ROOT/dashpilot-android/app/src/main/res/raw"

# Default folders to sync (override by passing arguments)
DEFAULT_FOLDERS=(web-vanilla web-retro web-ambient web-analog web-compose)
PREVIEW_FOLDERS=("${DEFAULT_FOLDERS[@]}" rive)

if [ $# -gt 0 ]; then
  FOLDERS=("$@")
else
  FOLDERS=("${DEFAULT_FOLDERS[@]}")
fi

for folder in "${PREVIEW_FOLDERS[@]}"; do
  cp -f "$APPS_DIR/$folder/preview.jpg" "$DRAWABLES_DIR/preview_${folder#web-}.jpg"
  echo "Copied preview $folder -> $DRAWABLES_DIR/preview_${folder#web-}.jpg"
done

mkdir -p "$RAW_DIR"
for riv in "$APPS_DIR"/rive/*.riv; do
  cp -f "$riv" "$RAW_DIR/"
  echo "Copied $(basename "$riv") -> $RAW_DIR"
done

for name in "${FOLDERS[@]}"; do
  src="$APPS_DIR/$name"
  dst="$ASSETS_DIR/$name"

  if [ ! -d "$src" ]; then
    echo "Error: source not found at $src" >&2
    exit 1
  fi

  mkdir -p "$dst"

  rsync -av --delete \
    --exclude='.DS_Store' \
    --exclude='preview.*' \
    --exclude='README.md' \
    --exclude='*backup*' \
    "$src/" "$dst/"

  echo "Synced $name -> $dst"
done

echo "Done."
