# Electrofisher

The one piece of River Fishing gear that is not tackle. The **Electrofisher** changes what a body of water holds: it removes a species permanently, for everyone, whether or not anyone was fishing for it — and it puts in one that was never there. It is a tool for whoever runs the world, and a survival player will never hold one.

Real electrofishing does exactly this job: a survey crew stuns a stretch of river, takes out what does not belong and releases what does. The name is not a joke.

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
| Any water within 24 blocks | The selection screen opens |
| No water within 24 blocks | *"No water to cast into"* |
| Anything, in survival or adventure | *"The electrofisher only works in creative"* |

Water with nothing living in it used to be an early exit with a *"Nothing lives in this water"* message. It is now the case the tool is most useful in, so it opens the same screen as anywhere else.

## The screen

The window is titled **"Electrofisher: this water's fish"**, with the coordinates of the water block it measured from underneath — *"Water at X, Y, Z"*.

It has two columns.

**Left — the families.** Eight rows: **This water**, then Carp family, Predators, Salmon & trout, Sturgeons, Koi, Sea fish, Big game. Each shows *how many of that family live here* out of how many exist, so `3/22` on Carp family means the water holds three of the twenty-two. The first row is not a family: it is what was living here when the screen opened, best environment fit first — the same list, in the same order, that the Fish Finder prints, because it is the same function asked the same question.

**Right — the fish of the selected family**, and a coloured strip on each row saying where it stands:

| Strip | State | What a click does |
|---|---|---|
| Green | Lives here — native, settled, or a transplant still holding on | Removes it |
| Red, struck through | An operator removed it | Puts it back |
| Grey | Not here | Puts it in |

**Two clicks, never one.**

1. The first click selects the row and turns it into a question — *click again to remove* or *click again to put in* appears on the right in yellow.
2. The second click on that same row does it.

Clicking anywhere else, changing family, or turning the page cancels the pending confirmation. A mis-click that empties a lake is not a mistake the tool lets you make.

Long families are paged — up to **14 rows** to a window, with arrows at either end of the list and the mouse wheel turning whole pages.

When it fires, a thunder crack sounds at the water, forty electric-spark particles come off the surface, and chat says one of:

> *Roach no longer lives in this water*
> *Roach now lives in this water*

## Any fish, any water

There is no habitat check. It is an admin item, so a marlin goes into a pond if that is what you want.

The first cut greyed out species whose habitat gates a release would fail, and that was wrong twice over. Gates are not what an operator tool is for — and the engine never needed one anyway: [§stocked-survival](stocking.md#living-outside-your-element) already keeps a stocked species at **a quarter of full activity** however badly the water suits it. The marlin in the pond was always catchable. The grey-out blocked something that worked.

That quarter is the whole cost. A fish in the water it belongs in is scored on season, hour, weather and distance like any other; a fish outside its element ignores all of that and sits at 0.25 — present, catchable, never comfortable.

## The scope is the region, not the pond

> **Read this before you click twice.** A "water" here is a **~128-block region**, not the pond you are looking at.

The region is the same one [stocking](stocking.md#regions-and-chunks) uses for residency, derived from the block position:

```
region = (x >> 7, z >> 7)
```

Clear roach off one bank and they are gone from the whole region. A river with several bays is one water. A pond and the lake three hundred blocks away are two. The screen prints the coordinates the region was measured from precisely so an operator who thinks they cleared one pond can find out now, not later.

The same is true in the other direction: a species you put in is in the whole region, not in the bay you clicked.

## What the ban actually does

The removal is a single flag, checked in a single place: the community factor that answers *"does this species live here?"*. Everything that asks that question routes through it, so the systems below cannot drift apart.

| System | Effect |
|---|---|
| Bite engine | The species can no longer bite. Its environment score is zero. |
| The fish you can see swimming | It stops being drawn in that water. |
| Fish Finder / Ichthyologist's Tablet | It stops being listed. |
| [Stocking](stocking.md) | It will no longer settle there. |
| [Bait Trap](blocks.md#bait-trap) | It stops turning up as live bait. |

Removing a species also strikes it out of that region's **residency book** — if it was a settled transplant, it is not settled there any more.

Putting one in is the same book written the other way: the species becomes a **permanent resident** of the region, at the settled 150 % population floor, and any ban on it is lifted in the same act. There is deliberately no separate "un-ban" — restoring a fish you removed and introducing one that was never here are the same thing, so they are one operation.

What the electrofisher does **not** touch is the per-chunk stock and [depletion](water-and-conditions.md#spot-depletion) numbers. Stock banked by earlier releases stays on the books exactly as it was.

## Undoing it

Both directions undo in the same place they were done.

A removed fish stays in the list — struck through, in its family and in **This water** — and two more clicks put it back. This is the difference from 0.7.0, where the list was built only from what could be caught in that water: a banned species could not be, so its row vanished the moment you closed the screen and the undo the tool advertised was unreachable. The list is now every species, so the row is always there.

The way back in the world still works too: **release a fish of that species into that water**. A fish you can see swimming has to be catchable, so a release lifts the ban outright.

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
- A fish you put in arrives as a **settled resident**, immediately and at full strength. There is no stocking effort, no roll, no ramp — everything [stocking](stocking.md) makes you earn, this hands over. That is the point of an operator tool, and it is also why it is creative-only.
- It gives players **no signal**. A species that has been removed simply stops appearing — the fish finder does not say *"banned"*, and nothing in the journal records it. A species that was added looks exactly like one the seed put there.
- There is no list of what you have changed. Point the tool at the water to read the current state; that state is the only record.

Used with that in mind it does the job it was asked for: decide what lives in one water.

## See also

- [Stocking](stocking.md) — how species get into a water the honest way
- [Species](species.md#families) — the seven families the screen is laid out by
- [Water and conditions](water-and-conditions.md#every-water-is-its-own) — how the seed decides what lives where
- [Tools](tools.md#ichthyologists-tablet) — the other operator-only tool
