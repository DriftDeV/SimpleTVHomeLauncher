package dev.drift.homelauncher;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

/** Piccoli aiuti per il lettore di schermo (TalkBack e simili): stati, intestazioni, annunci e scala dei testi. */
final class A11y {
    private A11y() {
    }

    /** Scala dei caratteri di sistema, limitata a 1,0-1,3: oltre, i layout fissi della TV non reggono. */
    static float textScale(Context c) {
        float s = c.getResources().getConfiguration().fontScale;
        return Math.max(1f, Math.min(1.3f, s));
    }

    /** True se un servizio di accessibilita' (lettore di schermo, Switch Access...) e' attivo. */
    static boolean enabled(Context c) {
        try {
            AccessibilityManager am = (AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
            return am != null && am.isEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    /** True se l'utente ha attivato "testo ad alto contrasto": i pulsanti traslucidi usano un riempimento piu' marcato. */
    static boolean highContrast(Context c) {
        try {
            return android.provider.Settings.Secure.getInt(c.getContentResolver(), "high_text_contrast_enabled", 0) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Riempimento a riposo dei pulsanti traslucidi (alfa su bianco): 18%, 30% con alto contrasto. */
    static int restFill(Context c) {
        return highContrast(c) ? 0x4DFFFFFF : 0x2EFFFFFF;
    }

    /** Titolo di sezione: navigabile come intestazione. */
    static <T extends View> T heading(T v) {
        v.setAccessibilityHeading(true);
        return v;
    }

    /** Decorativo o duplicato: il lettore di schermo lo salta. */
    static <T extends View> T hide(T v) {
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    /** Copre tutto cio' che sta sotto (anche i figli): serve per le viste "modali" dentro la stessa finestra. */
    static <T extends View> T hideTree(T v) {
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        return v;
    }

    static <T extends View> T show(T v) {
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        return v;
    }

    /** I cambi di testo vengono letti da soli (stato, errori, avanzamento dell'accesso). */
    static <T extends TextView> T live(T t) {
        t.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        return t;
    }

    /** 65 -> "1 minuto e 5 secondi": un lettore di schermo legge male "1:05". */
    static String spokenTime(Context c, long seconds) {
        long s = Math.max(0, seconds);
        int h = (int) (s / 3600), m = (int) ((s % 3600) / 60), sec = (int) (s % 60);
        android.content.res.Resources r = c.getResources();
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (h > 0) parts.add(r.getQuantityString(R.plurals.a11y_hours, h, h));
        if (m > 0) parts.add(r.getQuantityString(R.plurals.a11y_minutes, m, m));
        if (sec > 0 || parts.isEmpty()) parts.add(r.getQuantityString(R.plurals.a11y_seconds, sec, sec));
        if (parts.size() == 1) return parts.get(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            sb.append(i == 0 ? "" : i == parts.size() - 1 ? " e " : ", ").append(parts.get(i));
        }
        return sb.toString();
    }

    /** "720403" -> "7 2 0 4 0 3": il lettore dice le cifre una a una. */
    static String digits(String code) {
        StringBuilder sb = new StringBuilder();
        for (char ch : code.toCharArray()) {
            if (Character.isDigit(ch)) sb.append(sb.length() > 0 ? " " : "").append(ch);
        }
        return sb.toString();
    }

    /** Testo del menu senza il glifo "●", che il lettore leggerebbe come "pallino": lo stato diventa parole. */
    static String spoken(String label) {
        return label.replace("   ● ", ", ").replace("● ", "").trim();
    }
}
