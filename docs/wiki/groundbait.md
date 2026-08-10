# Groundbait

Feeding a spot is the one place in this mod where **more is not better**. Everywhere else a heavier line
and a bigger reel beat a lighter one; here a rich mix on the wrong water is worse than a lean one, and
on cold water it can shut a swim down entirely.

That is because groundbait does two things at once. It **pulls fish in**, and it **feeds them**. A fish
that has eaten does not bite.

---

## The four ready-made ones

| Groundbait | Nutrition | Fraction | What it is for |
|---|---|---|---|
| Powder Groundbait | 0.25 | 0.10 | A cloud. Calls up small silver fish fast, feeds almost nothing |
| Oil Cake Groundbait | 0.55 | 0.25 | Smell and oil. The carp-family middle |
| Grain Groundbait | 0.80 | 0.85 | Whole particle, lies on the bottom, holds big fish |
| Pellet Groundbait | 0.90 | 0.95 | The richest and coarsest — what stocked fish are raised on |

**None of the four is crafted any more.** They are what a MIX comes out as — plus the shop, which still
sells three of them if you would rather buy than stir.

What you craft is the **Groundbait Base**: wheat + wheat seeds, dead centre on both axes (0.50 / 0.50) and
belonging to no category at all. On its own it is not groundbait and cannot be thrown — it is bulk and
calories waiting for a character. Whatever you add decides what it becomes.

## Mixing your own

Put **two or more components in a crafting grid**. One SLOT is one spoon — a stack of 64 in one slot
is still one spoon, because one item per slot is what the craft actually consumes. The spoons decide
everything: three corn, one breadcrumb and four soil comes out at fraction **0.53** and nutrition
**0.35** — half of it is still particle, and it feeds barely more than a third of what pure corn would.
The four spoons of soil are what did that.

**How many jars you get: one per spoon of food.** Ballast pays nothing — soil is what you dilute a mix
*with*, not what it is made *of*. Three corn, one breadcrumb and four soil is four jars, and each of the
four is the lean mix above. That is the trade: ballast buys you leanness, not volume.

**A jar you mixed yourself is finished groundbait, not an ingredient.** It will not go back into the
grid. The four ready-made ones still will — that is what makes them a base.

A grid counts as a mix whenever it holds **two or more components**, or **a dye**. The one exception is
exactly one wheat and one wheat seeds — that is the base recipe. Two of each IS a mix, because the
vanilla recipe only ever matched one of each.

### What you can put in

| Component | Nutrition | Fraction | Reads as |
|---|---|---|---|
| **Groundbait Base** | 0.50 | 0.50 | — (no category) |
| **Groundbait Soil** | 0.00 | 0.35 | — (ballast) |
| Clay Ball | 0.00 | 0.55 | — (ballast) |
| Sugar | 0.20 | 0.05 | powder |
| Bread Crumb | 0.25 | 0.10 | powder |
| Wheat Seeds | 0.30 | 0.15 | powder |
| Bloodworm | 0.30 | 0.20 | powder |
| Cocoa Beans | 0.35 | 0.25 | cake |
| Sweet Berries | 0.35 | 0.40 | grain |
| Dried Kelp | 0.40 | 0.30 | powder |
| Beetroot | 0.40 | 0.45 | grain |
| Carrot | 0.45 | 0.55 | grain |
| Bread | 0.45 | 0.20 | powder |
| Melon Seeds | 0.50 | 0.30 | cake |
| Wheat | 0.55 | 0.45 | grain |
| Pumpkin Seeds | 0.55 | 0.35 | cake |
| Dough | 0.60 | 0.30 | powder |
| Sunflower | 0.60 | 0.25 | cake |
| Potato | 0.60 | 0.50 | grain |
| Maggot | 0.65 | 0.55 | pellet |
| Pearl Barley | 0.70 | 0.70 | grain |
| Worm | 0.70 | 0.65 | pellet |
| Fish Strip | 0.75 | 0.80 | pellet |
| Pea | 0.80 | 0.75 | grain |
| Chicken Liver | 0.80 | 0.65 | cake |
| Corn | 0.85 | 0.90 | grain |
| Boilie | 0.95 | 1.00 | pellet |

Any of the four ready-made groundbaits can go in as a **base**, at its own numbers.

**Ballast is the important one.** Soil and clay bring bulk, cloud and colour and no calories at all, so
they are how you pull fish in without filling them. On a hammered water half the mix should be ballast.

### What the numbers do

**Nutrition** cuts both ways, and that is the whole decision. It is how strongly the spot PULLS — a mix
with no food in it can only ever reach about 40% of full attraction, however much of it you throw — and
it is also how fast the fish fill up, and a full fish is not interested.

The two do not cancel, because only the filling-up half is divided by appetite. In warm water the fish
eat what you give them and a rich mix is worth its calories; in cold water the same jar fills them for
hours and buys almost nothing. Feeding often pushes the answer the same way as cold water: the more
frequently you top the spot up, the leaner the mix should be.

**Fraction** is matched against the size of fish you are after — dust for bleak and roach, whole grain
for carp and bream. Each species has a preferred grind that follows from its own weight, so this is also
the answer to "why do I only catch small stuff": feed coarser.

**Category** — powder, grain, pellet or cake — is whichever component has the most spoons behind it, and
is what each species' journal page lists as its preferred groundbait. Ballast never votes for the
category.

## Colour

Every component stains the mix, and the fed spot's cloud in the water comes out that colour. A dye in
the grid moves it most of the way to the dye's own colour without changing what the mix feeds or what it
reads as.

The jar's tooltip names that colour, and the pale speckles on its icon are drawn in it, so two jars in
a hotbar are told apart at a glance and a fed spot is recognisably yours.

**Colour does not affect the bite.** Unlike a lure's, it is read nowhere in the bite engine — it is how
you tell your mixes apart, and nothing more.

## Appetite: the thing that catches people out

Fish eat far less in cold water than in warm. The same jar that barely registers in a summer lake
fills a spot **about seven times faster** under the ice, so the amount that was right in July is far too
much in January.

| Water | Appetite |
|---|---|
| Warm biome, summer | full |
| Temperate, spring or autumn | about half |
| Cold biome, winter, or under ice | almost none |

Feeding is divided by that appetite. Get it wrong and the spot goes **overfed**: the fish are there,
they are eating, and they are not interested in your hook. You will be told when it happens.

An overfed spot recovers on its own in a couple of minutes. It is a pause, not a lost evening — but on
cold water the right amount really is a handful.

## How long a spot lasts

Between **3 and 12 minutes**, depending on the fraction. Dust clouds and washes out; whole grain lies on
the bottom and keeps working. Feeding the same place again tops it up.

A **feeder cage** on a bottom rig delivers whatever groundbait you loaded into it, mix and all, every
time you cast.

## In short

- Cold water, or a place you have fished hard: **lean and heavy on ballast**.
- A stocked pond: **rich, coarse, pellet** — imitate what they were raised on.
- Somewhere nobody fishes: **rich and coarse**, and plenty of it.
- Catching nothing but tiddlers: **feed coarser**.
- Fish over the spot but no bites: **you overfed**. Wait, and use less next time.
