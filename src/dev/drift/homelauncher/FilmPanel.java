package dev.drift.homelauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tab Film: accesso a un server Jellyfin della rete locale, righe di locandine con hero,
 * scheda del film e avvio della riproduzione. Nessuna libreria esterna, immagini ridimensionate dal server.
 */
final class FilmPanel extends FrameLayout {
    private static final int LOGIN = 0, LOADING = 1, LIBRARY = 2, ERROR = 3;

    private final Activity act;
    private final float u;
    private final Jelly jelly;
    private final MusicViews.Bg bg;
    private final FilmViews.Hero heroView;
    private final TextView heroTitle, heroMeta, heroOverview, status;
    private final LinearLayout badges, rowsBox, loginBox;
    private final ScrollView scroll;
    private final Pill retry, otherServer, loginBtn, findBtn, quickBtn;
    private final LinearLayout errBtns;
    private Bitmap homeBackdrop;
    private final TextView codeText;
    private String quickSecret;
    private boolean quickOn;
    private final TextView loginMsg;
    private final EditText fServer, fUser, fPass;
    private final AddressPicker picker; // inserimento semplificato: 192.168.x.y con porta 8096
    private final Pill modeBtn;
    private final TextView loginHint;
    private boolean advanced;
    private FrameLayout detail;
    private String detailId, reopenId; // reopenId: scheda da riaprire con dati freschi dopo il ritorno dal player
    private FilmViews.Poster detailFrom;
    private String heroFor = "";
    private boolean loaded;

    FilmPanel(Activity a, float u) {
        super(a);
        this.act = a;
        this.u = u;
        this.jelly = new Jelly(a);
        setClipChildren(false);
        bg = new MusicViews.Bg(a, null);
        addView(A11y.hide(bg), new LayoutParams(-1, -1));
        heroView = new FilmViews.Hero(a);
        addView(A11y.hide(heroView), at(520, 0, 760, 470));

        heroTitle = text(44, "sans-serif-light", 0xFFFFFFFF);
        addView(A11y.hide(heroTitle), at(80, 116, 760, -2)); // il titolo e' gia' nella descrizione della locandina
        heroMeta = text(20, "sans-serif", 0xE6FFFFFF);
        addView(A11y.hide(heroMeta), at(80, 178, 760, -2));
        badges = new LinearLayout(a);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        addView(A11y.hide(badges), at(80, 210, 760, 28));
        heroOverview = text(20, "sans-serif", 0xCCFFFFFF);
        heroOverview.setSingleLine(false);
        heroOverview.setMaxLines(2);
        addView(heroOverview, at(80, 250, 640, 56));

        scroll = new ScrollView(a);
        scroll.setFocusable(false); // contenitore: le fermate sono le locandine
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        rowsBox = new LinearLayout(a);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsBox.setClipChildren(false);
        scroll.addView(rowsBox, new LayoutParams(-1, -2));
        // viewport: clipChildren agisce sui bordi del figlio, quindi serve un contenitore perche' le righe non passino sopra hero e barra
        FrameLayout viewport = new FrameLayout(a);
        viewport.setClipChildren(true);
        viewport.addView(scroll, new LayoutParams(-1, -1));
        addView(viewport, at(0, 322, 1280, 398));

        // login
        loginBox = new LinearLayout(a);
        loginBox.setOrientation(LinearLayout.VERTICAL);
        loginBox.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView lt = A11y.heading(text(32, "sans-serif-light", 0xFFFFFFFF));
        lt.setText("Collega Jellyfin");
        lt.setGravity(Gravity.CENTER);
        loginBox.addView(lt, new LinearLayout.LayoutParams(-1, px(42)));
        loginHint = text(18, "sans-serif", 0xCCFFFFFF);
        loginHint.setGravity(Gravity.CENTER);
        loginBox.addView(loginHint, new LinearLayout.LayoutParams(-1, px(30)));
        fServer = field("Server (http://192.168.1.17:8096)", jelly.server, false);
        fUser = field("Utente", jelly.userName, false);
        fPass = field(jelly.hasSavedPassword() ? "Password (salvata)" : "Password", "", true);
        modeBtn = new Pill(a, "", u);
        picker = new AddressPicker(a, u, modeBtn, fUser);
        // tasto azione/invio: server -> utente -> password -> accedi (catena esplicita, non dipende dalla ricerca del focus)
        chain(fServer, fUser);
        chain(fUser, fPass);
        fPass.setOnEditorActionListener((v, action, ev) -> {
            if (!debounce()) post(this::doLogin);
            return true;
        });
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, px(40));
        mp.topMargin = px(6);
        loginBox.addView(modeBtn, mp);
        modeBtn.setOnClickListener(v -> toggleMode());
        loginBox.addView(fServer, fieldLp());
        loginBox.addView(picker, fieldLp());
        loginBox.addView(fUser, fieldLp());
        loginBox.addView(fPass, fieldLp());
        loginMsg = A11y.live(text(18, "sans-serif", 0xFFFFB4A9));
        loginMsg.setGravity(Gravity.CENTER);
        loginBox.addView(loginMsg, new LinearLayout.LayoutParams(-1, px(30)));
        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER);
        btns.setClipChildren(false);
        loginBox.setClipChildren(false);
        loginBtn = new Pill(a, "Accedi", u);
        loginBtn.setPrimary(true);
        quickBtn = new Pill(a, "Con codice", u);
        findBtn = new Pill(a, "Cerca server", u);
        loginBtn.setOnClickListener(v -> doLogin());
        quickBtn.setOnClickListener(v -> doQuick());
        findBtn.setOnClickListener(v -> doDiscover());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, px(48));
        bp.rightMargin = px(12);
        btns.addView(loginBtn, bp);
        LinearLayout.LayoutParams bq = new LinearLayout.LayoutParams(-2, px(48));
        bq.rightMargin = px(12);
        btns.addView(quickBtn, bq);
        btns.addView(findBtn, new LinearLayout.LayoutParams(-2, px(48)));
        loginBox.addView(btns, new LinearLayout.LayoutParams(-2, px(48)));
        // codice Quick Connect: grande, da leggere dal divano
        codeText = A11y.live(text(44, "sans-serif-medium", 0xFFFFFFFF));
        codeText.setGravity(Gravity.CENTER);
        codeText.setVisibility(GONE);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, px(56));
        cl.topMargin = px(14);
        loginBox.addView(codeText, cl);
        SharedPreferences prefs = a.getSharedPreferences("film", Context.MODE_PRIVATE);
        boolean simpleOk = jelly.server.isEmpty() || picker.load(jelly.server);
        applyMode(prefs.contains("advanced") ? prefs.getBoolean("advanced", false) || !simpleOk : !simpleOk);
        LayoutParams lp = at(380, 118, 520, -2);
        addView(loginBox, lp);

        // stato (caricamento / errore)
        status = A11y.live(text(24, "sans-serif-light", 0xFFFFFFFF));
        status.setGravity(Gravity.CENTER);
        addView(status, at(140, 300, 1000, 36));
        retry = new Pill(a, "Riprova", u);
        retry.setPrimary(true);
        retry.setOnClickListener(v -> loadLibrary(0));
        otherServer = new Pill(a, "Connetti a un altro server", u);
        otherServer.setOnClickListener(v -> otherServer());
        errBtns = new LinearLayout(a);
        errBtns.setOrientation(LinearLayout.HORIZONTAL);
        errBtns.setClipChildren(false);
        LinearLayout.LayoutParams er = new LinearLayout.LayoutParams(-2, px(46));
        er.rightMargin = px(12);
        errBtns.addView(retry, er);
        errBtns.addView(otherServer, new LinearLayout.LayoutParams(-2, px(46)));
        LayoutParams rp = at(0, 356, -2, 46);
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        rp.leftMargin = 0;
        addView(errBtns, rp);
        state(LOGIN, "");
    }

    // ------------------------------------------------------------ utilita'

    private int px(float v) {
        return Math.round(v * u);
    }

    private LayoutParams at(int left, int top, int w, int hh) {
        LayoutParams lp = new LayoutParams(w < 0 ? w : px(w), hh < 0 ? hh : px(hh));
        lp.leftMargin = px(left);
        lp.topMargin = px(top);
        return lp;
    }

    private TextView text(float size, String face, int color) {
        TextView t = new TextView(act);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u * A11y.textScale(act));
        t.setTypeface(Typeface.create(face, Typeface.NORMAL));
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(60));
        lp.topMargin = px(10);
        return lp;
    }

    private EditText field(String hint, String value, boolean password) {
        EditText e = new EditText(act);
        e.setText(value);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        e.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * u * A11y.textScale(act));
        e.setTextColor(0xFFFFFFFF);
        e.setHintTextColor(0x80FFFFFF);
        e.setPadding(px(18), 0, px(18), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x33FFFFFF);
        g.setCornerRadius(14 * u);
        e.setBackground(g);
        e.setImeOptions(password ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
        // senza tastiera aperta SU/GIU possono essere inghiottiti: sposta il focus a mano
        e.setOnKeyListener((v, code, ev) -> {
            // il rilascio dell'invio, dopo che ha spostato il focus, non deve far avanzare un altro campo
            if (code == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_UP
                    && android.os.SystemClock.uptimeMillis() - lastAction < 400) return true;
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            int dir = code == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN
                    : code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : 0;
            if (dir == 0) return false;
            View n = v.focusSearch(dir);
            if (n != null) n.requestFocus();
            return true;
        });
        return e;
    }

    private long lastAction;

    /** Invio/azione tastiera: l'IME e il tasto fisico possono arrivare entrambi, quindi si ignora il secondo (400 ms). */
    private boolean debounce() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastAction < 400) return true;
        lastAction = now;
        return false;
    }

    /**
     * Il focus si sposta un istante dopo: se lo spostassi subito, il rilascio del tasto invio
     * arriverebbe al campo appena attivato e Android lo farebbe avanzare di un altro campo.
     */
    private void chain(EditText from, final View to) {
        from.setOnEditorActionListener((v, action, ev) -> {
            if (!debounce()) post(() -> to.requestFocus());
            return true;
        });
    }

    /** Sfondo ambientale della home, da mostrare quando non c'e' un'immagine di film. */
    void setBackdrop(Bitmap b) {
        homeBackdrop = b;
    }

    private void state(int s, String msg) {
        loginBox.setVisibility(s == LOGIN ? VISIBLE : GONE);
        boolean lib = s == LIBRARY;
        for (View v : new View[]{heroView, heroTitle, heroMeta, badges, heroOverview, scroll}) {
            v.setVisibility(lib ? VISIBLE : GONE);
        }
        status.setVisibility(s == LOADING || s == ERROR ? VISIBLE : GONE);
        status.setText(msg);
        errBtns.setVisibility(s == ERROR ? VISIBLE : GONE);
        // accesso, caricamento ed errore: stesso sfondo ambientale della home, senza scurimento
        bg.setDimmed(lib);
        if (!lib) bg.set(homeBackdrop);
    }

    // ------------------------------------------------------------ ciclo di vita

    void enter() {
        setVisibility(VISIBLE);
        if (!jelly.loggedIn()) {
            state(LOGIN, "");
            (jelly.server.isEmpty() ? serverView() : fPass).requestFocus();
            if (jelly.server.isEmpty() && fServer.getText().length() == 0) doDiscover(); // primo avvio: cerca da solo
        } else if (!loaded) {
            loadLibrary(0);
        } else {
            state(LIBRARY, "");
            focusFirst();
        }
    }

    void leave() {
        removeCallbacks(refreshRun);
        quickOn = false;
        reopenId = null;
        closeDetail();
        setVisibility(GONE);
    }

    /** Dopo il ritorno dal player: aggiorna avanzamento e riga "continua a guardare". */
    void refreshIfShown() {
        // il player invia la posizione finale in onStop, dopo che la home e' gia' tornata in primo piano: si attende un attimo
        removeCallbacks(refreshRun);
        postDelayed(refreshRun, 1200);
    }

    private final Runnable refreshRun = new Runnable() {
        @Override
        public void run() {
            if (getVisibility() != VISIBLE || !loaded || !jelly.loggedIn()) return;
            // con la scheda aperta i dati sono vecchi ("Riproduci" invece di "Riprendi da"): si ricarica e la si riapre aggiornata
            if (detail != null) reopenId = detailId;
            loadLibrary(1);
        }
    };

    /** True se la pressione di Indietro e' stata gestita qui (chiude la scheda del film). */
    boolean onBack() {
        if (detail != null) {
            closeDetail();
            return true;
        }
        return false;
    }

    private void focusFirst() {
        View first = firstPoster();
        if (first != null) first.requestFocusFromTouch(); // anche con puntatore/touch: la libreria prende sempre il focus
    }

    private View firstPoster() {
        return findFirst(rowsBox);
    }

    private static View findFirst(View v) {
        if (v instanceof FilmViews.Poster) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = findFirst(g.getChildAt(i));
                if (f != null) return f;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ accesso

    private View serverView() {
        return advanced ? fServer : picker.first();
    }

    private String serverText() {
        return advanced ? fServer.getText().toString().trim() : picker.address();
    }

    /** Imposta il server in entrambi gli inserimenti; se non e' del tipo 192.168.x.y:8096 passa all'avanzato. */
    private void setServer(String s) {
        fServer.setText(s);
        if (!s.isEmpty() && !picker.load(s) && !advanced) applyMode(true);
    }

    private void applyMode(boolean adv) {
        advanced = adv;
        fServer.setVisibility(adv ? VISIBLE : GONE);
        picker.setVisibility(adv ? GONE : VISIBLE);
        modeBtn.setText(adv ? "Inserimento semplice" : "Inserimento avanzato");
        loginHint.setText(adv ? "Indirizzo del server nella tua rete, es. 192.168.1.17"
                : "Scegli i numeri con SU e GIU', OK per andare avanti");
    }

    private void toggleMode() {
        loginMsg.setTextColor(0xFFFFB4A9);
        loginMsg.setText("");
        if (advanced) {
            String cur = fServer.getText().toString().trim();
            if (!cur.isEmpty() && !picker.load(cur)) {
                loginMsg.setText("L'inserimento semplice vale solo per 192.168.x.x porta 8096");
                return;
            }
        } else {
            fServer.setText(picker.address());
        }
        applyMode(!advanced);
        act.getSharedPreferences("film", Context.MODE_PRIVATE).edit().putBoolean("advanced", advanced).apply();
        serverView().requestFocus();
    }

    private void doLogin() {
        loginMsg.setTextColor(0xFFFFB4A9);
        final String srv = serverText();
        final String usr = fUser.getText().toString().trim();
        if (srv.isEmpty() || usr.isEmpty()) {
            loginMsg.setText("Inserisci server e utente");
            return;
        }
        loginMsg.setText("");
        loginBtn.setText("Accesso…");
        jelly.login(srv, usr, fPass.getText().toString(), new Jelly.Done<Boolean>() {
            @Override
            public void ok(Boolean v) {
                loginBtn.setText("Accedi");
                fPass.setText("");
                loaded = false;
                loadLibrary(0);
            }

            @Override
            public void fail(String message, boolean unauthorized) {
                loginBtn.setText("Accedi");
                loginMsg.setText(unauthorized ? "Utente o password errati" : message);
            }
        });
    }

    /** Accesso con codice: la TV mostra un codice, lo si approva da telefono/browser (Impostazioni > Quick Connect). */
    private void doQuick() {
        String srv = serverText();
        if (srv.isEmpty()) {
            loginMsg.setText("Inserisci prima il server o usa Cerca server");
            return;
        }
        loginMsg.setText("");
        quickBtn.setText("Attendo…");
        jelly.quickInitiate(srv, new Jelly.Done<String[]>() {
            @Override
            public void ok(String[] r) {
                quickSecret = r[1];
                quickOn = true;
                codeText.setVisibility(VISIBLE);
                codeText.setText(r[0].length() == 6 ? r[0].substring(0, 3) + " " + r[0].substring(3) : r[0]);
                codeText.setContentDescription(act.getString(R.string.a11y_code, A11y.digits(r[0])));
                codeText.announceForAccessibility(act.getString(R.string.a11y_code_announce, A11y.digits(r[0])));
                loginMsg.setTextColor(0xCCFFFFFF);
                loginMsg.setText("Approvalo in Jellyfin › Quick Connect");
                pollQuick(0);
            }

            @Override
            public void fail(String message, boolean unauthorized) {
                quickBtn.setText("Con codice");
                loginMsg.setTextColor(0xFFFFB4A9);
                loginMsg.setText(message);
            }
        });
    }

    private void pollQuick(final int n) {
        final String secret = quickSecret;
        postDelayed(() -> {
            if (!quickOn || !secret.equals(quickSecret)) return;
            if (n > 90) {
                quickOn = false;
                quickBtn.setText("Con codice");
                codeText.setVisibility(GONE);
                loginMsg.setTextColor(0xFFFFB4A9);
                loginMsg.setText("Codice scaduto: riprova");
                return;
            }
            jelly.quickCheck(secret, new Jelly.Done<Boolean>() {
                @Override
                public void ok(Boolean authenticated) {
                    if (!authenticated) {
                        pollQuick(n + 1);
                        return;
                    }
                    jelly.quickLogin(secret, new Jelly.Done<Boolean>() {
                        @Override
                        public void ok(Boolean v) {
                            quickOn = false;
                            quickBtn.setText("Con codice");
                            codeText.setVisibility(GONE);
                            loginMsg.setText("");
                            loaded = false;
                            loadLibrary(0);
                        }

                        @Override
                        public void fail(String message, boolean unauthorized) {
                            quickOn = false;
                            quickBtn.setText("Con codice");
                            loginMsg.setTextColor(0xFFFFB4A9);
                            loginMsg.setText(message);
                        }
                    });
                }

                @Override
                public void fail(String message, boolean unauthorized) {
                    pollQuick(n + 1);
                }
            });
        }, 2000);
    }

    private void doDiscover() {
        loginMsg.setTextColor(0xFFFFB4A9);
        loginMsg.setText("");
        findBtn.setText("Cerco…");
        jelly.discover(new Jelly.Done<List<String>>() {
            @Override
            public void ok(List<String> found) {
                findBtn.setText("Cerca server");
                if (found.isEmpty()) {
                    loginMsg.setTextColor(0xFFFFB4A9);
                    loginMsg.setText("Nessun server trovato: inserisci l'indirizzo a mano");
                } else {
                    setServer(found.get(0));
                    loginMsg.setTextColor(0xCCFFFFFF);
                    loginMsg.setText(found.size() > 1 ? "Trovati " + found.size() + " server: uso il primo" : "Server trovato");
                    fUser.requestFocus();
                }
            }

            @Override
            public void fail(String message, boolean unauthorized) {
                findBtn.setText("Cerca server");
            }
        });
    }

    // ------------------------------------------------------------ libreria

    private int pending;
    private String loadError;
    private boolean loadUnauth;
    private List<Movie> rResume = new ArrayList<>(), rWatched = new ArrayList<>(), rLatest = new ArrayList<>(), rAll = new ArrayList<>();

    /** quiet=1: non mostra "Carico…" e mantiene il focus sul film corrente. */
    private void loadLibrary(final int quiet) {
        final String focusId = reopenId != null ? reopenId
                : quiet == 1 && act.getCurrentFocus() instanceof FilmViews.Poster
                ? ((FilmViews.Poster) act.getCurrentFocus()).movie.id : null;
        if (quiet == 0) state(LOADING, "Carico la libreria…");
        pending = 4;
        loadError = null;
        loadUnauth = false;
        final List<Movie> a = new ArrayList<>(), b = new ArrayList<>(), c = new ArrayList<>(), w = new ArrayList<>();
        jelly.resume(collect(a, focusId, quiet));
        jelly.latest(collect(b, focusId, quiet));
        jelly.all(collect(c, focusId, quiet));
        jelly.watched(collect(w, focusId, quiet));
        rWatched = w;
        rResume = a;
        rLatest = b;
        rAll = c;
    }

    private Jelly.Done<List<Movie>> collect(final List<Movie> into, final String focusId, final int quiet) {
        return new Jelly.Done<List<Movie>>() {
            @Override
            public void ok(List<Movie> v) {
                into.addAll(v);
                finish();
            }

            @Override
            public void fail(String message, boolean unauthorized) {
                loadError = message;
                loadUnauth |= unauthorized;
                finish();
            }

            private void finish() {
                if (--pending > 0) return;
                if (loadUnauth) {
                    if (quiet != 2 && jelly.hasSavedPassword()) {
                        // token scaduto: rientra da solo con la password salvata (un solo tentativo)
                        jelly.relogin(new Jelly.Done<Boolean>() {
                            @Override
                            public void ok(Boolean v) {
                                loadLibrary(2);
                            }

                            @Override
                            public void fail(String message, boolean unauthorized) {
                                askLogin("Sessione scaduta: accedi di nuovo");
                            }
                        });
                    } else {
                        askLogin("Sessione scaduta: accedi di nuovo");
                    }
                } else if (loadError != null && rAll.isEmpty()) {
                    reopenId = null;
                    if (quiet == 0) state(ERROR, loadError);
                } else {
                    build(focusId);
                }
            }
        };
    }

    /** Dall'errore di rete torna alla schermata di setup con i dati correnti: la sessione resta finche' non si accede altrove. */
    private void otherServer() {
        setServer(jelly.server);
        fUser.setText(jelly.userName);
        fPass.setText("");
        loginMsg.setText("");
        state(LOGIN, "");
        serverView().requestFocus();
    }

    private void askLogin(String msg) {
        reopenId = null;
        jelly.logout();
        loaded = false;
        setServer(jelly.server);
        fUser.setText(jelly.userName);
        fPass.setText("");
        state(LOGIN, "");
        loginMsg.setText(msg);
        fPass.requestFocus();
    }

    private void build(String focusId) {
        if (reopenId != null && detail != null) {
            // la scheda vecchia lascia il posto a quella ricostruita sotto, senza passare dalla libreria visibile
            removeView(detail);
            detail = null;
            detailFrom = null;
            showLibrary();
        }
        rowsBox.removeAllViews();
        if (rAll.isEmpty()) {
            reopenId = null;
            loaded = true;
            state(ERROR, "Nessun film nella libreria. Aggiungi film a Jellyfin e riprova.");
            return;
        }
        if (!rResume.isEmpty()) section("Continua a guardare", rResume);
        if (!rWatched.isEmpty()) section("Guardati di recente", rWatched);
        if (!rLatest.isEmpty()) section("Aggiunti di recente", rLatest);
        section("Tutti i film  ·  " + rAll.size(), rAll);
        Pill out = new Pill(act, "Esci da Jellyfin (" + jelly.userName + ")", u);
        out.setOnClickListener(v -> {
            rowsBox.removeAllViews();
            Img.clear();
            askLogin("");
        });
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, px(44));
        op.leftMargin = px(80);
        op.topMargin = px(8);
        op.bottomMargin = px(30);
        rowsBox.addView(out, op);
        loaded = true;
        state(LIBRARY, "");
        View target = null;
        if (focusId != null) target = findPoster(rowsBox, focusId);
        if (target == null) target = firstPoster();
        if (target != null) target.requestFocusFromTouch();
        if (reopenId != null) {
            View again = findPoster(rowsBox, reopenId);
            reopenId = null;
            if (again instanceof FilmViews.Poster) openDetail(((FilmViews.Poster) again).movie, (FilmViews.Poster) again);
        }
    }

    private static View findPoster(View v, String id) {
        if (v instanceof FilmViews.Poster && ((FilmViews.Poster) v).movie.id.equals(id)) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = findPoster(g.getChildAt(i), id);
                if (f != null) return f;
            }
        }
        return null;
    }

    private void section(String label, List<Movie> list) {
        final TextView t = A11y.heading(text(22, "sans-serif-medium", 0xE6FFFFFF));
        t.setText(label);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = px(80);
        tp.topMargin = px(6);
        rowsBox.addView(t, tp);
        LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(px(80), px(16), px(80), px(26));
        Row row = new Row(act);
        row.addView(content, new LayoutParams(-2, -2));
        int idx = 0;
        for (final Movie m : list) {
            final FilmViews.Poster p = new FilmViews.Poster(act, m, px(140), px(210), u);
            describePoster(p, m, idx++, list.size());
            String url = jelly.poster(m, 320);
            if (url != null) Img.load(url, b -> p.setImage(b));
            p.setOnClickListener(v -> openDetail(m, p));
            p.setOnFocusChangeListener((v, f) -> {
                if (f) {
                    showHero(m);
                    scroll.post(() -> scroll.smoothScrollTo(0, t.getTop() - px(6))); // il titolo della riga resta visibile
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(140), px(210));
            lp.rightMargin = px(18);
            content.addView(p, lp);
        }
        rowsBox.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    /** "Titolo, 2024, 29 secondi, in corso al 45 per cento, 1 di 5": tutto in una sola fermata, niente rumore. */
    private void describePoster(final FilmViews.Poster p, Movie m, final int index, final int total) {
        StringBuilder sb = new StringBuilder(m.name);
        if (m.year > 0 && !m.name.contains(String.valueOf(m.year))) sb.append(", ").append(act.getString(R.string.a11y_film_year, m.year));
        if (m.runtimeTicks > 0) sb.append(", ").append(A11y.spokenTime(act, m.runtimeTicks / 10000000L));
        if (m.progress() > 0.01) sb.append(", ").append(act.getString(R.string.a11y_film_in_progress, (int) Math.round(m.progress() * 100)));
        else if (m.played) sb.append(", ").append(act.getString(R.string.a11y_film_watched));
        String fmt = formats(m);
        if (!fmt.isEmpty()) sb.append(", ").append(fmt);
        sb.append(", ").append(act.getString(R.string.a11y_position, index + 1, total));
        p.setContentDescription(sb.toString());
        p.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCollectionItemInfo(android.view.accessibility.AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, index, 1, false));
            }
        });
    }

    /** "1080p, Dolby Vision, HEVC": il formato come testo unico per il lettore di schermo. */
    private String formats(Movie m) {
        List<String> l = new ArrayList<>();
        if (!m.resolution().isEmpty()) l.add(m.resolution());
        if (!m.hdr().isEmpty()) l.add(m.hdr());
        Movie.Stream v = m.video();
        if (v != null && !v.codec.isEmpty()) l.add(v.codec.toUpperCase(Locale.ROOT));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) sb.append(i > 0 ? ", " : "").append(l.get(i));
        return sb.toString();
    }

    private String duration(Movie m) {
        int min = m.minutes();
        if (min <= 0) return m.runtimeTicks > 0 ? (m.runtimeTicks / 10000000L) + " s" : "";
        return min >= 60 ? String.format(Locale.ITALIAN, "%d h %02d min", min / 60, min % 60) : min + " min";
    }

    private String meta(Movie m) {
        StringBuilder sb = new StringBuilder();
        if (m.year > 0) sb.append(m.year);
        String d = duration(m);
        if (!d.isEmpty()) sb.append(sb.length() > 0 ? "   ·   " : "").append(d);
        if (m.rating > 0) sb.append(sb.length() > 0 ? "   ·   " : "").append("★ ").append(String.format(Locale.ITALIAN, "%.1f", m.rating));
        if (!m.officialRating.isEmpty()) sb.append(sb.length() > 0 ? "   ·   " : "").append(m.officialRating);
        return sb.toString();
    }

    /** Come meta(), ma parlato: niente "·" e "★", la durata in parole. */
    private String metaSpoken(Movie m) {
        List<String> l = new ArrayList<>();
        if (m.year > 0) l.add(String.valueOf(m.year));
        if (m.runtimeTicks > 0) l.add(A11y.spokenTime(act, m.runtimeTicks / 10000000L));
        if (m.rating > 0) l.add(act.getString(R.string.a11y_rating, String.format(Locale.ITALIAN, "%.1f", m.rating)));
        if (!m.officialRating.isEmpty()) l.add(m.officialRating);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) sb.append(i > 0 ? ", " : "").append(l.get(i));
        return sb.toString();
    }

    private TextView badge(String s) {
        TextView t = text(16, "sans-serif-medium", 0xF2FFFFFF);
        t.setText(s);
        t.setPadding(px(10), px(3), px(10), px(3));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(6 * u);
        g.setStroke(Math.max(1, px(1.5f)), 0x99FFFFFF);
        t.setBackground(g);
        return t;
    }

    private void fillBadges(LinearLayout into, Movie m) {
        into.removeAllViews();
        into.setContentDescription(formats(m).isEmpty() ? null : act.getString(R.string.a11y_formats, formats(m)));
        List<String> l = new ArrayList<>();
        if (!m.resolution().isEmpty()) l.add(m.resolution());
        if (!m.hdr().isEmpty()) l.add(m.hdr());
        Movie.Stream v = m.video();
        if (v != null && !v.codec.isEmpty()) l.add(v.codec.toUpperCase(Locale.ROOT));
        List<Movie.Stream> au = m.of("Audio");
        if (!au.isEmpty()) {
            Movie.Stream a0 = au.get(0);
            String ch = a0.channels >= 6 ? "5.1" : a0.channels == 2 ? "Stereo" : "";
            l.add(a0.codec.toUpperCase(Locale.ROOT) + (ch.isEmpty() ? "" : " " + ch));
        }
        if (!m.of("Subtitle").isEmpty()) l.add("Sottotitoli");
        for (String s : l) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = px(8);
            into.addView(A11y.hide(badge(s)), lp);
        }
    }

    private void showHero(final Movie m) {
        heroFor = m.id;
        heroTitle.setText(m.name);
        heroMeta.setText(meta(m));
        heroOverview.setText(m.overview);
        fillBadges(badges, m);
        String url = jelly.hero(m, 960);
        if (url == null) {
            heroView.set(null);
            bg.set(homeBackdrop);
            return;
        }
        Img.load(url, b -> {
            if (!heroFor.equals(m.id) || b == null) return;
            heroView.set(b);
            try {
                bg.set(Blur.fromBitmap(b, 96, 54, 5));
            } catch (Throwable ignored) {
            }
        });
    }

    // ------------------------------------------------------------ scheda film

    private static String mmss(long ticks) {
        long s = ticks / 10000000L;
        return s >= 3600 ? String.format(Locale.ITALIAN, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                : String.format(Locale.ITALIAN, "%d:%02d", s / 60, s % 60);
    }

    private void openDetail(final Movie m, FilmViews.Poster from) {
        detailFrom = from;
        detailId = m.id;
        detail = new FrameLayout(act);
        detail.setClipChildren(false);
        detail.setAccessibilityPaneTitle(m.name);
        final MusicViews.Bg dbg = new MusicViews.Bg(act, null);
        detail.addView(A11y.hide(dbg), new LayoutParams(-1, -1));
        // stesso sfondo sfocato della hero
        String hurl = jelly.hero(m, 960);
        Bitmap hb = hurl == null ? null : Img.peek(hurl);
        if (hb != null) {
            try {
                dbg.set(Blur.fromBitmap(hb, 96, 54, 5));
            } catch (Throwable ignored) {
            }
        }
        final FilmViews.Hero hv = new FilmViews.Hero(act);
        hv.set(hb);
        detail.addView(A11y.hide(hv), at(520, 0, 760, 470));

        final FilmViews.Poster big = new FilmViews.Poster(act, m, px(240), px(360), u);
        big.setFocusable(false);
        String pu = jelly.poster(m, 480);
        if (pu != null) Img.load(pu, b -> big.setImage(b));
        detail.addView(A11y.hide(big), at(90, 128, 240, 360)); // la locandina grande e' decorativa, non un pulsante

        TextView t = A11y.heading(text(42, "sans-serif-light", 0xFFFFFFFF));
        t.setText(m.name);
        detail.addView(t, at(380, 122, 800, 54));
        TextView mt = text(20, "sans-serif", 0xE6FFFFFF);
        mt.setText(meta(m));
        mt.setContentDescription(metaSpoken(m));
        detail.addView(mt, at(380, 182, 800, 24));
        LinearLayout bd = new LinearLayout(act);
        bd.setOrientation(LinearLayout.HORIZONTAL);
        fillBadges(bd, m);
        detail.addView(bd, at(380, 214, 800, 26));
        TextView ov = text(20, "sans-serif", 0xF2FFFFFF);
        ov.setSingleLine(false);
        ov.setMaxLines(4);
        ov.setText(m.overview.isEmpty() ? "Nessuna trama disponibile." : m.overview);
        detail.addView(ov, at(380, 254, 680, 90));
        if (!m.genres.isEmpty()) {
            TextView gv = text(18, "sans-serif", 0xB3FFFFFF);
            StringBuilder gs = new StringBuilder();
            for (int i = 0; i < Math.min(4, m.genres.size()); i++) gs.append(i > 0 ? "  ·  " : "").append(m.genres.get(i));
            gv.setText(gs.toString());
            detail.addView(gv, at(380, 356, 800, 26));
        }
        // tracce: i selettori compaiono solo se c'e' davvero una scelta (piu' audio, o almeno un sottotitolo)
        final int[] sel = {-1, -1}; // indice del flusso audio e del sottotitolo (-1 = predefinito / nessuno)
        final List<Movie.Stream> au = m.of("Audio"), su = m.of("Subtitle");
        LinearLayout sels = new LinearLayout(act);
        sels.setOrientation(LinearLayout.HORIZONTAL);
        sels.setClipChildren(false);
        if (au.size() > 1) {
            final Pill ap = new Pill(act, "Audio: " + trackName(au.get(0)), u);
            ap.setOnClickListener(v -> {
                MenuDialog md = menu("Audio", m.name);
                for (final Movie.Stream st : au) {
                    md.add(trackName(st), () -> {
                        sel[0] = st.index;
                        ap.setText("Audio: " + trackName(st));
                    });
                }
                md.show();
            });
            addBtn(sels, ap);
        }
        if (!su.isEmpty()) {
            final Pill sp = new Pill(act, "Sottotitoli: nessuno", u);
            sp.setOnClickListener(v -> {
                MenuDialog md = menu("Sottotitoli", m.name);
                md.add("Nessuno", () -> {
                    sel[1] = -1;
                    sp.setText("Sottotitoli: nessuno");
                });
                for (final Movie.Stream st : su) {
                    md.add(trackName(st), () -> {
                        sel[1] = st.index;
                        sp.setText("Sottotitoli: " + trackName(st));
                    });
                }
                md.show();
            });
            addBtn(sels, sp);
        }
        if (sels.getChildCount() > 0) detail.addView(sels, at(380, 392, 820, 48));

        // pulsanti: Riproduci e' l'azione principale (piena); Indietro non serve, c'e' il tasto Back
        final boolean resume = m.positionTicks > 0; // il server azzera la posizione a fine film; un film gia' visto riguardato a meta' si riprende
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setClipChildren(false);
        Pill play = new Pill(act, resume ? "Riprendi da " + mmss(m.positionTicks) : "Riproduci", u);
        play.setPrimary(true);
        play.setOnClickListener(v -> play(m, resume ? m.positionTicks : 0, sel[0], sel[1]));
        addBtn(bar, play);
        if (resume) {
            Pill fromStart = new Pill(act, "Dall'inizio", u);
            fromStart.setOnClickListener(v -> play(m, 0, sel[0], sel[1]));
            addBtn(bar, fromStart);
        }
        final Pill seen = new Pill(act, m.played ? "Segna da vedere" : "Segna come visto", u);
        seen.setOnClickListener(v -> jelly.setPlayed(m.id, !m.played, new Jelly.Done<Boolean>() {
            @Override
            public void ok(Boolean played) {
                m.played = played;
                if (played) m.positionTicks = 0;
                seen.setText(played ? "Segna da vedere" : "Segna come visto");
                detailFrom.invalidate();
            }

            @Override
            public void fail(String message, boolean unauthorized) {
                seen.setText("Errore: " + message);
            }
        }));
        addBtn(bar, seen);
        detail.addView(bar, at(380, sels.getChildCount() > 0 ? 456 : 410, 820, 48));

        hideLibrary();
        addView(detail, new LayoutParams(-1, -1));
        play.requestFocus();
        detail.announceForAccessibility(act.getString(R.string.a11y_film_opened, m.name));
    }

    private void addBtn(LinearLayout bar, Pill p) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, px(48));
        lp.rightMargin = px(12);
        bar.addView(p, lp);
    }

    private final ArrayList<View> hiddenViews = new ArrayList<>();
    private final ArrayList<Integer> hiddenModes = new ArrayList<>();

    /** La scheda e' "modale" dentro la stessa finestra: la libreria sotto non deve restare navigabile. */
    private void hideLibrary() {
        hiddenViews.clear();
        hiddenModes.clear();
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c == detail) continue;
            hiddenViews.add(c);
            hiddenModes.add(c.getImportantForAccessibility());
            A11y.hideTree(c);
        }
    }

    private void showLibrary() {
        for (int i = 0; i < hiddenViews.size(); i++) hiddenViews.get(i).setImportantForAccessibility(hiddenModes.get(i));
        hiddenViews.clear();
        hiddenModes.clear();
    }

    private void closeDetail() {
        if (detail == null) return;
        removeView(detail);
        detail = null;
        showLibrary();
        if (detailFrom != null) detailFrom.requestFocus();
        detailFrom = null;
    }

    private static String trackName(Movie.Stream st) {
        if (!st.title.isEmpty()) return st.title;
        return st.lang.isEmpty() ? st.codec.toUpperCase(Locale.ROOT) : st.lang;
    }

    /** Pannello a vetro con la scheda sfocata dietro. */
    private MenuDialog menu(String title, String subtitle) {
        Bitmap back = null;
        try {
            back = Blur.snapshot(this, 6, 4);
        } catch (Throwable ignored) {
        }
        return new MenuDialog(act, title, subtitle, u, back);
    }

    private void play(Movie m, long startTicks, int audio, int sub) {
        Intent i = new Intent(act, PlayerActivity.class);
        i.putExtra("id", m.id);
        i.putExtra("title", m.name);
        i.putExtra("start", startTicks);
        i.putExtra("runtime", m.runtimeTicks);
        i.putExtra("audio", audio);
        i.putExtra("sub", sub);
        act.startActivity(i);
    }
}
