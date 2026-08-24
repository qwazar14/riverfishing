# -*- coding: utf-8 -*-
"""Wire one WAVE of new species into the mod: profile, models, icon, cutting recipe, lang, registry, trades.

    py tools/wire_species_wave.py

The data block below IS the wave; the code under it is generic, so the next wave replaces the block and
runs the same script. Every species names a DONOR that already works — its profile supplies the full set
of keys (they drift over releases, and a hand-written profile silently misses one), its item model carries
whatever that tree needs (builtin/entity here, item/generated on 26.x), and its cutting recipe carries the
loader gate. The patch then overrides what makes this fish itself.

Keys listed in REPLACE are swapped WHOLE rather than merged: a bull shark that inherited the mako's
"deep" biome, or a sculpin that kept the gudgeon's baits, is a fish nobody could explain.
"""
import io, json, os, re, shutil, subprocess, sys

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
# The ports get the same wave. They are NOT copies of this tree: 1.20.1 keeps the old `recipes/` and
# `tags/items/` layout and registers trades in Java, 26.x uses `recipe/`, `tags/item/`, data-driven
# trades from tools/gen_villager_trades.py, and needs its own items/ + fish_scaled models generated
# afterwards. So every step asks the tree what it is rather than assuming, and the DONOR files are read
# from the tree being written — that is what keeps a species in the right dialect on each branch.
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
A = "common/src/main/resources/assets/riverfishing"
D = "common/src/main/resources/data/riverfishing"

WAVE = ("§carp-kin (0.8.1): two more of the family — a Caspian roach that grew up, and a carp\n"
        "            // that forgot to put its scales on.")

# id: (donor, profile patch, (en, ru, uk) name, (en, ru, uk) journal description, (trade tier, emeralds, xp))
SPECIES = {
 "kutum": ("vimba", {
    "display": "Кутум", "group": "cyprinid",
    "water_bodies": {"river": 1.1, "lake": 0.4, "pond": 0.0, "swamp": 0.0, "sea": 1.0, "puddle": 0.0},
    "weight_g": {"min": 500, "max": 8000, "mean": 1400}, "length_cm": {"min": 30, "max": 70},
    "fight": {"strength": 0.6, "stamina": 0.7, "runs": 3, "pattern": "burst", "aggression": 0.6},
    "ideal": {"rod": ["feeder", "bottom"],
              "reel_size": 4000, "reel_tolerance": 1500,
              "line": {"type": "mono", "diameter_mm": 0.25, "tolerance_mm": 0.06},
              "rig": ["feeder", "float"], "groundbait": {"fraction": 0.60, "nutrition": 0.70},
              "bait": {"worm": 1.0, "bloodworm": 0.9, "maggot": 0.8, "fish_strip": 0.5, "pea": 0.4},
              "hook": {"ideal": 8, "tolerance": 3}},
    "season": {"spring": 1.4, "summer": 0.9, "autumn": 1.1, "winter": 0.4},
    "time": {"dawn": 1.3, "day": 0.8, "dusk": 1.3, "night": 0.7},
    "weather": {"clear": 1.0, "rain": 1.1, "thunder": 0.9},
    "depth_pref": "bottom", "distance_pref": {"min": 10, "max": 40},
    "habitat": {"depth_min": 2, "depth_max": 12, "width_min": 10},
    "biomes": {"temperate": 1.1, "warm": 1.0, "beach": 0.9},
    "base": 0.45, "min_angler_level": 4},
   ("Kutum", "Кутум", "Кутум"),
   ("A Caspian roach that grew up: brackish water, a spring run into the rivers, and a mouth built for "
    "shellfish. Fish it on the bottom with worm or bloodworm where the river meets the sea — and give it "
    "more line than a roach deserves, because eight kilos of one does not fight like a roach.",
    "Каспийская плотва, которая выросла: солоноватая вода, весенний ход в реки и рот, устроенный под "
    "моллюсков. Ловите со дна на червя или мотыля там, где река встречает море, — и ставьте леску толще, "
    "чем заслуживает плотва: восемь килограммов дерутся совсем не по-плотвиному.",
    "Каспійська плітка, яка виросла: солонувата вода, весняний хід у річки й рот, влаштований під "
    "молюсків. Ловіть із дна на черв'яка або мотиля там, де річка зустрічає море, — і ставте волосінь "
    "товщу, ніж заслуговує плітка: вісім кілограмів б'ються геть не по-пліточиному."),
   (3, 5, 9)),

 "naked_carp": ("mirror_carp", {
    "display": "Голый карп", "group": "cyprinid",
    "water_bodies": {"lake": 1.2, "pond": 1.1, "river": 0.6, "swamp": 0.4, "sea": 0.0, "puddle": 0.0},
    "weight_g": {"min": 2000, "max": 20000, "mean": 4500}, "length_cm": {"min": 40, "max": 105},
    "fight": {"strength": 0.85, "stamina": 0.85, "runs": 4, "pattern": "burst", "aggression": 0.8},
    "ideal": {"rod": ["carp"],
              "reel_size": 7000, "reel_tolerance": 1000,
              "line": {"type": "mono", "diameter_mm": 0.35, "tolerance_mm": 0.08},
              "rig": ["carp", "flat_feeder"], "groundbait": {"fraction": 0.75, "nutrition": 0.88},
              "bait": {"boilie": 1.0, "corn": 0.85, "pea": 0.6, "pearl_barley": 0.55, "dough": 0.5},
              "hook": {"ideal": 4, "tolerance": 2}},
    "season": {"spring": 0.8, "summer": 1.4, "autumn": 0.95, "winter": 0.03},
    "time": {"dawn": 1.2, "day": 0.85, "dusk": 1.2, "night": 1.05},
    "weather": {"clear": 0.9, "rain": 1.2, "thunder": 0.8},
    "depth_pref": "bottom", "distance_pref": {"min": 14, "max": 45},
    "habitat": {"depth_min": 2, "width_min": 12},
    "biomes": {"temperate": 1.0, "warm": 1.2},
    "base": 0.35, "min_angler_level": 5},
   ("Naked Carp", "Голый карп", "Голий короп"),
   ("A carp that never put its scales on — bare skin end to end, and the same appetite as the rest of the "
    "family. Boilies on the bottom, a carp rod, and patience: it grows past twenty kilos and uses every "
    "one of them.",
    "Карп, который так и не надел чешую, — голая кожа от головы до хвоста и тот же аппетит, что у всей "
    "родни. Бойлы со дна, карповое удилище и терпение: он перерастает двадцать килограммов и пользуется "
    "каждым из них.",
    "Короп, який так і не вдягнув луску, — гола шкіра від голови до хвоста і той самий апетит, що в усієї "
    "рідні. Бойли з дна, коропова вудка й терпіння: він переростає двадцять кілограмів і користується "
    "кожним із них."),
   (4, 8, 14)),
}

REPLACE = {"water_bodies", "weight_g", "length_cm", "fight", "season", "time", "weather",
           "distance_pref", "habitat", "biomes", "rod", "rig", "bait", "hook", "line", "groundbait",
           "legendary"}


def merge(base, patch):
    out = dict(base)
    for k, v in patch.items():
        if isinstance(v, dict) and isinstance(out.get(k), dict) and k not in REPLACE:
            out[k] = merge(out[k], v)
        else:
            out[k] = v
    return out


def sub_id(text, old, new):
    return re.sub(r"(?<![A-Za-z0-9_])" + re.escape(old) + r"(?![A-Za-z0-9_])", new, text)


def write(path, text):
    io.open(path, "w", encoding="utf-8", newline="\n").write(text)


def first_dir(base, *cands):
    for c in cands:
        p = os.path.join(base, c)
        if os.path.isdir(p):
            return p
    return None


def first_file(base, *cands):
    for c in cands:
        p = os.path.join(base, c)
        if os.path.isfile(p):
            return p
    return None


def wire_tree(tree):
    name = os.path.basename(tree)
    assets, data = os.path.join(tree, A), os.path.join(tree, D)
    prof_dir = os.path.join(data, "fish_profiles")
    # Cutting recipes were a Farmer's Delight extra; no tree carries them any more. Skip, do not die.
    rec_dir = first_dir(data, "recipe/cutting", "recipes/cutting")
    made = []

    for sp, (donor, patch, names, desc, trade) in SPECIES.items():
        base = json.load(io.open(os.path.join(prof_dir, donor + ".json"), encoding="utf-8"))
        write(os.path.join(prof_dir, sp + ".json"),
              json.dumps(merge(base, patch), ensure_ascii=False, indent=2) + "\n")
        for sub in ("models/item", "models/item/fish_icon"):
            src = io.open(os.path.join(assets, sub, donor + ".json"), encoding="utf-8").read()
            write(os.path.join(assets, sub, sp + ".json"), sub_id(src, donor, sp))
        if rec_dir and os.path.isfile(os.path.join(rec_dir, donor + ".json")):
            rec = io.open(os.path.join(rec_dir, donor + ".json"), encoding="utf-8").read()
            write(os.path.join(rec_dir, sp + ".json"), sub_id(rec, donor, sp))
    made.append("profiles+models")

    # ---- art: the sprites and journal pictures live in MAIN and are copied out, already compressed ----
    if tree != MAIN:
        copied = 0
        for sub in ("textures/item/fish", "textures/gui/journal/fish"):
            src_dir, dst_dir = os.path.join(MAIN, A, sub), os.path.join(assets, sub)
            for f in os.listdir(src_dir):
                if not f.endswith(".png"):
                    continue
                s_p, d_p = os.path.join(src_dir, f), os.path.join(dst_dir, f)
                if not os.path.isfile(d_p) or os.path.getsize(d_p) != os.path.getsize(s_p) \
                        or io.open(d_p, "rb").read() != io.open(s_p, "rb").read():
                    shutil.copy2(s_p, d_p)
                    copied += 1
        made.append("art x%d" % copied)

    # ---- lang ----
    for idx, loc in enumerate(("en_us", "ru_ru", "uk_ua")):
        p = os.path.join(assets, "lang", loc + ".json")
        txt = io.open(p, encoding="utf-8").read()
        add = []
        for sp, (_, _, names, desc, _) in SPECIES.items():
            for prefix, val in (("item", names[idx]), ("fish", names[idx]), ("fishdesc", desc[idx])):
                key = '"%s.riverfishing.%s"' % (prefix, sp)
                if key not in txt:
                    add.append('  %s: %s,' % (key, json.dumps(val, ensure_ascii=False)))
        if add:
            anchor = '  "item.riverfishing.bleak":'
            assert txt.count(anchor) == 1, (p, anchor)
            txt = txt.replace(anchor, "\n".join(add) + "\n" + anchor)
            write(p, txt)
            json.load(io.open(p, encoding="utf-8"))
    made.append("lang")

    # ---- registry roster ----
    mi = os.path.join(tree, "common/src/main/java/com/riverfishing/registry/ModItems.java")
    s = io.open(mi, encoding="utf-8").read()
    first = list(SPECIES)[0]
    if '"%s"' % first not in s:
        ids = list(SPECIES)
        lines = ["            // " + WAVE]
        for i in range(0, len(ids), 4):
            lines.append("            " + ", ".join('"%s"' % x for x in ids[i:i + 4]) + ",")
        m = re.search(r"(public static final String\[\] FISH_SPECIES = \{\n)", s)
        assert m, mi
        s = s[:m.end()] + "\n".join(lines) + "\n" + s[m.end():]
        write(mi, s)
        made.append("roster")

    # ---- trades: Java table on 1.20.1/1.21.1, a data generator on 26.x ----
    mv = os.path.join(tree, "common/src/main/java/com/riverfishing/registry/ModVillagers.java")
    v = io.open(mv, encoding="utf-8").read()
    anchor = '        buyPrime(fish, 5, "halibut", 22, 34);\n'
    if anchor in v:
        if '"%s"' % first not in v:
            block = ["", "        // §giants-and-minnows (0.8.0): the giants pay like the taimen tier they sit beside,",
                     "        // the minnows like the bleak they swim with."]
            for sp, (_, _, _, _, (tier, em, xp)) in SPECIES.items():
                block.append('        buyPrime(fish, %d, "%s", %d, %d);' % (tier, sp, em, xp))
            write(mv, v.replace(anchor, anchor + "\n".join(block) + "\n"))
            made.append("trades(java)")
    else:
        gen = first_file(tree, "tools/gen_villager_trades.py")
        if gen:
            g = io.open(gen, encoding="utf-8").read()
            if '"%s"' % first not in g:
                # Scoped to the FISH table on purpose: the tier keys "1:".."5:" also head the POOL
                # table of gear, and an unscoped search put seven species inside level 1's worm
                # listing, which stopped being valid Python the moment it landed.
                fm = re.search(r"\nFISH = \{", g)
                assert fm, "no FISH table in " + gen
                fstart = fm.end()
                fend = g.index("\n}", fstart)
                seg = g[fstart:fend]
                for tier in sorted({t[0] for (_, _, _, _, t) in SPECIES.values()}, reverse=True):
                    rows = [(sp, t[1], t[2]) for sp, (_, _, _, _, t) in SPECIES.items() if t[0] == tier]
                    tm = re.search(r"\n    %d: \[" % tier, seg)
                    assert tm, "tier %d not in the FISH table" % tier
                    close = seg.index("],", tm.end())
                    # The last row of a tier carries no trailing comma, so the block has to bring one:
                    # without it the two tuples juxtapose into a CALL, which parses fine and dies at
                    # runtime with "'tuple' object is not callable".
                    head = seg[:close].rstrip()
                    if not head.endswith(","):
                        head += ","
                    entry = ("\n        # §giants-and-minnows (0.8.0)\n        "
                             + ", ".join('("%s", %d, %d)' % r for r in rows))
                    seg = head + entry + seg[close:]
                write(gen, g[:fstart] + seg + g[fend:])
                made.append("trades(data)")

    # ---- fishes item tag: brought up to the FULL roster, not just this wave ----
    roster_src = io.open(mi, encoding="utf-8").read()
    body = re.search(r"FISH_SPECIES = \{(.*?)\};", roster_src, re.S).group(1)
    roster = re.findall(r'"([a-z_]+)"', body)
    tag_p = first_file(data, "tags/item/fishes.json", "tags/items/fishes.json")
    if tag_p:
        tag = json.load(io.open(tag_p, encoding="utf-8"))
        missing = ["riverfishing:" + x for x in roster if "riverfishing:" + x not in set(tag["values"])]
        if missing:
            tag["values"] = tag["values"] + missing
            write(tag_p, json.dumps(tag, ensure_ascii=False, indent=2) + "\n")
            made.append("tag+%d" % len(missing))

    # ---- generators this tree owns: sprite bounds, and 26.x's per-species item definitions ----
    for script in ("tools/gen_fish_bounds.py", "tools/gen_dynamic_icons.py"):
        if os.path.isfile(os.path.join(tree, script)):
            r = subprocess.run(["py", script], cwd=tree, capture_output=True, text=True)
            made.append(os.path.basename(script).replace("gen_", "").replace(".py", "")
                        + ("" if r.returncode == 0 else " FAILED"))
            if r.returncode != 0:
                print("    " + (r.stderr or r.stdout).strip().splitlines()[-1])

    print("  %-28s %s" % (name, ", ".join(made)))


def main():
    for tree in TREES:
        wire_tree(tree)
    print("\n%d species across %d trees" % (len(SPECIES), len(TREES)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
