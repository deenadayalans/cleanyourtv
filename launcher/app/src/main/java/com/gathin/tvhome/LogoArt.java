package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.io.File;
import java.util.Locale;

/**
 * White dock logos (PersonalHome.logo_dock). A logo is any PNG with transparency in files/logos/<package>.png:
 * only its shape is used, painted white, so a black or a colour logo both work. Empty margins are trimmed and the
 * logo's ink coverage is measured, so a thin wordmark and a solid glyph can be drawn at the same visual weight.
 */
final class LogoArt {
    /** Longest side the logo is kept at: a dock tile is at most about 270 px wide on a 1080p TV. */
    private static final int MAX_PX = 320;

    final Bitmap bitmap;
    /** Fraction of the trimmed box the logo's ink covers, 0..1. */
    final float coverage;

    private LogoArt(Bitmap bitmap, float coverage) {
        this.bitmap = bitmap;
        this.coverage = coverage;
    }

    /**
     * The app's logo for this visit: files/logos/<package>.png and any <package>.<n>.png take turns, one per
     * return to Home. Null when the app has none.
     */
    static File pick(Context ctx, String pkg, int visit) {
        File dir = Config.dir(ctx);
        File[] all = dir == null ? null : new File(dir, "logos").listFiles();
        if (all == null) return null;
        java.util.List<String> names = new java.util.ArrayList<>();
        for (File f : all) names.add(f.getName());
        String name = pickName(names, pkg, visit);
        return name == null ? null : new File(new File(dir, "logos"), name);
    }

    /** Of the file names, this package's logos in name order, and the one for this visit. */
    static String pickName(java.util.List<String> names, String pkg, int visit) {
        java.util.List<String> mine = new java.util.ArrayList<>();
        for (String n : names) {
            if (n.equals(pkg + ".png") || n.startsWith(pkg + ".") && n.endsWith(".png")
                    && n.substring(pkg.length() + 1, n.length() - 4).matches("[0-9]+")) mine.add(n);
        }
        if (mine.isEmpty()) return null;
        java.util.Collections.sort(mine);
        return mine.get(Math.floorMod(visit, mine.size()));
    }

    /** The logo in this file, or a lettermark of the app's name when there is none or it does not decode. */
    static LogoArt load(Context ctx, File file, String label) {
        LogoArt art = file == null ? null : fromFile(file);
        return art != null ? art : lettermark(ctx, label);
    }

    private static LogoArt fromFile(File file) {
        if (!file.isFile() || file.length() > 4 * 1024 * 1024) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= MAX_PX) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        Bitmap src = BitmapFactory.decodeFile(file.getPath(), o);
        if (src == null) return null;
        LogoArt art = whiten(src);
        src.recycle();
        return art;
    }

    /** The shape of src in white, trimmed to its ink, at most MAX_PX on its longest side. */
    static LogoArt whiten(Bitmap src) {
        return whiten(src, true);
    }

    private static LogoArt whiten(Bitmap src, boolean findGlyph) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int left = w, top = h, right = -1, bottom = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 24) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right < left || bottom < top) return null;   // fully transparent
        Rect box = new Rect(left, top, right + 1, bottom + 1);
        long ink = 0;
        for (int y = box.top; y < box.bottom; y++) {
            for (int x = box.left; x < box.right; x++) ink += px[y * w + x] >>> 24;
        }
        float coverage = ink / (255f * box.width() * box.height());
        // An app icon (a light glyph on a solid rounded square) would whiten into a plain square: keep the glyph.
        if (findGlyph && coverage > 0.8f && keepGlyph(px)) {
            Bitmap glyph = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
            LogoArt art = whiten(glyph, false);
            glyph.recycle();
            return art;
        }
        float scale = Math.min(1f, MAX_PX / (float) Math.max(box.width(), box.height()));
        int ow = Math.max(1, Math.round(box.width() * scale)), oh = Math.max(1, Math.round(box.height() * scale));
        Bitmap white = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        // SRC_IN: keep the logo's alpha, take the colour from the filter.
        paint.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        new Canvas(white).drawBitmap(src, box, new Rect(0, 0, ow, oh), paint);
        return new LogoArt(white, coverage);
    }

    /**
     * For a logo drawn on a solid plate: makes the plate transparent and keeps what stands out from it, by
     * brightness (the plate is the most common shade, the glyph the far end of the range, lighter or darker).
     * Returns false, changing nothing, when no glyph stands out.
     */
    static boolean keepGlyph(int[] px) {
        int[] hist = new int[256];
        int n = 0;
        for (int p : px) {
            if ((p >>> 24) < 200) continue;
            hist[lum(p)]++;
            n++;
        }
        if (n == 0) return false;
        int median = percentile(hist, n, 0.5f), p5 = percentile(hist, n, 0.05f), p95 = percentile(hist, n, 0.95f);
        boolean lighter = p95 - median >= median - p5;
        int spread = lighter ? p95 - median : median - p5;
        if (spread < 40) return false;
        float lo = spread * 0.35f, hi = spread * 0.75f;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            float d = lighter ? lum(p) - median : median - lum(p);
            float a = Math.max(0f, Math.min(1f, (d - lo) / (hi - lo)));
            px[i] = (Math.round((p >>> 24) * a) << 24) | (p & 0xFFFFFF);
        }
        return true;
    }

    private static int lum(int p) {
        return (((p >> 16) & 0xFF) * 299 + ((p >> 8) & 0xFF) * 587 + (p & 0xFF) * 114) / 1000;
    }

    private static int percentile(int[] hist, int n, float q) {
        int want = Math.round(q * (n - 1)), seen = 0;
        for (int v = 0; v < 256; v++) {
            seen += hist[v];
            if (seen > want) return v;
        }
        return 255;
    }

    /** One short white word or letter in the launcher's font: "Prime" for Prime Video, "J" for JioHotstar. */
    static LogoArt lettermark(Context ctx, String label) {
        String text = mark(label);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(160f);
        try {
            paint.setTypeface(UiFont.get(ctx, R.string.font_medium));
        } catch (Exception ignored) {
            paint.setTypeface(Typeface.DEFAULT_BOLD);
        }
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, bounds.width() + 8), Math.max(1, bounds.height() + 8), Bitmap.Config.ARGB_8888);
        new Canvas(bmp).drawText(text, 4 - bounds.left, 4 - bounds.top, paint);
        LogoArt art = whiten(bmp);
        bmp.recycle();
        return art;
    }

    /** The first word when it is short, else its first letter. */
    static String mark(String label) {
        String s = label == null ? "" : label.trim();
        if (s.isEmpty()) return "?";
        int space = s.indexOf(' ');
        String first = space > 0 ? s.substring(0, space) : s;
        return first.length() <= 6 ? first : first.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    /**
     * How large to draw the logo inside a tile, {width, height} in px: fitted into a box of the tile's middle, then
     * scaled so its ink covers about the same area as every other logo's (a thin wordmark grows, a solid glyph
     * shrinks), never past the tile.
     */
    int[] fit(int tileW, int tileH) {
        float boxW = tileW * 0.66f, boxH = tileH * 0.56f;
        float aspect = bitmap.getWidth() / (float) bitmap.getHeight();
        float fw = boxW, fh = boxW / aspect;
        if (fh > boxH) {
            fh = boxH;
            fw = boxH * aspect;
        }
        float target = 0.26f * boxW * boxH;
        float k = (float) Math.sqrt(target / Math.max(1f, coverage * fw * fh));
        k = Math.max(0.7f, Math.min(1.35f, k));
        k = Math.min(k, Math.min(tileW * 0.9f / fw, tileH * 0.8f / fh));
        return new int[]{Math.round(fw * k), Math.round(fh * k)};
    }
}
