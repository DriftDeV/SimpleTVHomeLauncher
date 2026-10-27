package dev.drift.homelauncher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Caricatore di immagini minimale: cache LRU in memoria (10 MB), due thread, callback sul thread UI. */
final class Img {
    interface Cb {
        void got(Bitmap b);
    }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(10 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Img() {
    }

    static Bitmap peek(String url) {
        return CACHE.get(url);
    }

    static void load(final String url, final Cb cb) {
        Bitmap hit = CACHE.get(url);
        if (hit != null) {
            cb.got(hit);
            return;
        }
        POOL.execute(() -> {
            Bitmap b = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                try (InputStream in = c.getInputStream()) {
                    b = BitmapFactory.decodeStream(in);
                } finally {
                    c.disconnect();
                }
            } catch (Throwable ignored) {
            }
            final Bitmap res = b;
            if (res != null) CACHE.put(url, res);
            MAIN.post(() -> cb.got(res));
        });
    }

    static void clear() {
        CACHE.evictAll();
    }
}
