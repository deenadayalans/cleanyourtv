package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The weather beside the clock (PersonalHome weather_places): an outline icon, the temperature and the place,
 * one place at a time, the next fading in every few seconds. All places come from one Open-Meteo request (no
 * key), made only while Home is on screen and at most every 30 minutes, 5 minutes after a failure.
 */
final class WeatherLine {
    private static final long FRESH_MS = 30 * 60_000L;
    private static final long RETRY_MS = 5 * 60_000L;

    /** One place's reading. */
    static final class Reading {
        final String place;
        final int temperature;
        final int icon;
        /** WMO weather code and whether it is day there, for the full-screen effect (WeatherFx). */
        final int code;
        final boolean day;
        /** The place's offset from UTC, and whether it is the home place (the first): only the others show a time. */
        final int utcOffsetSeconds;
        final boolean home;
        /** Wind at 10 m, km/h: from WINDY_KMH on a dry day, the line shows a wind icon and the screen blows. */
        final int windKmh;

        Reading(String place, int temperature, int code, boolean day) {
            this(place, temperature, code, day, 0, true, 0);
        }

        Reading(String place, int temperature, int code, boolean day, int utcOffsetSeconds, boolean home, int windKmh) {
            this.utcOffsetSeconds = utcOffsetSeconds;
            this.home = home;
            this.windKmh = windKmh;
            this.place = place;
            this.temperature = temperature;
            this.icon = windy(code, windKmh) ? R.drawable.ic_wx_wind : icon(code, day);
            this.code = code;
            this.day = day;
        }
    }

    /** Told each time another place comes on screen. */
    interface Listener {
        void onShown(Reading reading);
    }

    private final TextView view;
    private final List<PersonalHome.Place> places;
    private final long rotateMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    // Static so a recreated Home (settings, a theme push) does not fetch again before the data is stale.
    private static List<Reading> readings = new ArrayList<>();
    private static long fetchedAt, failedAt;
    private boolean active, fetching;
    private int shown;
    private Listener listener;
    /** The moving icon on screen, and its redraw while Home is showing (about 20 frames a second). */
    private WeatherIcon iconNow;
    private int iconRes;
    private final Runnable animate = new Runnable() {
        @Override
        public void run() {
            if (!active || iconNow == null) return;
            view.invalidateDrawable(iconNow);
            view.invalidate();
            handler.postDelayed(this, 50);
        }
    };

    void setListener(Listener l) {
        listener = l;
    }

    private final Runnable rotate = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            if (readings.size() > 1) {
                shown = (shown + 1) % readings.size();
                view.animate().alpha(0f).setDuration(260).withEndAction(() -> {
                    show();
                    view.animate().alpha(1f).setDuration(420).start();
                }).start();
            }
            handler.postDelayed(this, rotateMs);
        }
    };

    WeatherLine(TextView view, List<PersonalHome.Place> places, int rotateSeconds) {
        this.view = view;
        this.places = places;
        this.rotateMs = rotateSeconds * 1000L;
    }

    void start() {
        active = true;
        show();
        long now = SystemClock.elapsedRealtime();
        boolean stale = fetchedAt == 0 || now - fetchedAt >= FRESH_MS;
        boolean waiting = failedAt != 0 && now - failedAt < RETRY_MS;
        if (stale && !waiting && !fetching) fetch();
        handler.removeCallbacks(rotate);
        handler.postDelayed(rotate, rotateMs);
        handler.removeCallbacks(animate);
        handler.post(animate);
    }

    void stop() {
        active = false;
        handler.removeCallbacks(rotate);
        handler.removeCallbacks(animate);
        view.animate().cancel();
        view.setAlpha(1f);
    }

    void close() {
        stop();
        net.shutdownNow();
    }

    private void show() {
        if (readings.isEmpty()) {
            view.setText("");
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            return;
        }
        Reading r = readings.get(shown % readings.size());
        String text = String.format(Locale.ROOT, "%d°  %s", r.temperature, r.place);
        if (!r.home) {
            boolean h24 = android.text.format.DateFormat.is24HourFormat(view.getContext());
            text += " (" + localTime(System.currentTimeMillis(), r.utcOffsetSeconds, h24) + ")";
        }
        view.setText(text);
        if (iconNow == null || iconRes != r.icon) {
            iconRes = r.icon;
            iconNow = new WeatherIcon(view.getContext(), r.icon);
        }
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(iconNow, null, null, null);
        if (listener != null) listener.onShown(r);
    }

    private void fetch() {
        fetching = true;
        final String url = url(places);
        net.execute(() -> {
            List<Reading> fresh = null;
            try {
                fresh = parse(get(url), places);
            } catch (Exception ignored) {
                // offline, or the service is down: keep what we had and try again later
            }
            final List<Reading> result = fresh;
            handler.post(() -> {
                fetching = false;
                long now = SystemClock.elapsedRealtime();
                if (result == null || result.isEmpty()) {
                    failedAt = now;
                    return;
                }
                failedAt = 0;
                fetchedAt = now;
                readings = result;
                if (active) show();
            });
        });
    }

    static String url(List<PersonalHome.Place> places) {
        StringBuilder lat = new StringBuilder(), lon = new StringBuilder();
        for (PersonalHome.Place p : places) {
            if (lat.length() > 0) {
                lat.append(',');
                lon.append(',');
            }
            lat.append(String.format(Locale.ROOT, "%.4f", p.lat));
            lon.append(String.format(Locale.ROOT, "%.4f", p.lon));
        }
        return "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day,wind_speed_10m&timezone=auto";
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 256 * 1024) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    /** One location answers with an object, several with an array in the order asked. */
    static List<Reading> parse(String body, List<PersonalHome.Place> places) throws Exception {
        String s = body.trim();
        JSONArray all = s.startsWith("[") ? new JSONArray(s) : new JSONArray().put(new JSONObject(s));
        List<Reading> out = new ArrayList<>();
        for (int i = 0; i < all.length() && i < places.size(); i++) {
            JSONObject current = all.getJSONObject(i).optJSONObject("current");
            if (current == null || !current.has("temperature_2m")) continue;
            out.add(new Reading(places.get(i).name, (int) Math.round(current.getDouble("temperature_2m")),
                    current.optInt("weather_code", 0), current.optInt("is_day", 1) == 1,
                    all.getJSONObject(i).optInt("utc_offset_seconds", 0), i == 0,
                    (int) Math.round(current.optDouble("wind_speed_10m", 0))));
        }
        return out;
    }

    /** The time at a UTC offset: "2:21 PM", or "14:21" on a 24-hour TV. */
    static String localTime(long nowMs, int utcOffsetSeconds, boolean h24) {
        java.time.LocalTime t = java.time.Instant.ofEpochMilli(nowMs)
                .atOffset(java.time.ZoneOffset.ofTotalSeconds(utcOffsetSeconds)).toLocalTime();
        return t.format(java.time.format.DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm a", Locale.ENGLISH));
    }

    /** Breezy enough to show: 30 km/h or more, on a day with no rain, snow, storm or fog. */
    static final int WINDY_KMH = 30;

    static boolean windy(int code, int windKmh) {
        return windKmh >= WINDY_KMH && code <= 3;
    }

    /** WMO weather code to one of the outline icons. */
    static int icon(int code, boolean day) {
        if (code == 0) return day ? R.drawable.ic_wx_sun : R.drawable.ic_wx_moon;
        if (code <= 2) return day ? R.drawable.ic_wx_partly : R.drawable.ic_wx_moon;
        if (code == 3) return R.drawable.ic_wx_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_wx_fog;
        if (code >= 95) return R.drawable.ic_wx_storm;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return R.drawable.ic_wx_snow;
        if (code >= 51) return R.drawable.ic_wx_rain;
        return R.drawable.ic_wx_cloud;
    }
}
