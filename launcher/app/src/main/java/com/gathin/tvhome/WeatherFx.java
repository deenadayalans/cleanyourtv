package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Full-screen weather to match the place the weather line shows (PersonalHome weather_effects): spinning
 * snowflakes, rain, a storm with lightning, blowing leaves on a windy day, stars and the odd shooting star at night,
 * a slowly turning sun by day; nothing for fog or cloud. Every shape is drawn once into a small bitmap; a frame only places
 * a few dozen of them, at 25 frames a second, while Home is on screen. Nothing runs while an app is open.
 */
final class WeatherFx extends View {
    static final int NONE = 0, SNOW = 1, RAIN = 2, STORM = 3, STARS = 6, SUN = 7, WIND = 8;
    private static final long FRAME_MS = 40;
    private static final int MAX = 100;
    /** Rain: the first few particles are drops sliding down the screen glass; the rest are falling streaks. */
    private int glassDrops;

    private final Random random = new Random();
    private final float density;
    // One particle per index: position, speed, size, spin angle and speed, which sprite, and a phase.
    private final float[] x = new float[MAX], y = new float[MAX], vx = new float[MAX], vy = new float[MAX];
    private final float[] size = new float[MAX], angle = new float[MAX], spin = new float[MAX], phase = new float[MAX];
    private final int[] sprite = new int[MAX];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private Bitmap[] flakes, leaves;
    private Bitmap drop, streak, sunGlow, sunRays;
    private int kind = NONE, count;
    private boolean running;
    private long last, nextFlash, flashAt, nextMeteor, meteorAt;
    private float meteorX, meteorY, sunTurn;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = SystemClock.uptimeMillis();
            step(Math.min(0.1f, (now - last) / 1000f), now);
            last = now;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    WeatherFx(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
    }

    /** The effect for a WMO weather code; a clear sky by day or night. */
    static int kindFor(int code, boolean day) {
        if (code >= 95) return STORM;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return SNOW;
        if (code >= 51) return RAIN;
        // Fog and cloud draw nothing: a grey veil over the picture only dulled the screen.
        if (code >= 2) return NONE;
        return day ? SUN : STARS;
    }

    /** An effect by name (weather_effects_preview), or -1 for an unknown one. */
    static int kindNamed(String name) {
        switch (name == null ? "" : name.toLowerCase(java.util.Locale.ROOT)) {
            case "snow": return SNOW;
            case "rain": return RAIN;
            case "storm": return STORM;
            case "stars": return STARS;
            case "sun": return SUN;
            case "wind": return WIND;
            default: return -1;
        }
    }

    /** Switches to an effect, fading the old one out and the new one in. */
    void show(int next) {
        if (next == kind) return;
        animate().cancel();
        if (getAlpha() == 0f || kind == NONE) {
            begin(next);
            return;
        }
        animate().alpha(0f).setDuration(500).withEndAction(() -> begin(next)).start();
    }

    private void begin(int next) {
        kind = next;
        seed();
        setAlpha(0f);
        if (kind != NONE) animate().alpha(1f).setDuration(900).start();
        if (running) restart();
    }

    void start() {
        running = true;
        restart();
    }

    void stop() {
        running = false;
        removeCallbacks(tick);
        animate().cancel();
        if (kind != NONE) setAlpha(1f);
    }

    private void restart() {
        removeCallbacks(tick);
        if (kind == NONE || getWidth() == 0) {
            if (kind != NONE) post(this::restart);   // first layout still to come
            return;
        }
        last = SystemClock.uptimeMillis();
        post(tick);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        seed();
    }

    /** Fresh particles spread over the screen, so an effect starts full instead of falling in from the top. */
    private void seed() {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        switch (kind) {
            case SNOW: count = 45; break;
            case RAIN: count = 70; break;
            case STORM: count = 95; break;
            case WIND: count = 34; break;
            case STARS: count = 55; break;
            case SUN: count = 12; break;
            default: count = 0;
        }
        glassDrops = kind == RAIN ? 8 : kind == STORM ? 10 : 0;
        for (int i = 0; i < count; i++) spawn(i, w, h, random.nextFloat() * h);
        long now = SystemClock.uptimeMillis();
        nextFlash = now + 3000 + random.nextInt(6000);
        nextMeteor = now + 4000 + random.nextInt(6000);
        flashAt = meteorAt = 0;
    }

    private void spawn(int i, int w, int h, float startY) {
        float d = density;
        phase[i] = random.nextFloat() * 6.283f;
        x[i] = random.nextFloat() * w;
        y[i] = startY;
        switch (kind) {
            case SNOW: {
                float depth = random.nextFloat();   // near flakes are bigger, faster and brighter
                size[i] = (5 + depth * 9) * d;
                vy[i] = (22 + depth * 40) * d;
                vx[i] = (random.nextFloat() - 0.5f) * 10 * d;
                angle[i] = random.nextFloat() * 60f;
                spin[i] = (random.nextFloat() - 0.5f) * 50f;
                sprite[i] = random.nextInt(3);
                break;
            }
            case RAIN:
            case STORM: {
                if (i < glassDrops) {
                    // A drop on the screen glass: it slides slowly, then a new one beads up somewhere else.
                    size[i] = (11 + random.nextFloat() * 9) * d;
                    vy[i] = (6 + random.nextFloat() * 22) * d;
                    vx[i] = 0;
                    y[i] = h * random.nextFloat() * 0.6f;
                    angle[i] = 0;   // its age in seconds, for the fade in and out
                    break;
                }
                // Falling streaks in three depths: far ones short, faint and slower; near ones long and bright.
                float depth = random.nextFloat();
                depth = depth < 0.55f ? depth * 0.5f : depth;
                sprite[i] = depth < 0.3f ? 0 : depth < 0.75f ? 1 : 2;
                size[i] = (22 + depth * 58) * d;    // streak length
                phase[i] = 0.6f + depth * 1.6f;     // streak width, dp
                vy[i] = (kind == STORM ? 1500 : 1150) * (0.55f + depth * 0.6f) * d / 1.5f;
                vx[i] = -vy[i] * (kind == STORM ? 0.2f : 0.09f);
                break;
            }
            case WIND:
                // Leaves tumbling across, and faint streaks of air between them.
                sprite[i] = i < 20 ? random.nextInt(3) : 3;
                x[i] = startY == -1 ? -40 * d : random.nextFloat() * w;
                y[i] = h * (0.08f + random.nextFloat() * 0.8f);
                if (sprite[i] < 3) {
                    size[i] = (7 + random.nextFloat() * 9) * d;
                    vx[i] = (260 + random.nextFloat() * 260) * d;
                    vy[i] = (random.nextFloat() - 0.3f) * 40 * d;
                    spin[i] = (random.nextFloat() - 0.5f) * 520f;
                    angle[i] = random.nextFloat() * 360f;
                } else {
                    size[i] = (120 + random.nextFloat() * 220) * d;   // streak length
                    vx[i] = (700 + random.nextFloat() * 500) * d;
                }
                break;
            case STARS:
                size[i] = (1 + random.nextFloat() * 1.8f) * d;
                y[i] = random.nextFloat() * h * 0.55f;
                vx[i] = 0.6f + random.nextFloat() * 1.6f;   // twinkle speed
                break;
            case SUN:
                // A few soft sparkles drifting up through the sunlight.
                size[i] = (3 + random.nextFloat() * 5) * d;
                vy[i] = -(8 + random.nextFloat() * 12) * d;
                vx[i] = (random.nextFloat() - 0.5f) * 8 * d;
                break;
            default:
                break;
        }
    }

    private void step(float dt, long now) {
        int w = getWidth(), h = getHeight();
        boolean rain = kind == RAIN || kind == STORM;   // rain keeps its streak width in phase
        for (int i = 0; i < count; i++) {
            if (!rain) phase[i] += dt;
            switch (kind) {
                case SNOW:
                    y[i] += vy[i] * dt;
                    x[i] += (vx[i] + (float) Math.sin(phase[i] * 1.2f) * 16 * density) * dt;
                    angle[i] += spin[i] * dt;
                    if (y[i] > h + size[i]) spawn(i, w, h, -size[i]);
                    break;
                case RAIN:
                case STORM:
                    if (i < glassDrops) {
                        angle[i] += dt;
                        y[i] += vy[i] * dt * (0.6f + 0.4f * (float) Math.sin(angle[i] * 2f) * (float) Math.sin(angle[i] * 2f));
                        if (y[i] > h * 0.72f || angle[i] > 9f) spawn(i, w, h, 0);
                        break;
                    }
                    y[i] += vy[i] * dt;
                    x[i] += vx[i] * dt;
                    if (y[i] > h + size[i]) spawn(i, w, h, -size[i] - random.nextFloat() * h * 0.3f);
                    break;
                case SUN:
                    y[i] += vy[i] * dt;
                    x[i] += (vx[i] + (float) Math.sin(phase[i]) * 6 * density) * dt;
                    if (y[i] < -10) spawn(i, w, h, h + 10);
                    break;
                case WIND:
                    x[i] += vx[i] * dt;
                    if (sprite[i] < 3) {
                        y[i] += (vy[i] + (float) Math.sin(phase[i] * 3f) * 60 * density) * dt;
                        angle[i] += spin[i] * dt;
                    }
                    if (x[i] - size[i] > w) spawn(i, w, h, -1);
                    break;
                default:
                    break;
            }
            if (kind == SNOW || kind == RAIN || kind == STORM || kind == SUN) {
                if (x[i] < -40 * density) x[i] += w + 80 * density;
                else if (x[i] > w + 40 * density) x[i] -= w + 80 * density;
            }
        }
        if (kind == STORM && now >= nextFlash) {
            flashAt = now;
            nextFlash = now + 5000 + random.nextInt(9000);
        }
        if (kind == STARS && now >= nextMeteor) {
            meteorAt = now;
            meteorX = w * (0.3f + random.nextFloat() * 0.6f);
            meteorY = h * (0.05f + random.nextFloat() * 0.2f);
            nextMeteor = now + 8000 + random.nextInt(10000);
        }
        if (kind == SUN) sunTurn += 3f * dt;   // degrees a second
    }

    @Override
    protected void onDraw(Canvas c) {
        switch (kind) {
            case SNOW:
                if (flakes == null) flakes = new Bitmap[]{flake(0), flake(1), flake(2)};
                for (int i = 0; i < count; i++) {
                    Bitmap b = flakes[sprite[i]];
                    float s = size[i];
                    bitmapPaint.setAlpha(s > 10 * density ? 235 : 165);
                    c.save();
                    c.rotate(angle[i], x[i], y[i]);
                    dst.set(x[i] - s, y[i] - s, x[i] + s, y[i] + s);
                    src.set(0, 0, b.getWidth(), b.getHeight());
                    c.drawBitmap(b, src, dst, bitmapPaint);
                    c.restore();
                }
                break;
            case RAIN:
            case STORM: {
                if (drop == null) {
                    drop = drop();
                    streak = streak();
                }
                int first = Math.min(glassDrops, count);
                float tilt = count > first ? (float) Math.toDegrees(Math.atan2(-vx[first], vy[first])) : 0f;
                src.set(0, 0, streak.getWidth(), streak.getHeight());
                c.save();
                for (int i = first; i < count; i++) {
                    float len = size[i], wid = phase[i] * density;
                    bitmapPaint.setAlpha(sprite[i] == 0 ? 70 : sprite[i] == 1 ? 120 : 175);
                    c.save();
                    c.rotate(tilt, x[i], y[i]);
                    dst.set(x[i] - wid, y[i] - len, x[i] + wid, y[i]);
                    c.drawBitmap(streak, src, dst, bitmapPaint);
                    c.restore();
                }
                c.restore();
                src.set(0, 0, drop.getWidth(), drop.getHeight());
                for (int i = 0; i < first; i++) {
                    float age = angle[i], hgt = size[i], wid = hgt * 0.62f;
                    float a = Math.min(1f, age / 0.8f) * Math.min(1f, Math.max(0f, (9f - age) / 1f));
                    bitmapPaint.setAlpha((int) (210 * a));
                    dst.set(x[i] - wid / 2, y[i] - hgt, x[i] + wid / 2, y[i]);
                    c.drawBitmap(drop, src, dst, bitmapPaint);
                }
                if (kind == STORM && flashAt > 0) drawFlash(c);
                break;
            }
            case WIND: {
                if (leaves == null) leaves = new Bitmap[]{leaf(0xFF7FB069), leaf(0xFFE0A43A), leaf(0xFFC2603A)};
                if (streak == null) streak = streak();
                for (int i = 0; i < count; i++) {
                    if (sprite[i] < 3) {
                        Bitmap b = leaves[sprite[i]];
                        float s = size[i];
                        bitmapPaint.setAlpha(230);
                        src.set(0, 0, b.getWidth(), b.getHeight());
                        c.save();
                        c.rotate(angle[i], x[i], y[i]);
                        dst.set(x[i] - s, y[i] - s * 0.55f, x[i] + s, y[i] + s * 0.55f);
                        c.drawBitmap(b, src, dst, bitmapPaint);
                        c.restore();
                    } else {
                        // The streak sprite is vertical: turned on its side, its bright end leads the gust.
                        float len = size[i], wid = 1.1f * density;
                        bitmapPaint.setAlpha(60);
                        src.set(0, 0, streak.getWidth(), streak.getHeight());
                        c.save();
                        c.rotate(-90f, x[i], y[i]);
                        dst.set(x[i] - wid, y[i] - len, x[i] + wid, y[i]);
                        c.drawBitmap(streak, src, dst, bitmapPaint);
                        c.restore();
                    }
                }
                break;
            }
            case STARS:
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < count; i++) {
                    float t = 0.5f + 0.5f * (float) Math.sin(phase[i] * vx[i] * 3f);
                    paint.setColor(((int) (60 + 195 * t) << 24) | 0xFFFFFF);
                    c.drawCircle(x[i], y[i], size[i], paint);
                }
                if (meteorAt > 0) drawMeteor(c);
                break;
            case SUN:
                drawSun(c);
                break;
            default:
                break;
        }
    }

    /** The sun's glow in the top corner away from the clock, its rays turning slowly, and the sparkles. */
    private void drawSun(Canvas c) {
        int w = getWidth();
        if (sunGlow == null) {
            sunGlow = sunGlow();
            sunRays = sunRays();
        }
        float cx = w * 0.78f, cy = -40 * density, r = 520 * density;
        bitmapPaint.setAlpha(255);
        src.set(0, 0, sunGlow.getWidth(), sunGlow.getHeight());
        dst.set(cx - r, cy - r, cx + r, cy + r);
        c.drawBitmap(sunGlow, src, dst, bitmapPaint);
        c.save();
        c.rotate(sunTurn, cx, cy);
        float rr = 700 * density;
        src.set(0, 0, sunRays.getWidth(), sunRays.getHeight());
        dst.set(cx - rr, cy - rr, cx + rr, cy + rr);
        bitmapPaint.setAlpha(150);
        c.drawBitmap(sunRays, src, dst, bitmapPaint);
        c.restore();
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            float t = 0.5f + 0.5f * (float) Math.sin(phase[i] * 1.6f);
            paint.setColor(((int) (50 + 110 * t) << 24) | 0xFFF4D0);
            c.drawCircle(x[i], y[i], size[i], paint);
        }
    }

    /** A lightning flash: a quick bright white, a dip, a second smaller flicker, then gone. */
    private void drawFlash(Canvas c) {
        long t = SystemClock.uptimeMillis() - flashAt;
        float a;
        if (t < 80) a = 0.55f;
        else if (t < 160) a = 0.1f;
        else if (t < 230) a = 0.35f;
        else if (t < 600) a = 0.35f * (1f - (t - 230) / 370f);
        else {
            flashAt = 0;
            return;
        }
        c.drawColor(((int) (a * 255) << 24) | 0xFFFFFF);
    }

    /** A shooting star: a bright head with a fading tail, across the sky in under a second. */
    private void drawMeteor(Canvas c) {
        long t = SystemClock.uptimeMillis() - meteorAt;
        if (t > 900) {
            meteorAt = 0;
            return;
        }
        float k = t / 900f, d = density;
        float hx = meteorX - 420 * d * k, hy = meteorY + 180 * d * k;
        float fade = k < 0.8f ? 1f : (1f - k) / 0.2f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setShader(new android.graphics.LinearGradient(hx, hy, hx + 120 * d, hy - 52 * d,
                ((int) (230 * fade) << 24) | 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        paint.setStrokeWidth(2.2f * d);
        c.drawLine(hx, hy, hx + 120 * d, hy - 52 * d, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    // ---- sprites, each drawn once ---------------------------------------------------------------

    /** A six-armed snowflake, white, in one of three designs. */
    static Bitmap flake(int design) {
        int n = 96;
        float c = n / 2f, arm = n * 0.46f;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(n * (design == 2 ? 0.05f : 0.04f));
        for (int k = 0; k < 6; k++) {
            canvas.save();
            canvas.rotate(k * 60f, c, c);
            canvas.drawLine(c, c, c, c - arm, p);
            if (design == 0) {          // two pairs of branches
                branch(canvas, p, c, c - arm * 0.45f, arm * 0.28f);
                branch(canvas, p, c, c - arm * 0.72f, arm * 0.2f);
            } else if (design == 1) {   // one long pair and a tip
                branch(canvas, p, c, c - arm * 0.55f, arm * 0.36f);
                canvas.drawCircle(c, c - arm, n * 0.03f, p);
            } else {                    // a short star with a small diamond
                branch(canvas, p, c, c - arm * 0.6f, arm * 0.18f);
            }
            canvas.restore();
        }
        p.setStyle(Paint.Style.FILL);
        canvas.drawCircle(c, c, n * (design == 2 ? 0.07f : 0.05f), p);
        return b;
    }

    private static void branch(Canvas canvas, Paint p, float x, float y, float len) {
        canvas.drawLine(x, y, x - len * 0.7f, y - len * 0.7f, p);
        canvas.drawLine(x, y, x + len * 0.7f, y - len * 0.7f, p);
    }

    /** A clear glass teardrop: an almost see-through body, a bright rim and a small highlight; point up. */
    static Bitmap drop() {
        int w = 40, h = 64;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        float r = w * 0.42f, cx = w / 2f, cy = h - r - 2;
        Path tear = new Path();
        tear.moveTo(cx, 2);
        tear.cubicTo(cx + r * 0.25f, h * 0.32f, cx + r, cy - r * 0.55f, cx + r, cy);
        tear.arcTo(new RectF(cx - r, cy - r, cx + r, cy + r), 0, 180, false);
        tear.cubicTo(cx - r, cy - r * 0.55f, cx - r * 0.25f, h * 0.32f, cx, 2);
        tear.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx - r * 0.2f, cy - r * 0.2f, r * 1.4f,
                new int[]{0x14FFFFFF, 0x2EDDEBFF, 0x55C8DCFF}, new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(tear, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        p.setColor(0xA6FFFFFF);
        canvas.drawPath(tear, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xD9FFFFFF);
        canvas.drawOval(new RectF(cx - r * 0.55f, cy - r * 0.6f, cx - r * 0.2f, cy - r * 0.05f), p);
        return b;
    }

    /** One rain streak: a soft vertical line, faint at the top and brightest at the falling end. */
    static Bitmap streak() {
        int w = 8, h = 128;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            float t = y / (float) (h - 1);
            float along = t * t;                                  // fades in from the tail
            for (int x = 0; x < w; x++) {
                float dx = Math.abs(x - (w - 1) / 2f) / (w / 2f);
                float across = Math.max(0f, 1f - dx * dx);        // soft edges
                int a = Math.round(255 * along * across);
                px[y * w + x] = (a << 24) | 0xE6F0FF;
            }
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    /** A small leaf in one colour: an almond shape with a darker vein, point to the right. */
    static Bitmap leaf(int color) {
        int w = 48, h = 26;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Path p = new Path();
        p.moveTo(2, h / 2f);
        p.quadTo(w * 0.45f, -h * 0.25f, w - 2, h / 2f);
        p.quadTo(w * 0.45f, h * 1.25f, 2, h / 2f);
        p.close();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        canvas.drawPath(p, paint);
        paint.setColor(0x55000000);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(4, h / 2f, w - 6, h / 2f, paint);
        return b;
    }

    /** Warm sunlight: bright in the middle, fading out. */
    private static Bitmap sunGlow() {
        int n = 128;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(n / 2f, n / 2f, n / 2f,
                new int[]{0xCCFFF6D8, 0x66FFD98A, 0x1FFFC36B, 0x00FFC36B}, new float[]{0f, 0.18f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        new Canvas(b).drawRect(0, 0, n, n, p);
        return b;
    }

    /** Twelve soft rays fanning out from the middle. */
    private static Bitmap sunRays() {
        int n = 256;
        float c = n / 2f;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(c, c, c, new int[]{0x66FFF0C0, 0x26FFE2A0, 0x00FFE2A0},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        for (int k = 0; k < 12; k++) {
            Path ray = new Path();
            ray.moveTo(c, c);
            double a0 = Math.toRadians(k * 30 - 4), a1 = Math.toRadians(k * 30 + 4);
            ray.lineTo(c + (float) Math.cos(a0) * c, c + (float) Math.sin(a0) * c);
            ray.lineTo(c + (float) Math.cos(a1) * c, c + (float) Math.sin(a1) * c);
            ray.close();
            canvas.drawPath(ray, p);
        }
        return b;
    }
}
