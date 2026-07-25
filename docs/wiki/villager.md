# The Fisherman

River Fishing adds its own villager profession — **Fisherman** — with its own job-site block and five trade tiers. He sells tackle and buys your best fish.

> This is a **separate** profession from vanilla's Fisherman. Its internal id is `riverfishing:fisherman` (registry name `river_fisherman`) and it never takes a Barrel as its job site.

## The Fishing Stall

The Fisherman's job site is the **Fishing Stall** block.

```
Barrel + String + String + Iron Ingot          (shapeless)
```

Place one in range of an unemployed villager and he takes the job. The block is registered into `minecraft:acquirable_job_site`, so villager AI treats it exactly like a vanilla workstation.

The same block is also the [Tackle Station](tackle-station.md) — right-click it yourself and you get the tying bench.

## How his offers work

Vanilla draws exactly **two** offers per villager level out of that level's pool, for every profession. The Fisherman is topped up to **three** — a fishing shop needs counter space for bait, line, a rod *and* a fish buy. Pool size is the real currency here: every listing added dilutes every other one.

To keep gear visible, variants of the same thing are folded into **one rotating listing** — the trick vanilla itself uses for enchanted books. A pool entry means "a reel appears", not "the 12000 appears". Fish buys are collected per tier and sliced into **two disjoint rotating groups**, so a fisherman buys two *different* species per tier and never shows a duplicate.

| Tier | Pool size | Of which fish buys | Offers drawn |
|---|---|---|---|
| 1 Novice | 10 | 2 | 3 |
| 2 Apprentice | 10 | 2 | 3 |
| 3 Journeyman | 7 | 2 | 3 |
| 4 Expert | 14 | 2 | 3 |
| 5 Master | 12 | 2 | 3 |

**A level-1 Fisherman always buys a common fish.** The draw is random, so a fresh stall could have come up gear-only — and that first emerald for a bleak is the point of the profession. If none of the level-1 offers takes a fish, one is swapped in, rolled among Bleak, Roach, Gudgeon and Rotan: the four smalls that live in every water, so the guarantee is reachable wherever you spawned.

Across all five levels a maxed Fisherman shows 15 offers, of which roughly 3 are fish buys.

Ordinary sells allow 12 uses before restock; assembled rods and rigs allow 8. Every listing carries the usual 5 % price multiplier.

## What he sells

Entries marked **(rotating)** are one pool slot that picks one of the listed variants when the villager generates its trades.

### Tier 1 — Novice

| Offer | Price |
|---|---|
| 12 × Worm | 1 emerald |
| 8 × Bloodworm | 1 emerald |
| 2 × Float | 1 emerald |
| 6 × Powder Groundbait | 1 emerald |
| 3 × Corn / Pea / Barley Seeds **(rotating)** | 1 emerald |
| Mono Line 0.14 | 2 emeralds |
| Worm Farm | 4 emeralds |
| 4 × String | 1 emerald |

### Tier 2 — Apprentice

| Offer | Price |
|---|---|
| 10 × Maggot | 1 emerald |
| Reel 2000 / Reel 3000 **(rotating)** | 4 / 6 emeralds |
| Mono Line 0.18 | 2 emeralds |
| 6 × Grain Groundbait | 1 emerald |
| Bait Trap | 3 emeralds |
| Oak Boat | 4 emeralds |
| **Light Float Rig** — loaded with a Float and a Hook No.10 | 4 emeralds |
| **Bamboo Rod, assembled** — Mono 0.18 + Light Float Rig (float, No.10 hook) | 9 emeralds |

### Tier 3 — Journeyman

| Offer | Price |
|---|---|
| Spinner / Spoon Lure / 2 × Soft Plastic **(rotating)** | 3 / 4 / 2 emeralds |
| *(shop lures ship bench-stamped — see below)* | |
| Braided Line 0.16 / Fluorocarbon 0.20 **(rotating)** | 5 emeralds |
| 2 × Fluorocarbon Leader | 3 emeralds |
| Fish Finder | 14 emeralds |
| **Spinning Rod, assembled** — Reel 2000 + Braid 0.16 + Predator Rig (steel leader, spinner) | 16 emeralds |

### Tier 4 — Expert

| Offer | Price |
|---|---|
| Wobbler / Crankbait / Popper **(rotating)** | 7 / 7 / 6 emeralds |
| 3 × Live Bait | 2 emeralds |
| 4 × Prismarine Shard | 5 emeralds |
| 8 × Boilie | 3 emeralds |
| Reel 5000 / Reel 6000 **(rotating)** | 10 / 13 emeralds |
| Fluorocarbon 0.30 | 6 emeralds |
| Ice Auger | 9 emeralds |
| 2 × Ice Jig | 3 emeralds |
| Maggot Farm | 5 emeralds |
| 3 × Oil Cake Groundbait | 4 emeralds |
| **Feeder Rod, assembled** — Reel 5000 + Mono 0.25 + Feeder Rig (No.8 hook) | 18 emeralds |
| **Winter Rod, assembled** — Mono 0.14 + Winter Rig (mormyshka) | 14 emeralds |

### Tier 5 — Master

The prestige tier, and the only source of several items.

| Offer | Price |
|---|---|
| Digital Bite Alarm | 10 emeralds |
| Titanium Leader | 8 emeralds |
| Nautilus Shell | 10 emeralds |
| Reel 7000 / 8000 / 10000 / 12000 / 14000 **(rotating)** | 16 / 18 / 22 / 26 / 30 emeralds |
| Mono line 0.50 / 0.60 / 0.70 / 0.80 **(rotating)** | 8 / 10 / 12 / 14 emeralds |
| Braided Line 0.30 / Braid line 0.40 / 0.50 / 0.60 / Fluorocarbon 0.40 **(rotating)** | 10 / 14 / 16 / 18 / 12 emeralds |
| 3 × Hook #2 / 3 × Hook #1 **(rotating)** | 3 / 4 emeralds |
| **Carp Rod, assembled** — Reel 6000 + Braid 0.30 + Carp Rig (No.4 hook) | 30 emeralds |
| **Bottom Rod, assembled** — Reel 7000 + Braid 0.30 + Catfish Rig (steel leader, No.2 hook) | 28 emeralds |
| One **saltwater rod, assembled** **(rotating)**: | |
| • Sea spinning rod — Reel 8000 + Braid 0.40 + Predator Rig (steel leader, spinner) | 30 emeralds |
| • Surf rod — Reel 8000 + Mono 0.50 + Ledger Rig (No.2 hook) | 34 emeralds |
| • Boat rod — Reel 10000 + Braid 0.50 + Catfish Rig (steel leader, No.1 hook) | 34 emeralds |
| • Trolling rod — Reel 12000 + Braid 0.60 + Predator Rig (titanium leader, wobbler) | 40 emeralds |

**Hook #2 and Hook #1 have no crafting recipe** — the master fisherman is the only source.

His tackle is **bench-grade**. Anything the [Tackle Station](tackle-station.md) can tie leaves the stall carrying the same weight stamp the bench writes, so a bought lure pulls its real mass on the cast and a bought rig reads its real weight. Village stock carries no maker's mark, which is the only way to tell it from your own work.

He also stocks the plain vanilla items his own trade tree leans on: **string** (every reeled rod recipe needs it for the guide wraps), an **oak boat** (trolling needs one under you), and the two saltwater gate inputs — **prismarine shards** and a **nautilus shell**. The ocean tier stays expensive, but it no longer hinges on guardian luck.

Every assembled rod was checked against its blank's reel band and the [spool-diameter rule](reels-and-lines.md#the-spool-diameter-rule), so the stall can never sell a rod the assembly screen would refuse to socket. He sells **no bare blanks at all** — every rod that leaves the stall can be cast on the spot. Consumables (bait, groundbait) are left out on purpose; those are your call.

## The prime-fish rule

The Fisherman does not buy just any fish. He only buys **prime specimens**:

```
minimum accepted weight = ceil(species maximum weight × 0.7)
```

Any legal catch at or above 70 % of its species' ceiling is graded prime at the moment you land it, gets *"Prime specimen — the buyer wants this"* on its tooltip, and can be traded. The trade's cost slot shows the legend *"Accepts: from N"* so you know the bar before you go fishing.

Foul-hooked fish are never graded and can never be sold.

### What he pays

Prices below are the **base**; see [the market](#the-market) for how they actually move.

#### Tier 1

| Species | Accepts from | Base price | Trade XP |
|---|---|---|---|
| Bleak | 70 g | 1 emerald | 1 |
| Gudgeon | 105 g | 1 emerald | 1 |
| Roach | 700 g | 1 emerald | 1 |
| Bluegill | 560 g | 1 emerald | 1 |
| Round goby | 266 g | 1 emerald | 1 |
| Common dace | 700 g | 1 emerald | 1 |
| Rotan | 420 g | 1 emerald | 2 |
| Smelt | 175 g | 1 emerald | 3 |

#### Tier 2

| Species | Accepts from | Base price | Trade XP |
|---|---|---|---|
| Crucian Carp | 1.1 kg | 2 emeralds | 2 |
| White Bream | 0.8 kg | 2 emeralds | 2 |
| Perch | 1.4 kg | 2 emeralds | 2 |
| Ruffe | 105 g | 1 emerald | 2 |
| Rudd | 700 g | 2 emeralds | 2 |
| Sabrefish | 1.1 kg | 2 emeralds | 2 |
| White-eye bream | 910 g | 2 emeralds | 3 |
| Nase | 700 g | 2 emeralds | 4 |
| Vimba bream | 1.1 kg | 3 emeralds | 5 |

#### Tier 3

| Species | Accepts from | Base price | Trade XP |
|---|---|---|---|
| Bream | 2.8 kg | 3 emeralds | 4 |
| Asp | 5.6 kg | 6 emeralds | 9 |
| Ide | 2.1 kg | 3 emeralds | 5 |
| Chub | 2.8 kg | 3 emeralds | 5 |
| Tench | 2.5 kg | 4 emeralds | 5 |
| Blue bream | 560 g | 2 emeralds | 3 |
| Pike | 7 kg | 5 emeralds | 8 |
| Volga zander | 1.4 kg | 4 emeralds | 6 |
| Pink salmon | 2.5 kg | 4 emeralds | 8 |
| Whitefish | 2.8 kg | 4 emeralds | 8 |

#### Tier 4

| Species | Accepts from | Base price | Trade XP |
|---|---|---|---|
| Carp | 10.5 kg | 6 emeralds | 12 |
| Mirror Carp | 9.8 kg | 7 emeralds | 13 |
| Grass Carp | 17.5 kg | 9 emeralds | 14 |
| Zander | 4.2 kg | 6 emeralds | 10 |
| Trout | 3.5 kg | 6 emeralds | 12 |
| Largemouth bass | 5.6 kg | 7 emeralds | 12 |
| Rainbow trout | 4.2 kg | 7 emeralds | 12 |
| Grayling | 1.8 kg | 7 emeralds | 12 |
| Burbot | 5.6 kg | 5 emeralds | 10 |
| Mackerel | 1.4 kg | 3 emeralds | 6 |
| Herring | 420 g | 2 emeralds | 4 |
| Garfish | 1.1 kg | 3 emeralds | 6 |
| Flounder | 2.8 kg | 4 emeralds | 8 |
| Arctic char | 4.2 kg | 6 emeralds | 12 |
| Lenok | 4.2 kg | 6 emeralds | 12 |
| Atlantic salmon | 17.5 kg | 10 emeralds | 18 |

#### Tier 5

| Species | Accepts from | Base price | Trade XP |
|---|---|---|---|
| Catfish | 84 kg | 12 emeralds | 25 |
| Eel | 2.8 kg | 8 emeralds | 15 |
| Channel catfish | 12.6 kg | 10 emeralds | 20 |
| Sterlet | 11.2 kg | 16 emeralds | 30 |
| Silver carp | 17.5 kg | 14 emeralds | 26 |
| Sea bass | 5.6 kg | 7 emeralds | 14 |
| Cod | 28 kg | 9 emeralds | 18 |
| Saithe | 10.5 kg | 7 emeralds | 14 |
| Conger eel | 42 kg | 13 emeralds | 24 |
| Ray | 35 kg | 12 emeralds | 22 |
| Mahi-mahi | 14 kg | 10 emeralds | 20 |
| Wahoo | 28 kg | 14 emeralds | 26 |
| Yellowfin tuna | 105 kg | 20 emeralds | 34 |
| Barracuda | 14 kg | 8 emeralds | 16 |
| Blue marlin | 280 kg | 28 emeralds | 40 |
| Sailfish | 56 kg | 18 emeralds | 30 |
| Swordfish | 210 kg | 24 emeralds | 36 |
| Mako shark | 140 kg | 22 emeralds | 34 |
| Wild Carp | 12.6 kg | 14 emeralds | 28 |
| Taimen | 42 kg | 24 emeralds | 36 |
| Sturgeon | 105 kg | 26 emeralds | 38 |
| Halibut | 140 kg | 22 emeralds | 34 |

**The five Koi cannot be sold at all.** They are collectibles, not commerce. Every other species has a buy-trade somewhere in the five tiers. (Asp, White Bream and Mirror Carp were unsellable before 0.6.0 — an oversight, now on tiers 3, 2 and 4.)

## The market

Two forces move his prices, both saved with the world and shared across the whole server.

### Glut

Every **prime specimen landed** anywhere on the server saturates that species' market a little:

```
glut += 0.08 per prime landing        (capped at 1.0)
price = round(base × (1 − 0.5 × glut))    (never below 1 emerald)
```

So a heavily fished species pays down to **half price**. Glut recovers at **×0.85 per Minecraft day** — a couple of days off and it is nearly gone. Overfish the bream and the bream money dries up, which spreads anglers across species.

### The order of the day

One species per Minecraft day is the fisherman's **order**, paying **×2.5** regardless of glut. It rotates deterministically from the world's day count, so every player on the server sees the same order, and it is announced to each player once per day:

> *Fisherman's order of the day: Pike — pays ×2.5!*

The rotation pool is sixteen species, chosen to spread across the whole progression:

Bream · Perch · Pike · Roach · Crucian Carp · Carp · Zander · Trout · Bluegill · Largemouth bass · Sabrefish · Mackerel · Herring · Cod · Sea bass · Flounder

## See also

- [Tackle Station](tackle-station.md) — the other half of the Fishing Stall
- [Fishing mechanics](fishing-mechanics.md#prime-grade) — how prime grade is assigned
- [Species](species.md) · [Progression](progression.md)
