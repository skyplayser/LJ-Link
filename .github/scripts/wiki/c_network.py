from wikilib import *

group("Network", "Netzwerk")

N_CDJ = lambda x, y, n="CDJ": (x, y, 104, 50, "dj", (n, n), None)
PC = lambda x, y, sub=("LJ Link", "LJ Link"): (x, y, 130, 54, "pc", ("Computer", "Computer"), sub)


def d_switch():
    nodes = {"sw": (360, 150, 150, 50, "net", ("Switch", "Switch"), ("no router", "ohne Router")),
             "c1": N_CDJ(80, 50, "CDJ 1"), "c2": N_CDJ(220, 50, "CDJ 2"), "c3": N_CDJ(500, 50, "CDJ 3"), "c4": N_CDJ(640, 50, "CDJ 4"),
             "djm": (120, 250, 110, 50, "dj", ("DJM", "DJM"), None), "pc": PC(600, 250, ("LJ Link, Wi-Fi off", "LJ Link, WLAN aus"))}
    edges = [("c1", "sw", "cable", None), ("c2", "sw", "cable", None), ("c3", "sw", "cable", None), ("c4", "sw", "cable", None),
             ("djm", "sw", "cable", None), ("pc", "sw", "cable", None)]
    return DIAGRAM(720, 300, nodes, edges, ("Setup A: everything on one switch. Addresses 169.254.x.x, set by the devices themselves.",
                                            "Aufbau A: alles an einem Switch. Adressen 169.254.x.x, von den Geräten selbst vergeben."))


def d_router():
    nodes = {"r": (360, 150, 170, 54, "net", ("Router", "Router"), ("DHCP, Wi-Fi", "DHCP, WLAN")),
             "c1": N_CDJ(90, 50, "CDJ 1"), "c2": N_CDJ(250, 50, "CDJ 2"), "djm": (470, 50, 104, 50, "dj", ("DJM", "DJM"), None),
             "desk": (630, 50, 120, 50, "desk", ("Light desk", "Lichtpult"), None),
             "pc": PC(150, 255), "tab": (560, 255, 120, 50, "tab", ("Tablet", "Tablet"), ("browser", "Browser"))}
    edges = [("c1", "r", "cable", None), ("c2", "r", "cable", None), ("djm", "r", "cable", None), ("desk", "r", "cable", None),
             ("pc", "r", "cable", None), ("tab", "r", "wifi", ("Wi-Fi", "WLAN"))]
    return DIAGRAM(720, 300, nodes, edges, ("Setup B: router with DHCP. Addresses like 192.168.1.x, tablet over the router’s Wi-Fi.",
                                            "Aufbau B: Router mit DHCP. Adressen wie 192.168.1.x, Tablet über das WLAN des Routers."))


def d_djm():
    nodes = {"djm": (360, 150, 170, 54, "dj", ("DJM", "DJM"), ("LINK sockets", "LINK-Buchsen")),
             "c1": N_CDJ(90, 60, "CDJ 1"), "c2": N_CDJ(250, 50, "CDJ 2"), "c3": N_CDJ(470, 50, "CDJ 3"),
             "pc": PC(630, 150)}
    edges = [("c1", "djm", "cable", None), ("c2", "djm", "cable", None), ("c3", "djm", "cable", None), ("pc", "djm", "cable", ("free LINK", "freie LINK"))]
    return DIAGRAM(720, 220, nodes, edges, ("Setup C: the mixer’s LINK sockets as a switch, the computer in a free socket.",
                                            "Aufbau C: die LINK-Buchsen des Mixers als Switch, der Computer in einer freien Buchse."))


def d_direct():
    nodes = {"c1": N_CDJ(150, 70, "CDJ / XDJ"), "pc": PC(520, 70, ("Wi-Fi off!", "WLAN aus!"))}
    return DIAGRAM(720, 140, nodes, [("c1", "pc", "cable", ("one cable", "ein Kabel"))],
                   ("Setup D: direct cable to one player. Only this player is seen.", "Aufbau D: Direktkabel zu einem Player. Nur dieser Player ist sichtbar."))


def d_wifi():
    nodes = {"r": (360, 70, 170, 54, "net", ("Router / access point", "Router / Access Point"), ("Wi-Fi", "WLAN")),
             "d": (140, 190, 140, 50, "dj", ("Denon Prime", "Denon Prime"), None), "pc": PC(580, 190)}
    edges = [("d", "r", "wifi", ("Wi-Fi", "WLAN")), ("pc", "r", "wifi", ("Wi-Fi", "WLAN"))]
    return DIAGRAM(720, 240, nodes, edges, ("Setup E: Denon over Wi-Fi. Works like with Engine DJ; a cable is steadier.",
                                            "Aufbau E: Denon über WLAN. Geht wie bei Engine DJ; ein Kabel ist stabiler."))


def d_two():
    nodes = {"sw": (150, 150, 140, 50, "net", ("DJ switch", "DJ-Switch"), ("169.254 / 192.168.1", "169.254 / 192.168.1")),
             "c1": N_CDJ(80, 45, "CDJ 1"), "c2": N_CDJ(230, 45, "CDJ 2"),
             "pc": (360, 255, 160, 60, "pc", ("Computer", "Computer"), ("2 network ports", "2 Netzwerkanschlüsse")),
             "ln": (570, 150, 150, 50, "net", ("Light switch", "Licht-Switch"), ("e.g. 2.x / 10.x", "z. B. 2.x / 10.x")),
             "desk": (570, 45, 130, 50, "desk", ("Light desk", "Lichtpult"), None)}
    edges = [("c1", "sw", "cable", None), ("c2", "sw", "cable", None), ("pc", "sw", "cable", ("port 1", "Anschluss 1")),
             ("pc", "ln", "cable", ("port 2 (USB)", "Anschluss 2 (USB)")), ("desk", "ln", "cable", None)]
    return DIAGRAM(720, 300, nodes, edges, ("Setup F: two networks, the computer has one network port in each.",
                                            "Aufbau F: zwei Netzwerke, der Computer hat in jedem einen Anschluss."))


@page("wiki-network.html", "Basics", "Grundlagen")
def _():
    body = SEC(
        ADM("tip", ("**Short version:** everything on one switch or router by cable, Wi-Fi off on the computer, every player its own number. That works almost always. The rest of this page explains why, so you can fix it when it does not.",
                    "**Kurz gesagt:** alles per Kabel an einen Switch oder Router, WLAN am Computer aus, jeder Player eine eigene Nummer. Das klappt fast immer. Der Rest der Seite erklärt warum, damit du es reparieren kannst, wenn nicht.")),
        H2("Why the network matters", "Warum das Netzwerk zählt"),
        P("DJ players do not wait to be asked. They **announce themselves** several times a second on the network (broadcast) and send their status, beats and position the same way. LJ Link listens to these packets. That is why you cannot simply type in a player’s address: LJ Link has to be **in the same network** to hear it.",
          "DJ-Player warten nicht darauf, gefragt zu werden. Sie **melden sich selbst** mehrmals pro Sekunde im Netzwerk (Broadcast) und schicken ihren Status, die Beats und die Position genauso. LJ Link hört diese Pakete. Deshalb kann man nicht einfach die Adresse eines Players eintippen: LJ Link muss **im selben Netzwerk** sein, um ihn zu hören."),
        P("Broadcasts do not cross a router into another network and get lost easily over Wi-Fi. Hence the three rules below.",
          "Broadcasts gehen nicht über einen Router in ein anderes Netz und gehen über WLAN leicht verloren. Daraus folgen die drei Regeln unten."),
        H2("The three rules", "Die drei Regeln"),
        STEPS(("**One network.** Computer, players and mixer hang on the same switch or router, or on switches connected to each other.",
               "**Ein Netzwerk.** Computer, Player und Mixer hängen am selben Switch oder Router, oder an Switches, die miteinander verbunden sind."),
              ("**One connection.** The computer uses only one connection into this network. Wi-Fi and cable in the same network at once means every packet arrives twice.",
               "**Eine Verbindung.** Der Computer nutzt nur eine Verbindung in dieses Netz. WLAN und Kabel gleichzeitig im selben Netz heißt: Jedes Paket kommt doppelt an."),
              ("**Same address range.** All devices need addresses from the same range, for example all `192.168.1.x` or all `169.254.x.x`.",
               "**Gleicher Adressbereich.** Alle Geräte brauchen Adressen aus demselben Bereich, zum Beispiel alle `192.168.1.x` oder alle `169.254.x.x`.")),
        H2("IP addresses in one minute", "IP-Adressen in einer Minute"),
        P("Every device in a network has an address such as `192.168.1.20`. With the usual subnet mask `255.255.255.0` the **first three numbers** name the network and must be equal; the last number names the device and must differ.",
          "Jedes Gerät im Netzwerk hat eine Adresse wie `192.168.1.20`. Mit der üblichen Subnetzmaske `255.255.255.0` geben die **ersten drei Zahlen** das Netz an und müssen gleich sein; die letzte Zahl ist das Gerät und muss sich unterscheiden."),
        TABLE([("Computer", "Computer"), ("Device", "Gerät"), ("Result", "Ergebnis")], [
            ["<code>192.168.1.10</code>", "<code>192.168.1.50</code>", BADGE("ok", "same network", "gleiches Netz")],
            ["<code>192.168.1.10</code>", "<code>192.168.0.50</code>", BADGE("bad", "different network", "anderes Netz")],
            ["<code>169.254.12.7</code>", "<code>169.254.201.3</code>", BADGE("ok", "same network (mask 255.255.0.0)", "gleiches Netz (Maske 255.255.0.0)")],
            ["<code>169.254.12.7</code>", "<code>192.168.1.50</code>", BADGE("bad", "different network", "anderes Netz")],
        ]),
        H3("Where the addresses come from", "Woher die Adressen kommen"),
        UL(("**With a router (DHCP):** the router hands out addresses like `192.168.1.x`. Nothing to set up.", "**Mit Router (DHCP):** Der Router verteilt Adressen wie `192.168.1.x`. Nichts einzustellen."),
           ("**Without a router** (only a switch or a direct cable): every device gives itself an address `169.254.x.x` after a short while (self-assigned, also called link-local). Players, Mac and Windows all do this, so they find each other.",
            "**Ohne Router** (nur Switch oder Direktkabel): Jedes Gerät gibt sich nach kurzer Zeit selbst eine Adresse `169.254.x.x` (selbst vergeben, auch Link-Local genannt). Player, Mac und Windows machen das alle, deshalb finden sie sich."),
           ("**Fixed by hand:** needed when a device has a fixed address, for example many light desks. See [set an IP address](wiki-network-ip.html).", "**Von Hand fest:** nötig, wenn ein Gerät eine feste Adresse hat, zum Beispiel viele Lichtpulte. Siehe [IP-Adresse einstellen](wiki-network-ip.html).")),
        ADM("warn", ("Self-assigned addresses take a moment. After plugging in, wait up to a minute before you search. If a router comes into the network later, devices may keep their old `169.254` address: then restart the players or plug their cable out and in.",
                     "Selbst vergebene Adressen brauchen einen Moment. Nach dem Einstecken bis zu einer Minute warten, bevor du suchst. Kommt später ein Router dazu, behalten Geräte eventuell ihre alte `169.254`-Adresse: dann die Player neu starten oder ihr Kabel kurz ab- und anstecken.")),
        H2("Switch, router, hub", "Switch, Router, Hub"),
        TABLE([("Device", "Gerät"), ("What it does", "Was es macht"), ("For LJ Link", "Für LJ Link")], [
            [("**Switch** (unmanaged)", "**Switch** (unmanaged)"), ("connects cables to one network, passes broadcasts", "verbindet Kabel zu einem Netz, leitet Broadcasts weiter"), BADGE("rec")],
            [("**Router** with DHCP", "**Router** mit DHCP"), ("like a switch plus addresses, often Wi-Fi", "wie ein Switch, dazu Adressen, oft WLAN"), BADGE("rec")],
            [("**DJM LINK sockets**", "**DJM-LINK-Buchsen**"), ("small built-in switch in many DJM mixers", "kleiner eingebauter Switch in vielen DJM-Mischpulten"), BADGE("ok")],
            [("**Managed switch**", "**Managed Switch**"), ("can filter broadcasts or split VLANs", "kann Broadcasts filtern oder VLANs trennen"), BADGE("ok", "works if DJ gear is in one VLAN", "geht, wenn DJ-Geräte in einem VLAN sind")],
            [("**Wi-Fi repeater, mesh, guest network**", "**WLAN-Repeater, Mesh, Gastnetz**"), ("often blocks or delays broadcasts", "blockiert oder verzögert oft Broadcasts"), BADGE("bad")],
        ]),
        H2("Cable or Wi-Fi", "Kabel oder WLAN"),
        TABLE([("Source", "Quelle"), ("Cable", "Kabel"), ("Wi-Fi", "WLAN")], [
            [("Pioneer Pro DJ Link", "Pioneer Pro DJ Link"), BADGE("rec"), BADGE("bad", "avoid: packets get lost", "vermeiden: Pakete gehen verloren")],
            [("Denon StageLinQ", "Denon StageLinQ"), BADGE("rec"), BADGE("ok", "works, like Engine DJ", "geht, wie bei Engine DJ")],
            [("Tablet with the interface", "Tablet mit der Oberfläche"), BADGE("ok"), BADGE("rec", "normal case", "der Normalfall")],
            [("Light desk link", "Lichtpult-Link"), BADGE("rec"), BADGE("ok", "works, cable is steadier", "geht, Kabel ist stabiler")],
        ]),
        P("Which setup fits your gig is compared on [setups compared](wiki-network-setups.html).", "Welcher Aufbau zu deinem Gig passt, steht unter [Aufbauten im Vergleich](wiki-network-setups.html)."),
    )
    return (("Network basics", "Netzwerk-Grundlagen"),
            ("Network basics for LJ Link: why DJ players must be in the same network, IP addresses, DHCP and 169.254 self-assigned addresses, switch versus router, cable versus Wi-Fi.",
             "Netzwerk-Grundlagen für LJ Link: warum DJ-Player im selben Netz sein müssen, IP-Adressen, DHCP und selbst vergebene 169.254-Adressen, Switch oder Router, Kabel oder WLAN."),
            ("Network", "Netzwerk"), ("Network basics", "Netzwerk-Grundlagen"),
            ("What you need to know so that LJ Link finds every device.", "Was du wissen musst, damit LJ Link jedes Gerät findet."), body)


@page("wiki-network-setups.html", "Setups compared", "Aufbauten im Vergleich")
def _():
    body = SEC(
        H2("Which setup works best", "Welcher Aufbau am besten funktioniert"),
        TABLE([("Setup", "Aufbau"), ("Reliability", "Zuverlässigkeit"), ("Tablet", "Tablet"), ("Light desk", "Lichtpult"), ("Verdict", "Urteil")], [
            [("[A Switch](#a-switch)", "[A Switch](#a-switch)"), "★★★★★", ("with a Wi-Fi access point", "mit WLAN-Access-Point"), ("yes", "ja"), BADGE("rec")],
            [("[B Router with DHCP](#b-router)", "[B Router mit DHCP](#b-router)"), "★★★★★", ("yes, over its Wi-Fi", "ja, über sein WLAN"), ("yes", "ja"), BADGE("rec", "Recommended, most flexible", "Empfohlen, am flexibelsten")],
            [("[C DJM LINK sockets](#c-djm)", "[C DJM-LINK-Buchsen](#c-djm)"), "★★★★☆", ("no", "nein"), ("only with a free socket", "nur mit freier Buchse"), BADGE("ok")],
            [("[D Direct cable](#d-direct)", "[D Direktkabel](#d-direct)"), "★★★☆☆", ("no", "nein"), ("no", "nein"), BADGE("ok", "Emergency, one player", "Notlösung, ein Player")],
            [("[E Wi-Fi only](#e-wifi)", "[E Nur WLAN](#e-wifi)"), ("★★★☆☆ Denon, ★☆☆☆☆ Pioneer", "★★★☆☆ Denon, ★☆☆☆☆ Pioneer"), ("yes", "ja"), ("yes", "ja"), BADGE("ok", "Denon only", "nur Denon")],
            [("[F Two networks](#f-two)", "[F Zwei Netzwerke](#f-two)"), "★★★★☆", ("in one of them", "in einem davon"), ("yes, own network", "ja, eigenes Netz"), BADGE("ok", "For separate light networks", "Für getrennte Lichtnetze")],
        ]),
        LEGEND,
        H2("A Switch", "A Switch", "a-switch"),
        d_switch(),
        P("A small unmanaged switch (5 or 8 ports) connects players, mixer and computer. There is no router, so every device gives itself a `169.254.x.x` address. This is how most booths are wired and what Pioneer intends.",
          "Ein kleiner unmanaged Switch (5 oder 8 Ports) verbindet Player, Mixer und Computer. Es gibt keinen Router, also gibt sich jedes Gerät selbst eine `169.254.x.x`-Adresse. So sind die meisten Booths verkabelt, und so hat es Pioneer gedacht."),
        UL(("**Pro:** simple, robust, nothing to configure, no internet needed.", "**Vorteil:** einfach, robust, nichts einzustellen, kein Internet nötig."),
           ("**Con:** no Wi-Fi for a tablet; for that plug a Wi-Fi access point or a router into the switch, which turns it into setup B.", "**Nachteil:** kein WLAN fürs Tablet; dafür einen WLAN-Access-Point oder Router an den Switch stecken, dann wird es Aufbau B.")),
        ADM("warn", ("Switch Wi-Fi off on the computer. With a switch and no router, a computer with Wi-Fi on may send on the wrong adapter. This is the [known bug](wiki-troubleshooting.html#direct-wifi).",
                     "WLAN am Computer ausschalten. Mit Switch ohne Router kann ein Computer mit eingeschaltetem WLAN über den falschen Anschluss senden. Das ist der [bekannte Fehler](wiki-troubleshooting.html#direct-wifi).")),
        H2("B Router with DHCP", "B Router mit DHCP", "b-router"),
        d_router(),
        P("A router (any home router, a travel router, or the venue’s show network) hands out addresses. Players, mixer, the computer and the light desk plug in by cable, tablets join its Wi-Fi.",
          "Ein Router (jeder Heimrouter, ein Reiserouter oder das Show-Netz der Location) verteilt Adressen. Player, Mixer, Computer und Lichtpult hängen per Kabel daran, Tablets im WLAN."),
        UL(("**Pro:** everything in one network, tablets possible, addresses are clear (`192.168.x.x`).", "**Vorteil:** alles in einem Netz, Tablets möglich, Adressen sind übersichtlich (`192.168.x.x`)."),
           ("**Pro:** the light desk can stay in the same network if its address fits the router’s range.", "**Vorteil:** Das Lichtpult kann im selben Netz bleiben, wenn seine Adresse zum Bereich des Routers passt."),
           ("**Con:** too few ports? Add a switch to the router. Do not use the guest Wi-Fi: it isolates devices.", "**Nachteil:** Zu wenig Ports? Einen Switch an den Router hängen. Nicht das Gast-WLAN benutzen: Es trennt die Geräte voneinander.")),
        ADM("tip", ("Switch on the router first and the players second. Then the players get their address from the router straight away.", "Erst den Router einschalten, dann die Player. Dann bekommen die Player ihre Adresse gleich vom Router.")),
        H2("C DJM LINK sockets", "C DJM-LINK-Buchsen", "c-djm"),
        d_djm(),
        P("Many DJM mixers (for example DJM-900NXS2, DJM-A9, DJM-V10) have several LINK sockets that work like a small switch. If one is free, the computer goes there.",
          "Viele DJM-Mischpulte (zum Beispiel DJM-900NXS2, DJM-A9, DJM-V10) haben mehrere LINK-Buchsen, die wie ein kleiner Switch arbeiten. Ist eine frei, kommt der Computer dort hinein."),
        UL(("**Pro:** no extra device.", "**Vorteil:** kein zusätzliches Gerät."),
           ("**Con:** with four players there is often no socket left. Then use a switch.", "**Nachteil:** Mit vier Playern ist oft keine Buchse mehr frei. Dann einen Switch nehmen.")),
        H2("D Direct cable", "D Direktkabel", "d-direct"),
        d_direct(),
        P("One cable from the computer straight to one player. Players cannot be chained, so LJ Link sees **only this player**. Into a DJM LINK socket instead it is setup C.",
          "Ein Kabel vom Computer direkt zu einem Player. Player lassen sich nicht hintereinanderschalten, also sieht LJ Link **nur diesen Player**. Stattdessen in eine LINK-Buchse des DJM ist es Aufbau C."),
        ADM("danger", ("**Known bug:** with a direct cable and Wi-Fi on, players sometimes show up twice or not at all. Switch Wi-Fi off. If it happens, quit LJ Link and start it again. Details: [troubleshooting](wiki-troubleshooting.html#direct-wifi).",
                       "**Bekannter Fehler:** Mit Direktkabel und eingeschaltetem WLAN erscheinen Player manchmal doppelt oder gar nicht. WLAN ausschalten. Passiert es trotzdem, LJ Link beenden und neu starten. Details: [Fehlersuche](wiki-troubleshooting.html#direct-wifi).")),
        P("Modern laptops have no network socket: a USB-C or USB network adapter works fine. Old cables or adapters without auto MDI-X are rarely a problem today.",
          "Moderne Laptops haben keine Netzwerkbuchse: Ein USB-C- oder USB-Netzwerkadapter funktioniert gut. Alte Kabel oder Adapter ohne Auto-MDI-X sind heute selten ein Problem."),
        H2("E Wi-Fi only", "E Nur WLAN", "e-wifi"),
        d_wifi(),
        P("**Denon:** Prime and SC devices can join a Wi-Fi network like they do for Engine DJ; LJ Link finds them there. A cable is steadier at a show.",
          "**Denon:** Prime- und SC-Geräte können sich wie für Engine DJ in ein WLAN einbuchen; LJ Link findet sie dort. Bei einer Show ist ein Kabel stabiler."),
        P("**Pioneer:** CDJs and XDJs need a cable. A computer on the Wi-Fi of a router to which the players are cabled may see them, but packets get lost and positions jump. Not tested and not recommended.",
          "**Pioneer:** CDJs und XDJs brauchen ein Kabel. Ein Computer im WLAN eines Routers, an dem die Player per Kabel hängen, sieht sie eventuell, aber Pakete gehen verloren und Positionen springen. Nicht getestet und nicht empfohlen."),
        H2("F Two networks", "F Zwei Netzwerke", "f-two"),
        d_two(),
        P("The light desk stays in its own network (MA-Net, Art-Net, sACN), the DJ gear in another. The computer has one network port in each, for example the built-in one and a USB adapter. LJ Link chooses the port for the DJ gear by itself; the tempo goes to the desk through the other one, because the computer knows which network the desk is in.",
          "Das Lichtpult bleibt in seinem eigenen Netz (MA-Net, Art-Net, sACN), die DJ-Geräte in einem anderen. Der Computer hat in jedem einen Netzwerkanschluss, zum Beispiel den eingebauten und einen USB-Adapter. LJ Link wählt den Anschluss für die DJ-Geräte selbst; das Tempo geht über den anderen ans Pult, weil der Computer weiß, in welchem Netz das Pult ist."),
        P("Complete walk-through: [two networks](wiki-case-two-networks.html).", "Kompletter Ablauf: [Zwei Netzwerke](wiki-case-two-networks.html)."),
        H2("Light desk in the same network?", "Lichtpult im selben Netz?", "desk"),
        TABLE([("Situation", "Situation"), ("What to do", "Was tun")], [
            [("onPC on the same computer as LJ Link", "onPC auf demselben Computer wie LJ Link"), ("address `127.0.0.1`, nothing else", "Adresse `127.0.0.1`, sonst nichts")],
            [("desk gets its address from the router", "Pult bekommt seine Adresse vom Router"), ("setup B, enter the desk’s address", "Aufbau B, Adresse des Pults eintragen")],
            [("desk has a fixed address, e.g. `192.168.0.10`", "Pult hat eine feste Adresse, z. B. `192.168.0.10`"), ("give the computer a fitting address or use setup F", "dem Computer eine passende Adresse geben oder Aufbau F")],
            [("desk is in an Art-Net network `2.x.x.x` or `10.x.x.x`", "Pult ist in einem Art-Net-Netz `2.x.x.x` oder `10.x.x.x`"), ("setup F with a second network port", "Aufbau F mit zweitem Netzwerkanschluss")],
            [("Wolfmix", "Wolfmix"), ("no network at all: USB to the computer that runs WTOOLS", "gar kein Netzwerk: USB an den Computer, auf dem WTOOLS läuft")],
        ]),
    )
    return (("Network setups compared: direct cable, switch, router, Wi-Fi", "Netzwerk-Aufbauten im Vergleich: Direktkabel, Switch, Router, WLAN"),
            ("Which network setup works best for LJ Link: switch, router with DHCP, DJM LINK sockets, direct cable, Wi-Fi only or two networks. With diagrams, pros and cons.",
             "Welcher Netzwerk-Aufbau für LJ Link am besten funktioniert: Switch, Router mit DHCP, DJM-LINK-Buchsen, Direktkabel, nur WLAN oder zwei Netzwerke. Mit Diagrammen, Vor- und Nachteilen."),
            ("Network", "Netzwerk"), ("Setups compared", "Aufbauten im Vergleich"),
            ("Direct cable or router? Six setups with diagrams, and which one to choose.", "Direktkabel oder Router? Sechs Aufbauten mit Diagrammen, und welchen du nehmen solltest."), body)


@page("wiki-network-ip.html", "Set an IP address", "IP-Adresse einstellen")
def _():
    body = SEC(
        H2("When you need this", "Wann du das brauchst"),
        UL(("The light desk has a **fixed address** and the computer is in another range.", "Das Lichtpult hat eine **feste Adresse** und der Computer ist in einem anderen Bereich."),
           ("**Set up manually** in LJ Link says the computer and the device are in different networks.", "**Manuell einrichten** in LJ Link meldet, dass Computer und Gerät in verschiedenen Netzen sind."),
           ("The players have `169.254.x.x` and the computer has a router address, or the other way round.", "Die Player haben `169.254.x.x` und der Computer eine Router-Adresse, oder umgekehrt.")),
        P("Normally you do **not** need it: with a router everything is automatic, and without one the devices give themselves fitting addresses.",
          "Normalerweise brauchst du es **nicht**: Mit Router läuft alles automatisch, und ohne Router geben sich die Geräte selbst passende Adressen."),
        H2("Find the addresses", "Adressen herausfinden"),
        TABLE([("Device", "Gerät"), ("Where", "Wo")], [
            [("Pioneer player", "Pioneer-Player"), ("**Menu → Utility → Link Info** (on some models *Network Info*)", "**Menu → Utility → Link Info** (bei manchen Modellen *Network Info*)")],
            [("Denon Prime / SC", "Denon Prime / SC"), ("Settings → network or *About*", "Einstellungen → Netzwerk oder *Info*")],
            [("grandMA2", "grandMA2"), ("**Setup → Network → Network Interface**, onPC in the title bar", "**Setup → Network → Network Interface**, bei onPC in der Titelleiste")],
            [("grandMA3", "grandMA3"), ("**Menu → Network**, under *Interfaces*", "**Menu → Network**, unter *Interfaces*")],
            [("this computer", "dieser Computer"), ("LJ Link → **Set up manually** lists all network adapters with their address", "LJ Link → **Manuell einrichten** listet alle Netzwerkanschlüsse mit Adresse")],
        ]),
        H2("macOS", "macOS"),
        STEPS(("**System Settings → Network** → choose the cable connection (Ethernet or the USB adapter) → **Details**.", "**Systemeinstellungen → Netzwerk** → die Kabelverbindung wählen (Ethernet oder der USB-Adapter) → **Details**."),
              ("**TCP/IP** → *Configure IPv4*: **Manually**.", "**TCP/IP** → *IPv4 konfigurieren*: **Manuell**."),
              ("**IP address**: same first three numbers as the desk, own last number. Example desk `192.168.0.10` → computer `192.168.0.20`.", "**IP-Adresse**: gleiche erste drei Zahlen wie das Pult, eigene letzte Zahl. Beispiel Pult `192.168.0.10` → Computer `192.168.0.20`."),
              ("**Subnet mask** `255.255.255.0`. Router: leave empty. **OK**.", "**Teilnetzmaske** `255.255.255.0`. Router: leer lassen. **OK**.")),
        H2("Windows", "Windows"),
        STEPS(("**Settings → Network & Internet → Ethernet** → your connection.", "**Einstellungen → Netzwerk und Internet → Ethernet** → deine Verbindung."),
              ("At **IP assignment** press **Edit** → **Manual** → switch on **IPv4**.", "Bei **IP-Zuweisung** auf **Bearbeiten** → **Manuell** → **IPv4** einschalten."),
              ("**IP address** as above, **subnet mask** `255.255.255.0`, gateway and DNS empty. **Save**.", "**IP-Adresse** wie oben, **Subnetzmaske** `255.255.255.0`, Gateway und DNS leer. **Speichern**.")),
        ADM("warn", ("Write down what was set before and change it back after the gig. A computer with a fixed address gets no internet at home.", "Notier dir, was vorher eingestellt war, und stell es nach dem Gig zurück. Ein Computer mit fester Adresse bekommt zu Hause kein Internet.")),
        H2("Players and a fixed address", "Player und eine feste Adresse"),
        P("Pioneer players with `169.254.x.x` only talk to a computer in the same range. If you give the computer a fixed `192.168.x.x` address on the same cable, it can no longer hear them. Use a second network port for the desk ([setup F](wiki-network-setups.html#f-two)) or a router for all.",
          "Pioneer-Player mit `169.254.x.x` sprechen nur mit einem Computer im selben Bereich. Gibst du dem Computer am selben Kabel eine feste `192.168.x.x`-Adresse, hört er sie nicht mehr. Für das Pult einen zweiten Netzwerkanschluss nehmen ([Aufbau F](wiki-network-setups.html#f-two)) oder einen Router für alle."),
        H2("Check with LJ Link", "Mit LJ Link prüfen"),
        IMG("manual.png", "Set up manually with the network check", "Manuell einrichten mit der Netzwerk-Prüfung"),
        P("On the start screen **Set up manually** → choose the device → enter its address → **Check connection**. You get a tick or a cross for each step (valid address, network connection, same network, device answers, data interface) and a sentence what to change.",
          "Auf dem Startbildschirm **Manuell einrichten** → Gerät wählen → Adresse eintragen → **Verbindung prüfen**. Du bekommst für jeden Schritt einen Haken oder ein Kreuz (gültige Adresse, Netzwerkverbindung, gleiches Netz, Gerät antwortet, Datenschnittstelle) und einen Satz, was du ändern musst."),
    )
    return (("Set an IP address on macOS and Windows", "IP-Adresse unter macOS und Windows einstellen"),
            ("When LJ Link needs a fixed IP address and how to set it on macOS and Windows. Where to find the address of Pioneer players, Denon, grandMA2 and grandMA3.",
             "Wann LJ Link eine feste IP-Adresse braucht und wie man sie unter macOS und Windows einstellt. Wo die Adresse von Pioneer-Playern, Denon, grandMA2 und grandMA3 steht."),
            ("Network", "Netzwerk"), ("Set an IP address", "IP-Adresse einstellen"),
            ("Only needed in a few cases. Here is how, step by step.", "Nur in wenigen Fällen nötig. So geht es, Schritt für Schritt."), body)


@page("wiki-network-ports.html", "Ports and firewall", "Ports und Firewall")
def _():
    body = SEC(
        H2("Ports used by LJ Link", "Von LJ Link benutzte Ports"),
        TABLE([("What", "Was"), ("Protocol and port", "Protokoll und Port"), ("Direction", "Richtung")], [
            [("Pioneer Pro DJ Link: announcements, beats, status", "Pioneer Pro DJ Link: Anmeldung, Beats, Status"), "UDP 50000, 50001, 50002", ("in and out", "rein und raus")],
            [("Pioneer track data (dbserver)", "Pioneer-Trackdaten (dbserver)"), "TCP 12523 " + el("span", "and the port the player names", "und der Port, den der Player nennt"), ("out", "raus")],
            [("Denon StageLinQ discovery", "Denon StageLinQ Suche"), "UDP 51337", ("in and out", "rein und raus")],
            [("Denon StageLinQ data", "Denon StageLinQ Daten"), ("TCP, ports the device names", "TCP, Ports, die das Gerät nennt"), ("out", "raus")],
            [("Ableton Link tempo", "Ableton-Link-Tempo"), "UDP 20808, multicast 224.76.78.75", ("in; out when LJ Link leads the session", "rein; raus, wenn LJ Link die Session führt")],
            [("Ableton Link clock sync (only when leading)", "Ableton-Link-Uhrabgleich (nur beim Führen)"), ("UDP, a free port per adapter", "UDP, ein freier Port pro Adapter"), ("in and out", "rein und raus")],
            [("grandMA2", "grandMA2"), "TCP 30000 (Telnet)", ("out", "raus")],
            [("grandMA3", "grandMA3"), ("UDP, as set on the desk (e.g. 8000)", "UDP, wie am Pult eingestellt (z. B. 8000)"), ("out", "raus")],
            [("Daslight, own OSC target", "Daslight, eigenes OSC-Ziel"), ("UDP, as set", "UDP, wie eingestellt"), ("out", "raus")],
            [("ChamSys MagicQ (OSC taps)", "ChamSys MagicQ (OSC-Taps)"), ("UDP, as set on the desk (e.g. 8000)", "UDP, wie am Pult eingestellt (z. B. 8000)"), ("out", "raus")],
            [("Wolfmix / OS2L", "Wolfmix / OS2L"), ("TCP 5000 (WTOOLS default)", "TCP 5000 (Vorgabe von WTOOLS)"), ("out", "raus")],
            [("Web interface (browser, tablet)", "Weboberfläche (Browser, Tablet)"), ("TCP 8080, change with `--port`", "TCP 8080, änderbar mit `--port`"), ("in", "rein")],
            [("Update check", "Update-Prüfung"), ("HTTPS to github.com", "HTTPS zu github.com"), ("out", "raus")],
        ]),
        H2("Programs that take the same ports", "Programme, die dieselben Ports belegen"),
        P("A port can only be used by one program on a computer. Close these programs on the computer that runs LJ Link:",
          "Ein Port kann auf einem Computer nur von einem Programm benutzt werden. Diese Programme auf dem Computer mit LJ Link schließen:"),
        TABLE([("Program", "Programm"), ("Conflict", "Konflikt"), ("What you see", "Was du siehst")], [
            [("rekordbox (Link or Performance mode)", "rekordbox (Link- oder Performance-Modus)"), "UDP 50000–50002", ("players not found or no tracks", "Player nicht gefunden oder keine Tracks")],
            [("Beat Link Trigger, SoundSwitch, a second LJ Link", "Beat Link Trigger, SoundSwitch, ein zweites LJ Link"), "UDP 50001", ("yellow note *Beat port 50001 is in use*", "gelber Hinweis *Beat port 50001 is in use*")],
            [("Engine DJ, SoundSwitch, Resolume (StageLinQ)", "Engine DJ, SoundSwitch, Resolume (StageLinQ)"), "UDP 51337", ("Denon not found or reconnecting", "Denon nicht gefunden oder verbindet sich ständig neu")],
            [("another web server on 8080", "ein anderer Webserver auf 8080"), "TCP 8080", ("interface does not open", "Oberfläche öffnet sich nicht")],
        ]),
        ADM("note", ("On another computer in the same network these programs do not disturb LJ Link’s ports.", "Auf einem anderen Computer im selben Netz stören diese Programme die Ports von LJ Link nicht.")),
        H2("Firewall", "Firewall"),
        H3("macOS", "macOS"),
        P("macOS asks once for **Local Network**. Allow it. Changing later: System Settings → **Privacy & Security → Local Network**. If the macOS firewall is on (Network → Firewall), allow incoming connections for LJ Link.",
          "macOS fragt einmal nach **Lokales Netzwerk**. Erlauben. Später ändern: Systemeinstellungen → **Datenschutz & Sicherheit → Lokales Netzwerk**. Ist die macOS-Firewall an (Netzwerk → Firewall), eingehende Verbindungen für LJ Link erlauben."),
        H3("Windows", "Windows"),
        P("Allow LJ Link for **private networks** and make sure the network profile is *Private*, not *Public*. Other security software (antivirus with firewall) may need its own rule for LJ Link.",
          "LJ Link für **private Netzwerke** zulassen und darauf achten, dass das Netzwerkprofil *Privat* ist, nicht *Öffentlich*. Andere Sicherheitssoftware (Virenscanner mit Firewall) braucht eventuell eine eigene Regel für LJ Link."),
        H3("Light desk computer", "Pult-Computer"),
        P("grandMA onPC on another Windows computer also asks the firewall on its first start: allow **private networks** there too, otherwise the tempo never arrives.",
          "grandMA onPC auf einem anderen Windows-Computer fragt beim ersten Start ebenfalls die Firewall: auch dort **private Netzwerke** erlauben, sonst kommt das Tempo nie an."),
        H2("VPN and virtual adapters", "VPN und virtuelle Adapter"),
        P("VPN clients, Parallels, VMware, VirtualBox, Hyper-V and Docker add virtual network adapters. They can make LJ Link listen in the wrong place or see packets twice. Switch them off for the gig.",
          "VPN-Programme, Parallels, VMware, VirtualBox, Hyper-V und Docker legen virtuelle Netzwerkadapter an. Dadurch hört LJ Link eventuell an der falschen Stelle oder sieht Pakete doppelt. Für den Gig ausschalten."),
    )
    return (("Ports and firewall", "Ports und Firewall"),
            ("All network ports used by LJ Link (Pro DJ Link, StageLinQ, Ableton Link, Telnet, OSC, OS2L, web interface), programs that conflict and firewall settings on macOS and Windows.",
             "Alle Netzwerk-Ports von LJ Link (Pro DJ Link, StageLinQ, Ableton Link, Telnet, OSC, OS2L, Weboberfläche), Programme mit Konflikten und Firewall-Einstellungen unter macOS und Windows."),
            ("Network", "Netzwerk"), ("Ports and firewall", "Ports und Firewall"),
            ("For network admins and for when something blocks.", "Für Netzwerk-Admins und für den Fall, dass etwas blockiert."), body)
