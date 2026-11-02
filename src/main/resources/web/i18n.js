"use strict";

/*
 * Languages: English is the source language (all texts in the page and in the server messages are English) and the
 * default. German is a translation: DE maps an English text to its German text. Texts that are not in DE are shown as is.
 * The language is chosen per browser (localStorage); the program icon menu follows the one chosen on the computer itself.
 */
const DE = {
  // header and status
  "Connecting …": "Verbinde …",
  "PRIVACY": "PRIVATSPHÄRE",
  "Title, artist, album and cover are hidden": "Titel, Artist, Album und Cover sind ausgeblendet",
  "Full screen": "Vollbild",
  "Settings": "Einstellungen",
  "Zoom out": "Herauszoomen",
  "Zoom in": "Hineinzoomen",
  "Demo mode": "Demo-Modus",
  "Connected": "Verbunden",
  "Search running": "Suche läuft",
  "Error": "Fehler",
  "Ready": "Bereit",
  "Program not reachable": "Programm nicht erreichbar",
  "Connection lost – reconnecting …": "Verbindung unterbrochen – verbinde neu …",

  // start and search
  "What are the DJs playing right now?": "Was spielen die DJs gerade?",
  "LJ-Link-PRO shows live which track is playing on every player and where it is.": "LJ-Link-PRO zeigt live, welcher Track auf jedem Player läuft und wo er gerade steht.",
  "This view is read-only. To start, open LJ-Link-PRO on the computer itself.": "Diese Ansicht ist nur zum Zuschauen. Zum Starten bitte LJ-Link-PRO direkt am Computer öffnen.",
  "Search automatically": "Automatisch suchen",
  "Computer and DJ devices must be connected to the same network switch.": "Computer und DJ-Geräte müssen mit demselben Netzwerkkabel-Switch verbunden sein.",
  "Not working? Set up manually": "Klappt nicht? Manuell einrichten",
  "Watch demo (without devices)": "Demo ansehen (ohne Geräte)",
  "Searching …": "Suche läuft …",
  "Device found!": "Gerät gefunden!",
  "Nothing found yet?": "Noch nichts gefunden?",
  "Check: are the players switched on? Is the network cable plugged into the player and the switch? Is Wi-Fi turned off on the computer?": "Prüfe: Sind die Player eingeschaltet? Steckt das Netzwerkkabel im Player und im Switch? Ist das WLAN am Computer ausgeschaltet?",
  "Set up manually": "Manuell einrichten",
  "Cancel": "Abbrechen",
  "No. {n} · {address}": "Nr. {n} · {address}",
  "Searching for DJ devices on the network …": "Suche nach DJ-Geräten im Netzwerk …",
  "Device found – connecting …": "Gerät gefunden – verbinde …",
  "Connection to the DJ network failed. Please try again.": "Verbindung zum DJ-Netzwerk fehlgeschlagen. Bitte erneut versuchen.",
  "Demo mode – no real hardware": "Demo-Modus – keine echte Hardware",
  "Error: {x}": "Fehler: {x}",
  "That did not work": "Das hat nicht geklappt",
  "Try again": "Nochmal versuchen",
  "Waiting for player data …": "Warte auf Player-Daten …",

  // manual setup
  "We check step by step why the search finds nothing.": "Wir prüfen Schritt für Schritt, warum die Suche nichts findet.",
  "Which device do you have?": "Welches Gerät hast du?",
  "Pioneer DJM mixer (900/2000/V10/A9)": "Pioneer DJM-Mixer (900/2000/V10/A9)",
  "Denon Prime 4+, SC6000, Prime Go (StageLinQ)": "Denon Prime 4+, SC6000, Prime Go (StageLinQ)",
  "Devices": "Geräte",
  "Send BPM": "BPM senden",
  "Auto takes the tempo master; without a master the deck that is on air and playing. Manual always takes the chosen deck, even when another one is live.": "Auto nimmt den Tempo-Master, ohne Master das Deck, das auf Sendung ist und spielt. Manuell nimmt immer das gewählte Deck, auch wenn ein anderes live ist.",
  "Light desk settings": "Lichtpult-Einstellungen",
  "This can only be set on the computer that runs LJ-Link-PRO.": "Das lässt sich nur am Computer einstellen, auf dem LJ-Link-PRO läuft.",
  "Sending the tempo is switched off. Choose a light desk first.": "Tempo senden ist ausgeschaltet. Wähle zuerst ein Lichtpult.",
  "Sending {bpm} BPM from {deck}": "Sende {bpm} BPM von {deck}",
  "Waiting for a deck with a tempo …": "Warte auf ein Deck mit Tempo …",
  "The chosen deck has no tempo right now, nothing is sent.": "Das gewählte Deck hat gerade kein Tempo, es wird nichts gesendet.",
  "Auto": "Auto",
  "The deck that is master and/or live": "Das Deck, das Master und/oder live ist",
  "Chosen deck (not connected)": "Gewähltes Deck (nicht verbunden)",
  "playing": "spielt",
  "no track": "kein Track",
  "Search for the desk": "Pult suchen",
  "Searching the network … (about 10 seconds)": "Suche im Netzwerk … (etwa 10 Sekunden)",
  "No desk found. Enter its IP address by hand: on the desk, OSC input must be switched on (Setup, In & Out, OSC).": "Kein Pult gefunden. Trage die IP-Adresse von Hand ein: Am Pult muss der OSC-Eingang eingeschaltet sein (Setup, In & Out, OSC).",
  "Address entered. Press Save.": "Adresse eingetragen. Auf Speichern drücken.",
  "The search failed.": "Die Suche ist fehlgeschlagen.",
  "Everything that is found is shown. Untick a device to hide its players, for example when several floors are in the network. A new device that appears later is added automatically.": "Alles, was gefunden wird, wird angezeigt. Entferne den Haken bei einem Gerät, um seine Player auszublenden, zum Beispiel bei mehreren Floors im Netzwerk. Ein Gerät, das später dazukommt, wird automatisch hinzugefügt.",
  "Search Denon devices (StageLinQ)": "Denon-Geräte suchen (StageLinQ)",
  "Searching for Denon devices (StageLinQ) …": "Suche nach Denon-Geräten (StageLinQ) …",
  "Denon device found – connecting …": "Denon-Gerät gefunden – verbinde …",
  "No waveform found for this track": "Für diesen Track wurde keine Waveform gefunden",
  "This device reports the fader positions. The EQ knobs are not transmitted.": "Dieses Gerät meldet die Fader-Stellungen. Die EQ-Regler werden nicht gesendet.",
  "Protocol": "Protokoll",
  "Channel fader": "Kanal-Fader",
  "Full support: title, waveform (also 3-band) and exact position.": "Volle Unterstützung: Titel, Waveform (auch 3-Band) und genaue Position.",
  "Full support: title, waveform and position. Tracks must come from a rekordbox USB stick or SD card.": "Volle Unterstützung: Titel, Waveform und Position. Tracks müssen von einem rekordbox-Stick/SD kommen.",
  "Should work. Not tested on real hardware yet.": "Sollte funktionieren. Noch nicht an echter Hardware getestet.",
  "Mixer: shows ON AIR per channel. It has no waveform of its own; that comes from the players.": "Mixer: zeigt ON AIR pro Kanal an. Eigene Waveform hat er nicht – die kommt von den Playern.",
  "Denon devices use StageLinQ. They are found by \"Search automatically\" together with the Pioneer devices. Title, tempo, position, the fader positions and the waveform with beat grid and cues are shown (read from the device's library).": "Denon-Geräte nutzen StageLinQ. Sie werden mit „Automatisch suchen“ zusammen mit den Pioneer-Geräten gefunden. Titel, Tempo, Position, Fader-Stellungen sowie Waveform mit Beatgrid und Cues werden angezeigt (aus der Bibliothek des Geräts gelesen).",
  "IP address of the device": "IP-Adresse des Geräts",
  "On the player: Menu → Utility → Link Info (or Network). The IP address is shown there, e.g. 192.168.1.20.": "Am Player: Menü → Utility → Link-Info (oder Netzwerk). Dort steht die IP-Adresse, z. B. 192.168.1.20.",
  "Check connection": "Verbindung prüfen",
  "This computer": "Dieser Computer",
  "Search automatically again": "Erneut automatisch suchen",
  "Back": "Zurück",
  "No active network connection found. Please check the network cable.": "Keine aktive Netzwerkverbindung gefunden. Bitte Netzwerkkabel prüfen.",
  "Open on the tablet (same Wi-Fi): ": "Auf dem Tablet öffnen (gleiches WLAN): ",
  "  or  ": "  oder  ",
  "Checking …": "Prüfe …",
  "Check not possible.": "Prüfung nicht möglich.",
  // network check (texts come from the server)
  "IP address": "IP-Adresse",
  "Network connection": "Netzwerkanschluss",
  "Same network": "Gleiches Netzwerk",
  "Device responds": "Gerät antwortet",
  "Data interface": "Datenschnittstelle",
  "Result": "Ergebnis",
  "This is not a valid IP address. It looks like this: 192.168.1.20": "Das ist keine gültige IP-Adresse. Sie sieht so aus: 192.168.1.20",
  "This computer has no active network connection. Check the cable.": "Dieser Computer hat keine aktive Netzwerkverbindung. Kabel prüfen.",
  "Computer ({0}) and device are in the same network.": "Computer ({0}) und Gerät sind im selben Netzwerk.",
  "The device has an automatic address (169.254.x.x). The computer then needs a 169.254.x.x address too, otherwise they cannot find each other. Easiest: connect the computer and the DJ devices to the same switch, set the computer's IP address to \"automatic (DHCP)\" and wait about a minute.": "Das Gerät hat eine Automatik-Adresse (169.254.x.x). Der Computer braucht dann ebenfalls eine 169.254.x.x-Adresse, sonst finden sich beide nicht. Am einfachsten: Computer und DJ-Geräte an denselben Switch, am Computer die IP-Adresse auf \"automatisch (DHCP)\" stellen und etwa eine Minute warten.",
  "The computer ({0}) is in a different network than the device. Set the computer's network connection to an address in the same range, for example {1}, and choose 255.255.255.0 as subnet mask.": "Der Computer ({0}) liegt in einem anderen Netzwerk als das Gerät. Stelle den Netzwerkanschluss des Computers auf eine Adresse im selben Bereich, zum Beispiel {1}, und wähle als Subnetzmaske 255.255.255.0.",
  "The device is reachable.": "Das Gerät ist erreichbar.",
  "The device does not respond. Is it switched on and connected with a network cable? Is the IP address correct? (On the player: Menu, Utility, Link Info)": "Das Gerät antwortet nicht. Ist es eingeschaltet und per Netzwerkkabel verbunden? Ist die IP-Adresse richtig? (Am Player: Menü, Utility, Link-Info)",
  "The player shares track information.": "Der Player gibt Track-Informationen frei.",
  "Connection possible. Press \"Search automatically again\".": "Verbindung möglich. Drücke \"Erneut automatisch suchen\".",

  // settings
  "Zoom": "Zoom",
  "– {s} s visible": "– {s} s sichtbar",
  "Seconds of the detail waveform that are visible. Smaller value = closer.": "Wie viele Sekunden der Detail-Waveform sichtbar sind. Kleiner Wert = näher dran.",
  "Waveform colors": "Waveform-Farben",
  "Blue": "Blau",
  "RGB shows the colors of the player (bass, mids, highs). Blue is the classic look.": "RGB zeigt die Farben des Players (Bass, Mitten, Höhen). Blau ist die klassische Ansicht.",
  "Display": "Anzeige",
  "Day": "Tag",
  "Night": "Nacht",
  "Language": "Sprache",
  "Privacy mode": "Privatsphäre-Modus",
  "On – title, artist, album and cover are hidden": "An – Titel, Artist, Album und Cover sind ausgeblendet",
  "Off": "Aus",
  "Hides title, artist, album and cover. It can only be changed with the program icon: menu bar (Mac) or taskbar next to the clock (Windows) → click the icon → tick “Privacy mode”. It cannot be changed in the web interface, not even from a tablet.": "Blendet Titel, Artist, Album und Cover aus. Das lässt sich nur über das Programm-Symbol ändern: Menüleiste (Mac) bzw. Taskleiste neben der Uhr (Windows) → Symbol anklicken → Haken bei „Privatsphäre-Modus“. Über die Weboberfläche ist keine Änderung möglich, auch nicht vom Tablet.",
  "Light desk link (send tempo)": "Lichtpult-Link (Tempo senden)",
  "Custom OSC target": "Eigenes OSC-Ziel",
  "IP address of the desk, e.g. 192.168.1.50": "IP des Pults, z. B. 192.168.1.50",
  "Save": "Speichern",
  "Send test (120 BPM)": "Test senden (120 BPM)",
  "Done": "Fertig",
  "Disconnect": "Verbindung trennen",
  "Close": "Schließen",
  "· DETAIL": "· DETAIL",
  "· OVERVIEW": "· ÜBERSICHT",
  "Command on the desk": "Befehl am Pult",
  "OSC address": "OSC-Adresse",
  "Applies to GrandMA2, GrandMA3 and onPC (for onPC on this computer: 127.0.0.1). MA2: switch on the OSC input under Setup > Network > Remote Inputs. MA3: create an OSC entry under In & Out and enable receiving. {bpm} is replaced by the tempo.": "Gilt für GrandMA2, GrandMA3 und onPC (bei onPC auf diesem Computer: 127.0.0.1). MA2: OSC-Eingang unter Setup > Network > Remote Inputs aktivieren. MA3: OSC-Eintrag unter In & Out anlegen und Empfangen aktivieren. {bpm} wird durch das Tempo ersetzt.",
  "In Daslight, switch on the OSC input and assign this address to a fader or speed. The tempo is sent as a number.": "In Daslight den OSC-Eingang aktivieren und diese Adresse einem Regler oder Speed zuordnen. Gesendet wird das Tempo als Zahl.",
  "The tempo is sent as a number (float) to this address.": "Das Tempo wird als Zahl (float) an diese Adresse gesendet.",
  "Last sent: {bpm} BPM ({n} messages)": "Zuletzt gesendet: {bpm} BPM ({n} Nachrichten)",
  "Sending to {host}:{port} failed: {e}": "Senden an {host}:{port} fehlgeschlagen: {e}",

  // decks
  "REMAIN": "REST",
  "Title hidden": "Titel ausgeblendet",
  "Loading track …": "Lade Track …",
  "No track loaded": "Kein Track geladen",
  "Channel fader {pct} %": "Kanal-Fader {pct} %",
  "Channel is on air (Pro DJ Link does not transmit the fader position)": "Kanal ist auf Sendung (die Fader-Stellung überträgt Pro DJ Link nicht)",
  "Channel not on air": "Kanal nicht auf Sendung",
  "Waveform loading … (not available for streaming tracks)": "Waveform wird geladen … (nicht verfügbar bei Streaming-Tracks)",

  // mixer
  "No mixer found": "Kein Mixer gefunden",
  "The mixer appears on the network as soon as it is connected to the switch with a cable.": "Der Mixer meldet sich im Netzwerk, sobald er per Kabel mit dem Switch verbunden ist.",
  "No. {n}": "Nr. {n}",
  "Tempo master": "Tempo-Master",
  "SIMULATED": "SIMULIERT",
  "Pro DJ Link only transmits whether a channel is on air (ON AIR). The position of the faders and the EQ knobs are not sent and cannot be shown here.": "Pro DJ Link überträgt vom Mixer nur, ob ein Kanal auf Sendung ist (ON AIR). Die Stellung der Fader und die EQ-Regler werden nicht gesendet und können hier nicht angezeigt werden.",
  "Demo mode: faders and knobs are simulated.": "Demo-Modus: Fader und Regler sind simuliert.",
  "no player": "kein Player",

  // detail view (labels and values come from the server)
  "No data available.": "Keine Daten verfügbar.",
  "(hidden for the docs)": "(für die Doku ausgeblendet)",
  "no connection": "keine Verbindung",
  "hidden (privacy mode)": "ausgeblendet (Privatsphäre-Modus)",
  "yes": "ja", "no": "nein", "none": "keine", "unknown": "unbekannt", "not available": "nicht verfügbar",
  "inserted": "eingesteckt", "being removed": "wird entfernt", "empty": "leer", "asleep": "schläft",
  "Device": "Gerät", "Name": "Name", "Player number": "Player-Nummer", "MAC address": "MAC-Adresse",
  "Devices on the network (peers)": "Geräte im Netzwerk (Peers)", "Firmware": "Firmware",
  "Pre-Nexus model": "Vor-Nexus-Modell", "Last packet": "Letztes Paket", "Packet size": "Paketgröße",
  "Status": "Status", "Play state 1": "Wiedergabe-Status 1", "Play state 2": "Wiedergabe-Status 2", "Play state 3": "Wiedergabe-Status 3",
  "Playing": "Spielt", "Paused": "Pausiert", "At cue point": "Am Cue-Punkt", "Searching (jog/search)": "Sucht (Jog/Search)",
  "At end of track": "Am Trackende", "Reverse": "Rückwärts", "Vinyl mode": "Vinyl-Modus", "CDJ mode": "CDJ-Modus",
  "Busy (loading)": "Beschäftigt (lädt)", "Track loaded": "Track geladen", "ON AIR (from mixer)": "ON AIR (vom Mixer)",
  "Tempo and sync": "Tempo und Sync", "Track BPM": "Track-BPM", "Pitch": "Pitch", "Effective BPM": "Effektive BPM",
  "Master handoff to": "Master-Übergabe an", "Sync active": "Sync aktiv", "BPM-only sync": "Nur BPM synchronisiert",
  "Sync counter": "Sync-Zähler", "Beat number": "Beat-Nummer", "Beat in bar": "Beat im Takt",
  "Beats to next cue": "Beats bis zum nächsten Cue", "Packet counter": "Paketzähler",
  "Loop": "Loop", "Loop active": "Loop aktiv", "Loop start": "Loop-Start", "Loop end": "Loop-Ende", "Loop length": "Loop-Länge",
  "Media and source": "Medium und Quelle", "Track type": "Track-Typ", "Source: player": "Quelle: Player", "Source: slot": "Quelle: Slot",
  "Rekordbox ID": "Rekordbox-ID", "Track number": "Track-Nummer", "USB (local)": "USB (lokal)", "SD (local)": "SD (lokal)",
  "Link media available": "Link-Medium verfügbar", "CD slot": "CD-Slot",
  "Media: name": "Medium: Name", "Media: created": "Medium: Erstellt", "Media: tracks": "Medium: Tracks",
  "Media: playlists": "Medium: Playlists", "Media: size": "Medium: Größe", "Media: free": "Medium: Frei",
  "Media: my settings": "Medium: Eigene Einstellungen",
  "Track": "Track", "Metadata": "Metadaten", "Title": "Titel", "Artist": "Artist", "Album": "Album", "Genre": "Genre",
  "Label": "Label", "Remixer": "Remixer", "Original artist": "Original-Artist", "Key": "Key", "BPM (analysis)": "BPM (Analyse)",
  "Duration": "Dauer", "Bitrate": "Bitrate", "Year": "Jahr", "Added": "Hinzugefügt", "Rating": "Bewertung", "Color": "Farbe",
  "Comment": "Kommentar",
  "Position": "Position", "Current position": "Aktuelle Position", "Position source": "Positionsquelle",
  "exact position (CDJ-3000)": "genaue Position (CDJ-3000)", "beat grid calculation": "Beatgrid-Berechnung",
  "Position definitive": "Position eindeutig", "Beat number (position)": "Beat-Nummer (Position)", "Playback speed": "Wiedergabegeschwindigkeit",
  "Cues and loops": "Cues und Loops", "Entries": "Einträge", "Hot cue": "Hot Cue", "Memory point": "Memory Point",
  "Analysis data": "Analyse-Daten", "Waveform preview": "Waveform-Vorschau", "Detail waveform": "Detail-Waveform",
  "Beat grid": "Beatgrid", "Cover": "Cover",
  "Raw data (status packet, hex)": "Rohdaten (Statuspaket, hex)", "Packet": "Paket", "Mixer": "Mixer",
  "Section not readable": "Abschnitt nicht lesbar", "Note": "Hinweis",
  "Demo mode: simulated data, no real hardware": "Demo-Modus: simulierte Daten, keine echte Hardware",
};

let lang = "en";
try { if (localStorage.getItem("ljlinkpro.lang") === "de") lang = "de"; } catch (e) { /* default English */ }

/** Translates an English text (and fills {name} or {0} placeholders). */
function t(text, vars) {
  let out = text;
  if (lang === "de") {
    out = DE[text];
    if (out === undefined) {
      out = text;
      // texts with a variable part, e.g. "Hot cue A", "Player 3" or an error message
      const m = text.match(/^(Hot cue) ([A-H])(.*)$/);
      if (m) out = (DE[m[1]] || m[1]) + " " + m[2] + m[3];
      else if (/^Error: /.test(text)) out = t("Error: {x}", { x: text.slice(7) });
      else if (/^Sending to .* failed: /.test(text)) {
        const e = text.match(/^Sending to (.*):(\d+) failed: (.*)$/);
        if (e) out = t("Sending to {host}:{port} failed: {e}", { host: e[1], port: e[2], e: e[3] });
      } else if (text.startsWith("Player ")) out = text;
      else if (text.includes(" – loop until ")) out = text.replace(" – loop until ", " – Loop bis ");
      else if (/^CD with \d+ tracks$/.test(text)) out = text.replace("CD with ", "CD mit ").replace(" tracks", " Tracks");
      else if (/^\d+ ms ago$/.test(text)) out = "vor " + text.replace(" ms ago", " ms");
      else if (/\b(bytes|segments|beats|tracks|frames)\b/.test(text)) {
        // units in values such as "512 bytes", "1198 segments", "711 beats"
        out = text.replace(/\bbytes\b/, "Bytes").replace(/\bsegments\b/, "Segmente").replace(/\bbeats\b/, "Beats")
          .replace(/\btracks\b/, "Tracks").replace(/\bframes\b/, "Frames");
      }
    }
  }
  if (vars) {
    out = out.replace(/\{(\w+)\}/g, (m, k) => (vars[k] !== undefined ? vars[k] : m));
  }
  return out;
}

const norm = (s) => s.replace(/\s+/g, " ").trim();
const ATTRS = ["title", "placeholder", "aria-label"];
// Parts of the page whose texts are generated by the program and already translated with t().
const SKIP = "#decks, #info-body, #mixer-view, #found-list, #adapters, #check-result, #link-status, script, style";

/** Translates the static texts of the page (text nodes and some attributes), and back to English. */
function translateStatic() {
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  const nodes = [];
  while (walker.nextNode()) nodes.push(walker.currentNode);
  for (const node of nodes) {
    if (node.parentElement && node.parentElement.closest(SKIP)) continue;
    if (node.__en === undefined) {
      if (!norm(node.nodeValue)) continue;
      node.__en = node.nodeValue;
    }
    const key = norm(node.__en);
    const lead = node.__en.match(/^\s*/)[0];
    const trail = node.__en.match(/\s*$/)[0];
    node.nodeValue = lang === "de" ? lead + (DE[key] || key) + trail : node.__en;
  }
  for (const el of document.querySelectorAll("[title], [placeholder], [aria-label]")) {
    if (el.closest(SKIP)) continue;
    for (const a of ATTRS) {
      if (!el.hasAttribute(a)) continue;
      const stash = "data-en-" + a;
      if (!el.hasAttribute(stash)) el.setAttribute(stash, el.getAttribute(a));
      const en = el.getAttribute(stash);
      el.setAttribute(a, lang === "de" ? (DE[norm(en)] || en) : en);
    }
  }
  document.documentElement.lang = lang;
}
