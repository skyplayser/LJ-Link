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
The product is called LJ Link (not BoothView, which is trademarked). Java package `dev.ljlinkpro`, config in
`~/.ljlinkpro/`. The logo (waveform bars shooting light beams) is drawn in code in `Logo.java` and mirrored in
`web/logo.svg`; `packaging/LJ-Link-PRO.icns|ico|png` are generated from it (`java dev.ljlinkpro.Logo <dir>`, then `iconutil`)
and passed to jpackage as the installer and app icon.

## ADR-012: CDJ look of the waveforms
Three-band waveforms are sent as layers and painted like the players: detail = independent bands low (blue), mid (amber),
high (white); overview = cumulative layers painted outermost first. RGB/blue tracks are sent as height plus color. Loops are
orange (saved loops dimmer, the active loop brighter), memory cues red, hot cues in their rekordbox colors with letters.
Paused players report no moving position, so it is derived from the last beat number and the beat grid.

## ADR-013: Live position channel and RGB default
Positions travel on their own compact `pos` WebSocket message (`{"type":"pos","deck":n,"p":ms,"v":speed}`), sampled every 20 ms
on the server (about 45-50 messages/s per deck, measured). The speed `v` is measured from consecutive samples, so it follows
scratching; changes faster than about 6x speed count as jumps (cue, loop) and keep the old speed. The browser extrapolates
with `v` and pulls the shown position towards the newest sample (snaps at jumps over 800 ms). The CDJ-3000 reports exact
positions about 30 times per second; NXS/NXS2 only via beats and status, so their scratching can only be followed roughly.
The server now asks beat-link for RGB waveforms (as shown on the players); the palette is an approximation of the CDJ look
(detail: beat-link's green/blue swap undone; overview: red and blue swapped; colors brightened) and still differs in hue.
Loops: only the CDJ-3000 reports the active loop; other players show saved loops from the cue list.

## ADR-014: App window, CDJ-3000 style, docs and website
The interface opens in a Chromium app window (`--app=URL`, own profile in `~/.ljlinkpro/window-profile`) when Chrome, Edge
or Brave is installed, so it feels like a normal program; otherwise it falls back to the default browser (`--no-app-window`
forces that). The deck panel follows the CDJ-3000 display (PLAYER/TRACK boxes, time with milliseconds, tempo, BPM box, MT,
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
