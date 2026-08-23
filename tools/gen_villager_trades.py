#!/usr/bin/env python3
"""§26.1 data-driven villager trades: emits data/riverfishing/{villager_trade,trade_set,tags/villager_trade}
from the same table the pre-26.1 Java builders (ModVillagers.registerTrades) encoded.

§trade-pool on 26.x — WHY THIS LOOKS THE WAY IT DOES
----------------------------------------------------
Vanilla draws `amount` offers per villager level out of that level's trade tag, WITHOUT replacement
(AbstractVillager.addOffersFromItemListingsWithoutDuplicates: pick a random holder, remove it, keep it
if it yields an offer). So pool SIZE is the currency: every id in the tag dilutes every other one.
0.5.0 shipped 47 ids on level 5, 22 of them fish buys, which made a maxed fisherman half fish-buyer.

The 1.21.1 fix folded variants of one thing (five reels, four sea rods, a tier's species) into ONE
rotating listing. The 26.x data model has no such thing — a tag entry is exactly one trade and
HolderSet carries no weights — but it does have `merchant_predicate`, and a trade whose predicate
fails returns a null offer, which the draw loop RE-ROLLS (the holder is consumed, the counter is not
advanced). So `merchant_predicate: random_chance w` is a per-entry WEIGHT: k variants at w = 1/k
together occupy one pool slot, exactly like the old oneOf.

Pool shapes therefore land on the 1.21.1 targets 10/10/7/14/12 effective slots (2 of them fish buys)
against `amount` 3 — and every level keeps at least three unweighted (w = 1) entries, so the draw can
always fill all three offers no matter how the coins land.

§assembled-only — the stall sells no bare blanks; every rod ships with reel, line and a loaded rig.
§tackle-craft  — everything the Tackle Station can tie leaves the stall with the same TackleWeightG
                 stamp TackleForm.stamp() writes, or a bought lure would cast as 0 g.
§starter-fish  — the level-1 guarantee cannot be expressed in data (the draw is pure chance), so the
                 four ubiquitous smalls also get UNWEIGHTED copies under fisherman/starter/, tagged
                 #riverfishing:fisherman/starter, which VillagerTradePoolMixin swaps in when a fresh
                 level-1 stall came up gear-only. They are in no level pool.

Running this file GENERATES and then VERIFIES (json re-parse, pool shapes, every item id known to the
lang file, every assembled rod legal against RodType's reel band and TackleCompat's spool limit, every
bench stamp equal to TackleForm's own numbers). Non-zero exit = do not commit the output.
"""
import json
import random, math, os, re, shutil, sys

HERE = os.path.dirname(os.path.abspath(__file__))
COMMON = os.path.normpath(os.path.join(HERE, "..", "common", "src", "main"))
ROOT = os.path.join(COMMON, "resources", "data", "riverfishing")
JAVA = os.path.join(COMMON, "java", "com", "riverfishing")
LANG = os.path.join(COMMON, "resources", "assets", "riverfishing", "lang", "en_us.json")
PROFILES = os.path.join(ROOT, "fish_profiles")
TRADE_DIR = os.path.join(ROOT, "villager_trade", "fisherman")
SET_DIR = os.path.join(ROOT, "trade_set", "fisherman")
TAG_DIR = os.path.join(ROOT, "tags", "villager_trade", "fisherman")

PRIME_FRACTION = 0.7
DISCOUNT = 0.05
# §trade-pool 0.7.0: four and four, up from three and two. A tier registers between eight and
# twenty-six species and used to put TWO of them on any one counter, frozen there for that
# villager's life — so an angler with seventy-nine species in the journal could sell five of them.
# Doubling the slots doubles what one stall takes; the daily order slot (§order-slot) covers the
# rest, since it is the only thing that ROTATES.
TRADES_PER_LEVEL = 4        # §trade-pool: two more than vanilla's — bait, line, a rod AND two fish buys
FISH_SLOTS_PER_TIER = 4     # how much of each tier's pool the species share
STARTER_FISH = ["bleak", "roach", "gudgeon", "rotan"]  # live in every water — see the community guide

# Effective pool sizes we intend to land on, asserted after generation (§trade-pool).
# §internal-rig: tier 2 is ELEVEN, not twelve. The Java table on 1.20.1/1.21.1 stopped selling the
# assembled float rig on its own — it lives INSIDE the float rods (JournalScreen.isInternalRig) and is
# never tied by itself, so a standalone sale was a component with a price tag. That decision never
# reached this generator, which is why the two branches disagreed about tier 2 by exactly one slot.
TARGET_POOL = {1: 12, 2: 11, 3: 11, 4: 18, 5: 16}


# ---------------------------------------------------------------- SNBT

class Byte(int):
    """An NBT byte, so rig slot indices serialize as `0b`."""


def snbt(v):
    if isinstance(v, Byte):
        return "%db" % v
    if isinstance(v, int):
        return str(v)
    if isinstance(v, str):
        return json.dumps(v, ensure_ascii=False)
    if isinstance(v, list):
        return "[%s]" % ",".join(snbt(x) for x in v)
    if isinstance(v, dict):
        return "{%s}" % ",".join("%s:%s" % (_key(k), snbt(x)) for k, x in v.items())
    raise TypeError(v)


def _key(k):
    return k if re.fullmatch(r"[A-Za-z0-9_.+-]+", k) else json.dumps(k)


def full(path):
    """A bare mod path ("worm") or a full id ("minecraft:string") — the stall carries vanilla stock too."""
    return path if ":" in path else "riverfishing:" + path


def short(path):
    return path.split(":")[-1]


# ---------------------------------------------------------------- the numbers we mirror from Java
# Parsed, not copied: a rename or a re-balance on the Java side fails verification instead of
# silently shipping a rod the assembly GUI would refuse to socket.

def _java(rel):
    with open(os.path.join(JAVA, rel), encoding="utf-8", errors="replace") as f:
        return f.read()


def rod_types():
    """jsonKey -> (takesReel, minReel, maxReel) from RodType.java's enum header."""
    out = {}
    for m in re.finditer(r'^\s*[A-Z_]+\s*\(\s*"(\w+)"\s*,\s*[\d.]+\s*,\s*(true|false)\s*,\s*(\d+)\s*,\s*(\d+)\s*,',
                         _java("component/RodType.java"), re.M):
        out[m.group(1)] = (m.group(2) == "true", int(m.group(3)), int(m.group(4)))
    return out


def tackle_forms():
    """item id -> (isRig, weights, defaultLinkCm) from TackleForm.java's enum header."""
    out = {}
    for m in re.finditer(r'^\s*[A-Z_]+\s*\(\s*"(\w+)"\s*,\s*(?:true|false)\s*,\s*(true|false)\s*,'
                         r'\s*(?:true|false)\s*,\s*new int\[\]\{([\d,\s]+)\}\s*,\s*(\d+)\s*\)',
                         _java("tackle/TackleForm.java"), re.M):
        weights = [int(x) for x in m.group(3).split(",")]
        out[m.group(1)] = (m.group(2) == "true", weights, int(m.group(4)))
    return out


def max_line_diameter(reel_size):
    """TackleCompat.maxLineDiameter — kept in sync by verification, see check_java_constants()."""
    return 0.15 + reel_size / 1000.0 * 0.05


RODS = rod_types()
FORMS = tackle_forms()
STAMPED = {}    # every item the stall actually ships bench-graded -> its stamp, for the report


def stamp_tags(item_id):
    """The keys TackleForm.stamp() writes, or {} when the bench can't tie this item (built-in rod rigs)."""
    form = FORMS.get(short(item_id))
    if form is None:
        return {}
    is_rig, weights, link = form
    grams = weights[len(weights) // 2]                    # TackleForm.stockWeight()
    tags = {"TackleWeightG": grams}
    if is_rig:
        tags["LeaderLenCm"] = link
    else:
        tags["BalancePos"] = 1                            # the shop always ties centre-balanced
    if short(item_id) in ("spinner", "spoon"):
        tags["BladeSize"] = min(5, 1 + grams // 15)
    STAMPED[short(item_id)] = tags
    return tags


# ---------------------------------------------------------------- trade builders
# Each returns (file name, trade dict). A POOL slot is a LIST of these: one entry = full weight,
# k entries = 1/k each, so the slot as a whole is worth one pool draw (§trade-pool).

def sell(path, cost, count=1, xp=1):
    gives = {"id": full(path)}
    if count != 1:
        gives["count"] = count
    return short(path), {
        "wants": {"id": "minecraft:emerald", "count": cost},
        "gives": gives,
        "max_uses": 12, "xp": xp, "reputation_discount": DISCOUNT,
    }


def tackle(path, cost, count=1, xp=1):
    """§tackle-craft: a loose lure, bench-stamped so its grams count toward the cast."""
    name, trade = sell(path, cost, count, xp)
    tags = stamp_tags(path)
    assert tags, "%s is not a bench form — use sell()" % path
    trade["given_item_modifiers"] = [{"function": "minecraft:set_custom_data", "tag": snbt(tags)}]
    return name, trade


def house_blend():
    """§house-blend: the stall's own groundbait, sold already mixed.

    Base, barley, chopped worm and a spoon of maggot — the plain river blend everybody starts from, and
    deliberately GOOD RATHER THAN RIGHT: it fishes well for the silver fish it was built for and merely
    adequately for anything else. A ready recipe is a floor, not a ceiling, and this trade is where a
    player first meets that idea.

    The composition rides in custom_data exactly as GroundbaitNbt writes it. The colour is left out on
    purpose — the jar derives it from the parts, so writing it here would be a second copy of a number
    that can only ever disagree with the first.
    """
    name, trade = sell("groundbait_powder", 5, 8, 6)
    parts = [{"id": "groundbait_powder", "n": 3}, {"id": "pearl_barley", "n": 2},
             {"id": "worm", "n": 2}, {"id": "maggot", "n": 1}]
    trade["given_item_modifiers"] = [
        {"function": "minecraft:set_custom_data", "tag": snbt({"Groundbait": {"Parts": parts}})}]
    return "house_blend", trade


def stack(item_id, custom=None):
    s = {"id": full(item_id), "count": 1}
    if custom:
        s["components"] = {"minecraft:custom_data": custom}
    return s


def rig_stack(rig_id, contents):
    """A rig with its slots filled in RigLayout order; every bench-tiable part is stamped."""
    items = [{"Slot": Byte(i), "Item": stack(p, stamp_tags(p) or None)}
             for i, p in enumerate(contents) if p]
    custom = {"RigContents": {"Items": items}}
    custom.update(stamp_tags(rig_id))
    return stack(rig_id, custom)


def assembled(name, rod, reel, line, rig_id, contents, cost, xp):
    """§assembled-only: a ready-to-cast rod. maxUses 8 — the stall keeps fewer built rods on the rack."""
    parts = {}
    if reel:
        parts["Reel"] = stack(reel)
    parts["Line"] = stack(line)
    parts["Rig"] = rig_stack(rig_id, contents)
    return name, {
        "wants": {"id": "minecraft:emerald", "count": cost},
        "gives": {"id": full(rod)},
        "given_item_modifiers": [
            {"function": "minecraft:set_custom_data", "tag": snbt({"RodComponents": parts})}],
        "max_uses": 8, "xp": xp, "reputation_discount": DISCOUNT,
    }


def rig_only(name, rig_id, contents, cost, xp):
    return name, {
        "wants": {"id": "minecraft:emerald", "count": cost},
        "gives": {"id": full(rig_id)},
        "given_item_modifiers": [
            {"function": "minecraft:set_custom_data",
             "tag": snbt(rig_stack(rig_id, contents)["components"]["minecraft:custom_data"])}],
        "max_uses": 8, "xp": xp, "reputation_discount": DISCOUNT,
    }


def prime_threshold(fish):
    with open(os.path.join(PROFILES, fish + ".json"), encoding="utf-8") as f:
        prof = json.load(f)
    return math.ceil(prof["weight_g"]["max"] * PRIME_FRACTION)


def buy(fish, emeralds, xp):
    """§prime-fish: only the top 30% of the species' weight range is accepted; the expected component
    both gates the trade and makes the cost slot show the "accepts from N" legend."""
    gives = {"id": "minecraft:emerald"}
    if emeralds != 1:
        gives["count"] = emeralds
    return "buy_" + fish, {
        "wants": {"id": full(fish), "components": {"riverfishing:prime": prime_threshold(fish)}},
        "gives": gives,
        "max_uses": 12, "xp": xp, "reputation_discount": DISCOUNT,
    }


FLOAT_RIG = ["float", "hook_10"]        # FLOAT, HOOK, (bait)
PREDATOR_RIG = ["leader", "spinner"]    # LEADER, LURE

# ---------------------------------------------------------------- the shop

# ---------------------------------------------------------------- §lure-color / §tackle-box kits

# Vanilla's own dye diffuse colours, so a shop lure mixes exactly the way a player's would.
DYE_RGB = [0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
           0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21]


def mix_dyes(rng, count=None):
    """Vanilla's leather-armour mix over 1–3 random dyes: average the channels, then rescale by the
    average of each dye's own brightest channel, which is what keeps mixes vivid instead of muddy."""
    picks = [DYE_RGB[rng.randrange(len(DYE_RGB))] for _ in range(count or 1 + rng.randrange(3))]
    r = g = b = peak = 0
    for c in picks:
        cr, cg, cb = (c >> 16) & 255, (c >> 8) & 255, c & 255
        r, g, b, peak = r + cr, g + cg, b + cb, peak + max(cr, cg, cb)
    n = len(picks)
    r, g, b, peak = r // n, g // n, b // n, peak // n
    top = max(r, g, b) or 1
    gain = peak / top
    return (int(r * gain) << 16) | (int(g * gain) << 8) | int(b * gain)


def roll_weight(item_id, rng, lo, hi):
    """A weight inside the requested window, clamped to what the BENCH can tie — the window is the kit's
    intent, the ladder is the form's reality (a spinner tops out at 14 g). Mirrors ModVillagers.rollWeight."""
    form = FORMS.get(short(item_id))
    if form is None:
        return None
    _, weights, _ = form
    lo, hi = max(lo, weights[0]), min(hi, weights[-1])
    if hi <= lo:
        return max(weights[0], min(lo, weights[-1]))
    return rng.randrange(lo, hi + 1)


def inner(item_id, count, rng, lo=0, hi=0, dyed=False):
    """One item as it sits INSIDE a box: SNBT in ItemStack.OPTIONAL_CODEC's shape."""
    comps = {}
    tags = stamp_tags(item_id)
    if tags:
        tags = dict(tags)
        if hi:
            grams = roll_weight(item_id, rng, lo, hi)
            if grams is not None:
                tags["TackleWeightG"] = grams
                if short(item_id) in ("spinner", "spoon"):
                    tags["BladeSize"] = min(5, 1 + grams // 15)
        comps["minecraft:custom_data"] = tags
    if dyed:
        comps["minecraft:dyed_color"] = mix_dyes(rng)
    out = {"id": full(item_id), "count": count}
    if comps:
        out["components"] = comps
    return out


def kit(name, box_id, name_key, colour, parts, cost, xp, seed):
    """§tackle-box kits: a named, dyed box that arrives with the tackle for one kind of fishing in it.

    The rolls are BAKED at generation time — a datapack trade cannot roll per purchase — so each kit
    ships as several variants sharing one pool slot, which is how the stall varies anything else here."""
    rng = random.Random(seed)
    items = []
    for slot, part in enumerate(parts):
        items.append({"s": slot, "i": inner(*part[:2], rng, *part[2:])})
    return name, {
        "wants": {"id": "minecraft:emerald", "count": cost},
        "gives": {"id": full(box_id)},
        "given_item_modifiers": [
            {"function": "minecraft:set_custom_data", "tag": snbt({"Box": items})},
            {"function": "minecraft:set_components", "components": {
                "minecraft:custom_name": {"translate": name_key, "italic": False},
                "minecraft:dyed_color": colour}},
        ],
        "max_uses": 8, "xp": xp, "reputation_discount": DISCOUNT,
    }


# The four kits, contents settled in playtest. (item, count[, minG, maxG[, dyed]])
FLOAT_KIT = [("float", 2), ("hook_10", 4), ("hook_12", 4), ("worm", 8), ("maggot", 8)]
PIKE_KIT = [("leader", 2), ("spinner", 1, 3, 35, True), ("spoon", 1, 3, 35, True),
            ("wobbler", 1, 3, 35, True)]
CARP_KIT = [("boilie", 16), ("hook_6", 4), ("hook_8", 4), ("corn", 16), ("line_mono_030", 1),
            ("rig_flat_feeder", 1, 40, 60)]
SEA_KIT = [("leader_titanium", 2), ("octopus_jig", 1, 100, 200, True),
           ("giant_spoon", 1, 100, 200, True), ("rig_ground", 1, 100, 200), ("hook_2", 3),
           ("hook_4", 3), ("line_braid_040", 1), ("livebait", 4)]


def kits(name, box, key, colour, parts, cost, xp, variants):
    """The same kit rolled `variants` ways; they share one pool slot (§trade-pool)."""
    return [kit("%s_%d" % (name, i + 1), box, key, colour, parts, cost, xp, hash((name, i)) & 0xFFFF)
            for i in range(variants)]


def painted(form_id, cost, count, xp, shades=3):
    """§lure-color: the stall paints what it ties. Baked colours, so each form ships a few shades that
    share its slot — a rack of identical silver blades is not a tackle shop."""
    out = []
    for i in range(shades):
        rng = random.Random(hash((form_id, i)) & 0xFFFF)
        name, trade = tackle(form_id, cost, count, xp)
        trade = dict(trade)
        trade["given_item_modifiers"] = list(trade["given_item_modifiers"]) + [
            {"function": "minecraft:set_components",
             "components": {"minecraft:dyed_color": mix_dyes(rng)}}]
        out.append(("%s_%d" % (name, i + 1), trade))
    return out


# One list per level; each element is one POOL SLOT (variants inside it share the slot).

POOL = {
    # ---- Level 1 — Novice: starter consumables. 8 gear slots + 2 fish = 10.
    1: [
        [sell("worm", 1, 12, 1)],
        [sell("bloodworm", 1, 8, 1)],
        [sell("float", 1, 2, 2)],
        [sell("groundbait_powder", 1, 6, 2)],
        # §bait-crops: the "buy from traders" leg of the seed economy.
        [sell("corn_seeds", 1, 3, 1), sell("pea_seeds", 1, 3, 1), sell("barley_seeds", 1, 3, 1)],
        [sell("line_mono_014", 2, 1, 3)],
        [sell("worm_farm", 4, 1, 4)],
        # §vanilla-stock: every reeled rod recipe wants string for the guide wraps (§tackle-craft),
        # and string is a miserable early grind — the village shop is the honest way out.
        [sell("minecraft:string", 1, 4, 1)],
    ],
    # ---- Level 2 — Apprentice: float-fishing kit, including ready-made tackle. 8 + 2 = 10.
    2: [
        [sell("maggot", 1, 10, 2)],
        [sell("reel_2000", 4, 1, 5), sell("reel_3000", 6, 1, 6)],
        [sell("line_mono_018", 2, 1, 4)],
        # §groundbait-one-jar: BALLAST, not a second groundbait — the dial the mixing system turns on.
        [sell("groundbait_soil", 1, 8, 3)],
        [sell("bait_trap", 3, 1, 4)],                       # slowly farms livebait (§livebait)
        [sell("minecraft:oak_boat", 4, 1, 5)],              # §vanilla-stock: trolling needs a boat
        [assembled("assembled_bamboo_rod", "bamboo_rod", None, "line_mono_018",
                   "rig_float_light", FLOAT_RIG, 9, 8)],
    ],
    # ---- Level 3 — Journeyman: lures + a ready spinning setup. 5 + 2 = 7.
    3: [
        painted("spinner", 3, 1, 8) + painted("spoon", 4, 1, 8) + painted("silicone", 2, 2, 6),
        [sell("line_braid_016", 5, 1, 10), sell("line_fluoro_020", 5, 1, 10)],
        [sell("leader_fluoro", 3, 2, 6)],
        [sell("fish_finder", 14, 1, 12)],                   # §QoL: read the swim before you cast
        [sell("keepnet_small", 5, 1, 6)],                   # §keepnet: somewhere to put the catch
        kits("kit_float", "tackle_box_small", "kit.riverfishing.float", 0xC8D8E8,
             FLOAT_KIT, 7, 10, 1)
        + kits("kit_pike", "tackle_box_medium", "kit.riverfishing.pike", 0x4A7A3A,
               PIKE_KIT, 18, 16, 3),
        [assembled("assembled_spinning_rod", "spinning_rod", "reel_2000", "line_braid_016",
                   "rig_predator", PREDATOR_RIG, 16, 14)],
    ],
    # ---- Level 4 — Expert: predator/carp gear, winter tackle, ready feeder. 12 + 2 = 14.
    4: [
        painted("wobbler", 7, 1, 15) + painted("crankbait", 7, 1, 15) + painted("popper", 6, 1, 14),
        [sell("livebait", 2, 3, 8)],
        [sell("boilie", 3, 8, 10)],
        [sell("reel_5000", 10, 1, 15), sell("reel_6000", 13, 1, 16)],
        [sell("line_fluoro_030", 6, 1, 12)],
        [sell("ice_auger", 9, 1, 14)],                      # §ice-fishing: drill your first hole
        [sell("mormyshka", 3, 2, 8)],
        [sell("maggot_farm", 5, 1, 8)],                     # §bait-farm
        [house_blend()],                                    # §house-blend: the stall's own mix
        [sell("keepnet_medium", 9, 1, 10), sell("keepnet_large", 14, 1, 14)],
        kits("kit_carp", "tackle_box_medium", "kit.riverfishing.carp", 0xB0863C, CARP_KIT, 21, 18, 2),
        # §vanilla-stock + §tackle-craft: the saltwater reels are gated on ocean drops. Selling the
        # INPUTS keeps the gate priced without making it hinge on guardian RNG.
        [sell("minecraft:prismarine_shard", 5, 4, 10)],
        [assembled("assembled_feeder_rod", "feeder_rod", "reel_5000", "line_mono_025",
                   "rig_feeder", ["hook_8"], 18, 18)],
        # Reel-less ice rod: the mormyshka IS the tackle, so it ships fitted or the rod is useless.
        [assembled("assembled_winter_rod", "winter_rod", None, "line_mono_014",
                   "rig_winter", ["mormyshka"], 14, 14)],
    ],
    # ---- Level 5 — Master: the trade-only prestige gear (§progression). 10 + 2 = 12.
    5: [
        [sell("digital_alarm", 10, 1, 25)],
        [sell("keepnet_huge", 20, 1, 24)],
        kits("kit_sea", "tackle_box_large", "kit.riverfishing.sea", 0x2E5E8A, SEA_KIT, 51, 34, 3),
        [sell("leader_titanium", 8, 1, 20)],
        # §vanilla-stock: the 14000 reel and the trolling rod each want a nautilus shell, the single
        # worst piece of RNG in the ladder. Master tier sells it.
        [sell("minecraft:nautilus_shell", 10, 1, 22)],
        [sell("reel_7000", 16, 1, 26), sell("reel_8000", 18, 1, 28), sell("reel_10000", 22, 1, 30),
         sell("reel_12000", 26, 1, 32), sell("reel_14000", 30, 1, 34)],
        # §sea-lines-2: the heavy tier for the 8000-14000 reels — one mono slot, one braid/fluoro slot.
        [sell("line_mono_050", 8, 1, 20), sell("line_mono_060", 10, 1, 22),
         sell("line_mono_070", 12, 1, 24), sell("line_mono_080", 14, 1, 26)],
        [sell("line_braid_030", 10, 1, 22), sell("line_braid_040", 14, 1, 24),
         sell("line_braid_050", 16, 1, 26), sell("line_braid_060", 18, 1, 28),
         sell("line_fluoro_040", 12, 1, 24)],
        [sell("hook_2", 3, 3, 10), sell("hook_1", 4, 3, 12)],
        [assembled("assembled_carp_rod", "carp_rod", "reel_6000", "line_braid_030",
                   "rig_carp", ["hook_4"], 30, 30)],
        [assembled("assembled_bottom_rod", "bottom_rod", "reel_7000", "line_braid_030",
                   "rig_catfish", ["leader", "hook_2"], 28, 28)],
        # sea-tackle: the saltwater counter — master-tier gate to the ocean, one pool slot.
        [assembled("assembled_sea_spin_rod", "sea_spin_rod", "reel_8000", "line_braid_040",
                   "rig_predator", PREDATOR_RIG, 30, 28),
         assembled("assembled_surf_rod", "surf_rod", "reel_8000", "line_mono_050",
                   "rig_ground", ["hook_2"], 34, 30),
         assembled("assembled_boat_rod", "boat_rod", "reel_10000", "line_braid_050",
                   "rig_catfish", ["leader", "hook_1"], 34, 30),
         assembled("assembled_trolling_rod", "trolling_rod", "reel_12000", "line_braid_060",
                   "rig_predator", ["leader_titanium", "wobbler"], 40, 34)],
    ],
}

# Fish buys. Prices unchanged from 0.5.0; asp / white_bream / mirror_carp were unsellable oversights
# and join here. The five koi stay uncommercial on purpose. Each tier shares FISH_SLOTS_PER_TIER slots.
FISH = {
    1: [("bleak", 1, 1), ("gudgeon", 1, 1), ("roach", 1, 1), ("bluegill", 1, 1),
        ("round_goby", 1, 1), ("common_dace", 1, 1), ("rotan", 1, 2), ("smelt", 1, 3),
        # §giants-and-minnows (0.8.0)
        ("gorchak", 1, 1), ("verkhovka", 1, 1), ("sculpin", 1, 2), ("tubenose_goby", 1, 1)],
    2: [("crucian_carp", 2, 2), ("perch", 2, 2), ("ruffe", 1, 2), ("rudd", 2, 2), ("sabrefish", 2, 2),
        ("white_eye_bream", 2, 3), ("nase", 2, 4), ("vimba", 3, 5), ("white_bream", 2, 2),
        # §giants-and-minnows (0.8.0)
        ("golden_crucian", 2, 3)],
    3: [("bream", 3, 4), ("ide", 3, 5), ("chub", 3, 5), ("tench", 4, 5), ("blue_bream", 2, 3),
        ("pike", 5, 8), ("volga_zander", 4, 6), ("pink_salmon", 4, 8), ("whitefish", 4, 8),
        ("asp", 6, 9),
        # §florida-nine: the two small cichlids sit with the pan-fish of their weight.
        ("oscar", 2, 4), ("mayan_cichlid", 2, 3)],
    4: [("carp", 6, 12), ("mirror_carp", 7, 13), ("grass_carp", 9, 14), ("zander", 6, 10),
        # §florida-nine: the mid-weight predators, priced against largemouth bass and trout.
        ("peacock_bass", 7, 13), ("bullseye_snakehead", 6, 11), ("bluefish", 6, 12),
        ("trout", 6, 12), ("largemouth_bass", 7, 12), ("rainbow_trout", 7, 12), ("grayling", 7, 12),
        ("burbot", 5, 10), ("mackerel", 3, 6), ("herring", 2, 4), ("garfish", 3, 6),
        ("flounder", 4, 8), ("char", 6, 12), ("lenok", 6, 12), ("salmon", 10, 18)],
    5: [("catfish", 12, 25), ("eel", 8, 15), ("channel_catfish", 10, 20), ("sterlet", 16, 30),
        # §florida-nine: the big saltwater four, against the mahi/wahoo/barracuda band.
        ("tarpon", 20, 32), ("snook", 11, 21), ("jack_crevalle", 12, 22), ("striped_bass", 12, 23),
        ("silver_carp", 14, 26), ("seabass", 7, 14), ("cod", 9, 18), ("saithe", 7, 14),
        ("conger", 13, 24), ("ray", 12, 22), ("mahi", 10, 20), ("wahoo", 14, 26),
        ("yellowfin_tuna", 20, 34), ("barracuda", 8, 16), ("blue_marlin", 28, 40),
        ("sailfish", 18, 30), ("swordfish", 24, 36), ("mako", 22, 34), ("wild_carp", 14, 28),
        ("taimen", 24, 36), ("sturgeon", 26, 38), ("halibut", 22, 34),
        # §giants-and-minnows (0.8.0)
        ("arapaima", 26, 38), ("beluga", 30, 44), ("piraiba", 22, 34), ("goliath_grouper", 24, 36), ("bull_shark", 24, 36), ("frilled_shark", 26, 38), ("golden_dorado", 12, 22)],
}


# ---------------------------------------------------------------- writing

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def weighted(trade, weight):
    """§trade-pool: a per-entry weight. A failing merchant_predicate returns a null offer, which the
    without-duplicates draw re-rolls, so k variants at 1/k share ONE pool slot."""
    if weight < 1.0:
        trade = dict(trade)
        trade["merchant_predicate"] = {"condition": "minecraft:random_chance",
                                       "chance": round(weight, 5)}
    return trade


def generate():
    for d in (TRADE_DIR, SET_DIR, TAG_DIR):
        shutil.rmtree(d, ignore_errors=True)
    tags, weights = {}, {}

    for lvl in range(1, 6):
        tags[lvl], weights[lvl] = [], 0.0
        for slot in POOL[lvl]:
            for name, trade in slot:
                write(os.path.join(TRADE_DIR, str(lvl), name + ".json"), weighted(trade, 1.0 / len(slot)))
                tags[lvl].append("riverfishing:fisherman/%d/%s" % (lvl, name))
                weights[lvl] += 1.0 / len(slot)

        share = FISH_SLOTS_PER_TIER / len(FISH[lvl])
        for fish, emeralds, xp in FISH[lvl]:
            name, trade = buy(fish, emeralds, xp)
            write(os.path.join(TRADE_DIR, str(lvl), name + ".json"), weighted(trade, share))
            tags[lvl].append("riverfishing:fisherman/%d/%s" % (lvl, name))
            weights[lvl] += share

        write(os.path.join(TAG_DIR, "level_%d.json" % lvl), {"values": tags[lvl]})
        write(os.path.join(SET_DIR, "level_%d.json" % lvl), {
            "amount": TRADES_PER_LEVEL,
            "random_sequence": "riverfishing:trade_set/fisherman/level_%d" % lvl,
            "trades": "#riverfishing:fisherman/level_%d" % lvl,
        })

    # §starter-fish: unweighted copies of the four ubiquitous smalls, in no level pool. The mixin
    # picks one from this tag when a fresh level-1 stall drew no fish buy at all.
    starter = []
    for fish in STARTER_FISH:
        emeralds, xp = next((e, x) for f, e, x in FISH[1] if f == fish)
        _, trade = buy(fish, emeralds, xp)
        write(os.path.join(TRADE_DIR, "starter", fish + ".json"), trade)
        starter.append("riverfishing:fisherman/starter/" + fish)
    write(os.path.join(TAG_DIR, "starter.json"), {"values": starter})

    return tags, weights


# ---------------------------------------------------------------- verification

def check(ok, msg, fails):
    if not ok:
        fails.append(msg)
    return ok


def check_java_constants(fails):
    """The two formulas mirrored in Python must still read that way in Java."""
    src = _java("component/TackleCompat.java")
    check("return 0.15 + reelSize / 1000.0 * 0.05;" in src,
          "TackleCompat.maxLineDiameter changed — update max_line_diameter()", fails)
    src = _java("item/FishItem.java")
    check("Math.ceil(weightMaxG * com.riverfishing.registry.ModVillagers.PRIME_FRACTION)" in src,
          "FishItem.primeThresholdG changed — update prime_threshold()", fails)
    src = _java("tackle/TackleForm.java")
    check("return weights[weights.length / 2];" in src,
          "TackleForm.stockWeight changed — update stamp_tags()", fails)
    check("Math.min(5, 1 + grams / 15)" in src,
          "TackleForm blade sizing changed — update stamp_tags()", fails)
    # The species list lives in the tag; Java only knows the tag id, so that is all we cross-check.
    check("fisherman/starter" in _java("registry/ModVillagers.java"),
          "ModVillagers no longer references the fisherman/starter tag", fails)


def fish_species():
    """ModItems.FISH_SPECIES — the registry truth for which species are items at all."""
    src = _java("registry/ModItems.java")
    body = src[src.index("FISH_SPECIES = {"):]
    return set(re.findall(r'"(\w+)"', body[:body.index("};")]))


def known_ids():
    """Every registered riverfishing id: the lang file has one item/block entry each, and fish items
    key off `fish.riverfishing.*` instead, so they come from the registration array."""
    with open(LANG, encoding="utf-8") as f:
        keys = json.load(f).keys()
    return {k.split(".", 2)[2] for k in keys
            if re.match(r"^(item|block)\.riverfishing\.", k)} | fish_species()


VANILLA_STOCK = {"minecraft:emerald", "minecraft:string", "minecraft:oak_boat",
                 "minecraft:prismarine_shard", "minecraft:nautilus_shell"}


def pantry_ids():
    """§groundbait-one-jar: what a mix may legally be made of, read off GroundbaitMix's own pantry."""
    src = os.path.join(HERE, "..", "common", "src", "main", "java", "com", "riverfishing",
                       "groundbait", "GroundbaitMix.java")
    with open(src, encoding="utf-8") as f:
        java = f.read()
    # The base goes in under the BASE_ID constant rather than a literal, so it needs its own read —
    # and asserting it landed is the point, because the base is the one component every blend has.
    ids = set(re.findall(r'put\("([\w:/]+)",', java))
    ids |= set(re.findall(r'BASE_ID = "([\w:/]+)"', java))
    assert len(ids) > 20, "the pantry read came back empty — GroundbaitMix moved or changed shape"
    return ids


PANTRY_IDS = pantry_ids()


def walk_ids(node, out):
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "id" and isinstance(v, str):
                out.add(v)
            else:
                walk_ids(v, out)
    elif isinstance(node, list):
        for v in node:
            walk_ids(v, out)


def verify(tags, weights):
    fails = []
    check_java_constants(fails)
    ids, files = set(), 0

    for path in sorted(_all_json()):
        files += 1
        with open(path, encoding="utf-8") as f:
            try:
                doc = json.load(f)
            except ValueError as e:
                fails.append("%s does not parse: %s" % (path, e))
                continue
        walk_ids(doc, ids)
        for mod in doc.get("given_item_modifiers", []):
            tag = mod.get("tag", "")
            # §groundbait-one-jar: the ids inside a Groundbait tag are PANTRY ids, not item ids — bare
            # for this mod's own components, `minecraft:`-prefixed for vanilla ones — so the registry
            # check below would reject every one of them. They get their own check, against the pantry,
            # because a typo there does not crash: it silently sells a weaker blend, which is worse.
            for pantry in re.findall(r"Groundbait:\{Parts:\[(.*?)\]\}", tag):
                for pid in re.findall(r'id:"([\w:/]+)"', pantry):
                    check(pid in PANTRY_IDS, "unknown pantry component %s in a trade" % pid, fails)
                tag = tag.replace(pantry, "")
            ids |= set(re.findall(r'id:"([\w:/]+)"', tag))

    known = known_ids()
    for i in sorted(ids):
        if i in VANILLA_STOCK:
            continue
        check(i.startswith("riverfishing:") and i.split(":", 1)[1] in known,
              "unknown item id %s — a typo here would be a startup crash" % i, fails)

    # pool shape
    for lvl in range(1, 6):
        check(abs(weights[lvl] - TARGET_POOL[lvl]) < 1e-9,
              "level %d effective pool %.4f, expected %d" % (lvl, weights[lvl], TARGET_POOL[lvl]), fails)
        unweighted = sum(1 for slot in POOL[lvl] if len(slot) == 1)
        check(unweighted >= TRADES_PER_LEVEL,
              "level %d has only %d always-on entries — cannot guarantee %d offers"
              % (lvl, unweighted, TRADES_PER_LEVEL), fails)
        with open(os.path.join(SET_DIR, "level_%d.json" % lvl), encoding="utf-8") as f:
            check(json.load(f)["amount"] == TRADES_PER_LEVEL, "level %d amount wrong" % lvl, fails)

    # every non-koi registered species is buyable somewhere, and the koi nowhere
    sellable = {f for lvl in FISH for f, _, _ in FISH[lvl]}
    species = fish_species()
    koi = {s for s in species if s.startswith("carp_koi")}
    check(sellable == species - koi,
          "buy tables miss %s / sell unregistered %s" % (sorted(species - koi - sellable),
                                                         sorted(sellable - species)), fails)
    check(not (sellable & koi), "the koi must stay uncommercial", fails)
    check(all(f in {x for x, _, _ in FISH[1]} for f in STARTER_FISH),
          "a starter fish is not a level-1 species", fails)

    # §assembled-only: read the written SNBT back and re-check the assembly rules
    rods = []
    for lvl in range(1, 6):
        for name in os.listdir(os.path.join(TRADE_DIR, str(lvl))):
            with open(os.path.join(TRADE_DIR, str(lvl), name), encoding="utf-8") as f:
                doc = json.load(f)
            tag = "".join(m.get("tag", "") for m in doc.get("given_item_modifiers", []))
            if "RodComponents" not in tag:
                continue
            rod = short(doc["gives"]["id"])
            key = rod[:-4] if rod.endswith("_rod") else rod
            reel = re.search(r'Reel:\{id:"riverfishing:reel_(\d+)"', tag)
            line = re.search(r'Line:\{id:"riverfishing:line_\w+_(\d+)"', tag)
            takes, lo, hi = RODS[key]
            dia = int(line.group(1)) / 100.0
            if reel:
                size = int(reel.group(1))
                band = takes and lo <= size <= hi
                spool = dia <= max_line_diameter(size) + 1e-6
                check(band, "%s: reel %d outside %s band %d..%d" % (name, size, key, lo, hi), fails)
                check(spool, "%s: line %.2f over reel %d spool limit %.2f"
                      % (name, dia, size, max_line_diameter(size)), fails)
                rods.append((lvl, rod, size, dia, band and spool))
            else:
                check(not takes, "%s: %s takes a reel but ships without one" % (name, key), fails)
                rods.append((lvl, rod, 0, dia, True))

    print("wrote %d files: %d trades + 5 tags + 1 starter tag + 5 trade sets"
          % (files, sum(len(v) for v in tags.values()) + len(STARTER_FISH)))
    print()
    print("effective pool per level (draws = %d):" % TRADES_PER_LEVEL)
    for lvl in range(1, 6):
        ids_at = len(tags[lvl])
        fish_share = FISH_SLOTS_PER_TIER / weights[lvl]
        print("  L%d  %2d tag ids -> %4.1f effective slots  (%2d gear + %d fish)"
              "   fish share %4.1f%%   E[fish offers] %.2f"
              % (lvl, ids_at, weights[lvl], round(weights[lvl]) - FISH_SLOTS_PER_TIER,
                 FISH_SLOTS_PER_TIER, 100 * fish_share, TRADES_PER_LEVEL * fish_share))
    total = sum(TRADES_PER_LEVEL * FISH_SLOTS_PER_TIER / weights[lvl] for lvl in range(1, 6))
    print("  maxed fisherman: %.2f fish offers of %d (%.1f%%)"
          % (total, 5 * TRADES_PER_LEVEL, 100 * total / (5 * TRADES_PER_LEVEL)))
    print()
    print("assembled tackle (reel band + spool limit):")
    for lvl, rod, size, dia, good in sorted(rods):
        key = rod[:-4] if rod.endswith("_rod") else rod
        takes, lo, hi = RODS[key]
        band = "%d..%d" % (lo, hi) if takes else "reel-less"
        limit = "<= %.2f" % max_line_diameter(size) if size else "n/a"
        print("  L%d %-16s reel %-6s band %-11s line %.2f mm  limit %-8s %s"
              % (lvl, rod, size or "-", band, dia, limit, "OK" if good else "FAIL"))
    print()
    print("bench-stamped stock (§tackle-craft, mirrors TackleForm.stamp):")
    for item in sorted(STAMPED):
        print("  %-14s %s" % (item, snbt(STAMPED[item])))

    if fails:
        print()
        for f in fails:
            print("FAIL: " + f)
        return 1
    print()
    print("all checks pass: %d files parse, pool shapes match %s, every id known, every rod legal"
          % (files, TARGET_POOL))
    return 0


def _all_json():
    for base in (TRADE_DIR, SET_DIR, TAG_DIR):
        for root, _, names in os.walk(base):
            for n in names:
                if n.endswith(".json"):
                    yield os.path.join(root, n)


if __name__ == "__main__":
    sys.exit(verify(*generate()))
