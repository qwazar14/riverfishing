# Groundbait

There is **one groundbait**, and it is a **base**. Wheat + wheat seeds makes two jars; the village
fisherman sells them. On its own it sits dead centre — neutral grind, neutral richness — and it helps a
little. Everything after that is what you add to it.

**You cannot overfeed a spot.** Throw as much as you like: more feed is more fish, and no swim ever gets
worse for being fed. What a jar cannot do is out-fish its own contents — how *strong* a spot can get is
capped by what went into the bowl, never by how many jars did. The decision is not *how much*. It is
*what of*.

---

## Mixing your own

Put the jar and **anything else in a crafting grid**. One SLOT is one spoon — a stack of 64 in one slot
is still one spoon, because one item per slot is what the craft actually consumes.

**How many jars you get: one per spoon of food.** Ballast pays nothing — soil and clay are what you
dilute a mix *with*, not what it is made *of*. Three corn, one breadcrumb and four soil is four jars, and
each of the four is a lean, bulky mix. That is the trade: ballast buys you leanness, not volume.

**A jar you mixed yourself is finished groundbait, not an ingredient.** It will not go back into the
grid. A plain jar off the shelf will — that is what makes it a base.

A grid counts as a mix whenever it holds **two or more components**, or **a dye**. The one exception is
exactly one wheat and one wheat seeds — that is the jar's own recipe. Two of each IS a mix, because the
plain recipe only ever matched one of each.

### The pantry

*Counts as* is the bait this component reads as when the engine asks whether the fish eats it. A dash
means it makes no claim either way — it still changes the numbers, the colour and the variety.

| Component | Nutrition | Fraction | Counts as |
|---|---|---|---|
| **Groundbait** (the base) | 0.50 | 0.50 | — |
| **Groundbait Soil** | 0.00 | 0.35 | — (ballast) |
| Clay Ball | 0.00 | 0.55 | — (ballast) |
| Sugar | 0.20 | 0.05 | — |
| Bread Crumb | 0.25 | 0.10 | bread |
| Wheat Seeds | 0.30 | 0.15 | — |
| Bloodworm | 0.30 | 0.20 | bloodworm |
| Cocoa Beans | 0.35 | 0.25 | boilie |
| Sweet Berries | 0.35 | 0.40 | boilie |
| Dried Kelp | 0.40 | 0.30 | fish strip |
| Beetroot | 0.40 | 0.45 | corn |
| Carrot | 0.45 | 0.55 | corn |
| Bread | 0.45 | 0.20 | bread |
| Melon Seeds | 0.50 | 0.30 | pea |
| Wheat | 0.55 | 0.45 | pearl barley |
| Pumpkin Seeds | 0.55 | 0.35 | pea |
| Dough | 0.60 | 0.30 | dough |
| Sunflower | 0.60 | 0.25 | corn |
| Potato | 0.60 | 0.50 | dough |
| Maggot | 0.65 | 0.55 | maggot |
| Pearl Barley | 0.70 | 0.70 | pearl barley |
| Worm | 0.70 | 0.65 | worm |
| Fish Strip | 0.75 | 0.80 | fish strip |
| Pea | 0.80 | 0.75 | pea |
| Chicken Liver | 0.80 | 0.65 | chicken liver |
| Corn | 0.85 | 0.90 | corn |
| Boilie | 0.95 | 1.00 | boilie |

**Ballast is the important one.** Soil and clay bring bulk, cloud and colour and no calories at all, so
they are how you make a mix leaner and bulkier without making it worse.

---

## What the numbers do

Four things about a bed of feed decide how well it fishes for a given species.

### ★ Fraction — a big fraction calls big fish

Dust clouds and calls up bleak and roach. Whole grain and boilies lie on the bottom and are what a bream
or a carp is looking for. Every species has a grind it answers to, and it mostly follows from its own
size.

This is also the honest answer to *"why do I only catch small stuff"* — it is a choice, not a bug.
**Feed coarser.**

Coarse feed does one more thing: it flattens the size roll, so the fish that do come are more often good
ones. It is a better chance, never a promise.

### Nutrition — how much food, and how much it wants

More food gathers more fish. But each species has an appetite it is looking for: a carp wants a table
laid, a bleak wants a cloud, and a spread put down for the carp is not what the bleak came for.

### The menu — what is actually in it

If a fish eats worm, chopped worm in the feed works. The engine asks the species' own bait list, so the
answer is already on its journal page. The base and the ballast say nothing either way, which is why a
plain jar is neither the right food nor the wrong food.

### Variety — how many different things

The more that is down there, the wider the crowd. A plain jar reaches under half the pull a five-part
blend does:

| Mix | How strong the spot can get |
|---|---|
| A plain jar, nothing added | 0.48 |
| Base + one rich component | ~0.65 |
| Base + three, rich | ~0.85 |
| Five parts, rich | 1.00 |

Feeding more jars gets you to that ceiling faster. It never raises it.

---

## Feeding a spot

Right-click water to feed a **3×3** spot. A fed spot bites up to **twice as fast** and cuts up to 40% off
the wait.

**The same recipe adds up. A different one takes the swim over.** The old bed does not blend into the new
one — it is simply gone, and you are told so at the moment it happens. Decide the blend in the grid, not
by dribbling four jars in one at a time.

A **feeder, flat feeder, carp or 3-hook cage** empties one jar per cast **at the bobber** — the landing
spot, not the water in front of your boots. It is the same fed spot a right-click makes, so a cage and a
hand build one swim between them.

### How long it lasts

Between **3 and 12 minutes**, depending on the fraction. Dust clouds and washes out; whole grain lies on
the bottom and keeps working. That difference is what carries a long bottom session.

---

## Colour

Every component stains the mix, and the fed spot's cloud in the water comes out that colour. A dye in the
grid moves it most of the way to the dye's own colour without changing what the mix feeds or how it
fishes — including whether it counts as the same recipe in the water.

The jar's tooltip names that colour, and the pale speckles on its icon are drawn in it, so two jars in a
hotbar are told apart at a glance and a fed spot is recognisably yours.

**Colour does not affect the bite.** Unlike a lure's, it is read nowhere in the bite engine.

---

## What each species wants

<!-- SPECIES-GB -->

Sorted finest first, which is also smallest first — that is what the star means.

| Species | Fraction | Nutrition |
|---|---|---|
| Bleak | 0.14 — fine, clouds | 0.46 — moderate |
| Gudgeon | 0.22 — fine, clouds | 0.54 — moderate |
| Ruffe | 0.22 — fine, clouds | 0.54 — moderate |
| Smelt | 0.22 — fine, clouds | 0.56 — moderate |
| Rotan | 0.27 — fine, clouds | 0.60 — moderate |
| Round goby | 0.29 — fine, clouds | 0.62 — moderate |
| Rudd | 0.30 — mixed | 0.49 — moderate |
| Roach | 0.31 — mixed | 0.47 — moderate |
| Bluegill | 0.34 — mixed | 0.61 — moderate |
| Common dace | 0.34 — mixed | 0.52 — moderate |
| Crucian Carp | 0.40 — mixed | 0.63 — moderate |
| Herring | 0.40 — mixed | 0.57 — moderate |
| Perch | 0.40 — mixed | 0.70 — rich |
| Mayan cichlid | 0.42 — mixed | 0.50 — moderate |
| White Bream | 0.42 — mixed | 0.57 — moderate |
| White-eye bream | 0.42 — mixed | 0.61 — moderate |
| Blue bream | 0.44 — mixed | 0.55 — moderate |
| Nase | 0.46 — mixed | 0.59 — moderate |
| Sabrefish | 0.46 — mixed | 0.56 — moderate |
| Oscar | 0.47 — mixed | 0.68 — moderate |
| Volga zander | 0.47 — mixed | 0.70 — rich |
| Grayling | 0.49 — mixed | 0.57 — moderate |
| Garfish | 0.51 — mixed | 0.75 — rich |
| Mackerel | 0.51 — mixed | 0.75 — rich |
| Chub | 0.53 — mixed | 0.56 — moderate |
| Vimba bream | 0.53 — mixed | 0.60 — moderate |
| Ide | 0.54 — mixed | 0.66 — moderate |
| Tench | 0.54 — mixed | 0.63 — moderate |
| Bream | 0.56 — mixed | 0.68 — moderate |
| Eel | 0.56 — mixed | 0.74 — rich |
| Flounder | 0.56 — mixed | 0.71 — rich |
| Trout | 0.57 — mixed | 0.70 — rich |
| Whitefish | 0.57 — mixed | 0.52 — moderate |
| Rainbow trout | 0.58 — mixed | 0.70 — rich |
| Arctic char | 0.59 — mixed | 0.70 — rich |
| Pink salmon | 0.61 — mixed | 0.75 — rich |
| Bullseye snakehead | 0.62 — mixed | 0.70 — rich |
| Burbot | 0.62 — mixed | 0.75 — rich |
| Largemouth bass | 0.62 — mixed | 0.50 — moderate |
| Lenok | 0.62 — mixed | 0.70 — rich |
| Sea bass | 0.62 — mixed | 0.75 — rich |
| Zander | 0.62 — mixed | 0.50 — moderate |
| Peacock bass | 0.64 — mixed | 0.50 — moderate |
| Asp | 0.66 — coarse, holds big fish | 0.50 — moderate |
| Bluefish | 0.66 — coarse, holds big fish | 0.75 — rich |
| Pike | 0.66 — coarse, holds big fish | 0.50 — moderate |
| Koi Asagi | 0.69 — coarse, holds big fish | 0.75 — rich |
| Koi Bekko | 0.69 — coarse, holds big fish | 0.75 — rich |
| Koi Kohaku | 0.69 — coarse, holds big fish | 0.75 — rich |
| Koi Showa Sanke | 0.69 — coarse, holds big fish | 0.75 — rich |
| Koi Tancho Sanke | 0.69 — coarse, holds big fish | 0.75 — rich |
| Saithe | 0.71 — coarse, holds big fish | 0.75 — rich |
| Sterlet | 0.71 — coarse, holds big fish | 0.56 — moderate |
| Mirror Carp | 0.72 — coarse, holds big fish | 0.85 — rich |
| Carp | 0.73 — coarse, holds big fish | 0.85 — rich |
| Channel catfish | 0.73 — coarse, holds big fish | 0.77 — rich |
| Snook | 0.73 — coarse, holds big fish | 0.75 — rich |
| Striped bass | 0.74 — coarse, holds big fish | 0.75 — rich |
| Wild Carp | 0.75 — coarse, holds big fish | 0.84 — rich |
| Jack crevalle | 0.76 — coarse, holds big fish | 0.50 — moderate |
| Grass Carp | 0.77 — coarse, holds big fish | 0.66 — moderate |
| Mahi-mahi | 0.77 — coarse, holds big fish | 0.75 — rich |
| Atlantic salmon | 0.77 — coarse, holds big fish | 0.75 — rich |
| Barracuda | 0.79 — coarse, holds big fish | 0.75 — rich |
| Cod | 0.79 — coarse, holds big fish | 0.75 — rich |
| Silver carp | 0.79 — coarse, holds big fish | 0.81 — rich |
| Catfish | 0.81 — coarse, holds big fish | 0.81 — rich |
| Ray | 0.83 — coarse, holds big fish | 0.73 — rich |
| Conger eel | 0.84 — coarse, holds big fish | 0.74 — rich |
| Taimen | 0.87 — coarse, holds big fish | 0.50 — moderate |
| Wahoo | 0.88 — coarse, holds big fish | 0.50 — moderate |
| Halibut | 0.93 — coarse, holds big fish | 0.75 — rich |
| Sturgeon | 0.95 — coarse, holds big fish | 0.79 — rich |
| Tarpon | 0.99 — coarse, holds big fish | 0.75 — rich |
| Yellowfin tuna | 0.99 — coarse, holds big fish | 0.75 — rich |
| Blue marlin | 1.00 — coarse, holds big fish | 0.75 — rich |
| Mako shark | 1.00 — coarse, holds big fish | 0.75 — rich |
| Sailfish | 1.00 — coarse, holds big fish | 0.50 — moderate |
| Swordfish | 1.00 — coarse, holds big fish | 0.75 — rich |

---

## In short

- Catching nothing but tiddlers: **feed coarser**.
- After a carp or a bream: **coarse and rich**.
- After roach, bleak or a winter swim: **fine and lean** — ballast is how you get there.
- Get the fish's own food into the feed. Its journal page lists what it eats.
- **More different things** in the bowl beats more of one thing.
- Fish over the spot and nothing takes? It is never the amount. It is the grind, the richness, or the
  menu.
