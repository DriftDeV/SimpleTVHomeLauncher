package dev.drift.homelauncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Player a schermo intero (MediaPlayer di sistema, decoder hardware della TV). Il server sceglie fra direct play
 * e transcodifica in base al profilo dispositivo; audio e sottotitoli si cambiano riavviando dalla posizione corrente.
 * Riporta posizione e fine al server, cosi' "continua a guardare" funziona.
 */
public class PlayerActivity extends Activity implements SurfaceHolder.Callback {
    private static final long SEEK_TICKS = 10L * 10000000L;
    private static final int HIDE_MS = 5000;

    private float u;
    private Jelly jelly;
    private Movie movie = new Movie();
    private Jelly.PlayInfo info;
    private MediaPlayer mp;
    private SurfaceView surface;
    private FrameLayout root;
    private View overlay;
    private TextView titleView, tCur, tEnd, message;
    private MusicViews.Progress progress;
    private MusicViews.Ctl rew, play, fwd;
    private TextView seekNote;
    private long lastSeekAt;
    private int seekStreak;
    private Pill audioBtn, subBtn;
    private boolean prepared, surfaceReady, overlayOn, forceTranscode, started;
    private long startTicks, runtimeTicks;
    private int audioIdx = -1, subIdx = -1;
    private int videoW, videoH;
    private final Handler h = new Handler();
    private final Runnable hide = () -> showOverlay(false);
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            h.postDelayed(this, 500);
        }
    };
    private final Runnable progressReport = new Runnable() {
        @Override
        public void run() {
            if (prepared && info != null) jelly.reportProgress(movie, info, ticks(), !mp.isPlaying());
            h.postDelayed(this, 10000);
        }
    };

    private int px(float v) {
        return Math.round(v * u);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        u = getResources().getDisplayMetrics().heightPixels / 720f;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        jelly = new Jelly(this);
        movie.id = getIntent().getStringExtra("id");
        movie.name = String.valueOf(getIntent().getStringExtra("title"));
        String src = getIntent().getStringExtra("source");
        movie.mediaSourceId = src == null || src.isEmpty() ? movie.id : src;
        setTitle(movie.name);
        startTicks = getIntent().getLongExtra("start", 0);
        runtimeTicks = getIntent().getLongExtra("runtime", 0);
        audioIdx = getIntent().getIntExtra("audio", -1);
        subIdx = getIntent().getIntExtra("sub", -1);
        buildUi();
    }

    // ------------------------------------------------------------ interfaccia

    private TextView text(float size, String face, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u * A11y.textScale(this));
        t.setTypeface(Typeface.create(face, Typeface.NORMAL));
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private FrameLayout.LayoutParams at(int left, int top, int w, int hh) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? w : px(w), hh < 0 ? hh : px(hh));
        lp.leftMargin = px(left);
        lp.topMargin = px(top);
        return lp;
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, -1);
        sp.gravity = Gravity.CENTER;
        root.addView(surface, sp);

        message = A11y.live(text(26, "sans-serif-light", 0xFFFFFFFF)); // "Carico…" / errori: letti da soli
        message.setGravity(Gravity.CENTER);
        message.setSingleLine(false);
        root.addView(message, at(240, 300, 800, 120));

        FrameLayout ov = new FrameLayout(this);
        overlay = ov;
        View scrimTop = new View(this);
        scrimTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC000000, 0x00000000}));
        ov.addView(scrimTop, at(0, 0, 1280, 160));
        View scrimBot = new View(this);
        scrimBot.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xEE000000, 0x00000000}));
        ov.addView(scrimBot, at(0, 470, 1280, 250));

        titleView = A11y.heading(text(32, "sans-serif-light", 0xFFFFFFFF));
        titleView.setText(movie.name);
        ov.addView(titleView, at(80, 44, 1120, 40));

        LinearLayout seek = new LinearLayout(this);
        seek.setOrientation(LinearLayout.HORIZONTAL);
        seek.setGravity(Gravity.CENTER_VERTICAL);
        seek.setClipChildren(false);
        tCur = A11y.hide(text(18, "sans-serif", 0xE6FFFFFF)); // il valore lo dice la barra
        tCur.setGravity(Gravity.RIGHT);
        tEnd = A11y.hide(text(18, "sans-serif", 0xE6FFFFFF));
        progress = new MusicViews.Progress(this);
        progress.setSeeker(dir -> stepSeek(dir)); // azioni di accessibilita': scorri avanti/indietro
        seek.addView(tCur, new LinearLayout.LayoutParams(px(86), -2));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, px(24), 1f);
        pl.leftMargin = px(14);
        pl.rightMargin = px(14);
        seek.addView(progress, pl);
        seek.addView(tEnd, new LinearLayout.LayoutParams(px(86), -2));
        ov.addView(seek, at(120, 548, 1040, 30));

        LinearLayout ctl = new LinearLayout(this);
        ctl.setOrientation(LinearLayout.HORIZONTAL);
        ctl.setGravity(Gravity.CENTER);
        ctl.setClipChildren(false);
        rew = new MusicViews.Ctl(this, 3);
        play = new MusicViews.Ctl(this, 1);
        fwd = new MusicViews.Ctl(this, 4);
        rew.setOnClickListener(v -> stepSeek(-1));
        play.setOnClickListener(v -> togglePlay());
        fwd.setOnClickListener(v -> stepSeek(1));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(px(58), px(58));
        l1.rightMargin = px(30);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(px(74), px(74));
        l2.rightMargin = px(30);
        ctl.addView(rew, l1);
        ctl.addView(play, l2);
        ctl.addView(fwd, new LinearLayout.LayoutParams(px(58), px(58)));
        ov.addView(ctl, at(190, 594, 900, 80));

        audioBtn = new Pill(this, "Audio", u);
        subBtn = new Pill(this, "Sottotitoli", u);
        audioBtn.setOnClickListener(v -> trackMenu(true));
        subBtn.setOnClickListener(v -> trackMenu(false));
        // audio e sottotitoli subito a destra dei controlli, non in un angolo lontano
        LinearLayout.LayoutParams t1 = new LinearLayout.LayoutParams(-2, px(48));
        t1.leftMargin = px(60);
        ctl.addView(audioBtn, t1);
        LinearLayout.LayoutParams t2 = new LinearLayout.LayoutParams(-2, px(48));
        t2.leftMargin = px(12);
        ctl.addView(subBtn, t2);

        // feedback dei salti (+10 s / -10 s) al centro dello schermo
        seekNote = A11y.live(text(40, "sans-serif-medium", 0xFFFFFFFF)); // "+10 s": annunciato
        seekNote.setGravity(Gravity.CENTER);
        seekNote.setVisibility(View.GONE);
        ov.addView(seekNote, at(440, 300, 400, 56));

        root.addView(ov, new FrameLayout.LayoutParams(-1, -1));
        overlay.setVisibility(View.GONE);
        setContentView(root);
    }

    private void showOverlay(boolean on) {
        h.removeCallbacks(hide);
        overlayOn = on;
        overlay.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            update();
            if (getCurrentFocus() == null || !(getCurrentFocus() instanceof MusicViews.Ctl || getCurrentFocus() instanceof Pill)) {
                play.requestFocus();
            }
            armHide();
        }
    }

    /** Senza lettore di schermo i controlli si nascondono dopo 5 s; con una tecnologia assistiva restano finche' non si chiudono. */
    private void armHide() {
        h.removeCallbacks(hide);
        if (!A11y.enabled(this)) h.postDelayed(hide, HIDE_MS);
    }

    // ------------------------------------------------------------ riproduzione

    private long ticks() {
        return mp != null && prepared ? mp.getCurrentPosition() * 10000L : 0;
    }

    private void start() {
        if (started || !surfaceReady) return;
        started = true;
        load(startTicks, audioIdx, subIdx);
    }

    /** (Ri)carica il flusso dalla posizione indicata, con le tracce scelte. */
    private void load(final long from, int audio, int sub) {
        releasePlayer();
        message.setVisibility(View.VISIBLE);
        message.setText("Carico…");
        audioIdx = audio;
        subIdx = sub;
        final long pos = from;
        jelly.playbackInfo(movie.id, movie.mediaSourceId, pos, audio, sub, forceTranscode, new Jelly.Done<Jelly.PlayInfo>() {
            @Override
            public void ok(Jelly.PlayInfo pi) {
                info = pi;
                if (!pi.mediaSourceId.isEmpty()) movie.mediaSourceId = pi.mediaSourceId;
                if (audioIdx < 0) audioIdx = pi.audioIdx;
                prepare(pos);
            }

            @Override
            public void fail(String m, boolean unauthorized) {
                error(unauthorized ? "Sessione scaduta: riapri la tab Film" : m);
            }
        });
    }

    private void prepare(final long from) {
        try {
            mp = new MediaPlayer();
            mp.setDisplay(surface.getHolder());
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(info.url);
            mp.setOnVideoSizeChangedListener((m, w, hh) -> fit(w, hh));
            mp.setOnPreparedListener(m -> {
                prepared = true;
                message.setVisibility(View.GONE);
                // direct play: il file arriva intero e il server ignora la traccia scelta, la sceglie il player
                if (info.direct && !selectAudio(audioIdx)) {
                    forceTranscode = true;
                    load(from, audioIdx, subIdx);
                    return;
                }
                // sia il file sia la playlist HLS partono da 0:00: si salta alla posizione, dopo start (prima HLS lo ignora)
                m.start();
                if (from > 0) m.seekTo((int) (from / 10000L));
                jelly.reportStart(movie, info, from);
                h.removeCallbacks(progressReport);
                h.postDelayed(progressReport, 10000);
                h.removeCallbacks(tick);
                h.post(tick);
                showOverlay(true);
            });
            mp.setOnCompletionListener(m -> {
                jelly.reportStopped(movie, info, runtimeTicks > 0 ? runtimeTicks : ticks());
                finish();
            });
            mp.setOnErrorListener((m, what, extra) -> {
                if (info != null && info.direct && !forceTranscode) {
                    // il file non parte in direct play: riprova una volta chiedendo la transcodifica al server
                    forceTranscode = true;
                    load(prepared ? ticks() : from, audioIdx, subIdx);
                } else {
                    error("Impossibile riprodurre (errore " + what + "/" + extra + ")");
                }
                return true;
            });
            mp.prepareAsync();
        } catch (Throwable t) {
            error("Impossibile riprodurre: " + t.getMessage());
        }
    }

    private void fit(int w, int hh) {
        if (w <= 0 || hh <= 0) return;
        videoW = w;
        videoH = hh;
        int sw = root.getWidth(), sh = root.getHeight();
        if (sw <= 0 || sh <= 0) return;
        float sc = Math.min(sw / (float) w, sh / (float) hh);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(w * sc), Math.round(hh * sc));
        lp.gravity = Gravity.CENTER;
        surface.setLayoutParams(lp);
    }

    private void error(String msg) {
        message.setVisibility(View.VISIBLE);
        message.setText(msg + "\nPremi Indietro per tornare.");
    }

    private void releasePlayer() {
        h.removeCallbacks(tick);
        h.removeCallbacks(progressReport);
        prepared = false;
        if (mp != null) {
            try {
                mp.release();
            } catch (Throwable ignored) {
            }
            mp = null;
        }
    }

    private void togglePlay() {
        if (!prepared) return;
        if (mp.isPlaying()) mp.pause(); else mp.start();
        jelly.reportProgress(movie, info, ticks(), !mp.isPlaying());
        update();
        root.announceForAccessibility(getString(mp.isPlaying() ? R.string.a11y_state_playing : R.string.a11y_state_paused));
    }

    /** Salti progressivi: premendo di seguito il passo cresce (10 s, poi 30 s, poi 60 s). */
    private void stepSeek(int dir) {
        long now = System.currentTimeMillis();
        seekStreak = now - lastSeekAt < 700 ? seekStreak + 1 : 0;
        lastSeekAt = now;
        long sec = seekStreak < 3 ? 10 : seekStreak < 7 ? 30 : 60;
        seekBy(dir * sec * 10000000L);
        seekNote.setText((dir < 0 ? "−" : "+") + sec + " s");
        seekNote.setContentDescription(getString(dir < 0 ? R.string.a11y_seek_back : R.string.a11y_seek_forward, (int) sec));
        seekNote.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideNote);
        h.postDelayed(hideNote, 900);
    }

    private final Runnable hideNote = () -> seekNote.setVisibility(View.GONE);

    private void seekBy(long deltaTicks) {
        if (!prepared) return;
        long target = Math.max(0, ticks() + deltaTicks);
        if (runtimeTicks > 0) target = Math.min(target, runtimeTicks - 5 * 10000000L);
        mp.seekTo((int) (target / 10000L));
        update();
    }

    private static String clock(long ticks) {
        long s = Math.max(0, ticks / 10000000L);
        return s >= 3600 ? String.format(Locale.ITALIAN, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                : String.format(Locale.ITALIAN, "%d:%02d", s / 60, s % 60);
    }

    private void update() {
        if (!prepared) return;
        long t = ticks();
        long total = runtimeTicks > 0 ? runtimeTicks : mp.getDuration() * 10000L;
        progress.set(total > 0 ? t / (float) total : 0f, A11y.spokenTime(this, t / 10000000L),
                total > 0 ? A11y.spokenTime(this, total / 10000000L) : "");
        tCur.setText(clock(t));
        tEnd.setText(total > 0 ? clock(total) : "");
        play.setPlaying(mp.isPlaying());
    }

    // ------------------------------------------------------------ audio e sottotitoli

    private void trackMenu(final boolean audio) {
        if (info == null) return;
        h.removeCallbacks(hide);
        MenuDialog m = new MenuDialog(this, audio ? "Audio" : "Sottotitoli", "La riproduzione riparte da qui", u, null);
        List<Movie.Stream> list = new java.util.ArrayList<>();
        for (Movie.Stream s : info.streams) if (s.type.equals(audio ? "Audio" : "Subtitle")) list.add(s);
        if (!audio) m.add((subIdx < 0 ? "● " : "") + "Nessuno", () -> switchTracks(audioIdx, -1));
        for (final Movie.Stream s : list) {
            boolean cur = audio ? s.index == audioIdx : s.index == subIdx;
            String name = s.title.isEmpty() ? (s.lang.isEmpty() ? s.codec : s.lang) : s.title;
            m.add((cur ? "● " : "") + name, () -> switchTracks(audio ? s.index : audioIdx, audio ? subIdx : s.index));
        }
        if (list.isEmpty()) m.add("Nessuna traccia disponibile", null);
        m.show();
    }

    private void switchTracks(int a, int s) {
        if (a == audioIdx && s == subIdx) return;
        // direct play e cambia solo l'audio: lo cambia il player al volo, senza ricaricare
        if (info.direct && prepared && s == subIdx && selectAudio(a)) {
            audioIdx = a;
            return;
        }
        load(ticks(), a, s);
    }

    /**
     * Seleziona nel player la traccia audio con l'indice Jellyfin dato: la n-esima traccia audio del server
     * e' la n-esima del file. Se il player non sa cambiare traccia va bene solo la prima (quella che suona gia').
     */
    private boolean selectAudio(int jfIndex) {
        int k = 0;
        for (Movie.Stream s : info.streams) {
            if (!s.type.equals("Audio") || s.external) continue;
            if (s.index == jfIndex) break;
            k++;
        }
        try {
            MediaPlayer.TrackInfo[] ts = mp.getTrackInfo();
            for (int i = 0, n = k; i < ts.length; i++) {
                if (ts[i].getTrackType() != MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) continue;
                if (n-- == 0) {
                    mp.selectTrack(i);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return k == 0 || jfIndex < 0;
    }

    // ------------------------------------------------------------ tasti

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                togglePlay();
                showOverlay(true);
                return true;
            }
            if (k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                seekBy(SEEK_TICKS * 3);
                showOverlay(true);
                return true;
            }
            if (k == KeyEvent.KEYCODE_MEDIA_REWIND) {
                seekBy(-SEEK_TICKS * 3);
                showOverlay(true);
                return true;
            }
            if (!overlayOn) {
                // con l'interfaccia nascosta: OK = pausa, sinistra/destra = +-10 s, su/giu = mostra i controlli
                if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                    togglePlay();
                    showOverlay(true);
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    stepSeek(k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    showOverlay(true);
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_MENU) {
                    showOverlay(true);
                    return true;
                }
            } else {
                armHide();
                // sulla barra di avanzamento sinistra/destra cercano
                if (getCurrentFocus() == progress && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    stepSeek(k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (overlayOn && prepared) {
            showOverlay(false);
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------ ciclo di vita

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (!started) start();
        else if (mp != null) mp.setDisplay(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int f, int w, int hh) {
        fit(videoW, videoH);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        if (mp != null) mp.setDisplay(null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // fuori dallo schermo: salva la posizione e chiudi, cosi' la TV non resta con il video in sottofondo
        if (info != null) jelly.reportStopped(movie, info, ticks());
        info = null;
        releasePlayer();
        finish();
    }
}
