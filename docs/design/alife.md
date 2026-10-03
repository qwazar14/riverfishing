# §alife — the water lives on its own (1.1.0)

The old `BiteEngine` is a pure function: conditions × the player's tackle → species weights → a wait.
Fish exist only while a cast asks about them. 1.1.0 replaces that with an A-Life: every body of water
holds agents that get hungry, move, eat, learn and are caught **whether or not anyone is fishing**. The
player's job becomes reading that water and finding an approach to it; tackle no longer creates fish.

Core: `common/.../alife/` — `Species`, `Lake`, `AlifeSim`. Zero Minecraft imports (only the mod's own
`Season`/`TimeOfDay`/`Weather` enums).

```
gradlew :common:compileJava
java -cp common/build/classes/java/main com.riverfishing.alife.AlifeSim
```

## Decisions (user, 2026-09-25)

- **Agents are groups + individual trophies.** A shoal of 30 roach is one agent with a count and a mean
  weight; a trophy carp is its own agent with its own memory, and a slot that refills ~20 days after it
  is taken.
- **Ships as 1.1.0**, boilies/flavours (§boilies) are built on top of it.
- **The old engine stays behind one switch during development and must be trivial to cut** — the new
  path sits behind the same `BiteEngine.Outcome` surface; removing the old one is deleting a branch.
- **Offline life:** a water nobody is near is not ticked; when next asked it catches up — up to 72 h
  stepped hourly, a longer gap decays the slow memories in closed form and lives only the last day
  (40 days away costs < 1 ms in the sim).
- **Overfeeding may be punished** (the 0.8.0 "never punish overfeeding" rule is dropped): a shoal
  filled with free feed bites less.

## The model

**Zone** — a part of a water body with one character: depth, bed (`FishingManager.bedType` codes, 4 =
mud), cover (weed/snags), natural food (0..1, regrows ~1/day), feed beds on the bottom, a short fright.

**Agent state:** zone, hunger 0..1, wariness 0..1, familiarity per bait/flavour key, count, weight.

**Each step (15 min online, 1 h offline):**
1. hunger rises at `0.12/h × activity`; activity is the profile's own season/time/weather maps (the
   same amplification the old engine gave the bite) × barometric factor;
2. the agent eats from its zone: feed first (that is how familiarity is learned), then natural food. A
   day's ration is ~2 % of body weight; one portion of feed is a kilogram. Predators eat shoals of
   species at most a fifth of their weight — which removes fish from those shoals;
3. it moves to the zone of best utility: `drive × food × share` (food shared with the other grazers'
   biomass there) + rest `(1−hunger)(0.3+0.5·cover)` + the daily rhythm (shallows at dawn/dusk and for big
   fish at night, the deep in summer-noon heat and all winter) − fright − predators present (prey only)
   − travel. Inertia 0.1 so shoals do not flicker.

**Drive** = hunger + (1 − hunger) × min(0.4, 0.4 × familiar feed lying there) — competitive feeding on a
known bed. Capped low on purpose: a full belly still bites less.

**Slow memory:** wariness half-life 48 h (trophy 96 h); familiarity 150 h; feed on the bottom 18 h;
shoals regrow logistically 10 %/day (×3 in spring) with immigration from 30 % of capacity.

**The cast** (`Lake.offer`): every agent within scent reach (e^−d/8 blocks) of the bait zone bites at
`1.6 × √count × drive × activity × appeal × nearness × (1 + familiarity) × (1 − wariness) ×
(1 − suspicion × size-sensitivity) × (1 − zone fright)` per game hour. `appeal` is supplied by the caller —
the old bait/groundbait/line/hook match score — and `suspicion` is what looks wrong (line visibility,
lead). The total gives the wait (exponential), the rates pick the agent. A catch is `Lake.take` (one
fish fewer, the shoal a little warier); a loss or a release is `Lake.spooked` (+0.4 / +0.6 wariness).

## What the sim proves (all asserted)

| Promise | Result (seed 42) |
|---|---|
| carp in the shallows at dawn/dusk and night, the deep at summer noon | 95–100 % at 19–08 h, 5–15 % at 10–12 h |
| a week of prebaiting makes carp bite in the swim after feeding stops | 4.0 → 7.6 bites/h, familiarity 0.99 |
| predators follow prey | pike share a zone with prey 92 % of hours |
| winter fishes far slower | summer 32 fish / 16 h, winter 0 in the shallows (fish are in the hole) |
| a released trophy is shy, then forgets | 3.3 → 1.5 bites/h next day, wariness 0.51 → 0.18 in six days |
| hammering a shoal thins it for real, it refills | roach 113 → 19 → 48 in two weeks |
| 40 days offline is cheap and sane | < 1 ms, feed gone, trophy forgot |

## Phases

0. ✅ core + sim (this doc).
1. ✅ **Water → zones + persistence** (`fishing/AlifeData`). A lake is StockedData's ~128-block region;
   a zone is a chunk with water, read with 16 heightmap probes (depth, most common bed, weed/kelp/lily
   cover). Each new zone brings 2 species ranked by `BiteEngine.habitatScore` (the old gates minus the
   season/hour/weather) × `base` with a seeded jitter, sharing 0.02 kg per water block; big species now
   and then a trophy (2–3× mean). Big predators (≥ 1 kg) come in groups of ≤ 3. Cap 96 agents; past it
   a shoal joins the nearest of its kind within 32 blocks. A zone remembers which species can live in it
   and nobody moves into a zone its species cannot live in (a sea fish stays out of the river). Only
   `touched` lakes are saved; an untouched one is rebuilt identically from the seed and dropped from
   memory after 10 min unasked. `/rffish alife` (op) printed the living water at the source's position — the whole `/rffish alife …` debug
   family was removed before release (2026-09-27); `/rffish census` still counts what lives in a water.
   Checked on a Fabric dev server: a river/sea region, 49 zones, 96 agents, surveyed and seeded in well
   under a second; over three game hours the shoals spread, hunger diverged, the sea species moved out to
   the open sea. Later tuning on the same river: 4 species a zone, cap 128, scent 12 blocks, BITE_K 8.
2. ✅ **Ticking — lazily.** No world tick: a lake is brought up to now whenever it is asked, and a waiting
   line asks every 15 s (`reEvaluate`), so fished water lives step by step and the rest catches up on
   the next visit. ponytail: a catch-up is lived under today's weather/season; log weather if it shows.
3. ✅ **Bite through agents** (`BiteEngine.evaluateAlife`, same `Outcome`). `speciesWeight` is split into
   water × `tackleWeight` (identical numbers on the old path); the living water asks `tackleWeight` of
   each agent near the bait, behind the habitat gates of the bait's column. `AlifeData.attach` hands
   the cast its lake (cast + every re-evaluation); the species is picked as before, then the agent of
   that species (`Outcome.pickAgent`). In the living water the old chunk depletion and the fed-swim
   −40 % wait are off — the population is real and feed works by moving fish. Groundbait (hand throw
   and feeder cage) calls `AlifeData.fed` → `Lake.feed` with the mix's diets. A trophy agent's catch
   weighs what the fish weighs (`rollFish`); landing calls `Lake.take`; every lost fight (break, throw,
   timeout) calls `Lake.spooked` from `endSession`. **Switch:** `RiverFishingConfig.alife` (config key
   `alife`, default on, deliberately NOT in the config template); cutting it = deleting the `ctx.lake
   == null` branches. **Still on the old engine:** claimed ponds, farmed/stocked regions, ice holes
   (phase 4). Not yet: `blockReason` from agent state, grusha extras and bycatch do not touch agents,
   casting does not `disturb` the lake (SpookData still frightens the spot).
   Debug: `/rffish alife bite <bait> [hook]` (old vs living water on one spot, per agent),
   `catch <bait>`, `feed <key>`. Checked on a Fabric dev server: worm on a river — old W 7.45 / living
   9.40 (both ~2 s); maggot 8 s / 23 s, corn 14 s / 80 s (the carp were two chunks away). Six throws of
   corn pulled the carp from two neighbouring chunks onto the swim, where they ate it and went quiet —
   overfed. Landed fish left their shoals; after a restart the touched lake came back identical (counts,
   wariness, the feed on the bottom).
4. ✅ (ponds) **Private ponds rebuilt — every fish a record** (`alife/Life`, `fishing/PondLife`). The old
   stocking design is discarded (user, 2026-09-25). A claimed pond is its own lake (key = its ledger key),
   one zone per chunk of its claimed columns, volume = the water in it. A roster shoal's count IS its list
   of `Life.Head`s (uid, sex, weight, born, trophy, legend, returned, last spawn, and the game's record:
   Card/Morph/Name). Release, rod (incl. grusha extras), net and fry trap take THAT head out; a released
   trophy/legend comes back as itself, a re-caught legend grants no achievement. Nets and casts within 2
   blocks of a claimed column count as the pond (the "wild water beside the pond" phantom source).
   **Life:** a mature ♀ (≥ 0.4 × mean) with a mature ♂ in the species' window (own calendar per hour, or
   Serene Seasons' today) lays `Genome.clutch` eggs as `Life.Roe` (public, for drawing later); roe dies
   off and is grazed (less in weed), hatches in 3 days into a fry batch; fry are eaten by predators (weed
   hides them) and starve past head room; at 16 days the survivors become heads (genes crossed, lethal
   never hatch, V locus 0.75/0.9/1.0) up to the room. Room: 0.25 fish and 0.4 kg a water block, +50 %
   with full cover; snags add cover, a feeding station/aerator add richness (re-read every visit). Room
   never kills an owned fish — it stops growth and starves fry. Growth: von Bertalanffy K 0.006/day
   when fed; natural food scales with volume, so a stocked pond grows on what it is given. Predators eat
   the smallest head they can swallow. Ponds catch up a whole year step by step (spawns are not lost).
   **Migration:** the first time a pond is opened, every old record becomes a head as itself, heads the
   old count had beyond the records become fish of the pond's genes/sex/avg weight, fry a batch; the old
   book stays on disk unread; `growAround` no longer touches ponds. Sign, finder farm view, ecosystem and
   the fry trap read the living pond. Sim: 1 male → net lifts 1 then nothing; a pair leaves ~13 young
   (weed 14, pike 8); fed young ~0.5 kg after a summer, unfed stay fry; a 12-block puddle stops at 3.
   Dev server: an old book of 3 migrated to 3 heads (2 f / 1 m); 3 stocked carp came out as exactly
   those 3 weights; a pair spawned in its window → 5 young; 4 ♀ + 2 ♂ → 25 young, 13 f / 12 m; a restart
   kept every head. Debug: `/rffish alife claim|pond|stock <sp> <g> <sex> [trophy]|legacy <sp> <n>`.
   ✅ **4c (2026-09-25):** wild water is living water too. A fish thrown into wild water passes the old
   bank checks (cull, hostile water, province) and then becomes a head of the region's lake with its card,
   badges and its releaser (`Owner`) — a trophy stays catchable as itself, a pair spawns there like in a
   pond; fry become a batch. The old wild stocking book of a region is read into heads once, when the
   region's lake is first built (`AlifeData.migrated`); a brood whose release spot a pond has claimed is
   left to the pond. Nets in wild water sweep the shoals within 24 blocks: a remembered fish its netter
   released is legal, everything else is poaching as before. The electrofisher culls a species out of
   the living water and "stocks" a shoal (a pond gets ten remembered fish). `growAround` is off entirely.
   **Why no bite** is read off the fish (`Lake.diagnose`): no fish at the bait (≈16 blocks) → where the
   nearest are (deep / weed / shallows / open); fish there but refusing the rig → the old gate of that
   species (bait / hook / depth…); otherwise the factor holding back the keenest fish near the bait —
   full, season, time, weather, wary, spooked, or "other". Also replaces the "sluggish" line.
   Dev server: an old wild book of 4 grass carp became 4 heads once (no duplicate after a restart), a
   released walleye swims in the river, a clownfish was refused. Ice holes stay on the old engine — they
   are to be rebuilt from scratch (user).
5. **Trophies** with history (the fish that got away is the same fish), stocking/ponds add agents
   (StockedData residency), spawning runs.
6. ✅ **Readable water** (2026-09-25, built, not yet seen in a client). `ShoalTracker.scanLiving`: the
   fish on screen are the living water's agents — region lakes around the player (3×3 corners of a 40-
   block view) and nearby ponds; one shoal per zone at its deepest surveyed water (`Zone.ax/ay/az`), a
   lane per agent: a pond's fish one by one (≤24 a group, real weight/variety/pattern/trophy), a wild
   shoal ~2.2·√count fish (≤16), fry a flicker; up to 320 fish in all (was 160). `ShoalPacket.Entry`
   gained group (the agent), variety, pattern and flags FEEDING (bottom feeder with food → bubbles),
   RISING (upper-water feeder at dawn/dusk → rings), HUNTING (predator with prey in its zone → a dash, a
   splash, small fish scatter), TROPHY, FRY. `ShoalState` matches fish by group + ordinal across ALL
   spots, so an agent that changes zone swims over (`travelling`: 2.5× cruise toward its new home)
   instead of blinking. Every old feature stays: jumps, spook flight, schooling, predators looking at
   the bait, the bank, item/3D modes, morph tint, flatfish. **Koi fix:** the flat shoal renderer drew
   `item/fish/koi_carp` (the white base) only; it now stacks the item's five layers tinted by
   `FishMorph.koiTint(variety, layer, pattern)`, and a carp draws its variety sprite
   (`Genome.drawnAs`); the item mode's stack now carries Variety+Pattern too. Wild koi get a variety
   from `Genome.wildKoi`. **Sonar:** with the living water the finder's `here` = species within 24
   blocks of the sounding with their count ("Nearby: n"), a species the water could hold but that is not
   near goes below with "none nearby right now"; the chart draws a halo per zone of fish within 96
   blocks (size √n, teal / amber with a predator, hover = top species × n and the total).
   **After the first client test (user):** far fewer species than before → 6 species a zone DRAWN by weight
   (not the top 4, so rare fish turn up), region cap 384 agents; fish circling inside one block → the bank
   is measured per depth layer (it was fixed by whichever fish asked first — a bottom fish down a pit),
   a fish in a cramped layer rises to the first roomy one, and the zone anchor is an open deep column,
   not the deepest pit; halos only via Shift+RMB → every sweep (the held finder's once-a-second one too)
   carries the shoals, the client keeps them for 2 min and fades them; too many splash sounds → one per
   1.5 s over the whole water, within 24 blocks, quieter; rings are silent. Perf: Math.hypot was 55 % of
   a step (JFR) → plain sqrt, neighbour zones cached, moves to the next chunk over (20 blocks) per step:
   a full sea region (64 zones, 384 shoals) steps in 0.65 ms, 72 h catch-up 39 ms (was 157). Gaussian
   move noise kept on purpose: uniform noise of the same width broke the pike-follows-prey behaviour.
   **§fish-world (user: "1, 2, 3 and 5", and a wow when you dive):** roles from the profile — BOTTOM
   (flatfish, catfish, sturgeon, rays, bottom fish ≥ 800 g: on the bottom, creeping and stopping), AMBUSH
   (pike-like, by id: hangs almost still in a small patch, dashes when hunting), SCHOOL (small peaceful
   open-water shoalers: tighter, faster, aligned), CHASER (other predators: patrol 1.6× wider, faster).
   REST when out of its hours (profile time factor < 0.8) or full: slow, on the bottom. Depth follows the
   day: deeper in summer-noon heat, higher at night. **Spawning:** in the species' window a shoal is
   pulled to spawning grounds (depth ≤ 3 with weed or mud, `Lake.spawningGround`), shows SPAWNING (up in
   the weed, splashing), and a WILD counted shoal now spawns too — roe with no parents hatches into fry
   that join the nearest shoal of their kind (≤ 1.5× its capacity), without touching the lake (a wild
   water is still re-grown from the seed). Sim: wild carp 81 % of their window on the grounds, shoal
   6 → 9. **Groundbait:** a feed remembers where it landed (`Feed.x/z`); the spot of the freshest feed
   goes in `ShoalPacket.Spot` and feeding shoals there are BAITED: they gather over the spot (home,
   bank and travel measured from it), nose down, bubbles and a silt cloud. **Diving:** eyes in water →
   up to 4·√count fish a shoal (never more than it has), 40 a group, 700 in all, clear water.
   **§alife-years (1000 game days, `AlifeYears`):** a wild pond balances — roach ~120-127, bream 27, carp
   7, pike 2, perch cycling 10-22 under the pike, total ~87 kg flat. Fished at ~440 fish a year it holds
   at roughly 40 % (roach 24-57, bream 12-20) and no species dies out (wild water regrows from 30 % of
   its capacity — it cannot be fished dead). The first run showed an UNFED private pond filling to its
   room with starved fish (8 carp → 171); now a shoal hungrier than 0.75 does not spawn and fry starve in
   a zone with no food (FRY_STARVE_H), so: fed pond 8 → 66 carp over ten years to its 276 kg room, unfed
   8 → 15. A full pond then stands still — no fish of a player's ever dies of age (open question).
   **§alife-age (user: "old age for wild water; the ecosystem must balance itself"):** wild counted shoals
   no longer regrow toward a capacity. Deaths: age (a lifespan's share a year, `Species.lifespanYears`
   from the max weight: 4..40 game years), starvation (hunger > 0.95: 0.02 %/h), predators, anglers;
   remembered fish in wild water die past their own span (0.8..1.2 × lifespan by uid); a lone wild
   fish (trophy slot) ages and starves too. Births: the spawn only — a wild shoal of ≥ 2 that is not
   starving (hunger ≤ 0.75) lays 10 eggs a fish (≤ 600) on its spawning grounds; fry are eaten by the
   predator BIOMASS in the zone (saturating at 5 kg), starve without food (0.3 %/h wild, 1 %/h in a
   pond), and join their shoal (≤ 3× its seeded size). Predators pick prey by the square of its numbers
   (switching: the rare prey gets a refuge) and small predators (< 1 kg) also graze the zone's food.
   A shoal under a quarter of its size (or 2) gets a stray from the next water every ~10 days. Ponds
   age nobody (`Lake.pond`). A ledger (`Lake.ledger`, `-Dalife.ledger=true`) prints births and deaths by
   cause a year. **1000 days:** unfished — roach 99-186, bream 33-58, carp 7-17, perch 22-59, pike 1-2,
   ~110-147 kg, all five species through ten years; an angler two hours every dawn — catches 287 the
   first year, ~110 a year after, bream fished down to single figures; stop fishing after three years —
   bream 2 → 66, carp 2 → 17, perch 20 → 48 in 3-4 years; fed pond 8 → 70 carp over ten years to its
   ~272 kg room, unfed 8 → 16.
   **§cast-always / §alife-strike (user: spinning got too hard, "the fish are full" where a pike is visible;
   never refuse a cast):** a cast is never refused any more — nobody wanting the rig makes a DEAD line
   (no species, a wait of 999 999 ticks) that reEvaluate brings to life when the water changes; the hint
   still says why; the fly (which lands by itself) keeps its old quiet refusal, ice holes are untouched.
   Predators strike on reflex: their keenness never drops below 0.45 (a pike that has just fed still
   takes, and the hint no longer calls it "full"); an ACTIVE rod's offer reaches fish ~20 blocks off (the
   lure is worked back through the water), a still bait 12 (`BiteEngine.alifeOffer`). Rod pods save a
   line with no species yet.
7. ✅ **§boilies** (built 2026-09-25, rules checked, not yet played). `fish/Flavour` — 14 flavours by
   family/strength/colour, ingredients a player has (sweet berries, melon, chorus = plum, glow berries =
   pineapple, apple = citrus, honey = caramel, cocoa, corn, fish oil, prismarine crystals = krill, ink sac =
   squid, chicken liver, allium = garlic, blaze powder = chili). `fish/Boilie` — the spec (≤ 2 flavours,
   sinker / wafter / pop-up / snowman, 10-24 mm, fish meal, dip) and the author's rules as points,
   factor exp(0.85·p) in 0.3..2.0; `BoilieCheck` asserts the notes scene by scene (cold → squid/garlic,
   hot noon → strawberry/pineapple, night → liver/garlic, mud → punchy, clear water → natural, glass jump
   → sour pop-up, pike → meat, 24 mm too big for a roach). Items: `boilie` keeps its id (data in
   "Boilie"; no data = the plain boilie), `boilie_paste` (vanilla clay ball, tinted), `flavour` (vanilla
   potion textures, tinted liquid). Recipes (custom, not in the recipe book): paste = 1-4 wheat (size) +
   egg + 0-2 flavours + 0-2 dried kelp (wafter / pop-up) + fish meal; bottle = glass bottle + sugar +
   ingredient; snowman = sinker/wafter + pop-up. Boiled by right-clicking a water cauldron over a LIT
   campfire (4 boilies a paste) — pop-ups too (floated by the kelp, not baked: no furnace NBT hack).
   Dip: carry a bottle onto a boilie stack and right-click — 8 casts, a cast washes one off. Right-click
   water with boilies: throws 10 in (0.1 kg of feed carrying `flavour:*` keys the fish learn). Engine:
   the factor multiplies `tackleWeight` when the boilie is what that fish takes; a boilie ≥ 20 mm is too
   big for a fish under 300 g; a ≤ 15 mm sweet one is taken by dough/bread/maggot fish (the nuisance);
   the offer's keys carry the flavours (familiarity from prebaiting) and a dip/strong smell reaches
   further in murky water. Fisherman: one of strawberry / fish / garlic bottles at level 3. Journal: a
   guide page `boilies` (group 1, after the groundbait recipes: every recipe, boiling, snowman, dip,
   prebaiting and the flavour rules, en/ru/uk); each catch on a boilie adds one to its flavours under the
   species record (`fl`), and the species page shows "Favourite flavour" — "??? (n/3)" until one flavour
   has three fish, then that flavour and its count (for species that take boilies or have a record).
   Not yet: crumbled flavoured boilies carrying flavour into a groundbait mix.
8. Port to 1.20.1 and 26.x.

## Known gaps

- Species in the sim are five hand copies (`AlifeSim`), the adapter from `FishProfile` is phase 1.
- No spawning migration, no flow/downstream scent, no craving yet — phases 4 and 6.
- Winter in the shallows is dead by design; whether a winter swim in the hole fishes well enough is to
  be tuned when ice fishing goes through agents.
