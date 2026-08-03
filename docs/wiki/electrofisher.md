# Electrofisher

The one piece of River Fishing gear that is not tackle. The **Electrofisher** removes a species from a body of water — permanently, for everyone, whether or not anyone was fishing for it. It is a tool for whoever runs the world, and a survival player will never hold one.

Real electrofishing does exactly this job: a survey crew stuns a stretch of river and takes out what does not belong. The name is not a joke.

## Getting one

| Item | Item id | Recipe |
|---|---|---|
| Electrofisher | `electro_rod` | **No recipe** — operator tool |

- There is **no recipe anywhere** in the mod's data, and no villager trade, loot table or quest reward gives one. Creative menu or `/give` only.
- It appears in the River Fishing creative tab like every other item.
- Stacks to 1. **No durability, no cooldown, no power, no cost of any kind** — it never wears out and never runs down.
- Holding it in survival is harmless. Using it is refused:

> *The electrofisher only works in creative*

That check runs on the item **and** again on the server when you confirm, so a client that lies about game mode changes nothing.

## Using it

Right-click while aiming at water. You may also aim at the bank, or at the bottom through shallow water — the tool traces **24 blocks** along your view and resolves the water either way. It is the same ray-cast the [Fish Finder](tools.md#fish-finder) uses, so both tools always agree about which lake you just clicked.

| What you aimed at | What happens |
|---|---|
| Water with species in it | The selection screen opens |
| No water within 24 blocks | *"No water to cast into"* |
| Water where nothing can be caught | *"Nothing lives in this water"* |
| Anything, in survival or adventure | *"The electrofisher only works in creative"* |

## The screen

The window is titled **"Electrofisher: what to remove"**, with the coordinates of the water block it measured from underneath — *"Water at X, Y, Z"*.

The rows are **every species that can be caught in that water right now**, best environment fit first. It is the same list, in the same order, that the Fish Finder prints, because it is the same function asked the same question.

**Two clicks, never one.**

1. The first click selects the row and turns it into a question — *click again to remove* appears on the right in yellow.
2. The second click on that same row does it.

Clicking anywhere else, or turning the page, cancels the pending confirmation. A mis-click that empties a lake is not a mistake the tool lets you make.

Long lists are paged — up to **14 rows** to a window, with arrows at either end of the list and the mouse wheel turning whole pages.

When it fires, a thunder crack sounds at the water, forty electric-spark particles come off the surface, and chat says:

> *Roach no longer lives in this water*

## The scope is the region, not the pond

> **Read this before you click twice.** A "water" here is a **~128-block region**, not the pond you are looking at.

The region is the same one [stocking](stocking.md#regions-and-chunks) uses for residency, derived from the block position:

```
region = (x >> 7, z >> 7)
```

Clear roach off one bank and they are gone from the whole region. A river with several bays is one water. A pond and the lake three hundred blocks away are two. The screen prints the coordinates the region was measured from precisely so an operator who thinks they cleared one pond can find out now, not later.

## What the ban actually does

The removal is a single flag, checked in a single place: the community factor that answers *"does this species live here?"*. Everything that asks that question routes through it, so the four systems below cannot drift apart.

| System | Effect |
|---|---|
| Bite engine | The species can no longer bite. Its environment score is zero. |
| The fish you can see swimming | It stops being drawn in that water. |
| Fish Finder / Ichthyologist's Tablet | It stops being listed. |
| [Stocking](stocking.md) | It will no longer settle there. |
| [Bait Trap](blocks.md#bait-trap) | It stops turning up as live bait. |

Removing a species also strikes it out of that region's **residency book** — if it was a settled transplant, it is not settled there any more.

What the electrofisher does **not** touch is the per-chunk stock and [depletion](water-and-conditions.md#spot-depletion) numbers. Stock banked by earlier releases stays on the books exactly as it was.

## Undoing it

**While the screen is still open**, a removed row goes struck-through and its confirmation flips to *click again to restore*. Two more clicks put the species back, and chat says *"Roach lives here again"*.

**Once you close the screen, the row is gone.** The list is built from what can be caught in that water, and a banned species cannot be — so it has nothing to show. There is no row left to click.

The way back after that is in the world rather than in the menu: **release a fish of that species into that water**. A fish you can see swimming has to be catchable, so:

- If the species is not a resident of the region, a successful [settle roll](stocking.md#settling) lifts the ban outright and makes it a permanent resident again.
- Otherwise the release still banks stock, which is enough to make the species weakly catchable again (at the quarter-strength [stocked-survival](stocking.md#living-outside-your-element) floor) and to bring its row back into the electrofisher's list — struck through, and two clicks from being properly restored.

## What the server re-checks

The confirmation you clicked through lives on the client and is worth nothing on its own. When the packet arrives the server re-validates from scratch:

- the player is in creative;
- an Electrofisher is actually in their main or off hand;
- the species id is a real fish profile;
- the water is within **64 blocks** of the player.

## Where it is stored

The bans live in the overworld's `riverfishing_stocked` saved data, in a `Culled` list beside the `Regions` list the stocking book uses — the same statement about the same region, one saying *this lives here now* and the other *this does not live here any more*. Per world, and it survives restarts.

## An honest note on how blunt it is

- It is an **on/off switch for a whole region**. There is no "reduce by half", no radius, no per-chunk version.
- It can only take away. Putting a species into a water is [stocking](stocking.md)'s job, and no operator shortcut for it exists.
- It gives players **no signal**. A species that has been removed simply stops appearing — the fish finder does not say *"banned"*, and nothing in the journal records it.
- There is no list of what you have removed. The only way to see a region's bans is to point the tool at it again, and after a restart even the struck-through rows are gone (see [Undoing it](#undoing-it)).

Used with that in mind it does the job it was asked for: take the nuisance species out of one water so it stops getting in the way.

## See also

- [Stocking](stocking.md) — how species get *into* a water
- [Water and conditions](water-and-conditions.md#every-water-is-its-own) — how the seed decides what lives where
- [Tools](tools.md#ichthyologists-tablet) — the other operator-only tool
