package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/**
 * The weather line's icon, always moving a little (PersonalHome weather_places): the sun turns, rain and snow fall
 * from the cloud, clouds and fog drift, lightning blinks, wind gusts. It draws the outline icons (ic_wx_*) and adds
 * the moving parts in code; WeatherLine redraws it about 20 times a second while Home is on screen.
 */
final class WeatherIcon extends Drawable {
    private final int icon;
    private final Drawable base;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private int alpha = 0xD9;

    WeatherIcon(Context ctx, int icon) {
        this.icon = icon;
        boolean falls = icon == R.drawable.ic_wx_rain || icon == R.drawable.ic_wx_snow;
        // Rain and snow draw only the cloud, with their own falling drops or flakes under it.
        base = ctx.getDrawable(falls ? R.drawable.ic_wx_cloud : icon).mutate();
        size = base.getIntrinsicWidth();
        paint.setColor(0xFFFFFFFF);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float u = b.width() / 24f, cx = b.exactCenterX(), cy = b.exactCenterY();
        float t = (SystemClock.uptimeMillis() % 600_000L) / 1000f;
        base.setBounds(b);
        base.setAlpha(alpha);
        c.save();
        if (icon == R.drawable.ic_wx_sun) {
            c.rotate(t * 25f, cx, cy);                                     // the rays turn
            base.draw(c);
        } else if (icon == R.drawable.ic_wx_moon) {
            base.draw(c);
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 2.4f);           // a star twinkles beside it
            paint.setAlpha((int) (alpha * tw));
            c.drawCircle(b.left + 20 * u, b.top + 5 * u, 1.1f * u, paint);
        } else if (icon == R.drawable.ic_wx_rain || icon == R.drawable.ic_wx_snow) {
            c.translate(0, -3 * u);
            c.scale(0.9f, 0.9f, cx, b.top);
            base.draw(c);
            c.restore();
            c.save();
            boolean snow = icon == R.drawable.ic_wx_snow;
            paint.setStrokeWidth(1.6f * u);
            for (int k = 0; k < 3; k++) {
                float p = ((t * (snow ? 0.6f : 1.6f)) + k / 3f) % 1f;      // 0..1 down the fall
                float x = b.left + (8 + k * 4) * u, y = b.top + (14 + p * 9) * u;
                paint.setAlpha((int) (alpha * Math.sin(Math.PI * p)));
                if (snow) {
                    c.drawCircle(x + (float) Math.sin((t + k) * 3) * u, y, 1.1f * u, paint);
                } else {
                    c.drawLine(x, y, x - 0.8f * u, y + 2.4f * u, paint);
                }
            }
        } else if (icon == R.drawable.ic_wx_storm) {
            float f = t % 2.4f;                                             // a quick double blink every 2.4 s
            base.setAlpha(f < 0.1f || (f > 0.2f && f < 0.28f) ? 255 : (int) (alpha * 0.75f));
            base.draw(c);
        } else if (icon == R.drawable.ic_wx_wind) {
            c.translate((float) Math.sin(t * 2.2f) * 1.6f * u, 0);         // gusts
            base.draw(c);
        } else {
            // Cloud, partly cloudy and fog drift gently from side to side.
            float amp = icon == R.drawable.ic_wx_fog ? 1.8f : 1.2f;
            c.translate((float) Math.sin(t * 0.9f) * amp * u, 0);
            base.draw(c);
        }
        c.restore();
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        base.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
