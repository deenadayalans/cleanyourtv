#!/usr/bin/env bash
# Make TV copies of your photos in private/photos/ (JPEG or iPhone HEIC): upright, 1920 wide, in
# private/wallpapers-tv/, then push the theme so Home shows them. The originals are not changed.
# Usage (from the repo root): TV_HOST=<tv ip> family-home/refresh-wallpapers.sh
set -euo pipefail
shopt -s nullglob nocaseglob
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address}"
PRIVATE="${PRIVATE_DIR:-private}"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
for f in "$PRIVATE"/photos/*.heic; do n=$(basename "$f"); sips -s format jpeg "$f" --out "$TMP/${n%.*}.jpg" >/dev/null; done
mkdir -p "$PRIVATE/wallpapers-tv"
rm -f "$PRIVATE"/wallpapers-tv/*
tools/venv/bin/python -I family-home/tvjpeg.py "$PRIVATE/wallpapers-tv" "$PRIVATE"/photos/*.jpg "$PRIVATE"/photos/*.jpeg \
  "$PRIVATE"/photos/*.png "$TMP"/*.jpg
family-home/push-theme.sh
