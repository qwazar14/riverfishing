# Genetics

New in **0.9.0**. Every fish you land carries a genotype, and it is written on its [catch card](fishing-mechanics.md) under Shift. Four loci are on every species in the mod; the carp and the koi carry more, and what those extra loci spell is the fish's *variety* — the thing you see when you look at it.

Genes matter in two places, and it is worth keeping them apart from the start:

- **In the tank.** [Breeding](breeding.md) a pair crosses their genes into the roe, and the roe's own genotype decides how many fry live and what they look like.
- **In the water.** A [stocked](stocking.md) water keeps the *average* of everything ever released into it, and that average is what its fish are made of. This is where the size and colour loci do their only work — see below.

Nothing you caught before 0.9.0 changes. An old fish has no genes on it and reads as a default genotype: a carp reads as scaled, a koi as kohaku, everything else as recessive.

## The four loci

Four pairs of alleles on every fish, in this order, the dominant one written first:

| Locus | Letter | Dominant | Recessive |
|---|---|---|---|
| Size | **S** | bigger fish | — |
| Colour | **C** | more morphs | — |
| Vigour | **V** | survives the egg, recovers from fishing | — |
| Fertility | **F** | bigger clutches | — |

A genotype prints as space-separated pairs — `Ss Cc VV ff` — and a carp or a koi carries six or eight pairs instead of four.

### S — size

**Does nothing to the fish carrying it.** S is read only as the strong-allele *share* of a settled population, and there it scales every fish that water produces:

```
weight × (0.90 + 0.25 × share of S)
```

An all-`ss` stock throws fish 10 % light; an all-`SS` stock throws them 15 % heavy. The same share also drives the pond's own growth between spawning windows — see [Breeding](breeding.md#the-pond-grows-itself).

### C — colour

Also population-only. A settled water takes a **second morph roll** with a probability equal to its C share, so a `CC` line roughly doubles the chance of a morph in the water it was bred into. On the individual fish it does nothing at all.

### V — vigour

The first locus that acts on the fish carrying it. It is what survives the egg:

| Genotype | Fry that hatch |
|---|---|
| `vv` | 50 % |
| `Vv` | 70 % |
| `VV` | 90 % |

A [snag pile](blocks.md#water-upgrades) in the tank adds **+0.15**, capped at 95 %. A clutch never yields fewer than one fry. As a population share it also speeds a water's recovery from fishing pressure, by up to **×1.5** at a full share.

### F — fertility

The mother's F decides the clutch:

```
r      = clamp(mother weight / species mean, 0, 2)
eggs   = (10 + 15 × r) × fertility        (never below 4)
```

| Genotype | Fertility |
|---|---|
| `ff` | ×0.6 |
| `Ff` | ×1.0 |
| `FF` | ×1.5 |

So an ordinary specimen of an `Ff` fish lays 25 eggs, a double-mean `FF` hen 60. In the tank the count is then multiplied by the food, by the cross strength, and cut by a quarter for a leather pair — see [Breeding](breeding.md).

## Reading the genes

The genotype is on the fish's [catch card](fishing-mechanics.md), under Shift, beside the pattern index. It is stored in the item's NBT under `Genes`, and the variety word it spells is stored beside it as `Variety` — read back off the genotype every time rather than remembered, so a card can never say *mirror* over a pair of alleles that spell a leather carp.

A fish with no `Genes` tag at all — anything landed before 0.9.0 — reads as `KK nn` for a carp (scaled), `WW Rr bb` for a koi (kohaku) and the recessive allele twice for everything else.

## Where a wild fish's genes come from

Each allele of a wild fish is an independent coin, weighted per locus:

| Locus | Chance of the dominant allele |
|---|---|
| **S** | 0.30 for a baby, 0.40 juvenile, **0.50 adult**, 0.60 big, 0.70 giant |
| **C** | **0.25** normally, **0.70** if the fish has a morph |
| **V** | 0.50 |
| **F** | 0.50 |

Size follows the fish: a giant is a giant partly because it was born with the alleles for it, so the specimen you are proudest of is also the one worth putting in the tank.

**Water you have stocked ignores all of that.** If the water keeps a brood ledger for the species, each allele is drawn instead at the frequency *that water* holds — so a pond you filled with `SS` fish hands `SS` fish back. That is the whole point of a bloodline, and it is why a pond kept for a year is not the pond next door.

## Inheritance

Mendel and nothing more. One allele taken at random from each parent, per locus. **No linkage, no mutation, no drift.** The only thing that drifts between generations is the [pattern index](#the-pattern-index).

A cross never makes a third species: the fry take the **mother's** species and a genome crossed from both parents. That is how you breed a domestic carp out of a wild sazan — a sazan hen and a carp cock give sazan fry carrying the domestic scale alleles, and the other way round gives carp with wild blood in them.

## Carp wear their genotype

The scale cover of a carp is a real two-locus system in the fish, and it is one here.

| Locus | Dominant | Recessive |
|---|---|---|
| Scales | **K** scaled | **k** mirror |
| Nude | **N** nude (**lethal when doubled**) | **n** normal |

| Genotype | Variety |
|---|---|
| `K_ nn` | **Scaled** |
| `kk nn` | **Mirror** |
| `K_ Nn` | **Linear** |
| `kk Nn` | **Leather** |

**A carp is one species now.** Where the water used to hand you a Mirror Carp, a Linear Carp or a Naked Carp as separate fish, it hands you a **carp** wearing the genotype that says which — and the item is drawn with that variety's sprite. The item, the price, the journal page and the ledger all still say `carp`. The three old items stay registered, so a chest or a keepnet full of them keeps working, but nothing may *ask* for one any more: not a [contract](contracts.md), not the [order of the day](villager.md#the-order-of-the-day), not a species count.

The **sazan** (`wild_carp`) is the exception and stays its own species. It is the wild form — its own price, its own fight, its own legendary — and it only borrows the variety word, always reading as scaled.

### The lethal allele

`NN` never develops: the egg dies. Every nude-looking fish is therefore necessarily `Nn`, which has a consequence you can plan around:

- **Mirror breeds true.** `kk nn` × `kk nn` is 100 % mirror, forever.
- **Leather cannot.** `Nn` × `Nn` loses **a quarter of the clutch** and throws 50 % leather, 25 % mirror. The variety can never be fixed — which is exactly why a leather carp is worth more.
- A `Kk` scaled crossed with a mirror splits 50/50; a `KK` scaled throws only scaled.
- Linear × scaled (`Kk Nn` × `Kk nn`) gives 37.5 % scaled, 37.5 % linear, 12.5 % mirror, 12.5 % leather.

## Koi are bred, not found

The five koi items of earlier versions are one species now — **koi carp** — with four colour loci on top of the carp's two, and **seventeen** varieties fall out of the combinations.

| Locus | What it paints |
|---|---|
| **W** | the white ground |
| **R** | the red field (*hi*) |
| **B** | the black markings (*sumi*) |
| **G** | the metallic lustre |

One drawing paints all seventeen: four tint layers cut from a single white koi sprite, each handed a colour by the genotype. The eye is a fifth layer that takes no colour at all, so a platinum koi does not go blank-eyed.

### The seventeen varieties

The table is read **top to bottom, and the first row that fits names the fish**. `W_` means at least one dominant allele, `WW` means homozygous dominant, `ww` means none, and a locus not shown is not looked at.

| Variety | Genotype | What it looks like | Where from |
|---|---|---|---|
| **Tancho** | `WW RR bb` | one red crown on white | bred |
| **Yamabuki Ogon** | `ww rr bb G_` | solid gold | bred |
| **Ogon** | `W_ rr bb G_` | solid metallic white | bred |
| **Sakura Ogon** | `W_ R_ bb G_` | metallic red on white | bred |
| **Yamatonishiki** | `W_ R_ B_ G_` | metallic red and black on white | bred |
| **Kin Showa** | `ww R_ B_ G_` | metallic red on black | bred |
| **Gin Bekko** | `W_ rr B_ G_` | metallic black on white | wild, rare |
| **Kujaku** | `ww rr B_ G_` | metallic blue-grey | wild, rare |
| **Kin Hi Utsuri** | `ww R_ bb G_` | metallic red on a dark ground | bred |
| **Kohaku** | `W_ R_ bb` | red on white | wild, common |
| **Taisho Sanke** | `W_ R_ B_` | red and black on white | wild, common |
| **Showa** | `ww R_ B_` | red on black | wild |
| **Bekko** | `W_ rr B_` | black on white | wild, common |
| **Asagi** | `ww rr B_` | blue-grey, netted scales | wild |
| **Platinum** | `W_ rr bb` | plain white | bred |
| **Hi Utsuri** | `ww R_ bb` | red on a dark ground | wild |
| **Karasu** | `ww rr bb` | black | wild, rare |

The order is load-bearing twice over. **Tancho is not a variety of its own** — it is the one genotype homozygous at both the white and the red with no black to break the crown up, so every red pigment lands in a single spot; a kohaku that happens to be pure at both *is* a tancho, which is why tancho sits above it. And every lustre row sits above its matt twin, so **yamabuki, the gold koi, is a karasu with the lustre on**: a dark ground made metallic comes up gold.

### Wild and bred

A koi is never in the ordinary bite pool. It comes out of a **carp, sazan or mirror draw on a [carp rig](rigs-and-baits.md)**, at **0.5 %** anywhere and **35 %** in a cherry-blossom biome — a cherry-grove pond is the only place koi really belong.

**Nine of the seventeen are catchable**, at these odds within a koi roll:

| Variety | Share of wild koi |
|---|---|
| Kohaku | 30.8 % |
| Taisho Sanke | 19.2 % |
| Bekko | 15.4 % |
| Showa · Asagi · Hi Utsuri | 7.7 % each |
| Karasu · Kujaku · Gin Bekko | 3.8 % each |

**Eight are bred only** — tancho, platinum, yamabuki, ogon, sakura ogon, yamatonishiki, kin showa, kin hi utsuri. The lustre allele reaches your pond through exactly two wild fish, a **kujaku** or a **gin bekko**, about one koi in thirteen. Gold, platinum and tancho come out of a tank and nowhere else.

### What a variety is worth

The fisherman prices a koi by its variety, multiplying its base price:

| × | Varieties |
|---|---|
| **4.0** | Tancho |
| **3.5** | Yamabuki Ogon |
| **3.0** | Ogon · Yamatonishiki · Platinum |
| **2.5** | Sakura Ogon · Kin Showa · Kujaku |
| **2.0** | Gin Bekko · Kin Hi Utsuri · Showa · Asagi |
| **1.5** | Taisho Sanke · Hi Utsuri |
| **1.0** | Kohaku · Bekko |
| **0.8** | Karasu |

A [gem pattern](#the-twelve-gems) multiplies on top of that.

## The silver crucian clones herself

One species in the mod reproduces without the father's genes. A **crucian carp** hen crossed with a golden crucian gives a clutch that is a **copy of the mother** — his milt only starts the eggs dividing, and none of him is in them. The lethal-allele re-cross is skipped with it, because there is nothing to re-cross.

The reverse is an ordinary cross: a golden crucian hen with a silver crucian cock gives golden crucian fry with genes from both. Only the silver clones, and that is exactly how she displaces the golden wherever the two meet.

The [pattern index](#the-pattern-index) still inherits from both parents, so a clone line still drifts in colour even though its genes do not.

## Which species cross with which

A profile names the ids it will spawn with, and each cross carries a **strength** that scales the clutch — you read it off the egg count. Eight pools, twenty-one species:

| Pool | Species | Strength |
|---|---|---|
| **Carps** | Carp · Sazan · Koi carp | 1.0 |
| **Crucians** | Crucian carp · Golden crucian | 1.0 (the silver clones) |
| **Sturgeons** | Beluga · Sterlet · Sturgeon | 0.8 |
| **Breams** | Bream · White bream | **0.9** |
| | Bream · White bream · Blue bream · White-eye bream, every other pair | 0.2 |
| **Salmonids** | Salmon · Trout · Arctic char | 0.5 |
| **Zanders** | Zander · Volga zander | 0.35 |
| **Roach & rudd** | Roach · Rudd | 0.35 |
| **Coregonids** | Whitefish · Nelma | 0.3 |

The numbers are not arbitrary. A carp and a sazan are one animal, so 1.0. The bester — beluga × sterlet — has been farmed on 0.8 since the fifties. Bream × white bream at 0.9 is one of the commonest hybrids in European water, while the rare end of the bream complex survives badly at 0.2.

The scale varieties and the five legacy koi ids carry no pool of their own: a fish landed from one of those draws **is** a carp or a koi carp, and inherits that pool.

## The pattern index

Every carp and every koi you land carries a number, **0 to 999**, and it is on the card under Shift beside the genes. Twelve families divide the thousand, and each turns the fish's colour its own way; the index inside a family shifts it further, so two fish of the same variety and the same weight are not the same fish.

The index belongs to the fish it paints rather than to a hard-coded list, so it is a data tag — `riverfishing:patterned`. A pack that wants another species collecting patterns adds it there. Eleven items are in it today: the five carp ids, the koi carp and the five legacy koi.

### The twelve families

| Index | Family |
|---|---|
| 0–89 | Plain |
| 90–199 | Drift |
| 200–289 | Crown |
| 290–399 | Banded |
| 400–479 | Speckled |
| 480–559 | Mask |
| 560–639 | Marbled |
| 640–709 | Veined |
| 710–779 | Dappled |
| 780–849 | Ghost |
| 850–929 | Ember |
| 930–999 | **Aurora** |

The top band, **aurora**, is worth **×1.5** on its own — 7 % of all patterned fish. The journal counts the families you have seen for each species.

### The twelve gems

Twelve exact indices are gems, one per family. A gem takes the whole fish: a koi loses its patches, the body goes one solid colour, and the counter pays **×6**.

| Index | Gem | Index | Gem |
|---|---|---|---|
| **13** | Sapphire | **601** | Ruby |
| **127** | Gold | **677** | Copper |
| **239** | Emerald | **733** | Jade |
| **341** | Jet | **811** | Amber |
| **439** | Amethyst | **887** | Opal |
| **512** | Pearl | **971** | Obsidian |

Twelve indices out of a thousand: **one patterned fish in eighty-three is a gem of some kind, and a named one is one in a thousand.**

### Breeding a line

The index is heritable and never exact. A bred fish takes the **mean of its parents plus a drift** — a normal wobble of about ±12, roughly a fifth of the narrowest band — so a line breeds *toward* a family without ever locking onto a number. Bred to itself for ten generations, three lines in four are still in the family they started in.

A wild index is drawn from the world seed, the block and the tick, so the same spot cannot be re-cast for the same number. **Water you have stocked hands its own line back**: a released fish puts its index on the ledger, and the water's fish inherit from it instead of rolling. Farming a pond for aurora is a real thing to do, and chasing a named gem in it is not.

A parent with no index at all — a pre-0.9.0 fish — contributes nothing and the other parent's number is used as it stands.

## See also

- [Breeding](breeding.md) — the tank, the roe, the fry, and what the genes do there
- [Stocking](stocking.md) — the brood ledger, and how a water comes to have a bloodline
- [Species](species.md) · [Villager](villager.md) — what a variety is worth at the counter
- [Fishing mechanics](fishing-mechanics.md) — the catch card the genotype is written on
