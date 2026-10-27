"""Lettura dei dump prodotti da a11y_run.sh (servizio dev.drift.a11ytest: l'albero cosi' come lo vede un lettore di schermo)."""
import re, sys, os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'a11y_out')
FIELD = re.compile(r" (cls|text|desc|pane|foc|click|sel|chk|head|live|vis|a11yfoc|range|coll|state|tsz|acts|b)=")


def parse_line(line):
    """Una riga N -> dict di campi; i valori testuali vanno tra apici e possono contenere spazi."""
    d = {}
    m = re.match(r"^\S+ N d=(\d+)(.*)$", line)
    if not m:
        return None
    d['d'] = int(m.group(1))
    rest = m.group(2)
    parts = FIELD.split(rest)
    for i in range(1, len(parts) - 1, 2):
        v = parts[i + 1].strip() if parts[i] != 'text' and parts[i] != 'desc' and parts[i] != 'pane' and parts[i] != 'state' else parts[i + 1]
        if parts[i] in ('text', 'desc', 'pane', 'state'):
            v = None if v == 'null' else v[1:-1]
        d[parts[i]] = v
    d['acts'] = set(int(x) for x in (d.get('acts') or '').split(',') if x)
    b = [int(x) for x in (d.get('b') or '0 0 0 0').split()]
    d['bounds'] = b
    return d


def window_titles(lines):
    return [re.search(r"title='([^']*)'", l).group(1) if re.search(r"title='([^']*)'", l) else None for l in lines if ' W type=' in l]


def load(scene):
    p = os.path.join(OUT, scene + '.txt')
    if not os.path.exists(p):
        return None
    lines = [l.rstrip('\n') for l in open(p, encoding='utf-8', errors='replace')]
    nodes = [n for n in (parse_line(l) for l in lines) if n]
    return {'lines': lines, 'nodes': nodes, 'titles': window_titles(lines)}


def label(n):
    return (n.get('desc') or n.get('text') or '')


def center(n):
    l, t, r, b = n['bounds']
    return (l + r) // 2, (t + b) // 2


if __name__ == '__main__':
    # uso: a11y_lib.py tap <scene> <regex> -> "x y" del primo nodo la cui etichetta corrisponde
    if sys.argv[1] == 'tap':
        s = load(sys.argv[2])
        rx = re.compile(sys.argv[3])
        for n in (s['nodes'] if s else []):
            if rx.search(label(n)) and n.get('vis', 'true') == 'true':
                print(*center(n))
                break
        else:
            sys.exit(1)
    elif sys.argv[1] == 'code':
        s = load(sys.argv[2])
        for n in s['nodes']:
            m = re.fullmatch(r"(\d{3}) (\d{3})", n.get('text') or '')
            if m:
                print(m.group(1) + m.group(2))
                break
        else:
            sys.exit(1)
