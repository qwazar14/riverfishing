# The Keepnet

A keepnet is one inventory slot that swallows a day's catch. It is not a chest: a fish takes up room **in the shape it actually is**, so a metre of pike lies across the net as a long strip and a bream of the same weight sits as a short fat block. Room in the net is something you solve, not something you fill.

Four sizes — **Small**, **Medium**, **Large** and **Huge Keepnet** — each crafted from the one below.

## Why you want one

Every catch carries its own weight and length, so **no two fish stack**. An hour on the bank is an hour of your inventory filling one slot per fish, and the twentieth roach costs exactly as much room as the catfish. The keepnet takes them all into a single slot and packs them by shape.

That is the trade the item makes: unlimited *slots* become a finite *area* you have to think about. Which fish to keep and which to throw back becomes a real question again.

## The four sizes

| Keepnet | Grid | Cells | Crafted from |
|---|---|---|---|
| Small Keepnet | 5 × 3 | 15 | — |
| Medium Keepnet | 7 × 4 | 28 | Small |
| Large Keepnet | 8 × 5 | 40 | Medium |
| Huge Keepnet | 9 × 6 | 54 | Large |

Every cell is water. An earlier version reserved an edge column for bait and groundbait; it was dropped, so the whole grid is catch. Each net stacks to one and holds its contents in its own NBT — the fish travel with the item.

## What may go in

**This mod's fish, and nothing else.** No tackle, no blocks, no food, no fish from other mods. Tackle has [a box of its own](tackle-box.md).

## Shape decides the room

The footprint of a fish is worked out from the length and weight *that specimen* was caught at — the two numbers already on its tooltip. Nothing is authored per species, so it falls out of the fish's own proportions.

```
long axis  = ceil(length_cm / 25)                  capped at 7 cells
K          = 100 × weight_g / length_cm³           Fulton's condition factor
short axis = round(long axis × K / 1.6)            capped at 4 cells,
                                                   and at (long axis − 1) from 3 cells long
taper      = a fish ≥ 3 cells wide AND ≥ 4 long loses its four corner cells
```

One cell is 25 cm of fish. `K` is the number a real angler's club uses to say how deep-bodied a fish is: an eel runs about 0.2, a pike about 0.7, a bream or a flounder over 2. So at the same length a carp is broader than an asp, and the grid shows it. A fish is never as wide as it is long, and the biggest footprint anything can reach is 7 × 4.

Worked examples, from the mod's own shape check:

| Specimen | K | Footprint | Cells used |
|---|---|---|---|
| Perch, 25 cm, 250 g | 1.6 | 1 × 1 | 1 |
| Bream, 40 cm, 900 g | 1.41 | 2 × 2 | 4 |
| Flounder, 44 cm, 2200 g | 2.58 | 2 × 2 | 4 |
| Pike, 67 cm, 2000 g | 0.67 | 3 × 1 | 3 |
| Carp, 60 cm, 3500 g | 1.62 | 3 × 2 | 6 |
| Eel, 80 cm, 1000 g | 0.20 | 4 × 1 | 4 |
| Catfish, 150 cm, 30 kg | 0.89 | 6 × 3 | 14 (tapered) |
| Blue marlin, 300 cm, 200 kg | 0.74 | 7 × 3 | 17 (tapered) |

The tapered corners are genuinely free space — another fish's nose can sit in them, and the `% full` figure counts only cells a fish really occupies.

## Using it

Right-click a keepnet **held in your hand** to open it. That is the only way in: no hotbar shortcut, no opening it mid-fight.

| Action | What happens |
|---|---|
| Click a cell, empty cursor | Takes whatever covers that cell onto the cursor |
| Click a cell, fish on the cursor | Puts it down there if it fits |
| `R` | Turns the fish on the cursor a quarter turn (two orientations) |
| Shift-click a fish in your inventory | Drops **one** fish into the best free spot |
| **Tidy** | Repacks the whole net from scratch, biggest and most awkward first |
| **Empty** | Tips the entire catch into your inventory |

Hovering a cell with a fish on the cursor ghosts the fish into the cells it would take, turned as it would land: **green** means it fits, **red** means it does not. Hovering a fish already in the net names it, because *which one do I throw back* is the question.

**Tidy** is worth pressing before you give up on a net: the room you think you have lost to awkward gaps is usually still there. Anything that genuinely no longer fits after a repack goes to your inventory, and to the ground if your inventory is full — never nowhere. **Empty** does the same with the whole catch.

Shift-click moves one fish per click on purpose: each fish is its own decision.

The tooltip reads `Grid: W x H`, and once there is something inside, `Holds N, M% full`.

## Crafting

Shaped, `·` for an empty cell. Each size eats the one below it, so the chain is small → medium → large → huge.

| Result | Pattern | Key |
|---|---|---|
| Small Keepnet | `S·S / S·S / TST` | S = String, T = Stick |
| Medium Keepnet | `SSS / SCS / TST` | C = Small Keepnet |
| Large Keepnet | `SSS / ICI / SSS` | I = Iron Ingot, C = Medium Keepnet |
| Huge Keepnet | `SIS / ICI / SIS` | C = Large Keepnet |

Building a Huge Keepnet from nothing therefore costs **21 String, 4 Sticks and 6 Iron Ingots** in total.

## Buying one

The [fisherman](villager.md) sells all four, one per trade:

| Tier | Offer | Price |
|---|---|---|
| 3 Journeyman | Small Keepnet | 5 emeralds |
| 4 Expert | Medium / Large Keepnet **(rotating)** | 9 / 14 emeralds |
| 5 Master | Huge Keepnet | 20 emeralds |

## What it does *not* do

- **Nothing happens to a fish in the net over time.** No spoiling, no condition loss, no weight drift — a fish comes out exactly as it went in.
- **It is never placed.** The keepnet is an item, not a block; it works in your hand and only there.
- **Nothing else in the mod reads it.** A fish inside the net is not in your inventory, so you cannot sell it to the fisherman, cook it or mount it until you take it out.

The journal's **Guides** tab carries a short version of this page in game, under *The keepnet: shape decides the room*.

## See also

- [Species reference](species-reference.md) — the lengths and weights that decide a footprint
- [Villager](villager.md) — who sells the nets, and who buys the fish
- [Crafting](crafting.md) · [Progression](progression.md)
