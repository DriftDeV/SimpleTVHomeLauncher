package dev.drift.homelauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inserimento semplificato dell'indirizzo del server con il telecomando: "192.168." e la porta 8096 sono fissi,
 * si scelgono solo il terzo e il quarto numero (SU/GIU' li cambiano, tenendo premuto vanno piu' veloci, i tasti
 * numerici li scrivono; OK passa al successivo).
 */
final class AddressPicker extends LinearLayout {
    private static final Pattern HOME = Pattern.compile("^(?:http://)?192\\.168\\.(\\d{1,3})\\.(\\d{1,3})(?::8096)?/?$");

    private final Octet third, fourth;

    AddressPicker(Context c, float u, View before, View after) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setClipChildren(false);
        addView(label(c, u, "192.168."));
        third = new Octet(c, u, c.getString(R.string.a11y_octet3), 1);
        addView(third, new LayoutParams(Math.round(92 * u), Math.round(56 * u)));
        addView(label(c, u, "."));
        fourth = new Octet(c, u, c.getString(R.string.a11y_octet4), 2);
        addView(fourth, new LayoutParams(Math.round(92 * u), Math.round(56 * u)));
        addView(label(c, u, ":8096"));
        third.link(before, fourth);
        fourth.link(third, after);
    }

    private static TextView label(Context c, float u, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28 * u * A11y.textScale(c));
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        t.setTextColor(0xCCFFFFFF);
        t.setIncludeFontPadding(false);
        t.setPadding(Math.round(4 * u), 0, Math.round(4 * u), 0);
        A11y.hide(t);
        return t;
    }

    /** Se "192.168.x.y" con porta 8096 (o senza porta) carica i due numeri e dice di si'. */
    boolean load(String server) {
        Matcher m = HOME.matcher(server.trim());
        if (!m.matches()) return false;
        int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2));
        if (a > 255 || b > 255) return false;
        third.set(a);
        fourth.set(b);
        return true;
    }

    String address() {
        return "http://192.168." + third.value + "." + fourth.value + ":8096";
    }

    View first() {
        return third;
    }

    /** Un numero 0-255 da scegliere con SU/GIU'. */
    private static final class Octet extends View {
        private final float u;
        private final String name;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private View prev, next;
        private int value;
        private long typedAt;

        Octet(Context c, float u, String name, int initial) {
            super(c);
            this.u = u;
            this.name = name;
            value = initial;
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setTextSize(28 * u * A11y.textScale(c));
            setFocusable(true);
            describe();
        }

        void link(View prev, View next) {
            this.prev = prev;
            this.next = next;
        }

        void set(int v) {
            value = Math.max(0, Math.min(255, v));
            describe();
            invalidate();
        }

        private void describe() {
            setContentDescription(String.format(getContext().getString(R.string.a11y_octet_value), name, value));
        }

        private void step(int dir, int repeat) {
            int s = repeat >= 12 ? 10 : repeat >= 4 ? 5 : 1;
            set(((value + dir * s) % 256 + 256) % 256);
            typedAt = 0;
            if (A11y.enabled(getContext())) announceForAccessibility(String.valueOf(value));
        }

        private void typed(int digit) {
            long now = SystemClock.uptimeMillis();
            int v = now - typedAt < 1500 ? value * 10 + digit : digit;
            typedAt = now;
            set(v > 255 ? digit : v);
        }

        @Override
        public boolean onKeyDown(int code, KeyEvent ev) {
            int rc = ev.getRepeatCount();
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                step(1, rc);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                step(-1, rc);
                return true;
            }
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                typed(code - KeyEvent.KEYCODE_0);
                return true;
            }
            if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
                typed(code - KeyEvent.KEYCODE_NUMPAD_0);
                return true;
            }
            View to = code == KeyEvent.KEYCODE_DPAD_LEFT ? prev
                    : code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_CENTER
                    || code == KeyEvent.KEYCODE_ENTER ? next : null;
            if (to != null) {
                if (rc == 0) to.requestFocus();
                return true;
            }
            return super.onKeyDown(code, ev);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }

        @Override
        public boolean performAccessibilityAction(int action, android.os.Bundle args) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                step(1, 0);
                return true;
            }
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                step(-1, 0);
                return true;
            }
            return super.performAccessibilityAction(action, args);
        }

        @Override
        protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
            super.onFocusChanged(gained, direction, previouslyFocused);
            typedAt = 0;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            boolean f = isFocused();
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(f ? 0xF2FFFFFF : A11y.restFill(getContext()));
            float r = h / 2f;
            if (f) {
                // anello bianco staccato dal riempimento, come negli altri pulsanti
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(2.5f * u);
                fill.setColor(0xFFFFFFFF);
                canvas.drawRoundRect(1.25f * u, 1.25f * u, w - 1.25f * u, h - 1.25f * u, r, r, fill);
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(0xF2FFFFFF);
                canvas.drawRoundRect(5 * u, 5 * u, w - 5 * u, h - 5 * u, r - 5 * u, r - 5 * u, fill);
            } else {
                canvas.drawRoundRect(0, 0, w, h, r, r, fill);
            }
            text.setColor(f ? 0xFF111111 : 0xFFFFFFFF);
            Paint.FontMetrics fm = text.getFontMetrics();
            canvas.drawText(String.valueOf(value), w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, text);
            if (f) {
                // frecce su/giu': dicono che il numero si cambia con i tasti freccia
                fill.setColor(0xFF111111);
                float cx = w / 2f, a = 5 * u;
                arrow.reset();
                arrow.moveTo(cx - a, 12 * u);
                arrow.lineTo(cx + a, 12 * u);
                arrow.lineTo(cx, 12 * u - a);
                arrow.close();
                canvas.drawPath(arrow, fill);
                arrow.reset();
                arrow.moveTo(cx - a, h - 12 * u);
                arrow.lineTo(cx + a, h - 12 * u);
                arrow.lineTo(cx, h - 12 * u + a);
                arrow.close();
                canvas.drawPath(arrow, fill);
            }
        }
    }
}
