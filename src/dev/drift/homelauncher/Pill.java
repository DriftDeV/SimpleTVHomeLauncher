package dev.drift.homelauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Pulsante a capsula: traslucido a riposo, bianco pieno al focus. Il focus ha anche un anello bianco staccato dal
 * riempimento (non dipende dal livello di trasparenza); la tab attiva ha un puntino (non solo un riempimento piu' chiaro).
 */
final class Pill extends TextView {
    private final GradientDrawable bg = new GradientDrawable();
    private final GradientDrawable ring = new GradientDrawable();
    private final LayerDrawable layers;
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float u;
    private final int rest;
    private boolean active, primary;

    Pill(Context c, CharSequence text, float u) {
        super(c);
        this.u = u;
        rest = A11y.restFill(c);
        setText(text);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * u * A11y.textScale(c));
        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setGravity(Gravity.CENTER);
        setPadding(Math.round(24 * u), 0, Math.round(24 * u), 0);
        bg.setCornerRadius(100 * u);
        ring.setCornerRadius(100 * u);
        ring.setColor(0);
        layers = new LayerDrawable(new android.graphics.drawable.Drawable[]{ring, bg});
        setBackground(layers);
        setFocusable(true);
        setClickable(true);
        paint(false);
    }

    /** Tab selezionata: riempimento piu' chiaro anche senza focus. */
    void setActive(boolean a) {
        active = a;
        setSelected(a); // il lettore di schermo dice quale tab e' quella corrente
        paint(isFocused());
    }

    /** Azione principale della schermata: piena e chiara anche senza focus, cosi' si distingue dalle altre. */
    void setPrimary(boolean p) {
        primary = p;
        paint(isFocused());
    }

    private void paint(boolean focused) {
        bg.setColor(focused ? 0xF2FFFFFF : primary ? 0xD9FFFFFF : active ? 0x66FFFFFF : rest);
        setTextColor(focused || primary ? 0xFF111111 : 0xFFFFFFFF);
        // anello + vano scuro: il riempimento si rimpicciolisce al focus, cosi' il focus si vede anche sulla primaria bianca
        int in = focused ? Math.round(5 * u) : 0;
        layers.setLayerInset(1, in, in, in, in);
        ring.setStroke(focused ? Math.round(2.5f * u) : 0, 0xFFFFFFFF);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (active) {
            mark.setColor(isFocused() ? 0xFF111111 : 0xFFFFFFFF);
            canvas.drawCircle(getWidth() / 2f, getHeight() - 9 * u, 2.5f * u, mark);
        }
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
        super.onFocusChanged(gained, direction, previouslyFocused);
        paint(gained);
        animate().cancel();
        animate().scaleX(gained ? 1.06f : 1f).scaleY(gained ? 1.06f : 1f).setDuration(120).start();
    }
}
