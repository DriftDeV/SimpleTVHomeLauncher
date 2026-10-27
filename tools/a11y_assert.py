"""Verifica un gate dell'audit TalkBack sui dump di a11y_out/. Uso: python3 -I tools/a11y_assert.py <GATE>. Stampa PASS <GATE> o FAIL <GATE>: motivo."""
import os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import a11y_lib as L

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
SRC = os.path.join(ROOT, 'src', 'dev', 'drift', 'homelauncher')
MAIN = ['home', 'login', 'qc', 'lib', 'detail', 'player', 'lib_after', 'profile', 'avatar', 'sources', 'music', 'film_tab', 'home_tab']
fails = []


def need(scene):
    s = L.load(scene)
    if s is None or not any(' END' in l for l in s['lines']) or not s['nodes']:
        fails.append(f'dump mancante o vuoto: {scene}')
        return {'lines': [], 'nodes': [], 'titles': []}
    return s


def ok(cond, msg):
    if not cond:
        fails.append(msg)


def find(s, rx, key=None):
    r = re.compile(rx)
    return [n for n in s['nodes'] if r.search(key(n) if key else L.label(n))]


def src(name):
    return open(os.path.join(SRC, name), encoding='utf-8').read()


def labeled(nodes, i):
    """Un nodo focusabile e' etichettato se ha testo/descrizione o un discendente che li ha."""
    n = nodes[i]
    if L.label(n):
        return True
    j = i + 1
    while j < len(nodes) and nodes[j]['d'] > n['d']:
        if L.label(nodes[j]):
            return True
        j += 1
    return False


def C1():
    d = need('detail')
    ok(not find(d, r' di 5$'), 'la libreria (locandine "N di 5") e\' ancora esposta sotto la scheda')
    ok(not find(d, r'^Esci da Jellyfin'), 'il pulsante Esci da Jellyfin e\' esposto sotto la scheda')
    big = [n for n in d['nodes'] if n.get('foc') == 'true' and n['bounds'][2] - n['bounds'][0] == 240 and n['bounds'][3] - n['bounds'][1] == 360]
    ok(not big, 'la locandina grande e\' un nodo focusabile')
    ok(any(n.get('pane') for n in d['nodes']), 'la scheda non ha titolo di pannello')
    ok(find(d, r'^Riproduci$'), 'manca Riproduci nella scheda')
    a = need('lib_after')
    ok(len(find(a, r' di 5$')) == 5, 'chiusa la scheda la libreria non torna esposta (locandine)')
    ok(find(a, r'^Esci da Jellyfin'), 'chiusa la scheda manca Esci da Jellyfin')


def C2():
    ok('PlayerActivity' in open(os.path.join(L.OUT, 'top_player.txt')).read(), 'il player non e\' in primo piano')
    p = need('player')
    ok(find(p, r'^Pausa$|^Riproduci$'), 'dopo 14 s i controlli del player non sono piu\' esposti')
    ok([n for n in p['nodes'] if 'SeekBar' in (n.get('cls') or '') and n.get('vis') == 'true'], 'barra di avanzamento non visibile dopo 14 s')


def seekbars():
    out = []
    for sc in ('player', 'music'):
        out += [(sc, n) for n in need(sc)['nodes'] if 'SeekBar' in (n.get('cls') or '')]
    return out


def C3():
    sb = seekbars()
    ok({s for s, _ in sb} == {'player', 'music'}, 'manca la SeekBar in player o in Musica')
    for sc, n in sb:
        ok((n.get('desc') or '').startswith('Avanzamento'), f'{sc}: nome mancante')
        ok(re.search(r'\d.*(second|minut|or)', n.get('desc') or ''), f'{sc}: valore non parlato ("{n.get("desc")}")')
        ok((n.get('range') or '-').startswith('2:0.0:100.0:'), f'{sc}: RangeInfo percentuale mancante ({n.get("range")})')
        ok(4096 in n['acts'] and 8192 in n['acts'], f'{sc}: mancano le azioni scroll avanti/indietro ({sorted(n["acts"])})')
        ok(n.get('foc') == 'true', f'{sc}: non focusabile')


def tabs(scene):
    s = need(scene)
    return {n['text']: n for n in s['nodes'] if n.get('text') in ('Home', 'Film', 'Musica') and n.get('foc') == 'true'}


def H1a():
    for scene, cur in (('home', 'Home'), ('film_tab', 'Film'), ('music', 'Musica'), ('home_tab', 'Home')):
        t = tabs(scene)
        ok(len(t) == 3, f'{scene}: tab non trovate')
        for name, n in t.items():
            ok((n.get('sel') == 'true') == (name == cur), f'{scene}: tab {name} sel={n.get("sel")} (corrente: {cur})')


def H1b():
    for scene, content in (('lib', r'^Tutti i film'), ('music', r'^Blinding Lights$'), ('film_tab', r'^Tutti i film'), ('qc', r'^Collega Jellyfin')):
        s = need(scene)
        names = [L.label(n) for n in s['nodes']]
        ti = next((i for i, n in enumerate(s['nodes']) if n.get('text') == 'Home' and n.get('foc') == 'true'), None)
        ci = next((i for i, x in enumerate(names) if re.search(content, x)), None)
        ok(ti is not None and ci is not None and ti < ci, f'{scene}: la barra non precede il contenuto (barra {ti}, contenuto {ci})')


def H2():
    lg = need('login')
    ok([n for n in lg['nodes'] if n.get('live') not in (None, '0') and L.label(n)], 'login: nessuna regione live con testo (stato/errori)')
    qc = need('qc')
    ok([n for n in qc['nodes'] if n.get('live') not in (None, '0') and re.fullmatch(r'\d{3} \d{3}', n.get('text') or '')], 'qc: il codice non e\' in una regione live')
    ps = need('player_seek')
    ok([n for n in ps['nodes'] if n.get('live') not in (None, '0') and L.label(n)], 'player: dopo un seek nessuna nota live con testo')
    for f, pat in (('MusicPanel.java', 'announceForAccessibility'), ('PlayerActivity.java', 'announceForAccessibility'), ('PlayerActivity.java', 'A11y.live')):
        ok(pat in src(f), f'{f}: manca {pat}')


def H3():
    qc = need('qc')
    n = [n for n in qc['nodes'] if re.fullmatch(r'\d{3} \d{3}', n.get('text') or '')]
    ok(n, 'qc: codice non mostrato')
    for x in n:
        ok(re.fullmatch(r'Codice (\d ){5}\d', x.get('desc') or ''), f'qc: descrizione non cifra per cifra ({x.get("desc")})')
        ok(x.get('live') not in (None, '0'), 'qc: il codice non e\' annunciato (live)')
    ok('announceForAccessibility(act.getString(R.string.a11y_code_announce' in src('FilmPanel.java'), 'FilmPanel: manca l\'annuncio del codice')


def H4():
    for sc in MAIN:
        s = need(sc)
        for i, n in enumerate(s['nodes']):
            if n.get('foc') == 'true' and not labeled(s['nodes'], i):
                ok(False, f'{sc}: fermata di focus senza etichetta ({n.get("cls")} {n["bounds"]})')


def H5():
    pairs = [('home', 'home130', r'^Altre app$'), ('home', 'home130', r'^Home$'), ('home', 'home130', r'^Martedì|^\S+ \d+ \S+$'),
             ('lib', 'lib130', r'^Tutti i film'), ('music', 'music130', r'^Blinding Lights$')]
    for a, b, rx in pairs:
        na = find(need(a), rx, key=lambda n: n.get('text') or '')
        nb = find(need(b), rx, key=lambda n: n.get('text') or '')
        if not na or not nb:
            ok(False, f'{a}/{b}: nodo {rx} non trovato')
            continue
        ra = float(na[0].get('tsz') or 0)
        rb = float(nb[0].get('tsz') or 0)
        ok(ra > 0 and 1.25 <= rb / ra <= 1.35, f'{b}: {rx} tsz {ra} -> {rb} (atteso x1.3)')
    for sc in ('home130', 'lib130', 'detail130', 'music130', 'profile130'):
        for n in need(sc)['nodes']:
            l, t, r, b = n['bounds']
            if L.label(n) and (r > 1290 or b > 730):
                ok(False, f'{sc}: nodo fuori schermo {n["bounds"]} {L.label(n)[:30]}')


def M1a():
    lib = need('lib')
    ps = sorted(find(lib, r' di 5$'), key=lambda n: n['bounds'][0])
    ok(len(ps) == 5, f'lib: {len(ps)} locandine invece di 5')
    for i, n in enumerate(ps, 1):
        d = n.get('desc') or ''
        ok(d.endswith(f'{i} di 5'), f'locandina {i}: manca "{i} di 5"')
        ok(re.search(r'secondi|minut|ora|ore', d), f'locandina {i}: manca la durata')
        ok(re.search(r'\b(19|20)\d\d\b', d), f'locandina {i}: manca l\'anno')
        ok(n.get('coll') not in (None, '-'), f'locandina {i}: manca CollectionItemInfo')
    ok(any('visto' in (n.get('desc') or '') for n in ps), 'nessuna locandina dice "visto" (almeno 3 dei film di prova lo sono)')


def M1b():
    for sc in ('lib', 'detail'):
        s = need(sc)
        badges = [n for n in s['nodes'] if (n.get('text') or '') in ('1080p', '4K', 'HDR10', 'Dolby Vision', 'HEVC', 'H264', 'SDR')]
        ok(not badges, f'{sc}: badge ancora separati ({[b["text"] for b in badges]})')
    d = need('detail')
    ok(len(find(d, r'^Formato: ')) == 1, 'scheda: il gruppo "Formato: ..." non e\' unico')
    lib = need('lib')
    names = {re.split(r', ', n['desc'])[0] for n in find(lib, r' di 5$')}
    dup = [n for n in lib['nodes'] if (n.get('text') or '') in names]
    ok(not dup, f'lib: l\'hero ripete il titolo ({[n["text"] for n in dup]})')
    ok(not find(need('home'), r'^YouTube$', key=lambda n: n.get('text') or ''), 'home: l\'hero ripete il nome della card')


def M2():
    ok('Utente' in need('profile')['titles'], 'finestra profilo senza titolo "Utente"')
    ok('Immagine profilo' in need('avatar')['titles'], 'finestra avatar senza titolo')
    ok('Sorgenti' in need('sources')['titles'], 'finestra ingressi senza titolo')
    d = need('detail')
    ok(any(n.get('pane') for n in d['nodes']), 'scheda senza titolo di pannello')
    p = need('player')
    ok(any(t and 'Test' in t for t in p['titles']), f'player senza titolo di finestra ({p["titles"]})')
    ok('setTitle' in src('MenuDialog.java') and 'setAccessibilityPaneTitle' in src('MenuDialog.java'), 'MenuDialog: manca setTitle/paneTitle (copre anche "Sottotitoli")')


def M3():
    for sc in MAIN:
        for n in need(sc)['nodes']:
            if '●' in (n.get('desc') or ''):
                ok(False, f'{sc}: glifo nella descrizione parlata ({n["desc"][:40]})')
            if '●' in (n.get('text') or '') and not n.get('desc'):
                ok(False, f'{sc}: stato solo con glifo, senza descrizione ({n["text"][:40]})')
    ok(find(need('profile'), r', attivo$'), 'profilo: la voce attiva non dice "attivo"')


def M4():
    want = [('home', r'^Altre app$'), ('lib', r'^Tutti i film'), ('detail', r'^Test '), ('music', r'^Blinding Lights$'),
            ('login', r'^Collega Jellyfin$'), ('profile', r'^Utente$'), ('avatar', r'^Immagine profilo$'), ('sources', r'^Sorgenti$'), ('player', r'^Test ')]
    for sc, rx in want:
        n = find(need(sc), rx, key=lambda x: x.get('text') or '')
        ok(n and n[0].get('head') == 'true', f'{sc}: {rx} non e\' un\'intestazione')


def M5():
    a = [n for n in need('avatar')['nodes'] if (n.get('desc') or '').startswith('Avatar ')]
    names = [n['desc'] for n in a]
    ok(len(names) == 12, f'avatar: {len(names)} voci invece di 12')
    ok(len(set(names)) == len(names), 'avatar: nomi duplicati')
    ok(not [x for x in names if re.search(r'\d', x)], f'avatar: nomi numerici {names}')


def m1():
    bad = []
    for f in os.listdir(SRC):
        if f.endswith('.java'):
            t = src(f)
            for pat in (r'setContentDescription\("', r'announceForAccessibility\("', r'setAccessibilityPaneTitle\("', r'setStateDescription\("'):
                if re.search(pat, t):
                    bad.append(f'{f}: {pat}')
    ok(not bad, f'letterali residui: {bad}')
    st = open(os.path.join(ROOT, 'res', 'values', 'strings.xml'), encoding='utf-8').read()
    ok(len(re.findall(r'name="a11y_', st)) >= 25, 'strings.xml: poche risorse a11y_')


def m2():
    ok(find(need('music'), r'^Copertina di .+'), 'Musica: la copertina non ha "Copertina di ..."')


def m3():
    h = need('home')
    ok(find(h, r'^Ore \d\d:\d\d$'), 'home: orologio senza "Ore ..."')
    ok(find(h, r'^Data: '), 'home: data senza "Data: ..."')


def R1():
    t = open(os.path.join(L.OUT, 'crash.txt'), errors='replace').read()
    ok('FATAL' not in t and 'homelauncher' not in t, 'crash rilevato: ' + t[:200].replace('\n', ' | '))


def R2():
    ok(len([n for n in need('home')['nodes'] if n.get('foc') == 'true' and n.get('cls', '').endswith('View') and L.label(n) and n['bounds'][1] > 250]) >= 3, 'home: meno di 3 card')
    ok(len(find(need('lib'), r' di 5$')) == 5, 'Film: libreria non caricata')
    ok(find(need('detail'), r'^Riproduci$'), 'scheda film non si apre')
    ok('PlayerActivity' in open(os.path.join(L.OUT, 'top_player.txt')).read(), 'il player non parte')
    ok('HomeActivity' in open(os.path.join(L.OUT, 'top_after_player.txt')).read(), 'dal player non si torna alla home')
    ok(len([n for n in need('profile')['nodes'] if n.get('foc') == 'true']) >= 5, 'menu profilo incompleto')
    ok(find(need('music'), r'^Blinding Lights$', key=lambda n: n.get('text') or ''), 'Musica non mostra il brano')
    ok(len(find(need('lib_after'), r' di 5$')) == 5, 'dopo la scheda la libreria non torna')


if __name__ == '__main__':
    g = sys.argv[1]
    globals()[g]()
    if fails:
        print(f'FAIL {g}: ' + ' || '.join(fails[:6]) + (f' (+{len(fails) - 6})' if len(fails) > 6 else ''))
        sys.exit(1)
    print(f'PASS {g}')
