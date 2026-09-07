# Crafting reference

Every recipe the mod adds. Shaped recipes show their grid pattern as `row / row / row`, with `·` for an empty cell.

Items marked **trade only** or **quest only** have no recipe at all — see [Villager](villager.md) and [Progression](progression.md).

---

## Rods

From 0.10.0 only the four simplest blanks are crafted; every other rod is **bought assembled from the [fisherman](villager.md)** (the journal's "how to get" line says so). All shaped; a reeled blank needs string for its guide wraps.

| Rod | Pattern | Key |
|---|---|---|
| Stick Rod | `··S / ·S· / S··` | S = Stick |
| Pole Rod | `··b / ·b· / is·` | b = Bamboo, i = Iron Ingot, s = String |
| Ultralight Rod | `··b / sb· / i··` | b = Bamboo, s = String, i = Iron Ingot |
| Fly Rod | `··b / sb· / bn·` | b = Bamboo, s = String, n = Iron Nugget |

**Trade only:** Bamboo, Winter, Spinning, Feeder, Bottom, Carp, Sea spinning, Surf, Boat and Trolling rods. Anvil repair materials are listed in [Rods](rods.md).

---

## Reels

Two are crafted, shapeless; the other nine (2000, 3000, 5000, 6000, 7000, 8000, 10000, 12000, 14000) are **trade only** — the [fisherman](villager.md) sells them, and two arrive as [quest](progression.md#the-quest-chain) rewards.

| Reel | Ingredients |
|---|---|
| Reel 1000 | 2 × Iron Ingot + Redstone |
| Reel 4000 | 3 × Iron Ingot + Copper Ingot + 2 × Redstone |

There is no Reel 9000 item.

---

## Lines

All shaped around a **ring of 8 String** (`SSS / S?S / SSS`).

| Result | Centre of the ring |
|---|---|
| Mono Line 0.10 **×2** | *(empty)* |
| Fluorocarbon 0.14 **×2** | Amethyst Shard |
| The next diameter up **×1** | The previous line of the same material |

Braided Line 0.16 has no recipe any more — the [fisherman](villager.md) sells it. Upgrade chains, one craft per step; a diameter in **bold** is not crafted, you buy it and the chain carries on from there:

| Material | Chain |
|---|---|
| Mono | 0.10 → 0.14 → 0.18 → 0.25 → 0.30 → 0.40 · **0.50, 0.60, 0.70, 0.80** trade only |
| Braid | **0.16** → 0.20 → 0.25 · **0.30, 0.40, 0.50, 0.60** trade only |
| Fluorocarbon | 0.14 → 0.16 · **0.20** → 0.25 → 0.30 · **0.40** trade only |

### Line repair

Any line in the **centre** of the grid with **four String** on its four sides (`·S· / SLS / ·S·`) gives the same line back **fresh** — the [wear](reels-and-lines.md) is gone. Every diameter of every material, bought or crafted.

---

## Hooks

All shapeless. A bigger number is a smaller hook.

| Result | Ingredients |
|---|---|
| Hook No.16 **×2** | Iron Nugget |
| Hook No.14 | Hook No.16 + Iron Nugget |
| Hook No.12 | Hook No.14 + Iron Nugget |
| Hook No.10 | Hook No.12 + Iron Nugget |
| Hook No.8 | Hook No.10 + Iron Nugget |
| Hook No.6 | Hook No.8 + Iron Nugget |
| Hook No.4 | Hook No.6 + Iron Nugget |
| Hook #2 | **trade only** (master fisherman) |
| Hook #1 | **trade only** (master fisherman) |

All nine hook sizes make up the `riverfishing:hooks` tag used by the lure recipes. Note that #2 and #1 have no recipe of their own — the master fisherman sells them, and the [Tackle Station](tackle-station.md) ties any size on for you, billed in iron.

---

## Rigs

**No rig has a crafting recipe.** The six swappable bottom rigs — Ledger, Feeder, Flat Feeder, 3-Hook Feeder, Carp and Catfish — are tied at the **[Tackle Station](tackle-station.md)** to a weight you choose, which is what has to fit your blank's [test window](rods.md#loading-the-blank-the-test-window); they arrive with their hooks already slotted. (Until 0.10.0 a hand-crafted rig came out at a fallback mass you had no say in, so the grid recipes taught nothing the bench does not teach better.)

The Primitive, Light Float, Float, Winter and Predator rigs are built into their rod blanks and cannot be made or held.

---

## Leaders and float

All shapeless.

| Item | Ingredients |
|---|---|
| Steel Leader | String + Iron Nugget |
| Fluorocarbon Leader | **trade only** — the [fisherman](villager.md) |
| Titanium Leader | **trade only** — the fisherman, or a [quest](progression.md#the-quest-chain) reward |
| Float | Bamboo + Feather |

---

## Lures

**No lure has a grid recipe** — spinner, spoon, the wobbler family, jig, castmaster, spinnerbait, bladebait, swimbait, wacky worm, popper, crankbait and the ice jig alike. You **tie them at the [Tackle Station](tackle-station.md)** — pick the grams, get a real size, and a hook of your choosing — or buy them from the [fisherman](villager.md). (A grid-crafted lure carried no weight stamp, which the game read as 0 g: nothing added to the cast and nothing for the [lure-size filter](tackle-station.md#2-the-lure-size-filter-lures-only) to read. There was no reason to keep it.) The two heaviest classes (sea sizes) exist only at the bench. The one lure recipe left is the dye:

### Dyeing a lure

A **special recipe**: any one artificial lure plus one or more **dyes** in the grid, anywhere, gives the lure back with a mixed colour — exactly like leather armour. See [lure colour](rigs-and-baits.md#dyeing-lures).

---

## Baits

| Bait | Type | Ingredients |
|---|---|---|
| Dough | shapeless | Wheat + Water Bucket |
| Bread Crumb **×4** | shapeless | 1 × Bread |
| Boilie **×4** | shapeless | 2 × Wheat + Egg + Sugar |
| Live Bait | **special** | One caught fish weighing **150 g or less**, alone in the grid — the bait keeps the fish's weight |

Worm, Maggot, Bloodworm and Chicken Liver come from digging and mob drops; Corn, Pea and Pearl Barley come from the [bait crops](blocks.md#bait-crops). See [Tools](tools.md#where-bait-comes-from).

---

## Groundbait

| Groundbait | Type | Ingredients |
|---|---|---|
| Base Groundbait **×2** | shapeless | Wheat Seeds + Bread — *the base; everything else is [mixed](groundbait.md) into it* |
| Groundbait Soil **×4** | shapeless | Dirt — *ballast, the way you make a mix leaner* |
| Any mix | shapeless | Base Groundbait + up to 8 other things in a grid — one slot is one item, and one edible item in is one groundbait out |

---

## Blocks

| Block | Type | Ingredients |
|---|---|---|
| Fishing Stall | shapeless | Barrel + 2 × String + Iron Ingot |
| Rod Pod (1 slot) | shaped `s·s / ·s· / ·s·` | s = Stick |
| Rod Pod (3 slots) | shaped `n·n / iii / i·i` | n = Iron Nugget, i = Iron Ingot |
| Bait Trap | shaped `s·s / shs / s·s` | s = Stick, h = String |
| Worm Farm | shaped `PPP / PDP / PPP` | P = any Planks, D = Dirt |
| Maggot Farm | shaped `PPP / PFP / PPP` | P = any Planks, F = Rotten Flesh |
| Aquarium | shaped `GWG / RKR / PSP` | G = Glass, W = Water Bucket, R = Gravel, K = Kelp, P = any Planks, S = any Sign |
| Mini Aquarium | — | **no recipe** |

---

## Tools

| Item | Type | Ingredients |
|---|---|---|
| Fishing Journal | shapeless | Book + Hook No.12 + Leather |
| Fish Finder | shaped `iri / iqi / iii` | i = Iron Ingot, r = Redstone, q = Quartz |
| Filleting Knife | shaped `·I / S·` | I = Iron Ingot, S = Stick |
| Whetstone | shapeless | 2 × Smooth Stone + Stick |
| Ice Auger | shaped `··I / ·I· / SI·` | I = Iron Ingot, S = Stick |
| Bite Alarm (Bell) | shaped `·g· / gng` | g = Gold Ingot, n = Iron Nugget |
| Digital Bite Alarm | shaped `·g· / ibi / rfl` | g = Glass Pane, i = Iron Ingot, b = Bite Alarm (Bell), r = Redstone, f = Flint, l = Lapis Lazuli |
| Ichthyologist's Tablet | — | **no recipe** (operator tool) |

---

## Cooking

| Result | Method | Input | Time | XP |
|---|---|---|---|---|
| Cooked Fish Fillet | Smoker | Raw Fish Fillet | 100 ticks | 0.2 |
| Cooked Fish Fillet | Furnace | Raw Fish Fillet | 200 ticks | 0.2 |
| Cooked Fish Fillet | Campfire | Raw Fish Fillet | 600 ticks | 0.2 |

Raw fillets come from the [Filleting Knife](tools.md#filleting-knife) — one per 200 g of fish, at most a stack from any one specimen. The same item is the universal saltwater bait and a groundbait component.

---

## Nothing has a recipe for…

A short list of everything in the mod you cannot craft:

| Item | Where it comes from |
|---|---|
| Hook #2, Hook #1 | Master fisherman |
| Every lure (spinner, spoon, wobblers, jig, castmaster, spinnerbait, bladebait, swimbait, wacky worm, popper, crankbait, ice jig, soft plastic) | Tackle Station, or the fisherman |
| Skirted Octopus Jig, Giant Spoon | Tackle Station, or the fisherman's saltwater kit |
| All six bottom rigs | Tackle Station |
| Bamboo, Winter, Spinning, Feeder, Bottom, Carp, Sea spinning, Surf, Boat and Trolling rods | The fisherman (Spinning, Carp and Surf are also quest rewards) |
| Reels 2000–14000 except the 4000 | The fisherman (3000, 5000, 7000 and 14000 are also quest rewards) |
| Braided Line 0.16; mono 0.50–0.80, braid 0.30–0.60, fluorocarbon 0.20 and 0.40 | The fisherman |
| Fluorocarbon Leader, Titanium Leader | The fisherman (Titanium is also a quest reward) |
| Mini Aquarium | Creative / commands only |
| Ichthyologist's Tablet | Creative / commands only |
| Electrofisher | Creative / commands only |
| Primitive / Light Float / Float / Winter / Predator rigs | Built into their rod blanks |
| Every fish | Catch it |

## See also

- [Rods](rods.md) · [Reels and lines](reels-and-lines.md) · [Rigs and baits](rigs-and-baits.md)
- [Tackle Station](tackle-station.md) · [Villager](villager.md) · [Blocks](blocks.md) · [Tools](tools.md)
