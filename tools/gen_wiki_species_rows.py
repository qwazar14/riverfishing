# -*- coding: utf-8 -*-
"""Add the species the wiki has not heard of yet to every table, in all three languages.

    py tools/gen_wiki_species_rows.py

Six tables carry per-species rows (three on species.md, three on species-reference.md), each in en/ru/uk
— eighteen places a new fish has to appear, which is why the last wave of nine never made it into any of
them and why this is a generator rather than a chore. Every number is read from the fish profile the game
itself reads, so a wiki row cannot drift from the game the way a typed one does.

Only the columns that are PROSE get translated: the name, the water bodies, the depth horizon. Baits,
rods, rigs, biomes, groundbait kinds and fight patterns are ids in every language's table already — the
wiki deliberately prints what you would type, not a translation of it.

Idempotent: a species already present in a table is left alone.
"""
import io, json, os, re, sys

TREE = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
PROF = os.path.join(TREE, "common/src/main/resources/data/riverfishing/fish_profiles")
LANG = os.path.join(TREE, "common/src/main/resources/assets/riverfishing/lang")

LOCALES = [("", "en_us", "../../"), ("ru/", "ru_ru", "../../../"), ("uk/", "uk_ua", "../../../")]

WATER = {
    "en": {"lake": "lake", "river": "river", "pond": "pond", "swamp": "swamp", "sea": "sea", "puddle": "puddle"},
    "ru": {"lake": "озеро", "river": "река", "pond": "пруд", "swamp": "болото", "sea": "море", "puddle": "лужа"},
    "uk": {"lake": "озеро", "river": "річка", "pond": "став", "swamp": "болото", "sea": "море", "puddle": "калюжа"},
}
DEPTH = {
    "en": {"bottom": "bottom", "mid": "mid", "surface": "surface"},
    "ru": {"bottom": "у дна", "mid": "вполводы", "surface": "у поверхности"},
    "uk": {"bottom": "біля дна", "mid": "у півводи", "surface": "біля поверхні"},
}
UNIT = {"en": ("g", "kg", "cm"), "ru": ("г", "кг", "см"), "uk": ("г", "кг", "см")}


def weight(v, loc):
    g, kg, _ = UNIT[loc]
    if v >= 1000:
        s = ("%.1f" % (v / 1000.0)).rstrip("0").rstrip(".")
        return "%s %s" % (s, kg)
    return "%d %s" % (v, g)


def num(v):
    """Match the rows already in the tables: 1 prints as 1.0, 0.30 as 0.3, 12 as 12."""
    if float(v) == int(v) and abs(v) < 10:
        return "%.1f" % v
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return s if s else "0"


def scored(d, loc=None, limit=None):
    items = sorted(d.items(), key=lambda kv: -kv[1])
    if limit:
        items = items[:limit]
    return ", ".join("%s %s" % (k, num(v)) for k, v in items)


def waters(p, loc):
    live = {k: v for k, v in p["water_bodies"].items() if v > 0}
    items = sorted(live.items(), key=lambda kv: -kv[1])
    return ", ".join("%s %s" % (WATER[loc][k], num(v)) for k, v in items)


def row_main(idx, sid, p, name, prefix, loc):
    img = '<img src="%scommon/src/main/resources/assets/riverfishing/textures/item/fish/%s.png" width="28" alt="">' % (prefix, sid)
    w = p["weight_g"]
    lvl = str(p.get("min_angler_level", 0)) if p.get("min_angler_level", 0) else "—"
    return "| %d | %s %s | `%s` | %s – %s | %s | %d–%d %s | %s | %s |" % (
        idx, img, name, sid, weight(w["min"], loc), weight(w["max"], loc), weight(w["mean"], loc),
        p["length_cm"]["min"], p["length_cm"]["max"], UNIT[loc][2], waters(p, loc), lvl)


def row_tackle(sid, p, name, loc):
    i = p["ideal"]
    gb = i.get("groundbait")
    gb_txt = "%s / %s" % (num(gb["fraction"]), num(gb["nutrition"])) if isinstance(gb, dict) else "—"
    line = i["line"]
    return "| %s | %s | %s | %s | №%s ±%s | %s ±%s | %s %s ±%s | %s | %s |" % (
        name, scored(i["bait"], loc, 7), ", ".join(i["rod"]), ", ".join(i["rig"]),
        i["hook"]["ideal"], i["hook"]["tolerance"], i["reel_size"], i["reel_tolerance"],
        line["type"], num(line["diameter_mm"]), num(line["tolerance_mm"]),
        gb_txt, "+" if i.get("requires_leader") else "—")


def row_habitat(sid, p, name, loc):
    h = p.get("habitat", {})
    dmin, dmax = h.get("depth_min"), h.get("depth_max")
    depth = "%d–%d" % (dmin, dmax) if dmin and dmax else ("%d+" % dmin if dmin else "—")
    wmin = h.get("width_min")
    width = "%d+" % wmin if wmin else "—"
    d = p.get("distance_pref", {})
    dist = "%d–%d" % (d.get("min", 0), d.get("max", 0)) if d else "—"
    return "| %s | %s | %s | %s | %s | %s |" % (
        name, depth, width, scored(p.get("biomes", {}), loc), DEPTH[loc][p.get("depth_pref", "bottom")], dist)


def row_when(sid, p, name, loc):
    s, t, w = p["season"], p["time"], p["weather"]
    cells = [num(s[k]) for k in ("spring", "summer", "autumn", "winter")] \
        + [num(t[k]) for k in ("dawn", "day", "dusk", "night")] \
        + [num(w[k]) for k in ("clear", "rain", "thunder")]
    return "| %s | %s |" % (name, " | ".join(cells))


def row_fight(sid, p, name, loc):
    f = p["fight"]
    leg = p.get("legendary")
    leg_txt = weight(leg["weight_g"], loc) if leg else "—"
    return "| %s | %s | %s | %s | %s | %s | %s |" % (
        name, f.get("pattern", "steady"), num(f["strength"]), f.get("runs", 1),
        num(f["aggression"]) if "aggression" in f else "—", num(p["base"]), leg_txt)


TABLES = [
    # (file, header signature, row builder, does the row start with a number column)
    ("species.md", "| # |", row_main, True),
    ("species.md", "| Species | Best baits", row_tackle, False),
    ("species-reference.md", "| Species | Water depth", row_habitat, False),
    ("species-reference.md", "| Species | Spring", row_when, False),
    ("species-reference.md", "| Species | Fight pattern", row_fight, False),
]
# The header text is English only in the en tree; the others are matched by column count and position,
# so the signature list carries the localized first cells too.
HEADER_ALIASES = {
    "| # |": ["| # |"],
    "| Species | Best baits": ["| Species | Best baits", "| Вид | Лучшие наживки", "| Вид | Найкращі наживки"],
    "| Species | Water depth": ["| Species | Water depth", "| Вид | Глубина воды", "| Вид | Глибина води"],
    "| Species | Spring": ["| Species | Spr |", "| Вид | Весн.", "| Вид | Весна |"],
    "| Species | Fight pattern": ["| Species | Pattern |", "| Вид | Манера боя", "| Вид | Манера бою"],
}


def table_bounds(lines, signatures):
    """Start and end line of the body of the table whose header matches one of the signatures."""
    for i, line in enumerate(lines):
        if any(line.startswith(sig) for sig in signatures):
            j = i + 2                      # skip header and the |---| rule
            while j < len(lines) and lines[j].startswith("| "):
                j += 1
            return i + 2, j
    return None, None


def main():
    profiles = {}
    for f in os.listdir(PROF):
        profiles[f[:-5]] = json.load(io.open(os.path.join(PROF, f), encoding="utf-8"))

    roster_src = io.open(os.path.join(TREE, "common/src/main/java/com/riverfishing/registry/ModItems.java"),
                         encoding="utf-8").read()
    roster = re.findall(r'"([a-z_]+)"',
                        re.search(r"FISH_SPECIES = \{(.*?)\};", roster_src, re.S).group(1))

    added_total = 0
    for sub, lang_file, prefix in LOCALES:
        loc = "en" if sub == "" else sub.strip("/")
        names = json.load(io.open(os.path.join(LANG, lang_file + ".json"), encoding="utf-8"))
        for fname, sig, builder, numbered in TABLES:
            path = os.path.join(TREE, "docs/wiki", sub, fname)
            lines = io.open(path, encoding="utf-8").read().split("\n")
            start, end = table_bounds(lines, HEADER_ALIASES[sig])
            if start is None:
                print("  ! %s%s: table %s not found" % (sub, fname, sig))
                continue
            present = set()
            for line in lines[start:end]:
                m = re.search(r"`([a-z_]+)`", line)
                if m:
                    present.add(m.group(1))
                else:                       # tables keyed by display name
                    cell = line.split("|")[1].strip()
                    for sid in roster:
                        if names.get("fish.riverfishing." + sid) == cell:
                            present.add(sid)
                            break
            missing = [s for s in roster if s not in present and s in profiles]
            if not missing:
                continue
            idx = end - start
            new_rows = []
            for sid in missing:
                name = names.get("fish.riverfishing." + sid, sid)
                idx += 1
                new_rows.append(builder(idx, sid, profiles[sid], name, prefix, loc) if numbered
                                else builder(sid, profiles[sid], name, loc))
            lines[end:end] = new_rows
            io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
            added_total += len(new_rows)
            print("  %-22s %-28s +%d" % (sub + fname, sig[:26], len(new_rows)))
    print("\n%d rows added" % added_total)
    return 0


if __name__ == "__main__":
    sys.exit(main())
