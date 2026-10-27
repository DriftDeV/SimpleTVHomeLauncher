package dev.drift.homelauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

/**
 * Sfondo scuro con due aloni tinti sul colore dell'app a fuoco.
 * Gli aloni sono morbidi: si renderizzano una volta per colore in una bitmap minuscola
 * e il cambio colore e' un crossfade tra due bitmap (solo alpha, nessun ricalcolo per frame).
 */
final class BgView extends View {
    private static final int BW = 128, BH = 72;
    private Bitmap cur = make(0xFF3A3F5C);
    private Bitmap next;
    private float mix;
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect dst = new Rect();
    private ValueAnimator anim;
    private int lastTarget = 0xFF3A3F5C;

    BgView(Context c) {
        super(c);
    }

    private static int alpha(int c, int a) {
        return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c));
    }

    private static Bitmap make(int tint) {
        Bitmap b = Bitmap.createBitmap(BW, BH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        c.drawColor(0xFF08080C);
        p.setShader(new RadialGradient(BW * 0.8f, -BH * 0.1f, BW * 0.85f,
                alpha(tint, 0xB0), alpha(tint, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, BW, BH, p);
        p.setShader(new RadialGradient(BW * 0.05f, BH * 1.1f, BW * 0.7f,
                alpha(tint, 0x50), alpha(tint, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, BW, BH, p);
        return b;
    }

    /** Lo sfondo ambientale come lo si vede ora (alone gia' mescolato): lo riusano i pannelli che non hanno un'immagine propria. */
    Bitmap snapshot() {
        return next != null && mix > 0.5f ? next : cur;
    }

    void setTint(int target) {
        if (target == lastTarget) return;
        lastTarget = target;
        if (anim != null) anim.cancel();
        // se un fade era in corso, parti da cio' che si vede ora
        if (next != null) {
            cur = mix > 0.5f ? next : cur;
        }
        next = make(target);
        mix = 0f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(380);
        anim.addUpdateListener(a -> {
            mix = (Float) a.getAnimatedValue();
            invalidate();
            if (mix >= 1f) {
                cur = next;
                next = null;
                mix = 0f;
            }
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        dst.set(0, 0, getWidth(), getHeight());
        blit.setAlpha(255);
        canvas.drawBitmap(cur, null, dst, blit);
        if (next != null && mix > 0f) {
            blit.setAlpha(Math.round(mix * 255));
            canvas.drawBitmap(next, null, dst, blit);
        }
    }
}
