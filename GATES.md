# Gates: fix audit TalkBack del launcher

Scope: applicare tutti i fix dell'audit (3 Critical, 5 High, 5 Medium, 3 minori) e provarli sull'albero di accessibilita' reale (uiautomator) dell'emulatore Google TV.
Prova runtime: `tools/a11y_run.sh` pilota l'emulatore e salva gli XML in `a11y_out/`; `tools/a11y_assert.py <GATE>` li verifica.

- [x] G0: il progetto compila e produce l'APK
  CHECK: ./build.sh 2>&1 | tail -3
  EXPECT: build/home.apk
  EVIDENCE: WARNING: Restricted methods will be blocked in a future release unless native access is enabled | -rw-r--r--. 1 drift drift 90533 27 ott 02.05 build/home.apk

## Critical

- [x] C1: con la scheda film aperta il fondo non e' esposto e la locandina grande non e' un pulsante fantasma
  CHECK: python3 -I tools/a11y_assert.py C1
  EXPECT: PASS C1
  EVIDENCE: PASS C1

- [ ] C2: con un servizio di accessibilita' attivo i controlli del player non si nascondono da soli
  CHECK: python3 -I tools/a11y_assert.py C2
  EXPECT: PASS C2
  EVIDENCE: pending

- [ ] C3: la barra di avanzamento ha nome, valore parlato, RangeInfo e azioni di scorrimento
  CHECK: python3 -I tools/a11y_assert.py C3
  EXPECT: PASS C3
  EVIDENCE: pending

## High

- [x] H1a: la tab corrente espone `selected` (Home/Film/Musica)
  CHECK: python3 -I tools/a11y_assert.py H1a
  EXPECT: PASS H1a
  EVIDENCE: PASS H1a

- [x] H1b: nell'ordine di lettura la barra superiore precede il contenuto in Film e Musica
  CHECK: python3 -I tools/a11y_assert.py H1b
  EXPECT: PASS H1b
  EVIDENCE: PASS H1b

- [x] H2: i cambi di stato (stato/errori login, seek, caricamento, brano) hanno regioni live o annunci
  CHECK: python3 -I tools/a11y_assert.py H2
  EXPECT: PASS H2
  EVIDENCE: PASS H2

- [x] H3: il codice Quick Connect e' parlato cifra per cifra e annunciato
  CHECK: python3 -I tools/a11y_assert.py H3
  EXPECT: PASS H3
  EVIDENCE: PASS H3

- [x] H4: nessuna fermata di focus senza etichetta in Home, Film, scheda, player, Musica, menu
  CHECK: python3 -I tools/a11y_assert.py H4
  EXPECT: PASS H4
  EVIDENCE: PASS H4

- [x] H5: i testi seguono la scala dei caratteri di sistema (1,3 ingrandisce, niente crash)
  CHECK: python3 -I tools/a11y_assert.py H5
  EXPECT: PASS H5
  EVIDENCE: PASS H5

## Medium

- [x] M1a: le locandine dicono titolo, anno, durata, avanzamento/visto e posizione "N di M"
  CHECK: python3 -I tools/a11y_assert.py M1a
  EXPECT: PASS M1a
  EVIDENCE: PASS M1a

- [x] M1b: i badge sono un solo gruppo e l'hero non ripete il titolo della card a fuoco
  CHECK: python3 -I tools/a11y_assert.py M1b
  EXPECT: PASS M1b
  EVIDENCE: PASS M1b

- [x] M2: i pannelli a vetro hanno titolo di finestra ("Utente", "Immagine profilo", "Sottotitoli")
  CHECK: python3 -I tools/a11y_assert.py M2
  EXPECT: PASS M2
  EVIDENCE: PASS M2

- [x] M3: lo stato "attivo / collegato / selezionato" non dipende dal glifo (descrizione parlata senza "●")
  CHECK: python3 -I tools/a11y_assert.py M3
  EXPECT: PASS M3
  EVIDENCE: PASS M3

- [ ] M4: titoli e sezioni sono marcati come intestazioni
  CHECK: python3 -I tools/a11y_assert.py M4
  EXPECT: PASS M4
  EVIDENCE: pending

- [x] M5: gli avatar hanno nomi veri (12 nomi distinti, niente "Avatar N")
  CHECK: python3 -I tools/a11y_assert.py M5
  EXPECT: PASS M5
  EVIDENCE: PASS M5

## Minori

- [x] m1: le descrizioni e gli annunci di accessibilita' vengono da risorse (`strings.xml`), non da letterali
  CHECK: python3 -I tools/a11y_assert.py m1
  EXPECT: PASS m1
  EVIDENCE: PASS m1

- [x] m2: la copertina del brano ha una descrizione ("Copertina di ...")
  CHECK: python3 -I tools/a11y_assert.py m2
  EXPECT: PASS m2
  EVIDENCE: PASS m2

- [x] m3: orologio e data hanno un contesto parlato ("Ore ...", "Data ...")
  CHECK: python3 -I tools/a11y_assert.py m3
  EXPECT: PASS m3
  EVIDENCE: PASS m3

## Regressioni

- [x] R1: nessun crash dell'app durante tutta la corsa di verifica
  CHECK: python3 -I tools/a11y_assert.py R1
  EXPECT: PASS R1
  EVIDENCE: PASS R1

- [x] R2: le funzioni di prima funzionano ancora (Home, Film, scheda, player parte, menu profilo)
  CHECK: python3 -I tools/a11y_assert.py R2
  EXPECT: PASS R2
  EVIDENCE: PASS R2
