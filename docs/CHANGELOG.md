# Changelog

Full patchnotes. The short three-bullet form the in-game update checker shows lives in
[`updates.json`](../updates.json).

---

## 0.8.1 — the line goes where you cast it, and the giants can be landed

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) and NeoForge.

Full patchnote: [`docs/patchnotes/0.8.1.md`](patchnotes/0.8.1.md).

A repair release built from Discord reports. The first-person line stopped guessing which space it is
drawn in; the required pull now tapers above 20 kg, so the fish that no tackle in the mod could land
can be landed; breaches are rarer and their window has grace ticks. Two more of the carp family — kutum
and the naked carp — bring the roster to 93, and the crucian and mirror carp were repainted.

---

## 0.8.0 — groundbait you mix yourself, and a journal worth reading

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) and NeoForge.

Full patchnote: [`docs/patchnotes/0.8.0.md`](patchnotes/0.8.0.md).

---

## 0.7.0 — the water you can see into

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) and NeoForge.

*In development.*

### Somewhere to put the tackle

Asked for plainly by **arsen_501**: "неудобно все снасти в инвентаре носить". It is a fair complaint —
this mod ships seventeen line diameters, six hook sizes, four rig types and dozens of baits, and until
now they all lived loose in the same nine rows as your food and your building blocks.

- **Four sizes** — 9, 18, 27 and 36 slots. Each is a craft of the one below, and a chest is the heart of
  every recipe.
- **Tackle only**: line, hooks, rigs, lures, bait, leaders. Not rods and reels — those are what you hold,
  not what you rummage for, and a box that swallowed a rod would be a backpack wearing a tackle box's name.
- **Rename it in the box**, top row, no trip to an anvil. Renaming is the whole point of owning four of
  them, so it costs one click.
- **Dye the inserts** like leather armour. The colour shows on the icon, inside the open box and on the
  placed one, because all three read the same stack.
- **Right-click to set it down** and it faces you; **sneak + right-click to open**, in your hand or
  already placed. **Break it** and it keeps everything, because the placed block stores the box ITEM
  rather than a copy of its contents — there is one object, so the two can never drift apart.
- There is a **journal page** for it, and one for the keepnet beside it: two boxes, two pages — one for
  the catch, one for the tackle.

The fisherman sells four **ready-made kits** — float, pike, carp and saltwater — each named, dyed and
packed with bench-graded tackle. A kit is the answer to "what do I actually need for pike", in a form you
can carry to the water and open.

### The fisherman paints what he ties

Lures could be dyed at the bench since 0.6.0, and the colour changes the bite — but every lure the
fisherman sold came out the same factory silver, so the one system was invisible until you had a bench, a
lure and a dye. His racks now carry **mixed colours**, rolled the way a player's own dye job mixes, and
the weights are rolled inside each form's own ladder rather than being the stock size. Two spinners from
the same villager are two different spinners.

### Taking a species out of a water

Asked for by **vptareo-aao**: a way to remove a nuisance species from a particular water so it stops
getting in the way. The **electrofisher** does it — which is what real electrofishing is for, a survey
crew stunning a stretch of river to take out what does not belong.

Right-click water, get the list of what genuinely lives there, click a row twice. Nothing happens on one
click: a mis-click that empties a lake is not a mistake anyone should be able to make.

**Creative only**, as requested, and it refuses to fire outside creative as well as being uncraftable —
it permanently changes what a water can hold, and no survival cost would make that a fair trade.

The ban is checked in one place, the same function that decides whether a species lives in a water at
all, so a culled fish disappears from the bite engine, from the shoal you can see swimming, from the fish
finder and from stocking together. It is reversible from the same screen, and releasing one of that
species there lifts the ban as well: a fish that visibly swims in a water has to be catchable in it. The
screen states its own scope, because a water is a ~128-block region and an operator who thinks they
cleared a pond may have cleared a river. There is a **journal page** for it, sitting after every page
that teaches fishing — this one is not for anglers.

### The fish are in the water

Lean over a lake and you can see what lives in it. Every 12-block cell of water across the 3×3 chunks
around you carries its own shoal, drawn from the species that water actually holds — the same answer the
fish finder gives, because it is the same function (`BiteEngine.environmentScore`), so the shoal, the
finder and stocking can never disagree about a species again.

- **They are the population, not the record book.** Everyday fish, weighted by habitat, season, time,
  weather and biome, thinned by how hard the swim has been fished. A hammered spot visibly empties.
- **Shoals are shoals.** Anything under 900 g comes out as a group of three to seven sharing one circuit
  at nearly the same phase, so it travels together. A pike comes out alone.
- **Size is real size.** One block is one metre. A 20 cm roach is a flicker you have to be close to see;
  a three-metre sturgeon is a shadow you notice from the bank.
- **Depth hides nothing.** Only the water does — a swamp, rain, dusk. Lean over and look and you can make
  out what is sitting on the bottom.
- **Cells are pinned to the world, not to you**, so walking adds and drops shoals at the edges instead of
  dragging every fish along with you, and each shoal holds still while you fish it and changes by the hour.

Technically it costs almost nothing: each fish is a single textured quad carrying the species' own item
sprite, because the 256px fish icons turn into roughly a thousand quads each through a normal item model.
The whole thing is one packet every couple of seconds, and only when it has changed.

### A heavier line finally buys something

**Reported: top rod, thickest braid, snapped on a ten-kilo fish.** It was not bad luck.

Tackle tolerance was clamped at 1.0, so every line from *just enough* upward gave exactly the same
result — 108 kg of braid behaved identically to 22 kg against a 10 kg catfish, and the ten mono
diameters and seven braids above the minimum bought the player literally nothing. Meanwhile a run
against a standing drag filled the whole tension bar in about a second and a half regardless of what was
on the spool, because the run's load never looked at the line at all.

Strong tackle now changes the **load** rather than the ceiling. Tension stays normalised, so the bar, its
colour and the rod bend are unchanged, but everything fills it more slowly the further the tackle
out-guns the fish. Exactly-adequate tackle is untouched — the tuning that was right stays right; only the
over-gunned case, which was the broken one, moves. On that same ten-kilo catfish, a run at a closed drag
now takes 9 seconds to reach the limit on the heaviest braid instead of 1.6, and under-gunned mono is
harsher than before at 0.7.

### The fight has a direction now

The fight was deep but one-dimensional: tension up, progress up, and a forty-minute catfish differed from
a forty-second roach only in how long the numbers took. Every run now has a **course**, and the answer is
to put the rod the other way.

- It tracks **left** — pull right, **D**. **Right** — pull left, **A**.
- It has gone **deep** — **S**, pull back and get its head up.
- It is coming **up** to jump — **W**, rod down, which is what actually stops a fish leaping off the hook.

The input is the movement keys. Aim was tried first, because the server knows it for free, and it was
wrong on its own terms: countering a left-hand run meant turning thirty degrees away from the water, so
the mechanic asked you to stop watching the fight — and the leaning rod swung off screen with you.

Answering the run correctly is not a tax you avoid, it is the thing that lets you work: the fish tires
nearly **three times** as fast, the run loads the tackle at **half** rate, and — this is the part that was
missing — you can actually **gain line during a run**, most of a normal crank instead of the near-nothing
a wind into the run has always been. Pull the same way it is going and you get none of that. Standing
there with your hands off is in between: enough that a player who has not worked the mechanic out yet
still lands fish, slowly. The boss bar names the course *and the key* while the run lasts, because an
instruction you have to infer is not one.

**Runs last about 2.2× longer**, because a run is where all of this lives and the old two-second burst
was over before anyone could read the bar, decide and press. Held right, a long run is no harder on the
tackle than a short one used to be. Held wrong, it is a real problem. A tiring fish also shortens its
runs less sharply than it did, so the back half of a long fight no longer goes limp.

### A hooked fish nails you to the spot

The fight input is WASD, so without this a run turns into a foot race: you strafe half a chunk answering
three runs, which looks absurd and quietly beats the tension model by walking the fish in. While a fish
is on you move at **under a third** of normal speed — braced against the rod, shuffling, not jogging.

### Your feet are tackle too

This one works whether or not you have ever heard of the course mechanic. It reads one thing: whether the
distance between you and your hook grew or shrank since the last tick.

- **Backing away from the water is pumping with your legs.** It wins line and it loads the rod — the same
  trade a crank makes, at the same rate your tackle allows. It is how you actually beat a fish off a bank.
  A line you are pulling on does not go slack, so the rod does not bleed off while you walk: a few blocks
  at a time, then stop and let it settle. Walk the whole way and you snap long before you land it — on a
  carp that is about five blocks of room, on a marlin one. An open drag free-spools, so crouch-walking
  wins nothing at all.
- **Walking at the fish is slack**, the tension falls off fast, and a dead line is how a hook falls out.
  You get one warning; keep walking and it comes off. That is the only way to lose a fish that has nothing
  to do with how strong your line is.

An angler who stands still fishes exactly as before — nothing here punishes not knowing about it. Slack
only counts **between** runs, because a running fish keeps its own line tight; during a run your legs win
line at the same throttled rate a reel does, so you cannot walk a running fish backwards. Riding a boat
is the boat's movement and not yours, and a boot on the end of the line has no mouth to spit a hook out
of. Backing out of range of your own cast now says so instead of ending the fight in silence.

**The rod shows it.** A run drags the tip over the way the fish is going — left, right, up, down — which
is both what physically happens and where a player actually reads a fight. The boss bar names the course
as well, but the bar alone did not land: the first build had the text and not the rod, and the direction
simply did not register.

The seven fight patterns in the profiles become **direction scripts** rather than being replaced: a
greyhounding marlin keeps coming up, a sounding tuna is one long pull down, a relentless grass carp
alternates sides and never rests.

### The angler runs out too

A stamina bar for the person holding the rod. Winding costs it, and holding against a running fish costs
it — more when the rod is pointed the wrong way, because then you are fighting the rod as well as the
fish. It comes back **only when you stop pulling**: fully with the drag open, half with a standing drag
between runs, not at all while you wind.

Spent arms wind weakly and load the line harder, so the answer to being tired is to stop rather than to
click faster. That is the real technique the fight had never asked for — let the fish tire itself.

### Every species tires differently now

Every fish profile has carried a `stamina` value since profiles existed, and **nothing has ever read it**.
Fatigue came from weight alone, so a 2 kg pike and a 2 kg carp gassed out on the same tick — the one
difference the field exists to describe. It is in the fatigue maths now, measured against the table's own
median so the species that were tuned correctly are untouched, and clamped at both ends so a bleak is not
instantly limp and a tuna is not unkillable. The table itself was already good: bleak 0.15, gudgeon 0.20,
up to 1.00 for tuna, sturgeon and the two big carp. It simply was not plugged in.

The rest of the fight table got the review that goes with it:

- **The first fish anyone hooks was fighting like a pike.** The rotan weighs 90 g, sits at angler level 0
  and was set to *aggressive* — three runs at a 95% run chance, a new one every second and a half. It is a
  single calm run now.
- **Two level-4 carp were the hardest fights in the mod.** The wild carp and the grass carp ran nine
  times where a blue marlin runs eight. Both are down to a level-4 shape, and the effective run count now
  climbs with the level gate instead of crossing it.
- **`steady` stopped being the drawer.** It held everything unclassified, from a 30 g bleak to a 50 kg
  ray. The ray pulls once and then lies on the bottom being heavy, which is exactly the
  *active-then-passive* script; the channel catfish and the sterlet are real fighters and were filed next
  to the ruffe.
- **The swordfish was the only big-game fish in the mod recommending mono** — 0.50 mm, 25 kg of line,
  against an 80 kg fish, while every one of its siblings is on braid.

### Fish are wary of you

A short-lived fright value per patch of water — the counterpart to the chunk depletion that already
existed, but measured in seconds rather than days and never written to disk.

Running and jumping within six blocks, standing **in** the water, breaking a block nearby, a boat under
way, your own shadow when the sun is low behind you, and the cast itself where the tackle lands. While the
fish are frightened the bites stop and the visible shoal leaves. The only feedback is the rings running
outward across the surface and the water emptying — no message, no HUD.

It is a **field, not a flag**: what you do on the bank frightens the water around you, and a bait twenty
blocks out is disturbed only by its own cast landing. That asymmetry is what finally makes a long cast
worth making. Crouched and still is the one state that makes no noise at all. Recovery takes 30 to 90
seconds — a murky swamp forgets you quickly, clear shallows stay wary three times as long. Preset-driven
like every other harsh mechanic, and `"spook": 0` in the config switches it off.

### Every fish shows its age, and some show more than that

Fish are now coloured by a table rather than by a single flat sprite. A specimen's colour is read off its
own size: young fish are pale and silvery, old ones darken into their species' adult colour. A young bream
is a bright silver coin; an old one is deep bronze. This applies to every fish from every source — a
catch, a bait trap, a trade — and to the fish drifting in the water.

On top of that sit **morphs**, and the list is deliberately short: the stunted fish of an over-fished
pond, the deep-bodied one of an over-stocked pond, and xanthism — the gold mutation that turns up in
long-established stocked water. Each is a collection entry of its own on the species' journal
page, and each hangs off world state the mod already tracked and had never shown you: fish a swim down and
it starts handing out stunted fish; a stocked water that has taken hold starts throwing golden ones.

Everything a colour cannot honestly show was cut. An albino, a leucistic fish, a natural hybrid, a hooked
jaw, a set of lamprey scars — each of those is a drawing, not a tint, and the carp strains and koi
colouring already have their own drawn species here. The two morphs that matter most are **shaped** as well as tinted — a stunted fish is drawn
short and shallow, a humpbacked one short and deep — because a stunted fish that is only a slightly greyer
small fish is indistinguishable from a small fish.

**Not one new drawing.** Every morph is the species' own icon under a tint, a whitening pass and — for the
two shaped ones — a stretch of the sprite, from one table shared by the item, the journal and the water.
Three morphs across 79 species, plus age shading on all of them.

### The order of the day is the tutorial

Terraria's Angler is a tutorial wearing the costume of a chore: because the quest names the biome in
plain words, thirty quests walk a player through thirty habitats and they never once open a wiki. The
order of the day now does the same with the mod's own data.

The journal's quest page opens with today's order **written out as the recipe for catching it** — water
type, depth band, season, hour, bait, rig, rod, angler level — and every condition you already meet is
ticked against where you are standing and what you are holding. One panel, read once, teaches habitat,
depth preference, seasonality and bait choice at the same time.

Nothing on it is authored. Every line is the fish profile the bite engine reads, so the board cannot
teach something the engine does not do, and the server sends lang keys rather than sentences, so it draws
correctly in any language — including on a multiplayer client, which has no fish profiles at all.

**A fixed ladder of milestones** runs under the daily churn: every fifth order filled pays out a named
piece of kit — the echo sounder, a 6000 reel, a feeder rig, a digital alarm, a carp rod, a 10000 reel —
drawn on the board with the rungs you have not reached yet greyed out. Random rewards hold attention;
fixed milestones mean you always know what you are working towards.

It also closes a standing complaint: an order could name a fish the player's own fisherman would not
take. Writing the checklist forced the question, and the board now states the fisherman level that buys
the species — recorded as the trades are built, so it cannot drift from them.

### The mod says what it means

Two messages stopped being dead ends.

**The one that got away now has a size.** A shake-off, a snapped line and a bitten-through leader all
report roughly what was on the end of it — rounded hard on purpose, half-kilos up to ten and whole kilos
above, because you felt that fish and watched it turn, you did not put it on the scales. A figure to the
gram would be a precision the moment never had.

**"Nothing is taking here" now says why.** Every species is asked the bite engine's own question and the
most common answer is shown. If some fish here would take but your kit stops them, it names the kit — no
hook on the rig, nothing on the hook, a bait they do not eat, a hook the wrong size for their mouths. If
nothing at all is feeding, it names the water instead: the hour, the season, the weather, the depth.

It is a **hint, not an instruction**. It names the category and never the answer: "there are fish here,
but not for this bait" sends a player to the journal, where the species pages already list what each fish
eats. The diagnosis is one function inside the engine next to the gates it reports, so the advice can
never drift from the rule.

### Help for the player who is stuck, and only for them

A Super Guide rule, borrowed from Nintendo and kept to its spirit. Eight failures of the same kind on the
same rod class with nothing landed in between, and the mod offers — **one clickable line in chat** — to
open the journal page that teaches that flow. Land anything at all and every counter for that rod class
clears: the moment you work it out, the offer stops being on its way.

The limits are the feature:

- **Once per rod class per world.** A player who has seen the page has seen it, and the journal shelf
  holds every page anyway, so reading it again is a decision they make.
- **An offer, never a screen.** Nothing opens by itself, nothing blocks the game. Ignored, it costs one
  line of chat — which is the price of being wrong about someone being stuck.
- **The page fits the failure, not just the rod.** Eight snapped lines opens the tackle-stress page
  whatever you are holding; eight fish thrown off opens the drag page.
- **Honest bookkeeping.** Take the help and the next fish is recorded in the journal as landed after a
  hint. Nothing is withheld, nothing is locked; the record simply tells the truth.

It is also free diagnostics with nobody's data in it: every offer writes one line to the server log
saying which rod class and which failure tripped it. The flows that turn out to be unteachable will show
up there rather than in a bug report nobody bothers to write.

### The catch takes up room

The keepnet is a **grid**, and what you keep is the decision of the day.

A fish's footprint is worked out from the length and the weight the mod already records for every
specimen — nothing is authored per species. Length gives the long axis, one cell per 25 cm. The short axis
comes from Fulton's condition factor, 100·W/L³, the number an angling club uses to say how deep-bodied a
fish is: about 0.2 for an eel, 1 for a pike, over 2 for a bream. So an eel is a 4×1 bar, a pike is 3×1, a
carp is a fat 3×2, and a 30 kg catfish is a tapered 6×3 that eats half a box. Long fish turn to fit.

Four sizes, each crafted from the one below it: **small** 5×3, **medium** 7×4, **large** 8×5 and **huge**
9×6. It holds this mod's fish and nothing else — every cell is water. The megalodon, at 7×3, will not go
into anything under the large.

Three rules are in from the first build rather than promised for later, because the designer this is
borrowed from was explicit that a spatial inventory without them is a frustration generator:

- **Always rearrange.** Pick anything up, put it anywhere it fits, whenever you like.
- **The game never places for you, but it offers.** Shift-click drops a fish in the best place for it;
  TIDY repacks the whole box and hands back anything that no longer goes in. Neither is first-fit:
  every legal position in both rotations is scored by how much of the fish ends up against a wall or
  another fish, and the most contact wins. On a real dozen-fish catch that is one to two more fish in the
  same box than reading left-to-right and dropping each one in the first hole big enough.
- **It opens only in a pause** — on the held keepnet, never mid-fight.

The shapes are checked by a self-test rather than by eye:  prints the table for the real
species and asserts the invariants that must hold for every one of them.

### Flatfish lie flat

The flounder, the halibut and the ray are drawn from ABOVE — their sprite is the broad, eyed face of a
fish whose whole body is horizontal. Rendered upright like every other species they read as a bream
standing on its edge, which is the one thing a flatfish never does. They now lie down: in open water, in
the aquarium, and on the ground where you dropped them.

Flat means **parallel to the bottom** they travel along, and in the aquarium they work the floor of the
tank rather than looping through open water. Seen from the bank a flounder is barely anything to look at
— which is exactly what a flounder looks like from there, and you look DOWN at a flatfish anyway. In an
inventory slot the icon stays exactly as drawn: there it is a picture of the fish, not the fish.

### A fish can no longer carry another species' weight

**Reported as a 242 g ruffe — a fish whose range tops out at 150 g.** The specimen is rolled once, at the
cast, for whichever species is coming at that moment. A long wait then RE-PICKS the biter every fifteen
seconds as the light, the weather and freshly thrown groundbait change the odds — and that re-pick
changed the species while leaving the weight, the length and the trophy flag exactly as they were. A wait
that began on a perch and ended on a ruffe delivered a ruffe carrying the perch's weight.

Every out-of-range fish anyone has ever seen came through there. The re-pick now rolls the specimen again
against the new species' own profile.

It is also the second half of the trophy report: the flag was rolled for the ORIGINAL species too, which
is how an unremarkable fish could turn up flagged.

### A trophy is a big fish again

**Reported as a bug, and it was one.** A trophy was a dice roll: about 4% of catches won it, and the
weight was forced into the top of the range *afterwards*. That let an ordinary fish out-weigh a trophy of
the same species — the report was a 240 g ruffe that was ordinary next to a 233 g one that was a trophy.
In a mod that calls itself a simulator, the word has to mean what an angler means by it.

A trophy is now simply **a specimen in the top tenth of its species' weight range**, and nothing else. No
roll, no hidden flag. Everything that can put a bigger fish on your line — a heavy livebait, lure mass,
a well-matched kit, the Angler's Luck skill — now produces trophies by producing bigger fish, which is
how it works in the water.

The rate barely moves: across the 79 species the honest rule gives 2 to 7% of catches depending on how
skewed the species is (a catfish trophy stays rare, a ruffe trophy is commoner), median 4.0% against the
old flat 4%. **Angler's Luck** no longer adds a trophy chance — it flattens the size curve, which is the
only thing luck can honestly mean here. The config knob `trophy_chance` becomes `trophy_fraction`
(default 0.90 — raise it for rarer trophies).

And the bar is now **printed in the journal**: every species page states the weight from which a specimen
counts as a trophy. Nobody should have had to ask.

### Nine species from Florida, from the player who found the 0.6.1 bug

**Peacock bass, bullseye snakehead, Mayan cichlid, Oscar, striped bass, bluefish, jack crevalle, tarpon
and snook** — 79 species in total. The list came from idkwho0457_07869, the player whose report led to the
0.6.1 cast fix, and his own words became the fight profiles: a peacock bass that "breaks equipment" is
strength 0.95 with four runs, and a snakehead that "gives up usually quick" is the active-then-passive
pattern with stamina 0.35. That is a design brief no reference page would have given.

Nine species is 27 data files, 27 asset files and three languages, so all of it is generated rather than
typed: profiles with a vocabulary check against the existing 70 (an unknown bait key is silently ignored
by the engine, which would make a species quietly never bite), sprites palette-swapped from the
closest-bodied donor, and the models, cutting recipes, lang and registry wired by a script that copies
each per-version quirk from a species that already works in that tree rather than hardcoding it.

### The nine new species are drawn

They shipped with stand-in art, and the stand-ins were sharing proportions as well as pixels: the
bluefish, the striped bass and the tarpon all carried the asp's measurements, and the oscar carried the
mayan cichlid's. That table is what tells the keepnet how much of its own canvas a fish fills, so the
oscar — a deep-bodied cichlid recorded as a slim one — was being drawn half again too big to make the
"fish" fit its cells. All ten drawings are real now, measured rather than assumed, and the **bream** was
repainted while the brush was out.

### An eighth legendary: the Abyssal Demon

The **halibut** joins the legendary list — a 250 kg one-of-a-kind at 0.4% on a fish that already asks for
angler level 9. Named by the same player — they picked the halibut themselves — and the fish says so:
under its gold name it carries a dedication line, in all three languages. It is the first named-for-a-person
catch in the mod.

### Two trolling lures

A **skirted octopus jig** and a **giant spoon**, the last two items from the same player's list. They fill
a real gap rather than padding the tab: the heaviest existing lure step was 180 g while the boat blank
tests to 400 g and the trolling blank to 600 g, so both new ladders start where the old ones end —
60/120/250/400 g and 80/160/300/500 g.

Drawn rather than palette-swapped, because the existing eight are all small diagonal blades and the whole
point of these two is that they are big. Fifteen species now score them, weighted by what actually chases
a trolled lure: the pelagics rate the skirted jig highest, while bluefish and jack crevalle rate the big
spoon above everything else. Both dye like the other lures.

### The float is the float you actually rigged

A player described the float on the stick rod as "holey sticks", and both halves of that were true.

The bobber was drawn from the **rod class alone**, never from the rig. A stick rod is float-class but its
built-in primitive rig has no float slot and can never hold one, so the game drew a float that does not
exist. The flag was inconsistent too: only one of the five packet sites carried the ice-fishing check, so
an ice-fishing float rod showed a bobber at the cast and lost it at the bite.

It is now decided once, at the cast, from the rig, and crosses the wire as three states — the client
cannot see the rig, so a single bit was never enough:

- **nothing** on the surface for lure and bottom rigs, and for any rod fishing an ice hole;
- **a goose quill** — one red-over-white line — when a float rod has no float item rigged;
- **the float's own icon** in the world when one is: red antenna, a bulbous body red above the waterline
  and white below, a dark keel under it, with the proportions and palette read straight off the item
  texture rather than invented.

And it kills a long-standing bug nobody had reported: the old bobber was three vertical lines 0.018 apart,
so on the stick, bamboo and pole rods it rendered as a **tripled** float. Both new shapes are tightly
packed bundles cross-hatched in two axes, so they read solid from any angle and cannot separate into
stripes.

### The config file exists

`RiverFishingConfig` has had fourteen tunable fields since 0.3.0 and **nothing ever assigned one**. Every
server ran `realism`, and the `arcade` and `hardcore` branches were unreachable code — a modpack could not
soften line breaks, snags or depletion without patching the jar, while the mod page called itself a pack
anchor.

`config/riverfishing.json` is now written on first run with every knob at its default, because a config
nobody can discover is barely better than none. Numbers are clamped on read, so a stray minus sign cannot
produce a negative chance or a multiplier that breaks the fight maths, and an unknown preset name falls
back to realism **with a warning** rather than silently behaving as realism for no visible reason.

### 26.1.2 and 26.2: things that were quietly broken

The 26.x builds have had less playtesting than the rest, and a runtime pass over them found four faults
that a green compile cannot catch.

- **Nothing in the mod could be crafted.** 26.x dropped the `{"item": "x"}` ingredient form — an
  ingredient is a bare id, a `#tag` or a list — and every rod, reel, keepnet, line upgrade, lure and
  tackle box was thrown away at datapack load. Silently: the recipe book simply had gaps. Present since
  the first 26.x port, so it shipped in 0.5.0 and 0.6.x as well.
- **The shoal drew nothing.** 26.x gave items an atlas of their own, and the renderer was still asking
  the block atlas for fish sprites that are no longer on it.
- **"Trophy" was handed out for free.** Its criterion used the pre-1.20.5 item predicate shape, which
  26.x's codec ignores field by field — leaving an empty predicate, and an empty predicate matches
  anything. Picking up one dirt block earned it.
- **The Tackle Station never showed a result.** The container listener it used is gone on 26.x, and the
  hook that replaced it is not one anything calls for a plain container.

Plus: an unpainted tackle box had a transparent hole where its white insert should be (the dye tint's
default was written without an alpha byte), a painted one was placed grey (blocks still take their tint
from Java there), the fish lost their age shading everywhere the item model is drawn, a flatfish stood on
its edge on the bank, and the keepnet drew fish through the item-model pipeline, which on 26.x pushes
anything wider than its slot through an oversized-item pass and hands back a soft, washed-out copy of the
drawing.

### The wiki carries all 79 species

Adding a species to the wiki by hand costs five table rows across two files, times three languages — 135
rows of hand-typed numbers for a nine-species wave, which is exactly the part that gets skipped or
mistyped. They are now derived from the profile JSON by a generator with two properties it was built with
after being burned: it **refuses to invent vocabulary** (every label was harvested from rows already in
the wiki, and it asserts the word exists in the target file before writing), and it is idempotent per
**table** rather than per file, so a species present in one table and missing from another gets topped up
without being duplicated.

---

## 0.6.1 — the hotfix

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) and NeoForge.

A fix release. Every item came from a player report in the week 0.6.0 shipped.

### The cast no longer disappears on its own

The guard that checks you are still holding the rod your cast was made with compared the rod's item
**by memory reference**. An `ItemStack` reference goes stale as soon as anything rewrites the inventory
slot — common on a server, rare in single player — and from that tick the mod believed you had swapped
rods and ended the cast **silently, with no message**. It looked exactly like the rod reeling itself in
a moment after the cast, on every rod class. It now compares the hotbar slot, which is what "still the
same rod" was always meant to mean and cannot go stale.

### The Fishing Stall opens its bench on Forge

On **1.20.1 Forge only**, the Tackle Station screen was never registered, so right-clicking the stall
with an empty hand opened the menu server-side and drew nothing. Every other loader had it.

### The mod no longer teaches the wrong input

- `message.riverfishing.cast_spin` said **"Hold right-click to retrieve"**. Holding auto-repeats roughly
  five times a second, which both empties the retrieve at full speed and is too fast a cadence for a
  fish to take — the guide page already said the opposite. It now describes rhythmic clicks.
- **The rod tooltip names the rod's class on its first line** (`tooltip.riverfishing.rod_class.*`),
  derived from `RodType.rodClass()`: active rods are worked with clicks, float and bottom rods are cast
  and left alone. Previously every rod shared one identical hint. The winter rod is exempt — it is
  jigged in an ice hole and has its own line.
- **A new journal guide covers the waiting flow.** The shelf had thirteen guides, three of which taught
  RMB-cranking, and none about the two rod classes that are never retrieved.

### Wiki

The bottom-rod section now states that these rods are not retrieved and that a right-click outside a
bite ends the cast — in English, Russian and Ukrainian.

---

## 0.6.0 — the tackle & fight update

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) and NeoForge.

The headline is that **tackle now has a weight, and weight is a decision**. Before this, a rig was a rig
and a lure was a lure; now the grams you tie them at decide how far you cast, how long you wait, and
which fish will even look at it.

### The Tackle Station

Right-click a **Fishing Stall** with an empty hand and it becomes a tackle bench. Pick a form, step the
weight, feed it hooks, iron and string, take the finished tackle out. The block that gives a village its
fisherman is the same block you tie your own gear on.

- **6 bottom rigs and 8 lures**, each with its own weight ladder. Rigs come out with their hooks
  already slotted.
- **The weight is read by three separate systems**: your blank's cast-weight window, the lure-size
  filter, and cast distance. A lure's mass *is* its size — a heavy pilker genuinely silences the
  tiddlers, and it floors how small the hooked fish can be.
- **Dye a lure at the bench** — the colour affects the bite.
- Every piece carries the maker's name and its weight in the tooltip.
- The two heaviest classes of spoon, wobbler, jig and castmaster are **sea sizes**, and exist only here
  — the sea spinning, boat and trolling blanks finally have tackle inside their test windows.
- Hook link, balance and blade size are written and shown but **do not affect gameplay yet**; the
  tooltip and the wiki both say so.

Hand-crafted lures carry no weight stamp, which the game reads as 0 g: they add nothing to the cast and
never enter the size filter. The crafting recipes are still there, and the wiki now says plainly that
they are the basic path.

### The fight has physics

- **The rod bends under tension** — six bend steps driven by the actual stress on the tackle, visible to
  everyone nearby, not just to you.
- **A running fish loads the tackle by itself.** You no longer have to be cranking for the line to be in
  danger.
- **Fish tire out.** A long fight is now winnable by outlasting the fish instead of out-clicking it.
- **Small fry stop fighting like monsters** — a 200 g roach no longer behaves like a carp.
- Opening the drag always pays line, so crouch-spamming is no longer free.
- Trolling starts reliably, and the boss bar reads the fight correctly.

### The fisherman is not a fish market

The 0.5.0 trade tables were mostly fish, and gear almost never appeared.

- **Three offers per level** instead of two, drawn from a per-tier pool.
- **Tier 1 always includes one simple fish**, so the first trade is never a wall.
- **Rods are sold fully assembled only** — no more bare blanks you cannot cast.
- Tackle from the stall is **real bench tackle**, carrying the same weight stamp the bench writes.
- The stall also stocks plain vanilla goods — string, a boat, prismarine, a nautilus shell.

### Crafting

- **Every rod blank and every reel is craftable**, on one cost ladder — 24 recipes, no gaps.
- Hooks #2 and #1 joined the `riverfishing:hooks` tag, which had silently locked the two biggest hooks
  out of every lure recipe.

### Four new river species — 70 total

**Common dace, Volga zander, White-eye bream** and the **Round goby**, each with its own habitat gates,
fight profile and journal page.

Plus a realism pass over the existing species: weights and lengths corrected against the real fish.

### The wiki

An **18-page player wiki** in `docs/wiki`, written from the source rather than from memory, in
**English, Russian and Ukrainian** — every rod, reel, line, rig, bait, species and mechanic with the
actual numbers. There is also a single-page build with a language switcher, the species sprites inline,
and all 91 recipes drawn as real 3×3 grids generated from the recipe files, so they cannot drift from
what the game loads.

### Ukrainian

The mod now speaks **Ukrainian** — all 805 strings, with proper Ukrainian angling vocabulary and real
Ukrainian fish names, not transliterations.

### Community

There is a **Discord**: https://discord.gg/Kk2nKvsuRh — it is in the mod list, in the journal's guide
shelf, and on every page of the wiki. Bug reports and catches both welcome.

### Fixes

- The rod's assembly screen wrote tackle into a detached copy of the rod — changes could be silently
  lost. This is the bug behind "the rod is not assembled" reports.
- The "not assembled" message now says **which part** is missing instead of just refusing to cast.
- Five registry gaps a wiki pass exposed, including the saltwater blanks' durability and the drag curve
  being computed in two different places with two different answers.
- Cast-bar and pump-reel corrections from six playtest rounds.
- The 13 rod icons left over from 0.1.0 are gone; the break particles point at the drawn blanks.

---

# Список змін

## 0.6.0 — оновлення снастей і бою

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) і NeoForge.

Головне: **у снасті тепер є вага, і вага — це рішення**. Раніше оснастка була просто оснасткою, а
приманка просто приманкою; тепер грами, під які ти їх зв'язав, вирішують і дальність закиду, і час
очікування, і те, яка риба взагалі на це подивиться.

### Снастевий стіл

ПКМ по **Рибальській ятці** порожньою рукою — і вона стає верстаком для снастей. Вибери форму, набери
вагу, згодуй гачки, залізо й нитку, забери готове. Блок, який дає селу рибака, — той самий, на якому ти
в'яжеш собі снасть.

- **6 донних оснасток і 8 приманок**, у кожної своя вагова драбина. Оснастки виходять уже з гачками в
  слотах.
- **Вагу читають три різні системи**: тестове вікно бланка, фільтр розміру приманки й дальність закиду.
  Маса приманки — це її розмір: важка пілька справді відсікає дрібноту й задає нижню межу розміру риби.
- **Фарбування приманки на столі** — колір впливає на поклівку.
- На кожній речі стоїть ім'я майстра і вага.
- Два найважчі класи коливалки, воблера, джига й кастмастера — **морські розміри**, і вони існують лише
  тут: морський спінінг, човнове й тролінгове вудилища нарешті мають снасть у своїх тестових вікнах.
- Відступ гачка, баланс і номер пелюстки записуються й показуються, але **на гру поки не впливають** —
  про це прямо сказано і в підказці, і у вікі.

Приманка, скрафчена руками, не має штампа ваги, і гра читає це як 0 г: вона нічого не додає до закиду й
не потрапляє у фільтр розміру. Рецепти нікуди не зникли, і вікі тепер прямо каже, що це базовий шлях.

### У бою з'явилася фізика

- **Вудилище гнеться під натягом** — шість ступенів згину за справжнім навантаженням на снасть, і це
  бачать усі поруч, а не лише ти.
- **Риба на ривку сама навантажує снасть.** Тепер не обов'язково мотати, щоб волосінь була в небезпеці.
- **Риба втомлюється.** Довгий бій можна виграти витримкою, а не швидкістю кліків.
- **Дрібнота більше не воює як монстр** — плітка на 200 г не поводиться як короп.
- Відкритий фрикціон завжди віддає волосінь, тож спам присіданням більше не безкоштовний.
- Тролінг стартує надійно, а смуга боса показує бій правильно.

### Рибак — не рибний ринок

У 0.5.0 в обмінах була майже сама риба, а снасть майже не траплялася.

- **Три пропозиції на рівень** замість двох, з пулу на кожен ранг.
- **У першому ранзі завжди є одна проста риба** — перший обмін більше не глухий кут.
- **Вудки продаються лише повністю зібраними.**
- Снасть із ятки — **справжня снасть зі столу**, з тим самим штампом ваги.
- У ятці є й звичайні ванільні товари: нитка, човен, призмарин, мушля навтилуса.

### Крафт

- **Кожен бланк і кожна котушка крафтяться** — 24 рецепти, без прогалин.
- Гачки №2 і №1 додані до тегу `riverfishing:hooks`, який досі тихо не пускав два найбільші гачки в
  жоден рецепт приманки.

### Чотири нові річкові види — усього 70

**Ялець, берш, клепець** і **бичок-кругляк**, у кожного свої умови проживання, манера бою й сторінка в
щоденнику. Плюс прохід по реалізму: ваги й довжини звірені зі справжньою рибою.

### Вікі

**18 сторінок** у `docs/wiki`, написаних із коду, а не з пам'яті, **англійською, російською та
українською** — усі вудилища, котушки, волосінь, оснастки, наживки, види й механіки зі справжніми
числами. Є ще збірка в одну сторінку з перемикачем мов, спрайтами видів і всіма 91 рецептом,
намальованими справжніми сітками 3×3 просто з файлів рецептів.

### Українська

Мод тепер говорить **українською** — усі 805 рядків, справжня рибальська лексика й справжні українські
назви риб, а не транслітерації.

### Спільнота

З'явився **Discord**: https://discord.gg/Kk2nKvsuRh — він є у списку модів, на полиці гайдів у щоденнику
й на кожній сторінці вікі. Баг-репорти й улови однаково вітаються.

### Виправлення

- Екран збирання вудки писав снасть у відчеплену копію предмета, і зміни могли тихо зникати. Саме через
  це приходили скарги «вудка не зібрана».
- Повідомлення про незібрану вудку тепер каже, **якої саме частини** бракує.
- П'ять прогалин у реєстрі, які виявив прохід по вікі, зокрема міцність морських бланків і крива
  фрикціона, що рахувалася у двох місцях по-різному.
- Виправлення шкали закиду й викачування за шістьма раундами плейтесту.
- 13 іконок вудилищ, що лишилися з 0.1.0, прибрані.

---

# Список изменений

## 0.6.0 — обновление снастей и боя

**Minecraft 1.20.1 · 1.21.1 · 26.1.2 · 26.2** — Fabric, Forge (1.20.1) и NeoForge.

Главное: **у снасти теперь есть вес, и вес — это решение**. Раньше оснастка была просто оснасткой, а
приманка просто приманкой; теперь граммы, под которые ты их связал, решают и дальность заброса, и время
ожидания, и то, какая рыба вообще на это посмотрит.

### Снастевой станок

ПКМ по **Рыболовному прилавку** пустой рукой — и он становится верстаком для снастей. Выбери форму,
набери вес, скорми крючки, железо и нить, забери готовое. Блок, который даёт деревне рыбака, — тот же,
на котором ты вяжешь себе снасть.

- **6 донных оснасток и 8 приманок**, у каждой своя весовая лестница. Оснастки выходят уже с крючками в
  слотах.
- **Вес читают три разные системы**: тестовое окно бланка, фильтр размера приманки и дальность заброса.
  Масса приманки — это её размер: тяжёлая пилька действительно отсекает мелочь и задаёт нижнюю границу
  размера рыбы.
- **Покраска приманки на станке** — цвет влияет на поклёвку.
- На каждой снасти стоит имя мастера и вес.
- Два самых тяжёлых класса колебалки, воблера, джига и кастмастера — **морские размеры**, и существуют
  только здесь: морской спиннинг, лодочное и троллинговое удилища наконец получили снасть в своих
  тестовых окнах.
- Отступ крючка, огрузка и номер лепестка записываются и показываются, но **на игру пока не влияют** —
  об этом прямо сказано и в подсказке, и в вики.

Приманка, скрафченная руками, не несёт штампа веса, и игра читает это как 0 г: она ничего не добавляет к
забросу и не попадает в фильтр размера. Рецепты никуда не делись, и вики теперь прямо говорит, что это
базовый путь.

### В бою появилась физика

- **Удилище гнётся под натяжением** — шесть ступеней изгиба по настоящей нагрузке на снасть, и это видят
  все рядом, а не только ты.
- **Рыба на рывке сама нагружает снасть.** Теперь не обязательно мотать, чтобы леска была в опасности.
- **Рыба устаёт.** Долгий бой можно выиграть выдержкой, а не скоростью кликов.
- **Мелочь больше не воюет как монстр** — плотва на 200 г не ведёт себя как карп.
- Открытый фрикцион всегда отдаёт леску, так что спам приседанием больше не бесплатный.
- Троллинг стартует надёжно, а босс-бар показывает бой правильно.

### Рыбак — не рыбный рынок

В 0.5.0 в трейдах была почти одна рыба, а снасть почти не попадалась.

- **Три предложения на уровень** вместо двух, из пула на каждый ранг.
- **В первом ранге всегда есть одна простая рыба** — первый трейд больше не тупик.
- **Удочки продаются только полностью собранными.**
- Снасть с прилавка — **настоящая снасть со станка**, с тем же штампом веса.
- На прилавке есть и обычные ванильные товары: нить, лодка, призмарин, раковина наутилуса.

### Крафт

- **Каждый бланк и каждая катушка крафтятся** — 24 рецепта, без пробелов.
- Крючки №2 и №1 добавлены в тег `riverfishing:hooks`, который до этого тихо не пускал два самых больших
  крючка ни в один рецепт приманки.

### Четыре новых речных вида — всего 70

**Елец, берш, белоглазка** и **бычок-кругляк**, у каждого свои условия обитания, манера боя и страница в
дневнике. Плюс проход по реализму: веса и длины сверены с настоящей рыбой.

### Вики

**18 страниц** в `docs/wiki`, написанных из кода, а не по памяти, на **английском, русском и
украинском** — все удилища, катушки, лески, оснастки, наживки, виды и механики с настоящими числами.
Есть ещё сборка в одну страницу с переключателем языков, спрайтами видов и всеми 91 рецептами,
нарисованными настоящими сетками 3×3 прямо из файлов рецептов.

### Украинский

Мод теперь говорит **по-украински** — все 805 строк, настоящая рыболовная лексика и настоящие украинские
названия рыб, а не транслитерации.

### Сообщество

Появился **Discord**: https://discord.gg/Kk2nKvsuRh — он есть в списке модов, на полке гайдов в дневнике
и на каждой странице вики. Баг-репорты и уловы одинаково приветствуются.

### Исправления

- Экран сборки удочки писал снасть в отцепленную копию предмета, и изменения могли тихо пропадать.
  Именно из-за этого приходили жалобы «удочка не собрана».
- Сообщение о несобранной удочке теперь говорит, **какой именно части** не хватает.
- Пять пробелов в реестре, которые вскрыл проход по вики, в том числе прочность морских бланков и кривая
  фрикциона, считавшаяся в двух местах по-разному.
- Исправления шкалы заброса и выкачивания по шести раундам плейтеста.
- 13 иконок удилищ, оставшихся с 0.1.0, убраны.

---

## Earlier releases

Condensed; see the [update feed](../updates.json) for the in-game bullets.

- **0.5.0** — sea fishing: the ocean, trolling, big-game fights and 36 new species (66 total);
  legendary one-per-world named fish and a dynamic market; stocking 2.0.
- **0.4.0** — tackle stress and probabilistic breaks, live bait 2.0, topwaters and the splash attack,
  the America mini-pack.
- **0.3.0** — the carp line-up and the koi collection, aquarium and trophies, ice fishing, angler
  skills, pond stocking, bait crops, lure dyeing.
- **0.2.0** — multiloader (Fabric and Forge/NeoForge from one codebase), rod pods, bite alarms, journal
  and quests, the fisherman villager.
- **0.1.0** — the first public build: NBT rods, the data-driven bite engine, the fight mini-game.
