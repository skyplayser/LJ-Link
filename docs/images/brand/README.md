# LJ Link brand files

Logo "Fader" (since 2026-11-13): dark tile, "LJ" in Inter Display ExtraBold, five console faders in the suite colours
`#FF3B3B #FFB020 #3DDC84 #22C8F0 #B45CFF` on rails `#272C39`. The name is written **LJ Link** (no hyphen).

| File | Use |
|---|---|
| `../logo.png` | 512, tile without margin: favicon, apple-touch-icon, website, README, JSON-LD |
| `../logo-wordmark-dark.png` | wordmark for dark backgrounds |
| `../logo-wordmark-light.png` | wordmark for light backgrounds |
| `icon-1024.png` | app icon with the macOS margin |
| `avatar-512.png`, `avatar-1024.png` | square profile picture without corners, safe for circle crops (Discord server icon, bot avatar, Linktree, Instagram) |
| `social-preview-1280x640.png` | GitHub social preview (repository settings) |

All files are made by `python3 .github/scripts/brand/render_brand.py <InterDisplay-ExtraBold.ttf>` (needs Pillow and
Google Chrome for the headless rendering). Font: Inter, SIL Open Font License 1.1, see `Inter-OFL.txt`. The PNGs contain no font software.
