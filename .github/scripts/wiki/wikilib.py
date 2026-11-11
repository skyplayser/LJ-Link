"""Building blocks for the LJ Link wiki: a documentation layout (section tabs, side navigation, notes, diagrams) that
build_site.py turns into English and German pages. Every translated element must contain plain text only, so mixed text is
split into spans that each carry their own data-de."""
import html, math, re
from pathlib import Path

BASE = "https://skyplayser.github.io/LJ-Link/"
DISCORD = "https://discord.gg/N6DMca4Bjk"
REPO = "https://github.com/skyplayser/LJ-Link"

# Pages about another maker's gear: the title and the heading start with our own name, and a note below the heading says
# that LJ Link is independent (file -> (what it works with en, de, the maker)).
BRANDS = {
    "wiki-pioneer.html": ("Pioneer DJ equipment", "Geräten von Pioneer DJ", "AlphaTheta / Pioneer DJ"),
    "wiki-denon.html": ("Denon DJ equipment", "Geräten von Denon DJ", "inMusic / Denon DJ"),
    "wiki-serato.html": ("Serato DJ Pro", "Serato DJ Pro", "Serato"),
    "wiki-ableton-link.html": ("Ableton Link", "Ableton Link", "Ableton AG"),
    "wiki-grandma2.html": ("grandMA2 consoles and grandMA2 onPC", "grandMA2-Pulten und grandMA2 onPC", "MA Lighting"),
    "wiki-grandma3.html": ("grandMA3 consoles and grandMA3 onPC", "grandMA3-Pulten und grandMA3 onPC", "MA Lighting"),
    "wiki-chamsys.html": ("ChamSys MagicQ", "ChamSys MagicQ", "ChamSys"),
    "wiki-daslight.html": ("Daslight", "Daslight", "Nicolaudie / Daslight"),
    "wiki-wolfmix.html": ("the Wolfmix", "dem Wolfmix", "Wolfmix"),
}


def compat_note(file):
    """The independence note below the heading of a maker page (empty for other pages)."""
    if file not in BRANDS:
        return ""
    en, de, maker = BRANDS[file]
    extra = (" Ableton and Link are trademarks of Ableton AG.", " Ableton und Link sind Marken der Ableton AG.") \
        if maker == "Ableton AG" else ("", "")
    return el("p", "LJ Link is an independent program that works with %s. It is not a product of, or endorsed by, %s.%s"
              % (en, maker, extra[0]),
              "LJ Link ist ein unabhängiges Programm und arbeitet mit %s zusammen. Es ist kein Produkt %s und wird "
              "vom Hersteller nicht unterstützt.%s" % (de, "der Ableton AG" if maker == "Ableton AG" else "von " + maker, extra[1]),
              cls="small compat")


def esc(s):
    return html.escape(s, quote=True)


# ------------------------------------------------------------------ inline text
# `code`, **bold**, [link](href) and *italic*. Code, bold and links are tried first at every position, so stars inside
# code, link targets or a **bold** pair are never italic. *italic* needs a star that is not part of ** and not glued to a
# letter or digit outside (so 2*3 or file*name stay plain), text that does not start or end with a space and has no star.
_TOK = re.compile(r"`([^`]+)`|\*\*(.+?)\*\*|\[([^\]]+)\]\(([^)\s]+)\)"
                  r"|(?<![*\w])\*(?![\s*])([^*\n]+?)(?<![\s*])\*(?![*\w])")


def _tokens(s):
    out, i = [], 0
    for m in _TOK.finditer(s):
        out.append(("t", s[i:m.start()]))
        if m[1] is not None:
            out.append(("c", m[1]))
        elif m[2] is not None:
            out.append(("b", m[2]))
        elif m[5] is not None:
            out.append(("i", m[5]))
        else:
            out.append(("a", m[3], m[4]))
        i = m.end()
    out.append(("t", s[i:]))
    return out


def inline(en, de=None):
    """Text with `code`, **bold**, *italic* and [links](href) in both languages. The specials must appear in the same order."""
    de = en if de is None else de
    te, td = _tokens(en), _tokens(de)
    if [t[0] for t in te] != [t[0] for t in td] or [t[2] for t in te if t[0] == "a"] != [t[2] for t in td if t[0] == "a"]:
        raise ValueError("EN and DE differ in structure:\n  %s\n  %s" % (en, de))
    out = []
    for e, d in zip(te, td):
        kind = e[0]
        if kind == "t":
            if not e[1] and not d[1]:
                continue
            out.append(esc(e[1]) if e[1] == d[1] else '<span data-de="%s">%s</span>' % (esc(d[1]), esc(e[1])))
        elif kind == "c":
            out.append("<code>%s</code>" % esc(e[1]) if e[1] == d[1] else '<code data-de="%s">%s</code>' % (esc(d[1]), esc(e[1])))
        elif kind in ("b", "i"):
            out.append("<%s>%s</%s>" % (kind, esc(e[1]), kind) if e[1] == d[1]
                       else '<%s data-de="%s">%s</%s>' % (kind, esc(d[1]), esc(e[1]), kind))
        else:
            ext = ' rel="noopener"' if e[2].startswith("http") else ""
            out.append('<a href="%s"%s>%s</a>' % (esc(e[2]), ext, esc(e[1])) if e[1] == d[1]
                       else '<a href="%s"%s data-de="%s">%s</a>' % (esc(e[2]), ext, esc(d[1]), esc(e[1])))
    return "".join(out)


def _plain(s):
    return not _TOK.search(s)


def el(tag, en, de=None, cls="", attrs=""):
    """An element with translated content: plain text carries data-de itself, mixed text is split into spans."""
    de = en if de is None else de
    c = ' class="%s"' % cls if cls else ""
    if _plain(en) and _plain(de):
        if en == de:
            return "<%s%s%s>%s</%s>" % (tag, c, attrs, esc(en), tag)
        return '<%s%s%s data-de="%s">%s</%s>' % (tag, c, attrs, esc(de), esc(en), tag)
    return "<%s%s%s>%s</%s>" % (tag, c, attrs, inline(en, de), tag)


# ------------------------------------------------------------------ page state
class Page:
    def __init__(self):
        self.toc = []


CUR = Page()


def slug(s):
    s = s.lower().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


def H2(en, de, id_=None):
    id_ = id_ or slug(en)
    CUR.toc.append((id_, en, de))
    return el("h2", en, de, attrs=' id="%s"' % id_)


def H3(en, de, id_=None):
    return el("h3", en, de, attrs=' id="%s"' % (id_ or slug(en)))


def P(en, de, cls=""):
    return el("p", en, de, cls)


def LI(en, de):
    return el("li", en, de)


def OL(*items):
    return "<ol>" + "".join(LI(*i) for i in items) + "</ol>"


def UL(*items):
    return "<ul>" + "".join(LI(*i) for i in items) + "</ul>"


def STEPS(*items):
    """Numbered steps: each item (en, de) or (en, de, extra_html)."""
    out = '<ol class="dsteps">'
    for it in items:
        out += "<li>" + el("p", it[0], it[1]) + (it[2] if len(it) > 2 else "") + "</li>"
    return out + "</ol>"


def PRE(s):
    return "<pre><code>" + esc(s) + "</code></pre>"


ICONS = {"info": "i", "tip": "✓", "warn": "!", "danger": "✕", "alpha": "α", "beta": "β", "note": "✎", "example": "▶"}
TITLES = {"info": ("Info", "Info"), "tip": ("Tip", "Tipp"), "warn": ("Warning", "Achtung"),
          "danger": ("Important", "Wichtig"), "alpha": ("Alpha: untested", "Alpha: ungetestet"),
          "beta": ("Beta", "Beta"), "note": ("Note", "Hinweis"), "example": ("Example", "Beispiel")}


def ADM(kind, *body, title=None):
    """A note box. body: (en, de) paragraphs or ready html strings."""
    t = title or TITLES[kind]
    parts = "".join(P(*b) if isinstance(b, tuple) else b for b in body)
    return ('<div class="adm %s"><p class="adm-t"><span class="adm-i" aria-hidden="true">%s</span>%s</p>%s</div>'
            % (kind, ICONS[kind], el("span", *t), parts))


def BADGE(kind, en=None, de=None):
    names = {"stable": ("Stable", "Stabil"), "beta": ("Beta", "Beta"), "alpha": ("Alpha", "Alpha"),
             "unk": ("Unconfirmed", "Unbestätigt"), "gen": ("Generic", "Allgemein"), "no": ("Not possible", "Nicht möglich"),
             "plan": ("Planned", "Geplant"), "rec": ("Recommended", "Empfohlen"), "ok": ("Works", "Geht"),
             "bad": ("Avoid", "Vermeiden")}
    en, de = (en, de) if en else names[kind]
    return el("span", en, de, cls="badge " + kind)


def cell(c):
    if isinstance(c, tuple):
        return "<td>%s</td>" % inline(*c)
    return "<td>%s</td>" % c


def TABLE(head, rows, cls=""):
    out = '<div class="table%s"><table>' % (" " + cls if cls else "")
    if head:
        out += "<thead><tr>" + "".join(el("th", *h) for h in head) + "</tr></thead>"
    out += "<tbody>"
    for r in rows:
        out += "<tr>" + "".join(cell(c) for c in r) + "</tr>"
    return out + "</tbody></table></div>"


def DETAILS(en, de, *body, id_=None):
    i = ' id="%s"' % id_ if id_ else ""
    return "<details%s>%s%s</details>" % (i, el("summary", en, de), "".join(P(*b) if isinstance(b, tuple) else b for b in body))


def IMG(src, en, de, cap=None):
    out = '<figure class="dfig"><img class="pic" src="images/%s" alt="%s" data-de-alt="%s" loading="lazy">' % (src, esc(en), esc(de))
    if cap:
        out += el("figcaption", *cap)
    return out + "</figure>"


def CARDS(*cards):
    """cards: (icon, href, title_en, title_de, text_en, text_de)"""
    out = '<div class="dcards">'
    for ico, href, te, td, pe, pd in cards:
        out += '<a class="dcard" href="%s"><span class="ico">%s</span>%s%s</a>' % (href, ico, el("b", te, td), el("span", pe, pd))
    return out + "</div>"


def SEC(*parts):
    return "".join(parts)


# ------------------------------------------------------------------ diagrams
COL = {"dj": ("#13233f", "#3b82f6"), "pc": ("#1a1530", "#8b5cf6"), "desk": ("#231708", "#ff8a00"),
       "net": ("#0f2219", "#22c55e"), "tab": ("#1b1b1b", "#9aa6b6"), "bad": ("#260d0d", "#ef4444")}
LINE = {"cable": ("#60a5fa", ""), "wifi": ("#a78bfa", "6 5"), "usb": ("#fbbf24", "2 4"), "same": ("#9aa6b6", "1 5"),
        "dmx": ("#ff8a00", "")}


def _clip(cx, cy, w, h, dx, dy):
    if dx == 0 and dy == 0:
        return cx, cy
    t = min((w / 2) / abs(dx) if dx else 1e9, (h / 2) / abs(dy) if dy else 1e9)
    return cx + dx * t, cy + dy * t


def DIAGRAM(width, height, nodes, edges, cap=None, label=("Diagram", "Diagramm")):
    """nodes: {id: (x, y, w, h, kind, (en, de), (sub_en, sub_de) or None)} with x, y the centre.
    edges: (a, b, style, (en, de) or None)."""
    s = ['<figure class="ddiag"><svg viewBox="0 0 %d %d" role="img" aria-label="%s" data-de-aria-label="%s" '
         'xmlns="http://www.w3.org/2000/svg">' % (width, height, esc(label[0]), esc(label[1]))]
    for a, b, style, lab in edges:
        ax, ay, aw, ah = nodes[a][:4]
        bx, by, bw, bh = nodes[b][:4]
        x1, y1 = _clip(ax, ay, aw, ah, bx - ax, by - ay)
        x2, y2 = _clip(bx, by, bw, bh, ax - bx, ay - by)
        color, dash = LINE[style]
        s.append('<line x1="%.0f" y1="%.0f" x2="%.0f" y2="%.0f" stroke="%s" stroke-width="2.5"%s/>'
                 % (x1, y1, x2, y2, color, ' stroke-dasharray="%s"' % dash if dash else ""))
        if lab:
            mx, my = (x1 + x2) / 2, (y1 + y2) / 2
            tw = max(len(lab[0]), len(lab[1])) * 6.6 + 14
            s.append('<rect x="%.0f" y="%.0f" width="%.0f" height="20" rx="6" fill="#07090c" stroke="%s"/>'
                     % (mx - tw / 2, my - 10, tw, color))
            s.append('<text x="%.0f" y="%.0f" text-anchor="middle" font-size="12" fill="%s" data-de="%s">%s</text>'
                     % (mx, my + 4, color, esc(lab[1]), esc(lab[0])))
    for nid, (x, y, w, h, kind, name, sub) in nodes.items():
        fill, stroke = COL[kind]
        s.append('<rect x="%.0f" y="%.0f" width="%d" height="%d" rx="10" fill="%s" stroke="%s" stroke-width="1.5"/>'
                 % (x - w / 2, y - h / 2, w, h, fill, stroke))
        ty = y + (-3 if sub else 5)
        s.append('<text x="%.0f" y="%.0f" text-anchor="middle" font-size="14" font-weight="600" fill="#eef2f7" data-de="%s">%s</text>'
                 % (x, ty, esc(name[1]), esc(name[0])))
        if sub:
            s.append('<text x="%.0f" y="%.0f" text-anchor="middle" font-size="11.5" fill="#9aa6b6" data-de="%s">%s</text>'
                     % (x, y + 14, esc(sub[1]), esc(sub[0])))
    s.append("</svg>")
    if cap:
        s.append(el("figcaption", *cap))
    s.append("</figure>")
    return "".join(s)


LEGEND = ('<p class="dlegend"><span class="lg cable"></span>' + el("span", "network cable", "Netzwerkkabel")
          + '<span class="lg wifi"></span>' + el("span", "Wi-Fi", "WLAN")
          + '<span class="lg usb"></span>' + el("span", "USB", "USB")
          + '<span class="lg same"></span>' + el("span", "same computer", "selber Computer") + "</p>")


# ------------------------------------------------------------------ site tree and layout
TREE = []  # [(group_en, group_de, [(file, en, de, icon)])]
PAGES = {}  # file -> builder function


def group(en, de):
    TREE.append((en, de, []))


def page(file, en, de):
    """Registers a page in the current group; the decorated function returns
    (title (en, de), description (en, de), kicker (en, de), h1 (en, de), lead (en, de), body html)."""
    def deco(fn):
        TREE[-1][2].append((file, en, de))
        PAGES[file] = fn
        return fn
    return deco


def _flat():
    return [(g, p) for g in TREE for p in g[2]]


def _head(file, title, desc):
    og = title if title[0].startswith("LJ Link") else ("LJ Link Wiki – " + title[0], "LJ Link Wiki – " + title[1])
    suffix = " | Wiki" if title[0].startswith("LJ Link") else " | LJ Link Wiki"  # no "LJ Link" twice
    return f'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title data-de="{esc(title[1])}{suffix}">{esc(title[0])}{suffix}</title>
<meta name="description" content="{esc(desc[0])}" data-de-content="{esc(desc[1])}">
<meta name="robots" content="index, follow, max-image-preview:large, max-snippet:-1">
<meta name="author" content="SKYPLAYSER / NEXTPHILM">
<link rel="canonical" href="{BASE}{file}" data-de-href="{BASE}de/{file}">
<link rel="alternate" hreflang="en" href="{BASE}{file}">
<link rel="alternate" hreflang="de" href="{BASE}de/{file}">
<link rel="alternate" hreflang="x-default" href="{BASE}{file}">
<link rel="icon" href="images/logo.png" type="image/png">
<link rel="apple-touch-icon" href="images/logo.png">
<meta name="theme-color" content="#07090c">
<meta property="og:type" content="article">
<meta property="og:site_name" content="LJ Link">
<meta property="og:locale" content="en_US" data-de-content="de_DE">
<meta property="og:locale:alternate" content="de_DE" data-de-content="en_US">
<meta property="og:title" content="{esc(og[0])}" data-de-content="{esc(og[1])}">
<meta property="og:description" content="{esc(desc[0])}" data-de-content="{esc(desc[1])}">
<meta property="og:image" content="{BASE}images/og.png">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:url" content="{BASE}{file}" data-de-content="{BASE}de/{file}">
<meta name="twitter:card" content="summary_large_image">
<!-- structured-data -->
<link rel="stylesheet" href="style.css">
</head>
<body class="docs-page">
<header class="top">
  <div class="wrap dwrap">
    <a href="./" class="brand" style="display:flex;align-items:center;gap:10px"><img src="images/logo.png" alt="" width="32" height="32"><span>LJ Link</span><span class="brand-sub">Wiki</span></a>
    <button class="menu-btn" id="menu" aria-label="Menu" aria-expanded="false">&#9776;</button>
    <nav id="nav">
      <a href="./#features" data-de="Funktionen">Features</a>
      <a href="./#download">Download</a>
      <a href="wiki.html">Wiki</a>
      <a href="wiki-troubleshooting.html" data-de="Hilfe">Help</a>
      <a href="{DISCORD}" rel="noopener">Discord</a>
      <a href="{REPO}">GitHub</a>
      <a class="lang" id="lang" href="de/{file}" hreflang="de" lang="de" title="Deutsch" data-de-href="../{file}" data-de-hreflang="en" data-de-lang="en" data-de-title="English" data-de="EN" style="text-decoration:none">DE</a>
    </nav>
  </div>
</header>
'''


def render(site: Path, footer: str):
    flat = _flat()
    for idx, (g, (file, en, de)) in enumerate(flat):
        global CUR
        CUR = Page()
        title, desc, kicker, h1, lead, body = PAGES[file]()
        if file in BRANDS:  # our own name first on maker pages
            title = ("LJ Link: " + title[0], "LJ Link: " + title[1])
            h1 = ("LJ Link: " + h1[0], "LJ Link: " + h1[1])
        toc = CUR.toc
        # section tabs
        tabs = '<nav class="dtabs" aria-label="Wiki sections" data-de-aria-label="Wiki-Bereiche"><div class="dwrap">'
        for ge, gd, pages in TREE:
            on = " on" if ge == g[0] else ""
            tabs += '<a class="dtab%s" href="%s" data-de="%s">%s</a>' % (on, pages[0][0], esc(gd), esc(ge))
        tabs += "</div></nav>"
        # side navigation
        side = '<aside class="dside" id="dside"><nav aria-label="Wiki pages" data-de-aria-label="Wiki-Seiten">'
        for ge, gd, pages in TREE:
            side += el("p", ge, gd, cls="dgroup")
            for pf, pe, pd in pages:
                on = pf == file
                side += '<a class="dlink%s" href="%s"%s data-de="%s">%s</a>' % (
                    " on" if on else "", pf, ' aria-current="page"' if on else "", esc(pd), esc(pe))
                if on and toc:
                    side += '<div class="dsub">' + "".join(
                        '<a href="#%s" data-de="%s">%s</a>' % (i, esc(d), esc(e)) for i, e, d in toc) + "</div>"
        side += "</nav></aside>"
        # previous / next
        pn = '<nav class="dpn">'
        if idx > 0:
            pf, pe, pd = flat[idx - 1][1]
            pn += '<a class="prev" href="%s">%s%s</a>' % (pf, el("small", "Previous", "Zurück"), el("span", "← " + pe, "← " + pd))
        else:
            pn += "<span></span>"
        if idx < len(flat) - 1:
            pf, pe, pd = flat[idx + 1][1]
            pn += '<a class="next" href="%s">%s%s</a>' % (pf, el("small", "Next", "Weiter"), el("span", pe + " →", pd + " →"))
        pn += "</nav>"
        crumbs = ('<nav class="crumbs" aria-label="Breadcrumb"><a href="wiki.html">Wiki</a> › %s › %s</nav>'
                  % (el("span", *g[:2]), el("span", en, de)))
        main = f'''{tabs}
<div class="docs dwrap">
  <button class="dside-btn" id="dside-btn" aria-controls="dside" aria-expanded="false"><span aria-hidden="true">&#9776;</span> <span data-de="Inhalt des Wikis">Wiki contents</span></button>
  {side}
  <main class="dbody article wiki">
    {crumbs}
    <h1><span class="kicker" data-de="{esc(kicker[1])}">{esc(kicker[0])}</span><span data-de="{esc(h1[1])}">{esc(h1[0])}</span></h1>
    {compat_note(file)}
    {el("p", lead[0], lead[1], cls="tag")}
    {body}
    <div class="adm community"><p class="adm-t"><span class="adm-i" aria-hidden="true">💬</span><span data-de="Fehlt etwas oder stimmt etwas nicht?">Something missing or wrong?</span></p>{P("Ask in our [Discord](" + DISCORD + ") or [report a problem](wiki-troubleshooting.html#report). Reports from real gear help most, also when something does not work.", "Frag auf unserem [Discord](" + DISCORD + ") oder [melde ein Problem](wiki-troubleshooting.html#report). Am meisten helfen Berichte von echten Geräten, auch wenn etwas nicht klappt.")}</div>
    {pn}
  </main>
</div>
'''
        script = '''<script>
  (function () {
    var b = document.getElementById("dside-btn"), s = document.getElementById("dside");
    if (b && s) b.addEventListener("click", function () { var o = s.classList.toggle("open"); b.setAttribute("aria-expanded", o); });
    // Keep the current page visible in the side navigation and the current section in the tabs.
    var on = document.querySelector(".dlink.on"), side = document.getElementById("dside");
    if (on && side && side.scrollHeight > side.clientHeight) side.scrollTop = on.offsetTop - side.clientHeight / 3;
    var tab = document.querySelector(".dtab.on");
    if (tab) tab.parentNode.scrollLeft = tab.offsetLeft - 16;
    if (location.hash) { var d = document.querySelector(location.hash); if (d && d.tagName === "DETAILS") d.open = true; }
  })();
</script>
'''
        (site / file).write_text(_head(file, title, desc) + main + footer.replace("<script>", script + "<script>", 1), encoding="utf-8")
    return [p[0] for _, p in flat]


CSS = """
/* Wiki docs layout */
.docs-page .dwrap { max-width: 1320px; margin: 0 auto; padding: 0 24px; }
.brand-sub { color: var(--muted); font-weight: 500; border-left: 1px solid var(--line); padding-left: 10px; margin-left: 2px; }
.dtabs { background: var(--panel); border-bottom: 1px solid var(--line); position: sticky; top: 61px; z-index: 4; }
.dtabs .dwrap { display: flex; gap: 4px; overflow-x: auto; scrollbar-width: none; }
.dtabs .dwrap::-webkit-scrollbar { display: none; }
.dtab { color: var(--muted); text-decoration: none; padding: 13px 14px 11px; font-size: 15px; white-space: nowrap; border-bottom: 2px solid transparent; }
.dtab:hover { color: var(--text); }
.dtab.on { color: var(--text); font-weight: 600; border-bottom-color: var(--accent); }
.docs { display: grid; grid-template-columns: 250px minmax(0, 1fr); gap: 56px; align-items: start; }
.dside { position: sticky; top: 124px; max-height: calc(100vh - 140px); overflow-y: auto; padding: 28px 8px 40px 0; font-size: 15px; scrollbar-width: thin; }
.dgroup { margin: 22px 0 6px; font-size: 12px; font-weight: 700; letter-spacing: .1em; text-transform: uppercase; color: var(--accent-2); }
.dgroup:first-child { margin-top: 0; }
.dlink { display: block; color: var(--muted); text-decoration: none; padding: 5px 0 5px 14px; border-left: 1px solid var(--line); line-height: 1.35; }
.dlink:hover { color: var(--text); }
.dlink.on { color: var(--text); font-weight: 600; border-left: 2px solid var(--accent); padding-left: 13px; }
.dsub { border-left: 1px solid var(--line); padding: 2px 0 6px; }
.dsub a { display: block; color: var(--muted); text-decoration: none; font-size: 14px; padding: 4px 0 4px 28px; line-height: 1.35; }
.dsub a:hover { color: var(--accent-2); }
.dside-btn { display: none; }
.dbody { max-width: 860px; padding-bottom: 20px; min-width: 0; }
.dbody .crumbs { padding-top: 30px; }
.dbody h1 { font-size: clamp(30px, 4.4vw, 44px); margin: 14px 0 12px; }
.dbody h1 .kicker { margin-bottom: 10px; }
.dbody .tag { margin: 0 0 8px; font-size: 19px; max-width: none; }
.dbody h2 { font-size: clamp(23px, 2.8vw, 29px); margin: 52px 0 12px; scroll-margin-top: 130px; padding-top: 8px; }
.dbody h3 { font-size: 19px; margin: 30px 0 8px; scroll-margin-top: 130px; color: var(--text); }
.dbody p, .dbody li { color: #c3ccd8; }
.dbody b { color: var(--text); }
.dbody ul, .dbody ol { padding-left: 22px; }
.dbody li { margin: 7px 0; }
.dbody .table { margin: 16px 0; }
.dbody td, .dbody th { font-size: 15.5px; }
.dbody td { color: #c3ccd8; }
.dbody pre { margin: 14px 0; }
.dbody pre code { background: none; border: 0; padding: 0; }
.dbody details { margin: 10px 0; }
.dbody details p, .dbody details li { color: #c3ccd8; }
.dsteps { list-style: none; counter-reset: st; padding: 0 !important; margin: 18px 0; }
.dsteps > li { counter-increment: st; position: relative; padding: 2px 0 6px 46px; margin: 0 0 12px; }
.dsteps > li::before { content: counter(st); position: absolute; left: 0; top: 2px; width: 30px; height: 30px; border-radius: 50%; background: var(--panel-2); border: 1px solid var(--accent); color: var(--text); display: grid; place-items: center; font-weight: 700; font-size: 14px; }
.dsteps > li > p { margin: 3px 0 4px; }
.dsteps > li > ul, .dsteps > li > .table, .dsteps > li > pre, .dsteps > li > .adm { margin-top: 8px; }
.adm { background: #0e1520; border: 1px solid #1e3a6e; border-left: 4px solid var(--accent); border-radius: 10px; padding: 12px 18px 4px; margin: 20px 0; }
.adm-t { display: flex; align-items: center; gap: 10px; font-weight: 700; color: var(--text) !important; margin: 0 0 4px; }
.adm-i { width: 22px; height: 22px; border-radius: 50%; display: inline-grid; place-items: center; font-size: 12px; font-weight: 800; background: var(--accent); color: #fff; flex: 0 0 auto; }
.adm p { margin: 6px 0 10px; }
.adm ul, .adm ol { margin: 4px 0 10px; }
.adm.tip { background: #0c1a13; border-color: #14532d; border-left-color: var(--green); }
.adm.tip .adm-i { background: var(--green); }
.adm.warn, .adm.beta { background: #1a160b; border-color: #5c4a14; border-left-color: #fbbf24; }
.adm.warn .adm-i, .adm.beta .adm-i { background: #fbbf24; color: #1a160b; }
.adm.danger { background: #1d0d0d; border-color: #7f1d1d; border-left-color: var(--live); }
.adm.danger .adm-i { background: var(--live); }
.adm.alpha { background: #1d1008; border-color: #7c2d12; border-left-color: var(--orange); }
.adm.alpha .adm-i { background: var(--orange); }
.adm.note { background: var(--panel); border-color: var(--line); border-left-color: var(--muted); }
.adm.note .adm-i { background: var(--muted); color: var(--bg); }
.adm.example { background: #150f24; border-color: #3b2a6b; border-left-color: #8b5cf6; }
.adm.example .adm-i { background: #8b5cf6; }
.adm.community { margin-top: 56px; background: #121027; border-color: #3b2a6b; border-left-color: #8b5cf6; }
.adm.community .adm-i { background: transparent; font-size: 16px; }
.dcards { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; margin: 16px 0; }
.dcard { display: flex; flex-direction: column; gap: 4px; background: var(--panel); border: 1px solid var(--line); border-radius: 12px; padding: 16px; text-decoration: none; color: var(--muted); font-size: 15px; line-height: 1.45; }
.dcard:hover { border-color: var(--accent); }
.dcard b { color: var(--text); font-size: 16.5px; }
.dcard .ico { margin-bottom: 6px; }
.dfig, .ddiag { margin: 20px 0; }
.dfig img { width: 100%; height: auto; border-radius: 12px; border: 1px solid var(--line); display: block; }
.ddiag svg { width: 100%; height: auto; display: block; background: var(--panel); border: 1px solid var(--line); border-radius: 12px; }
.dfig figcaption, .ddiag figcaption { font-size: 14.5px; margin-top: 8px; }
.dlegend { font-size: 14px; color: var(--muted) !important; display: flex; flex-wrap: wrap; gap: 6px 14px; align-items: center; margin: -8px 0 18px; }
.lg { display: inline-block; width: 26px; height: 0; border-top: 3px solid; margin-right: -8px; }
.lg.cable { border-color: #60a5fa; } .lg.wifi { border-color: #a78bfa; border-top-style: dashed; }
.lg.usb { border-color: #fbbf24; border-top-style: dotted; } .lg.same { border-color: #9aa6b6; border-top-style: dotted; }
.badge.plan { color: var(--muted); border-color: var(--line); background: var(--panel-2); }
.badge.rec { color: #34d399; border-color: #14532d; background: #0c1f17; }
.badge.ok { color: var(--accent-2); border-color: #1e3a6e; background: #0d1a30; }
.badge.bad { color: #f87171; border-color: #7f1d1d; background: #260d0d; }
.dpn { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 28px; }
.dpn a { display: flex; flex-direction: column; gap: 2px; background: var(--panel); border: 1px solid var(--line); border-radius: 12px; padding: 12px 16px; text-decoration: none; color: var(--text); font-weight: 600; }
.dpn a:hover { border-color: var(--accent); }
.dpn small { color: var(--muted); font-weight: 400; font-size: 13px; }
.dpn .next { text-align: right; grid-column: 2; }
.docs-page footer { margin-top: 40px; }
@media (max-width: 960px) {
  .docs { grid-template-columns: minmax(0, 1fr); gap: 0; }
  .dtabs { top: 60px; }
  .dside-btn { display: inline-flex; gap: 8px; align-items: center; margin: 18px 0 0; background: var(--panel); color: var(--text); border: 1px solid var(--line); border-radius: 10px; padding: 9px 14px; font: inherit; font-size: 15px; cursor: pointer; justify-self: start; }
  .dside { display: none; position: static; max-height: none; padding: 8px 0 0; }
  .dside.open { display: block; border-bottom: 1px solid var(--line); padding-bottom: 18px; }
  .dbody .crumbs { padding-top: 16px; }
}
@media (max-width: 520px) {
  .docs-page .dwrap { padding: 0 16px; }
  .dpn { grid-template-columns: 1fr; }
  .dpn .next { grid-column: 1; }
  .dsteps > li { padding-left: 40px; }
}
"""
