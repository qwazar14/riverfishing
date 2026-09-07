# Fly fishing

Fly fishing is its own loop. Spinning is a rhythm, float and bottom are a wait, the fight is a tug-of-war — a fly is **timing** (the cast), **reading** (what is hatching) and **restraint** (the set). The fly weighs nothing; the line carries it, the cast is short and precise, and the fish takes on the surface where you can see it.

## What you need

| Item | How to get |
|---|---|
| **Fly Rod** | 3 × Bamboo on the diagonal + 1 × String + 1 × Iron Nugget |
| A **reel** | Any of the small reels, 1000–2000 — on a fly rod it is a drag, not a winch |
| A **line** | Any. The rig's own leader is the tippet |
| **Fly Rig** | Built into the Fly Rod — a leader slot and a fly slot |
| A **fly** | Any lure you tied on the [Tackle Station's Tie page](tackle-station.md). The fly slot takes nothing else |

The fly rod is a Float-class rod with a reel: the wait-for-the-bite flow, the strike bar, then the fight. The same tied lure is a mormyshka on the winter rod and a fly here.

## The cast is a rhythm

Hold use with no line out and the rod goes back: a needle sweeps a bar, and the two ends of the sweep are the two stops of a fly cast — the stop of the backcast, the stop of the forward cast.

- **Left-click on each end.** A tap with the needle near an end that has not been tapped this sweep is a **good beat**: one false cast, one more block of line in the air. A tap anywhere else, or on the same end twice, is a **bad beat**: the loop opens and the count goes back to zero.
- **Release use to deliver.** Released on a beat with the loop still closed = a **tight loop**: full distance, a soft landing. Released off the beat, or with the loop open = a **splash**: 60 % of the distance, and the fish there are spooked for about five seconds. Released well past an end = a **wind knot**: the splash plus line wear.
- **Distance is aim.** Six blocks of line to start, plus a block per good beat, up to the rod's 18; the fly lands where your crosshair meets the water at that distance.

In this version the backcast needs no room behind you — trees on the bank do not catch the line yet, and there is no roll cast.

## The drift

The fly lands and the drift begins; it lasts twelve seconds. On flowing water the fly moves with the current a block every half second; on still water it sits.

- **Drag.** The current bows the line and drags the fly, and a dragging fly is refused. The tension slot shows it: on flowing water it climbs three points every half second, and over 60 the bite is pushed away while it stays there. On still water there is no drag.
- **Mend — right-click (use).** Flips the line upstream and resets the drag to zero. A third mend in one drift spooks the water.
- **Line straight below you.** When the drift runs out the line comes tight downstream of you and catches nothing: *"Line's straight below you — pick up and cast again"*.
- **Pick up — sneak + use.** Ends the drift so you can cast again.

## The rise and the set

A fish takes a fly by coming up under it and turning down — and a trout that feels the hook before it has turned spits the fly. So the strike bar on a fly rod is not the float's random zone: **it is a clock**. The marker climbs once from left to right across the window, and the green is the species' own delay.

- The **rise is drawn**: the fish comes up under the fly nose-first over the first half second, with a ring on the water.
- **Lift (use) before the green** — *"Too fast — pulled it out of its mouth"*. **After it** — *"Too slow — it spat the fly"*. **Inside** — hooked, into the fight. The orange either side of the green still hooks one time in four.
- **Every miss puts the fish down**: the spot is spooked for twenty seconds or so.

The delay comes from the species' fight aggression (0–1), in ticks:

| | Formula | An aggressive fish (0.8) | A slow taker (0.3) |
|---|---|---|---|
| Green opens | `4 + (1 − aggression) × 8` | 6 ticks (0.3 s) | 10 ticks (0.5 s) |
| Green closes | `open + 8 + (1 − aggression) × 6` | 15 ticks (0.75 s) | 22 ticks (1.1 s) |
| Window | `close + 6` | 21 ticks | 28 ticks |

The FINESSE skill widens the green here exactly as it does on the float.

## The hatch

Insects hatch by season and hour, and a feeding fish takes what is hatching. When a hatch is on, the water shows it while your fly drifts: motes over the surface within six blocks of the fly and, now and then, a rise ring from a fish that is not yours.

| Hatch | When | Kind | Size |
|---|---|---|---|
| Midge | Winter, any hour; spring dawn | dry fly | 4 mm |
| Stonefly | Spring, day | nymph | 14 mm |
| Mayfly | Spring dusk; summer dawn | dry fly | 10 mm |
| Caddis | Summer, dusk | dry fly | 8 mm |
| Terrestrial | Summer, day, clear weather only | ant | 8 mm |
| Scud | Still water (lake, pond, swamp) whenever nothing else is up | shrimp | 8 mm |
| Baitfish | Autumn, any hour; any thunderstorm | streamer | 14 mm |

The fly's size is the longer side of your drawing — one pixel is one millimetre.

| Your fly | Factor |
|---|---|
| Right kind, within 3 mm of the hatch | **× 1.5** |
| Right kind, wrong size | × 1.0 |
| Wrong kind | × 0.6 |
| Nothing hatching — dry fly | × 0.7 |
| Nothing hatching — streamer | × 0.9 |
| Nothing hatching — anything else | × 1.0 |

Nymphs catch most fish most of the time, which is true on the river as well. The factor multiplies the tied lure's own family affinity from the [Tie page](tackle-station.md) — the stencil still decides who the fly is food to.

## Which fish

There is no fly table per species in this version: a fly is scored the way every tied lure is, by its stencil's family table, then by the hatch.

| Stencil | Cyprinids | Predators | Salmonids | Sea |
|---|---|---|---|---|
| Dry fly | 1.00 | 0.40 | **1.25** | 0.40 |
| Nymph | 1.15 | 0.65 | **1.20** | 0.55 |
| Ant | 1.15 | 0.60 | 1.15 | 0.50 |
| Shrimp | 0.80 | 1.05 | 0.90 | **1.15** |
| Streamer | 0.50 | **1.20** | 1.00 | 1.05 |

So trout, grayling, char and whitefish are the dry-fly and nymph fish; chub, ide, dace and rudd take a dry fly nearly as well; pike, perch and asp want the streamer; the sea fish want a shrimp or a streamer. Rods still count in the match score — a fish that lists another rod as ideal costs you on the rod component, as always.

## See also

- [Tackle Station](tackle-station.md) · [Rods](rods.md) · [Reels and lines](reels-and-lines.md)
- [Fishing mechanics](fishing-mechanics.md) · [Water and conditions](water-and-conditions.md#seasons)
- [Ice fishing](ice-fishing.md) — the same tied lure, through a hole
