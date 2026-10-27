package dev.drift.homelauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;

/** Viste della schermata Musica, disegnate a mano per restare leggere. */
final class MusicViews {
    private MusicViews() {
    }

    /** Copertina arrotondata (centercrop); senza immagine mostra un gradiente con una nota. */
    static final class Cover extends View {
        private final float radius;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Bitmap bmp;

        Cover(Context c, float radius) {
            super(c);
            this.radius = radius;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        /** Descrizione della copertina; null la rende decorativa (il lettore di schermo la salta). */
        void describe(String d) {
            setContentDescription(d);
            setImportantForAccessibility(d == null ? View.IMPORTANT_FOR_ACCESSIBILITY_NO : View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        void set(Bitmap b) {
            bmp = b;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            draw(canvas, bmp, getWidth(), getHeight(), radius, p);
        }

        static void draw(Canvas canvas, Bitmap bmp, int w, int h, float radius, Paint p) {
            if (bmp != null) {
                BitmapShader sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                float sc = Math.max(w / (float) bmp.getWidth(), h / (float) bmp.getHeight());
                Matrix m = new Matrix();
                m.setScale(sc, sc);
                m.postTranslate((w - bmp.getWidth() * sc) / 2f, (h - bmp.getHeight() * sc) / 2f);
                sh.setLocalMatrix(m);
                p.setShader(sh);
                canvas.drawRoundRect(0, 0, w, h, radius, radius, p);
                p.setShader(null);
            } else {
                p.setShader(new LinearGradient(0, 0, w, h, 0xFF5E5CE6, 0xFF1D2B64, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(0, 0, w, h, radius, radius, p);
                p.setShader(null);
                p.setColor(0xCCFFFFFF);
                float s = Math.min(w, h) * 0.3f;
                canvas.save();
                canvas.translate(w / 2f, h / 2f);
                canvas.scale(s, s);
                canvas.drawOval(-0.9f, 0.2f, -0.1f, 0.9f, p);
                canvas.drawRect(-0.2f, -0.9f, 0.05f, 0.6f, p);
                Path f = new Path();
                f.moveTo(0.05f, -0.9f);
                f.cubicTo(0.8f, -0.7f, 1.0f, -0.2f, 0.85f, 0.2f);
                f.cubicTo(0.7f, -0.3f, 0.4f, -0.4f, 0.05f, -0.4f);
                f.close();
                canvas.drawPath(f, p);
                canvas.restore();
                p.setColor(0xFF000000);
            }
        }
    }

    /** Barra di avanzamento: al focus si ingrossa e mostra un pallino (sinistra/destra = cerca). Esposta come cursore. */
    static final class Progress extends View {
        /** dir = -1 indietro, +1 avanti: chiamato anche dalle azioni di accessibilita'. */
        interface Seeker {
            void seek(int dir);
        }

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float frac;
        private Seeker seeker;

        Progress(Context c) {
            super(c);
            setFocusable(true);
            setContentDescription(c.getString(R.string.a11y_progress));
            setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.SeekBar");
                    info.setRangeInfo(android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(
                            android.view.accessibility.AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0, 100, frac * 100));
                    if (seeker != null) {
                        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
                    }
                }

                @Override
                public boolean performAccessibilityAction(View host, int action, android.os.Bundle args) {
                    if (seeker != null) {
                        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                            seeker.seek(1);
                            return true;
                        }
                        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                            seeker.seek(-1);
                            return true;
                        }
                    }
                    return super.performAccessibilityAction(host, action, args);
                }
            });
        }

        void setSeeker(Seeker s) {
            seeker = s;
        }

        /** Valore parlato: "Avanzamento: 13 secondi di 29 secondi" (o la percentuale se la durata non si conosce). */
        void set(float f, String spokenCur, String spokenTotal) {
            frac = Math.max(0f, Math.min(1f, f));
            Context c = getContext();
            setContentDescription(spokenTotal == null || spokenTotal.isEmpty()
                    ? c.getString(R.string.a11y_progress_percent, Math.round(frac * 100))
                    : c.getString(R.string.a11y_progress_value, spokenCur, spokenTotal));
            invalidate();
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
            super.onFocusChanged(gained, direction, previouslyFocused);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            boolean f = isFocused();
            float th = f ? h * 0.5f : h * 0.3f;
            float cy = h / 2f;
            p.setColor(0x33FFFFFF);
            canvas.drawRoundRect(0, cy - th / 2f, w, cy + th / 2f, th / 2f, th / 2f, p);
            float x = Math.max(th, w * frac);
            p.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(0, cy - th / 2f, x, cy + th / 2f, th / 2f, th / 2f, p);
            if (f) canvas.drawCircle(Math.min(w - h / 2f, Math.max(h / 2f, w * frac)), cy, h * 0.46f, p);
        }
    }

    /** Sfondo a schermo intero: copertina molto sfocata con dissolvenza fra una e l'altra, piu' un velo scuro. */
    static final class Bg extends View {
        private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect dst = new Rect();
        private Bitmap cur, next;
        private float mix;
        private int dim = 0x8C000000;
        private android.animation.ValueAnimator anim;

        /** Con lo sfondo ambientale della home niente scurimento: deve sembrare la home, non una sua copia piu' cupa. */
        void setDimmed(boolean on) {
            dim = on ? 0x8C000000 : 0;
            invalidate();
        }

        Bg(Context c, Bitmap initial) {
            super(c);
            cur = initial;
        }

        void set(Bitmap b) {
            if (b == next || (next == null && b == cur)) return;
            if (anim != null) anim.cancel();
            if (next != null && mix > 0.5f) cur = next;
            next = b;
            mix = 0f;
            anim = android.animation.ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(450);
            anim.addUpdateListener(a -> {
                mix = (Float) a.getAnimatedValue();
                if (mix >= 1f) {
                    cur = next;
                    next = null;
                    mix = 0f;
                }
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            dst.set(0, 0, getWidth(), getHeight());
            canvas.drawColor(0xFF08080C);
            blit.setAlpha(255);
            if (cur != null) canvas.drawBitmap(cur, null, dst, blit);
            if (next != null) {
                blit.setAlpha(Math.round(mix * 255));
                canvas.drawBitmap(next, null, dst, blit);
            }
            if (dim != 0) canvas.drawColor(dim);
        }
    }

    /** Pulsante tondo di controllo: 0 indietro, 1 play/pausa, 2 avanti. */
    static final class Ctl extends View {
        final int type;
        private boolean playing;
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);

        Ctl(Context c, int type) {
            super(c);
            this.type = type;
            setFocusable(true);
            setClickable(true);
            describe();
        }

        private void describe() {
            int id = type == 0 ? R.string.a11y_prev_track : type == 2 ? R.string.a11y_next_track
                    : type == 3 ? R.string.a11y_rewind : type == 4 ? R.string.a11y_forward
                    : playing ? R.string.a11y_pause : R.string.a11y_play;
            setContentDescription(getContext().getString(id));
        }

        void setPlaying(boolean pl) {
            playing = pl;
            describe();
            invalidate();
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
            super.onFocusChanged(gained, direction, previouslyFocused);
            animate().cancel();
            animate().scaleX(gained ? 1.12f : 1f).scaleY(gained ? 1.12f : 1f).setDuration(120).start();
            invalidate();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            boolean f = isFocused();
            bg.setColor(f ? 0xF2FFFFFF : 0x2EFFFFFF);
            canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, bg);
            g.setColor(f ? 0xFF111111 : 0xF2FFFFFF);
            float u = Math.min(w, h) * 0.2f;
            canvas.save();
            canvas.translate(w / 2f, h / 2f);
            Path p = new Path();
            if (type == 1) {
                if (playing) {
                    canvas.drawRoundRect(-u * 0.95f, -u, -u * 0.25f, u, u * 0.2f, u * 0.2f, g);
                    canvas.drawRoundRect(u * 0.25f, -u, u * 0.95f, u, u * 0.2f, u * 0.2f, g);
                } else {
                    p.moveTo(-u * 0.7f, -u * 1.1f);
                    p.lineTo(u * 1.1f, 0);
                    p.lineTo(-u * 0.7f, u * 1.1f);
                    p.close();
                    canvas.drawPath(p, g);
                }
            } else if (type == 3 || type == 4) {
                // due triangoli: riavvolgi / avanza
                float d = type == 3 ? -1 : 1;
                for (int k = 0; k < 2; k++) {
                    float x0 = d * (k == 0 ? -0.9f : 0.2f) * u * 0.9f;
                    p.reset();
                    p.moveTo(x0 + d * 0.0f, -u * 0.9f);
                    p.lineTo(x0 + d * u * 0.95f, 0);
                    p.lineTo(x0, u * 0.9f);
                    p.close();
                    canvas.drawPath(p, g);
                }
            } else {
                float d = type == 0 ? -1 : 1;
                // barra + triangolo
                canvas.drawRoundRect(d * u * 0.95f - u * 0.18f, -u, d * u * 0.95f + u * 0.18f, u, u * 0.1f, u * 0.1f, g);
                p.moveTo(-d * u * 0.9f, -u);
                p.lineTo(d * u * 0.65f, 0);
                p.lineTo(-d * u * 0.9f, u);
                p.close();
                canvas.drawPath(p, g);
            }
            canvas.restore();
        }
    }
}
