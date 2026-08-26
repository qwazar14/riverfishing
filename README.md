# River Fishing 🎣

A **realistic river, ice and sea fishing simulator** for Minecraft, built to be the thing a modpack is
about rather than a minigame beside it. Fishing here is a *process*, not a click on the water: you
assemble a rod from a blank, a reel, a line and a rig; you mix your own groundbait and match bait, hook
size and leader to the fish you actually want; you read the water, the season, the hour and the
barometer; and then you fight what takes.

Nothing bites by luck. A data-driven bite engine weighs your whole setup against every one of **93
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

📖 **[Read the wiki](https://qwazar14.github.io/riverfishing/)** — 27 pages in **English, Russian and
Ukrainian**, one URL with a language switcher. Every rod, reel, line, rig, bait, species, block and
mechanic with the actual numbers, an interactive catch calculator that answers "what does a nine-kilo
carp want", and every recipe drawn as a real crafting grid generated from the recipe files, so the wiki
cannot drift from what the game loads. Source in [`docs/wiki/`](docs/wiki/README.md)
([русский](docs/wiki/ru/README.md) · [українська](docs/wiki/uk/README.md)).

💬 **[Join the Discord](https://discord.gg/Kk2nKvsuRh)** — bug reports, catch screenshots, balance
arguments. Several features in this mod exist because a player asked for them by name.

---

## The rod is a real object

All **13 blanks are modelled**, hand-built piece by piece — grips, reel seats, guides — and the one in
your hands is drawn at its true length. A feeder rod is three blocks of carbon, because a feeder rod is
3.9 metres of carbon.

**It bends as a chain.** A blank is cut into sections, up to eight on the sea spinner, and each joint
turns a little more than the one behind it, so the tip travels furthest and the butt barely moves. The
bend is continuous and reads the fish's pull against *that blank's own power class*: a 1.8 kg bass on a
trolling rod barely marks it, the same fish on an ultralight folds it over.

**And it bends *toward* the fish** — a run to the left lays the whole blank over to the left, a dive
drives the tip down, a fish coming up to jump lifts it. Each blank carries its own spring, so turning
your view leaves the tip trailing and a strike visibly yanks it. The line is threaded off the spool,
through every guide ring and down to the water, in the colour and thickness of the line you spooled.

Not to your taste? `/rfrod blank off` puts the flat sprite back. It persists.

## You can see into the water

Lean over a lake and the fish are there — each holding its own position and heading, turned the way it
is swimming. A shoal of roach reads as a shoal. A single big shadow on the bottom is a single big fish,
and you will want to know what it is.

What you never get is a label. You get a shape and a size, and working out what it is stays yours.

**And the fish can see you.** Sprinting, walking heavily, jumping, breaking blocks, wading, a boat under
way, even your own shadow across the water at a low sun. A spooked patch stops biting; crouch and stand
still and it forgets you in thirty to ninety seconds.

## The fight asks you questions

Every run has a **course**, and **you answer it with the camera** — lean your view against the way the
fish is going. The view is re-anchored every time the course changes, so it is where you look *relative
to the run* that counts. Four arrow keys are a quiet override, rebindable in Minecraft's own controls,
but you never have to touch them.

**Nothing tells you which way it went — the rod does.** No arrow on the screen, no direction in the boss
bar. The blank is the instrument, and reading it is the skill.

Answering right is not a tax: the fish tires nearly three times as fast and the run loads your tackle at
roughly half rate.

- **Your feet are tackle.** Backing away from the water is pumping with your legs: it wins line and
  loads the rod. Walk the whole way and you snap.
- **The angler runs out.** A stamina bar for the person holding the rod, and it comes back only when you
  stop pulling — so the answer to tired arms is to stop, not to click faster.
- **Over-pull and the line snaps.** A real breaking-strain model means big fish demand the right line
  and a drag you know how to use — and a heavier line genuinely buys you time.
- **The giants are landable.** Required pull tapers above 20 kg, so a 400 kg marlin asks for 210 kg of
  tackle rather than the 802 kg a linear law wanted. Nothing under 20 kg changed.
- **The last dash at the bank is a roll, not a ritual** — 35 % for an ordinary fish, 85 % for a trophy
  on a hard pattern, never for a boot.

## Groundbait you mix yourself

There is **one** groundbait — Base Groundbait, from wheat seeds and bread — and everything else is what
you put *in* it. Drop the base in a crafting grid with up to eight other things from a 27-item pantry
and the mix takes on a **grind** and a **richness** of its own.

Every one of the 93 species wants its own pair: a bleak wants fine and lean, a carp coarse and rich, and
a mix aimed at one is actively wrong for the other. Feeding coarse is how you stop catching tiddlers.

**You cannot overfeed a spot.** What a mix cannot do is out-fish its own contents. The decision is not
*how much*. It is *what of*.

## What the engine weighs

- **The world:** water body (river / lake / pond / swamp / **sea**), depth, width, biome, season, time of
  day, weather, barometric pressure with a trending glass, feeding-frenzy windows, and how hard the spot
  has been fished.
- **Your setup:** rod blank and test window, reel size, line type / diameter / visibility, hook size,
  rig, **tackle weight**, bait, groundbait, leader and float depth.

Mismatched gear catches nothing, reels only spool line within their working range, and a line that is
too visible spooks small wary fish while a big one barely notices.

## Highlights

- **Six ways to fish** — float, spinning, long-cast bottom, ice, a full saltwater tier with surf, boat
  and sea-spinning rods over three depth zones, and trolling behind a moving boat.
- **The Tackle Station** — the fisherman's stall is also your bench: pick one of sixteen forms, step the
  weight, feed it hooks, iron and string. Weight sets cast distance, has to fall inside your blank's
  test window, and a lure's mass *is* its size. Dye a lure and the colour affects the bite.
- **The tackle box**, four sizes, 9 to 36 slots — tackle only, renameable in the box, dyeable, and it
  keeps everything when broken.
- **The keepnet is a grid, not a stack of slots.** A fish takes the space its body needs, so you choose
  what is worth keeping.
- **A market that moves.** Overfish a species and its price falls; the fisherman's **order of the day**
  pays ×2.5 for one species and hangs over his counter on its own sign.
- **A living population** — over-fish a water and it thins out, recovering over time and faster in
  spring. Stock a water yourself and a species can settle there for good.
- **Every fish shows its age.** A young fish is paler than an old one of the same species, and some
  carry named morphs: a golden tench, a humpbacked fish out of an over-stocked pond, a stunted one that
  looks it. **Eight legendary fish**, one per server each, with their own names and their own fights.
- **The journal is an encyclopedia.** Eight tabs, a page for every one of the 93 species with its own
  hand-drawn illustration — no gaps — a family rail, a live search, sortable tables of every bait, lure
  and piece of gear, and 24 written guides. The species card is built server-side, so it is complete on
  a dedicated server too.
- **Progression:** angler levels through four ranks and twelve gates, a six-perk skill tree, an
  eight-stage quest chain and 22 hand-crafted advancements.
- **Multiplayer throughout.** Everyone within twelve blocks sees your fight on the boss bar, and a
  friend can net your fish for you once it is past 85 % — which lands it for you and pays them XP.
- **Configurable.** `config/riverfishing.json` is written on first run: difficulty presets plus dials for
  phantom bites, break sensitivity, depletion, wear, snags, how much the fish notice you, and where the
  trophy bar sits.
- Bait farms, a live-bait trap, display aquariums, a trophy stand, cooking and fillets.

## Recommended companions

The mod runs fine alone, but the balance is written around two optional add-ons:

- **Serene Seasons** — unlocks the full seasonal bite (spring spawning runs, the winter slow-down).
- **Biomes O' Plenty** — a richer world for the habitat and biome factors to work in.

Neither is version-locked: Serene Seasons is reached by reflection and falls back to a neutral season if
absent, and Biomes O' Plenty is read through biome **tags** rather than referenced in code at all. The
[compatibility page](docs/wiki/compatibility.md) lists the exact versions every build is tested against.

**JEI** and **Jade** are supported on 1.20.1 and 1.21.1.

---

## How to play — the first hour

1. **Get bait.** Dig dirt, grass or mud with a **shovel** — worms drop about one time in ten. Later:
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
   lures, not worms, and fifteen species bite through a bare line — fit a leader.
5. **Feed the spot.** Craft **wheat seeds + bread** into Base Groundbait, mix it in a crafting grid with
   up to eight things from the pantry, and **right-click water** with the result. Coarse for big fish,
   fine for small ones.
6. **Cast.** Hold right-click to charge, release to throw. Tackle outside the blank's test window cannot
   be thrown properly, shown as a dead zone on the bar.
7. **Watch the float — silently.** There is no "Bite!" text. The float plunges, the line twitches. Lure
   rods and reeled float rods run a small timing bar on the strike; a reel-less pole saves its timing
   bar for the pull-out instead.
8. **Fight it.** Watch the rod, not the screen — it lays over the way the fish went. Lean your view
   against it, ease off when it runs, and watch your stamina. Land it and you get a unique item carrying
   the **species, weight and length**.

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

`main` carries the latest released state of the 26.x line; the per-version release branches are
`mc-1.20.1`, `mc-1.21.1` and `mc-26.1`, and the original single-loader Forge build is archived on
`forge-1.20.1`. `mc-1.21.1` also serves `updates.json` raw to the in-game update checker, which is why
that file is bumped after a release rather than with it.

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
data, the wiki and the code from drifting apart: recipe dialect per Minecraft version, lang parity,
wiki coverage, whether every fish is still landable by tackle that exists.

## Debug commands (ops)

- `/rffish unlockall` — fill the journal (all species, trophies, XP) so every quest goal is met.
- `/rffish reset` — wipe your records, XP and quests.

## License

See [LICENSE.txt](LICENSE.txt).
