#!/usr/bin/env python3
"""§26.1 data-driven villager trades: emits data/riverfishing/{villager_trade,trade_set,tags/villager_trade}
from the same table the pre-26.1 Java builders (ModVillagers.registerTrades) encoded.
Prime-fish buy thresholds are baked from fish_profiles/<id>.json (ceil(weight_max * 0.7))."""
import json, math, os, shutil

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources", "data", "riverfishing"))
PROFILES = os.path.join(ROOT, "fish_profiles")
TRADE_DIR = os.path.join(ROOT, "villager_trade", "fisherman")
SET_DIR = os.path.join(ROOT, "trade_set", "fisherman")
TAG_DIR = os.path.join(ROOT, "tags", "villager_trade", "fisherman")

PRIME_FRACTION = 0.7
DISCOUNT = 0.05

# (level, item_path, emerald_cost, count, xp) — maxUses 12
SELLS = [
    (1, "worm", 1, 12, 1), (1, "bloodworm", 1, 8, 1), (1, "float", 1, 2, 2),
    (1, "groundbait_powder", 1, 6, 2),
    (1, "corn_seeds", 1, 3, 1), (1, "pea_seeds", 1, 3, 1), (1, "barley_seeds", 1, 3, 1),
    (1, "line_mono_014", 2, 1, 3), (1, "worm_farm", 4, 1, 4),
    (2, "maggot", 1, 10, 2), (2, "reel_2000", 4, 1, 5), (2, "reel_3000", 6, 1, 6),
    (2, "line_mono_018", 2, 1, 4), (2, "groundbait_grain", 1, 6, 3), (2, "bait_trap", 3, 1, 4),
    (3, "spinner", 3, 1, 8), (3, "spoon", 4, 1, 8), (3, "silicone", 2, 2, 6),
    (3, "line_braid_016", 5, 1, 10), (3, "line_fluoro_020", 5, 1, 10),
    (3, "leader_fluoro", 3, 2, 6), (3, "fish_finder", 14, 1, 12),
    (4, "wobbler", 7, 1, 15), (4, "livebait", 2, 3, 8), (4, "boilie", 3, 8, 10),
    (4, "reel_5000", 10, 1, 15), (4, "reel_6000", 13, 1, 16), (4, "line_fluoro_030", 6, 1, 12),
    (4, "ice_auger", 9, 1, 14), (4, "winter_rod", 8, 1, 12), (4, "mormyshka", 3, 2, 8),
    (4, "maggot_farm", 5, 1, 8), (4, "groundbait_cake", 4, 3, 6),
    (5, "digital_alarm", 10, 1, 25), (5, "leader_titanium", 8, 1, 20), (5, "reel_7000", 16, 1, 26),
    (5, "line_braid_030", 10, 1, 22), (5, "carp_rod", 18, 1, 30), (5, "bottom_rod", 16, 1, 28),
]

# (level, fish_path, emeralds, xp) — maxUses 12; wants carries the riverfishing:prime component gate
BUYS = [
    (1, "bleak", 1, 1), (1, "gudgeon", 1, 1), (1, "roach", 1, 1),
    (2, "crucian_carp", 2, 2), (2, "perch", 2, 2), (2, "ruffe", 1, 2), (2, "rudd", 2, 2),
    (3, "bream", 3, 4), (3, "ide", 3, 5), (3, "chub", 3, 5), (3, "tench", 4, 5), (3, "pike", 5, 8),
    (4, "carp", 6, 12), (4, "grass_carp", 9, 14), (4, "zander", 6, 10), (4, "trout", 6, 12),
    (4, "grayling", 7, 12), (4, "burbot", 5, 10),
    (5, "catfish", 12, 25), (5, "eel", 8, 15), (5, "sterlet", 16, 30), (5, "wild_carp", 14, 28),
]

# ---- assembled tackle (§assembled-trades): SNBT written via minecraft:set_custom_data, maxUses 8 ----

def item_nbt(path):
    return '{id:"riverfishing:%s",count:1}' % path

def rig_custom(slots):  # slots: list of (byte, item_path)
    items = ",".join('{Slot:%db,Item:%s}' % (i, item_nbt(p)) for i, p in slots)
    return '{RigContents:{Items:[%s]}}' % items

def rig_stack_nbt(rig_path, slots):
    return '{id:"riverfishing:%s",count:1,components:{"minecraft:custom_data":%s}}' % (rig_path, rig_custom(slots))

def rod_custom(reel, line, rig_path, rig_slots):
    parts = []
    if reel:
        parts.append("Reel:" + item_nbt(reel))
    parts.append("Line:" + item_nbt(line))
    parts.append("Rig:" + rig_stack_nbt(rig_path, rig_slots))
    return "{RodComponents:{%s}}" % ",".join(parts)

FLOAT_RIG = [(0, "float"), (1, "hook_10")]
PREDATOR_RIG = [(0, "leader"), (1, "spinner")]
FEEDER_RIG = [(0, "hook_8")]

# (level, name, gives_item, emerald_cost, xp, snbt)
ASSEMBLED = [
    (2, "assembled_float_rig", "rig_float_light", 4, 6, rig_custom(FLOAT_RIG)),
    (2, "assembled_bamboo_rod", "bamboo_rod", 9, 8, rod_custom(None, "line_mono_018", "rig_float_light", FLOAT_RIG)),
    (3, "assembled_spinning_rod", "spinning_rod", 16, 14, rod_custom("reel_2000", "line_braid_016", "rig_predator", PREDATOR_RIG)),
    (4, "assembled_feeder_rod", "feeder_rod", 18, 18, rod_custom("reel_5000", "line_mono_025", "rig_feeder", FEEDER_RIG)),
]


def prime_threshold(fish):
    with open(os.path.join(PROFILES, fish + ".json"), encoding="utf-8") as f:
        prof = json.load(f)
    return math.ceil(prof["weight_g"]["max"] * PRIME_FRACTION)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    for d in (TRADE_DIR, SET_DIR, TAG_DIR):
        shutil.rmtree(d, ignore_errors=True)
    per_level = {lvl: [] for lvl in range(1, 6)}

    for lvl, path, cost, count, xp in SELLS:
        name = path
        trade = {
            "wants": {"id": "minecraft:emerald", "count": cost},
            "gives": {"id": "riverfishing:" + path, **({"count": count} if count != 1 else {})},
            "max_uses": 12, "xp": xp, "reputation_discount": DISCOUNT,
        }
        write(os.path.join(TRADE_DIR, str(lvl), name + ".json"), trade)
        per_level[lvl].append("riverfishing:fisherman/%d/%s" % (lvl, name))

    for lvl, fish, emeralds, xp in BUYS:
        name = "buy_" + fish
        trade = {
            "wants": {"id": "riverfishing:" + fish,
                      "components": {"riverfishing:prime": prime_threshold(fish)}},
            "gives": {"id": "minecraft:emerald", **({"count": emeralds} if emeralds != 1 else {})},
            "max_uses": 12, "xp": xp, "reputation_discount": DISCOUNT,
        }
        write(os.path.join(TRADE_DIR, str(lvl), name + ".json"), trade)
        per_level[lvl].append("riverfishing:fisherman/%d/%s" % (lvl, name))

    for lvl, name, gives, cost, xp, snbt in ASSEMBLED:
        trade = {
            "wants": {"id": "minecraft:emerald", "count": cost},
            "gives": {"id": "riverfishing:" + gives},
            "given_item_modifiers": [{"function": "minecraft:set_custom_data", "tag": snbt}],
            "max_uses": 8, "xp": xp, "reputation_discount": DISCOUNT,
        }
        write(os.path.join(TRADE_DIR, str(lvl), name + ".json"), trade)
        per_level[lvl].append("riverfishing:fisherman/%d/%s" % (lvl, name))

    for lvl in range(1, 6):
        write(os.path.join(TAG_DIR, "level_%d.json" % lvl), {"values": per_level[lvl]})
        write(os.path.join(SET_DIR, "level_%d.json" % lvl), {
            "amount": 2,
            "random_sequence": "riverfishing:trade_set/fisherman/level_%d" % lvl,
            "trades": "#riverfishing:fisherman/level_%d" % lvl,
        })

    n = sum(len(v) for v in per_level.values())
    print("wrote %d trades + 5 tags + 5 trade sets" % n)


if __name__ == "__main__":
    main()
