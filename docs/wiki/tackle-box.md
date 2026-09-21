# The Tackle Box

Twelve line diameters, nine hook sizes, eleven rig types and dozens of baits, all living loose in the same nine rows as your food and your building blocks. The tackle box takes them.

It is a **block and an item at once**: one object that opens in your hand and stands on the bank. Four sizes, each a craft of the one below.

## The four sizes

| Box | Item id | Rows | Slots |
|---|---|---|---|
| Small Tackle Box | `tackle_box_small` | 1 | 9 |
| Medium Tackle Box | `tackle_box_medium` | 2 | 18 |
| Large Tackle Box | `tackle_box_large` | 3 | 27 |
| Huge Tackle Box | `tackle_box_huge` | 4 | 36 |

Rows of nine, so the grid lines up with the inventory underneath it. Boxes do not stack. The tooltip reads *"N slots — tackle only"*, plus *"N of M used"* once something is inside.

The four icons are the same box drawn bigger with more latches on it, so a Huge reads differently from a Small in the hotbar without anyone having to hover it. The placed model grows with the size too.

## Crafting

Shaped, `·` for an empty cell. There is a **Chest** at the heart of every one of them, and every size above Small is an upgrade of the size below.

| Box | Pattern | Key |
|---|---|---|
| Small Tackle Box | `III / LCL / LLL` | I = Iron Nugget, L = Leather, C = Chest |
| Medium Tackle Box | `XCX / LBL / XCX` | X = Copper Ingot, C = Chest, L = Leather, B = Small Tackle Box |
| Large Tackle Box | `XCX / LBL / XCX` | X = Iron Ingot, C = Chest, L = Leather, B = Medium Tackle Box |
| Huge Tackle Box | `XCX / LBL / XCX` | X = Iron Ingot, C = Chest, L = Leather, B = Large Tackle Box |

> **Empty the box before you upgrade it.** The three upgrade recipes are ordinary shaped recipes — they build a *new* box, and the contents, the name and the colour of the one you feed in do not come across.

## Putting it down, and opening it

The controls are the ones every other block in the game already uses, so the box needs no rule of its own in your head:

| With the box in hand | What happens |
|---|---|
| Right-click a surface | Puts it down, facing you |
| Sneak + right-click a surface | Opens it, in your hand |
| Right-click at the air | Opens it |

A box already on the ground opens on **any** right-click, empty-handed or not.

Held or placed, it is the **same object**: the block stores the box item itself rather than a copy of what is in it. Sort the box on the bank, pick it up, and it is sorted in your hand — contents, name and colour with it. Walk more than 8 blocks away from an open placed box and the screen closes.

Break a placed box and it drops with everything still inside. So does it when it is blown up, burnt, or shoved by a piston. Middle-click picks it up loaded.

> **In creative mode, breaking a placed box destroys it and everything in it.** The drop is deliberately skipped for creative players, and there is no loot table behind the block to fall back on.

> All four sizes share one collision box, 12 × 7 × 10 pixels, while the models get bigger with the size. The lid of a Huge box overhangs the part of it you can actually hit.

## What counts as tackle

Only tackle goes in, and one function decides it for every route in: the slot, shift-click and the fisherman's ready-made kits all ask the same question. Shift-clicking a non-tackle stack out of your inventory does nothing at all rather than quietly putting it somewhere else.

**In:** lines, hooks, rigs, leaders, baits and artificial lures — plus anything in the `riverfishing:tackle` item tag, which is where the **Float** lives and where a pack can add its own.

**Out:** rods, reels, fish, groundbait, and tackle boxes. Rods and reels are the things you hold, not the things you rummage for, and a box that swallowed a rod would be a backpack wearing a tackle box's name.

## Naming

The top row of the box's own screen **is** the name field — no anvil, and no walk back to the village to use one. Type in it and the name is saved as you type, up to 32 characters. Clear the field and the box goes back to its default name.

The name sits on the box item, so it shows in your hotbar and stays with the box through being put down and picked back up.

## Colour

Put the box in a crafting grid with one or more **dyes**, exactly like leather armour and exactly like [dyeing a lure](rigs-and-baits.md#dyeing-lures). The mixed colour lands on the box's inserts, and **the contents come through the craft untouched**.

The colour shows in three places: the insert stripe on the item icon, the band on the placed block, and a band behind the slots when the box is open. An undyed box is off-white.

All four are in the vanilla `minecraft:dyeable` item tag, so a water cauldron washes the colour back off.

Unlike a lure's colour, a box's colour does **nothing** to the fishing. It is navigation: four boxes with four names and four colours — *pike*, *carp*, *winter*.

## Kits from the fisherman

The [fisherman](villager.md) sells four ready-made boxes, each already named, dyed, and packed with tackle carrying the [bench's](tackle-station.md) weight stamp.

| Kit | Box | Tier | Price | Inside |
|---|---|---|---|---|
| Kit: Float Fishing | Small | 3 Journeyman | 7 emeralds | 2 × Float, 4 × Hook No.10, 4 × Hook No.12, 8 × Worm, 8 × Maggot |
| Kit: Pike | Medium | 3 Journeyman | 18 emeralds | 2 × Steel Leader, Spinner, Spoon Lure, Wobbler — 3–35 g each, random colours |
| Kit: Carp | Medium | 4 Expert | 21 emeralds | 16 × Boilie, 4 × Hook No.6, 4 × Hook No.8, 16 × Corn, Mono Line 0.30, Flat Feeder Rig 40–60 g |
| Kit: Saltwater | Large | 5 Master | 51 emeralds | 2 × Titanium Leader, Skirted Octopus Jig and Giant Spoon 100–200 g, Ledger Rig 100–200 g, 3 × Hook No.2, 3 × Hook No.4, Braided Line 0.40, 4 × Live Bait |

The float and pike kits share **one** journeyman slot: a stall offers one of the two, not both. A kit is the answer to *"what do I need for pike"* in a form you can carry.

## Box or Station?

Two different blocks doing two different jobs.

| | Tackle Box | [Tackle Station](tackle-station.md) |
|---|---|---|
| Is | Storage you carry | A bench you tie tackle on |
| Block | Its own, in four sizes | The Fishing Stall |
| Does | Holds finished tackle | Builds rigs and lures out of hooks, iron, string and dye |
| Travels | Yes — pick it up loaded | No |

Tie it at the station, carry it in the box.

## See also

- [Rigs and baits](rigs-and-baits.md) · [Tackle Station](tackle-station.md)
- [Villager](villager.md) — the four kits · [Blocks](blocks.md) · [Crafting](crafting.md)
