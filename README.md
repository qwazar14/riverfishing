# River Fishing 🎣

A **realistic river, ice and sea fishing simulator** for Minecraft, built to be the thing a modpack is
about rather than a minigame beside it. Fishing here is a *process*, not a click on the water: you
assemble a rod from a blank, a reel, a line and a rig; you match bait, hook size, groundbait and leader
to the fish you actually want; you read the water, the season, the hour and the barometer; and then you
fight what takes.

Nothing bites by luck. A data-driven bite engine weighs your whole setup against every one of **79
species** under the current conditions and decides both *what* takes and *how long you wait*.

**⬇️ [Download the latest release](https://github.com/qwazar14/riverfishing/releases/latest)**

| Minecraft | Loaders |
|---|---|
| **1.20.1** | Fabric · Forge |
| **1.21.1** | Fabric · NeoForge |
| **26.1.2** | Fabric · NeoForge |
| **26.2** | Fabric · NeoForge |

Requires **[Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api)**; on
Fabric also **[Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)**.

📖 **[Read the wiki](https://qwazar14.github.io/riverfishing/)** — 24 pages in **English, Russian and
Ukrainian**, one URL with a language switcher. Every rod, reel, line, rig, bait, species, block and
mechanic with the actual numbers, and every recipe drawn as a real crafting grid generated from the
recipe files, so the wiki cannot drift from what the game loads. Source in
[`docs/wiki/`](docs/wiki/README.md) ([русский](docs/wiki/ru/README.md) ·
[українська](docs/wiki/uk/README.md)).

💬 **[Join the Discord](https://discord.gg/Kk2nKvsuRh)** — bug reports, catch screenshots, balance
arguments. Several features in this mod exist because a player asked for them by name.

---

## You can see into the water

Lean over a lake and the fish are there — drifting under the surface, at their real size, in the numbers
the water actually holds. A shoal of roach reads as a shoal. A single big shadow on the bottom is a
single big fish, and you will want to know what it is.

What you never get is a label. You get a shape and a size, and working out what it is stays yours.

**And the fish can see you.** Sprinting along the bank, landing hard, splashing in the shallows all
spook the water you are standing over, and a spooked fish does not bite.

## The fight asks you questions

Every run has a **course**, and you answer it with the movement keys: the fish tracks left, you lean on
it from the right; it goes deep, you pull back and get its head up; it comes up to jump, you put the rod
down. Answering right is not a tax — the fish tires nearly three times as fast and the run loads your
tackle at half rate.

- **Your feet are tackle.** Backing away from the water is pumping with your legs: it wins line and
  loads the rod. Walk the whole way and you snap.
- **The angler runs out.** A stamina bar for the person holding the rod, and it comes back only when you
  stop pulling — so the answer to tired arms is to stop, not to click faster.
- **The rod bends under tension**, in six steps driven by the real stress on your tackle, and everyone
  nearby sees it.
- **Over-pull and the line snaps.** A real breaking-strain model means big fish demand the right line
  and a drag you know how to use — and a heavier line genuinely buys you time.

## What the engine weighs

- **The world:** water body (river / lake / pond / swamp / **sea**), depth, width, biome, season, time of
  day, weather, barometric pressure with a trending glass, feeding-frenzy windows, and how hard the spot
  has been fished.
- **Your setup:** rod blank and test window, reel size, line type / diameter / visibility, hook size,
  rig, **tackle weight**, bait, groundbait, leader and float depth.

Mismatched gear catches nothing, reels only spool line within their working range, and a line that is
too visible spooks small wary fish while a big one barely notices.

## Highlights

- **Five ways to fish** — float, spinning, long-cast bottom, ice, and a full saltwater tier with surf,
  boat and trolling rods, ocean depth zones and big-game fight patterns.
- **The Tackle Station** — the fisherman's stall is also your bench: pick a form, step the weight, feed
  it hooks, iron and string. Weight sets cast distance, has to fall inside your blank's test window, and
  a lure's mass *is* its size. Dye a lure and the colour affects the bite.
- **The tackle box**, four sizes, 9 to 36 slots — tackle only, renameable in the box, dyeable, and it
  keeps everything when broken.
- **The keepnet is a grid, not a stack of slots.** A fish takes the space its body needs, so you choose
  what is worth keeping.
- **A market that moves.** Overfish a species and its price falls; the fisherman's **order of the day**
  pays ×2.5 for one species and hangs over his counter on its own sign.
- **A living population** — over-fish a water and it thins out, recovering over time and faster in
  spring. Stock a water yourself and a species can settle there for good.
- **Every fish shows its age.** A young fish is paler than an old one of the same species, and some
  carry named morphs: a golden tench, an albino, a stunted fish that looks it. **Eight legendary fish**,
  one per server each.
- **Progression:** a bestiary journal with a page and an illustration per species, angler levels and a
  six-perk skill tree, an eight-stage quest chain and 22 hand-crafted advancements.
- **Configurable.** `config/riverfishing.json` is written on first run: difficulty presets plus dials for
  phantom bites, break sensitivity, depletion, wear, snags, how much the fish notice you, and where the
  trophy bar sits.
- Bait farms, a live-bait trap, an aquarium and a mini aquarium, cooking and fillets.

## Recommended companions

The mod runs fine alone, but the balance is written around two optional add-ons:

- **Serene Seasons** — unlocks the full seasonal bite (spring spawning runs, the winter slow-down).
- **Biomes O' Plenty** — a richer world for the habitat and biome factors to work in.

Supports **JEI** and **Jade** on 1.20.1 and 1.21.1; the Filleting Knife registers as a knife for
**Farmer's Delight**.

---

## How to play — the first hour

1. **Get bait.** Dig dirt, grass or sand with a **shovel** — worms drop about one time in ten. Later:
   breed your own on a worm or maggot farm, gather live bait in a bait trap, or buy from the fisherman.
2. **Craft tackle.**
   - **Stick Rod** — three sticks on the diagonal.
   - **Hooks** — one iron nugget makes two #16 hooks; add a nugget to step up a size (a bigger number is
     a smaller hook).
   - **Line** — a ring of 8 string makes the thinnest mono; put a line in the centre of another ring to
     thicken it a step. Braid adds a phantom membrane, fluorocarbon an amethyst shard.
   - **Reels** — iron frame, copper gears, redstone drag, scaling with size. The saltwater sizes need
     ocean loot for their sealed bearings.
3. **Assemble the rod.** Hold it and **sneak + right-click**. Reel-less rods take a line; a reeled rod
   takes a reel first and the line spools onto it; bottom rods add a swappable rig. Incompatible parts
   are refused with the reason written in the window.
4. **Load the rig.** Drop a **hook** and **bait** into the rig's inline slots. Predators want artificial
   lures, not worms, and they bite through a bare line — fit a leader.
5. **Feed the spot** (optional). Right-click water holding **groundbait**; the type decides what it
   pulls in.
6. **Cast.** Hold right-click to charge, release to throw. Tackle outside the blank's test window cannot
   be thrown properly, shown as a dead zone on the bar.
7. **Watch the float — silently.** There is no "Bite!" text. The float plunges, the line twitches. Float
   rods run a small timing mini-game: strike while the marker is in the green.
8. **Fight it.** Read the course off the boss bar, answer with the movement keys, ease off when it runs,
   and watch your stamina. Land it and you get a unique item carrying the **species, weight and length**.

Read the **journal** for a per-species "how to catch", or scan the water with the **Fish Finder** to see
what is biting and how the pressure is trending.

---

## Building

An **Architectury** multi-loader project (`common` / `fabric` / `forge` / `neoforge`). The three
Minecraft lines live on their own branches and want different JDKs:

| Branch | Minecraft | JDK | Loader modules |
|---|---|---|---|
| `dev-0.7.0` | 1.21.1 | 21 | `fabric`, `neoforge` |
| `dev-0.7.0-1.20.1` | 1.20.1 | 17 | `fabric`, `forge` |
| `dev-0.7.0-26x` | 26.1.2 + 26.2 | 25 | `fabric`, `neoforge` (Stonecutter, both at once) |

`main` carries the latest released state; the original single-loader Forge build is archived on
`forge-1.20.1`.

```powershell
# from the project root, in PowerShell — JDK 21 for the 1.21.1 branch:
$env:JAVA_HOME = "C:\Users\<you>\AppData\Roaming\PrismLauncher\java\java-runtime-delta"
.\gradlew.bat build
```

If you have no system JDK, PrismLauncher's bundled runtimes are full JDKs: `java-runtime-gamma` is 17
and `java-runtime-delta` is 21.

The loader jars land in `<loader>/build/libs/riverfishing-<loader>-<version>.jar` — use the plain jar,
not `-dev` or `-sources`. Dev clients: `.\gradlew.bat :fabric:runClient`.

Species are **data-driven** — see [docs/FISH_PROFILES.md](docs/FISH_PROFILES.md) to add or retune fish
from a datapack without touching code. `tools/` holds the generators and the checkers that keep the
data, the wiki and the code from drifting apart.

## Debug commands (ops)

- `/rffish unlockall` — fill the journal (all species, trophies, XP) so every quest goal is met.
- `/rffish reset` — wipe your records, XP and quests.

## License

See [LICENSE.txt](LICENSE.txt).
