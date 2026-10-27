package dev.drift.homelauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/** Pulsante tondo con icona disegnata a mano per le azioni della barra alta: 0 sorgenti, 1 rete, 2 impostazioni. */
final class IconButton extends View {
    private final int type;
    private final float u;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int rest;

    IconButton(Context c, int type, String description, float u) {
        super(c);
        this.type = type;
        this.u = u;
        rest = A11y.restFill(c);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeCap(Paint.Cap.ROUND);
        g.setStrokeJoin(Paint.Join.ROUND);
        g.setStrokeWidth(2.6f * u);
        setContentDescription(description);
        setFocusable(true);
        setClickable(true);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Math.round(48 * u), Math.round(48 * u));
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
        float disc = Math.min(w, h) / 2f;
        bg.setStyle(Paint.Style.FILL);
        bg.setColor(rest);
        if (f) {
            // anello bianco staccato dal riempimento: il focus non dipende dalla trasparenza
            bg.setStyle(Paint.Style.STROKE);
            bg.setStrokeWidth(2.5f * u);
            bg.setColor(0xFFFFFFFF);
            canvas.drawCircle(w / 2f, h / 2f, disc - 1.25f * u, bg);
            bg.setStyle(Paint.Style.FILL);
            bg.setColor(0xF2FFFFFF);
            disc -= 5 * u;
        }
        canvas.drawCircle(w / 2f, h / 2f, disc, bg);
        g.setColor(f ? 0xFF111111 : 0xFFFFFFFF);
        canvas.save();
        canvas.translate(w / 2f, h / 2f);
        canvas.scale(u, u);
        g.setStrokeWidth(2.6f);
        if (type == 0) {
            // ingresso: schermo con freccia che entra
            canvas.drawRoundRect(new RectF(-4, -8, 12, 8), 2.5f, 2.5f, g);
            canvas.drawLine(-14, 0, 2, 0, g);
            canvas.drawLine(-2, -4, 2, 0, g);
            canvas.drawLine(-2, 4, 2, 0, g);
        } else if (type == 1) {
            // rete: tre archi e un punto
            for (int r = 1; r <= 3; r++) {
                float rad = r * 6f;
                canvas.drawArc(new RectF(-rad, 8 - rad, rad, 8 + rad), 225, 90, false, g);
            }
            g.setStyle(Paint.Style.FILL);
            canvas.drawCircle(0, 8, 2f, g);
            g.setStyle(Paint.Style.STROKE);
        } else {
            // ingranaggio: cerchio con otto denti
            canvas.drawCircle(0, 0, 5.5f, g);
            for (int i = 0; i < 8; i++) {
                double a = Math.toRadians(i * 45);
                canvas.drawLine((float) Math.cos(a) * 9f, (float) Math.sin(a) * 9f,
                        (float) Math.cos(a) * 12.5f, (float) Math.sin(a) * 12.5f, g);
            }
        }
        canvas.restore();
    }
}
