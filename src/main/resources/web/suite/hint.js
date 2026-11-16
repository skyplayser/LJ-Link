// LJ Link wiring for the shared Link-Suite hint card "Get Link-Suite" (suite/launcher-hint.js, copied from the
// Suite-Launcher). Shown under the three steps of the start page, only on the computer that runs LJ Link (the route is
// local only: tablets get 403 and no card), only when the launcher is not installed, after the start animation.
// The check is local (LauncherDetect in Java); the network is used only when someone clicks "Get Link-Suite".
import { showLauncherHint } from "./launcher-hint.js";

const ui = window.ljlinkUi;
const intro = window.ljlinkIntro;
const host = document.getElementById("launcher-hint");

/** Asks LJ Link whether the launcher is installed: {installed, version} or null (tablet, error: then no card). */
async function detect() {
  try {
    const r = await fetch("/api/suite/launcher", { cache: "no-store" });
    return r.ok ? await r.json() : null;
  } catch (e) {
    return null;
  }
}

async function start() {
  if (!host || !ui) return;
  // Not in documentation screenshots or other deep links (#shot, #settings …): those show something else.
  if (location.hash.replace("#", "")) return;
  // "Don't show again": not even look (the first check of a program start may ask Spotlight).
  if (ui.setting("hideLauncherHint") === true) return;
  // Only the computer running LJ Link asks; a tablet would only get a refusal.
  try {
    const info = await (await fetch("/api/info", { cache: "no-store" })).json();
    if (!info.controller) return;
  } catch (e) {
    return;
  }
  const launcher = await detect();
  const hint = showLauncherHint({
    host,
    launcher,
    iconSrc: "suite/icons/link-suite-icon.svg",
    lang: ui.lang() === "de" ? "de" : "en",
    hidden: ui.setting("hideLauncherHint") === true,
    compact: false,
    after: intro ? intro.done : true,
    onGet: () => {
      fetch("/api/suite/launcher-page", { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" })
        .catch(() => {});
    },
    onHide: () => ui.set("hideLauncherHint", true),
  });
  if (!hint.el) return;
  // Installed in the meantime: the card goes when this window gets the focus again.
  addEventListener("focus", async () => { if (hint.el) hint.update(await detect()); });
}

start();
