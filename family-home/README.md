# Family home: a personal home screen built on Clean Your TV

A fun, family home screen for an Android TV, made on a Sony BRAVIA VU3 (Google TV 14) on top of this repo's
launcher. Everything here is **off by default** in the launcher and turned on by the theme in `theme/config.json`,
so the same code works on any TV the main launcher supports.

> This branch adds the `INTERNET` permission for the weather (Open-Meteo, no account, at most one request every
> 30 minutes while Home is on screen). The upstream launcher never has network access, so this lives in a fork.

## What it does

- **White logo dock**: the dock apps as white logos floating on the wallpaper, no tiles. The focused one grows,
  its name fades in below, and a soft glow in the app's colour follows focus, on the dock and in the grid below.
- **Taglines**: a line above the dock for the focused app, several per app taking turns on each return to Home.
- **Clock, date and weather**: the clock, a short date, and the weather for a few places taking turns every 12
  seconds, with each other place's local time in brackets. The weather icon is always gently animated.
- **Weather on screen**: the place on display shows its weather over the whole screen: spinning snowflakes, rain
  in three depths with drops sliding down the glass, storms with lightning, leaves on windy days (30 km/h+), stars
  and shooting stars at night, turning sun rays by day. Cloud and fog only animate the icon. 25 frames a second,
  only while Home is on screen, about a fifth of one CPU core on a 2 GB TV.
- **Wallpaper sets**: the round picture button next to Settings steps through your own photos, then Scenes,
  Places, Aurora, Ruska, Glass, then off. A new picture comes up each time Home opens. Bright photos get a soft
  shade where the text and logos sit, so they stay readable.
- **Fonts**: a different open-licence font on every visit (Outfit, Fredoka, Josefin Sans, Exo 2, Cormorant
  Garamond, Raleway). Layout never moves with the font.
- **Birthdays**: on someone's day, "Happy birthday, Name!" under the clock and a confetti burst each time Home
  opens. Names and dates stay in your private folder.
- **Wi-Fi pill**: the network name and a VPN badge (green when connected, red "VPN off" with `vpn_app` set).

## Public and private

Everything in this folder is shareable. What is yours stays in `private/` at the repo root, which git ignores
here (keep it in a private repository of its own):

| `private/` | What |
|---|---|
| `photos/` | Your family photos, JPEG or iPhone HEIC, any size |
| `wallpapers-tv/` | TV copies of them, made by `refresh-wallpapers.sh` |
| `logos/` | The dock apps' logos. **They are the apps' trademarks: never publish them.** Any PNG with transparency works; only the shape is used, painted white |
| `birthdays.json` | `["Anya=14-03", "Ravi=02-11"]`, day then month |
| `theme-overrides.json` | Optional: any theme key you want to keep out of the public theme |

`private-example/` shows the layout with made-up values.

## Set up

1. Follow the main [PROMPT.md](../PROMPT.md) to debloat the TV and connect over ADB. `debloat/` has the list used on
   the VU3.
2. Edit `launcher/app/src/main/res/values/config.xml` for your TV (dock apps, Live TV package, remote helpers),
   then install: `TV_HOST=<tv ip> family-home/install.sh`. On Google TV, if the remote's app buttons stop working
   once the stock launcher is off, see [sony-remote-buttons.md](sony-remote-buttons.md).
3. Copy `private-example/` to `private/` at the repo root and fill it in.
4. Push everything:

       TV_HOST=<tv ip> family-home/push-logos.sh          # logos from private/logos
       TV_HOST=<tv ip> family-home/push-sets.sh           # wallpaper sets and fonts
       TV_HOST=<tv ip> family-home/refresh-wallpapers.sh  # your photos, then the theme

## Change things

| To change | Edit, then run |
|---|---|
| Taglines, weather places, dock order, Wi-Fi/VPN | `theme/config.json`, then `family-home/push-theme.sh` |
| Birthdays | `private/birthdays.json`, then `family-home/push-theme.sh` |
| Your photos | `private/photos/`, then `family-home/refresh-wallpapers.sh` |
| Logos | `private/logos/` and the list at the end of `push-logos.sh`, then `push-logos.sh` and `push-theme.sh` |
| Created pictures | `make_wallpapers.py` (it draws them from code), `tools/venv/bin/python family-home/make_wallpapers.py family-home/art`, then `push-sets.sh` |
| A set of your own | pictures in `private/sets/<n>-<name>/`, then `push-sets.sh` |
| Fonts | a `.ttf` in `fonts/`, then `push-sets.sh` |

Try an effect or a birthday without waiting for the weather or the date:

    TV_HOST=<tv ip> family-home/fx-preview.sh snow        # rain, storm, wind, stars, sun; off
    TV_HOST=<tv ip> family-home/fx-preview.sh birthday Anya   # birthday off

## Credits and licences

- The launcher is [Clean Your TV](https://github.com/Gathin23/cleanyourtv) (MIT). This folder's code and art are
  released under the same MIT licence.
- `art/` is drawn from code by `make_wallpapers.py`; no photos or third-party images.
- `fonts/` are Google Fonts under the SIL Open Font License; each licence is next to its font.
- Weather data by [Open-Meteo](https://open-meteo.com/) (CC BY 4.0).
