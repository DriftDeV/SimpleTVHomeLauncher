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

    /** Copertina arrotondata (centercrop); senza immagine mostra un gradiente con una nota (o le iniziali). */
    static final class Cover extends View {
        private float radius;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Bitmap bmp;
        private String initials;

        Cover(Context c, float radius) {
            super(c);
            this.radius = radius;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        /** Tonda (artisti) e, senza foto, con le iniziali del nome al posto della nota. */
        void setPerson(String name) {
            radius = -1;
            initials = name;
            invalidate();
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
            int w = getWidth(), h = getHeight();
            if (initials != null && bmp == null) person(canvas, initials, w, h, p);
            else draw(canvas, bmp, w, h, radius < 0 ? Math.min(w, h) / 2f : radius, p);
        }

        /** Cerchio con le iniziali su un colore ricavato dal nome: lo stesso artista ha sempre lo stesso colore. */
        static void person(Canvas canvas, String name, int w, int h, Paint p) {
            float hue = (name.hashCode() & 0x7fffffff) % 360;
            int c1 = android.graphics.Color.HSVToColor(new float[]{hue, 0.55f, 0.62f});
            int c2 = android.graphics.Color.HSVToColor(new float[]{(hue + 40) % 360, 0.65f, 0.30f});
            p.setShader(new LinearGradient(0, 0, w, h, c1, c2, Shader.TileMode.CLAMP));
            float r = Math.min(w, h) / 2f;
            canvas.drawCircle(w / 2f, h / 2f, r, p);
            p.setShader(null);
            StringBuilder ini = new StringBuilder();
            for (String part : name.trim().split("\\s+")) {
                if (!part.isEmpty() && ini.length() < 2) ini.appendCodePoint(Character.toUpperCase(part.codePointAt(0)));
            }
            p.setColor(0xF2FFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            p.setTextSize(r * 0.78f);
            Paint.FontMetrics fm = p.getFontMetrics();
            canvas.drawText(ini.toString(), w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(0xFF000000);
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
                    info.setContentDescription(spoken());
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

        private String cur = "", total = "";

        /**
         * Valore parlato: "Avanzamento: 13 secondi di 29 secondi" (o la percentuale se la durata non si conosce).
         * Calcolato solo quando il lettore di schermo lo chiede: cambiare la descrizione a ogni scatto manderebbe
         * un evento di accessibilita' ogni mezzo secondo (TalkBack rallenta e l'interfaccia non e' mai "ferma").
         */
        void set(float f, String spokenCur, String spokenTotal) {
            float nf = Math.max(0f, Math.min(1f, f));
            cur = spokenCur == null ? "" : spokenCur;
            total = spokenTotal == null ? "" : spokenTotal;
            if (getWidth() > 0 && Math.abs(nf - frac) * getWidth() < 0.5f) return; // a schermo non cambia nulla
            frac = nf;
            invalidate();
        }

        private String spoken() {
            Context c = getContext();
            return total.isEmpty() ? c.getString(R.string.a11y_progress_percent, Math.round(frac * 100))
                    : c.getString(R.string.a11y_progress_value, cur, total);
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

    /** Pulsante tondo di controllo: 0 indietro, 1 play/pausa, 2 avanti, 3/4 -10 s/+10 s, 5 informazioni. */
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
                    : type == 5 ? R.string.a11y_track_info
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
            if (type == 5) {
                // "i": puntino e asta arrotondata
                canvas.drawCircle(0, -u * 0.85f, u * 0.2f, g);
                canvas.drawRoundRect(-u * 0.16f, -u * 0.4f, u * 0.16f, u * 1.0f, u * 0.16f, u * 0.16f, g);
            } else if (type == 1) {
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

    /** Tre barrette: il brano che sta suonando (disegnate, niente glifi di testo). */
    static void eq(Canvas canvas, float cx, float cy, float s, Paint p) {
        float bw = s * 0.22f, gap = s * 0.17f, r = bw / 2f;
        float[] hs = {0.55f, 1f, 0.75f};
        float x = cx - (3 * bw + 2 * gap) / 2f;
        for (float hh : hs) {
            float top = cy + s / 2f - s * hh;
            canvas.drawRoundRect(x, top, x + bw, cy + s / 2f, r, r, p);
            x += bw + gap;
        }
    }

    /**
     * Riquadro della libreria musicale: copertina quadrata (o tonda per gli artisti) con titolo e sottotitolo sotto.
     * L'immagine si carica solo quando il riquadro e' vicino allo schermo e si libera quando si allontana,
     * cosi' una libreria grande non riempie la memoria della TV.
     */
    static final class Tile extends View {
        final MusicItem item;
        private final int size;
        private final float u;
        private final boolean round;
        private final String url;
        private Bitmap img;
        private boolean wanted, loading, current;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.text.TextPaint tTitle = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final android.text.TextPaint tSub = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final String sub;

        Tile(Context c, MusicItem item, String sub, String url, int size, float u) {
            super(c);
            this.item = item;
            this.sub = sub == null ? "" : sub;
            this.url = url;
            this.size = size;
            this.u = u;
            this.round = item.kind == MusicItem.ARTIST;
            float ts = A11y.textScale(c);
            tTitle.setColor(0xF2FFFFFF);
            tTitle.setTextSize(17 * u * ts);
            tTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            tSub.setColor(0xB3FFFFFF);
            tSub.setTextSize(15 * u * ts);
            tSub.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            if (round) {
                tTitle.setTextAlign(Paint.Align.CENTER);
                tSub.setTextAlign(Paint.Align.CENTER);
            }
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Math.max(2f, 3 * u));
            ring.setColor(0xFFFFFFFF);
            setFocusable(true);
            setClickable(true);
            setPivotX(size / 2f);
            setPivotY(size / 2f); // cresce attorno alla copertina, il testo scende appena
        }

        /** Altezza totale: copertina + due righe di testo. */
        static int height(int size, float u, Context c) {
            return size + Math.round((12 + 44 * A11y.textScale(c)) * u);
        }

        @Override
        protected void onMeasure(int a, int b) {
            setMeasuredDimension(size, height(size, u, getContext()));
        }

        /** Vicino alla parte visibile: carica la copertina; lontano: la lascia andare. */
        void near(boolean on) {
            wanted = on;
            if (!on) {
                if (img != null) {
                    img = null;
                    invalidate();
                }
                return;
            }
            if (img != null || loading || url == null) return;
            loading = true;
            Img.load(url, b -> {
                loading = false;
                if (wanted && b != null) {
                    img = b;
                    invalidate();
                }
            });
        }

        Bitmap image() {
            return img;
        }

        /** Il brano di questo riquadro e' quello che suona. */
        void setCurrent(boolean c) {
            if (current == c) return;
            current = c;
            invalidate();
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
            super.onFocusChanged(gained, direction, previouslyFocused);
            animate().cancel();
            animate().scaleX(gained ? 1.08f : 1f).scaleY(gained ? 1.08f : 1f).setDuration(140).start();
            invalidate();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float rad = round ? size / 2f : 10 * u;
            if (round && img == null) Cover.person(canvas, item.name, size, size, p);
            else Cover.draw(canvas, img, size, size, rad, p);
            if (current) {
                float r = 17 * u, cx = size - 10 * u - r, cy = size - 10 * u - r;
                p.setColor(0xD9000000);
                canvas.drawCircle(cx, cy, r, p);
                p.setColor(0xFFFFFFFF);
                eq(canvas, cx, cy, 15 * u, p);
            }
            if (isPressed()) {
                p.setColor(0x55000000);
                canvas.drawRoundRect(0, 0, size, size, rad, rad, p);
            }
            if (isFocused()) {
                float i = ring.getStrokeWidth() / 2f;
                canvas.drawRoundRect(i, i, size - i, size - i, rad, rad, ring);
            }
            p.setColor(0xFF000000);
            // testo: una riga ciascuno, tagliato con i puntini
            float x = round ? size / 2f : 0;
            Paint.FontMetrics fm = tTitle.getFontMetrics();
            float y = size + 12 * u - fm.ascent;
            canvas.drawText(TextUtils.ellipsize(item.name, tTitle, size, TextUtils.TruncateAt.END).toString(), x, y, tTitle);
            if (!sub.isEmpty()) {
                y += fm.descent - tSub.getFontMetrics().ascent + 3 * u;
                canvas.drawText(TextUtils.ellipsize(sub, tSub, size, TextUtils.TruncateAt.END).toString(), x, y, tSub);
            }
        }
    }

    /** Riga di una traccia nella scheda album: numero (o le barrette se suona), titolo, durata. Piena al focus. */
    static final class TrackRow extends View {
        final MusicItem item;
        private final float u;
        private final String num, dur, extra;
        private boolean current;
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.text.TextPaint tNum = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final android.text.TextPaint tTitle = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);

        /** extra: l'artista, solo se diverso da quello dell'album (compilation, ospiti). */
        TrackRow(Context c, MusicItem item, int number, String extra, float u) {
            super(c);
            this.item = item;
            this.u = u;
            this.num = String.valueOf(number);
            this.dur = MusicItem.clock(item.durationMs());
            this.extra = extra == null ? "" : extra;
            float ts = A11y.textScale(c);
            tNum.setTextSize(18 * u * ts);
            tNum.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            tTitle.setTextSize(20 * u * ts);
            tTitle.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            setFocusable(true);
            setClickable(true);
        }

        void setCurrent(boolean c) {
            if (current == c) return;
            current = c;
            invalidate();
        }

        boolean isCurrent() {
            return current;
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
            super.onFocusChanged(gained, direction, previouslyFocused);
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
            if (f || isPressed()) {
                bg.setColor(isPressed() ? 0xCCFFFFFF : 0xF2FFFFFF);
                canvas.drawRoundRect(0, 0, w, h, 12 * u, 12 * u, bg);
            }
            int main = f ? 0xFF111111 : 0xFFFFFFFF, soft = f ? 0xA6111111 : 0x99FFFFFF;
            float cy = h / 2f;
            if (current) {
                bg.setColor(main);
                eq(canvas, 36 * u, cy, 16 * u, bg);
            } else {
                tNum.setColor(soft);
                tNum.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(num, 36 * u, cy - (tNum.descent() + tNum.ascent()) / 2f, tNum);
            }
            tNum.setTextAlign(Paint.Align.RIGHT);
            tNum.setColor(soft);
            float durW = dur.isEmpty() ? 0 : tNum.measureText(dur);
            canvas.drawText(dur, w - 22 * u, cy - (tNum.descent() + tNum.ascent()) / 2f, tNum);
            float x = 72 * u, room = w - x - durW - 48 * u;
            tTitle.setColor(main);
            String t = TextUtils.ellipsize(item.name, tTitle, room, TextUtils.TruncateAt.END).toString();
            float ty = cy - (tTitle.descent() + tTitle.ascent()) / 2f;
            canvas.drawText(t, x, ty, tTitle);
            float used = tTitle.measureText(t);
            if (!extra.isEmpty() && used + 40 * u < room) {
                tTitle.setColor(soft);
                String e = TextUtils.ellipsize("  ·  " + extra, tTitle, room - used, TextUtils.TruncateAt.END).toString();
                canvas.drawText(e, x + used, ty, tTitle);
            }
        }
    }
}
