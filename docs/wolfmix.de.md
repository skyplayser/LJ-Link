# Wolfmix-Anbindung – Anforderungen und Plan

Stand: 2026-11-04. **Stufe 1 (F1–F7) ist gebaut** und gegen einen Test-Empfänger geprüft, siehe ADR-020 in `docs/decisions.md` und Kapitel 8a in `docs/lichtpult.de.md`. Offen: Test am echten Wolfmix, Stufe 2 und 3. Recherche aus Handbüchern, Händler-Datenblättern und Foren. **Nichts davon ist am echten Gerät getestet.**

## 1. Ausgangslage

- Wolfmix W1 (MK1–MK3) ist ein eigenständiges DMX-Pult mit Audioanalyse.
  - Anschlüsse: USB-C (Computer und Strom), USB-A (MIDI, Stick, WLINK) und DMX.
  - **Kein Ethernet, kein WLAN.**
- Takt bekommt der Wolfmix auf vier Wegen:
  1. **OS2L** über die Hersteller-App **WTOOLS** am Computer, Port 5000. So spricht VirtualDJ mit ihm.
  2. **Ableton Link** über WTOOLS.
  3. **MIDI Clock** über USB-A, das heißt nur von einem MIDI-Gerät.
  4. Tap-Taste, Mikrofon oder Line-Eingang direkt am Gerät.
- Laut Anleitung muss im FX-Rack der erste Drehregler gedrückt werden. Erst dann laufen Effekte im BPM-Sync.
- LJ Link kann heute senden an:
  - grandMA2 über Telnet (`Ma2Telnet`),
  - grandMA3 über OSC `/cmd`,
  - OSC-Zahl, zum Beispiel für Daslight.

  Alles läuft über `LinkService`: Typen `off`, `ma2`, `ma`, `osc`; `tick()` sendet laufend, die Leitquelle wird mit `setSource` gewählt.

Aufbau:

```
CDJs / Denon / Serato ─► LJ Link ─(gleicher Rechner oder LAN/WLAN)─► WTOOLS ─USB─► Wolfmix ─DMX─► Licht
```

## 2. Ist die Schnittstelle realistisch?

| Weg | Aufwand | Nutzen | Urteil |
|---|---|---|---|
| **OS2L senden** (LJ Link verhält sich wie VirtualDJ) | klein (1–2 Tage) | BPM **und jeder Beat** mit Beatnummer, also phasengenau | **Ja, damit anfangen** |
| Ableton Link senden (LJ Link wird ein Link-Teilnehmer) | groß (Link-Protokoll mit Zeitmessung und Timeline) | Tempo und Phase, geht auch mit anderen Link-Apps | später, optional |
| MIDI Clock | mittel; braucht ein USB-MIDI-Interface mit Host-Anschluss | Tempo ohne WTOOLS | nein, Hardware-Gefrickel |

**Fazit:** Realistisch, mit einer festen Einschränkung: Ein Computer mit WTOOLS muss per USB am Wolfmix hängen. Ohne Computer geht es nicht.

## 3. OS2L in Kürze (aus Sekundärquellen, vor dem Bau gegen os2l.org prüfen)

- TCP-Verbindung. Pro Nachricht ein JSON-Objekt.
- Discovery: Die DMX-Software (hier WTOOLS) meldet sich per DNS-SD/Bonjour als `_os2l._tcp`. Die Audio-Software (hier LJ Link) verbindet sich als Client. VirtualDJ kann die Adresse auch fest eintragen: `127.0.0.1:5000`.
- Beat bei jedem Schlag:
  ```json
  {"evt":"beat","change":false,"pos":42,"bpm":128.0,"strength":0.8}
  ```
  - `change` ist `true`, wenn sich BPM oder Phase sprunghaft geändert haben, etwa beim Track-Wechsel.
  - `strength` ist optional.
- Taste:
  ```json
  {"evt":"btn","name":"blackout","state":"on"}
  ```
- Befehl: `{"evt":"cmd","id":1,"param":50}`. Das Format ist unsicher und wird geprüft.
- VirtualDJ schickt zusätzliche, nicht dokumentierte Felder und Ereignisse (`subscribe`, Beat-Offset). Wahrscheinlich nicht nötig.

## 4. Mögliche Funktionen

### Stufe 1 – Kern (morgen)
- **F1** Neuer Pult-Typ **„Wolfmix / OS2L (WTOOLS, VirtualDJ-kompatibel)“** in den Einstellungen.
  - Host mit Vorgabe `127.0.0.1`, Port mit Vorgabe `5000`.
- **F2** Beat-Nachrichten im Takt der gewählten Leitquelle.
  - Die Quelle wählt dieselbe Logik wie bei MA: Master-Deck oder fest gewähltes Deck.
- **F3** Beatnummer `pos` fortlaufend.
  - Bei Track-Wechsel, Sprung (Hot Cue, Loop) oder Wechsel des Leit-Decks: `change:true`.
- **F4** Status in der Oberfläche:
  - verbunden / nicht erreichbar / „WTOOLS läuft nicht?“,
  - der zuletzt gesendete BPM-Wert, wie heute bei `markSending`.
- **F5** Automatisch neu verbinden mit Backoff (5 s), genau wie bei `Ma2Telnet`.
- **F6** „Test senden“: 8 Beats mit 120 BPM.
- **F7** Die Demo sendet **nie**. Das gilt schon heute für alle Pulte.

### Stufe 2 – Komfort
- **F8** Bonjour-Suche nach `_os2l._tcp`. Dann muss niemand Port und Adresse eintragen.
  - Die Suche passt in den „Automatisch suchen“-Knopf (`DeskScanner`).
- **F9** Stopp-Verhalten: Wenn das Leit-Deck stoppt, werden keine Beats mehr gesendet.
  - Optional zusätzlich eine `btn`-Nachricht, zum Beispiel „Pause“.
- **F10** Beat-Offset in Millisekunden. Damit lassen sich USB- und WTOOLS-Latenz ausgleichen (wie `os2lBeatOffset` in VirtualDJ).
- **F11** Taktanfang mitschicken (Beat 1 im Takt).
  - Pioneer und Denon kennen die Taktposition.
  - Ob der Wolfmix das nutzt, ist offen.

### Stufe 3 – Ideen, nur wenn der Wolfmix sie annimmt
- **F12** Ereignisse als `btn` senden, die auf dem Wolfmix Presets auslösen:
  - Track-Wechsel,
  - Drop oder Phrase (Pioneer-Phrasenanalyse: Intro / Up / Down / Chorus / Outro),
  - „Deck geht on air“.
- **F13** `strength` aus der Wellenform-Lautstärke am aktuellen Beat.
- **F14** Ableton Link als Sender, für alle Link-fähigen Lichtprogramme.
- **F15** OS2L-Ausgabe allgemein anbieten. Das können auch QLC+, SoundSwitch-ähnliche Tools und andere OS2L-Empfänger.

## 5. Bekannte und erwartete Probleme

| # | Problem | Folge / Gegenmaßnahme |
|---|---|---|
| P1 | Wolfmix hat kein Netzwerk. WTOOLS muss laufen, der Wolfmix muss per USB dranhängen. | In der Hilfe klar sagen. Status „WTOOLS nicht erreichbar“. |
| P2 | Unklar, ob WTOOLS auf Port 5000 nur lokal (127.0.0.1) lauscht oder auch im Netz. | Am Gerät testen. Wenn nur lokal: LJ Link und WTOOLS auf demselben Rechner. |
| P3 | Laut Support muss WTOOLS manchmal **vor** VirtualDJ mit eingeschaltetem OS2L starten. | Unser Client verbindet laufend neu (F5). Damit ist die Reihenfolge egal. |
| P4 | Im FX-Rack muss BPM-Sync am ersten Drehregler aktiviert sein. Sonst passiert scheinbar nichts. | In die Hilfe-Seite aufnehmen. |
| P5 | Serato liefert keine Spielposition. | Nur BPM, keine echte Phase. Beats aus BPM erzeugen, mit `change:true` beim Laden. |
| P6 | Wenn Tasten gleichzeitig getappt werden, BPM von Link kommt oder das Mikrofon den Takt erkennt, kann WTOOLS Quellen mischen. | Am Gerät prüfen, welche Quelle gewinnt. |
| P7 | Latenz und Jitter: Netzwerk-Quelle → LJ Link → TCP → WTOOLS → USB. | Beats aus dem Beatgrid **voraus planen**, nicht erst beim Paket-Eingang senden. Offset einstellbar (F10). |
| P8 | Laut Forum sind die Online-Handbücher teils älter als Firmware 2.x. | Firmware-Version beim Test notieren. |
| P9 | Andere Programme könnten Port 5000 belegen oder parallel OS2L senden, zum Beispiel VirtualDJ. | Hinweis im Status, wenn die Verbindung abgelehnt wird. |
| P10 | OS2L-Spezifikation nur über Spiegelseiten und Nachbauten gelesen. | Vor dem Bau os2l.org selbst lesen. Mitschnitt von VirtualDJ ↔ WTOOLS wäre die beste Referenz. |
| P11 | Ein Ein-Taster-Ereignis (F12) ist nur nützlich, wenn der Wolfmix OS2L-Tasten Presets zuordnen kann. Das ist bisher nicht belegt. | Erst testen, dann bauen. |

## 6. Plan für morgen

1. **Spezifikation sichern:** os2l.org lesen. Feldnamen, Zeilenende und Verbindungsablauf festhalten. Wenn möglich auch das Node-Paket `os2l` als Referenz ansehen.
2. **Test-Empfänger bauen:** Ein kleiner TCP-Server auf Port 5000, der jede Nachricht mit Zeitstempel ausgibt. Er ersetzt WTOOLS, solange kein Gerät da ist. Als JUnit-Test und als Skript.
3. **`Os2lClient`** nach dem Muster von `Ma2Telnet`: verbinden, neu verbinden, Fehler melden, `beat(change,pos,bpm,strength)`, `button(name,state)`.
4. **`LinkService`:**
   - neuer Typ `os2l` mit Vorgaben `127.0.0.1:5000`;
   - statt „alle n Sekunden BPM“ ein Beat-Takt. Aus Position und Beatgrid des Leit-Decks den nächsten Beat berechnen und pünktlich senden.
5. **UI und i18n:**
   - Typ im Pult-Dialog,
   - Felder Host und Port,
   - Statuszeile,
   - EN- und DE-Texte.
6. **Tests:**
   - Beatfolge bei 120 und 174 BPM: Abstand und `pos` stimmen.
   - `change` bei Track-Wechsel und Deck-Wechsel.
   - Neu verbinden.
   - Die Demo sendet nicht.
7. **Doku:**
   - Abschnitt Wolfmix in `docs/lichtpult.de.md`,
   - Hilfe-Seite mit WTOOLS-Schritten und P4,
   - ADR in `docs/decisions.md`.
8. **Test am echten Wolfmix:** siehe Checkliste.

## 7. Checkliste für den Gerätetest (macht der Nutzer)

- [ ] Wolfmix-Modell und Firmware: ______ ; WTOOLS-Version: ______
- [ ] WTOOLS: OS2L eingeschaltet, Wolfmix per USB verbunden
- [ ] FX-Rack: BPM-Sync aktiv (erster Drehregler)
- [ ] LJ Link auf demselben Rechner: verbunden? Folgt das BPM am Wolfmix?
- [ ] Beat-Phase: Blitzt das Licht auf dem Kick? Offset nötig? Wie viele ms?
- [ ] Tempowechsel am CDJ, Track-Wechsel, Hot Cue: Folgt der Wolfmix sauber?
- [ ] Deck stoppt: Was macht der Wolfmix?
- [ ] LJ Link auf **anderem** Rechner im Netz: Kommt die Verbindung zustande? (P2)
- [ ] Reihenfolge: WTOOLS nach LJ Link starten. Verbindet es sich selbst? (P3)
- [ ] Kann der Wolfmix OS2L-Tasten Presets zuordnen? (für F12)
- [ ] Zum Vergleich: Ableton Link in WTOOLS mit Serato oder rekordbox. Was fühlt sich besser an?

## Quellen

- Wolfmix Reference Manual: https://www.bhphotovideo.com/lit_files/799226.pdf
- Wolfmix Mini guides (EN): https://kulturbuero.ch/files-sg/wolfmix_mini_guides_en.pdf
- Wolfmix-Forum, MIDI: https://forum.wolfmix.com/viewtopic.php?t=1248
- Thomann W1 Mk3 (Anschlüsse): https://www.thomannmusic.com/wolfmix_w1_mk3_b_stock.htm
- OS2L-Nachbau (Node): https://github.com/lordvonadel/os2l
- QLC+ OS2L-Plugin: https://docs.qlcplus.org/v4/plugins/os2l
- VirtualDJ-Forum, erweiterte OS2L-Felder: https://virtualdj.com/forums/261216/VirtualDJ_Technical_Support/Extended_OS2L_parameters_in_VirtualDJ.html
- Engine-DJ-Forum, Prime 4 + Wolfmix: https://community.enginedj.com/t/denon-prime-4-ableton-link-via-usb-or-only-via-lan/55771
