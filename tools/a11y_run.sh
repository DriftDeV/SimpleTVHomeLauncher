#!/usr/bin/env bash
# Pilota l'emulatore Google TV e salva in a11y_out/ l'albero di accessibilita' di ogni schermata (letto da a11y_assert.py).
# Richiede: emulatore avviato, dev.drift.a11ytest abilitato come servizio, Jellyfin su 127.0.0.1:8096 (utente user/user).
set -u
cd "$(dirname "$0")/.."
SER="${SERIAL:-emulator-5556}"
A="$HOME/Android/Sdk/platform-tools/adb -s $SER"
OUT=a11y_out
PKG=dev.drift.homelauncher
rm -rf "$OUT"; mkdir -p "$OUT"
trap '$A shell settings put system font_scale 1.0 >/dev/null 2>&1' EXIT

dump() { # dump <scena>
  $A logcat -c
  $A shell am broadcast -a dev.drift.a11ytest.DUMP --es tag "$1" >/dev/null
  sleep 3
  $A logcat -d -s A11YDUMP | sed 's/^.*A11YDUMP *: *//' | grep -E "^$1 " > "$OUT/$1.txt"
  if ! grep -q " END" "$OUT/$1.txt"; then sleep 3; $A logcat -d -s A11YDUMP | sed 's/^.*A11YDUMP *: *//' | grep -E "^$1 " > "$OUT/$1.txt"; fi
}
tapt() { # tapt <scena> <regex>: tocca il nodo con quell'etichetta
  local xy; xy=$(python3 -I tools/a11y_lib.py tap "$1" "$2") || { echo "tapt: '$2' non trovato in $1" >&2; return 1; }
  $A shell input tap $xy; sleep 2
}
key() { for x in "$@"; do $A shell input keyevent "$x"; sleep 0.5; done; }
top() { $A shell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/.*u0 //; s/ t[0-9]*}.*//'; }
tab() { # tab <x> <scena> <regex>: tocca la tab e riprova finche' la scena contiene la regex (il primo tocco dopo l'avvio puo' perdersi)
  local i
  for i in 1 2 3 4; do
    $A shell input tap "$1" 62; sleep 3; dump "$2"
    grep -qE "$3" "$OUT/$2.txt" && return 0
  done
  echo "tab: '$3' mai apparso in $2" >&2; return 1
}
compat() { # compat <scena>: chiude l'avviso di Android 16 "app per una versione piu' vecchia" (targetSdk 25; non esiste su Android 9)
  dump "$1"
  if grep -q "Check for update" "$OUT/$1.txt"; then
    tapt "$1" '^OK$'; sleep 1
    dump "$1"
    grep -q "Check for update" "$OUT/$1.txt" && { key BACK; sleep 1; }
  fi
}
shot() { $A exec-out screencap -p > "$OUT/$1.png"; }

echo "== setup"
$A install -r build/home.apk >/dev/null
$A install -r test/a11y/build/a11ytest.apk >/dev/null
$A install -r test/build/test.apk >/dev/null
$A shell settings put secure enabled_accessibility_services dev.drift.a11ytest/dev.drift.a11ytest.Svc
$A shell settings put secure accessibility_enabled 1
$A shell settings put system font_scale 1.0
$A shell am force-stop $PKG; $A shell pm clear $PKG >/dev/null
$A shell pm grant $PKG android.permission.GET_ACCOUNTS
$A shell cmd notification allow_listener $PKG/$PKG.MediaListener >/dev/null
$A logcat -b all -c
$A shell input keyevent KEYCODE_HOME; sleep 1
$A shell am start -n $PKG/.HomeActivity >/dev/null; sleep 5

echo "== home"
sleep 6; compat home_chk
dump home; shot home

echo "== login (primo avvio) e Quick Connect"
tab 236 login "Collega Jellyfin"
tapt login '^Server' && $A shell input text 'http://10.0.2.2:8096' && sleep 1 && key BACK
tapt login '^Con codice$'; sleep 4
dump qc
CODE=$(python3 -I tools/a11y_lib.py code qc || true)
H='Authorization: MediaBrowser Client="a11y", Device="pc", DeviceId="a11y1", Version="1"'
TOK=$(curl -s -m 10 -H "$H" -H 'Content-Type: application/json' -d '{"Username":"user","Pw":"user"}' http://127.0.0.1:8096/Users/AuthenticateByName | python3 -I -c 'import sys,json;print(json.load(sys.stdin)["AccessToken"])')
echo "codice trovato: ${#CODE} cifre"
curl -s -m 10 -o /dev/null -w "authorize http %{http_code}\n" -X POST -H "Authorization: MediaBrowser Token=\"$TOK\"" "http://127.0.0.1:8096/QuickConnect/Authorize?code=$CODE"
sleep 9

echo "== libreria Film"
dump lib; shot lib

echo "== scheda film"
tapt lib '1 di 5$'; sleep 2
dump detail; shot detail

echo "== player"
tapt detail '^Riproduci$'; sleep 4
# Android 16 mostra una volta l'avviso "app per una versione piu' vecchia" (targetSdk 25): non c'e' su Android 9
compat player_chk
sleep 12
top > "$OUT/top_player.txt"
dump player
if grep -q "Check for update" "$OUT/player.txt"; then tapt player '^OK$'; sleep 2; dump player; fi
shot player
# il seek con la tastiera deve produrre una nota parlata (regione live)
# il servizio osserva per 6 s e scrive il dump alla prima regione live con testo (la nota "+10 s" resta visibile solo 900 ms)
$A logcat -c
$A shell am broadcast -a dev.drift.a11ytest.DUMP --es tag player_seek --ez watch true >/dev/null
$A shell input keyevent DPAD_RIGHT
sleep 5
$A logcat -d -s A11YDUMP | sed 's/^.*A11YDUMP *: *//' | grep -E "^player_seek " > "$OUT/player_seek.txt"

echo "== ritorno dal player e dalla scheda"
key BACK; sleep 2
[ "$(top)" = "$PKG/.PlayerActivity" ] && { key BACK; sleep 2; }
top > "$OUT/top_after_player.txt"
dump lib_after
if grep -q "Riproduci" "$OUT/lib_after.txt"; then key BACK; sleep 2; dump lib_after; fi

echo "== menu profilo / avatar / ingressi"
$A shell input tap 1110 52; sleep 2
dump profile
tapt profile '^Immagine profilo$'; sleep 2
dump avatar; shot avatar
key BACK; key BACK; sleep 1
$A shell input tap 700 52; sleep 2
dump sources
key BACK; sleep 1

echo "== Musica con sessione media"
$A shell am start -n dev.drift.mediatest/.Main >/dev/null 2>&1; sleep 3
$A shell input keyevent KEYCODE_HOME; sleep 2
tab 347 music "Blinding Lights"; shot music
tab 236 film_tab "Tutti i film"
tab 130 home_tab "YouTube"

echo "== scala caratteri 1,3"
$A shell settings put system font_scale 1.3
$A shell am force-stop $PKG; sleep 1
$A shell am start -n $PKG/.HomeActivity >/dev/null; sleep 5
dump home130; shot home130
tab 236 lib130 "Tutti i film"; shot lib130
tapt lib130 '1 di 5$'; sleep 2
dump detail130; shot detail130
key BACK; sleep 1
tab 347 music130 "Blinding Lights"; shot music130
$A shell input tap 1110 52; sleep 2
dump profile130; shot profile130
key BACK
$A shell settings put system font_scale 1.0

echo "== crash"
{ $A logcat -b crash -d; $A logcat -d AndroidRuntime:E '*:S' | grep -A8 "dev.drift.homelauncher"; } > "$OUT/crash.txt" 2>&1
echo "righe crash: $(wc -l < "$OUT/crash.txt")"
echo "fine: $(ls $OUT | wc -l) file in $OUT"
