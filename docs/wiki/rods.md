# Rods

Every rod is a bare **blank**; the reel, line and rig live inside it. The blank you pick decides how far you can cast, which reels fit, how heavy a rig it will throw, and which of the three fishing flows you play.

## The thirteen blanks

| Rod | Item id | Flow | Reel seat | Cast-weight window (test) | Max cast reach | Built-in rig | Durability | Anvil repair |
|---|---|---|---|---|---|---|---|---|
| Stick Rod | `stick_rod` | Float | none | 2–25 g | 6 blocks | Primitive Rig | 32 | Stick |
| Bamboo Rod | `bamboo_rod` | Float | none | 2–30 g | 6 blocks | Light Float Rig | 64 | Bamboo |
| Pole Rod | `pole_rod` | Float | none | 2–30 g | 6 blocks | Float Rig | 128 | Iron Ingot |
| Winter Rod | `winter_rod` | Float (ice only) | none | 1–12 g | ice hole only | Winter Rig | 96 | Iron Ingot |
| Ultralight Rod | `ultralight_rod` | Active | 1000–2000 | 1–10 g | 18 blocks | Predator Rig | 144 | Iron Ingot |
| Spinning Rod | `spinning_rod` | Active | 2000–4000 | 3–35 g | 16 blocks | Predator Rig | 192 | Iron Ingot |
| Feeder Rod | `feeder_rod` | Bottom | 3000–5000 | 20–90 g | 32 blocks | — (swappable) | 224 | Gold Ingot |
| Bottom Rod | `bottom_rod` | Bottom | 5000–7000 | 40–160 g | 32 blocks | — (swappable) | 256 | Gold Ingot |
| Carp Rod | `carp_rod` | Bottom | 5000–7000 | 60–220 g | 32 blocks | — (swappable) | 320 | Diamond |
| Surf rod | `surf_rod` | Bottom | 6000–8000 | 80–250 g | 32 blocks | — (swappable) | 384 | Diamond |
| Sea spinning rod | `sea_spin_rod` | Active | 5000–9000 | 20–120 g | 32 blocks | Predator Rig | 320 | Diamond |
| Boat rod | `boat_rod` | Bottom | 8000–12000 | 100–400 g | 18 blocks | — (swappable) | 448 | Diamond |
| Trolling rod | `trolling_rod` | Active | 10000–14000 | 150–600 g | 18 blocks | Predator Rig | 512 | Diamond |

Notes on the columns:

- **Reel seat** — the reel-size band the blank accepts. A reel outside the band cannot be socketed at all. Reel-less blanks have no reel slot; their line ties straight to the tip. There is no 9000 reel item, so the sea spinning rod's practical top reel is the 8000.
- **Cast-weight window ("test")** — the rigged weight the blank is built to throw, shown on the rod tooltip as `Test: N–M g`. See [Loading the blank](#loading-the-blank-the-test-window) below.
- **Max cast reach** — the farthest the rig can land at full power bar, *before* the cast-weight penalty. Reel-less blanks are hard-capped at 6 blocks (a fixed length of line on a tip); a longer throw is refused with *"A pole cannot reach past 6 blocks"*.
- **Built-in rig** — float and lure blanks carry one permanent rig; the assembly screen shows that rig's own slots inline and there is no swappable rig column. Bottom blanks keep the swappable rig column. A freshly crafted or trade-bought rod installs its built-in rig automatically the first time you open it or cast it.
- **Durability** — each hook-set (strike) costs 1 point. At zero the blank breaks for good. Repair on an **anvil** with the listed material. The ladder rises with the tier, and the saltwater blanks sit above the freshwater top: the trolling rod takes 512 strikes to the carp rod's 320.

## The three flows

The blank's flow is fixed and decides the whole cast-to-fight loop.

| Flow | Blanks | Loop |
|---|---|---|
| **Float** | Stick, Bamboo, Pole, Winter | Cast → watch the float → strike → fight. No reel, so no retrieve. |
| **Active** | Ultralight, Spinning, Sea spinning, Trolling | Cast → **work the lure with right-click cranks** → strike → hook-set timing → fight. |
| **Bottom** | Feeder, Bottom, Carp, Surf, Boat | Long cast → long forgiving wait → strike → fight. Can be parked on a [rod pod](blocks.md#rod-pods). |

Only **Bottom**-flow rods can be docked on a rod pod.

Reel-less float blanks (Stick, Bamboo, Pole) do **not** run the strike-timing bar — they save their single timing challenge for the **pull-out** at the end of the fight instead. See [the pull-out](fishing-mechanics.md#the-pull-out-reel-less-poles).

## Loading the blank: the test window

The rig's weight in grams is compared against the blank's window. There is a hidden **±15 % tolerance** on the printed numbers.

**Under-loaded** (below the window):

```
range factor = 0.85 × √(weight / windowMin)      (floored at 0.30 of full reach)
```

An under-loaded blank cannot load properly and cannot throw far. Below 85 % of the window minimum you also get the warning *"The rig is too light for this blank — short throw"* and a **silent 25 % longer wait for a bite**. The cast power bar draws the unreachable far end as a red dead band.

**In the window:**

```
range factor = 0.85 + 0.15 × (weight − windowMin) / (windowMax − windowMin)
```

So anything inside the window flies well — 85 % of full reach at the bottom of the window rising to 100 % at the top.

**Over-loaded** (above the window × 1.15):

```
overload penalty = 1 − (ratio − 1) × 0.5      (clamped to 0.4 … 1.0)
```

The warning *"Rig too heavy for this blank — risk of breakage"* appears and the penalty multiplies straight into your line's break tolerance during the fight — an overloaded blank snaps line much sooner.

**Wildly over-loaded** (more than **2.5×** the window maximum × 1.15) — the cast is refused, the blank **cracks** and loses a third of its durability (+1): *"The blank cracked under the weight — lost a third of its durability!"*

The weight compared here is the rig's **bench weight** if it was tied at a [Tackle Station](tackle-station.md), otherwise the rig type's fixed mass, **plus** the bench weight of any tied lure sitting in it.

## Rod-specific behaviour

- **Spinning Rod** — deliberately shorter reach (16 blocks) than the other long-range blanks, and its retrieve is about twice as long as the ultralight's (`castDistance × 20`, capped at 340 ticks vs. `× 10`, capped at 220). It gets a bite bonus that grows with fish size (`×min(1.2, 0.85 + meanKg × 0.15)`).
- **Ultralight Rod** — the finesse niche: a bite bonus that *fades* as fish get bigger (`×clamp(1.6 − meanKg × 0.6, 0.4, 1.6)`), crossing over with the spinning rod around 1 kg. Its fights are the hardest of the lure rods (see [ultralight fights](fishing-mechanics.md#predator-fights)).
- **Winter Rod** — fishes **only** through a drilled ice hole. Casting into open water is refused: *"The winter rod only fishes a hole — right-click drilled ice"*. See [Ice fishing](ice-fishing.md).
- **Trolling rod** and **Sea spinning rod** — the only two blanks that can [troll](sea-fishing.md#trolling) behind a moving boat.
- **Long-range blanks** (Spinning, Feeder, Bottom, Carp, Surf, Sea spinning) — on water narrower than 12 blocks you get *"Water too narrow for a long cast"* and every fish's environment score is cut to **×0.4**. Boat and Trolling rods are *not* flagged long-range, so narrow water does not penalise them.
- **Active-flow rods burn hunger** — one whole food point every 4 casts.

## Recipes

All rod recipes keep a diagonal, rod-like silhouette. Every reeled blank needs string for its guide wraps.

| Rod | Ingredients |
|---|---|
| Stick Rod | 3 × Stick (diagonal) |
| Bamboo Rod | 3 × Bamboo (diagonal) |
| Winter Rod | 2 × Stick + 1 × Iron Nugget |
| Pole Rod | 2 × Bamboo + 1 × Iron Ingot + 1 × String |
| Ultralight Rod | 2 × Bamboo + 1 × Iron Ingot + 1 × String |
| Spinning Rod | 1 × Bamboo + 2 × Iron Ingot + 1 × String |
| Feeder Rod | 3 × Iron Ingot + 1 × Gold Ingot + 1 × String |
| Bottom Rod | 3 × Iron Ingot + 2 × Gold Ingot + 1 × String |
| Carp Rod | 2 × Iron Ingot + 1 × Gold Ingot + 1 × Diamond + 1 × String |
| Sea spinning rod | 2 × Iron Ingot + 1 × Diamond + 1 × Prismarine Shard + 1 × String |
| Surf rod | 2 × Iron Ingot + 1 × Diamond + 2 × Prismarine Shard + 1 × String |
| Boat rod | 2 × Iron Ingot + 1 × Diamond + 1 × Prismarine Crystals + 1 × String |
| Trolling rod | 2 × Iron Ingot + 1 × Diamond + 1 × Nautilus Shell + 1 × String |

The saltwater four differ only in their **tip**: shard → two shards → crystals → nautilus shell. Freshwater blanks never need a diamond except the carp rod, so the first diamond you spend on tackle is normally the step into the sea. See [Crafting](crafting.md) for exact grid layouts.

The village [fisherman](villager.md) also sells rods — but only as **fully assembled, ready-to-cast** setups with reel, line and a loaded rig fitted.

## Assembling a rod

Hold the rod and **sneak + right-click** to open the assembly screen.

Order matters on reeled blanks: **the reel goes in first**, because the line spools onto the reel. The screen refuses impossible combinations and tells you why:

| Message | Meaning |
|---|---|
| *This blank has no reel seat* | You tried to fit a reel to a reel-less blank. |
| *Wrong reel size for this blank* | The reel is outside the blank's band. |
| *Line too thick for this reel* | See the [spool rule](reels-and-lines.md#the-spool-diameter-rule). |
| *Reel too small for the fitted line* | Swapping down to a reel that can't hold the line already on the rod. |
| *Fit a reel first — line spools onto the reel* | Line before reel on a reeled blank. |

A rod can fish as soon as it has a **line and a rig**. An empty hook slot doesn't block the cast — it just kills your bite chance. The tooltip names the first missing part.

With a line already out, sneak + right-click is **fight input** (the open drag), not the GUI — reel in first if you want to re-rig.

## See also

- [Reels and lines](reels-and-lines.md)
- [Rigs and baits](rigs-and-baits.md)
- [Fishing mechanics](fishing-mechanics.md)
- [Sea fishing](sea-fishing.md) · [Ice fishing](ice-fishing.md)
