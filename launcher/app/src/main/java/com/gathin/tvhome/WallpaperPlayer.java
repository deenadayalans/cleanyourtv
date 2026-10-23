package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    /** wallpaper_moods: the collection's groups by time of day; empty rotates through all of it. */
    private final Map<String, Set<String>> moods;
    /** config.xml latitude and longitude for the sun's times, and today's {sunrise, sunset} once worked out. */
    private final String latitude, longitude;
    private LocalDate sunDay;
    private int[] sun;
    /** PersonalHome wallpaper_change_on_home: every start after the first picks another picture. */
    private final boolean changeOnHome;
    private boolean started, forceNext;
    /** PersonalHome wallpaper_bottom_shade: the gradient painted into each picture, and the scroll shade over it. */
    private final boolean bottomShade;
    private View scrollShade;
    /** The wallpaper on/off button: hidden, Home shows the plain colour and no picture is decoded. */
    private boolean shown = true;
    private final int plainColor;
    /** The theme's own pictures, kept for when the wallpaper button comes back to them. */
    private final List<File> themeFiles;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this(context, root, config, false, false);
    }

    WallpaperPlayer(Context context, FrameLayout root, Config config, boolean changeOnHome, boolean bottomShade) {
        this.root = root;
        this.changeOnHome = changeOnHome;
        this.bottomShade = bottomShade;
        this.plainColor = config.backgroundColor;
        files = new ArrayList<>(config.wallpapers);
        themeFiles = Collections.unmodifiableList(new ArrayList<>(config.wallpapers));
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        moods = config.wallpaperMoods;
        latitude = moods.isEmpty() ? "" : context.getString(R.string.latitude);
        longitude = moods.isEmpty() ? "" : context.getString(R.string.longitude);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        names = config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(files.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() ? null : makeCaption(context, root);
        if (config.wallpaperEnabled && !files.isEmpty()) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (bottomShade) {
                scrollShade = new View(context);
                scrollShade.setBackgroundColor(0xFF000000);
                scrollShade.setAlpha(0f);
                scrollShade.setVisibility(View.GONE);
                scrollShade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                root.addView(scrollShade, 1, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            showFirst();
        }
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        List<File> playable = playable();
        // change_on_home: a cold start is a return to Home too, so it opens on another picture as well.
        File first = changeOnHome ? null : WallpaperRotation.find(playable, previous);
        if (first == null) first = WallpaperRotation.pick(playable, previous, order, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                show(bitmap);
                // The picture covers the whole screen: the root's plain colour under it is overdraw.
                root.setBackground(null);
                displayed = first.getName();
                showCaption(false);
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            playable = playable();
            first = WallpaperRotation.pick(playable, previous, order, random);
        }
    }

    void start() {
        active = true;
        if (changeOnHome && started) forceNext = true;
        started = true;
        update();
    }

    /** bottom_shade: how far the grid is scrolled, 0 (dock) to 1, darkens the picture by up to 60 percent. */
    void scrolled(float fraction) {
        if (scrollShade == null || !shown) return;
        float a = 0.6f * Math.max(0f, Math.min(1f, fraction));
        scrollShade.setAlpha(a);
        scrollShade.setVisibility(a > 0f ? View.VISIBLE : View.GONE);
    }

    void stop() {
        active = false;
    }

    /** The theme's own pictures (the first wallpaper set). */
    List<File> themePictures() {
        return themeFiles;
    }

    /** The wallpaper button's next set: these pictures from now on, starting with one of them at once. */
    void useSet(List<File> set) {
        if (set.isEmpty()) return;
        files.clear();
        files.addAll(set);
        forceNext = true;
        displayed = "";   // another set can reuse a file name (every single-picture theme has wallpaper.jpg)
        update();
    }

    /** True when the theme brought pictures to show. */
    boolean hasPictures() {
        return image.getParent() != null;
    }

    /** The wallpaper on/off button. Back on, the next picture of the collection comes up. */
    void setShown(boolean on) {
        if (on == shown || !hasPictures()) return;
        shown = on;
        image.setVisibility(on ? View.VISIBLE : View.GONE);
        if (scrollShade != null && !on) scrollShade.setVisibility(View.GONE);
        if (on) {
            root.setBackground(null);
            forceNext = true;
            update();
        } else {
            root.setBackgroundColor(plainColor);
        }
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private List<File> playable() {
        if (moods.isEmpty()) return files;
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return WallpaperMood.select(files, moods, WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    void close() {
        active = false;
        closed = true;
        decoder.shutdownNow();
    }

    void update() {
        if (!active || closed || loading || !shown || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
        List<File> playable = playable();
        File selected = WallpaperRotation.find(playable, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || forceNext || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(playable, previous, order, random);
        }
        forceNext = false;
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                show(bitmap);
                displayed = next.getName();
                showCaption(true);
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(110 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        root.addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (caption == null) return;
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /**
     * Black from 45 percent of the visible height down: 0, 35, 70 and 85 percent opacity, so white logos stay
     * readable over a bright photo. "Visible" is the part centerCrop leaves on a screen of another shape.
     */
    private void paintBottomShade(Bitmap bmp) {
        int bw = bmp.getWidth(), bh = bmp.getHeight();
        float visibleH = Math.min(bh, bw * (float) height / width);
        float top = (bh - visibleH) / 2f;
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint();
        // A bright photo (a white wall, a sky) gets more shade where the white text and logos sit; a dark one less.
        float lowLight = brightness(bmp, 0, bw, top + visibleH * 0.72f, top + visibleH * 0.92f);
        float boost = Math.max(0f, Math.min(1f, (lowLight - 0.35f) / 0.4f));
        paint.setShader(new LinearGradient(0, top + visibleH * 0.45f, 0, top + visibleH,
                new int[]{0x00000000, shade(0.35f + 0.2f * boost), shade(0.7f + 0.15f * boost), shade(0.85f + 0.07f * boost)},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, top + visibleH * 0.45f, bw, bh, paint);
        // Behind the clock, the date and the weather (left) and the icons (right): the brighter of the two decides,
        // nothing on a dark photo, up to 65 percent on a white one.
        float y0 = top + visibleH * 0.04f, y1 = top + visibleH * 0.26f;
        float topLight = Math.max(brightness(bmp, 0, bw * 0.45f, y0, y1), brightness(bmp, bw * 0.78f, bw, y0, y1));
        float a = 0.65f * Math.max(0f, Math.min(1f, (topLight - 0.35f) / 0.35f));
        if (a > 0f) {
            paint.setShader(new LinearGradient(0, top, 0, top + visibleH * 0.36f,
                    new int[]{shade(a), shade(a * 0.75f), 0x00000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, bw, top + visibleH * 0.36f, paint);
        }
    }

    private static int shade(float alpha) {
        return Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24;
    }

    /** Average brightness, 0..1, of a rectangle of the picture, from a sparse grid of samples. */
    static float brightness(Bitmap bmp, float fromX, float toX, float fromY, float toY) {
        int x0 = Math.max(0, (int) fromX), x1 = Math.min(bmp.getWidth() - 1, (int) toX);
        int y0 = Math.max(0, (int) fromY), y1 = Math.min(bmp.getHeight() - 1, (int) toY);
        if (y1 <= y0 || x1 <= x0) return 0f;
        long sum = 0;
        int n = 0;
        for (int y = y0; y <= y1; y += Math.max(1, (y1 - y0) / 12)) {
            for (int x = x0; x <= x1; x += Math.max(1, (x1 - x0) / 30)) {
                int p = bmp.getPixel(x, y);
                sum += (((p >> 16) & 0xFF) * 299 + ((p >> 8) & 0xFF) * 587 + (p & 0xFF) * 114) / 1000;
                n++;
            }
        }
        return n == 0 ? 0f : sum / (255f * n);
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Bitmap bitmap) {
        Bitmap old = current;
        current = bitmap;
        image.setImageBitmap(bitmap);
        if (old != null && old != bitmap) image.postDelayed(old::recycle, 500);
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0 || bottomShade;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            if (bottomShade) paintBottomShade(decoded);
            // One copy in GPU memory instead of a software bitmap plus its texture; the decode stays off the UI
            // thread for rotations, and Home's return no longer uploads 4-8 MB to the GPU.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
