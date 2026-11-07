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
  "LJ Link shows live which track is playing on every player and where it is.": "LJ Link zeigt live, welcher Track auf jedem Player läuft und wo er gerade steht.",
  "This view is read-only. To start, open LJ Link on the computer itself.": "Diese Ansicht ist nur zum Zuschauen. Zum Starten bitte LJ Link direkt am Computer öffnen.",
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
  "Searching for Pioneer CDJs (Pro DJ Link) …": "Suche nach Pioneer CDJs (Pro DJ Link) …",
  "The Pioneer search could not start: another program on this computer uses Pro DJ Link (rekordbox, Beat Link Trigger …). Close it and search again.": "Die Pioneer-Suche konnte nicht starten: Ein anderes Programm auf diesem Computer nutzt Pro DJ Link (rekordbox, Beat Link Trigger …). Bitte schließen und erneut suchen.",
  "Device found – connecting …": "Gerät gefunden – verbinde …",
  "OSC address of the tap (several: separate by comma)": "OSC-Adresse des Taps (mehrere: mit Komma trennen)",
  "ALPHA, UNTESTED: never tried on a real MagicQ, do not rely on it yet. ChamSys MagicQ has no OSC address for a BPM, so LJ Link sends a tap (an OSC message without value) on every beat of the leading deck. On the desk: Setup, View Settings, Network: OSC Mode Rx, OSC Rx Port as here. Then in the Macro window, Autom view, add a row of type OSC Message with this address (up to 15 characters) and the action Tap to Time (or Global Tap to Time). See the ChamSys guide, and please report how it went.": "ALPHA, UNGETESTET: noch nie an einem echten MagicQ ausprobiert, nicht darauf verlassen. ChamSys MagicQ hat keine OSC-Adresse für ein BPM, deshalb sendet LJ Link auf jedem Beat des führenden Decks einen Tap (eine OSC-Nachricht ohne Wert). Am Pult: Setup, View Settings, Network: OSC Mode Rx, OSC Rx Port wie hier. Dann im Macro-Fenster, Ansicht Autom, eine Zeile vom Typ OSC Message mit dieser Adresse (bis zu 15 Zeichen) und der Aktion Tap to Time (oder Global Tap to Time) anlegen. Siehe ChamSys-Anleitung und melde uns bitte, wie es lief.",
  "Lead the Link session with the DJ tempo": "Link-Session mit dem DJ-Tempo führen",
  "Also align beats and bars to the deck": "Auch Beats und Takte am Deck ausrichten",
  "Every program with Ableton Link in the network (Ableton Live, Resolume, Traktor, apps …) then runs at the tempo of the deck chosen in SEND BPM (Auto: the master). Beats and bars follow the beat grid of Pioneer and Denon decks. Switch Link on in the other program too.": "Jedes Programm mit Ableton Link im Netzwerk (Ableton Live, Resolume, Traktor, Apps …) läuft dann im Tempo des Decks, das bei SEND BPM gewählt ist (Auto: der Master). Beats und Takte folgen dem Beatgrid von Pioneer- und Denon-Decks. Im anderen Programm Link ebenfalls einschalten.",
  "1 other program": "1 anderes Programm",
  "{n} other programs": "{n} andere Programme",
  "Session: {bpm} BPM, {peers}": "Session: {bpm} BPM, {peers}",
  "No deck with a tempo: the Link session keeps its tempo.": "Kein Deck mit Tempo: Die Link-Session behält ihr Tempo.",
  "Demo mode: the Link session is not led.": "Demo-Modus: Die Link-Session wird nicht geführt.",
  "SEND BPM takes the Link session itself: choose a DJ deck to lead Link.": "SEND BPM nimmt die Link-Session selbst: Wähle ein DJ-Deck, das Link führen soll.",
  "Another program on this computer keeps taking over the Pioneer connection (a second LJ Link, Beat Link Trigger, rekordbox …). Close it and search again.": "Ein anderes Programm auf diesem Computer übernimmt immer wieder die Pioneer-Verbindung (ein zweites LJ Link, Beat Link Trigger, rekordbox …). Schließe es und suche erneut.",
  "Another device on the network keeps taking LJ Link's player number. Check that no second LJ Link or Beat Link program is running, then search again.": "Ein anderes Gerät im Netzwerk nimmt LJ Link immer wieder die Player-Nummer weg. Prüfe, dass kein zweites LJ Link oder Beat-Link-Programm läuft, und suche dann erneut.",
  "Another program on this computer uses Pro DJ Link (a second LJ Link, Beat Link Trigger, rekordbox …). Reconnecting …": "Ein anderes Programm auf diesem Computer nutzt Pro DJ Link (ein zweites LJ Link, Beat Link Trigger, rekordbox …). Verbinde neu …",
  "The connection to the players was taken over. Reconnecting …": "Die Verbindung zu den Playern wurde übernommen. Verbinde neu …",
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
  "Finds Pioneer and Denon devices at the same time. Devices that are switched on later are added by themselves.": "Findet Pioneer- und Denon-Geräte gleichzeitig. Geräte, die später eingeschaltet werden, kommen von selbst dazu.",
  "Connect": "Verbinden",
  "Computer, players and mixer on the same network switch, with cables.": "Computer, Player und Mixer am selben Netzwerk-Switch, per Kabel.",
  "Search": "Suchen",
  "One button finds every player, from any maker.": "Ein Knopf findet jeden Player, egal von welchem Hersteller.",
  "Watch": "Zuschauen",
  "Here or on a tablet in the same network: every deck, every waveform, live.": "Hier oder auf einem Tablet im selben Netzwerk: jedes Deck, jede Waveform, live.",
  "Works with": "Funktioniert mit",
  "Light desks": "Lichtpulte",
  "Pro DJ Link · CDJ, XDJ, DJM": "Pro DJ Link · CDJ, XDJ, DJM",
  "Tempo to grandMA, taps to Daslight": "Tempo an grandMA, Taps an Daslight",
  "Serato and Traktor are planned.": "Serato und Traktor sind geplant.",
  "Version {v}": "Version {v}",
  "Version {v} is available – install it from the program icon (menu bar or next to the clock).": "Version {v} ist verfügbar – installieren über das Programm-Symbol (Menüleiste bzw. neben der Uhr).",
  "Send BPM": "BPM senden",
  "Auto takes the tempo master; without a master the deck that is on air and playing. Manual always takes the chosen deck, even when another one is live. Fixed value is a deck of its own that sends the tempo you type in, for tests or when no DJ gear is connected.": "Auto nimmt den Tempo-Master, ohne Master das Deck, das auf Sendung ist und spielt. Manuell nimmt immer das gewählte Deck, auch wenn ein anderes live ist. Der Festwert ist ein eigenes Deck, das das eingetippte Tempo sendet, für Tests oder wenn keine DJ-Technik angeschlossen ist.",
  "Light desk settings": "Lichtpult-Einstellungen",
  "This can only be set on the computer that runs LJ Link.": "Das lässt sich nur am Computer einstellen, auf dem LJ Link läuft.",
  "Sending the tempo is switched off. Choose a light desk first.": "Tempo senden ist ausgeschaltet. Wähle zuerst ein Lichtpult.",
  "Sending {bpm} BPM from {deck}": "Sende {bpm} BPM von {deck}",
  "Waiting for a deck with a tempo …": "Warte auf ein Deck mit Tempo …",
  "The chosen deck has no tempo right now, nothing is sent.": "Das gewählte Deck hat gerade kein Tempo, es wird nichts gesendet.",
  "Auto": "Auto",
  "Report a problem": "Problem melden",
  "Known problems and solutions": "Bekannte Probleme und Lösungen",
  "Describe what happens and what you expected. Which devices, what you did, what you see.": "Beschreibe, was passiert und was du erwartet hättest: welche Geräte, was du gemacht hast, was du siehst.",
  "Include diagnostic data": "Diagnosedaten mitsenden",
  "Program and system version, network adapters, the devices that were found and their connection state, what each deck receives (waveform, beat grid, cues) and the last log lines. No track titles, no covers, no passwords.": "Programm- und Systemversion, Netzwerkadapter, gefundene Geräte und ihr Verbindungsstatus, was jedes Deck empfängt (Waveform, Beatgrid, Cues) und die letzten Log-Zeilen. Keine Tracktitel, keine Cover, keine Passwörter.",
  "Show diagnostic data": "Diagnosedaten ansehen",
  "Report on GitHub": "Auf GitHub melden",
  "Save as file": "Als Datei speichern",
  "Copy": "Kopieren",
  "Close": "Schließen",
  "Loading …": "Lade …",
  "Diagnostic data can only be added on the computer that runs LJ Link.": "Diagnosedaten lassen sich nur am Computer anhängen, auf dem LJ Link läuft.",
  "(no description)": "(keine Beschreibung)",
  "Copied.": "Kopiert.",
  "Copying is not allowed here; use “Save as file”.": "Kopieren ist hier nicht erlaubt; nutze „Als Datei speichern“.",
  "Saved. Attach the file to your report or send it to us.": "Gespeichert. Hänge die Datei an deine Meldung an oder schick sie uns.",
  "Please describe the problem first.": "Beschreibe bitte zuerst das Problem.",
  "The diagnostic data is in the clipboard: paste it into the issue (Ctrl/Cmd+V).": "Die Diagnosedaten sind in der Zwischenablage: füge sie in die Meldung ein (Strg/Cmd+V).",
  "Please also attach the file from “Save as file”.": "Hänge bitte zusätzlich die Datei aus „Als Datei speichern“ an.",
  "A GitHub account is needed to send it. Without one: “Send by email”.": "Zum Absenden brauchst du ein GitHub-Konto. Ohne Konto: „Per E-Mail senden“.",
  "Send by email": "Per E-Mail senden",
  "The diagnostic data is attached as a file (LJ-Link-report-….txt from the downloads folder).": "Die Diagnosedaten hängen als Datei an (LJ-Link-report-….txt aus dem Download-Ordner).",
  "The diagnostic data was saved as a file: attach it to the mail (it is also in the clipboard).": "Die Diagnosedaten wurden als Datei gespeichert: Hänge sie an die Mail an (sie sind auch in der Zwischenablage).",
  "Your mail program opens with the report to {mail}. If nothing opens, send it by hand to that address.": "Dein Mailprogramm öffnet sich mit der Meldung an {mail}. Öffnet sich nichts, schick sie von Hand an diese Adresse.",
  "Example: The CDJ-3000 shows up twice and says “No track loaded”, although a track is playing. Windows 11, cable to the switch.": "Beispiel: Der CDJ-3000 erscheint doppelt und zeigt „Kein Track geladen“, obwohl ein Track läuft. Windows 11, Kabel am Switch.",
  "Send to speed master": "An Speedmaster senden",
  "Speed 16 (BPM)": "Speed 16 (BPM)",
  "Speed {n}": "Speed {n}",
  "grandMA2 console or onPC, over Telnet. On the desk: Setup, Console, Global Settings, Telnet: Login Enabled. Default user of every show: administrator, password admin (change it if your show uses another). {bpm} is replaced by the tempo. Step by step: see the light desk guide.": "grandMA2-Pult oder onPC, über Telnet. Am Pult: Setup, Console, Global Settings, Telnet: Login Enabled. Standard-Benutzer jeder Show: administrator, Passwort admin (ändern, falls deine Show einen anderen nutzt). {bpm} wird durch das Tempo ersetzt. Schritt für Schritt: siehe Lichtpult-Anleitung.",
  "grandMA3 console or onPC, over OSC. On the desk: Menu, In & Out, OSC: a line with this port, Mode UDP, Receive Command Yes, then Enable Input. {bpm} is replaced by the tempo. Step by step: see the light desk guide.": "grandMA3-Pult oder onPC, über OSC. Am Pult: Menu, In & Out, OSC: eine Zeile mit diesem Port, Mode UDP, Receive Command Yes, dann Enable Input. {bpm} wird durch das Tempo ersetzt. Schritt für Schritt: siehe Lichtpult-Anleitung.",
  "User of the show, e.g. administrator": "Benutzer der Show, z. B. administrator",
  "Password": "Passwort",
  "Address entered. Enter the user and press Save.": "Adresse eingetragen. Benutzer eintragen und auf Speichern drücken.",
  "Beat port 50001 is in use by another program; positions are estimated.": "Beat-Port 50001 ist von einem anderen Programm belegt; Positionen werden geschätzt.",
  "Demo mode: nothing is sent to the desk.": "Demo-Modus: Es wird nichts ans Pult gesendet.",
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
  "Wolfmix (OS2L, WTOOLS) – Alpha": "Wolfmix (OS2L, WTOOLS) – Alpha",
  "BETA: tried on a Wolfmix W1; Ableton Link is more exact. Wolfmix (WTOOLS) and other OS2L software. Start WTOOLS on the computer the Wolfmix is plugged into, switch OS2L on there and enter that computer's address (127.0.0.1 if it is this one). LJ Link sends the tempo and every beat. On the Wolfmix, push the first encoder in an FX rack so it follows the BPM. See the Wolfmix guide, and please report how it went.": "BETA: an einem Wolfmix W1 ausprobiert; Ableton Link ist genauer. Wolfmix (WTOOLS) und andere OS2L-Software. WTOOLS auf dem Computer starten, an dem der Wolfmix per USB hängt, dort OS2L einschalten und die Adresse dieses Computers eintragen (127.0.0.1, wenn es dieser hier ist). LJ Link sendet das Tempo und jeden Beat. Am Wolfmix den ersten Drehregler im FX-Rack drücken, damit er dem BPM folgt. Siehe Wolfmix-Anleitung und melde uns bitte, wie es lief.",
  "No OS2L receiver at {host}:{port} ({e}). Is the lighting program running with OS2L switched on?": "Kein OS2L-Empfänger unter {host}:{port} ({e}). Läuft das Lichtprogramm (WTOOLS) und ist OS2L eingeschaltet?",
  "IP address of the desk, e.g. 192.168.1.50": "IP des Pults, z. B. 192.168.1.50",
  "Save": "Speichern",
  "Send test (120 BPM)": "Test senden (120 BPM)",
  "Daslight (Ableton Link) – recommended": "Daslight (Ableton Link) – empfohlen",
  "Daslight (OSC tap)": "Daslight (OSC-Tap)",
  "Wolfmix (Ableton Link) – recommended": "Wolfmix (Ableton Link) – empfohlen",
  "Wolfmix (OS2L, WTOOLS) – Beta": "Wolfmix (OS2L, WTOOLS) – Beta",
  "Recommended for Wolfmix: LJ Link leads the Ableton Link session with the tempo, beats and bars of the deck chosen in SEND BPM, and switches Ableton Link on below if it is off. On the Wolfmix side choose Ableton Link as the BPM source (in WTOOLS). Both computers must be in the same network. See the Wolfmix guide.": "Empfohlen für Wolfmix: LJ Link führt die Ableton-Link-Session mit Tempo, Beats und Takten des in SEND BPM gewählten Decks und schaltet Ableton Link unten ein, falls es aus ist. Auf der Wolfmix-Seite Ableton Link als BPM-Quelle wählen (in WTOOLS). Beide Rechner müssen im selben Netzwerk sein. Siehe Wolfmix-Anleitung.",
  "Recommended for Daslight: LJ Link leads the Ableton Link session with the tempo, beats and bars of the deck chosen in SEND BPM, and switches Ableton Link on below if it is off. In Daslight: Settings, MIDI / Audio / BPM, BPM source Ableton Link Sync. Both computers must be in the same network. See the Daslight guide.": "Empfohlen für Daslight: LJ Link führt die Ableton-Link-Session mit Tempo, Beats und Takten des in SEND BPM gewählten Decks und schaltet Ableton Link unten ein, falls es aus ist. In Daslight: Settings, MIDI / Audio / BPM, BPM Source Ableton Link Sync. Beide Rechner müssen im selben Netzwerk sein. Siehe Daslight-Anleitung.",
  "Daslight cannot take the BPM as a number over OSC, so LJ Link sends a tap (an OSC message with the value 255, then 0) on every beat of the leading deck. In Daslight: Settings, OSC, OSC Input on, Incoming Port as here; then map this address to BPM Tap (target Global) in the OSC mappings. Ableton Link is more exact, see the Daslight guide.": "Daslight kann das BPM nicht als Zahl über OSC annehmen, deshalb sendet LJ Link auf jedem Beat des führenden Decks einen Tap (eine OSC-Nachricht mit dem Wert 255, danach 0). In Daslight: Settings, OSC, OSC Input an, Incoming Port wie hier; dann diese Adresse in den OSC Mappings auf BPM Tap (Target Global) legen. Ableton Link ist genauer, siehe Daslight-Anleitung.",
  "The tempo goes out over Ableton Link, see the Ableton Link section below.": "Das Tempo geht über Ableton Link raus, siehe Abschnitt Ableton Link unten.",
  "Ableton Link is switched off: switch it on below, or press Save.": "Ableton Link ist aus: unten einschalten oder Speichern drücken.",
  "Fixed value": "Festwert",
  "Fixed value in BPM": "Festwert in BPM",
  "Send demo data (the tempos of the demo decks go to the desk)": "Demo-Daten senden (die Tempos der Demo-Decks gehen an das Pult)",
  "Done": "Fertig",
  "About LJ Link": "Über LJ Link",
  "© 2026 Philipp Spruck / NEXTPHILM · Eclipse Public License 2.0. Not an official product of AlphaTheta / Pioneer DJ, inMusic / Denon DJ, MA Lighting, Daslight, Serato or Ableton; all trademarks belong to their owners.": "© 2026 Philipp Spruck / NEXTPHILM · Eclipse Public License 2.0. Kein offizielles Produkt von AlphaTheta / Pioneer DJ, inMusic / Denon DJ, MA Lighting, Daslight, Serato oder Ableton; alle Marken gehören ihren Inhabern.",
  "Licenses": "Lizenzen",
  "Disconnect": "Verbindung trennen",
  "Close": "Schließen",
  "· DETAIL": "· DETAIL",
  "· OVERVIEW": "· ÜBERSICHT",
  "Command on the desk": "Befehl am Pult",
  "OSC address": "OSC-Adresse",
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
  "Sent to light desks: {bpm} BPM": "An Lichtpulte gesendet: {bpm} BPM",
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
      } else if (/^No OS2L receiver at /.test(text)) {
        const e = text.match(/^No OS2L receiver at (.*):(\d+) \((.*)\)\. Is the lighting/);
        if (e) out = t("No OS2L receiver at {host}:{port} ({e}). Is the lighting program running with OS2L switched on?", { host: e[1], port: e[2], e: e[3] });
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
const SKIP = "#decks, #info-body, #mixer-view, #found-list, #adapters, #check-result, #link-status, #abl-status, script, style";

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
