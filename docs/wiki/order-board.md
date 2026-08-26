# The Order Board

One species per Minecraft day is the fisherman's **order of the day**. The **Order Board** is the journal panel that writes that order out as the *recipe for catching it* — water, depth, season, hour, bait, rig and rod — and ticks every condition you already meet where you are standing.

Nothing on the panel is written by hand. Every line is read out of the species' own profile, the same one the [bite engine](fishing-mechanics.md) reads, so the board cannot teach you something the engine does not do.

> New in **0.7.0**. What the order *pays* — the ×2.5 and the seat it takes on every counter — is on [The Fisherman](villager.md#the-order-of-the-day).

## Opening it

Craft the **Fishing Journal** — Book + Hook No.12 + Leather, shapeless — right-click it, and open the **Quests** tab. The board sits at the top, above the quest chain.

The order itself is announced in chat once per Minecraft day, whichever tab you are on:

> *Fisherman's order of the day: Pike — pays ×2.5. The journal's quest page has the full recipe.*

The panel is built by the **server** at the moment you open the journal, and it is sent as translation keys rather than sentences — so it draws in your own language and works on a multiplayer client, which has no fish profiles at all.

> **The ticks are a snapshot, not a HUD.** They are measured when the journal opens. Walk to another swim, change a bait, and reopen the journal to see the marks move. This is a book you consult, not an overlay.

## What each line means

Each row is marked **✔** when you meet it, **☐** when you do not, and **-** when it states a fact rather than sets a condition.

| Line | What it lists | Ticked when |
|---|---|---|
| **Water** | every water body type the species scores above zero in | you are over water of one of those types |
| **Depth, blocks** | the habitat gate, as `min–max` or `≥ min` | the measured depth there is inside the band |
| **Season** | only the seasons it feeds *best* in | the current season is one of them |
| **Hour** | only its *best* daily windows | the current hour is one of them |
| **Bait** | the three baits it rates highest | your rig carries a bait this species scores at all |
| **Rig** | the rig types it expects | that rig is on your rod |
| **Rod** | the rod types it expects | you are holding one |
| **Angler level** | its `min_angler_level`, and only if it has one | your [angler level](progression.md#what-your-level-unlocks) is at least that |
| **Bought by a fisherman of level** | the trade tier that buys this species | never — it is marked **-** |

A few details worth knowing, because they change how a line reads:

- **Where you are standing** is the nearest water surface within **8 blocks** of you, and only if it is within 6 blocks of your own height. Away from water, the Water and Depth rows simply stay unticked.
- **Season and Hour list the peak, not the possible.** The engine scores every season and every part of the day; the board prints only the top-scoring entries. You can catch the fish outside them — just more slowly.
- **Season needs [Serene Seasons](water-and-conditions.md).** Without that mod the engine has no season to read and treats everyone the same, so the row is ticked for you.
- **The Bait row is wider than it looks.** It shows the top three, but the tick is granted for *any* bait on your rig this species will take.
- **Rod and rig** are read off the rod in your main hand, or your off hand if the main hand is not a rod.
- **The buyer line is information.** It tells you which fisherman tier takes this fish, so you know before you go whether the stall you know is senior enough. See [the order slot](villager.md#the-order-slot).

## Filling the order

An order is filled the moment you **land a prime specimen** of the day's species — not when you sell it. Prime means at or above 70 % of the species' maximum weight; a foul-hooked fish is never graded and never counts. The rule is the same one the counter uses, and it is written out under [the prime-fish rule](villager.md#the-prime-fish-rule).

> *Order filled — that is 7 of them.*

**One order a day.** The credit is stamped with the Minecraft day, so a second prime specimen of the same species that day adds nothing to the count. The day's ×2.5 at the stall does not care — that is per fish, and it is a separate thing entirely.

## The milestone ladder

Under the checklist the panel shows **Orders filled: N** and six item icons, each with the order number it unlocks at. A rung you have not reached yet is greyed over.

Every fifth filled order pays out, from a fixed ladder:

| Filled orders | Reward |
|---|---|
| 5 | Fish Finder |
| 10 | Reel 6000 |
| 15 | Feeder Rig |
| 20 | Digital Bite Alarm |
| 25 | Carp Rod |
| 30 | Reel 10000 |

The prize goes straight into your inventory, or drops at your feet if there is no room, and the board says so in chat:

> *Order 15: the board pays out — Feeder Rig*

The ladder is deliberately the gear a growing angler is about to want: something to read the water with, a reel that holds a proper fish, the rig half the species list asks for, an alarm so two rods can sit at once, then a real blank and the drag to go under it. It ends at thirty. The counter keeps counting after that — there is simply nothing further to hand out.

## See also

- [The Fisherman](villager.md) — the market, the ×2.5 and the daily order slot
- [Progression](progression.md) — the journal, angler level and the quest chain
- [Water and conditions](water-and-conditions.md) — the water types, depths, seasons and hours the rows name
- [Rigs, hooks and baits](rigs-and-baits.md) · [Species](species.md)
