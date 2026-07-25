# Sea fishing

Saltwater is a separate tier, not a coat of paint. The freshwater ladder tops out at 7 kg of reel drag and a 220 g cast; ocean fish are an order of magnitude heavier, so the sea gear is a **gate**.

## The saltwater gear

Four blanks, four reels, and a heavy line tier.

| Blank | Reel band | Cast window | Flow | Reach | Built for |
|---|---|---|---|---|---|
| **Surf rod** | 6000–8000 | 80–250 g | Bottom | 32 blocks | Bottom fishing from the shore |
| **Sea spinning rod** | 5000–9000 | 20–120 g | Active | 32 blocks | Shore and boat lure fishing |
| **Boat rod** | 8000–12000 | 100–400 g | Bottom | 18 blocks | Deep bottom rigs from a boat |
| **Trolling rod** | 10000–14000 | 150–600 g | Active | 18 blocks | Towing behind a moving boat |

All four are diamond-built and repair with a **diamond**. Their recipes differ only in the tip — prismarine shard, two shards, prismarine crystals, nautilus shell — so the ladder reads at a glance. Full details in [Rods](rods.md).

The four saltwater reels (8000, 10000, 12000, 14000) need ocean loot: prismarine, crystals, a nautilus shell. See [Reels and lines](reels-and-lines.md#reel-recipes). Their [drag](reels-and-lines.md#the-drag-curve) climbs steeper than the freshwater ladder — 9.5 / 14.5 / 19.5 / 24.5 kg — which is what makes the pelagics stoppable at all.

For line, the heavy tier exists precisely for these reels: mono up to 0.80 mm (64 kg) and braid up to 0.60 mm (108 kg). A 400 kg marlin needs it.

The universal saltwater hook bait is **Fish strip** — four from any caught fish. Nineteen species rate it, nine of them at 0.9 or better.

Four sea species are toothy enough to demand a leader: **Conger eel, Wahoo, Barracuda** and **Mako shark**.

### Getting the gear

Two routes:

- **Craft it.** Diamonds plus ocean loot — see [Crafting](crafting.md).
- **Buy it.** A **master-tier** [fisherman](villager.md) has one rotating listing that offers a fully assembled sea spinning (30 emeralds), surf (34), boat (34) or trolling (40) rod, plus rotating listings for the big reels, the heavy lines and the No.2/No.1 hooks.

## The ocean zones

Sea fishing means the water body classified as **`sea`** — the biome is tagged ocean or deep ocean. Within that, three biome groups shape what lives where:

| Group | Trigger | Character |
|---|---|---|
| `ocean_biome` | Any ocean or deep-ocean biome | The general shelf |
| `deep` | **Deep**-ocean biomes only | The offshore zone |
| `beach` | Beach biomes | The surf line |

The other layer is the water column itself. Every sea species has a `depth_min` gate on how deep the water must be at your cast point:

| Depth needed | Species |
|---|---|
| 2+ blocks | Mackerel, Herring, Garfish, Sea bass, Flounder |
| 3+ | Ray, Barracuda |
| 4+ | Cod, Saithe, Conger eel, Halibut |
| 5+ | Mahi-mahi |
| 6+ | Wahoo |
| 8+ | Yellowfin tuna, Sailfish, Mako shark |
| 10+ | Blue marlin, Swordfish |

And a `width_min` gate, running from 10 blocks (herring) to 24 (blue marlin, swordfish).

> The in-game guide says "the pelagics live over deep water only". Strictly, what gates them is the **water-column depth and the width**, not the deep-ocean biome tag — the `deep` group merely gives them their best factor (1.2–1.3 for the pelagics). In practice you find them by getting a long way offshore over genuinely deep water, which is the same thing.

### The coastal and shelf nine

| Species | Weight | Level | Take it on |
|---|---|---|---|
| Herring | 100 g – 600 g | 4 | Fish strip, small lures; light sea tackle |
| Mackerel | 300 g – 2 kg | 4 | Castmaster and small lures, fast retrieve |
| Garfish | 300 g – 1.5 kg | 4 | Fish strip near the surface; sea spinning only |
| Flounder | 300 g – 4 kg | 4 | Bottom rigs, fish strip and worm — a shy, pressing bite |
| Sea bass | 500 g – 8 kg | 5 | Wobblers and soft plastics in the surf, at dusk |
| Saithe | 1 kg – 15 kg | 5 | Soft jigs mid-water over the drop-offs |
| Cod | 2 kg – 40 kg | 6 | Fish strip and jigs on the bottom over depth |
| Ray | 2 kg – 50 kg | 6 | Heavy bottom rig with cut bait |
| Conger eel | 3 kg – 60 kg | 7 | Bottom rig, fish strip or live bait — **leader mandatory** |

**Round goby** also lives in the sea (factor 1.1), and **Smelt** is primarily a sea fish (1.2) — both ungated or nearly so, and both good first saltwater catches.

### The pelagic four and the big-game trophies

| Species | Weight | Level | Pattern |
|---|---|---|---|
| Mahi-mahi | 2 kg – 20 kg | 7 | greyhounding |
| Barracuda | 2 kg – 20 kg | 6 | burst — **leader mandatory** |
| Wahoo | 5 kg – 40 kg | 7 | burst — **leader mandatory** |
| Yellowfin tuna | 10 kg – 150 kg | 7 | **sounding** |
| Sailfish | 20 kg – 80 kg | 7 | greyhounding |
| Mako shark | 20 kg – 200 kg | 7 | greyhounding — **leader mandatory** |
| Swordfish | 30 kg – 300 kg | 7 | **sounding** |
| Blue marlin | 50 kg – 400 kg | 7 | greyhounding |
| Halibut | 2 kg – 200 kg | 9 | **sounding** |

Three of these hide a [legendary specimen](fishing-mechanics.md#legendary-fish): yellowfin tuna (Old Ridgeback, 140 kg), blue marlin (The Leviathan, 380 kg) and mako shark (The Megalodon, 390 kg).

**Halibut** is the deepest gate in the mod alongside the sturgeon — angler level **9**.

## Trolling

Trolling is fishing from a **moving boat**. The boat does the casting and the retrieving; you steer and then fight.

### Arming it

You need, all at once:

1. An **assembled Trolling rod or Sea spinning rod** in your **main hand**.
2. A **boat** you are riding. Any watercraft that actually moves you works.
3. Horizontal speed inside the working window: **0.12 to 0.60 blocks per tick** (roughly 2.4 to 12 blocks per second), measured as a smoothed average of the boat's real position change.

Hold that for **60 consecutive good ticks** (3 seconds) and the line goes out by itself at a fixed 0.55 power along your look direction: *"Trolling: line is out behind the boat"*.

A rough patch — a turn, a wave — **decays** the arming counter by 3 rather than resetting it, so you don't restart the whole 3 seconds every time the boat wobbles.

### Fishing it

- The lure **trails about 14 blocks astern** and the target follows the boat, so the line visibly drags behind you and never trips the 40-block session guard.
- The boat's own movement works the lure — a retrieve tick every 2 ticks, automatically. **You never wind it in.** There is no "retrieve empty" and no end of cast; the lure trails for as long as you like.
- Over open sea there are **no snags and no foul-hooking**.
- **No strike timing.** The boat's momentum sets the hook itself: *"Fish on! The boat's pull set the hook — fight it!"* You go straight into the fight.

Hold the speed: too slow and the lure sinks, too fast and the fish spook. Stop the boat and fight when you are hooked up.

Nine species list trolling as ideal tackle: **Mahi-mahi, Wahoo, Yellowfin tuna, Barracuda, Blue marlin, Sailfish, Swordfish, Mako shark** and, in fresh water, **Taimen**.

## Big-game fights

Two fight patterns exist only out here. Both are covered in full in [fight patterns](fishing-mechanics.md#fight-patterns); the short version:

### Sounding — Yellowfin tuna, Swordfish, Halibut (and the freshwater Sturgeon)

The fish dives, and **while it is down it takes line** — your progress bar drains 0.0035 per tick. Every 25 ticks of the dive the long drag scream plays and you are told *"Sounding — pump it back between dives!"*

You cannot out-muscle a dive. Ride it out, then pump the line back in the 3.5–6.5 second gap before the next one. Sounding fish get **+3 runs**, a 92 % run chance and the longest fight timeout bonus in the game (+700 ticks).

### Greyhounding — Blue marlin, Sailfish, Mahi-mahi, Mako shark (and Atlantic salmon)

Between runs the fish **breaches**: *"It jumps — give slack, do not reel!"*, opening a **15-tick** window. Crank inside it and there is a **35 % chance the hook rips straight out**. The answer is the [open drag](fishing-mechanics.md#the-three-drag-positions) — crouch, ride the jump, then stand up and wind.

### And in general

Everything caught on an Active rod counts as a [predator fight](fishing-mechanics.md#predator-fights) — sharper pulls, a tighter break margin, slower landing and extra head-shakes, all scaling with weight. A 100 kg marlin on a trolling rod is about as far from a roach on a pole as the mod goes.

## Progression into the sea

Quest **stage 8 — The sea and big game** is the sea chain. Its ten goals, in order:

| Quest | Reward |
|---|---|
| Catch a sea bass in the surf | Castmaster |
| Net 5 herring | 8 × Fish strip |
| Haul a cod up from the deep | 8 emeralds |
| Discover 40 fish species | **Trolling rod** |
| Take a mahi-mahi on the troll | 10 emeralds |
| Pump up a tuna of 60 kg or more | 24 emeralds |
| Raise a halibut — like an anchor off the bottom | Braid line 0.60 |
| Catch any billfish: marlin, sailfish or swordfish | 40 emeralds |
| Discover 60 fish species | **Reel 14000** |
| Fully complete stage 8 | 64 emeralds |

Stage 8 unlocks once you have finished 70 % of [stage 7](progression.md#the-quest-chain). Stage 7's completion reward is the **Surf rod** — that is the intended door into saltwater.

## See also

- [Rods](rods.md) · [Reels and lines](reels-and-lines.md) · [Tackle Station](tackle-station.md)
- [Fishing mechanics](fishing-mechanics.md) · [Water and conditions](water-and-conditions.md)
- [Species](species.md) · [Progression](progression.md)
