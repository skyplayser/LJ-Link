from wikilib import *

group("Use cases", "Anwendungsfälle")


def facts(rows):
    """Short table at the top of every use case."""
    return TABLE(None, rows, cls="facts")


def CHECK(*items):
    return ADM("tip", UL(*items), title=("Checklist before the doors open", "Checkliste vor Einlass"))


CASES = [
    ("wiki-case-club.html", "🏛️", "Club with CDJs and grandMA2 at FOH", "Club mit CDJs und grandMA2 am FOH", "The classic: Pioneer booth, light desk at front of house, tempo on a speed master.", "Der Klassiker: Pioneer-Booth, Lichtpult am FOH, Tempo auf einem Speedmaster."),
    ("wiki-case-onpc.html", "💻", "One laptop with onPC", "Ein Laptop mit onPC", "LJ Link and grandMA2/grandMA3 onPC on the same computer.", "LJ Link und grandMA2/grandMA3 onPC auf demselben Computer."),
    ("wiki-case-mixed.html", "🔀", "Several DJs, mixed gear", "Mehrere DJs, gemischte Geräte", "Pioneer and Denon in one night, changeovers, who gives the tempo.", "Pioneer und Denon an einem Abend, Wechsel, wer das Tempo gibt."),
    ("wiki-case-mobile.html", "🚐", "Mobile with Denon and Wolfmix", "Mobil mit Denon und Wolfmix", "Small setup with one laptop, Wi-Fi and a Wolfmix on USB.", "Kleiner Aufbau mit einem Laptop, WLAN und einem Wolfmix an USB."),
    ("wiki-case-serato.html", "🟩", "Serato on the DJ laptop", "Serato auf dem DJ-Laptop", "LJ Link next to Serato, the LJ watches on a tablet.", "LJ Link neben Serato, der LJ schaut auf dem Tablet zu."),
    ("wiki-case-tablet.html", "📱", "Waveforms on a tablet", "Waveforms auf dem Tablet", "No desk link, just seeing the drops at the desk.", "Kein Pult-Link, nur die Drops am Pult sehen."),
    ("wiki-case-two-networks.html", "🧩", "Two networks", "Zwei Netzwerke", "Keep MA-Net, Art-Net or sACN apart from the DJ network.", "MA-Net, Art-Net oder sACN vom DJ-Netz getrennt halten."),
    ("wiki-case-direct.html", "🔌", "Direct cable", "Direktkabel", "One cable, no switch: what works and what to watch out for.", "Ein Kabel, kein Switch: was geht und worauf du achten musst."),
    ("wiki-case-stream.html", "🎥", "Livestream and privacy mode", "Livestream und Privatsphäre-Modus", "Hide titles and covers on screens, streams and photos.", "Titel und Cover auf Bildschirmen, Streams und Fotos verbergen."),
]


@page("wiki-use-cases.html", "Overview", "Übersicht")
def _():
    body = SEC(
        P("Each page describes one complete setup from the cables to the first beat on the desk: what you need, a diagram, the steps in order, a checklist and the typical problems.",
          "Jede Seite beschreibt einen kompletten Aufbau vom Kabel bis zum ersten Beat am Pult: was du brauchst, ein Diagramm, die Schritte in der richtigen Reihenfolge, eine Checkliste und die typischen Probleme."),
        H2("All use cases", "Alle Anwendungsfälle"),
        CARDS(*[(i, f, te, td, pe, pd) for f, i, te, td, pe, pd in CASES]),
        H2("Quick decision", "Schnell entschieden"),
        TABLE([("Question", "Frage"), ("Answer", "Antwort")], [
            [("Direct cable or router?", "Direktkabel oder Router?"), ("**Router or switch**, always when you can. Direct cable only for one player. [Compared](wiki-network-setups.html)", "**Router oder Switch**, immer wenn es geht. Direktkabel nur für einen Player. [Vergleich](wiki-network-setups.html)")],
            [("Wi-Fi or cable?", "WLAN oder Kabel?"), ("**Cable** for Pioneer and the computer. Wi-Fi is fine for tablets and Denon.", "**Kabel** für Pioneer und den Computer. WLAN geht für Tablets und Denon.")],
            [("Where does LJ Link run?", "Wo läuft LJ Link?"), ("On a computer in the DJ network. With Serato: on the DJ’s laptop.", "Auf einem Computer im DJ-Netz. Mit Serato: auf dem Laptop des DJs.")],
            [("Which desk works best?", "Welches Pult geht am besten?"), ("grandMA2, onPC and Daslight are tested. grandMA3 and Wolfmix are beta.", "grandMA2, onPC und Daslight sind getestet. grandMA3 und Wolfmix sind Beta.")],
            [("Several floors?", "Mehrere Floors?"), ("One LJ Link computer per floor, and under Settings → **Devices** tick only the devices of that floor.", "Ein LJ-Link-Computer pro Floor, und unter Einstellungen → **Geräte** nur die Geräte dieses Floors anhaken.")],
        ]),
    )
    return (("Use cases: complete setups for club, festival, mobile and stream", "Anwendungsfälle: komplette Aufbauten für Club, Festival, mobil und Stream"),
            ("LJ Link use cases step by step: club with CDJs and grandMA2, onPC on one laptop, mixed Pioneer and Denon, mobile with Wolfmix, Serato, tablet, two networks, direct cable, livestream.",
             "LJ-Link-Anwendungsfälle Schritt für Schritt: Club mit CDJs und grandMA2, onPC auf einem Laptop, gemischt Pioneer und Denon, mobil mit Wolfmix, Serato, Tablet, zwei Netzwerke, Direktkabel, Livestream."),
            ("Use cases", "Anwendungsfälle"), ("Use cases", "Anwendungsfälle"),
            ("Find the setup that matches your gig.", "Finde den Aufbau, der zu deinem Gig passt."), body)


@page("wiki-case-club.html", "Club with CDJs and grandMA2", "Club mit CDJs und grandMA2")
def _():
    body = SEC(
        facts([[("**Gear**", "**Geräte**"), ("2–4 CDJ/XDJ, DJM, grandMA2 console or onPC at FOH", "2–4 CDJ/XDJ, DJM, grandMA2-Pult oder onPC am FOH")],
               [("**Network**", "**Netzwerk**"), ("one switch or router in the booth, one long cable to FOH", "ein Switch oder Router in der Booth, ein langes Kabel zum FOH")],
               [("**Result**", "**Ergebnis**"), ("waveforms at FOH, tempo on Speed 16 (BPM)", "Waveforms am FOH, Tempo auf Speed 16 (BPM)")],
               [("**Status**", "**Stand**"), BADGE("stable")]]),
        DIAGRAM(720, 300, {"sw": (360, 140, 150, 50, "net", ("Switch / router", "Switch / Router"), ("in the booth", "in der Booth")),
                           "c1": (70, 45, 100, 46, "dj", ("CDJ 1", "CDJ 1"), None), "c2": (200, 45, 100, 46, "dj", ("CDJ 2", "CDJ 2"), None),
                           "c3": (520, 45, 100, 46, "dj", ("CDJ 3", "CDJ 3"), None), "c4": (650, 45, 100, 46, "dj", ("CDJ 4", "CDJ 4"), None),
                           "djm": (120, 235, 100, 46, "dj", ("DJM", "DJM"), None),
                           "pc": (450, 255, 140, 54, "pc", ("Laptop at FOH", "Laptop am FOH"), ("LJ Link", "LJ Link")),
                           "ma": (640, 255, 120, 50, "desk", ("grandMA2", "grandMA2"), None)},
                [("c1", "sw", "cable", None), ("c2", "sw", "cable", None), ("c3", "sw", "cable", None), ("c4", "sw", "cable", None),
                 ("djm", "sw", "cable", None), ("pc", "sw", "cable", ("long cable", "langes Kabel")), ("ma", "sw", "cable", None)],
                ("The desk can hang on the same switch, or on its own network (see two networks).", "Das Pult kann am selben Switch hängen oder in einem eigenen Netz (siehe Zwei Netzwerke).")),
        LEGEND,
        H2("What you need", "Was du brauchst"),
        UL(("A switch or router in the booth with a free port, or a free LINK socket on the DJM.", "Einen Switch oder Router in der Booth mit freiem Port, oder eine freie LINK-Buchse am DJM."),
           ("A network cable to FOH. Up to 100 m is fine for Ethernet; use a good Cat5e/Cat6 cable.", "Ein Netzwerkkabel zum FOH. Bis 100 m ist für Ethernet in Ordnung; ein gutes Cat5e/Cat6-Kabel nehmen."),
           ("A laptop with LJ Link (and a USB network adapter if it has no socket).", "Einen Laptop mit LJ Link (und einen USB-Netzwerkadapter, falls er keine Buchse hat)."),
           ("Access to the grandMA2 show: Telnet on, a user with rights.", "Zugang zur grandMA2-Show: Telnet an, ein Benutzer mit Rechten.")),
        H2("Steps", "Schritte"),
        STEPS(("**Booth:** check that all players and the DJM are on the switch and have different player numbers.", "**Booth:** prüfen, dass alle Player und der DJM am Switch hängen und verschiedene Player-Nummern haben."),
              ("**Cable to FOH:** plug the long cable into the switch and the laptop. Switch Wi-Fi off on the laptop.", "**Kabel zum FOH:** das lange Kabel in den Switch und den Laptop stecken. WLAN am Laptop ausschalten."),
              ("**Search:** start LJ Link, **Search automatically**. All players and the mixer appear.", "**Suchen:** LJ Link starten, **Automatisch suchen**. Alle Player und der Mixer erscheinen."),
              ("**Desk in the network:** plug the grandMA2 into the same switch, or use its own network ([two networks](wiki-case-two-networks.html)). Check that addresses fit ([basics](wiki-network.html)).", "**Pult ins Netz:** den grandMA2 an denselben Switch, oder ein eigenes Netz nutzen ([Zwei Netzwerke](wiki-case-two-networks.html)). Prüfen, dass die Adressen passen ([Grundlagen](wiki-network.html))."),
              ("**Desk:** Setup → Console → Global Settings → Telnet **Login Enabled**. Effects that should follow the tempo on **Speed 16 (BPM)**.", "**Pult:** Setup → Console → Global Settings → Telnet **Login Enabled**. Effekte, die dem Tempo folgen sollen, auf **Speed 16 (BPM)**."),
              ("**LJ Link:** LINK → grandMA2 → **Search for the desk** → click it → user/password → **Save** → **Send test**. The master jumps to 120.", "**LJ Link:** LINK → grandMA2 → **Pult suchen** → anklicken → Benutzer/Passwort → **Speichern** → **Test senden**. Der Master springt auf 120."),
              ("**SEND BPM** on **Auto**: the tempo follows the master deck that is on air.", "**SEND BPM** auf **Auto**: Das Tempo folgt dem Master-Deck, das auf Sendung ist.")),
        CHECK(("Every player shows a track and a waveform.", "Jeder Player zeigt einen Track und eine Waveform."),
              ("The red LIVE frame follows the channel that is up.", "Der rote LIVE-Rahmen folgt dem Kanal, der offen ist."),
              ("Green SEND BPM sits on the right deck, the master on the desk follows a tempo change.", "Grünes SEND BPM sitzt auf dem richtigen Deck, der Master am Pult folgt einer Tempoänderung."),
              ("Laptop on mains power, sleep mode off.", "Laptop am Netzteil, Ruhezustand aus.")),
        H2("During the night", "Während der Nacht"),
        UL(("A DJ changeover with new players: they are added by themselves; old ones disappear after a few seconds.", "Ein DJ-Wechsel mit neuen Playern: Sie kommen von selbst dazu; alte verschwinden nach wenigen Sekunden."),
           ("Tempo jumps between two decks (both master)? Fix SEND BPM to one deck for this moment.", "Tempo springt zwischen zwei Decks (beide Master)? SEND BPM für diesen Moment auf ein Deck festlegen."),
           ("Want your effects to follow only in parts of the set? Leave the link on and switch the effect to another speed master on the desk.", "Effekte sollen nur in Teilen des Sets folgen? Den Link anlassen und am Pult den Effekt auf einen anderen Speedmaster legen.")),
        H2("Typical problems", "Typische Probleme"),
        TABLE([("Problem", "Problem"), ("Fix", "Lösung")], [
            [("players at the booth seen, desk not reachable", "Player in der Booth sichtbar, Pult nicht erreichbar"), ("desk in another address range: [IP address](wiki-network-ip.html) or [two networks](wiki-case-two-networks.html)", "Pult in einem anderen Adressbereich: [IP-Adresse](wiki-network-ip.html) oder [Zwei Netzwerke](wiki-case-two-networks.html)")],
            [("players appear twice", "Player erscheinen doppelt"), ("Wi-Fi off, only one cable", "WLAN aus, nur ein Kabel")],
            [("master changes, effect does not", "Master ändert sich, Effekt nicht"), ("effect assigned to Speed 16?", "Effekt auf Speed 16 gelegt?")],
        ]),
    )
    return (("Use case: club with CDJs and grandMA2 at FOH", "Anwendungsfall: Club mit CDJs und grandMA2 am FOH"),
            ("Complete setup for a club: Pioneer CDJs and DJM in the booth, LJ Link at front of house, tempo on the grandMA2 speed master over Telnet. Steps and checklist.",
             "Kompletter Aufbau für einen Club: Pioneer CDJs und DJM in der Booth, LJ Link am FOH, Tempo über Telnet auf den Speedmaster des grandMA2. Schritte und Checkliste."),
            ("Use case", "Anwendungsfall"), ("Club with CDJs and grandMA2", "Club mit CDJs und grandMA2"),
            ("The classic club setup, from the booth to the speed master.", "Der klassische Club-Aufbau, von der Booth bis zum Speedmaster."), body)


@page("wiki-case-onpc.html", "One laptop with onPC", "Ein Laptop mit onPC")
def _():
    body = SEC(
        facts([[("**Gear**", "**Geräte**"), ("DJ gear, one laptop with LJ Link and grandMA2 or grandMA3 onPC, maybe an MA node or wing", "DJ-Geräte, ein Laptop mit LJ Link und grandMA2 oder grandMA3 onPC, eventuell ein MA-Node oder Wing")],
               [("**Network**", "**Netzwerk**"), ("the laptop in the DJ network; desk address `127.0.0.1`", "der Laptop im DJ-Netz; Pult-Adresse `127.0.0.1`")],
               [("**Status**", "**Stand**"), BADGE("stable", "grandMA2 onPC stable", "grandMA2 onPC stabil") + " " + BADGE("beta", "grandMA3 onPC beta", "grandMA3 onPC Beta")]]),
        DIAGRAM(720, 170, {"dj": (110, 85, 130, 50, "dj", ("DJ gear", "DJ-Geräte"), None),
                           "sw": (330, 85, 110, 46, "net", ("Switch", "Switch"), None),
                           "pc": (580, 85, 200, 70, "pc", ("Laptop", "Laptop"), ("LJ Link → 127.0.0.1 → onPC", "LJ Link → 127.0.0.1 → onPC"))},
                [("dj", "sw", "cable", None), ("sw", "pc", "cable", None)],
                ("The tempo never leaves the laptop: no desk network needed.", "Das Tempo verlässt den Laptop nie: kein Pult-Netz nötig.")),
        H2("Why this is easy", "Warum das einfach ist"),
        P("LJ Link and onPC on the same computer talk over `127.0.0.1` (the computer itself). No address, no firewall between them, no second network. Only the DJ gear has to be reachable.",
          "LJ Link und onPC auf demselben Computer sprechen über `127.0.0.1` (der Computer selbst). Keine Adresse, keine Firewall dazwischen, kein zweites Netz. Nur die DJ-Geräte müssen erreichbar sein."),
        H2("Steps with grandMA2 onPC", "Schritte mit grandMA2 onPC"),
        STEPS(("Laptop by cable into the DJ network, Wi-Fi off. LJ Link → **Search automatically**.", "Laptop per Kabel ins DJ-Netz, WLAN aus. LJ Link → **Automatisch suchen**."),
              ("onPC: Setup → Console → Global Settings → Telnet **Login Enabled**.", "onPC: Setup → Console → Global Settings → Telnet **Login Enabled**."),
              ("LJ Link: LINK → grandMA2 → address `127.0.0.1`, port `30000`, `administrator` / `admin` (or your user) → **Save** → **Send test**.", "LJ Link: LINK → grandMA2 → Adresse `127.0.0.1`, Port `30000`, `administrator` / `admin` (oder dein Benutzer) → **Speichern** → **Test senden**.")),
        H2("Steps with grandMA3 onPC", "Schritte mit grandMA3 onPC"),
        STEPS(("onPC: Menu → In & Out → OSC: interface **127.0.0.1** (or the loopback entry), port `8000`, Receive Command **Yes**, prefix empty, **Enable Input**.", "onPC: Menu → In & Out → OSC: Interface **127.0.0.1** (oder der Loopback-Eintrag), Port `8000`, Receive Command **Yes**, Prefix leer, **Enable Input**."),
              ("LJ Link: LINK → grandMA3 → `127.0.0.1`, `8000` → **Save** → **Send test**.", "LJ Link: LINK → grandMA3 → `127.0.0.1`, `8000` → **Speichern** → **Test senden**.")),
        ADM("note", ("If the grandMA3 OSC interface list has no loopback entry, choose the network port the laptop uses and enter that address in LJ Link instead of `127.0.0.1`. Not tested yet; please tell us what your onPC shows.",
                     "Hat die OSC-Interface-Liste des grandMA3 keinen Loopback-Eintrag, den Netzwerkanschluss des Laptops wählen und dessen Adresse statt `127.0.0.1` in LJ Link eintragen. Noch nicht getestet; sag uns bitte, was dein onPC anzeigt.")),
        H2("Performance", "Leistung"),
        UL(("LJ Link needs little CPU, but onPC with a big show and a 3D view needs a lot. Close the browser tab of LJ Link on the laptop if you only watch on a tablet.", "LJ Link braucht wenig Rechenleistung, onPC mit großer Show und 3D-Ansicht aber viel. Den Browser-Tab von LJ Link auf dem Laptop schließen, wenn du nur auf dem Tablet schaust."),
           ("Laptop on mains power, energy saving off; on Windows choose the *High performance* power plan.", "Laptop am Netzteil, Energiesparen aus; unter Windows den Energieplan *Höchstleistung* wählen."),
           ("An MA node or Art-Net out runs on a second network port, see [two networks](wiki-case-two-networks.html).", "Ein MA-Node oder Art-Net-Ausgang läuft über einen zweiten Netzwerkanschluss, siehe [Zwei Netzwerke](wiki-case-two-networks.html).")),
        CHECK(("Test sent → master shows 120.", "Test gesendet → Master zeigt 120."), ("Effect assigned to that master.", "Effekt diesem Master zugeordnet."), ("Show saved (with the user for LJ Link).", "Show gespeichert (mit dem Benutzer für LJ Link).")),
    )
    return (("Use case: LJ Link and grandMA onPC on one laptop", "Anwendungsfall: LJ Link und grandMA onPC auf einem Laptop"),
            ("Run LJ Link and grandMA2 or grandMA3 onPC on the same laptop: tempo over 127.0.0.1, Telnet and OSC settings, performance tips and checklist.",
             "LJ Link und grandMA2 oder grandMA3 onPC auf demselben Laptop: Tempo über 127.0.0.1, Telnet- und OSC-Einstellungen, Leistungstipps und Checkliste."),
            ("Use case", "Anwendungsfall"), ("One laptop with onPC", "Ein Laptop mit onPC"),
            ("The simplest desk link: everything on one computer.", "Der einfachste Pult-Link: alles auf einem Computer."), body)


@page("wiki-case-mixed.html", "Several DJs, mixed gear", "Mehrere DJs, gemischte Geräte")
def _():
    body = SEC(
        facts([[("**Gear**", "**Geräte**"), ("house CDJs and DJM, plus a guest DJ with a Denon Prime 4+ or an XDJ-AZ", "Haus-CDJs und DJM, dazu ein Gast-DJ mit Denon Prime 4+ oder XDJ-AZ")],
               [("**Network**", "**Netzwerk**"), ("everything on one switch or router", "alles an einem Switch oder Router")],
               [("**Result**", "**Ergebnis**"), ("all decks side by side, tempo from whoever plays", "alle Decks nebeneinander, Tempo von dem, der spielt")],
               [("**Status**", "**Stand**"), BADGE("stable")]]),
        DIAGRAM(720, 230, {"sw": (360, 120, 160, 50, "net", ("Switch / router", "Switch / Router"), None),
                           "c1": (90, 45, 110, 46, "dj", ("CDJ 1–2", "CDJ 1–2"), None), "djm": (90, 195, 110, 46, "dj", ("DJM", "DJM"), None),
                           "den": (620, 45, 150, 50, "dj", ("Denon Prime 4+", "Denon Prime 4+"), ("guest DJ", "Gast-DJ")),
                           "pc": (620, 195, 130, 50, "pc", ("Computer", "Computer"), ("LJ Link", "LJ Link"))},
                [("c1", "sw", "cable", None), ("djm", "sw", "cable", None), ("den", "sw", "cable", ("cable", "Kabel")), ("pc", "sw", "cable", None)]),
        H2("How LJ Link handles it", "Wie LJ Link damit umgeht"),
        UL(("One **Search automatically** finds Pioneer and Denon at the same time and keeps looking while it runs.", "Ein **Automatisch suchen** findet Pioneer und Denon gleichzeitig und sucht weiter, solange es läuft."),
           ("A device that comes later is added; one that leaves disappears after a few seconds.", "Ein Gerät, das später kommt, wird hinzugefügt; eines, das geht, verschwindet nach wenigen Sekunden."),
           ("The Mixer tab shows one block per mixer: the DJM with ON AIR, the Denon with real faders.", "Der Reiter Mixer zeigt einen Block pro Mixer: den DJM mit ON AIR, den Denon mit echten Fadern."),
           ("**SEND BPM Auto** with two systems: the master that is also live and playing wins.", "**SEND BPM Auto** bei zwei Systemen: Es gewinnt der Master, der auch live ist und spielt.")),
        H2("Changeover step by step", "Wechsel Schritt für Schritt"),
        STEPS(("Before the night: a free port on the switch with a cable ready for the guest.", "Vor der Nacht: ein freier Port am Switch mit bereitliegendem Kabel für den Gast."),
              ("Guest plugs in the Prime 4+. Within seconds its decks appear next to the CDJs.", "Der Gast steckt den Prime 4+ an. Nach Sekunden erscheinen seine Decks neben den CDJs."),
              ("During the overlap: SEND BPM **Auto** follows the system that is live. If it jumps, set SEND BPM to the deck that plays.", "Während der Überschneidung: SEND BPM **Auto** folgt dem System, das live ist. Springt es, SEND BPM auf das spielende Deck setzen."),
              ("After the change: put SEND BPM back to Auto. The old devices disappear when they are switched off.", "Nach dem Wechsel: SEND BPM zurück auf Auto. Die alten Geräte verschwinden, wenn sie ausgeschaltet werden.")),
        ADM("warn", ("A guest controller like the XDJ-AZ also uses Pro DJ Link: it needs **player numbers** that the house CDJs do not use.", "Ein Gast-Controller wie der XDJ-AZ nutzt ebenfalls Pro DJ Link: Er braucht **Player-Nummern**, die die Haus-CDJs nicht benutzen.")),
        H2("Many devices or floors", "Viele Geräte oder Floors"),
        P("Under Settings → **Devices** tick devices on or off. With two floors in one network, each LJ Link computer shows only its floor.",
          "Unter Einstellungen → **Geräte** Geräte an- oder abhaken. Mit zwei Floors in einem Netz zeigt jeder LJ-Link-Computer nur seinen Floor."),
        CHECK(("Guest device seen before the change.", "Gast-Gerät vor dem Wechsel gesehen."), ("Player numbers unique.", "Player-Nummern eindeutig."), ("SEND BPM back on Auto.", "SEND BPM zurück auf Auto.")),
    )
    return (("Use case: several DJs with Pioneer and Denon", "Anwendungsfall: mehrere DJs mit Pioneer und Denon"),
            ("Pioneer CDJs and a guest Denon Prime 4+ or XDJ-AZ in one night with LJ Link: one search for all, changeovers, SEND BPM with two masters, several floors.",
             "Pioneer CDJs und ein Gast-Denon Prime 4+ oder XDJ-AZ an einem Abend mit LJ Link: eine Suche für alle, Wechsel, SEND BPM mit zwei Mastern, mehrere Floors."),
            ("Use case", "Anwendungsfall"), ("Several DJs, mixed gear", "Mehrere DJs, gemischte Geräte"),
            ("Pioneer and Denon in one view, through every changeover.", "Pioneer und Denon in einer Ansicht, durch jeden Wechsel."), body)


@page("wiki-case-mobile.html", "Mobile with Denon and Wolfmix", "Mobil mit Denon und Wolfmix")
def _():
    body = SEC(
        ADM("beta", ("The Wolfmix part is **beta**: tried on a Wolfmix W1, where Ableton Link worked better than OS2L. Keep the Wolfmix microphone or tap as a fallback.", "Der Wolfmix-Teil ist **Beta**: an einem Wolfmix W1 ausprobiert, dort lief Ableton Link besser als OS2L. Mikrofon oder Tap des Wolfmix als Rückfall bereithalten.")),
        facts([[("**Gear**", "**Geräte**"), ("Denon Prime (or CDJ), one laptop, Wolfmix on USB", "Denon Prime (oder CDJ), ein Laptop, Wolfmix an USB")],
               [("**Network**", "**Netzwerk**"), ("small travel router, or a cable from the Prime to the laptop", "kleiner Reiserouter, oder ein Kabel vom Prime zum Laptop")],
               [("**Status**", "**Stand**"), BADGE("stable", "Denon stable", "Denon stabil") + " " + BADGE("beta", "Wolfmix beta", "Wolfmix Beta")]]),
        DIAGRAM(720, 230, {"r": (300, 70, 160, 50, "net", ("Travel router", "Reiserouter"), ("Wi-Fi", "WLAN")),
                           "d": (90, 175, 140, 50, "dj", ("Denon Prime", "Denon Prime"), None),
                           "pc": (430, 175, 190, 60, "pc", ("Laptop", "Laptop"), ("LJ Link + WTOOLS", "LJ Link + WTOOLS")),
                           "w": (640, 175, 110, 50, "desk", ("Wolfmix", "Wolfmix"), None),
                           "t": (600, 60, 110, 46, "tab", ("Phone", "Handy"), None)},
                [("d", "r", "cable", None), ("pc", "r", "cable", None), ("pc", "w", "usb", ("USB", "USB")), ("t", "r", "wifi", ("Wi-Fi", "WLAN"))]),
        LEGEND,
        H2("Steps", "Schritte"),
        STEPS(("Router on, then the Prime and the laptop by cable (Wi-Fi also works for the Prime).", "Router an, dann den Prime und den Laptop per Kabel (für den Prime geht auch WLAN)."),
              ("LJ Link → **Search automatically**. The Prime’s decks appear with faders and waveforms.", "LJ Link → **Automatisch suchen**. Die Decks des Prime erscheinen mit Fadern und Waveforms."),
              ("Wolfmix on USB, **WTOOLS** on, **OS2L** on.", "Wolfmix an USB, **WTOOLS** an, **OS2L** an."),
              ("LJ Link: LINK → **Wolfmix** → `127.0.0.1`, `5000` → **Save** → **Send test**.", "LJ Link: LINK → **Wolfmix** → `127.0.0.1`, `5000` → **Speichern** → **Test senden**."),
              ("Wolfmix: push the **first encoder** in the FX rack. Effects follow the beats.", "Wolfmix: im FX-Rack den **ersten Drehregler** drücken. Effekte folgen den Beats."),
              ("Optional: the phone in the router’s Wi-Fi shows the waveforms ([tablet](wiki-case-tablet.html)).", "Optional: Das Handy im WLAN des Routers zeigt die Waveforms ([Tablet](wiki-case-tablet.html)).")),
        H2("Without a router", "Ohne Router"),
        P("A cable from the Prime straight to the laptop also works ([direct cable](wiki-case-direct.html)), with Wi-Fi off on the laptop. Then there is no phone view.",
          "Ein Kabel vom Prime direkt zum Laptop geht auch ([Direktkabel](wiki-case-direct.html)), mit WLAN am Laptop aus. Dann gibt es keine Handy-Ansicht."),
        CHECK(("Test beats arrive in WTOOLS.", "Test-Beats kommen in WTOOLS an."), ("Fallback (mic or tap) ready on the Wolfmix.", "Rückfall (Mikro oder Tap) am Wolfmix bereit."), ("Report how it went: [Discord](" + DISCORD + ").", "Berichte, wie es lief: [Discord](" + DISCORD + ").")),
    )
    return (("Use case: mobile with Denon and Wolfmix", "Anwendungsfall: mobil mit Denon und Wolfmix"),
            ("Small mobile setup with LJ Link: Denon Prime, one laptop with WTOOLS, Wolfmix on USB over Ableton Link or OS2L (beta), travel router and phone view.",
             "Kleiner mobiler Aufbau mit LJ Link: Denon Prime, ein Laptop mit WTOOLS, Wolfmix an USB über Ableton Link oder OS2L (Beta), Reiserouter und Handy-Ansicht."),
            ("Use case", "Anwendungsfall"), ("Mobile with Denon and Wolfmix", "Mobil mit Denon und Wolfmix"),
            ("One laptop, one controller, one Wolfmix.", "Ein Laptop, ein Controller, ein Wolfmix."), body)


@page("wiki-case-serato.html", "Serato on the DJ laptop", "Serato auf dem DJ-Laptop")
def _():
    body = SEC(
        facts([[("**Gear**", "**Geräte**"), ("DJ laptop with Serato DJ Pro, a tablet or laptop for the LJ", "DJ-Laptop mit Serato DJ Pro, ein Tablet oder Laptop für den LJ")],
               [("**Network**", "**Netzwerk**"), ("DJ laptop and tablet in the same Wi-Fi or router", "DJ-Laptop und Tablet im selben WLAN oder Router")],
               [("**Status**", "**Stand**"), BADGE("beta")]]),
        DIAGRAM(720, 170, {"pc": (150, 85, 210, 64, "pc", ("DJ laptop", "DJ-Laptop"), ("Serato + LJ Link", "Serato + LJ Link")),
                           "r": (400, 85, 130, 50, "net", ("Router", "Router"), ("Wi-Fi", "WLAN")),
                           "t": (620, 85, 130, 50, "tab", ("LJ tablet", "LJ-Tablet"), ("browser", "Browser"))},
                [("pc", "r", "wifi", None), ("t", "r", "wifi", None)]),
        H2("Talk to the DJ first", "Erst mit dem DJ reden"),
        P("LJ Link has to run **on the DJ’s laptop**, because it reads Serato’s log there. Ask before you install anything on someone else’s computer. LJ Link only reads; it does not change Serato or the files.",
          "LJ Link muss **auf dem Laptop des DJs** laufen, weil es dort das Protokoll von Serato liest. Frag, bevor du etwas auf einem fremden Computer installierst. LJ Link liest nur; es ändert weder Serato noch die Dateien."),
        H2("Steps", "Schritte"),
        STEPS(("Install LJ Link on the DJ laptop, start it, **Search automatically**.", "LJ Link auf dem DJ-Laptop installieren, starten, **Automatisch suchen**."),
              ("Load a track in Serato: the deck appears with waveform overview, beat grid and cues.", "In Serato einen Track laden: Das Deck erscheint mit Waveform-Übersicht, Beatgrid und Cues."),
              ("Optional for the live tempo: in Serato **Setup → Expansion Packs → Ableton Link** on, and the Link button on.", "Optional für das Live-Tempo: in Serato **Setup → Expansion Packs → Ableton Link** an, und den Link-Knopf an."),
              ("Laptop and tablet in the same network. On the tablet open the address shown under the toolbar, for example `http://192.168.1.50:8080`.", "Laptop und Tablet im selben Netz. Auf dem Tablet die Adresse unter der Werkzeugleiste öffnen, zum Beispiel `http://192.168.1.50:8080`."),
              ("Tempo to the desk: LINK on the DJ laptop, the desk must be reachable from there. Under SEND BPM choose the Ableton Link deck for the pitched tempo.", "Tempo ans Pult: LINK am DJ-Laptop, das Pult muss von dort erreichbar sein. Unter SEND BPM das Ableton-Link-Deck für das gepitchte Tempo wählen.")),
        ADM("info", ("Serato decks have **no playhead**: you see what is loaded and its waveform, but not where the track is. Count with the beat grid and the tempo.", "Serato-Decks haben **keinen Playhead**: Du siehst, was geladen ist, und die Waveform, aber nicht, wo der Track steht. Mit Beatgrid und Tempo mitzählen.")),
        H2("Limits", "Grenzen"),
        UL(("MP3 and AIFF tested; FLAC and M4A not read yet.", "MP3 und AIFF getestet; FLAC und M4A werden noch nicht gelesen."),
           ("Tested on macOS. Windows not tested yet.", "Getestet unter macOS. Windows noch nicht getestet."),
           ("Hotel or venue Wi-Fi often isolates devices: use your own travel router.", "Hotel- oder Location-WLAN trennt Geräte oft voneinander: einen eigenen Reiserouter nehmen.")),
    )
    return (("Use case: Serato on the DJ laptop", "Anwendungsfall: Serato auf dem DJ-Laptop"),
            ("LJ Link next to Serato DJ Pro on the DJ laptop, the lighting jockey watches on a tablet: steps, Ableton Link for the live tempo, limits.",
             "LJ Link neben Serato DJ Pro auf dem DJ-Laptop, der Lichtjockey schaut auf dem Tablet zu: Schritte, Ableton Link für das Live-Tempo, Grenzen."),
            ("Use case", "Anwendungsfall"), ("Serato on the DJ laptop", "Serato auf dem DJ-Laptop"),
            ("When the DJ brings a laptop instead of a USB stick.", "Wenn der DJ einen Laptop statt eines USB-Sticks mitbringt."), body)


@page("wiki-case-tablet.html", "Waveforms on a tablet", "Waveforms auf dem Tablet")
def _():
    body = SEC(
        facts([[("**Gear**", "**Geräte**"), ("DJ gear, a computer with LJ Link, a tablet, phone or second laptop", "DJ-Geräte, ein Computer mit LJ Link, ein Tablet, Handy oder zweiter Laptop")],
               [("**Network**", "**Netzwerk**"), ("router with Wi-Fi, or an access point on the switch", "Router mit WLAN, oder ein Access Point am Switch")],
               [("**Status**", "**Stand**"), BADGE("stable")]]),
        IMG("tablet.png", "LJ Link on a tablet", "LJ Link auf einem Tablet"),
        H2("Steps", "Schritte"),
        STEPS(("Computer with LJ Link by cable in the DJ network, as on [setups compared](wiki-network-setups.html).", "Computer mit LJ Link per Kabel im DJ-Netz, wie unter [Aufbauten im Vergleich](wiki-network-setups.html)."),
              ("Wi-Fi for the tablet: a router or access point **connected to the same switch**. Not the guest network.", "WLAN fürs Tablet: ein Router oder Access Point, **am selben Switch angeschlossen**. Nicht das Gastnetz."),
              ("Under the toolbar LJ Link shows its address, for example `http://192.168.1.50:8080`. Open it in the tablet’s browser.", "Unter der Werkzeugleiste zeigt LJ Link seine Adresse, zum Beispiel `http://192.168.1.50:8080`. Im Browser des Tablets öffnen."),
              ("Full screen and night mode on the tablet. Add it to the home screen for one tap next time.", "Vollbild und Nachtmodus am Tablet. Zum Home-Bildschirm hinzufügen, dann reicht nächstes Mal ein Tipp.")),
        ADM("warn", ("If the computer only has `169.254.x.x` addresses (no router), LJ Link shows no address for tablets: then there is no network a tablet could join. Add a router or access point.", "Hat der Computer nur `169.254.x.x`-Adressen (kein Router), zeigt LJ Link keine Adresse für Tablets: Dann gibt es kein Netz, dem ein Tablet beitreten könnte. Einen Router oder Access Point dazunehmen.")),
        H2("What a tablet can do", "Was ein Tablet kann"),
        UL(("See everything: waveforms, decks, mixer, SEND BPM mark.", "Alles sehen: Waveforms, Decks, Mixer, SEND-BPM-Markierung."),
           ("Own view settings: zoom, view, colours, day or night, language.", "Eigene Anzeige-Einstellungen: Zoom, Ansicht, Farben, Tag oder Nacht, Sprache."),
           ("Not: start or stop LJ Link, change SEND BPM or the privacy mode. That stays on the computer.", "Nicht: LJ Link starten oder beenden, SEND BPM oder den Privatsphäre-Modus ändern. Das bleibt am Computer.")),
        H2("Problems", "Probleme"),
        TABLE([("Problem", "Problem"), ("Fix", "Lösung")], [
            [("page does not open", "Seite öffnet sich nicht"), ("tablet in the same network? Firewall on the computer allows LJ Link (private network)?", "Tablet im selben Netz? Firewall am Computer erlaubt LJ Link (privates Netz)?")],
            [("opens, then freezes", "öffnet, dann hängt es"), ("weak Wi-Fi; move the access point closer, 5 GHz", "schwaches WLAN; Access Point näher, 5 GHz")],
            [("tablet goes dark", "Tablet wird dunkel"), ("switch off auto-lock for the gig", "automatische Sperre für den Gig ausschalten")],
        ]),
    )
    return (("Use case: waveforms on a tablet or second screen", "Anwendungsfall: Waveforms auf Tablet oder zweitem Bildschirm"),
            ("Show the DJ waveforms from LJ Link on a tablet, phone or second laptop at the light desk: Wi-Fi setup, address, what a tablet can do, problems.",
             "Die DJ-Waveforms von LJ Link auf einem Tablet, Handy oder zweiten Laptop am Lichtpult zeigen: WLAN-Aufbau, Adresse, was ein Tablet kann, Probleme."),
            ("Use case", "Anwendungsfall"), ("Waveforms on a tablet", "Waveforms auf dem Tablet"),
            ("See the drops coming, wherever you stand.", "Die Drops kommen sehen, egal wo du stehst."), body)


@page("wiki-case-two-networks.html", "Two networks", "Zwei Netzwerke")
def _():
    body = SEC(
        facts([[("**Gear**", "**Geräte**"), ("DJ network, a separate light network (MA-Net, Art-Net, sACN), a computer with two network ports", "DJ-Netz, ein getrenntes Lichtnetz (MA-Net, Art-Net, sACN), ein Computer mit zwei Netzwerkanschlüssen")],
               [("**Why**", "**Warum**"), ("light networks often have fixed addresses and lots of traffic; they should stay apart", "Lichtnetze haben oft feste Adressen und viel Verkehr; sie sollen getrennt bleiben")],
               [("**Status**", "**Stand**"), BADGE("ok", "Works, depends on your computer", "Geht, hängt vom Computer ab")]]),
        DIAGRAM(720, 300, {"sw": (150, 150, 140, 50, "net", ("DJ switch", "DJ-Switch"), ("169.254 / 192.168.1", "169.254 / 192.168.1")),
                           "c1": (80, 45, 100, 46, "dj", ("CDJ 1", "CDJ 1"), None), "c2": (230, 45, 100, 46, "dj", ("CDJ 2", "CDJ 2"), None),
                           "pc": (360, 255, 160, 60, "pc", ("Computer", "Computer"), ("2 network ports", "2 Netzwerkanschlüsse")),
                           "ln": (570, 150, 150, 50, "net", ("Light switch", "Licht-Switch"), ("e.g. 2.0.0.x", "z. B. 2.0.0.x")),
                           "desk": (570, 45, 130, 50, "desk", ("grandMA", "grandMA"), None)},
                [("c1", "sw", "cable", None), ("c2", "sw", "cable", None), ("pc", "sw", "cable", ("built-in", "eingebaut")),
                 ("pc", "ln", "cable", ("USB adapter", "USB-Adapter")), ("desk", "ln", "cable", None)]),
        H2("Steps", "Schritte"),
        STEPS(("Port 1 (built-in or adapter 1) into the DJ switch. Leave it automatic.", "Anschluss 1 (eingebaut oder Adapter 1) in den DJ-Switch. Automatisch lassen."),
              ("Port 2 (USB network adapter) into the light network. Give it a **fixed address** in the light network’s range, for example desk `2.0.0.1` → computer `2.0.0.50`, mask `255.0.0.0` or as the light network uses. [How to](wiki-network-ip.html)", "Anschluss 2 (USB-Netzwerkadapter) ins Lichtnetz. Ihm eine **feste Adresse** im Bereich des Lichtnetzes geben, zum Beispiel Pult `2.0.0.1` → Computer `2.0.0.50`, Maske `255.0.0.0` oder wie das Lichtnetz es nutzt. [So geht’s](wiki-network-ip.html)"),
              ("**Never a gateway** on port 2. Leave *router* empty, so internet and DJ traffic do not go there.", "**Nie ein Gateway** an Anschluss 2. *Router* leer lassen, damit Internet- und DJ-Verkehr nicht dorthin gehen."),
              ("LJ Link → **Search automatically**. It picks the DJ port by itself from where the players are. *Set up manually* lists both ports.", "LJ Link → **Automatisch suchen**. Es wählt den DJ-Anschluss selbst danach, wo die Player sind. *Manuell einrichten* listet beide Anschlüsse."),
              ("LINK → desk address in the light network (`2.0.0.1`). The computer sends it through port 2 because that network is directly on it.", "LINK → Adresse des Pults im Lichtnetz (`2.0.0.1`). Der Computer schickt es über Anschluss 2, weil dieses Netz direkt an ihm hängt.")),
        ADM("danger", ("The two networks must use **different address ranges**. If both are `192.168.1.x`, the computer does not know where to send and both break.", "Die zwei Netze müssen **verschiedene Adressbereiche** haben. Sind beide `192.168.1.x`, weiß der Computer nicht, wohin er senden soll, und beides geht kaputt.")),
        H2("Why not one network", "Warum nicht ein Netz"),
        UL(("Art-Net and sACN can send a lot of broadcast traffic; DJ gear does not need it.", "Art-Net und sACN können viel Broadcast-Verkehr senden; die DJ-Geräte brauchen ihn nicht."),
           ("The light network is often installed with fixed addresses you should not change.", "Das Lichtnetz ist oft mit festen Adressen installiert, die du nicht ändern solltest."),
           ("A problem in one network does not take the other one down.", "Ein Problem in einem Netz reißt das andere nicht mit.")),
        CHECK(("Two different address ranges.", "Zwei verschiedene Adressbereiche."), ("No gateway on the light port.", "Kein Gateway am Licht-Anschluss."), ("Test sent arrives on the desk.", "Test gesendet kommt am Pult an.")),
    )
    return (("Use case: separate DJ and light networks", "Anwendungsfall: getrenntes DJ- und Lichtnetz"),
            ("Keep MA-Net, Art-Net or sACN apart from the Pro DJ Link network: a computer with two network ports, fixed address without gateway, LJ Link chooses the DJ port itself.",
             "MA-Net, Art-Net oder sACN vom Pro-DJ-Link-Netz getrennt halten: ein Computer mit zwei Netzwerkanschlüssen, feste Adresse ohne Gateway, LJ Link wählt den DJ-Anschluss selbst."),
            ("Use case", "Anwendungsfall"), ("Two networks", "Zwei Netzwerke"),
            ("One computer between the DJ network and the light network.", "Ein Computer zwischen DJ-Netz und Lichtnetz."), body)


@page("wiki-case-direct.html", "Direct cable", "Direktkabel")
def _():
    body = SEC(
        facts([[("**Gear**", "**Geräte**"), ("one player or controller, one network cable, a computer", "ein Player oder Controller, ein Netzwerkkabel, ein Computer")],
               [("**When**", "**Wann**"), ("quick rehearsal, a single controller (XDJ-RX, Prime), an emergency", "schnelle Probe, ein einzelner Controller (XDJ-RX, Prime), ein Notfall")],
               [("**Status**", "**Stand**"), BADGE("ok", "Works, with a known bug", "Geht, mit bekanntem Fehler")]]),
        H2("What works", "Was geht"),
        UL(("**One device** directly: a CDJ, an all-in-one controller or a Denon Prime.", "**Ein Gerät** direkt: ein CDJ, ein All-in-One-Controller oder ein Denon Prime."),
           ("Into a **DJM LINK socket** instead: all players on the mixer are seen. That is the better direct cable.", "Stattdessen in eine **LINK-Buchse des DJM**: Alle Player am Mixer werden gesehen. Das ist das bessere Direktkabel."),
           ("No router: both sides give themselves `169.254.x.x` addresses. Wait up to a minute after plugging in.", "Kein Router: Beide Seiten geben sich `169.254.x.x`-Adressen. Nach dem Einstecken bis zu einer Minute warten.")),
        H2("Steps", "Schritte"),
        STEPS(("**Switch Wi-Fi off** on the computer. This is the most important step.", "**WLAN am Computer ausschalten.** Das ist der wichtigste Schritt."),
              ("Plug the cable into the player (LINK socket) and the computer (or USB adapter).", "Das Kabel in den Player (LINK-Buchse) und den Computer (oder USB-Adapter) stecken."),
              ("Wait 30–60 seconds, then **Search automatically**.", "30–60 Sekunden warten, dann **Automatisch suchen**."),
              ("Nothing? **Set up manually**, enter the player’s address (Utility → Link Info) and **Check connection**.", "Nichts? **Manuell einrichten**, die Adresse des Players eintragen (Utility → Link Info) und **Verbindung prüfen**.")),
        ADM("danger", ("**Known bug:** with a direct cable and Wi-Fi on, players sometimes show up twice or not at all. Quit LJ Link (icon → Quit) and start again; switch Wi-Fi off; or put a small switch between. If you see it, please [send a report](wiki-troubleshooting.html#report) while it is on screen.",
                       "**Bekannter Fehler:** Mit Direktkabel und eingeschaltetem WLAN erscheinen Player manchmal doppelt oder gar nicht. LJ Link beenden (Symbol → Beenden) und neu starten; WLAN ausschalten; oder einen kleinen Switch dazwischen. Wenn du es siehst, bitte [einen Bericht senden](wiki-troubleshooting.html#report), solange es auf dem Bildschirm ist.")),
        H2("Better: a small switch", "Besser: ein kleiner Switch"),
        P("A 5-port switch costs little, needs no setup and removes most problems of the direct cable. Keep one in the bag.",
          "Ein 5-Port-Switch kostet wenig, braucht keine Einrichtung und beseitigt die meisten Probleme des Direktkabels. Einen in der Tasche haben."),
    )
    return (("Use case: direct cable without switch or router", "Anwendungsfall: Direktkabel ohne Switch oder Router"),
            ("Connect one CDJ, controller or Denon Prime directly to the LJ Link computer with one cable: 169.254 addresses, Wi-Fi off, the known bug and why a small switch is better.",
             "Einen CDJ, Controller oder Denon Prime mit einem Kabel direkt an den LJ-Link-Computer: 169.254-Adressen, WLAN aus, der bekannte Fehler und warum ein kleiner Switch besser ist."),
            ("Use case", "Anwendungsfall"), ("Direct cable", "Direktkabel"),
            ("One cable, no switch. Quick, with one catch.", "Ein Kabel, kein Switch. Schnell, mit einem Haken."), body)


@page("wiki-case-stream.html", "Livestream and privacy mode", "Livestream und Privatsphäre-Modus")
def _():
    body = SEC(
        facts([[("**When**", "**Wann**"), ("livestream, screen share, photos or video of the booth, unreleased tracks", "Livestream, Bildschirm teilen, Fotos oder Videos der Booth, unveröffentlichte Tracks")],
               [("**Status**", "**Stand**"), BADGE("stable")]]),
        IMG("privacy.png", "Privacy mode: no titles, waveforms stay", "Privatsphäre-Modus: keine Titel, Waveforms bleiben"),
        H2("What it hides", "Was er verbirgt"),
        P("LJ Link then sends **no** titles, artists, albums, genres, labels, remixers, comments and covers at all, also not to tablets or other computers. Waveforms, times, tempo and cues stay visible.",
          "LJ Link sendet dann **gar keine** Titel, Artists, Alben, Genres, Labels, Remixer, Kommentare und Cover mehr, auch nicht an Tablets oder andere Computer. Waveforms, Zeiten, Tempo und Cues bleiben sichtbar."),
        H2("Switch it on", "Einschalten"),
        STEPS(("Click the **program icon** (menu bar on a Mac, next to the clock on Windows).", "Auf das **Programm-Symbol** klicken (Menüleiste beim Mac, neben der Uhr bei Windows)."),
              ("Tick **Privacy mode**. The interface shows **PRIVACY**.", "**Privatsphäre-Modus** anhaken. Die Oberfläche zeigt **PRIVATSPHÄRE**."),
              ("To start in this mode every time: start LJ Link with `--privacy`.", "Um jedes Mal in diesem Modus zu starten: LJ Link mit `--privacy` starten.")),
        ADM("info", ("On purpose it can **only** be switched from the program icon, not from the browser. Nobody with a tablet can switch it off during the stream.", "Er lässt sich absichtlich **nur** über das Programm-Symbol schalten, nicht aus dem Browser. Niemand mit einem Tablet kann ihn während des Streams ausschalten.")),
        IMG("privacy-settings.png", "Privacy mode hint in the settings", "Hinweis zum Privatsphäre-Modus in den Einstellungen"),
    )
    return (("Use case: livestream and privacy mode", "Anwendungsfall: Livestream und Privatsphäre-Modus"),
            ("Hide track titles, artists and covers in LJ Link for livestreams, screen sharing and photos with the privacy mode, while waveforms, tempo and cues stay.",
             "Tracktitel, Artists und Cover in LJ Link für Livestreams, Bildschirmfreigabe und Fotos mit dem Privatsphäre-Modus verbergen, Waveforms, Tempo und Cues bleiben."),
            ("Use case", "Anwendungsfall"), ("Livestream and privacy mode", "Livestream und Privatsphäre-Modus"),
            ("No titles on any screen, nothing else changes.", "Keine Titel auf irgendeinem Bildschirm, sonst ändert sich nichts."), body)
