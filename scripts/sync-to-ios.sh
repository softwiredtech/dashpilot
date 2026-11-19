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
DEFAULT_FOLDERS=(web-vanilla web-vanilla-v2 web-retro web-ambient web-analog web-compose)
RIVE_FOLDERS=(rive rive-felina rive-halo rive-monte-carlo rive-parabellum)
PREVIEW_FOLDERS=("${DEFAULT_FOLDERS[@]}" "${RIVE_FOLDERS[@]}")

if [ $# -gt 0 ]; then
  FOLDERS=("$@")
else
  FOLDERS=("${DEFAULT_FOLDERS[@]}")
fi

for folder in "${PREVIEW_FOLDERS[@]}"; do
  name="${folder#web-}"
  name="${name#rive-}"
  cp -f "$APPS_DIR/$folder/preview.jpg" "$XCASSETS_DIR/preview_$name.imageset/preview_$name.jpg"
  echo "Copied preview $folder -> $XCASSETS_DIR/preview_$name.imageset/preview_$name.jpg"
done

for name in "${FOLDERS[@]}"; do
  src="$APPS_DIR/$name"

  if [ ! -d "$src" ]; then
    echo "Error: source not found at $src" >&2
    exit 1
  fi

  # Copy into the app bundle when running inside Xcode
  if [ -n "${BUILT_PRODUCTS_DIR:-}" ] && [ -n "${CONTENTS_FOLDER_PATH:-}" ]; then
    dst="${BUILT_PRODUCTS_DIR}/${CONTENTS_FOLDER_PATH}/${name}"
    cp -R "$src/" "$dst/"
    rm -f "$dst"/preview.* "$dst"/README.md
    echo "Copied $name -> app bundle"
  else
    echo "Prepared $name (run from Xcode to copy into bundle)"
  fi
done

if [ -n "${BUILT_PRODUCTS_DIR:-}" ] && [ -n "${CONTENTS_FOLDER_PATH:-}" ]; then
  # Same file names as the Android raw resources, so both apps share one url
  for folder in "${RIVE_FOLDERS[@]}"; do
    for riv in "$APPS_DIR/$folder"/*.riv; do
      cp -f "$riv" "${BUILT_PRODUCTS_DIR}/${CONTENTS_FOLDER_PATH}/$(basename "$riv" | tr '[:upper:]-' '[:lower:]_')"
    done
    rm -rf "${BUILT_PRODUCTS_DIR}/${CONTENTS_FOLDER_PATH}/${folder}"
    if [ -f "$APPS_DIR/$folder/manifest.json" ]; then
      mkdir -p "${BUILT_PRODUCTS_DIR}/${CONTENTS_FOLDER_PATH}/${folder}"
      cp -f "$APPS_DIR/$folder/manifest.json" "${BUILT_PRODUCTS_DIR}/${CONTENTS_FOLDER_PATH}/${folder}/manifest.json"
    fi
  done
  echo "Copied rive dashboards -> app bundle"
fi

echo "Done."
