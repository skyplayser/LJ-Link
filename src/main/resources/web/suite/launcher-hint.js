// Copied unchanged from the Suite-Launcher project, design/launcher-hinweis/ (module 1.0, 16 Nov 2026).
// Do not edit here: changes belong in the Suite-Launcher, then copy again.
// Link-Suite · Hinweiskarte „Link-Suite laden“ für die Startbildschirme aller Programme (Baustein 1.0, 16.11.2026).
// Zeigt eine kleine, ruhige Karte, wenn der Launcher Link-Suite NICHT installiert ist. Die Prüfung macht der
// Hauptprozess (launcher-detect.ts / LauncherDetect.java) – lokal, ohne Netz. Ins Netz geht erst etwas, wenn jemand auf
// „Link-Suite laden“ klickt, und dann nur der Browser mit der Seite der Suite.
// ES-Modul ohne Abhängigkeiten. CSP-fest: kein innerHTML, keine style-Attribute (nur Klassen und WAAPI).
// Einbau: EINBAU.md · Typen: launcher-hint.d.ts · Aussehen: launcher-hint.css

/** Seite, die je System die passende Datei anbietet. Öffnen nur über den Hauptprozess (shell.openExternal). */
export const LAUNCHER_PAGE = 'https://skyplayser.github.io/Link-Suite/';

export const TEXTS = {
  de: {
    title: 'Alle Programme an einem Ort',
    text: 'Link-Suite installiert und aktualisiert LJ Link, VJ-Link und die anderen mit einem Klick.',
    small: 'Kostenlos · macOS und Windows',
    get: 'Link-Suite laden',
    hide: 'Nicht mehr zeigen',
    aria: 'Hinweis: Link-Suite',
  },
  en: {
    title: 'All programs in one place',
    text: 'Link-Suite installs and updates LJ Link, VJ-Link and the others with one click.',
    small: 'Free · macOS and Windows',
    get: 'Get Link-Suite',
    hide: 'Don’t show again',
    aria: 'Note: Link-Suite',
  },
};

/**
 * @typedef {Object} LauncherHintOptions
 * @property {HTMLElement} host            Platz im Startbildschirm (die Karte wird hinein gehängt)
 * @property {{installed: boolean}|null} launcher  Ergebnis der Erkennung aus dem Hauptprozess; null = unbekannt → nichts zeigen
 * @property {string} iconSrc              Pfad zu icons/link-suite-icon.svg
 * @property {() => void} onGet            Klick „Link-Suite laden“: Hauptprozess öffnet LAUNCHER_PAGE im Browser
 * @property {() => void} onHide           Klick „Nicht mehr zeigen“: Programm merkt sich das
 * @property {boolean} [hidden=false]      Merker „Nicht mehr zeigen“ des Programms
 * @property {boolean} [allowed=true]      false auf Ausgängen, während Show/Messung, im Demo-/Screenshot-Modus ohne eigenen Schalter
 * @property {'de'|'en'|Partial<typeof TEXTS.de>} [lang='de']  Sprache oder eigene Texte (i18n des Programms)
 * @property {boolean|Promise<unknown>} [after]  erst danach einblenden, z. B. Promise von linksuite-intro-done; true = sofort
 * @property {boolean} [compact=false]     schmale Spalte: Knöpfe unter dem Text
 */

/**
 * Zeigt die Karte, falls angebracht. Gibt immer ein Steuerobjekt zurück.
 * @param {LauncherHintOptions} o
 * @returns {{ el: HTMLElement|null, remove(): void, update(launcher: {installed: boolean}|null): void }}
 */
export function showLauncherHint(o) {
  /** @type {HTMLElement|null} */ let card = null;
  const ctl = {
    el: /** @type {HTMLElement|null} */ (null),
    remove() { card?.remove(); card = null; ctl.el = null; },
    /** Neue Erkennung (z. B. nach Rückkehr ins Fenster): installiert → Karte weg */
    update(/** @type {{installed: boolean}|null} */ l) { if (!l || l.installed) ctl.remove(); },
  };
  if (!o.host || o.hidden || o.allowed === false || !o.launcher || o.launcher.installed) return ctl;

  const T = typeof o.lang === 'object' ? { ...TEXTS.de, ...o.lang } : TEXTS[o.lang === 'en' ? 'en' : 'de'];
  const el = (/** @type {string} */ tag, /** @type {string} */ cls, /** @type {string=} */ txt) => {
    const e = document.createElement(tag); if (cls) e.className = cls; if (txt != null) e.textContent = txt; return e;
  };
  const id = 'lsh-' + Math.random().toString(36).slice(2, 8);
  card = el('aside', 'lsh' + (o.compact ? ' lsh-compact' : ''));
  card.setAttribute('aria-labelledby', id + '-t');
  card.setAttribute('aria-describedby', id + '-d');
  const icon = /** @type {HTMLImageElement} */ (el('img', 'lsh-icon'));
  icon.src = o.iconSrc; icon.alt = ''; icon.draggable = false;
  const txt = el('div', 'lsh-txt');
  const title = el('b', 'lsh-title', T.title); title.id = id + '-t';
  const desc = el('p', 'lsh-text', T.text); desc.id = id + '-d';
  txt.append(title, desc, el('small', 'lsh-small', T.small));
  const acts = el('div', 'lsh-acts');
  const get = /** @type {HTMLButtonElement} */ (el('button', 'lsh-get', T.get));
  get.type = 'button';
  get.addEventListener('click', () => o.onGet());
  const hide = /** @type {HTMLButtonElement} */ (el('button', 'lsh-hide', T.hide));
  hide.type = 'button';
  hide.addEventListener('click', () => { o.onHide(); fadeOut(); });
  acts.append(get, hide);
  card.append(icon, txt, acts);
  card.classList.add('lsh-wait');                     // unsichtbar, bis das Intro fertig ist (Platz ist schon reserviert)
  o.host.append(card);
  ctl.el = card;

  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const show = () => {
    if (!card) return;
    card.classList.remove('lsh-wait');
    // sanftes Einblenden, keine Bewegung (auch nicht bei voller Bewegung – die Karte soll nicht auffallen)
    if (!reduced) card.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 400, easing: 'ease-out' });
  };
  const fadeOut = () => {
    const c = card; if (!c) return;
    if (reduced) { ctl.remove(); return; }
    c.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 200, easing: 'ease-in', fill: 'forwards' }).finished.then(() => ctl.remove(), () => ctl.remove());
  };
  if (o.after && typeof o.after === 'object' && 'then' in o.after) o.after.then(() => setTimeout(show, 250), show);
  else show();
  return ctl;
}
