#!/usr/bin/env bash
# Show one weather effect all the time (snow, rain, storm, wind, stars, sun), or "off" for the real weather.
# Also: birthday <Name> celebrates Name today, "birthday off" stops it.
# Usage (from the repo root): TV_HOST=<tv ip> family-home/fx-preview.sh snow
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address}"
FX="${1:?usage: fx-preview.sh snow|rain|storm|wind|stars|sun|off  or  birthday <Name>|off}"
PY=tools/venv/bin/python
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
case "$FX" in
  off) SET="weather_effects_preview=-" ;;
  birthday) [ "${2:-off}" = off ] && SET="birthday_preview=-" || SET="birthday_preview=$2" ;;
  *) SET="weather_effects_preview=$FX" ;;
esac
$PY -I family-home/build_config.py "$TMP/config.json" "$SET"
DIR=/sdcard/Android/data/com.gathin.tvhome/files
$PY -I tools/tv.py --push "$TMP/config.json" /data/local/tmp/theme-config.json >/dev/null
# Restart Home the way theme.sh does, keeping the Home helper switched on.
$PY -I tools/tv.py "cp /data/local/tmp/theme-config.json $DIR/config.json; rm /data/local/tmp/theme-config.json; a=\$(settings get secure enabled_accessibility_services); am force-stop com.gathin.tvhome; am start -n com.gathin.tvhome/.MainActivity >/dev/null; sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1; echo preview $*"
