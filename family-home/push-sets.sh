#!/usr/bin/env bash
# Copy the repo themes' pictures to the TV as extra wallpaper sets (files/sets/<n>-<name>/), which the wallpaper
# button steps through after the family photos: Scenes, Places, Aurora, Ruska, Glass. Your own theme stays active.
# Add a set of your own: put pictures in private/sets/<n>-<name>/ and run this again.
# Usage (from the repo root): TV_HOST=<tv ip> family-home/push-sets.sh   then family-home/push-theme.sh to restart Home.
set -euo pipefail
shopt -s nullglob nocaseglob
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address}"
PY=tools/venv/bin/python
PRIVATE="${PRIVATE_DIR:-private}"
DIR=/sdcard/Android/data/com.gathin.tvhome/files/sets
$PY -I tools/tv.py "rm -rf $DIR; mkdir -p $DIR"
push_font() {   # push_font <folder on the TV, under files/> <font file>
  $PY -I tools/tv.py "mkdir -p /sdcard/Android/data/com.gathin.tvhome/files/$1"
  $PY -I tools/b64push.py "$2" /data/local/tmp/set-font.ttf >/dev/null
  $PY -I tools/tv.py "cp /data/local/tmp/set-font.ttf /sdcard/Android/data/com.gathin.tvhome/files/$1/${3:-font.ttf} && rm /data/local/tmp/set-font.ttf"
  echo "$1: font $(basename "$2")"
}
push_set() {   # push_set <folder name on the TV> <picture>...
  local name=$1; shift
  $PY -I tools/tv.py "mkdir -p $DIR/$name"
  for f in "$@"; do
    local base; base=$(basename "$f")
    [ "$base" = wallpaper.jpg ] && base="${name#*-}.jpg"   # aurora.jpg, not another wallpaper.jpg
    $PY -I tools/b64push.py "$f" "/data/local/tmp/set-$base" >/dev/null
    $PY -I tools/tv.py "cp /data/local/tmp/set-$base $DIR/$name/$base && rm /data/local/tmp/set-$base"
  done
  echo "$name: $# picture(s)"
}
# Each repo theme's own picture plus the ones family-home/make_wallpapers.py draws for it (family-home/art/<set>/). The fonts
# in family-home/fonts/ (open-licence Google Fonts) go to files/fonts/.
push_set 1-scenes themes/scenes/wallpapers/*.jpg
push_set 2-places family-home/art/places/*.jpg
push_set 3-aurora themes/aurora/wallpaper.jpg family-home/art/aurora/*.jpg
push_set 4-ruska themes/ruska/wallpaper.jpg family-home/art/ruska/*.jpg
push_set 5-glass themes/glass/wallpaper.jpg family-home/art/glass/*.jpg
# The fonts the home screen takes turns with (one per visit to Home), next to the launcher's own Outfit.
$PY -I tools/tv.py "rm -rf /sdcard/Android/data/com.gathin.tvhome/files/fonts"
for f in family-home/fonts/*.ttf; do push_font fonts "$f" "$(basename "$f")"; done
for d in ${PRIVATE}/sets/*/; do push_set "$(basename "$d")" "$d"*.jpg "$d"*.jpeg "$d"*.png; done
