package dev.drift.homelauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;

/** Avatar circolare con iniziale e nome: capsula traslucida, bianca al focus. */
final class UserChip extends View {
    private final float u;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint initial = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int rest;
    private String nameText = "";
    private String initialText = "";
    private Bitmap photo;
    private final Paint photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    UserChip(Context c, float u) {
        super(c);
        this.u = u;
        rest = A11y.restFill(c);
        initial.setColor(0xFFFFFFFF);
        initial.setTextAlign(Paint.Align.CENTER);
        initial.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        initial.setTextSize(20 * u);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setTextSize(20 * u * A11y.textScale(c));
        setFocusable(true);
        setClickable(true);
    }

    void setPhoto(Bitmap b) {
        photo = b;
        if (b != null) photoPaint.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        invalidate();
    }

    void set(String n, int color) {
        nameText = n;
        initialText = n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase();
        dot.setColor(color);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = Math.round(8 * u + 36 * u + 10 * u + name.measureText(nameText) + 20 * u);
        setMeasuredDimension(w, Math.round(48 * u));
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
        super.onFocusChanged(gained, direction, previouslyFocused);
        animate().cancel();
        animate().scaleX(gained ? 1.06f : 1f).scaleY(gained ? 1.06f : 1f).setDuration(120).start();
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
        float in = 0;
        bg.setStyle(Paint.Style.FILL);
        if (f) {
            // anello bianco staccato dal riempimento: il focus non dipende dalla trasparenza
            bg.setStyle(Paint.Style.STROKE);
            bg.setStrokeWidth(2.5f * u);
            bg.setColor(0xFFFFFFFF);
            float i = 1.25f * u;
            canvas.drawRoundRect(i, i, w - i, h - i, h / 2f - i, h / 2f - i, bg);
            bg.setStyle(Paint.Style.FILL);
            in = 5 * u;
        }
        bg.setColor(f ? 0xF2FFFFFF : rest);
        canvas.drawRoundRect(in, in, w - in, h - in, h / 2f - in, h / 2f - in, bg);
        float cx = 8 * u + 18 * u, cy = h / 2f;
        if (photo != null) {
            float d = 36 * u;
            Matrix m = new Matrix();
            m.setScale(d / photo.getWidth(), d / photo.getHeight());
            m.postTranslate(cx - d / 2f, cy - d / 2f);
            photoPaint.getShader().setLocalMatrix(m);
            canvas.drawCircle(cx, cy, 18 * u, photoPaint);
        } else {
            canvas.drawCircle(cx, cy, 18 * u, dot);
            Paint.FontMetrics fm = initial.getFontMetrics();
            canvas.drawText(initialText, cx, cy - (fm.ascent + fm.descent) / 2f, initial);
        }
        name.setColor(f ? 0xFF111111 : 0xFFFFFFFF);
        Paint.FontMetrics nm = name.getFontMetrics();
        canvas.drawText(nameText, 8 * u + 36 * u + 10 * u, cy - (nm.ascent + nm.descent) / 2f, name);
    }
}
