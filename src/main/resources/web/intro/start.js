// LJ Link wiring for the shared Link-Suite start animation (intro/program-intro.js, copied from the Suite-Launcher).
// app.js decides before the first frame whether it may play (setting "Start animation", deep links, once per tab) and
// removes the covering layer #lsi when not, or when this module does not take over in time. This module waits for the
// first drawn state, skips it when this program start was already greeted in this browser, and starts it.
import { playProgramIntro, defaultImages } from "./program-intro.js";

const hook = window.ljlinkIntro;
const layer = document.getElementById("lsi");
const START_KEY = "linksuite-intro:lj:start";

/** Shown on screen: has a box and no hidden ancestor (the start page is hidden while decks are shown). */
const shown = (el) => !!el && el.getClientRects().length > 0;

/** True when this program start (/api/info "started") was already greeted in this browser; remembers it otherwise. */
function greetedBefore() {
  const started = hook.info && hook.info.started;
  if (!started) return false;
  try {
    if (localStorage.getItem(START_KEY) === String(started)) return true;
    localStorage.setItem(START_KEY, String(started));
  } catch (e) { /* storage unavailable: show it */ }
  return false;
}

async function start() {
  if (!layer) return;
  if (!hook || !hook.enabled) { layer.remove(); hook?.finished(); return; }
  await hook.ready;
  if (!layer.isConnected) return;                   // removed by a key, a click or the time limit
  if (greetedBefore()) { layer.remove(); hook.finished(); return; }
  try { await document.fonts.ready; } catch (e) { /* no font API: measure now */ }
  await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
  if (!hook.claim()) return;
  const theme = document.documentElement.dataset.theme === "day" ? "light" : "dark";
  try {
    playProgramIntro({
      layer,
      app: "lj",
      targetKind: "icon",
      // The big logo of the start page when it is shown, otherwise the small one in the top bar.
      logoTarget: () => {
        const hero = document.querySelector("#view-welcome .hero-logo");
        return shown(hero) ? hero : document.querySelector("#topbar img.logo");
      },
      revealTargets: () => [...document.querySelectorAll(
        "#view-welcome h1, #view-welcome .lead, #remote-note, #controls, #view-welcome .steps li")].filter(shown),
      images: defaultImages("intro/icons/", theme, "lj"),
      theme,
      enabled: true,
      once: false,                                  // app.js keeps the once-per-tab mark, this module the per-start one
      text: hook.text,
    });
  } catch (e) {
    abort();
  }
}

/** Something failed: the page must be usable at once (layer gone, nothing left hidden by the animation). */
function abort() {
  layer?.remove();
  hook?.finished();
  document.documentElement.classList.remove("lsi-run");
  for (const a of document.getAnimations()) {
    if (a.effect && a.effect.getTiming && a.effect.getTiming().fill === "both") a.cancel();
  }
}

start().catch(abort);
