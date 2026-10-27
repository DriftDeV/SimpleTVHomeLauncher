package dev.drift.homelauncher;

import android.app.Activity;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserManager;
import android.os.StatFs;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends Activity {
    private static final class Profile {
        final int id;
        String account; // account Google collegato, null = utente locale
        String name;

        Profile(int id, String account, String name) {
            this.id = id;
            this.account = account;
            this.name = name;
        }
    }

    private static final String[] DEFAULT_FAVS = {
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android", "com.google.android.youtube.tvmusic", "app.symfonik.music.player",
            "com.limelight", "com.android.vending", "com.tcl.tv", "com.tcl.ui_mediaCenter"};
    private static final String[] DEFAULT_HIDDEN = {
            "com.tcl.factory.view", "com.tcl.keyhelp", "com.tcl.initsetup", "com.android.tv.settings",
            "com.android.gallery3d", "com.android.wallpaper.livepicker"};
    private static final int PAD = 80;

    private float u;
    private SharedPreferences sp;
    private List<AppEntry> apps = new ArrayList<>();
    private List<String> favs = new ArrayList<>();
    private Set<String> hidden = new HashSet<>();

    private BgView bg;
    private TextView heroTitle, heroSub, clock, status, otherLabel;
    private Row rowFav, rowOther;
    private LinearLayout listFav, listOther;
    private LinearLayout pills;
    private int hiddenCount;
    private LinearLayout actions;
    private Runnable tintRun;
    private UserChip userChip;
    private MusicPanel musicPanel;
    private FilmPanel filmPanel;
    private Pill homePill, musicPill, filmPill;
    private static final int TAB_HOME = 0, TAB_MUSIC = 1, TAB_FILM = 2;
    private int tab = TAB_HOME;
    private View rootView;
    private final List<Profile> profiles = new ArrayList<>();
    private int curId;
    private static final int[] AVATAR = {0xFF5E5CE6, 0xFFFF9F0A, 0xFF30D158, 0xFFFF375F, 0xFF64D2FF, 0xFFBF5AF2};
    private final Handler handler = new Handler();
    private final SimpleDateFormat fmtTime = new SimpleDateFormat("HH:mm", Locale.ITALIAN);
    private final SimpleDateFormat fmtDate = new SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN);
    private boolean started;

    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateStatus();
            if (started) handler.postDelayed(this, 15000);
        }
    };
    private final OnAccountsUpdateListener accountsListener = accounts -> onAccountsChanged();
    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            loadAsync(null);
        }
    };

    private int px(float v) {
        return Math.round(v * u);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        u = getResources().getDisplayMetrics().heightPixels / 720f;
        sp = getSharedPreferences("home", MODE_PRIVATE);
        loadProfiles();
        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF08080C));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildUi();
        loadAsync(null);
        MusicTracker.get(this).start();
        if (checkSelfPermission(android.Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.GET_ACCOUNTS}, 1);
        }
    }

    // ---------------------------------------------------------------- UI

    private TextView text(String s, float size, String face, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u * A11y.textScale(this));
        t.setTypeface(Typeface.create(face, Typeface.NORMAL));
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private FrameLayout.LayoutParams at(int left, int top, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = left;
        lp.topMargin = top;
        return lp;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this) {
            /** La quota Z sposta la barra in fondo all'albero: la rimettiamo in testa (ordine di lettura). */
            @Override
            public void addChildrenForAccessibility(ArrayList<View> out) {
                ArrayList<View> all = new ArrayList<>();
                super.addChildrenForAccessibility(all);
                for (View v : all) if (lifted(v)) out.add(v);
                for (View v : all) if (!lifted(v)) out.add(v);
            }

            private boolean lifted(View v) {
                while (v.getParent() != this && v.getParent() instanceof View) v = (View) v.getParent();
                return v.getTranslationZ() > 0;
            }
        };
        root.setClipChildren(false);
        bg = new BgView(this);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        musicPanel = new MusicPanel(this, u);
        musicPanel.setVisibility(View.GONE);
        filmPanel = new FilmPanel(this, u);
        filmPanel.setVisibility(View.GONE);

        // scurisce leggermente la fascia alta: la barra resta leggibile su qualsiasi tinta (contrasto).
        // Portante: con lo scrim a 0x8C e la tinta di AppEntry.dominant() con V <= 0,68 il testo dei pulsanti a riposo
        // sta sopra 4,6:1; senza scrim scende a ~3,4:1. Se cambi uno dei due valori, ricalcola il contrasto.
        View scrim = new View(this);
        scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x8C000000, 0x00000000}));
        root.addView(onTop(scrim), at(0, 0, -1, px(150)));

        // barra alta: tab (navigano fra sezioni) a sinistra; azioni, orologio e utente a destra
        pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setClipChildren(false);
        homePill = addPill("Home", () -> setTab(TAB_HOME));
        filmPill = addPill("Film", () -> setTab(TAB_FILM));
        musicPill = addPill("Musica", () -> setTab(TAB_MUSIC));
        root.addView(onTop(pills), at(px(PAD), px(38), -2, px(48)));

        LinearLayout topRight = new LinearLayout(this);
        topRight.setOrientation(LinearLayout.HORIZONTAL);
        topRight.setGravity(Gravity.CENTER_VERTICAL);
        topRight.setClipChildren(false); // il focus ingrandisce i pulsanti: senza questo l'anello viene tagliato in alto e in basso
        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setClipChildren(false);
        addAction(0, getString(R.string.a11y_inputs), this::openSources);
        addAction(1, getString(R.string.a11y_network), () -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        addAction(2, getString(R.string.a11y_settings), this::openSettings);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2);
        ap.rightMargin = px(30);
        topRight.addView(actions, ap);
        clock = text("", 50, "sans-serif-light", 0xFFFFFFFF);
        topRight.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        userChip = new UserChip(this, u);
        userChip.setOnClickListener(v -> openUserMenu());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.leftMargin = px(24);
        topRight.addView(userChip, cp);
        FrameLayout.LayoutParams tr = at(0, px(22), -2, px(60));
        tr.gravity = Gravity.RIGHT;
        tr.rightMargin = px(PAD);
        tr.leftMargin = 0;
        root.addView(onTop(topRight), tr);

        status = text("", 18, "sans-serif", 0xCCFFFFFF);
        status.setGravity(Gravity.RIGHT);
        FrameLayout.LayoutParams sl = at(0, px(86), px(700), px(26));
        sl.gravity = Gravity.RIGHT;
        sl.rightMargin = px(PAD);
        sl.leftMargin = 0;
        root.addView(onTop(status), sl);

        // hero: nome e dettagli dell'app a fuoco
        heroTitle = text("", 64, "sans-serif-light", 0xFFFFFFFF);
        root.addView(A11y.hide(heroTitle), at(px(PAD), px(130), px(1000), -2)); // ripete il nome della card a fuoco
        heroSub = text("", 21, "sans-serif", 0xB3FFFFFF);
        root.addView(heroSub, at(px(PAD), px(214), px(1000), px(28)));

        // riga preferiti
        listFav = new LinearLayout(this);
        listFav.setOrientation(LinearLayout.HORIZONTAL);
        listFav.setGravity(Gravity.CENTER_VERTICAL);
        listFav.setClipChildren(false);
        listFav.setClipToPadding(false);
        listFav.setPadding(px(PAD), 0, px(PAD), 0);
        rowFav = new Row(this);
        rowFav.addView(listFav, new ViewGroup.LayoutParams(-2, -1));
        root.addView(rowFav, at(0, px(262), -1, px(250)));

        // riga altre app
        otherLabel = A11y.heading(text("Altre app", 22, "sans-serif-medium", 0xCCFFFFFF));
        root.addView(otherLabel, at(px(PAD), px(526), px(400), px(26)));
        listOther = new LinearLayout(this);
        listOther.setOrientation(LinearLayout.HORIZONTAL);
        listOther.setGravity(Gravity.CENTER_VERTICAL);
        listOther.setClipChildren(false);
        listOther.setClipToPadding(false);
        listOther.setPadding(px(PAD), 0, px(PAD), 0);
        rowOther = new Row(this);
        rowOther.addView(listOther, new ViewGroup.LayoutParams(-2, -1));
        root.addView(rowOther, at(0, px(552), -1, px(150)));

        root.addView(musicPanel, new FrameLayout.LayoutParams(-1, -1));
        root.addView(filmPanel, new FrameLayout.LayoutParams(-1, -1));
        rootView = root;
        setContentView(root);
        updateClock();
        updateStatus();
        updateChip();
        homePill.setActive(true);
    }

    /**
     * La barra superiore viene prima nell'albero (ordine di lettura) ma deve restare disegnata sopra ai pannelli
     * Film e Musica, che sono aggiunti dopo: la quota Z la porta sopra, senza ombra perche' non ha contorno.
     */
    private <T extends View> T onTop(T v) {
        v.setOutlineProvider(null);
        v.setTranslationZ(4);
        return v;
    }

    private void addAction(int type, String description, Runnable action) {
        IconButton b = new IconButton(this, type, description, u);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(actions.getChildCount() == 0 ? 0 : 12);
        actions.addView(b, lp);
    }

    private Pill addPill(String label, Runnable action) {
        Pill p = new Pill(this, label, u);
        p.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -1);
        lp.rightMargin = px(12);
        pills.addView(p, lp);
        return p;
    }

    private void updateClock() {
        Date now = new Date();
        String t = fmtTime.format(now);
        clock.setText(t);
        clock.setContentDescription(getString(R.string.a11y_clock, t));
    }

    /** Solo la data; la rete compare soltanto se manca (RAM e spazio disco non servono in salotto). */
    private void updateStatus() {
        String d = fmtDate.format(new Date());
        StringBuilder sb = new StringBuilder(Character.toUpperCase(d.charAt(0)) + d.substring(1));
        String n = network();
        if (n.equals("Offline")) sb.append("   ·   Offline");
        status.setText(sb.toString());
        status.setContentDescription(getString(R.string.a11y_date, sb.toString()));
    }

    private String network() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return "Offline";
            if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) return "Ethernet";
            if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                WifiInfo wi = wm.getConnectionInfo();
                if (wi != null && wi.getLinkSpeed() > 0) {
                    int lvl = WifiManager.calculateSignalLevel(wi.getRssi(), 5);
                    return "Wi-Fi " + wi.getLinkSpeed() + " Mbps (" + lvl + "/4)";
                }
                return "Wi-Fi";
            }
            return ni.getTypeName();
        } catch (Throwable t) {
            return "Rete";
        }
    }

    // ---------------------------------------------------------------- dati

    private void loadAsync(final String focusPkg) {
        new Thread(() -> {
            final List<AppEntry> list = queryApps();
            runOnUiThread(() -> {
                apps = list;
                loadPrefs();
                rebuildRows(focusPkg);
            });
        }).start();
    }

    private List<AppEntry> queryApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppEntry> out = new HashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || out.containsKey(pkg)) continue;
                Drawable banner = null;
                try {
                    banner = ri.activityInfo.loadBanner(pm);
                    if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
                } catch (Throwable ignored) {
                }
                Drawable icon = ri.loadIcon(pm);
                String ver = "";
                try {
                    PackageInfo pi = pm.getPackageInfo(pkg, 0);
                    if (pi.versionName != null) ver = pi.versionName;
                } catch (Throwable ignored) {
                }
                Intent li = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                out.put(pkg, new AppEntry(pkg, ri.loadLabel(pm), li, banner, icon, ver));
            }
        }
        List<AppEntry> l = new ArrayList<>(out.values());
        Collections.sort(l, (a, b) -> a.label.toString().compareToIgnoreCase(b.label.toString()));
        return l;
    }

    private void loadProfiles() {
        profiles.clear();
        String raw = sp.getString("profiles", null);
        if (raw != null) {
            for (String e : raw.split("\n")) {
                String[] f = e.split("\t", 3);
                try {
                    if (f.length == 3) {
                        profiles.add(new Profile(Integer.parseInt(f[0]), f[1].isEmpty() ? null : f[1], f[2]));
                    } else {
                        int i = e.indexOf(':'); // formato vecchio: id:nome
                        if (i > 0) profiles.add(new Profile(Integer.parseInt(e.substring(0, i)), null, e.substring(i + 1)));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (profiles.isEmpty() && sp.contains("fav")) {
            // migra le preferenze della versione senza profili
            sp.edit().putString("fav_0", sp.getString("fav", ""))
                    .putStringSet("hid_0", sp.getStringSet("hidden", new HashSet<String>())).apply();
        }
        curId = sp.getInt("cur", 0);
        syncProfiles();
    }

    private void saveProfiles() {
        StringBuilder sb = new StringBuilder();
        for (Profile p : profiles) {
            sb.append(p.id).append('\t').append(p.account == null ? "" : p.account).append('\t')
                    .append(p.name).append('\n');
        }
        sp.edit().putString("profiles", sb.toString()).putInt("cur", curId).apply();
    }

    /** Account Google presenti sulla TV; null se non possiamo leggerli (permesso mancante). */
    private List<Account> googleAccounts() {
        try {
            if (checkSelfPermission(android.Manifest.permission.GET_ACCOUNTS)
                    != PackageManager.PERMISSION_GRANTED) return null;
            return Arrays.asList(AccountManager.get(this).getAccountsByType("com.google"));
        } catch (Throwable t) {
            return null;
        }
    }

    private String systemUserName() {
        try {
            String n = ((UserManager) getSystemService(USER_SERVICE)).getUserName();
            if (n != null && !n.isEmpty()) return n;
        } catch (Throwable ignored) {
        }
        return "Utente";
    }

    /** nome.cognome99@gmail.com -> "Nome.cognome" come nome iniziale del profilo (rinominabile). */
    private static String prettify(String account) {
        String s = account.contains("@") ? account.substring(0, account.indexOf('@')) : account;
        s = s.replaceAll("[0-9._-]+$", "");
        if (s.isEmpty()) s = account;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private int nextId() {
        int id = 0;
        for (Profile p : profiles) id = Math.max(id, p.id + 1);
        return id;
    }

    /** Allinea i profili agli account Google reali: uno per account, nessun profilo orfano. */
    private void syncProfiles() {
        List<Account> acc = googleAccounts();
        if (acc != null) {
            Set<String> names = new HashSet<>();
            for (Account a : acc) names.add(a.name);
            for (Iterator<Profile> it = profiles.iterator(); it.hasNext(); ) {
                Profile p = it.next();
                if (p.account != null && !names.contains(p.account)) {
                    sp.edit().remove("fav_" + p.id).remove("hid_" + p.id).remove("av_" + p.id).apply();
                    it.remove();
                }
            }
            for (Account a : acc) {
                boolean has = false;
                for (Profile p : profiles) if (a.name.equals(p.account)) has = true;
                if (has) continue;
                Profile free = null;
                for (Profile p : profiles) if (p.account == null) {
                    free = p;
                    break;
                }
                if (free != null) {
                    free.account = a.name;
                    if (free.name.equals("Utente") || free.name.startsWith("Profilo")) free.name = prettify(a.name);
                } else {
                    profiles.add(new Profile(nextId(), a.name, prettify(a.name)));
                }
            }
            if (!acc.isEmpty()) {
                for (Iterator<Profile> it = profiles.iterator(); it.hasNext(); ) {
                    Profile p = it.next();
                    if (p.account == null) {
                        sp.edit().remove("fav_" + p.id).remove("hid_" + p.id).remove("av_" + p.id).apply();
                        it.remove();
                    }
                }
            }
        }
        if (profiles.isEmpty()) profiles.add(new Profile(0, null, systemUserName()));
        if (profile(curId) == null) curId = profiles.get(0).id;
        saveProfiles();
    }

    private Profile profile(int id) {
        for (Profile p : profiles) if (p.id == id) return p;
        return null;
    }

    private void updateChip() {
        Profile p = profile(curId);
        userChip.set(p.name, AVATAR[p.id % AVATAR.length]);
        int av = avatarOf(p.id);
        userChip.setPhoto(av >= 0 ? AvatarArt.bitmap(av, 128) : null);
        userChip.setContentDescription(getString(R.string.a11y_profile, p.name));
    }

    private void loadPrefs() {
        String f = sp.getString("fav_" + curId, null);
        if (f == null) {
            favs = new ArrayList<>();
            for (String p : DEFAULT_FAVS) favs.add(p);
            hidden = new HashSet<>(Arrays.asList(DEFAULT_HIDDEN));
            savePrefs();
        } else {
            favs = new ArrayList<>();
            for (String p : f.split(",")) if (!p.isEmpty()) favs.add(p);
            hidden = new HashSet<>(sp.getStringSet("hid_" + curId, new HashSet<String>()));
        }
    }

    private void savePrefs() {
        StringBuilder sb = new StringBuilder();
        for (String p : favs) sb.append(p).append(',');
        sp.edit().putString("fav_" + curId, sb.toString()).putStringSet("hid_" + curId, new HashSet<>(hidden)).apply();
    }

    private AppEntry find(String pkg) {
        for (AppEntry e : apps) if (e.pkg.equals(pkg)) return e;
        return null;
    }

    private List<AppEntry> visibleFavs() {
        List<AppEntry> l = new ArrayList<>();
        for (String p : favs) {
            AppEntry e = find(p);
            if (e != null && !hidden.contains(p) && !l.contains(e)) l.add(e);
        }
        return l;
    }

    private void rebuildRows(String focusPkg) {
        listFav.removeAllViews();
        listOther.removeAllViews();
        List<AppEntry> fl = visibleFavs();
        Card focus = null;
        for (AppEntry e : fl) {
            Card c = makeCard(e, 320, 180, 1.1f, 26);
            listFav.addView(c, margins(320, 180, 26));
            if (e.pkg.equals(focusPkg)) focus = c;
        }
        int others = 0;
        for (AppEntry e : apps) {
            if (fl.contains(e) || hidden.contains(e.pkg)) continue;
            Card c = makeCard(e, 176, 99, 1.12f, 18);
            listOther.addView(c, margins(176, 99, 18));
            if (e.pkg.equals(focusPkg)) focus = c;
            others++;
        }
        otherLabel.setVisibility(others > 0 ? View.VISIBLE : View.GONE);
        rowOther.setVisibility(others > 0 ? View.VISIBLE : View.GONE);
        int hc = 0;
        for (AppEntry e : apps) if (hidden.contains(e.pkg)) hc++;
        hiddenCount = hc;
        if (focus == null && listFav.getChildCount() > 0) focus = (Card) listFav.getChildAt(0);
        if (focus == null && listOther.getChildCount() > 0) focus = (Card) listOther.getChildAt(0);
        if (tab != TAB_HOME) {
            for (View v : new View[]{heroTitle, heroSub, rowFav, otherLabel, rowOther}) {
                if (v.getVisibility() == View.VISIBLE) v.setVisibility(View.INVISIBLE);
            }
        } else if (focus != null) focus.requestFocus();
        else heroTitle.setText("Nessuna app");
    }

    private LinearLayout.LayoutParams margins(int w, int h, int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(w), px(h));
        lp.rightMargin = px(gap);
        return lp;
    }

    private Card makeCard(final AppEntry e, int w, int h, float scale, int gap) {
        Card c = new Card(this, e, px(w), px(h), scale);
        c.setOnClickListener(v -> launch(e.intent));
        c.setOnLongClickListener(v -> {
            openAppMenu(e);
            return true;
        });
        c.setContentDescription(e.label);
        c.setOnFocusChangeListener((v, f) -> {
            if (!f) return;
            heroTitle.setText(e.label);
            heroSub.setText("");
            // la tinta di sfondo cambia solo se il focus si ferma: scorrendo veloce lo sfondo non lampeggia
            if (tintRun != null) handler.removeCallbacks(tintRun);
            final int color = e.color;
            tintRun = () -> bg.setTint(color);
            handler.postDelayed(tintRun, 250);
        });
        return c;
    }

    // ---------------------------------------------------------------- azioni

    private void launch(Intent i) {
        try {
            if ((i.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) == 0) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Impossibile aprire", Toast.LENGTH_SHORT).show();
        }
    }

    /** Pannello a vetro: istantanea sfocata della home come sfondo. */
    private MenuDialog menu(String title, String subtitle) {
        Bitmap back = null;
        try {
            back = Blur.snapshot(rootView, 6, 4);
        } catch (Throwable ignored) {
        }
        return new MenuDialog(this, title, subtitle, u, back);
    }

    private void openUserMenu() {
        final Profile cur = profile(curId);
        List<Account> acc = googleAccounts();
        String sub = cur.account != null ? cur.account
                : acc == null ? "Permesso account non concesso" : "Utente di sistema: " + systemUserName();
        MenuDialog m = menu("Utente", sub);
        for (final Profile p : profiles) {
            m.add(p.name + (p.id == curId ? "   ● attivo" : ""), () -> switchProfile(p.id));
        }
        m.add("Aggiungi account Google", () -> launch(new Intent(Settings.ACTION_ADD_ACCOUNT)
                .putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[]{"com.google"})));
        if (cur.account != null) {
            m.add("Gestisci account", () -> launch(new Intent("android.settings.ACCOUNT_SYNC_SETTINGS")
                    .putExtra("account_name", cur.account).putExtra("account_type", "com.google")
                    .putExtra("account", new Account(cur.account, "com.google"))));
        }
        m.add("Immagine profilo", () -> openAvatarPicker(cur));
        m.add("Rinomina profilo", () -> renameProfile(cur));
        if (hiddenCount > 0) m.add("App nascoste (" + hiddenCount + ")", this::openHiddenMenu);
        m.add("Impostazioni TV", this::openSettings);
        m.show();
    }

    private void switchProfile(int id) {
        curId = id;
        saveProfiles();
        loadPrefs();
        updateChip();
        rebuildRows(null);
    }

    private void renameProfile(final Profile p) {
        askName("Rinomina profilo", p.name, name -> {
            p.name = name;
            saveProfiles();
            updateChip();
        });
    }

    private int avatarOf(int profileId) {
        return sp.getInt("av_" + profileId, -1);
    }

    /** Selettore avatar: griglia di immagini prefatte, salvata per profilo. */
    private void openAvatarPicker(final Profile p) {
        final MenuDialog m = menu("Immagine profilo", p.name);
        int size = px(64), gap = px(10), cols = 6;
        int cur = avatarOf(p.id);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        LinearLayout row = null;
        for (int i = 0; i < AvatarArt.COUNT; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                grid.addView(row, new LinearLayout.LayoutParams(-2, -2));
            }
            final int idx = i;
            AvatarCell cell = new AvatarCell(this, i, size, i == cur);
            cell.setOnClickListener(v -> {
                sp.edit().putInt("av_" + p.id, idx).apply();
                m.dismiss();
                updateChip();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.rightMargin = gap;
            lp.bottomMargin = gap;
            row.addView(cell, lp);
        }
        m.addView(grid, new LinearLayout.LayoutParams(-2, -2));
        m.add("Usa l'iniziale", () -> {
            sp.edit().remove("av_" + p.id).apply();
            updateChip();
        });
        m.show();
    }

    /** Account aggiunti o rimossi dal sistema: riallinea profili e schermata. */
    private void onAccountsChanged() {
        int before = curId;
        syncProfiles();
        updateChip();
        if (curId != before) {
            loadPrefs();
            rebuildRows(null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        onAccountsChanged();
    }

    private interface Done {
        void run(String name);
    }

    private void askName(String title, String initial, final Done done) {
        MenuDialog m = menu(title, "OK per aprire la tastiera");
        final android.widget.EditText e = m.addInput("Nome", initial);
        m.add("Salva", () -> {
            String n = e.getText().toString().replaceAll("[\\n:]", " ").trim();
            if (n.isEmpty()) n = "Utente";
            if (n.length() > 16) n = n.substring(0, 16);
            done.run(n);
        });
        m.add("Annulla", null);
        m.show();
    }

    /** Cambia tab (Home, Musica, Film); la top bar resta sempre visibile. */
    private void setTab(int t) {
        if (t == TAB_FILM) filmPanel.setBackdrop(bg.snapshot());
        if (t == tab) {
            if (t == TAB_MUSIC) musicPanel.enter();
            else if (t == TAB_FILM) filmPanel.enter();
            return;
        }
        int old = tab;
        tab = t;
        homePill.setActive(t == TAB_HOME);
        musicPill.setActive(t == TAB_MUSIC);
        filmPill.setActive(t == TAB_FILM);
        View[] home = {heroTitle, heroSub, rowFav, otherLabel, rowOther};
        for (View v : home) {
            if (t != TAB_HOME && v.getVisibility() == View.VISIBLE) v.setVisibility(View.INVISIBLE);
            else if (t == TAB_HOME && v.getVisibility() == View.INVISIBLE) v.setVisibility(View.VISIBLE);
        }
        if (old == TAB_MUSIC) musicPanel.leave();
        if (old == TAB_FILM) filmPanel.leave();
        if (t == TAB_MUSIC) {
            try {
                musicPanel.setBackdrop(Blur.snapshot(rootView, 6, 4));
            } catch (Throwable ignored) {
            }
            musicPanel.enter();
        } else if (t == TAB_FILM) {
            filmPanel.enter();
        } else {
            if (listFav.getChildCount() > 0) listFav.getChildAt(0).requestFocus();
            else if (listOther.getChildCount() > 0) listOther.getChildAt(0).requestFocus();
        }
    }

    private void openSettings() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.android.tv.settings");
        launch(i != null ? i : new Intent(Settings.ACTION_SETTINGS));
    }

    private void openSources() {
        MenuDialog m = menu("Sorgenti", "Seleziona un ingresso");
        try {
            TvInputManager tm = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            for (final TvInputInfo info : tm.getTvInputList()) {
                String label = String.valueOf(info.loadCustomLabel(this));
                if (label.equals("null") || label.isEmpty()) label = String.valueOf(info.loadLabel(this));
                if (info.isPassthroughInput()) {
                    int st = tm.getInputState(info.getId());
                    label += st == TvInputManager.INPUT_STATE_CONNECTED ? "   ● collegato"
                            : st == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? "   ● standby" : "";
                    m.add(label, () -> launch(new Intent(Intent.ACTION_VIEW,
                            TvContract.buildChannelUriForPassthroughInput(info.getId()))));
                } else {
                    m.add(label, () -> {
                        Intent i = getPackageManager().getLaunchIntentForPackage("com.tcl.tv");
                        if (i != null) launch(i);
                    });
                }
            }
        } catch (Throwable t) {
            m.add("Apri app TV", () -> {
                Intent i = getPackageManager().getLaunchIntentForPackage("com.tcl.tv");
                if (i != null) launch(i);
            });
        }
        m.show();
    }

    private void openAppMenu(final AppEntry e) {
        final boolean fav = favs.contains(e.pkg) && visibleFavs().contains(e);
        MenuDialog m = menu(String.valueOf(e.label), e.pkg);
        m.add("Apri", () -> launch(e.intent));
        if (fav) {
            m.add("Sposta a sinistra", () -> move(e, -1));
            m.add("Sposta a destra", () -> move(e, 1));
            m.add("Rimuovi dai preferiti", () -> {
                favs.remove(e.pkg);
                savePrefs();
                rebuildRows(e.pkg);
            });
        } else {
            m.add("Aggiungi ai preferiti", () -> {
                favs.remove(e.pkg);
                favs.add(e.pkg);
                savePrefs();
                rebuildRows(e.pkg);
            });
        }
        m.add("Nascondi", () -> {
            hidden.add(e.pkg);
            savePrefs();
            rebuildRows(null);
        });
        m.add("Info app", () -> launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + e.pkg))));
        m.show();
    }

    private void move(AppEntry e, int dir) {
        List<AppEntry> vis = visibleFavs();
        int i = vis.indexOf(e), j = i + dir;
        if (i < 0 || j < 0 || j >= vis.size()) {
            rebuildRows(e.pkg);
            return;
        }
        Collections.swap(favs, favs.indexOf(vis.get(i).pkg), favs.indexOf(vis.get(j).pkg));
        savePrefs();
        rebuildRows(e.pkg);
    }

    private void openHiddenMenu() {
        MenuDialog m = menu("App nascoste", "Scegli quale ripristinare");
        for (final AppEntry e : apps) {
            if (!hidden.contains(e.pkg)) continue;
            m.add(String.valueOf(e.label), () -> {
                hidden.remove(e.pkg);
                savePrefs();
                rebuildRows(e.pkg);
            });
        }
        m.show();
    }

    // ---------------------------------------------------------------- ciclo di vita

    @Override
    protected void onStart() {
        super.onStart();
        if (tab == TAB_FILM) filmPanel.refreshIfShown();
        started = true;
        handler.removeCallbacks(statusTick);
        handler.post(statusTick);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
        try {
            AccountManager.get(this).addOnAccountsUpdatedListener(accountsListener, handler, false);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        handler.removeCallbacks(statusTick);
        try {
            AccountManager.get(this).removeOnAccountsUpdatedListener(accountsListener);
        } catch (Throwable ignored) {
        }
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Throwable ignored) {
        }
    }

    /** HOME premuto mentre siamo gia' in primo piano: torna al primo preferito. */
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (tab != TAB_HOME) setTab(TAB_HOME);
        if (listFav.getChildCount() > 0) {
            rowFav.smoothScrollTo(0, 0);
            listFav.getChildAt(0).requestFocus();
        }
    }

    /** SU dalla riga preferiti: vai al pulsante in alto piu' vicino in orizzontale (non a caso). */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
            View f = getCurrentFocus();
            if (f instanceof Card && (f.getParent() == listFav
                    || (f.getParent() == listOther && listFav.getChildCount() == 0))) {
                int[] loc = new int[2];
                f.getLocationOnScreen(loc);
                float cx = loc[0] + f.getWidth() * f.getScaleX() / 2f;
                View best = null;
                float bestD = Float.MAX_VALUE;
                List<View> tops = new ArrayList<>();
                for (int i = 0; i < pills.getChildCount(); i++) tops.add(pills.getChildAt(i));
                for (int i = 0; i < actions.getChildCount(); i++) tops.add(actions.getChildAt(i));
                tops.add(userChip);
                for (View v : tops) {
                    if (v.getVisibility() != View.VISIBLE) continue;
                    v.getLocationOnScreen(loc);
                    float d = Math.abs(loc[0] + v.getWidth() / 2f - cx);
                    if (d < bestD) {
                        bestD = d;
                        best = v;
                    }
                }
                if (best != null) {
                    best.requestFocus();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f instanceof Card) {
                openAppMenu(((Card) f).entry);
                return true;
            }
        }
        return super.onKeyDown(code, e);
    }

    @Override
    public void onBackPressed() {
        if (tab == TAB_FILM && filmPanel.onBack()) return;
        if (tab != TAB_HOME) {
            setTab(TAB_HOME);
            return;
        }
        // il launcher non si chiude
    }
}
