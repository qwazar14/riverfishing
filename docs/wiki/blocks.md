# Blocks

Nine blocks plus three crops. Everything with contents shows its counts through **Jade** if you have it installed.

| Block | Item id | Recipe |
|---|---|---|
| Fishing Stall | `fishing_stall` | Barrel + 2 × String + Iron Ingot |
| Rod Pod (1 slot) | `rod_pod_1` | 4 × Stick |
| Rod Pod (3 slots) | `rod_pod_3` | 5 × Iron Ingot + 2 × Iron Nugget |
| Bait Trap | `bait_trap` | 6 × Stick + 1 × String |
| Worm Farm | `worm_farm` | 8 × Planks + 1 × Dirt |
| Maggot Farm | `maggot_farm` | 8 × Planks + 1 × Rotten Flesh |
| Aquarium | `aquarium` | 2 × Glass + Water Bucket + 2 × Gravel + Kelp + 2 × Planks + Sign |
| Mini Aquarium | `trophy_stand` | **No recipe** |
| Drilled Ice Hole | `ice_hole` | Made with the [Ice Auger](tools.md#ice-auger) |
| Corn / Peas / Barley | crops | Planted from their seeds |

---

## Fishing Stall

Two jobs in one block:

- It is the [Fisherman](villager.md) villager's **job site** (a registered point of interest, in `minecraft:acquirable_job_site`).
- Right-click it with an empty hand and it is the [**Tackle Station**](tackle-station.md).

Materials left in the bench live in the block and **drop when it is broken**.

---

## Rod Pods

A rod pod holds cast **Bottom**-flow rods so their lines stay in the water while you do something else. Two sizes: 1 slot and 3 slots. The pod faces the way you were looking when you placed it, so the rods point at the water.

### Docking a rod

1. Cast the rod normally — you need a line already in the water. Without one: *"Cast the rig first"*.
2. Right-click the pod with the cast rod in hand: *"Rod on the pod"*.

Only Feeder, Bottom, Carp, Surf and Boat rods can be docked. Anything else: *"Only bottom rods go on the pod"*. No free slots: *"No free pod slots"*.

### Taking it back

Right-click with an **empty hand**. The pod hands you a *biting* rod first if there is one, otherwise the first occupied slot. Whatever was in your main hand goes to your inventory.

- Grab a rod inside its bite window and the fight starts immediately.
- If the fish had **hooked itself**, you also get *"The fish hooked itself — bring it in!"*
- On a false alarm: *"Nothing — false alarm"*.
- Otherwise: *"Took the rod"*.

### What a podded line does

Each podded line renders in one of three visible states, so you can read the pod from across the camp:

| State | Look |
|---|---|
| Waiting | Taut |
| Bite window open | Taut and twitching |
| A real bite was missed | **Slack sag** — reel in and recast |

In the last ~10 seconds before a real take, the fish **nibbles**: faint water ticks at the rig and, with an alarm mounted, a barely-there stir of it. Spotting the shift from a nibble to a real bite is the whole thing.

When the bite fires:

- **40 %** of real bites **hook themselves** against the rod's weight, giving you a full **30-second** window to get there.
- The other 60 % give a wide **8–15 second** reaction window.
- Miss a real bite entirely and the bait is gone: the line goes slack and needs recasting.

Podded lines also [re-read the world](fishing-mechanics.md#live-re-evaluation) every 15 seconds, exactly like a held line. (That context is not saved to disk, so after a chunk reload a podded line falls back to the wait it was given at the cast.)

Breaking a pod drops every docked rod and every mounted alarm.

> The pod's own code comments mention a 5-slot variant; only the **1-slot** and **3-slot** pods actually exist.

---

## Bite alarms

Alarms mount **on the pod**, not on the rod — right-click the pod holding one. It goes to the first occupied slot without an alarm, or to any free slot: *"Alarm attached"*. If every slot already has one: *"Need a rod on the pod without an alarm"*.

| Alarm | Recipe | Audible range | False alarms |
|---|---|---|---|
| **Bite Alarm (Bell)** | 3 × Gold Ingot + 1 × Iron Nugget | ~16 blocks | **High** — 0.0008 per tick |
| **Digital Bite Alarm** | Glass Pane + 2 × Iron Ingot + Bite Alarm (Bell) + Redstone + Flint + Lapis Lazuli | ~32 blocks | **Very low** — 0.00004 per tick |

The digital alarm is also sold by a master [fisherman](villager.md) for 10 emeralds.

Both keep signalling for the **whole** bite window, not just the first moment:

- The **bell** showers note particles every 5 ticks and rings every 20.
- The **digital** flashes redstone particles every 4 ticks and beeps every 8.

A false alarm has a shorter window (2–3.5 s) and resets itself if you ignore it — the line keeps waiting.

**Without an alarm a bite makes no sound at all.** Only water movement at the rig gives it away. The false-alarm rate is scaled by the difficulty preset's phantom multiplier.

> The digital alarm's internal description claims it also sends a HUD alert. It does not — in the current build both alarms are sound and particles only.

---

## Bait Trap

A "малявочник" — a net you stand in the water. It is a waterloggable block, so the water stays inside the net.

It works as long as it is waterlogged **or** has water below or on any of its four sides.

- Every **2 to 4 minutes** it catches something.
- **35 %** of catches are a real **small fish** (up to 150 g) drawn from the species that actually live in the water at the trap — a proper catch with its own weight and length, capped at 4 stored.
- The rest are fry, stored as **Live Bait**, capped at 12.
- Right-click with anything to collect everything at once. Empty: *"The trap is empty for now"*.

### Feeding the trap

Right-click it with **groundbait** and it runs at **double speed**: *"Groundbait poured in — fish will come twice as fast"*. Each groundbait adds 4 charges, one charge is spent per catch, and the cap is 12 charges. Already full: *"The trap is already fully baited"*.

The fisherman sells traps at apprentice tier (3 emeralds), and one is the reward for the *Catch 10 fish* quest.

---

## Worm Farm

A composter for worms. It needs **soil directly below it** (any block in the `dirt` tag) to work.

- Right-click with **anything compostable** (the vanilla composter list) to raise the heap one level, up to 4. The heap is visible.
- Every **2 to 4 minutes** the worms eat through one level: the heap visibly sinks and **3 worms** are added, up to a stock of **24**.
- Right-click with an empty hand (or anything non-compostable) to collect. Nothing yet: *"No worms bred yet"*.

Sold by the fisherman at novice tier for 4 emeralds — the intended early exit from digging dirt by hand.

---

## Maggot Farm

The same idea with **Rotten Flesh**.

- Right-click with rotten flesh to load **one piece per click**, up to **16**. The heap rises a visible layer every 4 pieces. Progress reads *"Rotten flesh loaded: N/16"*; when full, *"The farm is packed full of flesh"*.
- Roughly **every 45–90 seconds** one piece hatches into **4 maggots** and the heap sinks. Stock caps at **64**.
- Right-click with anything but flesh to collect. Nothing yet: *"No maggots bred yet"*.

Sold at expert tier for 5 emeralds.

> **Note:** breaking either farm returns the block, but **not** what was already growing inside it. The bred worms and maggots live as a counter on the block, not as items, so harvest before you move.

---

## Mini Aquarium

Despite the item id `trophy_stand`, this is a desktop tank: a small pedestal with a glass box on top.

- Holds up to **5 fish**, each **150 g or lighter**. Too heavy: *"Too big — the mini aquarium takes small fish up to 150 g"*. Full: *"The aquarium already holds five fish"*.
- Right-click with a fish to add it, empty-handed to take the last one back out.
- It faces the player who placed it, so the fish look back at you.
- Breaking it pops every fish inside.

> The Mini Aquarium has **no crafting recipe** in the current build — it is only obtainable in creative or by command. It does have a loot table, so once placed it can be recovered.

---

## Aquarium

A proper display tank: **2 blocks wide × 2 tall × 1 deep**, glass on top, wooden base with a nameplate below. All four cells place and break together, and it fails to place if there is not room for all four.

- Holds up to **3 fish** of **any size**.
- Right-click with a fish to add, empty-handed to remove the last one.
- The bottom-left cell is the master and holds the contents; break any cell and the whole thing comes down, dropping one aquarium plus every fish inside.

---

## Drilled Ice Hole

Made by an [Ice Auger](tools.md#ice-auger) from ice sitting on water, and the only way to [ice fish](ice-fishing.md).

It is a full copy of vanilla ice, so it is slippery, melts in bright light, and leaves water rather than a hole in the lake when broken. It also **refreezes** — a 25 % chance per random tick, roughly every 5 minutes, but only in a cold enough biome and only when no player is within 4 blocks.

Right-click it with an assembled **Winter Rod** to fish. Anything else: *"Needs an assembled winter rod"*.

---

## Bait crops

Three farmland crops for the plant baits. They behave exactly like vanilla wheat — bonemeal, random ticks, farmland requirement — with four visual growth stages.

| Crop | Seed | Mature harvest |
|---|---|---|
| Corn | Corn Seeds | 1–3 × Corn + 1–2 × Corn Seeds |
| Peas | Pea Seeds | 1–3 × Pea + 1–2 × Pea Seeds |
| Barley | Barley Seeds | 1–2 × **Pearl Barley** + 1–2 × Barley Seeds |

Barley yields Pearl Barley directly — there is no threshing step.

Break an immature crop and you just get the seed back.

**Getting seeds:** each of the three drops from **grass and tall grass** at 5 %, exactly like vanilla wheat seeds but a little rarer. The [fisherman](villager.md) also sells 3 seeds of a rotating type for 1 emerald at novice tier.

All three crops are registered into **Serene Seasons**' spring, summer and autumn crop tags, so they respect the seasons if that mod is installed.

---

## Jade integration

With Jade installed, looking at these blocks shows their contents without opening anything:

| Block | Line |
|---|---|
| Worm Farm | *Worms: N* |
| Maggot Farm | *Maggots: N (flesh: M)* |
| Bait Trap | *Livebait in trap: N* |
| Rod Pod | *Rods mounted: N* |
| Aquarium | *Fish: N/3* |

## See also

- [Tools](tools.md) · [Tackle Station](tackle-station.md) · [Villager](villager.md)
- [Tackle box](tackle-box.md) — a block that also opens right there in your hand
- [Rigs and baits](rigs-and-baits.md) · [Ice fishing](ice-fishing.md)
- [Crafting](crafting.md)
