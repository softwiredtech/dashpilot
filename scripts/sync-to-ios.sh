#!/usr/bin/env bash
# Prepares and copies dash-apps into the iOS app bundle.
# Called by the Xcode build phase, or manually before building.
#
# When called from Xcode, BUILT_PRODUCTS_DIR and CONTENTS_FOLDER_PATH are set
# automatically. When run manually, the copy step is skipped.
#
# Usage:
#   ./scripts/sync-to-ios.sh                      # prepare all dash-apps
#   ./scripts/sync-to-ios.sh web-vanilla web-retro # prepare specific folders

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APPS_DIR="$REPO_ROOT/dash-apps"
XCASSETS_DIR="$REPO_ROOT/dashpilot-ios/dashpilot/Assets.xcassets"

# Default folders (override by passing arguments)
DEFAULT_FOLDERS=(web-vanilla web-retro web-ambient web-analog)

if [ $# -gt 0 ]; then
  FOLDERS=("$@")
else
  FOLDERS=("${DEFAULT_FOLDERS[@]}")
fi

ADASVIZ_DIR="$REPO_ROOT/adasviz"

for folder in "${DEFAULT_FOLDERS[@]}"; do
  name="${folder#web-}"
  cp -f "$APPS_DIR/$folder/preview.jpg" "$XCASSETS_DIR/preview_$name.imageset/preview_$name.jpg"
  echo "Copied preview $folder -> $XCASSETS_DIR/preview_$name.imageset/preview_$name.jpg"
done

for name in "${FOLDERS[@]}"; do
  src="$APPS_DIR/$name"

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

  # Copy into the app bundle when running inside Xcode
  if [ -n "${BUILT_PRODUCTS_DIR:-}" ] && [ -n "${CONTENTS_FOLDER_PATH:-}" ]; then
    dst="${BUILT_PRODUCTS_DIR}/${CONTENTS_FOLDER_PATH}/${name}"
    cp -R "$src/" "$dst/"
    rm -f "$dst"/preview.*
    echo "Copied $name -> app bundle"
  else
    echo "Prepared $name (run from Xcode to copy into bundle)"
  fi
done

echo "Done."
