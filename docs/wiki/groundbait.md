# Groundbait

There is one groundbait — **Base Groundbait** — and it is exactly what the name says: the thing you build
on. **Wheat Seeds + Bread** makes two of it, and the village fisherman sells it.

You can feed a spot with the base on its own and it works. It is simply **not very effective**: it sits
dead centre, neutral grind and neutral richness, with nothing it is especially for. Everything after that
is what you **add** to it.

**You cannot overfeed a spot.** Throw as much as you like: more feed is more fish, and no swim ever gets
worse for being fed. What groundbait cannot do is out-fish its own contents — how *strong* a spot can get
is capped by what went into the mix, never by how much of it you threw. The decision is not *how much*.
It is *what of*.

---

## The two-minute version

1. Craft **Wheat Seeds + Bread** → 2 × Base Groundbait.
2. Put the base in a crafting grid **together with up to 8 other things**. One slot = one item.
3. **Right-click water** with the result. You have fed a 3×3 spot.
4. Catching nothing but tiddlers? **Feed coarser** — corn, boilies, pearl barley.
5. Nothing biting at all? It is not the amount. It is the grind, the richness, or the menu.

If you would rather copy than think, skip to the [cookbook](#the-cookbook) — every recipe there was
found by searching the pantry and checked against the fish it names.

---

## Making a mix

Put **Base Groundbait** in a crafting grid together with **anything else, up to 8 items**. One SLOT is
one item — a stack of 64 in a slot still counts as one, because one per slot is what the craft takes.

**The base is required.** Corn and boilies on their own are not groundbait; they are what you add *to*
groundbait. That is the whole shape of the system: one thing you make, and a pantry of things you put in
it.

**How much comes out: one groundbait for every edible item that went in.** Ballast pays nothing — soil
and clay are what you dilute a mix *with*, not what it is made *of*. Base ×1, corn ×3 and soil ×4 comes
out as four, and each of the four is a lean, bulky mix. That is the trade: ballast buys you leanness, not
volume.

**A mix you made yourself is finished groundbait, not an ingredient.** It will not go back into the grid.
Plain base will — that is what makes it the base.

### The pantry

*Counts as* is the bait this component reads as when the engine asks whether the fish eats it. A dash
means it makes no claim either way — it still changes the numbers, the colour and the variety.

| Component | Nutrition | Fraction | Counts as |
|---|---|---|---|
| **Base Groundbait** (required) | 0.50 | 0.50 | — |
| **Groundbait Soil** | 0.00 | 0.35 | — (ballast) |
| Clay Ball | 0.00 | 0.55 | — (ballast) |
| Sugar | 0.20 | 0.05 | — |
| Bread Crumb | 0.25 | 0.10 | bread |
| Wheat Seeds | 0.30 | 0.15 | — |
| Bloodworm | 0.30 | 0.20 | bloodworm |
| Cocoa Beans | 0.35 | 0.25 | boilie |
| Sweet Berries | 0.35 | 0.40 | boilie |
| Dried Kelp | 0.40 | 0.30 | raw fish fillet |
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
| Raw Fish Fillet | 0.75 | 0.80 | raw fish fillet |
| Pea | 0.80 | 0.75 | pea |
| Chicken Liver | 0.80 | 0.65 | chicken liver |
| Corn | 0.85 | 0.90 | corn |
| Boilie | 0.95 | 1.00 | boilie |

**Ballast is the important one.** Soil and clay bring bulk, cloud and colour and no calories at all, so
they are how you make a mix leaner and bulkier without making it worse.

**Vanilla is a real shelf, not a courtesy.** Wheat, bread, a potato and a beetroot are hour-one items,
and over the base they mix into something that genuinely fishes. You do not have to wait on a corn farm
to start feeding.

---

## What the numbers do

Nutrition and fraction are the **average over everything in the mix**, weighted by how many of each and
with ballast included. That is how ballast dilutes: it averages in zeros.

Four things then decide how well that bed of feed fishes **for a given species**:

```
menu      how much of the mix this fish actually eats     (its own bait list)
fraction  1 - |mix fraction - the species' fraction|      -> 0.45 .. 1.00
nutrition 1 - |mix nutrition - the species' nutrition|    -> 0.60 .. 1.00
variety   0.90 .. 1.00, by how many different things are in it

groundbait score = menu x fraction x nutrition x variety, capped at 1.0
an unfed swim    = 0.40
```

### ★ Fraction — a big fraction calls big fish

Dust clouds and calls up bleak and roach. Whole grain and boilies lie on the bottom and are what a bream
or a carp is looking for. Every species has a grind it answers to, and it mostly follows from its own
size — a 20 g bleak wants 0.14, a 4 kg carp wants 0.73.

This is also the honest answer to *"why do I only catch small stuff"* — it is a choice, not a bug.
**Feed coarser.**

Coarse feed does one more thing: over a coarse bed the **size roll flattens**, so the fish that do come
are more often good ones. Half the mix or finer changes nothing; an all-boilie bed is the full effect.
It is a better chance, never a promise.

### Nutrition — how much food, and how much it wants

More food gathers more fish. But each species has an appetite it is looking for: a carp wants a table
laid (0.85), a bleak wants a cloud (0.46), and a spread put down for the carp is not what the bleak
came for.

This is why "just use boilies" does not work. Boilies are the richest and coarsest thing in the pantry,
which makes them **perfect for a carp and useless for a roach** — and the roach swim is where most
people fish.

### The menu — what is actually in it

If a fish eats worm, chopped worm in the feed works. The engine asks the species' own bait list, so the
answer is already on its journal page: whatever it takes on the hook, it wants in the feed.

The base and the ballast say nothing either way, which is why plain base is neither the right food nor
the wrong food. That is a deliberate difference from "there is nothing here a bream wants" — the first
fishes like plain base, the second fishes worse than it.

### Variety, and the difference between score and ceiling

Two numbers decide a swim, and they are not the same number.

- **Score** — the four terms above. *Who comes.* A perfectly matched mix scores 1.00 for its fish and
  0.25 for the wrong one.
- **Ceiling** — how strong the spot can ever build, from nutrition and variety. *How many come.*

```
ceiling = 0.25 + 0.45 x nutrition + 0.30 x (things in it - 1) / 4      (capped at 1.0)
```

| Mix | Ceiling |
|---|---|
| Plain base, nothing added | 0.48 |
| Base + one rich thing | ~0.65 |
| Base + three, rich | ~0.85 |
| Base + four, rich | 1.00 |

Feeding more gets you to that ceiling faster. It never raises it.

A three-part mix can score 1.00 and still be beaten in practice by a five-part mix scoring 0.90, because
the five-part swim holds more fish. **Aim for the base plus three or four things**, and let the ballast
be one of them when you need it lean.

### Worked example

Base ×1, Maggot ×3, Worm ×1, Wheat Seeds ×2 — seven items in the grid.

```
nutrition = (0.50x1 + 0.65x3 + 0.70x1 + 0.30x2) / 7 = 0.54
fraction  = (0.50x1 + 0.55x3 + 0.65x1 + 0.15x2) / 7 = 0.44
variety   = 4 different things
output    = 7   (everything here has calories)
ceiling   = 0.25 + 0.45x0.54 + 0.30x0.75 = 0.72
```

Against a **bream** (wants 0.56 / 0.68, and its bait list has maggot 1.0 and worm 0.9):

```
menu      = 0.45 + 0.75 x (1.0x3 + 0.9x1) / 4 = 1.18
fraction  = 0.45 + 0.55 x (1 - |0.44 - 0.56|) = 0.93
nutrition = 0.60 + 0.40 x (1 - |0.54 - 0.68|) = 0.94
variety   = 0.90 + 0.10 x 0.75                = 0.98
score     = 1.18 x 0.93 x 0.94 x 0.98 = 1.01  -> capped at 1.00
```

Against a **carp** (wants 0.73 / 0.85, and its bait list barely mentions maggot) the same mix comes out
near 0.45 — no better than not feeding at all. One mix, two verdicts. That is the whole feature.

---

## Feeding a spot

Right-click water to feed a **3×3** spot. A fed spot bites up to **twice as fast** and cuts up to 40% off
the wait. The centre column counts at full strength, the outer ring at 60%.

**The same recipe adds up. A different one takes the swim over.** The old bed does not blend into the new
one — it is simply gone, and you are told so at the moment it happens. Decide the blend in the grid, not
by throwing four different mixes in one after another. (A dye does *not* make a mix different: a pink one
and a plain one of the same recipe are the same food.)

A **feeder, flat feeder, carp or 3-hook cage** empties one per cast **at the bobber** — the landing spot,
not the water in front of your boots. It is the same fed spot a right-click makes, so a cage and a hand
build one swim between them. Empty the cage by hand first if you want to fish a swim you already built
with something else.

### How long it lasts

Between **3 and 12 minutes**, depending on the fraction. Freshness halves every 90 seconds for pure dust
and every 4½ minutes for whole grain.

Dust clouds and washes out; whole grain lies on the bottom and keeps working. That difference is what
carries a long bottom session — a fine mix on a feeder rod is spent before the first bite window closes.

---

## Colour

Every component stains the mix, and the fed spot's cloud in the water comes out that colour. A dye in the
grid moves it most of the way to the dye's own colour without changing what the mix feeds or how it
fishes.

The tooltip names that colour, and the pale speckles on the icon are drawn in it, so two mixes in a
hotbar are told apart at a glance and a fed spot is recognisably yours.

**Colour does not affect the bite.** Unlike a lure's, it is read nowhere in the bite engine.

---

## The cookbook

Ready recipes are a **floor, not a ceiling** — every one of these was found by searching the pantry, and
every one is beatable by somebody who tunes it for the water in front of them. Numbers are how many of
each go in the grid; every recipe starts with the base, because that is the rule. *Score* is against the
fish named; *ceil* is how strong the swim can build.

| For | Recipe | n / f | Ceil | Out | Scores |
|---|---|---|---|---|---|
| **Hour one, no farm yet** | Base ×3, Wheat ×2, Bread ×3, Potato ×1 | 0.51 / 0.39 | 0.70 | 9 | roach 0.67, crucian 0.69, bream 0.66 |
| **Bleak, roach, ruffe, gudgeon** — also winter and pressured water | Base ×1, Maggot ×1, Bloodworm ×1, Wheat Seeds ×3 | 0.39 / 0.28 | 0.65 | 6 | bleak 0.95, roach 1.00, ruffe 0.96, gudgeon 1.00 |
| **Bream, white bream, blue bream, vimba, sabrefish** | Base ×1, Maggot ×3, Worm ×1, Wheat Seeds ×2 | 0.54 / 0.44 | 0.72 | 7 | all five at 1.00 |
| **Tench, crucian carp, ide** | Base ×2, Dough ×2, Maggot ×2, Corn ×1 | 0.62 / 0.51 | 0.75 | 7 | tench 0.93, crucian 0.96, ide 0.77 |
| **Carp, mirror carp, wild carp** | Base ×1, Corn ×3, Boilie ×3, Pea ×2 | 0.83 / 0.86 | 0.85 | 9 | carp 0.96, mirror 0.96, wild 1.00 |
| **Grass carp, silver carp** | Base ×1, Corn ×3, Pearl Barley ×3, Boilie ×1 | 0.76 / 0.79 | 0.82 | 8 | grass 0.76, silver 0.74 |
| **Catfish, eel, burbot** | Base ×2, Boilie ×1, Worm ×3, Chicken Liver ×3 | 0.72 / 0.66 | 0.80 | 9 | catfish 0.91, eel 0.91, burbot 0.97 |
| **Sturgeon, channel catfish** | Base ×1, Boilie ×1, Worm ×1, Chicken Liver ×3 | 0.76 / 0.68 | 0.82 | 6 | sturgeon 0.91, channel cat 1.00 |
| **Sterlet, nase — the plain worm swim** | Base ×1, Clay Ball ×1, Worm ×3 | 0.52 / 0.60 | 0.63 | 4 | sterlet 1.00, burbot 0.96, nase 0.89 |
| **Cod, flounder, conger, saithe** | Base ×1, Raw Fish Fillet ×3, Dried Kelp ×1 | 0.63 / 0.64 | 0.68 | 5 | cod 1.00, flounder 1.00, conger 0.97 |

Two things to notice. The **bream** mix scores a perfect 1.00 on five different species — because their
grind, their appetite and their bait lists agree, and one blend covers a whole bream swim. The **sterlet**
mix scores 1.00 too, but its ceiling is only 0.63: only three things in it, one of them clay, make a
precise and thin swim. Add a fourth edible one if you want a crowd rather than a sniper.

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

## How much groundbait actually decides

Worth knowing the price of the question, so you do not feed a swim that needed a different bait.

A bite is a weighted sum, and the feed is **one seventh** of it:

```
bait           30%
groundbait     15%
rig            13%
rod            12%
line           12%
hook           10%
reel            8%
```

An unfed swim is not zero, it is **0.40** — fishing without groundbait is fine, just not ideal. A
perfect mix scores 1.00. So the whole distance between "did not feed" and "fed flawlessly" is
`0.15 × 0.60 = 0.09` of the bite weight, about nine per cent. Feed will not rescue a session with the
wrong bait on the hook: bait weighs twice as much and costs less.

**What feed really buys is the second lever — time.** A fresh swim speeds bites up by as much as
**double** (the multiplier is `1 + freshness`, capped at 2.0). That is why feeding feels like far more
than nine per cent: the fish do not so much bite more often as **arrive sooner**.

And the third — size. A coarse table flattens the size curve: the roll's exponent is divided by
`1 + 0.55 × coarseness × freshness`, where coarseness is the fraction above 0.5 stretched to one. In
practice: **half fraction and finer changes nothing**, and whole grain on a fresh swim visibly lifts
the average fish.

---

## Predators: the ones there is nothing to feed

A mix is judged against the **species' own bait list** — against what the fish actually eats. Which
leads to something no tooltip says: **pike, zander and asp eat nothing in the pantry.** Not a grain,
not a worm, not liver.

In numbers, for pike (its pair is fraction 0.66, nutrition 0.50):

| Swim | Groundbait score |
|---|---|
| Not fed | **0.40** |
| Plain base, no additives | **0.62** |
| A carp mix: sweetcorn, boilies | **0.29** |

Read that as: a sweet carp mix on a pike swim works **against** you — it is worse than not feeding at
all. Plain base helps, because it claims to be no particular food: the engine scores it "neither the
right food nor the wrong food" (0.75 on menu) and then only looks at fraction and nutrition.

**Some predators do feed, though.** Catfish, eel, perch and trout all have pantry items in their diet,
and for them groundbait works at full strength:

| Species | What it takes from the pantry | Mix |
|---|---|---|
| Catfish | chicken liver 1.0, worm 0.7, boilie 0.6 | liver + chopped worm → **0.90** |
| Eel | worm 1.0, liver 0.7 | worm and liver, mid grind |
| Perch | worm 0.6 | worm and bloodworm, fine grind |
| Trout | worm 0.6 | worm, fine grind, not rich |

The rule is simple: **if you catch it on a spinning rod, there is nothing to feed it.** If it takes
natural bait off the bottom, feed it — and feed it the very thing you are fishing with.

---

## Tactics: how much to throw, and when to top up

**One throw puts down 0.60 of freshness**, but every mix has its own **ceiling**:

```
ceiling = 0.25 + 0.45 × nutrition + 0.30 × variety
```

where variety counts components: one gives 0, five or more gives 1. Which means:

| Mix | Ceiling | Throws to reach it |
|---|---|---|
| Plain base | 0.475 | one and a bit |
| Base + worm + maggot + barley | ~0.80 | two |
| Five parts, rich | 1.00 | two |

**More than two throws in a row is pointless** — the swim hits its ceiling and further jars do
nothing. You cannot spoil it either: there is no overfeeding in this mod, by design.

**When to top up.** Freshness halves every half-life: **90 seconds** for pure dust, up to **four and a
half minutes** for whole grain. A swim lives between three and twelve minutes, again by fraction. In
practice:

- a fine mix on the feeder — top up every **2–3 minutes**, or by the third cast you are fishing a bare
  patch of river;
- grain and boilies — they carry a session; top up every **8–10 minutes**;
- top up with **the same mix**: an identical recipe adds towards the ceiling, a different one
  **displaces** the swim outright and resets it to a single throw's worth.

**Geometry.** A throw covers **3×3 blocks**. The centre column counts at full strength, the eight
neighbours at **0.6**. So casting accuracy is worth about forty per cent of what your feed does —
landing in the middle is meaningfully better than "somewhere near it".

**What feed does not fix.** It does not restore a depleted population: if a spot has been fished out
(or you left a bait trap in it), feed will gather what is left but adds no fish. Recovery runs on its
own clock, and groundbait does not touch it.

---

## Common mistakes

**"I fed a lot and it got worse."** It did not — you cannot overfeed. What happened is that the last
throw had a different recipe and replaced the swim you had built. Watch the message when you throw.

**"Boilies are the best, so I use boilies."** Boilies are the richest and coarsest thing there is, which
makes them wrong for everything under a kilo. Most of what swims past you is under a kilo. They are also
not groundbait on their own — nothing is, without the base.

**"Nothing but small fish."** Feed coarser. Every step of fraction pushes the shoal upward, and over a
coarse bed even the fish that do come roll bigger.

**"My mix scores 1.00 but the swim is quiet."** Score is who comes; the ceiling is how many. Three things
with ballast among them can be perfectly matched and still hold almost nothing. Add food and add variety.

**"I fed the water in front of me and cast out to the drop-off."** The fed spot is 3×3 around where the
groundbait landed, and the bobber has to land in it. Feed where you are going to cast — or load the cage
and let the cast do it for you.

---

## In short

- Catching nothing but tiddlers: **feed coarser**.
- After a carp or a bream: **coarse and rich**.
- After roach, bleak or a winter swim: **fine and lean** — ballast is how you get there.
- Get the fish's own food into the mix. Its journal page lists what it eats.
- **The base plus three or four different things** beats more of one thing — score is who comes, the
  ceiling is how many.
- Fish over the spot and nothing takes? It is never the amount. It is the grind, the richness, or the
  menu.
