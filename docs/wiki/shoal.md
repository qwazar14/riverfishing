# Fish in the water

Since 0.7.0 the water shows you what is in it. Stand anywhere near water and the fish it actually holds drift below the surface — the right species, at their own depth, at their real size. Nothing has to be cast and you do not need a rod in your hand.

This page is about reading them.

## What you are looking at

The shoal is chosen by the same function that decides what takes your bait, and the same one the [Fish Finder](tools.md#fish-finder) reads: water type, depth, width, season, time of day, weather, biome group and the water's own [species community](water-and-conditions.md#every-water-is-its-own).

Two consequences worth having:

- **Your tackle is not part of it.** Swapping a lure never makes a fish appear or vanish. You are seeing what lives here, not what is interested in you.
- **It follows the state of the water.** [Fishing pressure](water-and-conditions.md#spot-depletion) thins the shoal where a species has been fished down and lets it fill back in as the swim recovers; a [stocked](stocking.md) species shows up once it is actually there. A hammered swim looks empty because it is.

The make-up of a shoal is fixed to the in-game **hour**: it holds still while you fish it, and has moved on when you come back later.

## How much is shown

Water is described in **12×12-block cells** pinned to the world grid, out to **24 blocks** each way from where you stand — the 3×3 chunks around you, so a lake is populated to the far bank instead of in a ring at your feet. At most **20 cells** and **96 fish** at once.

Because the cells are pinned to the world rather than to you, walking adds and drops whole shoals at the edges instead of dragging the fish along with you.

Fish are handed out by distance, so a far cell is not starved by a near one:

| Distance to the patch | Fish in it | Smallest fish shown |
|---|---|---|
| up to 12 blocks | up to 7 | any |
| 12–26 blocks | up to 4 | any |
| 26–40 blocks | up to 4 | 35 cm |
| over 40 blocks | up to 2 | 90 cm |

Nothing at all is drawn past **42 blocks**, and the last ten blocks of that are a fade rather than an edge. Small fish thin out with distance the way they really do: a 20 cm roach thirty blocks off is two pixels of noise, while a metre of pike is worth seeing from the opposite bank.

How busy a patch looks follows how rich it is — within the caps above, good water is crowded and poor water is bare.

Water more than **20 blocks** above or below you is skipped. The lake at the foot of the cliff you are standing on is not the water you are looking at.

## How well you see them

Each patch carries a **clarity** that decides how solidly its fish are drawn.

| Water | Clarity |
|---|---|
| Puddle, Pond | 1.00 |
| Lake | 0.90 |
| River | 0.80 |
| Sea | 0.75 |
| Swamp | 0.50 |

Rain multiplies that by **0.7** and a thunderstorm by **0.45**. Water that sky light does not reach — under a roof, under an overhang, in a cave — is dimmed to as little as **0.35** of the figure above.

> **Depth deliberately hides nothing.** A fish on the bottom of a clear lake is drawn as plainly as one just under the surface. Only the water itself dims a fish. If you bother to lean over and look, you get to see what is down there.

## What the shoal tells you

- **Size is true.** One block is one metre, with a small readability bias, and never smaller than 0.16 or larger than 4.5 blocks on screen. A shape you can make out from the bank is a genuinely big fish.
- **These are everyday fish, not the record book.** The specimens shown scatter around the species' average weight; a trophy is something you catch, not something you spot.
- **Colour is age.** Young fish are pale and silvery, old ones darken toward their species' adult colour — the same table that paints the fish in your hand.
- **Depth is the species' habit.** Surface species sit in the top block or two, mid-water species hang in the middle, bottom species travel just off the bed.
- **Small species travel in numbers.** A species averaging under **900 g** comes out in groups of three to six sharing one circuit; anything heavier swims alone.
- **There are no labels.** You get a shape, a size and a colour. Working out what it is stays yours to do — that is what the [Fish Finder](tools.md#fish-finder) and the [journal](tools.md#fishing-journal) are for.

The fish also respect the shoreline: they turn before they reach the bank, and they hold to their own patch instead of wandering into the next one.

## They react to you

Anything that frightens the water frightens the shoal, and you get to watch it happen. A cast landing on their heads, footsteps on the bank, a jump, wading in, a moving boat, a block broken nearby, or your own shadow thrown across the water by a low sun.

A frightened shoal:

- turns away from you and breaks for open water — over about a second, not instantly
- swims at up to **3.2×** its cruising speed (a fish cruises at roughly half its own body length per second)
- drops about a block deeper
- dims as it goes, and once the water is badly stirred there is nothing left to draw at all

They come back as the water calms, which takes **30 to 90 seconds** depending on the water: a murky swamp or a deep, open lake forgets you fastest, while clear shallows and a small pond stay wary longest. Crouched and standing still makes no noise at all — that is the whole counter-play. Setting `spook` to **0** in the config switches the reaction off entirely.

It is the same disturbance that cancels bites: a rig sitting in frightened water goes quiet in proportion to how frightened it is, whether it is in your hands or on the rod pod. So a patch of water emptying in front of you is a warning worth reading.

## What it is not

- **Not a live census.** The shoal is refreshed every **2 seconds**, and only when what it says has actually changed. It is ambient scenery, not a HUD.
- **Not a fish finder.** No names, no stock percentages, no biting-now list — see [Tools](tools.md#fish-finder) for those.
- **Not a promise of a bite.** A fish you can see still has to want what you are offering.

## See also

- [Water and conditions](water-and-conditions.md) — what decides which species live here
- [Fishing mechanics](fishing-mechanics.md#the-bite-engine) · [Species](species.md)
- [Stocking](stocking.md) · [Tools](tools.md#fish-finder)
