package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * A burst of confetti for a birthday (PersonalHome birthdays): pieces fly up from the bottom corners and the top,
 * flip and flutter down, and after about ten seconds the view stops drawing. Nothing runs otherwise.
 */
final class ConfettiView extends View {
    private static final int N = 140;
    private static final long FRAME_MS = 33, LENGTH_MS = 10_000;
    private static final int[] COLORS = {0xFFFF5A5F, 0xFFFFB400, 0xFF00C2A8, 0xFF4F8BFF, 0xFFB45CFF, 0xFFFF7AC8,
            0xFFFFFFFF, 0xFF7ED957};

    private final Random random = new Random();
    private final float density;
    private final float[] x = new float[N], y = new float[N], vx = new float[N], vy = new float[N];
    private final float[] w = new float[N], h = new float[N], angle = new float[N], spin = new float[N], flip = new float[N];
    private final int[] color = new int[N];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long startedAt, last;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.1f, (now - last) / 1000f);
            last = now;
            step(dt);
            invalidate();
            if (now - startedAt > LENGTH_MS) {
                stop();
                return;
            }
            postDelayed(this, FRAME_MS);
        }
    };

    ConfettiView(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        // Invisible rather than gone: the view keeps its size, so a burst can start at once, and draws nothing.
        setVisibility(INVISIBLE);
    }

    /** One burst; a burst already running starts again. */
    void burst() {
        if (getWidth() == 0) {
            post(this::burst);   // first layout still to come
            return;
        }
        int sw = getWidth(), sh = getHeight();
        float d = density;
        for (int i = 0; i < N; i++) {
            int from = i % 3;   // left cannon, right cannon, or a shower from the top
            if (from == 2) {
                x[i] = random.nextFloat() * sw;
                y[i] = -random.nextFloat() * sh * 0.9f;    // a shower that keeps coming for several seconds
                vx[i] = (random.nextFloat() - 0.5f) * 80 * d;
                vy[i] = (40 + random.nextFloat() * 40) * d;
            } else {
                boolean left = from == 0;
                x[i] = left ? -10 * d : sw + 10 * d;
                y[i] = sh * (0.85f + random.nextFloat() * 0.1f);
                float speed = (700 + random.nextFloat() * 500) * d;
                double a = Math.toRadians(left ? 50 + random.nextFloat() * 25 : 105 + random.nextFloat() * 25);
                vx[i] = (float) Math.cos(a) * speed * 0.7f;
                vy[i] = -(float) Math.sin(a) * speed;
            }
            w[i] = (6 + random.nextFloat() * 6) * d;
            h[i] = w[i] * (0.4f + random.nextFloat() * 0.4f);
            angle[i] = random.nextFloat() * 360;
            spin[i] = (random.nextFloat() - 0.5f) * 720;
            flip[i] = random.nextFloat() * 6.28f;
            color[i] = COLORS[random.nextInt(COLORS.length)];
        }
        startedAt = last = SystemClock.uptimeMillis();
        setVisibility(VISIBLE);
        setAlpha(1f);
        running = true;
        removeCallbacks(tick);
        post(tick);
    }

    void stop() {
        running = false;
        removeCallbacks(tick);
        setVisibility(INVISIBLE);
    }

    private void step(float dt) {
        float g = 900 * density, drag = 1.6f;
        long age = SystemClock.uptimeMillis() - startedAt;
        if (age > LENGTH_MS - 1500) setAlpha(Math.max(0f, (LENGTH_MS - age) / 1500f));   // fade out at the end
        for (int i = 0; i < N; i++) {
            vy[i] += g * dt * (vy[i] > 0 ? 0.12f : 1f);   // paper floats once it falls
            vx[i] -= vx[i] * drag * dt;
            if (vy[i] > 0) vy[i] = Math.min(vy[i], 70 * density);
            x[i] += (vx[i] + (float) Math.sin(flip[i] * 0.7f) * 30 * density) * dt;
            y[i] += vy[i] * dt;
            angle[i] += spin[i] * dt;
            flip[i] += dt * 6;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        for (int i = 0; i < N; i++) {
            if (y[i] < -20 * density || y[i] > getHeight() + 20 * density) continue;
            // cos(flip) squashes the piece as it turns over in the air.
            float sx = Math.abs((float) Math.cos(flip[i]));
            paint.setColor(color[i]);
            c.save();
            c.translate(x[i], y[i]);
            c.rotate(angle[i]);
            c.scale(Math.max(0.15f, sx), 1f);
            c.drawRect(-w[i] / 2, -h[i] / 2, w[i] / 2, h[i] / 2, paint);
            c.restore();
        }
    }
}
