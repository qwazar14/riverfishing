# -*- coding: utf-8 -*-
"""Emit the 0.7.0 Florida/US species profiles into all three source trees.

    python tools/gen_florida_species.py

Nine species, requested by idkwho0457_07869 on the Discord, who also found the §session-guard bug that
0.6.1 fixes. Their own words are the design brief and are quoted per species below — a peacock bass that
"breaks equipment" and a snakehead that "gives up usually quick" are gameplay statements, not trivia,
and they map straight onto `fight.strength` and the `active_then_passive` pattern.

Generated rather than hand-written for one reason: nine profiles × three trees is 27 files, and the
numbers have to agree across them. Tune the table here and re-run.

Real weights and lengths are rod-and-reel realistic rather than IGFA-record: `max` is a fish of a
lifetime, `mean` is what actually comes up. The whole roster was reality-checked in 0.6.0 and these
follow that pass.
"""
import io, json, os, sys

TREES = [
    r"C:/Users/Qwazar/VS Code Projects/fishing mod",
    r"C:/Users/Qwazar/wt/rf1201",
    r"C:/Users/Qwazar/wt/rf26",
]
REL = "common/src/main/resources/data/riverfishing/fish_profiles"

# Florida canals are freshwater and tropical: these three die in a cold snap, hence winter ~0.05 and a
# hard `warm` bias. The saltwater five ride the existing sea gating.
FRESH = {"lake": 1.2, "pond": 1.1, "river": 1.0, "swamp": 0.9, "sea": 0.0, "puddle": 0.0}
SEA = {"sea": 1.2, "river": 0.0, "lake": 0.0, "pond": 0.0, "swamp": 0.0, "puddle": 0.0}
# Tarpon and snook run up into brackish creeks — the only two that are not sea-only.
BRACKISH = {"sea": 1.2, "river": 0.5, "lake": 0.0, "pond": 0.0, "swamp": 0.3, "puddle": 0.0}

SPECIES = {
    # ---- freshwater canal exotics ----
    "peacock_bass": dict(
        display="Павлиний окунь",
        # "strong fight like a torpedo ... freshwater bullies known for breaking equipment"
        water=FRESH, w=(300, 12000, 1800), l=(25, 75),
        fight=dict(strength=0.95, stamina=0.8, runs=4, pattern="aggressive", aggression=0.95),
        rod=["spinning", "ultralight"], reel=3000, reel_tol=1000,
        line=("braid", 0.20, 0.05), rig=["predator"],
        bait={"wobbler": 1.2, "popper": 1.15, "crankbait": 1.0, "silicone": 0.95, "spinner": 0.9, "livebait": 0.85, "jig": 0.8},
        hook=(4, 3), season=(1.2, 1.3, 1.0, 0.05), time=(1.3, 1.0, 1.3, 0.4),
        weather=(1.0, 1.0, 0.9), depth="mid", dist=(5, 30),
        habitat=dict(depth_min=1, depth_max=10, width_min=6),
        biomes={"warm": 1.4}, base=0.55, level=5),
    "bullseye_snakehead": dict(
        display="Глазчатый змееголов",
        # "gives a strong fight but gives up usually quick" — that IS active_then_passive.
        water=FRESH, w=(400, 8000, 1500), l=(30, 90),
        fight=dict(strength=0.9, stamina=0.35, runs=2, pattern="active_then_passive", aggression=0.85),
        rod=["spinning"], reel=3000, reel_tol=1000,
        line=("braid", 0.22, 0.06), rig=["predator"],
        bait={"livebait": 1.2, "silicone": 1.05, "popper": 1.0, "wobbler": 0.95, "jig": 0.85, "worm": 0.6},
        hook=(2, 3), season=(1.1, 1.3, 1.0, 0.05), time=(1.2, 1.0, 1.2, 0.6),
        weather=(1.0, 1.1, 1.0), depth="surface", dist=(3, 20),
        habitat=dict(depth_min=1, depth_max=6, width_min=4),
        biomes={"warm": 1.4, "swamp": 1.2}, base=0.6, level=5),
    "mayan_cichlid": dict(
        display="Цихлазома майя",
        water=FRESH, w=(80, 1200, 300), l=(12, 35),
        fight=dict(strength=0.45, stamina=0.5, runs=2, pattern="burst", aggression=0.8),
        rod=["ultralight", "pole"], reel=1500, reel_tol=1000,
        line=("mono", 0.14, 0.04), rig=["float", "predator"],
        bait={"worm": 1.2, "maggot": 1.0, "bloodworm": 1.0, "silicone": 0.8, "bread": 0.7},
        hook=(10, 3), season=(1.1, 1.3, 1.0, 0.05), time=(1.1, 1.1, 1.1, 0.5),
        weather=(1.0, 1.0, 0.9), depth="mid", dist=(2, 15),
        habitat=dict(depth_min=1, width_min=3),
        biomes={"warm": 1.4, "swamp": 1.1}, base=1.0, level=3),
    "oscar": dict(
        display="Астронотус",
        water=FRESH, w=(150, 1600, 450), l=(15, 40),
        fight=dict(strength=0.55, stamina=0.5, runs=2, pattern="burst", aggression=0.85),
        rod=["ultralight", "spinning"], reel=2000, reel_tol=1000,
        line=("mono", 0.16, 0.04), rig=["float", "predator"],
        bait={"worm": 1.2, "livebait": 1.1, "silicone": 0.9, "maggot": 0.9, "jig": 0.8},
        hook=(8, 3), season=(1.1, 1.3, 1.0, 0.05), time=(1.2, 1.0, 1.2, 0.5),
        weather=(1.0, 1.0, 0.9), depth="mid", dist=(2, 18),
        habitat=dict(depth_min=1, width_min=4),
        biomes={"warm": 1.4, "swamp": 1.1}, base=0.9, level=3),
    # ---- inshore salt ----
    "striped_bass": dict(
        display="Полосатый лаврак",
        # A bulldog: never spectacular, never stops. Relentless, high stamina, cold-tolerant.
        water=BRACKISH, w=(500, 35000, 4000), l=(30, 130),
        fight=dict(strength=0.85, stamina=0.95, runs=4, pattern="relentless", aggression=0.7),
        rod=["surf", "sea_spin", "boat"], reel=7000, reel_tol=2000,
        line=("braid", 0.30, 0.08), rig=["predator", "ground"],
        bait={"livebait": 1.2, "fish_strip": 1.1, "wobbler": 1.0, "silicone": 0.95, "spoon": 0.9, "jig": 0.85},
        hook=(2, 2), season=(1.2, 0.9, 1.3, 0.7), time=(1.3, 0.7, 1.4, 1.1),
        weather=(0.9, 1.2, 1.2), depth="mid", dist=(10, 45),
        habitat=dict(depth_min=2, width_min=12),
        biomes={"temperate": 1.2, "cold": 1.0, "beach": 1.3, "ocean_biome": 1.0}, base=0.6, level=6),
    "bluefish": dict(
        display="Луфарь",
        # Savage slasher in packs — high aggression, teeth mean a leader really matters.
        water=SEA, w=(400, 14000, 2000), l=(30, 110),
        fight=dict(strength=0.8, stamina=0.7, runs=4, pattern="aggressive", aggression=1.0),
        rod=["sea_spin", "surf", "boat"], reel=6000, reel_tol=2000,
        line=("braid", 0.28, 0.07), rig=["predator"],
        bait={"spoon": 1.2, "castmaster": 1.15, "fish_strip": 1.1, "wobbler": 1.0, "silicone": 0.9, "livebait": 0.9},
        # A mouthful of teeth that goes through mono — the same class as pike, barracuda and wahoo.
        leader=True,
        hook=(2, 2), season=(1.0, 1.2, 1.3, 0.4), time=(1.3, 0.9, 1.3, 0.8),
        weather=(1.0, 1.1, 1.1), depth="mid", dist=(10, 50),
        habitat=dict(depth_min=2, width_min=14),
        biomes={"temperate": 1.2, "beach": 1.2, "ocean_biome": 1.1, "warm": 1.0}, base=0.6, level=6),
    "jack_crevalle": dict(
        display="Каранкс",
        # "known as canal tuna here" — the byword for a fish that simply will not quit.
        water=BRACKISH, w=(800, 30000, 4500), l=(35, 120),
        fight=dict(strength=0.95, stamina=1.0, runs=5, pattern="relentless", aggression=0.95),
        rod=["sea_spin", "boat", "surf"], reel=8000, reel_tol=2000,
        line=("braid", 0.35, 0.08), rig=["predator"],
        bait={"popper": 1.25, "spoon": 1.1, "castmaster": 1.1, "silicone": 1.0, "livebait": 1.0, "wobbler": 0.95},
        hook=(1, 2), season=(1.1, 1.3, 1.2, 0.3), time=(1.3, 1.0, 1.3, 0.6),
        weather=(1.0, 1.1, 1.0), depth="mid", dist=(10, 50),
        habitat=dict(depth_min=2, width_min=12),
        biomes={"warm": 1.4, "beach": 1.2, "ocean_biome": 1.1}, base=0.55, level=7),
    "tarpon": dict(
        display="Тарпон",
        # "known as silver king here". The jumps are the whole point — greyhounding, like the marlin.
        water=BRACKISH, w=(5000, 130000, 30000), l=(90, 250),
        fight=dict(strength=1.0, stamina=0.95, runs=5, pattern="greyhounding", aggression=0.75),
        rod=["boat", "sea_spin", "surf"], reel=10000, reel_tol=3000,
        line=("braid", 0.45, 0.10), rig=["predator", "catfish"],
        bait={"livebait": 1.3, "fish_strip": 1.1, "silicone": 1.0, "popper": 1.0, "jig": 0.9},
        hook=(1, 2), season=(1.2, 1.3, 1.0, 0.2), time=(1.4, 0.7, 1.4, 1.2),
        weather=(1.0, 1.1, 0.9), depth="mid", dist=(15, 60),
        habitat=dict(depth_min=3, width_min=16),
        biomes={"warm": 1.5, "beach": 1.2, "ocean_biome": 1.0}, base=0.35, level=9),
    "snook": dict(
        display="Снук",
        # One violent run for the nearest structure; win it in the first seconds or lose the fish.
        water=BRACKISH, w=(700, 25000, 3500), l=(35, 140),
        fight=dict(strength=0.9, stamina=0.6, runs=3, pattern="burst", aggression=0.85),
        rod=["sea_spin", "surf", "spinning"], reel=6000, reel_tol=2000,
        line=("braid", 0.30, 0.08), rig=["predator"],
        bait={"livebait": 1.25, "silicone": 1.1, "wobbler": 1.05, "popper": 1.0, "jig": 0.9, "fish_strip": 0.85},
        hook=(2, 2), season=(1.1, 1.3, 1.1, 0.2), time=(1.4, 0.7, 1.4, 1.3),
        weather=(1.0, 1.1, 1.0), depth="mid", dist=(5, 35),
        habitat=dict(depth_min=2, width_min=8),
        biomes={"warm": 1.4, "beach": 1.2, "swamp": 1.0, "ocean_biome": 0.9}, base=0.55, level=7),
}


def profile(s):
    """Key order matches the existing 70 files, so a diff against a neighbour reads cleanly."""
    wmin, wmax, wmean = s["w"]
    lmin, lmax = s["l"]
    lt, ld, ltol = s["line"]
    hi, htol = s["hook"]
    spring, summer, autumn, winter = s["season"]
    dawn, day, dusk, night = s["time"]
    clear, rain, thunder = s["weather"]
    return {
        "display": s["display"],
        "water_bodies": s["water"],
        "weight_g": {"min": wmin, "max": wmax, "mean": wmean},
        "length_cm": {"min": lmin, "max": lmax},
        "fight": s["fight"],
        "ideal": {
            "rod": s["rod"],
            "reel_size": s["reel"], "reel_tolerance": s["reel_tol"],
            "line": {"type": lt, "diameter_mm": ld, "tolerance_mm": ltol},
            "rig": s["rig"], "groundbait": [],
            **({"requires_leader": True} if s.get("leader") else {}),
            "bait": s["bait"],
            "hook": {"ideal": hi, "tolerance": htol},
        },
        "season": {"spring": spring, "summer": summer, "autumn": autumn, "winter": winter},
        "time": {"dawn": dawn, "day": day, "dusk": dusk, "night": night},
        "weather": {"clear": clear, "rain": rain, "thunder": thunder},
        "depth_pref": s["depth"],
        "distance_pref": {"min": s["dist"][0], "max": s["dist"][1]},
        "habitat": s["habitat"],
        "biomes": s["biomes"],
        "base": s["base"],
        "min_angler_level": s["level"],
    }


def main():
    # Sanity: the vocabulary these profiles use must already exist in the 70 shipped ones, or the
    # engine silently ignores the key and the species quietly never bites.
    ref = os.path.join(TREES[0], REL)
    known_bait, known_rod, known_rig, known_biome = set(), set(), set(), set()
    for f in os.listdir(ref):
        d = json.load(io.open(os.path.join(ref, f), encoding="utf-8"))
        known_bait |= set(d["ideal"].get("bait", {}))
        known_rod |= set(d["ideal"].get("rod", []))
        known_rig |= set(d["ideal"].get("rig", []))
        known_biome |= set(d.get("biomes", {}))
    bad = []
    for name, s in SPECIES.items():
        for k in s["bait"]:
            if k not in known_bait:
                bad.append("%s: unknown bait %r" % (name, k))
        for k in s["rod"]:
            if k not in known_rod:
                bad.append("%s: unknown rod %r" % (name, k))
        for k in s["rig"]:
            if k not in known_rig:
                bad.append("%s: unknown rig %r" % (name, k))
        for k in s["biomes"]:
            if k not in known_biome:
                bad.append("%s: unknown biome %r" % (name, k))
        if not (s["w"][0] < s["w"][2] < s["w"][1]):
            bad.append("%s: mean weight outside min..max" % name)
    if bad:
        for b in bad:
            print("  " + b)
        sys.exit("vocabulary check failed — nothing written")

    for tree in TREES:
        out = os.path.join(tree, REL)
        for name, s in SPECIES.items():
            p = os.path.join(out, name + ".json")
            io.open(p, "w", encoding="utf-8", newline="\n").write(
                json.dumps(profile(s), ensure_ascii=False, indent=2) + "\n")
        total = len([f for f in os.listdir(out) if f.endswith(".json")])
        print("  %-46s %d profiles now (%d written)" % (os.path.basename(tree), total, len(SPECIES)))
    print("\n%d species emitted into %d trees" % (len(SPECIES), len(TREES)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
