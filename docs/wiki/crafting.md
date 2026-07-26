# Crafting reference

Every recipe the mod adds. Shaped recipes show their grid pattern as `row / row / row`, with `·` for an empty cell.

Items marked **trade only** or **quest only** have no recipe at all — see [Villager](villager.md) and [Progression](progression.md).

---

## Rods

All shaped. Every reeled blank needs string for its guide wraps.

| Rod | Pattern | Key |
|---|---|---|
| Stick Rod | `··S / ·S· / S··` | S = Stick |
| Bamboo Rod | `··b / ·b· / b··` | b = Bamboo |
| Winter Rod | `··N / ·S· / S··` | N = Iron Nugget, S = Stick |
| Pole Rod | `··b / ·b· / is·` | b = Bamboo, i = Iron Ingot, s = String |
| Ultralight Rod | `··b / sb· / i··` | b = Bamboo, s = String, i = Iron Ingot |
| Spinning Rod | `··b / si· / i··` | b = Bamboo, s = String, i = Iron Ingot |
| Feeder Rod | `··g / si· / ii·` | g = Gold Ingot, s = String, i = Iron Ingot |
| Bottom Rod | `·gg / si· / ii·` | g = Gold Ingot, s = String, i = Iron Ingot |
| Carp Rod | `··d / si· / ig·` | d = Diamond, g = Gold Ingot, s = String, i = Iron Ingot |
| Sea spinning rod | `··p / si· / id·` | p = Prismarine Shard, d = Diamond, s = String, i = Iron Ingot |
| Surf rod | `·pp / si· / id·` | p = Prismarine Shard, d = Diamond, s = String, i = Iron Ingot |
| Boat rod | `··c / si· / id·` | c = Prismarine Crystals, d = Diamond, s = String, i = Iron Ingot |
| Trolling rod | `··n / si· / id·` | n = Nautilus Shell, d = Diamond, s = String, i = Iron Ingot |

The saltwater four differ only in the **tip**. Anvil repair materials are listed in [Rods](rods.md).

---

## Reels

All shapeless.

| Reel | Ingredients |
|---|---|
| Reel 1000 | 2 × Iron Ingot + Redstone |
| Reel 2000 | 2 × Iron Ingot + Copper Ingot + Redstone |
| Reel 3000 | 3 × Iron Ingot + Copper Ingot + Redstone |
| Reel 4000 | 3 × Iron Ingot + Copper Ingot + 2 × Redstone |
| Reel 5000 | 4 × Iron Ingot + Copper Ingot + 2 × Redstone |
| Reel 6000 | 4 × Iron Ingot + 2 × Copper Ingot + 2 × Redstone |
| Reel 7000 | 5 × Iron Ingot + 2 × Copper Ingot + 2 × Redstone |
| Reel 8000 | Iron Block + 2 × Copper Ingot + 2 × Redstone + Prismarine Shard |
| Reel 10000 | Iron Block + 3 × Copper Ingot + 2 × Redstone + 2 × Prismarine Shard |
| Reel 12000 | Iron Block + Copper Block + 2 × Redstone + Prismarine Crystals + Diamond |
| Reel 14000 | Iron Block + Copper Block + Redstone Block + Nautilus Shell + 2 × Diamond |

There is no Reel 9000 item.

---

## Lines

All shaped around a **ring of 8 String** (`SSS / S?S / SSS`).

| Result | Centre of the ring |
|---|---|
| Mono Line 0.10 **×2** | *(empty)* |
| Braided Line 0.16 **×2** | Phantom Membrane |
| Fluorocarbon 0.14 **×2** | Amethyst Shard |
| The next diameter up **×1** | The previous line of the same material |

Upgrade chains, one craft per step:

| Material | Chain |
|---|---|
| Mono | 0.10 → 0.14 → 0.18 → 0.25 → 0.30 → 0.40 → 0.50 → 0.60 → 0.70 → 0.80 |
| Braid | 0.16 → 0.20 → 0.25 → 0.30 → 0.40 → 0.50 → 0.60 |
| Fluorocarbon | 0.14 → 0.16 → 0.20 → 0.25 → 0.30 → 0.40 |

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

All nine hook sizes make up the `riverfishing:hooks` tag used by the lure recipes. Note that #2 and #1 have no recipe of their own — the master fisherman is their only source.

---

## Rigs

> **These are the basic recipes.** A hand-tied rig comes out at its rig type's fallback mass — you get
> no say in it. For a rig at a **weight you choose**, tie it at the **[Tackle Station](tackle-station.md)**
> instead. Strongly recommended: the weight is what has to fit your blank's
> [test window](rods.md#loading-the-blank-the-test-window), and bench rigs arrive with their hooks
> already slotted.

Only the six swappable bottom rigs are craftable. The Primitive, Light Float, Float, Winter and Predator rigs are built into their rod blanks and cannot be made or held.

| Rig | Type | Ingredients |
|---|---|---|
| Ledger Rig | shapeless | String + 2 × Iron Nugget |
| Feeder Rig | shaped `n·n / nsn` | n = Iron Nugget, s = String |
| Flat Feeder Rig | shaped `nnn / nsn` | n = Iron Nugget, s = String |
| 3-Hook Feeder Rig | shapeless | 3 × String + 2 × Iron Nugget |
| Carp Rig | shapeless | 2 × String + Slime Ball + Iron Nugget |
| Catfish Rig | shapeless | 2 × String + Iron Ingot + Iron Nugget |

All six can also be tied at the [Tackle Station](tackle-station.md) to a chosen weight, and come with their hooks pre-slotted.

---

## Leaders and float

All shapeless.

| Item | Ingredients |
|---|---|
| Steel Leader | String + Iron Nugget |
| Fluorocarbon Leader | String + Prismarine Shard |
| Titanium Leader | String + Iron Ingot |
| Float | Bamboo + Feather |

---

## Lures

> **These are the basic recipes, and they cost you the lure's mass.** A hand-crafted lure carries **no
> weight stamp**, which the game reads as **0 g** — it adds nothing to your cast and does not drive the
> [lure-size filter](tackle-station.md#2-the-lure-size-filter-lures-only), so it never picks its fish.
> Tie lures at the **[Tackle Station](tackle-station.md)** instead — pick the grams, get a real size.
> The two heaviest classes (sea sizes) exist only there.

The `h` ingredient is the `riverfishing:hooks` tag (No.16–No.4).

| Lure | Type | Ingredients |
|---|---|---|
| Spinner | shaped `n / i / h` | n = Iron Nugget, i = Iron Ingot, h = hook |
| Spoon Lure | shaped `g / h` | g = Gold Ingot, h = hook |
| Castmaster | shapeless | 2 × Iron Ingot + hook |
| Crankbait | shapeless | Iron Ingot + Iron Nugget + hook |
| Soft Jig | shapeless | Iron Ingot + String + hook |
| Popper | shapeless | Iron Nugget + Bamboo + hook |
| Wobbler | — | **[Tackle Station](tackle-station.md)** or the fisherman (expert tier) |
| Soft Plastic | — | **[Tackle Station](tackle-station.md)** or the fisherman (journeyman tier) |

### Dyeing a lure

A **special recipe**: any one artificial lure plus one or more **dyes** in the grid, anywhere, gives the lure back with a mixed colour — exactly like leather armour. See [lure colour](rigs-and-baits.md#dyeing-lures).

---

## Baits

| Bait | Type | Ingredients |
|---|---|---|
| Dough | shapeless | Wheat + Water Bucket |
| Bread Crumb **×4** | shapeless | 1 × Bread |
| Boilie **×4** | shapeless | 2 × Wheat + Egg + Sugar |
| Fish strip **×4** | shapeless | Any fish (`riverfishing:fishes`) |
| Ice Jig | shapeless | Gold Nugget + any hook No.16–No.4 + 2 × String |
| Live Bait | **special** | One caught fish weighing **150 g or less**, alone in the grid — the bait keeps the fish's weight |

Worm, Maggot, Bloodworm and Chicken Liver come from digging and mob drops; Corn, Pea and Pearl Barley come from the [bait crops](blocks.md#bait-crops). See [Tools](tools.md#where-bait-comes-from).

---

## Groundbait

| Groundbait | Type | Ingredients |
|---|---|---|
| Powder Groundbait **×2** | shapeless | Bread + Wheat |
| Grain Groundbait **×2** | shapeless | Wheat + Wheat Seeds |
| Oil Cake Groundbait **×6** | **special** | Sunflower + **Piston** — the piston is the press and is returned |
| Pellet Groundbait | — | **quest only** (*Catch a tench*, stage 2) |

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

Raw fillets come from the [Filleting Knife](tools.md#filleting-knife) (one per 300 g of fish) or, with **Farmer's Delight**, from a [cutting board](tools.md#farmers-delight-cutting-board).

---

## Nothing has a recipe for…

A short list of everything in the mod you cannot craft:

| Item | Where it comes from |
|---|---|
| Hook #2, Hook #1 | Master fisherman |
| Wobbler, Soft Plastic | Tackle Station, or the fisherman |
| Pellet Groundbait | The *Catch a tench* quest reward |
| Mini Aquarium | Creative / commands only |
| Ichthyologist's Tablet | Creative / commands only |
| Primitive / Light Float / Float / Winter / Predator rigs | Built into their rod blanks |
| Every fish | Catch it |

## See also

- [Rods](rods.md) · [Reels and lines](reels-and-lines.md) · [Rigs and baits](rigs-and-baits.md)
- [Tackle Station](tackle-station.md) · [Villager](villager.md) · [Blocks](blocks.md) · [Tools](tools.md)
