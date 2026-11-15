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
DEFAULT_FOLDERS=(web-vanilla web-vanilla-v2 web-retro web-ambient web-analog web-compose)
RIVE_FOLDERS=(rive rive-felina rive-halo rive-monte-carlo rive-parabellum)
PREVIEW_FOLDERS=("${DEFAULT_FOLDERS[@]}" "${RIVE_FOLDERS[@]}")

# Android resource names allow only lowercase letters, digits and underscores
resource_name() {
  echo "$1" | tr '[:upper:]-' '[:lower:]_'
}

if [ $# -gt 0 ]; then
  FOLDERS=("$@")
else
  FOLDERS=("${DEFAULT_FOLDERS[@]}")
fi

for folder in "${PREVIEW_FOLDERS[@]}"; do
  name="${folder#web-}"
  dst="$DRAWABLES_DIR/preview_$(resource_name "${name#rive-}").jpg"
  cp -f "$APPS_DIR/$folder/preview.jpg" "$dst"
  echo "Copied preview $folder -> $dst"
done

mkdir -p "$RAW_DIR"
for folder in "${RIVE_FOLDERS[@]}"; do
  for riv in "$APPS_DIR/$folder"/*.riv; do
    dst="$RAW_DIR/$(resource_name "$(basename "$riv")")"
    cp -f "$riv" "$dst"
    echo "Copied $(basename "$riv") -> $dst"
  done
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
