# River Fishing 🎣

A deep, **realistic river & ice-fishing simulator** for Minecraft **1.20.1** on **Forge & Fabric**, built
as a modpack-anchor activity. Fishing is a *process*, not a click on the water: assemble a rod from a
blank + reel + line + rig + hook, match bait and groundbait to the fish you want, read the water, cast,
and outsmart 30 species that each live by their own rules. Success = how well your whole setup matches a
given fish under the current conditions.

> **Status:** runs on **Forge and Fabric** (Minecraft 1.20.1) — see release **0.2.0**; newer Minecraft
> versions are next. **Requires [Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api)**;
> on Fabric also **[Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)**. (The original
> single-loader Forge build is archived on the `forge-1.20.1` branch.)

---

## What makes it different

Nothing bites by luck. A **data-driven bite engine** weighs **more than a dozen factors** — both your
tackle and the world around you — to decide *what* bites and *how fast*:

- **The world:** water body (river / lake / pond / swamp), **depth**, width, biome (climate + terrain),
  **season**, **time of day**, **weather**, and **barometric pressure** (with a trending glass), plus
  feeding-frenzy windows and how hard the spot has been fished.
- **Your setup:** rod blank & test, reel size, line type / diameter / visibility, hook size, rig, bait,
  groundbait, leader and float depth.

Mismatched gear won't catch, reels only spool line within their working range, and a line that's too
visible spooks small, wary fish while a big one barely notices.

## Highlights

- **Rod assembly GUI** with live rod ↔ reel ↔ line compatibility checks and gear wear.
- A **tension fight mini-game** — over-pull and the line snaps; a realistic breaking-strain model means
  big fish demand the right line and a strong drag.
- A **living population** — over-fish a species and it thins out; it recovers over time (faster in spring).
- **Progression:** a bestiary journal, angler levels + a 6-perk skill tree, a 6-stage quest chain, and
  hand-crafted advancements (including tricky and funny ones).
- A **fisherman villager** who trades tackle and buys your prime catches.
- **Ice fishing** — drill a hole with the auger, drop a winter rod, work the mormyshka in a rhythm game.
- **Bait farms** (worms & maggots), a live-bait trap, a display **aquarium**, a trophy stand,
  cooking & fillets.

## Recommended companions

The mod runs fine on its own, but it's **balanced around** two optional soft dependencies:

- **Serene Seasons** — unlocks the full seasonal bite (spring spawns, winter slow-down).
- **Biomes O' Plenty** — richer biomes for the habitat/biome factor to shine.

> ⚠️ **Freshwater only, for now** — rivers, lakes and ponds. **Ocean / sea fishing isn't supported yet.**

---

## How to play (the beginner loop)

1. **Get bait.** Dig dirt / grass / sand with a **shovel** — **worms** drop (~10%). Later you can breed
   your own with a **worm farm** / **maggot farm**, gather live bait in a **bait trap**, or buy bait from
   the **fisherman villager**.
2. **Craft tackle.**
   - **Stick Rod** — 3 sticks (diagonal).
   - **Hooks** — 1 iron nugget → 2 small hooks (№16, shapeless). Refine to a bigger hook by adding an
     iron nugget (№16 → №14 → №12 …); a bigger number = a smaller hook.
   - **Line** — a **ring of 8 string** makes the thinnest mono (0.10 mm). To thicken it a step, put a
     line in the **centre** with 8 string around it. Braid adds a phantom membrane, fluorocarbon an
     amethyst shard, to the base ring.
3. **Assemble the rod.** Hold the rod and **sneak + right-click** to open the assembly GUI. Reel-less
   rods (stick / bamboo / pole) just take a **line**; a reeled rod adds a **reel** first (line spools
   **onto** the reel), and bottom rods add a swappable **rig**. Incompatible parts are rejected with a
   reason shown in the window (reel size ↔ rod, line diameter ↔ reel).
4. **Load the rig.** In the same GUI, drop a **hook** and **bait** (worm, maggot…) into the rig's inline
   slots. Predators (pike / zander / perch on spinning gear) take **artificial lures**
   (spinner / spoon / wobbler / soft-plastic), not natural bait; fit a **predator rig with a steel
   leader** or they bite through a bare line.
5. **(Optional) Feed the spot.** Right-click water while holding **groundbait** to create a fed zone that
   decays over ~10 minutes. The groundbait type decides which fish it pulls in.
6. **Cast.** **Hold** right-click to charge the power bar, **release** to cast that far. An under-loaded
   blank can't throw as far (shown as a dead zone on the bar).
7. **Watch the float — silently.** There's no "Bite!" text: the **float plunges / the line twitches**.
   Float rods run a small **timing mini-game** — strike (right-click) while the marker is in the green.
8. **Fight it.** A boss bar shows the fish. Tap right-click to **reel** (raises tension); ease off to
   **give line**. When it runs, over-tension **snaps the line** and you lose the rig — a strong enough
   line + drag for that fish is what lets you land it. Land it and you get a unique item carrying the
   **species, weight and length**.

Read the **fishing journal** for a per-species "how to catch", or scan the water with the **echo sounder**
to see what's biting and how the pressure is trending.

---

## Building

An **Architectury** multi-loader project (`common` / `forge` / `fabric`). Needs a **JDK 17**; if you
don't have a system JDK, PrismLauncher's bundled `java-runtime-gamma` is a full JDK 17 and works:

```powershell
# from the project root, in PowerShell:
$env:JAVA_HOME = "C:\Users\<you>\AppData\Roaming\PrismLauncher\java\java-runtime-gamma"
.\gradlew.bat build
```

The loader jars land in `forge/build/libs/riverfishing-forge-0.2.0.jar` and
`fabric/build/libs/riverfishing-fabric-0.2.0.jar` (use the plain jar, not `-dev`/`-sources`). Dev clients:
`.\gradlew.bat :forge:runClient` and `.\gradlew.bat :fabric:runClient`.

## Debug commands (ops)

- `/rffish unlockall` — fill the journal (all species, trophies, XP) so every quest goal is met.
- `/rffish reset` — wipe your records, XP and quests.

## License

See [LICENSE.txt](LICENSE.txt).
