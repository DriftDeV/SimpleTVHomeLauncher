package dev.drift.homelauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

/** Istantanea sfocata di una view: si scatta a bassa risoluzione, quindi il blur costa pochi ms. */
final class Blur {
    private Blur() {
    }

    static Bitmap snapshot(View v, int div, int radius) {
        int w = Math.max(1, v.getWidth() / div), h = Math.max(1, v.getHeight() / div);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(1f / div, 1f / div);
        v.draw(c);
        blurPixels(b, radius);
        return b;
    }

    /** Copia di src ridotta a w x h (riempie, centrata) e sfocata: sfondo morbido dalla copertina. */
    static Bitmap fromBitmap(Bitmap src, int w, int h, int radius) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float sc = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        c.translate((w - src.getWidth() * sc) / 2f, (h - src.getHeight() * sc) / 2f);
        c.scale(sc, sc);
        c.drawBitmap(src, 0, 0, new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        blurPixels(b, radius);
        return b;
    }

    private static void blurPixels(Bitmap b, int radius) {
        int w = b.getWidth(), h = b.getHeight();
        int[] a = new int[w * h], t = new int[w * h];
        b.getPixels(a, 0, w, 0, 0, w, h);
        for (int i = 0; i < 3; i++) {
            box(a, t, w, h, radius, true);
            box(t, a, w, h, radius, false);
        }
        b.setPixels(a, 0, w, 0, 0, w, h);
    }

    /** Box blur con somma mobile, un canale alla volta, bordi ripetuti. */
    private static void box(int[] src, int[] dst, int w, int h, int r, boolean horiz) {
        int lines = horiz ? h : w, len = horiz ? w : h, step = horiz ? 1 : w, lineStep = horiz ? w : 1;
        int win = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int base = l * lineStep;
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = src[base + Math.min(len - 1, Math.max(0, i)) * step];
                sa += p >>> 24;
                sr += (p >> 16) & 255;
                sg += (p >> 8) & 255;
                sb += p & 255;
            }
            for (int i = 0; i < len; i++) {
                dst[base + i * step] = ((sa / win) << 24) | ((sr / win) << 16) | ((sg / win) << 8) | (sb / win);
                int add = src[base + Math.min(len - 1, i + r + 1) * step];
                int rem = src[base + Math.max(0, i - r) * step];
                sa += (add >>> 24) - (rem >>> 24);
                sr += ((add >> 16) & 255) - ((rem >> 16) & 255);
                sg += ((add >> 8) & 255) - ((rem >> 8) & 255);
                sb += (add & 255) - (rem & 255);
            }
        }
    }
}
