# -*- coding: utf-8 -*-
"""§modded-biomes: the biome tags that tell the mod what kind of water a modded biome holds.

    py tools/gen_biome_tags.py

Terralith and Oh The Biomes You'll Go add a couple of hundred biomes. Vanilla tags carry most of what
the habitat model needs (forest, taiga, mountain, jungle), but nothing in vanilla says "this water is
salt" or "this is a cherry grove by another name" — and the mod's sea species, its swamp species and
its koi all hang off exactly that. So the lists live here, as tag files with {"id": …, "required":
false} entries: a world without either mod loads them as empty, which is what "required: false" is
for, and nobody has to keep a Java table in step with two mods' release notes.

Sources: the author's own survey of both mods, biome by biome, in this session.
"""
import io, json, os

SALT = """terralith:alpha_islands terralith:alpha_islands_winter terralith:basalt_cliffs
terralith:deep_warm_ocean terralith:gravel_beach terralith:mirage_isles terralith:white_cliffs
byg:dead_sea byg:rocky_beach byg:snowy_rocky_black_beach byg:snowy_black_beach byg:white_beach
byg:rainbow_beach byg:tropical_islands byg:coral_mangroves""".split()

FRESH = """terralith:amethyst_rainforest terralith:blooming_valley terralith:caldera
terralith:cloud_forest terralith:desert_oasis terralith:frozen_cliffs terralith:glacial_chasm
terralith:ice_marsh terralith:jungle_mountains terralith:lavender_forest terralith:lavender_valley
terralith:lush_desert terralith:lush_valley terralith:moonlight_valley terralith:orchid_swamp
terralith:red_oasis terralith:rocky_jungle terralith:sakura_grove terralith:sakura_valley
terralith:sandstone_valley terralith:shield terralith:shield_clearing terralith:siberian_grove
terralith:siberian_taiga terralith:snowy_cherry_grove terralith:snowy_maple_forest
terralith:snowy_shield terralith:temperate_highlands terralith:tropical_jungle
terralith:valley_clearing terralith:volcanic_crater terralith:warm_river terralith:wintry_forest
terralith:wintry_lowlands terralith:yellowstone terralith:yosemite_lowlands
terralith:cave/fungal_caves terralith:cave/underground_jungle
byg:ancient_forest byg:aspen_boreal byg:aspen_forest byg:autumnal_valley byg:bayou byg:blue_taiga
byg:bluff_steeps byg:boreal_forest byg:cold_swamplands byg:coniferous_forest byg:crag_gardens
byg:cika_woods byg:cherry_blossom_forest byg:cypress_swamplands byg:deciduous_forest
byg:dover_mountains byg:ebony_woods byg:enchanted_forest byg:evergreen_taiga byg:flowering_meadow
byg:fragmented_jungle byg:glowshroom_bayou byg:great_lakes byg:guiana_shield byg:jacaranda_forest
byg:lavender_field byg:lush_stacks byg:maple_taiga byg:meadow byg:orchard byg:overgrown_greens
byg:prairie byg:red_oak_forest byg:red_rock_valley byg:redwood_thicket byg:redwood_tropics
byg:rose_fields byg:seasonal_deciduous_forest byg:seasonal_taiga byg:shattered_glacier
byg:sierra_valley byg:snowy_blue_taiga byg:snowy_deciduous_forest byg:snowy_pumpkin_patch
byg:subzero_hypogeal byg:temperate_rainforest byg:tropical_rainforest byg:twilight_meadow
byg:weeping_witch_forest byg:zelkova_forest byg:fresh_water_lake byg:frozen_lake
byg:great_lake_isles byg:polluted_lake byg:oasis byg:marshlands byg:bog byg:mangrove_marshes""".split()

# A swamp is a water type in this mod, not a decoration: still, slow, tannic, and a different fish
# list. These read as swamp whatever their mod calls them.
SWAMP_ADD = """terralith:orchid_swamp terralith:ice_marsh byg:bayou byg:glowshroom_bayou
byg:cypress_swamplands byg:cold_swamplands byg:marshlands byg:bog byg:mangrove_marshes""".split()

# Koi live in cherry groves — and a sakura grove is a cherry grove with another name.
CHERRY = ["minecraft:cherry_grove", "terralith:sakura_grove", "terralith:sakura_valley",
          "terralith:snowy_cherry_grove", "byg:cherry_blossom_forest"]


def tag(values, always=()):
    out = [{"id": v, "required": False} for v in values]
    return {"replace": False, "values": list(always) + out}


def write(root, name, body):
    p = os.path.join(root, "common", "src", "main", "resources", "data", "riverfishing",
                     "tags", "worldgen", "biome", name + ".json")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    io.open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(body, indent=2, ensure_ascii=False) + "\n")
    return p


def merge_swamp(root):
    """The swamp tag already lists vanilla and Biomes O' Plenty — keep those, add the new ones."""
    p = os.path.join(root, "common", "src", "main", "resources", "data", "riverfishing",
                     "tags", "worldgen", "biome", "is_swamp.json")
    body = json.load(io.open(p, encoding="utf-8"))
    have = {v if isinstance(v, str) else v["id"] for v in body["values"]}
    for v in SWAMP_ADD:
        if v not in have:
            body["values"].append({"id": v, "required": False})
    io.open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(body, indent=2, ensure_ascii=False) + "\n")
    return p


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for root in (ROOT,):
    write(root, "is_saltwater", tag(SALT))
    write(root, "is_freshwater", tag(FRESH))
    write(root, "is_cherry", tag(CHERRY[1:], always=[CHERRY[0]]))
    merge_swamp(root)
print("saltwater %d · freshwater %d · swamp +%d · cherry %d"
      % (len(SALT), len(FRESH), len(SWAMP_ADD), len(CHERRY)))
