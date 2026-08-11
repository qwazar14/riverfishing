# Tools and processing

The items that aren't tackle: how you read the water, keep your gear sharp, and turn a catch into dinner.

| Item | Item id | Recipe |
|---|---|---|
| Fishing Journal | `fishing_journal` | Book + Hook No.12 + Leather |
| Fish Finder | `fish_finder` | 7 × Iron Ingot + Redstone + Quartz |
| Ichthyologist's Tablet | `hydro_probe` | **No recipe** — operator tool |
| Filleting Knife | `fillet_knife` | Iron Ingot + Stick |
| Whetstone | `whetstone` | 2 × Smooth Stone + Stick |
| Ice Auger | `ice_auger` | 3 × Iron Ingot + Stick |
| Bite Alarm (Bell) | `bell_alarm` | 3 × Gold Ingot + Iron Nugget |
| Digital Bite Alarm | `digital_alarm` | Glass Pane + 2 × Iron Ingot + Bell Alarm + Redstone + Flint + Lapis Lazuli |

---

## Fishing Journal

Right-click to read. Six tabs — **Fish**, **Baits & Lures**, **Gear**, **Quests**, **Skills** and **Guides** — covered in [Progression](progression.md#the-fishing-journal).

The Fish tab is a live bestiary: every species you have caught, with its count, your personal best, and a "how to catch" page listing its water bodies, depth, width, biomes, best season and time, baits and tackle — all read from the **same profile the bite engine uses**, so the advice can never drift from the balance.

The Guides shelf holds twelve written pages: the drag, tackle stress, live bait, *every water is its own*, working the lure, topwater, trolling, sea giants, legendary fish, the market and the daily order, fishing together, and the tackle bench.

Your records survive death.

---

## Fish Finder

Right-click while aiming at water — or at the bank or bottom beside it; the tool traces 24 blocks along your view and finds the water either way. It always tells you something, and puts a half-second cooldown on itself.

What it reports:

```
Fish finder — biting here right now:
Bream, Roach, Perch, Pike, Rudd, Gudgeon, Bleak, Tench
Known for: Tench
Stock: Bream 180%, Mako shark 45% (temp)
Pressure: 1004 hPa ↓  —  bite: frenzy
```

| Line | Meaning |
|---|---|
| The species list | Up to 8 species that can bite here **right now**, ordered by environment score. If nothing can: *"The finder is silent — nothing here now"* |
| *Known for:* | This water's [signature species](water-and-conditions.md#every-water-is-its-own) — they bite ×1.8 |
| *Stock:* | Every species whose [stock level](stocking.md) is more than 10 % off normal. **(temp)** marks an unsettled transplant |
| *Pressure:* | The [barometer](water-and-conditions.md#barometric-pressure): reading, trend arrow, and a colour-coded outlook (frenzy / active / fair / slow / dead) |

The Fish Finder is sold by a journeyman [fisherman](villager.md) for 14 emeralds, and is the reward-shaped item you want before committing to a long bottom session.

---

## Ichthyologist's Tablet

The operator version. Uncraftable — creative or `/give` only.

It prints the full diagnostic dump to chat:

- Water body type, width, depth and the biome group set
- Season, time, weather and whether a frenzy is running
- Pressure in hPa, its trend and the exact bite factor
- **Per species**: its environment score `E`, its level gate, its stock or TEMP percentage, and its favourite bait
- A **diagnosis** grouping every *absent* species by the first gate that blocks it — `blocked[water]`, `blocked[depth(3)]`, `blocked[width]`, `blocked[biome]`, `blocked[season]`, `blocked[time]`, `blocked[weather]`

Right-clicking a **Fishing Stall** with the tablet instead reports that block's point-of-interest record — a villager job-site diagnostic that tells you whether the stall is properly registered, and to break and re-place it if not.

---

## Filleting Knife

Hold the knife, put a **caught fish in your other hand**, and right-click.

```
fillets = max(1, fishWeightGrams / 200)
```

The fish is consumed, the knife loses 1 durability (of 128), and you get that many **Raw Fish Fillets** — stackable food, unlike the unique catch.

The knife is in the `forge:tools/knives` tag, so it works as a knife for other mods that look for one.

Filleting a **koi** works, announces your name in server chat with *"you seriously filleted it?"*, and earns the *Heartless Cook* advancement.

### Fillets

| Item | Nutrition | Saturation |
|---|---|---|
| Raw Fish Fillet | 2 | 0.2 |
| Cooked Fish Fillet | 5 | 0.6 |

Cook raw fillets three ways:

| Method | Time | XP |
|---|---|---|
| Smoker | 100 ticks | 0.2 |
| Furnace | 200 ticks | 0.2 |
| Campfire | 600 ticks | 0.2 |

One fish is at most **one stack** of fillets, however big it was: a legendary catfish is 64, not 500.

---

## Whetstone

Hold the whetstone, put a **hook in your other hand**, right-click. The hook's [bluntness](rigs-and-baits.md#blunt-hooks) resets to 0 % and the whetstone loses 1 of its 128 durability.

Hooks live inside rigs, so the workflow is: open the rig, pull the hook out, sharpen it, put it back.

---

## Ice Auger

Right-click **Ice, Packed Ice, Blue Ice or Frosted Ice** that sits directly on water to drill a [Drilled Ice Hole](blocks.md#drilled-ice-hole).

- 64 durability, 1 per hole, with a 15-tick cooldown between drills.
- Over anything but water: *"No water under the ice"*, and the ice is left alone.

A pickaxe just breaks the ice — only the auger makes a hole you can fish. Sold by an expert fisherman for 9 emeralds.

---

## Bite alarms

Alarms mount on a [Rod Pod](blocks.md#bite-alarms), not on a rod. Full behaviour is on the [Blocks](blocks.md#bite-alarms) page.

---

## Live Bait, by hand

Two ways to turn a small catch into bait, both limited to fish of **150 g or less**:

1. **Crafting** — put the fish alone in the grid.
2. **By hand** — hold the fish in your main hand, a **hook** in your off hand, then **sneak + right-click**. This consumes both and gives one Live Bait.

Either way the bait keeps the fish's weight, which [culls the small takers](rigs-and-baits.md#live-bait-carries-a-weight).

You can also gather Live Bait passively from a [Bait Trap](blocks.md#bait-trap).

---

## Where bait comes from

| Bait | Source |
|---|---|
| Worm | Dig dirt / grass block / coarse dirt / podzol / rooted dirt / farmland / dirt path / mud **with a shovel** — 10 % |
| Maggot | Zombie drops — 33 % |
| Bloodworm | Drowned drops — 33 % |
| Chicken Liver | Chicken drops — 25 % |
| Corn Seeds / Pea Seeds / Barley Seeds | Grass and tall grass (the plants) — 5 % each |

All of these are loot-table injections, so they behave identically on both loaders. Bait can also be farmed ([Worm Farm](blocks.md#worm-farm), [Maggot Farm](blocks.md#maggot-farm), [bait crops](blocks.md#bait-crops)) or bought from the fisherman.

---

## Pack integration

| Mod | What it adds |
|---|---|
| **Serene Seasons** *(optional)* | Unlocks the whole [seasonal layer](water-and-conditions.md#seasons) of the bite engine, plus spring's faster spot recovery. Without it every season factor is 1.0. |
| **Biomes O' Plenty** *(optional)* | Richer biomes for the habitat model. BoP biomes carry the vanilla tags, and the mod's `is_swamp` tag names BoP's Bayou, Marsh, Wetland, Bog and Mangrove explicitly. |
| **Farmer's Delight** *(optional)* | The Filleting Knife is in the `forge:tools/knives` tag, so Farmer's Delight and anything else looking for a knife will take it. |
| **Jade** *(optional)* | Look-at counts for the farms, trap, pod and aquarium. |
| **JEI** *(optional)* | A "Fishing" category showing each species' baits, tackle, best conditions, water and recommended level. |

## See also

- [Rigs and baits](rigs-and-baits.md) · [Blocks](blocks.md)
- [Keepnet](keepnet.md) · [Tackle box](tackle-box.md) — where the catch and the tackle go
- [Progression](progression.md) · [Water and conditions](water-and-conditions.md)
- [Electrofisher](electrofisher.md) — the creative-only tool that is not tackle: it decides what lives in a water
- [Crafting](crafting.md)
