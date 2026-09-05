# Faunal provinces

New in **0.9.0**. Minecraft has no geography, only weather: the same swamp, taiga and jungle repeat to the world border, so a fish gated on biomes alone lives everywhere its climate occurs. A taimen and a peacock bass ended up in the same river because the temperature matched.

Every world is now cut into four faunal **provinces**, and a species is absent from every province it does not belong to — however right the water looks.

## Four provinces, drawn by the seed

| Province | Roughly |
|---|---|
| **Palearctic** | Europe, northern Asia |
| **Nearctic** | North America |
| **Neotropic** | South America |
| **Indomalaya** | South and South-East Asia |

The map is drawn from the world seed on a **3072-block grid**: each cell is given a scattered centre and one of the four provinces, and a position belongs to whichever centre is nearest. Cells that drew the same province merge, so a real region is organic and usually larger than one cell — a straight walk holds one province for about three thousand blocks. There is nothing to save and nothing to generate; the same seed always draws the same map.

**The sea is not divided.** One ocean, one fauna. Sea species carry no province list at all, and a list that is empty means everywhere.

## Who lives where

Seventy of the 107 species name their provinces. The other 37 are ungated: 31 sea fish, and the six koi ids, which are an ornamental fish that goes wherever a cherry grove is.

| Province | Species |
|---|---|
| Palearctic | **59** |
| Nearctic | **24** |
| Neotropic | **18** |
| Indomalaya | **15** |

Eight species are on **all four** — the fish people have carried everywhere: the **common carp** and its three scale varieties, the **sazan**, the **grass carp**, the **silver carp** and the **rainbow trout**. Nothing else is cosmopolitan; brown trout and largemouth bass sit on three provinces each, not four.

The exact roster of each province is on each species' journal page and in the [fish finder](tools.md#fish-finder), which names the province you are standing in.

## What happens outside the range

A species outside its province scores **zero**. No factor, no half rate — it is simply not there, and it never enters the draw.

There is no message about it while you are fishing; the finder's province line is where you look. **Releasing** a fish into wild water that is the wrong province refuses outright and says so:

> *Taimen does not live in the Neotropic — a claimed pond of your own would take it*

Nothing is banked by that release. The fish stays in your hand.

## Two ways to fish for what is not yours

**Travel to it.** Three thousand blocks is a journey, and that is the point — the map is a reason to walk.

**Or bring one home.** A [pond you have claimed with a sign](blocks.md#private-pond-sign) is **exempt from the province gate entirely**, along with the depth gate, the width gate and the specialist biome gate. A carp lives in a dug pit; an arapaima lives in your garden if you put it there.

And a species you have **[settled](stocking.md)** in wild water outside its range does live there, at **a quarter** of full activity — a floor rather than a penalty, so it never makes a fish worse off where it belongs. Genuinely catchable, never comfortable.

## Specialists: `biomes_require`

The old `biomes` field is a **best-of**: a species scores by whichever of its listed groups the water matches, and any one of them will do. `biomes_require` is a different thing — **all of them at once, or nothing**. Sixteen species ask for a conjunction like that, and they are the ones worth hunting for.

| Species | Requires | Province |
|---|---|---|
| Arapaima | warm **and** jungle | Neotropic |
| Oscar | warm **and** jungle | Neotropic |
| Peacock bass | warm **and** jungle | Neotropic |
| Piraiba | warm **and** jungle | Neotropic |
| Red piranha | warm **and** jungle | Neotropic |
| Golden dorado | warm **and** river | Neotropic |
| Mayan cichlid | warm **and** swamp | Neotropic |
| Bullseye snakehead | warm **and** swamp | Indomalaya |
| Grayling | cold **and** river | Palearctic |
| Lenok | cold **and** river | Palearctic |
| Sculpin | cold **and** river | Palearctic |
| Taimen | cold **and** river | Palearctic |
| Burbot | cold | Palearctic, Nearctic |
| Nelma | cold | Palearctic |
| Whitefish | cold | Palearctic, Nearctic |
| Rotan | swamp | Palearctic, Indomalaya |

Four of the sixteen name only one group; they are on this list because that group is a hard requirement rather than a preference. A claimed pond is exempt from this gate too.

## Biome groups

Fourteen names, and a water can hold several at once.

| Group | Where it comes from |
|---|---|
| `cold` · `temperate` · `warm` | The biome's base temperature — below 0.3, between, above 0.95 |
| `river_biome` | A river biome |
| `ocean_biome` | An ocean, a deep ocean, or a modded biome named as salt water |
| `deep` | A deep ocean |
| `beach` · `jungle` · `forest` · `taiga` · `mountain` | The vanilla biome tags (mountain covers hills too) |
| `dry` | Savanna or badlands |
| `swamp` | A swamp water body, or a modded biome named as a swamp |
| `cherry` | A cherry grove, or a modded biome named as one, or any biome with *cherry* in its id |

A **river reads its banks**: eight directions at four distances, and the groups it finds are merged into the water's own. A taiga river is therefore cold *and* temperate at once, which is what stops a river through two biomes from being a hard edge.

## Modded biomes

**116 modded biomes** from Terralith, Oh The Biomes You'll Go and Biomes O' Plenty have their water named — which of them are salt, which are fresh, which are swamps, and which are cherry groves under another name. A sakura grove holds koi and gets its own pond.

None of the three mods is required. A world without them loads the lists as nothing, and everything works exactly as before.

## See also

- [Water and conditions](water-and-conditions.md) — the rest of what decides a bite
- [Stocking](stocking.md) — settling a foreign species, and what a claimed pond changes
- [Species](species.md) · [Species reference](species-reference.md) — the per-species gates
- [Tools](tools.md#fish-finder) — the sounder's province line and the chart
