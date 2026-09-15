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
DEFAULT_FOLDERS=(web-vanilla web-retro web-ambient web-analog)
PREVIEW_FOLDERS=("${DEFAULT_FOLDERS[@]}" web-expo rive rive-modular)

if [ $# -gt 0 ]; then
  FOLDERS=("$@")
else
  FOLDERS=("${DEFAULT_FOLDERS[@]}")
fi

ADASVIZ_DIR="$REPO_ROOT/adasviz"

for folder in "${PREVIEW_FOLDERS[@]}"; do
  name="${folder#web-}"
  name="${name#rive-}"
  cp -f "$APPS_DIR/$folder/preview.jpg" "$DRAWABLES_DIR/preview_${name}.jpg"
  echo "Copied preview $folder -> $DRAWABLES_DIR/preview_${name}.jpg"
done

# Rive dash-apps ship as a built .riv in their dist/ folder (see dash-apps/rive-modular/README.md)
cp -f "$APPS_DIR/rive-modular/dist/dashboard_modular.riv" "$RAW_DIR/dashboard_modular.riv"
echo "Copied rive-modular -> $RAW_DIR/dashboard_modular.riv"

for name in "${FOLDERS[@]}"; do
  src="$APPS_DIR/$name"
  dst="$ASSETS_DIR/$name"

  # web-vanilla depends on adasviz assets (wasm, js, 3D models)
  if [ "$name" = "web-vanilla" ]; then
    echo "Copying adasviz into $src..."
    cp -rf "$ADASVIZ_DIR"/* "$src/"
    rm -f "$src/README.md"
  fi

  if [ ! -d "$src" ]; then
    echo "Error: source not found at $src" >&2
    exit 1
  fi

  mkdir -p "$dst"

  rsync -av --delete \
    --exclude='.DS_Store' \
    --exclude='.firebase' \
    --exclude='.firebaserc' \
    --exclude='firebase.json' \
    --exclude='.gitignore' \
    --exclude='preview.*' \
    --exclude='serve.sh' \
    --exclude='*.d.ts' \
    --exclude='*backup*' \
    "$src/" "$dst/"

  echo "Synced $name -> $dst"
done

echo "Done."
