# To-do: Wolfmix und DJ-Software anbinden

Stand: 2026-11-03. Ein Plan, nichts davon ist gebaut. Annahmen sind mit **(prüfen)** markiert.

## Überblick

Was ein Live-Deck braucht, und woher die Daten bei jeder Software kommen könnten:

| | Welcher Track | Wellenform | Beatgrid / Cues | BPM live | Position / Phase live |
|---|---|---|---|---|---|
| **Serato** (läuft schon) | Log ✅ | Datei-Tags ✅ | Datei-Tags ✅ | Link ✅ (nur Tempo) | ❌ → Phase über Link (B2) |
| **VirtualDJ** | Plugin / OS2L | eigene Analyse (B1) | `database.xml` / Plugin | OS2L / Plugin | Plugin ✅ |
| **rekordbox** (FLX-Nutzer) | `master.db` (Verlauf, verzögert) / Brücke | ANLZ-Dateien ✅ | ANLZ ✅ | Brücke (rkbx_link) | Brücke (rkbx_link) |
| **Traktor** | Broadcast-Metadaten / MIDI | eigene Analyse (B1) | `collection.nml` ✅ | MIDI / Link | MIDI Beat Phase |
| **Wolfmix** (Ausgang) | – | – | – | OS2L senden | OS2L-Beats |

Details zum Wolfmix stehen in [wolfmix.de.md](wolfmix.de.md).

## Bausteine, die mehrere Programme freischalten

### B1 – Wellenform selbst berechnen
Fast jede Software verrät irgendwo den Dateipfad. Dann kann LJ Link die Audiodatei selbst lesen und die Wellenform berechnen. Die Farben kämen aus drei Bändern: tief, mittel, hoch. Der Pfad zur Wellenform ist dann `WaveformEncoder` → `Deck.setWaveform`.

- Dekodieren:
  - MP3 und WAV/AIFF in Java.
  - FLAC über eine Bibliothek.
  - **M4A/AAC** ist schwierig. Möglich sind das System (macOS AVFoundation per `afconvert`, Windows Media Foundation) oder ein mitgeliefertes ffmpeg (Lizenz LGPL, Größe beachten). **(prüfen)**
- Ergebnis zwischenspeichern, Schlüssel: Datei und Änderungsdatum.
- Das schaltet frei:
  - Wellenformen für VirtualDJ und Traktor,
  - einen Ersatz für Serato-Dateien ohne Tags,
  - FLAC/M4A bei Serato.

### B2 – Ableton Link als vollwertiger Teilnehmer
Heute hört LJ Link bei Link nur das Tempo ab. Ein echter Link-Teilnehmer kann die Uhr abgleichen (Ping/Pong-Messung wie in der Link-Bibliothek). Dann bekommt LJ Link die **Beat-Phase** von jeder Link-fähigen Software, also von Serato, Traktor, VirtualDJ und Ableton.

- Damit läuft bei Serato ein Playhead im Takt. Die absolute Position bleibt unbekannt.
- Später kann LJ Link damit auch selbst Link senden, für Wolfmix, Lichtprogramme und Apps.
- Aufwand: groß. Protokoll aus dem Quellcode der Link-Bibliothek nachbauen (GPL-Code nicht kopieren, nur das Protokoll).

### B3 – MIDI-Eingang
- Eine virtuelle MIDI-Schnittstelle:
  - macOS: IAC-Treiber, eingebaut.
  - Windows: loopMIDI nötig, ein zusätzliches Programm.
- LJ Link liest damit drei Dinge:
  - **MIDI Clock** für das Tempo,
  - von Traktor **Beat Phase** und Tempo,
  - Play und Cue.
- Java: `javax.sound.midi`.

### B4 – OS2L-Server
LJ Link meldet sich per Bonjour als `_os2l._tcp`, so wie es sonst ein Lichtprogramm tut. VirtualDJ verbindet sich dann **ohne jede Installation** und schickt BPM und jeden Beat.

Das passt zum OS2L-Code für den Wolfmix: Derselbe Nachrichten-Code arbeitet einmal als Client, einmal als Server.

## Pro Software

### VirtualDJ – gut machbar
1. **Schneller Gewinn:** OS2L-Eingang (B4). VirtualDJ zeigt dann BPM und Beats als „Tempo-Quelle“, wie heute Ableton Link.
2. **Vollständig:** Ein kleines **LJ-Link-Plugin für VirtualDJ**, mit dem offiziellen SDK (C++, kostenlos).
   - Es fragt pro Deck ab: Titel, Interpret, Datei, BPM, Pitch, Position, Spielt, Cues.
   - Abgefragt wird über VDJScript (`deck 1 get_position`, `deck 1 get_title` usw.).
   - Es schickt die Werte per UDP an `127.0.0.1`.
   - Wellenform über B1, Beatgrid aus dem Plugin oder aus `database.xml`.
3. Bauen: Plugin für macOS (bundle) und Windows (DLL). Der Installer legt es in den VirtualDJ-Plugin-Ordner.
4. **(prüfen)** Exakte Befehlsnamen für Dateipfad und Beatgrid in den SDK-Headern.

Ergebnis: echte Live-Decks mit Playhead, ähnlich wie bei Denon.

### Traktor – machbar über Umwege
1. **Bibliothek:** `collection.nml`, eine XML-Datei mit Pfad, BPM, Tonart, Cues und Beatgrid (`CUE_V2` Typ 4 = Grid). Wellenform über B1.
   - Die eigenen Traktor-Wellenformen (`Stripes`) haben ein unbekanntes Format. Sie lassen wir weg.
2. **Was geladen ist und spielt:**
   - **MIDI-Mapping** (B3): LJ Link liefert eine fertige `.tsi`-Datei mit, die ein „Generic MIDI“-Gerät anlegt. Pro Deck kommen darüber Beat Phase, Play, Tempo und, wenn möglich, die Track-Position. **(prüfen, welche Outputs es gibt)**
   - **Broadcasting:** LJ Link stellt einen Mini-Icecast-Server bereit. Traktor schickt beim Track-Wechsel Titel und Interpret mit. Das bezieht sich nur auf den Mix, nicht auf ein Deck. Den Titel suchen wir dann in `collection.nml`.
   - Ableton Link oder MIDI Clock für das Tempo.
3. Ergebnis: Track, Wellenform, Beatgrid und Phase. Die Position wird aus der Phase und dem ersten Beat geschätzt.
4. Einrichtung: mittel. Mapping importieren, unter Windows loopMIDI. Eine bebilderte Anleitung ist Pflicht.

### rekordbox (Performance-Modus, FLX-Controller) – schwierig
rekordbox sendet von sich aus nichts. Ableton Link kann es laut rkbx_link nur empfangen, nicht senden.

1. **Bibliothek:** `master.db` (SQLCipher, der Schlüssel ist öffentlich bekannt, siehe pyrekordbox) und die ANLZ-Dateien (`.DAT/.EXT/.2EX`).
   - Daraus kommen Wellenform (Farbe), Beatgrid, Cues und Phrasen.
   - Die Parser stecken schon in beat-link/crate-digger, die wir nutzen. **Wellenformen in Pioneer-Qualität sind also realistisch.**
2. **Live-Status:**
   - **a) Brücke rkbx_link** (fremdes Tool, liest den rekordbox-Speicher). Es gibt Position, Beatgrid und Tempo per OSC oder Link aus. LJ Link würde diesen OSC-Strom empfangen.
     - Vorteil: Wir lesen selbst keinen fremden Speicher.
     - Nachteile:
       - kostenpflichtig für neue Versionen,
       - vor allem Windows, Mac nur eingeschränkt,
       - kann mit jedem rekordbox-Update brechen.
   - **b) Verlauf in `master.db`:** gespielte Tracks, mit Verzögerung. Reicht nur für „was läuft gerade ungefähr“.
   - **c)** MIDI-Ausgänge aus rekordbox MIDI Learn (LED-Rückmeldung). **(prüfen)**
   - Ob rekordbox im Performance-Modus über Pro DJ Link sichtbar ist (mit DJM-Mischpult per LAN). **(prüfen, dann ginge es über beat-link)**
3. **Eigenes Speicherlesen nicht einplanen.** Es ist fragil, braucht auf dem Mac Sonderrechte und ist rechtlich heikel.

### Serato – Rest-Lücke schließen
- Die Position fehlt. Über B2 (Link-Phase) und das Beatgrid aus den Tags gibt es einen Playhead im Takt, die absolute Position aber nicht.
- B1 deckt Dateien ohne Serato-Tags ab und schaltet FLAC/M4A frei.
- `History/Sessions/*.session` liefert die gespielten Tracks. Als Kontrolle oder für eine Setliste nützlich.

## Reihenfolge (Vorschlag)

| # | Paket | Aufwand | Schaltet frei |
|---|---|---|---|
| 1 | OS2L senden (Wolfmix) | S | Wolfmix, andere OS2L-Lichtprogramme |
| 2 | OS2L empfangen (B4) | S | VirtualDJ-Tempo und Beats ohne Installation |
| 3 | Wellenform aus Audio (B1) | M | VirtualDJ, Traktor, Serato FLAC/M4A |
| 4 | Traktor: NML + MIDI-Mapping (B3) | M | Traktor-Decks |
| 5 | VirtualDJ-Plugin | M | VirtualDJ-Decks mit Position |
| 6 | rekordbox: Bibliothek + rkbx_link-Brücke | M | FLX-Nutzer (mit fremdem Tool) |
| 7 | Ableton Link als Teilnehmer (B2) | L | Phase für alle, Link senden |

## Bekannte Risiken

- **Installation von Dritt-Software:** rkbx_link, loopMIDI, VirtualDJ-Plugin. Jede Zusatzinstallation kostet Nutzer. Anleitungen mit Bildern werden nötig.
- **Herstellerupdates:**
  - Speicherbrücken (rkbx_link) brechen oft.
  - Bei den Dateiformaten (NML, ANLZ, Serato-Tags) ist das Risiko gering.
- **M4A/AAC dekodieren:** Lizenz und Größe von ffmpeg, oder System-Decoder pro Betriebssystem.
- **Kein Testgerät oder keine Lizenz:** Für Traktor, VirtualDJ und rekordbox brauchen wir jeweils eine installierte Version zum Testen. **(klären: was ist vorhanden?)**
- **Markennamen:** Traktor, rekordbox, VirtualDJ und Serato nur beschreibend nennen („funktioniert mit …“), keine Logos.

## Quellen
- rkbx_link: https://github.com/grufkork/rkbx_link
- VirtualDJ Plugin-SDK (Rust-Wrapper, zeigt die Abfragen): https://docs.rs/virtualdj-plugin-sdk/latest/aarch64-unknown-linux-gnu/struct.PluginContext.html
- VirtualDJ GetInfo-Abfragen: https://virtualdj.com/forums/56155/VirtualDJ_Plugins/Queries_used_by_GetInfo().html
- Traktor Beat Phase per MIDI: https://community.native-instruments.com/discussion/comment/160233
- Traktor BPM per MIDI: https://community.native-instruments.com/discussion/comment/215705
- OS2L-Nachbau: https://github.com/lordvonadel/os2l
