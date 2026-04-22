#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_ICON="$ROOT_DIR/assets/app_icon/source/app_icon.ico"
APP_ICON_DIR="$ROOT_DIR/macos/Runner/Assets.xcassets/AppIcon.appiconset"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

if [[ ! -f "$SOURCE_ICON" ]]; then
  echo "Missing source icon: $SOURCE_ICON"
  exit 1
fi

render_icon() {
  local size="$1"
  local output="$2"
  sips -s format png -z "$size" "$size" "$SOURCE_ICON" --out "$TMP_DIR/$output" >/dev/null
  cp "$TMP_DIR/$output" "$APP_ICON_DIR/$output"
}

render_icon 16 app_icon_16.png
render_icon 32 app_icon_32.png
render_icon 64 app_icon_64.png
render_icon 128 app_icon_128.png
render_icon 256 app_icon_256.png
render_icon 512 app_icon_512.png
render_icon 1024 app_icon_1024.png

echo "Updated macOS app icons from $SOURCE_ICON"
