# -*- coding: utf-8 -*-
"""Illustration layer for the bundled wiki: fish sprites and rendered craft grids.

Imported by build_wiki_html.py. Two deliberate choices:

* Fish sprites are the repo's own 256px art downscaled to 64px with nearest-neighbour, so pixel
  art stays crisp; gear icons are 16px and ship untouched, upscaled by CSS with image-rendering
  set to pixelated.
* Vanilla ingredients are drawn as labelled colour tiles rather than Minecraft's own textures.
  Embedding Mojang art in a source-available ARR project would be redistributing it, and the
  colour + code + legend reads well enough that it isn't worth the licensing question.
"""
import base64, io, json, os, re

REPO = "."
TEX = REPO + "/common/src/main/resources/assets/riverfishing/textures/item"
RECIPES = REPO + "/common/src/main/resources/data/riverfishing/recipe"
LANG = REPO + "/common/src/main/resources/assets/riverfishing/lang/en_us.json"

# Materials, coloured from the real thing. Code is what shows in the tile.
VANILLA = {
    "iron_ingot": ("#C9CBCE", "Fe"), "iron_nugget": ("#AEB1B5", "fe"),
    "iron_block": ("#9FA3A8", "FE"), "gold_ingot": ("#E5C13C", "Au"),
    "gold_nugget": ("#EFD772", "au"), "copper_ingot": ("#C87B54", "Cu"),
    "copper_block": ("#AE6A46", "CU"), "diamond": ("#4FDCD6", "Dia"),
    "redstone": ("#B02A20", "Rs"), "redstone_block": ("#8C1F19", "RS"),
    "string": ("#E6E6DE", "Str"), "stick": ("#9B7444", "Stk"),
    "bamboo": ("#7FA84A", "Bam"), "wheat": ("#D7B24A", "Wht"),
    "wheat_seeds": ("#9DB84A", "Sd"), "planks": ("#A9814F", "Pln"),
    "prismarine_shard": ("#7BC4B4", "Pri"), "prismarine_crystals": ("#A6DFCF", "Cry"),
    "nautilus_shell": ("#E2DABE", "Nau"), "water_bucket": ("#7A8EA8", "H2O"),
    "smooth_stone": ("#B3B3B3", "Stn"), "gravel": ("#8B847F", "Grv"),
    "dirt": ("#795939", "Drt"), "amethyst_shard": ("#9A6FD0", "Ame"),
    "lapis_lazuli": ("#2B57B0", "Lap"), "quartz": ("#E6E0D6", "Qtz"),
    "flint": ("#494949", "Fli"), "phantom_membrane": ("#C7BDA6", "Mem"),
    "leather": ("#8A5A34", "Lea"), "feather": ("#ECECE7", "Fea"),
    "egg": ("#E6DABE", "Egg"), "slime_ball": ("#7FCB6A", "Sli"),
    "sugar": ("#EFEFEF", "Sug"), "bread": ("#BE8945", "Brd"),
    "book": ("#987951", "Bk"), "barrel": ("#886941", "Brl"),
    "kelp": ("#3D7945", "Klp"), "rotten_flesh": ("#885949", "Rot"),
    "glass": ("#CDE5ED", "Gls"), "glass_pane": ("#CDE5ED", "Pn"),
    "bone_meal": ("#EBE9DD", "Bn"), "signs": ("#A9814F", "Sgn"),
}

# §mc-icons: vanilla item icons, read from a LOCAL Minecraft jar extraction and embedded only in the
# bundled page. Nothing here is ever written into the repo — Mojang's art must not be redistributed
# inside a source-available ARR project. Set MC_ICONS to the extraction root to turn this on; without
# it every vanilla ingredient falls back to its coloured tile and the build still works.
MCICONS = ""   # set by gen_wiki_bundle.main() from --mc-jar; empty means "tiles only"

# Where a texture is not simply item/<name>.png: blocks have no flat item sprite (their icon is a
# rendered 3D model), and a few of our ingredients are tags rather than items.
MC_PATH = {
    "iron_block": "block/iron_block", "copper_block": "block/copper_block",
    "redstone_block": "block/redstone_block", "smooth_stone": "block/smooth_stone",
    "gravel": "block/gravel", "dirt": "block/dirt", "glass": "block/glass",
    "glass_pane": "block/glass",          # panes have no flat icon of their own
    "planks": "block/oak_planks",         # tag -> oak as the stand-in
    "signs": "item/oak_sign",             # tag -> oak as the stand-in
    "barrel": "block/barrel_side",
}

_cache = {}


def _uri(path):
    if path in _cache:
        return _cache[path]
    if not os.path.exists(path):
        _cache[path] = None
        return None
    with open(path, "rb") as f:
        _cache[path] = "data:image/png;base64," + base64.b64encode(f.read()).decode("ascii")
    return _cache[path]


def fish_uri(species, small_dir):
    return _uri(os.path.join(small_dir, species + ".png"))


_used_fish = set()


def fish_tile(species, small_dir):
    """A sprite reference, not the sprite: the data URI is emitted ONCE into fish_css().
    Species appear in four different tables, so inlining each time quadrupled the payload."""
    if not _uri(os.path.join(small_dir, species + ".png")):
        return None
    _used_fish.add(species)
    return '<i class="fs f-%s"></i>' % species.replace("_", "-")


def fish_css(small_dir):
    rules = []
    for sp in sorted(_used_fish):
        rules.append(".f-%s{background-image:url(%s)}"
                     % (sp.replace("_", "-"), _uri(os.path.join(small_dir, sp + ".png"))))
    return "\n".join(rules)


BLOCK = os.path.dirname(TEX) + "/block"

# Blocks have no flat item sprite — their inventory icon is a rendered model. A few of ours have a
# usable face texture; the rest build their model out of vanilla textures and have nothing to show.
BLOCK_FACE = {"maggot_farm": "maggot_farm_top", "worm_farm": "worm_farm_top",
              "aquarium": "aquarium_glass", "ice_hole": "ice_hole"}


def item_uri(name):
    """Our own item sprite.

    §rod-layers: a rod's real icon is COMPOSITED from the drawn layer sprites in item/rod, and its
    item model is builtin/entity carrying only a particle texture. The flat item/<x>_rod.png files are
    dead 0.1.0 placeholders that were replaced by the drawn blanks and never deleted — never read them.
    """
    if name.endswith("_rod"):
        return _uri(TEX + "/rod/blank_" + name[:-4] + ".png")
    return (_uri(TEX + "/" + name + ".png")
            or _uri(TEX + "/fish/" + name + ".png")
            or _uri(BLOCK + "/" + BLOCK_FACE.get(name, name) + ".png"))


_mc_used = set()


def mc_tile(name):
    """A vanilla icon reference, or None to fall back to the coloured tile."""
    if not MCICONS:
        return None
    rel = MC_PATH.get(name, "item/" + name)
    if not _uri(os.path.join(MCICONS, "assets/minecraft/textures", rel + ".png")):
        return None
    _mc_used.add(name)
    return '<i class="ci v-%s"></i>' % name.replace("_", "-")


def mc_css():
    rules = []
    for n in sorted(_mc_used):
        rel = MC_PATH.get(n, "item/" + n)
        rules.append(".v-%s{background-image:url(%s)}"
                     % (n.replace("_", "-"),
                        _uri(os.path.join(MCICONS, "assets/minecraft/textures", rel + ".png"))))
    return "\n".join(rules)


def names():
    """English display name -> species id, from the lang file the game itself uses."""
    lang = json.load(io.open(LANG, encoding="utf-8"))
    out = {}
    for k, v in lang.items():
        if k.startswith("fish.riverfishing."):
            out.setdefault(v, k.rsplit(".", 1)[1])
    return out


def gear_names():
    """Display name -> id for every non-fish item and block that has a sprite we can show."""
    lang = json.load(io.open(LANG, encoding="utf-8"))
    fish = set(names().values())
    out = {}
    for k, v in lang.items():
        if not (k.startswith("item.riverfishing.") or k.startswith("block.riverfishing.")):
            continue
        ident = k.rsplit(".", 1)[1]
        if ident in fish or not item_uri(ident):
            continue
        out.setdefault(v, ident)
    return out


_gear_used = set()


def gear_tile(ident):
    if not item_uri(ident):
        return None
    _gear_used.add(ident)
    return '<i class="fs g-%s"></i>' % ident.replace("_", "-")


def gear_css():
    return "\n".join(".g-%s{background-image:url(%s)}" % (i.replace("_", "-"), item_uri(i))
                     for i in sorted(_gear_used))


def _cell(ing):
    """One grid cell from a recipe ingredient (or None for empty)."""
    if ing is None:
        return '<i class="cx"></i>'
    alt = False
    if isinstance(ing, list):
        # "any of these" — show the first and say so in the tooltip.
        if not ing:
            return '<i class="cx"></i>'
        alt = len(ing) > 1
        ing = ing[0]
    if isinstance(ing, str):
        ing = {"item": ing}
    ident = ing.get("item") or ing.get("tag") or ""
    tag = "tag" in ing or alt
    ns, _, name = ident.partition(":")
    if ns == "riverfishing":
        # Our own tags have no sprite of their own — stand a member in for the whole tag.
        TAG_FACE = {"hooks": "hook_10", "fishes": "roach"}
        uri = item_uri(TAG_FACE.get(name, name))
        label = ("any " + name) if name in TAG_FACE else name.replace("_", " ")
        if uri:
            return ('<i class="ci" title="%s%s"><img src="%s" alt=""></i>'
                    % (label, " (any of tag)" if tag else "", uri))
        return '<i class="ct" style="--t:#8FB08A" title="%s">%s</i>' % (label, name[:3])
    hint = " (tag)" if tag else ""
    icon = mc_tile(name)
    if icon:
        return icon.replace('class="ci ', 'title="minecraft:%s%s" class="ci ' % (name, hint), 1)
    colour, code = VANILLA.get(name, ("#8C948E", name[:3]))
    return ('<i class="ct" style="--t:%s" title="minecraft:%s%s">%s</i>'
            % (colour, name, hint, code))


def _result(res):
    ident = res.get("id") or res.get("item") or ""
    name = ident.partition(":")[2]
    count = res.get("count", 1)
    uri = item_uri(name)
    n = '<b>%d</b>' % count if count and count > 1 else ""
    label = name.replace("_", " ")
    if uri:
        return '<i class="ci out" title="%s"><img src="%s" alt="">%s</i>' % (label, uri, n)
    # A few blocks have no texture of their own (their model is built from vanilla ones). An empty
    # bordered box reads as a bug, so say what it is — the caption above names it in full anyway.
    initials = "".join(w[0] for w in name.split("_"))[:3].upper()
    return '<i class="ct out ob" title="%s">%s%s</i>' % (label, initials, n)


def craft_html():
    """Every shaped/shapeless recipe as a real grid, grouped by output family."""
    groups = {"Rods": [], "Reels": [], "Lines and leaders": [], "Rigs": [],
              "Lures and baits": [], "Blocks and tools": [], "Food and other": []}

    def bucket(name):
        if name.endswith("_rod") and name != "rod_pod_1" and name != "rod_pod_3":
            return "Rods"
        if name.startswith("reel_"):
            return "Reels"
        if name.startswith("line_") or name.startswith("leader"):
            return "Lines and leaders"
        if name.startswith("rig_"):
            return "Rigs"
        if name in ("spinner", "spoon", "wobbler", "silicone", "popper", "crankbait", "jig",
                    "castmaster", "mormyshka", "boilie", "dough", "bread", "fish_strip",
                    "livebait") or name.startswith("groundbait"):
            return "Lures and baits"
        if name in ("aquarium", "bait_trap", "worm_farm", "maggot_farm", "fishing_stall",
                    "trophy_stand", "rod_pod_1", "rod_pod_3", "bell_alarm", "digital_alarm",
                    "ice_auger", "fillet_knife", "whetstone", "fish_finder", "journal",
                    "keepnet", "float") or name.startswith("hook_"):
            return "Blocks and tools"
        return "Food and other"

    for fn in sorted(os.listdir(RECIPES)):
        if not fn.endswith(".json"):
            continue
        d = json.load(io.open(os.path.join(RECIPES, fn), encoding="utf-8"))
        t = d.get("type", "")
        if t not in ("minecraft:crafting_shaped", "minecraft:crafting_shapeless"):
            continue
        res = d.get("result")
        if not isinstance(res, dict):
            continue
        name = (res.get("id") or res.get("item") or "").partition(":")[2]
        cells = []
        if t == "minecraft:crafting_shaped":
            key = d.get("key", {})
            pat = d.get("pattern", [])
            for r in range(3):
                row = pat[r] if r < len(pat) else ""
                for c in range(3):
                    ch = row[c] if c < len(row) else " "
                    cells.append(_cell(key.get(ch)) if ch != " " else _cell(None))
            grid = '<div class="cg">%s</div>' % "".join(cells)
        else:
            ings = d.get("ingredients", [])
            grid = ('<div class="cs">%s</div>'
                    % "".join(_cell(i) for i in ings))
        groups[bucket(name)].append(
            '<figure class="rc"><figcaption>%s</figcaption><div class="rr">%s'
            '<span class="ar">&rarr;</span>%s</div></figure>'
            % (name.replace("_", " "), grid, _result(res)))

    out = ['<h2 id="crafting--recipe-grids">Recipe grids</h2>',
           '<p>Generated straight from the recipe files, so these cannot drift from what the game '
           'loads. Our own items show their real sprite; vanilla materials are coloured tiles &mdash; '
           'hover any cell for its full id.</p>']
    for g, items in groups.items():
        if not items:
            continue
        out.append('<h3 id="crafting--grids-%s">%s</h3>' % (re.sub(r"\W+", "-", g.lower()), g))
        out.append('<div class="rg">%s</div>' % "".join(items))
    return "\n".join(out), sum(len(v) for v in groups.values())


CSS = """
.rg{display:grid;grid-template-columns:repeat(auto-fill,minmax(178px,1fr));gap:16px;margin:0 0 26px}
.rc{margin:0;padding:11px 12px 13px;background:var(--raise);border:1px solid var(--rule)}
.rc figcaption{font-family:var(--sans);font-size:11px;text-transform:uppercase;
  letter-spacing:.09em;color:var(--water);margin-bottom:9px}
.rr{display:flex;align-items:center;gap:9px}
.cg{display:grid;grid-template-columns:repeat(3,22px);grid-auto-rows:22px;gap:2px}
.cs{display:flex;flex-wrap:wrap;gap:2px;max-width:96px}
/* Class selectors only — a `.cg i` here would out-specify .ct and eat its tile colour. */
.ci,.ct,.cx{width:22px;height:22px;display:flex;align-items:center;
  justify-content:center;border:1px solid var(--rule);background:var(--paper);
  position:relative;font-style:normal}
.cx{background:transparent;border-style:dotted;opacity:.5}
.ci img{width:20px;height:20px;image-rendering:pixelated}
/* Vanilla icons ride a background so each texture is embedded once, not per cell. */
.ci[class*=" v-"]{background-size:20px 20px;background-repeat:no-repeat;
  background-position:center;image-rendering:pixelated}
.ct{font-family:var(--sans);font-size:8px;font-weight:700;letter-spacing:0;
  color:#12100E;background:var(--t);border-color:color-mix(in srgb,var(--t) 65%,#000)}
.out{width:30px;height:30px;border-width:2px;border-color:var(--brass)}
.out img{width:26px;height:26px}
/* Textureless block output: initials on the page ground, not a material colour. */
.ob{background:var(--paper);color:var(--dim);font-size:9px}
.out b{position:absolute;right:-2px;bottom:-4px;font-family:var(--sans);font-size:10px;
  color:var(--ink);text-shadow:0 0 3px var(--paper),0 0 3px var(--paper)}
.ar{font-size:15px;color:var(--brass);margin:0 1px}
.fs{display:inline-block;width:30px;height:30px;background-size:contain;
  background-repeat:no-repeat;background-position:center;image-rendering:pixelated;
  vertical-align:-9px;margin-right:6px}
"""
