#!/usr/bin/env python3
"""Renders the LJ Link logo files ("Fader", chosen 2026-11-13) with headless Chrome.

    python3 .github/scripts/brand/render_brand.py [path/to/InterDisplay-ExtraBold.ttf]

Geometry and colours follow the suite logo package (render.cjs, mark() and icon()); the wordmark says
"LJ Link" (no hyphen). Font: Inter Display ExtraBold, SIL OFL 1.1 (docs/images/brand/Inter-OFL.txt); the PNGs
contain no font software. Writes:

    docs/images/logo.png                       icon cropped to the tile, 512 (favicon, apple-touch-icon, site, README)
    docs/images/logo-wordmark-dark.png         wordmark for dark backgrounds
    docs/images/logo-wordmark-light.png        wordmark for light backgrounds
    docs/images/brand/icon-1024.png            app icon with the macOS margin, 1024
    docs/images/brand/avatar-512.png           square profile picture without corners (Discord, Linktree, Instagram)
    docs/images/brand/avatar-1024.png          same, 1024
    docs/images/brand/social-preview-1280x640.png  GitHub social preview
"""
import base64
import io
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
if len(sys.argv) < 2:
    sys.exit("usage: render_brand.py <path to InterDisplay-ExtraBold.ttf>")
FONT = Path(sys.argv[1])

SCRIPT = r"""
const C=['#FF3B3B','#FFB020','#3DDC84','#22C8F0','#B45CFF'];
const DARK='#272C39';
function rr(g,x,y,w,h,r){g.beginPath();g.roundRect(x,y,w,h,r);g.fill()}
function fader(g,x,y,w,h){
  const s=w/5.6, gap=(w-5*s)/4, lv=[0.62,0.28,0.78,0.45,0.16];
  C.forEach((c,i)=>{const cx=x+i*(s+gap)+s/2, tw=Math.max(2,s*0.2);
    g.fillStyle=DARK;rr(g,cx-tw/2,y,tw,h,tw/2);
    const ch=Math.max(4,h*0.24), cy=y+(h-ch)*lv[i];
    g.fillStyle=c;rr(g,x+i*(s+gap),cy,s,ch,Math.min(s,ch)*0.28)});
}
function tileFill(g){const gr=g.createLinearGradient(100,100,924,924);gr.addColorStop(0,'#1A1E2A');gr.addColorStop(1,'#0D1016');return gr}
function content(g){
  g.fillStyle='#F2F4F8';g.font='800 320px IDX';g.textAlign='center';g.textBaseline='alphabetic';g.letterSpacing='-4px';
  g.fillText('LJ',512,520);fader(g,230,600,564,220);
}
// App icon (1024 frame, tile with margin), as in render.cjs icon('fader').
function icon(n){const c=document.createElement('canvas');c.width=c.height=n;const g=c.getContext('2d');g.setTransform(n/1024,0,0,n/1024,0,0);
  g.fillStyle=tileFill(g);rr(g,101.5,101.5,821,821,196);g.strokeStyle='#2A2F3D';g.lineWidth=3;g.beginPath();g.roundRect(101.5,101.5,821,821,196);g.stroke();
  g.save();g.beginPath();g.roundRect(101.5,101.5,821,821,196);g.clip();content(g);g.restore();return c}
// Square profile picture: tile colours fill the whole square, content centred for circle crops.
function avatar(n){const c=document.createElement('canvas');c.width=c.height=n;const g=c.getContext('2d');g.setTransform(n/1024,0,0,n/1024,0,0);
  const gr=g.createLinearGradient(0,0,1024,1024);gr.addColorStop(0,'#1A1E2A');gr.addColorStop(1,'#0D1016');g.fillStyle=gr;g.fillRect(0,0,1024,1024);
  g.translate(512,512);g.scale(1.06,1.06);g.translate(-512,-553);content(g);return c}
// Wordmark, as in render.cjs mark('fader', fill), text "LJ Link".
function mark(fill){const c=document.createElement('canvas');c.width=1400;c.height=288;const g=c.getContext('2d');g.scale(2,2);
  fader(g,28,34,166,80);
  g.fillStyle=fill;g.font='800 92px IDX';g.textAlign='left';g.textBaseline='alphabetic';g.letterSpacing='-1px';g.fillText('LJ Link',218,104);
  const w=Math.ceil(218+g.measureText('LJ Link').width+28);const o=document.createElement('canvas');o.width=w*2;o.height=288;o.getContext('2d').drawImage(c,0,0);return o}
// GitHub social preview 1280 x 640.
function social(){const W=1280,H=640;const c=document.createElement('canvas');c.width=W;c.height=H;const g=c.getContext('2d');
  const bg=g.createLinearGradient(0,0,W,H);bg.addColorStop(0,'#10131B');bg.addColorStop(1,'#07090C');g.fillStyle=bg;g.fillRect(0,0,W,H);
  const t=icon(1024);g.drawImage(t,101,101,822,822,110,150,340,340);
  g.textAlign='left';g.textBaseline='alphabetic';
  g.fillStyle='#F2F4F8';g.font='800 132px IDX';g.letterSpacing='-2px';g.fillText('LJ Link',520,300);
  g.fillStyle='#AEB7C6';g.font='800 36px IDX';g.letterSpacing='0px';
  g.fillText('Show your LJ what the sound does.',524,376);
  g.fillStyle='#7D8798';g.font='800 28px IDX';
  g.fillText('Every deck, every waveform, every cue,',524,432);g.fillText('live in one view. Free for macOS and Windows.',524,470);
  C.forEach((col,i)=>{g.fillStyle=col;rr(g,524+i*58,512,44,14,4)});
  return c}
(async()=>{await document.fonts.load('800 92px IDX');
  const out={icon:icon(1024).toDataURL(),avatar512:avatar(512).toDataURL(),avatar1024:avatar(1024).toDataURL(),
    markDark:mark('#F2F3F7').toDataURL(),markLight:mark('#141821').toDataURL(),social:social().toDataURL()};
  document.getElementById('out').textContent=JSON.stringify(out);})();
"""


def main():
    font = base64.b64encode(FONT.read_bytes()).decode()
    page = ("<!doctype html><html><head><meta charset=utf-8><style>@font-face{font-family:IDX;src:url(data:font/ttf;base64,%s)}"
            "</style></head><body><pre id=out></pre><script>%s</script></body></html>" % (font, SCRIPT))
    with tempfile.TemporaryDirectory() as tmp:
        html = Path(tmp) / "brand.html"
        html.write_text(page)
        dom = subprocess.run([CHROME, "--headless", "--disable-gpu", "--no-first-run", "--user-data-dir=" + tmp + "/profile",
                              "--virtual-time-budget=15000", "--dump-dom", html.as_uri()],
                             capture_output=True, text=True, timeout=120).stdout
    data = json.loads(re.search(r'<pre id="out">(.*?)</pre>', dom, re.S)[1].replace("&quot;", '"').replace("&amp;", "&"))
    img = lambda k: Image.open(io.BytesIO(base64.b64decode(data[k].split(",", 1)[1]))).copy()

    images, brand = ROOT / "docs/images", ROOT / "docs/images/brand"
    brand.mkdir(exist_ok=True)
    icon = img("icon")
    save = lambda im, p: (im.save(p, optimize=True), print(p.relative_to(ROOT), im.size))
    save(icon, brand / "icon-1024.png")
    save(icon.crop((100, 100, 924, 924)).resize((512, 512), Image.LANCZOS), images / "logo.png")
    save(img("markDark"), images / "logo-wordmark-dark.png")
    save(img("markLight"), images / "logo-wordmark-light.png")
    save(img("avatar512"), brand / "avatar-512.png")
    save(img("avatar1024"), brand / "avatar-1024.png")
    save(img("social").convert("RGB"), brand / "social-preview-1280x640.png")


if __name__ == "__main__":
    main()
