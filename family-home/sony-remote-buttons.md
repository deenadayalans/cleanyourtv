# Sony BRAVIA VU3: remote app buttons and volume repeat together


**TV:** Sony BRAVIA VU3 (`BRAVIA_VU3_4K`, Realtek), Google TV 14 (API 34), build `UKR1.240726.001`, India.
Same build as `examples/sony-bravia-vu31/`.

**What happened**

With launcherx disabled, the remote's Netflix, YouTube, Prime Video and Music buttons did nothing, as on the third
VU31 in that list. `INPUT_KEY_SERVICE=1` with `app_keys` (191 Netflix, 201 YouTube, 189 Prime Video, 202 Music)
brought them back, but holding volume then moved one step per press. That's the trade from
`docs/devices/tcl-55p71b-g09-key-repeat.md`, now seen on a third vendor and on Android 14. Measured in
`dumpsys audio`: up to 9 `adjustSuggestedStreamVolume` calls a second with no helper bound, 2-3 a second (one
per press) with `InputKeyService` bound.

**Why a launcher-side receiver can't fix it** (read from `/system/priv-app/AdvanceSetting/AdvanceSetting.apk`
with `dexdump`, nothing launched)

`Utils.startApp` calls `startActivity` directly only when `hasSystemFeature("com.google.android.feature.AMATI_EXPERIENCE")`
is false. Otherwise it calls `launchAppViaLauncherX`, which sends `com.google.global_button.ACTION_LAUNCH_APP` with
an explicit component, `launcherx/.globalbutton.GlobalButtonLaunch_Receiver`, and only if `canIntentBeHandled`.
So no other package can stand in for launcherx.

**What worked: both, with the new launcher as Home**

- Leave launcherx enabled: it answers the buttons and filters no keys.
- `config.xml`: `stock_home_redirect` = `com.google.android.apps.tv.launcherx`, `key_filter` = `false`. Install
  with `STOCK_LAUNCHER=none HOME_KEY_SERVICE=1`.
- `set-home-activity` prints Success but Home stays launcherx (priority 2), so `build-and-install.sh` stops at its
  Home check before it enables the helper. Enabling `HomeKeyService` by hand works: the bound service shows
  `eventTypes=TYPE_WINDOW_STATE_CHANGED`, Home lands on `com.gathin.tvhome` after a brief launcherx flash, all
  four buttons open their apps, and holding volume ramps (7-8 steps a second). It survived a reboot.
- Disabling only `launcherx/.home.HomeActivity` is refused on this build: `SecurityException: Shell cannot change
  component state`.
- With launcherx answering, the Music button opens YouTube at `youtube.com/topic/music?launch=remote`, not the
  YouTube Music app (firmware behaviour).

**Suggestion:** a note in `examples/sony-bravia-vu31/` and the key-repeat doc, and maybe a way for
`build-and-install.sh` to accept Home resolving to `stock_home_redirect` when `HOME_KEY_SERVICE=1` is set.
That second part touches the Home guard, so it's the maintainer's call.
