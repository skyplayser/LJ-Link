"""Writes site/changelog.html (the What's new page) from the list R below, newest version first.

    python3 .github/scripts/changelog_gen.py site

Add every new version at the top of R (English and German, "What's new" and "Fixes"), run this, commit the page.
It takes head and footer from site/pioneer-bpm-grandma.html; build_site.py then makes the German page.
"""
import html, sys, re
from pathlib import Path
site = Path(sys.argv[1])
E = lambda s: html.escape(s, quote=True)

R = [
 ("1.7.8", "10 November 2026", "10. November 2026", [
   ("New setting Settings → Updates → Check for updates automatically (on by default, only on the computer running LJ Link). When it is off, LJ Link only asks GitHub when you choose \"Check for updates\" in the menu of the program icon.",
    "Neue Einstellung Einstellungen → Updates → Automatisch nach Updates suchen (standardmäßig an, nur auf dem Rechner mit LJ Link). Ist sie aus, fragt LJ Link GitHub nur, wenn du im Menü des Programm-Symbols „Nach Updates suchen“ wählst."),
   ("Updates are checked before they are installed: LJ Link only installs a download whose SHA-256 checksum matches the one GitHub publishes for it; otherwise the file is deleted and nothing is installed. Signed updates are prepared; until a signing key is in place the update dialog says that the update is not signed and only the checksum from GitHub is checked.",
    "Updates werden vor der Installation geprüft: LJ Link installiert nur einen Download, dessen SHA-256-Prüfsumme zu der passt, die GitHub dafür angibt; sonst wird die Datei gelöscht und nichts installiert. Signierte Updates sind vorbereitet; solange kein Signaturschlüssel eingetragen ist, zeigt der Update-Dialog an, dass das Update nicht signiert ist und nur die Prüfsumme von GitHub geprüft wird."),
   ("New download names without \"PRO\": LJ-Link-macos-arm64.dmg, LJ-Link-macos-intel.dmg and LJ-Link-windows.exe. For a while each release also carries copies under the old LJ-Link-PRO-… names, so older versions still find their update.",
    "Neue Download-Namen ohne „PRO“: LJ-Link-macos-arm64.dmg, LJ-Link-macos-intel.dmg und LJ-Link-windows.exe. Eine Zeit lang liegen jedem Release zusätzlich Kopien unter den alten Namen LJ-Link-PRO-… bei, damit ältere Versionen ihr Update weiter finden."),
   ("Settings → About LJ Link and the license page show a note on live use and the trademark notice.",
    "Einstellungen → Über LJ Link und die Lizenzseite zeigen einen Hinweis zum Live-Betrieb und den Marken-Hinweis."),
   ("Website, wiki and README: a note on live use (test your setup, keep a fallback such as tap tempo), a clearer trademark notice, and \"works with\" instead of \"tested\", because there is no formal test protocol. Tell us your setup with a hardware report.",
    "Website, Wiki und README: ein Hinweis zum Live-Betrieb (Setup vorher testen, einen Ausweg wie Tap-Tempo bereithalten), ein klarerer Marken-Hinweis und „läuft mit“ statt „getestet“, weil es kein formales Testprotokoll gibt. Erzähl uns mit einem Hardware-Bericht von deinem Setup."),
   ("The privacy policy now covers problem reports, contact, GitHub issues and storage periods, and describes the Discord bot as it works.",
    "Die Datenschutzerklärung beschreibt jetzt auch Fehlerberichte, Kontakt, GitHub-Issues und Speicherdauern, und den Discord-Bot so, wie er arbeitet."),
 ], [
   ("The local control of LJ Link (settings, light desk link, demo, diagnostics) only accepts requests on the computer itself from LJ Link's own page; other websites opened in a browser on that computer can no longer change anything. Tablets keep their view (read-only pages and live data are unchanged).",
    "Die lokale Steuerung von LJ Link (Einstellungen, Lichtpult-Link, Demo, Diagnose) nimmt nur noch Anfragen am Rechner selbst von LJ Links eigener Seite an; andere Websites, die dort im Browser offen sind, können nichts mehr ändern. Tablets behalten ihre Ansicht (Leseseiten und Live-Daten unverändert)."),
   ("Corrected third-party notices (JmDNS, the SQLite library actually bundled, Deep Symmetry, the Java runtime) with a written source code offer; each release now carries the source archives of the bundled Java runtime (Eclipse Temurin) and Remote Tea.",
    "Korrigierte Lizenzhinweise Dritter (JmDNS, die tatsächlich mitgelieferte SQLite-Bibliothek, Deep Symmetry, die Java-Laufzeit) mit schriftlichem Quellcode-Angebot; jedem Release liegen jetzt die Quellarchive der mitgelieferten Java-Laufzeit (Eclipse Temurin) und von Remote Tea bei."),
   ("Denon: the downloaded Engine library is read in memory and the temporary file deleted at once (very large libraries over 256 MB stay as a file only while they are open). Leftovers of earlier runs are deleted at start.",
    "Denon: Die heruntergeladene Engine-Bibliothek wird im Arbeitsspeicher gelesen und die temporäre Datei sofort gelöscht (sehr große Bibliotheken über 256 MB bleiben nur so lange als Datei, wie sie offen sind). Reste früherer Läufe werden beim Start gelöscht."),
   ("Updates ask GitHub as LJ-Link/<version> instead of the old name.",
    "Die Update-Anfrage meldet sich bei GitHub als LJ-Link/<Version> statt mit dem alten Namen."),
   ("The waveform in the browser could fall behind and jump. One slow or sleeping device with the page open (for example an iPad) could hold up all other browsers, and delays on the way to the browser were not compensated. Now the other browsers stay live when one device is slow, every position carries a timestamp so a late message no longer shifts the display, and brief gaps in the position from the players no longer make the waveform jump. Not tested on real hardware yet (alpha).",
    "Die Waveform im Browser konnte hinterherhinken und springen. Ein einzelnes langsames oder schlafendes Gerät mit offener Seite (zum Beispiel ein iPad) konnte alle anderen Browser aufhalten, und Verzögerungen auf dem Weg zum Browser wurden nicht ausgeglichen. Jetzt bleiben die anderen Browser live, wenn ein Gerät langsam ist, jede Position trägt einen Zeitstempel, damit verspätete Nachrichten die Anzeige nicht mehr verschieben, und kurze Lücken in der Position von den Playern lassen die Waveform nicht mehr springen. Noch nicht an echter Hardware getestet (Alpha)."),
   ("All of this is not tested on real hardware yet, nor a real update to 1.7.8 (alpha).",
    "Das alles ist noch nicht an echter Hardware getestet, auch kein echtes Update auf 1.7.8 (Alpha)."),
 ]),
 ("1.7.7", "9 November 2026", "9. November 2026", [
   ("Prepared: connection to VJ-Link (coming soon), our VJ software. LJ Link sends it tempo, a running beat, phrases (intro, build-up, chorus, breakdown, outro), hot cues, loops, title, cover and waveform, so visuals run in time and change scene on the drop. LJ Link announces itself in the network (Bonjour), and the header shows \"VJ-Link · Scene …\" while VJ-Link is connected. Runs in the demo; phrases from real players are not tested yet (alpha).",
    "Vorbereitet: Anbindung an VJ-Link (Coming soon), unsere VJ-Software. LJ Link schickt ihr Tempo, einen fortlaufenden Beat, Phrasen (Intro, Aufbau, Chorus, Breakdown, Outro), Hot Cues, Loops, Titel, Cover und Waveform, damit die Visuals im Takt laufen und beim Drop die Szene wechseln. LJ Link meldet sich dafür im Netzwerk (Bonjour), und die Kopfzeile zeigt \"VJ-Link · Szene …\", solange VJ-Link verbunden ist. Läuft in der Demo; Phrasen von echten Playern sind noch nicht getestet (Alpha)."),
   ("The demo now plays like a DJ set: tracks with intro, build-up, choruses, breakdown and outro, the next track mixed in on the beat with faders, EQ and crossfader moving, a new master about every two minutes, and now and then a 4-beat loop.",
    "Die Demo spielt jetzt wie ein DJ-Set: Tracks mit Intro, Aufbau, Chorus, Breakdown und Outro, der nächste Track wird im Takt eingemischt, Fader, EQ und Crossfader bewegen sich, etwa alle zwei Minuten wechselt der Master, und ab und zu läuft ein 4-Beat-Loop."),
   ("Below the setting ☰ → Screen → Keep screen awake – Alpha, a note now shows the reliable way on an iPad or iPhone opened over the network: Settings → Display & Brightness → Auto-Lock: Never, with the charger connected, and Low Power Mode off (on the iPhone it shortens Auto-Lock to 30 seconds, probably on the iPad too).",
    "Unter der Einstellung ☰ → Bildschirm → Bildschirm wach halten – Alpha steht jetzt der sichere Weg für ein iPad oder iPhone, das LJ Link über das Netzwerk öffnet: Einstellungen → Anzeige & Helligkeit → Automatische Sperre: Nie, am Netzteil, und den Stromsparmodus aus (auf dem iPhone verkürzt er die automatische Sperre auf 30 Sekunden, auf dem iPad vermutlich auch)."),
   ("Correction to 1.7.6: Keep screen awake works on a Mac. On the iPad it has not worked reliably so far and is not tested on a real iPad yet (alpha). Android tablets and Windows PCs are not tested yet either.",
    "Richtigstellung zu 1.7.6: Bildschirm wach halten läuft am Mac. Auf dem iPad läuft es bisher nicht zuverlässig und ist dort noch nicht am Gerät getestet (Alpha). Android-Tablets und Windows-PCs sind ebenfalls noch nicht getestet."),
 ], [
   ("On an iPad or tablet, LJ Link now tries more robustly to keep the screen on, and the status says honestly whether it works: after the tap first \"Starting the video …\", then \"Active (video) – the screen stays on\". If the video does not start or iOS stops it, the status shows \"Video stopped – please tap the page\" and the strip comes back: \"☼ The screen may turn off – tap here to restart keep-awake\". Not tested on a real iPad yet (alpha).",
    "Auf iPad und Tablet versucht LJ Link jetzt robuster, den Bildschirm an zu lassen, und der Status zeigt ehrlich, ob das klappt: nach dem Tipp erst \"Video startet …\", dann \"Aktiv (Video) – der Bildschirm bleibt an\". Läuft das Video nicht an oder hält iOS es an, zeigt der Status \"Video angehalten – bitte auf die Seite tippen\", und der Streifen erscheint wieder: \"☼ Der Bildschirm kann ausgehen – hier tippen, um den Wachmodus neu zu starten\". Noch nicht an einem echten iPad getestet (Alpha)."),
   ("On the Mac the demo and the display could run only in bursts while LJ Link sat in the menu bar (macOS App Nap slowed it down). LJ Link now switches App Nap off for itself; if it still stutters right after the update, quit LJ Link once and start it again.",
    "Am Mac liefen die Demo und die Anzeige teils nur ruckweise, solange LJ Link in der Menüleiste saß (macOS App Nap hat es ausgebremst). LJ Link schaltet App Nap jetzt für sich selbst ab; ruckelt es direkt nach dem Update noch, LJ Link einmal beenden und neu starten."),
 ]),
 ("1.7.6", "7 November 2026", "7. November 2026", [
   ("The screen stays on while LJ Link is open. On the Mac (LJ Link in a browser on the computer itself) this works at once, without a tap.",
    "Der Bildschirm bleibt an, solange LJ Link offen ist. Auf dem Mac (LJ Link im Browser am Computer selbst) geht das sofort, ohne Tippen."),
   ("On an iPad or tablet that opens LJ Link over the network, tap once: until then a strip right below the header shows \"☼ Tap here once to keep the screen on\". A tap on the strip or anywhere else on the page is enough.",
    "Auf einem iPad oder Tablet, das LJ Link über das Netzwerk öffnet, einmal tippen: Bis dahin zeigt ein Streifen direkt unter der Kopfzeile \"☼ Einmal hier tippen, damit der Bildschirm an bleibt\". Ein Tipp auf den Streifen oder irgendwo sonst auf der Seite reicht."),
   ("New setting under ☰ → Screen → Keep screen awake – Alpha, on by default and saved per browser. Below it a status shows whether it is active.",
    "Neue Einstellung unter ☰ → Bildschirm → Bildschirm wach halten – Alpha, standardmäßig an und pro Browser gespeichert. Darunter zeigt ein Status, ob sie aktiv ist."),
   ("On a Mac, LJ Link keeps the computer from falling asleep while a browser (also an iPad) is connected.",
    "Auf dem Mac verhindert LJ Link den Ruhezustand des Computers, solange ein Browser (auch ein iPad) verbunden ist."),
   ("The whole feature is alpha: not tested on real devices yet, neither on a Mac nor on an iPad, Android tablet or Windows PC.",
    "Die ganze Funktion ist Alpha: noch nicht an echten Geräten getestet, weder am Mac noch auf iPad, Android-Tablet oder Windows-PC."),
 ], []),
 ("1.7.5", "7 November 2026", "7. November 2026", [
   ("The BPM on each deck shows the real value of the player again, with two decimals (for example 137.95). The steadied tempo that goes to the light desks (rounded to 0.1) now appears as a tooltip on the BPM field: \"Sent to light desks: 138.0 BPM\". What is sent does not change. Not tested on real hardware yet.",
    "Die BPM-Anzeige im Deck zeigt wieder den echten Wert des Players, mit zwei Nachkommastellen (zum Beispiel 137,95). Das beruhigte Tempo, das an die Lichtpulte geht (auf 0,1 gerundet), steht jetzt als Tooltip am BPM-Feld: \"An Lichtpulte gesendet: 138,0 BPM\". Was gesendet wird, ändert sich nicht. Noch nicht an echter Hardware getestet."),
 ], []),
 ("1.7.4", "7 November 2026", "7. November 2026", [
   ("The diagnostic data now show the raw tempo data of every Pioneer player (track BPM, pitch, tempo from the status and the beat packets, sync and master state, the tempo LJ Link sends). If the tempo on a light desk still jumps, a report with these lines tells us why.",
    "Die Diagnosedaten zeigen jetzt die Tempo-Rohdaten jedes Pioneer-Players (Track-BPM, Pitch, Tempo aus den Status- und den Beat-Paketen, Sync- und Master-Zustand, das Tempo, das LJ Link sendet). Springt das Tempo an einem Lichtpult weiter, sagt uns ein Bericht mit diesen Zeilen, warum."),
   ("The BPM on each deck now shows the steadied tempo, the one LJ Link sends. It can differ from the player display by up to 0.15 BPM. Not tested on real hardware yet.",
    "Die BPM-Anzeige im Deck zeigt jetzt das beruhigte Tempo, also das, was LJ Link sendet. Sie kann bis zu 0,15 BPM vom Display des Players abweichen. Noch nicht an echter Hardware getestet."),
 ], [
   ("Tempo jumps at a rounding edge: when a player stands right between two tenths (for example 137.95 BPM), the tempo for light desks and Ableton Link kept flipping between 137.9 and 138.0, and the Wolfmix restarted its beat. LJ Link now steadies the raw tempo first and only sends a new value when it moves clearly away, so the tempo stays put. Changes of 0.25 BPM or more go through in under a second; smaller deviations of up to 0.15 BPM are deliberately not sent. The tempo of a new track goes out at once, and over OS2L a beat grid of the same track that arrives later no longer restarts the beat. A fixed value, the tempo of an Ableton Link session as source and Serato have no jitter and go out exactly, every tenth included. Not tested on real hardware yet.",
    "Tempo springt an einer Rundungsgrenze: Steht ein Player genau zwischen zwei Zehnteln (zum Beispiel 137,95 BPM), kippte das Tempo für Lichtpulte und Ableton Link immer wieder zwischen 137,9 und 138,0, und der Wolfmix startete seinen Beat neu. LJ Link beruhigt jetzt zuerst das Roh-Tempo und sendet einen neuen Wert erst, wenn er sich deutlich entfernt; das Tempo bleibt ruhig. Änderungen ab 0,25 BPM kommen in unter einer Sekunde durch; kleinere Abweichungen bis 0,15 BPM werden absichtlich nicht gesendet. Das Tempo eines neuen Tracks geht sofort raus, und über OS2L startet ein später nachgeladenes Beatgrid desselben Tracks den Beat nicht mehr neu. Ein fester Wert, das Tempo einer Ableton-Link-Session als Quelle und Serato zittern nicht und gehen genau raus, auch jedes Zehntel. Noch nicht an echter Hardware getestet."),
   ("OSC and grandMA: a change of exactly 0.1 BPM was sent late, only with the next 2-second refresh. It now goes out at once.",
    "OSC und grandMA: Eine Änderung um genau 0,1 BPM wurde verspätet gesendet, erst mit der nächsten Auffrischung nach 2 Sekunden. Jetzt geht sie sofort raus."),
 ]),
 ("1.7.3", "6 November 2026", "6. November 2026", [
   ("Wolfmix over Ableton Link, the recommended way after a test on a Wolfmix W1: choose Wolfmix (Ableton Link) under Light desk link, LJ Link switches Ableton Link on, and WTOOLS takes the tempo from Link. Wolfmix is now beta.",
    "Wolfmix über Ableton Link, nach einem Test an einem Wolfmix W1 der empfohlene Weg: Unter Lichtpult-Link Wolfmix (Ableton Link) wählen, LJ Link schaltet Ableton Link ein, und WTOOLS nimmt das Tempo von Link. Wolfmix ist jetzt Beta."),
 ], [
   ("Tempo jitter: players report their pitched tempo with a jitter of a tenth (137.9, 138.0, 137.9 …). The tempo for light desks and Ableton Link is now rounded to 0.1 BPM, and a change of only a tenth counts when it lasts 2 seconds. Real tempo changes go through at once.",
    "Tempo-Zittern: Player melden ihr gepitchtes Tempo mit einem Zittern von einem Zehntel (137,9, 138,0, 137,9 …). Das Tempo für Lichtpulte und Ableton Link wird jetzt auf 0,1 BPM gerundet, eine Änderung um nur ein Zehntel zählt erst, wenn sie 2 Sekunden anhält. Echte Tempoänderungen gehen sofort durch."),
   ("Double flashes and gaps over OS2L and the OSC taps: with Pioneer players the beats now come straight from the players' beat packets instead of an estimated position, so no beat is sent twice or dropped, even with older players or tracks without rekordbox data.",
    "Doppelblitze und Aussetzer über OS2L und die OSC-Taps: Bei Pioneer-Playern kommen die Beats jetzt direkt aus den Beat-Paketen der Player statt aus einer geschätzten Position, kein Beat wird mehr doppelt gesendet oder verschluckt, auch nicht bei älteren Playern oder Tracks ohne rekordbox-Daten."),
 ]),
 ("1.7.2", "6 November 2026", "6. November 2026", [
   ("Daslight over Ableton Link, the recommended way: choose Daslight (Ableton Link) under Light desk link, and LJ Link switches Ableton Link on and leads the session with the tempo, beats and bars of the SEND BPM deck. In Daslight set the BPM source to Ableton Link Sync.",
    "Daslight über Ableton Link, der empfohlene Weg: Unter Lichtpult-Link Daslight (Ableton Link) wählen, LJ Link schaltet Ableton Link ein und führt die Session mit Tempo, Beats und Takten des SEND-BPM-Decks. In Daslight die BPM Source auf Ableton Link Sync stellen."),
   ("Fixed value under SEND BPM: a deck of its own that sends the tempo you type in (slider 60 to 180 BPM, field 30 to 300), to the light desk and over Ableton Link. Handy for tests or without DJ gear.",
    "Festwert unter SEND BPM: ein eigenes Deck, das das eingetippte Tempo sendet (Regler 60 bis 180 BPM, Feld 30 bis 300), ans Lichtpult und über Ableton Link. Praktisch für Tests oder ohne DJ-Technik."),
   ("Send demo data: while the demo runs, a tick in the light desk settings lets the demo tempos go to the desk and to Ableton Link. Off at every start.",
    "Demo-Daten senden: Während die Demo läuft, lässt ein Haken in den Lichtpult-Einstellungen die Demo-Tempos ans Pult und an Ableton Link gehen. Bei jedem Start aus."),
   ("Works with Daslight: Ableton Link out and Daslight (over Ableton Link and over the OSC tap) are no longer alpha. The Daslight wiki page now has screenshots of the setup.",
    "Läuft mit Daslight: Ableton Link senden und Daslight (über Ableton Link und über den OSC-Tap) sind kein Alpha mehr. Die Daslight-Wiki-Seite hat jetzt Screenshots der Einrichtung."),
 ], [
   ("Daslight OSC tap: Daslight ignores OSC messages without a value, so the tap is now the value 255 followed by 0. Default port 7000 like Daslight.",
    "Daslight-OSC-Tap: Daslight ignoriert OSC-Nachrichten ohne Wert, der Tap ist jetzt deshalb der Wert 255, gefolgt von 0. Vorgabe-Port 7000 wie bei Daslight."),
 ]),
 ("1.7.1", "6 November 2026", "6. November 2026", [
   ("Daslight (alpha, not tested on a real Daslight yet): Daslight cannot take the BPM as an OSC number, so LJ Link now sends an OSC tap on every beat of the SEND BPM deck, which you map to the BPM TAP button in Daslight's OSC mapping.",
    "Daslight (Alpha, noch nicht an einem echten Daslight getestet): Daslight kann das BPM nicht als OSC-Zahl annehmen, deshalb sendet LJ Link jetzt auf jedem Beat des SEND-BPM-Decks einen OSC-Tap, den du im OSC-Mapping von Daslight auf die Taste BPM TAP legst."),
   ("Rewritten wiki page for Daslight with the setup, step by step.",
    "Wiki-Seite zu Daslight neu geschrieben, mit der Einrichtung Schritt für Schritt."),
 ], [
   ("The old Daslight output (the tempo as a plain OSC number) never worked with Daslight's BPM and was replaced by the taps. The generic OSC target still sends the number for other programs.",
    "Der alte Daslight-Ausgang (das Tempo als einfache OSC-Zahl) funktionierte beim BPM von Daslight nie und wurde durch die Taps ersetzt. Das allgemeine OSC-Ziel sendet die Zahl weiterhin für andere Programme."),
 ]),
 ("1.7.0", "6 November 2026", "6. November 2026", [
   ("ChamSys MagicQ (alpha, not tested on a real desk yet): LJ Link sends an OSC tap on every beat of the SEND BPM deck, which MagicQ maps to Tap to Time. One or several addresses can be set.",
    "ChamSys MagicQ (Alpha, noch nicht an einem echten Pult getestet): LJ Link sendet auf jedem Beat des SEND-BPM-Decks einen OSC-Tap, den MagicQ auf Tap to Time legt. Eine oder mehrere Adressen sind möglich."),
   ("New wiki page ChamSys MagicQ with the setup on the desk, step by step.",
    "Neue Wiki-Seite ChamSys MagicQ mit der Einrichtung am Pult, Schritt für Schritt."),
   ("Idea and first draft: soiza on GitHub. Thank you!",
    "Idee und erster Entwurf: soiza auf GitHub. Danke!"),
 ], []),
 ("1.6.1", "5 November 2026", "5. November 2026", [
   ("Everything from 1.6.0, now as a download: Ableton Link out (alpha) with tempo, beats and bars.",
    "Alles aus 1.6.0, jetzt als Download: Ableton Link senden (Alpha) mit Tempo, Beats und Takten."),
 ], [
   ("1.6.0 never appeared as a download because the build for Macs with Apple Silicon failed. 1.6.1 is the same version with a working build.",
    "1.6.0 ist nie als Download erschienen, weil der Build für Macs mit Apple Silicon scheiterte. 1.6.1 ist dieselbe Version mit funktionierendem Build."),
 ]),
 ("1.6.0", "5 November 2026", "5. November 2026", [
   ("Ableton Link out (alpha, not tested with real gear yet): LJ Link can lead the Link session with the tempo of the SEND BPM deck, so Ableton Live, Resolume, Traktor and Link apps run at the DJ's tempo.",
    "Ableton Link senden (Alpha, noch nicht mit echten Geräten getestet): LJ Link kann die Link-Session mit dem Tempo des SEND-BPM-Decks führen, damit laufen Ableton Live, Resolume, Traktor und Link-Apps im Tempo des DJs."),
   ("Optionally also beats and bars: with Pioneer and Denon decks the beat and the start of the bar follow the beat grid of the deck.",
    "Auf Wunsch auch Beats und Takte: Bei Pioneer- und Denon-Decks folgen Beat und Taktanfang dem Beatgrid des Decks."),
   ("New section Ableton Link in the settings, with the session tempo and how many programs follow.",
    "Neuer Abschnitt Ableton Link in den Einstellungen, mit dem Tempo der Session und wie viele Programme folgen."),
   ("The deck that leads Link wears a white LINK mark; the diagnostic data show the Link state.",
    "Das Deck, das Link führt, trägt ein weißes LINK; die Diagnosedaten zeigen den Link-Zustand."),
 ], [
   ("The Ableton Link deck no longer counts LJ Link itself as one of the programs.",
    "Das Ableton-Link-Deck zählt LJ Link nicht mehr selbst als eines der Programme."),
 ]),
 ("1.5.1", "5 November 2026", "5. November 2026", [
   ("If another program takes over the Pioneer connection, LJ Link reconnects by itself and names it when it runs on the same computer (a second LJ Link, Beat Link Trigger, rekordbox).",
    "Übernimmt ein anderes Programm die Pioneer-Verbindung, verbindet sich LJ Link von selbst neu und nennt es, wenn es auf demselben Computer läuft (ein zweites LJ Link, Beat Link Trigger, rekordbox)."),
   ("The diagnostic data count how often the Pioneer connection was shut down and why.",
    "Die Diagnosedaten zählen, wie oft die Pioneer-Verbindung abgeschaltet wurde und warum."),
 ], [
   ("Windows: the Pioneer connection kept shutting itself down on some PCs. Waveforms loaded only sometimes, BPM and play state froze. Details on the known issues page.",
    "Windows: Die Pioneer-Verbindung schaltete sich auf manchen PCs immer wieder selbst ab. Waveforms luden nur manchmal, BPM und Play-Zustand blieben stehen. Details auf der Seite Bekannte Fehler."),
   ("Players are shown with their real address instead of 0.0.0.0.",
    "Player werden mit ihrer echten Adresse angezeigt statt mit 0.0.0.0."),
 ]),
 ("1.5.0", "4 November 2026", "4. November 2026", [
   ("Wolfmix via OS2L (alpha, untested): every beat of the leading deck goes to WTOOLS, with bar-aligned beat numbers and the tempo.",
    "Wolfmix über OS2L (Alpha, ungetestet): Jeder Beat des führenden Decks geht an WTOOLS, mit taktgenauer Beat-Nummer und dem Tempo."),
   ("OS2L also works with other OS2L receivers such as QLC+.",
    "OS2L funktioniert auch mit anderen OS2L-Empfängern wie QLC+."),
 ], []),
 ("1.4.4", "3 November 2026", "3. November 2026", [], [
   ("1.4.3 was built from an incomplete state. 1.4.4 contains all changes of 1.4.1 and 1.4.2.",
    "1.4.3 wurde aus einem unvollständigen Stand gebaut. 1.4.4 enthält alle Änderungen von 1.4.1 und 1.4.2."),
 ]),
 ("1.4.2", "3 November 2026", "3. November 2026", [
   ("New name: LJ Link. Links, the website address and updates keep working.",
    "Neuer Name: LJ Link. Links, Website-Adresse und Updates funktionieren weiter."),
   ("Open source under the Eclipse Public License 2.0; Settings → About LJ Link → Licenses shows the licenses of all libraries.",
    "Open Source unter der Eclipse Public License 2.0; Einstellungen → Über LJ Link → Lizenzen zeigt die Lizenzen aller Bibliotheken."),
 ], [
   ("The Windows installer replaces installations under the old name.",
    "Der Windows-Installer ersetzt Installationen unter dem alten Namen."),
 ]),
 ("1.4.1", "3 November 2026", "3. November 2026", [
   ("Report a problem: describe what happens and send it with diagnostic data (network, devices, decks, log) by email or on GitHub.",
    "Problem melden: beschreiben, was passiert, und mit Diagnosedaten (Netzwerk, Geräte, Decks, Log) per E-Mail oder auf GitHub senden."),
   ("The search starts with Pioneer; a failed Pioneer search is shown with a hint when another program holds Pro DJ Link.",
    "Die Suche beginnt mit Pioneer; eine gescheiterte Pioneer-Suche wird angezeigt, mit Hinweis, wenn ein anderes Programm Pro DJ Link belegt."),
 ], [
   ("Tracks without rekordbox analysis (CDs, streaming, unanalysed files) are shown as loaded instead of “No track loaded”.",
    "Tracks ohne rekordbox-Analyse (CDs, Streaming, nicht analysierte Dateien) werden als geladen angezeigt statt „No track loaded“."),
 ]),
 ("1.4.0", "3 November 2026", "3. November 2026", [
   ("Serato DJ Pro on the same computer: title, BPM, key, waveform overview, beat grid and cues of the loaded tracks.",
    "Serato DJ Pro auf demselben Computer: Titel, BPM, Tonart, Waveform-Übersicht, Beatgrid und Cues der geladenen Tracks."),
   ("Ableton Link: the tempo of a Link session (Serato, Traktor, Live) shows up as a deck and can go to the light desk.",
    "Ableton Link: Das Tempo einer Link-Session (Serato, Traktor, Live) erscheint als Deck und kann ans Lichtpult gehen."),
 ], []),
 ("1.3.1", "2 November 2026", "2. November 2026", [], [
   ("Denon: after a lost connection LJ Link pauses before it reconnects instead of reconnecting in a loop.",
    "Denon: Nach einem Verbindungsabbruch wartet LJ Link kurz, statt sich in einer Schleife neu zu verbinden."),
   ("When the light desk refuses a command (for example missing user rights), the reason is shown.",
    "Lehnt das Lichtpult einen Befehl ab (zum Beispiel fehlende Benutzerrechte), wird der Grund angezeigt."),
 ]),
 ("1.3.0", "2 November 2026", "2. November 2026", [
   ("grandMA2 over Telnet with login; every show has the user administrator with the password admin, which is filled in.",
    "grandMA2 über Telnet mit Login; jede Show hat den Benutzer administrator mit dem Passwort admin, das wird vorausgefüllt."),
   ("Choose the speed master on the grandMA (Speed 1–15, or Speed 16 = BPM).",
    "Speedmaster am grandMA wählen (Speed 1–15, oder Speed 16 = BPM)."),
   ("grandMA3 command line from the manual.", "grandMA3-Befehl nach Handbuch."),
 ], [
   ("The update check works in the installed app.", "Die Update-Prüfung funktioniert in der installierten App."),
   ("grandMA2 gets whole BPM, its command line takes no fractions.", "Der grandMA2 bekommt ganze BPM, seine Kommandozeile nimmt keine Kommazahlen."),
   ("Switched-off Telnet and a failed login on the grandMA2 are reported.", "Ausgeschaltetes Telnet und ein fehlgeschlagener Login am grandMA2 werden gemeldet."),
   ("Devices that are not DJ players no longer show up in the Denon search.", "Geräte, die keine DJ-Player sind, erscheinen nicht mehr in der Denon-Suche."),
 ]),
 ("1.2.1", "2 November 2026", "2. November 2026", [
   ("Clearer start page.", "Übersichtlichere Startseite."),
 ], [
   ("The demo never sends a tempo to the light desk.", "Die Demo sendet nie ein Tempo ans Lichtpult."),
   ("Pioneer keeps working when another program uses the beat port 50001; positions are then estimated and a note is shown.",
    "Pioneer läuft weiter, wenn ein anderes Programm den Beat-Port 50001 belegt; die Positionen werden dann geschätzt und ein Hinweis erscheint."),
   ("The settings close when the connection is ended.", "Die Einstellungen schließen sich, wenn die Verbindung getrennt wird."),
 ]),
 ("1.2.0", "2 November 2026", "2. November 2026", [
   ("SEND BPM: choose which deck gives the tempo to the light desk, Auto or one deck.",
    "SEND BPM: wählen, welches Deck dem Lichtpult das Tempo gibt, Auto oder ein festes Deck."),
   ("Update check: LJ Link tells you about a new version and installs it from the program icon.",
    "Update-Prüfung: LJ Link meldet eine neue Version und installiert sie über das Programm-Symbol."),
 ], [
   ("Smoother waveform on the CDJ-2000NXS2 and older players.", "Ruhigere Waveform beim CDJ-2000NXS2 und älteren Playern."),
   ("Player model names are shown correctly.", "Die Modellnamen der Player werden richtig angezeigt."),
   ("Denon players reconnect after a short interruption.", "Denon-Player verbinden sich nach einer kurzen Unterbrechung wieder."),
 ]),
]

def li(en, de): return f'<li data-de="{E(de)}">{E(en)}</li>'
def block(cls, en, de, items):
    if not items:
        body = f'<p class="none" data-de="Keine in dieser Version.">None in this version.</p>'
    else:
        body = "<ul>" + "".join(li(*i) for i in items) + "</ul>"
    return f'<div class="rel-part {cls}"><h3 data-de="{de}">{en}</h3>{body}</div>'

latest = R[0][0]
out = []
toc = []
for i, (v, den, dde, new, fix) in enumerate(R):
    vid = "v" + v.replace(".", "-")
    toc.append(f'<a href="#{vid}">{v}</a>')
    badge = '<span class="badge stable" data-de="Aktuell">Latest</span>' if i == 0 else ""
    out.append(f'''    <section class="release" id="{vid}">
      <div class="rel-head"><h2>LJ Link {v}</h2>{badge}<span class="rel-date" data-de="{dde}">{den}</span></div>
      {block("new", "What's new", "Neu", new)}
      {block("fix", "Fixes", "Behoben", fix)}
    </section>''')

guide = (site / "pioneer-bpm-grandma.html").read_text(encoding="utf-8")
head = guide[:guide.index("<main")]
footer = guide[guide.index("<footer>"):]
rep = [
 (r'<title[^>]*>.*?</title>', '<title data-de="Neuigkeiten und Änderungen – alle Versionen | LJ Link">What\'s new – patch notes of every version | LJ Link</title>'),
 (r'<meta name="description"[^>]*>', '<meta name="description" content="What is new and what was fixed in every LJ Link version, newest first: Ableton Link out, Wolfmix, Serato, grandMA2 and grandMA3, Pioneer and Denon." data-de-content="Was in jeder LJ-Link-Version neu ist und was behoben wurde, die neueste zuerst: Ableton Link senden, Wolfmix, Serato, grandMA2 und grandMA3, Pioneer und Denon.">'),
 (r'<meta property="og:title"[^>]*>', '<meta property="og:title" content="LJ Link – what\'s new" data-de-content="LJ Link – was ist neu">'),
 (r'<meta property="og:description"[^>]*>', '<meta property="og:description" content="Patch notes of every LJ Link version, newest first." data-de-content="Die Änderungen jeder LJ-Link-Version, die neueste zuerst.">'),
]
for pat, new in rep:
    head, n = re.subn(pat, lambda m: new, head, count=1, flags=re.S)
    assert n == 1, pat
head = head.replace("pioneer-bpm-grandma.html", "changelog.html")
head = head.replace('<meta property="og:type" content="article">', '<meta property="og:type" content="website">')
print([l[:160] for l in head.splitlines() if "ioneer" in l])

main = f'''<main class="wrap article changelog">
  <nav class="crumbs" aria-label="Breadcrumb"><a href="./">LJ Link</a> › <span data-de="Neuigkeiten">What's new</span></nav>
  <div class="hero">
    <h1><span class="kicker" data-de="Änderungen · alle Versionen">Patch notes · every version</span><span data-de="Was ist neu in LJ Link">What's new in LJ Link</span></h1>
    <p class="tag" data-de="Jede Version mit ihren Neuerungen und Fehlerbehebungen, die neueste oben. Aktuell ist LJ Link {latest}.">Every version with what is new and what was fixed, newest at the top. The latest is LJ Link {latest}.</p>
    <div class="cta" style="justify-content:flex-start">
      <a class="btn primary" href="https://github.com/skyplayser/LJ-Link/releases/latest" data-de="LJ Link {latest} herunterladen">Download LJ Link {latest}</a>
      <a class="btn" href="wiki-known-issues.html" data-de="Bekannte Fehler">Known issues</a>
    </div>
    <p class="rel-toc"><span data-de="Zu Version:">Jump to:</span> {" ".join(toc)}</p>
  </div>
{chr(10).join(out)}
    <p class="rel-more" data-de="Ältere Testversionen vor 1.2.0 sind nicht aufgeführt. Jede Version mit Download findest du auf GitHub unter Releases.">Test builds before 1.2.0 are not listed. Every version with its download is on GitHub under Releases.</p>
</main>

'''
(site / "changelog.html").write_text(head + main + footer, encoding="utf-8")
print("ok", len(R))
