# River Fishing 🎣

A deep, **realistic fishing simulator** for Minecraft **1.21.1** on **Fabric & NeoForge**, built as a
modpack-anchor activity. Fishing is a *process*, not a click on the water: assemble a rod from a blank +
reel + line + rig + hook, match bait and groundbait to the fish you want, read the water, cast, and
outsmart **70 species** that each live by their own rules. Success = how well your whole setup matches a
given fish under the current conditions.

> **Status:** release **0.5.0** is out for **1.20.1**, **1.21.1** and **26.1.2**; **0.6.0** is in
> development on `dev-0.6.0`. **Requires [Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api)**;
> on Fabric also **[Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)**.
>
> Branches: `mc-1.21.1` (this one), `mc-1.20.1` (Fabric + Forge), `mc-26.1` (Stonecutter: 26.1.2 + 26.2).
> The original single-loader Forge build is archived on `forge-1.20.1`.

💬 **[Join the Discord](https://discord.gg/Kk2nKvsuRh)** — bug reports, catch screenshots, balance
arguments, and news of what is coming next. It is where the community lives.

📖 **[Full wiki in `docs/wiki/`](docs/wiki/README.md)** — every rod, reel, line, rig, bait, species and
mechanic, with the actual numbers. Also in **[русском](docs/wiki/ru/README.md)** and
**[українською](docs/wiki/uk/README.md)**.

---

## What makes it different

Nothing bites by luck. A **data-driven bite engine** weighs **more than a dozen factors** — both your
tackle and the world around you — to decide *what* bites and *how fast*:

- **The world:** water body (river / lake / pond / swamp / **sea**), **depth**, width, biome (climate +
  terrain), **season**, **time of day**, **weather**, and **barometric pressure** (with a trending glass),
  plus feeding-frenzy windows and how hard the spot has been fished.
- **Your setup:** rod blank & test window, reel size, line type / diameter / visibility, hook size, rig,
  **tackle weight**, bait, groundbait, leader and float depth.

Mismatched gear won't catch, reels only spool line within their working range, and a line that's too
visible spooks small, wary fish while a big one barely notices.

## Highlights

- **Rod assembly GUI** with live rod ↔ reel ↔ line compatibility checks and gear wear.
- A **tension fight mini-game** — the blank visibly **bends** under load, the fish **tires** over the
  fight, over-pull snaps the line, and a realistic breaking-strain model means big fish demand the right
  line and a strong drag.
- The **Tackle Station** — the fisherman's stall ties custom rigs and lures: pick a form, then the
  **weight**, which decides iron cost, casting distance and which fish will even look at it.
- **Saltwater fishing** — a dedicated sea tier (surf / sea spinning / boat / trolling), ocean depth
  zones, **trolling from a moving boat**, and big-game fight patterns (sounding dives, billfish jumps).
- **Ice fishing** — drill a hole with the auger, drop a winter rod, work the mormyshka in a rhythm game.
- A **living population** — over-fish a species and it thins out; it recovers over time. **Stock** a
  water with your own releases and a species can settle there for good.
- **Progression:** a bestiary journal, angler levels + a 7-perk skill tree, an 8-stage quest chain, and
  hand-crafted advancements (including tricky and funny ones).
- A **fisherman villager** who sells ready-to-cast tackle and buys your prime catches at a market price
  that moves with supply.
- **Bait farms** (worms & maggots), a live-bait trap, display **aquariums**, cooking & fillets.

## Recommended companions

The mod runs fine on its own, but it's **balanced around** two optional soft dependencies:

- **Serene Seasons** — unlocks the full seasonal bite (spring spawns, winter slow-down).
- **Biomes O' Plenty** — richer biomes for the habitat/biome factor to shine.

Also integrates with **JEI**, **Jade** and **Farmer's Delight** (knife-cutting fish into fillets) when
they're present.

---

## How to play (the beginner loop)

1. **Get bait.** Dig dirt / grass / sand with a **shovel** — **worms** drop (~10%). Later you can breed
   your own with a **worm farm** / **maggot farm**, gather live bait in a **bait trap**, or buy bait from
   the **fisherman villager**.
2. **Craft tackle.**
   - **Stick Rod** — 3 sticks (diagonal). Reeled rods also need **string** for the guide wraps.
   - **Hooks** — 1 iron nugget → 2 small hooks (№16, shapeless). Refine to a bigger hook by adding an
     iron nugget (№16 → №14 → №12 …); a bigger number = a smaller hook.
   - **Line** — a **ring of 8 string** makes the thinnest mono (0.10 mm). To thicken it a step, put a
     line in the **centre** with 8 string around it. Braid adds a phantom membrane, fluorocarbon an
     amethyst shard, to the base ring.
   - **Reels** — iron frame + copper gears + redstone drag, scaling with size. The saltwater sizes
     (8000+) need ocean loot for their sealed bearings.
3. **Assemble the rod.** Hold the rod and **sneak + right-click** to open the assembly GUI. Reel-less
   rods (stick / bamboo / pole) just take a **line**; a reeled rod adds a **reel** first (line spools
   **onto** the reel), and bottom rods add a swappable **rig**. Incompatible parts are rejected with a
   reason shown in the window (reel size ↔ rod, line diameter ↔ reel).
4. **Load the rig.** In the same GUI, drop a **hook** and **bait** (worm, maggot…) into the rig's inline
   slots. Predators (pike / zander / perch on spinning gear) take **artificial lures**
   (spinner / spoon / wobbler / soft-plastic), not natural bait; fit a **predator rig with a steel
   leader** or they bite through a bare line.
5. **(Optional) Feed the spot.** Right-click water while holding **groundbait** to create a fed zone that
   decays over a few minutes. The groundbait type decides which fish it pulls in.
6. **Cast.** **Hold** right-click to charge the power bar, **release** to cast that far. Tackle outside
   the blank's test window can't be thrown properly (shown as a dead zone on the bar).
7. **Watch the float — silently.** There's no "Bite!" text: the **float plunges / the line twitches**.
   Float rods run a small **timing mini-game** — strike (right-click) while the marker is in the green.
8. **Fight it.** A boss bar names the fish and what it's doing; a cue under it tells you when to reel and
   when to give line. Tap right-click to **reel** (raises tension); **sneak** to open the drag and pay
   out line. When it runs, over-tension **snaps the line** and you lose the rig — a strong enough line +
   drag for that fish is what lets you land it. Land it and you get a unique item carrying the
   **species, weight and length**.

Read the **fishing journal** for a per-species "how to catch", or scan the water with the **Fish Finder**
to see what's biting and how the pressure is trending.

---

## Building

An **Architectury** multi-loader project (`common` / `fabric` / `neoforge`). Minecraft 1.21.1 needs a
**JDK 21**; if you don't have a system JDK, PrismLauncher's bundled `java-runtime-delta` is a full
JDK 21 and works:

```powershell
# from the project root, in PowerShell:
$env:JAVA_HOME = "C:\Users\<you>\AppData\Roaming\PrismLauncher\java\java-runtime-delta"
.\gradlew.bat build
```

The loader jars land in `fabric/build/libs/riverfishing-fabric-<version>.jar` and
`neoforge/build/libs/riverfishing-neoforge-<version>.jar` (use the plain jar, not `-dev`/`-sources`).
Dev clients: `.\gradlew.bat :fabric:runClient` and `.\gradlew.bat :neoforge:runClient`.

Species are **data-driven** — see [docs/FISH_PROFILES.md](docs/FISH_PROFILES.md) to add or retune fish
from a datapack without touching code.

## Debug commands (ops)

- `/rffish unlockall` — fill the journal (all species, trophies, XP) so every quest goal is met.
- `/rffish reset` — wipe your records, XP and quests.

## License

See [LICENSE.txt](LICENSE.txt).
