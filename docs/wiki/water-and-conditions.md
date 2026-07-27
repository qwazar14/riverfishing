# Water and conditions

Where and when you fish matters as much as what you fish with. This page covers the world half of the [bite engine](fishing-mechanics.md#the-bite-engine).

## Water bodies

Every cast classifies the spot into one of six types. Each species' profile carries a factor per type; a factor of **0 means the fish does not live there at all**.

| Type | Key | How it is decided |
|---|---|---|
| Sea | `sea` | The biome is tagged ocean or deep ocean |
| River | `river` | The biome is tagged river |
| Swamp | `swamp` | The biome is in the `riverfishing:is_swamp` tag |
| Puddle | `puddle` | Inland still water, fewer than **12** connected blocks |
| Pond | `pond` | Inland still water, fewer than **200** connected blocks |
| Lake | `lake` | Inland still water, 200 or more connected blocks |

Ocean, river and swamp are decided purely **by biome** with no flood-fill at all, so classifying a spot in the middle of an ocean is free. Only ambiguous inland still water runs a flood-fill, and it is hard-capped at 400 blocks — just enough to tell puddle from pond from lake. Results are cached per spot.

The `is_swamp` tag lists vanilla Swamp and Mangrove Swamp, plus Biomes O' Plenty's Bayou, Marsh, Wetland, Bog and Mangrove when that mod is installed.

### Width

Width is the longest open-water span through your cast point — four cardinal scans (each capped at 64 blocks), taking the larger of the X and Z spans.

Width drives three things:

- **Habitat gates.** A species with `width_min: 16` is absent from anything narrower.
- **Long-range casting.** On water narrower than **12 blocks** the six long-range blanks get *"Water too narrow for a long cast"* and every fish's environment score drops to **×0.4**.
- **Species richness.** See [community](#every-water-is-its-own) below.

### Depth

Depth is the number of water blocks straight down from the surface at your cast point, counted up to 16. It is a **hard gate** on both sides: a species with `depth_min: 5` is absent from shallows, and one with `depth_max: 3` is absent from a deep hole.

Depth also darkens the water for [lure colour](rigs-and-baits.md#dyeing-lures) purposes.

## Biome groups

Every spot is classified into a **set** of groups. A species' `biomes` map lists the groups it lives in with a factor each; the best matching group wins, an empty map means "anywhere", and **no match at all means the fish is absent**.

| Group | Trigger |
|---|---|
| `cold` | Biome base temperature below 0.3 |
| `temperate` | Base temperature 0.3 to 0.95 |
| `warm` | Base temperature above 0.95 |
| `river_biome` | Vanilla `is_river` tag |
| `ocean_biome` | Vanilla `is_ocean` or `is_deep_ocean` |
| `deep` | Vanilla `is_deep_ocean` |
| `beach` | Vanilla `is_beach` |
| `taiga` | Vanilla `is_taiga` |
| `mountain` | Vanilla `is_mountain` or `is_hill` |
| `jungle` | Vanilla `is_jungle` |
| `forest` | Vanilla `is_forest` |
| `dry` | Vanilla `is_savanna` or `is_badlands` |
| `swamp` | Swamp water body, or the mod's `is_swamp` biome tag |
| `cherry` | Any biome whose id contains "cherry" — so vanilla Cherry Grove and BoP's Cherry Blossom Grove both count |

Exactly one of cold / temperate / warm always applies; the rest stack on top.

How many of the 79 species reference each group: **temperate** 55, **cold** 34, **warm** 32, **ocean_biome** 15, **deep** 12, **mountain** 8, **taiga** 7, **beach** 5, **swamp** 5, **cherry** 5. The `river_biome`, `forest`, `jungle` and `dry` groups are recognised but **no shipped species currently uses them** — they exist for datapacks.

Note that Biomes O' Plenty biomes carry the vanilla tags, so the habitat model works across it without any extra data.

## Time of day

| Bucket | Vanilla day time |
|---|---|
| Dawn | 23000–24000 and 0–1000 |
| Day | 1000–11000 |
| Dusk | 11000–13500 |
| Night | 13500–23000 |

The time factor is raised to the power **1.4**, so the daily rhythm is strongly felt. A burbot has `day: 0.0` and is genuinely uncatchable in daylight; a catfish at `night: 1.4` becomes `1.4^1.4 ≈ 1.61`.

## Weather

Three buckets — **clear**, **rain**, **thunder** — applied at face value with no exponent. Most bottom feeders like rain; surface hunters and sight-feeders prefer clear.

## Seasons

Seasons come from **Serene Seasons**. Without that mod installed the season is undefined and every species' season factor is a flat 1.0 — you lose the whole seasonal layer.

With it, the factor is raised to the power **1.5**, the strongest exponent in the engine. A burbot's `summer: 0.0` means no burbot in summer at all; smelt at `winter: 1.5` becomes `1.5^1.5 ≈ 1.84`.

**Spring is spawning season**: a fished-out water recovers about **2.5× faster**.

The mod also registers its [bait crops](blocks.md#bait-crops) into Serene Seasons' spring / summer / autumn crop tags.

## Barometric pressure

Minecraft has no atmosphere, so the mod synthesises one — deterministically, from the world seed and game time, with no saved state. Three layered sine waves (periods 1.6, 3.7 and 0.8 in-game days) produce a smooth synoptic wander between roughly **991 and 1035 hPa**.

The barometer deliberately **leads** the vanilla sky — reading it is how a real angler anticipates.

```
trend       = pressure now − pressure 3000 ticks ago     (3 in-game hours)
trendFactor = clamp(1 − trend × 0.045, 0.85, 1.28)       (falling glass boosts)
absFactor   = clamp(1.05 − (hPa − 1008)² × 0.00035, 0.82, 1.05)
biteFactor  = clamp(trendFactor × absFactor, 0.70, 1.35)
```

This is a **uniform multiplier on every species' bite weight**, so it scales the whole water's time-to-bite. A falling glass feeds everything; a settled bluebird high slows everything down. The absolute value nudges on top, peaking just under 1008 hPa.

The [Fish Finder](tools.md#fish-finder) shows the reading, an arrow (a change under 1 hPa across the window counts as steady), and a colour-coded outlook:

| Outlook | Bite factor |
|---|---|
| frenzy | ≥ 1.18 |
| active | ≥ 1.05 |
| fair | ≥ 0.92 |
| sluggish | below 0.92 |

## Feeding frenzy

Twice per in-game day the whole water feeds. The two windows are derived from the world seed and the day number, so **every player on the server sees the same frenzy** with no saved state:

- A morning-ish window starting somewhere in ticks 500–8500
- An evening/night window starting somewhere in ticks 11500–20500
- Each lasts **2000 ticks** (about 100 seconds)

During a frenzy bites come **3× faster**, fish visibly splash around your float, and the cast reports *"Feeding frenzy! The water is boiling"*. Landing a fish in one earns the *Feeding Time* advancement.

## Fed spots

Right-clicking water with groundbait creates a 3×3 fed zone. Full details in [Rigs and baits](rigs-and-baits.md#the-fed-spot): 0.6 freshness per feed stacking to 1.0, halving every 90 seconds, dead after 3 minutes, worth up to double bite weight and 40 % off the wait.

## Spot depletion

Fishing pressure is tracked **per chunk**, saved with the world.

| Event | Pressure added |
|---|---|
| Each cast | +0.022 (whole-chunk disturbance) |
| Each **landed** fish | +0.09 — **to that species only** |

Pressure decays with a **30000-tick half-life** (25 minutes idle), or about 10 minutes in spring. It caps at 1.5.

```
attractiveness = clamp(1 − pressure, 0.1, 1.0)
```

Fishing out the bream slows only the bream — the perch keep biting as before. About 40 minutes of steady casting will visibly deplete a spot, and around **ten kept fish of one species** in quick succession is enough to drive that species to the floor. Below 0.4 you are told *"This spot is fished out — move on"*. The floor is 10 %, so nowhere ever goes completely dead.

Releasing fish reverses it — see [Stocking](stocking.md).

## Every water is its own

Each **~128-block region** of water holds its own species set, derived from the **world seed**. This lake is a tench lake forever; the taimen river has to be *found*.

```
absent fraction by width:   < 8 blocks → 60 %      < 16 → 45 %
                            < 32      → 30 %       ≥ 32 → 20 %
```

So a small pond is species-poor and a big river or lake is rich. Then, per species:

- Species whose profile `base` is **0.95 or higher** live **everywhere** — the ubiquitous commons, so no water is ever dead. There are sixteen: Bream, Crucian Carp, Roach, Rudd, White Bream, Perch, Gudgeon, Ruffe, Bleak, Bluegill, Mackerel, Herring, Rotan, Nase, Smelt and Round goby.
- A [stocked](stocking.md) species is always present.
- Otherwise a stable seed hash decides. Above the absent threshold the species lives here at ×1.0 — and the top **8 %** of rolls become the water's **signature fish**, biting at **×1.8**.
- Below the threshold the species is absent, unless a recent release has left a temporary population there.

The [Fish Finder](tools.md#fish-finder) reads all of this out: which species are biting here right now, a *"Known for:"* line naming the signature fish, and live stock percentages.

## The full picture

Putting it together, this is the environment half of a fish's bite weight:

```
E = waterBodyFactor
  × season^1.5
  × timeOfDay^1.4
  × weather
  × bestBiomeGroup^1.3
  × distanceFactor
  × communityFactor
```

with hard gates on water type, depth, width, biome match and community presence — and then, uniformly across all species, the barometric factor and the spot's depletion.

## See also

- [Fishing mechanics](fishing-mechanics.md) · [Species](species.md)
- [Stocking](stocking.md) · [Tools](tools.md#fish-finder)
- [Sea fishing](sea-fishing.md) — how the ocean zones map onto biome groups
