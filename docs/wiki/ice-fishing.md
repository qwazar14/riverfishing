# Ice fishing

Ice fishing is vertical: no casting, no aiming. You drill a hole, drop a jig straight down, and work it in a rhythm until the nod twitches.

## What you need

| Item | How to get |
|---|---|
| **Ice Auger** | 3 × Iron Ingot + 1 × Stick, or the [fisherman](villager.md) at expert tier (9 emeralds) |
| **Winter Rod** | 2 × Stick + 1 × Iron Nugget, or an assembled one from the fisherman at expert tier (14 emeralds) |
| **Winter Rig** | Built into the Winter Rod — you never craft or carry it separately |
| **Ice Jig** (mormyshka) | Gold Nugget + any hook (No.16–No.4) + 2 × String, or the fisherman at expert tier |
| A **line** | Any. The traded winter rod ships with Mono Line 0.14 |

The Winter Rod is reel-less, so it takes no reel — just a line and its built-in rig.

## Drilling a hole

Right-click a block of **Ice, Packed Ice, Blue Ice or Frosted Ice** that sits **directly on water** with the auger.

- The ice becomes a **Drilled Ice Hole**: *"Drilled a fishing hole"*.
- Costs 1 durability (the auger has 64) and puts a 15-tick cooldown on the item.
- Over anything but water you get *"No water under the ice"* and nothing happens.

A pickaxe just breaks the ice; only the auger makes a hole you can fish.

### The hole is temporary

The Drilled Ice Hole is a full copy of vanilla ice, so it behaves exactly like it — slippery underfoot, it melts back to water in bright light, and breaking it leaves water rather than a dry gap in the lake.

On top of that it **skins over**. On each random tick there is a 25 % chance it refreezes into ordinary Ice — roughly every 5 minutes on average — but only when:

- the biome is cold enough to freeze water, **and**
- no living player is within **4 blocks**.

So the hole you are standing at never freezes under you; the one you walked away from will need re-drilling.

## Fishing the hole

Right-click the hole with an **assembled Winter Rod**.

- Any other rod: *"Needs an assembled winter rod"*.
- A Winter Rod aimed at open water instead: *"The winter rod only fishes a hole — right-click drilled ice"*.
- Any *other* rod trying to cast into water that is capped by a solid ice sheet: *"Drill a hole with an ice auger"*.

The first click drops the jig; there is no power bar and no aiming. The mormyshka goes straight down and the line renders with **no float** on it.

### Winter conditions are forced

Fishing through a hole sets the bite context's season to **WINTER** regardless of the real season, and flags the cast as an ice hole. So only cold-water fish bite — everything with `winter: 0.0` is simply absent.

The wait is clamped to **200–2400 ticks** (10 seconds to 2 minutes) — a patient winter wait, but never an endless one.

### Jigging

While you are waiting, **every right-click works the jig**. The rhythm matters:

| Cadence | Message | Effect |
|---|---|---|
| A jig every **8–20 ticks** (0.4–1.0 s) — steady | *"Steady rhythm - fish are coming!"* | Pulls the bite **34 ticks closer** |
| Frantic spamming or lazy jigging | *"Jigging the mormyshka…"* | Pulls it only **8 ticks** closer |

The bite can never be dragged closer than 10 ticks away. Each jig plays a soft retrieve click and throws splash particles at the hole — three for a good rhythm, one for a bad one.

### The take

The bite fires the standard Float-flow window of **72 ticks** (3.6 s), with no sound — you watch the line. Because the Winter Rod is reel-less it does **not** run a strike-timing bar; the click sets the hook directly, and the single timing challenge comes afterwards as the **[pull-out](fishing-mechanics.md#the-pull-out-reel-less-poles)**.

The winter rod's pull-out uses the same forgiving curve as the Pole Rod — sweep period `30 − kg × 4.0` (floor 14 ticks), green zone `0.20 − kg × 0.033` (floor 0.060).

### Snags are almost absent

Fishing vertically into a clean hole is a flat **1 %** snag chance, and that 1 % is always the recoverable kind — the mormyshka comes back. Compare 10 % (3 % of which loses the rig) for ordinary fishing.

## What bites under the ice

Baiting the winter rig: its single slot takes a **mormyshka** or any **natural bait**. Eight species rate the Ice Jig at all:

| Species | Ice Jig score |
|---|---|
| Ruffe | 1.0 |
| Roach | 0.9 |
| Gudgeon | 0.9 |
| Perch | 0.9 |
| Smelt | 0.9 |
| Whitefish | 0.9 |
| Bleak | 0.8 (but `winter: 0.0` — never in winter) |
| Bream | 0.7 |

**Bloodworm** is the other classic under the ice — Smelt, Whitefish, Gudgeon, Ruffe and Blue bream all rate it 1.0.

The strongest winter species by season factor:

| Species | Winter factor | Notes |
|---|---|---|
| Burbot | **1.6** | Also `day: 0.0` — a cold-**night** fish only |
| Smelt | **1.5** | Level 1; wants a reel-less rod anyway |
| Cod | 1.3 | Sea; needs 4+ blocks of water and a 16-wide body |
| Whitefish | 1.2 | Lists the winter rod as ideal tackle |
| Arctic char | 1.1 | Cold-biome specialist (`cold` 1.4) |
| Halibut | 1.1 | Level 9 |
| Ruffe | 1.0 | Also active at night |
| Pike / Saithe | 0.9 | |
| Perch / Grayling / Volga zander | 0.8 | |

Only **Smelt** and **Whitefish** actually list the winter rod and the winter rig as their ideal tackle. Everything else you pull through the ice is being caught on gear it doesn't strictly want — which costs you on the rod and rig components of the [match score](fishing-mechanics.md#match-coefficient-m--your-tackle), but is entirely playable.

Seven species have `winter: 0.0` and will never bite through the ice: Crucian Carp, Rudd, Bleak, Chub, Tench, Catfish and Eel. Carp, Mirror Carp, Wild Carp, Grass Carp and Silver carp are effectively shut down at 0.02–0.05.

## Progression

The mod tracks fish landed through the ice as its own counter, feeding quest **stage 6 — Under the ice**:

| Quest | Goal | Reward |
|---|---|---|
| Catch your first fish through the ice | 1 ice catch | 2 × Ice Jig |
| Catch a burbot | any burbot | 4 × Oil Cake Groundbait |
| Catch a ruffe | any ruffe | 12 × Maggot |
| Catch 10 fish through the ice | 10 ice catches | **Winter Rod** |
| Catch 30 fish through the ice | 30 ice catches | 24 emeralds |
| Fully complete stage 6 | all five above | 50 emeralds |

There is also the code-driven advancement **From Under the Ice** — pull a burbot through a hole with the winter rod.

Stage 6 unlocks once 70 % of stage 5's tasks are done, and completing 70 % of stage 6 opens [stage 7, the north and the taiga](progression.md#the-quest-chain).

## See also

- [Rods](rods.md#rod-specific-behaviour) · [Rigs and baits](rigs-and-baits.md)
- [Fishing mechanics](fishing-mechanics.md) · [Water and conditions](water-and-conditions.md#seasons)
- [Progression](progression.md) · [Tools](tools.md#ice-auger)
