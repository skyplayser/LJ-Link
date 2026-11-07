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
            [("waveform only sometimes, BPM frozen (Windows, 1.5.0 or older)", "Waveform nur manchmal, BPM steht (Windows, 1.5.0 oder älter)"), ("update to 1.5.1, see [known issues](wiki-known-issues.html#windows-shutdown)", "auf 1.5.1 aktualisieren, siehe [Bekannte Fehler](wiki-known-issues.html#windows-shutdown)")],
            [("track not from a rekordbox export (streaming, CD, never analysed)", "Track nicht aus einem rekordbox-Export (Streaming, CD, nie analysiert)"), ("export with rekordbox, analysed", "mit rekordbox exportieren, analysiert")],
            [("CDJ-3000 with new firmware, old export", "CDJ-3000 mit neuer Firmware, alter Export"), ("export again with rekordbox 6 or 7", "mit rekordbox 6 oder 7 neu exportieren")],
            [("rekordbox on this computer", "rekordbox auf diesem Computer"), ("quit it", "beenden")],
            [("Denon: track not analysed", "Denon: Track nicht analysiert"), ("analyse in Engine DJ or on the device", "in Engine DJ oder am Gerät analysieren")],
        ]),
        SYM("frozen", "BPM stays frozen, play is not shown, waveform only sometimes", "BPM bleibt stehen, Play wird nicht angezeigt, Waveform nur manchmal", [
            [("LJ Link 1.5.0 or older on Windows: the Pioneer connection keeps shutting itself down (known bug, fixed)", "LJ Link 1.5.0 oder älter unter Windows: die Pioneer-Verbindung schaltet sich immer wieder selbst ab (bekannter Fehler, behoben)"),
             ("update to 1.5.1 or newer; details in [known issues](wiki-known-issues.html#windows-shutdown)", "auf 1.5.1 oder neuer aktualisieren; Details unter [Bekannte Fehler](wiki-known-issues.html#windows-shutdown)")],
            [("a second LJ Link, Beat Link Trigger or rekordbox on this computer", "ein zweites LJ Link, Beat Link Trigger oder rekordbox auf diesem Computer"), ("close it; LJ Link reconnects by itself", "schließen; LJ Link verbindet sich von selbst neu")],
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
            [("ChamSys MagicQ: no reaction (alpha)", "ChamSys MagicQ: keine Reaktion (Alpha)"), ("OSC Mode Rx and the same port on the desk, an Autom row of type OSC Message with exactly the address from LJ Link; see [ChamSys MagicQ](wiki-chamsys.html)", "OSC Mode Rx und derselbe Port am Pult, eine Autom-Zeile vom Typ OSC Message mit genau der Adresse aus LJ Link; siehe [ChamSys MagicQ](wiki-chamsys.html)")],
            [("effect not on that speed master", "Effekt nicht auf diesem Speedmaster"), ("assign it", "zuordnen")],
            [("desk in another network", "Pult in einem anderen Netz"), ("[network setups](wiki-network-setups.html#desk)", "[Netzwerk-Aufbauten](wiki-network-setups.html#desk)")],
            [("tempo jumps between two values", "Tempo springt zwischen zwei Werten"), ("SEND BPM on one deck; see [Tempo on the light desk jumps](#tempo-jumps)", "SEND BPM auf ein Deck; siehe [Tempo am Lichtpult springt](#tempo-jumps)")],
        ]),
        DETAILS("Tempo on the light desk jumps (137.9, 138.0, 137.9 …)", "Tempo am Lichtpult springt (137,9, 138,0, 137,9 …)",
                ("**Cause:** the player stands right at a rounding edge, for example 137.95 BPM. LJ Link sends the tempo rounded to 0.1 BPM, and tiny swings of a few hundredths flipped the value between 137.9 and 138.0. A Wolfmix can restart its beat on every change.",
                 "**Ursache:** Der Player steht genau an einer Rundungsgrenze, zum Beispiel 137,95 BPM. LJ Link sendet das Tempo auf 0,1 BPM gerundet, und winzige Schwankungen um ein paar Hundertstel ließen den Wert zwischen 137,9 und 138,0 kippen. Ein Wolfmix kann bei jeder Änderung seinen Beat neu starten."),
                ("**What LJ Link does (1.7.4 and newer):** it steadies the raw tempo first and only sends a new value when it moves more than 0.15 BPM away from the sent one. So the tempo stays put at the rounding edge. A change of 0.25 BPM or more goes through in under a second; smaller deviations of up to 0.15 BPM are deliberately not sent. Since 1.7.5 the BPM on the deck shows the real value of the player with two decimals (137.95); the sent, steadied tempo is the tooltip on the BPM field. The display change is not tested on real hardware yet.",
                 "**Was LJ Link macht (ab 1.7.4):** Es beruhigt zuerst das Roh-Tempo und sendet einen neuen Wert erst, wenn er sich um mehr als 0,15 BPM vom gesendeten entfernt. So bleibt das Tempo an der Rundungsgrenze ruhig. Eine Änderung ab 0,25 BPM kommt in unter einer Sekunde durch; kleinere Abweichungen bis 0,15 BPM werden absichtlich nicht gesendet. Seit 1.7.5 zeigt die BPM im Deck den echten Wert des Players mit zwei Nachkommastellen (137,95); das gesendete, beruhigte Tempo steht als Tooltip am BPM-Feld. Die Anzeigeänderung ist noch nicht an echter Hardware getestet."),
                ("**Recommended:** update to the newest version, put SEND BPM on one deck, and where the desk can take it use **Ableton Link** (for example [Wolfmix](wiki-wolfmix.html#link) or [Daslight](wiki-daslight.html#link)).",
                 "**Empfehlung:** auf die neueste Version aktualisieren, SEND BPM auf ein Deck stellen und, wo das Pult es kann, **Ableton Link** nehmen (zum Beispiel [Wolfmix](wiki-wolfmix.html#link) oder [Daslight](wiki-daslight.html#link))."),
                ("**Still jumping?** Please [send a report](#report) while it happens. The diagnostic data show the raw tempo of every Pioneer player, so we can see where the jumps come from.",
                 "**Springt es weiter?** Bitte einen [Bericht senden](#report), während es passiert. Die Diagnosedaten zeigen das Roh-Tempo jedes Pioneer-Players, so sehen wir, woher die Sprünge kommen."),
                id_="tempo-jumps"),
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


@page("wiki-known-issues.html", "Known issues", "Bekannte Fehler")
def _():
    body = SEC(
        ADM("info", ("Bugs we know about, what you see and how to fix or avoid them. The newest version is always on the [releases page](https://github.com/skyplayser/LJ-Link/releases/latest). Found something that is not listed? [Report it](wiki-troubleshooting.html#report) or tell us on [Discord](" + DISCORD + ").",
                     "Fehler, die wir kennen, woran man sie erkennt und wie man sie behebt oder umgeht. Die neueste Version gibt es immer auf der [Release-Seite](https://github.com/skyplayser/LJ-Link/releases/latest). Etwas gefunden, das hier fehlt? [Melde es](wiki-troubleshooting.html#report) oder schreib uns auf [Discord](" + DISCORD + ").")),
        H2("Overview", "Übersicht"),
        TABLE([("Problem", "Problem"), ("Affects", "Betrifft"), ("Status", "Stand")], [
            [("[Pioneer connection keeps shutting itself down](#windows-shutdown)", "[Pioneer-Verbindung schaltet sich immer wieder ab](#windows-shutdown)"), ("Windows, Pioneer, 1.5.0 and older", "Windows, Pioneer, 1.5.0 und älter"), BADGE("ok", "Fixed in 1.5.1", "Behoben in 1.5.1")],
            [("[Direct cable and Wi-Fi: players twice or not at all](#direct-wifi)", "[Direktkabel und WLAN: Player doppelt oder gar nicht](#direct-wifi)"), ("direct cable without router, Wi-Fi on", "Direktkabel ohne Router, WLAN an"), BADGE("bad", "Open, workaround", "Offen, Umgehung")],
        ]),

        H2("Pioneer connection keeps shutting itself down (Windows)", "Pioneer-Verbindung schaltet sich immer wieder ab (Windows)", "windows-shutdown"),
        TABLE(None, [
            [("**Status**", "**Stand**"), BADGE("ok", "Fixed in 1.5.1", "Behoben in 1.5.1")],
            [("**Affects**", "**Betrifft**"), ("LJ Link 1.5.0 and older on some Windows PCs, Pioneer players (CDJ, XDJ)", "LJ Link 1.5.0 und älter auf manchen Windows-PCs, Pioneer-Player (CDJ, XDJ)")],
        ]),
        H3("What you see", "Woran man es erkennt"),
        UL(("The players are found, but the waveform loads only sometimes or only for one player.", "Die Player werden gefunden, aber die Waveform lädt nur manchmal oder nur bei einem Player."),
           ("The BPM is shown once and then stays frozen. Play, pitch and tempo changes on the CDJ are not shown.", "Das BPM wird einmal angezeigt und bleibt dann stehen. Play, Pitch und Tempoänderungen am CDJ werden nicht angezeigt."),
           ("In the device list the players have the address `0.0.0.0` or a long address like `0:0:0:0:0:ffff:a9fe:…`.", "In der Geräteliste haben die Player die Adresse `0.0.0.0` oder eine lange Adresse wie `0:0:0:0:0:ffff:a9fe:…`."),
           ("In the diagnostic data: `VirtualCdj running: false`, devices named `beat-link` with changing numbers (7, 8, 9 …) at the computer’s own address, and the log line `Another device has claimed it owns our device number, shutting down`.", "In den Diagnosedaten: `VirtualCdj running: false`, Geräte namens `beat-link` mit wechselnden Nummern (7, 8, 9 …) an der eigenen Adresse des Computers und die Log-Zeile `Another device has claimed it owns our device number, shutting down`.")),
        H3("Why it happens", "Warum es passiert"),
        P("On some Windows PCs the network hands incoming packets over with an IPv6 form of the sender’s address (`::ffff:169.254.1.1` instead of `169.254.1.1`). LJ Link then did not recognise its own announcements, took itself for a second device with the same player number, defended the number against itself and shut its connection down. A few seconds later it joined again with a new number, and the same thing happened. While the connection was down, no status, waveforms or metadata came in.",
          "Auf manchen Windows-PCs liefert das Netzwerk eingehende Pakete mit einer IPv6-Form der Absenderadresse (`::ffff:169.254.1.1` statt `169.254.1.1`). LJ Link hat dann seine eigenen Ansagen nicht erkannt, sich selbst für ein zweites Gerät mit derselben Player-Nummer gehalten, die Nummer gegen sich selbst verteidigt und die Verbindung abgeschaltet. Ein paar Sekunden später kam es mit einer neuen Nummer zurück, und alles ging von vorn los. Solange die Verbindung weg war, kamen kein Status, keine Waveforms und keine Metadaten an."),
        P("It is not your network, your cables or your rekordbox export. rekordbox and the USB stick are fine.", "Es liegt nicht an deinem Netzwerk, deinen Kabeln oder deinem rekordbox-Export. rekordbox und USB-Stick sind in Ordnung."),
        H3("How to fix it", "So behebst du es"),
        STEPS(("Install LJ Link **1.5.1 or newer** from the [releases page](https://github.com/skyplayser/LJ-Link/releases/latest), or in LJ Link: Menu (☰) → **Check for updates**.", "LJ Link **1.5.1 oder neuer** von der [Release-Seite](https://github.com/skyplayser/LJ-Link/releases/latest) installieren, oder in LJ Link: Menü (☰) → **Nach Updates suchen**."),
              ("Quit LJ Link completely (icon next to the clock → Quit) and start it again.", "LJ Link ganz beenden (Symbol neben der Uhr → Beenden) und neu starten."),
              ("Load a track and press play: BPM, play state and waveform now follow the player. The players show a normal address like `169.254.7.232` or `192.168.1.20`.", "Einen Track laden und Play drücken: BPM, Play-Zustand und Waveform folgen jetzt dem Player. Die Player zeigen eine normale Adresse wie `169.254.7.232` oder `192.168.1.20`.")),
        ADM("tip", ("Since 1.5.1 LJ Link also reconnects by itself if another program takes over its player number, and names it when it runs on the same computer (a second LJ Link, Beat Link Trigger, rekordbox). Then close that program.",
                    "Seit 1.5.1 verbindet sich LJ Link auch von selbst neu, wenn ein anderes Programm seine Player-Nummer übernimmt, und nennt es, wenn es auf demselben Computer läuft (ein zweites LJ Link, Beat Link Trigger, rekordbox). Dann dieses Programm schließen.")),
        P("Still happening with 1.5.1? Please [send a report](wiki-troubleshooting.html#report) right after starting LJ Link, with the gear connected.", "Passiert es mit 1.5.1 immer noch? Bitte direkt nach dem Start von LJ Link [einen Bericht senden](wiki-troubleshooting.html#report), mit angeschlossenen Geräten."),

        H2("Direct cable and Wi-Fi: players twice or not at all", "Direktkabel und WLAN: Player doppelt oder gar nicht", "direct-wifi"),
        TABLE(None, [
            [("**Status**", "**Stand**"), BADGE("bad", "Open, workaround", "Offen, Umgehung")],
            [("**Affects**", "**Betrifft**"), ("one player straight into the computer without a router, Wi-Fi on", "ein Player direkt am Computer ohne Router, WLAN an")],
        ]),
        P("Without a router, computer and players give themselves `169.254.x.x` addresses. With Wi-Fi on, the computer may send on the wrong adapter or receive every packet twice.", "Ohne Router geben sich Computer und Player `169.254.x.x`-Adressen. Mit eingeschaltetem WLAN sendet der Computer eventuell über den falschen Anschluss oder bekommt jedes Paket doppelt."),
        STEPS(("Switch Wi-Fi off on the computer.", "WLAN am Computer ausschalten."),
              ("Quit LJ Link and start it again.", "LJ Link beenden und neu starten."),
              ("Better: put a small switch or router in between, see [network setups](wiki-network-setups.html#b-router).", "Besser: einen kleinen Switch oder Router dazwischen, siehe [Netzwerk-Aufbauten](wiki-network-setups.html#b-router).")),
    )
    return (("Known issues", "Bekannte Fehler"),
            ("Known LJ Link bugs, how to recognise them and how to fix them: Pioneer connection shutting itself down on Windows (fixed in 1.5.1), direct cable with Wi-Fi.",
             "Bekannte Fehler von LJ Link, woran man sie erkennt und wie man sie behebt: Pioneer-Verbindung schaltet sich unter Windows ab (behoben in 1.5.1), Direktkabel mit WLAN."),
            ("Help", "Hilfe"), ("Known issues", "Bekannte Fehler"),
            ("Bugs we know about and how to get around them.", "Fehler, die wir kennen, und wie man sie umgeht."), body)


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
