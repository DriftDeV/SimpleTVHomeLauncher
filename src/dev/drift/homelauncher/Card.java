package dev.drift.homelauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Card arrotondata con scala/ombra al focus, disegnata a mano per restare leggera. */
final class Card extends View {
    private static final java.util.Map<Long, Bitmap> SHADOWS = new java.util.HashMap<>();
    private final float margin;
    private Bitmap shadow;
    private final Paint shadowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    final AppEntry entry;
    private final float radius;
    private final float focusScale;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float ringWidth;

    Card(Context c, AppEntry e, int w, int h, float focusScale) {
        super(c);
        this.entry = e;
        this.radius = h * 0.12f;
        this.focusScale = focusScale;
        this.ringWidth = Math.max(2f, h * 0.018f);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(ringWidth);
        ring.setColor(0xE6FFFFFF);
        setFocusable(true);
        setClickable(true);
        this.margin = h * 0.16f;
        setWillNotDraw(false);
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
        super.onFocusChanged(gained, direction, previouslyFocused);
        animate().cancel();
        animate().scaleX(gained ? focusScale : 1f).scaleY(gained ? focusScale : 1f)
                .setDuration(140).setInterpolator(new DecelerateInterpolator()).start();
        invalidate();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        Bitmap content = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(content);
        Drawable b = entry.banner;
        if (b != null) {
            c.drawColor(0xFF15151B);
            b.setBounds(0, 0, w, h);
            b.draw(c);
        } else {
            float[] hsv = new float[3];
            Color.colorToHSV(entry.color, hsv);
            hsv[2] = Math.max(0.25f, hsv[2] * 0.55f);
            int dark = Color.HSVToColor(hsv);
            hsv[2] = Math.min(1f, hsv[2] * 1.5f);
            int light = Color.HSVToColor(hsv);
            paint.setShader(new LinearGradient(0, 0, w, h, light, dark, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            Drawable ic = entry.icon;
            if (ic != null) {
                int s = Math.round(h * 0.55f);
                int l = (w - s) / 2, t = (h - s) / 2;
                ic.setBounds(l, t, l + s, t + s);
                ic.draw(c);
            }
        }
        shadow = shadowFor(w, h);
        paint.setShader(new BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
    }

    /** Ombra morbida pre-renderizzata, una per dimensione: costa un solo quad per frame. */
    private Bitmap shadowFor(int w, int h) {
        long key = ((long) w << 32) | h;
        Bitmap b = SHADOWS.get(key);
        if (b == null) {
            int m = Math.round(margin);
            b = Bitmap.createBitmap(w + 2 * m, h + 2 * m, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF000000);
            p.setMaskFilter(new android.graphics.BlurMaskFilter(margin * 0.5f, android.graphics.BlurMaskFilter.Blur.NORMAL));
            c.drawRoundRect(m, m + margin * 0.25f, m + w, m + h + margin * 0.25f, radius, radius, p);
            SHADOWS.put(key, b);
        }
        return b;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (shadow != null) {
            shadowPaint.setAlpha(isFocused() ? 150 : 90);
            canvas.drawBitmap(shadow, -margin, -margin, shadowPaint);
        }
        canvas.drawRoundRect(0, 0, w, h, radius, radius, paint);
        if (isPressed()) {
            shade.setColor(0x55000000);
            canvas.drawRoundRect(0, 0, w, h, radius, radius, shade);
        }
        if (isFocused()) {
            float i = ringWidth / 2f;
            canvas.drawRoundRect(i, i, w - i, h - i, radius - i, radius - i, ring);
        }
    }
}
