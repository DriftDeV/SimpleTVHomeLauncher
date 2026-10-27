package dev.drift.homelauncher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

final class AppEntry {
    final String pkg;
    final CharSequence label;
    final Intent intent;
    final Drawable banner;
    final Drawable icon;
    final String version;
    final int color;

    AppEntry(String pkg, CharSequence label, Intent intent, Drawable banner, Drawable icon, String version) {
        this.pkg = pkg;
        this.label = label;
        this.intent = intent;
        this.banner = banner;
        this.icon = icon;
        this.version = version;
        this.color = dominant(banner != null ? banner : icon);
    }

    /** Colore medio pesato sulla saturazione, usato per tingere lo sfondo. */
    private static int dominant(Drawable d) {
        final int neutral = 0xFF3A3F5C;
        if (d == null) return neutral;
        try {
            Bitmap b = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Rect old = d.copyBounds();
            d.setBounds(0, 0, 16, 9);
            d.draw(c);
            d.setBounds(old);
            float[] hsv = new float[3];
            double sx = 0, sy = 0, ss = 0, sv = 0, sw = 0;
            for (int y = 0; y < 9; y++) {
                for (int x = 0; x < 16; x++) {
                    int p = b.getPixel(x, y);
                    float a = Color.alpha(p) / 255f;
                    Color.colorToHSV(p, hsv);
                    float w = a * hsv[1] * hsv[2];
                    double rad = Math.toRadians(hsv[0]);
                    sx += Math.cos(rad) * w;
                    sy += Math.sin(rad) * w;
                    ss += hsv[1] * w;
                    sv += hsv[2] * w;
                    sw += w;
                }
            }
            b.recycle();
            if (sw < 1.5) return neutral;
            double hue = Math.toDegrees(Math.atan2(sy, sx));
            if (hue < 0) hue += 360;
            float s = (float) Math.min(0.75, ss / sw);
            // V <= 0,68 e' portante per il contrasto della barra alta (vedi lo scrim in HomeActivity)
            float v = (float) Math.max(0.45, Math.min(0.68, sv / sw));
            return Color.HSVToColor(new float[]{(float) hue, s, v});
        } catch (Throwable t) {
            return neutral;
        }
    }
}
