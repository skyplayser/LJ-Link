// Copied unchanged from the Suite-Launcher project, design/intro-programm/program-intro.js (module 1.0, 16 Nov 2026).
// Do not edit here: changes belong in the Suite-Launcher, then copy again. LJ Link wiring: intro/start.js.
// Link-Suite · gemeinsame Startanimation für jedes Programm (Baustein 1.0, 16.11.2026).
// Ablauf: Suite-Zeichen → alle acht Programme fächern auf → Suite-Logo → die anderen kehren ins Zeichen zurück, das
// Suite-Logo löst sich auf → das geöffnete Programm fliegt (FLIP) auf sein Ziel im echten Startbildschirm, dort wird aus
// dem Icon das Logo (Zeichen bleibt, Name wischt herein) → der Startbildschirm wird frei, seine Elemente blenden gestaffelt ein.
// Grundlage: Suite-Launcher src/renderer/intro/launcher-intro.ts, Vorlage Link-Suite/Konzepte/_Startanimation.
// Regeln: ≤ 2,5 s, überspringbar (Taste außer ⌘/Strg, Klick → 150 ms ausblenden), reduzierte Bewegung, kein Strobe,
// kein Ton, abschaltbar (enabled), einmal je Start. Keine Abhängigkeiten, reines ES-Modul.
// CSP-fest: keine style-Attribute, kein innerHTML – nur CSSOM (el.style.x = …) und Web Animations API.
// Einbau: EINBAU.md. Typen: program-intro.d.ts.

/** @typedef {'lj'|'ljl'|'vj'|'vd'|'net'|'cr'|'pl'|'db'} AppId */

/**
 * @typedef {Object} IntroImages
 * @property {string} symbol     Suite-Zeichen (suite-symbol.svg)
 * @property {string} suiteLogo  Suite-Logo passend zum Modus (suite-logo-dark.svg / -light.svg)
 * @property {Record<AppId, string>} icons  Icons aller acht Programme (Reihenfolge des Fächers: ORDER)
 * @property {string} [logo]     Logo des geöffneten Programms passend zum Modus (nur targetKind 'logo' ohne signTarget nötig)
 */

/**
 * @typedef {Object} ProgramIntroOptions
 * @property {HTMLElement} layer  Schicht über dem Startbildschirm (.lsi-layer, am besten schon im HTML, damit nichts aufblitzt)
 * @property {AppId} app          Programm, das gerade startet
 * @property {() => (Element|DOMRect|null)} logoTarget  Ziel, beim Start gewählt. Element bevorzugt: es wird bis zur Landung verborgen.
 *   Ist es ein Behälter mit mehreren <img>/<svg> (only-dark/only-light), nimmt der Baustein das sichtbare.
 * @property {'logo'|'icon'} [targetKind='logo']  'logo': Ziel ist das ganze Logo mit Namen. 'icon': Ziel ist eine Icon-Kachel.
 * @property {() => (Element|null)} [signTarget]  Nur bei HTML-Logos (Zeichen + Text, z. B. VJ/VD .start-logo .slots):
 *   Das Icon landet mit seinem Zeichen genau auf diesem Element, dann wischt das echte Logo-Element von links auf.
 * @property {boolean} [targetPadded=false]  Bei 'icon': Ziel zeigt ein Suite-Icon MIT Rand (wie die SVGs, Kachel = 821/1024).
 *   false = das Ziel ist selbst die Kachel (z. B. LJ .hero-logo mit border-radius).
 * @property {() => Element[]} [revealTargets]  Elemente des Startbildschirms, die gestaffelt einblenden (Kacheln, Knöpfe)
 * @property {IntroImages} images
 * @property {'dark'|'light'} [theme='dark']  Fläche der Schicht (Klasse lsi-light)
 * @property {boolean} [enabled=true]   Schalter des Programms (suite.json → intro). false: sofort fertig
 * @property {boolean} [reduced]        Bewegung reduzieren; Standard: prefers-reduced-motion
 * @property {false|'session'} [once=false]  'session': der Baustein prüft sessionStorage selbst (Weboberflächen, Tablets)
 * @property {string} [sessionKey]      Schlüssel für once:'session' (Standard 'linksuite-intro:<app>')
 * @property {number} [speed=1]         Zeitlupe zum Prüfen (0.25)
 * @property {number|null} [holdAt=null]  Standbild bei dieser Zeit in ms (Screenshots)
 * @property {(key: 'skip'|'key'|'or') => string} [text]  Texte des Hinweises „Überspringen: Taste oder Klick“
 */

/** @typedef {{app: AppId, skipped: boolean, shown: boolean}} IntroDone */

/** Reihenfolge des Fächers (Wunsch: LJ, LJL, VJ, VD, NET, Crew, PL, dB). */
export const ORDER = /** @type {AppId[]} */ (['lj', 'ljl', 'vj', 'vd', 'net', 'cr', 'pl', 'db']);

/** Lage der Zeichen, aus Link-Suite/Konzepte/_Startanimation (make_startanimation.py): viewBox des Logos, Zeichen-Box
 *  im Logo (x ab 0, Breite w), Breite und Mitte (y) des Zeichens im 1024er-Icon. LJL hat (noch) kein Logo. */
export const BRANDS = {
  suite: { logoBox: [-8, -6.5, 591.54, 106], sign: { w: 108, y: 0, h: 90 }, iconSignW: 564, iconSignCy: 700 },
  lj: { logoBox: [-8, 2, 507.49, 96], sign: { w: 166, y: 10, h: 80 }, iconSignW: 564, iconSignCy: 710 },
  vj: { logoBox: [-8, 4.35, 545.49, 84.69], sign: { w: 166, y: 42, h: 26 }, iconSignW: 564, iconSignCy: 705 },
  vd: { logoBox: [-8, 4.35, 557.67, 87.65], sign: { w: 166, y: 32, h: 52 }, iconSignW: 596, iconSignCy: 725 },
  pl: { logoBox: [-8, 4.35, 513.55, 83.65], sign: { w: 166, y: 30, h: 48 }, iconSignW: 564, iconSignCy: 716 },
  net: { logoBox: [-8, 4.35, 585.33, 83.65], sign: { w: 166, y: 25, h: 44.15 }, iconSignW: 564, iconSignCy: 713.19 },
  cr: { logoBox: [-8, 3.99, 634.28, 85.09], sign: { w: 166, y: 14, h: 66 }, iconSignW: 552, iconSignCy: 704 },
  db: { logoBox: [-8, -14, 518.45, 108], sign: { w: 166, y: -6, h: 92 }, iconSignW: 470, iconSignCy: 662 },
};

/**
 * Standard-Bildpfade aus dem mitgelieferten Ordner icons/.
 * @param {string} base  z. B. './intro/icons/'
 * @param {'dark'|'light'} theme
 * @param {AppId} app
 * @returns {IntroImages}
 */
export function defaultImages(base, theme, app) {
  const m = theme === 'light' ? 'light' : 'dark';
  /** @type {Record<string,string>} */ const icons = {};
  for (const id of ORDER) icons[id] = base + id + (id === 'ljl' ? '-icon.png' : '-icon.svg');
  return { symbol: base + 'suite-symbol.svg', suiteLogo: base + 'suite-logo-' + m + '.svg', icons: /** @type {Record<AppId,string>} */ (icons),
    logo: app === 'ljl' ? undefined : base + app + '-logo-' + m + '.svg' };
}

const DE = { skip: 'Überspringen:', key: 'Taste', or: 'oder Klick' };
const EASE = 'cubic-bezier(.2,.8,.2,1)';

/**
 * Spielt die Startanimation. Erst aufrufen, wenn der Startbildschirm sein erstes Layout hat.
 * @param {ProgramIntroOptions} o
 * @returns {{finish(skipped: boolean): void, done: Promise<IntroDone>}}
 */
export function playProgramIntro(o) {
  const app = o.app, layer = o.layer, speed = o.speed || 1, holdAt = o.holdAt ?? null;
  const text = o.text || ((/** @type {'skip'|'key'|'or'} */ k) => DE[k]);
  /** @type {(v: IntroDone) => void} */ let resolveDone = () => {};
  const done = new Promise(r => { resolveDone = r; });
  const announce = (/** @type {boolean} */ skipped, /** @type {boolean} */ shown) => {
    const detail = { app, skipped, shown };
    dispatchEvent(new CustomEvent('linksuite-intro-done', { detail }));
    resolveDone(detail);
  };

  // ------------------------------------------------ abgeschaltet oder in dieser Sitzung schon gezeigt
  const key = o.sessionKey || 'linksuite-intro:' + app;
  let seenBefore = false;
  if (o.once === 'session' && holdAt === null) {
    try { seenBefore = sessionStorage.getItem(key) === '1'; sessionStorage.setItem(key, '1'); } catch { /* privat: dann zeigen */ }
  }
  if (o.enabled === false || seenBefore) {
    layer.remove();
    queueMicrotask(() => announce(false, false));
    return { finish() {}, done };
  }

  const reduced = o.reduced ?? matchMedia('(prefers-reduced-motion: reduce)').matches;
  const W = innerWidth, H = innerHeight, cx = W / 2, cy = H / 2;
  const k = Math.max(0.2, Math.min(W / 1000, H / 600, 1.4)); // Bühne 1000 × 600 wie die Vorlage
  /** @type {Animation[]} */ const anims = [];
  const seen = new Set();
  /** Erstes Stück je Element füllt rückwärts (Anfangszustand), alle weiteren nur vorwärts. */
  const run = (/** @type {Element} */ el, /** @type {Keyframe[]} */ frames, /** @type {number} */ delay, /** @type {number} */ dur, easing = EASE) => {
    const fill = seen.has(el) ? 'forwards' : 'both'; seen.add(el);
    const a = el.animate(frames, { delay: delay / speed, duration: dur / speed, easing, fill });
    anims.push(a); return a;
  };
  /** Bild fest an seinem ZIEL (Last), Mitte (x, y), Größe w × h. Frühere Lagen = Transform relativ dazu (FLIP). */
  const put = (/** @type {string} */ src, /** @type {number} */ x, /** @type {number} */ y, /** @type {number} */ w, h = w) => {
    const i = /** @type {HTMLImageElement & {_c: number[]}} */ (new Image());
    i.src = src; i.alt = ''; i.decoding = 'sync'; i.draggable = false;
    i.style.left = (x - w / 2) + 'px'; i.style.top = (y - h / 2) + 'px'; i.style.width = w + 'px'; i.style.height = h + 'px';
    i._c = [x, y]; layer.append(i); return i;
  };
  const pose = (/** @type {HTMLImageElement & {_c: number[]}} */ i, /** @type {number} */ x, /** @type {number} */ y, s = 1) =>
    `translate(${x - i._c[0]}px,${y - i._c[1]}px) scale(${s})`;
  const rectOf = (/** @type {Element|DOMRect|null|undefined} */ t) => !t ? null : ('getBoundingClientRect' in t ? t.getBoundingClientRect() : t);
  const visible = (/** @type {DOMRect|null} */ r) => !!r && r.width > 2 && r.height > 2 && r.bottom > 0 && r.right > 0 && r.top < H && r.left < W;

  // ------------------------------------------------ Schicht
  layer.classList.add('lsi-layer');
  layer.classList.toggle('lsi-light', o.theme === 'light');
  let bg = /** @type {HTMLElement|null} */ (layer.querySelector('.lsi-bg'));
  if (!bg) { bg = document.createElement('div'); bg.className = 'lsi-bg'; layer.append(bg); }
  const skip = document.createElement('div');
  skip.className = 'lsi-skip';
  const kbd = document.createElement('kbd'); kbd.textContent = text('key');
  skip.append(text('skip') + ' ', kbd, ' ' + text('or'));
  bg.append(skip);
  layer.hidden = false;
  document.documentElement.classList.add('lsi-run');

  // ------------------------------------------------ Ziel bestimmen
  const rawTarget = o.logoTarget();
  /** Element, das verborgen wird und am Ende erscheint (Behälter), und das sichtbare Bild darin (Maß). */
  let hideEl = rawTarget && 'getBoundingClientRect' in rawTarget ? rawTarget : null;
  let measureEl = hideEl;
  if (hideEl && !(hideEl instanceof HTMLImageElement) && !(hideEl instanceof SVGSVGElement) && !o.signTarget) {
    const kids = [...hideEl.querySelectorAll(':scope > img, :scope > svg')].filter(e => e.getBoundingClientRect().width > 0);
    if (kids.length) measureEl = kids[0];
  }
  let R = rectOf(measureEl || rawTarget);
  const signEl = o.signTarget ? o.signTarget() : null;
  const signR = rectOf(signEl);
  const B = /** @type {Record<string, typeof BRANDS.lj>} */ (BRANDS)[app];
  let kind = o.targetKind || 'logo';
  if (kind === 'logo' && !B) kind = 'icon';                       // LJL: kein Logo → als Icon landen
  const onTarget = visible(R);
  if (!onTarget) R = null;                                         // Rückfall: Mitte, am Ende ausblenden
  const real = /** @type {Element[]} */ ([]);                      // echte Elemente, die erst am Ende sichtbar sind
  if (hideEl && onTarget) real.push(hideEl);
  const reveal = (o.revealTargets ? o.revealTargets() : []).filter(e => e && e !== hideEl);

  if (reduced) return reducedRun();

  // ================================================ 1 · 0–380 ms: Suite-Zeichen erscheint
  // Suite-Logo oben mittig (Ziel des Zeichens in Phase 2b): Höhe 60·k
  const SB = BRANDS.suite, LH = 60 * k, sl = LH / SB.logoBox[3], LW = SB.logoBox[2] * sl;
  const upL = cy - 118 * k, suiteLogo = put(o.images.suiteLogo, cx, upL, LW, LH);
  const sym0 = [cx - LW / 2 + (4 + 58) * sl, upL - LH / 2 + (4 + 49) * sl];   // Zeichen im Suite-Logo (symbol.svg −4 −4 116 98)
  const SW = 150 * k, sym = put(o.images.symbol, sym0[0], sym0[1], SW, SW * 98 / 116), symEnd = 116 * sl / SW;
  run(sym, [{ opacity: 0, transform: pose(sym, cx, cy, 0.82) }, { opacity: 1, transform: pose(sym, cx, cy, 1) }], 0, 380, 'cubic-bezier(.2,.7,.2,1)');

  // ================================================ 2 · 320–1140 ms: Zeichen rückt hoch, acht Programme fächern auf
  const upS = cy - 96 * k;
  run(sym, [{ transform: pose(sym, cx, cy, 1) }, { transform: pose(sym, cx, upS, 0.62) }], 320, 520);
  const n = ORDER.length, F0 = 92 * k, G0 = 22 * k, rowY = cy + 64 * k;
  const fit = Math.min(1, (W * 0.92) / (n * F0 + (n - 1) * G0)), F = F0 * fit, G = G0 * fit;
  const slotX = (/** @type {number} */ i) => cx - (n * F + (n - 1) * G) / 2 + F / 2 + i * (F + G);
  const me = ORDER.indexOf(app);

  // Ziel des geöffneten Programms (Last) und seine Endgröße
  const t = landing();
  /** @type {(HTMLImageElement & {_c: number[]})|null} */ let mine = null;
  ORDER.forEach((id, i) => {
    const x = slotX(i);
    if (id === app) {
      mine = put(o.images.icons[id], t.x, t.y, t.lay);
      run(mine, [{ opacity: 0, transform: pose(mine, cx, upS, 0.25 * F / t.lay) }, { opacity: 1, transform: pose(mine, x, rowY, F / t.lay) }], 380 + i * 40, 480);
      return;
    }
    // die anderen liegen am Zeichen im Suite-Logo (dahin kehren sie zurück)
    const el = put(o.images.icons[id], sym0[0], sym0[1], F);
    run(el, [{ opacity: 0, transform: pose(el, cx, upS, 0.25) }, { opacity: 1, transform: pose(el, x, rowY, 1) }], 380 + i * 40, 480);
    // ============================================ 3 · ab 1450 ms: zurück ins Suite-Zeichen, nach Abstand zum Geöffneten
    const d = 1450 + Math.abs(i - me) * 22;
    run(el, [{ opacity: 1, transform: pose(el, x, rowY, 1) }, { opacity: 0, transform: pose(el, sym0[0], sym0[1], 0.22) }], d, 380, 'cubic-bezier(.55,0,.6,1)');
  });

  // ================================================ 2b · 860–1500 ms: Zeichen wird zum Suite-Logo (alle Programme, dann das Logo)
  const suiteClip = `inset(0 ${100 - (120 / SB.logoBox[2]) * 100}% 0 0)`;
  run(sym, [{ transform: pose(sym, cx, upS, 0.62) }, { transform: pose(sym, sym0[0], sym0[1], symEnd) }], 860, 300, 'cubic-bezier(.55,0,.2,1)');
  run(suiteLogo, [{ opacity: 0, clipPath: suiteClip, transform: 'none' }, { opacity: 1, clipPath: suiteClip, transform: 'none' }], 1120, 120, 'linear');
  run(sym, [{ opacity: 1 }, { opacity: 0 }], 1200, 150, 'linear');
  run(suiteLogo, [{ clipPath: suiteClip }, { clipPath: 'inset(0 0% 0 0)' }], 1160, 340, 'cubic-bezier(.3,.7,.2,1)');
  // 3b · 1700–1950 ms: Suite-Logo löst sich auf (die Programme laufen gerade hinein)
  run(suiteLogo, [{ opacity: 1, transform: 'scale(1)' }, { opacity: 0, transform: 'scale(1.05)' }], 1700, 250, 'ease-in');

  // ================================================ 4 · 1480–2080 ms: das geöffnete Programm fliegt auf sein Ziel (FLIP, flacher Bogen)
  const m = /** @type {HTMLImageElement & {_c: number[]}} */ (/** @type {unknown} */ (mine));
  const x0 = slotX(me), FLY = 1480, DUR = 600, LAND = FLY + DUR;
  run(m, [{ transform: `translateY(${rowY - t.y}px) scale(${F / t.lay})` }, { transform: `translateY(0px) scale(${t.end})` }], FLY, DUR, 'cubic-bezier(.6,0,.2,1)');
  anims.push(m.animate([{ translate: `${x0 - t.x}px 0px` }, { translate: '0px 0px' }], { delay: FLY / speed, duration: DUR / speed, easing: 'cubic-bezier(.35,0,.15,1)', fill: 'forwards' }));
  // Fläche gibt den Startbildschirm frei (Farbe = Hintergrund des Programms: kein Helligkeitssprung)
  // Rückfall (Ziel nicht sichtbar): Fläche bleibt, bis das Logo in der Mitte ausgeblendet ist (nie Logo über der Oberfläche)
  const bgAt = onTarget ? 1650 : LAND + 460, revealAt = onTarget ? 1850 : LAND + 480;
  run(bg, [{ opacity: 1 }, { opacity: 0 }], bgAt, onTarget ? 450 : 220, 'ease-out');

  // ================================================ 5 · ab 2080 ms: Icon → Zeichen → Logo, Name wischt herein
  let end = LAND + 380;
  for (const el of real) run(el, [{ opacity: 0 }, { opacity: 0 }], 0, 1, 'linear');   // echtes Ziel bis zur Landung verbergen
  if (t.mode === 'icon') {
    // Icon-Kachel: deckungsgleich landen, 150 ms in das echte Element überblenden (Bilder dürfen sich unterscheiden)
    for (const el of real) run(el, [{ opacity: 0 }, { opacity: 1 }], LAND, 150, 'linear');
    run(m, [{ opacity: 1 }, { opacity: 0 }], LAND + (real.length ? 60 : 300), 150, 'linear');
    end = LAND + (real.length ? 320 : 480);
  } else if (t.mode === 'html') {
    // HTML-Logo (Zeichen + Text): das echte Element erscheint erst nur mit dem Zeichen, dann wischt der Name herein
    const r = /** @type {DOMRect} */ (R), s = /** @type {DOMRect} */ (signR);
    const clip = `inset(0 ${Math.max(0, 100 - ((s.right - r.left + 6) / r.width) * 100)}% 0 0)`;
    for (const el of real) {
      run(el, [{ opacity: 0, clipPath: clip }, { opacity: 1, clipPath: clip }], LAND - 20, 120, 'linear');
      run(el, [{ clipPath: clip }, { clipPath: 'inset(0 0% 0 0)' }], LAND + 60, 360, 'cubic-bezier(.3,.7,.2,1)');
    }
    run(m, [{ opacity: 1 }, { opacity: 0 }], LAND + 60, 150, 'linear');
  } else {
    // SVG-Logo: eigenes Logo-Bild liegt deckungsgleich auf dem Ziel; am Ende übernimmt das echte Element ohne Sprung
    const lg = put(/** @type {string} */ (o.images.logo), t.lx, t.ly, t.lw, t.lh);
    run(lg, [{ opacity: 0, clipPath: t.clip }, { opacity: 1, clipPath: t.clip }], LAND - 20, 120, 'linear');
    run(m, [{ opacity: 1 }, { opacity: 0 }], LAND + 60, 150, 'linear');
    run(lg, [{ clipPath: t.clip }, { clipPath: 'inset(0 0% 0 0)' }], LAND + 60, 360, 'cubic-bezier(.3,.7,.2,1)');
    if (!onTarget) { run(lg, [{ opacity: 1 }, { opacity: 0 }], LAND + 460, 200, 'ease-in'); end = LAND + 660; }
  }

  // ================================================ 6 · ab 1850 ms (Rückfall später): Elemente des Startbildschirms blenden gestaffelt ein
  const step = reveal.length > 1 ? Math.min(50, 300 / (reveal.length - 1)) : 0;
  reveal.forEach((el, i) => run(el, [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'none' }], revealAt + i * step, 300));
  end = Math.max(end, reveal.length ? revealAt + (reveal.length - 1) * step + 300 : 0);
  return control(end);

  // ------------------------------------------------ Landepunkt des geöffneten Icons
  function landing() {
    const lay0 = F;
    // Rückfall ohne sichtbares Ziel: Logo mittig, 84·k hoch (wie die Vorlage), am Ende ausblenden
    let r = R;
    if (!r) {
      if (kind === 'icon' || !o.images.logo) { const s = 120 * k; r = new DOMRect(cx - s / 2, cy - s / 2, s, s); kind = 'icon'; }
      else { const h = 84 * k, w = h * B.logoBox[2] / B.logoBox[3]; r = new DOMRect(cx - w / 2, cy - h / 2, w, h); }
    }
    if (kind === 'icon') {
      const size = o.targetPadded ? r.width : r.width * 1024 / 821;          // Kachel im Icon = 821/1024
      const lay = Math.max(size, lay0);
      return { mode: 'icon', x: r.left + r.width / 2, y: r.top + r.height / 2, lay, end: size / lay, lx: 0, ly: 0, lw: 0, lh: 0, clip: '' };
    }
    if (signR && signEl && visible(signR)) {
      // HTML-Logo: Zeichen-Box im Icon = Zeichen-Element
      const size = signR.width * 1024 / B.iconSignW, lay = Math.max(size, lay0);
      return { mode: 'html', x: signR.left + signR.width / 2, y: signR.top + signR.height / 2 - size * (B.iconSignCy - 512) / 1024, lay, end: size / lay, lx: 0, ly: 0, lw: 0, lh: 0, clip: '' };
    }
    // SVG-Logo: Zeichen-Box aus der viewBox (Höhe des Ziels zählt, Breite folgt dem Seitenverhältnis)
    const kl = r.height / B.logoBox[3], lw = B.logoBox[2] * kl, left = r.left + (r.width - lw) / 2;
    const signW = B.sign.w * kl, signCx = left + (B.sign.w / 2 - B.logoBox[0]) * kl;
    const signCy = r.top + (B.sign.y + B.sign.h / 2 - B.logoBox[1]) * kl;
    const size = signW * 1024 / B.iconSignW, lay = Math.max(size, lay0);
    const clip = `inset(0 ${100 - (((B.sign.w - B.logoBox[0]) * kl + 6) / lw) * 100}% 0 0)`;
    return { mode: 'svg', x: signCx, y: signCy - size * (B.iconSignCy - 512) / 1024, lay, end: size / lay,
      lx: left + lw / 2, ly: r.top + r.height / 2, lw, lh: r.height, clip };
  }

  // ------------------------------------------------ Bewegung reduzieren: keine Bewegung, ~1 s
  function reducedRun() {
    const h = 84 * k, w = h * BRANDS.suite.logoBox[2] / BRANDS.suite.logoBox[3];
    const big = put(o.images.suiteLogo, cx, cy, w, h);
    run(big, [{ opacity: 0 }, { opacity: 1 }], 0, 250, 'ease-out');
    run(big, [{ opacity: 1 }, { opacity: 0 }], 600, 200, 'ease-in');
    run(/** @type {HTMLElement} */ (bg), [{ opacity: 1 }, { opacity: 0 }], 700, 250, 'ease-out');
    for (const el of [...real, ...reveal]) run(el, [{ opacity: 0 }, { opacity: 1 }], 700, 250, 'ease-out');
    return control(950);
  }

  // ------------------------------------------------ Ende, Überspringen
  /** @param {number} total */
  function control(total) {
    let finished = false;
    const onKey = (/** @type {KeyboardEvent} */ e) => { if (!e.metaKey && !e.ctrlKey) finish(true); };
    const onResize = () => finish(true);                 // Ziele stimmen nicht mehr
    const onPointer = () => finish(true);
    /** @param {boolean} skipped */
    function finish(skipped) {
      if (finished) return; finished = true;
      clearTimeout(timer);
      removeEventListener('keydown', onKey); removeEventListener('resize', onResize); layer.removeEventListener('pointerdown', onPointer);
      // Deckkraft der echten Elemente merken, alle Animationen weg, Rest in 150 ms weich nachziehen (nie hart schneiden)
      const was = [...real, ...reveal].map(el => /** @type {const} */ ([el, Number(getComputedStyle(el).opacity)]));
      anims.forEach(a => a.cancel());
      document.documentElement.classList.remove('lsi-run');
      if (skipped) {
        layer.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 150, fill: 'forwards' });
        for (const [el, op] of was) if (op < 1) el.animate([{ opacity: op }, { opacity: 1 }], { duration: 150 });
        setTimeout(() => layer.remove(), 160);
      } else layer.remove();                             // alles liegt deckungsgleich: Schicht weg, kein Sprung
      announce(skipped, true);
    }
    let timer = 0;
    if (holdAt !== null) {
      anims.forEach(a => { a.pause(); a.currentTime = holdAt / speed; });
      skip.remove();
      return { finish, done };
    }
    timer = window.setTimeout(() => finish(false), total / speed);
    addEventListener('keydown', onKey);
    addEventListener('resize', onResize);
    layer.addEventListener('pointerdown', onPointer);
    return { finish, done };
  }
}
