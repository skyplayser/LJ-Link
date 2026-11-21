# Decisions

Short ADRs. Newest last.

## ADR-001: beat-link 8.0.0, Java 21, Gradle Kotlin DSL
beat-link 8.0.0 (Maven Central, EPL-2.0, released 2025-07) is the latest release. It targets Java 11
and works on 21. Gradle wrapper 9.8.2 is checked in. API names were verified against the
`beat-link-8.0.0-sources.jar` (not guessed).

## ADR-002: Virtual CDJ number defaults to auto-assign outside 1-4
`VirtualCdj.setUseStandardPlayerNumber(false)` is the beat-link default and matches the brief
(do not disturb real players). If metadata requests via dbserver fail on some players, the spike offers
`--standard-number`. To be confirmed with hardware in Phase 0.

## ADR-003: Recording format is JSON Lines
One object per line with `t` (ms since start) and `type`. Easy to replay, diff and trim. Status events are
throttled to 10 Hz per device, precise-position events to every 3rd packet. Waveform bytes are not yet
recorded in Phase 0 (only availability and size); full data is added in Phase 1 with the normalized model.

## ADR-004: Opus Quad compatibility mode
beat-link 8.0 contains `VirtualRekordbox` (EXPERIMENTAL), used when an Opus Quad-style device is found.
The spike logs `proxyingRekordbox` so Phase 0 shows which mode is active.

## ADR-005: Phrases via AnalysisTagFinder (".EXT", "PSSI")
Song structure is read from the `PSSI` tag of the `.EXT` analysis file, as in beat-link's own
`WaveformDetailComponent`. The 3-band waveform is requested via `WaveformFinder.setPreferredStyle(THREE_BAND)`.

## ADR-006: Browser UI served by the Java app (changed from the spike-only start)
The app starts a local web server (Javalin, port 8080, next free port if busy), opens the default browser and shows a
setup assistant: automatic search, manual setup, demo. The same page works on other devices in the LAN (e.g. an Android
tablet) as a read-only viewer. Control endpoints (start/stop/check) accept loopback clients only. The UI is plain
HTML/CSS/JS in `src/main/resources/web` for now (no build step); TypeScript + Vite may follow when the UI grows.

## ADR-007: "Manual IP" is a network check, not a direct connection
beat-link discovers devices only through UDP broadcast announcements and picks the network interface by matching the
announcing device's subnet. It cannot connect to a device by IP. The manual setup therefore checks subnet match,
reachability and the dbserver port, and explains in plain German what to change. Verify with hardware whether this is enough.

## ADR-008: Search starts with DeviceFinder only
`VirtualCdj.start()` blocks while no device is on the network. The source first listens for announcements
(non-blocking, cancellable) and joins as a virtual CDJ only after a device was seen plus 2.5 s grace time.

## ADR-009: Native installers via jpackage, tray icon via AWT
`./gradlew packageApp` runs the JDK's `jpackage` with a bundled runtime (no Java install needed): `.dmg` on macOS,
`.exe` on Windows (WiX 3). jpackage cannot cross-build, so GitHub Actions builds macOS arm64, macOS Intel and Windows.
The menu bar / system tray icon uses `java.awt.SystemTray` with an icon drawn in code (no third-party assets); menu:
open interface, quit. On macOS `apple.awt.UIElement=true` hides the Dock icon. A second start opens the running instance
(`/api/ping`). Not yet signed or notarized: macOS needs right-click > Open, Windows shows SmartScreen. App icon files
(.icns/.ico) are still the Java default. Not yet verified on Windows.

## ADR-010: Stacked live waveforms, settings, light desk link
Each deck is a row: player data left, scrolling detail waveform (150 frames/s, playhead centered) and the whole-track
overview right; the on-air deck gets a red "LIVE" frame. Settings (zoom, RGB/blue, day/night) live in the browser's
localStorage. The light desk link sends the tempo of the leading deck (master, else on-air playing, else playing) as OSC
over UDP: GrandMA2/3/onPC as command line via `/cmd` (default `SpecialMaster 3.1 At {bpm}`), Daslight and custom targets as a
plain float at a configurable address. Settings are stored in `~/.ljlinkpro/link.json`. The default MA command, ports and
Daslight mapping are NOT verified against real consoles and must be checked on site. Beat/phrase/deck-state output is not built yet.

## ADR-011: Name and logo
*The logo and icon path part is superseded by ADR-036 (new logo "Fader"); the name part still applies.*
The product is called LJ Link (not BoothView, to avoid a conflict with an existing name). Java package `dev.ljlinkpro`, config in
`~/.ljlinkpro/`. The logo (waveform bars shooting light beams) is drawn in code in `Logo.java` and mirrored in
`web/logo.svg`; `packaging/LJ-Link.icns|ico|png` (until 1.7.7 `LJ-Link-PRO.*`) are generated from it (`java dev.ljlinkpro.Logo <dir>`, then `iconutil`)
and passed to jpackage as the installer and app icon.

## ADR-012: Player look of the waveforms
Three-band waveforms are sent as layers and painted like the players: detail = independent bands low (blue), mid (amber),
high (white); overview = cumulative layers painted outermost first. RGB/blue tracks are sent as height plus color. Loops are
orange (saved loops dimmer, the active loop brighter), memory cues red, hot cues in the colors stored in the DJ's library, with letters.
Paused players report no moving position, so it is derived from the last beat number and the beat grid.

## ADR-013: Live position channel and RGB default
Positions travel on their own compact `pos` WebSocket message (`{"type":"pos","deck":n,"p":ms,"v":speed}`, extended in ADR-024), sampled every 20 ms
on the server (about 45-50 messages/s per deck, measured). The speed `v` is measured from consecutive samples, so it follows
scratching; changes faster than about 6x speed count as jumps (cue, loop) and keep the old speed. The browser extrapolates
with `v` and pulls the shown position towards the newest sample (snaps at jumps over 800 ms). The CDJ-3000 reports exact
positions about 30 times per second; NXS/NXS2 only via beats and status, so their scratching can only be followed roughly.
The server now asks beat-link for RGB waveforms (as shown on the players); the palette is an approximation of the look on the players
(detail: beat-link's green/blue swap undone; overview: red and blue swapped; colors brightened) and still differs in hue.
Loops: only the CDJ-3000 reports the active loop; other players show saved loops from the cue list.

## ADR-014: App window, player layout, docs and website
The interface opens in a Chromium app window (`--app=URL`, own profile in `~/.ljlinkpro/window-profile`) when Chrome, Edge
or Brave is installed, so it feels like a normal program; otherwise it falls back to the default browser (`--no-app-window`
forces that). The deck panel follows the layout DJs know from current players (PLAYER/TRACK boxes, time with milliseconds, tempo, BPM box, MT,
SYNC, KEY). Screenshots for the README, guide and website are taken from the demo mode with headless Chrome
(`/#shot,manual`, `/#shot,info-1`, `/#shot,settings`, `/#shot,day`); `shot` hides network addresses. The website is the
folder `site/`, published by `.github/workflows/pages.yml` once GitHub Pages is enabled (source: GitHub Actions).

## ADR-015: Mixer tab, channel bar and what Pro DJ Link can deliver
beat-link 8.0 exposes from a DJM only: ON AIR per channel (derived from the mixer's flags, so it already includes channel fader
and crossfader), fader start commands, master tempo and beat. It has no fader position and no EQ. The deck rows get a blue channel
bar and HI/MID/LOW knobs, and a Mixer tab shows the channel strips; both read optional `fader` and `eq` values per deck. Pro DJ Link
fills only on air (the bar shows full/empty, the knobs stay hidden); the demo simulates values and labels them as simulated.
Possible real sources, not built: USB-MIDI of the mixer (needs a mapping per model and the cable to the computer) and Denon
StageLinQ (the Prime/SC6000 `StateMap` is reported by community libraries to include fader positions; EQ unverified).
Screenshots: the internal browser delivers at most 800 px wide images, so documentation screenshots are taken with headless
Chrome at 2x (`--force-device-scale-factor=2`) from the same URLs.

## ADR-016: Privacy mode is enforced on the server and switched only from the program icon
`Privacy` holds the flag (saved in `~/.ljlinkpro/settings.json`, `--privacy` for one run). It is changed only by the tray menu
check box; no HTTP or WebSocket endpoint can change it (verified: `/api/privacy` answers 404 for every method). With the mode
on, the server removes title, artist, album, genre, label, remixer, comment and cover from deck messages, the mixer channel
names, the cover endpoint (404) and the player detail view (cue comments too); the browser only displays the state. Unit tests:
`PrivacyTest`.

## ADR-017: Languages
English is the source language and the default; German is a translation (`i18n.js`: `DE` maps English text to German,
`t()` for generated text, `translateStatic()` for the page). Everything the server sends is English (status messages, network
check texts as templates with arguments, detail view labels and values) and is translated in the browser, so each browser can
choose its own language. The language of the tray menu is stored server-side and follows the interface language chosen on the
computer itself (`POST /api/language`, local only). Documentation screenshots exist in both languages (`docs/images`, `docs/images/de`).

## ADR-018: Denon StageLinQ source
`StageLinqSource` (new, `StageLinqProtocol` for the wire format) joins Denon devices passively: it announces itself on UDP 51337
like other StageLinQ clients, connects to the device's directory service, requests the services and then uses StateMap
(values as JSON: title, artist, tempo, play, master, track length, channel faders, loop, cue) and BeatInfo (beat, BPM and the sample
position per deck). The protocol was read from two MIT licensed open source libraries (go-stagelinq by icedream, StageLinq by
chrisle); no code was copied. Verified against a real Prime 4+ (firmware 5.0.4): `TrackLength` is a number of samples (not seconds),
BeatInfo `samples` run in real time at the track's sample rate, `CurrentBPM` equals the track BPM (pitch is not derivable yet), and the
device also announces internal helper services named `OfflineAnalyzer` that report decks that do not exist; they are ignored.
Loop and cue positions are assumed to be milliseconds and a loop is only shown when that is plausible. Waveform, beat grid and cues come
from the Engine library (`m.db`, qCompress blobs) behind the FileTransfer service, see ADR-019. Key, pitch and EQ are not transmitted. Set
`LJLINKPRO_DEBUG=1` to log the raw values.
A device may need a few seconds before it accepts a new session after a client was killed.

## ADR-019: Several devices at once (scopes)
Every source now works in a `Hub.Scope`: its own search state, mixer and decks. Decks get a global number (used by the browser and
the API) and keep the device's own number as `label`, so player 1 and Denon deck 1 can coexist. Pro DJ Link is one scope (each
player is its own device group); every Denon controller found by `StageLinqDiscovery` gets its own scope and `StageLinqSource`, which
is closed when the device says goodbye, the connection ends or it stays silent for 20 s, so its decks disappear and it can return.
The overall phase is monitoring as soon as any scope is, otherwise searching. "Search automatically" starts all kinds at once.
The browser hides decks of unticked device groups (kept in localStorage, so a read-only tablet chooses for itself); new groups are
shown by default. The Engine library gives only a 1024 value overview per track; the detail view is interpolated from it (honest
limit, not a fake of the Pioneer 150 fps detail).

## Known bug: direct cable plus Wi-Fi
With players connected by a direct cable (link-local 169.254.x.x, no router) and Wi-Fi on, players sporadically show up twice
or are not found; a restart of the program helps. Suspected cause: beat-link receives on all adapters but sends on one, and
with a link-local network next to Wi-Fi macOS may route or deliver packets on the wrong or on two adapters. Needs a
diagnostics report taken while the problem is visible before changing the interface choice.

## ADR-020: Wolfmix through OS2L, beat by beat
The Wolfmix has no network, only USB; the tempo reaches it through the computer app WTOOLS, which accepts OS2L (the protocol of
VirtualDJ, spec at os2l.org: TCP, one JSON object per message, `beat` with `change`, `pos`, `bpm`). LJ Link is the audio side:
it connects to WTOOLS (127.0.0.1:5000 by default) and sends `beat` on every beat. `Os2lBeats` takes the beat from the beat grid
and the position predicted from the latest sample (`Deck.predictedPositionMs`), checked every 4 ms in its own thread, so a
beat is at most a few ms late (measured mean 1.5 ms, max 7 ms against a local receiver). `pos` is the grid index shifted so that
bar starts are multiples of 4. `change` is set after a start, a jump of more than 400 ms, a track or deck change and a tempo
step of 0.5 BPM. A first beat that began more than 60 ms ago is skipped rather than sent late. Decks without a position
(Serato, Ableton Link) run freely at their tempo. The spec names no message delimiter; every object ends with a line feed.
Not yet checked against a real WTOOLS: whether it accepts a connection from another computer, how it takes the line feed, and
the USB latency (an offset setting is planned).

## ADR-021: Steadied output tempo: dead band on the raw tempo
Players compute their tempo from track BPM and pitch (`CdjStatus.getEffectiveTempo()`); in a real test with two XDJ-1000MK2
and a Wolfmix the readings most likely swung by a few hundredths around about 137.95, the rounding edge of 0.1. The 1.7.3 hold time on the
rounded number (0.1 change after 2 s) still let it flip every few seconds, and every flip restarted the Wolfmix beat.
`Deck.stableBpm()` (used by the OSC/MA2 tick in `LinkService`, `Os2lBeats` and `AbletonLinkPeer`)
now works on the raw tempo:
- Dead band 0.15 BPM: the sent tempo stays while the raw tempo, smoothed with a time constant of 0.5 s (time based, so the
  calling rate does not matter), is closer than that. 0.15 is three times the assumed jitter (+-0.05) and still only 0.11 %
  at 138 BPM (at most 65 ms of drift per minute for Link without phase, typically under 25 ms, since the sent value is within
  0.05 of the smoothed one after a change); finer differences than the 0.02 % fader steps of an XDJ-1000MK2
  (0.03 BPM) matter only for long free running, which phase correction and beat packets cover.
- A slow drift beyond the band is taken as the rounded smoothed value, so the sent tempo ends within 0.05 of the true one.
- A jump of 0.25 BPM or more (pitch fader, new track) is taken once the raw tempo stays within 0.1 for 0.25 s, at the latest
  after 0.75 s while the fader keeps moving: +1 BPM arrives in about 0.25-0.45 s in one step. The smoothing pauses during a jump,
  so a nudge shorter than 0.25 s changes nothing.
- The sent value is still rounded to 0.1 (the Wolfmix and the OSC text show one decimal).
- Tempos without measuring noise bypass the dead band (`Deck.exactTempo`), every step goes out at once: the fixed value,
  an Ableton Link session as a source (`LinkTempoSource`) and Serato (BPM from the file tag, no pitch).
- Display (changed in 1.7.5): the deck BPM shows the real player value with two decimals (137.95); the steadied, sent tempo is the
  tooltip on the BPM field. Output unchanged; display change not tested on real hardware yet.
- No tempo (0) resets the sent tempo and the first tempo after it goes out at once; the deck display tick asks every
  100 ms also without a tempo, so this really happens. After another track (`Deck.trackVersion`, raised by the sources;
  unlike `beatVersion` not by a late beat grid) a tempo outside the band goes out at once for 1 s, and so does a jump when
  nobody asked for the tempo for more than 1 s. OS2L flags `change=true` once per track (a late beat grid of the same track
  is no new start while beat packets give the beats).
- `System.nanoTime()` may be negative: no time serves as a "not set" marker, flags do.
Assumptions (not verified on hardware): the jitter is at most a few hundredths; a real change is at least 0.25 BPM or
creeps slowly. The OSC tick now counts a change of one tenth as a change (`>= 0.05`; `138.0 - 137.9` is `0.0999…` in floating point,
so tenths waited for the 2 s keep-alive). OS2L keeps `change=true` for start, jump, track change and steps of 0.5 BPM; the `bpm` of
each beat is the steadied value, so it only changes on a real change. Tests: `StableBeatsTest`. The diagnostics now list per player
track BPM, the four pitch copies, status and beat packet tempo (now and the range of the last 10 s), sync/master and the sent tempo,
plus whether CrateDigger runs and whether standard player numbers are used; they only look up existing decks (never create
one) and forget a player when it leaves the network. `LJLINKPRO_DEBUG=1` records the tempo lines every second in a buffer of
their own (300 lines, shown in the diagnostics), so they do not push start and error messages out of the log. Every change of
the sent tempo of the leading deck is logged, the fixed value included ("Sent tempo of deck Fixed value: …").

## ADR-022: Keep the screen awake (wake lock, video fallback, caffeinate on macOS)
In a booth the view stays open for hours and nobody touches the device, so displays dimmed and locked. Setting "Keep
screen awake" (per browser in `ljlinkpro.settings`, key `keepAwake`, default on):
- Screen Wake Lock API (`navigator.wakeLock.request("screen")`) where it exists. The spec marks `Navigator.wakeLock` and
  `WakeLock` as `[SecureContext]` (w3c/screen-wake-lock, MDN: "available only in secure contexts"), so it works on the Mac
  itself (`http://localhost` counts as secure) but is missing on an iPad that opens `http://<ip>:8080`. The browser releases
  the lock when the page is hidden; it is requested again on `visibilitychange` / `fullscreenchange`. A refusal
  (`NotAllowedError`, e.g. power saving) is retried on the next tap, then the video fallback is used.
- Fallback: a silent 10 s video (64x64 black, H.264 baseline plus a silent AAC track, about 4 KB) embedded as a data URI in
  `web/keep-awake-video.js` (made with ffmpeg, command in the file; nothing taken from NoSleep.js, only the idea). A data URI
  avoids HTTP range requests, which Safari needs for media files. Started on the first tap/click/key (browsers start unmuted
  media only after a gesture); until then a strip below the header "☼ Tap here once to keep the screen on" is shown (its own row, so it
  never widens the header or pushes the menu out; a tap on it starts it too). The setting is labelled "– Alpha" until tested
  on real devices.
  Checked in the WebKit source (`HTMLMediaElement::shouldDisableSleep`, `mediaType`, `computeCanProduceAudio`, main branch
  2026-11): the display is kept on only for a playing video that has an audio track, is not muted, has volume > 0, has not
  ended and does **not** have the `loop` attribute. So, unlike the first idea, the video is not muted and not looped; it
  jumps back by itself on `timeupdate`. Being off screen (1 px, opacity 0) does not matter, only a hidden document does.
  Side effect on iOS: as unmuted media it may take the audio session (pause music of another app on the iPad).
- macOS: `SleepGuard` runs `/usr/bin/caffeinate -i -w <pid>` while at least one browser is connected (released 60 s after
  the last one leaves, so reloads do not flap; ended on quit, and by `-w` also after a crash). `-i` keeps the computer from
  idle sleep, which would stop LJ Link for the iPad. The display (`-d`) is deliberately left to the page: the server does not
  know whether the Mac display shows LJ Link at all, and an unwatched Mac display in a dark booth should be allowed to sleep.
- Windows: `SetThreadExecutionState` would need JNA or the FFM API (preview in Java 21) or a PowerShell helper; not done.
  The page part (wake lock on localhost) works there anyway.
Status shown under the setting and as tooltip: active, active (video), waiting for a tap, paused while hidden, not available,
off. Races: the hub reports the browser count under one lock (reports cannot arrive swapped), and every report raises a
generation number in `SleepGuard`, so a release timer that already fired does nothing once a browser came back. Not tested on a real iPad or on Windows yet (headless Chrome only).

Addendum (2026-11-08, after the developer found it working on the Mac but not on the iPad over `http://<ip>`; research in
`docs/handoffs/2026-11-08-ipad-wach-recherche.md`). Still Alpha, still untested on a real iPad.
- The video is no longer invisible by CSS: 2x2 px, `opacity: 0.01`, `position: fixed` at the bottom left corner (inside the
  viewport), `z-index` at the top instead of `-1`, never `display: none` / `visibility: hidden`, never `muted`, `loop` or volume 0,
  and appended to `body`. Reason: the current WebKit source only looks at a hidden *document*, but old reports (iOS 10) said
  `opacity: 0` stopped the trick, and iOS may skip rendering a fully transparent or covered element. A real, rendered pixel
  rules that out at no visible cost (black at 1 % on any background changes a colour value by about 2 of 255, on 2x2 px).
  2x2 instead of 1x1 so the element has a real area even after device-pixel rounding; on top so no panel covers it;
  `pointer-events: none` so it never takes a tap.
- Honest status: `play()` resolving only means "starting". "Active (video)" is shown only once `currentTime` is seen moving;
  it is checked every 3 s while the page is visible. If the video is paused (`pause`/`ended` events, e.g. iOS after a lock,
  another audio app, a call, Siri) or its time stands still, the status becomes "Video stopped – please tap the page" and the
  strip comes back with "The screen may turn off – tap here to start it again"; the next tap/click/key plays it again.
  On `visibilitychange` (unlocked, back to the tab) and `pageshow` from the back/forward cache the state is checked again
  instead of trusting an old "active". QS round 2: "starting" ends after 2 checks (about 6 s) without movement in "stopped"
  (iOS may resolve `play()` without playing). A video that still plays keeps its last state on full screen, visibility
  and `pageshow` (no flicker to "starting"); only a real standstill changes it. The check timer runs only in "starting" and
  "active (video)" and is cleared otherwise (off, hidden, stopped, wake lock); there is never more than one.
- While the video way is used, a note under the setting recommends "Display & Brightness → Auto-Lock: Never" with the charger
  connected, as the more reliable way on iPad/iPhone without HTTPS. It also asks to switch Low Power Mode off, worded carefully: that it shortens
  Auto-Lock to 30 s is documented for the iPhone only.
- Diagnostics: the problem report (`/api/diagnostics`) is made by the server and holds no browser data, and on a tablet it is
  not even offered, so no new channel was added. Instead the way (Wake Lock or video), whether `currentTime` moves (with
  `paused`, `muted`, `volume`), the last stop (time and reason) and the user agent are in the tooltip of the status line and
  logged with `console.info` on every state change.
## ADR-023: VJ-Link interface (`/vjlink`, Bonjour `_ljlink._tcp`)
Status: Alpha (2026-11-09). Tested against the demo only; phrases from real players untested.

VJ-Link (the developer's VJ program) used the browser stream `/ws`, which lacks beat phase, phrases, cues, band levels and
track-change events and only sends decks on a change. So LJ Link gets its own read-only stream for VJ programs:
- `ws://<host>:<port>/vjlink`, always on, nothing to set up. JSON protocol version 1, every message has `t`; unknown
  fields are ignored by the receiver, so fields can be added. On connect: `hello`, all `deck`s, `tempo`.
- `tempo` (~30/s): tempo of the master deck and a running beat counter (`beat`, whole number = beat) from the beat grid at
  the predicted position. The counter is continuous over loops, cue jumps and a change of master: after a jump it moves
  forward to the same beat phase (less than one beat), so visuals never count backwards. `bar` from the beat grid,
  `phraseBeat` from the phrases.
- `deck` at once on a change (compared without the position), otherwise every 0.5 s; `gone` when a deck disappears.
  `trackId` is a hash of title, artist and length: the same for every source, and it does not reveal the title.
  Privacy mode drops title, artist and cover (and a new `hello` with `privacy` goes out when it is switched).
- `event`: `track` (a new track plays on air or as master; not for what already plays when LJ Link starts), `phrase`,
  `next` (32/16/8/4 beats before the next phrase), `cue` (a hot cue A-H or memory point M is passed while playing or
  jumped to), `loop` (on/off with beats).
- `wave` (~30/s, master deck): bass/mids/highs from the detail waveform at the play position (strongest frame since the
  last message, so no kick is missed); colour waveforms are split by red/green/blue (rekordbox colours lows red, highs
  blue), three-band ones are taken as they are; times channel fader and EQ when the mixer reports them.
- `mixer` (~10/s) when faders or a crossfader are known (Denon, demo; Pro DJ Link only knows ON AIR).
- The VJ program may send `{"t":"vj","scene":…,"blackout":…}`; the header shows "VJ-Link connected · Scene …".
- Phrases: rekordbox phrase analysis (`PSSI` in the `.EXT` file) via beat-link's `AnalysisTagFinder`; the mid and low moods
  are mapped to the five kinds of the high mood (verses → up, bridge → down).
- Bonjour/mDNS `_ljlink._tcp` with TXT `vjlink=1`, `path=/vjlink`, `version`, `app` on every IPv4 adapter (JmDNS), so
  VJ-Link finds LJ Link on another computer. Failures are only logged.
- The demo now plays a DJ set: phrases 16/8/16/8/16/16 bars, the other deck starts beat-aligned and synced at the outro,
  faders, EQ (bass swap) and crossfader move, the new deck becomes master when the old track ends (about every two
  minutes), every other track loops the last bar of its breakdown four times, cues sit on the phrase starts.

## ADR-024: Browser sending decoupled, `pos` with server time, bridged position gaps
Status: Alpha (2026-11-10), for 1.7.7. Tested against the demo and with an artificially hanging client only; real
players and the reported long-running delay (Discord, jnsp) are not reproduced. Background: `docs/handoffs/2026-11-10-waveform-verzoegerung-support.md`.

- **One sender per browser.** `Hub` used to send on its single ticker thread, and Javalin 6.7's `WsContext.send` blocks
  (Jetty `sendString`), so one slow or sleeping browser (iPad in Wi-Fi, throttled tab) delayed every other browser and
  the ticks then came in bursts. Now every browser is a `WsClient` with its own queue, drained by its own task on a
  cached thread pool. Messages are keyed (`state`, `mixer`, `deck:n`, `pos:n`): a newer message replaces a waiting one
  with the same key and moves to the end, so no backlog can build up. `deckGone` is never replaced; it drops whatever
  still waits for that deck, and late messages for a removed deck are dropped (deck numbers are never reused).
- **Stalled browsers are dropped, eventually.** A send only blocks once the operating system's socket buffers (server and
  browser side) are full. A send that has then been blocked for more than 5 s (`WsClient.STALL_NANOS`), or more than 1000
  waiting messages, disconnects the browser hard (`Session.disconnect`, a close frame would wait behind the blocked send).
  So a hanging browser is not dropped after 5 s but only after its buffers filled up, which takes seconds to minutes
  depending on the system (QS: a stopped raw client after about 40 s; a stopped Chrome on macOS not within 90 s, its
  buffers grow). The other browsers stay live meanwhile either way, which is the point. The page reconnects after 1.5 s
  by itself and gets the full state again. The client count for the sleep guard (ADR-022) follows as before. The
  problem report shows how many browsers were dropped.
- **`pos` only on change.** A position goes out when position or speed changed, otherwise as a keepalive once per second
  (a paused deck no longer gets 50 identical messages a second). Playing decks still get up to 50 per second.
- **Server time in `pos`.** `{"type":"pos","deck":n,"p":ms,"v":speed,"s":seq,"t":serverMs,"a":ageMs}`: `t` is a monotonic
  server clock in ms when the message was made, `a` the age of the sample then, `s` a counter over all `pos` messages.
  The page drops a message older (`s`) than the last one of the deck. It estimates the clock offset as the smallest
  `received - t` over a sliding window of 8-10 s (2 s buckets), re-estimated with every message, and places the position
  at the time it was valid (`t - a + offset`) instead of its arrival. So a late message no longer shifts the waveform.
  Assumption: the fastest delivery in the window is the normal one; a constant delay (a few ms on a LAN) cannot be
  measured without a round trip and stays. Window and counter reset on every (re)connect.
  After a gap of more than one 2 s bucket without `pos` messages (a stalled or frozen page that then reads its backlog
  from the socket buffers) the window would only hold the old backlog messages, and they would count as current. So the
  offset from before the gap is held for 3 s more (only for gaps under 5 min, where clock drift is a few ms; a held value
  is never carried into another hold, so nothing can build up with the uptime). Backlog messages are then placed in the
  past and carried on to now, and fresh messages refill the window meanwhile. Trade-off: if the server's own clock
  stood still during the gap (the server computer slept and the connection survived), positions are off by that time
  for those 3 s and then snap; on macOS the sleep guard keeps the server awake while browsers are connected.
- **Shown position (app.js `advance`).** No more 100 ms cap per frame (a stall left a lasting lag) and no dead band (it
  kept up to 25 ms, with NXS2 up to 220 ms, behind for good). The pull towards the newest sample is time-based with a time
  constant that falls as the error grows: `tau = fast + (slow - fast) / (1 + (err/knee)^2)`, exact players 1500/120 ms,
  knee 60 ms; coarse players (NXS2 and older) 6000/200 ms, knee 250 ms. A frame gap over 250 ms, a jump over 800 ms or the
  tab becoming visible again snap to the newest position. Simulated: an exact player's 300 ms error is under 50 ms after
  0.8 s; noise of ±15 ms changes the shown speed by about ±1 %.
  Coarse players only: while the error is under 400 ms it is low-pass filtered with a 2 s time constant before the pull
  (`errLow += (err - errLow) * (1 - exp(-dt/2000))`); from 400 ms on it is used unfiltered, the 800 ms snap is unchanged.
  Assumption: an NXS2's error is up to ±0.2 s between beats (from a code comment, not measured); its speed is exact.
  Simulated with the real `advance` code (60 fps, 50 messages/s, 120 s, shown speed over 100 ms, p1/p99), ±150 ms
  noise: stepwise per beat ±4 % → ±1 %, independent per message ±1.8 % → ±0.3 %, sawtooth per beat ±2.5 % → ±0.3 %;
  a 300 ms offset is under 50 ms after 7.0 s (8.3 s before) with 3 ms overshoot, 600 ms after 2.9 s (9.5 s) with 40 ms
  overshoot. Exact players are unchanged. Not checked on a real NXS2.
- **Bridged TimeFinder gaps (`PositionHold`).** beat-link 8.0.0 `TimeFinder.getTimeFor` returns -1 when it has no entry
  for the player; it removes the entry on a status packet without beat grid or with beat number < 0, on a beat packet
  without beat grid (unless precise positions arrive), and when the device is lost (checked in the sources). The
  fallback `positionFromBeatGrid` is only exact on the beat. A -1 after a good position is now bridged for up to 1.5 s by
  carrying the last good position on at the deck's speed (held while paused); only then does the beat grid take over,
  until TimeFinder knows the position again. No bridging without a track, after another track was loaded, or when
  TimeFinder never had the position (beat port taken: beat grid at once, as before). A short -1 therefore no longer
  reaches `Deck.samplePosition`, which would stop the measured speed. After a real loss of 1.5 s the old behaviour holds.
- Not changed: the VJ-Link stream (`VjLink`) still sends on its own ticker; a hanging VJ program only delays other VJ
  programs, not the browsers.

## ADR-025: rekordbox library and live-status bridges need a new legal review first
LJ Link reads Pioneer data only through Pro DJ Link (beat-link) and the analysis files the players share. Reading the
rekordbox 6/7 library directly (an encrypted database) or taking the live status of rekordbox from a third-party tool
that reads its memory is not planned. Before either is built there is a new legal review, recorded here as an ADR; the
public roadmap only says "not planned – review first" and names no keys, tools or ways around the encryption.

## ADR-026: Ableton Link: protocol only, no code of Ableton's library
`AbletonLinkPeer.java` implements the Link protocol in Java without using or translating code of Ableton's Link library
(C++, GPL-2.0-or-later). Spot check on 10 Nov 2026 against github.com/Ableton/link (`Measurement.hpp`, `Sessions.hpp`,
`NodeId.hpp`), comparing structure, identifiers, constants and comments:
- **Structure:** one Java class with a scheduler, records and maps; Ableton's library is header-only C++ with templates
  over clock and IO context and separate classes (Measurement, Sessions, Peers, Controller). No class or function layout
  is mirrored.
- **Identifiers and comments:** no shared names apart from the protocol's own (message headers `_asdp_v`/`_link_v`,
  payload keys `tmln`, `sess`, `stst`, `mep4`, `__gt`, `_pgt`, `__ht`). No comment text or licence header is taken over;
  our comments describe the protocol in our own words.
- **Behaviour and numbers:** these follow the library, because other peers must agree with us: the two clock-offset
  samples per pong (ghost time minus the mean of the host times; mean of the ghost times minus the host time), more than
  100 samples and their median, a ping every 50 ms with at most 5 retries, re-measuring the joined session every 30 s,
  joining a session whose ghost time is more than 0.5 s ahead (tie: smaller session id), and adopting the timeline with
  the larger beat origin. They were taken from reading the source as protocol facts, not as code.
- **Differences:** the node id is 8 random alphanumeric bytes (the library uses random bytes), measurement and retries
  run on our own 50 ms tick, and the phase lead from the deck's beat grid (`lead`, `deckBeat`) has no counterpart in
  the library.
Result: protocol and parameters only, no code copied. The point to watch is that the session and measurement rules
were read from GPL source; if this is ever questioned, the answer is this ADR plus the git history of the file. Not a
legal opinion.

## ADR-027: Repository name, account and old releases
The public repository stays `skyplayser/LJ-Link` and the account `skyplayser` is not renamed. There is never a new
repository called `LJ-Link-PRO`: versions 1.4.1 and older ask GitHub for updates under the old name and reach the
current repository only through GitHub's redirect, which a repository with that name would break. Release pages of old
versions point to the current version ("Please use the latest version."); assets are only removed with the developer's
approval.

## ADR-028: Local API guard (R-15)
All routes under `/api/` are local by default; only `/api/ping`, `/api/version`, `/api/info` and `/api/deck/*`
(read-only, for tablets) and the WebSockets `/ws` and `/vjlink` are open to the LAN. A central Javalin `before` filter
(`ControlGuard`) requires a loopback client, a `Host` of `localhost`, `127.0.0.1` or `[::1]` (own port), an `Origin` (if
sent) equal to `http://` + host, and `Content-Type: application/json` for POST (403/415 otherwise). This blocks CSRF and
DNS rebinding from web pages opened on the show computer; new routes are protected unless explicitly made public.
A `Sec-Fetch-Site` header other than `same-origin` or `none` is refused, also for GET, so other pages cannot trigger
`/api/check` or `/api/link/scan` with `<img>` or `<script>`.

## ADR-029: Update integrity (R-16)
The updater only installs a download whose SHA-256 equals the asset `digest` from the GitHub release API. In addition,
once `Updater.SIGNING_PUBLIC_KEY` is set, `<asset>.sig` must hold a valid Ed25519 signature over the 32-byte SHA-256,
made offline with the developer's key (`scripts/sign-release.sh`); a missing or invalid signature means no installation,
only a link to the release page. While the key is the empty placeholder (1.7.8), only the checksum is checked and the
dialog says "not signed". The private key never goes to GitHub.

## ADR-030: Asset names (R-06)
From 1.7.8 installers are named `LJ-Link-*`; a copy under `LJ-Link-PRO-*` is attached for about three releases (at least
three months) because older installs only look for that name. The updater looks for the new name first, then the old
one, and identifies itself as `LJ-Link/<version>`. Package `dev.ljlinkpro`, `~/.ljlinkpro`, the defaults domain and
`/ljlinkpro/bpm` stay.

## ADR-031: Engine library copy (R-25)
The downloaded `m.db` is restored into an in-memory SQLite database and the temp file deleted at once; only libraries
above 256 MB stay as a file while open. Leftovers of ended runs are deleted at start. Temp copies get unique names
(`ljlinkpro-<pid>-<source>-<random>-m.db`).

## ADR-032: Third-party notices check (R-14)
`checkThirdPartyNotices` (runs with `check`/`test`) fails the build when a shipped JAR is missing from
`THIRD_PARTY_NOTICES.md` (by `group:name` and version). Temurin and Remote Tea sources are attached to each release by
the workflow; written offer (T-16) in the notices and the license page.

## ADR-033: Quit window with feedback and rating
"Quit" in the program icon menu first shows a small window (`QuitDialog`): community note, optional 1-5 stars,
"Give feedback", "Share link", "Quit" and "Don't show this again when quitting" (`skipQuitDialog` in the settings file,
saved as soon as it is ticked). "Give feedback" does not quit (the web interface would be gone): it opens
`/#report?stars=N` in the web interface. Closing the window (X or Escape) cancels. The quit of an update, a missing
system tray and a headless JVM never show the window. The rating only goes into the report text ("Rating: N of 5 stars"
first) that the user sends by hand; nothing is sent automatically. A rating alone is enough to send a report.
The web settings have "Show note when quitting" (the inverse of `skipQuitDialog`), read and set through the local-only
route `/api/quit-note` (GET, POST `{"on": bool}` with JSON, ControlGuard), shown only on the computer that runs LJ Link
and only when the program icon is installed (`"tray"` in the GET answer). Enter in the window means "Quit" (default
button, focused), since that is what the user chose. The GitHub link of the report window sets no label (a wish or a
rating is not a bug). In the web the stars behave like radio buttons (clearing only with "Clear rating" or
Delete/Backspace/0); in the Swing window clicking the chosen star again clears it, as there is no clear button there.

## ADR-034: Report mail without the false "attached" claim; summary in the diagnostics
A `mailto:` link can never carry a file. When the whole report makes the link longer than `MAILTO_MAX` (2000
characters, whole link; assumption: Windows/Outlook cut mailto links at about 2000, not measured), "Send by email" saves
the report into the browser's downloads folder, copies the diagnostics to the clipboard and writes into the mail first
"PLEASE ATTACH: the file <exact name> from your downloads folder. The mail program does not attach it by itself.", then
the description, then as many lines of the new summary as fit. The dialog shows a clearly visible box (`#report-attach`,
`role="alert"`) with the file name and where it is; the browser's download folder cannot be read, so it says "usually
Downloads". The diagnostics start with `== Summary ==` (`Diagnostics.SUMMARY_HEADER`): version and system, IPv4
adapters, network warnings, every source in one line, per source its key lines (`DeckSource.summary()`; Pro DJ Link:
own player number and beat-link's adapter, MetadataFinder/CrateDigger and mounted media, mixer status count, a note
when a mixer is announced but sends no readable mixer status, every device seen by announcement with number, name,
address, local adapter in its network and packet counts), then one short line per deck. Most important first, since
the mail drops lines from the end. Devices are remembered from their announcement also after they left, so a DJM-A9
shows up as device 33 even without mixer status. Packet counters are diagnostic only and change no behaviour. IPv6
addresses are never listed (they can contain the hardware address), only counted; with `preferIPv4Stack` Java sees none.

## ADR-035: Mixer by announcement, metadata hint, ShowKontrol note, clean quit (Alpha, no hardware)
Background: support report DJM-A9 with 4x CDJ-3000X (handoffs 2026-11-13). beat-link stays at 8.0.0;
`setUseStandardPlayerNumber(false)` is unchanged.
- Mixer: a device counts as mixer by its announcement when its number is 33-40 or its name starts with "DJM"
  (`ProDjLinkSource.isMixer`; assumption). The Mixer tab then shows it with model, number and address. `statusPackets`
  in the mixer message is "waiting" until the virtual CDJ has listened 5 s, then "none" if no `MixerStatus` arrived
  (honest note in the tab: recognised, but no readable data; ON AIR comes from the players) or "ok" with BPM/master.
  A mixer that leaves is removed. The channel strips are unchanged (they come from the players' status).
- Metadata hint: an analysed track (rekordbox id set) without a title for 30 s gets `metaHint` "onelibrary" for the
  models beat-link main lists in `isUsingDeviceLibraryPlus` (CDJ-3000X, XDJ-AZ, OPUS-QUAD; checked in beat-link main
  `DeviceAnnouncement`/`OpusProvider`; CDJ-1500X is not listed there and is not included) and "missing" otherwise. The
  deck shows a short title ("Not supported" / "No track data") and a sentence over the waveform with a link to the
  wiki (troubleshooting, tracks and waveforms). Diagnostics: model per player, OneLibrary flag, mounted media with
  "db"/"no db" (CrateDigger has the export.pdb or not), and the last CrateDigger problems from the log with their cause.
- ShowKontrol: a device whose name contains "SHOWKONTROL" (it announces itself as "TCS-SHOWKONTROL") gives a quiet note
  in the app (`notes` in the state message, per source; shown while searching and while monitoring) and a diagnostics
  line; nothing is filtered. When the announcement came from one of this computer's own addresses, the note ("runs on
  this computer") is kept for the whole search: once the virtual CDJ runs, beat-link 8.0.0 ignores announcements from
  its own address (`DeviceFinder.addIgnoredAddress`), so ShowKontrol seems to leave although it still runs.
- Summary order (for the mail): version, all WARNING/NOTE lines, Pro DJ Link lines (own number, players, media, mixer
  count, compact "Seen …" lines), adapters, sources that found something, decks, then sources that found nothing.
- Quit: `ProDjLinkSource.stop()` removes all of its listeners from beat-link's singletons, stops the finders, the
  BeatFinder (port 50001; it was left running before), the virtual CDJ (always, not only when its start was confirmed)
  and the DeviceFinder. Joining and stopping exclude each other (`lifecycle` lock), so a join in progress cannot leave
  the virtual CDJ online after stop. Callbacks after stop are ignored. The slow quit (8 s and more) came from Bonjour:
  JmDNS closed one adapter after the other and waited for each; now all are closed in parallel and quitting waits at
  most 1.5 s (`Bonjour.CLOSE_TIMEOUT_MS`).

## ADR-036: New logo "Fader" (suite style)
The developer chose the logo "Fader" on 2026-11-13; it replaces the waveform bars with light beams (ADR-011). It follows
the suite style (as VJ-Link): a dark rounded tile (gradient #1A1E2A to #0D1016, edge #2A2F3D), the letters "LJ" in Inter
Display ExtraBold and five console faders with caps in the suite colours (#FF3B3B, #FFB020, #3DDC84, #22C8F0, #B45CFF,
rails #272C39). The reference is `render.cjs` of the suite logo package (kept outside this repository).
- Installer icons: `packaging/LJ-Link.icns|ico|png` are the files of the logo package (rendered in a browser), not
  generated by `Logo.main` any more; `Logo.main` still writes PNGs with the same geometry (checked against
  `LJ-Link.png` by `LogoTest`).
- Letters as outlines: the glyphs "L" and "J" were taken from the TTF with Java AWT (`GlyphVector.getGlyphOutline`,
  320 px, baseline 520, letter spacing -4 px, no kerning pair L-J) and stored as one SVG path in `Logo.LETTERS` and
  `web/logo.svg`. No font is shipped or loaded. The OFL 1.1 does not cover artwork made with a font; we still name Inter
  in THIRD_PARTY_NOTICES.md and ship the license text (`legal/OFL-Inter.txt`).
- `web/logo.svg` uses the viewBox 100 100 824 824: the tile fills the image (favicon, header, start screen); the macOS
  icon margin is only in the installer icons. The CSS no longer rounds the logo images (the SVG has its own corners).
- Menu bar / tray (`Logo.tray`): assumption that "LJ" is unreadable at 16-22 px, so the tray icon shows only the five
  faders on the tile, which fills the image. It stays a coloured image, not a macOS template icon (as before).

## ADR-037: Web interface in the suite design of VJ-Link
Decided by the developer on 2026-11-13: the web interface uses the design tokens of VJ-Link (`--bg #0C0E13`,
`--panel #151821`, `--raise #1E222E`, `--line #2A2F3D`, `--text #DCE1EA`, `--dim #7E879A`, `--live`, `--ok`, `--warn`,
`--sA`…`--sE`, `--mono`, system-ui 13px, small caps headings, 2px focus ring, pills, panels and segmented controls),
but the accent is cyan `#22C8F0` from the "Fader" logo instead of the suite pink. The day theme stays as a light
variant of the same tokens. `SuiteDesignTest` checks tokens, that no old variable is left and the contrast.
- Old names mapped: `--muted` → `--dim`, `--panel-2` → `--raise`, `--bad` and `--onair` → `--live`, `--radius` removed
  (radii inline as in the suite). LJ-only tokens: `--accent-text` (text on the accent), `--master` (orange of the tempo
  master and MT, CDJ convention), `--wave-bg`, `--playhead` (read by `app.js`), `--shade` (dialog backdrop).
- Primary buttons are cyan with dark text (white on cyan would be 2:1). Day: accent `#006F8E`, ok `#0D7345`, text,
  dim text, accent, ok, warn, live and master reach WCAG AA (4.5:1) on bg and panel; text, dim, accent, ok, warn and
  live also on raised controls (status pill, badges). Night: the suite `--dim` on `--raise` is 4.4:1 and `--live` on
  `--raise` 4.49:1 (suite values kept; hints sit on bg or panel, 4.9:1 or more).
- White text on red (LIVE tag, mixer ON AIR) uses the LJ-only token `--live-fill`: night `#E02828` (4.7:1 with white;
  the suite `#FF3B3B` would be 3.5:1), day the same as `--live`. Frames, glow and the deck strip keep `--live`.
- Text size (decided by the developer on 2026-11-13 after QS): base text 15px instead of the suite 13px, hints 13px,
  tabs, small caps headings, pills, VIEW and the zoom text 12px, everywhere. The booth is read from further away than a
  desktop app. Deck values are not affected.
- Touch: with `pointer: coarse` every button, tab, segment, tool button (−, +, VIEW), checkbox row, slider, the
  `summary` in the report and "Clear rating" is at least 44px high; the media query repeats the more specific desktop
  selectors so it wins. On the desktop buttons are 36-40px. Inline text links are not enlarged (WCAG 2.5.8 exception).
- The waveform area is not dimmed as a whole: the 75 % opacity sits on the canvases (and the remaining time next to
  the overview), the background is the same 75 % mix with the panel. So the notes over an empty waveform ("No track
  data", OneLibrary) keep their full contrast (`--dim` on the waveform background 5.6:1 at night).
- Stars in the report: empty stars are `--dim` outlines, chosen stars are filled `--warn`, 3:1 or more as graphics in
  both themes (the old `--sB` on white was 1.8:1).
- Unchanged on purpose: sizes of deck number, PLAYER/TRACK/BPM boxes, elapsed and remaining time, ON AIR frame and LIVE
  tag, waveform heights, layout (400px deck column, 820px and 640px breakpoints), all waveform and cue colours and the
  black waveform background at night. Waveforms stay at 75 % opacity for every deck (the old CSS had two rules, the
  unconditional one won; kept so the waveforms look as before).
- Changed colours outside the tokens: channel bar and mixer faders cyan (were blue `#2F7BFF`), SYNC chip cyan, number
  boxes, knobs and the deck strip follow the theme (were fixed light grey and unreadable in the day theme), notes are
  suite notes (cyan edge = information, amber edge = warning) instead of the dark yellow box, which was dark in the day
  theme too. The report box after "Send by email" keeps a strong amber frame.
- No blur behind dialogs (the suite uses `backdrop-filter`): it would be recomputed for every frame of the waveform
  canvases on tablets.
- The Swing windows (quit note, update) use the system look and get no accent colour; they stay as they are.

## ADR-038: Own player number chosen by LJ Link and given up on conflict; beat packets without status (issue #2, Alpha)
An XDJ-AZ announced itself as 1-4, 7 and 33 from one address; LJ Link's virtual CDJ had 7 as well and beat-link kept
sending "device number defense" packets to the XDJ-AZ. Checked in the beat-link 8.0.0 sources: self-assignment starts
at 7 (or 1 with `setUseStandardPlayerNumber(true)`), skips numbers in `DeviceFinder.getCurrentDevices()` after a 4 s
watch period and goes up to 15. Defending cannot be switched off: `DeviceFinder` calls `defendDeviceNumber` for every
announcement with our number while the virtual CDJ runs. The only levers are `setDeviceNumber` before `start()` and
stopping the virtual CDJ.
- LJ Link chooses its number itself (`ProDjLinkSource.chooseOwnNumber`): the first from 7 to 15 that no device announced
  since the search started (also devices that left again), that LJ Link did not give up already, and not 7 when an
  XDJ-AZ is seen. The last part is an assumption from one diagnostics report; it is not known whether the XDJ-AZ
  announces 7 by itself or only in reply to LJ Link. LJ Link waits the same 4 s after the first device as beat-link.
  If the number is refused, beat-link's own self-assignment is tried once.
- When a device announces LJ Link's number while it runs (checked for every new device and once a second), LJ Link
  stops its virtual CDJ and joins again with another number instead of defending it. One defense packet may still go
  out before (beat-link delivers announcements to listeners on the Swing thread). At most 3 changes per search; if the
  device copies every number, LJ Link keeps the last one and says so in the diagnostics.
- `Beat.isBeatWithinBarMeaningful()` throws a NullPointerException while no status packet of that player arrived (it
  reads `VirtualCdj.getLatestStatusFor(beat)` without a null check). That was the "Problem delivering beat announcement
  to listener" flood. LJ Link reads the status itself; without one the beat in the bar counts as unknown (0).
- The TimeFinder NullPointerException ("lastPosition is null", beat without beat grid and without status) is inside
  beat-link and not fixable from outside. The log shows each message at most 3 times a minute and then counts it
  (`LogBuffer.Throttle`), also on the console; the diagnostics add "[log] repeated N more times …". Only the exception
  line that followed the message when it was shown, and the "at"/"Caused by" lines after it, are left out with it;
  any other line (also another exception) counts as a message of its own, so nothing foreign is lost.
- LJ Link's own stop of the virtual CDJ (number change) is counted only under the VirtualCdj's monitor, which
  `VirtualCdj.stop()` also takes; a stop by beat-link in between is therefore never mistaken for ours.
- Two adapters in the players' network deliver every broadcast twice. Status packets twice do no harm (same content);
  a beat packet twice could give the light desk two beats if it polls between the copies. The same beat (player,
  address, beat in bar) again within 30 ms is now ignored and counted as "Beat duplicate" in the diagnostics.
- Not checked on hardware: the XDJ-AZ behaviour, whether status packets then arrive (BPM, ON AIR), and whether a number
  above 7 makes a difference to any player.

## ADR-039: Free port before start, a new server per port (issue #3)
With port 8080 in use LJ Link quit: `Main.start` called `start(p)` on the same Javalin instance for 8080-8089, but
Javalin 6.7.0 throws "Server already started - Javalin instances cannot be reused" on the second call (checked in
`JettyServer.kt`). Also, Jetty binds all addresses with SO_REUSEADDR; on Windows that can succeed although another
program listens on the port (seen the same way on macOS with a program bound to 127.0.0.1 only), and the browser then
reaches the other program.
- Each port is checked first (`Main.portTaken`): a plain `ServerSocket` bind to all addresses with Java's defaults
  (exclusive on Windows) must work, and nothing may answer on 127.0.0.1 (a timeout counts as taken). Then a new Javalin
  instance with all routes is built and started; if Jetty still fails, the next port gets a new instance.
- The port in use is shown in the log ("Port 8080 is in use by another program; LJ Link uses port 8081 instead."), in the
  tray tooltip ("LJ Link – localhost:8081"; the menu already showed the address) and on the start page
  (`/api/info` has `port` and `portWanted`).
- LJ Link opens and shows `http://127.0.0.1:PORT`, not `localhost` (QS round 2): browsers try `localhost` as `[::1]`
  first, and with `java.net.preferIPv4Stack=true` (needed for beat-link, see `Main`) LJ Link can neither bind nor
  check `[::1]`; a program there (for example a Node.js server on "localhost") would get the browser. ControlGuard
  already accepted `127.0.0.1`; 127.0.0.1 is a secure context like localhost (Wake Lock); Bonjour announces the LAN
  addresses and is not affected.
- Browsers keep settings per address. After an earlier version (a language is saved in the settings file) LJ Link
  opens `http://localhost:PORT/#move-settings=<token>`; `settings-move.js` (loaded before `i18n.js` and `app.js`)
  sends the settings to `http://127.0.0.1:PORT/#import-settings=<token>.<data>`. That page removes the hash at once and
  imports only after `POST /api/settings-move` accepted the token (`SettingsMove`: 128 bit, issued by `Main` only for
  this, valid 5 minutes, redeemed once; the route is local and same-origin through ControlGuard). Only known keys with
  valid values are taken (language, zoom 4-40, mode, theme, view, keepAwake, hidden group ids, link preset; looked up
  with `hasOwnProperty`, so no `__proto__`), existing entries stay, then the marker `ljlinkpro.moveDone` stops every
  later import. Without a valid token nothing is imported (QS round 2: another web page could plant settings through
  the hash). `app.js` also copies only the known keys from storage instead of `Object.assign`. The hash leaves the
  address bar and the tab history at once; the browser's global history still lists both addresses with the token,
  where it is worthless (one use, 5 minutes). When the browser cannot be opened, the log shows the address without
  hash, so no token gets into the diagnostics.
- `browserSettingsMoved` in the settings file is set when the token was redeemed, when there is nothing to move (a new
  installation), or after 2 starts that opened the move address without success (`browserSettingsMoveAttempts`). A
  start without browser does not count, so the move is made up later. If another program sits on `[::1]` at that
  port, at most 2 starts reach it; from then on LJ Link opens 127.0.0.1 and the old browser settings stay behind
  (QS round 3).
- All ten ports in use: the log says so, and a message window (menu language, with the hint on `--port`) is shown,
  unless Java runs headless. Then LJ Link quits with exit code 1.
- Not checked on Windows; "another program on 127.0.0.1" and on `[::1]` were reproduced on macOS.

## ADR-040: Input for LJL Link (formerly "LJ Link Sender"), club key and limits (Alpha)
LJL Link (named "LJ Link Sender" until 14.11.2026) is a small separate app on the DJ's computer that reports deck data (track, length, waveforms, later
the position) to LJ Link in the booth over the network. This is LJ Link's side of the sender protocol, version 1.
- **Input**: WebSocket `/sender` (`SenderInbox`). `hello` → `welcome` or `reject` (`key`, `protocol`, `full`, then
  closed with 1008); `load`, `waveform` (`overview` → `Deck.setWaveform`, `detail` → `Deck.setDetail`, both style
  `threeband`: 4 bytes per entry, low, mid, high, 0), `position` (passed to `Deck.samplePosition`, Alpha), `unload`,
  `names`, `ping` → `pong`, `bye`. Deck messages before the welcome, unknown types and broken JSON are ignored. A
  waveform counts only if its `id` is the deck's current track, `entries * 4` equals the decoded length, and a detail
  waveform has 150 entries per second (the browser draws only that; `fps` missing is taken as 150).
- **Open to the network, but only with the club key.** `/sender` is not under `/api/`, so `ControlGuard` does not
  apply and stays unchanged. A sender is accepted only with the current club key, compared in constant time
  (`MessageDigest.isEqual`). Everything it sends is read as deck data only; there is no message that controls LJ Link,
  the players or the light desks.
- **Club key** (`SenderClub`): 128 random bits (`SecureRandom`) as lowercase hex, created on first use and kept in the
  settings file (`senderClubKey`). Local API (ControlGuard, this computer only): `GET /api/sender` (club name, key,
  port, own IPv4 addresses, connected senders), `GET /api/sender/club.json` (what the sender reads: `version`, `club`,
  `hosts`, `port`, `key`), `POST /api/sender/key` (new key; every connected sender gets `reject key` and is
  disconnected, so an old `club.json` stops working at once), `POST /api/sender/club` (club name; blank = computer
  name). The key is never logged, never in a problem report and never sent to `/ws` (tablets).
- **Limits**: 4 senders (`reject full`), decks 1-4, 2 MB per message after the welcome and 64 KB before it (Jetty
  `setMaxTextMessageSize` per session, closes with 1009; checked again in `SenderInbox`), names cut to 64 characters,
  titles to 200, control characters removed. 15 s without any message and the sender is dropped with its decks.
- **Connections without a hello** (QS round 1: 8 of them kept every real sender out): closed after 3 s, at most 2 per
  address, at most 8 in all. A new connection always gets a place: the oldest waiting one of the same address, or else
  the oldest waiting one at all, is closed (1008 `busy`). So a sender that says hello at once always gets in, and one
  that reconnects quickly is not kept out by its own earlier attempts. Refusals are logged at most once a minute per reason, the rest is
  counted ("N more refused"), so a flood cannot push the rest out of the log buffer of the problem report.
- **Decks**: one `Hub.Scope` per sender (`sender-s1` …, kind `sender`). Group and deck name are the DJ software
  ("Serato DJ Pro"), never the computer's name: it is personal, goes into problem reports and was cut off on the deck. A
  second sender with the same software gets a number ("Serato DJ Pro 2"), so a DJ handover stays clear. The computer's
  name is only in the local `/api/sender` list; the log names senders only by id and address. Sender scopes are
  persistent from creation (`Hub.persistentScope`): "Search", "Demo" and "Stop" (`Hub.reset`) keep them, they go only
  when the sender goes.
- **Position** (added after the first real test): `position` may carry `speed` (the sender's measured playing speed);
  the deck's velocity is set to it (`Deck.samplePositionWithSpeed`), 1.0 without it. The 150 ms estimate of other
  sources never found a speed with the sender's 4-5 messages a second, so the playhead stepped from message to message
  (measured: up to 0.6 s off a straight line; now median 1-15 ms, at most 43 ms). Sender decks are `coarsePosition` (0.1 s steps, smoothed
  like an NXS2). `ms: null` = position unknown (Serato hides the deck): no playhead.
- **Browser**: while senders are connected and no search shows decks, the sender decks are above the start or search
  page, not below it. Sender decks show no track number, cover, channel fader or pitch (the sender has none of them);
  a deck without position says so ("NO POSITION" and a line in the detail waveform) instead of "--:--" and "PAUSE";
  `titleHidden` shows "Title hidden by the DJ". The text for a deck without position names no single cause (2-deck
  view, no screen recording permission, calibrating).
- **Senders do not set the phase** (QS round 1: with a sender connected the start page, the search and "Disconnect"
  were unreachable). `Hub.phase()` and the state's mode and message leave persistent scopes out; the state carries
  `senders` (how many are connected). The browser keeps the sender decks below the start, search and error pages
  (with the zoom/VIEW bar), and does not clear them when the search stops; the server removes them with `deckGone`
  when the sender goes. The problem report has its own section "LJL Link (Alpha)" (id, address, software, titles
  allowed or not, connections without hello; no names, titles or key).
- **names: false** (also the default when the field is missing): title and artist are never stored; the deck reports
  `titleHidden: true`; the browser shows "Title hidden by the DJ". A
  `names` message with `on: false` removes the titles of all of the sender's decks at once; `on: true` brings titles
  with the next `load`.
- **Bonjour**: TXT of `_ljlink._tcp` also carries `sender=1`, `senderpath=/sender`.
- Alpha: tested with unit tests, a real Jetty in a test, a simulated sender and a development build of the sender
  against a test instance; not on Windows.

## ADR-041: Suite chat without a host (Alpha)
The lighting booth, the DJs and the VJs often have to shout across the floor ("drop in 30 s", "more fog?"). Every
program of the suite (LJ Link, LJL Link, VJ-Link and further suite programs) in the same network joins one chat room by
itself – no server, like a walkie-talkie channel. Protocol: `docs/suite-chat.md`.
- **UDP multicast** 239.255.76.76:47676, TTL 1, on every IPv4 adapter. Presence every 5 s, every message twice (with an
  id, shown once), a newcomer asks for the history and the others send their last 20 messages again. No host: whoever
  is in the network is in the room; it works without LJ Link, too.
- **Switchable** in every program. LJ Link: setting "Suite chat", on by default, kept; off, it neither sends nor reads.
- **Nickname**: asked when entering the chat. LJ Link keeps it in its settings; LJL Link only in memory (nothing is
  written to the DJ's Mac).
- **Only the LJ Link computer** reads and writes the chat in LJ Link (local API `GET/POST /api/chat`, `POST
  /api/chat/settings`, ControlGuard); a tablet does not see it.
- **Limits** (every receiver): 500 characters, nickname 24, line breaks become spaces, direction marks and control
  characters go, 10 messages per peer in 10 s, 200 messages, 50 peers, datagrams up to 8 KB. Text only, never HTML.
- **Privacy**: never logged, never in a problem report (only on/off and counts). Every device in the network can read
  along (nothing is encrypted); networks that block multicast (guest Wi-Fi with client isolation) have no chat.
- Replaces the first draft of this ADR (a chat over the LJL Link connection, switched on by the booth): that one only
  reached DJs with LJL Link and needed LJ Link as the host.
- Alpha: unit tests, a live run on one Mac with LJ Link (Java), LJL Link (Swift) and a third test program; not yet
  between two computers, not on Windows. VJ-Link and LJL Link have the chat too (same protocol, checked together with LJ
  Link in shared final QS rounds, Nov 2026).

## ADR-042: Player number assigned by a mixer, XDJ-AZ hint (issue #2, Alpha)
The reporter's diagnostics in issue #2 (1.7.9 and 1.7.12, XDJ-AZ with Pro DJ Link switched on, 2 decks) show beat-link's log
"Mixer at address 192.168.1.143 told us to use device number 3" and "Mixer confirmed device assignment."; LJ Link then
runs as player 3 and titles, BPM, waveform and ON AIR arrive. The diagnostics still said "standard numbers 1-4 not used".
Checked in the beat-link 8.0.0 sources (`VirtualCdj.claimDeviceNumber`, `handleSpecialAnnouncementPacket`):
- While claiming a number, a "will assign" packet (0x01) from a mixer is always answered with a request; the number in
  the following "assign" packet (0x03) replaces the claimed one, also when a fixed number was set with
  `setDeviceNumber`. There is no setting to decline it. So LJ Link's own choice (7-15, ADR-038) does not prevent it;
  1.7.12 still got 3 from the XDJ-AZ.
- With a fixed number the claimed number changes only through a mixer's assignment, and self-assignment without
  `setUseStandardPlayerNumber(true)` starts at 7. Therefore an own number 1-4, or one that differs from the number LJ
  Link asked for, means a mixer assigned it.
- beat-link has no getter for the assigning mixer; it only logs the line above. `LogBuffer` hands every printed line to
  listeners; `ProDjLinkSource` reads the address and number from that line (reset before each start of the virtual CDJ)
  and the name from the devices seen at that address.
- The diagnostics now say "one of the standard numbers 1-4, assigned by the mixer XDJ-AZ at … (LJ Link asked for 8);
  another player with number 3 would collide with LJ Link" (summary: a NOTE line). The app shows a grey hint above the
  decks while LJ Link has 1-4 (state field `ownNumber`, only then).
- Behaviour unchanged on purpose: data arrived exactly in the state with the assigned number; whether it arrives
  because of the assignment is not known. A real player that later claims the same number gets one defense packet
  from beat-link (`handleDeviceClaimPacket` defends during the claim, before LJ Link's conflict check can step aside).
  Not checked on hardware.
- Deck hint: for the XDJ-AZ the metadata hint is now "xdj-az" instead of "onelibrary". It only appears when the
  XDJ-AZ sends status (a track is loaded), so its Pro DJ Link mode is already on; the text therefore points to the
  stick: in the report titles arrived when the stick also had the older rekordbox database (rekordbox wrote it along
  automatically there). beat-link 8.0.0 reads only `export.pdb` for players (`CrateDigger`; `exportLibrary.db` only in
  `OpusProvider`). "BPM, time and beats still work" stays for the CDJ-3000X (seen in a user report).
- No status at all: when an XDJ-AZ announces itself as player 1-4 but none of its players sent a status packet 10 s
  after LJ Link joined (the 4-deck mode in the report: only beat packets), the app shows a grey note above the decks
  ("sends no deck data; in a user report they arrived only with Pro DJ Link switched on, Deck tab, then only 2 decks",
  link `wiki-pioneer.html#xdj-az`), state field `xdjAzNoStatus`, gone as soon as a status arrives; the summary has a
  NOTE line. Alpha, not checked on hardware.
- Diagnostics no longer call OneLibrary models "not readable": "OneLibrary model; beat-link 8.0.0 reads only the
  classic rekordbox database export.pdb on the stick", summary "OneLibrary model (titles arrive)" when they do.

## ADR-043: Approval of LJL Link without club.json (Alpha)
A DJ who arrives without the club's stick (no `club.json`, so no club key) can still send Serato decks: LJL Link finds
LJ Link by Bonjour and asks to be let in, the LJ allows it once with a click, and from then on LJ Link knows this Mac.
Specification: LJL Link `docs/protocol.md`, section "Approval without club.json". Additive, `protocol` stays 1.
- **Bonjour**: TXT of `_ljlink._tcp` also carries `pair=1` and `booth=<16 hex>`. The booth id (`SenderClub.booth()`,
  64 random bits from `SecureRandom`, setting `senderBoothId`) is not secret; LJL Link derives its pseudonym for this
  LJ Link from it. "New key" replaces it together with the club key and Bonjour sends the new TXT at once
  (`ServiceInfo.setText` on every adapter, `Bonjour.reannounce()`).
- **hello** may carry `device` (exactly 32 hex digits, upper case is taken as lower case; anything else counts as
  missing) and may leave out `key`. The decision (`SenderInbox.hello`), in this order: wrong `protocol` → `reject
  protocol`; valid key → `welcome`; `device` approved → `welcome` (name and last seen updated); 4 senders → `reject
  full`; `device` present but not approved: refused in the last 10 min → `reject denied`, more than 2 requests already
  waiting → `reject full`, otherwise `{"type":"pending"}` and a request in the browser; neither key nor device →
  `reject key`. A wrong key with a `device` takes the approval path ("without a valid key"). A valid key or an approved
  Mac at a full LJ Link still gets `reject full` (as before). Neither key nor device is checked before "full", so an
  old sender (LJL Link 0.3) with an outdated stick still hears `key` at a full LJ Link, as before.
- **Assumptions** (not fixed by the protocol): "more than 2 waiting" is read literally, so at most 3 requests wait at
  once (`MAX_WAITING = 2`, a fourth gets `full`). A Mac that asks again while its request waits replaces the older
  request (closed without a message). Waiting requests do not use the places of connections without hello (8 in all,
  2 per address), so a flood of connections cannot push them out. "Allow" when 4 senders are connected by then: the
  Mac goes into the list but gets `reject full`; it gets in on its next try. "Remove" sends `reject denied` and closes
  only connections admitted through the approval (a connection with the club key stays); the Mac is not blocked, it may
  ask again (a new request appears). Refusals are kept in memory only (at most 256), a restart forgets them.
- **While waiting**: `ping` → `pong`, any message counts for the 15-s silence rule, `bye` or a closed connection
  withdraws the request, deck messages and a second hello are ignored, the 64-KB limit before the welcome stays. 120 s
  without an answer → `reject timeout`, closed.
- **Approved Macs** (`SenderPairing`): pseudonym, name (cleaned, cut to 64 characters), first and last seen (wall
  clock), setting `senderApprovedMacs`. At most 32; a new one pushes out the one not seen for the longest time. Last
  seen is updated on every welcome and when the connection ends. "New key" (`SenderPairing.newKey`): new club key, new
  booth id, the list and the refusals emptied, every sender and every request gets `reject key`, Bonjour again.
- **Local API only** (all under `/api/sender/`, so `ControlGuard` keeps them to the browser on this computer):
  `GET /api/sender/pairing` (requests: handle, name, address, seconds left; approved Macs: a reference that is new with
  every start, name, first and last seen, connected), `POST /api/sender/allow` / `deny` (`{"id"}`; 404 when the request
  is gone), `POST /api/sender/approved/remove`, `POST /api/sender/key`. The pseudonym never leaves the server, not even
  to the local browser. Nothing of this goes over `/ws`: a tablet sees neither the request nor a name, address or the
  list.
- **Privacy**: pseudonym, name and address of a Mac on the approval path are never logged (log lines say "approval
  request waiting", "approved Mac", "refused: denied" without address), never in the problem report (the section "LJL
  Link (Alpha)" names an approved sender by its id only and adds the counts "Approved DJ Macs: N, approval requests
  waiting: M"), and its decks carry no address (tablets). The key path logs and shows the address as before.
- **Browser**: requests appear as an amber bar under the top bar on this computer ("“MacBook von Anna” (192.168.1.34)
  wants to send Serato decks with LJL Link – Alpha", seconds left, Allow / Deny); not modal. Settings: section "LJL
  Link (DJ Macs) – Alpha" with the approved Macs (name, last seen or "connected now", Remove) and "New key" with a
  confirmation that names all effects. Polled every 1.5 s from the local API, only when this browser has control.
- Alpha: unit tests (decision in every branch, limits, timeout, list, privacy, route guards on a real Jetty) and a
  simulated sender against a test instance; not yet with LJL Link 0.4 on a second Mac.

## ADR-044: Clock bar and Art-Net timecode (timecode Alpha)
- **Clock bar**: in the toolbar above the waveforms (left of the zoom), so it costs no extra height; shown wherever the
  toolbar is (deck view on the computer and on tablets, not on the mixer tab). Amber dot-matrix digits `HH:MM:SS`
  (24 h, 5 x 7 square dots painted on a canvas, `#f5a623` on `#1c1d20`, like a stage LED clock; the text stays in
  a hidden element for screen readers), small label "Time". On by default; switch "Show clock above the waveforms" in Settings → "Clock and
  timecode", saved per browser (`clock` in the browser settings, like the other display settings).
- **Same time everywhere**: the hub sends `{"type":"clock","w":wallMs,"z":utcOffsetMin,"tc":{...}}` once a second (and
  on every new timecode frame, at most every 100-ms tick). The browser keeps the largest of the last 10 "w - its own
  clock" (the least delayed sample) and shows the server's time in the server's time zone, so a tablet with a wrong
  clock or zone shows the time of the LJ Link computer. Before the first message it shows its own time.
- **Art-Net timecode** (`ArtNetTimecode`, Alpha, default off, setting `artnetTimecode`): ArtTimeCode (OpCode 0x9700)
  on UDP 6454, fields frames/seconds/minutes/hours/type at bytes 14-18; type 0 = 24, 1 = 25, 2 = 29.97 drop frame,
  3 = 30 fps. Packets with other ids, op codes, types or out-of-range values are ignored. The port is only open while
  the switch is on; `SO_REUSEADDR` and, where supported, `SO_REUSEPORT` are set so a desk program on the same computer
  that also uses them can share it (broadcast reaches both). If the port is taken anyway, the settings say so; nothing
  crashes. Switching on again retries.
- While frames arrive the bar shows `HH:MM:SS:FF` (`;` before the frames for drop frame), label "Timecode" (amber) and the
  rate ("25 fps"). The browser lets frames run on only while the frame changed between the last two messages, for at
  most 200 ms, and never steps back by up to 5 frames; a held timecode (the desk keeps sending the same frame) shows
  exactly that frame. Unicast to a port that another program on the same computer also listens on may reach only one
  of them; broadcast reaches all. 2 s without a frame: back to the time
  with a grey "no timecode".
- **Privacy**: the sender's address is not read, kept, logged or sent; the log says "Art-Net timecode received" once
  per switching on. The `clock` message carries only the frame, rate and age.
- **Local only**: `GET/POST /api/timecode` (`{"on"}` → `on`, `listening`, `receiving`, `error`) is under `/api/` and not
  public, so `ControlGuard` keeps tablets and other pages out; the switch is only shown in the browser with control.
- **Beat grid**: the detail waveform keeps a free band at top and bottom (up to 16 px, at most 16 % of the height; the
  waveform keeps about 75-80 % of the height at the usual deck size). Beats: a light line through the waveform plus
  bright ticks in the bands; bars: red, 2 px, fully opaque in the bands, 75 % through the waveform, with a dark edge and a small cap at the edges.
  The overview has no beat grid (unchanged).
- Alpha: unit tests (parser, timeout, clock message, defaults, route guard on Jetty) and a test instance fed with
  Python-generated Art-Net packets on 127.0.0.1; not yet with a real desk (grandMA3, ChamSys) or show software.

## ADR-045: Clock size, track info width and display settings on the LJ Link computer (club feedback, Alpha)
- **Clock size**: a grip at the bottom right of the clock bar (Pointer Events: mouse, pen, touch; arrow keys) sets the
  digit height from 20 to 160 px (`clockSize`, default 28; phones keep 21 px at the default). The dot pitch is a whole
  number of CSS pixels (height / 7), canvas in device pixels, so the dots stay sharp; it is capped at what fits into the
  toolbar width. The toolbar wraps, so a big clock pushes zoom and view onto a second line. Double-click / double tap
  (or Enter) on the grip: default size.
- **Track info width**: a drag line between the info area and the waveform of each deck (`infoWidth`, 0 = default of
  the mode). The CSS keeps it within 400 px (the compact width) and 60 % of the deck (`clamp()` in `--info-w`); in the
  stacked layout under 820 px there is no line. Double-click / double tap: default. Settings → "Track info": Compact
  (400 px) / Expanded (520 px, title 23 px, artist 19 px); choosing a mode drops a dragged width.
- **Titles**: title and artist wrap onto up to two lines, then "…"; the full text is in the `title` attribute. Privacy
  mode is unchanged: the element then only holds "Title hidden" (text and tooltip).
- **Saved on the computer**: the browser storage depends on the origin (127.0.0.1, localhost, port, IP) and can be
  cleared. The display settings (`zoom`, `mode`, `theme`, `view`, `keepAwake`, `intro`, `clock`, `clockSize`,
  `infoMode`, `infoWidth`) are also kept in `settings.json` under `ui` (`UiSettings`, only known keys with valid
  values). `GET /api/ui-settings` is public (tablets read it; display choices only, nothing personal) and says whether
  the browser has control; `POST` is local (`ControlGuard`, now per method). The browser with control sends every
  change (300 ms debounce) and starts with the server values; on its first start with this version it hands over its
  own settings. A tablet starts with the server values, but keys it changed itself (`ljlinkpro.ownSettings`; for old
  storage: values that differ from the defaults) stay local and win there.
- **Stay per browser**: language (`ljlinkpro.lang`; the computer's language already goes to the program icon),
  `hideLauncherHint` and other hint cards, hidden devices (`ljlinkpro.hidden`) and the link preset.
- Tablets get changed values from the computer on the next load (no live push). The start animation decides before the
  server answers, so on a new address `intro` takes effect from the second load.
- Alpha: unit tests (defaults, validation, restart, route guard on Jetty) and a test instance in headless Chrome
  (drag, reload, restart, other origin, LAN address as tablet); not yet on a real iPad.
