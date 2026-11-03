#!/usr/bin/env python3
"""Builds the website in place for GitHub Pages.

    python3 .github/scripts/build_site.py site docs/images

- copies the screenshots (and the German ones to images/de/) and adds WebP versions in two sizes,
- makes de/index.html from the English index.html: every data-de="…" replaces the element's text,
  every data-de-<attr>="…" replaces that attribute, links get ../ and German screenshots are used,
- wraps screenshots in <picture> with the WebP versions,
- writes the structured data (SoftwareApplication, WebSite, FAQPage from the FAQ section),
- writes sitemap.xml with both languages and the screenshots.
"""
import datetime
import html
import json
import re
import shutil
import sys
from pathlib import Path

from PIL import Image

BASE = "https://skyplayser.github.io/LJ-Link-PRO/"
REPO = "https://github.com/skyplayser/LJ-Link-PRO"
NO_WEBP = {"logo", "og"}  # used by other sites and apps as PNG
WIDTHS = (1400, 2800)


def copy_images(site, images):
    out = site / "images"
    for sub in ("", "de"):
        (out / sub).mkdir(parents=True, exist_ok=True)
        for png in sorted((images / sub).glob("*.png")):
            shutil.copy2(png, out / sub / png.name)
            if png.stem in NO_WEBP:
                continue
            with Image.open(png) as im:
                im = im.convert("RGB")
                for w in WIDTHS:
                    small = im if im.width <= w else im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
                    name = png.stem + ".webp" if w == WIDTHS[-1] else "%s-%d.webp" % (png.stem, w)
                    small.save(out / sub / name, "WEBP", quality=80, method=6)


def to_german(page, site):
    page = page.replace('<html lang="en">', '<html lang="de">', 1)
    # Text first, so data-de="…" is not mistaken for an attribute value below.
    page = re.sub(r'<(\w+)([^>]*?)\sdata-de="([^"]*)"([^>]*)>[^<]*</\1>',
                  lambda m: "<%s%s%s>%s</%s>" % (m[1], m[2], m[4], m[3], m[1]), page)

    def attrs(m):
        tag = m[0]
        for name, value in re.findall(r'\sdata-de-([\w:-]+)="([^"]*)"', tag):
            tag = re.sub(r'(\s%s=)"[^"]*"' % re.escape(name), lambda a: '%s"%s"' % (a[1], value), tag, count=1)
        return tag
    page = re.sub(r"<[^>]+>", attrs, page)

    page = re.sub(r'((?:src|href)=")(images/|impressum\.html|datenschutz\.html)', r"\1../\2", page)
    return re.sub(r'(src="\.\./images/)([\w-]+\.png")',
                  lambda m: m[1] + ("de/" if (site / "images" / "de" / m[2][:-1]).exists() else "") + m[2], page)


def pictures(page, site):
    def wrap(m):
        tag, path = m[0], m[1]
        local = site / re.sub(r"^(\.\./)+", "", path)
        if not local.with_suffix(".webp").exists():
            return tag
        stem = path[:-4]
        sizes = "(max-width: 1120px) 100vw, 1080px" if 'class="shot"' in tag else "(max-width: 860px) 100vw, 540px"
        srcset = "%s-%d.webp %dw, %s.webp %dw" % (stem, WIDTHS[0], WIDTHS[0], stem, WIDTHS[1])
        return '<picture><source type="image/webp" srcset="%s" sizes="%s">%s</picture>' % (srcset, sizes, tag)
    return re.sub(r'<img\s[^>]*?src="((?:\.\./)?images/[^"]+\.png)"[^>]*>', wrap, page)


def text(fragment):
    return html.unescape(re.sub(r"<[^>]+>", "", fragment)).strip()


def structured_data(page, lang, url):
    meta = lambda prop: html.unescape(re.search(r'<meta (?:name|property)="%s" content="([^"]*)"' % prop, page)[1])
    shots = [BASE + p for p in dict.fromkeys(re.findall(r'<img [^>]*?src="(?:\.\./)?(images/[^"]+\.png)"', page))
             if not p.endswith("logo.png")]
    features = [text(f) for f in re.findall(r'<div class="card"><h3><span class="ico"[^>]*>[^<]*</span>(.*?)</h3>',
                                            page.split('id="features"')[1].split("</section>")[0])]
    faq = page.split('id="faq"')[1].split("</section>")[0]
    graph = [
        {"@type": "WebSite", "@id": BASE + "#website", "name": "LJ-Link-PRO", "url": BASE, "inLanguage": ["en", "de"]},
        {
            "@type": "SoftwareApplication", "@id": BASE + "#app", "name": "LJ-Link-PRO", "url": url,
            "description": meta("description"), "inLanguage": lang,
            "applicationCategory": "MultimediaApplication", "applicationSubCategory": "DJ and lighting software",
            "operatingSystem": "macOS 12+, Windows 10, Windows 11",
            "offers": {"@type": "Offer", "price": "0", "priceCurrency": "EUR"}, "isAccessibleForFree": True,
            "downloadUrl": REPO + "/releases/latest", "installUrl": url + "#download",
            "image": BASE + "images/logo.png", "screenshot": shots, "featureList": features,
            "keywords": "lighting jockey, LJ, Lichtjockey, DJ waveform, Pro DJ Link, StageLinQ, CDJ-3000, "
                        "Denon Prime, grandMA2, grandMA3, Daslight, BPM, tap tempo, Veranstaltungstechnik",
            "author": {"@type": "Organization", "name": "NEXTPHILM", "url": "https://github.com/skyplayser"},
            "sameAs": [REPO],
        },
        {
            "@type": "FAQPage", "@id": url + "#faq", "inLanguage": lang,
            "mainEntity": [{"@type": "Question", "name": text(q),
                            "acceptedAnswer": {"@type": "Answer", "text": text(a)}}
                           for q, a in re.findall(r"<summary[^>]*>(.*?)</summary>(.*?)</details>", faq, re.S)],
        },
    ]
    block = json.dumps({"@context": "https://schema.org", "@graph": graph}, ensure_ascii=False)
    return page.replace("<!-- structured-data -->", '<script type="application/ld+json">%s</script>' % block, 1)


def sitemap(site, page):
    today = datetime.date.today().isoformat()
    alt = "".join('\n    <xhtml:link rel="alternate" hreflang="%s" href="%s"/>' % (l, BASE + p)
                  for l, p in (("en", ""), ("de", "de/"), ("x-default", "")))
    urls = []
    for path, sub in (("", ""), ("de/", "de/")):
        imgs = "".join("\n    <image:image><image:loc>%simages/%s%s</image:loc></image:image>"
                       % (BASE, sub if (site / "images" / sub / f).exists() else "", f)
                       for f in dict.fromkeys(re.findall(r'src="images/([\w-]+\.png)"', page)) if f != "logo.png")
        urls.append("  <url>\n    <loc>%s</loc>\n    <lastmod>%s</lastmod>%s%s\n  </url>" % (BASE + path, today, alt, imgs))
    (site / "sitemap.xml").write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" '
        'xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns:image="http://www.google.com/schemas/sitemap-image/1.1">\n'
        + "\n".join(urls) + "\n</urlset>\n", encoding="utf-8")


def main():
    site, images = Path(sys.argv[1]), Path(sys.argv[2])
    copy_images(site, images)
    source = (site / "index.html").read_text(encoding="utf-8")
    strip = lambda p: re.sub(r'\sdata-de(?:-[\w:-]+)?="[^"]*"', "", p)
    en = structured_data(pictures(source, site), "en", BASE)
    de = to_german(source, site)
    de = structured_data(pictures(de, site), "de", BASE + "de/")
    (site / "de").mkdir(exist_ok=True)
    (site / "index.html").write_text(strip(en), encoding="utf-8")
    (site / "de" / "index.html").write_text(strip(de), encoding="utf-8")
    sitemap(site, source)


if __name__ == "__main__":
    main()
