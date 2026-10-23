#!/usr/bin/env bash
# Push the family home theme to the TV: the public theme, your private birthdays (private/birthdays.json) and the
# TV copies of your photos (private/wallpapers-tv/, made by refresh-wallpapers.sh). Restarts Home.
# Usage (from the repo root): TV_HOST=<tv ip> family-home/push-theme.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PRIVATE="${PRIVATE_DIR:-private}"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/theme"
tools/venv/bin/python -I family-home/build_config.py "$TMP/theme/config.json"
[ -d "$PRIVATE/wallpapers-tv" ] && ln -s "$(cd "$PRIVATE/wallpapers-tv" && pwd)" "$TMP/theme/wallpapers"
tools/theme.sh "$TMP/theme"
