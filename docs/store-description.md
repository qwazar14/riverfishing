# River Fishing 🎣

*The project description for CurseForge and Modrinth. Paste as is. The per-release copy lives in
[docs/patchnotes/](patchnotes/).*

---

A **realistic river, ice and sea fishing simulator**, built to be the thing a modpack is about rather than
a minigame beside it. Fishing here is a process, not a click on the water: you assemble a rod from a
blank, a reel, a line and a rig; you match bait, hook size, groundbait and leader to the fish you actually
want; you read the water, the season, the hour and the barometer; and then you fight what takes.

Nothing bites by luck. A data-driven bite engine weighs your whole setup against every one of **79
species** under the current conditions and decides both *what* takes and *how long you wait*.

## Versions

- **1.20.1** — Fabric & Forge
- **1.21.1** — Fabric & NeoForge
- **26.1.2** — Fabric & NeoForge
- **26.2** — Fabric & NeoForge

**Requires [Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api)**; on Fabric
also **Fabric API**.

The mod runs fine alone, but the balance is written around two optional add-ons:

- **Serene Seasons** — unlocks the full seasonal bite (spring spawning runs, the winter slow-down).
- **Biomes O' Plenty** — a richer world for the habitat and biome factors to work in.

The Filleting Knife registers as a knife for **Farmer's Delight**. **JEI** and **Jade** are supported on
1.20.1 and 1.21.1.

## You can see into the water

Lean over a lake and the fish are there — drifting under the surface, at their real size, in the numbers
the water actually holds. A shoal of roach reads as a shoal. A single big shadow on the bottom is a single
big fish, and you will want to know what it is.

What you never get is a label. You get a shape and a size, and working out what it is stays yours.

Muddy water, rain and dusk dim them. **And the fish can see you** — sprinting along the bank, landing
hard, splashing in the shallows all spook the water you are standing over, and a spooked fish does not
bite. Move up quietly, or wait for it to settle.

## Five ways to fish

- **Float fishing** — the classic pole. Set the float depth, watch it plunge, strike on the timing
  mini-game. Panfish and silver fish, close in.
- **Spinning** — active lure work. Cast and retrieve a spinner, spoon, wobbler or soft plastic to trigger
  pike, zander, perch and asp. A steel leader is not optional.
- **Long-cast bottom fishing** — feeder, bottom and carp rods reach far water and sit there with a loaded
  groundbait feeder. Patient, and where the big bream, tench, carp and catfish come from.
- **Ice fishing** — drill a hole with the auger, drop a short winter rod, work the mormyshka in a rhythm.
  A whole winter mode of its own.
- **The sea** — surf, boat and trolling blanks, saltwater rigs and big-game fights. Cod, seabass, conger,
  halibut, tuna, marlin, swordfish, mako. Trolling tows a lure behind a moving boat and the pelagic
  species answer it.

## The fight asks you questions

Every run has a **course**, and you answer it with the movement keys: the fish tracks left, you lean on it
from the right; it goes deep, you pull back and get its head up; it comes up to jump, you put the rod
down. The boss bar names the course *and* the key, and the rod tip is dragged the way the fish is going,
so you can read the fight off the rod as well as off the bar.

Answering right is not a tax — it is what lets you work. The fish tires nearly three times as fast and the
run loads your tackle at half rate. Pull the same way it is going and you get none of that.

- **Your feet are tackle.** Backing away from the water is pumping with your legs: it wins line and loads
  the rod, the same trade a crank makes. Walk the whole way and you snap.
- **The angler runs out.** A stamina bar for the person holding the rod. It comes back only when you stop
  pulling, so the answer to tired arms is to stop rather than to click faster.
- **The rod bends under tension**, in six steps driven by the real stress on your tackle.
- **Over-pull and the line snaps.** A real breaking-strain model means big fish demand the right line and
  a drag you know how to use — and a heavier line genuinely buys you time.
- **Every species tires differently**, and small fry stop fighting like monsters.

## Tackle you build

- **The Tackle Station.** Right-click a Fishing Stall with an empty hand and it becomes a bench: pick a
  form, step the weight, feed it hooks, iron and string, take the finished tackle out. **Weight is a
  decision** — it sets your cast distance, it has to fall inside your blank's test window, and a lure's
  mass *is* its size, so a heavy pilker genuinely silences the small stuff.
- **Dye a lure at the bench.** The colour affects the bite.
- **The tackle box**, in four sizes, 9 to 36 slots. Tackle only — line, hooks, rigs, lures, bait, leaders.
  Rename it in the box, dye the inserts, break it and it keeps everything.
- **Every rod blank and every reel is craftable**, on one cost ladder.

## The catch

- **The keepnet is a grid, not a stack of slots.** A fish takes the space its body needs — a pike lies
  across four cells, a roach takes one — so you choose what is worth keeping.
- **Every fish shows its age.** A young fish is paler and greyer than an old one of the same species, and
  some carry named morphs on top of that: a golden tench, an albino, a stunted fish that is short and thin
  for its weight and looks it.
- **Eight legendary fish**, one per server each, with their own names and their own fights.
- Land one and you get a unique item carrying the **species, weight and length**.

## A living world

- **Over-fish a species and it thins out**, per chunk, recovering over time and faster in spring.
- **Stock a water yourself** and the species settles in — or does not, depending on whether the water
  suits it.
- **The electrofisher** (creative only) removes a nuisance species from one particular water, and can put
  it back.
- **A fisherman villager** who sells tackle, ready-made kits and assembled rods, and **buys your prime
  catches at prices that move**: overfish a species and its price falls; his **order of the day** pays
  ×2.5 for one species, and one seat on his counter always shows it.

## Progression

A bestiary **journal** with a page and an illustration per species, **angler levels** and a six-perk skill
tree, an **eight-stage quest chain**, and 22 hand-crafted advancements including some tricky and some
funny ones. Plus bait farms, a live-bait trap, an aquarium and a mini aquarium, cooking and fillets.

**The order of the day is the tutorial.** The fisherman names a species, and the journal entry for it is
one click away — so the game answers "what do I do now" with a specific fish, a place and a bait instead
of a menu.

## Configurable

`config/riverfishing.json` is written on first run with every knob at its default: difficulty presets plus
individual dials for phantom bites, break sensitivity, depletion, line and hook wear, snags, foul-hooking,
how much the fish notice you, and where the trophy bar sits. A modpack can retune the whole thing without
patching.

## How to play — the first hour

1. **Get bait.** Dig dirt, grass or sand with a **shovel** — worms drop about one time in ten. Later:
   breed your own on a worm or maggot farm, gather live bait in a bait trap, or buy from the fisherman.
2. **Craft tackle.** A **Stick Rod** is three sticks on the diagonal. **Hooks**: one iron nugget makes two
   #16 hooks; add a nugget to step up a size (a bigger number is a smaller hook). **Line**: a ring of
   8 string makes the thinnest mono; put a line in the centre of another ring to thicken it a step.
3. **Assemble the rod.** Hold it and **sneak + right-click**. Reel-less rods take a line; a reeled rod
   takes a reel first and the line spools onto it; bottom rods add a swappable rig. Incompatible parts are
   refused with the reason written in the window.
4. **Load the rig.** Drop a **hook** and **bait** into the rig's inline slots. Predators want artificial
   lures, not worms, and they bite through a bare line — fit a leader.
5. **Feed the spot** (optional). Right-click water holding **groundbait**; the fed zone decays over about
   ten minutes and the type decides what it pulls in.
6. **Cast.** Hold right-click to charge, release to throw. An under-loaded blank cannot reach.
7. **Watch the float — silently.** There is no "Bite!" text. The float plunges, the line twitches. Float
   rods run a small timing mini-game: strike while the marker is in the green.
8. **Fight it.** Read the course off the boss bar, answer with the movement keys, ease off when it runs,
   and watch your stamina.

Read the **journal** for a per-species "how to catch", or scan the water with the **fish finder** to see
what is biting and how the pressure is trending.

## Admin commands

- `/rffish unlockall` — fill the journal so every quest goal is met.
- `/rffish reset` — wipe your records, XP and quests.

## Wiki and community

**[qwazar14.github.io/riverfishing](https://qwazar14.github.io/riverfishing/)** — 24 pages in **English,
Russian and Ukrainian**, one URL with a language switcher. Every rod, reel, line, rig, bait, species,
block and mechanic with the actual numbers, and every recipe drawn as a real crafting grid generated from
the recipe files, so the wiki cannot drift from what the game loads.

**[discord.gg/Kk2nKvsuRh](https://discord.gg/Kk2nKvsuRh)** — bug reports and catches equally welcome.
Several features in this mod exist because a player asked for them by name. A report with a screenshot and
your mod list gets fixed far faster than one without.

The mod speaks **English, Russian and Ukrainian** in full — 927 strings each, with real angling vocabulary
and real fish names rather than transliterations.
