# -*- coding: utf-8 -*-
"""Bundle docs/wiki/*.md into one self-contained browsable HTML page.

No markdown library is available here, so this converts the subset the wiki actually uses —
verified by census: h1-h4, GFM pipe tables, fenced code, inline code/bold/italic/links,
blockquotes, hr, ordered and unordered lists. No images, no raw HTML, no nested lists.
"""
# -*- coding: utf-8 -*-
"""Bundle docs/wiki/*.md into one self-contained, browsable HTML page.

    python tools/gen_wiki_bundle.py --out build/wiki.html
    python tools/gen_wiki_bundle.py --out build/wiki.html --mc-jar <path to a Minecraft client jar>

Run from the repo root. The markdown in docs/wiki is the source of truth; this only presents it.
No markdown library is required — the converter covers exactly the subset the wiki uses (h1-h4, GFM
pipe tables, fenced code, inline code/bold/italic/links, blockquotes, hr, ordered and unordered
lists). No images, no raw HTML and no nested lists appear in the wiki, so none are handled.

On top of the conversion it adds two things the plain markdown cannot show:
  * every shaped/shapeless recipe as a real 3x3 grid, generated from the recipe JSON so it can never
    drift from what the game loads;
  * the item's own sprite beside its name wherever a table cell is exactly an item name.

--mc-jar is OPTIONAL and off by default. With it, vanilla ingredients show their real icons, read out
of the given jar; without it they fall back to labelled colour tiles and the build still works.
Nothing extracted from the jar is ever written into the repo — see tools/wiki_art.py for why.
"""
import argparse, glob, io, os, re, html, json, shutil, subprocess, sys, tempfile, zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wiki_art
import wiki_calculator

SRC = "docs/wiki"
def _mod_version():
    """From gradle.properties, because a hand-kept copy of a version number is a version number that is
    wrong. This one said 0.6.1 while the mod said 0.7.0."""
    for line in io.open(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                                     "gradle.properties"), encoding="utf-8"):
        if line.startswith("mod_version="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("gradle.properties has no mod_version")


MOD_VERSION = _mod_version()
MC_VERSION = "1.21.1"

# English lives at the root of docs/wiki; every other language mirrors the same filenames in a
# subdirectory. All of them go into ONE page: the sprites and recipe grids are the bulk of the payload
# and are shared, so a second language costs only its text (~120 KB against ~950 KB of art).
LANGS = [("en", "English", ""), ("ru", "Русский", "ru"), ("uk", "Українська", "uk")]

# Sidebar group headings per language. A missing language falls back to English.
GROUP_LABELS = {
    "ru": {"Start": "Начало", "Gear": "Снасть", "Playing": "Игра", "Reference": "Справочник"},
    "uk": {"Start": "Початок", "Gear": "Снасть", "Playing": "Гра", "Reference": "Довідник"},
}

# Sidebar order and grouping, mirroring the wiki's own index. THIS LIST IS THE PUBLISHED WIKI: a page
# on disk that is not named here passes every checker and then does not exist for any reader. Six of
# them did exactly that. The assertion after the build is what makes the next omission loud.
GROUPS = [
    ("", ["README"]),
    ("Start", ["getting-started"]),
    ("Gear", ["rods", "reels-and-lines", "rigs-and-baits", "groundbait", "tackle-station", "crafting",
              "tools", "blocks", "keepnet", "tackle-box"]),
    ("Playing", ["fishing-mechanics", "water-and-conditions", "shoal", "ice-fishing", "sea-fishing",
                 "stocking"]),
    ("Reference", ["species", "species-reference", "calculator", "progression", "villager",
                   "order-board", "config", "electrofisher", "compatibility"]),
]


def check_groups_cover_disk():
    """Every page in docs/wiki must be named in GROUPS, or it is invisible to every reader.

    The comment above GROUPS has promised this check since six pages went missing that way. It
    was never actually written, and compatibility.md then did it a seventh time: added, linked
    from three READMEs and from two store descriptions, passing every checker, and absent from
    the published page for four days. Now the build refuses instead.
    """
    listed = {name for _, names in GROUPS for name in names}
    on_disk = {os.path.basename(f)[:-3] for f in glob.glob(os.path.join(SRC, "*.md"))}
    missing = sorted(on_disk - listed)
    phantom = sorted(listed - on_disk)
    if missing:
        raise SystemExit("gen_wiki_bundle: %d page(s) exist in %s but are not in GROUPS, so "
                         "they would not be published: %s"
                         % (len(missing), SRC, ", ".join(missing)))
    if phantom:
        raise SystemExit("gen_wiki_bundle: GROUPS names %d page(s) that do not exist: %s"
                         % (len(phantom), ", ".join(phantom)))
GITHUB = "https://github.com/qwazar14/riverfishing/blob/dev-0.7.0/docs/"

FISH_TEX = "common/src/main/resources/assets/riverfishing/textures/item/fish"


def find_ffmpeg():
    return shutil.which("ffmpeg") or next(
        (p for p in [r"C:\Program Files\ffmpeg-n7.1-latest-win64-gpl-7.1\bin\ffmpeg.exe"]
         if os.path.exists(p)), None)


def shrink_fish(dest, size):
    """Downscale the 256px fish art with NEAREST neighbour so the pixels stay crisp.

    Without ffmpeg we fall back to the originals: correct, just a heavier page (the 70 sprites are
    ~1.3 MB at full size against ~0.2 MB at 64px).
    """
    ff = find_ffmpeg()
    if not ff:
        print("  ffmpeg not found — embedding fish sprites at full 256px (bigger page)")
        return FISH_TEX
    os.makedirs(dest, exist_ok=True)
    for f in sorted(os.listdir(FISH_TEX)):
        if f.endswith(".png"):
            subprocess.run([ff, "-v", "error", "-y", "-i", os.path.join(FISH_TEX, f),
                            "-vf", "scale=%d:%d:flags=neighbor" % (size, size),
                            os.path.join(dest, f)], check=True)
    return dest


def find_mc_jar():
    """The loom cache already holds a client jar, so look there before falling back to colour tiles.

    Most of a recipe grid is vanilla — a rod is bamboo, stick and iron — so without these icons the rod
    and reel grids degrade to nothing but three-letter labels, which reads as "the grids are gone". The
    art is still not committed; it is read at build time and embedded only in the generated page.
    """
    pats = glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/*/minecraft-client.jar"))
    # Prefer the version this wiki documents; the cache also holds the port branches' jars, and a
    # different version can have renamed a texture we then silently miss.
    exact = [p for p in pats if os.path.basename(os.path.dirname(p)) == MC_VERSION]
    return (exact or sorted(pats, reverse=True) or [None])[0]


def unpack_mc(jar, dest):
    """Pull just the item and block textures out of a Minecraft jar, into a scratch dir."""
    with zipfile.ZipFile(jar) as z:
        names = [n for n in z.namelist()
                 if n.endswith(".png")
                 and (n.startswith("assets/minecraft/textures/item/")
                      or n.startswith("assets/minecraft/textures/block/"))]
        z.extractall(dest, names)
    print("  vanilla icons: %d textures from %s" % (len(names), os.path.basename(jar)))
    return dest


def slug(text):
    """GitHub-style heading anchor, so the wiki's own #links resolve."""
    s = re.sub(r"[^\w\s-]", "", text.strip().lower(), flags=re.U)
    return re.sub(r"[\s_]+", "-", s).strip("-")


def esc(t):
    return html.escape(t, quote=False)


# species.md carries an <img> per row for the GitHub view, written by tools/gen_wiki_md_sprites.py.
# Here they are worse than useless: this converter escapes inline HTML, so they printed as a wall of
# literal <img src="..."> text, AND they broke illustrate(), whose whole-cell match no longer saw a
# bare species name — so the one table people actually read lost its tiles in both languages.
MD_IMG = re.compile(r"<img[^>]*>\s*")


class Page:
    def __init__(self, pid, raw):
        self.pid = pid
        self.raw = MD_IMG.sub("", raw)
        self.title = ""
        self.headings = []      # (level, text, anchor)


def inline(text, page):
    """Inline spans. Code first so its contents are never re-processed."""
    spans = []

    def stash(m):
        spans.append("<code>%s</code>" % esc(m.group(1)))
        return "\x00%d\x00" % (len(spans) - 1)

    text = re.sub(r"`([^`]+)`", stash, text)
    text = esc(text)

    def link(m):
        label, target = m.group(1), m.group(2)
        if target.startswith(("http://", "https://")):
            return '<a href="%s" target="_blank" rel="noopener">%s</a>' % (target, label)
        if target.startswith("../"):
            return '<a href="%s%s" target="_blank" rel="noopener">%s</a>' % (
                GITHUB, target[3:], label)
        if target.startswith("#"):
            return '<a href="#%s--%s" class="x">%s</a>' % (page.pid, slug(target[1:]), label)
        m2 = re.match(r"([a-zA-Z0-9._-]+)\.md(?:#(.+))?$", target)
        if m2:
            pid = m2.group(1)
            anchor = ("--" + slug(m2.group(2))) if m2.group(2) else ""
            return '<a href="#%s%s" class="x">%s</a>' % (pid, anchor, label)
        return label

    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", link, text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<![*\w])\*([^*\n]+)\*(?!\*)", r"<em>\1</em>", text)
    for i, s in enumerate(spans):
        text = text.replace("\x00%d\x00" % i, s)
    return text


ALL_NAMES = {}          # display name -> item id, set per language in main()
_grid_swaps = []        # [(page, item)] replaced
_grid_left = []         # [(page, first cell, why)] left as letters, reported at the end

BACKTICKED = re.compile(r"`([^`]+)`")
LEGEND = re.compile(r"^\s*\w\s*=\s*\S")

# The two hand-drawn grids that are not in a table. They sit in prose on the page a new player reads
# first, so they are the grids that most deserve to be pictures — and there is no row to read a name
# from, which is why these are listed rather than resolved. Keyed by page and by which ASCII fence it
# is on that page, both of which are the same in all three languages; the drawing is language-neutral
# and only the paragraph around it is translated.
#
# The second is a teaching diagram, not one recipe: the plain string ring and the same ring with a line
# in the middle, which is how every thicker line is made. Both halves are real recipes, so both are
# drawn, and the arrow between them survives.
FENCE_GRIDS = {("getting-started", 0): ["stick_rod"],
               ("getting-started", 1): ["line_mono_010", "line_mono_014"]}


def pattern_rows(cell):
    """The grid written inside a table cell, or None if this cell is not a craft pattern.

    Up to 3 rows of up to 3, not exactly 3x3: a Minecraft recipe may be 2x2 or 2x3, and the wiki writes
    those the same short way the JSON does — the bite alarm is `·g· / gng` against a pattern of
    [" g ", "gng"]. Demanding three full rows silently skipped every small recipe.

    Still strict enough to be safe. getting-started.md has a row whose cells read `⇪ reel` · `⇩ ease
    off!`, using the same middle dot as a separator between fight prompts, and the rig tables are full
    of `Hook · Bait`; both blow the 3-character limit and neither is mistaken for a grid. The real
    safeguard is downstream anyway — nothing is drawn until the shape matches the recipe JSON.
    """
    m = BACKTICKED.search(cell)
    if not m or "·" not in m.group(1):
        return None
    rows = [r.replace(" ", "") for r in m.group(1).split("/")]
    return rows if 1 <= len(rows) <= 3 and all(1 <= len(r) <= 3 for r in rows) else None


def table_grids(head, body, pid):
    """Swap each hand-written letter pattern in a table for the real grid built from the recipe JSON.

    The markdown keeps its letters — read on GitHub it is still a perfectly good ASCII grid, and it costs
    no translation work in three languages. This only changes what the built page shows.

    Nothing here trusts the markdown. The row's first cell is resolved to an item, that item's recipe is
    loaded, and the letters are checked against it through wiki_art.shape() before anything is replaced:
    the wiki writes `S = Stick` in English and `S = Палка` in Russian, so neither the letters nor the
    legend can be compared directly, but the arrangement can. A row that does not resolve, or whose
    shape disagrees, is LEFT as letters and reported — a wrong picture is worse than a plain one.

    Returns the replacements and the columns that ended up carrying nothing.
    """
    grids, blanked = {}, set()
    for r, row in enumerate(body):
        col = next((c for c, cell in enumerate(row) if pattern_rows(cell)), None)
        if col is None:
            continue
        name = re.sub(r"\*+", "", row[0]).strip()
        ident = ALL_NAMES.get(name)
        if not ident:
            _grid_left.append((pid, name, "no item goes by that name"))
            continue
        want = wiki_art.recipe_shape(ident)
        if want is None:
            _grid_left.append((pid, name, "%s has no shaped recipe" % ident))
            continue
        if want != wiki_art.shape(pattern_rows(row[col])):
            _grid_left.append((pid, name, "the wiki's grid disagrees with %s.json (%s vs %s)"
                               % (ident, wiki_art.shape(pattern_rows(row[col])), want)))
            continue
        grids[(r, col)] = wiki_art.grid_html(wiki_art.recipes()[ident])
        _grid_swaps.append((pid, ident))
        # The legend column only existed to decode the letters, which are now pictures.
        for c, cell in enumerate(row):
            if c != col and LEGEND.match(cell):
                grids[(r, c)] = ""
                blanked.add(c)
    # Drop a column only if EVERY row emptied it — a table can mix craftables with trade-only rows, and
    # those keep their text.
    dead = {c for c in blanked
            if all(grids.get((r, c)) == "" for r in range(len(body)) if len(body[r]) > c)}
    return grids, dead


def fence_grid(pid, nth, text):
    """The listed recipes for a standalone ASCII grid, drawn, or None to keep the text as it is.

    A fence holding ONE grid is checked the same way a table row is — its drawing must agree with the
    recipe, or it stays text. The two-grid diagram cannot be: it is two patterns, an arrow and a legend
    laid out side by side on the same lines, and pulling one grid back out of that is guesswork. Its
    guard is weaker on purpose and stated here rather than implied: the recipes must exist, and that
    is all.
    """
    idents = FENCE_GRIDS.get((pid, nth))
    if not idents:
        return None
    for ident in idents:
        if ident not in wiki_art.recipes():
            _grid_left.append((pid, ident, "listed for a fence but there is no such recipe"))
            return None
    if len(idents) == 1:
        rows = [ln.replace(" ", "") for ln in text.strip().split("\n")]
        want = wiki_art.recipe_shape(idents[0])
        if want is not None and want != wiki_art.shape(rows):
            _grid_left.append((pid, idents[0], "the fence disagrees with the recipe (%s vs %s)"
                               % (wiki_art.shape(rows), want)))
            return None
    _grid_swaps.extend((pid, i) for i in idents)
    return ('<div class="fg">%s</div>'
            % '<span class="ar">&rarr;</span>'.join(
                wiki_art.grid_html(wiki_art.recipes()[i]) for i in idents))


def calculator_inputs():
    """Profiles, roster order and the three name tables the calculator widget needs."""
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    pdir = os.path.join(base, "common/src/main/resources/data/riverfishing/fish_profiles")
    profiles = {f[:-5]: json.load(io.open(os.path.join(pdir, f), encoding="utf-8"))
                for f in os.listdir(pdir)}
    mi = io.open(os.path.join(base, "common/src/main/java/com/riverfishing/registry/ModItems.java"),
                 encoding="utf-8").read()
    roster = re.findall(r'"([a-z_]+)"', re.search(r"FISH_SPECIES = \{(.*?)\};", mi, re.S).group(1))
    ldir = os.path.join(base, "common/src/main/resources/assets/riverfishing/lang")
    names = {code: json.load(io.open(os.path.join(ldir, f + ".json"), encoding="utf-8"))
             for code, f in (("en", "en_us"), ("ru", "ru_ru"), ("uk", "uk_ua"))}
    return profiles, roster, names


def convert(page):
    out, lines, i, fences = [], page.raw.split("\n"), 0, 0
    while i < len(lines):
        ln = lines[i]

        if ln.startswith("```"):
            i += 1
            body = []
            while i < len(lines) and not lines[i].startswith("```"):
                body.append(lines[i])
                i += 1
            i += 1
            text = "\n".join(body)
            if "·" in text:
                drawn = fence_grid(page.pid, fences, text)
                fences += 1
                if drawn:
                    out.append(drawn)
                    continue
            out.append("<pre><code>%s</code></pre>" % esc(text))
            continue

        m = re.match(r"(#{1,4})\s+(.*)", ln)
        if m:
            lvl, txt = len(m.group(1)), m.group(2).strip()
            a = "%s--%s" % (page.pid, slug(txt))
            if lvl == 1 and not page.title:
                page.title = txt
                out.append('<h1 id="%s">%s</h1>' % (a, inline(txt, page)))
            else:
                page.headings.append((lvl, txt, a))
                out.append('<h%d id="%s">%s</h%d>' % (lvl, a, inline(txt, page), lvl))
            i += 1
            continue

        # GFM pipe table: a header row followed by an alignment row.
        if ln.startswith("|") and i + 1 < len(lines) and re.match(r"^\|[\s:|-]+\|?\s*$", lines[i + 1]):
            def cells(row):
                return [c.strip() for c in row.strip().strip("|").split("|")]

            head = cells(ln)
            aligns = []
            for spec in cells(lines[i + 1]):
                aligns.append("right" if spec.endswith(":") and not spec.startswith(":")
                              else "center" if spec.startswith(":") and spec.endswith(":")
                              else "left")
            i += 2
            body = []
            while i < len(lines) and lines[i].startswith("|"):
                body.append(cells(lines[i]))
                i += 1
            grids, dead = table_grids(head, body, page.pid)
            t = ["<div class=\"tw\"><table><thead><tr>"]
            for n, h in enumerate(head):
                if n in dead:
                    continue
                t.append('<th style="text-align:%s">%s</th>'
                         % (aligns[n] if n < len(aligns) else "left", inline(h, page)))
            t.append("</tr></thead><tbody>")
            for rn, row in enumerate(body):
                t.append("<tr>")
                for n, c in enumerate(row):
                    if n in dead:
                        continue
                    # A replaced cell is finished HTML — inline() would escape the markup it just built.
                    cell = grids[(rn, n)] if (rn, n) in grids else inline(c, page)
                    t.append('<td style="text-align:%s">%s</td>'
                             % (aligns[n] if n < len(aligns) else "left", cell))
                t.append("</tr>")
            t.append("</tbody></table></div>")
            out.append("".join(t))
            continue

        if re.match(r"^---+\s*$", ln):
            out.append("<hr>")
            i += 1
            continue

        if ln.startswith("> "):
            body = []
            while i < len(lines) and lines[i].startswith(">"):
                body.append(lines[i].lstrip(">").strip())
                i += 1
            out.append("<blockquote>%s</blockquote>" % inline(" ".join(body), page))
            continue

        m = re.match(r"^([-*]|\d+\.)\s+", ln)
        if m:
            ordered = ln[0].isdigit()
            items = []
            while i < len(lines) and re.match(r"^([-*]|\d+\.)\s+", lines[i]):
                items.append(re.sub(r"^([-*]|\d+\.)\s+", "", lines[i]))
                i += 1
            tag = "ol" if ordered else "ul"
            out.append("<%s>%s</%s>" % (tag, "".join("<li>%s</li>" % inline(x, page)
                                                     for x in items), tag))
            continue

        if not ln.strip():
            i += 1
            continue

        para = []
        while i < len(lines) and lines[i].strip() and not re.match(
                r"^(#{1,4}\s|```|\||>|---+\s*$|[-*]\s|\d+\.\s)", lines[i]):
            para.append(lines[i].strip())
            i += 1
        if para:
            out.append("<p>%s</p>" % inline(" ".join(para), page))
        else:
            i += 1

    return "\n".join(out)


CELL = re.compile(r'<td style="text-align:[a-z]+">([^<>]+)</td>')
COUNT_SUFFIX = re.compile(r"\s*[×x]\s*\d+\s*$")

# Filled by main(); the illustrate() helpers below read them.
FISH_NAMES = GEAR_NAMES = FISH_CI = GEAR_CI = {}
FISH64 = FISH_TEX


def illustrate(body):
    """Put the item's own sprite in the first cell of a row whose whole text IS an item name.

    Row-scoped on purpose: the main species table leads with a row NUMBER, so anchoring on the
    first cell of the row misses exactly the table people actually read. Whole-cell equality keeps
    it from decorating prose that merely mentions a name.
    """
    def row(m):
        inner = m.group(1)
        done = [False]

        def cell(cm):
            if done[0]:
                return cm.group(0)
            nm = cm.group(1).strip()
            # "Mono Line 0.10 ×2" is still the mono line — the ×N is a craft yield, not the name.
            core = COUNT_SUFFIX.sub("", nm).strip()
            low = core.lower()
            tile = None
            if core in FISH_NAMES or low in FISH_CI:
                tile = wiki_art.fish_tile(FISH_NAMES.get(core) or FISH_CI[low], FISH64)
            elif core in GEAR_NAMES or low in GEAR_CI:
                tile = wiki_art.gear_tile(GEAR_NAMES.get(core) or GEAR_CI[low])
            if not tile:
                return cm.group(0)
            done[0] = True
            return cm.group(0).replace(cm.group(1), tile + cm.group(1), 1)

        return "<tr>" + CELL.sub(cell, inner) + "</tr>"

    return re.sub(r"<tr>(.*?)</tr>", row, body, flags=re.S)


TPL = u"""<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>River Fishing — Wiki</title>
<style>
:root{
  --paper:#F2F4F1; --raise:#FBFCFA; --ink:#171C1A; --dim:#5C6660; --faint:#8C948E;
  --rule:#D6DBD5; --brass:#8A5E1E; --brass-lift:#B0812F; --water:#2E5A63;
  --code-bg:#E7EBE6;
  --sans:"Roboto Condensed","Archivo Narrow","Franklin Gothic Medium","Liberation Sans Narrow","Arial Narrow",system-ui,sans-serif;
  --serif:Charter,"Bitstream Charter",Cambria,Georgia,"Iowan Old Style",serif;
  --mono:"Cascadia Mono","JetBrains Mono",Consolas,"DejaVu Sans Mono",monospace;
}
@media (prefers-color-scheme:dark){:root{
  --paper:#0E1211; --raise:#161B19; --ink:#E4E9E4; --dim:#9AA49D; --faint:#6E7872;
  --rule:#252D29; --brass:#C79A46; --brass-lift:#E0B767; --water:#7FB3BC;
  --code-bg:#1B211E;
}}
:root[data-theme="dark"]{
  --paper:#0E1211; --raise:#161B19; --ink:#E4E9E4; --dim:#9AA49D; --faint:#6E7872;
  --rule:#252D29; --brass:#C79A46; --brass-lift:#E0B767; --water:#7FB3BC;
  --code-bg:#1B211E;
}
:root[data-theme="light"]{
  --paper:#F2F4F1; --raise:#FBFCFA; --ink:#171C1A; --dim:#5C6660; --faint:#8C948E;
  --rule:#D6DBD5; --brass:#8A5E1E; --brass-lift:#B0812F; --water:#2E5A63;
  --code-bg:#E7EBE6;
}
*{box-sizing:border-box}
body{margin:0;background:var(--paper);color:var(--ink);font-family:var(--serif);
  font-size:16px;line-height:1.62;-webkit-font-smoothing:antialiased}
.wrap{display:grid;grid-template-columns:255px minmax(0,1fr);gap:0;min-height:100vh}

aside{border-right:1px solid var(--rule);background:var(--raise);
  position:sticky;top:0;height:100vh;overflow-y:auto;padding:22px 0 40px}
.brand{font-family:var(--sans);padding:0 20px 16px;border-bottom:1px solid var(--rule);
  margin-bottom:14px}
.brand b{display:block;font-size:19px;font-weight:700;letter-spacing:.01em}
.brand span{display:block;font-size:11px;text-transform:uppercase;letter-spacing:.13em;
  color:var(--faint);margin-top:3px}
.find{margin:0 20px 16px;width:calc(100% - 40px);font-family:var(--sans);font-size:13px;
  padding:7px 9px;background:var(--paper);color:var(--ink);
  border:1px solid var(--rule);border-radius:2px}
.find:focus{outline:2px solid var(--brass);outline-offset:1px;border-color:var(--brass)}
.lgs{display:flex;gap:1px;margin:0 20px 14px}
.lg{flex:1;font-family:var(--sans);font-size:11px;letter-spacing:.04em;padding:5px 2px;
  background:var(--paper);color:var(--dim);border:1px solid var(--rule);cursor:pointer}
.lg[aria-pressed="true"]{background:var(--brass);border-color:var(--brass);color:#12100E;
  font-weight:700}
.lg:hover:not([aria-pressed="true"]){color:var(--ink)}
.lg:focus-visible{outline:2px solid var(--brass);outline-offset:2px}
/* The one link out of the page, so it sits below the nav rather than competing with it. */
.dsc{display:block;font-family:var(--sans);font-size:11px;text-transform:uppercase;
  letter-spacing:.13em;color:var(--faint);text-decoration:none;margin:18px 20px 4px;
  padding-top:12px;border-top:1px solid var(--rule)}
.dsc:hover{color:var(--brass-lift)}
.dsc:focus-visible{outline:2px solid var(--brass);outline-offset:2px}
.grp{font-family:var(--sans);font-size:10px;text-transform:uppercase;letter-spacing:.15em;
  color:var(--faint);padding:16px 20px 5px}
.nl{display:block;font-family:var(--sans);font-size:14px;padding:4px 20px 4px 17px;
  color:var(--dim);text-decoration:none;border-left:3px solid transparent}
.nl:hover{color:var(--ink);background:color-mix(in srgb,var(--brass) 8%,transparent)}
.nl[aria-current="page"]{color:var(--ink);font-weight:700;border-left-color:var(--brass);
  background:color-mix(in srgb,var(--brass) 11%,transparent)}
.nl:focus-visible,a:focus-visible{outline:2px solid var(--brass);outline-offset:2px}
.nl.off{display:none}

main{display:grid;grid-template-columns:minmax(0,1fr) 190px;gap:38px;
  padding:44px 42px 120px;max-width:1240px}
article{min-width:0;max-width:72ch}
.toc{font-family:var(--sans);font-size:12.5px;position:sticky;top:44px;align-self:start;
  max-height:calc(100vh - 90px);overflow-y:auto;border-left:1px solid var(--rule);padding-left:14px}
.toc b{display:block;font-size:10px;text-transform:uppercase;letter-spacing:.14em;
  color:var(--faint);margin-bottom:8px;font-weight:400}
.toc a{display:block;color:var(--dim);text-decoration:none;padding:2.5px 0;line-height:1.35}
.toc a:hover{color:var(--brass-lift)}

h1{font-family:var(--sans);font-size:38px;line-height:1.08;font-weight:700;
  letter-spacing:-.01em;margin:0 0 22px;text-wrap:balance}
h2{font-family:var(--sans);font-size:23px;font-weight:700;line-height:1.2;
  margin:44px 0 12px;padding-bottom:5px;border-bottom:1px solid var(--rule);text-wrap:balance}
h3{font-family:var(--sans);font-size:17px;font-weight:700;margin:28px 0 8px;
  letter-spacing:.005em;text-wrap:balance}
h4{font-family:var(--sans);font-size:13px;font-weight:700;text-transform:uppercase;
  letter-spacing:.1em;color:var(--water);margin:22px 0 6px}
p{margin:0 0 14px}
ul,ol{margin:0 0 15px;padding-left:22px}
li{margin-bottom:5px}
a{color:var(--brass);text-decoration:none;border-bottom:1px solid
  color-mix(in srgb,var(--brass) 38%,transparent)}
a:hover{color:var(--brass-lift);border-bottom-color:var(--brass-lift)}
hr{border:0;border-top:1px solid var(--rule);margin:34px 0}
blockquote{margin:0 0 16px;padding:11px 16px;background:var(--raise);
  border-left:3px solid var(--water);color:var(--dim)}
blockquote a{color:var(--brass)}
code{font-family:var(--mono);font-size:.855em;background:var(--code-bg);
  padding:.1em .34em;border-radius:2px;color:var(--ink)}
pre{background:var(--raise);border:1px solid var(--rule);border-left:3px solid var(--water);
  padding:13px 15px;overflow-x:auto;margin:0 0 18px;line-height:1.5}
pre code{background:none;padding:0;font-size:12.8px}

.tw{overflow-x:auto;margin:0 0 20px;max-width:min(100%,1000px);
  border-bottom:1px solid var(--rule)}
table{border-collapse:collapse;font-family:var(--sans);font-size:13.5px;
  font-variant-numeric:tabular-nums;min-width:100%}
th{text-align:left;font-weight:700;font-size:11px;text-transform:uppercase;
  letter-spacing:.09em;color:var(--water);padding:7px 13px 7px 0;white-space:nowrap;
  border-bottom:2px solid var(--rule);position:sticky;top:0;background:var(--paper)}
td{padding:6px 13px 6px 0;border-bottom:1px solid var(--rule);vertical-align:top}
tr:last-child td{border-bottom:0}
td code{font-size:12px}
tbody tr:hover td{background:color-mix(in srgb,var(--brass) 6%,transparent)}

__ART_CSS__
.pg{animation:in .16s ease-out}
@keyframes in{from{opacity:0}to{opacity:1}}
@media (prefers-reduced-motion:reduce){.pg{animation:none}}

@media (max-width:1080px){
  main{grid-template-columns:minmax(0,1fr);padding:30px 24px 90px}
  .toc{display:none}
}
@media (max-width:760px){
  .wrap{grid-template-columns:1fr}
  aside{position:static;height:auto;border-right:0;border-bottom:1px solid var(--rule)}
  h1{font-size:30px}
}
</style>

<div class="wrap">
  <aside>
    <div class="brand"><b>River Fishing</b><span>Wiki &middot; __VER__ &middot; MC __MC__</span></div>
    <div class="lgs" id="lgs" role="group" aria-label="Language">__SWITCH__</div>
    <input class="find" id="find" type="search" placeholder="Filter pages&hellip;"
           aria-label="Filter pages">
    <div id="nav">__NAV__</div>
    <a class="dsc" href="https://discord.gg/Kk2nKvsuRh" target="_blank" rel="noopener">Discord</a>
  </aside>
  <main>
    <article id="doc">__SECTIONS__</article>
    <div class="toc" id="toc"></div>
  </main>
</div>

<script>
// IX is keyed by language: { en: [pages…], ru: […], uk: […] }. The hash carries both, "#ru/rods",
// so a language choice survives a reload and can be linked to directly.
var IX = __INDEX__;
var LANG = '__DEFAULT_LANG__';
var TOC_TITLE = { en: 'On this page', ru: 'На этой странице', uk: 'На цій сторінці' };

function pagesOf(l){ return IX[l] || IX[LANG]; }
function byId(l, pid){ return pagesOf(l).filter(function(p){ return p.id === pid; })[0]; }

function show(lang, pid, anchor){
  if(!IX[lang]) lang = LANG;
  LANG = lang;
  if(!byId(lang, pid)) pid = pagesOf(lang)[0].id;

  document.querySelectorAll('.pg').forEach(function(s){
    s.hidden = (s.id !== 'pg-' + lang + '-' + pid);
  });
  document.querySelectorAll('#nav nav').forEach(function(n){ n.hidden = n.dataset.l !== lang; });
  document.querySelectorAll('.lg').forEach(function(b){
    b.setAttribute('aria-pressed', b.dataset.l === lang ? 'true' : 'false');
  });
  document.documentElement.lang = lang;
  document.querySelectorAll('#nav nav[data-l="' + lang + '"] .nl').forEach(function(a){
    if(a.dataset.p === pid) a.setAttribute('aria-current','page');
    else a.removeAttribute('aria-current');
  });

  var p = byId(lang, pid), t = document.getElementById('toc');
  t.innerHTML = p.h.length
    ? '<b>' + (TOC_TITLE[lang] || TOC_TITLE.en) + '</b>' + p.h.map(function(h){
        return '<a href="#' + h.a + '">' + h.t + '</a>';
      }).join('')
    : '';

  if(anchor){
    var el = document.getElementById(anchor);
    if(el){ el.scrollIntoView(); return; }
  }
  window.scrollTo(0,0);
}

function route(){
  var h = decodeURIComponent(location.hash.replace(/^#/,''));
  var lang = LANG;
  var slash = h.indexOf('/');
  if(slash > -1){
    var want = h.slice(0,slash);
    // Strip the prefix even when that language is not in this build, so a shared "#uk/rods" link
    // still lands on the right PAGE in the default language instead of doing nothing.
    if(/^[a-z]{2}$/.test(want)){ if(IX[want]) lang = want; h = h.slice(slash+1); }
  }
  if(!h){ show(lang, pagesOf(lang)[0].id); return; }
  var cut = h.indexOf('--');
  if(cut > -1) show(lang, h.slice(0,cut), h);
  else if(byId(lang, h)) show(lang, h);
  else {
    var el = document.getElementById(h);
    if(el) el.scrollIntoView();
  }
}
window.addEventListener('hashchange', route);

// Switching language keeps you on the same page, which is the whole point of a switcher.
document.querySelectorAll('.lg').forEach(function(b){
  b.addEventListener('click', function(){
    var cur = document.querySelector('.pg:not([hidden])');
    var pid = cur ? cur.id.replace('pg-' + LANG + '-', '') : pagesOf(b.dataset.l)[0].id;
    location.hash = b.dataset.l + '/' + pid;
  });
});

document.getElementById('find').addEventListener('input', function(e){
  var q = e.target.value.trim().toLowerCase();
  document.querySelectorAll('#nav nav[data-l="' + LANG + '"] .nl').forEach(function(a){
    var p = byId(LANG, a.dataset.p);
    var hay = (a.textContent + ' ' + p.h.map(function(h){return h.t;}).join(' ')).toLowerCase();
    a.classList.toggle('off', q !== '' && hay.indexOf(q) === -1);
  });
  document.querySelectorAll('.grp').forEach(function(g){ g.style.display = q ? 'none' : ''; });
});

route();
</script>
"""

def main():
    global FISH_NAMES, GEAR_NAMES, FISH_CI, GEAR_CI, FISH64

    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", required=True, help="path to write the bundled HTML to")
    ap.add_argument("--mc-jar", help="Minecraft client jar — enables real vanilla ingredient icons")
    ap.add_argument("--fish-size", type=int, default=64,
                    help="px to downscale the 256px fish art to (default 64)")
    args = ap.parse_args()

    check_groups_cover_disk()

    if not os.path.isdir(SRC):
        ap.error("run me from the repo root: %s not found" % SRC)

    scratch = tempfile.mkdtemp(prefix="rf-wiki-")
    try:
        FISH64 = shrink_fish(os.path.join(scratch, "fish"), args.fish_size)
        jar = args.mc_jar or find_mc_jar()
        if jar:
            wiki_art.MCICONS = unpack_mc(jar, os.path.join(scratch, "mc"))
            print("  vanilla icons from %s" % jar)
        else:
            print("  no Minecraft jar found: vanilla ingredients fall back to coloured tiles")

        craft_count = 0

        sections, navs, index, built = [], [], {}, []
        for code, label, sub in LANGS:
            root = os.path.join(SRC, sub) if sub else SRC
            missing = [pid for _, ids in GROUPS for pid in ids
                       if not os.path.exists(os.path.join(root, pid + ".md"))]
            if missing:
                # A language is either complete or absent — half a wiki is worse than none, and the
                # switcher must never offer a tab that 404s half its pages.
                print("  %-3s skipped: %d of %d pages not translated yet"
                      % (code, len(missing), sum(len(i) for _, i in GROUPS)))
                continue

            # Sprites are matched by the item's NAME, so each language needs its own lookup — the
            # Russian tables say "Лещ", not "Bream", and would otherwise come out undecorated.
            global FISH_NAMES, GEAR_NAMES, FISH_CI, GEAR_CI, ALL_NAMES
            FISH_NAMES = wiki_art.names(code)
            GEAR_NAMES = wiki_art.gear_names(code)
            # Recipe lookup takes the unfiltered map: rod_pod_1, rod_pod_3 and bait_trap are craftable
            # but have no texture of their own, so GEAR_NAMES — which exists to find an icon — omits
            # exactly the three rows whose grids are hardest to read as letters.
            ALL_NAMES = wiki_art.all_names(code)
            # Case-insensitive fallback. It was added because the lang file disagreed with itself —
            # "Mono Line 0.10" against "Mono line 0.50" — and the wiki had copied both, so exact
            # matching dropped half the line rows. §line-names has since made the ladders speak one
            # way, so nothing is known to need this today; it stays as a net under any page whose
            # capitalisation drifts from lang again, because the failure is a silently missing icon.
            FISH_CI = {k.lower(): v for k, v in FISH_NAMES.items()}
            GEAR_CI = {k.lower(): v for k, v in GEAR_NAMES.items()}
            # The grids are the same art in every language, but their headings and captions are prose.
            craft_grids, craft_count = wiki_art.craft_html(code)
            calc_profiles, calc_roster, calc_names = calculator_inputs()

            pages, order = {}, []
            for _, ids in GROUPS:
                for pid in ids:
                    p = Page(pid, io.open(os.path.join(root, pid + ".md"), encoding="utf-8").read())
                    pages[pid] = p
                    order.append(pid)

            idx = []
            for pid in order:
                p = pages[pid]
                # The recipe grids and the item sprites are generated from the game's own files, so
                # they are identical in every language — only the surrounding prose is translated.
                body = illustrate(convert(p))
                # §calculator: the only interactive page in the wiki. Markdown cannot carry it, so the
                # page ships a marker and the widget is generated here — data straight from the fish
                # profiles, so it answers with the game's numbers rather than a copy of them.
                if "<!-- CALCULATOR -->" in p.raw:
                    first_calc = not globals().get("_CALC_EMITTED")
                    globals()["_CALC_EMITTED"] = True
                    body = body.replace("&lt;!-- CALCULATOR --&gt;",
                                        wiki_calculator.widget(code, calc_profiles, calc_roster, calc_names, first_calc))
                    body = body.replace("<!-- CALCULATOR -->",
                                        wiki_calculator.widget(code, calc_profiles, calc_roster, calc_names, first_calc))
                if pid == "crafting":
                    body += "\n" + craft_grids
                    p.headings.append((2, wiki_art.GRID_LABELS.get(
                        code, wiki_art.GRID_LABELS["en"])["title"], "crafting--recipe-grids"))
                sections.append('<section class="pg" data-l="%s" id="pg-%s-%s" hidden>%s</section>'
                                % (code, code, pid, body))
                idx.append({"id": pid, "title": p.title or pid,
                            "h": [{"t": t, "a": a} for lvl, t, a in p.headings if lvl == 2]})
            index[code] = idx

            nav = []
            for grp, ids in GROUPS:
                if grp:
                    nav.append('<div class="grp">%s</div>'
                               % esc(GROUP_LABELS.get(code, {}).get(grp, grp)))
                for pid in ids:
                    nav.append('<a class="nl" href="#%s/%s" data-p="%s">%s</a>'
                               % (code, pid, pid, esc(pages[pid].title or pid)))
            navs.append('<nav data-l="%s" hidden>%s</nav>' % (code, "\n".join(nav)))
            built.append((code, label))
            print("  %-3s %d pages" % (code, len(order)))

        print("  inline grids: %d letter patterns replaced with the real recipe" % len(_grid_swaps))
        if _grid_left:
            # Never silent. A row left as letters is either a wiki bug or a name this cannot resolve,
            # and both are things somebody has to see rather than discover on the published page.
            print("  left as letters (%d):" % len(_grid_left))
            for pid, name, why in sorted(set(_grid_left)):
                print("    %-18s %-28s %s" % (pid, name, why))

        if not built:
            ap.error("no complete language found under %s" % SRC)

        switch = "".join('<button class="lg" data-l="%s">%s</button>' % (c, esc(l)) for c, l in built)

        doc = (TPL.replace("__ART_CSS__", "\n".join([wiki_art.CSS, wiki_art.fish_css(FISH64),
                                                     wiki_art.gear_css(), wiki_art.mc_css()]))
                  .replace("__VER__", MOD_VERSION).replace("__MC__", MC_VERSION)
                  .replace("__SWITCH__", switch)
                  .replace("__NAV__", "\n".join(navs))
                  .replace("__SECTIONS__", "\n".join(sections))
                  .replace("__DEFAULT_LANG__", built[0][0])
                  .replace("__INDEX__", json.dumps(index, ensure_ascii=False)))

        out_dir = os.path.dirname(os.path.abspath(args.out))
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
        io.open(args.out, "w", encoding="utf-8", newline="\n").write(doc)

        print("langs=%s  sections=%d  grids=%d\n"
              "  fish  %3d refs / %d sprites\n"
              "  gear  %3d refs / %d sprites\n"
              "  mc    %3d sprites, %d coloured tiles left\n"
              "  -> %s (%.0f KB)"
              % ("+".join(c for c, _ in built), len(sections), craft_count,
                 doc.count('class="fs f-'), len(wiki_art._used_fish),
                 doc.count('class="fs g-'), len(wiki_art._gear_used),
                 len(wiki_art._mc_used), doc.count('class="ct"'),
                 args.out, len(doc) / 1024.0))
    finally:
        shutil.rmtree(scratch, ignore_errors=True)


if __name__ == "__main__":
    main()
