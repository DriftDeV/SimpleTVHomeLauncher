package dev.drift.homelauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client minimale dell'API REST di Jellyfin (HTTP + JSON, nessuna libreria esterna).
 * Sessione salvata in SharedPreferences: solo server, utente e token, mai la password.
 */
final class Jelly {
    interface Done<T> {
        void ok(T value);

        void fail(String message, boolean unauthorized);
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String FIELDS = "Overview,MediaSources,Genres,ProductionYear,CommunityRating,"
            + "RunTimeTicks,OfficialRating,BackdropImageTags";

    private final SharedPreferences sp;
    String server, token, userId, userName;
    private final String deviceId;

    Jelly(Context c) {
        sp = c.getSharedPreferences("film", Context.MODE_PRIVATE);
        String id = sp.getString("deviceId", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            sp.edit().putString("deviceId", id).apply();
        }
        deviceId = id;
        server = sp.getString("server", "");
        token = sp.getString("token", "");
        userId = sp.getString("userId", "");
        userName = sp.getString("userName", "");
    }

    boolean loggedIn() {
        return !server.isEmpty() && !token.isEmpty() && !userId.isEmpty();
    }

    /** Esci: dimentica sessione e password salvata; server e utente restano per riaccedere in fretta. */
    void logout() {
        token = "";
        userId = "";
        sp.edit().remove("token").remove("userId").remove("pw").apply();
    }

    // ------------------------------------------------------------ password salvata (cifrata con il Keystore)

    private static final String KS = "AndroidKeyStore", ALIAS = "home_jellyfin_pw";

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KS);
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS);
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    /** Salva la password cifrata (AES-GCM, chiave nel Keystore). Se il Keystore non va, non salva nulla. */
    void savePassword(String pw) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] ct = c.doFinal(pw.getBytes("UTF-8"));
            sp.edit().putString("pw", Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(ct, Base64.NO_WRAP)).apply();
        } catch (Throwable t) {
            sp.edit().remove("pw").apply();
        }
    }

    String savedPassword() {
        try {
            String v = sp.getString("pw", null);
            if (v == null || !v.contains(":")) return null;
            String[] p = v.split(":");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(p[0], Base64.NO_WRAP)));
            return new String(c.doFinal(Base64.decode(p[1], Base64.NO_WRAP)), "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    boolean hasSavedPassword() {
        return sp.contains("pw");
    }

    /** Rientra con le credenziali salvate (server, utente, password cifrata). */
    void relogin(Done<Boolean> cb) {
        String pw = savedPassword();
        if (server.isEmpty() || userName.isEmpty() || pw == null) {
            cb.fail("Nessuna password salvata", true);
            return;
        }
        login(server, userName, pw, cb);
    }

    /** "192.168.1.17" -> "http://192.168.1.17:8096"; toglie la barra finale. */
    static String normalize(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) return s;
        if (!s.contains("://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        String rest = s.substring(s.indexOf("://") + 3);
        if (!rest.contains(":") && !rest.contains("/") && s.startsWith("http://")) s += ":8096";
        return s;
    }

    private String authHeader(boolean withToken) {
        StringBuilder sb = new StringBuilder("MediaBrowser ");
        if (withToken && !token.isEmpty()) sb.append("Token=\"").append(token).append("\", ");
        sb.append("Client=\"Home\", Device=\"").append(Build.MODEL.replace('"', ' '))
                .append("\", DeviceId=\"").append(deviceId).append("\", Version=\"0.1\"");
        return sb.toString();
    }

    private static class HttpError extends Exception {
        final int code;

        HttpError(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    private String request(String method, String path, String body, boolean withToken) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(server + path).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", authHeader(withToken));
        c.setRequestProperty("Accept", "application/json");
        try {
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String txt = "";
            if (in != null) {
                try (InputStream i = in) {
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    for (int n; (n = i.read(buf)) > 0; ) o.write(buf, 0, n);
                    txt = o.toString("UTF-8");
                }
            }
            if (code >= 400) throw new HttpError(code, "HTTP " + code);
            return txt;
        } finally {
            c.disconnect();
        }
    }

    private interface Job<T> {
        T run() throws Exception;
    }

    private <T> void async(final Job<T> job, final Done<T> cb) {
        POOL.execute(() -> {
            try {
                final T v = job.run();
                MAIN.post(() -> cb.ok(v));
            } catch (HttpError e) {
                final boolean unauth = e.code == 401 || e.code == 403;
                MAIN.post(() -> cb.fail(unauth ? "Sessione scaduta o credenziali errate" : e.getMessage(), unauth));
            } catch (Throwable t) {
                final String m = t instanceof java.net.UnknownHostException || t instanceof java.net.ConnectException
                        || t instanceof java.net.SocketTimeoutException ? "Server non raggiungibile" : String.valueOf(t.getMessage());
                MAIN.post(() -> cb.fail(m, false));
            }
        });
    }

    // ------------------------------------------------------------ accesso

    void login(final String url, final String user, final String pw, Done<Boolean> cb) {
        final String srv = normalize(url);
        async(() -> {
            server = srv;
            JSONObject b = new JSONObject().put("Username", user).put("Pw", pw);
            accept(new JSONObject(request("POST", "/Users/AuthenticateByName", b.toString(), false)), user);
            savePassword(pw);
            return true;
        }, cb);
    }

    /** Salva la sessione dalla risposta di autenticazione (password o Quick Connect). */
    private void accept(JSONObject r, String fallbackName) throws Exception {
        token = r.getString("AccessToken");
        userId = r.getJSONObject("User").getString("Id");
        userName = r.getJSONObject("User").optString("Name", fallbackName);
        sp.edit().putString("server", server).putString("token", token).putString("userId", userId)
                .putString("userName", userName).apply();
    }

    // ------------------------------------------------------------ Quick Connect (accesso con un codice, senza digitare la password)

    /** Chiede un codice al server: restituisce {codice da mostrare, segreto per il controllo}. */
    void quickInitiate(final String url, Done<String[]> cb) {
        final String srv = normalize(url);
        async(() -> {
            server = srv;
            if (!new JSONObject("{\"v\":" + request("GET", "/QuickConnect/Enabled", null, false) + "}").optBoolean("v")) {
                throw new IllegalStateException("Quick Connect non e' attivo sul server");
            }
            JSONObject r = new JSONObject(request("POST", "/QuickConnect/Initiate", "", false));
            return new String[]{r.getString("Code"), r.getString("Secret")};
        }, cb);
    }

    /** True quando l'utente ha approvato il codice dal telefono o dal browser. */
    void quickCheck(final String secret, Done<Boolean> cb) {
        async(() -> new JSONObject(request("GET", "/QuickConnect/Connect?secret=" + secret, null, false))
                .optBoolean("Authenticated"), cb);
    }

    void quickLogin(final String secret, Done<Boolean> cb) {
        async(() -> {
            JSONObject b = new JSONObject().put("Secret", secret);
            accept(new JSONObject(request("POST", "/Users/AuthenticateWithQuickConnect", b.toString(), false)), "Utente");
            sp.edit().remove("pw").apply(); // nessuna password: la sessione e' solo il token
            return true;
        }, cb);
    }

    // ------------------------------------------------------------ libreria

    private static List<Movie> movies(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        JSONArray a = o.has("Items") ? o.getJSONArray("Items") : new JSONArray();
        List<Movie> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) l.add(Movie.from(a.getJSONObject(i)));
        return l;
    }

    void resume(Done<List<Movie>> cb) {
        async(() -> movies(request("GET", "/UserItems/Resume?userId=" + userId
                + "&MediaTypes=Video&Limit=12&Fields=" + FIELDS, null, true)), cb);
    }

    void latest(Done<List<Movie>> cb) {
        async(() -> movies(request("GET", "/Items?userId=" + userId + "&IncludeItemTypes=Movie&Recursive=true"
                + "&SortBy=DateCreated&SortOrder=Descending&Limit=20&Fields=" + FIELDS, null, true)), cb);
    }

    void watched(Done<List<Movie>> cb) {
        async(() -> movies(request("GET", "/Items?userId=" + userId + "&IncludeItemTypes=Movie&Recursive=true"
                + "&Filters=IsPlayed&SortBy=DatePlayed&SortOrder=Descending&Limit=20&Fields=" + FIELDS, null, true)), cb);
    }

    void all(Done<List<Movie>> cb) {
        async(() -> movies(request("GET", "/Items?userId=" + userId + "&IncludeItemTypes=Movie&Recursive=true"
                + "&SortBy=SortName&Fields=" + FIELDS, null, true)), cb);
    }

    void item(final String id, Done<Movie> cb) {
        async(() -> Movie.from(new JSONObject(request("GET", "/Items/" + id + "?userId=" + userId, null, true))), cb);
    }

    void setPlayed(final String id, final boolean played, Done<Boolean> cb) {
        async(() -> {
            request(played ? "POST" : "DELETE", "/UserPlayedItems/" + id + "?userId=" + userId, null, true);
            return played;
        }, cb);
    }

    String imageUrl(String id, String type, String tag, int maxWidth) {
        return server + "/Items/" + id + "/Images/" + type + "?maxWidth=" + maxWidth + "&quality=88"
                + (tag.isEmpty() ? "" : "&tag=" + tag) + "&api_key=" + token;
    }

    /** Immagine da usare per un film: locandina (Primary) oppure null se non c'e'. */
    String poster(Movie m, int w) {
        return m.imgTag.isEmpty() ? null : imageUrl(m.id, "Primary", m.imgTag, w);
    }

    /** Immagine larga per l'hero: sfondo se c'e', altrimenti la locandina. */
    String hero(Movie m, int w) {
        if (!m.backdropTag.isEmpty()) return imageUrl(m.id, "Backdrop", m.backdropTag, w);
        return poster(m, w);
    }

    // ------------------------------------------------------------ musica

    private static final String MFIELDS = "&Fields=ChildCount&EnableTotalRecordCount=false";

    private List<MusicItem> music(String query) throws Exception {
        return MusicItem.list(request("GET", "/Items?userId=" + userId + "&Recursive=true" + query + MFIELDS, null, true));
    }

    /** Brani ascoltati di recente, dal piu' recente. */
    void recentSongs(Done<List<MusicItem>> cb) {
        async(() -> music("&IncludeItemTypes=Audio&Filters=IsPlayed&SortBy=DatePlayed&SortOrder=Descending&Limit=20"), cb);
    }

    void albums(Done<List<MusicItem>> cb) {
        async(() -> music("&IncludeItemTypes=MusicAlbum&SortBy=SortName"), cb);
    }

    /** Artisti degli album (non ogni ospite citato nei brani). */
    void artists(Done<List<MusicItem>> cb) {
        async(() -> {
            List<MusicItem> l = MusicItem.list(request("GET", "/Artists/AlbumArtists?userId=" + userId
                    + "&SortBy=SortName" + MFIELDS, null, true));
            for (MusicItem m : l) m.kind = MusicItem.ARTIST;
            return l;
        }, cb);
    }

    /** n brani a caso (li sceglie il server). */
    void randomSongs(final int n, Done<List<MusicItem>> cb) {
        async(() -> music("&IncludeItemTypes=Audio&SortBy=Random&Limit=" + n), cb);
    }

    /** Brani dati per id, nell'ordine degli id (quelli spariti dal server mancano). */
    void songsById(final List<String> ids, Done<List<MusicItem>> cb) {
        async(() -> {
            StringBuilder sb = new StringBuilder();
            for (String id : ids) sb.append(sb.length() > 0 ? "," : "").append(id);
            List<MusicItem> got = music("&Ids=" + sb);
            List<MusicItem> out = new ArrayList<>();
            for (String id : ids) for (MusicItem m : got) if (m.id.equals(id)) out.add(m);
            return out;
        }, cb);
    }

    void albumTracks(final String albumId, Done<List<MusicItem>> cb) {
        async(() -> music("&ParentId=" + albumId + "&IncludeItemTypes=Audio&SortBy=ParentIndexNumber,IndexNumber,SortName"), cb);
    }

    void artistAlbums(final String artistId, Done<List<MusicItem>> cb) {
        async(() -> music("&IncludeItemTypes=MusicAlbum&AlbumArtistIds=" + artistId + "&SortBy=ProductionYear,SortName"), cb);
    }

    /** Tutti i brani di un artista, album per album (per "Riproduci" e "Casuale" nella sua scheda). */
    void artistSongs(final String artistId, Done<List<MusicItem>> cb) {
        async(() -> music("&IncludeItemTypes=Audio&AlbumArtistIds=" + artistId
                + "&SortBy=ProductionYear,Album,ParentIndexNumber,IndexNumber"), cb);
    }

    /** Copertina: quella del brano, se no quella del suo album; null se non ce n'e'. */
    String cover(MusicItem m, int w) {
        if (!m.imgTag.isEmpty()) return imageUrl(m.id, "Primary", m.imgTag, w);
        if (!m.albumImgTag.isEmpty() && !m.albumId.isEmpty()) return imageUrl(m.albumId, "Primary", m.albumImgTag, w);
        return null;
    }

    /**
     * Flusso audio: il server manda il file cosi' com'e' se il formato va bene, se no lo converte in mp3.
     * La chiave va come ApiKey: le versioni recenti rifiutano api_key su questo indirizzo.
     */
    String audioUrl(String id) {
        // bitrate massimo alto: in rete locale anche il FLAC arriva originale, senza passare per l'MP3.
        // m4a solo con AAC (%7C = "|", contenitore|codec): l'ALAC lo converte il server
        return server + "/Audio/" + id + "/universal?UserId=" + userId + "&DeviceId=" + deviceId
                + "&MaxStreamingBitrate=140000000&Container=" + MusicItem.DIRECT.replace("m4a", "m4a%7Caac")
                + "&TranscodingContainer=mp3&TranscodingProtocol=http&AudioCodec=mp3&ApiKey=" + token;
    }

    /** Il brano completo (formato, qualita', genere, ascolti) per la scheda "Informazioni". */
    void songDetail(final MusicItem m, Done<MusicItem> cb) {
        async(() -> {
            m.detail(new JSONObject(request("GET", "/Items/" + m.id + "?userId=" + userId, null, true)));
            return m;
        }, cb);
    }

    /** Stato della riproduzione di un brano: tiene aggiornati "ascoltati di recente" e il conteggio sul server. */
    void reportAudio(final String what, final String id, final long ms, final boolean paused) {
        POOL.execute(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("ItemId", id);
                b.put("PositionTicks", ms * 10000L);
                b.put("IsPaused", paused);
                b.put("CanSeek", true);
                b.put("PlayMethod", "DirectStream");
                request("POST", "/Sessions/Playing" + what, b.toString(), true);
            } catch (Throwable ignored) {
            }
        });
    }

    // ------------------------------------------------------------ riproduzione

    /** Risposta di PlaybackInfo: URL da riprodurre e tracce disponibili. */
    static final class PlayInfo {
        String url = "", mediaSourceId = "", playSessionId = "";
        boolean direct; // in entrambi i casi il flusso parte da 0:00 (la playlist HLS copre tutto il film): il player cerca la posizione
        List<Movie.Stream> streams = new ArrayList<>();
        int audioIdx = -1, subIdx = -1;
    }

    /**
     * Profilo di cosa sa riprodurre questa TV (MediaPlayer di Android, decoder hardware): il server decide
     * fra direct play e transcodifica. Dolby Vision profilo 5 non e' mostrabile senza DV: va transcodificato con tone mapping.
     */
    private static JSONObject deviceProfile(boolean burnSubs) throws Exception {
        JSONObject p = new JSONObject();
        p.put("Name", "Home Launcher");
        p.put("MaxStreamingBitrate", 40000000);
        p.put("DirectPlayProfiles", new JSONArray().put(new JSONObject().put("Container", "mp4,m4v,mkv,webm")
                .put("Type", "Video").put("VideoCodec", "h264,hevc,vp9").put("AudioCodec", "aac,mp3,ac3,eac3,opus,flac")));
        p.put("TranscodingProfiles", new JSONArray().put(new JSONObject().put("Container", "ts").put("Type", "Video")
                .put("VideoCodec", "h264").put("AudioCodec", "aac,mp3,ac3").put("Protocol", "hls").put("Context", "Streaming")
                .put("MaxAudioChannels", "6").put("MinSegments", "1").put("BreakOnNonKeyFrames", true)));
        JSONArray conds = new JSONArray()
                .put(new JSONObject().put("Condition", "EqualsAny").put("Property", "VideoRangeType")
                        .put("Value", "SDR|HDR10|HLG|DOVIWithHDR10|DOVIWithHLG|DOVIWithSDR").put("IsRequired", false))
                .put(new JSONObject().put("Condition", "LessThanEqual").put("Property", "VideoBitDepth").put("Value", "10").put("IsRequired", false));
        p.put("CodecProfiles", new JSONArray()
                .put(new JSONObject().put("Type", "Video").put("Codec", "hevc,h264,vp9").put("Conditions", conds)));
        JSONArray subs = new JSONArray();
        String[] fmts = {"srt", "subrip", "ass", "ssa", "vtt", "sub", "pgssub", "dvdsub", "dvbsub"};
        for (String f : fmts) subs.put(new JSONObject().put("Format", f).put("Method", burnSubs ? "Encode" : "Drop"));
        p.put("SubtitleProfiles", subs);
        return p;
    }

    /**
     * sourceId: la versione del film (il server applica le tracce scelte solo se c'e'); startTicks: da dove far partire;
     * audioIdx: traccia audio (-1 = predefinita); subIdx: sottotitolo (-1 = nessuno); force: solo transcodifica.
     */
    void playbackInfo(final String id, final String sourceId, final long startTicks, final int audioIdx, final int subIdx,
                      final boolean force, Done<PlayInfo> cb) {
        async(() -> {
            JSONObject b = new JSONObject();
            b.put("UserId", userId);
            b.put("DeviceProfile", deviceProfile(subIdx >= 0));
            b.put("MaxStreamingBitrate", 40000000);
            b.put("StartTimeTicks", startTicks);
            b.put("AutoOpenLiveStream", false);
            b.put("EnableDirectPlay", !force);
            b.put("EnableDirectStream", !force);
            b.put("EnableTranscoding", true);
            b.put("AllowVideoStreamCopy", !force);
            b.put("AllowAudioStreamCopy", !force);
            b.put("MediaSourceId", sourceId);
            if (audioIdx >= 0) b.put("AudioStreamIndex", audioIdx);
            b.put("SubtitleStreamIndex", subIdx); // -1 esplicito, se no il server imprime il sottotitolo predefinito
            JSONObject r = new JSONObject(request("POST", "/Items/" + id + "/PlaybackInfo?userId=" + userId, b.toString(), true));
            JSONArray ms = r.getJSONArray("MediaSources");
            if (ms.length() == 0) throw new IllegalStateException("Nessuna sorgente multimediale");
            JSONObject s = ms.getJSONObject(0);
            PlayInfo pi = new PlayInfo();
            pi.mediaSourceId = s.optString("Id");
            pi.playSessionId = r.optString("PlaySessionId");
            pi.streams = Movie.parseStreams(s.optJSONArray("MediaStreams"));
            pi.audioIdx = s.has("DefaultAudioStreamIndex") ? s.optInt("DefaultAudioStreamIndex", -1) : audioIdx;
            pi.subIdx = subIdx;
            String tu = s.optString("TranscodingUrl", "");
            boolean canDirect = !force && s.optBoolean("SupportsDirectPlay") && tu.isEmpty();
            if (canDirect) {
                pi.direct = true;
                pi.url = server + "/Videos/" + id + "/stream?static=true&mediaSourceId=" + pi.mediaSourceId
                        + "&deviceId=" + deviceId + "&PlaySessionId=" + pi.playSessionId + "&api_key=" + token;
            } else if (!tu.isEmpty()) {
                pi.url = (tu.startsWith("http") ? tu : server + tu) + (tu.contains("api_key=") ? "" : (tu.contains("?") ? "&" : "?") + "api_key=" + token);
            } else {
                throw new IllegalStateException("Il server non puo' riprodurre questo file");
            }
            return pi;
        }, cb);
    }

    private void report(final String path, final Movie m0, final PlayInfo pi, final long ticks, final boolean paused) {
        POOL.execute(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("ItemId", m0.id);
                b.put("MediaSourceId", pi.mediaSourceId);
                b.put("PlaySessionId", pi.playSessionId);
                b.put("PositionTicks", ticks);
                b.put("IsPaused", paused);
                b.put("CanSeek", true);
                b.put("PlayMethod", pi.direct ? "DirectPlay" : "Transcode");
                request("POST", path, b.toString(), true);
            } catch (Throwable ignored) {
            }
        });
    }

    void reportStart(Movie m, PlayInfo pi, long ticks) {
        report("/Sessions/Playing", m, pi, ticks, false);
    }

    void reportProgress(Movie m, PlayInfo pi, long ticks, boolean paused) {
        report("/Sessions/Playing/Progress", m, pi, ticks, paused);
    }

    void reportStopped(Movie m, PlayInfo pi, long ticks) {
        report("/Sessions/Playing/Stopped", m, pi, ticks, false);
    }

    // ------------------------------------------------------------ rete locale

    /** Cerca server Jellyfin sulla LAN con il broadcast UDP di scoperta (porta 7359). */
    void discover(Done<List<String>> cb) {
        POOL.execute(() -> {
            final List<String> found = new ArrayList<>();
            try (DatagramSocket s = new DatagramSocket()) {
                s.setBroadcast(true);
                s.setSoTimeout(1500);
                byte[] q = "who is JellyfinServer?".getBytes("UTF-8");
                s.send(new DatagramPacket(q, q.length, InetAddress.getByName("255.255.255.255"), 7359));
                long end = System.currentTimeMillis() + 2500;
                while (System.currentTimeMillis() < end) {
                    try {
                        byte[] buf = new byte[2048];
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        s.receive(p);
                        String addr = new JSONObject(new String(p.getData(), 0, p.getLength(), "UTF-8")).optString("Address");
                        if (!addr.isEmpty() && !found.contains(addr)) found.add(addr);
                    } catch (java.net.SocketTimeoutException e) {
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
            MAIN.post(() -> cb.ok(found));
        });
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
