"""Writes the wiki pages (site/wiki*.html) and the wiki styles at the end of site/style.css.

    python3 .github/scripts/wiki/build.py site

The page texts are in c_*.py (English and German side by side), the layout in wikilib.py. Run this after a change and
commit the generated pages; build_site.py then makes the German pages from them as for every other page.
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import wikilib
import c_start, c_network, c_sources, c_desks, c_cases, c_help  # noqa: registers the pages in order

site = Path(sys.argv[1])
guide = (site / "pioneer-bpm-grandma.html").read_text(encoding="utf-8")
footer = guide[guide.index("<footer>"):]  # the same footer and scripts (language, phone menu) as the guides
files = wikilib.render(site, footer)
css = (site / "style.css").read_text(encoding="utf-8")
marker = "/* Wiki docs layout */"
if marker in css:
    css = css[:css.index(marker)].rstrip() + "\n"
(site / "style.css").write_text(css.rstrip() + "\n" + wikilib.CSS.lstrip("\n"), encoding="utf-8")
print(len(files), "pages:", " ".join(files))
