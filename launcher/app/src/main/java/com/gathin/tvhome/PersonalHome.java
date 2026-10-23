package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Personal home-screen keys (this branch only): white logos in the dock, rotating taglines, a clock without the
 * date, rotating weather for a few places, and a new wallpaper on every return to Home. Read from config.xml and
 * overlaid by the pushed theme's config.json (the copy Config keeps in the app's files dir). All off by default.
 */
final class PersonalHome {

    /** One weather place: the name shown next to its temperature, and where it is. */
    static final class Place {
        final String name;
        final double lat, lon;

        Place(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    /** logo_dock: the dock shows white logos from files/logos/<package>.png, with no tiles, band or glass. */
    final boolean logoDock;
    /** clock_only: no greeting and no date beside the clock. */
    final boolean clockOnly;
    /** wallpaper_change_on_home: a different picture from the collection each time Home comes back. */
    final boolean wallpaperChangeOnHome;
    /** wallpaper_bottom_shade: a black gradient over the lower part of the wallpaper, darker as the grid scrolls. */
    final boolean wallpaperBottomShade;
    /** weather_places: "Name=lat,lon"; empty leaves the weather (and every network request) out. */
    final List<Place> weatherPlaces;
    /** weather_rotate_seconds: how long each place stays before the next one fades in. */
    final int weatherRotateSeconds;
    /** weather_effects: snow, rain, storms, fog and so on over the whole screen, for the place being shown. */
    final boolean weatherEffects;
    /** weather_effects_preview: snow, rain, storm, fog, cloud, stars or sun all the time, whatever the weather. */
    final String weatherPreview;
    /** birthdays: "Name=DD-MM" (a year after it is allowed and ignored); on the day, a greeting and confetti. */
    final List<String> birthdays;
    /** birthday_preview: celebrate this name today, whatever the date, to see what it looks like. */
    final String birthdayPreview;
    /** taglines: "package=line" entries, several per package allowed; one is shown per visit to Home. */
    final Map<String, List<String>> taglines;

    private PersonalHome(boolean logoDock, boolean clockOnly, boolean wallpaperChangeOnHome, boolean wallpaperBottomShade,
                         List<Place> weatherPlaces, int weatherRotateSeconds, boolean weatherEffects, String preview,
                         Map<String, List<String>> taglines, List<String> birthdays, String birthdayPreview) {
        this.birthdays = Collections.unmodifiableList(birthdays);
        this.birthdayPreview = birthdayPreview;
        this.weatherEffects = weatherEffects;
        this.weatherPreview = preview;
        this.logoDock = logoDock;
        this.clockOnly = clockOnly;
        this.wallpaperChangeOnHome = wallpaperChangeOnHome;
        this.wallpaperBottomShade = wallpaperBottomShade;
        this.weatherPlaces = Collections.unmodifiableList(weatherPlaces);
        this.weatherRotateSeconds = weatherRotateSeconds;
        this.taglines = Collections.unmodifiableMap(taglines);
    }

    static PersonalHome load(Context ctx) {
        Resources res = ctx.getResources();
        boolean logoDock = res.getBoolean(R.bool.logo_dock);
        boolean clockOnly = res.getBoolean(R.bool.clock_only);
        boolean changeOnHome = res.getBoolean(R.bool.wallpaper_change_on_home);
        boolean bottomShade = res.getBoolean(R.bool.wallpaper_bottom_shade);
        List<String> places = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.weather_places)));
        int rotate = res.getInteger(R.integer.weather_rotate_seconds);
        boolean effects = res.getBoolean(R.bool.weather_effects);
        String preview = "";
        List<String> lines = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.taglines)));
        List<String> birthdays = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.birthdays)));
        String birthdayPreview = "";
        JSONObject json = readTheme(ctx);
        if (json != null) {
            logoDock = json.optBoolean("logo_dock", logoDock);
            clockOnly = json.optBoolean("clock_only", clockOnly);
            changeOnHome = json.optBoolean("wallpaper_change_on_home", changeOnHome);
            bottomShade = json.optBoolean("wallpaper_bottom_shade", bottomShade);
            places = strings(json.optJSONArray("weather_places"), places);
            rotate = json.optInt("weather_rotate_seconds", rotate);
            effects = json.optBoolean("weather_effects", effects);
            preview = json.optString("weather_effects_preview", "").trim();
            lines = strings(json.optJSONArray("taglines"), lines);
            birthdays = strings(json.optJSONArray("birthdays"), birthdays);
            birthdayPreview = json.optString("birthday_preview", "").trim();
        }
        return new PersonalHome(logoDock, clockOnly, changeOnHome, bottomShade, places(places),
                Math.max(5, Math.min(120, rotate)), effects, preview, taglines(lines), birthdays, birthdayPreview);
    }

    /** A wallpaper set's name from its folder: "1-scenes" is "Scenes", "my_trip" is "My trip". */
    static String setName(String folder) {
        String s = folder.replaceFirst("^[0-9]+[-_ ]*", "").replace('_', ' ').replace('-', ' ').trim();
        if (s.isEmpty()) return folder;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The largest fontScale: the header keeps room for it. */
    static final float MAX_FONT_SCALE = 1.18f;

    /** How much larger to draw a font whose letters are small for its size: Cormorant, by about a fifth. */
    static float fontScale(String fileName) {
        return fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).contains("cormorant") ? MAX_FONT_SCALE : 1f;
    }

    /** Whose birthday it is on this day (birthday_preview's name too), in list order. */
    List<String> birthdaysOn(java.time.LocalDate day) {
        List<String> out = celebrating(birthdays, day.getDayOfMonth(), day.getMonthValue());
        if (!birthdayPreview.isEmpty() && !out.contains(birthdayPreview)) out.add(birthdayPreview);
        return out;
    }

    /** The names in "Name=DD-MM" entries whose day and month match; an entry that does not parse is skipped. */
    static List<String> celebrating(List<String> entries, int day, int month) {
        List<String> out = new ArrayList<>();
        for (String entry : entries) {
            int eq = entry.lastIndexOf('=');
            if (eq <= 0) continue;
            String[] dm = entry.substring(eq + 1).trim().split("[-/.]");
            try {
                if (dm.length >= 2 && Integer.parseInt(dm[0].trim()) == day && Integer.parseInt(dm[1].trim()) == month) {
                    out.add(entry.substring(0, eq).trim());
                }
            } catch (NumberFormatException ignored) {
                // skip it
            }
        }
        return out;
    }

    /** "Happy birthday, Anya!", "Happy birthday, Anya and Ravi!", "Happy birthday, Anya, Ravi and Amma!" */
    static String greeting(List<String> names) {
        StringBuilder who = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) who.append(i == names.size() - 1 ? " and " : ", ");
            who.append(names.get(i));
        }
        return "Happy birthday, " + who + "!";
    }

    /** The tagline for this app on this visit, or null when it has none. */
    String tagline(String pkg, int visit) {
        return pick(pkg == null ? null : taglines.get(pkg), visit);
    }

    /** The line for this visit, going round the list; null for none. */
    static String pick(List<String> lines, int visit) {
        if (lines == null || lines.isEmpty()) return null;
        return lines.get(Math.floorMod(visit, lines.size()));
    }

    /** "Doha=25.29,51.53"; an entry that does not parse is skipped. */
    static List<Place> places(List<String> entries) {
        List<Place> out = new ArrayList<>();
        for (String entry : entries) {
            int eq = entry.indexOf('='), comma = entry.indexOf(',', eq + 1);
            if (eq <= 0 || comma < 0) continue;
            try {
                double lat = Double.parseDouble(entry.substring(eq + 1, comma).trim());
                double lon = Double.parseDouble(entry.substring(comma + 1).trim());
                if (Math.abs(lat) <= 90 && Math.abs(lon) <= 180) out.add(new Place(entry.substring(0, eq).trim(), lat, lon));
            } catch (NumberFormatException ignored) {
                // skip it
            }
        }
        return out;
    }

    /** "package=line" entries in order, grouped by package. */
    static Map<String, List<String>> taglines(List<String> entries) {
        Map<String, List<String>> out = new HashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String pkg = entry.substring(0, eq).trim(), line = entry.substring(eq + 1).trim();
            if (pkg.isEmpty() || line.isEmpty()) continue;
            List<String> list = out.get(pkg);
            if (list == null) out.put(pkg, list = new ArrayList<>());
            list.add(line);
        }
        return out;
    }

    private static List<String> strings(JSONArray a, List<String> fallback) {
        if (a == null) return fallback;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /** The theme's config.json as Config last kept it; null with no theme. */
    private static JSONObject readTheme(Context ctx) {
        File file = new File(ctx.getFilesDir(), Config.FILE_NAME);
        if (!file.isFile() || file.length() > 64 * 1024) return null;
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }
}
