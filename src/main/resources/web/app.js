"use strict";

// Documentation mode (#shot) must be active before the first deck is drawn.
if (location.hash.includes("shot")) document.documentElement.classList.add("shot-early");

const MODEL_NOTES = {
  cdj3000: "Full support: title, waveform (also 3-band) and exact position.",
  nxs2: "Full support: title, waveform and position. Tracks must come from a rekordbox USB stick or SD card.",
  xdj1000: "Should work. Not tested on real hardware yet.",
  djm: "Mixer: shows ON AIR per channel. It has no waveform of its own; that comes from the players.",
  denon: "Denon devices use StageLinQ. They are found by \"Search automatically\" together with the Pioneer devices. Title, tempo, position, the fader positions and the waveform with beat grid and cues are shown (read from the device's library).",
};

const $ = (id) => document.getElementById(id);
const views = ["welcome", "search", "manual", "error", "monitor"];

let ui = { controller: true, state: { phase: "idle", devices: [], message: "" }, manualOpen: false };
let searchStartedAt = 0;
const decks = new Map(); // number -> { data, el, ... }

function show(name) {
  for (const v of views) $("view-" + v).classList.toggle("hidden", v !== name);
}

function setStatus(kind, text) {
  const s = $("status");
  s.className = "status " + kind;
  $("status-text").textContent = t(text);
}

async function post(url) {
  try {
    await fetch(url, { method: "POST" });
  } catch (e) {
    setStatus("bad", "Program not reachable");
  }
}

/* ---------- Documentation screenshots (#shot): test names and generated covers instead of real tracks ---------- */

const TEST_TITLES = ["Test Track One", "Test Track Two", "Test Track Three", "Test Track Four", "Test Track Five",
  "Test Track Six", "Test Track Seven", "Test Track Eight", "Test Track Nine", "Test Track Ten"];
const TEST_ARTISTS = ["Test Artist", "Sample Artist", "Demo Band", "Example DJ", "Placeholder Crew"];
const isShot = () => document.body.classList.contains("shot") || document.documentElement.classList.contains("shot-early");
const testTitle = (n) => TEST_TITLES[n % TEST_TITLES.length];
const testArtist = (n) => TEST_ARTISTS[n % TEST_ARTISTS.length];
const coverCache = {};

/** A small generated cover (gradient with bars), so documentation never shows real artwork. */
function testCover(n) {
  if (!coverCache[n]) {
    const c = document.createElement("canvas");
    c.width = c.height = 120;
    const g = c.getContext("2d");
    const hue = (n * 67 + 200) % 360;
    const grad = g.createLinearGradient(0, 0, 120, 120);
    grad.addColorStop(0, `hsl(${hue},80%,55%)`);
    grad.addColorStop(1, `hsl(${(hue + 60) % 360},85%,25%)`);
    g.fillStyle = grad;
    g.fillRect(0, 0, 120, 120);
    g.fillStyle = "rgba(255,255,255,0.55)";
    for (let i = 0; i < 6; i++) {
      const h = 25 + ((i * 37 + n * 23) % 70);
      g.fillRect(12 + i * 17, 108 - h, 11, h);
    }
    coverCache[n] = c.toDataURL();
  }
  return coverCache[n];
}

/* ---------- Views driven by server state ---------- */

function render() {
  const st = ui.state;
  $("btn-stop").classList.toggle("hidden", st.phase === "idle" || !ui.controller);
  $("tab-waveform").classList.toggle("hidden", st.phase !== "monitoring");
  $("tab-mixer").classList.toggle("hidden", st.phase !== "monitoring");

  const warn = $("warnings");
  warn.textContent = (st.warnings || []).map(w => t(w)).join(" · ");
  warn.classList.toggle("hidden", !(st.warnings || []).length || st.phase !== "monitoring");
  if (st.phase === "monitoring") {
    show("monitor");
    applyTab();
    setStatus("live", st.mode === "demo" ? "Demo mode" : "Connected");
    $("no-decks").classList.toggle("hidden", decks.size > 0);
    return;
  }
  if (st.phase === "searching") {
    show("search");
    setStatus("warn", "Search running");
    $("search-message").textContent = t(st.message || "");
    const searchWarn = $("search-warnings");
    searchWarn.textContent = (st.searchWarnings || []).map(w => t(w)).join(" · ");
    searchWarn.classList.toggle("hidden", !(st.searchWarnings || []).length);
    renderFound(st.devices);
    $("search-title").textContent = t(st.devices.length ? "Device found!" : "Searching …");
    $("search-help").classList.toggle("hidden", st.devices.length > 0 || Date.now() - searchStartedAt < 15000);
    return;
  }
  if (st.phase === "error") {
    show("error");
    setStatus("bad", "Error");
    $("error-message").textContent = t(st.message);
    return;
  }
  // idle
  setStatus("", "Ready");
  show(ui.manualOpen ? "manual" : "welcome");
}

function renderFound(devices) {
  const ul = $("found-list");
  ul.textContent = "";
  for (const d of devices) {
    const li = document.createElement("li");
    const strong = document.createElement("strong");
    strong.textContent = d.name;
    li.append(strong, document.createTextNode(t("No. {n} · {address}", { n: d.number, address: d.address })));
    ul.append(li);
  }
}

/* ---------- WebSocket ---------- */

let ws;
function connect() {
  ws = new WebSocket((location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/ws");
  ws.onopen = () => setStatus("", "Ready");
  ws.onmessage = (ev) => handle(JSON.parse(ev.data));
  ws.onclose = () => {
    setStatus("bad", "Connection lost – reconnecting …");
    setTimeout(connect, 1500);
  };
}

function handle(msg) {
  if (msg.type === "state") {
    if (msg.phase === "searching" && ui.state.phase !== "searching") searchStartedAt = Date.now();
    if (msg.phase !== "monitoring") clearDecks();
    ui.state = msg;
    applyPrivacy();
    render();
    applyGroups();
  } else if (msg.type === "deck") {
    updateDeck(msg);
    if (ui.state.phase === "monitoring") $("no-decks").classList.add("hidden");
  } else if (msg.type === "mixer") {
    ui.mixers = msg.mixers || [];
    if (ui.tab === "mixer") renderMixer();
  } else if (msg.type === "pos") {
    const d = decks.get(msg.deck);
    if (d) { d.pos = msg.p; d.posAt = performance.now(); d.vel = msg.v; }
  } else if (msg.type === "deckGone") {
    removeDeck(msg.deck);
  }
}

/* ---------- Settings (kept in this browser only) ---------- */

const DEFAULTS = { zoom: 12, mode: "rgb", theme: "night", view: "both" };
let settings = { ...DEFAULTS };
try {
  Object.assign(settings, JSON.parse(localStorage.getItem("ljlinkpro.settings") || "{}"));
} catch (e) { /* storage unavailable: use defaults */ }

function saveSettings() {
  try { localStorage.setItem("ljlinkpro.settings", JSON.stringify(settings)); } catch (e) { /* ignore */ }
}

let playheadColor = "#fff";
function applySettings() {
  document.documentElement.dataset.theme = settings.theme === "day" ? "day" : "night";
  playheadColor = getComputedStyle(document.documentElement).getPropertyValue("--playhead").trim() || "#fff";
  $("zoom").value = settings.zoom;
  $("zoom-label").textContent = t("– {s} s visible", { s: settings.zoom });
  $("zoom-value").textContent = settings.zoom + " s";
  $("view-value").textContent = { both: "", detail: t("· DETAIL"), overview: t("· OVERVIEW") }[settings.view] || "";
  $("decks").dataset.view = settings.view;
  for (const b of $("seg-mode").children) b.setAttribute("aria-pressed", String(b.dataset.value === settings.mode));
  for (const b of $("seg-theme").children) b.setAttribute("aria-pressed", String(b.dataset.value === settings.theme));
  for (const d of decks.values()) d.cache = null;
}

/* ---------- Light desk link (tempo out via OSC) ---------- */

// type "ma": command line sent as OSC /cmd; type "osc": plain OSC float at the given address.
const LINK_PRESETS = {
  off:      { type: "off" },
  grandma2: { type: "ma2", port: 30000, text: "SpecialMaster 3.16 At {bpm}", speed: true, label: "Command on the desk", login: true,
              user: "administrator", password: "admin",
              hint: "grandMA2 console or onPC, over Telnet. On the desk: Setup, Console, Global Settings, Telnet: Login Enabled. Default user of every show: administrator, password admin (change it if your show uses another). {bpm} is replaced by the tempo. Step by step: see the light desk guide." },
  grandma:  { type: "ma",  port: 8000, text: "Master 3.1 At BPM {bpm}", speed: true, label: "Command on the desk",
              hint: "grandMA3 console or onPC, over OSC. On the desk: Menu, In & Out, OSC: a line with this port, Mode UDP, Receive Command Yes, then Enable Input. {bpm} is replaced by the tempo. Step by step: see the light desk guide." },
  daslight: { type: "osc", port: 8000, text: "/ljlinkpro/bpm", label: "OSC address",
              hint: "Daslight receives this address as a number over OSC; map it in Daslight's OSC mapping. Whether your Daslight version can set the BPM this way is not confirmed yet, see the light desk guide." },
  osc:      { type: "osc", port: 8000, text: "/ljlinkpro/bpm", label: "OSC address",
              hint: "The tempo is sent as a number (float) to this address." },
};

/* Speed masters of the grandMA: 1 to 15, and 16, which is the BPM master. The choice is written into the command. */
const SPEED_IN_COMMAND = /((?:SpecialMaster|Master)\s+3\.)(\d+)/i;

function fillSpeedChoices() {
  const sel = $("link-speed");
  if (sel.options.length) return;
  for (let i = 1; i <= 16; i++) {
    const o = document.createElement("option");
    o.value = String(i);
    o.textContent = i === 16 ? t("Speed 16 (BPM)") : t("Speed {n}", { n: i });
    sel.append(o);
  }
}

function syncSpeedFromCommand() {
  fillSpeedChoices();
  const m = $("link-text").value.match(SPEED_IN_COMMAND);
  $("link-speed").value = m ? m[2] : "";
}

function linkPresetFromConfig(c) {
  if (c.type === "off") return "off";
  if (c.type === "osc") return ui.linkPreset === "daslight" ? "daslight" : "osc";
  if (c.type === "ma2") return "grandma2";
  return "grandma";
}

function showLinkFields(preset) {
  const p = LINK_PRESETS[preset];
  $("link-fields").classList.toggle("hidden", preset === "off");
  if (preset === "off") return;
  $("link-hint").textContent = t(p.hint);
  $("link-login").classList.toggle("hidden", !p.login);
  $("link-speed-row").classList.toggle("hidden", !p.speed);
  syncSpeedFromCommand();
  $("link-text").placeholder = t(p.label);
}

async function loadLink() {
  try {
    const res = await (await fetch("/api/link")).json();
    const c = res.config;
    try { ui.linkPreset = localStorage.getItem("ljlinkpro.linkPreset") || ""; } catch (e) { ui.linkPreset = ""; }
    const preset = linkPresetFromConfig(c);
    $("link-target").value = preset;
    $("link-host").value = c.host;
    $("link-port").value = c.port;
    $("link-text").value = c.type === "ma" || c.type === "ma2" ? c.command : c.address;
    $("link-user").value = c.user || (c.type === "ma2" ? LINK_PRESETS.grandma2.user : "");
    $("link-pass").value = c.password || (c.type === "ma2" && !c.user ? LINK_PRESETS.grandma2.password : "");
    showLinkFields(preset);
    const st = res.status;
    $("link-status").textContent = st.error ? t(st.error)
      : st.sentCount ? t("Last sent: {bpm} BPM ({n} messages)", { bpm: st.bpm == null ? "–" : st.bpm.toFixed(1), n: st.sentCount }) : "";
  } catch (e) { /* link settings are only available on the computer itself */
    $("link-setting").classList.add("hidden");
  }
}

function linkBody() {
  const preset = $("link-target").value;
  const p = LINK_PRESETS[preset];
  const text = $("link-text").value;
  return {
    type: p.type,
    host: $("link-host").value || "127.0.0.1",
    port: Number($("link-port").value) || 8000,
    address: p.type === "osc" ? text : "/ljlinkpro/bpm",
    command: p.type === "ma" || p.type === "ma2" ? text : "",
    user: p.login ? $("link-user").value : "",
    password: p.login ? $("link-pass").value : "",
  };
}

$("link-target").onchange = () => {
  const preset = $("link-target").value;
  const p = LINK_PRESETS[preset];
  try { localStorage.setItem("ljlinkpro.linkPreset", preset); } catch (e) { /* ignore */ }
  ui.linkPreset = preset;
  if (preset !== "off") {
    $("link-port").value = p.port;
    $("link-text").value = p.text;
    // A new grandMA2 show always has the user "administrator" with the password "admin".
    if (p.login && !$("link-user").value) { $("link-user").value = p.user; $("link-pass").value = p.password; }
    if (!$("link-host").value) $("link-host").value = "127.0.0.1";
  }
  showLinkFields(preset);
  if (preset === "off") saveLink();
};

async function saveLink() {
  await fetch("/api/link", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(linkBody()) });
  loadLink();
}

$("link-scan").onclick = async () => {
  const out = $("link-found");
  out.textContent = t("Searching the network … (about 10 seconds)");
  $("link-scan").disabled = true;
  try {
    const found = await (await fetch("/api/link/scan")).json();
    out.textContent = "";
    if (!found.length) {
      out.textContent = t("No desk found. Enter its IP address by hand: on the desk, OSC input must be switched on (Setup, In & Out, OSC).");
      return;
    }
    for (const f of found) {
      const b = document.createElement("button");
      b.className = "secondary";
      b.textContent = f.name;
      b.onclick = () => {
        // The search finds a grandMA2 by its Telnet port, so it also chooses the grandMA2 connection.
        if ($("link-target").value !== "grandma2") { $("link-target").value = "grandma2"; $("link-target").onchange(); }
        $("link-host").value = f.host;
        out.textContent = t("Address entered. Enter the user and press Save.");
      };
      out.append(b, " ");
    }
  } catch (e) {
    out.textContent = t("The search failed.");
  } finally {
    $("link-scan").disabled = false;
  }
};
$("link-speed").onchange = () => {
  const n = $("link-speed").value;
  const cmd = $("link-text").value;
  $("link-text").value = SPEED_IN_COMMAND.test(cmd) ? cmd.replace(SPEED_IN_COMMAND, "$1" + n) : cmd;
};
$("link-text").addEventListener("input", syncSpeedFromCommand);
$("link-save").onclick = saveLink;
$("link-test").onclick = async () => {
  await saveLink();
  await fetch("/api/link/test", { method: "POST" });
  setTimeout(loadLink, 400);
};

/* ---------- Decks ---------- */

const FRAMES_PER_SECOND = 150; // detail waveform resolution of rekordbox
const PLAYHEAD_AT = 0.25;      // the playhead sits a quarter in, so more of the future is visible
const OVERVIEW_TOP = 14;       // CSS px reserved above the overview for cue markers

function two(n) { return String(n).padStart(2, "0"); }

/** 01:05.5 */
function fmtPrecise(ms) {
  if (ms == null || ms < 0 || !isFinite(ms)) return ["--:--", ".---"];
  const t = Math.floor(ms);
  const sec = Math.floor(t / 1000);
  return [two(Math.floor(sec / 60)) + ":" + two(sec % 60), "." + String(t % 1000).padStart(3, "0")];
}

/** -03:19 and .725 for the remaining time under the elapsed time */
function fmtRemainingPrecise(ms) {
  if (ms == null || ms < 0 || !isFinite(ms)) return ["", ""];
  const t = Math.floor(ms);
  const sec = Math.floor(t / 1000);
  return ["-" + two(Math.floor(sec / 60)) + ":" + two(sec % 60), "." + String(t % 1000).padStart(3, "0")];
}

/** -05:41 */
function fmtRemaining(ms) {
  if (ms == null || ms < 0 || !isFinite(ms)) return "";
  const s = Math.floor(ms / 1000);
  return "-" + two(Math.floor(s / 60)) + ":" + two(s % 60);
}

function createDeck(n) {
  const el = document.createElement("article");
  el.className = "deck";
  el.tabIndex = 0;
  el.setAttribute("role", "button");
  el.dataset.deck = n;
  el.addEventListener("keydown", (e) => { if (e.key === "Enter") openInfo(n); });
  el.innerHTML = `
    <span class="live-tag">LIVE</span>
    <div class="strip"></div>
    <div class="deck-side">
      <div class="chan">
        <div class="fader binary" title=""><i></i></div>
        <span class="chip send hidden">SEND BPM</span>
        <div class="knobs hidden">
          <span class="knob" data-eq="hi">HI<span class="dial"></span></span>
          <span class="knob" data-eq="mid">MID<span class="dial"></span></span>
          <span class="knob" data-eq="low">LOW<span class="dial"></span></span>
        </div>
      </div>
      <div class="deck-top">
        <div class="box player"><small>PLAYER</small><b class="deck-num"></b></div>
        <div class="box"><small>TRACK</small><b class="track-no">--</b></div>
        <div class="art"><img alt="" hidden></div>
        <div class="deck-info"><div class="deck-title"></div><div class="deck-artist"></div></div>
      </div>
      <div class="deck-mid">
        <div class="time-block">
          <div class="time-main"><span class="elapsed"></span><span class="elapsed-ms"></span></div>
          <div class="remain-main"><small>REMAIN</small><span class="remain-val"></span><span class="remain-ms"></span></div>
        </div>
        <div class="tempo-col">
          <div class="tempo-line">TEMPO<b class="pitch"></b></div>
          <div class="box bpm-box"><small>BPM</small><b class="bpm-val">--</b></div>
        </div>
      </div>
      <div class="deck-low"><span class="chips"></span><span class="key-box"></span><span class="dev-name"></span></div>
    </div>
    <div class="deck-waves">
      <div class="wave detail"><canvas class="c-detail"></canvas><div class="wave-msg"></div></div>
      <div class="over-row"><div class="wave overview"><canvas class="c-over"></canvas></div><div class="remaining"></div></div>
    </div>`;
  const d = {
    n, el, data: null, pos: -1, posAt: 0,
    wave: null, waveVersion: -1, waveLoading: false, cache: null,
    detail: null, detailVersion: -1, detailLoading: false, artVersion: -1,
    shown: {}, beatT: null, beatB: null, beatVersion: -1, beatLoading: false,
    q: (sel) => el.querySelector(sel),
  };
  d.cDetail = d.q(".c-detail");
  d.cOver = d.q(".c-over");
  decks.set(n, d);
  return d;
}

/** Keeps the decks ordered: device by device in the order they were found, then by the device's own number. */
function sortDecks() {
  const container = $("decks");
  const ordered = [...decks.values()].sort((a, b) => ((a.data && a.data.order) || 0) - ((b.data && b.data.order) || 0) || a.n - b.n);
  ordered.forEach((d, i) => { if (container.children[i] !== d.el) container.insertBefore(d.el, container.children[i] || null); });
}

/* ---------- Devices: which ones are shown ---------- */

const hiddenGroups = new Set();
try { for (const g of JSON.parse(localStorage.getItem("ljlinkpro.hidden") || "[]")) hiddenGroups.add(g); } catch (e) { /* ignore */ }

function saveHidden() {
  try { localStorage.setItem("ljlinkpro.hidden", JSON.stringify([...hiddenGroups])); } catch (e) { /* ignore */ }
}

/** Hides the decks of unticked devices and keeps the list in the settings current. */
function applyGroups() {
  const groups = ui.state.groups || [];
  for (const d of decks.values()) {
    d.el.classList.toggle("hidden", !!(d.data && hiddenGroups.has(d.data.group)));
  }
  const names = d => d.data && d.data.groupName;
  const multi = groups.length > 1;
  for (const d of decks.values()) {
    const label = d.q(".dev-name");
    const text = d.data ? names(d) : "";
    if (d.shown.dev !== text) { d.shown.dev = text; label.textContent = text; }
  }
  $("devices-setting").classList.toggle("hidden", groups.length < 2 && !groups.some(g => hiddenGroups.has(g.id)));
  const list = $("device-list");
  const key = groups.map(g => g.id + "|" + g.name).join(";");
  if (list.dataset.key === key) {
    // Same devices: only bring the ticks up to date, so the list is not rebuilt under the user's finger or keyboard focus.
    list.querySelectorAll("input").forEach((box, i) => { box.checked = !hiddenGroups.has(groups[i].id); });
    return;
  }
  list.dataset.key = key;
  list.textContent = "";
  for (const g of groups) {
    const label = document.createElement("label");
    label.className = "check";
    const box = document.createElement("input");
    box.type = "checkbox";
    box.checked = !hiddenGroups.has(g.id);
    box.onchange = () => { if (box.checked) hiddenGroups.delete(g.id); else hiddenGroups.add(g.id); saveHidden(); applyGroups(); };
    label.append(box, " ", document.createTextNode(isShot() ? g.name.replace(/\d{4,}/g, "") : g.name));
    list.append(label);
  }
  sortDecks();
}

function removeDeck(n) {
  const d = decks.get(n);
  if (d) { d.el.remove(); decks.delete(n); }
}

function clearDecks() {
  for (const n of [...decks.keys()]) removeDeck(n);
}

/** Writes text only when it changed, so the 60 fps loop does not touch the DOM needlessly. */
function setText(d, key, node, text) {
  if (d.shown[key] !== text) { d.shown[key] = text; node.textContent = text; }
}

function updateDeck(m) {
  const isNew = !decks.has(m.deck);
  const d = decks.get(m.deck) || createDeck(m.deck);
  d.data = m;
  setText(d, "num", d.q(".deck-num"), m.label);
  setText(d, "dev", d.q(".dev-name"), m.groupName || "");
  d.q(".chip.send").classList.toggle("hidden", !m.sendingBpm);
  setText(d, "kind", d.q(".box.player small"), m.kind === "denon" || m.kind === "serato" ? "DECK" : m.kind === "link" ? "LINK" : "PLAYER");
  if (isNew || d.order !== m.order) { d.order = m.order; sortDecks(); }
  d.el.classList.toggle("hidden", hiddenGroups.has(m.group));
  d.el.classList.toggle("onair", !!m.onAir);
  d.el.classList.toggle("playing", !!m.playing);
  d.el.classList.toggle("master", !!m.master);
  const shownTitle = isShot() && m.title ? testTitle(m.deck) : m.title;
  const shownArtist = isShot() && m.artist ? testArtist(m.deck) : m.artist;
  d.q(".deck-title").textContent = t(m.hasTrack ? (ui.state.privacy ? "Title hidden" : (shownTitle || "Loading track …")) : "No track loaded");
  d.q(".deck-artist").textContent = m.hasTrack && !ui.state.privacy ? (shownArtist || "") : "";
  d.q(".remain-main small").textContent = t("REMAIN");
  d.q(".bpm-val").textContent = m.hasTrack && m.effectiveBpm ? m.effectiveBpm.toFixed(1) : "--";
  d.q(".track-no").textContent = m.hasTrack && m.trackNumber ? two(m.trackNumber) : "--";
  d.q(".pitch").textContent = m.hasTrack ? (m.pitch >= 0 ? "+" : "") + m.pitch.toFixed(2) + "%" : "";
  d.q(".key-box").innerHTML = m.hasTrack && m.key ? "<small>KEY</small>" + m.key.replace(/[<&]/g, "") : "";
  const chips = d.q(".chips");
  chips.textContent = "";
  const addChip = (text, cls) => { const c = document.createElement("span"); c.className = "chip " + cls; c.textContent = text; chips.append(c, " "); };
  if (m.master) addChip("MT", "mt");
  if (m.synced) addChip("SYNC", "sync");
  if (m.hasTrack && !m.playing) addChip("PAUSE", "pause");

  const fader = d.q(".fader");
  const level = m.fader != null ? m.fader : (m.onAir ? 1 : 0);
  fader.classList.toggle("binary", m.fader == null);
  fader.firstElementChild.style.width = Math.round(level * 100) + "%";
  fader.title = m.fader != null ? t("Channel fader {pct} %", { pct: Math.round(m.fader * 100) })
    : t(m.onAir ? "Channel is on air (Pro DJ Link does not transmit the fader position)" : "Channel not on air");
  const knobs = d.q(".knobs");
  knobs.classList.toggle("hidden", !m.eq);
  if (m.eq) {
    for (const k of knobs.querySelectorAll(".knob")) {
      k.querySelector(".dial").style.setProperty("--angle", Math.round(m.eq[k.dataset.eq] * 135) + "deg");
    }
  }

  const img = d.q(".art img");
  if (m.hasArt) {
    if (d.artVersion !== m.artVersion) {
      d.artVersion = m.artVersion;
      img.src = isShot() ? testCover(d.n) : "/api/deck/" + d.n + "/art?v=" + m.artVersion;
    }
    img.hidden = false;
  } else { img.hidden = true; d.artVersion = -1; }

  const msg = d.q(".wave-msg");
  if (!m.hasTrack) msg.textContent = "";
  else if (m.hasDetail || m.hasWaveform) msg.textContent = "";
  else if (m.noWaveform) msg.textContent = t("No waveform found for this track");
  else msg.textContent = t("Waveform loading … (not available for streaming tracks)");

  if (m.hasWaveform && d.waveVersion !== m.waveformVersion) loadData(d, "waveform", m.waveformVersion);
  if (!m.hasWaveform) { d.wave = null; d.waveVersion = -1; d.cache = null; }
  if (m.hasBeats && d.beatVersion !== m.beatVersion) loadBeats(d, m.beatVersion);
  if (!m.hasBeats) { d.beatT = null; d.beatB = null; d.beatVersion = -1; }
  if (m.hasDetail && d.detailVersion !== m.detailVersion) loadData(d, "detail", m.detailVersion);
  if (!m.hasDetail) { d.detail = null; d.detailVersion = -1; }
}

async function loadBeats(d, version) {
  if (d.beatLoading) return;
  d.beatLoading = true;
  try {
    const r = await fetch("/api/deck/" + d.n + "/beats?v=" + version);
    if (r.ok) {
      const j = await r.json();
      d.beatT = Int32Array.from(j.t);
      d.beatB = Uint8Array.from(j.b);
      d.beatVersion = version;
    }
  } catch (e) { /* retried on the next deck update */ }
  d.beatLoading = false;
}

async function loadData(d, kind, version) {
  const loadingKey = kind === "detail" ? "detailLoading" : "waveLoading";
  if (d[loadingKey]) return;
  d[loadingKey] = true;
  try {
    const r = await fetch("/api/deck/" + d.n + "/" + kind + "?v=" + version);
    if (r.ok) {
      const bytes = new Uint8Array(await r.arrayBuffer());
      if (kind === "detail") { d.detail = bytes; d.detailVersion = version; }
      else { d.wave = bytes; d.waveVersion = version; d.cache = null; }
    }
  } catch (e) { /* retried on the next deck update */ }
  d[loadingKey] = false;
}

/* ---------- Drawing (60 fps) ---------- */

// Colors as on the CDJs: three-band layers (low blue, mid amber, high white), orange loops, red memory cues.
const BAND = { low: "rgb(32,83,217)", mid: "rgb(242,170,60)", high: "rgb(255,255,255)" };
const BAND_BLUE = { low: "rgb(30,80,208)", mid: "rgb(74,138,255)", high: "rgb(207,230,255)" };
const LOOP_FILL = "rgba(255,140,0,0.38)";
const LOOP_ACTIVE_FILL = "rgba(255,150,0,0.62)";
const LOOP_EDGE = "#ff8c00";
const MEMORY_COLOR = "#ff2a2a";

/**
 * Position shown on screen. The server sends measured positions and speeds up to 50 times per second; between and
 * after them the position keeps moving with that speed and is pulled gently towards the newest sample, so
 * the playhead stays smooth even while scratching. Big jumps (cue, loop) snap at once.
 */
function advance(d, now) {
  if (d.pos < 0) { d.smooth = null; d.cur = -1; return -1; }
  const v = d.vel || 0;
  const target = d.pos + (now - d.posAt) * v;
  if (d.smooth == null || Math.abs(target - d.smooth) > 800) {
    d.smooth = target;
  } else {
    const dt = Math.min(100, now - (d.smoothAt || now));
    d.smooth += v * dt;
    // Small deviations are noise of the player's position reports: ignore them, correct bigger ones gently.
    const err = target - d.smooth;
    // Players without exact position reports (NXS2) are off by up to 0.2 s between beats; their speed is exact, so trust it.
    const coarse = d.data && d.data.coarse;
    const dead = coarse ? 220 : 25;
    if (Math.abs(err) > dead) d.smooth += (err - Math.sign(err) * dead) * (coarse ? 0.03 : 0.06);
  }
  d.smoothAt = now;
  const dur = d.data ? d.data.durationMs : 0;
  d.smooth = Math.max(0, dur ? Math.min(d.smooth, dur) : d.smooth);
  d.cur = d.smooth;
  return d.cur;
}

function currentPos(d) {
  return d.cur == null ? -1 : d.cur;
}

/** Bar color for a "color" style entry: RGB as sent by the player, or the classic blue look from the height. */
function barColor(arr, i) {
  if (settings.mode === "blue") {
    const t = arr[i] / 255;
    return `rgb(${Math.round(30 + t * 150)},${Math.round(70 + t * 160)},${Math.round(200 + t * 55)})`;
  }
  return `rgb(${arr[i + 1]},${arr[i + 2]},${arr[i + 3]})`;
}

function bandColors() {
  return settings.mode === "blue" ? BAND_BLUE : BAND;
}

/** Canvas sizes are measured by an observer, not in the frame loop: reading the layout after writing the clock texts forced a reflow per deck and frame. */
const sizeObserver = new ResizeObserver((entries) => {
  for (const e of entries) { e.target._cw = e.target.clientWidth; e.target._ch = e.target.clientHeight; }
});

function fitCanvas(c) {
  if (c._cw == null) { c._cw = c.clientWidth; c._ch = c.clientHeight; sizeObserver.observe(c); }
  const dpr = window.devicePixelRatio || 1;
  const w = Math.max(1, Math.round(c._cw * dpr));
  const h = Math.max(1, Math.round(c._ch * dpr));
  if (c.width !== w || c.height !== h) { c.width = w; c.height = h; }
  return { w, h, dpr, ctx: c.getContext("2d") };
}

/** Hot cues: colored flag with a letter A-H. Memory cues: small red flag. */
function drawCueTag(ctx, x, y, cue, size, dpr) {
  if (cue.hot > 0) {
    ctx.fillStyle = cue.color || "#ffffff";
    ctx.fillRect(x - size / 2, y, size, size);
    ctx.fillStyle = "#000";
    ctx.font = "bold " + Math.round(size * 0.78) + "px system-ui, sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(String.fromCharCode(64 + cue.hot), x, y + size / 2 + dpr * 0.5);
  } else {
    ctx.fillStyle = MEMORY_COLOR;
    ctx.beginPath();
    ctx.moveTo(x - size / 2, y);
    ctx.lineTo(x + size / 2, y);
    ctx.lineTo(x, y + size * 0.8);
    ctx.closePath();
    ctx.fill();
  }
}

/** All loops of the track: saved loops plus the one that is active right now. */
function loopsOf(d) {
  const out = [];
  for (const cue of (d.data.cues || [])) {
    if (cue.loop && cue.loopEnd > cue.t) out.push({ start: cue.t, end: cue.loopEnd, active: false });
  }
  if (d.data.loop) out.push({ start: d.data.loop[0], end: d.data.loop[1], active: true });
  return out;
}

/** Binary search: index of the first beat at or after the given time. */
function firstBeatAtOrAfter(times, t) {
  let lo = 0, hi = times.length;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (times[mid] < t) lo = mid + 1; else hi = mid;
  }
  return lo;
}

/** Scrolling detail waveform with the playhead a quarter in; the part already played is dimmed. */
function drawDetail(d, pos) {
  const { w, h, dpr, ctx } = fitCanvas(d.cDetail);
  ctx.clearRect(0, 0, w, h);
  if (!d.detail || pos < 0) return;
  const n = d.detail.length / 4;
  const layered = d.data.detailStyle === "threeband";
  const bands = bandColors();
  const center = (pos / 1000) * FRAMES_PER_SECOND;
  const framesPerPx = (settings.zoom * FRAMES_PER_SECOND) / w;
  const msPerPx = framesPerPx * 1000 / FRAMES_PER_SECOND;
  const headX = Math.round(w * PLAYHEAD_AT);
  const mid = h / 2;
  const timeAt = (x) => pos + (x - headX) * msPerPx;
  const xAt = (t) => headX + (t - pos) / msPerPx;

  for (let x = 0; x < w; x++) {
    const start = center + (x - headX) * framesPerPx;
    const from = Math.max(0, Math.floor(start));
    const to = Math.min(n - 1, Math.max(from, Math.floor(start + framesPerPx - 1e-6)));
    if (to < 0 || from >= n) continue;
    ctx.globalAlpha = x < headX ? 0.5 : 1;
    if (layered) {
      let lo = 0, mi = 0, hi = 0;
      for (let f = from; f <= to; f++) {
        if (d.detail[f * 4] > lo) lo = d.detail[f * 4];
        if (d.detail[f * 4 + 1] > mi) mi = d.detail[f * 4 + 1];
        if (d.detail[f * 4 + 2] > hi) hi = d.detail[f * 4 + 2];
      }
      for (const [v, color] of [[lo, bands.low], [mi, bands.mid], [hi, bands.high]]) {
        const bar = (v / 255) * (mid - 2);
        ctx.fillStyle = color;
        ctx.fillRect(x, mid - bar, 1, Math.max(1, bar * 2));
      }
    } else {
      let best = from, bestH = -1;
      for (let f = from; f <= to; f++) {
        const hv = d.detail[f * 4];
        if (hv > bestH) { bestH = hv; best = f; }
      }
      const bar = (bestH / 255) * (mid - 2);
      ctx.fillStyle = barColor(d.detail, best * 4);
      ctx.fillRect(x, mid - bar, 1, Math.max(1, bar * 2));
    }
  }
  ctx.globalAlpha = 1;

  // Loops: the waveform inside a loop turns orange, the active loop a bit brighter (as on the CDJ).
  ctx.globalCompositeOperation = "source-atop";
  for (const loop of loopsOf(d)) {
    const x1 = Math.max(0, xAt(loop.start)), x2 = Math.min(w, xAt(loop.end));
    if (x2 <= x1) continue;
    ctx.fillStyle = loop.active ? "rgba(255,150,0,0.92)" : "rgba(255,140,0,0.72)";
    ctx.fillRect(x1, 0, x2 - x1, h);
  }
  ctx.globalCompositeOperation = "source-over";
  for (const loop of loopsOf(d)) {
    const x1 = Math.max(0, xAt(loop.start)), x2 = Math.min(w, xAt(loop.end));
    if (x2 <= x1) continue;
    ctx.fillStyle = LOOP_EDGE;
    ctx.fillRect(x1, 0, x2 - x1, Math.max(2, 2 * dpr));
    ctx.fillRect(x1, h - Math.max(2, 2 * dpr), x2 - x1, Math.max(2, 2 * dpr));
  }

  // Beat grid: short ticks at top and bottom, longer on the first beat of a bar.
  if (d.beatT && d.beatT.length) {
    const barPx = (60000 / ((d.data.trackBpm || d.data.effectiveBpm || 120))) * 4 / msPerPx;
    const beatPx = barPx / 4;
    if (barPx >= 4) {
      let i = firstBeatAtOrAfter(d.beatT, timeAt(0));
      for (; i < d.beatT.length; i++) {
        const x = Math.round(xAt(d.beatT[i]));
        if (x > w) break;
        const down = d.beatB[i] === 1;
        if (!down && beatPx < 4) continue;
        ctx.fillStyle = down ? "#ff3b30" : "rgba(255,255,255,0.6)";
        const len = (down ? 14 : 7) * dpr;
        ctx.fillRect(x, 0, Math.max(1, dpr), len);
        ctx.fillRect(x, h - len, Math.max(1, dpr), len);
      }
    }
  }

  for (const cue of (d.data.cues || [])) {
    const x = xAt(cue.t);
    if (x < 0 || x > w) continue;
    ctx.fillStyle = cue.hot > 0 ? (cue.color || "#ffffff") : MEMORY_COLOR;
    ctx.fillRect(Math.round(x), 0, Math.max(1, dpr), h);
    drawCueTag(ctx, Math.round(x) + 8 * dpr, 2 * dpr, cue, 12 * dpr, dpr);
  }

  ctx.fillStyle = playheadColor;
  ctx.fillRect(headX - Math.floor(dpr / 2), 0, Math.max(1, dpr), h);

  if (d.data.loop && d.data.loopBeats > 0) {
    ctx.fillStyle = LOOP_EDGE;
    ctx.font = "bold " + Math.round(14 * dpr) + "px system-ui, sans-serif";
    ctx.textAlign = "left";
    ctx.textBaseline = "top";
    ctx.fillText("\u21BB " + d.data.loopBeats, headX + 6 * dpr, 4 * dpr);
  }
}

/** Whole-track overview with loops and cue markers on top; the part already played is dimmed. */
function drawOverview(d, pos) {
  const { w, h, dpr, ctx } = fitCanvas(d.cOver);
  ctx.clearRect(0, 0, w, h);
  if (!d.wave) return;
  const top = Math.round(OVERVIEW_TOP * dpr);
  const wh = Math.max(2, h - top);
  const layered = d.data.previewStyle === "threeband";
  if (!d.cache || d.cache.width !== w || d.cache.height !== h || d.cache.mode !== settings.mode) {
    const off = document.createElement("canvas");
    off.width = w; off.height = h;
    off.mode = settings.mode;
    const o = off.getContext("2d");
    const n = d.wave.length / 4;
    const bands = bandColors();
    const half = wh / 2 - 1;
    for (let x = 0; x < w; x++) {
      const i = Math.min(n - 1, Math.floor((x / w) * n)) * 4;
      if (layered) {
        // Cumulative heights, painted from the outermost (everything) to the innermost (low).
        for (const [v, color] of [[d.wave[i + 2], bands.high], [d.wave[i + 1], bands.mid], [d.wave[i], bands.low]]) {
          const bar = (v / 255) * half;
          o.fillStyle = color;
          o.fillRect(x, top + wh / 2 - bar, 1, Math.max(1, bar * 2));
        }
      } else {
        const bar = (d.wave[i] / 255) * half;
        o.fillStyle = barColor(d.wave, i);
        o.fillRect(x, top + wh / 2 - bar, 1, Math.max(1, bar * 2));
      }
    }
    d.cache = off;
  }
  const dur = d.data.durationMs;
  const px = dur > 0 && pos >= 0 ? Math.round((pos / dur) * w) : 0;
  ctx.globalAlpha = 0.5;
  ctx.drawImage(d.cache, 0, 0, px, h, 0, 0, px, h);
  ctx.globalAlpha = 1;
  ctx.drawImage(d.cache, px, 0, w - px, h, px, 0, w - px, h);
  if (dur > 0) {
    ctx.globalCompositeOperation = "source-atop";
    for (const loop of loopsOf(d)) {
      const x1 = (loop.start / dur) * w, x2 = (loop.end / dur) * w;
      ctx.fillStyle = loop.active ? "rgba(255,150,0,0.92)" : "rgba(255,140,0,0.78)";
      ctx.fillRect(x1, top, Math.max(2 * dpr, x2 - x1), wh);
    }
    ctx.globalCompositeOperation = "source-over";
    for (const cue of (d.data.cues || [])) {
      drawCueTag(ctx, Math.round((cue.t / dur) * w), 1, cue, Math.round(11 * dpr), dpr);
    }
  }
  if (dur > 0 && pos >= 0) {
    ctx.fillStyle = playheadColor;
    ctx.fillRect(px, top, Math.max(1, dpr), wh);
  }
}

function frame(now) {
  try {
    if (ui.state.phase === "monitoring") {
      for (const d of decks.values()) {
        if (d.el.classList.contains("hidden")) continue; // a hidden device costs nothing
        const pos = advance(d, now);
        const dur = d.data ? d.data.durationMs : 0;
        const has = d.data && d.data.hasTrack;
        const [mm, ms] = has ? fmtPrecise(pos) : ["", ""];
      setText(d, "elapsed", d.q(".elapsed"), mm);
      setText(d, "elapsedMs", d.q(".elapsed-ms"), ms);
      const [rm, rms] = has && dur > 0 && pos >= 0 ? fmtRemainingPrecise(dur - pos) : ["", ""];
      setText(d, "remainVal", d.q(".remain-val"), rm);
      setText(d, "remainMs", d.q(".remain-ms"), rms);
        setText(d, "remaining", d.q(".remaining"), has && dur > 0 && pos >= 0 ? fmtRemaining(dur - pos) : "");
        if (settings.view !== "overview") drawDetail(d, pos);
        if (settings.view !== "detail") drawOverview(d, pos);
      }
    }
  } catch (e) {
    // One bad frame must never stop the animation loop.
    console.error(e);
  }
  requestAnimationFrame(frame);
}

/* ---------- Privacy mode (read-only here; switched from the program icon) ---------- */

function applyPrivacy() {
  const on = !!ui.state.privacy;
  document.body.classList.toggle("privacy", on);
  $("privacy-badge").classList.toggle("hidden", !on);
  $("privacy-box").checked = on;
  $("privacy-text").textContent = t(on ? "On – title, artist, album and cover are hidden" : "Off");
}

/* ---------- Tabs and mixer view ---------- */

ui.tab = "waveform";
ui.mixers = [];

function knobEl(label, value) {
  const k = document.createElement("div");
  k.className = "knob-big" + (value == null ? " off" : "");
  const dial = document.createElement("span");
  dial.className = "dial";
  if (value != null) dial.style.setProperty("--angle", Math.round(value * 135) + "deg");
  const text = document.createElement("span");
  text.textContent = label;
  k.append(dial, text);
  return k;
}

function renderMixer() {
  const list = $("mixer-list");
  list.textContent = "";
  const all = (ui.mixers || []).filter(m => !(m.channels && m.channels.every(c => !c.deck) && !m.present));
  const shown = all.filter(m => !m.channels || !m.channels.some(c => c.deck) || m.channels.some(c => c.deck && !hiddenGroups.has(groupOfDeck(c.number))));
  if (!shown.length) {
    const sec = document.createElement("section");
    const head = document.createElement("div");
    head.className = "mixer-head";
    const h = document.createElement("h2");
    h.textContent = t("No mixer found");
    head.append(h);
    const note = document.createElement("div");
    note.className = "note";
    note.textContent = t("The mixer appears on the network as soon as it is connected to the switch with a cable.");
    sec.append(head, note);
    list.append(sec);
    return;
  }
  for (const m of shown) list.append(mixerSection(m));
}

function groupOfDeck(number) {
  const d = decks.get(number);
  return d && d.data ? d.data.group : null;
}

function mixerSection(m) {
  const sec = document.createElement("section");
  sec.className = "mixer-block";
  const head = document.createElement("div");
  head.className = "mixer-head";
  const h = document.createElement("h2");
  h.textContent = m.present ? m.name : t("No mixer found");
  head.append(h);
  if (m.present) {
    const meta = document.createElement("span");
    meta.className = "meta";
    const hideAddress = isShot();
    meta.textContent = (m.kind === "pioneer" ? t("No. {n}", { n: m.number }) : "") +
      (m.address && m.address !== "demo" && !hideAddress ? (m.kind === "pioneer" ? " · " : "") + m.address : "") +
      (m.bpm ? " · " + m.bpm.toFixed(1) + " BPM" : "") + (m.master ? " · " + t("Tempo master") : "");
    head.append(meta);
  }
  if (m.simulated) {
    const sim = document.createElement("span");
    sim.className = "sim";
    sim.textContent = t("SIMULATED");
    head.append(sim);
  }
  const note = document.createElement("div");
  note.className = "note";
  if (!m.present) {
    note.textContent = t("The mixer appears on the network as soon as it is connected to the switch with a cable.");
  } else if (!m.levels) {
    note.textContent = t("Pro DJ Link only transmits whether a channel is on air (ON AIR). The position of the faders and the EQ knobs are not sent and cannot be shown here.");
  } else if (m.simulated) {
    note.textContent = t("Demo mode: faders and knobs are simulated.");
  } else {
    note.textContent = t("This device reports the fader positions. The EQ knobs are not transmitted.");
  }
  const strips = document.createElement("div");
  strips.className = "mixer-strips";
  for (const c of m.channels) {
    const el = document.createElement("div");
    el.className = "mix-ch" + (c.onAir ? " onair" : "");
    const title = document.createElement("h3");
    title.textContent = "CH " + c.ch;
    const name = document.createElement("div");
    name.className = "deck-name";
    name.textContent = c.deck ? (c.title ? (isShot() ? testTitle(c.number) : c.title) : (m.kind === "denon" ? "Deck " : "Player ") + c.ch) : t("no player");
    const track = document.createElement("div");
    const level = c.fader != null ? c.fader : (c.onAir ? 1 : 0);
    track.className = "fader-track" + (c.fader == null ? " binary" : "");
    const fill = document.createElement("i");
    fill.style.height = Math.round(level * 100) + "%";
    track.append(fill);
    const knobs = document.createElement("div");
    knobs.className = "knobs-big";
    knobs.append(knobEl("HI", c.eq ? c.eq.hi : null), knobEl("MID", c.eq ? c.eq.mid : null), knobEl("LOW", c.eq ? c.eq.low : null));
    const state = document.createElement("span");
    state.className = "state";
    state.textContent = c.onAir ? "ON AIR" : "OFF";
    el.append(title, name, track, knobs, state);
    strips.append(el);
  }
  sec.append(head, note, strips);
  return sec;
}

function applyTab() {
  const monitoring = ui.state.phase === "monitoring";
  const mixer = monitoring && ui.tab === "mixer";
  $("decks").classList.toggle("hidden", mixer);
  $("no-decks").classList.toggle("hidden", mixer || decks.size > 0);
  $("mixer-view").classList.toggle("hidden", !mixer);
  $("toolbar").classList.toggle("hidden", !monitoring || mixer);
  $("tab-waveform").classList.toggle("active", !mixer);
  // Without devices there is nothing to show in these two tabs.
  $("tab-waveform").classList.toggle("hidden", !monitoring);
  $("tab-mixer").classList.toggle("hidden", !monitoring);
  $("tab-mixer").classList.toggle("active", mixer);
  if (mixer) renderMixer();
}

/* ---------- Detail view of one player ---------- */

let infoDeck = null;
let infoTimer = null;

const SENSITIVE_ROWS = new Set(["IP address", "MAC address", "Packet"]);

function renderInfo(sections) {
  const shot = isShot();
  const body = $("info-body");
  body.textContent = "";
  if (!sections.length) {
    const p = document.createElement("p");
    p.className = "hint";
    p.textContent = t("No data available.");
    body.append(p);
    return;
  }
  for (const sec of sections) {
    const wrap = document.createElement("section");
    wrap.className = "info-section";
    const h = document.createElement("h3");
    h.textContent = t(sec.title);
    const grid = document.createElement("div");
    grid.className = "info-rows";
    for (const rawRow of sec.rows) {
      let r = shot && SENSITIVE_ROWS.has(rawRow.k) ? { k: rawRow.k, v: "(hidden for the docs)" } : rawRow;
      if (shot && infoDeck != null) {
        const test = { "Title": testTitle(infoDeck), "Artist": testArtist(infoDeck), "Album": "Test Album", "Genre": "Test Genre",
          "Label": "Test Label", "Remixer": "–", "Original artist": "–", "Comment": "–" }[r.k];
        if (test !== undefined && r.v && r.v !== "–" && r.v !== "null") r = { k: r.k, v: test };
        else if (/^(Hot cue|Memory point)/.test(r.k)) r = { k: r.k, v: r.v.replace(/\s+".*"$/, "") };
      }
      if (r.v.includes("\n")) {
        const label = document.createElement("div");
        label.className = "k";
        label.textContent = t(r.k);
        grid.append(label, document.createElement("div"));
        const pre = document.createElement("pre");
        pre.textContent = r.v;
        grid.append(pre);
      } else {
        const k = document.createElement("div");
        k.className = "k";
        k.textContent = t(r.k);
        const v = document.createElement("div");
        v.className = "v";
        v.textContent = t(r.v);
        grid.append(k, v);
      }
    }
    wrap.append(h, grid);
    body.append(wrap);
  }
}

async function refreshInfo() {
  if (infoDeck == null) return;
  try {
    const sections = await (await fetch("/api/deck/" + infoDeck + "/info")).json();
    if (infoDeck == null) return;
    // Keep the scroll position while the values update.
    const sheet = document.querySelector(".info-sheet");
    const top = sheet.scrollTop;
    renderInfo(sections);
    sheet.scrollTop = top;
    $("info-live").textContent = "live · " + new Date().toLocaleTimeString();
  } catch (e) {
    $("info-live").textContent = t("no connection");
  }
}

function openInfo(n) {
  infoDeck = n;
  const d = decks.get(n);
  $("info-title").textContent = "Player " + n + (d && d.data && d.data.name ? " – " + d.data.name : "");
  $("info-body").textContent = "";
  $("info").classList.remove("hidden");
  refreshInfo();
  clearInterval(infoTimer);
  infoTimer = setInterval(refreshInfo, 1000);
}

function closeInfo() {
  infoDeck = null;
  clearInterval(infoTimer);
  $("info").classList.add("hidden");
}

$("decks").addEventListener("click", (e) => {
  const el = e.target.closest(".deck");
  if (!el) return;
  const n = Number(el.dataset.deck);
  if (n) openInfo(n);
});
$("info-close").onclick = closeInfo;
$("info").addEventListener("click", (e) => { if (e.target === $("info")) closeInfo(); });
document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeInfo(); });

/* ---------- Manual setup ---------- */

function openManual() {
  ui.manualOpen = true;
  ui.lastCheck = null;
  $("check-result").textContent = "";
  updateModelNote();
  loadAdapters();
  render();
}

function updateModelNote() {
  $("model-note").textContent = t(MODEL_NOTES[$("model").value] || "");
}

async function loadAdapters() {
  try {
    const info = await (await fetch("/api/info")).json();
    ui.controller = info.controller;
    const ul = $("adapters");
    ul.textContent = "";
    if (!info.adapters.length) {
      const li = document.createElement("li");
      li.textContent = t("No active network connection found. Please check the network cable.");
      ul.append(li);
    }
    // Documentation screenshots show an example instead of the real adapters.
    const adapters = isShot() ? [{ name: "Ethernet", ip: "192.168.1.10" }] : info.adapters;
    for (const a of adapters) {
      const li = document.createElement("li");
      li.textContent = a.name + " – " + a.ip;
      ul.append(li);
    }
    const lan = $("lan");
    lan.textContent = info.urls.length ? t("Open on the tablet (same Wi-Fi): ") + info.urls.join(t("  or  ")) : "";
  } catch (e) { /* shown via connection status */ }
}

function renderCheckResults() {
  const ul = $("check-result");
  const res = ui.lastCheck;
  if (!res) return;
  ul.textContent = "";
  for (const c of res.checks) {
    const li = document.createElement("li");
    li.className = c.ok ? "ok" : "bad";
    const label = document.createElement("span");
    label.className = "label";
    label.textContent = t(c.label);
    const hint = document.createElement("span");
    hint.className = "hintline";
    hint.textContent = t(c.hint, c.args);
    li.append(label, hint);
    ul.append(li);
  }
}

async function runCheck() {
  const ul = $("check-result");
  ul.textContent = t("Checking …");
  try {
    const r = await fetch("/api/check?ip=" + encodeURIComponent($("ip").value));
    ui.lastCheck = await r.json();
    renderCheckResults();
  } catch (e) {
    ui.lastCheck = null;
    ul.textContent = t("Check not possible.");
  }
}

/* ---------- Language ---------- */

/** Switches the language of the interface, rebuilds all generated texts and tells the program (tray menu). */
function setLanguage(l, remember = true) {
  lang = l === "de" ? "de" : "en";
  if (remember) { try { localStorage.setItem("ljlinkpro.lang", lang); } catch (e) { /* ignore */ } }
  $("lang-select").value = lang;
  translateStatic();
  if (typeof loadVersion === "function") loadVersion();
  applySettings();
  applyPrivacy();
  updateModelNote();
  render();
  if (ui.tab === "mixer") renderMixer();
  for (const d of decks.values()) if (d.data) updateDeck(d.data);
  if (!$("settings").classList.contains("hidden")) loadLink();
  if (infoDeck != null) refreshInfo();
  renderCheckResults();
  loadAdapters();
  if (remember && ui.controller) post("/api/language?lang=" + lang);
}

/* ---------- Wiring ---------- */

function startSearch() {
  ui.manualOpen = false;
  post("/api/search/start");
}

$("btn-auto").onclick = startSearch;
$("btn-demo").onclick = () => { ui.manualOpen = false; post("/api/demo/start"); };
$("btn-manual").onclick = openManual;
$("btn-help-manual").onclick = () => { post("/api/stop").then(openManual); };
$("btn-cancel").onclick = () => post("/api/stop");
/** Version under the start page, with a hint when a newer one exists. */
async function loadVersion() {
  try {
    const v = await (await fetch("/api/version")).json();
    const line = $("version-line");
    line.textContent = v.current === "dev" ? "" : t("Version {v}", { v: v.current });
    if (v.update) {
      const u = document.createElement("span");
      u.className = "update";
      u.textContent = " · " + t("Version {v} is available – install it from the program icon (menu bar or next to the clock).", { v: v.update });
      line.append(u);
    }
  } catch (e) { /* offline: no version line */ }
}
loadVersion();

$("btn-stop").onclick = () => { $("settings").classList.add("hidden"); post("/api/stop"); };
$("btn-retry-auto").onclick = startSearch;
$("btn-manual-back").onclick = () => { ui.manualOpen = false; render(); };
$("btn-error-retry").onclick = startSearch;
$("btn-error-manual").onclick = () => { post("/api/stop").then(openManual); };
$("btn-check").onclick = runCheck;
$("ip").addEventListener("keydown", (e) => { if (e.key === "Enter") runCheck(); });
$("model").onchange = updateModelNote;
$("btn-settings").onclick = () => { $("settings").classList.remove("hidden"); loadLink(); };
$("btn-settings-close").onclick = () => $("settings").classList.add("hidden");
$("settings").addEventListener("click", (e) => { if (e.target === $("settings")) $("settings").classList.add("hidden"); });
document.addEventListener("keydown", (e) => { if (e.key === "Escape") $("settings").classList.add("hidden"); });
$("zoom").oninput = () => { settings.zoom = Number($("zoom").value); saveSettings(); applySettings(); };
$("seg-mode").onclick = (e) => { if (e.target.dataset.value) { settings.mode = e.target.dataset.value; saveSettings(); applySettings(); } };
$("seg-theme").onclick = (e) => { if (e.target.dataset.value) { settings.theme = e.target.dataset.value; saveSettings(); applySettings(); } };
const ZOOM_STEPS = [4, 6, 8, 12, 16, 24, 32, 40];
function stepZoom(dir) {
  // dir > 0 = zoom in = fewer seconds visible
  let i = ZOOM_STEPS.findIndex((z) => z >= settings.zoom);
  if (i < 0) i = ZOOM_STEPS.length - 1;
  i = Math.min(ZOOM_STEPS.length - 1, Math.max(0, i - dir));
  settings.zoom = ZOOM_STEPS[i];
  saveSettings(); applySettings();
}
$("zoom-in").onclick = () => stepZoom(1);
$("zoom-out").onclick = () => stepZoom(-1);
$("btn-view").onclick = () => {
  const order = ["both", "detail", "overview"];
  settings.view = order[(order.indexOf(settings.view) + 1) % order.length];
  saveSettings(); applySettings();
};
$("tab-link").onclick = () => {
  $("settings").classList.remove("hidden"); loadLink();
  $("link-setting").scrollIntoView({ block: "center" });
};
/* ---------- Send BPM: which deck gives the tempo to the light desk ---------- */

let bpmTimer = null;

async function loadBpm() {
  let res;
  try { res = await (await fetch("/api/link")).json(); } catch (e) { res = null; }
  const state = $("bpm-state");
  const list = $("bpm-list");
  if (!res || !res.config) {
    state.textContent = t("This can only be set on the computer that runs LJ-Link-PRO.");
    list.textContent = "";
    return;
  }
  const st = res.status;
  const off = res.config.type === "off";
  const sending = st.sendingDeck;
  const names = {};
  for (const d of res.decks) names[d.key] = deckLabel(d);
  state.textContent = off ? t("Sending the tempo is switched off. Choose a light desk first.")
    : st.error ? t(st.error)
    : sending ? t("Sending {bpm} BPM from {deck}", { bpm: st.bpm == null ? "–" : st.bpm.toFixed(1), deck: names[sending] || sending })
    : t("Waiting for a deck with a tempo …");
  state.classList.toggle("warn", off || !!st.error);
  const key = JSON.stringify([res.decks.map(d => d.key + d.name), st.source]);
  if (list.dataset.key !== key) {
    list.dataset.key = key;
    list.textContent = "";
    const option = (value, title, sub) => {
      const label = document.createElement("label");
      label.className = "bpm-option";
      label.dataset.value = value;
      const radio = document.createElement("input");
      radio.type = "radio";
      radio.name = "bpm-source";
      radio.value = value;
      radio.checked = st.source === value;
      radio.onchange = () => setBpmSource(value);
      const text = document.createElement("span");
      const b = document.createElement("b");
      b.textContent = title;
      const small = document.createElement("small");
      small.className = "bpm-sub";
      small.textContent = sub || "";
      text.append(b, small);
      label.append(radio, text);
      list.append(label);
    };
    option("auto", t("Auto"), t("The deck that is master and/or live"));
    for (const d of res.decks) option(d.key, deckLabel(d), "");
    // A chosen deck that is not there right now stays visible, so the choice is not lost silently.
    if (st.source !== "auto" && !res.decks.some(d => d.key === st.source)) option(st.source, t("Chosen deck (not connected)"), "");
  }
  // Live values without rebuilding the list.
  for (const d of res.decks) {
    const el = list.querySelector(`[data-value="${CSS.escape(d.key)}"] .bpm-sub`);
    if (!el) continue;
    const flags = [d.master ? "MASTER" : "", d.onAir ? "LIVE" : "", d.playing ? t("playing") : ""].filter(Boolean).join(" · ");
    el.textContent = (d.bpm > 0 ? d.bpm.toFixed(1) + " BPM" : t("no track")) + (flags ? " · " + flags : "");
  }
  list.querySelectorAll(".bpm-option").forEach(l => l.classList.toggle("sending", !!sending && (l.dataset.value === sending || (st.source === "auto" && l.dataset.value === "auto"))));
}

function deckLabel(d) {
  return (d.kind === "denon" ? "Deck " : "Player ") + d.label + (d.name ? " · " + d.name : "");
}

async function setBpmSource(value) {
  await fetch("/api/link/source", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ deck: value }) });
  loadBpm();
}

function openBpm() {
  $("bpm").classList.remove("hidden");
  $("bpm-list").dataset.key = "";
  loadBpm();
  clearInterval(bpmTimer);
  bpmTimer = setInterval(loadBpm, 1000);
}

function closeBpm() {
  $("bpm").classList.add("hidden");
  clearInterval(bpmTimer);
}

$("tab-bpm").onclick = openBpm;
$("bpm-close").onclick = closeBpm;
$("bpm").addEventListener("click", (e) => { if (e.target === $("bpm")) closeBpm(); });
$("bpm-setup").onclick = () => { closeBpm(); $("tab-link").onclick(); };
document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeBpm(); });

$("tab-waveform").onclick = () => { $("settings").classList.add("hidden"); ui.tab = "waveform"; applyTab(); };
$("tab-mixer").onclick = () => { $("settings").classList.add("hidden"); if (ui.state.phase === "monitoring") { ui.tab = "mixer"; applyTab(); } };
$("lang-select").onchange = () => setLanguage($("lang-select").value);
$("btn-fullscreen").onclick = () => {
  if (document.fullscreenElement) document.exitFullscreen();
  else document.documentElement.requestFullscreen?.();
};

fetch("/api/info").then((r) => r.json()).then((info) => {
  ui.controller = info.controller;
  $("controls").classList.toggle("hidden", !info.controller);
  $("remote-note").classList.toggle("hidden", info.controller);
  $("lan").textContent = info.urls.length ? t("Open on the tablet (same Wi-Fi): ") + info.urls.join(t("  or  ")) : "";
  render();
  // The program icon menu follows the language chosen on this computer.
  if (info.controller) post("/api/language?lang=" + lang);
}).catch(() => {});

setInterval(() => { if (ui.state.phase === "searching") render(); }, 1000);
$("lang-select").value = lang;
translateStatic();
applySettings();

// Deep links such as /#settings, /#manual, /#mixer, /#info-1 or /#day,shot (used for the screenshots in the docs;
// "shot" hides the network addresses).
setTimeout(() => {
  const h = location.hash;
  if (h.includes("shot")) document.body.classList.add("shot");
  if (h.includes("lang-de")) setLanguage("de", false);
  if (h.includes("lang-en")) setLanguage("en", false);
  if (h.includes("day")) { settings.theme = "day"; applySettings(); }
  if (h.includes("mixer")) { ui.tab = "mixer"; applyTab(); }
  if (h.includes("detail")) { settings.view = "detail"; applySettings(); }
  if (h.includes("bpm")) openBpm();
  if (h.includes("settings")) { $("settings").classList.remove("hidden"); loadLink(); }
  else if (h.includes("manual")) openManual();
  else { const m = h.match(/info-(\d+)/); if (m) openInfo(Number(m[1])); }
}, 1500);
connect();
requestAnimationFrame(frame);
