#!/usr/bin/env bash
# Rebuild and reinstall the launcher for this TV after editing launcher/app/src/main/res/values/config.xml.
# Usage (from the repo root): TV_HOST=<tv ip> family-home/install.sh
set -uo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
T() { tools/venv/bin/python tools/tv.py "$@"; }
# STOCK_LAUNCHER=none: Google's launcher must stay ENABLED here, it answers the remote's app buttons.
# The script's Home check then reports that Home resolves to Google's launcher: expected on this TV,
# HomeKeyService (stock_home_redirect in config.xml) sends that screen back to the new launcher.
STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 launcher/build-and-install.sh
# The script stops at that check before enabling the helper, so enable it here (key_filter is false in
# config.xml, so it watches windows only and holding volume still ramps).
T 'cmd appops set com.gathin.tvhome AUTO_START allow 2>/dev/null; settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1'
sleep 2
T 'input keyevent HOME'
sleep 4
T 'dumpsys activity activities | grep mFocusedApp'
echo "mFocusedApp should name com.gathin.tvhome/.MainActivity."
