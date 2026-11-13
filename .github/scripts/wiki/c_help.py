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
            [("rekordbox, ShowKontrol, Engine DJ or SoundSwitch on this computer", "rekordbox, ShowKontrol, Engine DJ oder SoundSwitch auf diesem Computer"), ("quit them", "beenden")],
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
        SYM("mixer", "Mixer tab: “No mixer found”, or the mixer shows but no tempo", "Reiter Mixer: „Kein Mixer gefunden“, oder der Mixer steht da, aber ohne Tempo", [
            [("LJ Link 1.7.9 or older: a Pioneer mixer was only shown when it sent a mixer status LJ Link can read (seen with a DJM-A9)", "LJ Link 1.7.9 oder älter: Ein Pioneer-Mixer wurde nur gezeigt, wenn er einen Mixer-Status schickte, den LJ Link lesen kann (gesehen mit einem DJM-A9)"),
             ("update to 1.7.10 or newer: the mixer then shows up as soon as it announces itself in the network, with model, number and address (alpha, not tested on a real mixer)", "auf 1.7.10 oder neuer aktualisieren: Der Mixer erscheint dann, sobald er sich im Netz anmeldet, mit Modell, Nummer und Adresse (Alpha, nicht an einem echten Mixer getestet)")],
            [("note **Mixer recognised (Alpha), but it sends no data LJ Link can read (tempo, master)**", "Hinweis **Mixer erkannt (Alpha), sendet aber keine Daten, die LJ Link lesen kann (Tempo, Master)**"),
             ("the mixer is there, but after LJ Link has listened for 5 seconds no mixer status has arrived. Tempo and master from the mixer are missing; ON AIR per channel is read from the players, not from this status (with the A9 not tested). Please [send a report](#report): it counts the packets the mixer sends", "der Mixer ist da, aber nachdem LJ Link 5 Sekunden zugehört hat, kam kein Mixer-Status. Tempo und Master vom Mixer fehlen; ON AIR je Kanal liest LJ Link aus den Playern, nicht aus diesem Status (mit dem A9 nicht getestet). Bitte [einen Bericht senden](#report): er zählt die Pakete, die der Mixer schickt")],
            [("mixer not in the same network", "Mixer nicht im selben Netz"), ("players and computer on the DJM’s LINK sockets or on the same switch", "Player und Computer an den LINK-Buchsen des DJM oder am selben Switch")],
        ]),
        SYM("showkontrol", "Grey note “ShowKontrol runs on this computer” or “ShowKontrol is running on the network”", "Grauer Hinweis „ShowKontrol läuft auf diesem Computer“ oder „ShowKontrol läuft im Netz“", [
            [("note **ShowKontrol runs on this computer. Both programs at the same time on one computer can disturb each other.** ShowKontrol announced itself from an address of this computer", "Hinweis **ShowKontrol läuft auf diesem Computer. Beide Programme gleichzeitig auf einem Computer können sich stören.** ShowKontrol hat sich von einer Adresse dieses Computers angemeldet"),
             ("like rekordbox, do not run ShowKontrol and LJ Link at the same time on one computer: both join Pro DJ Link with their own player number and use the same network ports, and they can disturb each other (titles and waveforms missing in one or both). Quit one of them, or run LJ Link on a second computer. Running both together is not tested. The note already shows in the search view and stays until the search is stopped, because once LJ Link has joined it no longer sees announcements from its own computer", "wie bei rekordbox: ShowKontrol und LJ Link nicht gleichzeitig auf einem Computer betreiben. Beide melden sich mit eigener Player-Nummer bei Pro DJ Link an und nutzen dieselben Netzwerk-Ports, sie können sich stören (Titel und Waveforms fehlen in einem oder beiden). Eins von beiden beenden oder LJ Link auf einem zweiten Computer betreiben. Beide zusammen sind nicht getestet. Der Hinweis erscheint schon in der Suchansicht und bleibt, bis die Suche gestoppt wird, weil LJ Link nach dem Anmelden keine Ankündigungen vom eigenen Computer mehr sieht")],
            [("note **ShowKontrol is running on the network. Both programs at the same time on one computer can disturb each other.** ShowKontrol runs on another computer in the network", "Hinweis **ShowKontrol läuft im Netz. Beide Programme gleichzeitig auf einem Computer können sich stören.** ShowKontrol läuft auf einem anderen Computer im Netz"),
             ("the note is only for information; both programs in one network are not tested. It goes away when ShowKontrol leaves the network. Nothing is filtered or switched off", "der Hinweis ist nur zur Information; beide Programme in einem Netz sind nicht getestet. Er verschwindet, wenn ShowKontrol das Netz verlässt. Nichts wird gefiltert oder abgeschaltet")],
        ]),
        SYM("denon", "A Denon device is not found or keeps reconnecting", "Ein Denon-Gerät wird nicht gefunden oder verbindet sich ständig neu", [
            [("LJ Link 1.3.0", "LJ Link 1.3.0"), ("update to 1.3.1 or newer", "auf 1.3.1 oder neuer aktualisieren")],
            [("Engine DJ, SoundSwitch, Resolume on this computer", "Engine DJ, SoundSwitch, Resolume auf diesem Computer"), ("close them", "schließen")],
            [("connection just broke", "Verbindung gerade abgebrochen"), ("the device needs a few seconds; wait", "das Gerät braucht ein paar Sekunden; warten")],
        ]),
        H2("Tracks and waveforms", "Tracks und Waveforms", "tracks-and-waveforms"),
        ADM("warn", ("**CDJ-3000X**: with the current version LJ Link gets **no titles, waveforms or beat grid** from it. Tempo, BPM, time and play/pause do arrive, so the tempo to the light desk works (seen in a user report with four CDJ-3000X). The most likely cause is the newer **OneLibrary** format of these players: the open-source Pro DJ Link library LJ Link uses (beat-link) cannot read it yet in the version built in. **XDJ-AZ and OPUS-QUAD** use the same format and probably behave alike, but they are not tested; whether LJ Link gets the tempo of an OPUS-QUAD at all is open. Support needs a newer version of that library and a test on real players.",
                     "**CDJ-3000X**: Mit der aktuellen Version bekommt LJ Link von ihr **keine Titel, Waveforms und kein Beatgrid**. Tempo, BPM, Zeit und Play/Pause kommen an, das Tempo ans Lichtpult geht also (gesehen in einem Nutzerbericht mit vier CDJ-3000X). Die wahrscheinliche Ursache ist das neuere **OneLibrary**-Format dieser Player: Die Open-Source-Bibliothek für Pro DJ Link, die LJ Link nutzt (beat-link), kann es in der eingebauten Version noch nicht lesen. **XDJ-AZ und OPUS-QUAD** nutzen dasselbe Format und verhalten sich vermutlich gleich, sind aber nicht getestet; ob LJ Link vom OPUS-QUAD überhaupt das Tempo bekommt, ist offen. Für die Unterstützung braucht es eine neuere Version dieser Bibliothek und einen Test an echten Playern."),
            ("Since 1.7.10 LJ Link no longer shows “Loading track …” forever: if a player reports a track but no title arrives within **30 seconds**, the deck says **Not supported** (OneLibrary models) or **No track data** (other players), with a sentence on the waveform and a link to this section (alpha, not tested on real players).",
             "Seit 1.7.10 zeigt LJ Link nicht mehr endlos „Lade Track …“: Meldet ein Player einen Track, aber nach **30 Sekunden** ist kein Titel da, steht im Deck **Nicht unterstützt** (OneLibrary-Modelle) oder **Keine Trackdaten** (andere Player), mit einem Satz auf der Waveform und einem Link auf diesen Abschnitt (Alpha, nicht an echten Playern getestet)."),
            title=("Not supported yet", "Noch nicht unterstützt")),
        SYM("no-track-data", "Deck shows “Not supported” or “No track data”", "Deck zeigt „Nicht unterstützt“ oder „Keine Trackdaten“", [
            [("**Not supported**: CDJ-3000X, XDJ-AZ or OPUS-QUAD (most likely the OneLibrary format)", "**Nicht unterstützt**: CDJ-3000X, XDJ-AZ oder OPUS-QUAD (wahrscheinlich das OneLibrary-Format)"),
             ("nothing you can change on your side yet; with the CDJ-3000X tempo, BPM, time and play/pause still work (XDJ-AZ and OPUS-QUAD not tested). Please [send a report](#report) with the players connected, it shows which format your stick has", "auf deiner Seite gerade nichts zu ändern; bei der CDJ-3000X gehen Tempo, BPM, Zeit und Play/Pause weiter (XDJ-AZ und OPUS-QUAD nicht getestet). Bitte mit angeschlossenen Playern [einen Bericht senden](#report), er zeigt, welches Format dein Stick hat")],
            [("**No track data** appears only for tracks analysed by rekordbox whose data does not arrive. Unanalysed files, CDs and some streaming sources show **Track without rekordbox analysis** instead", "**Keine Trackdaten** erscheint nur bei von rekordbox analysierten Tracks, deren Daten nicht ankommen. Nicht analysierte Dateien, CDs und manche Streaming-Quellen zeigen stattdessen **Track without rekordbox analysis**"), ("for those: export with rekordbox, analysed", "für diese: mit rekordbox exportieren, analysiert")],
            [("**No track data**: rekordbox, ShowKontrol or another Pro DJ Link program on this computer", "**Keine Trackdaten**: rekordbox, ShowKontrol oder ein anderes Pro-DJ-Link-Programm auf diesem Computer"), ("quit it, then **Search automatically** again", "beenden, dann erneut **Automatisch suchen**")],
            [("**No track data**: still unclear", "**Keine Trackdaten**: weiter unklar"), ("please [send a report](#report) while it happens", "bitte einen [Bericht senden](#report), während es passiert")],
        ]),
        SYM("no-waveform", "“No track loaded” or no waveform", "„No track loaded“ oder keine Waveform", [
            [("LJ Link older than 1.4.1", "LJ Link älter als 1.4.1"), ("update", "aktualisieren")],
            [("waveform only sometimes, BPM frozen (Windows, 1.5.0 or older)", "Waveform nur manchmal, BPM steht (Windows, 1.5.0 oder älter)"), ("update to 1.5.1, see [known issues](wiki-known-issues.html#windows-shutdown)", "auf 1.5.1 aktualisieren, siehe [Bekannte Fehler](wiki-known-issues.html#windows-shutdown)")],
            [("CDJ-3000X, XDJ-AZ or OPUS-QUAD (OneLibrary)", "CDJ-3000X, XDJ-AZ oder OPUS-QUAD (OneLibrary)"), ("not supported yet, see [above](#tracks-and-waveforms)", "noch nicht unterstützt, siehe [oben](#tracks-and-waveforms)")],
            [("track not from a rekordbox export (streaming, CD, never analysed)", "Track nicht aus einem rekordbox-Export (Streaming, CD, nie analysiert)"), ("export with rekordbox, analysed", "mit rekordbox exportieren, analysiert")],
            [("CDJ-3000 with new firmware, old export", "CDJ-3000 mit neuer Firmware, alter Export"), ("export again with rekordbox 6 or 7", "mit rekordbox 6 oder 7 neu exportieren")],
            [("rekordbox or ShowKontrol on this computer", "rekordbox oder ShowKontrol auf diesem Computer"), ("quit it; run only one Pro DJ Link program per computer", "beenden; nur ein Pro-DJ-Link-Programm pro Computer betreiben")],
            [("Denon: track not analysed", "Denon: Track nicht analysiert"), ("analyse in Engine DJ or on the device", "in Engine DJ oder am Gerät analysieren")],
        ]),
        SYM("frozen", "BPM stays frozen, play is not shown, waveform only sometimes", "BPM bleibt stehen, Play wird nicht angezeigt, Waveform nur manchmal", [
            [("LJ Link 1.5.0 or older on Windows: the Pioneer connection keeps shutting itself down (known bug, fixed)", "LJ Link 1.5.0 oder älter unter Windows: die Pioneer-Verbindung schaltet sich immer wieder selbst ab (bekannter Fehler, behoben)"),
             ("update to 1.5.1 or newer; details in [known issues](wiki-known-issues.html#windows-shutdown)", "auf 1.5.1 oder neuer aktualisieren; Details unter [Bekannte Fehler](wiki-known-issues.html#windows-shutdown)")],
            [("a second LJ Link, Beat Link Trigger, rekordbox or ShowKontrol on this computer", "ein zweites LJ Link, Beat Link Trigger, rekordbox oder ShowKontrol auf diesem Computer"), ("close it; LJ Link reconnects by itself", "schließen; LJ Link verbindet sich von selbst neu")],
        ]),
        SYM("beat-port", "Yellow note “Beat port 50001 is in use”", "Gelber Hinweis „Beat port 50001 is in use“", [
            [("rekordbox, Beat Link Trigger, SoundSwitch or a second LJ Link", "rekordbox, Beat Link Trigger, SoundSwitch oder ein zweites LJ Link"), ("close it, search again; until then positions are estimated", "schließen, erneut suchen; bis dahin werden Positionen geschätzt")],
        ]),
        SYM("jumps", "The waveform lags behind, jumps or stutters", "Die Waveform hängt hinterher, springt oder ruckelt", [
            [("LJ Link 1.7.7 or older: one slow or sleeping device with the page open (for example an iPad) could hold up all other browsers, delays on the way to the browser were not compensated, and brief gaps in the position from the players could make the waveform jump", "LJ Link 1.7.7 oder älter: Ein einzelnes langsames oder schlafendes Gerät mit offener Seite (zum Beispiel ein iPad) konnte alle anderen Browser aufhalten, Verzögerungen auf dem Weg zum Browser wurden nicht ausgeglichen, und kurze Lücken in der Position von den Playern konnten die Waveform springen lassen"),
             ("update to 1.7.8 or newer (fix not tested on real hardware yet, alpha)", "auf 1.7.8 oder neuer aktualisieren (Korrektur noch nicht an echter Hardware getestet, Alpha)")],
            [("the page is still open on devices you are not using (old tab, iPad in standby)", "die Seite ist noch auf Geräten offen, die du gerade nicht nutzt (alter Tab, iPad im Standby)"), ("close the page there", "die Seite dort schließen")],
            [("the display has fallen behind after a long time", "die Anzeige ist nach langer Zeit hinterher"), ("reload the page: Cmd+R on a Mac, Ctrl+R or F5 on Windows, the reload button in Safari on an iPad", "die Seite neu laden: Cmd+R am Mac, Strg+R oder F5 unter Windows, auf dem iPad die Taste zum Neuladen in Safari")],
            [("CDJ-2000NXS2 and older report position only between beats", "CDJ-2000NXS2 und älter melden die Position nur zwischen Beats"), ("normal after a cue or loop; the CDJ-3000 is exact", "nach Cue oder Loop normal; der CDJ-3000 ist genau")],
            [("busy network", "ausgelastetes Netz"), ("own switch for the DJ gear", "eigener Switch für die DJ-Geräte")],
            [("still lagging or jumping", "hängt oder springt weiter"), ("please [send a report](#report) while it happens", "bitte einen [Bericht senden](#report), während es passiert")],
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
        DETAILS("LJ Link does not start, or runs on another port than 8080 (port in use)", "LJ Link startet nicht oder läuft auf einem anderen Port als 8080 (Port belegt)",
            P("The interface of LJ Link uses TCP port **8080**. If another program already uses it (a web server, a development tool, another app), LJ Link 1.7.10 and newer takes the next free port by itself, up to **8089** (alpha; checked on a Mac, not tested on Windows).",
              "Die Oberfläche von LJ Link nutzt den TCP-Port **8080**. Belegt ihn schon ein anderes Programm (ein Webserver, ein Entwicklerwerkzeug, eine andere App), nimmt LJ Link ab 1.7.10 von selbst den nächsten freien Port, bis **8089** (Alpha; am Mac geprüft, unter Windows nicht getestet)."),
            TABLE([("Where you see the port", "Wo der Port steht"), ("What it shows", "Was dort steht")], [
                [("Start page", "Startseite"), ("**Port 8080 is in use by another program, so LJ Link runs on port 8081.** With a network, the address for a tablet follows", "**Port 8080 ist von einem anderen Programm belegt, deshalb läuft LJ Link auf Port 8081.** Mit Netzwerk folgt die Adresse für ein Tablet")],
                [("Program icon (menu bar or next to the clock)", "Programm-Symbol (Menüleiste oder neben der Uhr)"), ("the address in the menu and in the tooltip, for example `LJ Link – 127.0.0.1:8081`", "die Adresse im Menü und im Tooltip, zum Beispiel `LJ Link – 127.0.0.1:8081`")],
                [("Address bar", "Adresszeile"), ("`http://127.0.0.1:8081` on the computer itself", "`http://127.0.0.1:8081` am Computer selbst")],
            ]),
            ADM("warn", ("Tablets and bookmarks need the new port too: if LJ Link runs on 8081, open `http://<address>:8081` on the tablet. For an address that stays the same, start LJ Link with a port of its own (below).",
                         "Tablets und Lesezeichen brauchen dann auch den neuen Port: Läuft LJ Link auf 8081, auf dem Tablet `http://<Adresse>:8081` öffnen. Für eine Adresse, die gleich bleibt, LJ Link mit einem eigenen Port starten (unten).")),
            TABLE([("Cause", "Ursache"), ("Fix", "Lösung")], [
                [("LJ Link 1.7.9 or older: does not start at all when port 8080 is in use (known bug, [issue #3](https://github.com/skyplayser/LJ-Link/issues/3))", "LJ Link 1.7.9 oder älter: startet gar nicht, wenn Port 8080 belegt ist (bekannter Fehler, [Issue #3](https://github.com/skyplayser/LJ-Link/issues/3))"),
                 ("update to 1.7.10 or newer, or start LJ Link with another port (below)", "auf 1.7.10 oder neuer aktualisieren, oder LJ Link mit einem anderen Port starten (unten)")],
                [("all ports from 8080 to 8089 are in use; a window says **LJ Link cannot start: ports 8080 to 8089 are all in use by other programs.**", "alle Ports von 8080 bis 8089 sind belegt; ein Fenster sagt **LJ Link kann nicht starten: Die Ports 8080 bis 8089 sind alle von anderen Programmen belegt.**"),
                 ("close one of these programs, or start LJ Link with another port (below)", "eines dieser Programme schließen, oder LJ Link mit einem anderen Port starten (unten)")],
                [("LJ Link already runs", "LJ Link läuft schon"), ("a second start with the same port only opens the running LJ Link", "ein zweiter Start mit demselben Port öffnet nur das laufende LJ Link")],
            ]),
            P("**Start with another port, Mac:** quit LJ Link first (program icon → **Quit**), otherwise the command only brings the running LJ Link to the front. Then open **Terminal** (Applications → Utilities) and enter:",
              "**Mit einem anderen Port starten, Mac:** LJ Link zuerst beenden (Programm-Symbol → **Beenden**), sonst holt der Befehl nur das laufende LJ Link nach vorn. Dann das **Terminal** öffnen (Programme → Dienstprogramme) und eingeben:"),
            PRE('open -a "LJ Link" --args --port 18095'),
            P("Is your app still called **LJ-Link-PRO** (installed before 1.7.8)? Then use `open -a \"LJ-Link-PRO\" --args --port 18095`. We checked on a Mac that the app receives the option this way.",
              "Heißt deine App noch **LJ-Link-PRO** (installiert vor 1.7.8)? Dann `open -a \"LJ-Link-PRO\" --args --port 18095` verwenden. Am Mac geprüft, dass die App die Option so bekommt."),
            P("**Start with another port, Windows:** right-click the LJ Link shortcut on the desktop → **Copy**, then **Paste**, so the original stays. Right-click the copy → **Properties**, and at the end of **Target**, after the closing quote, add ` --port 18095`, for example `\"…\\LJ Link.exe\" --port 18095`. Start LJ Link with this shortcut. Not tested by us on Windows; the reporter of issue #3 started LJ Link this way.",
              "**Mit einem anderen Port starten, Windows:** Rechtsklick auf die LJ-Link-Verknüpfung auf dem Desktop → **Kopieren**, dann **Einfügen**, damit das Original bleibt. Rechtsklick auf die Kopie → **Eigenschaften** und bei **Ziel** am Ende, nach dem schließenden Anführungszeichen, ` --port 18095` anhängen, zum Beispiel `\"…\\LJ Link.exe\" --port 18095`. LJ Link mit dieser Verknüpfung starten. Von uns unter Windows nicht getestet; der Melder von Issue #3 hat LJ Link so gestartet."),
            P("The option applies to that start only, so start LJ Link this way every time. If 18095 is in use too, LJ Link takes the next free port up to 18104. On a tablet then open `http://<address>:18095`.",
              "Die Option gilt nur für diesen Start, also LJ Link jedes Mal so starten. Ist auch 18095 belegt, nimmt LJ Link den nächsten freien Port bis 18104. Auf dem Tablet dann `http://<Adresse>:18095` öffnen."),
            id_="port"),
        SYM("icon", "No icon in the menu bar or next to the clock", "Kein Symbol in der Menüleiste oder neben der Uhr", [
            [("LJ Link already runs", "LJ Link läuft schon"), ("a second start only opens the interface; close the first", "ein zweiter Start öffnet nur die Oberfläche; das erste schließen")],
            [("macOS hides it", "macOS blendet es aus"), ("System Settings → Menu Bar; a full menu bar hides icons", "Systemeinstellungen → Menüleiste; eine volle Menüleiste blendet Symbole aus")],
            [("Windows hides it", "Windows blendet es aus"), ("behind the arrow next to the clock; drag it out", "hinter dem Pfeil neben der Uhr; herausziehen")],
        ]),
        SYM("screen-off", "The screen goes dark (Mac, iPad, tablet)", "Bildschirm geht aus (Mac, iPad, Tablet)", [
            [("older than 1.7.6", "älter als 1.7.6"), ("update; 1.7.6 keeps the screen on (alpha; works on a Mac, on the iPad not reliable so far, Android and Windows not tested yet)", "aktualisieren; 1.7.6 hält den Bildschirm an (Alpha; läuft am Mac, auf dem iPad bisher nicht zuverlässig, Android und Windows noch nicht getestet)")],
            [("iPad or iPhone over `http://<address>:8080`: without HTTPS Safari has no real screen lock; LJ Link tries a workaround (alpha) that does not work on every device and has not been reliable on the iPad so far", "iPad oder iPhone über `http://<Adresse>:8080`: Ohne HTTPS hat Safari keine echte Bildschirmsperre; LJ Link versucht es mit einem Notbehelf (Alpha), der nicht auf jedem Gerät klappt und auf dem iPad bisher nicht zuverlässig war"),
             ("the reliable way: power adapter on, Settings → **Display & Brightness** → **Auto-Lock** → **Never**, and **Low Power Mode** off (on the iPhone it shortens auto-lock to 30 seconds, probably on the iPad too). See [tablet](wiki-case-tablet.html#keep-awake-ios)", "der sichere Weg: Netzteil dran, Einstellungen → **Anzeige & Helligkeit** → **Automatische Sperre** → **Nie**, und den **Stromsparmodus** ausschalten (auf dem iPhone verkürzt er die automatische Sperre auf 30 Sekunden, auf dem iPad vermutlich auch). Siehe [Tablet](wiki-case-tablet.html#keep-awake-ios)")],
            [("tablet over the network, strip **☼ Tap here once to keep the screen on** right below the header", "Tablet über das Netzwerk, Streifen **☼ Einmal hier tippen, damit der Bildschirm an bleibt** direkt unter der Kopfzeile"),
             ("tap the strip or anywhere else on the page once; LJ Link then tries to keep the screen on, and ☰ → **Screen** shows **Starting the video …**, then **Active (video) – the screen stays on**. If the screen still locks, use auto-lock **Never** as above", "einmal auf den Streifen oder irgendwo sonst auf die Seite tippen; LJ Link versucht dann, den Bildschirm anzuhalten, und ☰ → **Bildschirm** zeigt **Video startet …**, danach **Aktiv (Video) – der Bildschirm bleibt an**. Sperrt der Bildschirm trotzdem, die automatische Sperre wie oben auf **Nie** stellen")],
            [("status **Video stopped – please tap the page**, strip **☼ The screen may turn off – tap here to restart keep-awake**", "Status **Video angehalten – bitte auf die Seite tippen**, Streifen **☼ Der Bildschirm kann ausgehen – hier tippen, um den Wachmodus neu zu starten**"),
             ("the video did not start within about 6 seconds, or iOS stopped it (for example after switching apps). Tap the page once more. If this happens again and again, use auto-lock **Never** as above", "das Video lief nicht innerhalb von etwa 6 Sekunden an, oder iOS hat es angehalten (etwa nach einem App-Wechsel). Noch einmal auf die Seite tippen. Passiert das immer wieder, die automatische Sperre wie oben auf **Nie** stellen")],
            [("setting switched off in this browser", "Einstellung in diesem Browser ausgeschaltet"), ("☰ → **Screen** → tick **Keep screen awake – Alpha**; it is saved per browser", "☰ → **Bildschirm** → **Bildschirm wach halten – Alpha** anhaken; das wird pro Browser gespeichert")],
            [("status **Not available in this browser**", "Status **In diesem Browser nicht verfügbar**"), ("set auto-lock on the device to **Never** for the gig (power adapter on, Low Power Mode off)", "automatische Sperre am Gerät für den Gig auf **Nie** stellen (Netzteil dran, Stromsparmodus aus)")],
            [("iPad: music of another app pauses, or LJ Link shows up under Now Playing (possible, not tested yet)", "iPad: Musik einer anderen App pausiert, oder LJ Link erscheint unter „Wird gespielt“ (möglich, noch ungetestet)"),
             ("side effect of the silent video that tries to keep the screen on; if it bothers you, switch the setting off and set auto-lock to **Never** instead", "Nebenwirkung des stillen Videos, das den Bildschirm anhalten soll; stört es, die Einstellung ausschalten und stattdessen die automatische Sperre auf **Nie** stellen")],
        ]),
        SYM("update", "“Check for updates” fails", "„Nach Updates suchen“ schlägt fehl", [
            [("version 1.2.x", "Version 1.2.x"), ("install the newest version by hand once", "die neueste Version einmal von Hand installieren")],
            [("no internet", "kein Internet"), ("normal in a show network; update at home", "im Show-Netz normal; zu Hause aktualisieren")],
        ]),
        H2("Report a problem or request a feature", "Problem melden oder Wunsch äußern", "report"),
        STEPS(("Keep LJ Link open while the problem is there, gear connected.", "LJ Link offen lassen, während das Problem da ist, Geräte angeschlossen."),
              ("Menu (☰) → **Feedback** → **Report a problem or request a feature**, or in the menu of the icon.", "Menü (☰) → **Feedback** → **Problem melden oder Wunsch äußern**, oder im Menü des Symbols."),
              ("Optional: rate LJ Link with 1 to 5 stars (alpha). The rating becomes the first line of the report; **Clear rating** removes it. A wish or a rating alone is welcome too.", "Optional: LJ Link mit 1 bis 5 Sternen bewerten (Alpha). Die Bewertung steht als erste Zeile im Bericht; **Bewertung entfernen** nimmt sie weg. Ein Wunsch oder nur eine Bewertung ist auch willkommen."),
              ("Describe gear, firmware, Mac or Windows and what you see. Leave **Include diagnostic data** ticked: version, adapters, devices and their state, what each deck receives, last log lines. No titles, covers or passwords. **Show diagnostic data** shows it before sending.", "Geräte, Firmware, Mac oder Windows und das Gesehene beschreiben. **Diagnosedaten mitsenden** angehakt lassen: Version, Adapter, Geräte und ihr Zustand, was jedes Deck bekommt, letzte Log-Zeilen. Keine Titel, Cover oder Passwörter. **Diagnosedaten ansehen** zeigt alles vor dem Senden."),
              ("**Send by email** (to fehlermelden@nextphilm.de) or **Report on GitHub**. Or ask on [Discord](" + DISCORD + ").", "**Per E-Mail senden** (an fehlermelden@nextphilm.de) oder **Auf GitHub melden**. Oder auf [Discord](" + DISCORD + ") fragen."),
              ("If a yellow box **Please attach the file to the mail yourself** appears: attach the file it names to the mail by hand before you send it (see below).", "Erscheint ein gelber Kasten **Bitte hänge die Datei selbst an die Mail an**: die genannte Datei vor dem Senden von Hand an die Mail hängen (siehe unten).")),
        ADM("warn", ("**Your mail program does not attach the diagnostic data by itself.** A link that opens a mail can only carry text, and only up to about 2000 characters. If the report is longer, LJ Link (1.7.10 and newer):",
                     "**Dein Mailprogramm hängt die Diagnosedaten nicht selbst an.** Ein Link, der eine Mail öffnet, kann nur Text mitgeben, und nur bis etwa 2000 Zeichen. Ist der Bericht länger, macht LJ Link (ab 1.7.10) Folgendes:"),
            UL(("saves the whole diagnostic data as a file `LJ-Link-report-….txt` in your **downloads folder** (where your browser saves downloads, usually *Downloads*) and also copies it to the clipboard,", "Es speichert die vollständigen Diagnosedaten als Datei `LJ-Link-report-….txt` in deinem **Download-Ordner** (dort, wo dein Browser Downloads ablegt, meist *Downloads*) und kopiert sie zusätzlich in die Zwischenablage,"),
               ("shows the yellow box **Please attach the file to the mail yourself** with the exact file name,", "zeigt den gelben Kasten **Bitte hänge die Datei selbst an die Mail an** mit dem genauen Dateinamen,"),
               ("starts the mail text with **PLEASE ATTACH: the file … from your downloads folder**, then your description and as many lines of the **summary** as fit.", "beginnt den Mailtext mit **BITTE ANHÄNGEN: die Datei … aus deinem Download-Ordner**, dann deine Beschreibung und so viele Zeilen der **Kurzfassung**, wie hineinpassen.")),
            ("Attach that file to the mail before sending. If it is missing, paste the clipboard into the mail (Cmd+V or Ctrl+V), or press **Save as file** again. If everything fits into the link, the whole diagnostic data is in the mail text and there is nothing to attach. With 1.7.9 and older the mail said the data was attached although it was not; please attach the file by hand there too.",
             "Diese Datei vor dem Senden an die Mail hängen. Fehlt sie, die Zwischenablage in die Mail einfügen (Cmd+V oder Strg+V) oder noch einmal **Als Datei speichern** drücken. Passt alles in den Link, stehen die ganzen Diagnosedaten im Mailtext und es gibt nichts anzuhängen. Bei 1.7.9 und älter stand in der Mail, die Daten hingen an, obwohl sie es nicht taten; bitte auch dort die Datei von Hand anhängen."),
            title=("Attach the file yourself", "Datei selbst anhängen")),
        P("Since 1.7.10 the diagnostic data starts with a short **summary** (section `== Summary ==`), in this order: version and system; every warning and note (for example a mixer without readable status, ShowKontrol on this computer or on the network, two adapters in one network); LJ Link’s own player number and adapter; the players with model and whether titles arrive; the media; the mixer; the devices that announced themselves (for example a mixer as device 33) with their status and beat packets; then the network adapters, the sources, the decks, and last the sources that found nothing. The most important lines come first, so they stay in the mail even when the summary is cut. The full details follow below it.",
          "Seit 1.7.10 beginnen die Diagnosedaten mit einer **Kurzfassung** (Abschnitt `== Summary ==`), in dieser Reihenfolge: Version und System; alle Warnungen und Hinweise (zum Beispiel ein Mixer ohne lesbaren Status, ShowKontrol auf diesem Computer oder im Netz, zwei Adapter im selben Netz); LJ Links eigene Player-Nummer und Schnittstelle; die Player mit Modell und ob Titel ankommen; die Medien; der Mixer; die Geräte, die sich angemeldet haben (zum Beispiel ein Mixer als Gerät 33), mit ihren Status- und Beat-Paketen; danach die Netzwerkadapter, die Quellen, die Decks und zuletzt die Quellen, die nichts gefunden haben. Die wichtigsten Zeilen stehen vorn und bleiben so in der Mail, auch wenn die Kurzfassung gekürzt wird. Darunter folgen alle Einzelheiten."),
    )
    return (("Troubleshooting by symptom", "Fehlersuche nach Symptom"),
            ("LJ Link troubleshooting by symptom: nothing found, players twice, direct cable bug, mixer not found, ShowKontrol, no titles or waveform (CDJ-3000X, XDJ-AZ, OPUS-QUAD), waveform lags or jumps, port in use, Denon reconnects, desk does not react, screen goes dark, icon and updates, how to report.",
             "LJ-Link-Fehlersuche nach Symptom: nichts gefunden, Player doppelt, Direktkabel-Fehler, Mixer nicht gefunden, ShowKontrol, keine Titel oder Waveform (CDJ-3000X, XDJ-AZ, OPUS-QUAD), Waveform hängt oder springt, Port belegt, Denon verbindet neu, Pult reagiert nicht, Bildschirm geht aus, Symbol und Updates, wie man meldet."),
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
            [("[LJ Link does not start when port 8080 is in use](wiki-troubleshooting.html#port)", "[LJ Link startet nicht, wenn Port 8080 belegt ist](wiki-troubleshooting.html#port)"), ("1.7.9 and older, any program on port 8080", "1.7.9 und älter, irgendein Programm auf Port 8080"), BADGE("alpha", "Fixed in 1.7.10, alpha", "Behoben in 1.7.10, Alpha")],
            [("[XDJ-AZ: player number 7 taken twice, log full of errors](wiki-pioneer.html#xdj-az)", "[XDJ-AZ: Player-Nummer 7 doppelt, Log voller Fehler](wiki-pioneer.html#xdj-az)"), ("XDJ-AZ, 1.7.9 and older", "XDJ-AZ, 1.7.9 und älter"), BADGE("alpha", "Fixed in 1.7.10, alpha", "Behoben in 1.7.10, Alpha")],
            [("[No titles, waveforms or beat grid from OneLibrary players](#onelibrary)", "[Keine Titel, Waveforms und kein Beatgrid von OneLibrary-Playern](#onelibrary)"), ("CDJ-3000X (XDJ-AZ, OPUS-QUAD not tested)", "CDJ-3000X (XDJ-AZ, OPUS-QUAD nicht getestet)"), BADGE("bad", "Open", "Offen")],
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

        H2("No titles, waveforms or beat grid from OneLibrary players", "Keine Titel, Waveforms und kein Beatgrid von OneLibrary-Playern", "onelibrary"),
        TABLE(None, [
            [("**Status**", "**Stand**"), BADGE("bad", "Open", "Offen")],
            [("**Affects**", "**Betrifft**"), ("CDJ-3000X (seen in a user report), XDJ-AZ and OPUS-QUAD (same format, not tested), all LJ Link versions so far", "CDJ-3000X (gesehen in einem Nutzerbericht), XDJ-AZ und OPUS-QUAD (gleiches Format, nicht getestet), alle bisherigen LJ-Link-Versionen")],
        ]),
        P("These players use the newer **OneLibrary** format of rekordbox. That is most likely why titles, covers, waveforms and the beat grid do not arrive: the open-source Pro DJ Link library LJ Link uses (beat-link) cannot read this format yet in the version built in. With the CDJ-3000X, tempo, BPM, time and play/pause do arrive and the tempo goes to the light desk as usual; for XDJ-AZ and OPUS-QUAD this is not tested. Up to 1.7.9 the deck kept showing “Loading track …”; since 1.7.10 it says **Not supported** after 30 seconds (alpha).",
          "Diese Player nutzen das neuere **OneLibrary**-Format von rekordbox. Das ist wahrscheinlich der Grund, warum Titel, Cover, Waveforms und Beatgrid nicht ankommen: Die Open-Source-Bibliothek für Pro DJ Link, die LJ Link nutzt (beat-link), kann dieses Format in der eingebauten Version noch nicht lesen. Bei der CDJ-3000X kommen Tempo, BPM, Zeit und Play/Pause an, und das Tempo geht wie gewohnt ans Lichtpult; bei XDJ-AZ und OPUS-QUAD ist das nicht getestet. Bis 1.7.9 stand im Deck dauerhaft „Lade Track …“; seit 1.7.10 steht dort nach 30 Sekunden **Nicht unterstützt** (Alpha)."),
        P("Support needs a newer version of that library and a test on real players; there is no date yet. Have such players? A [report](wiki-troubleshooting.html#report) with the players connected helps.",
          "Für die Unterstützung braucht es eine neuere Version dieser Bibliothek und einen Test an echten Playern; einen Termin gibt es noch nicht. Du hast solche Player? Ein [Bericht](wiki-troubleshooting.html#report) mit angeschlossenen Playern hilft."),
    )
    return (("Known issues", "Bekannte Fehler"),
            ("Known LJ Link bugs, how to recognise them and how to fix them: Pioneer connection shutting itself down on Windows (fixed in 1.5.1), direct cable with Wi-Fi, no titles or waveforms from CDJ-3000X, XDJ-AZ and OPUS-QUAD.",
             "Bekannte Fehler von LJ Link, woran man sie erkennt und wie man sie behebt: Pioneer-Verbindung schaltet sich unter Windows ab (behoben in 1.5.1), Direktkabel mit WLAN, keine Titel oder Waveforms von CDJ-3000X, XDJ-AZ und OPUS-QUAD."),
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
            [("Web interface", "Weboberfläche"), ("`http://127.0.0.1:8080`, if in use the next free port up to 8089 ([port in use](wiki-troubleshooting.html#port))", "`http://127.0.0.1:8080`, wenn belegt der nächste freie Port bis 8089 ([Port belegt](wiki-troubleshooting.html#port))")],
        ]),
        H2("Command line", "Kommandozeile"),
        P("Start options for experts and scripts:", "Startoptionen für Profis und Skripte:"),
        TABLE([("Option", "Option"), ("Effect", "Wirkung")], [
            ["<code>--port N</code>", ("web interface on port N instead of 8080; if N is in use, the next free port up to N+9. How to start with it: [port in use](wiki-troubleshooting.html#port)", "Weboberfläche auf Port N statt 8080; ist N belegt, der nächste freie Port bis N+9. So startest du damit: [Port belegt](wiki-troubleshooting.html#port)")],
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
