# Lichtpult-Link: Tempo vom DJ ans Lichtpult

Diese Anleitung erklärt Schritt für Schritt, wie LJ Link das Tempo (BPM) der DJ-Player an ein Lichtpult schickt, sodass
Chaser und Effekte im Takt der Musik laufen. Sie setzt nichts voraus: Wer noch nie ein Pult mit einem anderen Programm verbunden
hat, kommt damit durch.

**Inhalt**

1. [Was passiert eigentlich?](#1-was-passiert-eigentlich)
2. [Vorbereitung: Netzwerk](#2-vorbereitung-netzwerk)
3. [Welches Deck gibt das Tempo? (SEND BPM)](#3-welches-deck-gibt-das-tempo-send-bpm)
4. [grandMA2 und grandMA2 onPC](#4-grandma2-und-grandma2-onpc)
5. [grandMA3 und grandMA3 onPC](#5-grandma3-und-grandma3-onpc)
6. [dot2](#6-dot2)
7. [Daslight](#7-daslight)
8. [Andere Programme (eigenes OSC-Ziel)](#8-andere-programme-eigenes-osc-ziel)
9. [Fehlersuche](#9-fehlersuche)
10. [Was ist geprüft, was nicht?](#10-was-ist-geprüft-was-nicht)

---

## 1. Was passiert eigentlich?

LJ Link liest das Tempo der Player (Pioneer und Denon) und schickt es übers Netzwerk an dein Pult. Je nach Pult auf
zwei verschiedenen Wegen:

| Pult | Weg | Was ankommt |
|---|---|---|
| grandMA2, grandMA2 onPC | **Telnet** (TCP, Port 30000) | ein Befehl in der Kommandozeile, z. B. `SpecialMaster 3.16 At 128` |
| grandMA3, grandMA3 onPC | **OSC** (UDP, frei wählbarer Port, z. B. 8000) | ein Befehl in der Kommandozeile, z. B. `Master 3.1 At BPM 128` |
| Daslight und andere | **OSC** (UDP) | eine Zahl, z. B. `128.0` an die Adresse `/ljlinkpro/bpm` |
| dot2 | – | nicht möglich, siehe [Kapitel 6](#6-dot2) |

Gesendet wird, sobald sich das Tempo um mindestens 0,1 BPM ändert. Bleibt es gleich, wird es zur Sicherheit wiederholt:
per OSC alle 2 Sekunden, beim grandMA2 alle 15 Sekunden (damit die Kommandozeile nicht vollläuft).

LJ Link **steuert nur das Tempo**. Es startet keine Cues, ändert keine Show und schickt nichts an die DJ-Geräte.

> **Wichtig:** Ein Speedmaster bewegt nur Effekte und Chaser, die ihm **zugewiesen** sind. Wenn sich auf dem Pult nichts tut,
> obwohl das Tempo ankommt, ist fast immer der Effekt keinem Speedmaster zugeordnet.

## 2. Vorbereitung: Netzwerk

1. **Computer und Pult müssen sich erreichen.** Am einfachsten hängen beide am selben Netzwerk-Switch wie die DJ-Player.
   Läuft das Pult in einem eigenen Licht-Netzwerk, braucht der Computer eine zweite Netzwerkkarte (z. B. USB-Adapter) in diesem
   Netz.
2. **IP-Adresse des Pults herausfinden.**
   - grandMA2: *Setup → Network → Network Interface* (oder in der Titelleiste von onPC).
   - grandMA3: *Menu → Network* (unter *Interfaces* steht die Adresse).
   - onPC auf **demselben Computer** wie LJ Link: einfach `127.0.0.1` eintragen.
3. **Gleiches Netz?** Die ersten drei Zahlengruppen müssen gleich sein, wenn das Netz wie meistens `255.255.255.0` nutzt.
   Beispiel: Computer `192.168.1.10`, Pult `192.168.1.50` passt; `10.0.0.5` passt nicht.
4. **Firewall (nur onPC auf einem anderen Rechner).** Windows fragt beim ersten Start von onPC, ob es im Netzwerk erreichbar sein
   darf: **„Private Netzwerke“ erlauben.** Wurde das abgelehnt: *Windows-Sicherheit → Firewall → App durch die Firewall
   zulassen → grandMA onPC* anhaken.
5. **Pult automatisch suchen (nur grandMA2).** In LJ Link *Einstellungen → Lichtpult-Link → Pult suchen*. Gefunden wird jeder
   grandMA2 und jedes grandMA2 onPC im Netz, auch wenn die Fernsteuerung dort noch aus ist. Ein Klick auf den Treffer trägt die
   Adresse ein. Den grandMA3 findet die Suche noch nicht, dort die Adresse von Hand eintragen.

## 3. Welches Deck gibt das Tempo? (SEND BPM)

Oben in LJ Link gibt es den Reiter **SEND BPM**. Dort legst du fest, woher das Tempo kommt:

- **Auto:** das Deck, das **Master** und/oder **live** ist (am Mixer auf Sendung). Gibt es zwei Systeme mit je einem Master
  (z. B. CDJs und ein Denon), gewinnt der Master, der auch live ist und spielt.
- **Ein bestimmtes Deck:** immer dieses Deck, auch wenn ein anderes live ist. Hat es gerade keinen Track, wird nichts gesendet.

Das Deck, von dem gerade gesendet wird, trägt in der Waveform-Ansicht oben neben dem Faderbalken ein grünes **SEND BPM**.
Im Demo-Modus wird nie etwas an ein Pult gesendet.

---

## 4. grandMA2 und grandMA2 onPC

Der grandMA2 hat **keinen OSC-Eingang**. LJ Link verbindet sich deshalb wie ein Telnet-Programm mit der Kommandozeile des
Pults (Port 30000), meldet sich mit einem Benutzer der Show an und tippt dort den Befehl für den Speedmaster.

### 4.1 Am Pult: Telnet einschalten

1. Am grandMA2 (oder in grandMA2 onPC) die Taste **Setup** drücken.
2. **Console** antippen, dann **Global Settings**.
3. Den Eintrag **Telnet** suchen und auf **Login Enabled** stellen.

Das gilt für das Pult und für onPC gleichermaßen. Solange Telnet aus ist (*Login Disabled*), findet die Suche das Pult zwar,
aber es nimmt keine Befehle an.

### 4.2 Am Pult: einen Benutzer für LJ Link anlegen (empfohlen)

Jede Telnet-Verbindung muss sich als Benutzer der Show anmelden. Benutzername und Passwort unterscheiden Groß- und
Kleinschreibung. MA Lighting empfiehlt, für die Fernsteuerung einen eigenen Benutzer anzulegen, damit er niemandem am Pult in
die Quere kommt:

1. In der Benutzerverwaltung der Show (unter **Setup**, Bereich **Console**, *User & Profiles*) einen neuen Benutzer anlegen,
   z. B. `lj`.
2. Ein Passwort vergeben, z. B. `link`.
3. Der Benutzer braucht die Rechte, Speedmaster zu verändern (Rechte-Stufe mindestens „Playback“; im Zweifel „Program“).
4. **Show speichern.** Benutzer stehen in der Show; ohne Speichern sind sie nach dem nächsten Laden weg.

**Ohne eigenen Benutzer geht es auch:** Laut grandMA2-Handbuch hat jede Show zwei Benutzer: **Administrator** mit dem Passwort
`admin` (voller Zugriff) und **Guest** (eingeschränkt). LJ Link trägt deshalb `administrator` / `admin` als Vorgabe ein.
Hat jemand das Passwort in deiner Show geändert, trage das richtige ein. Weil dieses Passwort öffentlich bekannt ist, empfiehlt
es sich, es in Shows zu ändern, die in einem offenen Netz laufen.

### 4.3 Am Pult: den Speedmaster vorbereiten

1. Entscheiden, welcher **Speedmaster** das Tempo bekommt. Der grandMA2 hat die Speedmaster **Speed 1 bis 15** und
   **Speed 16 = BPM**. In der Kommandozeile heißen sie `SpecialMaster 3.1` bis `SpecialMaster 3.16`. Effekte und Chaser, die im
   Takt laufen sollen, diesem Speedmaster zuordnen.
2. **Test von Hand:** In die Kommandozeile des Pults `SpecialMaster 3.16 At 120` tippen (oder `3.1` usw. für Speed 1) und Enter drücken. Der Speedmaster
   sollte jetzt auf 120 BPM stehen (sichtbar im *Masters*-Fenster), und der zugeordnete Effekt im Takt laufen.
3. Passt das nicht zu deiner Show (z. B. weil du einen anderen Speedmaster nimmst), merke dir den Befehl, der bei dir
   funktioniert. Genau diesen trägst du gleich in LJ Link ein, nur mit `{bpm}` statt der Zahl.

### 4.4 In LJ Link einrichten

1. Menü (☰) öffnen → **Lichtpult-Link (Tempo senden)**, oder oben den Reiter **LINK** anklicken.
2. Bei der Auswahl **grandMA2 / grandMA2 onPC (Telnet)** wählen.
3. **Pult suchen** drücken und den gefundenen grandMA2 anklicken, oder die IP-Adresse von Hand eintragen
   (onPC auf demselben Computer: `127.0.0.1`).
4. **Port** bleibt `30000`.
5. **Benutzer** und **Passwort**: vorgegeben ist `administrator` / `admin` (Standard jeder grandMA2-Show). Nur ändern, wenn deine
   Show einen anderen Benutzer oder ein anderes Passwort nutzt.
6. Bei **An Speedmaster senden** den Speedmaster wählen: **Speed 16 (BPM)** (Vorgabe) oder Speed 1 bis 15. Die Auswahl
   schreibt die Nummer in den Befehl.
7. **Befehl** prüfen: Standard ist `SpecialMaster 3.16 At {bpm}`. `{bpm}` wird durch das Tempo als **ganze Zahl** ersetzt, z. B. `128`:
   Die Kommandozeile des grandMA2 nimmt keine Kommazahlen an (`At 128.0` bewirkt nichts).
8. **Speichern**, dann **Test senden (120 BPM)**. Der Speedmaster sollte auf 120 springen.
9. Unter dem Knopf steht „Zuletzt gesendet: … BPM“. Steht dort eine Fehlermeldung, siehe [Fehlersuche](#9-fehlersuche).

**Hinweise**

- Jeder gesendete Befehl landet in der Befehlshistorie des Pults. Deshalb wird ein gleichbleibendes Tempo beim grandMA2 nur
  alle 15 Sekunden wiederholt.
- Nicht über Telnet ins Fixture-Setup oder den Zeitplan (Schedule) gehen: laut MA sperrt das den Zugriff für andere Benutzer
  ohne Warnung. LJ Link schickt nur den eingetragenen Befehl.

---

## 5. grandMA3 und grandMA3 onPC

Der grandMA3 nimmt Befehle für die Kommandozeile per **OSC** an (Adresse `/cmd`, Text). LJ Link schickt dorthin den
Befehl für den Speedmaster.

### 5.1 Am Pult: OSC-Eingang einrichten

1. **Menu** öffnen → **In & Out** → Reiter **OSC**.
2. Oben bei **Interface** die Netzwerkkarte wählen, über die der Computer mit LJ Link erreichbar ist.
3. In der Tabelle eine Zeile anlegen (oder eine vorhandene nehmen) und so einstellen:

   | Feld | Wert |
   |---|---|
   | **Mode** | `UDP` |
   | **Port** | `8000` (oder ein anderer freier Port; muss in LJ Link gleich sein) |
   | **Prefix** | leer lassen |
   | **Receive Command** | `Yes` (sonst werden Befehle ignoriert) |
   | **Receive** | egal (betrifft nur Daten an Fader und Tasten) |
   | **EchoInput** | `Yes` zum Testen: eingehende Nachrichten erscheinen im *System Monitor* |

4. **Enable Input** einschalten. Kommt OSC an, leuchtet die Titelleiste von *Enable Input* gelb auf.

Hinweis zum Prefix: Steht dort z. B. `gma3`, erwartet das Pult `/gma3/cmd` statt `/cmd`. LJ Link sendet an `/cmd`, also
das Feld leer lassen.

### 5.2 Am Pult: Speedmaster prüfen

1. Den Effekt oder die Sequenz einem **Speedmaster** zuordnen (der erste heißt `Master 3.1`).
2. Test in der Kommandozeile: `Master 3.1 At BPM 120` tippen, Enter. Der Speedmaster steht auf 120 BPM.
   (Das ist die Schreibweise aus dem grandMA3-Handbuch.)

### 5.3 In LJ Link einrichten

1. Menü (☰) → **Lichtpult-Link (Tempo senden)** oder Reiter **LINK**.
2. **grandMA3 / grandMA3 onPC (OSC)** wählen.
3. IP-Adresse des Pults eintragen (onPC auf demselben Computer: `127.0.0.1`).
4. **Port** wie am Pult, z. B. `8000`.
5. **Befehl**: Standard ist `Master 3.1 At BPM {bpm}`.
6. **Speichern**, dann **Test senden (120 BPM)**. Mit *EchoInput* siehst du die Nachricht im System Monitor.

---

## 6. dot2

Der dot2 lässt sich über das Netzwerk weder per Telnet noch per OSC steuern. Laut Handbuch nimmt er von außen nur MIDI,
DMX (auch Art-Net und sACN) und Schaltkontakte an, und damit kann LJ Link ihm das Tempo heute **nicht** schicken.

Was du stattdessen tun kannst:

- **Tap-Tempo:** Speed-Taste halten und im Takt auf die Taste eines Executors tippen. Das große BPM-Feld in LJ Link hilft
  beim Mitzählen.
- **Sound-to-Light:** Der dot2 hat einen Audio-Eingang mit BPM-Erkennung (Fenster *Sound Input*).

Ein Weg über MIDI ist für eine spätere Version vorgemerkt.

---

## 7. Daslight

Daslight synchronisiert sein Tempo offiziell über **Tap-Tempo**, **MIDI-Clock** und **Ableton Link**. Außerdem hat Daslight ein
**OSC-Mapping**, mit dem eingehende OSC-Nachrichten Bedienelementen zugeordnet werden.

LJ Link sendet für Daslight das Tempo als Zahl über OSC (z. B. `128.0` an `/ljlinkpro/bpm`). **Ob sich damit in deiner
Daslight-Version das BPM setzen lässt, ist noch nicht bestätigt.** So probierst du es aus:

1. In Daslight die **OSC-Eingabe** einschalten und den **Port** notieren (in den Einstellungen bzw. im OSC-Mapping).
2. In LJ Link **Daslight** wählen, die IP-Adresse des Daslight-Rechners (gleicher Computer: `127.0.0.1`) und den Port
   eintragen, Adresse `/ljlinkpro/bpm` lassen, **Speichern**, **Test senden**.
3. In Daslight im **MIDI/OSC-Monitor** nachsehen, ob `/ljlinkpro/bpm` ankommt.
4. Im **OSC-Mapping** die Adresse dem BPM- oder Speed-Regler zuordnen, sofern deine Version das anbietet.

Kommt die Nachricht an, lässt sich aber kein BPM zuordnen, bleibt im Moment nur Tap-Tempo oder MIDI-Clock von einer anderen
Quelle. Eine MIDI-Clock direkt aus LJ Link ist vorgemerkt.

---

## 8. Andere Programme (eigenes OSC-Ziel)

**Eigenes OSC-Ziel** schickt das Tempo als Zahl (float) an eine frei wählbare Adresse und einen Port, z. B. für Resolume,
QLC+, Chamsys MagicQ, TouchDesigner oder eigene Skripte. Im Zielprogramm diese Adresse einem Tempo-Regler zuordnen.

---

## 8a. Wolfmix und andere OS2L-Programme (Beta)

> **Beta:** an einem Wolfmix W1 ausprobiert. **Ableton Link** lief dort besser als OS2L und ist der empfohlene Weg: in LJ Link
> **Wolfmix (Ableton Link) – empfohlen** wählen, in WTOOLS Ableton Link als Tempo-Quelle. OS2L (unten) ist die Alternative; im
> Test startete der Wolfmix seinen Beat bei Tempowechseln über OS2L manchmal neu. Die Korrekturen aus 1.7.3 und 1.7.4 sind am
> Wolfmix noch nicht ausprobiert. Die ausführliche Anleitung: [wolfmix-anleitung.de.md](wolfmix-anleitung.de.md).

Der Wolfmix hat selbst kein Netzwerk (nur USB und DMX). Das Tempo kommt über die Hersteller-App **WTOOLS** auf dem Computer,
an dem der Wolfmix per USB hängt. LJ Link spricht mit WTOOLS über **OS2L** ("Open Sound to Light", so macht es auch VirtualDJ)
und schickt nicht nur das Tempo, sondern **jeden Beat** mit Beat-Nummer. Der Takt (jeder vierte Beat ist die Eins) stimmt mit
dem Beatgrid des Players überein.

1. Wolfmix per USB an einen Computer anschließen, **WTOOLS** starten und dort **OS2L** einschalten.
2. In LJ Link: Menü (☰) → Lichtpult-Link → **Wolfmix (OS2L, WTOOLS) – Beta**. Adresse `127.0.0.1`, wenn WTOOLS auf demselben
   Computer läuft wie LJ Link, sonst die Adresse des WTOOLS-Computers. Port `5000` wie im Wolfmix-Handbuch; ein Beitrag im
   VirtualDJ-Forum nennt `9996`. Kommt nichts an, den OS2L-Port in WTOOLS ansehen und eintragen. Speichern.
3. **Test senden** schickt acht Beats mit 120 BPM. Am Wolfmix im FX-Rack den **ersten Drehregler drücken**, damit die Effekte
   dem BPM folgen.
4. Es wird jeweils das Deck als Takt benutzt, das unter SEND BPM gewählt ist (Auto: Master).

Verhalten: Steht das Deck, werden keine Beats gesendet. Springt das Deck (Cue, Loop, Track-Wechsel), meldet LJ Link
`change`, damit das Lichtprogramm die Phase neu sucht. Bei Serato und Ableton Link gibt es keine Position; dort läuft der Takt
frei mit dem Tempo des Tracks. Läuft WTOOLS nicht, zeigt LJ Link das im Status und versucht es alle 5 Sekunden neu.
Die Demo sendet nur, wenn **Demo-Daten senden** angehakt ist.

Das geht auch mit anderen Programmen, die OS2L empfangen (z. B. QLC+ mit dem OS2L-Plugin); dort Port und Adresse des Programms
eintragen.

---

## 9. Fehlersuche

| Was du siehst | Ursache | Lösung |
|---|---|---|
| „Tempo senden ist ausgeschaltet“ | Kein Pult gewählt | Unter *Lichtpult-Link* ein Pult wählen und speichern |
| „Warte auf ein Deck mit Tempo …“ | Kein Deck spielt / hat einen Track | Track laden, oder unter SEND BPM ein Deck wählen |
| „Das gewählte Deck hat gerade kein Tempo“ | Unter SEND BPM ist ein festes Deck gewählt, das leer ist | Track laden oder auf *Auto* stellen |
| „Demo-Modus: Es wird nichts ans Pult gesendet“ | Demo läuft | Mit echten Geräten suchen |
| „Sending to …:30000 failed: Connection refused“ (grandMA2) | Telnet ist aus, oder falsche IP | 4.1: *Login Enabled*; IP prüfen |
| grandMA2: es kommt nichts an, keine Fehlermeldung | Benutzer/Passwort falsch (Groß-/Kleinschreibung!) | Benutzer in der Show prüfen, Show gespeichert? |
| grandMA3: *Enable Input* leuchtet nie gelb | Falscher Port, falsches Interface, Firewall | Port gleich? Interface richtig? Firewall erlauben |
| grandMA3: gelb, aber nichts passiert | *Receive Command* steht auf `No`, oder Prefix gesetzt | 5.1: `Receive Command = Yes`, Prefix leer |
| Speedmaster ändert sich, Effekt nicht | Effekt keinem Speedmaster zugeordnet | Effekt dem Speedmaster zuweisen |
| „Pult suchen“ findet nichts | grandMA3 (wird nicht gesucht), anderes Netz, Pult aus | IP von Hand eintragen; Netz prüfen (Kapitel 2) |
| Tempo springt zwischen zwei Werten | Auto wechselt zwischen zwei Mastern | Unter SEND BPM ein festes Deck wählen |

## 10. Was ist geprüft, was nicht?

Ehrlich und vollständig, Stand dieser Version:

| Punkt | Stand |
|---|---|
| Suche findet grandMA2 onPC im Netz (auch mit ausgeschalteter Fernsteuerung) | an einem echten grandMA2 onPC geprüft |
| Telnet-Login und `SpecialMaster 3.16 At {bpm}` (ganze BPM) beim grandMA2 onPC | an einem echten grandMA2 onPC geprüft: Login klappt, der Befehl wird ausgeführt |
| OSC `/cmd` und Einstellungen beim grandMA3 | aus dem grandMA3-Handbuch, noch nicht am echten Pult geprüft |
| Befehl `Master 3.1 At BPM {bpm}` beim grandMA3 | aus dem grandMA3-Handbuch |
| dot2 | nicht möglich (siehe Kapitel 6) |
| Daslight per OSC | **nicht bestätigt** (siehe Kapitel 7) |
| Wolfmix per Ableton Link oder OS2L (Kapitel 8a) | **Beta:** an einem Wolfmix W1 ausprobiert, Ableton Link lief besser als OS2L. Die Korrekturen aus 1.7.3 und 1.7.4 sind am Wolfmix noch nicht ausprobiert. Ob WTOOLS Verbindungen von einem anderen Computer annimmt, ist offen |

Wenn du es an deinem Pult ausprobiert hast, erzähl es uns gerne über ein Issue auf GitHub (Vorlage *Hardware report*). Dann
wird diese Tabelle aktualisiert.

Quellen: grandMA2-Hilfe (Remote Control über Telnet, Keyword Login, Special Masters), grandMA3-Handbuch (Remote Inputs → OSC,
Keyword BPM/Master), dot2-Handbuch (Remote Inputs, Speed), Daslight-5-Produktbeschreibung.
