# Wolfmix mit LJ Link benutzen

> **Beta.** An einem Wolfmix W1 mit zwei XDJ-1000MK2 ausprobiert. **Ableton Link** lief dort besser als OS2L und ist der
> empfohlene Weg. Über OS2L schwankte das Tempo um ein Zehntel, und der Wolfmix startete seinen Beat manchmal neu
> (Doppelblitze, Aussetzer). 1.7.3 nimmt die Beats direkt aus den Beat-Paketen der Player, und 1.7.4 hält das Tempo ruhig,
> wenn ein Player an einer Rundungsgrenze wie 137,95 BPM steht. **Beide Korrekturen sind am Wolfmix noch nicht ausprobiert.**
> Halte bei einer wichtigen Veranstaltung immer einen Plan B bereit (Tap-Taste am Wolfmix oder die Audio-Erkennung des Geräts).
> Wenn du es ausprobierst, melde uns bitte das Ergebnis, auch wenn es nicht klappt (Abschnitt 8).

## 1. Was das macht

Der Wolfmix hat keinen Netzwerkanschluss, nur USB und DMX. Das Tempo kann er deshalb nur über einen Computer bekommen, an dem er
per USB hängt, und zwar über die Hersteller-App **WTOOLS**. LJ Link gibt WTOOLS das Tempo auf einem von zwei Wegen:

- **Ableton Link (empfohlen):** LJ Link gibt das Tempo mit Beat und Takt in eine Link-Session, WTOOLS übernimmt es.
- **OS2L (Alternative):** LJ Link schickt WTOOLS das Tempo und **jeden einzelnen Beat** mit Nummer (so arbeitet auch VirtualDJ).

Effekte und Chaser des Wolfmix laufen dann im Takt der DJs.

```
CDJs / Denon / Serato ─► LJ Link ──Ableton Link oder OS2L──► WTOOLS ──USB──► Wolfmix ──DMX──► Licht
```

Das ist nur eine Einbahnstraße: LJ Link liest vom DJ-Equipment und sendet ans Licht. Es steuert nichts am DJ-Equipment.

## 2. Was du brauchst

- Einen Wolfmix (W1 MK1, MK2 oder MK3), per **USB** an einen Computer angeschlossen.
- Die App **WTOOLS** von Wolfmix auf diesem Computer (kostenloser Download beim Hersteller).
- LJ Link, am besten die neueste Version (mindestens 1.7.4 für das ruhige Tempo), am besten auf demselben Computer wie WTOOLS.
- Natürlich die Player, von denen LJ Link das Tempo bekommt (Pioneer, Denon, Serato).

## 3. Ableton Link (empfohlen)

Nimm Ableton Link, wann immer es geht. Das Wolfmix-Handbuch (Kapitel 8, Seiten 19–20) nennt Ableton Link sehr genau, weil der
Wolfmix das BPM übernimmt, das die DJ-Software berechnet, hier LJ Link. In unserem Test lief es auch ruhiger als OS2L.

1. **Wolfmix per USB** an den Computer anschließen und **WTOOLS starten**. Der Wolfmix muss dort erkannt werden.
2. In **LJ Link**: Menü (☰) → **Lichtpult-Link** → **Wolfmix (Ableton Link) – empfohlen**. Es schaltet weiter unten im Menü
   **Ableton Link** ein, falls es aus ist.
3. In **WTOOLS** **Ableton Link** als Quelle für das Tempo wählen. WTOOLS und LJ Link müssen im selben Netzwerk oder auf
   demselben Computer sein.
4. **Am Wolfmix** im **FX-Rack** den **ersten Drehregler drücken**, damit die Effekte dem BPM folgen (Handbuch, Kapitel 8).
   Ohne das passiert scheinbar nichts.
5. Unter **SEND BPM** das Deck wählen (oder Auto, dann der Master). Das BPM am Wolfmix folgt ihm, mit Beat und Takt.

## 4. OS2L (Alternative)

> **Hinweis:** OS2L ist die Alternative, wenn Ableton Link nicht geht. In unserem Test **startete der Wolfmix seinen Beat
> manchmal neu**, wenn sich das Tempo über OS2L änderte. Warum, ist vom Hersteller nicht beschrieben. LJ Link sendet ein neues
> Tempo jetzt nur noch bei einer echten Änderung, das ist am Wolfmix aber **noch nicht ausprobiert**. Passiert es bei dir, auf
> Ableton Link wechseln (Abschnitt 3).

1. **Wolfmix per USB** an den Computer anschließen und **WTOOLS starten**. Dort **OS2L** einschalten.
2. In **LJ Link**: Menü (☰) → **Lichtpult-Link** → **Wolfmix (OS2L, WTOOLS) – Beta**.
3. **Adresse** `127.0.0.1`, wenn WTOOLS auf demselben Computer läuft, sonst die Adresse des WTOOLS-Computers (ob WTOOLS
   Verbindungen von einem anderen Computer annimmt, ist nicht geprüft, siehe Abschnitt 7).
4. **Port** `5000` wie im Wolfmix-Handbuch. **Achtung:** Ein Beitrag im VirtualDJ-Forum nennt dagegen Port `9996`. Kommt nichts
   an, den OS2L-Port in WTOOLS ansehen und diesen eintragen. **Speichern** drücken.
5. **Test senden (120 BPM)** drücken. LJ Link schickt acht Beats mit 120 BPM.
   - In der Statuszeile unter dem Knopf erscheint die Zahl der gesendeten Nachrichten.
   - Erscheint „Kein OS2L-Empfänger unter …“, siehe Abschnitt 6.
6. **Am Wolfmix** im **FX-Rack** den **ersten Drehregler drücken**, damit die Effekte dem BPM folgen.
7. Ein Deck laden und starten. Unter **SEND BPM** steht, welches Deck den Takt gibt. Auto nimmt den Master.

## 5. Was LJ Link genau sendet

### Tempo (ab 1.7.4, am Wolfmix noch nicht ausprobiert)

- Das Tempo wird auf 0,1 BPM gerundet und ändert sich erst, wenn sich das geglättete Tempo des Players um mehr als
  **0,15 BPM** vom gesendeten Wert entfernt.
- Ein Player an einer Rundungsgrenze (137,95, pendelt zwischen 137,9 und 138,0) lässt das Tempo nicht mehr springen.
- Eine Änderung ab **0,25 BPM** kommt in unter einer Sekunde durch. Kleinere Abweichungen bis 0,15 BPM werden absichtlich nicht gesendet.
- Ein fester Wert, das Tempo einer Ableton-Link-Session als Quelle und Serato gehen genau raus, ohne diese Beruhigung.
- Die BPM-Anzeige im Deck zeigt (ab 1.7.5) den echten Wert des Players mit zwei Nachkommastellen, noch nicht an echter Hardware getestet. Das beruhigte, gesendete Tempo steht als Tooltip am BPM-Feld („An Lichtpulte gesendet“). Es kann bis zu 0,15 BPM von der Anzeige abweichen.
- Das gilt für Ableton Link und OS2L gleich. Mehr dazu im Wiki unter
  [Tempo am Lichtpult springt](https://skyplayser.github.io/LJ-Link/wiki-troubleshooting.html#tempo-jumps).

### Bei OS2L zusätzlich

- **Bei jedem Beat** eine Nachricht mit Beat-Nummer und Tempo. Bei Pioneer-Playern kommt der Beat direkt aus ihren
  Beat-Paketen und liegt damit genau auf dem Beat, auch ohne rekordbox-Daten.
- Die Beat-Nummer ist so gezählt, dass jeder **vierte Beat die Eins eines Taktes** ist, wie es das Beatgrid des Players sagt.
- `change` ist an, wenn die Phase neu gesucht werden soll: nach dem Start, nach einem Sprung (Cue, Loop), bei einem
  anderen Track oder Deck und bei einem Tempo-Sprung ab 0,5 BPM.
- **Steht das Deck**, werden keine Beats gesendet.
- **Serato und Ableton Link** liefern keine Spielposition. Dort laufen die Beats **frei** im Tempo. Eine echte Phase gibt es dort nicht.
- Läuft WTOOLS nicht, zeigt LJ Link das im Status und versucht es alle 5 Sekunden neu.

### Allgemein

- Die **Demo** sendet nur, wenn du in den Lichtpult-Einstellungen **Demo-Daten senden** anhakst. Der Haken ist bei jedem Start aus.
- Nicht gesendet werden: Tasten, Track-Titel, Wellenform, Drop oder Phrasen.

## 6. Wenn es nicht klappt

| Was du siehst | Mögliche Ursache | Was tun |
|---|---|---|
| Ableton Link: Wolfmix folgt nicht | In WTOOLS ist nicht Ableton Link als Quelle gewählt, oder BPM-Sync am Wolfmix ist aus | In WTOOLS Ableton Link wählen. Ersten Drehregler im FX-Rack drücken. WTOOLS und LJ Link im selben Netz oder auf demselben Computer |
| „Kein OS2L-Empfänger unter 127.0.0.1:5000 …“ | WTOOLS läuft nicht, OS2L ist aus, oder WTOOLS hört auf einem anderen Port | WTOOLS starten, OS2L einschalten. Den Port in WTOOLS ansehen (5000 oder 9996) und in LJ Link eintragen |
| Status zeigt gesendete Nachrichten, aber der Wolfmix reagiert nicht | BPM-Sync am Wolfmix aus, oder WTOOLS nimmt die Nachrichten nicht an | Ersten Drehregler im FX-Rack drücken. WTOOLS neu starten, **danach** LJ Link neu starten |
| Doppelblitze oder Aussetzer bei OS2L, der Beat startet neu | Tempowechsel über OS2L (siehe Hinweis in Abschnitt 4) | Neueste Version nehmen. Wenn es bleibt: auf Ableton Link wechseln und melden |
| Tempo springt (137,9, 138,0, 137,9 …) | Player steht an einer Rundungsgrenze | Neueste Version nehmen (ab 1.7.4, ungetestet), SEND BPM auf ein Deck stellen, Ableton Link nehmen. Diagnosebericht senden |
| Nichts am Wolfmix, aber der Test geht | Anderes Programm blockiert den Port, oder eine zweite OS2L-Quelle (z. B. VirtualDJ) sendet auch | Andere OS2L-Programme beenden |

Zuerst immer: WTOOLS **vor** LJ Link starten oder beide neu starten.

## 7. Was nicht geprüft oder nicht bekannt ist

- Ob die Korrekturen aus 1.7.3 und 1.7.4 die Doppelblitze und Beat-Neustarts am Wolfmix über OS2L beseitigen.
- Welchen OS2L-Port WTOOLS als Vorgabe nutzt (`5000` im Handbuch, `9996` in einem Forenbeitrag).
- Ob WTOOLS Verbindungen von einem anderen Computer annimmt.
- Wie viel Verzögerung USB zwischen WTOOLS und Wolfmix dazugibt.

## 8. Bitte melde dein Ergebnis

Jede Rückmeldung hilft, aus der Beta eine stabile Funktion zu machen. Schreib uns, auch wenn es **nicht** geht:

- **In LJ Link:** Menü (☰) → **Problem melden** (mit Diagnosebericht; ab 1.7.4 stehen darin auch die Tempo-Rohdaten
  jedes Pioneer-Players), per E-Mail, als GitHub-Issue oder im [Discord](https://discord.gg/N6DMca4Bjk).
- Bitte dazuschreiben: Wolfmix-Modell (MK1/MK2/MK3) und Firmware-Version, WTOOLS-Version, Betriebssystem, Ableton Link oder
  OS2L, ob LJ Link und WTOOLS auf demselben Computer liefen, und was genau passiert ist (Test-Knopf, echte Tracks,
  Doppelblitze oder Neustarts, Fehlermeldung).
- Checkliste zum Abhaken: [wolfmix.de.md](wolfmix.de.md), Abschnitt 7.

## 9. Mehr

- Wiki: [Wolfmix](https://skyplayser.github.io/LJ-Link/wiki-wolfmix.html).
- Warum OS2L und was später kommen könnte: [wolfmix.de.md](wolfmix.de.md) und [roadmap-software.de.md](roadmap-software.de.md).
- Technische Entscheidungen: ADR-020 und ADR-021 in [decisions.md](decisions.md).
- Andere Lichtpulte: [lichtpult.de.md](lichtpult.de.md).

Wolfmix und WTOOLS sind Marken ihrer Inhaber. LJ Link ist kein Produkt von Wolfmix oder Nicolaudie.
