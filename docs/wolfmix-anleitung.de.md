# Wolfmix mit LJ Link benutzen

> **⚠️ Ungetestetes Alpha-Feature.** Die Wolfmix-Anbindung wurde **noch nie an einem echten Wolfmix ausprobiert.** Sie ist nur gegen
> einen Test-Empfänger auf dem Entwicklungsrechner geprüft. Es kann sein, dass sie bei dir nicht oder nur teilweise funktioniert.
> Bitte nicht bei einer wichtigen Veranstaltung zum ersten Mal einsetzen, und halte immer ein Plan B bereit (Tap-Taste am Wolfmix
> oder die Audio-Erkennung des Geräts). Wenn du sie ausprobierst, melde uns bitte das Ergebnis, auch wenn es nicht klappt (Abschnitt 7).

## 1. Was das macht

Der Wolfmix hat keinen Netzwerkanschluss, nur USB und DMX. Das Tempo kann er deshalb nur über einen Computer bekommen, an dem er
per USB hängt, und zwar über die Hersteller-App **WTOOLS**. LJ Link schickt WTOOLS über das Protokoll **OS2L** (so arbeitet auch
VirtualDJ) das Tempo und **jeden einzelnen Beat** der Player. Effekte und Chaser des Wolfmix laufen dann im Takt der DJs.

```
CDJs / Denon / Serato ─► LJ Link ──OS2L──► WTOOLS ──USB──► Wolfmix ──DMX──► Licht
```

Das ist nur eine Einbahnstraße: LJ Link liest vom DJ-Equipment und sendet ans Licht. Es steuert nichts am DJ-Equipment.

## 2. Was du brauchst

- Einen Wolfmix (W1 MK1, MK2 oder MK3), per **USB** an einen Computer angeschlossen.
- Die App **WTOOLS** von Wolfmix auf diesem Computer (kostenloser Download beim Hersteller).
- LJ Link, mindestens Version 1.4.4, am besten auf demselben Computer. Dann ist die Adresse `127.0.0.1`.
  Auf einem anderen Computer im Netz geht es vielleicht auch, **das ist nicht geprüft** (siehe Abschnitt 6).
- Natürlich die Player, von denen LJ Link das Tempo bekommt (Pioneer, Denon, Serato).

## 3. Einrichten, Schritt für Schritt

1. **Wolfmix per USB** an den Computer anschließen und **WTOOLS starten**. Der Wolfmix muss dort erkannt werden.
2. In **WTOOLS** den Schalter **OS2L** einschalten. (Laut Hersteller-Anleitung: Wolfmix per USB verbinden, WTOOLS öffnen, auf
   die Schaltfläche *OS2L* klicken. In der Ansicht kann der Name leicht anders sein, je nach Version.)
3. In **LJ Link** (Browser-Ansicht auf dem Computer): **Einstellungen → Lichtpult-Link (Tempo senden)** → **Wolfmix (OS2L, WTOOLS) – Alpha**.
4. **Adresse** `127.0.0.1` (wenn WTOOLS auf demselben Computer läuft), **Port** `5000`. **Speichern** drücken.
5. **Test senden (120 BPM)** drücken. LJ Link schickt acht Beats mit 120 BPM, einen alle halbe Sekunde.
   - In der Statuszeile unter dem Knopf erscheint die Zahl der gesendeten Nachrichten.
   - Erscheint „Kein OS2L-Empfänger unter …“, siehe Abschnitt 5.
6. **Am Wolfmix** im **FX-Rack** den **ersten Drehregler drücken**, damit der Effekt dem Tempo folgt (BPM-Sync). Ohne das passiert scheinbar nichts.
7. Ein Deck laden und starten. Unter **SEND BPM** steht, welches Deck den Takt gibt. Auto nimmt den Master. Die Effekte sollten jetzt auf dem Beat laufen.

## 4. Was LJ Link genau sendet

- **Bei jedem Beat** eine Nachricht mit Beat-Nummer (`pos`), Tempo (`bpm`) und dem Hinweis `change`.
- Die Beat-Nummer ist so gezählt, dass jeder **vierte Beat die Eins eines Taktes** ist, wie es das Beatgrid des Players sagt.
- `change` ist an, wenn die Phase neu gesucht werden soll: nach dem Start, nach einem Sprung (Cue, Loop, Suchen), bei einem
  anderen Track oder Deck und bei einer Tempoänderung von mehr als 0,5 BPM.
- **Steht das Deck**, werden keine Beats gesendet. Rückwärts (Scratch) werden auch keine gesendet.
- **Serato und Ableton Link** liefern keine Spielposition. Dort läuft der Takt **frei** im Tempo des geladenen Tracks, auch
  wenn der Track gerade nicht läuft. Eine echte Phase gibt es dort nicht.
- Die **Demo** sendet nie etwas.
- Nicht gesendet werden: Tasten, Track-Titel, Wellenform, Drop oder Phrasen. Das wäre eine spätere Erweiterung.

## 5. Wenn es nicht klappt

| Was du siehst | Mögliche Ursache | Was tun |
|---|---|---|
| „Kein OS2L-Empfänger unter 127.0.0.1:5000 …“ | WTOOLS läuft nicht, OS2L ist aus, oder WTOOLS hört auf einem anderen Port | WTOOLS starten, OS2L einschalten. Den Port in WTOOLS ansehen und in LJ Link eintragen |
| Status zeigt gesendete Nachrichten, aber der Wolfmix reagiert nicht | BPM-Sync am Wolfmix aus, oder WTOOLS nimmt die Nachrichten nicht an | Ersten Drehregler im FX-Rack drücken. WTOOLS neu starten, **danach** LJ Link neu starten |
| Läuft, aber leicht neben dem Beat | Verzögerung durch Netzwerk, WTOOLS und USB. Eine einstellbare Verzögerung gibt es **noch nicht** | Melden (Abschnitt 7), mit Angabe, um wie viel Millisekunden es ungefähr daneben liegt |
| Springt oder hängt nach Track-Wechsel | Phase wird neu gesucht | Ein paar Beats abwarten. Wenn es bleibt: melden |
| Nichts am Wolfmix, aber der Test geht | Anderes Programm blockiert den Port, oder eine zweite OS2L-Quelle (z. B. VirtualDJ) sendet auch | Andere OS2L-Programme beenden |

Zuerst immer: WTOOLS **vor** LJ Link starten oder beide neu starten. LJ Link versucht alle 5 Sekunden, die Verbindung neu aufzubauen.

## 6. Was nicht geprüft oder nicht bekannt ist

- **Alles am echten Wolfmix.** Wir wissen nicht, ob WTOOLS unsere Nachrichten so annimmt, wie wir es beim Test-Empfänger sehen.
- **Trennzeichen:** Die OS2L-Beschreibung nennt kein Zeichen zwischen den Nachrichten. LJ Link hängt an jede einen Zeilenumbruch an.
  Falls WTOOLS daran scheitert, ist das der erste Verdacht.
- **Anderer Computer:** Ob WTOOLS Verbindungen von einem anderen Rechner annimmt oder nur von demselben, ist unbekannt.
- **Verzögerung:** Im Test gegen den Test-Empfänger lagen die Beats im Schnitt 1,5 ms (höchstens 7 ms) neben dem Takt. Dazu kommen beim
  echten Wolfmix noch WTOOLS und USB, die wir nicht gemessen haben. Ein einstellbarer Ausgleich ist geplant, aber noch nicht gebaut.
- **Firmware und WTOOLS-Version:** Die Online-Handbücher sind zum Teil älter als die aktuelle Firmware. Unsere Annahmen stammen daraus.
- **Presets per OS2L:** Ob der Wolfmix Tasten von OS2L zu Presets zuordnen kann, ist nicht belegt. LJ Link sendet auch keine Tasten.

## 7. Bitte melde dein Ergebnis

Jede Rückmeldung hilft, aus der Alpha eine geprüfte Funktion zu machen. Schreib uns, auch wenn es **nicht** geht:

- **In LJ Link:** Einstellungen → **Problem melden …** (mit Diagnosedaten), per E-Mail oder als GitHub-Issue.
- Bitte dazuschreiben: Wolfmix-Modell (MK1/MK2/MK3) und Firmware-Version, WTOOLS-Version, Betriebssystem, ob LJ Link und
  WTOOLS auf demselben Computer liefen, und was genau passiert ist (Test-Knopf, echte Tracks, Abweichung vom Beat in ms, Fehlermeldung).
- Checkliste zum Abhaken: [wolfmix.de.md](wolfmix.de.md), Abschnitt 7.

## 8. Mehr

- Warum OS2L und was später kommen könnte: [wolfmix.de.md](wolfmix.de.md) und [roadmap-software.de.md](roadmap-software.de.md).
- Technische Entscheidung: ADR-020 in [decisions.md](decisions.md).
- Andere Lichtpulte: [lichtpult.de.md](lichtpult.de.md).

Wolfmix und WTOOLS sind Marken ihrer Inhaber. LJ Link ist kein Produkt von Wolfmix oder Nicolaudie.
