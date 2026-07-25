# Reels, lines and leaders

The reel decides how much drag you have and how thick a line you can spool; the line decides how much weight you can pull before it snaps and how badly it spooks fish.

## Reels

Eleven sizes, in the usual angling numbering: bigger number = bigger reel.

| Reel | Item id | Drag shown on tooltip | Spool window (mm) | Fits blanks |
|---|---|---|---|---|
| Reel 1000 | `reel_1000` | 1.0 kg | 0.06 – 0.20 | Ultralight |
| Reel 2000 | `reel_2000` | 2.0 kg | 0.11 – 0.25 | Ultralight, Spinning |
| Reel 3000 | `reel_3000` | 3.0 kg | 0.16 – 0.30 | Spinning, Feeder |
| Reel 4000 | `reel_4000` | 4.0 kg | 0.21 – 0.35 | Spinning, Feeder |
| Reel 5000 | `reel_5000` | 5.0 kg | 0.26 – 0.40 | Feeder, Bottom, Carp, Sea spinning |
| Reel 6000 | `reel_6000` | 6.0 kg | 0.31 – 0.45 | Bottom, Carp, Surf, Sea spinning |
| Reel 7000 | `reel_7000` | 7.0 kg | 0.36 – 0.50 | Bottom, Carp, Surf, Sea spinning |
| Reel 8000 | `reel_8000` | 9.5 kg | 0.41 – 0.55 | Surf, Sea spinning, Boat |
| Reel 10000 | `reel_10000` | 14.5 kg | 0.51 – 0.65 | Boat, Trolling |
| Reel 12000 | `reel_12000` | 19.5 kg | 0.61 – 0.75 | Boat, Trolling |
| Reel 14000 | `reel_14000` | 24.5 kg | 0.71 – 0.85 | Trolling |

There is **no 9000 reel item**, even though the sea spinning rod's band reaches 9000.

### Two drag numbers

Two different drag formulas exist in the mod and it is worth knowing which one you are looking at.

The **tooltip** shows a tiered figure — the freshwater ladder is linear, the saltwater sizes climb steeper:

```
size ≤ 7000 :  drag = size / 1000
size > 7000 :  drag = 7.0 + (size − 7000) / 1000 × 2.5
```

The **fight itself** uses the plain linear figure for every size:

```
fight drag = size / 1000        (so a 14000 contributes 14.0, not 24.5)
```

So on the biggest reels the tooltip is optimistic. What the fight drag actually does:

- Adds `0.5 × drag` on top of your line's breaking strain when working out the break tolerance.
- **Gives line faster** when you ease off: `relaxTick = 0.010 + clamp(drag/10, 0, 0.5) × 0.02`. A big reel bleeds tension roughly three times as fast as a 1000.
- **Reel feel**: small reels are twitchy, big ones are coarse but absorbing. `sensitivity = clamp(1 + (4000 − size)/4000 × 0.5, 0.6, 1.5)`. A 1000 sits at 1.375, a 4000 at 1.0, a 14000 at the 0.6 floor. A **reel-less** rod is the twitchiest of all at 1.3.
- Slightly speeds up landing: `landPulse` carries a `0.9 + size/14000` term.

### The spool-diameter rule

A spool only holds line up to a maximum diameter. This is a **hard** rule in the assembly screen — a too-thick line cannot be socketed.

```
maxLineDiameter(mm) = 0.15 + reelSize / 1000 × 0.05
```

1000 → 0.20 mm, then +0.05 mm per 1000: 7000 → 0.50 mm, 14000 → 0.85 mm.

The **minimum** figure on the tooltip is display-only — a thinner line still fits, it just under-fills the spool:

```
minLineDiameter(mm) = max(0.06, maxLineDiameter − 0.14)
```

The journal also shows the smallest reel that can take a given line, but that lookup only scans sizes 1000–7000, so it reports nothing for lines of 0.55 mm and up.

### Reel recipes

All shapeless. The freshwater ladder is a strictly growing pile of plain metal; the saltwater step needs ocean loot.

| Reel | Ingredients |
|---|---|
| 1000 | 2 × Iron Ingot + 1 × Redstone |
| 2000 | 2 × Iron Ingot + 1 × Copper Ingot + 1 × Redstone |
| 3000 | 3 × Iron Ingot + 1 × Copper Ingot + 1 × Redstone |
| 4000 | 3 × Iron Ingot + 1 × Copper Ingot + 2 × Redstone |
| 5000 | 4 × Iron Ingot + 1 × Copper Ingot + 2 × Redstone |
| 6000 | 4 × Iron Ingot + 2 × Copper Ingot + 2 × Redstone |
| 7000 | 5 × Iron Ingot + 2 × Copper Ingot + 2 × Redstone |
| 8000 | 1 × Iron Block + 2 × Copper Ingot + 2 × Redstone + 1 × Prismarine Shard |
| 10000 | 1 × Iron Block + 3 × Copper Ingot + 2 × Redstone + 2 × Prismarine Shard |
| 12000 | 1 × Iron Block + 1 × Copper Block + 2 × Redstone + 1 × Prismarine Crystals + 1 × Diamond |
| 14000 | 1 × Iron Block + 1 × Copper Block + 1 × Redstone Block + 1 × Nautilus Shell + 2 × Diamond |

No freshwater reel needs a diamond.

## Lines

Three materials. Breaking strain scales with the **square** of the diameter:

```
breaking strain (kg) = 100 × diameter² × materialFactor
```

| Material | Strength factor | Visibility factor | Character |
|---|---|---|---|
| mono (nylon) | 1.00 | 1.00 | The baseline all-rounder. |
| fluorocarbon | 1.10 | 0.45 | Refractive index near water — nearly invisible, a touch stronger. |
| braid (Dyneema) | 3.00 | 1.45 | Enormously strong for its diameter, but opaque and highly visible. |

Fluorocarbon also **wears 40 % slower** than the other two.

### Every line

| Diameter | Mono | Braid | Fluorocarbon |
|---|---|---|---|
| 0.10 mm | **1.0 kg** | — | — |
| 0.14 mm | **2.0 kg** | — | **2.2 kg** |
| 0.16 mm | — | **7.7 kg** | **2.8 kg** |
| 0.18 mm | **3.2 kg** | — | — |
| 0.20 mm | — | **12.0 kg** | **4.4 kg** |
| 0.25 mm | **6.3 kg** | **18.8 kg** | **6.9 kg** |
| 0.30 mm | **9.0 kg** | **27.0 kg** | **9.9 kg** |
| 0.40 mm | **16.0 kg** | **48.0 kg** | **17.6 kg** |
| 0.50 mm | **25.0 kg** | **75.0 kg** | — |
| 0.60 mm | **36.0 kg** | **108.0 kg** | — |
| 0.70 mm | **49.0 kg** | — | — |
| 0.80 mm | **64.0 kg** | — | — |

Thick fluorocarbon does not exist in the mod (impractical in reality). Braid stops at 0.60 mm, mono runs all the way to 0.80 mm.

Their in-game names are not perfectly consistent — the thin end of each ladder was added before the heavy tier:

| Item ids | Display name |
|---|---|
| `line_mono_010` … `line_mono_040` | Mono **L**ine 0.10 … 0.40 |
| `line_mono_050` … `line_mono_080` | Mono **l**ine 0.50 … 0.80 |
| `line_braid_016` … `line_braid_030` | Braided Line 0.16 … 0.30 |
| `line_braid_040` … `line_braid_060` | Braid line 0.40 … 0.60 |
| `line_fluoro_014` … `line_fluoro_040` | Fluorocarbon 0.14 … 0.40 |

### Line visibility

A thick, opaque line slows the bite — and small wary fish mind it far more than big ones do.

```
visibility = materialVisibility × (diameter / 0.20)
if visibility > 1:
    sensitivity = clamp(1.5 − meanWeightKg × 0.5, 0.1, 1.5)
    bite weight ×= max(0.4, 1 − 0.25 × (visibility − 1) × sensitivity)
```

0.20 mm mono is the reference point (visibility exactly 1, no penalty). 0.30 mm braid sits at 2.18 — a real handicap on a roach swim, barely noticed by a catfish. Fluorocarbon of any sensible diameter stays under 1 and is never penalised.

Separately, matching the species' **preferred material** is worth 1.0 in the tackle-match score vs. 0.6 for a mismatch, and the diameter is scored on a falloff around the species' ideal.

### Line wear

Line carries a wear value from 0 % to 100 %, shown on its tooltip, and it travels with the line stack.

```
breaking strain multiplier = 1 − 0.55 × (wear / 100)
```

At 100 % wear the line keeps only 45 % of its strain and the tooltip turns red: *"Line worn out — may snap at any moment!"*

Where wear comes from (on the default *realism* preset):

| Event | Line wear |
|---|---|
| Each cast | +0.1 (fractional; +0.06 on fluorocarbon) — accumulates probabilistically |
| Snagging free | +3 |
| A dead snag that costs the rig | +6 |
| Every ~15 ticks held over the tackle limit | +1 |
| A line break | +1 |
| A catastrophic tackle failure at the strike | +5 |
| An empty strike on a blunt hook | +1 |

There is **no way to repair a line** — a worn line gets replaced.

### Line recipes

The base of every chain is a **ring of 8 String** in the crafting grid.

| Result | Recipe |
|---|---|
| Mono Line 0.10 ×2 | Ring of 8 × String (hollow centre) |
| Braided Line 0.16 ×2 | Ring of 8 × String + Phantom Membrane in the centre |
| Fluorocarbon 0.14 ×2 | Ring of 8 × String + Amethyst Shard in the centre |
| Any thicker line ×1 | Ring of 8 × String + **the previous line of the same material** in the centre |

The upgrade chains, one step per craft:

- **Mono:** 0.10 → 0.14 → 0.18 → 0.25 → 0.30 → 0.40 → 0.50 → 0.60 → 0.70 → 0.80
- **Braid:** 0.16 → 0.20 → 0.25 → 0.30 → 0.40 → 0.50 → 0.60
- **Fluorocarbon:** 0.14 → 0.16 → 0.20 → 0.25 → 0.30 → 0.40

The [fisherman](villager.md) also sells 0.14 and 0.18 mono at low tiers, braid 0.16 / fluoro 0.20 at journeyman, fluoro 0.30 at expert, and the whole heavy tier at master.

## Leaders

A leader is a separate item that goes into the **leader slot** of a Predator or Catfish rig. Seven species are toothy enough to bite straight through a bare line — pike, zander, conger eel, wahoo, barracuda, mako shark and taimen.

| Leader | Item id | Bite-through protection | Stealth | Recipe |
|---|---|---|---|---|
| Steel Leader | `leader` | 100 % | 10 % | String + Iron Nugget |
| Fluorocarbon Leader | `leader_fluoro` | 85 % | 90 % | String + Prismarine Shard |
| Titanium Leader | `leader_titanium` | 100 % | 60 % | String + Iron Ingot |

**Protection** is checked once, the moment the fish is hooked, only for species that require a leader:

```
bite-off chance = 0.75 × (1 − protection)        (0.75 is the realism preset)
```

So no leader at all against a pike is a **75 % chance** the line is bitten through and you lose the fish *and* the rig; a fluorocarbon leader drops that to 11 %; steel and titanium make it impossible.

**Stealth** works on the bite chance for every species while a leader is fitted:

```
bite weight ×= 0.85 + stealth × 0.30
```

| Leader fitted | Bite multiplier |
|---|---|
| Steel | ×0.88 |
| Titanium | ×1.03 |
| Fluorocarbon | ×1.12 |

Leaderless bottom and float fishing is never penalised by this — the multiplier only applies when a leader is actually on the rig. Separately, a species that *requires* a leader takes a flat **×0.15** bite penalty if you fish it without one.

## See also

- [Rods](rods.md) · [Rigs and baits](rigs-and-baits.md)
- [Fishing mechanics](fishing-mechanics.md) — how strain, drag and tension interact in the fight
- [Crafting](crafting.md)
