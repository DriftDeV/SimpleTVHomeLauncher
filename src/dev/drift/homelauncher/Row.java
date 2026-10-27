package dev.drift.homelauncher;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.HorizontalScrollView;

/** Riga scorrevole che tiene al centro l'elemento a fuoco, con scroll fluido. */
final class Row extends HorizontalScrollView {
    Row(Context c) {
        super(c);
        setFocusable(false); // contenitore: le fermate sono le card, non la riga
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipChildren(false);
        setClipToPadding(false);
        setFillViewport(true);
        setSmoothScrollingEnabled(true);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (focused == null) return;
        View v = focused;
        while (v.getParent() != child && v.getParent() instanceof View) v = (View) v.getParent();
        smoothScrollTo(v.getLeft() + v.getWidth() / 2 - getWidth() / 2, 0);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }
}
