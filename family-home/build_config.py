"""Writes the theme's config.json for the TV: family-home/theme/config.json plus what stays private.

    python3 family-home/build_config.py OUT.json [key=value ...]

Adds "birthdays" from private/birthdays.json (a JSON list of "Name=DD-MM") and any keys in
private/theme-overrides.json, so names and dates never sit in the public repo. Extra key=value arguments set a key
for this push only (fx-preview.sh uses it); a value of "-" removes the key.
"""
import json
import os
import sys

here = os.path.dirname(os.path.abspath(__file__))
private = os.environ.get("PRIVATE_DIR", os.path.join(os.path.dirname(here), "private"))

with open(os.path.join(here, "theme", "config.json"), encoding="utf-8") as f:
    config = json.load(f)
birthdays = os.path.join(private, "birthdays.json")
if os.path.isfile(birthdays):
    with open(birthdays, encoding="utf-8") as f:
        config["birthdays"] = [str(b) for b in json.load(f)]
overrides = os.path.join(private, "theme-overrides.json")
if os.path.isfile(overrides):
    with open(overrides, encoding="utf-8") as f:
        config.update(json.load(f))
for arg in sys.argv[2:]:
    key, _, value = arg.partition("=")
    if value == "-":
        config.pop(key, None)
    else:
        config[key] = value
with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(config, f, indent=2, ensure_ascii=False)
