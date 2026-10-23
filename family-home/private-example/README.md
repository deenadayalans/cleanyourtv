# private/ (example)

Copy this folder to `private/` at the repo root and fill it in. It stays out of the public repo (see
`.gitignore`); keep it in a private repository of its own.

- `photos/`: your family photos (JPEG or HEIC). `family-home/refresh-wallpapers.sh` makes the TV copies.
- `logos/`: one PNG per dock app, named as in the list at the end of `family-home/push-logos.sh`. Logos belong to
  their companies: keep them private.
- `birthdays.json`: `["Name=DD-MM", ...]`.
- `theme-overrides.json` (optional): theme keys you would rather not publish, e.g. `{"vpn_app": "..."}`.
