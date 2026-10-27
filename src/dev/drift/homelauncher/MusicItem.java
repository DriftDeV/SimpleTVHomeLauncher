package dev.drift.homelauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Elemento della libreria musicale di Jellyfin: brano (Audio), album (MusicAlbum) o artista (MusicArtist). */
final class MusicItem {
    static final int SONG = 0, ALBUM = 1, ARTIST = 2;

    int kind;
    String id = "", name = "", artist = "", artistId = "", album = "", albumId = "";
    String imgTag = "", albumImgTag = "";
    int year, index, disc, childCount;
    long runtimeTicks;

    // dettagli del brano (solo dopo detail(): servono alla scheda "Informazioni" del player)
    boolean detailed;
    String genres = "", container = "", codec = "", profile = "", layout = "", added = "";
    int bitrate, sampleRate, bitDepth, channels, playCount;
    long size;

    /** Completa il brano con i dati della risposta di /Items/{id}: genere, ascolti e il flusso audio del file. */
    void detail(JSONObject o) {
        JSONArray g = o.optJSONArray("Genres");
        StringBuilder gs = new StringBuilder();
        if (g != null) for (int i = 0; i < g.length(); i++) gs.append(i > 0 ? ", " : "").append(g.optString(i));
        genres = gs.toString();
        if (year == 0) year = o.optInt("ProductionYear");
        added = o.optString("DateCreated");
        JSONObject ud = o.optJSONObject("UserData");
        if (ud != null) playCount = ud.optInt("PlayCount");
        JSONArray srcs = o.optJSONArray("MediaSources");
        JSONObject s = srcs != null && srcs.length() > 0 ? srcs.optJSONObject(0) : null;
        if (s != null) {
            container = s.optString("Container");
            size = s.optLong("Size");
            bitrate = s.optInt("Bitrate");
            JSONArray ms = s.optJSONArray("MediaStreams");
            for (int i = 0; ms != null && i < ms.length(); i++) {
                JSONObject st = ms.optJSONObject(i);
                if (!"Audio".equals(st.optString("Type"))) continue;
                codec = st.optString("Codec");
                profile = st.optString("Profile");
                if (st.optInt("BitRate") > 0) bitrate = st.optInt("BitRate");
                sampleRate = st.optInt("SampleRate");
                bitDepth = st.optInt("BitDepth");
                channels = st.optInt("Channels");
                layout = st.optString("ChannelLayout");
                break;
            }
        }
        detailed = true;
    }

    /** Formati che il player della TV legge cosi' come sono; il resto il server lo converte in MP3. */
    static final String DIRECT = "mp3,aac,m4a,flac,ogg,opus,wav";

    /** True se il server mandera' il file originale (stessa regola dell'indirizzo del flusso). */
    boolean direct() {
        if (container.isEmpty()) return true;
        for (String c : container.toLowerCase(java.util.Locale.ROOT).split(",")) {
            if (("," + DIRECT + ",").contains("," + c.trim() + ",")) {
                // m4a puo' contenere ALAC, che le TV con Android 9 non leggono: va diretto solo con AAC
                return !c.trim().equals("m4a") || codec.isEmpty() || codec.equalsIgnoreCase("aac");
            }
        }
        return false;
    }

    static MusicItem from(JSONObject o) {
        MusicItem m = new MusicItem();
        String type = o.optString("Type");
        m.kind = "MusicAlbum".equals(type) ? ALBUM : "MusicArtist".equals(type) ? ARTIST : SONG;
        m.id = o.optString("Id");
        m.name = o.optString("Name");
        // l'artista dell'album prima di quelli del brano: "feat." e ospiti non spostano il brano sotto un altro nome
        m.artist = o.optString("AlbumArtist");
        if (m.artist.isEmpty()) {
            JSONArray a = o.optJSONArray("Artists");
            if (a != null && a.length() > 0) m.artist = a.optString(0);
        }
        JSONArray aa = o.optJSONArray("AlbumArtists");
        if (aa != null && aa.length() > 0) m.artistId = aa.optJSONObject(0).optString("Id");
        m.album = o.optString("Album");
        m.albumId = o.optString("AlbumId");
        JSONObject tags = o.optJSONObject("ImageTags");
        if (tags != null) m.imgTag = tags.optString("Primary");
        m.albumImgTag = o.optString("AlbumPrimaryImageTag");
        m.year = o.optInt("ProductionYear");
        m.index = o.optInt("IndexNumber");
        m.disc = o.optInt("ParentIndexNumber");
        m.childCount = o.optInt("ChildCount");
        m.runtimeTicks = o.optLong("RunTimeTicks");
        return m;
    }

    static List<MusicItem> list(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        JSONArray a = o.optJSONArray("Items");
        List<MusicItem> l = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) l.add(from(a.getJSONObject(i)));
        return l;
    }

    long durationMs() {
        return runtimeTicks / 10000L;
    }

    /** "3:07"; vuoto se la durata non si conosce. */
    static String clock(long ms) {
        if (ms <= 0) return "";
        long s = ms / 1000;
        return s >= 3600 ? String.format(java.util.Locale.ITALIAN, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                : String.format(java.util.Locale.ITALIAN, "%d:%02d", s / 60, s % 60);
    }
}
