from wikilib import *

group("Light desks", "Lichtpulte")

REPORT = ADM("example", ("Tried it on your desk? Post the result in our [Discord](" + DISCORD + ") or [report it](wiki-troubleshooting.html#report): desk, software version, what worked and what did not. That is how beta and alpha become stable.",
                         "An deinem Pult ausprobiert? Schreib das Ergebnis in unseren [Discord](" + DISCORD + ") oder [melde es](wiki-troubleshooting.html#report): Pult, Softwareversion, was ging und was nicht. So wird aus Beta und Alpha stabil."),
             title=("Help us test", "Hilf beim Testen"))


@page("wiki-lighting-desks.html", "Overview", "Übersicht")
def _():
    body = SEC(
        H2("Which desk, which way", "Welches Pult, welcher Weg"),
        TABLE([("Desk", "Pult"), ("Status", "Stand"), ("Way", "Weg"), ("What arrives", "Was ankommt")], [
            [("[grandMA2, grandMA2 onPC](wiki-grandma2.html)", "[grandMA2, grandMA2 onPC](wiki-grandma2.html)"), BADGE("stable"), "Telnet, TCP 30000", "<code>SpecialMaster 3.16 At 128</code>"],
            [("[grandMA3, grandMA3 onPC](wiki-grandma3.html)", "[grandMA3, grandMA3 onPC](wiki-grandma3.html)"), BADGE("beta"), ("OSC, UDP (e.g. 8000)", "OSC, UDP (z. B. 8000)"), "<code>Master 3.1 At BPM 128</code>"],
            [("[Wolfmix (WTOOLS)](wiki-wolfmix.html)", "[Wolfmix (WTOOLS)](wiki-wolfmix.html)"), BADGE("alpha"), "OS2L, TCP 5000", ("every beat with its number and the tempo", "jeder Beat mit Nummer und das Tempo")],
            [("[Daslight](wiki-daslight.html)", "[Daslight](wiki-daslight.html)"), BADGE("unk"), "OSC, UDP", ("a number `128.0` at `/ljlinkpro/bpm`", "eine Zahl `128.0` an `/ljlinkpro/bpm`")],
            [("[Own OSC target](wiki-other-desks.html#osc)", "[Eigenes OSC-Ziel](wiki-other-desks.html#osc)"), BADGE("gen"), "OSC, UDP", ("a number at your address", "eine Zahl an deine Adresse")],
            [("[QLC+ and other OS2L programs](wiki-other-desks.html#os2l)", "[QLC+ und andere OS2L-Programme](wiki-other-desks.html#os2l)"), BADGE("alpha"), "OS2L, TCP", ("beats and tempo", "Beats und Tempo")],
            [("[dot2](wiki-other-desks.html#dot2)", "[dot2](wiki-other-desks.html#dot2)"), BADGE("no"), "–", ("takes no network commands", "nimmt keine Netzwerk-Befehle an")],
        ]),
        H2("How the link works", "So funktioniert der Link"),
        P("LJ Link reads the tempo of the deck chosen under **SEND BPM** and sends it to the desk. The desk puts it on a **speed master**; every effect and chaser assigned to that master runs in time.",
          "LJ Link liest das Tempo des Decks, das unter **SEND BPM** gewählt ist, und schickt es ans Pult. Das Pult legt es auf einen **Speedmaster**; jeder Effekt und Chaser, der diesem Master zugeordnet ist, läuft im Takt."),
        UL(("Sent as soon as the tempo changes by at least **0.1 BPM**.", "Gesendet wird, sobald sich das Tempo um mindestens **0,1 BPM** ändert."),
           ("Repeated when it stays the same: OSC every **2 seconds**, grandMA2 every **15 seconds** (so the command line does not fill up).", "Bleibt es gleich, wird wiederholt: OSC alle **2 Sekunden**, grandMA2 alle **15 Sekunden** (damit die Kommandozeile nicht vollläuft)."),
           ("OS2L (Wolfmix) gets **every single beat**, so the desk also knows where the bar starts.", "OS2L (Wolfmix) bekommt **jeden einzelnen Beat**, das Pult weiß also auch, wo der Takt anfängt."),
           ("LJ Link **only sets the tempo**. It starts no cues and changes nothing in the show.", "LJ Link **setzt nur das Tempo**. Es startet keine Cues und ändert nichts an der Show."),
           ("The **demo never sends** anything.", "Die **Demo sendet nie** etwas.")),
        ADM("danger", ("A speed master only moves effects and chasers that are **assigned** to it. If the master changes but nothing happens on stage, the effect is almost always on another master.",
                       "Ein Speedmaster bewegt nur Effekte und Chaser, die ihm **zugeordnet** sind. Ändert sich der Master, aber auf der Bühne passiert nichts, liegt der Effekt fast immer auf einem anderen Master.")),
        H2("SEND BPM: which deck gives the tempo", "SEND BPM: welches Deck das Tempo gibt", "send-bpm"),
        IMG("send-bpm.png", "The SEND BPM tab", "Der Reiter SEND BPM"),
        TABLE([("Choice", "Auswahl"), ("Behaviour", "Verhalten"), ("Good for", "Gut für")], [
            [("**Auto**", "**Auto**"), ("the deck that is master and/or live; with two systems the master that is also live and playing", "das Deck, das Master und/oder live ist; bei zwei Systemen der Master, der auch live ist und spielt"), ("normal nights, one system", "normale Abende, ein System")],
            [("**One deck**", "**Ein Deck**"), ("always this deck, even if another one is live; without a track nothing is sent", "immer dieses Deck, auch wenn ein anderes live ist; ohne Track wird nichts gesendet"), ("Ableton Link as source, tempo jumping between two masters", "Ableton Link als Quelle, Tempo springt zwischen zwei Mastern")],
        ]),
        P("The deck that sends wears a green **SEND BPM** in every view, tablets included. The choice stays after a restart and can only be changed on the computer itself.",
          "Das sendende Deck trägt in jeder Ansicht ein grünes **SEND BPM**, auch auf Tablets. Die Wahl bleibt nach einem Neustart und lässt sich nur am Computer selbst ändern."),
        H2("Set up in LJ Link", "In LJ Link einrichten"),
        STEPS(("Menu (☰) → **Light desk link (send tempo)**, or the tab **LINK**.", "Menü (☰) → **Lichtpult-Link (Tempo senden)**, oder der Reiter **LINK**."),
              ("Choose the desk. The fields below change to what this desk needs, with a hint.", "Pult wählen. Die Felder darunter passen sich an das Pult an, mit einem Hinweis."),
              ("Address and port, for grandMA2 also user and password. onPC on the same computer: `127.0.0.1`.", "Adresse und Port, beim grandMA2 auch Benutzer und Passwort. onPC auf demselben Computer: `127.0.0.1`."),
              ("**Save**, then **Send test (120 BPM)**. Below it *Last sent* shows the value or an error.", "**Speichern**, dann **Test senden (120 BPM)**. Darunter zeigt *Zuletzt gesendet* den Wert oder einen Fehler.")),
        H2("Status messages", "Statusmeldungen"),
        TABLE([("Message", "Meldung"), ("Meaning", "Bedeutung")], [
            [("“Sending the tempo is switched off”", "„Tempo senden ist ausgeschaltet“"), ("no desk chosen", "kein Pult gewählt")],
            [("“Waiting for a deck with a tempo …”", "„Warte auf ein Deck mit Tempo …“"), ("no deck plays or has a track", "kein Deck spielt oder hat einen Track")],
            [("“The chosen deck has no tempo right now”", "„Das gewählte Deck hat gerade kein Tempo“"), ("fixed deck under SEND BPM is empty", "festes Deck unter SEND BPM ist leer")],
            [("“Demo mode: nothing is sent to the desk”", "„Demo-Modus: Es wird nichts ans Pult gesendet“"), ("demo runs", "Demo läuft")],
            [("“Connection refused”", "„Connection refused“"), ("desk not reachable on this port (grandMA2: Telnet off)", "Pult auf diesem Port nicht erreichbar (grandMA2: Telnet aus)")],
            [("“No OS2L receiver at …”", "„Kein OS2L-Empfänger unter …“"), ("WTOOLS not running or OS2L off", "WTOOLS läuft nicht oder OS2L ist aus")],
        ]),
    )
    return (("Light desks: tempo to grandMA2, grandMA3, Wolfmix, Daslight", "Lichtpulte: Tempo an grandMA2, grandMA3, Wolfmix, Daslight"),
            ("How LJ Link sends the DJ tempo to a light desk: grandMA2 over Telnet, grandMA3 over OSC, Wolfmix over OS2L, Daslight and OSC programs. SEND BPM, timing and status messages.",
             "Wie LJ Link das DJ-Tempo an ein Lichtpult schickt: grandMA2 über Telnet, grandMA3 über OSC, Wolfmix über OS2L, Daslight und OSC-Programme. SEND BPM, Timing und Statusmeldungen."),
            ("Light desks", "Lichtpulte"), ("Tempo to the light desk", "Tempo ans Lichtpult"),
            ("Which desk works how well, and how the tempo gets there.", "Welches Pult wie gut funktioniert, und wie das Tempo dort ankommt."), body)


@page("wiki-grandma2.html", "grandMA2", "grandMA2")
def _():
    body = SEC(
        ADM("tip", ("**Stable:** search, Telnet login and `SpecialMaster 3.16 At {bpm}` were tested on a real grandMA2 onPC. A hardware console uses the same software and is expected to behave the same; not tested yet.",
                    "**Stabil:** Suche, Telnet-Login und `SpecialMaster 3.16 At {bpm}` wurden an einem echten grandMA2 onPC getestet. Ein Hardware-Pult nutzt dieselbe Software und sollte sich gleich verhalten; noch nicht getestet."),
            title=("Status", "Stand")),
        H2("How it works", "So funktioniert es"),
        P("The grandMA2 has **no OSC input**. LJ Link connects like a Telnet program to the command line of the desk (TCP port 30000), logs in as a user of the show and types the command for the speed master.",
          "Der grandMA2 hat **keinen OSC-Eingang**. LJ Link verbindet sich wie ein Telnet-Programm mit der Kommandozeile des Pults (TCP-Port 30000), meldet sich als Benutzer der Show an und tippt den Befehl für den Speedmaster."),
        H2("1. Switch on Telnet", "1. Telnet einschalten"),
        STEPS(("On the desk (or in onPC) press **Setup**.", "Am Pult (oder in onPC) **Setup** drücken."),
              ("Tap **Console**, then **Global Settings**.", "**Console** antippen, dann **Global Settings**."),
              ("Set **Telnet** to **Login Enabled**.", "**Telnet** auf **Login Enabled** stellen.")),
        P("While Telnet is off, the search still finds the desk, but it takes no commands.", "Solange Telnet aus ist, findet die Suche das Pult zwar, aber es nimmt keine Befehle an."),
        H2("2. A user for LJ Link", "2. Ein Benutzer für LJ Link"),
        P("Every Telnet connection logs in as a user of the show. Names and passwords are case sensitive.", "Jede Telnet-Verbindung meldet sich als Benutzer der Show an. Namen und Passwörter unterscheiden Groß- und Kleinschreibung."),
        TABLE([("Option", "Möglichkeit"), ("User / password", "Benutzer / Passwort"), ("Note", "Hinweis")], [
            [("default of every show", "Standard jeder Show"), "<code>administrator</code> / <code>admin</code>", ("LJ Link enters this by default; full access", "LJ Link trägt das vor; voller Zugriff")],
            [("own user (recommended by MA)", "eigener Benutzer (empfiehlt MA)"), ("e.g. `lj` / `link`", "z. B. `lj` / `link`"), ("rights at least *Playback*, if unsure *Program*; **save the show**", "Rechte mindestens *Playback*, im Zweifel *Program*; **Show speichern**")],
        ]),
        ADM("warn", ("The default password `admin` is public. In shows on an open network change it, and enter the new one in LJ Link.", "Das Standardpasswort `admin` ist öffentlich bekannt. In Shows in einem offenen Netz ändern und das neue in LJ Link eintragen.")),
        H2("3. Prepare the speed master", "3. Speedmaster vorbereiten"),
        STEPS(("Choose the speed master: **Speed 1 to 15** or **Speed 16 = BPM**. On the command line they are `SpecialMaster 3.1` to `SpecialMaster 3.16`.", "Speedmaster wählen: **Speed 1 bis 15** oder **Speed 16 = BPM**. In der Kommandozeile heißen sie `SpecialMaster 3.1` bis `SpecialMaster 3.16`."),
              ("Assign the effects and chasers that should run in time to this master.", "Die Effekte und Chaser, die im Takt laufen sollen, diesem Master zuordnen."),
              ("Test by hand: type `SpecialMaster 3.16 At 120` and Enter. The master shows 120 BPM in the *Masters* window and the effect runs in time.", "Von Hand testen: `SpecialMaster 3.16 At 120` tippen und Enter. Der Master zeigt im Fenster *Masters* 120 BPM, und der Effekt läuft im Takt.")),
        H2("4. Set up in LJ Link", "4. In LJ Link einrichten"),
        IMG("settings.png", "Light desk link settings", "Einstellungen des Lichtpult-Links"),
        STEPS(("Menu (☰) → **Light desk link** or tab **LINK** → **grandMA2 / grandMA2 onPC (Telnet)**.", "Menü (☰) → **Lichtpult-Link** oder Reiter **LINK** → **grandMA2 / grandMA2 onPC (Telnet)**."),
              ("**Search for the desk** and click the hit. It finds every grandMA2 and onPC in the network, even with Telnet off. Or type the address; onPC on this computer: `127.0.0.1`.", "**Pult suchen** und den Treffer anklicken. Gefunden wird jeder grandMA2 und jedes onPC im Netz, auch mit ausgeschaltetem Telnet. Oder die Adresse eintippen; onPC auf diesem Computer: `127.0.0.1`."),
              ("Port `30000`. User and password as in step 2.", "Port `30000`. Benutzer und Passwort wie in Schritt 2."),
              ("**Send to speed master**: Speed 16 (BPM) or 1 to 15. The command becomes `SpecialMaster 3.16 At {bpm}`.", "**An Speedmaster senden**: Speed 16 (BPM) oder 1 bis 15. Der Befehl wird `SpecialMaster 3.16 At {bpm}`."),
              ("**Save**, **Send test (120 BPM)**. The master jumps to 120.", "**Speichern**, **Test senden (120 BPM)**. Der Master springt auf 120.")),
        ADM("info", ("`{bpm}` becomes a **whole number** such as `128`: the grandMA2 command line ignores decimals (`At 128.0` does nothing). Every command lands in the command history, hence the repeat only every 15 seconds.",
                     "`{bpm}` wird eine **ganze Zahl** wie `128`: Die Kommandozeile des grandMA2 ignoriert Kommazahlen (`At 128.0` bewirkt nichts). Jeder Befehl landet in der Befehlshistorie, deshalb die Wiederholung nur alle 15 Sekunden.")),
        H2("Problems", "Probleme"),
        TABLE([("You see", "Du siehst"), ("Cause", "Ursache"), ("Fix", "Lösung")], [
            [("“Connection refused”", "„Connection refused“"), ("Telnet off or wrong address", "Telnet aus oder falsche Adresse"), ("step 1, check the address", "Schritt 1, Adresse prüfen")],
            [("nothing arrives, no error", "es kommt nichts an, kein Fehler"), ("user or password wrong (case!)", "Benutzer oder Passwort falsch (Groß/klein!)"), ("check the user, was the show saved?", "Benutzer prüfen, Show gespeichert?")],
            [("“Error #44 INSUFFICIENT USER RIGHTS”", "„Error #44 INSUFFICIENT USER RIGHTS“"), ("user may not change masters", "Benutzer darf keine Master ändern"), ("give more rights", "mehr Rechte geben")],
            [("master changes, effect does not", "Master ändert sich, Effekt nicht"), ("effect on another master", "Effekt auf anderem Master"), ("assign the effect", "Effekt zuordnen")],
            [("search finds nothing", "Suche findet nichts"), ("other network, desk off", "anderes Netz, Pult aus"), ("[network](wiki-network-setups.html#desk)", "[Netzwerk](wiki-network-setups.html#desk)")],
        ]),
        ADM("note", ("Do not enter Fixture Setup or the schedule over Telnet: according to MA this locks other users out without warning. LJ Link only sends the command you entered.",
                     "Nicht über Telnet ins Fixture-Setup oder den Zeitplan gehen: Laut MA sperrt das andere Benutzer ohne Warnung aus. LJ Link schickt nur den eingetragenen Befehl.")),
        REPORT,
    )
    return (("grandMA2 and grandMA2 onPC: BPM over Telnet", "grandMA2 und grandMA2 onPC: BPM über Telnet"),
            ("Send the DJ tempo to a grandMA2 or grandMA2 onPC speed master with LJ Link: switch on Telnet, user and password, speed master, search, test and problems.",
             "Das DJ-Tempo mit LJ Link auf einen Speedmaster am grandMA2 oder grandMA2 onPC schicken: Telnet einschalten, Benutzer und Passwort, Speedmaster, Suche, Test und Probleme."),
            ("Light desks · stable", "Lichtpulte · stabil"), ("grandMA2 and onPC", "grandMA2 und onPC"),
            ("The tempo on a speed master over Telnet.", "Das Tempo über Telnet auf einen Speedmaster."), body)


@page("wiki-grandma3.html", "grandMA3", "grandMA3")
def _():
    body = SEC(
        ADM("beta", ("Built from the grandMA3 manual (OSC `/cmd`, `Master 3.1 At BPM`) and tested against a simulation. **Not yet confirmed on a real grandMA3 or onPC.**",
                     "Nach dem grandMA3-Handbuch gebaut (OSC `/cmd`, `Master 3.1 At BPM`) und gegen eine Simulation getestet. **Noch nicht an einem echten grandMA3 oder onPC bestätigt.**")),
        H2("How it works", "So funktioniert es"),
        P("The grandMA3 takes command line commands over **OSC** (address `/cmd`, text). LJ Link sends the command for the speed master there.",
          "Der grandMA3 nimmt Befehle für die Kommandozeile per **OSC** an (Adresse `/cmd`, Text). LJ Link schickt den Befehl für den Speedmaster dorthin."),
        H2("1. OSC input on the desk", "1. OSC-Eingang am Pult"),
        STEPS(("**Menu** → **In & Out** → tab **OSC**.", "**Menu** → **In & Out** → Reiter **OSC**."),
              ("At **Interface** choose the network port through which the LJ Link computer is reachable.", "Bei **Interface** den Netzwerkanschluss wählen, über den der LJ-Link-Computer erreichbar ist."),
              ("Add a line (or use one) with these values:", "Eine Zeile anlegen (oder eine vorhandene nehmen) mit diesen Werten:",
               TABLE([("Field", "Feld"), ("Value", "Wert")], [
                   ["<b>Mode</b>", "<code>UDP</code>"],
                   ["<b>Port</b>", el("span", "8000 or another free port, the same in LJ Link", "8000 oder ein anderer freier Port, in LJ Link gleich")],
                   ["<b>Prefix</b>", el("span", "empty", "leer")],
                   ["<b>Receive Command</b>", el("span", "Yes (otherwise commands are ignored)", "Yes (sonst werden Befehle ignoriert)")],
                   ["<b>EchoInput</b>", el("span", "Yes for testing: incoming messages show in the System Monitor", "Yes zum Testen: eingehende Nachrichten erscheinen im System Monitor")],
               ])),
              ("Switch on **Enable Input**. When OSC arrives, its title bar lights yellow.", "**Enable Input** einschalten. Kommt OSC an, leuchtet seine Titelleiste gelb.")),
        ADM("info", ("With a prefix such as `gma3` the desk expects `/gma3/cmd`. LJ Link sends to `/cmd`, so leave the prefix empty.", "Mit einem Prefix wie `gma3` erwartet das Pult `/gma3/cmd`. LJ Link sendet an `/cmd`, also das Prefix leer lassen.")),
        H2("2. Speed master", "2. Speedmaster"),
        STEPS(("Assign the effect or sequence to a speed master (the first is `Master 3.1`).", "Den Effekt oder die Sequenz einem Speedmaster zuordnen (der erste heißt `Master 3.1`)."),
              ("Test on the command line: `Master 3.1 At BPM 120`, Enter.", "In der Kommandozeile testen: `Master 3.1 At BPM 120`, Enter.")),
        H2("3. Set up in LJ Link", "3. In LJ Link einrichten"),
        STEPS(("**Light desk link** → **grandMA3 / grandMA3 onPC (OSC)**.", "**Lichtpult-Link** → **grandMA3 / grandMA3 onPC (OSC)**."),
              ("Address of the desk (onPC on this computer: `127.0.0.1`). The search does not find a grandMA3 yet.", "Adresse des Pults (onPC auf diesem Computer: `127.0.0.1`). Die Suche findet einen grandMA3 noch nicht."),
              ("Port as on the desk, e.g. `8000`. Command `Master 3.1 At BPM {bpm}`.", "Port wie am Pult, z. B. `8000`. Befehl `Master 3.1 At BPM {bpm}`."),
              ("**Save**, **Send test**. With EchoInput the message shows in the System Monitor.", "**Speichern**, **Test senden**. Mit EchoInput erscheint die Nachricht im System Monitor.")),
        H2("Problems", "Probleme"),
        TABLE([("You see", "Du siehst"), ("Cause", "Ursache"), ("Fix", "Lösung")], [
            [("Enable Input never lights", "Enable Input leuchtet nie"), ("wrong port, wrong interface, firewall", "falscher Port, falsches Interface, Firewall"), ("same port? right interface? allow in the firewall", "gleicher Port? richtiges Interface? in der Firewall erlauben")],
            [("lights, but nothing happens", "leuchtet, aber nichts passiert"), ("Receive Command `No` or a prefix", "Receive Command `No` oder ein Prefix"), ("`Receive Command = Yes`, prefix empty", "`Receive Command = Yes`, Prefix leer")],
            [("master changes, effect does not", "Master ändert sich, Effekt nicht"), ("effect on another master", "Effekt auf anderem Master"), ("assign the effect", "Effekt zuordnen")],
        ]),
        REPORT,
    )
    return (("grandMA3 and grandMA3 onPC: BPM over OSC (beta)", "grandMA3 und grandMA3 onPC: BPM über OSC (Beta)"),
            ("Send the DJ tempo to a grandMA3 or grandMA3 onPC speed master over OSC with LJ Link: In & Out settings, Receive Command, speed master, test and problems. Beta.",
             "Das DJ-Tempo mit LJ Link über OSC auf einen Speedmaster am grandMA3 oder grandMA3 onPC schicken: In-&-Out-Einstellungen, Receive Command, Speedmaster, Test und Probleme. Beta."),
            ("Light desks · beta", "Lichtpulte · Beta"), ("grandMA3 and onPC", "grandMA3 und onPC"),
            ("The tempo on a speed master over OSC.", "Das Tempo über OSC auf einen Speedmaster."), body)


@page("wiki-wolfmix.html", "Wolfmix", "Wolfmix")
def _():
    body = SEC(
        ADM("alpha", ("**Never tried on a real Wolfmix.** Built from the OS2L protocol and tested against a test receiver (beats in time, on average 1.5 ms off). It may not work at all. Do not rely on it at an important event, and keep tap or the microphone as a fallback.",
                      "**Noch nie an einem echten Wolfmix ausprobiert.** Nach dem OS2L-Protokoll gebaut und gegen einen Test-Empfänger geprüft (Beats im Takt, im Schnitt 1,5 ms Abweichung). Es kann sein, dass es gar nicht funktioniert. Nicht bei einer wichtigen Veranstaltung darauf verlassen und Tap oder Mikrofon als Rückfall bereithalten.")),
        H2("How it works", "So funktioniert es"),
        P("The Wolfmix has **no network**, only USB and DMX. The tempo comes through the maker’s app **WTOOLS** on the computer the Wolfmix is plugged into. LJ Link talks to WTOOLS over **OS2L** (Open Sound to Light, the way VirtualDJ does it) and sends **every beat** with its number, not only the tempo. Every fourth beat is the one of the bar, matching the beat grid of the player.",
          "Der Wolfmix hat **kein Netzwerk**, nur USB und DMX. Das Tempo kommt über die Hersteller-App **WTOOLS** auf dem Computer, an dem der Wolfmix hängt. LJ Link spricht mit WTOOLS über **OS2L** (Open Sound to Light, so macht es auch VirtualDJ) und schickt **jeden Beat** mit Nummer, nicht nur das Tempo. Jeder vierte Beat ist die Eins des Takts, passend zum Beatgrid des Players."),
        DIAGRAM(720, 150, {"dj": (100, 75, 120, 50, "dj", ("DJ gear", "DJ-Geräte"), None),
                           "pc": (360, 75, 190, 60, "pc", ("Computer", "Computer"), ("LJ Link + WTOOLS", "LJ Link + WTOOLS")),
                           "w": (620, 75, 120, 50, "desk", ("Wolfmix", "Wolfmix"), None)},
                [("dj", "pc", "cable", ("network", "Netzwerk")), ("pc", "w", "usb", ("USB", "USB"))],
                ("Best setup: LJ Link and WTOOLS on the same computer, the Wolfmix by USB.", "Bester Aufbau: LJ Link und WTOOLS auf demselben Computer, der Wolfmix per USB.")),
        H2("Step by step", "Schritt für Schritt"),
        STEPS(("Plug the Wolfmix into the computer by USB and start **WTOOLS**. Switch on **OS2L** there.", "Den Wolfmix per USB an den Computer anschließen und **WTOOLS** starten. Dort **OS2L** einschalten."),
              ("In LJ Link: **Light desk link** → **Wolfmix (OS2L, WTOOLS) – Alpha**.", "In LJ Link: **Lichtpult-Link** → **Wolfmix (OS2L, WTOOLS) – Alpha**."),
              ("Address `127.0.0.1` if WTOOLS runs on the same computer, otherwise the address of the WTOOLS computer. Port `5000`. **Save**.", "Adresse `127.0.0.1`, wenn WTOOLS auf demselben Computer läuft, sonst die Adresse des WTOOLS-Computers. Port `5000`. **Speichern**."),
              ("**Send test**: eight beats at 120 BPM.", "**Test senden**: acht Beats mit 120 BPM."),
              ("On the Wolfmix **push the first encoder** in the FX rack, so the effects follow the BPM.", "Am Wolfmix im FX-Rack den **ersten Drehregler drücken**, damit die Effekte dem BPM folgen.")),
        H2("What is sent", "Was gesendet wird"),
        UL(("A beat message for every beat of the deck under SEND BPM, with beat number and tempo.", "Eine Beat-Nachricht für jeden Beat des Decks unter SEND BPM, mit Beat-Nummer und Tempo."),
           ("**change** after start, a jump (cue, loop), a track or deck change or a tempo step, so the desk finds the phase again.", "**change** nach Start, Sprung (Cue, Loop), Track- oder Deckwechsel oder Tempo-Sprung, damit das Pult die Phase neu findet."),
           ("A standing deck sends no beats. Serato and Ableton Link have no position: there the beats run freely at the tempo.", "Ein stehendes Deck sendet keine Beats. Serato und Ableton Link haben keine Position: Dort laufen die Beats frei im Tempo."),
           ("If WTOOLS is not running, LJ Link shows it in the status and tries again every 5 seconds.", "Läuft WTOOLS nicht, zeigt LJ Link das im Status und versucht es alle 5 Sekunden neu.")),
        H2("What is not known yet", "Was noch unbekannt ist"),
        UL(("Whether WTOOLS reads the messages exactly as sent.", "Ob WTOOLS die Nachrichten genau so liest, wie sie gesendet werden."),
           ("Whether WTOOLS accepts connections from another computer.", "Ob WTOOLS Verbindungen von einem anderen Computer annimmt."),
           ("How much delay USB adds between WTOOLS and the Wolfmix.", "Wie viel Verzögerung USB zwischen WTOOLS und Wolfmix dazugibt.")),
        P("The full alpha guide: [Wolfmix guide on GitHub](https://github.com/skyplayser/LJ-Link/blob/main/docs/wolfmix-anleitung.de.md) (German).", "Die ausführliche Alpha-Anleitung: [Wolfmix-Anleitung auf GitHub](https://github.com/skyplayser/LJ-Link/blob/main/docs/wolfmix-anleitung.de.md)."),
        REPORT,
    )
    return (("Wolfmix over OS2L and WTOOLS (alpha)", "Wolfmix über OS2L und WTOOLS (Alpha)"),
            ("Send DJ beats and tempo from LJ Link to a Wolfmix through WTOOLS over OS2L. Untested alpha feature: setup, what is sent and what is not known yet.",
             "DJ-Beats und Tempo von LJ Link über WTOOLS per OS2L an einen Wolfmix schicken. Ungetestetes Alpha-Feature: Einrichtung, was gesendet wird und was noch unbekannt ist."),
            ("Light desks · alpha", "Lichtpulte · Alpha"), ("Wolfmix", "Wolfmix"),
            ("Every beat over OS2L through WTOOLS. Untested.", "Jeder Beat über OS2L durch WTOOLS. Ungetestet."), body)


@page("wiki-daslight.html", "Daslight", "Daslight")
def _():
    body = SEC(
        ADM("warn", ("**Unconfirmed:** LJ Link sends, but whether your Daslight version can put an OSC number on its BPM is not known yet.",
                     "**Unbestätigt:** LJ Link sendet, aber ob deine Daslight-Version eine OSC-Zahl auf ihr BPM legen kann, ist noch nicht bekannt."), title=("Status", "Stand")),
        H2("How it works", "So funktioniert es"),
        P("Daslight syncs its tempo officially via tap, MIDI clock and Ableton Link, and has an **OSC mapping** for incoming messages. LJ Link sends the tempo as a number over OSC, for example `128.0` to `/ljlinkpro/bpm`.",
          "Daslight synchronisiert sein Tempo offiziell über Tap, MIDI-Clock und Ableton Link und hat ein **OSC-Mapping** für eingehende Nachrichten. LJ Link sendet das Tempo als Zahl über OSC, zum Beispiel `128.0` an `/ljlinkpro/bpm`."),
        H2("Try it", "Ausprobieren"),
        STEPS(("In Daslight switch on the **OSC input** and note the **port**.", "In Daslight die **OSC-Eingabe** einschalten und den **Port** notieren."),
              ("In LJ Link: **Daslight**, address of the Daslight computer (same computer: `127.0.0.1`), port, keep the address `/ljlinkpro/bpm`. **Save**, **Send test**.", "In LJ Link: **Daslight**, Adresse des Daslight-Computers (gleicher Computer: `127.0.0.1`), Port, Adresse `/ljlinkpro/bpm` lassen. **Speichern**, **Test senden**."),
              ("In Daslight check the **MIDI/OSC monitor** for `/ljlinkpro/bpm`.", "In Daslight im **MIDI/OSC-Monitor** nach `/ljlinkpro/bpm` schauen."),
              ("In the **OSC mapping** assign the address to the BPM or speed control, if your version offers it.", "Im **OSC-Mapping** die Adresse dem BPM- oder Speed-Regler zuordnen, sofern deine Version das anbietet.")),
        H2("If it cannot be assigned", "Wenn es sich nicht zuordnen lässt"),
        UL(("Use **Ableton Link** in Daslight with a Link-capable DJ software (Serato, Traktor).", "In Daslight **Ableton Link** mit einer Link-fähigen DJ-Software nutzen (Serato, Traktor)."),
           ("Tap tempo, with the big BPM field in LJ Link as a guide.", "Tap-Tempo, mit dem großen BPM-Feld in LJ Link als Hilfe."),
           ("A MIDI clock straight from LJ Link is planned.", "Eine MIDI-Clock direkt aus LJ Link ist geplant.")),
        REPORT,
    )
    return (("Daslight over OSC (unconfirmed)", "Daslight über OSC (unbestätigt)"),
            ("Send the DJ tempo from LJ Link to Daslight as an OSC number and map it to the BPM. Status unconfirmed: how to try it and the alternatives.",
             "Das DJ-Tempo von LJ Link als OSC-Zahl an Daslight schicken und dem BPM zuordnen. Stand unbestätigt: wie du es ausprobierst und die Alternativen."),
            ("Light desks · unconfirmed", "Lichtpulte · unbestätigt"), ("Daslight", "Daslight"),
            ("The tempo as an OSC number.", "Das Tempo als OSC-Zahl."), body)


@page("wiki-other-desks.html", "OSC, OS2L, dot2", "OSC, OS2L, dot2")
def _():
    body = SEC(
        H2("Own OSC target", "Eigenes OSC-Ziel", "osc"),
        P("**Own OSC target** sends the tempo as a number (float) to an address and port of your choice. That fits Resolume, QLC+, ChamSys MagicQ, TouchDesigner, Max, Node-RED or own scripts: map the address to a tempo control in the target program.",
          "**Eigenes OSC-Ziel** schickt das Tempo als Zahl (Float) an eine frei wählbare Adresse und einen Port. Das passt zu Resolume, QLC+, ChamSys MagicQ, TouchDesigner, Max, Node-RED oder eigenen Skripten: im Zielprogramm die Adresse einem Tempo-Regler zuordnen."),
        TABLE([("Field", "Feld"), ("Example", "Beispiel")], [
            [("Address of the target computer", "Adresse des Zielcomputers"), "<code>127.0.0.1</code>"],
            ["Port", "<code>7000</code>"],
            [("OSC address", "OSC-Adresse"), "<code>/ljlinkpro/bpm</code>"],
            [("Value", "Wert"), el("span", "float, e.g. 128.0, every 2 s and on every change", "Float, z. B. 128.0, alle 2 s und bei jeder Änderung")],
        ]),
        ADM("tip", ("Many programs scale OSC values to 0…1. Then map the range in the target program, for example 0…200 BPM.", "Viele Programme skalieren OSC-Werte auf 0…1. Dann im Zielprogramm den Bereich zuordnen, zum Beispiel 0…200 BPM.")),
        H2("Other OS2L programs", "Andere OS2L-Programme", "os2l"),
        P("The Wolfmix choice works with any program that receives OS2L, for example QLC+ with its OS2L plugin. Enter the address and port of that program instead of WTOOLS. Same alpha status as the [Wolfmix](wiki-wolfmix.html).",
          "Die Auswahl Wolfmix funktioniert mit jedem Programm, das OS2L empfängt, zum Beispiel QLC+ mit seinem OS2L-Plugin. Statt WTOOLS Adresse und Port dieses Programms eintragen. Gleicher Alpha-Stand wie beim [Wolfmix](wiki-wolfmix.html)."),
        H2("dot2", "dot2", "dot2"),
        ADM("danger", ("The dot2 takes neither Telnet nor OSC over the network. According to its manual it only takes MIDI, DMX (also Art-Net and sACN) and contacts from outside, so LJ Link cannot send it the tempo today.",
                       "Der dot2 nimmt übers Netzwerk weder Telnet noch OSC an. Laut Handbuch nimmt er von außen nur MIDI, DMX (auch Art-Net und sACN) und Schaltkontakte an, deshalb kann LJ Link ihm das Tempo heute nicht schicken."), title=("Not possible", "Nicht möglich")),
        UL(("**Tap tempo**: hold the Speed key and tap on an executor key in time; the big BPM field in LJ Link helps counting.", "**Tap-Tempo**: Speed-Taste halten und im Takt auf eine Executor-Taste tippen; das große BPM-Feld in LJ Link hilft beim Mitzählen."),
           ("**Sound to light**: the dot2 has an audio input with BPM detection (window *Sound Input*).", "**Sound-to-Light**: Der dot2 hat einen Audio-Eingang mit BPM-Erkennung (Fenster *Sound Input*)."),
           ("A way over MIDI is planned.", "Ein Weg über MIDI ist geplant.")),
        H2("Your desk is missing?", "Dein Pult fehlt?"),
        P("ChamSys, Avolites, Hog, Onyx, Chauvet, Lightkey, Freestyler … Tell us on [Discord](" + DISCORD + ") which desk you use and how it takes a tempo from outside (OSC, MIDI, Art-Net, Telnet). Often the own OSC target already does it.",
          "ChamSys, Avolites, Hog, Onyx, Chauvet, Lightkey, Freestyler … Sag uns auf [Discord](" + DISCORD + "), welches Pult du nutzt und wie es ein Tempo von außen annimmt (OSC, MIDI, Art-Net, Telnet). Oft reicht schon das eigene OSC-Ziel."),
    )
    return (("Own OSC target, OS2L programs and dot2", "Eigenes OSC-Ziel, OS2L-Programme und dot2"),
            ("Send the DJ tempo to Resolume, QLC+, MagicQ, TouchDesigner or any OSC program, to OS2L programs, and why the dot2 cannot receive it.",
             "Das DJ-Tempo an Resolume, QLC+, MagicQ, TouchDesigner oder jedes OSC-Programm schicken, an OS2L-Programme, und warum der dot2 es nicht empfangen kann."),
            ("Light desks", "Lichtpulte"), ("OSC, OS2L and dot2", "OSC, OS2L und dot2"),
            ("Everything that is not grandMA, Wolfmix or Daslight.", "Alles, was nicht grandMA, Wolfmix oder Daslight ist."), body)
