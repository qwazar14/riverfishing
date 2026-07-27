# -*- coding: utf-8 -*-
"""Emit wiki table rows for species, from the profiles, in all three languages.

    python tools/gen_species_tables.py peacock_bass tarpon ...     (named species)
    python tools/gen_species_tables.py --new                       (every species not yet in the wiki)
    python tools/gen_species_tables.py --check                     (report coverage, write nothing)

Adding a species to the wiki by hand costs four table rows — one in species.md and three in
species-reference.md — times three languages, so twelve rows each. Nine species is 108 rows of
hand-typed numbers in Russian and Ukrainian, which is both the largest single cost of a species wave and
the part most likely to be skipped or mistyped. All of it is derivable from the profile JSON.

Only the NAMED species are appended; the existing rows are never rewritten. That keeps the diff small and
reviewable, and means a formatting choice made by hand in 0.5.0 is not silently reflowed.

The label vocabulary is not invented — every word was harvested from the rows already in the wiki
(озеро/річка/ставок, у дна/біля дна, and so on) and is ASSERTED to appear in the target file before
anything is written. Biome groups and fight patterns are deliberately left in English, because that is
how the existing 70 rows carry them.
"""
import io, json, os, re, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROF = os.path.join(REPO, "common/src/main/resources/data/riverfishing/fish_profiles")
LANGDIR = os.path.join(REPO, "common/src/main/resources/assets/riverfishing/lang")
WIKI = os.path.join(REPO, "docs/wiki")

# locale -> (wiki subdir, image path prefix, lang file)
LOC = [("en", "", "../..", "en_us"), ("ru", "ru", "../../..", "ru_ru"), ("uk", "uk", "../../..", "uk_ua")]

WATER = {
    "en": dict(river="river", lake="lake", pond="pond", swamp="swamp", puddle="puddle", sea="sea"),
    "ru": dict(river="река", lake="озеро", pond="пруд", swamp="болото", puddle="лужа", sea="море"),
    "uk": dict(river="річка", lake="озеро", pond="ставок", swamp="болото", puddle="калюжа", sea="море"),
}
DEPTH = {
    "en": dict(bottom="bottom", mid="mid", surface="surface"),
    "ru": dict(bottom="у дна", mid="вполводы", surface="у поверхности"),
    "uk": dict(bottom="біля дна", mid="у півводи", surface="біля поверхні"),
}
UNIT = {"en": ("g", "kg", "cm"), "ru": ("г", "кг", "см"), "uk": ("г", "кг", "см")}


def mass(g, loc):
    small, big, _ = UNIT[loc]
    if g < 1000:
        return "%d %s" % (g, small)
    v = g / 1000.0
    return ("%d %s" % (v, big)) if abs(v - round(v)) < 1e-9 else ("%.1f %s" % (v, big))


def water_list(p, loc):
    items = [(k, v) for k, v in p["water_bodies"].items() if v > 0]
    items.sort(key=lambda kv: (-kv[1], kv[0]))
    return ", ".join("%s %s" % (WATER[loc][k], ("%g" % v)) for k, v in items)


def biomes(p):
    items = sorted(p.get("biomes", {}).items(), key=lambda kv: (-kv[1], kv[0]))
    return ", ".join("%s %g" % (k, v) for k, v in items) or "—"


def rng(lo, hi, plus_when_open=True):
    if hi is None:
        return "%d+" % lo if plus_when_open else "%d" % lo
    return "%d–%d" % (lo, hi)


def row_species(n, sp, p, loc, img, name):
    _, _, cm = UNIT[loc]
    return ("| %d | <img src=\"%s/common/src/main/resources/assets/riverfishing/textures/item/fish/%s.png\""
            " width=\"28\" alt=\"\"> %s | `%s` | %s – %s | %s | %d–%d %s | %s | %s |"
            % (n, img, sp, name, sp,
               mass(p["weight_g"]["min"], loc), mass(p["weight_g"]["max"], loc),
               mass(p["weight_g"]["mean"], loc),
               p["length_cm"]["min"], p["length_cm"]["max"], cm,
               water_list(p, loc),
               p.get("min_angler_level") or "—"))


def row_habitat(sp, p, loc, name):
    h = p.get("habitat", {})
    d = rng(h.get("depth_min", 0), h.get("depth_max"))
    w = rng(h.get("width_min", 0), h.get("width_max"))
    dp = p.get("distance_pref", {})
    return "| %s | %s | %s | %s | %s | %d–%d |" % (
        name, d, w, biomes(p), DEPTH[loc][p["depth_pref"]], dp.get("min", 0), dp.get("max", 0))


def row_conditions(sp, p, loc, name):
    s, t, w = p["season"], p["time"], p["weather"]
    vals = [s["spring"], s["summer"], s["autumn"], s["winter"],
            t["dawn"], t["day"], t["dusk"], t["night"],
            w["clear"], w["rain"], w["thunder"]]
    return "| %s | %s |" % (name, " | ".join("%g" % v for v in vals))


def row_fight(sp, p, loc, name):
    f = p["fight"]
    leg = p.get("legendary")
    legs = ("%s / %g%%" % (mass(leg["weight_g"], loc), leg["chance"] * 100)) if leg else "—"
    agg = "%g" % f["aggression"] if f.get("aggression") else "—"
    return "| %s | %s | %g | %d | %s | %g | %s |" % (
        name, f["pattern"], f["strength"], f["runs"], agg, p.get("base", 1.0), legs)


HOOK = {"en": "No.", "ru": "№", "uk": "№"}


def row_tackle(sp, p, loc, name):
    i = p["ideal"]
    baits = ", ".join("%s %g" % (k, v) for k, v in
                      sorted(i.get("bait", {}).items(), key=lambda kv: (-kv[1], kv[0]))) or "—"
    ln = i["line"]
    return "| %s | %s | %s | %s | %s%d ±%d | %d ±%d | %s %g ±%g | %s | %s |" % (
        name, baits,
        ", ".join(sorted(i.get("rod", []))) or "—",
        ", ".join(sorted(i.get("rig", []))) or "—",
        HOOK[loc], i["hook"]["ideal"], i["hook"]["tolerance"],
        i["reel_size"], i["reel_tolerance"],
        ln["type"], ln["diameter_mm"], ln["tolerance_mm"],
        ", ".join(sorted(i.get("groundbait", []))) or "—",
        "**yes**" if i.get("requires_leader") else "—")


TABLES = [
    ("species.md", re.compile(r"^\| # \|"), row_species, True),
    ("species-reference.md", re.compile(r"^\| (Species|Вид) \| (Water depth|Глубина воды|Глибина води)"), row_habitat, False),
    ("species-reference.md", re.compile(r"^\| (Species|Вид) \| (Spr|Весн\.|Весн)"), row_conditions, False),
    ("species-reference.md", re.compile(r"^\| (Species|Вид) \| (Pattern|Манера боя|Манера бою)"), row_fight, False),
    ("species.md", re.compile(r"^\| (Species|Вид) \| (Best baits|Лучшие наживки|Найкращі наживки)"), row_tackle, False),
]


def table_end(lines, header_i):
    """Index just past the last body row of the table whose header is at header_i."""
    i = header_i + 2                      # header + separator
    while i < len(lines) and lines[i].startswith("|"):
        i += 1
    return i


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    new_only = "--new" in sys.argv
    check = "--check" in sys.argv

    profiles = {f[:-5]: json.load(io.open(os.path.join(PROF, f), encoding="utf-8"))
                for f in os.listdir(PROF) if f.endswith(".json")}
    langs = {code: json.load(io.open(os.path.join(LANGDIR, code + ".json"), encoding="utf-8"))
             for _, _, _, code in LOC}

    en_txt = io.open(os.path.join(WIKI, "species.md"), encoding="utf-8").read()
    present = set(re.findall(r"\| `([a-z0-9_]+)` \|", en_txt))
    missing = sorted(sp for sp in profiles if sp not in present)

    if check:
        print("profiles %d, in species.md %d, missing %d" % (len(profiles), len(present), len(missing)))
        for sp in missing:
            print("   " + sp)
        return 0

    todo = args or (missing if new_only else [])
    if not todo:
        sys.exit("nothing to do — pass species ids, or --new, or --check")
    bad = [sp for sp in todo if sp not in profiles]
    if bad:
        sys.exit("no profile for: %s" % ", ".join(bad))

    for loc, sub, img, code in LOC:
        # Refuse to write a label we cannot prove is the one this wiki already uses.
        ref = io.open(os.path.join(WIKI, sub, "species.md") if sub else os.path.join(WIKI, "species.md"),
                      encoding="utf-8").read()
        for w in WATER[loc].values():
            if w not in ref:
                sys.exit("%s/species.md never uses %r — refusing to invent vocabulary" % (sub or "en", w))
        href = io.open(os.path.join(WIKI, sub, "species-reference.md") if sub
                       else os.path.join(WIKI, "species-reference.md"), encoding="utf-8").read()
        for d in DEPTH[loc].values():
            if d not in href:
                sys.exit("%s/species-reference.md never uses %r — refusing to invent vocabulary"
                         % (sub or "en", d))

        written = 0
        for fname, hdr, builder, numbered in TABLES:
            path = os.path.join(WIKI, sub, fname) if sub else os.path.join(WIKI, fname)
            lines = io.open(path, encoding="utf-8").read().split("\n")
            hi = next((i for i, l in enumerate(lines) if hdr.match(l)), None)
            if hi is None:
                sys.exit("%s: no table header matching %s" % (path, hdr.pattern))
            end = table_end(lines, hi)
            n = 0
            if numbered:
                nums = [int(m.group(1)) for m in
                        (re.match(r"\| (\d+) \|", l) for l in lines[hi + 2:end]) if m]
                n = max(nums) if nums else 0
            # Idempotent per TABLE, not per file: species.md holds two per-species tables, so a species
            # can be in one and absent from the other. Re-running must top up the gaps without
            # duplicating what is already there.
            body = lines[hi + 2:end]
            add = []
            for sp in todo:
                name = langs[code].get("fish.riverfishing." + sp) or sp
                already = any(l.startswith("| " + name + " |") or ("| `%s` |" % sp) in l for l in body)
                if already:
                    continue
                n += 1
                add.append(builder(n, sp, profiles[sp], loc, img, name) if numbered
                           else builder(sp, profiles[sp], loc, name))
            lines[end:end] = add
            written += len(add)
            io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
        print("  %-3s %d rows written across %d tables" % (loc, written, len(TABLES)))

    print("\n%d species x 4 tables x 3 languages = %d rows" % (len(todo), len(todo) * 12))
    return 0


if __name__ == "__main__":
    sys.exit(main())
