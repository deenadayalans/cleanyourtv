#!/usr/bin/env bash
# Push the white logos in private/logos/ to the launcher on the TV, named by package (files/logos/<package>.png).
# Each app uses exactly the files named for it in the list at the end (NAME_dark.png or NAMEdark.png when there is
# one, else NAME.png); list two names for an app to have them take turns, one per return to Home.
# Logos are the apps' trademarks: keep them in your private folder, never in a public repo.
# Usage (from the repo root): TV_HOST=<tv ip> family-home/push-logos.sh   then family-home/push-theme.sh to restart Home.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address}"
PY=tools/venv/bin/python
PRIVATE="${PRIVATE_DIR:-private}"
DIR=/sdcard/Android/data/com.gathin.tvhome/files/logos
$PY -I tools/tv.py "mkdir -p $DIR; rm -f $DIR/*.png"
while read -r pkg names; do
  # The files named for this app, in order (several take turns, one per return to Home). For each name,
  # NAME_dark.png or NAMEdark.png is used when there is one, else NAME.png.
  files=""
  for name in $names; do
    for f in "${PRIVATE}/logos/${name}_dark.png" "${PRIVATE}/logos/${name}dark.png" "${PRIVATE}/logos/${name}.png"; do
      [ -f "$f" ] && { files="$files $f"; break; }
    done
  done
  [ -n "$files" ] || { echo "no logo for $pkg: it shows as a lettermark"; continue; }
  n=0
  for src in $files; do
    n=$((n + 1)); [ $n -eq 1 ] && dest=$pkg.png || dest=$pkg.$n.png
    $PY -I tools/b64push.py "$src" "/data/local/tmp/logo-$dest" >/dev/null
    $PY -I tools/tv.py "cp /data/local/tmp/logo-$dest $DIR/$dest && rm /data/local/tmp/logo-$dest && echo '$src -> $dest'"
  done
done <<'MAP'
com.netflix.ninja netflix
com.amazon.amazonvideo.livingroom primevideodark
com.google.android.youtube.tv youtube
com.spotify.tv.android spotify
com.apple.atve.androidtv.appletv appletv
in.startv.hotstar jiohotstar
com.sonyliv sonyliv
MAP
