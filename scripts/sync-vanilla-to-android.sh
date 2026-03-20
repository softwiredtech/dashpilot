#!/usr/bin/env bash
# Syncs the web-vanilla dash-app into the Android assets folder.
# Run from anywhere inside the dashpilot repo.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$REPO_ROOT/dash-apps/web-vanilla"
DST="$REPO_ROOT/dashpilot-android/app/src/main/assets/web-vanilla"

if [ ! -d "$SRC" ]; then
  echo "Error: source not found at $SRC" >&2
  exit 1
fi

mkdir -p "$DST/fonts"

# Sync app files (exclude firebase/dev artifacts)
rsync -av --delete \
  --exclude='.DS_Store' \
  --exclude='.firebase' \
  --exclude='.firebaserc' \
  --exclude='firebase.json' \
  --exclude='.gitignore' \
  --exclude='serve.sh' \
  --exclude='*.d.ts' \
  --exclude='fonts/' \
  "$SRC/" "$DST/"

# Patch Google Fonts -> local fonts for offline use
sed -i '' \
  '/rel="preconnect"/d; /fonts\.googleapis\.com\/css2/d' \
  "$DST/index.html"

# Insert local @font-face declarations if not already present
if ! grep -q 'fonts/inter-' "$DST/index.html"; then
  sed -i '' '/<meta charset="UTF-8"/a\
  <style>\
    @font-face { font-family: '"'"'Inter'"'"'; font-style: normal; font-weight: 300; font-display: swap; src: url('"'"'fonts/inter-300.ttf'"'"') format('"'"'truetype'"'"'); }\
    @font-face { font-family: '"'"'Inter'"'"'; font-style: normal; font-weight: 400; font-display: swap; src: url('"'"'fonts/inter-400.ttf'"'"') format('"'"'truetype'"'"'); }\
    @font-face { font-family: '"'"'Inter'"'"'; font-style: normal; font-weight: 500; font-display: swap; src: url('"'"'fonts/inter-500.ttf'"'"') format('"'"'truetype'"'"'); }\
    @font-face { font-family: '"'"'Inter'"'"'; font-style: normal; font-weight: 600; font-display: swap; src: url('"'"'fonts/inter-600.ttf'"'"') format('"'"'truetype'"'"'); }\
  </style>
' "$DST/index.html"
fi

# Download Inter font files if missing
for weight in 300 400 500 600; do
  if [ ! -f "$DST/fonts/inter-${weight}.ttf" ]; then
    echo "Downloading Inter weight ${weight}..."
    case $weight in
      300) slug="UcCO3FwrK3iLTeHuS_nVMrMxCp50SjIw2boKoduKmMEVuOKfMZg" ;;
      400) slug="UcCO3FwrK3iLTeHuS_nVMrMxCp50SjIw2boKoduKmMEVuLyfMZg" ;;
      500) slug="UcCO3FwrK3iLTeHuS_nVMrMxCp50SjIw2boKoduKmMEVuI6fMZg" ;;
      600) slug="UcCO3FwrK3iLTeHuS_nVMrMxCp50SjIw2boKoduKmMEVuGKYMZg" ;;
    esac
    curl -sfo "$DST/fonts/inter-${weight}.ttf" \
      "https://fonts.gstatic.com/s/inter/v20/${slug}.ttf"
  fi
done

echo "Done. Synced web-vanilla -> $DST"
