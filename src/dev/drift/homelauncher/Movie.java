package dev.drift.homelauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Un film della libreria Jellyfin, con i soli campi che usiamo. */
final class Movie {
    static final class Stream {
        String type = "", codec = "", title = "", lang = "", range = "";
        int index, width, height, channels;
        boolean isDefault, external;
    }

    String id = "", name = "", overview = "", officialRating = "", container = "", imgTag = "", backdropTag = "";
    int year;
    long runtimeTicks, positionTicks;
    double rating, playedPct;
    boolean played;
    String mediaSourceId = "";
    final List<String> genres = new ArrayList<>();
    final List<Stream> streams = new ArrayList<>();

    static Movie from(JSONObject o) {
        Movie m = new Movie();
        m.id = o.optString("Id");
        m.name = o.optString("Name");
        m.overview = o.optString("Overview");
        m.officialRating = o.optString("OfficialRating");
        m.year = o.optInt("ProductionYear");
        m.runtimeTicks = o.optLong("RunTimeTicks");
        m.rating = o.optDouble("CommunityRating", 0);
        JSONObject tags = o.optJSONObject("ImageTags");
        if (tags != null) m.imgTag = tags.optString("Primary");
        JSONArray bd = o.optJSONArray("BackdropImageTags");
        if (bd != null && bd.length() > 0) m.backdropTag = bd.optString(0);
        JSONObject ud = o.optJSONObject("UserData");
        if (ud != null) {
            m.played = ud.optBoolean("Played");
            m.playedPct = ud.optDouble("PlayedPercentage", 0);
            m.positionTicks = ud.optLong("PlaybackPositionTicks");
        }
        JSONArray g = o.optJSONArray("Genres");
        if (g != null) for (int i = 0; i < g.length(); i++) m.genres.add(g.optString(i));
        JSONArray srcs = o.optJSONArray("MediaSources");
        if (srcs != null && srcs.length() > 0) {
            JSONObject s = srcs.optJSONObject(0);
            m.container = s.optString("Container");
            m.mediaSourceId = s.optString("Id");
            m.streams.addAll(parseStreams(s.optJSONArray("MediaStreams")));
        }
        return m;
    }

    static List<Stream> parseStreams(JSONArray ms) {
        List<Stream> out = new ArrayList<>();
        if (ms == null) return out;
        for (int i = 0; i < ms.length(); i++) {
            JSONObject j = ms.optJSONObject(i);
            Stream st = new Stream();
            st.type = j.optString("Type");
            st.codec = j.optString("Codec");
            st.title = j.optString("DisplayTitle");
            st.lang = j.optString("Language");
            st.range = j.optString("VideoRangeType");
            st.index = j.optInt("Index");
            st.width = j.optInt("Width");
            st.height = j.optInt("Height");
            st.channels = j.optInt("Channels");
            st.isDefault = j.optBoolean("IsDefault");
            st.external = j.optBoolean("IsExternal");
            out.add(st);
        }
        return out;
    }

    Stream video() {
        for (Stream s : streams) if (s.type.equals("Video")) return s;
        return null;
    }

    List<Stream> of(String type) {
        List<Stream> l = new ArrayList<>();
        for (Stream s : streams) if (s.type.equals(type)) l.add(s);
        return l;
    }

    /** "4K", "1080p", "720p"... dalla larghezza del video. */
    String resolution() {
        Stream v = video();
        if (v == null) return "";
        if (v.width >= 3600) return "4K";
        if (v.width >= 1800) return "1080p";
        if (v.width >= 1200) return "720p";
        return v.height > 0 ? v.height + "p" : "";
    }

    /** "Dolby Vision", "HDR10", "HLG" oppure "" per SDR. */
    String hdr() {
        Stream v = video();
        if (v == null) return "";
        String r = v.range;
        if (r.startsWith("DOVI")) return "Dolby Vision";
        if (r.startsWith("HDR10Plus")) return "HDR10+";
        if (r.startsWith("HDR10")) return "HDR10";
        if (r.startsWith("HLG")) return "HLG";
        return "";
    }

    int minutes() {
        return (int) (runtimeTicks / 600000000L);
    }

    double progress() {
        if (runtimeTicks <= 0) return 0;
        return Math.min(1.0, positionTicks / (double) runtimeTicks);
    }
}
