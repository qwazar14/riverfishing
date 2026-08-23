# Rigs, hooks and baits

The rig is the terminal tackle at the end of your line. It carries its own small inventory — hooks, bait, groundbait, float, leader, lure — and the bite engine reads your whole setup out of it.

## Rigs

Eleven rig types. Five are **built into** a rod blank and never exist as loose items (they are hidden from the creative tab); six are swappable bottom rigs you craft or tie.

| Rig | Item id | Mass | Slot layout (left to right) | Where it comes from |
|---|---|---|---|---|
| Primitive Rig | `rig_primitive` | 4 g | Hook · Bait | Built into the Stick Rod |
| Light Float Rig | `rig_float_light` | 6 g | Float · Hook · Bait | Built into the Bamboo Rod |
| Float Rig | `rig_float` | 7 g | Float · Hook · Hook · Bait · Bait | Built into the Pole Rod |
| Winter Rig | `rig_winter` | 3 g | Bait | Built into the Winter Rod |
| Predator Rig | `rig_predator` | 14 g | Leader · Lure | Built into Ultralight / Spinning / Sea spinning / Trolling |
| Ledger Rig | `rig_ground` | 28 g | Hook · Bait | Craft / bench |
| Feeder Rig | `rig_feeder` | 30 g | Hook · Bait · Groundbait | Craft / bench |
| Flat Feeder Rig | `rig_flat_feeder` | 40 g | Hook · Bait · Groundbait | Craft / bench |
| 3-Hook Feeder Rig | `rig_grusha` | 55 g | Hook ×3 · Bait ×3 · Groundbait | Craft / bench |
| Carp Rig | `rig_carp` | 65 g | Hook · Bait · Groundbait | Craft / bench |
| Catfish Rig | `rig_catfish` | 95 g | Leader · Hook · Bait | Craft / bench |

The **mass** column is the fallback weight used for casting. A rig tied at a [Tackle Station](tackle-station.md) carries its own chosen weight instead, and any tied lure in it adds its weight on top.

Open a loose rig with **sneak + right-click**. A rig socketed in a rod is edited from the rod's assembly screen.

### What each slot accepts

| Slot | Accepts |
|---|---|
| **Hook** | Any hook |
| **Bait** | Any natural bait — plus the Ice Jig, which is technically artificial but is the winter rig's only tackle |
| **Groundbait** | Base Groundbait, plain or mixed |
| **Float** | The Float item |
| **Leader** | Steel, Fluorocarbon or Titanium Leader |
| **Lure** | Any artificial lure — **or** Live Bait |

Loading **several** baits or hooks is worthwhile: the bite engine takes the *best-scoring* one for each fish it considers. So a Float Rig with a worm on one hook and a maggot on the other fishes for both audiences at once.

### The 3-Hook Feeder Rig

Its three hooks give a small chance of a **multiple catch** when you land a fish:

- 2 % — two fish
- 0.1 % — three fish

The extras are near-copies of the main fish (90–110 % of its weight).

### Rig recipes

| Rig | Recipe |
|---|---|
| Ledger Rig | String + 2 × Iron Nugget (shapeless) |
| Feeder Rig | 4 × Iron Nugget + 1 × String (shaped cage) |
| Flat Feeder Rig | 5 × Iron Nugget + 1 × String (shaped cage) |
| 3-Hook Feeder Rig | 3 × String + 2 × Iron Nugget (shapeless) |
| Carp Rig | 2 × String + 1 × Slime Ball + 1 × Iron Nugget (shapeless) |
| Catfish Rig | 2 × String + 1 × Iron Ingot + 1 × Iron Nugget (shapeless) |

Crafted rigs come **empty**. Rigs tied at the [Tackle Station](tackle-station.md) come with their hooks already slotted.

## Hooks

Angling numbering: **a bigger number means a smaller hook.**

| Hook | Item id | How to get |
|---|---|---|
| Hook No.16 | `hook_16` | 1 × Iron Nugget → **2** hooks |
| Hook No.14 | `hook_14` | No.16 + Iron Nugget |
| Hook No.12 | `hook_12` | No.14 + Iron Nugget |
| Hook No.10 | `hook_10` | No.12 + Iron Nugget |
| Hook No.8 | `hook_8` | No.10 + Iron Nugget |
| Hook No.6 | `hook_6` | No.8 + Iron Nugget |
| Hook No.4 | `hook_4` | No.6 + Iron Nugget |
| Hook #2 | `hook_2` | **Not craftable** — [fisherman](villager.md), master tier |
| Hook #1 | `hook_1` | **Not craftable** — [fisherman](villager.md), master tier |

All nine sizes are in the `riverfishing:hooks` tag, so any hook can serve as the hook ingredient in a lure recipe. (Before 0.6.0 the tag stopped at #4, which locked the two biggest hooks — the ones only the master fisherman sells — out of every lure craft.)

**Hook size is a hard gate.** Each species has an ideal hook size and a tolerance; the score falls off linearly and if it drops below **0.34** the fish will simply not take. In practice you must be within roughly ±2.6 × the species' tolerance of its ideal. Put a No.1 on a bleak swim and nothing bites at all.
**But hook size does not pick the fish's weight.** The hook decides which SPECIES can take; the individual's grams are then rolled inside that species' own range. A No.4 will not keep 20 g gobies off your worm — only a live baitfish ([its weight floors the catch](#live-bait-carries-a-weight)) or a heavy lure does that.


A Predator or Winter rig with no separate hook scores a flat **0.85** — the lure's treble and the mormyshka carry their own hooks.

### Blunt hooks

Hooks accumulate **bluntness** 0–100 %, shown on the tooltip. Each hook-set dulls your sharpest hook by 1 (default preset).

```
chance the strike fails = (bluntness / 100) × 0.5
```

At full bluntness half your strikes come back empty: *"Empty strike — the hook was blunt"*. Fix it with a **Whetstone**: hold the whetstone, put the hook in the other hand and right-click. Pull the hook out of the rig's screen to sharpen it, then put it back.

## The float

| Item | Recipe |
|---|---|
| Float (`float`) | Bamboo + Feather (shapeless) |

Whenever a float is loaded in the rig, the rod's assembly screen shows a vertical **depth slider** ("Depth") with three stops — *near surface* / *mid-water* / *near bottom*. The setting is stored on the rod.

```
bait presented at the species' preferred depth : bite weight ×1.3
wrong horizon                                  : bite weight ×0.55
```

Every species has a `depth_pref`. Across the 91 species: 33 are **bottom**, 28 **mid**, 9 **surface**.

## Natural baits

Twelve natural baits, plus the Ice Jig which lives in bait slots.

| Bait | Item id | Where it comes from |
|---|---|---|
| Worm | `worm` | Dig dirt, grass block, coarse dirt, podzol, rooted dirt, farmland, dirt path or mud **with a shovel** — 10 % per block. Also the [Worm Farm](blocks.md#worm-farm) and the fisherman. |
| Maggot | `maggot` | Zombie drops (33 %), the [Maggot Farm](blocks.md#maggot-farm), the fisherman. |
| Bloodworm | `bloodworm` | Drowned drops (33 %), the fisherman. |
| Chicken Liver | `chicken_liver` | Chicken drops (25 %). |
| Corn | `corn` | [Corn crop](blocks.md#bait-crops), 1–3 per mature plant. |
| Pea | `pea` | [Pea crop](blocks.md#bait-crops), 1–3 per mature plant. |
| Pearl Barley | `pearl_barley` | [Barley crop](blocks.md#bait-crops), 1–2 per mature plant. |
| Dough | `dough` | Wheat + Water Bucket (shapeless). |
| Bread Crumb | `bread` | 1 × Bread → **4** (shapeless). |
| Boilie | `boilie` | 2 × Wheat + Egg + Sugar → **4** (shapeless). |
| Raw Fish Fillet | `fish_strip` | Right-click a caught fish with the Filleting Knife — one per 200 g. The universal saltwater bait, a groundbait component, and food once cooked. |
| Live Bait | `livebait` | Any caught fish of **150 g or less**, put alone in the grid. Or hold the fish, a hook in the off hand, sneak and use. Or gather them in a [Bait Trap](blocks.md#bait-trap). |
| Ice Jig | `mormyshka` | Gold Nugget + any hook (No.16–No.4) + 2 × String. |

Natural bait is **eaten on the strike** — one piece per hooked fish. The bait the fish actually *preferred* is the one consumed, not simply the first slot. Lures and the Ice Jig are never consumed.

The **Frugal** skill gives a +5 % chance per rank (up to 25 %) that the bait survives the bite.

### Live Bait carries a weight

A live baitfish keeps the weight of the fish it was made from, shown on its tooltip as *"Baitfish: N — culls the small takers"*. During the catch roll it **floors the size** of the fish you get:

```
minimum fish weight ≈ 6 × baitfish weight
```

capped at 60 % of the species' weight range so the roll is still a roll, and only for species that actually rate live bait at 0.5 or better. A big baitfish therefore filters out the tiddlers and calls a genuinely large predator.

Live Bait goes into a **Bait** slot on a bottom rig, or into the **Lure** slot of a Predator rig.

## Artificial lures

Eight lures. All are **predator-only** — a peaceful fish will not take an artificial bait, and the `LURE` slot only exists on the Predator rig.

| Lure | Item id | Recipe | Notes |
|---|---|---|---|
| Spinner | `spinner` | Iron Nugget + Iron Ingot + hook (vertical) | Forgiving retrieve. |
| Spoon Lure | `spoon` | Gold Ingot + hook (vertical) | Forgiving retrieve. |
| Castmaster | `castmaster` | 2 × Iron Ingot + hook (shapeless) | Long-casting; forgiving retrieve. |
| Skirted Octopus Jig | `octopus_jig` | Tackle Station only | Trolling weight, 60–400 g. A skirted head for a trolled spread; the pelagics rate it highest. |
| Giant Spoon | `giant_spoon` | Tackle Station only | Trolling weight, 80–500 g. Big flash and a wide wobble; bluefish and jack crevalle rate it above everything. |
| Crankbait | `crankbait` | Iron Ingot + Iron Nugget + hook (shapeless) | **Needs a steady rhythm.** |
| Soft Jig | `jig` | Iron Ingot + String + hook (shapeless) | Forgiving retrieve. |
| Popper | `popper` | Iron Nugget + Bamboo + hook (shapeless) | **Topwater** — pop-and-pause. |
| Wobbler | `wobbler` | **No crafting recipe** — [Tackle Station](tackle-station.md) or the fisherman (expert tier) | **Needs a steady rhythm.** |
| Soft Plastic | `silicone` | **No crafting recipe** — [Tackle Station](tackle-station.md) or the fisherman (journeyman tier) | Forgiving retrieve. |

The `hook` ingredient is the `riverfishing:hooks` tag (No.16 through No.4).

### Working the lure

Each right-click is one crank. The **gap between clicks** is the lure's action:

| Lure family | Good cadence | If you get it wrong |
|---|---|---|
| Wobbler, Crankbait | a click every **8–18 ticks** (~0.4–0.9 s) | The swimming action dies — fish follow and turn away. |
| Spinner, Spoon, Soft Jig, Soft Plastic, Castmaster | anything from **5 to 30 ticks** | Hard to get wrong — the window is that wide. |
| Popper | Deliberate **pauses** — a pop 6–30 ticks after the last one | Constant cranking drags it under; sitting dead over 60 ticks lets it go stale. |

A good crank pulls the bite **10 ticks closer**; a sloppy one only 2. **Holding** right-click auto-repeats about every 4 ticks — line comes in, but no fish takes that "action". A lure left dead in the water for more than 30 ticks (80 for a popper) pushes the take back out.

### Dyeing lures

Put an artificial lure plus one or more **dyes** in the crafting grid — exactly like leather armour. The mixed colour is classified into three effective classes and matched against the light and clarity of the water:

| Class | Colours | Best in | Ideal light value |
|---|---|---|---|
| Natural | white / silver / pale blue (low saturation, bright) | Clear, bright water | 0.85 |
| Bright | chartreuse / orange / red (saturation > 50 %) | Murky water, low light, overcast | 0.40 |
| Dark | black / dark green (brightness < 35 %) | Night, deep or stained water | 0.12 |

```
bite weight ×= 0.75 + 0.60 × closeness      (so ×0.75 on a total miss up to ×1.35 on a match)
closeness = 1 − min(1, |conditionLight − idealLight| × 2)
```

The condition-light value blends time of day (day +0.28, dawn/dusk −0.08, night −0.32), weather (clear +0.10, rain −0.14, thunder −0.20), depth, swamp murk (−0.12), river flow (+0.04) and season, starting from 0.5.

An **undyed** lure skips the colour check entirely.

## Groundbait

One groundbait, and it is a base. Right-click water while holding it to feed a spot; mix your own by
putting the base in a crafting grid with up to 8 other things. The full system is on its own page: **[Groundbait](groundbait.md)**.

| Groundbait | Item id | Recipe |
|---|---|---|
| Base Groundbait | `groundbait_powder` | Wheat Seeds + Bread → **2**. The fisherman sells it, and his own house blend at Expert |

### The fed spot

A fed spot is a **3×3 column zone**:

- Each feed adds **0.6** freshness, up to a ceiling set by the mix — **0.48** for plain base, **1.00**
  for the base plus four rich things. Feeding more reaches the ceiling faster; it never raises it.
- Freshness **halves every 1800-5400 ticks** and the zone is spent after **3 to 12 minutes**, both
  depending on the fraction. Dust washes out; whole grain lies where it landed.
- The centre column counts at full strength; the outer ring at **60 %**.
- A coloured particle cloud marks the zone, in the mix's own colour.

**You cannot overfeed.** There is no fullness and no penalty for feeding — the ceiling above is the only
limit, and it is a property of the mix, not of how much of it went in.

**The same recipe adds up; a different one takes the swim over.** The old bed does not blend into the new
one.

What it does:

```
menu     = how much of the mix this fish actually eats   (its own bait list)
fraction = 1 - |mix fraction - the species' fraction|    -> 0.45 .. 1.00
nutrition= 1 - |mix nutrition - the species' nutrition|  -> 0.60 .. 1.00
variety  = 0.90 .. 1.00 by how many different components
groundbait score = menu x fraction x nutrition x variety, capped at 1.0
unfed spot       = 0.4

bite weight  x= clamp(1 + freshness, 1, 2)
time-to-bite x= 1 - 0.40 x freshness
size roll    flattens with a coarse mix — a better chance at a good fish, never a promise
```

So a freshly fed spot with a mix built for the fish roughly doubles its attractiveness *and* cuts up to
40 % off the wait.

A **feeder cage** (the Groundbait slot on a feeder / flat feeder / 3-hook / carp rig) empties one on
every cast **at the bobber**, through the same call hand-feeding makes. There is no separate "cage
freshness" any more: the cage and your hand build one fed spot between them.

## See also

- [Tackle Station](tackle-station.md) — tie rigs and lures to a chosen weight
- [Rods](rods.md) · [Reels and lines](reels-and-lines.md)
- [Species](species.md) — every fish's preferred baits
- [Fishing mechanics](fishing-mechanics.md)
