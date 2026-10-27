package dev.drift.homelauncher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/**
 * Player dei brani di Jellyfin dentro il launcher. Pubblica una MediaSession: la parte "in riproduzione"
 * della tab Musica la legge come quella di qualsiasi altra app, e i tasti multimediali del telecomando la comandano.
 * Tutto sul thread principale.
 */
final class MusicPlayer {
    /** Avvisato a ogni cambio di brano, stato o coda. */
    interface Listener {
        void onPlayerChanged();
    }

    private static MusicPlayer inst;

    static synchronized MusicPlayer get(Context c) {
        if (inst == null) inst = new MusicPlayer(c.getApplicationContext());
        return inst;
    }

    /** Il player solo se e' gia' stato creato (chi lo legge e basta non lo fa nascere). */
    static synchronized MusicPlayer peek() {
        return inst;
    }

    private static final long PAUSE_RELEASE_MS = 15 * 60 * 1000L; // in pausa cosi' a lungo: si chiude tutto
    private static final int REPORT_MS = 10000;

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final MediaSession session;
    private final MediaController controller;
    private final AudioManager am;
    private final List<Listener> listeners = new ArrayList<>();
    private Jelly jelly;
    private MediaPlayer mp;
    private final List<MusicItem> queue = new ArrayList<>();
    private int pos = -1;
    private boolean prepared, wantPlay, focusLostTransient;
    private int errors;
    private Bitmap art;
    private String artFor = "";

    private final AudioManager.OnAudioFocusChangeListener focus = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            pause();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (playing()) {
                focusLostTransient = true;
                pauseInternal();
            }
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (mp != null) mp.setVolume(0.3f, 0.3f);
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            if (mp != null) mp.setVolume(1f, 1f);
            if (focusLostTransient) {
                focusLostTransient = false;
                play();
            }
        }
    };

    private final Runnable report = new Runnable() {
        @Override
        public void run() {
            MusicItem c = current();
            if (c != null && prepared) jelly.reportAudio("/Progress", c.id, position(), !playing());
            h.postDelayed(this, REPORT_MS);
        }
    };

    private final Runnable idleRelease = this::stop;

    private MusicPlayer(Context c) {
        ctx = c;
        am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        session = new MediaSession(c, "SimpleTVHome");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                prev();
            }

            @Override
            public void onSeekTo(long ms) {
                seekTo(ms);
            }

            @Override
            public void onStop() {
                stop();
            }
        }, h);
        controller = new MediaController(c, session.getSessionToken());
    }

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        for (Listener l : new ArrayList<>(listeners)) l.onPlayerChanged();
        MusicTracker.get(ctx).localChanged();
    }

    /** Controller della propria sessione: non serve il permesso sulle notifiche per leggerlo. */
    MediaController controller() {
        return controller;
    }

    /** Copertina del brano corrente (null finche' non arriva). */
    Bitmap art() {
        return art;
    }

    MediaSession.Token sessionToken() {
        return session.getSessionToken();
    }

    MusicItem current() {
        return pos >= 0 && pos < queue.size() ? queue.get(pos) : null;
    }

    boolean hasTrack() {
        return current() != null;
    }

    boolean playing() {
        return mp != null && prepared && mp.isPlaying();
    }

    /** Il brano sta per partire o sta suonando (anche mentre si carica). */
    boolean active() {
        return hasTrack() && wantPlay;
    }

    long position() {
        try {
            return mp != null && prepared ? mp.getCurrentPosition() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    long duration() {
        MusicItem c = current();
        if (mp != null && prepared) {
            try {
                int d = mp.getDuration();
                if (d > 0) return d;
            } catch (Throwable ignored) {
            }
        }
        return c == null ? 0 : c.durationMs();
    }

    // ------------------------------------------------------------ coda

    /** Suona la lista a partire da start (la lista diventa la coda). */
    void playList(List<MusicItem> list, int start) {
        if (list == null || list.isEmpty()) return;
        jelly = new Jelly(ctx); // legge l'accesso salvato adesso: puo' essere cambiato dalla tab Film
        endCurrent();
        queue.clear();
        for (MusicItem m : list) if (m.kind == MusicItem.SONG) queue.add(m);
        if (queue.isEmpty()) return;
        errors = 0;
        load(Math.max(0, Math.min(start, queue.size() - 1)));
    }

    List<MusicItem> queue() {
        return queue;
    }

    int index() {
        return pos;
    }

    void next() {
        if (!hasTrack() || pos + 1 >= queue.size()) return;
        endCurrent();
        load(pos + 1);
    }

    /** Fine dell'ultimo brano: resta li', in pausa all'inizio, pronto a ripartire. */
    private void finished() {
        MusicItem c = current();
        jelly.reportAudio("/Stopped", c.id, duration(), false);
        h.removeCallbacks(report);
        wantPlay = false;
        try {
            mp.seekTo(0);
        } catch (Throwable ignored) {
        }
        h.removeCallbacks(idleRelease);
        h.postDelayed(idleRelease, PAUSE_RELEASE_MS);
        publish();
        changed();
    }

    /** Nei primi 3 secondi torna al brano prima, dopo riparte dall'inizio di questo (come i lettori comuni). */
    void prev() {
        if (!hasTrack()) return;
        if (position() > 3000 || pos == 0) {
            seekTo(0);
            return;
        }
        endCurrent();
        load(pos - 1);
    }

    void playPause() {
        if (playing()) pause(); else play();
    }

    void play() {
        if (!hasTrack()) return;
        if (!requestFocus()) return;
        h.removeCallbacks(idleRelease);
        wantPlay = true;
        if (mp == null) {
            load(pos);
            return;
        }
        if (prepared && !mp.isPlaying()) {
            mp.start();
            publish();
            jelly.reportAudio("/Progress", current().id, position(), false);
        }
        startService();
        changed();
    }

    void pause() {
        focusLostTransient = false;
        pauseInternal();
    }

    private void pauseInternal() {
        wantPlay = false;
        if (mp != null && prepared && mp.isPlaying()) {
            mp.pause();
            jelly.reportAudio("/Progress", current().id, position(), true);
        }
        h.removeCallbacks(idleRelease);
        h.postDelayed(idleRelease, PAUSE_RELEASE_MS);
        publish();
        changed();
    }

    void seekTo(long ms) {
        if (mp == null || !prepared) return;
        long d = duration();
        mp.seekTo((int) Math.max(0, d > 0 ? Math.min(ms, d - 500) : ms));
        publish();
        changed();
    }

    void seekBy(long delta) {
        seekTo(position() + delta);
    }

    /** Ferma tutto e svuota la coda. */
    void stop() {
        endCurrent();
        queue.clear();
        pos = -1;
        wantPlay = false;
        art = null;
        artFor = "";
        h.removeCallbacks(idleRelease);
        am.abandonAudioFocus(focus);
        session.setActive(false);
        publish();
        ctx.stopService(new Intent(ctx, MusicService.class));
        changed();
    }

    // ------------------------------------------------------------ interno

    private boolean requestFocus() {
        return am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    /** Chiude il brano corrente avvisando il server di dove si e' fermato. */
    private void endCurrent() {
        h.removeCallbacks(report);
        MusicItem c = current();
        if (c != null && mp != null && jelly != null) jelly.reportAudio("/Stopped", c.id, position(), false);
        release();
    }

    private void release() {
        prepared = false;
        if (mp != null) {
            try {
                mp.release();
            } catch (Throwable ignored) {
            }
            mp = null;
        }
    }

    private void load(final int i) {
        pos = i;
        final MusicItem m = queue.get(i);
        if (jelly == null) jelly = new Jelly(ctx);
        wantPlay = true;
        if (!requestFocus()) {
            wantPlay = false;
            publish();
            changed();
            return;
        }
        h.removeCallbacks(idleRelease);
        session.setActive(true);
        loadArt(m);
        if (!m.detailed) {
            // formato e qualita' per la parte "in riproduzione" e la scheda "Informazioni"
            jelly.songDetail(m, new Jelly.Done<MusicItem>() {
                @Override
                public void ok(MusicItem v) {
                    if (v == current()) changed();
                }

                @Override
                public void fail(String message, boolean unauthorized) {
                }
            });
        }
        release();
        try {
            final MediaPlayer p = new MediaPlayer();
            mp = p;
            p.setAudioStreamType(AudioManager.STREAM_MUSIC);
            p.setDataSource(jelly.audioUrl(m.id));
            p.setOnPreparedListener(x -> {
                if (x != mp) return;
                prepared = true;
                errors = 0;
                if (wantPlay) x.start();
                jelly.reportAudio("", m.id, 0, !wantPlay);
                h.removeCallbacks(report);
                h.postDelayed(report, REPORT_MS);
                publish();
                changed();
            });
            p.setOnCompletionListener(x -> {
                if (x != mp) return;
                if (pos + 1 < queue.size()) next(); else finished();
            });
            p.setOnErrorListener((x, what, extra) -> {
                if (x != mp) return true;
                android.util.Log.w("MusicPlayer", "errore " + what + "/" + extra + " su " + m.id);
                // brano illeggibile: si passa al successivo, ma non all'infinito
                if (++errors >= 3 || pos + 1 >= queue.size()) {
                    stop();
                } else {
                    release();
                    load(pos + 1);
                }
                return true;
            });
            p.prepareAsync();
        } catch (Throwable t) {
            stop();
            return;
        }
        startService();
        publish();
        changed();
    }

    private void loadArt(final MusicItem m) {
        if (artFor.equals(m.albumId.isEmpty() ? m.id : m.albumId)) return;
        artFor = m.albumId.isEmpty() ? m.id : m.albumId;
        art = null;
        final String key = artFor;
        String url = jelly.cover(m, 512);
        if (url == null) return;
        Img.load(url, b -> {
            if (!key.equals(artFor)) return;
            art = b;
            publish();
            changed();
        });
    }

    private void startService() {
        try {
            ctx.startService(new Intent(ctx, MusicService.class));
        } catch (Throwable ignored) {
            // senza servizio la musica suona lo stesso finche' il launcher resta vivo
        }
    }

    /** Aggiorna metadati e stato della sessione. */
    private void publish() {
        MusicItem c = current();
        if (c == null) {
            session.setMetadata(null);
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_NONE, 0, 1f).build());
            return;
        }
        MediaMetadata.Builder md = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, c.id)
                .putString(MediaMetadata.METADATA_KEY_TITLE, c.name)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, c.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, c.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration())
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, pos + 1)
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, queue.size());
        if (art != null) md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        session.setMetadata(md.build());
        int st = !prepared ? (wantPlay ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PAUSED)
                : mp.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (pos + 1 < queue.size()) actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                .setState(st, position(), st == PlaybackState.STATE_PLAYING ? 1f : 0f, SystemClock.elapsedRealtime()).build());
    }
}
