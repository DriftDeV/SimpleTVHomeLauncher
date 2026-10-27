package dev.drift.mediatest;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;

/** App di prova: finge un player con una sessione multimediale per testare la tab Musica. */
public class Main extends Activity {
    private static final String[][] TRACKS = {
            {"Blinding Lights", "The Weeknd", "After Hours", "spotify:track:0VjIjW4GlUZAMYd2vXMi3b"},
            {"Midnight City", "M83", "Hurry Up, We're Dreaming", "spotify:track:4tZwfgrHOc3mvqYlEYSvVi"},
            {"Instant Crush", "Daft Punk", "Random Access Memories", "spotify:track:2cGxRwrMyEAp8dEbuZaVv6"}};
    private MediaSession ms;
    private int idx = 0;
    private boolean playing = true;
    private long pos = 42000;
    private long since = SystemClock.elapsedRealtime();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        t.setText("MediaTest: sessione attiva");
        t.setTextSize(24);
        setContentView(t);
        ms = new MediaSession(this, "mediatest");
        ms.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        ms.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                pos = cur();
                playing = true;
                since = SystemClock.elapsedRealtime();
                publish(false);
            }

            @Override
            public void onPause() {
                pos = cur();
                playing = false;
                publish(false);
            }

            @Override
            public void onSeekTo(long to) {
                pos = to;
                since = SystemClock.elapsedRealtime();
                publish(false);
            }

            @Override
            public void onSkipToNext() {
                idx = (idx + 1) % TRACKS.length;
                pos = 0;
                since = SystemClock.elapsedRealtime();
                publish(true);
            }

            @Override
            public void onSkipToPrevious() {
                idx = (idx + TRACKS.length - 1) % TRACKS.length;
                pos = 0;
                since = SystemClock.elapsedRealtime();
                publish(true);
            }
        });
        ms.setActive(true);
        publish(true);
    }

    private long cur() {
        return playing ? pos + (SystemClock.elapsedRealtime() - since) : pos;
    }

    private Bitmap art(int i) {
        Bitmap bm = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        int[][] cols = {{0xFFFF512F, 0xFFDD2476}, {0xFF36D1DC, 0xFF5B86E5}, {0xFFFDC830, 0xFFF37335}};
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 300, 300, cols[i][0], cols[i][1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 300, 300, p);
        p.setShader(null);
        p.setColor(0x55FFFFFF);
        c.drawCircle(150, 150, 90, p);
        return bm;
    }

    private void publish(boolean newTrack) {
        if (newTrack) {
            String[] t = TRACKS[idx];
            ms.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, t[0])
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, t[1])
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, t[2])
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, t[3])
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, 215000)
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art(idx)).build());
        }
        ms.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos, 1f,
                        SystemClock.elapsedRealtime()).build());
    }
}
