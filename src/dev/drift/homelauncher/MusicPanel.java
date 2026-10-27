package dev.drift.homelauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.provider.Settings;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Tab Musica a tutto schermo dentro la home: sfondo sfocato dalla copertina (anche dietro la top bar),
 * copertina grande al centro, titolo, barra di avanzamento e controlli. La top bar resta visibile sopra.
 */
final class MusicPanel extends FrameLayout implements MusicTracker.Listener {
    private final Activity act;
    private final float u;
    private final MusicTracker mt;
    private final Handler h = new Handler();

    private final MusicViews.Bg bg;
    private Bitmap homeBackdrop;
    private final MusicViews.Cover cover;
    private final TextView eyebrow, title, subtitle, tCur, tEnd;
    private final MusicViews.Progress progress;
    private final LinearLayout seekBox, controls;
    private final MusicViews.Ctl prev, play, next;
    private final Pill action;
    private Object lastArt;
    private String lastKey = "";
    private boolean lastPlaying;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (isShown()) h.postDelayed(this, 500);
        }
    };

    MusicPanel(Activity a, float u) {
        super(a);
        this.act = a;
        this.u = u;
        this.mt = MusicTracker.get(a);
        setClipChildren(false);
        bg = new MusicViews.Bg(a, null);
        addView(bg, new LayoutParams(-1, -1));

        eyebrow = text(16, "sans-serif-medium", 0xB3FFFFFF);
        eyebrow.setGravity(Gravity.CENTER);
        addView(eyebrow, at(0, 426, 1280, 18));

        cover = new MusicViews.Cover(a, 20 * u);
        cover.setElevation(18 * u);
        final float rad = 20 * u;
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad);
            }
        });
        addView(cover, at(490, 112, 300, 300));

        title = A11y.heading(text(30, "sans-serif-light", 0xFFFFFFFF));
        title.setGravity(Gravity.CENTER);
        addView(title, at(140, 448, 1000, 38));
        subtitle = text(22, "sans-serif", 0xE6FFFFFF);
        subtitle.setGravity(Gravity.CENTER);
        addView(subtitle, at(140, 490, 1000, 24));

        seekBox = new LinearLayout(a);
        seekBox.setOrientation(LinearLayout.HORIZONTAL);
        seekBox.setGravity(Gravity.CENTER_VERTICAL);
        seekBox.setClipChildren(false);
        tCur = A11y.hide(text(18, "sans-serif", 0xCCFFFFFF)); // il valore lo dice la barra
        tCur.setGravity(Gravity.RIGHT);
        tEnd = A11y.hide(text(18, "sans-serif", 0xCCFFFFFF));
        progress = new MusicViews.Progress(a);
        progress.setSeeker(dir -> mt.seekBy(dir * 10000L)); // stesse azioni dei tasti, anche per il lettore di schermo
        progress.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                mt.seekBy(-10000);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                mt.seekBy(10000);
                return true;
            }
            return false;
        });
        seekBox.addView(tCur, new LinearLayout.LayoutParams(px(62), -2));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, px(22), 1f);
        pl.leftMargin = px(14);
        pl.rightMargin = px(14);
        seekBox.addView(progress, pl);
        seekBox.addView(tEnd, new LinearLayout.LayoutParams(px(62), -2));
        addView(seekBox, at(290, 530, 700, 26));

        controls = new LinearLayout(a);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setClipChildren(false);
        prev = new MusicViews.Ctl(a, 0);
        play = new MusicViews.Ctl(a, 1);
        next = new MusicViews.Ctl(a, 2);
        prev.setOnClickListener(v -> mt.prev());
        play.setOnClickListener(v -> mt.playPause());
        next.setOnClickListener(v -> mt.next());
        controls.addView(prev, ctl(58, 30));
        controls.addView(play, ctl(74, 30));
        controls.addView(next, ctl(58, 0));
        addView(controls, at(340, 574, 600, 80));

        action = new Pill(a, "", u);
        LayoutParams ap = at(0, 540, -2, 48);
        ap.gravity = Gravity.CENTER_HORIZONTAL;
        ap.leftMargin = 0;
        addView(action, ap);
    }

    private int px(float v) {
        return Math.round(v * u);
    }

    private LayoutParams at(int left, int top, int w, int hh) {
        LayoutParams lp = new LayoutParams(w < 0 ? w : px(w), hh < 0 ? hh : px(hh));
        lp.leftMargin = px(left);
        lp.topMargin = px(top);
        return lp;
    }

    private TextView text(float size, String face, int color) {
        TextView t = new TextView(act);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u * A11y.textScale(act));
        t.setTypeface(Typeface.create(face, Typeface.NORMAL));
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout.LayoutParams ctl(int size, int rightMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(size), px(size));
        lp.rightMargin = px(rightMargin);
        return lp;
    }

    /** Sfondo da usare quando non c'e' copertina (la home sfocata o un gradiente). */
    void setBackdrop(Bitmap b) {
        homeBackdrop = b;
    }

    /** Mostra la tab: collega il tracker, aggiorna e porta il focus sui controlli. */
    void enter() {
        setVisibility(VISIBLE);
        mt.addListener(this);
        mt.start();
        lastArt = null;
        bind();
        if (mt.hasTrack()) play.requestFocus(); else action.requestFocus();
        h.removeCallbacks(tick);
        h.post(tick);
    }

    void leave() {
        mt.removeListener(this);
        h.removeCallbacks(tick);
        setVisibility(GONE);
    }

    @Override
    public void onMusicChanged() {
        bind();
    }

    private static String mmss(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.ITALIAN, "%d:%02d", s / 60, s % 60);
    }

    private void updateProgress() {
        if (!mt.hasTrack()) return;
        long pos = mt.position();
        progress.set(mt.duration > 0 ? pos / (float) mt.duration : 0f, A11y.spokenTime(act, pos / 1000),
                mt.duration > 0 ? A11y.spokenTime(act, mt.duration / 1000) : "");
        tCur.setText(mmss(pos));
        tEnd.setText(mt.duration > 0 ? mmss(mt.duration) : "");
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = act.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    private void bind() {
        boolean track = mt.hasTrack();
        int vis = track ? VISIBLE : INVISIBLE;
        seekBox.setVisibility(vis);
        controls.setVisibility(vis);
        action.setVisibility(track ? GONE : VISIBLE);
        cover.set(track ? mt.art : null);
        cover.describe(track ? act.getString(R.string.a11y_cover, mt.album.isEmpty() ? mt.title : mt.album) : null);
        if (mt.art != lastArt || (mt.art == null && lastArt == null)) {
            lastArt = mt.art;
            Bitmap blurred = null;
            if (mt.art != null) {
                try {
                    blurred = Blur.fromBitmap(mt.art, 96, 54, 5);
                } catch (Throwable ignored) {
                }
            }
            bg.set(blurred != null ? blurred : homeBackdrop);
        }
        if (track) {
            eyebrow.setText((mt.playing() ? "IN RIPRODUZIONE" : "IN PAUSA") + "  ·  " + appLabel(mt.pkg).toUpperCase());
            title.setText(mt.title);
            String sub = mt.artist;
            if (!mt.album.isEmpty()) sub = sub.isEmpty() ? mt.album : sub + "  ·  " + mt.album;
            subtitle.setText(sub);
            play.setPlaying(mt.playing());
            updateProgress();
            // annunci: cambio brano e play/pausa (il lettore di schermo non li vede da solo)
            String key = mt.pkg + "|" + mt.title + "|" + mt.artist;
            if (!key.equals(lastKey)) {
                lastKey = key;
                announceForAccessibility(act.getString(R.string.a11y_now_playing, mt.title, mt.artist));
            } else if (mt.playing() != lastPlaying) {
                announceForAccessibility(act.getString(mt.playing() ? R.string.a11y_state_playing : R.string.a11y_state_paused));
            }
            lastPlaying = mt.playing();
        } else if (!mt.hasAccess()) {
            eyebrow.setText("MUSICA");
            title.setText("Serve un permesso");
            subtitle.setText("Per vedere cosa stai ascoltando, abilita \"Accesso alle notifiche\" per Home.");
            action.setText("Concedi accesso");
            action.setOnClickListener(v -> openAccessSettings());
        } else {
            eyebrow.setText("MUSICA");
            title.setText("Niente in riproduzione");
            subtitle.setText("Avvia Spotify e riproduci un brano: apparirà qui con i controlli.");
            action.setText("Apri Spotify");
            action.setOnClickListener(v -> openApp("com.spotify.tv.android"));
        }
        if (isShown() && !(findFocus() != null)) {
            if (track) play.requestFocus(); else action.requestFocus();
        }
    }

    private void openAccessSettings() {
        try {
            act.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Toast.makeText(act, "Abilitalo da Impostazioni > App > Accesso speciale", Toast.LENGTH_LONG).show();
        }
    }

    private void openApp(String pkg) {
        Intent i = act.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(act, "App non installata", Toast.LENGTH_SHORT).show();
            return;
        }
        act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
