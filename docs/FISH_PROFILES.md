# Fish profiles — datapack guide

Every fish in River Fishing is driven by one JSON file. **All balance is datapack-overridable**: drop a
file at the same path in your datapack and the mod uses yours — rebalance any species, retune baits,
seasons, biomes, or level gates without touching code.

```
data/riverfishing/fish_profiles/<species>.json
```

> **Adding a brand-new species** needs a mod update (each species is its own item + sprite), but
> **everything about an existing species** — where it lives, what it eats, how it fights, how rare it
> is — is yours to change. Journal pages, bite engine, and trade gating all read the same profile, so
> your changes stay consistent everywhere.

## Full schema (annotated)

```json5
{
  "display": "Окунь",                      // fallback name (players normally see the lang entry)

  // Presence multiplier per water-body type; 0.0 = never lives there.
  // Types: puddle, pond, lake, river, swamp, sea
  "water_bodies": { "lake": 1.1, "river": 1.0, "pond": 0.8, "swamp": 0.5, "sea": 0.0, "puddle": 0.0 },

  "weight_g":  { "min": 50, "max": 2000, "mean": 250, "spread": 0.6 },  // grams; drives fight & value
  "length_cm": { "min": 10, "max": 45 },   // length follows weight by the cube-root law automatically

  "fight": {
    "strength": 0.5,        // 0..1 — raw pull (with weight, sets the required tackle)
    "stamina": 0.5,         // 0..1 — how long it keeps fighting
    "runs": 2,              // baseline number of runs
    "pattern": "aggressive",// steady | active_then_passive | aggressive | burst | relentless
    "aggression": 0.8       // 0..1 — head-shakes / thrash frequency
  },

  // The "ideal" block is BOTH the bite-engine scoring AND the journal's how-to-catch page.
  "ideal": {
    "rod": ["ultralight", "spinning"],     // stick|bamboo|pole|winter|ultralight|spinning|feeder|bottom|carp
    "reel_size": 3000, "reel_tolerance": 1000,          // 1000..7000, ± tolerance
    "line": { "type": "braid", "diameter_mm": 0.10, "tolerance_mm": 0.04 }, // mono|braid|fluoro
    "rig": ["predator"],                   // float | predator | carp | flat_feeder | catfish | grusha
    "groundbait": [],                      // powder | grain | pellet | cake (empty = groundbait-neutral)
    "bait": {                              // per-bait attraction, 0..~1.2 (best bait on the rig wins)
      // natural: worm, maggot, bloodworm, corn, pea, pearl_barley, boilie, livebait, chicken_liver, mormyshka
      // lures:   spinner, spoon, silicone, wobbler, popper, crankbait, jig, castmaster
      "livebait": 0.9, "crankbait": 1.0, "silicone": 0.95
    },
    "hook": { "ideal": 8, "tolerance": 3 },// hook size; outside ideal±tolerance the bite drops off
    "requires_leader": false               // true = toothy: bites through leaderless line
  },

  // Environmental multipliers (1.0 = neutral). Seasons need Serene Seasons (or map to MC "seasons").
  "season":  { "spring": 1.1, "summer": 0.9, "autumn": 1.3, "winter": 0.8 },
  "time":    { "dawn": 1.3, "day": 1.0, "dusk": 1.2, "night": 0.5 },
  "weather": { "clear": 1.0, "rain": 1.0, "thunder": 0.9 },

  "depth_pref": "mid",                     // surface | mid | bottom — where the rig must fish
  "distance_pref": { "min": 5, "max": 25 },// metres from the bank the fish holds at

  // HARD habitat gates — the fish simply is not present outside these.
  "habitat": { "depth_min": 1, "depth_max": 10, "width_min": 4 },   // water depth (blocks) / body width

  // Biome-group multipliers (empty = lives anywhere; any listed group must match).
  // Climate: cold | temperate | warm     Terrain: river_biome | ocean_biome | beach | jungle |
  // forest | taiga | mountain | dry | swamp | cherry
  "biomes": { "cold": 1.0, "temperate": 1.0, "warm": 0.6 },

  "base": 1.0,               // relative density: 1.1 = common bream-tier, 0.5 = rare prize
  "min_angler_level": 0      // journal level gate; 0 = ungated (omit for beginners' fish)
}
```

## How the engine uses it

Bite chance is a product of: water-body factor × habitat gates × biome groups × season × time ×
weather × best-bait score × tackle match (rod/reel/line/hook vs `ideal`) × `base` — so a profile is a
complete description of "when, where and on what". If any hard gate fails (habitat, `min_angler_level`,
water body at 0), the species is silently absent rather than merely rare.

Balance rules of thumb:

| Field | Common panfish | Mid predator | Trophy prize |
|---|---|---|---|
| `base` | 1.0–1.2 | 0.7–0.9 | 0.4–0.6 |
| `weight_g.spread` | 0.6 | 0.7 | 0.7–0.8 |
| `min_angler_level` | 0 | 3–4 | 5+ |
| `fight.strength` | 0.2–0.4 | 0.5–0.8 | 0.8+ |

## Worked example

`bluegill.json` in this repo is a fully commented "common panfish" reference; `channel_catfish.json`
shows a night bottom-feeder with livebait preference; `largemouth_bass.json` a topwater predator.

## Testing your changes

1. Put your file in a datapack: `data/riverfishing/fish_profiles/<species>.json`, `/reload`.
2. `/rffish` (op) has debug helpers; the water probe item prints the classified water body, and the
   journal page reflects your edited profile immediately — it reads the same data.
