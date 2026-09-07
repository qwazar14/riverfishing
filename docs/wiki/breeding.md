# Breeding

New in **0.9.0**. The aquarium stopped being a display case and became a tank. Put a mature pair in it during their spawning window, keep them fed and the water clean, and in three days there is roe in the result slot; take the parents out and you watch it hatch on the tank floor. What comes out carries [genes](genetics.md) from both parents, so a bloodline is a thing you can build.

## The tank

The [Aquarium](blocks.md#aquarium) is unchanged as a block — 2 wide × 2 tall × 1 deep, four cells that place and break together. Right-click **any** cell and it opens the master's screen. The old right-click-with-a-fish verbs are gone; everything goes through the slots now.

### The slots

Twelve, and each one is fussy about what it takes.

| Slot | What goes in it |
|---|---|
| **Fish** ×6 | A caught fish **with a [catch card](fishing-mechanics.md)**. A netted fish has a card too, so it counts; a fish landed before 0.9.0 does not |
| **Food** | Fish meal, any *natural* bait, or fish oil |
| **Groundbait** | A jar of [groundbait](groundbait.md) |
| **Water** | A water bucket — it is consumed and an empty bucket is left in the slot |
| **Result** | The roe, then the fry. Output only, unless the fish slots are empty |
| **Modules** ×2 | Any of the five [water upgrade blocks](blocks.md#water-upgrades) |

### What it needs to spawn

The screen tells you which of these is missing, in this order — the first failure is the one it shows.

1. **A pair.** The largest ♀ in the tank, plus any ♂ she will spawn with. Sex is on the card.
2. **Both mature** — size class *adult* or better, which is **at least half the species' mean weight**. Babies and juveniles keep growing; they do not spawn.
3. **The spawning window.** Every one of the 107 species has one. A **warm outflow** module widens it to the neighbouring third of the same season.
4. **Fed.** The food clock must not have run out.
5. **Water at 50 % or better.**
6. **The result slot empty.**

Then **three world days** of all of it holding at once. Break any of them and the clock resets to zero — this is not a progress bar you can nurse.

Fish **oil** in the food slot takes a day off the run, and so does a **gravel bed** module; with both, a spawn takes one day. Sleeping counts, because the clock reads the world's day, not ticks of the block.

## Feeding

One unit is consumed the moment the previous one runs out, and the food slot is checked before the groundbait slot.

| Food | Days |
|---|---|
| Natural bait | **1** |
| A jar of groundbait | **2** |
| Fish meal | **3** |
| Fish oil | *not food* — it waits for a spawn to hurry |

A **feeding station** in a module slot **doubles all of those** — six days on one lump of fish meal — and needs no groundbait charges the way the block in the world does.

Either **fish meal** as the last food *or* a groundbait jar standing in its slot swells the clutch by **×1.25**. It is one condition, not two: both together is still ×1.25.

## The water

A fresh tank starts at 100 %. It fouls by the day and by the fish:

```
per day = −(8 + 2 × fish in the tank)
with an aerator module:  +5 − (8 + 2 × fish) / 2
```

So an empty tank loses 8 % a day; three fish, 14 %; six fish, 20 %. An aerator turns that into −2 %, −5 % — and an *empty* tank with an aerator actually **gains** a point a day.

A water bucket takes it straight back to 100 % and leaves you the empty bucket. Below **50 %** nothing spawns; below **25 %** the roe stops developing until you fix it.

## The clutch

How many eggs is decided by the mother, then modified by the tank:

```
r     = clamp(mother's weight / species mean, 0, 2)
eggs  = (10 + 15 × r) × fertility        (never fewer than 4)
        × 1.25   with fish meal or a groundbait jar
        × 0.75   leather carp × leather carp
        × the cross strength if the parents are different species
```

`fertility` is **×0.6** for `ff`, **×1.0** for `Ff`, **×1.5** for `FF`. An ordinary `Ff` hen therefore lays 25; a double-mean `FF` hen 60. The screen shows the number it will actually produce, so the preview cannot lie to you.

## Roe

The roe appears in the result slot as one item carrying the species, the crossed genotype, the egg count and — for a carp or a koi — the [pattern index](genetics.md#the-pattern-index).

**Take the parents out** and it starts incubating; leave them in and they will eat it, so the clock simply waits.

| Condition | Days |
|---|---|
| A climate the species likes | **4** |
| A climate it does not | **8** |
| A **warm outflow** module | **4**, always |

Only the *climate* is read — cold, temperate or warm — because a tank stands in a house and the terrain around it is nobody's business.

You watch it happen on the gravel: loose pale beads, then eye spots in each, then the milky dead ones showing among them, then tails breaking out of the shells, then fry along the glass.

A fisherman **buys roe**, and pays for it by family: **three times the fish's own price for a sturgeon or a salmonid**, and half the fish's price for everything else, never less than one emerald. Sturgeon roe is 78 emeralds; a beluga's is 90. Right-click him holding the clutch.

## Fry

How many of the eggs become fry is the **vigour** locus of the roe itself:

| Genotype | Survive |
|---|---|
| `vv` | 50 % |
| `Vv` | 70 % |
| `VV` | 90 % |

A **snag pile** module adds **+0.15**, capped at 95 %. A clutch never yields fewer than one fry.

Fry are an item. Two things to do with them:

- **Throw them into water** (the deliberate drop, not an inventory spill) and they stock it. Out in the wild a further cut applies — **70 % survive bare water**, up to 100 % with a snag pile and an aerator within reach. Thirty surviving fry [settle a species](stocking.md).
- **Lift them out again** with the **fry trap** — the bait trap, given a second job. It takes up to ten at a time of whichever species has most in that water, and only from a brood released into the same claim it stands in.

The fisherman **sells fry** of the day's order species: **ten for 8 emeralds**. And one post in two on the third line of his [contract](contracts.md) board is a fry order — **20, 30 or 40** fry of a species, paid like a contract.

## The five modules

Two slots, one block each. The same five blocks work [in the world](blocks.md#water-upgrades) around a pond; in the tank they mean something narrower.

| Module | In the tank |
|---|---|
| **Aerator** | Halves the water's daily decay and adds 5 % a day back |
| **Snag pile** | Fry survival **+0.15** (capped at 95 %) |
| **Gravel bed** | The spawn run is **one day shorter** |
| **Warm outflow** | Incubation is always **4 days**; the spawning window widens to the neighbouring third of the season |
| **Feeding station** | Every food lasts **twice** as long, and needs no charges |

## Which species cross with which

A pair does not have to be one species. Eight pools are written, and each cross carries a **strength** that scales the clutch — you read it off the egg count.

| Pool | Strength |
|---|---|
| Carp · Sazan · Koi carp | 1.0 |
| Crucian carp · Golden crucian | 1.0 |
| Beluga · Sterlet · Sturgeon | 0.8 |
| Bream · White bream | 0.9 |
| The four breams, every other pair | 0.2 |
| Salmon · Trout · Arctic char | 0.5 |
| Zander · Volga zander | 0.35 |
| Roach · Rudd | 0.35 |
| Whitefish · Nelma | 0.3 |

The fry always take the **mother's** species and a genome from both parents, so a cross moves *blood* between species rather than making a third one. A sazan hen and a carp cock give sazan fry carrying the domestic scale alleles; the other way round gives carp with wild blood in them. Full table and the reasoning behind each number on [Genetics](genetics.md#which-species-cross-with-which).

The **silver crucian is the exception**: her eggs need the milt and none of his genes, so her clutch is a copy of her whoever the father was.

## The pond grows itself

A species [settled](stocking.md) in a water with at least one pair on its ledger grows on its own, **once every 24-day season** — up to four seasons paid out in one visit if you have been away.

```
head count  += pairs × (1 + 0.5 × the water's F share) × (1 + fry survival)
average kg  += 6 % of the species mean × (1 + 0.5 × the water's S share)
                × 1.25 with a feeding station in reach
```

The weight ceiling is **90 % of the species maximum**, and once the head count passes **eight fish per pair** the weight step halves — a crowded pond grows numbers, not size. Separately, **twelve days** after a bucket of fry goes in, half of them mature into adults, split evenly between the sexes.

You are told when it happens, within 64 blocks of the sign:

> *Carp grew in your pond: 14 fish, 3.8 kg on average · stock 180 %*

## See also

- [Genetics](genetics.md) — the loci, the carp's scales, the seventeen koi, the pattern index
- [Stocking](stocking.md) — the brood ledger, settling, and what a bloodline does to the water
- [Blocks](blocks.md#water-upgrades) — the five upgrade blocks as blocks
- [Contracts](contracts.md) — fry orders · [Villager](villager.md) — roe and fry at the counter
