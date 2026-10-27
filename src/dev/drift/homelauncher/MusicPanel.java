package dev.drift.homelauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tab Musica. In alto cio' che suona (il player interno con i brani di Jellyfin, oppure un'altra app come Spotify),
 * sotto la libreria musicale di Jellyfin in righe: Brani del giorno, Ascoltati di recente, Album, Artisti.
 * Album e artisti aprono una scheda sopra la libreria; Indietro la chiude. L'accesso a Jellyfin e' quello della tab Film.
 */
final class MusicPanel extends FrameLayout implements MusicTracker.Listener, MusicPlayer.Listener {
    private static final int TILE = 150, DAILY = 6;

    private final Activity act;
    private final float u;
    private final MusicTracker mt;
    private final MusicPlayer player;
    private final Handler h = new Handler();
    private Jelly jelly;
    private Runnable onNeedLogin;

    // in riproduzione
    private final MusicViews.Bg bg;
    private Bitmap homeBackdrop;
    private final MusicViews.Cover cover;
    private final TextView title, subtitle, tCur, tEnd;
    private final MusicViews.Progress progress;
    private final LinearLayout seekBox, controls, badges;
    private final MusicViews.Ctl info, prev, play, next;
    private String badgesFor = "";
    private FrameLayout infoView;
    private String infoId = "";
    private LinearLayout infoCols;
    private final Pill action;
    private Object lastArt;
    private String lastKey = "";
    private boolean lastPlaying;

    // libreria
    private final ScrollView scroll;
    private final LinearLayout rowsBox;
    private final TextView status;
    private final LinearLayout statusBtns;
    private String loadedKey = "";
    private int pending;
    private String loadError;
    private boolean loadUnauth, triedRelogin;
    private List<MusicItem> rDaily = new ArrayList<>(), rRecent = new ArrayList<>(), rAlbums = new ArrayList<>(), rArtists = new ArrayList<>();
    private View lastTile;

    // schede (album, artista) impilate sopra la libreria
    private final ArrayList<FrameLayout> stack = new ArrayList<>();
    private final ArrayList<View> stackFrom = new ArrayList<>();
    private final Map<View, Integer> a11yModes = new HashMap<>();

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
        this.player = MusicPlayer.get(a);
        setClipChildren(false);
        bg = new MusicViews.Bg(a, null);
        addView(A11y.hide(bg), new LayoutParams(-1, -1));

        // ---- in riproduzione: copertina, titolo, artista, avanzamento a sinistra; controlli a destra
        cover = new MusicViews.Cover(a, 14 * u);
        cover.setElevation(14 * u);
        final float rad = 14 * u;
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad);
            }
        });
        addView(cover, at(80, 118, 168, 168));

        title = A11y.heading(text(30, "sans-serif-light", 0xFFFFFFFF));
        addView(title, at(276, 124, 620, 40));
        subtitle = text(20, "sans-serif", 0xE6FFFFFF);
        addView(subtitle, at(276, 168, 620, 26));
        // formato e qualita' in breve; tutto il resto nella scheda "Informazioni"
        badges = new LinearLayout(a);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        addView(badges, at(276, 204, 620, 26));

        seekBox = new LinearLayout(a);
        seekBox.setOrientation(LinearLayout.HORIZONTAL);
        seekBox.setGravity(Gravity.CENTER_VERTICAL);
        seekBox.setClipChildren(false);
        tCur = A11y.hide(text(17, "sans-serif", 0xCCFFFFFF)); // il valore lo dice la barra
        tEnd = A11y.hide(text(17, "sans-serif", 0xCCFFFFFF));
        tEnd.setGravity(Gravity.RIGHT);
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
        seekBox.addView(tCur, new LinearLayout.LayoutParams(px(52), -2));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, px(22), 1f);
        pl.leftMargin = px(10);
        pl.rightMargin = px(10);
        seekBox.addView(progress, pl);
        seekBox.addView(tEnd, new LinearLayout.LayoutParams(px(52), -2));
        addView(seekBox, at(276, 246, 560, 26));

        controls = new LinearLayout(a);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        controls.setClipChildren(false);
        info = new MusicViews.Ctl(a, 5);
        prev = new MusicViews.Ctl(a, 0);
        play = new MusicViews.Ctl(a, 1);
        next = new MusicViews.Ctl(a, 2);
        info.setOnClickListener(v -> openInfo());
        prev.setOnClickListener(v -> mt.prev());
        play.setOnClickListener(v -> mt.playPause());
        next.setOnClickListener(v -> mt.next());
        controls.addView(info, ctl(52, 40)); // staccato: non e' un comando di riproduzione
        controls.addView(prev, ctl(52, 22));
        controls.addView(play, ctl(66, 22));
        controls.addView(next, ctl(52, 0));
        LayoutParams cp = at(0, 162, -2, 84);
        cp.gravity = Gravity.TOP | Gravity.RIGHT;
        cp.leftMargin = 0;
        cp.rightMargin = px(80);
        addView(controls, cp);

        action = new Pill(a, "", u);
        LayoutParams ap = at(0, 180, -2, 48);
        ap.gravity = Gravity.TOP | Gravity.RIGHT;
        ap.leftMargin = 0;
        ap.rightMargin = px(80);
        addView(action, ap);

        // ---- libreria: righe che scorrono sotto, tagliate dal riquadro (non passano sopra cio' che suona)
        scroll = new ScrollView(a);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        rowsBox = new LinearLayout(a);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsBox.setClipChildren(false);
        scroll.addView(rowsBox, new LayoutParams(-1, -2));
        FrameLayout viewport = new FrameLayout(a);
        viewport.setClipChildren(true);
        viewport.addView(scroll, new LayoutParams(-1, -1));
        addView(viewport, at(0, 306, 1280, 414));

        status = A11y.live(text(22, "sans-serif-light", 0xFFFFFFFF));
        status.setGravity(Gravity.CENTER);
        status.setSingleLine(false);
        status.setMaxLines(2);
        addView(status, at(140, 384, 1000, 64));
        statusBtns = new LinearLayout(a);
        statusBtns.setOrientation(LinearLayout.HORIZONTAL);
        statusBtns.setClipChildren(false);
        LayoutParams sp = at(0, 466, -2, 48);
        sp.gravity = Gravity.CENTER_HORIZONTAL;
        sp.leftMargin = 0;
        addView(statusBtns, sp);
    }

    // ------------------------------------------------------------ utilita'

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

    private Pill pill(String label, boolean primary, Runnable r) {
        Pill p = new Pill(act, label, u);
        p.setPrimary(primary);
        p.setOnClickListener(v -> r.run());
        return p;
    }

    private void addBtn(LinearLayout bar, Pill p) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, px(48));
        lp.rightMargin = px(12);
        bar.addView(p, lp);
    }

    /** Sfondo da usare quando non c'e' copertina (la home sfocata). */
    void setBackdrop(Bitmap b) {
        homeBackdrop = b;
    }

    /** Cosa fare quando serve l'accesso a Jellyfin (la home apre la tab Film). */
    void setOnNeedLogin(Runnable r) {
        onNeedLogin = r;
    }

    // ------------------------------------------------------------ ciclo di vita

    /** Mostra la tab: collega tracker e player, aggiorna la libreria e porta il focus dove serve. */
    void enter() {
        setVisibility(VISIBLE);
        mt.addListener(this);
        player.addListener(this);
        mt.start();
        lastArt = null;
        bind();
        h.removeCallbacks(tick);
        h.post(tick);
        // come la tab Film: entrando il focus va nel contenuto (le righe di prima restano finche' arrivano quelle nuove)
        if (hasLibrary()) {
            View t = lastTile != null && lastTile.isAttachedToWindow() ? lastTile : firstTile();
            if (t != null) t.requestFocusFromTouch();
        } else {
            focusDefault();
        }
        loadLibrary();
    }

    void leave() {
        mt.removeListener(this);
        player.removeListener(this);
        h.removeCallbacks(tick);
        while (!stack.isEmpty()) pop(false);
        setVisibility(GONE);
    }

    /** True se Indietro e' stato gestito qui (chiude la scheda in cima). */
    boolean onBack() {
        if (stack.isEmpty()) return false;
        pop(true);
        return true;
    }

    @Override
    public void onMusicChanged() {
        bind();
    }

    @Override
    public void onPlayerChanged() {
        markCurrent(this);
        // la scheda Informazioni aperta prima che arrivassero i dettagli: si completa
        MusicItem c = player.current();
        if (infoView != null && stack.contains(infoView) && c != null && c.detailed && c.id.equals(infoId)
                && infoCols != null && infoCols.getTag() == null) {
            fillInfo(c);
        }
    }

    // ------------------------------------------------------------ formato e qualita'

    private static boolean lossless(MusicItem m) {
        String c = m.codec.toLowerCase(Locale.ROOT);
        return c.equals("flac") || c.equals("alac") || c.startsWith("pcm") || c.equals("wav") || c.equals("ape") || c.equals("wavpack");
    }

    private static String format(MusicItem m) {
        String c = m.codec.isEmpty() ? m.container : m.codec;
        if (c.equalsIgnoreCase("aac") && !m.profile.isEmpty() && m.profile.toUpperCase(Locale.ROOT).contains("HE")) c = "HE-AAC";
        return c.startsWith("pcm") ? "PCM" : c.toUpperCase(Locale.ROOT);
    }

    private static String khz(int hz) {
        return hz % 1000 == 0 ? hz / 1000 + " kHz" : String.format(Locale.ITALIAN, "%.1f kHz", hz / 1000f);
    }

    /** "16 bit · 44,1 kHz" per i formati senza perdita, "320 kbps" per gli altri. */
    private static String quality(MusicItem m) {
        if (lossless(m)) {
            String s = m.bitDepth > 0 ? m.bitDepth + " bit" : "";
            if (m.sampleRate > 0) s += (s.isEmpty() ? "" : " · ") + khz(m.sampleRate);
            return s;
        }
        return m.bitrate > 0 ? Math.round(m.bitrate / 1000f) + " kbps" : "";
    }

    private static String channels(MusicItem m) {
        if (m.channels == 1) return "Mono";
        if (m.channels == 2) return "Stereo";
        if (m.channels == 6) return "5.1";
        if (m.channels == 8) return "7.1";
        return m.channels > 0 ? m.channels + " canali" : "";
    }

    private TextView badge(String s) {
        TextView t = text(16, "sans-serif-medium", 0xF2FFFFFF);
        t.setText(s);
        t.setPadding(px(10), px(3), px(10), px(3));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(6 * u);
        g.setStroke(Math.max(1, px(1.5f)), 0x99FFFFFF);
        t.setBackground(g);
        return t;
    }

    /** Etichette brevi sotto il titolo (come nella scheda dei film): formato, qualita', canali, conversione. */
    private void fillBadges(MusicItem m) {
        String key = m == null ? "" : m.id + (m.detailed ? "+" : "");
        if (key.equals(badgesFor)) return;
        badgesFor = key;
        badges.removeAllViews();
        if (m == null || !m.detailed) {
            badges.setContentDescription(null);
            return;
        }
        List<String> l = new ArrayList<>();
        if (!format(m).isEmpty()) l.add(format(m));
        if (!quality(m).isEmpty()) l.add(quality(m));
        if (!channels(m).isEmpty()) l.add(channels(m));
        if (!m.direct()) l.add("Convertito in MP3");
        StringBuilder spoken = new StringBuilder();
        for (String s : l) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = px(8);
            badges.addView(A11y.hide(badge(s)), lp);
            spoken.append(spoken.length() > 0 ? ", " : "").append(s.replace(" · ", ", "));
        }
        badges.setContentDescription(act.getString(R.string.a11y_formats, spoken.toString()));
    }

    // ------------------------------------------------------------ scheda Informazioni

    /** Tutto quello che si sa del brano che suona, su richiesta (pulsante "i"): brano a sinistra, audio a destra. */
    private void openInfo() {
        final MusicItem c = player.current();
        if (c == null) return;
        infoView = push(act.getString(R.string.a11y_track_info), info, player.art());
        infoId = c.id;
        MusicViews.Cover cv = bigCover(infoView, c, 80, 128, 200, false);
        if (player.art() != null) cv.set(player.art());
        TextView t = A11y.heading(text(34, "sans-serif-light", 0xFFFFFFFF));
        t.setText(c.name);
        infoView.addView(t, at(320, 126, 880, 46));
        TextView s = text(20, "sans-serif", 0xE6FFFFFF);
        s.setText(c.artist);
        infoView.addView(s, at(320, 176, 880, 26));
        infoCols = new LinearLayout(act);
        infoCols.setOrientation(LinearLayout.HORIZONTAL);
        infoCols.setClipChildren(false);
        infoView.addView(infoCols, at(308, 224, 912, 480));
        fillInfo(c);
        if (!c.detailed) {
            jelly.songDetail(c, new Jelly.Done<MusicItem>() {
                @Override
                public void ok(MusicItem v) {
                    onPlayerChanged();
                }

                @Override
                public void fail(String message, boolean unauthorized) {
                }
            });
        }
    }

    private void fillInfo(MusicItem c) {
        infoCols.removeAllViews();
        infoCols.setTag(c.detailed ? Boolean.TRUE : null);
        LinearLayout left = infoColumn("Brano"), right = infoColumn("Audio");
        infoRow(left, "Artista", c.artist);
        infoRow(left, "Album", c.album);
        infoRow(left, "Anno", c.year > 0 ? String.valueOf(c.year) : "");
        infoRow(left, "Genere", c.genres);
        String tr = c.index > 0 ? String.valueOf(c.index) : "";
        if (c.disc > 0 && !tr.isEmpty()) tr += "   ·   disco " + c.disc;
        infoRow(left, "Traccia", tr);
        infoRow(left, "Durata", MusicItem.clock(player.duration() > 0 ? player.duration() : c.durationMs()));
        if (c.detailed) infoRow(left, "Ascolti", String.valueOf(c.playCount));
        infoRow(left, "Aggiunto il", date(c.added));
        if (c.detailed) {
            infoRow(right, "Formato", format(c) + (c.container.isEmpty() || c.container.equalsIgnoreCase(c.codec)
                    ? "" : "   ·   file " + c.container.toUpperCase(Locale.ROOT)));
            infoRow(right, "Profilo", c.profile);
            infoRow(right, "Bitrate", c.bitrate > 0 ? Math.round(c.bitrate / 1000f) + " kbps" : "");
            infoRow(right, "Frequenza", c.sampleRate > 0 ? khz(c.sampleRate) : "");
            infoRow(right, "Profondità", c.bitDepth > 0 ? c.bitDepth + " bit" : "");
            infoRow(right, "Canali", channels(c) + (c.layout.isEmpty() || channels(c).equalsIgnoreCase(c.layout) ? "" : "  (" + c.layout + ")"));
            infoRow(right, "Qualità", lossless(c) ? "Senza perdita" : "Compresso con perdita");
            infoRow(right, "Dimensione", c.size > 0 ? String.format(Locale.ITALIAN, "%.1f MB", c.size / 1048576f) : "");
            infoRow(right, "Riproduzione", c.direct() ? "File originale" : "Convertito in MP3 dal server");
        } else {
            infoRow(right, "Formato", "Carico i dettagli…");
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(436), -2);
        lp.rightMargin = px(28);
        infoCols.addView(left, lp);
        infoCols.addView(right, new LinearLayout.LayoutParams(px(436), -2));
        View first = left.getChildCount() > 1 ? left.getChildAt(1) : null;
        if (first != null && (findFocus() == null || !isInside(findFocus(), infoCols))) first.requestFocusFromTouch();
    }

    private LinearLayout infoColumn(String head) {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        TextView h = A11y.heading(text(20, "sans-serif-medium", 0xE6FFFFFF));
        h.setText(head);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = px(12);
        lp.bottomMargin = px(6);
        col.addView(h, lp);
        return col;
    }

    /**
     * Riga "etichetta  valore": a fuoco si accende, cosi' col telecomando si scorre e il lettore di schermo legge
     * "Bitrate: 320 kbps" in una sola fermata. Le righe senza valore non compaiono.
     */
    private void infoRow(LinearLayout col, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(12), 0, px(12), 0);
        r.setFocusable(true);
        android.graphics.drawable.GradientDrawable on = new android.graphics.drawable.GradientDrawable();
        on.setCornerRadius(10 * u);
        on.setColor(0x33FFFFFF);
        on.setStroke(Math.max(2, px(2)), 0xFFFFFFFF);
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, on);
        r.setBackground(sl);
        TextView l = A11y.hide(text(17, "sans-serif", 0xB3FFFFFF));
        l.setText(label);
        TextView v = A11y.hide(text(19, "sans-serif", 0xFFFFFFFF));
        v.setText(value);
        r.addView(l, new LinearLayout.LayoutParams(px(140), -2));
        r.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setContentDescription(label + ": " + value);
        col.addView(r, new LinearLayout.LayoutParams(-1, px(38)));
    }

    /** "2026-10-27T13:06:34Z" -> "27 ottobre 2026". */
    private static String date(String iso) {
        if (iso == null || iso.length() < 10) return "";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(iso.substring(0, 10));
            return new SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN).format(d);
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------ in riproduzione

    private void updateProgress() {
        if (!mt.hasTrack()) return;
        long pos = mt.position();
        progress.set(mt.duration > 0 ? pos / (float) mt.duration : 0f, A11y.spokenTime(act, pos / 1000),
                mt.duration > 0 ? A11y.spokenTime(act, mt.duration / 1000) : "");
        setIfChanged(tCur, MusicItem.clock(Math.max(1, pos)));
        setIfChanged(tEnd, MusicItem.clock(mt.duration));
    }

    /** Il testo si tocca solo se cambia: niente ridisegni ed eventi a vuoto a ogni scatto dell'orologio. */
    private static void setIfChanged(TextView t, String s) {
        if (!s.contentEquals(t.getText())) t.setText(s);
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = act.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    private boolean installed(String pkg) {
        return act.getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    private void bind() {
        boolean track = mt.hasTrack();
        seekBox.setVisibility(track ? VISIBLE : INVISIBLE);
        controls.setVisibility(track ? VISIBLE : GONE);
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
        boolean actionShown = false;
        MusicItem local = track && mt.isLocal() ? player.current() : null;
        info.setVisibility(local != null ? VISIBLE : GONE);
        fillBadges(local);
        if (track) {
            title.setText(mt.title);
            String sub = mt.artist;
            if (!mt.album.isEmpty()) sub = sub.isEmpty() ? mt.album : sub + "  ·  " + mt.album;
            if (local != null && local.year > 0) sub = sub + (sub.isEmpty() ? "" : "  ·  ") + local.year;
            if (!mt.isLocal()) sub = sub + (sub.isEmpty() ? "" : "  ·  ") + appLabel(mt.pkg); // da dove arriva, se non e' Jellyfin
            subtitle.setText(sub);
            play.setPlaying(mt.playing());
            next.setEnabled(true);
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
        } else {
            lastKey = "";
            title.setText("Niente in riproduzione");
            if (!mt.hasAccess()) {
                subtitle.setText("Per vedere qui anche Spotify, concedi l'accesso alle notifiche");
                action.setText("Concedi accesso");
                action.setOnClickListener(v -> openAccessSettings());
                actionShown = true;
            } else if (installed("com.spotify.tv.android")) {
                subtitle.setText(hasLibrary() ? "Scegli un brano qui sotto, o avvia Spotify" : "Avvia Spotify e riproduci un brano");
                action.setText("Apri Spotify");
                action.setOnClickListener(v -> openApp("com.spotify.tv.android"));
                actionShown = true;
            } else {
                subtitle.setText(hasLibrary() ? "Scegli un brano qui sotto" : "");
            }
        }
        action.setVisibility(actionShown ? VISIBLE : GONE);
        if (focusLost()) focusDefault();
    }

    /** Il focus e' sparito (era su un pulsante ora nascosto): va rimesso. Se e' nella barra in alto, resta li'. */
    private boolean focusLost() {
        if (!isShown() || !stack.isEmpty()) return false;
        View f = act.getCurrentFocus();
        return f == null || !f.isShown();
    }

    /**
     * Dove mettere il focus entrando: il primo brano della libreria, se no i controlli.
     * FromTouch: anche dopo un tocco o il puntatore il contenuto prende il focus (come nella tab Film).
     */
    private void focusDefault() {
        View t = firstTile();
        if (t != null) t.requestFocusFromTouch();
        else if (mt.hasTrack()) play.requestFocusFromTouch();
        else if (action.getVisibility() == VISIBLE) action.requestFocusFromTouch();
        else if (statusBtns.getChildCount() > 0) statusBtns.getChildAt(0).requestFocusFromTouch();
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

    // ------------------------------------------------------------ libreria: caricamento

    private boolean hasLibrary() {
        return scroll.getVisibility() == VISIBLE && rowsBox.getChildCount() > 0;
    }

    /** Messaggio al posto delle righe, con fino a due pulsanti (il primo e' l'azione principale). */
    private void showStatus(String msg, Pill... btns) {
        scroll.setVisibility(GONE);
        status.setVisibility(VISIBLE);
        status.setText(msg);
        statusBtns.removeAllViews();
        for (Pill p : btns) if (p != null) addBtn(statusBtns, p);
        statusBtns.setVisibility(btns.length > 0 ? VISIBLE : GONE);
        bind();
        boolean inside = findFocus() != null;
        if ((inside || focusLost()) && stack.isEmpty() && statusBtns.getChildCount() > 0) statusBtns.getChildAt(0).requestFocusFromTouch();
    }

    private void needLogin(String msg) {
        loadedKey = "";
        rowsBox.removeAllViews();
        showStatus(msg, pill("Collega Jellyfin", true, () -> {
            if (onNeedLogin != null) onNeedLogin.run();
        }));
    }

    /** Ricarica la libreria a ogni ingresso nella tab (gli "ascoltati di recente" cambiano mentre si ascolta). */
    private void loadLibrary() {
        jelly = new Jelly(act); // l'accesso puo' essere cambiato nella tab Film
        if (!jelly.loggedIn()) {
            needLogin("Collega il tuo server Jellyfin per ascoltare la tua musica");
            return;
        }
        String key = jelly.server + "|" + jelly.userId;
        final boolean quiet = key.equals(loadedKey) && rowsBox.getChildCount() > 0;
        if (!quiet) showStatus("Carico la tua musica…");
        final String focusId = quiet && findFocus() instanceof MusicViews.Tile ? ((MusicViews.Tile) findFocus()).item.id : null;
        pending = 4;
        loadError = null;
        loadUnauth = false;
        final List<MusicItem> d = new ArrayList<>(), r = new ArrayList<>(), al = new ArrayList<>(), ar = new ArrayList<>();
        rDaily = d;
        rRecent = r;
        rAlbums = al;
        rArtists = ar;
        final Jelly j = jelly;
        daily(j, collect(d, j, key, focusId, quiet));
        j.recentSongs(collect(r, j, key, focusId, quiet));
        j.albums(collect(al, j, key, focusId, quiet));
        j.artists(collect(ar, j, key, focusId, quiet));
    }

    private Jelly.Done<List<MusicItem>> collect(final List<MusicItem> into, final Jelly j, final String key,
                                                final String focusId, final boolean quiet) {
        return new Jelly.Done<List<MusicItem>>() {
            @Override
            public void ok(List<MusicItem> v) {
                into.addAll(v);
                finish();
            }

            @Override
            public void fail(String message, boolean unauthorized) {
                loadError = message;
                loadUnauth |= unauthorized;
                finish();
            }

            private void finish() {
                if (--pending > 0 || j != jelly) return; // risposta di un caricamento superato
                if (loadUnauth) {
                    if (!triedRelogin && j.hasSavedPassword()) {
                        // token scaduto: rientra da solo con la password salvata (un solo tentativo)
                        triedRelogin = true;
                        j.relogin(new Jelly.Done<Boolean>() {
                            @Override
                            public void ok(Boolean v) {
                                loadLibrary();
                            }

                            @Override
                            public void fail(String message, boolean unauthorized) {
                                needLogin("Accesso a Jellyfin scaduto: accedi di nuovo");
                            }
                        });
                    } else {
                        needLogin("Accesso a Jellyfin scaduto: accedi di nuovo");
                    }
                    return;
                }
                triedRelogin = false;
                if (rAlbums.isEmpty() && rDaily.isEmpty()) {
                    loadedKey = "";
                    rowsBox.removeAllViews();
                    if (loadError != null) {
                        showStatus("Jellyfin non risponde: " + loadError, pill("Riprova", true, () -> loadLibrary()));
                    } else {
                        showStatus("Non c'è musica su questo server Jellyfin. Aggiungi una libreria Musica e riprova.",
                                pill("Riprova", true, () -> loadLibrary()));
                    }
                    return;
                }
                loadedKey = key;
                build(focusId, quiet);
            }
        };
    }

    /**
     * Brani del giorno: 6 scelti a caso dal server la prima volta del giorno, poi gli stessi fino a mezzanotte
     * (salvati per server e utente). Se qualcuno sparisce dal server se ne scelgono altri.
     */
    private void daily(final Jelly j, final Jelly.Done<List<MusicItem>> cb) {
        final SharedPreferences sp = act.getSharedPreferences("musiclib", Context.MODE_PRIVATE);
        final String who = j.server + "|" + j.userId + "|";
        final String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        String saved = sp.getString("daily", "");
        final Jelly.Done<List<MusicItem>> pickNew = new Jelly.Done<List<MusicItem>>() {
            @Override
            public void ok(List<MusicItem> v) {
                StringBuilder ids = new StringBuilder();
                for (MusicItem m : v) ids.append(ids.length() > 0 ? "," : "").append(m.id);
                sp.edit().putString("daily", who + today + "|" + ids).apply();
                cb.ok(v);
            }

            @Override
            public void fail(String message, boolean unauthorized) {
                cb.fail(message, unauthorized);
            }
        };
        if (saved.startsWith(who + today + "|")) {
            String list = saved.substring((who + today + "|").length());
            final List<String> ids = list.isEmpty() ? new ArrayList<>() : Arrays.asList(list.split(","));
            if (!ids.isEmpty()) {
                j.songsById(ids, new Jelly.Done<List<MusicItem>>() {
                    @Override
                    public void ok(List<MusicItem> v) {
                        if (v.size() < ids.size()) j.randomSongs(DAILY, pickNew);
                        else cb.ok(v);
                    }

                    @Override
                    public void fail(String message, boolean unauthorized) {
                        cb.fail(message, unauthorized);
                    }
                });
                return;
            }
        }
        j.randomSongs(DAILY, pickNew);
    }

    // ------------------------------------------------------------ libreria: righe

    private void build(String focusId, boolean quiet) {
        View had = findFocus();
        boolean focusInRows = had != null && isInside(had, rowsBox);
        rowsBox.removeAllViews();
        lastTile = null;
        if (!rDaily.isEmpty()) section("Brani del giorno", rDaily, rDaily.size());
        if (!rRecent.isEmpty()) section("Ascoltati di recente", rRecent, rRecent.size());
        if (!rAlbums.isEmpty()) section("Album  ·  " + rAlbums.size(), rAlbums, rAlbums.size());
        if (!rArtists.isEmpty()) section("Artisti  ·  " + rArtists.size(), rArtists, rArtists.size());
        View spacer = new View(act);
        rowsBox.addView(spacer, new LinearLayout.LayoutParams(-1, px(24)));
        scroll.setVisibility(VISIBLE);
        status.setVisibility(GONE);
        statusBtns.removeAllViews();
        statusBtns.setVisibility(GONE);
        markCurrent(rowsBox);
        bind();
        if (!isShown() || !stack.isEmpty()) return;
        // focus: sul riquadro di prima se c'e' ancora, se no sul primo; non si ruba ai controlli in alto
        View target = focusId != null ? findTile(rowsBox, focusId) : null;
        if (target == null && (!quiet || focusInRows || findFocus() == null)) target = firstTile();
        if (target != null && (!quiet || focusInRows || findFocus() == null || !findFocus().isShown())) target.requestFocusFromTouch();
    }

    private void section(String label, final List<MusicItem> list, final int total) {
        final TextView t = A11y.heading(text(22, "sans-serif-medium", 0xE6FFFFFF));
        t.setText(label);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = px(80);
        tp.topMargin = px(6);
        rowsBox.addView(t, tp);
        rowsBox.addView(tileRow(list, total, t, scroll), new LinearLayout.LayoutParams(-1, -2));
    }

    /** Riga scorrevole di riquadri; heading/vscroll: titolo da tenere visibile scorrendo in verticale (null = niente). */
    private Row tileRow(final List<MusicItem> list, final int total, final TextView heading, final ScrollView vscroll) {
        final LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(px(80), px(16), px(80), px(22));
        final Row row = new Row(act);
        row.addView(content, new LayoutParams(-2, -2));
        int idx = 0;
        for (final MusicItem m : list) {
            final int i = idx++;
            String sub = m.kind == MusicItem.ARTIST ? "" : m.kind == MusicItem.ALBUM
                    ? (m.year > 0 ? m.artist + "  ·  " + m.year : m.artist) : m.artist;
            final MusicViews.Tile tile = new MusicViews.Tile(act, m, sub, jelly.cover(m, 300), px(TILE), u);
            describe(tile, m, i, total);
            tile.setOnClickListener(v -> {
                if (m.kind == MusicItem.SONG) player.playList(list, i);
                else if (m.kind == MusicItem.ALBUM) openAlbum(m, tile);
                else openArtist(m, tile);
            });
            tile.setOnFocusChangeListener((v, f) -> {
                if (!f) return;
                if (vscroll == scroll) lastTile = tile;
                if (heading != null && vscroll != null) vscroll.post(() -> vscroll.smoothScrollTo(0, heading.getTop() - px(6)));
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(TILE), MusicViews.Tile.height(px(TILE), u, act));
            lp.rightMargin = px(26);
            content.addView(tile, lp);
        }
        // copertine caricate solo vicino alla parte visibile (memoria limitata sulle TV)
        row.setOnScrollChangeListener((v, x, y, ox, oy) -> near(content, x, row.getWidth()));
        row.post(() -> near(content, row.getScrollX(), row.getWidth()));
        return row;
    }

    private void near(LinearLayout content, int scrollX, int width) {
        int w = width > 0 ? width : getResources().getDisplayMetrics().widthPixels;
        int from = scrollX - w, to = scrollX + 2 * w;
        for (int k = 0; k < content.getChildCount(); k++) {
            View c = content.getChildAt(k);
            if (c instanceof MusicViews.Tile) ((MusicViews.Tile) c).near(c.getRight() > from && c.getLeft() < to);
        }
    }

    /** "Titolo, artista, 2 di 6": una sola fermata con tutto quello che serve. */
    private void describe(final View v, MusicItem m, final int index, int total) {
        StringBuilder sb = new StringBuilder();
        if (m.kind == MusicItem.ALBUM) sb.append(act.getString(R.string.a11y_album, m.name));
        else if (m.kind == MusicItem.ARTIST) sb.append(act.getString(R.string.a11y_artist, m.name));
        else sb.append(m.name);
        if (m.kind != MusicItem.ARTIST && !m.artist.isEmpty()) sb.append(", ").append(m.artist);
        if (m.kind == MusicItem.ALBUM && m.year > 0) sb.append(", ").append(m.year);
        if (m.kind == MusicItem.SONG && m.runtimeTicks > 0) sb.append(", ").append(A11y.spokenTime(act, m.durationMs() / 1000));
        sb.append(", ").append(act.getString(R.string.a11y_position, index + 1, total));
        v.setContentDescription(sb.toString());
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCollectionItemInfo(android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, index, 1, false));
            }
        });
    }

    private View firstTile() {
        return find(rowsBox, v -> v instanceof MusicViews.Tile);
    }

    private View findTile(View root, String id) {
        return find(root, v -> v instanceof MusicViews.Tile && ((MusicViews.Tile) v).item.id.equals(id));
    }

    private interface Test {
        boolean ok(View v);
    }

    private static View find(View v, Test t) {
        if (t.ok(v)) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = find(g.getChildAt(i), t);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static boolean isInside(View v, View container) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == container) return true;
        }
        return false;
    }

    /** Segna il brano che suona nei riquadri e nelle righe delle tracce sotto root. */
    private void markCurrent(View root) {
        MusicItem c = player.current();
        String id = c == null ? "" : c.id;
        if (root instanceof MusicViews.Tile) {
            MusicViews.Tile t = (MusicViews.Tile) root;
            t.setCurrent(t.item.kind == MusicItem.SONG && t.item.id.equals(id));
        } else if (root instanceof MusicViews.TrackRow) {
            MusicViews.TrackRow r = (MusicViews.TrackRow) root;
            boolean now = r.item.id.equals(id);
            if (now != r.isCurrent()) {
                r.setCurrent(now);
                r.setContentDescription(trackSpoken(r.item, (Integer) r.getTag(), now));
            }
        } else if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) markCurrent(g.getChildAt(i));
        }
    }

    /**
     * Su e giu' fra le due zone: dai controlli in alto si scende sull'ultimo riquadro visitato (non su quello che
     * capita sotto il pulsante), dalla prima riga si sale ai controlli.
     */
    @Override
    public View focusSearch(View focused, int dir) {
        if (stack.isEmpty() && focused != null) {
            boolean inTop = isInside(focused, controls) || isInside(focused, seekBox) || focused == action;
            if (dir == FOCUS_DOWN && inTop && hasLibrary()) {
                View t = lastTile != null && lastTile.isAttachedToWindow() ? lastTile : firstTile();
                if (t != null) return t;
            }
            if (dir == FOCUS_UP && focused instanceof MusicViews.Tile && rowsBox.getChildCount() > 1
                    && isInside(focused, rowsBox.getChildAt(1))) {
                if (controls.getVisibility() == VISIBLE) return play;
                if (action.getVisibility() == VISIBLE) return action;
            }
        }
        return super.focusSearch(focused, dir);
    }

    // ------------------------------------------------------------ schede

    /** Nuova scheda a tutto schermo sopra la libreria (o sopra la scheda precedente). */
    private FrameLayout push(String paneTitle, View from, Bitmap backdrop) {
        FrameLayout d = new FrameLayout(act);
        d.setClipChildren(false);
        d.setAccessibilityPaneTitle(paneTitle);
        MusicViews.Bg dbg = new MusicViews.Bg(act, null);
        d.addView(A11y.hide(dbg), new LayoutParams(-1, -1));
        Bitmap b = null;
        if (backdrop != null) {
            try {
                b = Blur.fromBitmap(backdrop, 96, 54, 5);
            } catch (Throwable ignored) {
            }
        }
        dbg.set(b != null ? b : homeBackdrop);
        d.setTag(dbg);
        stack.add(d);
        stackFrom.add(from);
        addView(d, new LayoutParams(-1, -1));
        applyA11y();
        return d;
    }

    private void pop(boolean refocus) {
        FrameLayout d = stack.remove(stack.size() - 1);
        View from = stackFrom.remove(stackFrom.size() - 1);
        removeView(d);
        applyA11y();
        if (!refocus) return;
        // la libreria puo' essere stata ricaricata mentre la scheda era aperta: si cerca lo stesso elemento nelle righe nuove
        if (from instanceof MusicViews.Tile && !from.isAttachedToWindow() && stack.isEmpty()) {
            from = findTile(rowsBox, ((MusicViews.Tile) from).item.id);
        }
        if (from != null && from.isAttachedToWindow()) from.requestFocusFromTouch();
        else if (stack.isEmpty()) focusDefault();
        else stack.get(stack.size() - 1).requestFocusFromTouch();
    }

    /** Solo la scheda in cima e' visibile al lettore di schermo; senza schede torna tutto com'era. */
    private void applyA11y() {
        View top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        // la copertina in alto ha un'ombra (quota Z): senza nasconderla passerebbe sopra la scheda
        cover.setVisibility(top == null ? VISIBLE : INVISIBLE);
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (top == null || c == top) {
                Integer m = a11yModes.remove(c);
                if (m != null) c.setImportantForAccessibility(m);
            } else {
                if (!a11yModes.containsKey(c)) a11yModes.put(c, c.getImportantForAccessibility());
                c.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
    }

    private static Bitmap imageOf(View from) {
        return from instanceof MusicViews.Tile ? ((MusicViews.Tile) from).image() : null;
    }

    private MusicViews.Cover bigCover(FrameLayout d, MusicItem m, int left, int top, int size, boolean person) {
        final MusicViews.Cover c = new MusicViews.Cover(act, 14 * u);
        if (person) c.setPerson(m.name);
        c.setElevation(14 * u);
        final float rad = person ? size * u / 2f : 14 * u;
        c.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad);
            }
        });
        d.addView(A11y.hide(c), at(left, top, size, size)); // decorativa: il nome e' nel titolo
        String url = jelly.cover(m, 520);
        if (url != null) Img.load(url, c::set);
        return c;
    }

    private static List<MusicItem> shuffled(List<MusicItem> l) {
        List<MusicItem> s = new ArrayList<>(l);
        Collections.shuffle(s);
        return s;
    }

    private String countLabel(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    /** "Traccia 3, Titolo, artista ospite, 3 minuti, in riproduzione". */
    private String trackSpoken(MusicItem t, int number, boolean playing) {
        StringBuilder sb = new StringBuilder(act.getString(R.string.a11y_track, number, t.name));
        if (t.runtimeTicks > 0) sb.append(", ").append(A11y.spokenTime(act, t.durationMs() / 1000));
        if (playing) sb.append(", ").append(act.getString(R.string.a11y_state_playing));
        return sb.toString();
    }

    private void openAlbum(final MusicItem album, View from) {
        final FrameLayout d = push(album.name, from, imageOf(from));
        bigCover(d, album, 80, 128, 260, false);
        TextView t = A11y.heading(text(40, "sans-serif-light", 0xFFFFFFFF));
        t.setText(album.name);
        d.addView(t, at(380, 126, 820, 52));
        final TextView meta = text(20, "sans-serif", 0xE6FFFFFF);
        meta.setText(album.year > 0 ? album.artist + "   ·   " + album.year : album.artist);
        d.addView(meta, at(380, 186, 820, 26));

        final List<MusicItem> tracks = new ArrayList<>();
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setClipChildren(false);
        final Pill playAll = pill("Riproduci", true, () -> {
            if (!tracks.isEmpty()) player.playList(tracks, 0);
        });
        addBtn(bar, playAll);
        addBtn(bar, pill("Casuale", false, () -> {
            if (!tracks.isEmpty()) player.playList(shuffled(tracks), 0);
        }));
        boolean fromArtist = stack.size() > 1; // aperto dalla scheda dell'artista: Indietro ci torna gia'
        if (!album.artistId.isEmpty() && !fromArtist) {
            final MusicItem artist = new MusicItem();
            artist.kind = MusicItem.ARTIST;
            artist.id = album.artistId;
            artist.name = album.artist;
            final View[] self = new View[1]; // Indietro dalla scheda dell'artista torna su questo pulsante
            Pill more = pill("Altro di " + album.artist, false, () -> openArtist(artist, self[0]));
            self[0] = more;
            addBtn(bar, more);
        }
        d.addView(bar, at(380, 228, 840, 48));

        ScrollView list = new ScrollView(act);
        list.setFocusable(false);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(0, px(4), 0, px(24));
        final LinearLayout rows = new LinearLayout(act);
        rows.setOrientation(LinearLayout.VERTICAL);
        list.addView(rows, new LayoutParams(-1, -2));
        FrameLayout clip = new FrameLayout(act);
        clip.addView(list, new LayoutParams(-1, -1));
        d.addView(clip, at(368, 294, 852, 426));
        final TextView loading = A11y.live(text(20, "sans-serif-light", 0xCCFFFFFF));
        loading.setText("Carico i brani…");
        rows.addView(loading, new LinearLayout.LayoutParams(-1, px(48)));

        playAll.requestFocusFromTouch();
        d.announceForAccessibility(act.getString(R.string.a11y_album_opened, album.name, album.artist));
        jelly.albumTracks(album.id, new Jelly.Done<List<MusicItem>>() {
            @Override
            public void ok(List<MusicItem> v) {
                if (!stack.contains(d)) return;
                tracks.addAll(v);
                rows.removeAllViews();
                long ms = 0;
                for (int i = 0; i < v.size(); i++) {
                    final int k = i;
                    MusicItem tr = v.get(i);
                    ms += tr.durationMs();
                    int number = tr.index > 0 ? tr.index : i + 1;
                    String extra = !tr.artist.isEmpty() && !tr.artist.equals(album.artist) ? tr.artist : "";
                    MusicViews.TrackRow r = new MusicViews.TrackRow(act, tr, number, extra, u);
                    r.setTag(number);
                    r.setContentDescription(trackSpoken(tr, number, false));
                    r.setOnClickListener(x -> player.playList(tracks, k));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(52));
                    lp.bottomMargin = px(4);
                    lp.leftMargin = px(12);
                    lp.rightMargin = px(12);
                    rows.addView(r, lp);
                }
                if (v.isEmpty()) {
                    TextView none = text(20, "sans-serif-light", 0xCCFFFFFF);
                    none.setText("Nessun brano in questo album");
                    rows.addView(none, new LinearLayout.LayoutParams(-1, px(48)));
                }
                String base = album.year > 0 ? album.artist + "   ·   " + album.year : album.artist;
                long min = Math.round(ms / 60000.0);
                meta.setText(base + "   ·   " + countLabel(v.size(), "brano", "brani") + (min > 0 ? "   ·   " + min + " min" : ""));
                markCurrent(rows);
            }

            @Override
            public void fail(String message, boolean unauthorized) {
                if (!stack.contains(d)) return;
                loading.setText(unauthorized ? "Accesso a Jellyfin scaduto: accedi di nuovo dalla tab Film" : "Brani non disponibili: " + message);
            }
        });
    }

    private void openArtist(final MusicItem artist, View from) {
        final FrameLayout d = push(artist.name, from, imageOf(from));
        bigCover(d, artist, 80, 128, 200, true);
        TextView t = A11y.heading(text(40, "sans-serif-light", 0xFFFFFFFF));
        t.setText(artist.name);
        d.addView(t, at(320, 142, 880, 52));
        final TextView meta = text(20, "sans-serif", 0xE6FFFFFF);
        d.addView(meta, at(320, 202, 880, 26));

        final List<MusicItem> songs = new ArrayList<>();
        final boolean[] wantShuffle = {false, false}; // {richiesto, casuale}: si suona appena arrivano i brani
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setClipChildren(false);
        final Pill playAll = pill("Riproduci", true, () -> {
            if (!songs.isEmpty()) player.playList(songs, 0);
            else wantShuffle[0] = true;
        });
        addBtn(bar, playAll);
        addBtn(bar, pill("Casuale", false, () -> {
            if (!songs.isEmpty()) player.playList(shuffled(songs), 0);
            else {
                wantShuffle[0] = true;
                wantShuffle[1] = true;
            }
        }));
        d.addView(bar, at(320, 244, 880, 48));

        final TextView head = A11y.heading(text(22, "sans-serif-medium", 0xE6FFFFFF));
        head.setText("Album");
        d.addView(head, at(80, 368, 600, 30));
        final FrameLayout rowBox = new FrameLayout(act);
        rowBox.setClipChildren(false);
        d.addView(rowBox, at(0, 392, 1280, -2));

        playAll.requestFocusFromTouch();
        d.announceForAccessibility(act.getString(R.string.a11y_artist_opened, artist.name));
        jelly.artistAlbums(artist.id, new Jelly.Done<List<MusicItem>>() {
            @Override
            public void ok(List<MusicItem> v) {
                if (!stack.contains(d)) return;
                rowBox.addView(tileRow(v, v.size(), null, null), new LayoutParams(-1, -2));
                if (v.isEmpty()) head.setText("Nessun album");
                updateMeta(meta, v.size(), songs.size());
                meta.setTag(v.size());
            }

            @Override
            public void fail(String message, boolean unauthorized) {
                if (stack.contains(d)) head.setText("Album non disponibili: " + message);
            }
        });
        jelly.artistSongs(artist.id, new Jelly.Done<List<MusicItem>>() {
            @Override
            public void ok(List<MusicItem> v) {
                if (!stack.contains(d)) return;
                songs.addAll(v);
                updateMeta(meta, meta.getTag() instanceof Integer ? (Integer) meta.getTag() : -1, v.size());
                if (wantShuffle[0] && !v.isEmpty()) player.playList(wantShuffle[1] ? shuffled(v) : v, 0);
            }

            @Override
            public void fail(String message, boolean unauthorized) {
            }
        });
    }

    private void updateMeta(TextView meta, int albums, int songs) {
        List<String> parts = new ArrayList<>();
        if (albums >= 0) parts.add(countLabel(albums, "album", "album"));
        if (songs > 0) parts.add(countLabel(songs, "brano", "brani"));
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(sb.length() > 0 ? "   ·   " : "").append(p);
        meta.setText(sb.toString());
    }
}
