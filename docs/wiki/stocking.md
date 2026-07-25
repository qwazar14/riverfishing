# Stocking

Every water holds its own species set, fixed by the world seed. If a species you want isn't there, you can put it there — by **releasing fish**. This is the mod's answer to "my pond is boring", and it is designed to be the backbone of a server economy.

## How to release a fish

**Throw a caught fish into water.** The item floats, shrinks away over 40 ticks (2 seconds) with bubbles and a splash, and is gone — released. Pull it back out onto land before the two seconds are up and the release is cancelled.

It works for **every** species, not just koi. The fish's own weight is what matters.

Releasing does two separate things, and it is important to keep them apart:

1. It always **banks stock** — the fish physically swims here now.
2. If the species does not already live here, it **rolls to settle** — to become a permanent resident.

## Regions and chunks

Two different scales are in play:

- **Residency** (does this species live here permanently?) is stored per **~128-block region** — the same regions the seed's species communities use. Settle a species and it is a resident of that whole region forever.
- **Stock level** (how many are there right now?) is stored per **chunk**, and read across the **3×3 chunk neighbourhood** — fish don't respect chunk borders.

## Habitat fit

Before anything else the game measures how well the water actually suits the species. It runs the same environment score the [bite engine](fishing-mechanics.md#environment-score-e--the-world) uses — water body, depth, width, biome groups, season — but **without** the community factor (settling is exactly the act of joining a community you're not in yet) and with time flattened to day and weather to clear. Viability is about the *water*, not the hour.

```
fit = 0        →  hostile water
fit > 0        →  livable
```

## The three residency tiers

| Standing | When | Stock ceiling |
|---|---|---|
| **Native** | The seed's community places the species here (or its `base` is 0.95+, the ubiquitous commons) | **250 %** |
| **Settled transplant** | It has passed a settle roll in this region | **150 %** |
| **Unsettled transplant** | Released but not settled | **0–100 %**, temporary |

A native species has no upper limit trouble: it fattens up to two and a half times its normal density. A settled transplant caps at 150 %. An unsettled one has **no 100 % baseline at all** — its population grows from zero, is catchable while it lasts, and disperses if the species never takes hold.

A **hostile-water** release (`fit = 0`) banks nothing unless it settles on that very throw.

## Banking stock

```
sizeRatio = weight / species mean weight
units     = 0.5 × clamp(sizeRatio, 0, 3) ^ 1.5
```

The exponent is **superlinear in size**, which is the whole point: sport catch-and-release of *prime* fish is what feeds a water, not bucketfuls of fry.

| What you release | Units | Fish needed to fill a native water to 250 % |
|---|---|---|
| A fish at the species median | 0.50 | ~17 |
| A double-median trophy | 1.41 | ~6 |
| A triple-median monster (the cap) | 2.60 | ~4 |
| Fry at a tenth of the median | 0.016 | ~530 |

Each unit removes 0.18 from the chunk's per-species pressure, and pressure below zero *is* the surplus.

Feedback in the action bar:

> *Released: Bream — local stock 180 %*

## Settling

If the species is **not** already resident, every release also rolls for permanence:

```
chance = 0.18 × (0.03 + min(1.2, fit)²) × clamp(sizeRatio, 0.1, 2.0)
```

rolled once per fish in the stack. The `fit²` term is what makes water quality decisive; the flat 0.03 is a sliver that is never removed, so even completely wrong water keeps a chance.

| Water quality | Median fish | Double-median trophy |
|---|---|---|
| Ideal (fit ≈ 1.0) | ~19 % | ~37 % |
| Mediocre (fit ≈ 0.5) | ~5 % | ~10 % |
| Barely livable (fit ≈ 0.1) | ~0.7 % | ~1.4 % |
| Hostile (fit = 0) | ~0.5 % | ~1.1 % |

Note that size here uses the **raw ratio**, not the superlinear units — settling is about the specimen being an adult, not about tonnage.

Feedback:

| Outcome | Message |
|---|---|
| Settled | *"Bream has settled in this water — it lives here now!"* |
| Didn't settle, livable water | *"Bream didn't settle (the odds were 19 %) — temporary population 45 %"* |
| Didn't settle, hostile water | *"Mako shark can barely survive here — didn't settle (the odds were 0.5 %)"* |

**A failed settle is not a wasted fish.** While the surplus lasts, that species is temporarily catchable at this spot, at a strength proportional to its temporary population. It only truly disperses if the species never takes hold.

## Living outside your element

A species that has **settled** in water that fails its natural gates does not vanish — it lives at **a quarter strength**:

```
environment score = max(naturalScore, 0.25 × stockedPresence)
```

So the settled shark in the river is genuinely catchable, just never comfortable. This is what makes non-standard stocking real rather than cosmetic.

## Decay and drain

The surplus is **not permanent**. It decays on the same 30000-tick (25-minute) half-life as ordinary [fishing pressure](water-and-conditions.md#spot-depletion) — the fish disperse. Spring speeds the clock up 2.5×, which cuts both ways: fished-out water recovers faster, and a stocked surplus disperses faster.

What *is* permanent is **residency**. Once a species has settled in a region it lives there forever, whatever happens to the stock number.

There is also a **drain**: while a spot is running above roughly **125 % stock**, each fish you keep thins the school **25 % faster** than normal. A packed pond is easy fishing, but it does not stay packed. Releasing your trophies is how you keep it going.

## Reading the water

The [Fish Finder](tools.md#fish-finder) reports it all:

- The species biting here right now (up to eight)
- A *"Known for:"* line naming the water's signature fish
- A *"Stock:"* line listing every species whose level is more than 10 % away from normal — with a **(temp)** marker on unsettled transplants
- The barometer and bite outlook

The operator **Ichthyologist's Tablet** adds the raw numbers: each species' environment score, its level gate, its favourite bait, and `stock` vs `TEMP` percentages — plus a diagnosis grouping every *absent* species by the first gate that blocks it (water / depth / width / biome / season / time / weather).

## In short

- Release **big** fish, not many small ones.
- Release into water that actually **suits** the species if you want it to stay.
- Every release helps even when it fails — the fish is catchable there meanwhile.
- Natives pack to 250 %, settled transplants to 150 %, and an over-packed school thins faster.
- **Sport fishing is what feeds a water.**

## See also

- [Water and conditions](water-and-conditions.md#every-water-is-its-own) — how the seed decides what lives where
- [Fishing mechanics](fishing-mechanics.md) · [Species](species.md)
- [Tools](tools.md#fish-finder)
