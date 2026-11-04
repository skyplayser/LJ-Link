from wikilib import *

group("DJ sources", "DJ-Quellen")

Y, N, P_ = ("✓", "✓"), ("–", "–"), None


@page("wiki-sources.html", "Overview", "Übersicht")
def _():
    body = SEC(
        H2("What each source shows", "Was jede Quelle zeigt"),
        TABLE([("", ""), ("Pioneer", "Pioneer"), ("Denon", "Denon"), ("Serato", "Serato"), ("Ableton Link", "Ableton Link")], [
            [("Status", "Stand"), BADGE("stable"), BADGE("stable"), BADGE("beta"), BADGE("beta")],
            [("Connection", "Verbindung"), ("network cable", "Netzwerkkabel"), ("cable or Wi-Fi", "Kabel oder WLAN"), ("same computer", "selber Computer"), ("network", "Netzwerk")],
            [("Title, artist", "Titel, Artist"), Y, Y, Y, N],
            [("Waveform", "Waveform"), ("detail and overview, colour", "Detail und Übersicht, Farbe"), ("overview resolution", "Auflösung der Übersicht"), ("overview from the file", "Übersicht aus der Datei"), N],
            [("Beat grid, cues", "Beatgrid, Cues"), Y, Y, Y, N],
            [("Loops", "Loops"), ("CDJ-3000 live, stored ones", "CDJ-3000 live, gespeicherte"), ("not verified", "nicht geprüft"), ("stored ones", "gespeicherte"), N],
            [("Play position", "Abspielposition"), ("CDJ-3000 exact, older ones from beats", "CDJ-3000 genau, ältere aus Beats"), ("exact", "genau"), N, N],
            [("Tempo live", "Tempo live"), Y, Y, ("track BPM from the file", "Track-BPM aus der Datei"), ("session tempo", "Tempo der Session")],
            [("Master, on air", "Master, auf Sendung"), ("master, ON AIR per channel", "Master, ON AIR pro Kanal"), ("master, fader levels", "Master, Fader-Stellungen"), N, N],
            [("Tempo to the desk", "Tempo ans Pult"), Y, Y, ("track BPM", "Track-BPM"), Y],
        ]),
        P("All sources can run at the same time: **Search automatically** looks for all of them, and the decks appear side by side.",
          "Alle Quellen können gleichzeitig laufen: **Automatisch suchen** sucht nach allen, und die Decks erscheinen nebeneinander."),
        CARDS(("🟦", "wiki-pioneer.html", "Pioneer Pro DJ Link", "Pioneer Pro DJ Link", "CDJ, XDJ and DJM over the network.", "CDJ, XDJ und DJM über das Netzwerk."),
              ("🟥", "wiki-denon.html", "Denon StageLinQ", "Denon StageLinQ", "Prime 4+, Prime 4, Prime Go, SC6000.", "Prime 4+, Prime 4, Prime Go, SC6000."),
              ("🟩", "wiki-serato.html", "Serato DJ Pro", "Serato DJ Pro", "Loaded tracks on the same computer.", "Geladene Tracks auf demselben Computer."),
              ("🟨", "wiki-ableton-link.html", "Ableton Link", "Ableton Link", "The tempo of a Link session.", "Das Tempo einer Link-Session.")),
        H2("Planned", "Geplant"),
        TABLE([("Software", "Software"), ("Status", "Stand"), ("Idea", "Idee")], [
            [("VirtualDJ", "VirtualDJ"), BADGE("plan"), ("receive OS2L from VirtualDJ for tempo and beats; later a small plugin for the decks", "OS2L von VirtualDJ empfangen für Tempo und Beats; später ein kleines Plugin für die Decks")],
            [("Traktor", "Traktor"), BADGE("plan"), ("collection file for beat grid and cues, MIDI for the live state", "Collection-Datei für Beatgrid und Cues, MIDI für den Live-Zustand")],
            [("rekordbox (Performance mode, FLX controllers)", "rekordbox (Performance-Modus, FLX-Controller)"), BADGE("plan"), ("library and waveforms from rekordbox files; the live state needs an extra tool", "Bibliothek und Waveforms aus den rekordbox-Dateien; der Live-Zustand braucht ein Zusatzprogramm")],
            [("Waveforms from audio files", "Waveforms aus Audiodateien"), BADGE("plan"), ("compute the waveform from the file for software that does not share one", "die Waveform aus der Datei berechnen, für Software, die keine teilt")],
        ]),
        P("Want one of these? Tell us on [Discord](" + DISCORD + "), it decides what comes first.", "Du willst eins davon? Sag es uns auf [Discord](" + DISCORD + "), das entscheidet, was zuerst kommt."),
    )
    return (("DJ sources: Pioneer, Denon, Serato, Ableton Link", "DJ-Quellen: Pioneer, Denon, Serato, Ableton Link"),
            ("What LJ Link reads from Pioneer Pro DJ Link, Denon StageLinQ, Serato DJ Pro and Ableton Link, compared in one table, plus planned support for VirtualDJ, Traktor and rekordbox.",
             "Was LJ Link von Pioneer Pro DJ Link, Denon StageLinQ, Serato DJ Pro und Ableton Link liest, in einer Tabelle verglichen, dazu geplante Unterstützung für VirtualDJ, Traktor und rekordbox."),
            ("DJ sources", "DJ-Quellen"), ("DJ sources compared", "DJ-Quellen im Vergleich"),
            ("Which DJ gear and software LJ Link reads, and what each one can show.", "Welche DJ-Geräte und Software LJ Link liest, und was jede Quelle zeigen kann."), body)


@page("wiki-pioneer.html", "Pioneer Pro DJ Link", "Pioneer Pro DJ Link")
def _():
    body = SEC(
        H2("Devices", "Geräte"),
        TABLE([("Device", "Gerät"), ("Status", "Stand"), ("Notes", "Hinweise")], [
            ["CDJ-3000", BADGE("stable"), ("status, metadata, waveforms, cues, loops, exact position", "Status, Metadaten, Waveforms, Cues, Loops, genaue Position")],
            ["CDJ-2000NXS2", BADGE("stable"), ("all of it, position from beats and beat grid; no active loop reported", "alles, Position aus Beats und Beatgrid; meldet keinen aktiven Loop")],
            ["DJM-2000NXS", BADGE("stable"), ("as mixer: ON AIR per channel", "als Mixer: ON AIR pro Kanal")],
            ["CDJ-2000NXS, CDJ-900NXS, XDJ-1000MK2, XDJ-XZ, XDJ-AZ", BADGE("unk", "Expected", "Erwartet"), ("same protocol, not tested yet", "gleiches Protokoll, noch nicht getestet")],
            ["DJM-900NXS2, DJM-A9, DJM-V10", BADGE("unk", "Expected", "Erwartet"), ("as mixer, not tested yet", "als Mixer, noch nicht getestet")],
        ]),
        H2("What you need", "Was du brauchst"),
        UL(("Players and mixer **by cable** on one switch, router or the DJM’s LINK sockets. See [setups compared](wiki-network-setups.html).", "Player und Mixer **per Kabel** an einem Switch, Router oder den LINK-Buchsen des DJM. Siehe [Aufbauten im Vergleich](wiki-network-setups.html)."),
           ("Tracks on a **USB stick or SD card exported with rekordbox** (export mode, tracks analysed).", "Tracks auf einem **mit rekordbox exportierten USB-Stick oder einer SD-Karte** (Export-Modus, Tracks analysiert)."),
           ("Every player its own **player number**: on the player *Utility → Player No.*", "Jeder Player eine eigene **Player-Nummer**: am Player *Utility → Player No.*"),
           ("**No rekordbox** on the computer that runs LJ Link.", "**Kein rekordbox** auf dem Computer mit LJ Link.")),
        H2("Step by step", "Schritt für Schritt"),
        STEPS(("Connect all players, the mixer and the computer by cable. Switch Wi-Fi off on the computer.", "Alle Player, den Mixer und den Computer per Kabel verbinden. WLAN am Computer ausschalten."),
              ("Check the player numbers: 1, 2, 3, 4, each only once. The mixer channel of each player should match its number, otherwise ON AIR shows the wrong deck.", "Player-Nummern prüfen: 1, 2, 3, 4, jede nur einmal. Der Mixerkanal jedes Players sollte zu seiner Nummer passen, sonst zeigt ON AIR das falsche Deck."),
              ("Start LJ Link → **Search automatically**. Within a few seconds every player appears, the mixer shows up in the Mixer tab.", "LJ Link starten → **Automatisch suchen**. Nach wenigen Sekunden erscheint jeder Player, der Mixer im Reiter Mixer."),
              ("Load a track. Title, cover and waveforms arrive; with several players this can take a few seconds.", "Track laden. Titel, Cover und Waveforms kommen an; bei mehreren Playern kann das ein paar Sekunden dauern.")),
        ADM("info", ("LJ Link joins the network as a **virtual player** with its own number, like other Pro DJ Link programs do. It only listens and sends no sync, master or tempo commands.",
                     "LJ Link meldet sich im Netz als **virtueller Player** mit eigener Nummer an, wie andere Pro-DJ-Link-Programme auch. Es hört nur zu und schickt keine Sync-, Master- oder Tempo-Befehle.")),
        H2("Limits of Pro DJ Link", "Grenzen von Pro DJ Link"),
        UL(("**Faders and EQ**: no DJM sends them. The blue bar only shows on air or off.", "**Fader und EQ**: Kein DJM sendet sie. Der blaue Balken zeigt nur auf Sendung oder aus."),
           ("**ON AIR** also needs sound at the master output and matching channel numbers.", "**ON AIR** braucht auch Ton am Master-Ausgang und passende Kanalnummern."),
           ("**Position on NXS2 and older**: they report only between beats. LJ Link runs them at their pitch, so the waveform moves smoothly; after a cue or loop there can be small jumps. Scratching is shown only roughly.", "**Position bei NXS2 und älter**: Sie melden nur zwischen den Beats. LJ Link lässt sie mit ihrem Pitch laufen, die Waveform läuft also flüssig; nach einem Cue oder Loop kann es kleine Sprünge geben. Scratchen wird nur grob gezeigt."),
           ("**Streaming tracks** (Beatport, SoundCloud, TIDAL …), CDs and never analysed files have no waveform data.", "**Streaming-Tracks** (Beatport, SoundCloud, TIDAL …), CDs und nie analysierte Dateien haben keine Waveform-Daten."),
           ("**CDJ-3000 with new firmware**: if a stick from an older rekordbox shows no waveform, export it again with rekordbox 6 or 7.", "**CDJ-3000 mit neuer Firmware**: Zeigt ein Stick aus einem älteren rekordbox keine Waveform, mit rekordbox 6 oder 7 neu exportieren.")),
        H2("Problems", "Probleme"),
        DETAILS("A player appears twice", "Ein Player erscheint doppelt",
                UL(("Wi-Fi and cable in the same network: switch Wi-Fi off.", "WLAN und Kabel im selben Netz: WLAN ausschalten."),
                   ("Two players with the same number, or LJ Link started twice.", "Zwei Player mit derselben Nummer, oder LJ Link doppelt gestartet."),
                   ("Virtual adapters (VPN, Parallels, VMware, Docker): switch them off.", "Virtuelle Adapter (VPN, Parallels, VMware, Docker): ausschalten."))),
        DETAILS("“No track loaded” or no waveform", "„No track loaded“ oder keine Waveform",
                UL(("Update to LJ Link 1.4.1 or newer.", "Auf LJ Link 1.4.1 oder neuer aktualisieren."),
                   ("Track from a rekordbox export? Streaming and CD have no data.", "Track aus einem rekordbox-Export? Streaming und CD haben keine Daten."),
                   ("rekordbox on this computer? Quit it.", "rekordbox auf diesem Computer? Beenden."))),
        DETAILS("Yellow note “Beat port 50001 is in use”", "Gelber Hinweis „Beat port 50001 is in use“",
                P("rekordbox, Beat Link Trigger, SoundSwitch or a second LJ Link listens for beats. Close it and search again. Until then positions are estimated.", "rekordbox, Beat Link Trigger, SoundSwitch oder ein zweites LJ Link hört auf Beats. Schließen und erneut suchen. Bis dahin werden Positionen geschätzt.")),
        P("More: [troubleshooting](wiki-troubleshooting.html).", "Mehr: [Fehlersuche](wiki-troubleshooting.html)."),
    )
    return (("Pioneer Pro DJ Link: CDJ, XDJ and DJM", "Pioneer Pro DJ Link: CDJ, XDJ und DJM"),
            ("Connect Pioneer CDJ-3000, CDJ-2000NXS2, XDJ and DJM mixers to LJ Link over Pro DJ Link: network, player numbers, rekordbox export, limits and problems.",
             "Pioneer CDJ-3000, CDJ-2000NXS2, XDJ und DJM-Mischpulte über Pro DJ Link mit LJ Link verbinden: Netzwerk, Player-Nummern, rekordbox-Export, Grenzen und Probleme."),
            ("DJ sources", "DJ-Quellen"), ("Pioneer Pro DJ Link", "Pioneer Pro DJ Link"),
            ("CDJs, XDJs and DJM mixers over the network.", "CDJs, XDJs und DJM-Mischpulte über das Netzwerk."), body)


@page("wiki-denon.html", "Denon StageLinQ", "Denon StageLinQ")
def _():
    body = SEC(
        H2("Devices", "Geräte"),
        TABLE([("Device", "Gerät"), ("Status", "Stand"), ("Notes", "Hinweise")], [
            ["Prime 4+ (firmware 5.0.4)", BADGE("stable"), ("title, artist, tempo, play state, master, position, channel faders, waveform, beat grid, cues; loops not verified", "Titel, Artist, Tempo, Spielstatus, Master, Position, Kanalfader, Waveform, Beatgrid, Cues; Loops nicht geprüft")],
            ["SC6000, SC6000M, Prime 4, Prime Go, Prime 2", BADGE("unk", "Expected", "Erwartet"), ("same protocol, not tested yet", "gleiches Protokoll, noch nicht getestet")],
        ]),
        H2("Connecting", "Verbinden"),
        P("Denon devices speak **StageLinQ**, the same interface Engine DJ and SoundSwitch use. The device must be in the same network as the computer, by cable or Wi-Fi.",
          "Denon-Geräte sprechen **StageLinQ**, dieselbe Schnittstelle, die Engine DJ und SoundSwitch nutzen. Das Gerät muss im selben Netz wie der Computer sein, per Kabel oder WLAN."),
        STEPS(("Connect the device by cable to the switch or router (recommended), or join both to the same Wi-Fi.", "Das Gerät per Kabel mit dem Switch oder Router verbinden (empfohlen), oder beide ins selbe WLAN bringen."),
              ("Close **Engine DJ**, SoundSwitch and other StageLinQ programs on the computer that runs LJ Link.", "**Engine DJ**, SoundSwitch und andere StageLinQ-Programme auf dem Computer mit LJ Link schließen."),
              ("**Search automatically**. The device appears with all its decks; the first time LJ Link loads its library, which can take a moment.", "**Automatisch suchen**. Das Gerät erscheint mit allen Decks; beim ersten Mal lädt LJ Link seine Bibliothek, das kann einen Moment dauern.")),
        H2("What you get", "Was du bekommst"),
        UL(("**Real fader positions** in the blue bar (Pioneer cannot do that). EQ is not sent.", "**Echte Fader-Stellungen** im blauen Balken (das kann Pioneer nicht). EQ wird nicht gesendet."),
           ("**Exact position** of every deck.", "**Genaue Position** jedes Decks."),
           ("**Waveform** with beat grid, bar starts, hot cues and loops from the Engine library on the device’s drive or card, loaded once. It has overview resolution; the detail view is smoothed from it.", "**Waveform** mit Beatgrid, Taktanfängen, Hot Cues und Loops aus der Engine-Bibliothek auf dem Laufwerk oder der Karte des Geräts, einmal geladen. Sie hat die Auflösung der Übersicht; die Detailansicht ist daraus geglättet."),
           ("Covers only if the library stores them.", "Cover nur, wenn die Bibliothek welche enthält.")),
        H2("Problems", "Probleme"),
        DETAILS("The device is not found", "Das Gerät wird nicht gefunden",
                UL(("Same network? Check the addresses ([basics](wiki-network.html)).", "Gleiches Netz? Adressen prüfen ([Grundlagen](wiki-network.html))."),
                   ("Engine DJ or SoundSwitch open on this computer? Close them, they use the same port.", "Engine DJ oder SoundSwitch auf diesem Computer offen? Schließen, sie nutzen denselben Port."),
                   ("Guest Wi-Fi or a mesh repeater? Use cable.", "Gast-WLAN oder Mesh-Repeater? Kabel nehmen."))),
        DETAILS("It keeps reconnecting", "Es verbindet sich ständig neu",
                P("Update to LJ Link 1.3.1 or newer. After a broken connection the device needs a few seconds before it accepts a new one; that is normal.", "Auf LJ Link 1.3.1 oder neuer aktualisieren. Nach einem Abbruch braucht das Gerät einige Sekunden, bis es eine neue Verbindung annimmt; das ist normal.")),
        DETAILS("No waveform for a track", "Keine Waveform für einen Track",
                P("The track must be analysed in Engine DJ or on the device. Tracks from streaming services have no stored waveform.", "Der Track muss in Engine DJ oder am Gerät analysiert sein. Tracks von Streaming-Diensten haben keine gespeicherte Waveform.")),
    )
    return (("Denon StageLinQ: Prime 4+, SC6000, Prime Go", "Denon StageLinQ: Prime 4+, SC6000, Prime Go"),
            ("Connect Denon DJ Prime 4+, Prime 4, Prime Go and SC6000 to LJ Link over StageLinQ: cable or Wi-Fi, fader positions, waveforms from the Engine library, problems.",
             "Denon DJ Prime 4+, Prime 4, Prime Go und SC6000 über StageLinQ mit LJ Link verbinden: Kabel oder WLAN, Fader-Stellungen, Waveforms aus der Engine-Bibliothek, Probleme."),
            ("DJ sources", "DJ-Quellen"), ("Denon StageLinQ", "Denon StageLinQ"),
            ("Engine DJ devices with real fader positions.", "Engine-DJ-Geräte mit echten Fader-Stellungen."), body)


@page("wiki-serato.html", "Serato DJ Pro", "Serato DJ Pro")
def _():
    body = SEC(
        ADM("beta", ("Tested with Serato DJ Pro 3.x on macOS with MP3 and AIFF. Windows is expected to work but not tested yet.",
                     "Getestet mit Serato DJ Pro 3.x unter macOS mit MP3 und AIFF. Windows sollte gehen, ist aber noch nicht getestet.")),
        H2("How it works", "So funktioniert es"),
        P("Serato has no live interface for other programs. While it runs, it writes a log that names the file on each deck. LJ Link follows this log and reads title, artist, BPM, key, length, waveform overview, beat grid and cues from the tags Serato writes into the file.",
          "Serato hat keine Live-Schnittstelle für andere Programme. Während es läuft, schreibt es ein Protokoll, das die Datei auf jedem Deck nennt. LJ Link folgt diesem Protokoll und liest Titel, Artist, BPM, Tonart, Länge, Waveform-Übersicht, Beatgrid und Cues aus den Tags, die Serato in die Datei schreibt."),
        ADM("warn", ("That is why **LJ Link must run on the same computer as Serato.** It does not work over the network.",
                     "Deshalb **muss LJ Link auf demselben Computer wie Serato laufen.** Über das Netzwerk geht es nicht.")),
        H2("Step by step", "Schritt für Schritt"),
        STEPS(("Install LJ Link on the DJ’s laptop and start Serato DJ Pro.", "LJ Link auf dem Laptop des DJs installieren und Serato DJ Pro starten."),
              ("**Search automatically.** As soon as a track is loaded, its deck appears.", "**Automatisch suchen.** Sobald ein Track geladen ist, erscheint sein Deck."),
              ("The lighting jockey watches on a tablet or a second computer: open the address shown under the toolbar in the same network. See [Serato on the DJ laptop](wiki-case-serato.html).", "Der Lichtjockey schaut auf einem Tablet oder zweiten Computer zu: die Adresse unter der Werkzeugleiste im selben Netz öffnen. Siehe [Serato auf dem DJ-Laptop](wiki-case-serato.html).")),
        H2("Limits", "Grenzen"),
        UL(("**No play position**: Serato does not reveal it, so there is no moving playhead.", "**Keine Abspielposition**: Serato verrät sie nicht, deshalb gibt es keinen laufenden Playhead."),
           ("**Tempo** is the track BPM from the file, not the pitched tempo. For the live tempo switch on Ableton Link in Serato ([Ableton Link](wiki-ableton-link.html)).", "**Tempo** ist das Track-BPM aus der Datei, nicht das gepitchte Tempo. Für das Live-Tempo in Serato Ableton Link einschalten ([Ableton Link](wiki-ableton-link.html))."),
           ("**FLAC and M4A** are not read yet. Files without Serato tags show only the file name.", "**FLAC und M4A** werden noch nicht gelesen. Dateien ohne Serato-Tags zeigen nur den Dateinamen."),
           ("No on-air or master information.", "Keine Information zu On Air oder Master.")),
    )
    return (("Serato DJ Pro", "Serato DJ Pro"),
            ("Use LJ Link with Serato DJ Pro on the same computer: loaded tracks, BPM, key, waveform overview, beat grid and cues from the file tags, and the limits.",
             "LJ Link mit Serato DJ Pro auf demselben Computer: geladene Tracks, BPM, Tonart, Waveform-Übersicht, Beatgrid und Cues aus den Datei-Tags, und die Grenzen."),
            ("DJ sources", "DJ-Quellen"), ("Serato DJ Pro", "Serato DJ Pro"),
            ("Loaded tracks with waveform and beat grid, on the DJ’s computer.", "Geladene Tracks mit Waveform und Beatgrid, auf dem Computer des DJs."), body)


@page("wiki-ableton-link.html", "Ableton Link", "Ableton Link")
def _():
    body = SEC(
        ADM("beta", ("Built from Ableton’s published protocol and tested against it, but not yet tried with a real session at a gig.",
                     "Nach dem veröffentlichten Protokoll von Ableton gebaut und dagegen getestet, aber noch nicht mit einer echten Session auf einem Gig ausprobiert.")),
        H2("What it is", "Was es ist"),
        P("Ableton Link keeps the tempo of several programs in step over the network. Serato DJ Pro, Traktor, Ableton Live and many apps support it. LJ Link listens to the session and takes its **tempo**; it never joins or changes the session.",
          "Ableton Link hält das Tempo mehrerer Programme über das Netzwerk im Gleichschritt. Serato DJ Pro, Traktor, Ableton Live und viele Apps unterstützen es. LJ Link hört der Session zu und nimmt ihr **Tempo**; es tritt ihr nie bei und ändert nichts."),
        H2("When to use it", "Wann du es nutzt"),
        UL(("**Traktor** or another program without its own support: tempo to the light desk.", "**Traktor** oder ein anderes Programm ohne eigene Unterstützung: Tempo ans Lichtpult."),
           ("**Serato**: the live tempo with pitch, in addition to the track data from the file.", "**Serato**: das Live-Tempo mit Pitch, zusätzlich zu den Trackdaten aus der Datei."),
           ("**Live sets** with Ableton Live.", "**Live-Sets** mit Ableton Live.")),
        H2("Step by step", "Schritt für Schritt"),
        STEPS(("In the DJ software switch on **Link** (Serato: *Setup → Expansion Packs → Ableton Link*, Traktor: *Preferences → Link*, Live: *Link* button).", "In der DJ-Software **Link** einschalten (Serato: *Setup → Expansion Packs → Ableton Link*, Traktor: *Preferences → Link*, Live: Knopf *Link*)."),
              ("LJ Link runs on the same computer or in the same network.", "LJ Link läuft auf demselben Computer oder im selben Netz."),
              ("**Search automatically.** A deck *Ableton Link* appears with the session tempo.", "**Automatisch suchen.** Ein Deck *Ableton Link* erscheint mit dem Tempo der Session."),
              ("Under **SEND BPM** choose this deck, if the tempo should come from Link.", "Unter **SEND BPM** dieses Deck wählen, wenn das Tempo von Link kommen soll.")),
        H2("Limits", "Grenzen"),
        UL(("Tempo only: no track, no waveform, no position.", "Nur Tempo: kein Track, keine Waveform, keine Position."),
           ("The beat phase is not read yet, so a Wolfmix gets free running beats at this tempo.", "Die Beat-Phase wird noch nicht gelesen, ein Wolfmix bekommt also frei laufende Beats in diesem Tempo."),
           ("rekordbox may only follow a Link session and not lead it; then it gives no tempo.", "rekordbox folgt einer Link-Session eventuell nur und führt sie nicht; dann liefert es kein Tempo.")),
    )
    return (("Ableton Link tempo", "Ableton-Link-Tempo"),
            ("Take the tempo from an Ableton Link session (Serato DJ Pro, Traktor, Ableton Live) in LJ Link and send it to the light desk. Setup and limits.",
             "Das Tempo einer Ableton-Link-Session (Serato DJ Pro, Traktor, Ableton Live) in LJ Link übernehmen und ans Lichtpult schicken. Einrichtung und Grenzen."),
            ("DJ sources", "DJ-Quellen"), ("Ableton Link", "Ableton Link"),
            ("The tempo of Serato, Traktor, Live and many apps.", "Das Tempo von Serato, Traktor, Live und vielen Apps."), body)
