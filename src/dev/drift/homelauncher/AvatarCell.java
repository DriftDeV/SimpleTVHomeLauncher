package dev.drift.homelauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/** Cella tonda del selettore avatar: ingrandita e cerchiata di bianco al focus. */
final class AvatarCell extends View {
    private final Bitmap art;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect dst = new Rect();
    private final boolean current;

    AvatarCell(Context c, int idx, int sizePx, boolean current) {
        super(c);
        this.art = AvatarArt.bitmap(idx, sizePx);
        this.current = current;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(2f, sizePx * 0.06f));
        ring.setColor(0xFFFFFFFF);
        setFocusable(true);
        setClickable(true);
        String name = c.getString(c.getResources().getIdentifier("avatar_" + (idx % AvatarArt.COUNT), "string", c.getPackageName()));
        String d = c.getString(R.string.a11y_avatar, name);
        setContentDescription(current ? c.getString(R.string.a11y_avatar_current, d) : d);
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
        super.onFocusChanged(gained, direction, previouslyFocused);
        animate().cancel();
        animate().scaleX(gained ? 1.15f : 1f).scaleY(gained ? 1.15f : 1f).setDuration(120).start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        int pad = Math.round(w * 0.08f);
        dst.set(pad, pad, w - pad, h - pad);
        canvas.drawBitmap(art, null, dst, bmp);
        if (isFocused() || current) {
            ring.setAlpha(isFocused() ? 255 : 110);
            float i = ring.getStrokeWidth() / 2f;
            canvas.drawCircle(w / 2f, h / 2f, w / 2f - i, ring);
        }
    }
}
