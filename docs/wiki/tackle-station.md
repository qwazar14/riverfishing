# The Tackle Station

Right-click a **Fishing Stall** with an empty hand and it becomes a tackle bench: pick a form, pick a weight, feed it hooks, iron and string, and take the finished tackle out. The block that gives the village its [fisherman](villager.md) is the same block you tie your own rigs and lures on.

The bench and the profession share one block. Its recipe is **Barrel + 2 × String + Iron Ingot** (shapeless).

## How it works

The flow is stonecutter-style:

1. Choose a **tab** — *Peaceful* (bottom rigs) or *Predator* (artificial lures).
2. Click a **form** in the 3×3 grid.
3. Step the **weight** with the `< Weight: N g >` control. The weight is the real decision.
4. Fill the four material wells at the bottom: **Hook**, **Iron**, **String**, **Dye**. Ghost icons and a live `×N` count under each well show what the current selection needs, in red when you are short.
5. The result slot previews the tackle. **Taking it** consumes the materials.

Materials **stay in the bench** when you close it, so you can walk up and tie another one. They drop if the block is broken.

## Costs

```
iron    = max(1, round(grams / 30))          + 1 for the two feeder cages
string  = 1 for lures, 2 for rigs, 3 for the 3-hook rig
hooks   = 1, or 3 for the 3-hook rig
dye     = optional, lures only, 1 dye
```

Rigs come out of the bench with their hooks **already slotted** — ready to bait. Every piece the bench ties carries the maker's name in its tooltip (*"Tied by …"*), and a **Tackle weight: N g** line that the casting system reads.

## Peaceful forms

| Form | Weight steps (g) | Iron per step | String | Hooks | Default hook link |
|---|---|---|---|---|---|
| 3-Hook Feeder Rig | 30 / 50 / 80 | 1 / 2 / 3 | 3 | 3 | 15 cm |
| Feeder Rig | 40 / 60 / 80 | 2 / 3 / 4 | 2 | 1 | 60 cm |
| Flat Feeder Rig | 40 / 60 / 80 | 2 / 3 / 4 | 2 | 1 | 10 cm |
| Ledger Rig | 30 / 60 / 100 | 1 / 2 / 3 | 2 | 1 | 40 cm |
| Carp Rig | 60 / 90 / 120 / 160 | 2 / 3 / 4 / 5 | 2 | 1 | 20 cm |
| Catfish Rig | 80 / 150 / 250 | 3 / 5 / 8 | 2 | 1 | 50 cm |

The float, predator and winter rigs are **not** on the list — they live permanently inside their rod blanks and are never tied separately.

## Predator forms

All eight lures take an optional dye.

| Form | Weight steps (g) | Iron per step |
|---|---|---|
| Spinner | 3 / 7 / 14 | 1 / 1 / 1 |
| Spoon Lure | 10 / 20 / 35 / 60 / 180 | 1 / 1 / 1 / 2 / 6 |
| Wobbler | 6 / 12 / 20 / 40 / 160 | 1 / 1 / 1 / 1 / 5 |
| Soft Plastic | 5 / 10 / 20 / 40 | 1 / 1 / 1 / 1 |
| Popper | 7 / 12 / 30 | 1 / 1 / 1 |
| Crankbait | 8 / 14 / 22 / 40 | 1 / 1 / 1 / 1 |
| Soft Jig | 10 / 20 / 40 / 80 / 200 | 1 / 1 / 1 / 3 / 7 |
| Castmaster | 14 / 28 / 45 / 80 / 160 | 1 / 1 / 2 / 3 / 5 |

The heavy steps at the top of the spoon, wobbler, jig and castmaster ladders are the **sea sizes** — they exist so the sea spinning (20–120 g), boat (100–400 g) and trolling (150–600 g) rods finally have tackle inside their test windows.

The Wobbler and Soft Plastic have **no crafting recipe at all**, so the bench (or the fisherman) is the only way to make them.

## Why weight matters

The grams you choose are read by three separate systems.

### 1. The rod's test window

The tackle's weight must sit inside the blank's [cast-weight window](rods.md#loading-the-blank-the-test-window). Too light and the blank cannot load — a short cast and a silent 25 % longer wait. Too heavy and your line's break tolerance drops; 2.5× over and the blank cracks on the cast.

A rig's weight for this purpose is its bench weight **plus** the bench weight of any tied lure sitting in one of its slots. Untied (crafted or traded, unstamped) tackle contributes its rig-type fallback mass, and an untied lure contributes **0 g**.

### 2. The lure-size filter (lures only)

A lure's mass *is* its size, and size decides which fish will commit to it:

```
optimum fish weight (kg) = 0.5 × √(lure grams)
ratio  = max(0.05, meanWeightKg / optimum)
bite weight ×= max(0.05, 2 / (ratio + 1/ratio))
```

| Lure weight | Optimum fish |
|---|---|
| 5 g | ~1.1 kg |
| 14 g | ~1.9 kg |
| 20 g | ~2.2 kg |
| 40 g | ~3.2 kg |
| 80 g | ~4.5 kg |
| 160 g | ~6.3 kg |
| 200 g | ~7.1 kg |

Off-size fish don't vanish, they just bite far less — the floor is 5 %. So a heavy pilker genuinely silences the tiddlers, and because the total tackle weight also drives the wait, big lures mean **slower, rarer, bigger** takes.

The same weight also **floors the individual fish's size roll** at roughly `8 × lure grams` (capped at 60 % of the species' range). A 200 g jig will not produce a 700 g zander.

### 3. Cast distance

Heavier flies farther, within the blank's limits. The bench shows a `cast ~N blocks` hint computed as `round(4 × √grams)` — 20 g reads ~18 blocks, 160 g reads ~51. **This is a feel indicator only.** Your actual reach is set by the rod (6 to 32 blocks) scaled by how well the weight fits its window; nothing casts 51 blocks.

## The "advanced" drawer

Click the **► advanced** row to open it. What it holds depends on the form:

- **Rigs** — a draggable **hook link** slider, 5 to 100 cm. Each rig style resets to its own sensible default when you select it (flat feeder 10 cm, 3-hook 15 cm, carp 20 cm, ledger 40 cm, catfish 50 cm, feeder 60 cm).
- **Lures** — three **balance** buttons: *nose* / *center* / *tail*. Default is center.
- **Spinner and Spoon** only — a **blade size** is derived automatically from the mass (`min(5, 1 + grams/15)`) and written on the lure. Every spinner comes out at blade 1; spoons run 1 / 2 / 3 / 5 / 5 across their five weights.

> **These three knobs currently have no gameplay effect.** Hook link, balance and blade size are written into the tackle's NBT and displayed on its tooltip, and nothing else in the mod reads them. They are groundwork for a later pass — treat them as cosmetic for now. The **weight** and the **dye** are the two choices at this bench that actually change how fish behave.

## Dyeing at the bench

Drop a dye in the fourth well and the lure comes out coloured — the same result as the [lure-dye crafting recipe](rigs-and-baits.md#dyeing-lures), and the colour does affect the bite. The dye well is ignored for rigs.

## See also

- [Rigs and baits](rigs-and-baits.md) · [Rods](rods.md)
- [Villager](villager.md) — the same block is the fisherman's job site
- [Fishing mechanics](fishing-mechanics.md)
