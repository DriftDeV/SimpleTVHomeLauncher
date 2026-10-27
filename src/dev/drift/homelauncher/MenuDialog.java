package dev.drift.homelauncher;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Pannello centrale a vetro smerigliato: dietro c'e' la home sfocata e scurita,
 * il pannello e' un velo bianco traslucido con bordo sottile.
 */
final class MenuDialog {
    private final Dialog dialog;
    private final LinearLayout panel;
    private final Context ctx;
    private final float u;

    MenuDialog(Context ctx, String title, String subtitle, float u, Bitmap backdrop) {
        this.ctx = ctx;
        this.u = u;
        dialog = new Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar);
        Window w = dialog.getWindow();
        Resources res = ctx.getResources();
        Drawable layer0 = backdrop != null ? new BitmapDrawable(res, backdrop) : new ColorDrawable(0xFF101014);
        if (layer0 instanceof BitmapDrawable) ((BitmapDrawable) layer0).setFilterBitmap(true);
        w.setBackgroundDrawable(new LayerDrawable(new Drawable[]{layer0, new ColorDrawable(0x66000000)}));
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        LinearLayout frame = new LinearLayout(ctx);
        frame.setGravity(Gravity.CENTER);
        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(36 * u);
        panel.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setStroke(Math.max(1, Math.round(1.5f * u)), 0x4DFFFFFF);
        bg.setCornerRadius(32 * u);
        panel.setBackground(bg);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);

        dialog.setTitle(title); // titolo di finestra: il lettore di schermo lo annuncia all'apertura
        panel.setAccessibilityPaneTitle(title);
        panel.addView(A11y.heading(text(title, 30, "sans-serif-light", 0xFFFFFFFF)));
        if (subtitle != null) panel.addView(text(subtitle, 17, "sans-serif", 0xB3FFFFFF));
        panel.addView(new View(ctx), new LinearLayout.LayoutParams(1, Math.round(18 * u)));
        frame.addView(panel, new LinearLayout.LayoutParams(Math.round(520 * u), ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(frame);
        dialog.setOnShowListener(d -> {
            panel.setAlpha(0f);
            panel.setScaleX(0.96f);
            panel.setScaleY(0.96f);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(new DecelerateInterpolator()).start();
        });
    }

    private TextView text(String s, float size, String face, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u * A11y.textScale(ctx));
        t.setTypeface(Typeface.create(face, Typeface.NORMAL));
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    MenuDialog add(String label, Runnable action) {
        final TextView v = text(label, 21, "sans-serif-medium", 0xF2FFFFFF);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(Math.round(20 * u), 0, Math.round(20 * u), 0);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(14 * u);
        bg.setColor(Color.TRANSPARENT);
        v.setBackground(bg);
        v.setFocusable(true);
        v.setClickable(true);
        // stato a parole, non col glifo: "Luigicolucci, attivo" / "Nessuno" selezionato
        v.setContentDescription(A11y.spoken(label));
        if (label.startsWith("● ")) v.setSelected(true);
        v.setOnFocusChangeListener((x, f) -> {
            bg.setColor(f ? 0xF2FFFFFF : Color.TRANSPARENT);
            v.setTextColor(f ? 0xFF111111 : 0xF2FFFFFF);
        });
        v.setOnClickListener(x -> {
            dialog.dismiss();
            if (action != null) action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(56 * u));
        lp.topMargin = Math.round(4 * u);
        panel.addView(v, lp);
        return this;
    }

    /** Campo di testo (tastiera della TV); chi chiama legge il testo da quello restituito. */
    EditText addInput(String hint, String initial) {
        EditText e = new EditText(ctx);
        e.setText(initial);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * u * A11y.textScale(ctx));
        e.setTextColor(0xFFFFFFFF);
        e.setHintTextColor(0x80FFFFFF);
        e.setPadding(Math.round(20 * u), 0, Math.round(20 * u), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x26FFFFFF);
        bg.setCornerRadius(14 * u);
        e.setBackground(bg);
        // la tastiera della TV puo' inghiottire i tasti direzionali: sposta il focus a mano
        e.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            int dir = code == android.view.KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN
                    : code == android.view.KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : 0;
            if (dir == 0) return false;
            View n = v.focusSearch(dir);
            if (n != null) n.requestFocus();
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(60 * u));
        lp.bottomMargin = Math.round(8 * u);
        panel.addView(e, lp);
        return e;
    }

    MenuDialog addView(View v, LinearLayout.LayoutParams lp) {
        panel.addView(v, lp);
        return this;
    }

    void dismiss() {
        dialog.dismiss();
    }

    void show() {
        dialog.show();
    }
}
