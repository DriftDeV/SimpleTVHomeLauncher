package dev.drift.homelauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

/** Viste della tab Film: immagine dell'hero con sfumatura e locandina con focus. */
final class FilmViews {
    private FilmViews() {
    }

    /** Immagine a destra dell'hero, sfumata a sinistra e in basso (maschera in alpha, funziona su qualsiasi sfondo). */
    static final class Hero extends View {
        private Bitmap bmp;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint mask = new Paint();
        private final Matrix m = new Matrix();

        Hero(Context c) {
            super(c);
            mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }

        void set(Bitmap b) {
            bmp = b;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (bmp == null) return;
            int w = getWidth(), h = getHeight();
            int layer = canvas.saveLayer(0, 0, w, h, null);
            float sc = Math.max(w / (float) bmp.getWidth(), h / (float) bmp.getHeight());
            m.setScale(sc, sc);
            m.postTranslate((w - bmp.getWidth() * sc) / 2f, (h - bmp.getHeight() * sc) / 2f);
            canvas.drawBitmap(bmp, m, p);
            // sfuma a sinistra e in basso
            mask.setShader(new LinearGradient(0, 0, w, 0, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, mask);
            mask.setShader(new LinearGradient(0, h * 0.45f, 0, h, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, mask);
            mask.setShader(null);
            canvas.restoreToCount(layer);
        }
    }

    /** Locandina 2:3 con angoli arrotondati, barra di avanzamento e spunta "visto". */
    static final class Poster extends View {
        final Movie movie;
        private Bitmap img;
        private final float u;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private StaticLayout title;
        private int w, h;

        Poster(Context c, Movie m, int w, int h, float u) {
            super(c);
            this.movie = m;
            this.u = u;
            this.w = w;
            this.h = h;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Math.max(2f, 3 * u));
            ring.setColor(0xFFFFFFFF);
            tp.setColor(0xF2FFFFFF);
            tp.setTextSize(17 * u * A11y.textScale(c));
            tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            setFocusable(true);
            setClickable(true);
            setContentDescription(m.name);
        }

        void setImage(Bitmap b) {
            img = b;
            invalidate();
        }

        @Override
        protected void onMeasure(int a, int b) {
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
            super.onFocusChanged(gained, direction, previouslyFocused);
            animate().cancel();
            animate().scaleX(gained ? 1.1f : 1f).scaleY(gained ? 1.1f : 1f).setDuration(140).start();
            invalidate();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float rad = 12 * u;
            if (img != null) {
                MusicViews.Cover.draw(canvas, img, w, h, rad, p);
            } else {
                // senza immagine: gradiente con il titolo
                p.setShader(new LinearGradient(0, 0, w, h, 0xFF3A3F5C, 0xFF1B1E33, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(0, 0, w, h, rad, rad, p);
                p.setShader(null);
                if (title == null) {
                    title = new StaticLayout(movie.name, tp, w - Math.round(24 * u), Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
                }
                canvas.save();
                canvas.translate(12 * u, (h - title.getHeight()) / 2f);
                title.draw(canvas);
                canvas.restore();
            }
            // avanzamento e visto
            double pr = movie.progress();
            if (!movie.played && pr > 0.01) {
                p.setColor(0x99000000);
                canvas.drawRect(0, h - 7 * u, w, h, p);
                p.setColor(0xFFFFFFFF);
                canvas.drawRect(0, h - 7 * u, (float) (w * pr), h, p);
            }
            if (movie.played) {
                float r = 12 * u, cx = w - 10 * u - r, cy = 10 * u + r;
                p.setColor(0xCC000000);
                canvas.drawCircle(cx, cy, r, p);
                p.setColor(0xFFFFFFFF);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.2f * u);
                p.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(cx - r * 0.45f, cy + r * 0.02f, cx - r * 0.1f, cy + r * 0.38f, p);
                canvas.drawLine(cx - r * 0.1f, cy + r * 0.38f, cx + r * 0.5f, cy - r * 0.32f, p);
                p.setStyle(Paint.Style.FILL);
            }
            if (isPressed()) {
                p.setColor(0x55000000);
                canvas.drawRoundRect(0, 0, w, h, rad, rad, p);
            }
            if (isFocused()) {
                float i = ring.getStrokeWidth() / 2f;
                canvas.drawRoundRect(i, i, w - i, h - i, rad, rad, ring);
            }
            p.setColor(0xFF000000);
        }
    }
}
