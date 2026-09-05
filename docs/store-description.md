# River Fishing 🎣

*The project description for CurseForge and Modrinth. Paste as is. The per-release copy lives in
[docs/patchnotes/](patchnotes/).*

---

A **realistic river, ice and sea fishing simulator**, built to be the thing a modpack is about rather than a minigame beside it. Fishing here is a process, not a click on the water: you assemble a rod from a blank, a reel, a line and a rig; you mix your own groundbait and match bait, hook size and leader to the fish you actually want; you read the water, the season, the hour and the barometer; and then you fight what takes.

Nothing bites by luck. A data-driven bite engine weighs your whole setup against every one of **107 species** under the current conditions and decides both _what_ takes and _how long you wait_.

And since 0.9.0 the water is something you **keep** as well as fish. Every fish you land carries a card with its genes on it, a tank breeds them the way Mendel said, koi come in seventeen varieties you build rather than find, and the world has geography — four faunal provinces, so a taimen and a peacock bass never share a river because the temperature happened to match.

## Versions

*   **1.20.1** — Fabric & Forge
*   **1.21.1** — Fabric & NeoForge
*   **26.1.2** — Fabric & NeoForge
*   **26.2** — Fabric & NeoForge

**Requires [Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api)**; on Fabric also **Fabric API**.

The mod runs fine alone, but the balance is written around two optional add-ons:

*   **Serene Seasons** — the full seasonal calendar. Without it the mod keeps its own: 24 days a season, a 96-day year, so spawning runs, the winter slow-down and the spring restocking work everywhere.
*   **Biomes O' Plenty**, **Terralith** and **Oh The Biomes You'll Go** — a richer world for the habitat factors to work in. Two hundred modded biomes have their water named: salt, fresh, swamp, and which of them are cherry groves by another name (a sakura grove holds koi).

None is version-locked: Serene Seasons is reached by reflection and simply falls back to the mod's own calendar if it is absent, and the biome mods are read through biome **tags** rather than referenced in code at all. The [compatibility page](https://qwazar14.github.io/riverfishing/) lists the exact versions every build is tested against.

**JEI** and **Jade** are supported on 1.20.1 and 1.21.1.

## The rod is a real object

All **13 blanks are modelled**, hand-built piece by piece — grips, reel seats, guides — and the one in your hands is drawn at its true length. A feeder rod is three blocks of carbon, because a feeder rod is 3.9 metres of carbon.

**They bend as a chain.** A blank is cut into sections, up to eight on the sea spinner, and each joint turns a little more than the one behind it, so the tip travels furthest and the butt barely moves. The bend is continuous, driven by the fish's pull against that blank's own power class — a 1.8 kg bass on a trolling rod barely marks it, the same fish on an ultralight folds it over.

**And it bends _toward_ the fish.** Not down — toward. A fish running left lays the whole blank over to the left; one sounding drives the tip down; one coming up to jump lifts it. Look down past your own line and the rod rolls over and bows up toward it, because that is where the pull is.

**The blank has mass.** Each rod carries its own spring, so turning your view leaves the tip trailing and it overshoots on the way back. A fish hitting the line kicks that same spring and the rod visibly yanks.

**The line is threaded** off the spool, through every guide ring, out of the tip and down to the water, drawn in the colour, thickness and transparency of the line you actually spooled. **The reel turns** — all eleven are modelled and seated on the rod's own seat.

Not to your taste? `/rfrod blank off` puts the flat sprite back, and the physics has its own switch. Both persist.

## You can see into the water

Lean over a lake and the fish are there — each one holding its own position and heading, turned the way it is swimming, its tail on a slow beat. A shoal of roach reads as a shoal and keeps its numbers; a predator that notices your bait comes to look and loses interest; carp roll, salmon leap. A single big shadow on the bottom is a single big fish, and you will want to know what it is.

What you never get is a label. You get a shape and a size, and working out what it is stays yours. The water shows the fish that bite: the rare ones pass through one hour in four.

Muddy water, rain and a roof over the water dim them. **And the fish can see you** — sprinting, walking heavily, jumping, breaking blocks, wading in the shallows, a boat under way, even your own shadow thrown across the water at a low sun. A spooked patch stops biting and the visible fish bolt. Crouch, stand still, and it forgets you in thirty to ninety seconds. This is what a long cast is _for_: what you do on the bank does not reach out there.

## Every fish carries its card

A fish you caught is not three grey lines. It is a framed card in the colour of its standing, with badges — trophy, morph, legend, prime, foul-hooked, netted, poached — and under them: what a fisherman pays for it, its **nature** (timid, wary, greedy, bold — rolled when it took, and what you felt in the fight), where it was caught, its size class and sex, its weight and length, its Latin name, who caught it, when, and on which rod. Hold Shift and the card says _how_: bait, water, time, season, weather, the bed it lay on, the hole or drop-off it was holding on, its genes and its pattern index.

The card is not decoration. A **contract** reads it to see whether the fish was caught on its terms. The **aquarium** reads the sex and the genes to breed. The **counter** reads the grade to price it. A fish netted out of the water carries a card too, and it says so.

## Six ways to fish

*   **Float fishing** — the classic pole. Set the float depth, watch it plunge, strike on the click — then a timing bar for the pull-out decides whether it comes over the bank. Panfish and silver fish, close in.
*   **Spinning** — active lure work with fourteen lure forms. A popper is fished on the surface in a pop-and-pause cadence that ends in a visible blowup; a wobbler or crankbait only works on a strict crank rhythm; a spinnerbait wants weedy coloured water, a bladebait cold water and vertical work, a swimbait the trophy end, a wacky worm finesse. Against toothy fish a leader is the difference between a fish and a cut line — and which one matters: steel is bomb-proof but glints and costs you bites, fluorocarbon is near-invisible and earns them, titanium is both.
*   **Long-cast bottom fishing** — feeder, bottom and carp rods reach far water and sit there with a loaded groundbait feeder. Patient, and where the big bream, tench, carp and catfish come from.
*   **Ice fishing** — drill a hole with the auger, drop a short winter rod, work the mormyshka in a rhythm. A whole winter mode of its own.
*   **The sea** — surf, sea-spinning and boat blanks, the heavy reels and lines to match, and big-game fights. Cod, seabass, conger, halibut, tuna, marlin, swordfish, mako. How deep and how open the water is decides what lives there.
*   **Trolling** — hold a steady speed in a boat and the line goes out by itself, the lure working astern. Mahi-mahi, wahoo and tuna, with marlin and sharks further out.

## The fight asks you questions

Every run has a **course**, and **you answer it with the camera**. Lean your view against the way the fish is going and you are pulling against it; the view is re-anchored every time the course changes, so it is where you look _relative to the run_ that counts. Four arrow keys are there as a quiet override and are rebindable in Minecraft's own controls, but you never have to touch them.

**Nothing tells you which way it went — the rod does.** There is no arrow on the screen and the boss bar names no direction. The blank is the instrument: it lays over toward the fish and loads with the pull, and reading it is the skill.

Answering right is not a tax — it is what lets you work. The fish tires nearly three times as fast and the run loads your tackle at roughly half rate. Pull the same way it is going and you get none of that.

*   **Your feet are tackle.** Backing away from the water is pumping with your legs: it wins line and loads the rod, the same trade a crank makes. Walk the whole way and you snap. While a fish is on you cannot step off the edge of a block, so a pier is not a trap.
*   **The angler runs out.** Holding a rod against a running fish costs _you_, and pointing it the wrong way costs more. It comes back only when you stop pulling, so the answer to tired arms is to stop rather than to click faster.
*   **Over-pull and the line snaps** — a real breaking-strain model, so big fish demand the right line and a drag you know how to use, and a heavier line genuinely buys you time.
*   **The giants are landable.** The pull a fish demands tapers above 20 kg, so a 400 kg marlin asks for 210 kg of tackle rather than the 802 kg the linear law wanted. Nothing under 20 kg changed.
*   **The last dash at the bank is a roll, not a ritual** — 35% for an ordinary fish, 85% for a trophy on a hard pattern, never for a boot. Rolled once, and a failed roll is a quiet landing.
*   **Every species tires differently**, and small fry stop fighting like monsters.

## Groundbait you mix yourself

There is **one** groundbait — Base Groundbait, from wheat seeds and bread — and everything else is what you put _in_ it. Drop the base in a crafting grid with up to eight other things from a 27-item pantry and the mix takes on a **grind** and a **richness** of its own.

Every one of the 107 species wants its own pair. A bleak wants fine and lean, a carp wants coarse and rich, and a mix aimed at one is actively wrong for the other. Feeding coarse is how you stop catching tiddlers.

**You cannot overfeed a spot.** Throw as much as you like — more feed is more fish. What a mix cannot do is out-fish its own contents: how strong a swim can get is capped by what went into it, never by how much you threw. The decision is not _how much_. It is _what of_.

## Tackle you build

*   **The Tackle Station.** Right-click a Fishing Stall with an empty hand and it becomes a bench: pick one of twenty forms, step the weight, feed it hooks, iron and string. **Weight is a decision** — it sets your cast distance, it has to fall inside your blank's test window, and a lure's mass _is_ its size, so a heavy pilker genuinely silences the small stuff.
*   **Dye a lure**, at the bench or in a plain crafting grid, mixing dyes the way you mix leather armour. The colour is read against the light — time of day, weather, depth, biome, season — and worth up to +35% on a match.
*   **The tackle box**, in four sizes, 9 to 36 slots. Tackle only. Rename it, dye the inserts, break it and it keeps everything.
*   **13 rod blanks, 11 reels, 23 lines, 11 rigs and 9 hook sizes.** The lines and hooks craft as ladders — each step is the one below it ringed with string, or plus an iron nugget. The blanks and reels each craft on their own from vanilla materials.
*   **The cast gauge reads in metres** — the distance the line will land at, on an oak-and-brass sheet, with the band an under-loaded rig cannot reach hatched red.

## The catch

*   **The keepnet is a grid, not a stack of slots.** A fish takes the space its body needs — a pike lies across three cells and a big one fills ten, a roach takes one — so you choose what is worth keeping. A keepnet sells to the fisherman whole: prime fish at the market price, the rest at half.
*   **A fish wears its age and its genotype.** A young one is paler and greyer than an old one of the same species; some carry named morphs — a golden tench, a humpbacked fish out of an over-stocked pond, a stunted one that is short and thin for its weight and looks it. A carp is drawn as the scale cover its genes gave it — scaled, mirror, linear or leather — and every carp and koi wears a **pattern** from twelve families, moved a notch by a thousand-point index. Twelve of the thousand are **gems**: the whole fish one saturated colour, and the counter pays six times over.
*   **Eight legendary fish**, one per world each, with their own names and their own fights — Grandfather Sazan, the Tsar-Fish, the Queen of the Snags, the Master of the Pit, the Leviathan, the Abyssal Demon, Old Ridgeback and the Megalodon.
*   **Oily fish render down.** Nine species give fish oil in a furnace, on a campfire or in a brewing stand, and a **Potion of Fish Oil** swims (dolphin's grace and water breathing), mends (regeneration and resistance) and clears mining fatigue outright. Three small fish grind into fish meal — bone meal for the fields, protein for the groundbait.

## Breed them

Right-click the **aquarium** and it opens: six fish slots, food, groundbait, water, a result slot and two module slots. Put a mature ♀ and ♂ of one species in during its spawning window — every one of the 107 species has one — keep them fed and the water clean, and in three days there is **roe**. Take the parents out and it **incubates on the tank floor**: loose pale beads, then eye spots, then the milky dead ones, then tails, then **fry** along the glass. Fry are an item: throw them into water to stock it, sell them by the bucket, or lift them out of a stocked water with the fry trap.

**Every fish has a genome**, and it is on the card in the open. Four loci on everything — size, colour, vigour, fertility — a pair of alleles each, inherited the way Mendel said: an `SS` line throws bigger fish, `VV` survives the egg, `FF` lays bigger clutches. Carp carry two more, the real two-locus scale system: mirror breeds true, leather never can, because the nude allele kills the fish that inherits it twice — which is exactly why a leather carp is worth more.

**Koi are bred, not found.** Five colour loci — white ground, red, black, lustre and the tancho crown — and seventeen named varieties fall out of the combinations. Nine come out of the water; tancho, yamabuki, platinum ogon and the other metallics are bred and nothing else. A kohaku line breeds true; a tancho comes out of a tancho line; the lustre reaches your pond only on a wild kujaku or gin bekko, about one koi in thirteen. One drawing paints all seventeen.

**Species cross where they cross in life.** A sazan and a carp are one animal and spawn freely; beluga × sterlet is bester, farmed since the fifties; salmon × trout, zander × volga zander, roach × rudd and the bream complex each carry a strength you read off the egg count. The silver crucian clones herself — her clutch is a copy of her whoever the father was, which is exactly how she displaces the golden one wherever they meet.

## A pond you keep

*   **Stocking is a brood, not a dice roll.** Release one mature pair — the card says which sex — or thirty fry, and if they live through the species' spawning window in water that suits them, the species is settled for good. The release message is a checklist: sexes, fit, days to settle. What comes out of a stocked water is a founder until the brood has spawned, and their cross after it.
*   **Claim a pond with a sign.** Within three blocks of a body of up to 600 blocks — a dug pit, a village pond, never a river. No wild fish move in; the depth, width, biome and province gates step back, so a carp lives in a dug pit; your own net is legal.
*   **Five water upgrades**, counted within 24 blocks: an aerator, a snag pile, a gravel bed, a warm outflow and a feeding station that shows its fill level. All five double as aquarium modules.
*   **A settled species changes the water.** Grass carp eat the weed; silver carp clear it and the sight hunters feed harder; carp muddy the bed and the salmonids leave; a settled pike keeps the small cyprinids down. A pond with a pair in it grows on its own once every spawning window, more with a fertile bloodline and good cover — and everything ever released is averaged into the water's bloodline.
*   **Nets, and what they cost.** A seine and a cast net haul fish from a water without a rod. They are for the pond you stocked yourself: haul from anyone else's water and every fisherman in the village knows — your reputation drops, the water empties twice as fast, and the fish carries POACHED on its card for ever. Restitution is fish released back into wild water, five kilograms a point.
*   **Over-fish a species and it thins out**, per chunk, recovering over time and faster in spring. **The electrofisher** (creative only) removes a nuisance species from one water or puts any species into it — and what it culled stays culled.

## The world has geography

Minecraft has weather, not geography: the same swamp repeats to the world border. Every world is now cut into **four faunal provinces** — palearctic, nearctic, neotropic, indomalaya — by the seed, in organic regions about three thousand blocks across. A species outside its province is not rarer; it is **absent**. The sea is undivided, and so are the fish people have carried everywhere. What that leaves is a real map — fifty-nine species in the palearctic, twenty-four in the nearctic, eighteen in the neotropic, fifteen in indomalaya — and a reason to walk. Sixteen specialists ask for a conjunction on top: a taimen wants cold _and_ a river, an arapaima warm _and_ jungle. The sounder names the province you are standing in, and a claimed pond stands outside the gate entirely.

## Contracts and reputation

The fisherman posts **three jobs a day** on a board beside his counter. Take one as a paper — _three bream from 500 g, from a river, on a float rod, at night_ — and the fish's own card says whether it was caught on those terms, so the fish can sit in a chest for a week with the rod in another one. Bring the paper back with the fish and he pays in emeralds, XP and **reputation**, which opens a shelf at his counter nobody else is shown. Two papers at a time, seven days each, fish in a keepnet count. One post in two on the third line is a fry order; a clutch of roe sells too, sturgeon and salmon roe at three times the fish's price.

## The fish finder is an instrument

*   **Right-click — the section.** The water along your aim, the real bed metre by metre coloured by what it is made of, the fish standing where their depth is met. Click a species for what the mod knows about it here; click a blocked one for _why it will not bite_.
*   **Crouch + right-click — sounding.** A marker cast five blocks wide measures the bed and finds holes and drop-offs. Fish hold on them; casting on one is worth a real bite bonus.
*   **Hold it — the strip.** A sounder trace scrolls on the HUD while the finder is in your hand, with a needle to the nearest mark.
*   **The chart.** Everything you have ever sounded, pan and zoom, out to sixty-four blocks a pixel with the provinces painted under the water. It belongs to the sounder and lives in the world save: sell a surveyed finder and you are selling the survey.
*   **The bottom moves the bite** — cyprinids like mud and clay, predators rock and gravel, salmonids gravel — and the sample view lists the ledger of the water in front of you: settled species, stock, brood, bloodline, upgrades in reach, the province.

## Progression

A **journal** that is a full in-game encyclopedia: eight tabs, a page for every one of the 107 species with its own hand-drawn illustration — no gaps, no placeholders — a family rail, a live search, sortable tables of every bait, lure and piece of gear with the numbers the engine actually reads, and every recipe drawn as the crafting grid it is. **30 written guides** on top of that, from the drag to the sea giants to genes and geography. The species card is built server-side, so it is complete on a dedicated server too.

**Angler levels** through four ranks and twelve gates, a **six-perk skill tree**, an **eight-stage quest chain** that ends at the sea, and **22 hand-crafted advancements** including some tricky and some funny ones. Plus bait farms, a live-bait trap, the aquarium and a mini aquarium, cooking and fillets.

**The order of the day is the tutorial.** The fisherman names a species, and the journal entry for it is one click away — so the game answers "what do I do now" with a specific fish, a place and a bait instead of a menu.

## Multiplayer

Built for it, not merely tolerant of it. Everyone within twelve blocks sees your fight on the boss bar, and a friend can **net your fish for you** — crouched, empty-handed, close in, once you have it past 85% — which lands it for you and pays them angler XP. Every angler's line is drawn for everyone else, in the material they actually spooled. A pond, a contract board and a reputation are per player, and a fish finder's chart travels with the finder.

## Configurable

`config/riverfishing.json` is written on first run with every knob at its default: difficulty presets plus individual dials for phantom bites, break sensitivity, depletion, line and hook wear, snags, foul-hooking, how much the fish notice you, and where the trophy bar sits. A modpack can retune the whole thing without patching. The species that collect patterns are a data tag; a pack that wants another one adds it there.

## How to play — the first hour

1.  **Get bait.** Dig dirt, grass or mud with a **shovel** — worms drop about one time in ten. Later: breed your own on a worm or maggot farm, gather live bait in a bait trap, or buy from the fisherman.
2.  **Craft tackle.** A **Stick Rod** is three sticks on the diagonal. **Hooks**: one iron nugget makes two #16 hooks; add a nugget to step up a size (a bigger number is a smaller hook). **Line**: a ring of 8 string makes the thinnest mono; put a line in the centre of another ring to thicken it a step.
3.  **Assemble the rod.** Hold it and **sneak + right-click**. Reel-less rods take a line; a reeled rod takes a reel first and the line spools onto it; bottom rods add a swappable rig. Incompatible parts are refused with the reason written in the window.
4.  **Load the rig.** Drop a **hook** and **bait** into the rig's inline slots. Predators want artificial lures, not worms, and fifteen species bite through a bare line — fit a leader.
5.  **Feed the spot.** Craft **Wheat Seeds + Bread** into Base Groundbait (or buy it), then mix it in a crafting grid with up to eight things from the pantry and **right-click water** with the result. Feed coarse for big fish, fine for small ones.
6.  **Cast.** Hold right-click to charge, release to throw. The gauge reads in metres; an under-loaded blank cannot reach.
7.  **Watch the float — silently.** There is no "Bite!" text. The float plunges, the line twitches, and you strike. Lure rods and reeled float rods put a timing bar on the strike; a reel-less pole saves its timing bar for the pull-out.
8.  **Fight it.** Watch the rod, not the screen — it lays over the way the fish went. Lean your view against it, ease off when it runs, and watch your stamina.
9.  **Read the card.** What you landed says where it belongs and what it is worth. Release the ones the water needs; the board will pay for the ones it wants.

Read the **journal** for a per-species "how to catch", or scan the water with the **fish finder** to see what is biting and why the rest is not.

## Admin commands

*   `/rffish unlockall` — fill the journal so every quest goal is met.
*   `/rffish reset` — wipe your records, XP and quests.
*   `/rffish give <species> [variety|random|all] [count] [pattern]` — a fish made the way the water makes one, card and all; `all` on a koi is one of each of the seventeen.

## Wiki and community

**[qwazar14.github.io/riverfishing](https://qwazar14.github.io/riverfishing/)** — 30 pages in **English, Russian and Ukrainian**, one URL with a language switcher. Every rod, reel, line, rig, bait, species, block and mechanic with the actual numbers, an interactive catch calculator that answers "what does a nine-kilo carp want", the genetics and the provinces, and every recipe drawn as a real crafting grid generated from the recipe files, so the wiki cannot drift from what the game loads.

**[discord.gg/Kk2nKvsuRh](https://discord.gg/Kk2nKvsuRh)** — bug reports and catches equally welcome. Several features in this mod exist because a player asked for them by name. A report with a screenshot and your mod list gets fixed far faster than one without.

The mod speaks **English, Russian and Ukrainian** in full — 1,497 strings each, with real angling vocabulary and real fish names rather than transliterations.
