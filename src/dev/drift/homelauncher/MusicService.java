package dev.drift.homelauncher;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Tiene vivo il launcher mentre suona un brano di Jellyfin e si usa un'altra app: un servizio in primo piano
 * non viene chiuso per fare spazio. Il lavoro vero lo fa MusicPlayer; qui c'e' solo la notifica obbligatoria.
 */
public class MusicService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MusicPlayer p = MusicPlayer.peek();
        MusicItem c = p == null ? null : p.current();
        if (c == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // targetSdk 25: nessun canale di notifica richiesto, nemmeno su Android 8 e successivi
        Notification n = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(c.name)
                .setContentText(c.artist)
                .setOngoing(true)
                .setStyle(new Notification.MediaStyle().setMediaSession(p.sessionToken()))
                .build();
        startForeground(1, n);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
