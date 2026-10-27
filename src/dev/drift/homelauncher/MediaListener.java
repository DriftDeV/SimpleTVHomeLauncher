package dev.drift.homelauncher;

import android.service.notification.NotificationListenerService;

/**
 * Non legge le notifiche: serve solo perche' Android consenta a un'app di vedere le sessioni
 * multimediali attive. Quando il sistema la collega, avvia il tracker della musica.
 */
public class MediaListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        MusicTracker.get(this).start();
    }
}
