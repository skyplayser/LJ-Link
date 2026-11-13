"use strict";
/*
 * LJ Link opens http://127.0.0.1:PORT instead of http://localhost:PORT (issue #3: browsers try "localhost" as [::1]
 * first, where another program may listen). Browsers keep settings per address, so the first start after the change
 * opens localhost with #move-settings=<token>: that page hands its LJ Link settings to 127.0.0.1 and goes there.
 *
 * Safe against other web pages (ADR-039): 127.0.0.1 imports only after the server accepted the one-time token that
 * LJ Link itself put into the address (POST /api/settings-move, local and same-origin only); only known keys with
 * valid values are taken, never "__proto__" or "constructor", and existing entries stay. Afterwards a marker stops any
 * later #import-settings. The token stays in the browser's global history, where it is worthless: it works once and
 * only for 5 minutes. Runs before i18n.js and app.js read the settings.
 */
(() => {
  const MARK = "ljlinkpro.moveDone";
  const has = (o, k) => Object.prototype.hasOwnProperty.call(o, k);
  const parse = (text) => { try { return JSON.parse(text); } catch (e) { return undefined; } };

  /** Only the settings LJ Link uses, with their allowed values; the result is a plain map of storage strings. */
  function clean(data) {
    const out = new Map();
    if (!data || typeof data !== "object" || Array.isArray(data)) return out;
    const lang = has(data, "ljlinkpro.lang") ? data["ljlinkpro.lang"] : null;
    if (lang === "de" || lang === "en") out.set("ljlinkpro.lang", lang);
    const s = has(data, "ljlinkpro.settings") && typeof data["ljlinkpro.settings"] === "string" ? parse(data["ljlinkpro.settings"]) : null;
    if (s && typeof s === "object" && !Array.isArray(s)) {
      const v = {};
      if (has(s, "zoom") && Number.isInteger(s.zoom) && s.zoom >= 4 && s.zoom <= 40) v.zoom = s.zoom;
      if (has(s, "mode") && (s.mode === "rgb" || s.mode === "blue")) v.mode = s.mode;
      if (has(s, "theme") && (s.theme === "night" || s.theme === "day")) v.theme = s.theme;
      if (has(s, "view") && ["both", "detail", "overview"].includes(s.view)) v.view = s.view;
      if (has(s, "keepAwake") && typeof s.keepAwake === "boolean") v.keepAwake = s.keepAwake;
      if (Object.keys(v).length) out.set("ljlinkpro.settings", JSON.stringify(v));
    }
    const hidden = has(data, "ljlinkpro.hidden") && typeof data["ljlinkpro.hidden"] === "string" ? parse(data["ljlinkpro.hidden"]) : null;
    if (Array.isArray(hidden)) {
      const groups = hidden.filter((g) => typeof g === "string" && /^[\w.:-]{1,64}$/.test(g)).slice(0, 64);
      if (groups.length) out.set("ljlinkpro.hidden", JSON.stringify(groups));
    }
    const preset = has(data, "ljlinkpro.linkPreset") ? data["ljlinkpro.linkPreset"] : null;
    if (typeof preset === "string" && /^[A-Za-z]{1,30}$/.test(preset)) out.set("ljlinkpro.linkPreset", preset);
    return out;
  }

  try {
    const hash = location.hash;
    const move = hash.match(/^#move-settings=([0-9a-f]{32})$/);
    if (move && location.hostname === "localhost") {
      const data = {};
      for (const k of ["ljlinkpro.lang", "ljlinkpro.settings", "ljlinkpro.hidden", "ljlinkpro.linkPreset"]) {
        const v = localStorage.getItem(k);
        if (v !== null) data[k] = v;
      }
      document.documentElement.style.visibility = "hidden";
      location.replace("http://127.0.0.1:" + location.port + "/#import-settings=" + move[1] + "." + encodeURIComponent(JSON.stringify(data)));
      return;
    }
    if (location.hostname !== "127.0.0.1") return;
    const imp = hash.match(/^#import-settings=([0-9a-f]{32})\.(.{0,20000})$/);
    // Remove the hash at once from the address bar and this tab's history, so app.js does not read it as a deep link.
    // The browser's global history still lists both addresses with the token; it is worthless there (one use, 5 min).
    if (hash.startsWith("#import-settings")) history.replaceState(null, "", location.pathname + location.search);
    if (localStorage.getItem(MARK)) return;
    fetch("/api/settings-move", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: imp ? imp[1] : "" }),
    }).then((r) => (r.ok ? r.json() : null)).then((res) => {
      if (!res) return;
      if (res.ok === true) {
        let added = 0;
        let raw = "";
        try { raw = decodeURIComponent(imp[2]); } catch (e) { /* broken: import nothing */ }
        for (const [k, v] of clean(parse(raw))) {
          if (localStorage.getItem(k) === null) { localStorage.setItem(k, v); added++; }
        }
        localStorage.setItem(MARK, "1");
        if (added) location.reload(); // i18n.js and app.js have read the settings already
      } else if (res.done === true) {
        localStorage.setItem(MARK, "1"); // nothing will be handed over any more
      }
    }).catch(() => { /* not reachable now: try on the next load */ });
  } catch (e) { /* no storage or a broken address: start with the defaults */ }
})();
