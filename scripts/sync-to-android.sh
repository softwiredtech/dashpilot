#!/usr/bin/env bash
# Syncs dash-apps folders into the Android assets directory.
# Usage:
#   ./scripts/sync-to-android.sh                      # sync all dash-apps
#   ./scripts/sync-to-android.sh web-vanilla web-retro # sync specific folders

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APPS_DIR="$REPO_ROOT/dash-apps"
ASSETS_DIR="$REPO_ROOT/dashpilot-android/app/src/main/assets"

# Folders to sync: arguments, or all directories in dash-apps
if [ $# -gt 0 ]; then
  FOLDERS=("$@")
else
  FOLDERS=()
  for d in "$APPS_DIR"/*/; do
    FOLDERS+=("$(basename "$d")")
  done
fi

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
    --exclude='.firebase' \
    --exclude='.firebaserc' \
    --exclude='firebase.json' \
    --exclude='.gitignore' \
    --exclude='serve.sh' \
    --exclude='*.d.ts' \
    --exclude='*backup*' \
    "$src/" "$dst/"

  echo "Synced $name -> $dst"
done

echo "Done."
