package dev.drift.homelauncher;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Segue la sessione multimediale attiva (preferendo Spotify), ricorda gli ultimi brani
 * e li salva in locale. Nessun accesso alla rete, nessun account.
 */
final class MusicTracker {
    interface Listener {
        void onMusicChanged();
    }

    private static MusicTracker inst;

    static synchronized MusicTracker get(Context c) {
        if (inst == null) inst = new MusicTracker(c.getApplicationContext());
        return inst;
    }

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private MediaSessionManager msm;
    private MediaController ctrl;
    private boolean started;

    // snapshot esposto alla UI
    String pkg = "", title = "", artist = "", album = "", uri = "";
    Bitmap art;
    long duration;
    int state = PlaybackState.STATE_NONE;
    private long pos, posTime;
    private float speed = 1f;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::pick;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata m) {
            refresh();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            refresh();
        }

        @Override
        public void onSessionDestroyed() {
            requery();
        }
    };

    private MusicTracker(Context c) {
        ctx = c;
        // pulizia dei dati della vecchia cronologia, non piu' usata
        c.getSharedPreferences("music", Context.MODE_PRIVATE).edit().clear().apply();
        File[] old = c.getFilesDir().listFiles();
        if (old != null) for (File f : old) if (f.getName().startsWith("m_")) f.delete();
    }

    void addListener(Listener l) {
        listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** True se l'utente ha concesso l'accesso (il sistema ci ha collegato come listener). */
    boolean hasAccess() {
        String enabled = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(ctx.getPackageName());
    }

    boolean connected() {
        return started && ctrl != null;
    }

    synchronized void start() {
        if (!hasAccess()) return;
        try {
            msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(ctx, MediaListener.class);
            if (!started) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, cn, h);
                started = true;
            }
            pick(msm.getActiveSessions(cn));
        } catch (SecurityException e) {
            started = false;
        }
    }

    private void requery() {
        try {
            if (msm != null) pick(msm.getActiveSessions(new ComponentName(ctx, MediaListener.class)));
        } catch (SecurityException ignored) {
        }
    }

    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                if (c.getPackageName().startsWith("com.spotify")) {
                    best = c;
                    break;
                }
            }
            if (best == null) {
                for (MediaController c : list) {
                    PlaybackState s = c.getPlaybackState();
                    if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                        best = c;
                        break;
                    }
                }
            }
            if (best == null && !list.isEmpty()) best = list.get(0);
        }
        if (ctrl != null) ctrl.unregisterCallback(callback);
        ctrl = best;
        if (ctrl != null) ctrl.registerCallback(callback, h);
        refresh();
    }

    private static String s(MediaMetadata m, String... keys) {
        for (String k : keys) {
            CharSequence v = m.getText(k);
            if (v != null && v.length() > 0) return v.toString();
        }
        return "";
    }

    private void refresh() {
        if (ctrl == null) {
            pkg = title = artist = album = uri = "";
            art = null;
            duration = 0;
            state = PlaybackState.STATE_NONE;
        } else {
            pkg = ctrl.getPackageName();
            MediaMetadata m = ctrl.getMetadata();
            if (m != null) {
                title = s(m, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                artist = s(m, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                        MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                album = s(m, MediaMetadata.METADATA_KEY_ALBUM);
                String id = s(m, MediaMetadata.METADATA_KEY_MEDIA_ID);
                uri = id.matches("spotify:(track|episode|album|playlist|artist|show):[A-Za-z0-9]+")
                        ? id : s(m, MediaMetadata.METADATA_KEY_MEDIA_URI);
                Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                art = b;
                duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            } else {
                title = artist = album = uri = "";
                art = null;
                duration = 0;
            }
            PlaybackState ps = ctrl.getPlaybackState();
            if (ps != null) {
                state = ps.getState();
                pos = ps.getPosition();
                posTime = ps.getLastPositionUpdateTime();
                speed = ps.getPlaybackSpeed();
            } else {
                state = PlaybackState.STATE_NONE;
            }
        }
        for (Listener l : listeners) l.onMusicChanged();
    }

    boolean playing() {
        return state == PlaybackState.STATE_PLAYING;
    }

    boolean hasTrack() {
        return !title.isEmpty();
    }

    long position() {
        long p = pos;
        if (state == PlaybackState.STATE_PLAYING && posTime > 0) {
            p += (long) ((SystemClock.elapsedRealtime() - posTime) * speed);
        }
        if (duration > 0) p = Math.min(p, duration);
        return Math.max(0, p);
    }

    void playPause() {
        if (ctrl == null) return;
        if (playing()) ctrl.getTransportControls().pause(); else ctrl.getTransportControls().play();
    }

    /** Sposta la riproduzione di delta ms (se il player lo consente). */
    void seekBy(long delta) {
        if (ctrl == null || !hasTrack()) return;
        long to = Math.max(0, position() + delta);
        if (duration > 0) to = Math.min(to, duration - 500);
        ctrl.getTransportControls().seekTo(to);
    }

    void next() {
        if (ctrl != null) ctrl.getTransportControls().skipToNext();
    }

    void prev() {
        if (ctrl != null) ctrl.getTransportControls().skipToPrevious();
    }
}
