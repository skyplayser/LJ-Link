from wikilib import *

group("Help", "Hilfe")


def SYM(id_, en, de, cause_fix):
    """A symptom with a table of causes and fixes."""
    return DETAILS(en, de, TABLE([("Cause", "Ursache"), ("Fix", "Lösung")], cause_fix), id_=id_)


@page("wiki-troubleshooting.html", "Troubleshooting", "Fehlersuche")
def _():
    body = SEC(
        ADM("tip", ("Work from top to bottom: most problems are the network. Click a symptom to open it.", "Von oben nach unten vorgehen: Die meisten Probleme liegen am Netzwerk. Ein Symptom anklicken, um es zu öffnen.")),
        H2("Finding devices", "Geräte finden"),
        SYM("nothing-found", "Nothing is found", "Es wird nichts gefunden", [
            [("macOS: local network not allowed", "macOS: lokales Netzwerk nicht erlaubt"), ("System Settings → Privacy & Security → **Local Network** → LJ Link on", "Systemeinstellungen → Datenschutz & Sicherheit → **Lokales Netzwerk** → LJ Link an")],
            [("Windows: firewall, or network set to Public", "Windows: Firewall, oder Netz auf Öffentlich"), ("allow LJ Link for private networks, profile **Private**", "LJ Link für private Netzwerke zulassen, Profil **Privat**")],
            [("not in the same network", "nicht im selben Netz"), ("everything on one switch or router; see [basics](wiki-network.html)", "alles an einem Switch oder Router; siehe [Grundlagen](wiki-network.html)")],
            [("addresses not ready yet (no router)", "Adressen noch nicht fertig (kein Router)"), ("wait up to a minute, search again", "bis zu einer Minute warten, erneut suchen")],
            [("rekordbox, Engine DJ or SoundSwitch on this computer", "rekordbox, Engine DJ oder SoundSwitch auf diesem Computer"), ("quit them", "beenden")],
            [("Pioneer over Wi-Fi", "Pioneer über WLAN"), ("use a cable", "Kabel nehmen")],
            [("still unclear", "immer noch unklar"), ("**Set up manually** → **Check connection** names the reason", "**Manuell einrichten** → **Verbindung prüfen** nennt den Grund")],
        ]),
        SYM("direct-wifi", "Direct cable and Wi-Fi: players twice or not at all (known bug)", "Direktkabel und WLAN: Player doppelt oder gar nicht (bekannter Fehler)", [
            [("without a router computer and players give themselves `169.254.x.x`; with Wi-Fi on the computer may send on the wrong adapter or receive every packet twice", "ohne Router geben sich Computer und Player `169.254.x.x`; mit WLAN an sendet der Computer eventuell über den falschen Anschluss oder bekommt jedes Paket doppelt"),
             ("quit LJ Link and start it again; Wi-Fi off; or a small switch or router in between. Please [send a report](wiki-troubleshooting.html#report) while it happens", "LJ Link beenden und neu starten; WLAN aus; oder einen kleinen Switch oder Router dazwischen. Bitte [Bericht senden](wiki-troubleshooting.html#report), während es passiert")],
        ]),
        SYM("twice", "A player shows up twice", "Ein Player erscheint doppelt", [
            [("Wi-Fi and cable in the same network", "WLAN und Kabel im selben Netz"), ("Wi-Fi off", "WLAN aus")],
            [("virtual adapters (VPN, Parallels, VMware, VirtualBox, Hyper-V, Docker)", "virtuelle Adapter (VPN, Parallels, VMware, VirtualBox, Hyper-V, Docker)"), ("switch them off", "ausschalten")],
            [("two players with the same number", "zwei Player mit derselben Nummer"), ("Utility → Player No.", "Utility → Player No.")],
            [("computer `169.254.x.x`, players from a router", "Computer `169.254.x.x`, Player vom Router"), ("same switch or router as the players", "an denselben Switch oder Router wie die Player")],
        ]),
        SYM("denon", "A Denon device is not found or keeps reconnecting", "Ein Denon-Gerät wird nicht gefunden oder verbindet sich ständig neu", [
            [("LJ Link 1.3.0", "LJ Link 1.3.0"), ("update to 1.3.1 or newer", "auf 1.3.1 oder neuer aktualisieren")],
            [("Engine DJ, SoundSwitch, Resolume on this computer", "Engine DJ, SoundSwitch, Resolume auf diesem Computer"), ("close them", "schließen")],
            [("connection just broke", "Verbindung gerade abgebrochen"), ("the device needs a few seconds; wait", "das Gerät braucht ein paar Sekunden; warten")],
        ]),
        H2("Tracks and waveforms", "Tracks und Waveforms"),
        SYM("no-waveform", "“No track loaded” or no waveform", "„No track loaded“ oder keine Waveform", [
            [("LJ Link older than 1.4.1", "LJ Link älter als 1.4.1"), ("update", "aktualisieren")],
            [("track not from a rekordbox export (streaming, CD, never analysed)", "Track nicht aus einem rekordbox-Export (Streaming, CD, nie analysiert)"), ("export with rekordbox, analysed", "mit rekordbox exportieren, analysiert")],
            [("CDJ-3000 with new firmware, old export", "CDJ-3000 mit neuer Firmware, alter Export"), ("export again with rekordbox 6 or 7", "mit rekordbox 6 oder 7 neu exportieren")],
            [("rekordbox on this computer", "rekordbox auf diesem Computer"), ("quit it", "beenden")],
            [("Denon: track not analysed", "Denon: Track nicht analysiert"), ("analyse in Engine DJ or on the device", "in Engine DJ oder am Gerät analysieren")],
        ]),
        SYM("beat-port", "Yellow note “Beat port 50001 is in use”", "Gelber Hinweis „Beat port 50001 is in use“", [
            [("rekordbox, Beat Link Trigger, SoundSwitch or a second LJ Link", "rekordbox, Beat Link Trigger, SoundSwitch oder ein zweites LJ Link"), ("close it, search again; until then positions are estimated", "schließen, erneut suchen; bis dahin werden Positionen geschätzt")],
        ]),
        SYM("jumps", "The waveform jumps or stutters", "Die Waveform springt oder ruckelt", [
            [("CDJ-2000NXS2 and older report position only between beats", "CDJ-2000NXS2 und älter melden die Position nur zwischen Beats"), ("normal after a cue or loop; the CDJ-3000 is exact", "nach Cue oder Loop normal; der CDJ-3000 ist genau")],
            [("busy network", "ausgelastetes Netz"), ("own switch for the DJ gear", "eigener Switch für die DJ-Geräte")],
        ]),
        SYM("onair", "ON AIR shows the wrong deck", "ON AIR zeigt das falsche Deck", [
            [("player number and mixer channel do not match", "Player-Nummer und Mixerkanal passen nicht"), ("player 1 on channel 1, and so on", "Player 1 auf Kanal 1 und so weiter")],
            [("no sound at the master output", "kein Ton am Master-Ausgang"), ("ON AIR needs audio", "ON AIR braucht Ton")],
        ]),
        SYM("serato", "Serato decks have no playhead", "Serato-Decks haben keinen Playhead", [
            [("Serato does not reveal the play position", "Serato verrät die Abspielposition nicht"), ("a limit of Serato; track, BPM, key, waveform overview and beat grid come from the file. [Serato](wiki-serato.html)", "eine Grenze von Serato; Track, BPM, Tonart, Waveform-Übersicht und Beatgrid kommen aus der Datei. [Serato](wiki-serato.html)")],
            [("FLAC or M4A file", "FLAC- oder M4A-Datei"), ("not read yet; MP3 and AIFF work", "wird noch nicht gelesen; MP3 und AIFF gehen")],
        ]),
        H2("Light desk", "Lichtpult"),
        SYM("desk", "The desk does not react", "Das Pult reagiert nicht", [
            [("grandMA2: Telnet off", "grandMA2: Telnet aus"), ("Setup → Console → Global Settings → Telnet: Login Enabled", "Setup → Console → Global Settings → Telnet: Login Enabled")],
            [("grandMA2: Error #44", "grandMA2: Error #44"), ("user needs more rights", "Benutzer braucht mehr Rechte")],
            [("grandMA3: Receive Command off, prefix set", "grandMA3: Receive Command aus, Prefix gesetzt"), ("Receive Command Yes, prefix empty, Enable Input", "Receive Command Yes, Prefix leer, Enable Input")],
            [("effect not on that speed master", "Effekt nicht auf diesem Speedmaster"), ("assign it", "zuordnen")],
            [("desk in another network", "Pult in einem anderen Netz"), ("[network setups](wiki-network-setups.html#desk)", "[Netzwerk-Aufbauten](wiki-network-setups.html#desk)")],
            [("tempo jumps between two values", "Tempo springt zwischen zwei Werten"), ("SEND BPM on one deck", "SEND BPM auf ein Deck")],
        ]),
        H2("Program", "Programm"),
        SYM("icon", "No icon in the menu bar or next to the clock", "Kein Symbol in der Menüleiste oder neben der Uhr", [
            [("LJ Link already runs", "LJ Link läuft schon"), ("a second start only opens the interface; close the first", "ein zweiter Start öffnet nur die Oberfläche; das erste schließen")],
            [("macOS hides it", "macOS blendet es aus"), ("System Settings → Menu Bar; a full menu bar hides icons", "Systemeinstellungen → Menüleiste; eine volle Menüleiste blendet Symbole aus")],
            [("Windows hides it", "Windows blendet es aus"), ("behind the arrow next to the clock; drag it out", "hinter dem Pfeil neben der Uhr; herausziehen")],
        ]),
        SYM("update", "“Check for updates” fails", "„Nach Updates suchen“ schlägt fehl", [
            [("version 1.2.x", "Version 1.2.x"), ("install the newest version by hand once", "die neueste Version einmal von Hand installieren")],
            [("no internet", "kein Internet"), ("normal in a show network; update at home", "im Show-Netz normal; zu Hause aktualisieren")],
        ]),
        H2("Report a problem", "Ein Problem melden", "report"),
        STEPS(("Keep LJ Link open while the problem is there, gear connected.", "LJ Link offen lassen, während das Problem da ist, Geräte angeschlossen."),
              ("Menu (☰) → **Report a problem**, or in the menu of the icon.", "Menü (☰) → **Problem melden**, oder im Menü des Symbols."),
              ("Describe gear, firmware, Mac or Windows and what you see. Leave **Include diagnostic data** ticked: version, adapters, devices and their state, what each deck receives, last log lines. No titles, covers or passwords. **Show diagnostic data** shows it before sending.", "Geräte, Firmware, Mac oder Windows und das Gesehene beschreiben. **Diagnosedaten mitsenden** angehakt lassen: Version, Adapter, Geräte und ihr Zustand, was jedes Deck bekommt, letzte Log-Zeilen. Keine Titel, Cover oder Passwörter. **Diagnosedaten anzeigen** zeigt alles vor dem Senden."),
              ("**Send by email** (to fehlermelden@nextphilm.de) or **Report on GitHub**. Or ask on [Discord](" + DISCORD + ").", "**Per E-Mail senden** (an fehlermelden@nextphilm.de) oder **Auf GitHub melden**. Oder auf [Discord](" + DISCORD + ") fragen.")),
    )
    return (("Troubleshooting by symptom", "Fehlersuche nach Symptom"),
            ("LJ Link troubleshooting by symptom: nothing found, players twice, direct cable bug, no waveform, port in use, Denon reconnects, desk does not react, icon and updates, how to report.",
             "LJ-Link-Fehlersuche nach Symptom: nichts gefunden, Player doppelt, Direktkabel-Fehler, keine Waveform, Port belegt, Denon verbindet neu, Pult reagiert nicht, Symbol und Updates, wie man meldet."),
            ("Help", "Hilfe"), ("Troubleshooting", "Fehlersuche"),
            ("Find the cause by what you see.", "Finde die Ursache über das, was du siehst."), body)


@page("wiki-glossary.html", "Glossary", "Glossar")
def _():
    terms = [
        ("Pro DJ Link", "Pro DJ Link", "Pioneer’s network for CDJs, XDJs and DJM mixers. Players share tracks, beats and status with each other.", "Das Netzwerk von Pioneer für CDJs, XDJs und DJM-Mischpulte. Player teilen darüber Tracks, Beats und Status."),
        ("StageLinQ", "StageLinQ", "Denon’s network interface for Engine DJ devices, used by Engine DJ, SoundSwitch and LJ Link.", "Die Netzwerk-Schnittstelle von Denon für Engine-DJ-Geräte, genutzt von Engine DJ, SoundSwitch und LJ Link."),
        ("Ableton Link", "Ableton Link", "A protocol that keeps the tempo of programs in step over the network.", "Ein Protokoll, das das Tempo von Programmen über das Netzwerk im Gleichschritt hält."),
        ("OSC", "OSC", "Open Sound Control: short messages with an address and a value over UDP. grandMA3, Daslight, Resolume and many programs take it.", "Open Sound Control: kurze Nachrichten mit Adresse und Wert über UDP. grandMA3, Daslight, Resolume und viele Programme nehmen es an."),
        ("OS2L", "OS2L", "Open Sound to Light: beats and tempo from DJ software to light software over TCP. Used by VirtualDJ and the Wolfmix.", "Open Sound to Light: Beats und Tempo von DJ-Software an Lichtsoftware über TCP. Genutzt von VirtualDJ und dem Wolfmix."),
        ("Telnet", "Telnet", "A text connection to the command line of a device. The grandMA2 takes commands this way.", "Eine Textverbindung zur Kommandozeile eines Geräts. Der grandMA2 nimmt so Befehle an."),
        ("Speed master", "Speedmaster", "A master on the light desk that sets the speed of the effects and chasers assigned to it.", "Ein Master am Lichtpult, der die Geschwindigkeit der ihm zugeordneten Effekte und Chaser bestimmt."),
        ("SEND BPM", "SEND BPM", "The tab in LJ Link that chooses which deck gives the tempo to the desk.", "Der Reiter in LJ Link, der wählt, welches Deck das Tempo ans Pult gibt."),
        ("Master (DJ)", "Master (DJ)", "The player the others sync to. Its BPM box is orange in LJ Link.", "Der Player, auf den die anderen synchronisieren. Sein BPM-Kasten ist in LJ Link orange."),
        ("ON AIR", "ON AIR", "The mixer channel is up and sound is at the master output. Red LIVE frame in LJ Link.", "Der Mixerkanal ist offen und am Master-Ausgang ist Ton. Rote LIVE-Umrandung in LJ Link."),
        ("Beat grid", "Beatgrid", "The beats of a track, analysed by rekordbox, Engine DJ or Serato. Red ticks mark bar starts.", "Die Beats eines Tracks, analysiert von rekordbox, Engine DJ oder Serato. Rote Striche markieren Taktanfänge."),
        ("Hot cue, memory cue", "Hot Cue, Memory Cue", "Marked points in the track: hot cues A–H as flags, memory cues as red triangles.", "Markierte Stellen im Track: Hot Cues A–H als Flaggen, Memory Cues als rote Dreiecke."),
        ("Switch", "Switch", "Connects network cables to one network.", "Verbindet Netzwerkkabel zu einem Netz."),
        ("Router, DHCP", "Router, DHCP", "A router hands out addresses (DHCP) and often has Wi-Fi.", "Ein Router verteilt Adressen (DHCP) und hat oft WLAN."),
        ("Link-local, 169.254", "Link-Local, 169.254", "The address a device gives itself when there is no router.", "Die Adresse, die sich ein Gerät selbst gibt, wenn es keinen Router gibt."),
        ("Subnet mask", "Subnetzmaske", "Says which part of the address is the network. With 255.255.255.0 the first three numbers must be equal.", "Sagt, welcher Teil der Adresse das Netz ist. Mit 255.255.255.0 müssen die ersten drei Zahlen gleich sein."),
        ("127.0.0.1", "127.0.0.1", "The computer itself. For a program on the same computer.", "Der Computer selbst. Für ein Programm auf demselben Computer."),
        ("onPC", "onPC", "The light desk software of MA Lighting on a normal computer.", "Die Lichtpult-Software von MA Lighting auf einem normalen Computer."),
        ("Art-Net, sACN, MA-Net", "Art-Net, sACN, MA-Net", "Networks for lighting data (DMX over Ethernet) and MA desks. Usually kept apart from the DJ network.", "Netzwerke für Lichtdaten (DMX über Ethernet) und MA-Pulte. Meist vom DJ-Netz getrennt."),
        ("Privacy mode", "Privatsphäre-Modus", "LJ Link sends no titles, artists or covers. Switched only from the program icon.", "LJ Link sendet keine Titel, Artists oder Cover. Nur über das Programm-Symbol schaltbar."),
    ]
    rows = [[el("b", te, td), (pe, pd)] for te, td, pe, pd in terms]
    body = SEC(H2("Terms", "Begriffe"), TABLE([("Term", "Begriff"), ("Meaning", "Bedeutung")], rows))
    return (("Glossary", "Glossar"),
            ("The terms of the LJ Link wiki explained: Pro DJ Link, StageLinQ, Ableton Link, OSC, OS2L, Telnet, speed master, SEND BPM, ON AIR, DHCP, 169.254, onPC, Art-Net.",
             "Die Begriffe des LJ-Link-Wikis erklärt: Pro DJ Link, StageLinQ, Ableton Link, OSC, OS2L, Telnet, Speedmaster, SEND BPM, ON AIR, DHCP, 169.254, onPC, Art-Net."),
            ("Help", "Hilfe"), ("Glossary", "Glossar"),
            ("Every technical word of this wiki in one sentence.", "Jedes Fachwort dieses Wikis in einem Satz."), body)


@page("wiki-reference.html", "Reference", "Referenz")
def _():
    body = SEC(
        H2("Defaults", "Vorgaben"),
        TABLE([("Setting", "Einstellung"), ("Default", "Vorgabe")], [
            [("grandMA2 port, user, password", "grandMA2 Port, Benutzer, Passwort"), "<code>30000</code>, <code>administrator</code>, <code>admin</code>"],
            [("grandMA2 command", "grandMA2 Befehl"), "<code>SpecialMaster 3.16 At {bpm}</code>"],
            [("grandMA3 command", "grandMA3 Befehl"), "<code>Master 3.1 At BPM {bpm}</code>"],
            [("OSC port (grandMA3, Daslight, own)", "OSC-Port (grandMA3, Daslight, eigenes)"), "<code>8000</code>"],
            [("Daslight / own OSC address", "Daslight / eigene OSC-Adresse"), "<code>/ljlinkpro/bpm</code>"],
            [("OS2L (Wolfmix) host, port", "OS2L (Wolfmix) Host, Port"), "<code>127.0.0.1</code>, <code>5000</code>"],
            [("Send on change of", "Senden bei Änderung um"), "0.1 BPM"],
            [("Repeat", "Wiederholung"), el("span", "OSC 2 s, grandMA2 15 s", "OSC 2 s, grandMA2 15 s")],
            [("Web interface", "Weboberfläche"), "<code>http://localhost:8080</code>"],
        ]),
        H2("Command line", "Kommandozeile"),
        P("Start options for experts and scripts:", "Startoptionen für Profis und Skripte:"),
        TABLE([("Option", "Option"), ("Effect", "Wirkung")], [
            ["<code>--port N</code>", ("web interface on port N instead of 8080", "Weboberfläche auf Port N statt 8080")],
            ["<code>--privacy</code>", ("start in privacy mode", "im Privatsphäre-Modus starten")],
            ["<code>--no-browser</code>", ("do not open the interface at start", "die Oberfläche beim Start nicht öffnen")],
            ["<code>--no-tray</code>", ("no icon in the menu bar or next to the clock", "kein Symbol in der Menüleiste oder neben der Uhr")],
            ["<code>--help</code>", ("list all options", "alle Optionen anzeigen")],
        ]),
        H2("Files", "Dateien"),
        TABLE([("File", "Datei"), ("Content", "Inhalt")], [
            ["<code>~/.ljlinkpro/settings.json</code>", ("privacy mode, language", "Privatsphäre-Modus, Sprache")],
            ["<code>~/.ljlinkpro/link.json</code>", ("light desk link settings", "Einstellungen des Lichtpult-Links")],
        ]),
        P("`~` is your user folder. Deleting the folder resets LJ Link. View settings (zoom, colours) are stored in each browser.", "`~` ist dein Benutzerordner. Den Ordner löschen setzt LJ Link zurück. Anzeige-Einstellungen (Zoom, Farben) speichert jeder Browser selbst."),
        H2("Ports", "Ports"),
        P("All ports are on [ports and firewall](wiki-network-ports.html).", "Alle Ports stehen unter [Ports und Firewall](wiki-network-ports.html)."),
        H2("Source code", "Quellcode"),
        P("LJ Link is open source under the Eclipse Public License 2.0: [GitHub](" + REPO + "). Notes on the architecture are in `docs/decisions.md`.",
          "LJ Link ist Open Source unter der Eclipse Public License 2.0: [GitHub](" + REPO + "). Notizen zur Architektur stehen in `docs/decisions.md`."),
    )
    return (("Reference: defaults, command line, files", "Referenz: Vorgaben, Kommandozeile, Dateien"),
            ("LJ Link reference: default ports, users and commands for every desk, send intervals, command line options and settings files.",
             "LJ-Link-Referenz: voreingestellte Ports, Benutzer und Befehle für jedes Pult, Sendeintervalle, Kommandozeilen-Optionen und Einstellungsdateien."),
            ("Help", "Hilfe"), ("Reference", "Referenz"),
            ("Every default value in one place.", "Jeder Vorgabewert an einem Ort."), body)
