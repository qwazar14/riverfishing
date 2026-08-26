# Исследование к 0.7.0: идеи из рыболовных игр, модов и реальной рыбалки

Собрано восемью агентами с живым веб-поиском, затем прогнано через восемь отсеивающих
проходов, которые выбрасывали всё, что **уже есть в моде**, всё **серверное** (владелец это
исключил) и всё, что не сделать имеющимися средствами: пиксель-арт 16×16 и 256px, JSON-модели,
палитровые свапы и геометрия из `GL_LINES`. Ни 3D-моделей, ни анимационных ригов, ни записи звука.

**250 записей, 147 уникальных.** По трудоёмкости для одного человека: 53 малых, 84 средних, 10 больших. Все 147 работают в одиночной игре.

Что нужно знать о надёжности: Reddit закрыт для этого краулера, часть гайдов Steam и Fandom
отдавали 403 — там агенты работали по выдержкам из поиска и это помечали. Комментарии
CurseForge тоже недоступны, поэтому «о чём просят игроки» опирается на форумы Steam и GitHub-issues.
Группировка по темам — моя, приблизительная: восемь направлений сильно пересекались. Ценность
каждой записи в строке **Источник** — именно она делает идею проверяемой.

---

## Оглавление

- [Виды ловли и снасти, которых в моде нет](#виды-ловли-и-снасти-которых-в-моде-нет) — 26
- [Бой, снасть и слабое звено](#бой-снасть-и-слабое-звено) — 32
- [Поклёвка: как её прочитать, не нарушая тишины](#поклёвка-как-её-прочитать-не-нарушая-тишины) — 19
- [Коллекция, рекорды, морфы](#коллекция-рекорды-морфы) — 19
- [Обучение, диагностика, вскрытие ошибок](#обучение-диагностика-вскрытие-ошибок) — 14
- [Вода и условия: погода, давление, прозрачность](#вода-и-условия-погода-давление-прозрачность) — 15
- [Презентация: глубина, дно, точка, прикормка](#презентация-глубина-дно-точка-прикормка) — 16
- [Прочее: снаряжение, экономика, мир](#прочее-снаряжение-экономика-мир) — 6

---

## Виды ловли и снасти, которых в моде нет

*26 идей.*

### Spooking: fish notice you before they notice the bait  `S`

**Как работает.** A short-lived per-spot spook value, separate from the long-lived chunk depletion you already have. It rises from sprinting or jumping on the bank within about 6 blocks of the cast point, standing in the water, a cast landing within about 2 blocks of a visible fish, a boat that moved in the last few seconds, any nearby block break, and your own shadow - computed cheaply as sun altitude below roughly 30 degrees with the sun behind the player relative to the water. While spooked, bite rolls are suppressed and any visible fish leave. It decays over 30-90 seconds of holding still and crouching: fast in murky or deep water, very slow in clear shallows and small ponds. Surface it only through the water, with ring particles fleeing outward - never a text warning.

**Почему подходит.** The cheapest way to make the water feel inhabited rather than statistical, and it finally gives approach route, crouching and patience mechanical weight in a mod already built around patience. It is also the shared difficulty layer for sight fishing, spearfishing, bowfishing and small-stream work, so building it once pays for four other ideas. Zero new assets.

**Источник.** Vintage Story's Primitive Survival mod - bait a trap, then "keep your distance from the trap to avoid alarming your target". Vintage Story 1.22 - fish "quickly escape melee weapons", and fishing a spot eventually depletes it. Call of the Wild: The Angler - sight fishing means reading splashing, water breaks and bubbles as location identifiers, i.e. fish are present objects that can be there or gone. Real trout-stream stalking (flylab.substack.com, 'Stalking Fish: Live With Poor Casts') - approach and shadow matter more than the cast.

### Magnet fishing: a neodymium magnet on a rope that pulls ferrous history out of the water instead of fish  `S`

**Как работает.** An item that reuses the existing charged cast and reel-in code with the fish roll swapped for a scrap roll. Three magnet tiers set a pull-strength cap; a find heavier than the cap hooks, resists, then visibly slips, so the player learns what to upgrade. The loot table is weighted by what generated nearby above or below the water - village, bridge, ruined portal, shipwreck, mineshaft, ocean monument - and results arrive as a single encrusted lump that the Tackle Station cleans into iron nuggets, chains, buckets, horse armour, name tags, the odd map, and rarely something genuinely good. Two flavour rules straight from the hobby: a lump can occasionally be live ordnance that primes when cleaned (a fair, telegraphed TNT surprise), and a magnet stuck on a snag needs the grapple to recover. A stretch of river under a bridge holds only so many bicycles, so the spot depletes permanently.

**Почему подходит.** The largest new activity per line of code here, because it inherits the cast, the reel and the junk/treasure tables you already ship, and it finally gives that junk a purpose. It also fishes places fish do not live - under a village bridge, a shallow ditch, a frozen city river - so it uses the map differently from every rod. Assets: a magnet, a rope and one encrusted-lump sprite.

**Источник.** Wikipedia 'Magnet fishing' - a strong neodymium magnet on 15-30 m of rope, gloves against sharp finds, sometimes a grapple hook alongside; typical finds are bicycles, coins, tyre rims and car parts, occasionally firearms and unexploded ordnance; hazards include crushed fingers and tetanus; permits and fines vary by country. Magnet Fishing Simulator (Steam) - stronger magnets retrieve heavier and more valuable items, every recovered item can be cleaned, repaired and then sold or kept in a collection for bonuses, and better gear unlocks deeper water.

### Dead-baiting for pike with a drop-off indicator: the only strike window in the mod with a floor as well as a ceiling  `S`

**Как работает.** A new bait class - a dead small fish, which your keepnet and fillet-knife chain already produce - on a wire trace under a drop-off clip, usable on bottom or float blanks. The run is silent and purely visual, as the mod prefers: the clip arm falls and line pays off the spool in visible ticks. Three outcomes from one timestamp: strike inside roughly the first second and you pull the bait out of its mouth (miss, bait gone); strike in the good window, about 2-5 seconds scaled by fish size, and it is hooked cleanly in the jaw for a normal fight, releasable, stockable, keepnet-alive; wait past the window and you still land it, but deep-hooked, which the journal records and which makes that fish non-releasable and non-stockable. Add the drop-back variant so a fish running toward the bank shows a falling rather than rising indicator - a second cue to learn.

**Почему подходит.** It introduces a genuinely new failure mode: succeeding badly. Everything today is caught-or-lost, and this makes catch-and-release and the stocking system into stakes rather than a menu choice, for the cost of one bait class, one indicator visual and a two-bound timer. It also drops straight into your existing predator and catfish rigs and gives pike waters a technique of their own.

**Источник.** Real UK pike angling. A drop-back indicator is a weighted clip on the line between reel and first eye: if the pike runs away the line pulls free of the clip and the arm falls, giving slack; if it swims toward you the indicator drops back (AnglingActive 'Pike Fishing: Bite Indication'; Maggotdrowners 'Pike rigs and bite detection'). The timing rule is explicit - strike early but not too early, give the pike a couple of seconds to turn the bait, and do not let it swallow deep, because a swallowed bait means a deep-hooked fish (Angling Times, 'How to avoid deep hooking pike while deadbaiting').

### Ice tip-ups: drill five holes, set flagged tip-ups, and run to whichever flag pops — while the cold clock runs  `M`

**Как работает.** A tip-up is a placeable block that sits over a drilled hole with a bait and a set depth. It fishes unattended on a long timer; on a take, the flag sprite swaps to raised (a blockstate texture swap) and particles fire. You have a grab window to reach it. Because you can only run so far in cold, the real decision is hole spacing — spread wide for coverage, tight for reaction time. A shanty/heater block pauses freezing damage in a small radius, which is the counterplay to spreading out.

**Почему подходит.** The mod already has ice holes, mormyshka jigging, rod pods with self-hooking, and Minecraft already ships freezing damage — so this is recombination, not new systems. But the *feel* is completely different from the existing pod: pods are one static spot, tip-ups are a spatial puzzle with a movement cost. It's a blockstate sprite swap and a timer; no new art class.

**Источник.** RF4 suggestions megathread (steamcommunity.com/app/766570/discussions/9/1635292137555417026/), where the ice-fishing wishlist is spelled out item by item: "hand auger, gas auger, tip-ups, Shanty, heater, jig pole, Sonar."

### Grade the CONDITION of the specimen by how you fought it, not by luck — and let only a well-fought fish survive release  `M`

**Как работает.** Dave the Diver's stars are an elegant trick: the same fish is worth more or less depending on how respectfully you took it, which retroactively makes every tool choice a quality decision and stops the player from defaulting to the loudest option. Port: at landing, compute a condition grade from fight telemetry you already have — total time under max tension, foul-hook flag, number of tension spikes, whether the drag ever locked, hook wear. Pristine / good / exhausted / damaged. Condition multiplies the villager price and the fillet yield, and it gates release: only pristine and good fish survive being released and therefore count for stocking and settling. An exhausted trophy is a mount but a dead end. Add a landing net and an unhooking mat as cheap tackle that raise the grade floor — real gear, real purpose.

**Почему подходит.** Your prime grading is a weight percentile, which is luck; this is a skill grade sitting on the same item NBT. It gives the fight an outcome gradient instead of land/lose, it gives your stocking system a reason to care about HOW you caught the broodstock, and catch-and-release mortality from over-long fights is a genuine sport-fishing concern, so it earns its place in a realism mod rather than looking like a game mechanic.

**Источник.** Dave the Diver — per the Dave the Diver Wiki 'Fish' page and the Steam discussion 'What's the meaning of rank and stars of the fish cards': quality stars are set by METHOD, not by roll — 1 star for fish killed with guns, knives, bombs or rocks, 2 for the spear gun (or any large fish), 3 for tranquilised or netted fish, and higher stars yield more meat at the same weight.

### Records only count if the fish is weighed on a certified scale within a time window, and the specimen loses weight while you carry it — so filing a record becomes a small logistics run  `M`

**Как работает.** New block: Certified Scale (16x16 item + simple JSON block model). It has a validity timer — the fisherman villager re-certifies it once per in-game year for a fee; an uncertified scale weighs but cannot file. A caught specimen stack carries `caught_tick` and loses a small % of weight per in-game day (dries out); weighing on a certified scale freezes the value and writes the record. Beating your own record requires the IGFA margin (2 oz / 0.5%), equal weight files as a tie. Fish that break off or that you never weigh go into a separate 'unverified claims' page with an estimated size derived from the peak tension the fight reached — permanently unverifiable, which is exactly the flavour real record programmes have.

**Почему подходит.** Gives the existing prime/trophy grading actual friction and a place in the world: your camp needs a weigh station, so a fishing base becomes worth building. Weight decay makes the walk home matter and rewards fishing near a scale or hauling a keepnet. All of it is timers and NBT — no models, no sounds.

**Источник.** IGFA World Record Requirements (igfa.org/world-record-requirements): scales must be "certified for accuracy by government agencies or other qualified and accredited organizations within the past 12 months", claims must reach IGFA within 60 days (US) or 90 days (international), a record must beat the standing one by 2 oz (fish under 11.33 kg) or 0.5% (heavier), and an equal weight is recorded as a tie. New York DEC Angler Achievement Awards: State Record entries must be weighed "on certified scales" at a certified location and examined by a DEC Fisheries Biologist.

### An annual licence with species endorsements and limited harvest tags: renew each in-game year, buy the stamps that let you keep the prestige species, and punch a numbered tag for each one you kill  `M`

**Как работает.** Licence item with a `year` in NBT, sold by the fisherman villager; expired licence means he refuses to buy your fish and journal entries file as 'unlicensed' (recorded but never eligible for records). Optional stamps: a salmonid stamp, a sturgeon tag, a night-fishing permit — each a cheap item that unlocks *keeping* (not catching) a class of species. Harvest tags are a small stack per year for the marquee species: killing one consumes and punches a tag with species/weight/date, and a fully punched card is a keepsake item that stacks into your yearly archive. Everything is catch-and-release friendly — no licence needed to catch and release, which nudges the behaviour tagging and length records want.

**Почему подходит.** It gives the mod a yearly heartbeat: a reason for the calendar (Serene Seasons is already integrated) to matter to progression, a soft cap on hoovering up rare species that is fiction rather than an arbitrary cooldown, and a stack of filled cards and stamps as a physical history of the years you played. It also feeds the existing villager economy without touching anything server-side.

**Источник.** Fishing licence practice (en.wikipedia.org/wiki/Fishing_licence): England/Wales require a rod licence for "salmon, trout, freshwater fish, smelt or eels" plus separate permission from the owner of the fishing rights; licensing varies by water type and country. Species endorsements/stamps and catch-recording cards are the US equivalent — California DFW Report Cards (steelhead, sturgeon, salmon, spiny lobster) and Washington DFW Catch Record Cards, where the angler must record each retained fish on the card and return it annually. Annual-cycle award programmes for the reset rhythm: Fish Ohio pins and NY DEC's per-species stickers (dec.ny.gov Angler Achievement Awards, 40 species with minimum lengths and a lower-threshold Youth category).

### Vanilla particles as surface signs — bubble trails, mud plumes and rolling fish that reveal where the engine has actually put the fish, and which species they are  `M`

**Как работает.** The engine already decides which species are present, which chunks are depleted, and when a frenzy is on — this just surfaces that decision at the real water position. Map sign to particle: a slow meandering line of `bubble` = a benthic sifter feeding on the bed (tench, bream); scattered individual bubble patches over an area = carp; a `campfire_cosy_smoke`/dust-style puff or a brief darkening = a mud plume from a fish digging silt; `splash` plus the vanilla `fishing` particle at dawn and dusk = fish rolling to clear their gill rakers; tiny repeated `splash` dimples = a surface shoal of small fish. Emit a sign ONLY while that species is genuinely in a feeding state at that position, so casting within a few blocks of a sign gives a large bonus and, crucially, tells the player which species and which water layer. Gate it on stillness: signs only render after the player has been standing quietly (not sprinting) for several seconds, which mechanises the angler's habit of watching the water before casting. Journal page decodes the sign vocabulary, unlocked by observing each one.

**Почему подходит.** This is the biggest hole in a deliberately silent-bite sim: right now the player has no way to FIND fish except trial and error, so the silence reads as emptiness rather than tension. Particles are free — no models, no sounds, no textures — and it retroactively makes pond stocking visible: you can watch your stocked tench fizzing over the spot you released them.

**Источник.** Real mechanism: DNA Baits, "How to Read the Water: 10 Signs Carp Are in Your Swim" (showing/head-and-shoulder rolls, tight clusters of bubbles, trails moving slowly across the swim, milky haze and mud plumes, bow waves in the margins, rocking reeds, bird activity). The species discrimination comes from Farnham Angling Society's tench page (tench sift silt and vent bubbles through the gills in distinct meandering lines) and the Maggotdrowners "Fizzing Peg" thread: fizz reappearing in the same place is tench, individual patches spread over an area are feeding carp, and bream fizz even when feeding high in the water.

### Every released fish either dies or learns: a survival roll driven by your technique, and a spot memory of the exact bait-and-rig combination that caught it  `M`

**Как работает.** Two consequences per released fish. (a) SURVIVAL roll from factors the player controls, not from luck: hook location (which the hook-pattern idea already determines — circle and wide gape hook shallow, laying-on overdepth deep-hooks), barbed versus barbless, fight duration from the existing fight timer (a long exhausting fight in warm water is the real killer), water temperature, air exposure measured as how long the fish sat in hand or inventory before release, and whether a landing net was used. A fish that dies does NOT return to the chunk's population — so the mod's existing per-chunk depletion becomes something the player's own technique drives, and the pond you stocked is a pond you can personally ruin. (b) WARINESS: a surviving fish stamps that SPOT with the (bait, hooklink, rig, hook size) combination that caught it. Reusing the identical combination there decays over repeated captures — for that combination specifically, recovering over in-game weeks — while changing bait, scaling the hook down or switching to a fluoro hooklink resets it. Name the diagnosis in the journal: "the fish here are wise to sweetcorn on a size 8."

**Почему подходит.** The mod already has a keepnet, per-chunk depletion, stocking, hook wear and a fight timer, so every input exists — this is the missing feedback loop that makes catch-and-release a skill rather than a formality. And rig shyness is exactly the anti-grind mechanic a single-player sim needs: it forces the player to use the breadth of tackle the mod already ships, with no timers, no artificial cooldowns and no other players required.

**Источник.** Real mechanism, survival: Bartholomew & Bohnsack, "A Review of Catch-and-Release Angling Mortality" — 274 studies, median release mortality 11%, mean 18%, range 0-95%, with "anatomical hooking location the most important mortality factor"; jaw- or mouth-hooked fish under 1%, while up to 40% of deeply hooked brook trout on live bait died. Barbless vs barbed: 77% versus 53% survival in Arlinghaus et al. 2007 as cited in the barbless-hook literature. Duluth News Tribune, "Catch, release and dead?" on handling time and water temperature. Real mechanism, wariness: Pole Position Tackle on conditioned carp becoming "more sensitive to fishing lines and baited rigs" after repeated catch-and-release, with avoidance lasting weeks to months; Carpology's pressured-carp rig advice, and the day-ticket counter of scaling to smaller hooks and fluorocarbon or light braid hooklinks.

### A submersible fishing lamp that grows its own food chain over about 20 minutes, with the bites at the edge of the light  `M`

**Как работает.** A lamp block placed in water or hung off a boat, with a depth setting. It emits light and accumulates an attraction value over about 20 real minutes in three stages: plankton (nothing catchable, particle haze), then small fry (your existing baitfish and livebait pool becomes reliably catchable inside the lit radius), then predators drawn to the fry. Two rules keep it a skill rather than a switch: the hot ring is the OUTER edge of the lit radius rather than the centre, so cast distance matters, and it only works after dusk, resetting at sunrise. Invert it on the bank - an open torch or a held lantern over clear water shallower than about 3 blocks raises the spook value from the idea above.

**Почему подходит.** Night is currently only a modifier in the bite engine; this turns it into a place you set up and wait in, which suits the mod's temperament exactly. It costs one block, one sprite and a timer, and it is the enabling infrastructure for bowfishing and night spearfishing. One honest overlap to manage: it is adjacent to groundbait, so keep the axes distinct - groundbait shifts which species bite via bait match, the lamp shifts the food chain over time and moves the hot spot to the edge ring.

**Источник.** Real night crappie and squid lighting: green penetrates furthest so green submersible lights are standard; one report notes a bait cloud on the sonar within 15 minutes of dark and a solid screen 15 minutes later; the rules of thumb are to hang the light at about 50% of the depth you are fishing and to "cast or drop your bait on the edge of the light where it fades into dark water", because that is where predators wait (FishUSA 'Mastering Crappie Fishing at Night', Bass Pro 1Source 'Crappie Fishing After Dark', Outrigger Outdoors). Dredge - light versus darkness is the core loop, better lights fight the panic meter, and lights going out is a punishment event. Wikipedia 'Bowfishing' - sport boats carry raised platforms with powerful floodlights to expose and attract fish in the calm of night.

### Crustacean pots: baited traps you leave in the water, with a soak curve, spoilage and gear decay  `M`

**Как работает.** A pot block placed on the bottom with open water on all four cardinal sides (the placement rule both Primitive Survival and the CurseForge Fish Traps mods already use), rendering a buoy at the surface so a spread is readable from the bank. Bait one slot; the catch table comes from your existing water-body classification - river and pond give crayfish, snails and a rare specimen crawfish, sea zones give crab, lobster, shrimp and mussels. Yield follows a soak curve rather than a flat rate: it climbs for roughly one in-game day, plateaus, then starts converting the held catch into spoiled or scavenged remains, so a forgotten pot is worse than an empty one. The pot takes wear per soak and eventually needs re-tying at the Tackle Station, and hauling it up too fast in a current shifts the escape roll against you.

**Почему подходит.** It fills two blind spots at once: nothing in the mod works while you are away, and 79 species contain no shellfish, so this adds a missing taxon that feeds cooking, the villager's trades and the bait chain (crayfish are premium catfish and asp bait) without a single new fight pattern. Assets are one block, one buoy and a few 16x16 shellfish. Keep it clearly distinct from your bait trap by making pots exclusively crustaceans and molluscs, never fish.

**Источник.** Dredge - crab pots are deployed anywhere in the ocean and stay marked by a buoy, passively catching crabs and some fish; eight tiers, 1-3 items per day, a 3-8 day working life, then a $15-$40 repair (dredge.wiki.gg/wiki/Crab_Pot). Stardew Valley - a pot needs bait every day (bait type is irrelevant), produces overnight, and its catch table is set by water type: freshwater gives crayfish, periwinkle and snail, saltwater gives clam, cockle, crab, lobster, mussel, oyster and shrimp; the Luremaster profession removes the bait requirement. Moonglow Bay - set lobster traps around the world and check back the next day for a haul. Maine Sea Grant vessel and gear guide - a real pot is tied by a vertical line to a surface buoy, singly or in a string.

### Trotlines and limb lines: a baited line strung across the river that fishes while you sleep, and pays for it in spoilage and local extinction  `M`

**Как работает.** Two stake blocks up to about 24 blocks apart in a straight line, at least one endpoint on land, with the mainline drawn in GL_LINES - already in the mod's rendering vocabulary. The line exposes N hook slots scaling with span; each is baited individually and rolls independently against the local bite table on a slow interval, including while the player is asleep, elsewhere or logged out, resolved lazily from a stored timestamp. The cost is the point: a fish that has hung for hours comes up dead and spoiled (fillet-only, no prime grade, no release, no stocking, no journal first-catch credit), the line drives that chunk's depletion far harder than any rod, and a big fish or a flood can snap the mainline and take every hook with it. Checking means walking the line hook by hook.

**Почему подходит.** Unattended volume fishing is a whole missing register - it is how rivers were actually worked - and it turns the depletion and stocking systems you already built into a real decision: farm this pool out for meat, or keep it as a sport swim. Differentiate it hard from the rod pod: the pod is one live rod needing you nearby for a 30-second window, whereas the trotline is a persistent world block whose currency is quantity, spoilage and consequence. Assets: two stakes and a rope drawn in lines.

**Источник.** Vintage Story's Primitive Survival mod - attach cordage between fixed objects near water and then add hooks to the line; placement must be a straight run, limb lines span a short distance and trotlines reach up to 40 blocks; hooks are crafted from flint, bone or cast molds and can be baited or fitted with lures. Maine Sea Grant gear guide - a longline is a mainline held at depth by spaced floats with short baited gangions at intervals, from short halibut tub trawls to miles of hooks.

### Cast net: spot a baitfish school in the shallows and take the whole school in one throw  `M`

**Как работает.** A throwable net item in two or three radii. Baitfish schools appear as visible transient markers in shallow water - a swirl of particles plus surface dimples - spawned by the environmental rules your bite engine already runs, denser at dawn and dusk and around the night lamp. Throw the net so its footprint covers a school and you get 1-8 live baitfish at once, scaled by school density and net radius, straight into the livebait pipeline. The fail states are the real ones: water deeper than the net's radius lets the school escape under the sinking net; kelp, lily pads, seagrass or a nearby snag tangles it and costs durability; and the throw itself spikes the spook value, so you get one attempt before the shallows empty.

**Почему подходит.** It is the active counterpart to the bait trap - instant, skill-based and site-specific rather than passive - and it feeds the existing livebait rule where the baitfish's weight gates the predator's size, so a good throw directly decides how big a pike you can target that evening. It also gives shallow water and the surface something to watch, which supports sight fishing and the night lamp. Assets: one net sprite and a particle school.

**Источник.** Wikipedia 'Cast net' - a circular weighted net of 4-12 ft radius, roughly one pound of lead per foot of circumference, thrown two-handed in a circular hammer-throw motion from boat, bank or while wading; it works only in water no deeper than the net's radius, tangles and rips on reeds and branches, and its job is small baitfish and forage species. Moonglow Bay uses a net as a distinct third method alongside rod and traps, mainly for lobster, crab and oyster types.

### Tip-up spread on the ice: silent flags, hand-lining with no drag, holes that refreeze, and an auger bore that caps your fish  `M`

**Как работает.** Cheap wooden tip-ups placed on drilled holes, up to a small cap of about five, each set to a depth and baited. A take pops the flag - purely visual, no sound, consistent with your silent-bite rule - and you have to notice it across the ice and run. The fight is hand-lining rather than reeling: no drag at all, so it becomes a hold-and-release tug of war where over-pulling breaks the line outright, which feels nothing like the rod fight model. Two new pressures: every hole slowly refreezes unless re-cleared or fitted with a thermal cover, and the auger you own sets a bore diameter that hard-caps the weight you can physically pull through, so a bigger fish is lost at the ice unless you widen the hole first. A wind tip-up variant auto-jigs on a slow tick while unattended.

**Почему подходит.** Ice fishing today is one player at one hole with a mormyshka; a spread turns a frozen lake into a patrol loop and a spatial puzzle, which is the real sport's actual shape. Be honest that this is the thinnest idea in the list, since you already ship rod pods with self-hooking - it is only worth building for its three new parts: drag-free hand-lining, refreezing holes, and the auger-bore weight cap. Assets: a tip-up sprite, a flag state and a cover block.

**Источник.** Wikipedia 'Tip-up (ice fishing)' - the spool sits below the ice and a fish taking line rotates it, releasing a sprung flag; wind tip-ups carry a sail that jigs the bait for you; thermal tip-ups cover the whole hole to slow refreezing and to stop light spooking sensitive fish. Ice Lakes - three auger classes trade drilling speed against bore width, and heavier augers make larger holes so the biggest fish can fit through; depth over a hole is dialled with the mouse wheel and the map carries a darker-is-deeper depth overlay (Steam store page plus the 'Ice Fishing Equipment' and 'Visual Guide: Basics of Ice Fishing' community guides).

### Sight fishing: a handful of individually visible, pre-rolled fish you have to spot and stalk  `M`

**Как работает.** Spawn a small number, 2-5 per loaded water body, of lightweight fish markers with no model at all - a sprite or particle silhouette plus an occasional surface rise ring, well inside the asset budget. Each marker is a fully pre-rolled specimen: species, weight, fight pattern and preferred layer, decided at spawn by the existing bite engine rather than at hookup. Visibility is conditional and teaches real skill: only while crouched, only in daylight with the sun behind you, only within a few blocks, and only where the water is shallow and clear enough, with kelp, murk and depth hiding them; they vanish the moment the spook value spikes. Cast within a couple of blocks with a matching layer and bait and you are fishing THAT fish at a large bite bonus; land the cast on top of it and you spook it. Markers drift slowly, hold behind current-breaking blocks, and rise to a hatch.

**Почему подходит.** Every catch today is a dice roll behind opaque water, and a mod with 256px hand-drawn icons for 79 species has no moment where you see the fish before you catch it. This is also the shared prerequisite for spearfishing, bowfishing and noodling - build one cheap fish target and three other ideas become possible - and it is what makes dry-fly work and small-stream stalking legible instead of statistical.

**Источник.** Call of the Wild: The Angler - sight fishing means scanning the surface for splashing, water breaks and bubbles as location identifiers that put you in the right spot. Ultimate Fishing Simulator - its underwater camera lets you observe the underwater life of fish and adjust technique to conditions, and PC Gamer singled it out as the game's best feature; tellingly, it is disabled in Realistic Mode. Vintage Story 1.22 - fish are visible entities in the water that the pole catches and that flee from melee.

### Spearfishing on a breath: sink, stop moving, let them come back, and aim low  `M`

**Как работает.** A pole spear, later a sling, plus a float line, used underwater with Minecraft's air bar as the entire resource. The play is the real technique rather than a shooter: swimming fast scares the fish markers off (reusing the spook system), so you weight down, stop, and wait on the bottom while your air drains and they drift back into range - a direct trade of breath for opportunity. Two rules give it teeth. First, a refraction offset: a spear thrown while your eyes are above the surface must be aimed low by an amount scaling with distance and the fish's depth (the real bowfishing rules of thumb, about 4 inches per 10 ft of range and 3 inches per foot of depth, convert cleanly to a block offset). Second, the float line: a speared big fish tows you for several seconds, so you surface in time, drown, or cut the line and lose both spear and fish. Only species with a spearable tag are valid targets, which leaves the 79-species table intact.

**Почему подходит.** Every technique in the mod happens down a line from dry land or a boat; this is the only one where the player is in the fish's medium and the resource at risk is their own air - and Minecraft already ships swimming, air, water clarity and depth for free. Assets are a spear sprite, a float, and the shared fish marker from the sight-fishing idea; the vanilla trident need not be involved. The coastal and ocean zones added in 0.5.0 finally get something to do in the daytime shallows.

**Источник.** Wikipedia 'Spearfishing' - polespears and Hawaiian slings are hand-powered elastic weapons, hunting is done on a breath-hold with a buoy and float line, weight belt and stringer, and success turns on optical refraction at the surface, which makes fish appear higher than they are, plus patient positioning near structure. Freediving Hunter: Spearfishing The World - one button dives and starts a breath countdown, another surfaces you, and if you overstay lining up a shot or fighting a fish your diver blacks out (TheXboxHub review). Dave the Diver - the harpoon aims within a restricted 45-degree cone rather than freely, a Hush Dart puts a fish to sleep after about 8 seconds so you can swim up and take it alive, and night dives cost part of the day but unlock night-only fish.

### Bowfishing at night from a lit boat, at rough fish only, aiming low for refraction  `M`

**Как работает.** A bowfishing bow that only accepts a tethered arrow item; the tether renders in GL_LINES and the arrow must be reeled back after every shot, hit or miss. The refraction offset is the whole skill: the aim point sits below the visible fish by an amount computed from range and depth, so the mod literally teaches the real rule. The valid-species tag list is short and thematic - carp, silver carp, grass carp, gar, catfish, ray - and a shot at a game or protected species is refused with the arrow wasted, which quietly teaches your own species table. It works only in shallow water of 2-3 blocks, best at night with the submersible lamp or a lantern in the boat pulling fish up, and a big fish can strip the arrow off the line or tow the boat. The meat is fillet-grade only, never prime, never releasable - this is harvest fishing rather than sport, and the journal should say so.

**Почему подходит.** It reuses Minecraft's bow draw, points a technique squarely at your carp-heavy roster, and costs only a bow sprite, an arrow sprite and a line drawn in GL_LINES. It stacks naturally on the night lamp and the boat, turning a lit shallow at midnight into its own hunting ground rather than a worse version of daytime. And its refraction rule is one of the rare places a game can teach a real physical fact and have it feel like skill.

**Источник.** Wikipedia 'Bowfishing' - heavy fibreglass, aluminium or carbon arrows with barbed points on 80-400 lb braided line, retrieved with a hand-wrap spool, a spincast reel or a bottle retriever; modern sport bowfishing often happens at night from boats with a raised platform and powerful floodlights, because fish are less alert and the water is calm; legal targets are rough fish - carp, gar, paddlefish, buffalofishes, catfish - plus rays, sharks and barracuda in salt. Aiming guidance from Bass Pro 1Source, Realtree and MeatEater: aim roughly six inches below, with rules of thumb of 4 inches lower per 10 feet of distance and 3 inches lower per foot of depth, because refraction makes beginners shoot straight over the fish.

### Noodling: reach bare-handed into an underwater hole and hold on while your air runs out  `M`

**Как работает.** Generate a rare catfish-hole feature in river and lake banks - a submerged undercut of one or two blocks, visually just a dark cavity, optionally hinted by a stick-poke check. Swim down, right-click it with an empty hand, and a hold-to-grip endurance bar starts while your air bar keeps draining. Releasing early loses the fish; holding to the end lands it; holding past your air means real drowning damage - so the decision is whether this fish is worth your last two seconds of breath. Roll the occupant from the hole's water body: usually a big catfish, and its size can exceed anything the local rod tackle could land, which is the reward; sometimes nothing; and sometimes something that bites back for a couple of hearts, a snapping turtle or a beaver. Cap it hard - holes are rare and slow to respawn - and involve no tackle, bait or line at all.

**Почему подходит.** It is the only idea here where the resource at stake is the player's own life rather than their line, which is an entirely new tension for a mod whose risk model is tackle failure. The cost is minimal - one worldgen feature, one hold bar, no new items, no models - and it slots straight into the existing catfish and big-river material. If the owner wants one memorable, screenshot-worthy mechanic no other Minecraft fishing mod has, this is it.

**Источник.** Real hand-fishing, documented in Bradley Beesley's 2001 documentary Okie Noodling (Wikipedia; IMDb) - noodlers wade murky water and reach into dark holes so a big flathead catfish clamps onto the hand; it is legal in only four US states (Oklahoma, Louisiana, Tennessee, Mississippi), and the named risks are snake and beaver bites, lost fingers and outright drowning (Outside Online, 'Noodling for Catfish Is Now a Tourist Activity'). I found no video-game precedent for it at all, which is exactly why it is on this list.

### Fish freshness clock, with a crushed-ice box and a keepnet that stop it  `M`

**Как работает.** Tide stamps a caught fish with a time and later refuses to let you bucket it — a hidden expiry with no player-facing state. River Fishing: every caught specimen carries catch-time in its NBT and moves through Fresh → Slack → Turning → Spoiled on a real-time clock. Fresh is required for the fisherman's prime-grade price; Slack sells at a discount; Turning halves fillet yield; Spoiled cooks into nothing useful. A keepnet in water, an ice box block, or simply fishing in winter/through an ice hole pauses the clock. Tooltip shows the state as a word, not a bar.

**Почему подходит.** It turns three things River Fishing already owns — the keepnet, the walk to the villager, and the ≥70% prime-grade sale — into a single decision (fish on or take the good one home), and it hands ice fishing a genuine mechanical advantage. Pure item NBT plus one block and a few 16x16 icons.

**Источник.** Tide 2.0 — "'Bucketable' fish system using timestamps to determine viability" (Tide 2.0 changelog via api.modrinth.com/v2/project/tide/version); Aquaculture 2 grades fillet yield by weight but has no freshness at all (modrinth.com/mod/aquaculture)

### Illegal gear — cast net, spear, dynamite: instant hauls that permanently wreck the spot and get you blacklisted  `M`

**Как работает.** In all four, nets/spears/dynamite are simply faster fishing with no downside — they're convenience or automation. River Fishing: keep the speed, add the cost. A cast net or a dynamite stick returns a multi-fish haul with no cast, no fight and no tackle wear — and in exchange slams that chunk's stock to near zero, permanently lowers its carrying capacity by a chunk-persistent amount, gives zero journal credit and no prime grade, and makes the fisherman villager refuse to buy from you for N days after witnessing it (or after you carry netted fish to him).

**Почему подходит.** River Fishing has depletion, stocking, a buying villager and a journal but no mechanic that puts them in tension with each other. Poaching is the lever that makes the ecosystem a moral system instead of three independent numbers, and it needs no new systems at all — only a new consumer of existing ones. It also answers the automation crowd honestly rather than pretending they don't exist.

**Источник.** Lili's Lucky Lures — "a Spear for direct action fishing, Dynamite for unconventional anglers" (api.modrinth.com/v2/project/DMDVFZSF); Giacomo's Fishing Net — a net you throw or place in water, baitable with bone/fish meal, enchantable with Luck of the Sea (curseforge.com/minecraft/mc-mods/giacomos-fishing-net); Fisherman's Haven — "traps enable players to catch multiple fish simultaneously using nets" (modrinth.com/mod/fishermans-haven); Create: Fishery Industry — Frame Trap sweeps up whatever it passes (curseforge.com/minecraft/mc-mods/create-fishery-industry)

### Bait that is alive: perishable stock, maggots that pupate into a new bait tier, minnows that die out of water  `M`

**Как работает.** Aquaculture makes bait a durability pool and hooks a way to stretch it; Tide makes bait a stackable multi-slot buff. In both, bait is inert inventory. River Fishing: bait becomes stock you have to keep. A bait box holds worms in damp earth for a few days before they go off; maggots pupate on a clock into casters and then into flies — a genuinely different, better bait for topwater and surface-feeding species; minnows and other livebait die without water within minutes of leaving the keepnet and drop from live-bait to dead-bait effectiveness (which some predators actually prefer on the bottom). The ice box and a damp box slow every clock.

**Почему подходит.** River Fishing has worm and maggot farms, a bait trap and groundbait, but bait itself has no state, so bait management is a shopping trip rather than a practice. Perishable bait is the classic realism-sim housekeeping layer, it makes the ice box and keepnet double-purpose, and maggot→caster→fly buys a whole new bait tier out of three 16x16 sprites.

**Источник.** Aquaculture 2 — bait is durability: worms 20 uses, leeches 35, minnows 50, and hook choice changes consumption (Iron Hook 20% chance not to consume, Diamond 50%) (minecraft-guides.com/mod/aquaculture-2); Tide 2.1 — multiple baits stack on one rod and "one bait item from each slot will be consumed upon catching a fish" (Tide 2.1 changelog)

### Landing phase: the last two metres, with a net, a mat, and a real chance to lose it at your feet  `M`

**Как работает.** Both of these are patches over a missing step: one sells you extra time to secure the catch, the other removes the failure entirely because it felt unfair. Neither models landing. River Fishing: when stamina hits zero the fight is not over — a short landing phase begins. With a landing net equipped it resolves almost automatically. Without one you must lift the fish (a rod-angle input against the existing bend steps) and a heavy specimen can pop the hook or break the line right at the surface, weight-scaled. An unhooking mat on the bank cuts release mortality and prevents the grade damage that a fish thrashing on gravel would cause.

**Почему подходит.** This is where fish are genuinely lost, it gives the net a job beyond storage, and it plugs straight into the tension and six-step bend model already built. It also completes catch-and-release: how you land the fish is what decides whether releasing it means anything. No new UI beyond the existing fight overlay.

**Источник.** Angler's Desire — one of its five hook enhancements exists purely to "provide more time to secure your catch" (planetminecraft.com/mod/angler-s-desire); Starcatcher v3.0 — "grace period now lasts indefinitely", a change made because players were losing fish at the moment of landing (Starcatcher changelog via api.modrinth.com/v2/project/starcatcher/version)

### Fly fishing as a fourth rod class, built on drift maintenance rather than a power bar: false-cast rhythm to build line, then keep the drift drag-free by mending  `L`

**Как работает.** Cast phase: alternating click rhythm, and it's the *evenness* of intervals that builds line out — too fast and the loop collapses (short cast), too slow and it dumps. Drift phase: the fly rides the current at a fixed rate with zero input; a small 'drag' meter fills whenever the line bellies, and a mend key (one tap, with a cooldown) resets it. Bite chance scales with how much of the drift was drag-free. Add a hatch table: time-of-day + season + biome selects the active insect, and a fly whose type doesn't match the hatch gets refused even by a hungry fish. Dry fly rides the surface, nymph rides a set depth.

**Почему подходит.** It is a genuinely different verb from everything the mod has — the click-cadence retrieve is about the gap between clicks driving lure action; this is about maintaining nothing at all, which no other class does. It needs one new rod blank, a handful of 16x16 fly sprites and a JSON hatch table, so it lands inside the asset ceiling. And it is the single largest unserved request in the genre, which matters for a mod trying to be a modpack anchor.

**Источник.** Requested for years and by players' own account never done: Call of the Wild: The Angler 'FLY FISHING' thread (steamcommunity.com/app/1408610/discussions/0/4766584664411881106/) — "all i want is fly fishing and i think it might spark the game for people again", "fly fishing would bring many new players to the game", "I love flyfishing irl and can´t wait for any game to implement that", and the flat statement that "no fishing sim on the market has [fly fishing] that I am aware of." Ultimate Fishing Simulator 2 players are angry it was dropped from the sequel (app/1136380/discussions/2/601908461606798353/). Also asked for in the RF4 suggestions thread: "find something to be able to fish on the fly."

### Make the keepnet spatial: fish occupy Tetris footprints derived from the length and girth you already store, so 'which fish do I keep' becomes the day's real decision  `L`

**Как работает.** DREDGE made storage the game's central decision by making it spatial, then hung health, progression and equipment loadout on the same grid so one mechanic carried four systems. Port: your keepnet becomes a grid whose cells are bought/upgraded (creel → keepnet → cooler → live-well). A fish's footprint is generated from its length and weight, which the mod already computes for the size-scaled journal icons — an eel is 1x5 and rotatable, a bream is a fat 2x3, a big carp is 3x4 with a notch. Reserve edge cells for the things anglers actually carry (bait tub, groundbait, a bottle of water) so tackle competes with catch. Take the dev's warnings seriously: implement hot-swap and an auto-place button on day one, and only open the grid at the Tackle Station or on a landed fish, never mid-fight.

**Почему подходит.** It answers 'how is a catch celebrated' physically rather than with a popup: a trophy carp that eats half your net is a felt event. It gives your prime/condition grades and your donate-or-sell decisions real teeth, because keeping the big one costs you the rest. And it is pure 2D UI over icons you already drew — nothing in your asset ceiling blocks it. Note you already ship a keepnet item; this is a rebuild of it, not a new object.

**Источник.** DREDGE — the developer's own write-up, 'Deep Dive: The surprising depth of spatial inventories in Dredge' by Joel Mason / Black Salt Games on Game Developer, plus the DREDGE Wiki. The prototype with a '+1 Fish' notification was 'extremely boring'; the 7x9 grid with rotatable Tetris-shaped fish (1x1, 1x2, 2x2, L-shapes for aberrants) fixed it. The grid doubles as the health bar — damage occupies cells until repaired. Stated lessons: hot-swapping is 'absolutely essential to avoid player frustration', 'you can't place an item for the player', and spatial inventories only work during plausible pauses in the action.

### Angling permit, closed spawning season, minimum legal size, and a bag limit with culling  `L`

**Как работает.** The fisherman villager already exists — have him sell a paper Angling Permit stamped with a water-body/biome and an expiry in in-game days. Fish profiles gain a spawning window (Serene Seasons is already a dependency) and a minimum legal length derived from the existing max-weight/size data. During the closed season, or under the minimum, that fish must go back; keeping it means the villager refuses to trade with you and confiscates it, or a warden villager fines you. Add a per-species daily bag limit to the keepnet with Bassmaster culling: at the limit, keeping a bigger fish auto-releases your smallest. All of it configurable off for players who don't want rules.

**Почему подходит.** It's the one whole axis of realism sims the mod has none of, it's entirely single-player (rules imposed by the world, not by other anglers), and it reuses the villager, the keepnet, Serene Seasons and the existing prime-grade weight data. It also gives the prime-fish sale loop an actual constraint instead of infinite hoarding.

**Источник.** Fishing Planet sells per-waterway licences by duration and fines you for keeping fish without one (players report being fined even for releasing on an expired licence). Fishing: North Atlantic: 'Every fish species can only be fished within its season' (Misc Games KB). Fishing: Barents Sea makes you plan around yearly quotas. Bassmaster's own Opens rules give the culling mechanic: at the five-fish limit you 'cull smaller fish by replacing them with larger ones', and may not keep fishing until you're back down to five.

### Fly fishing as drift management: drag, mending and the hatch - the one flow that uses Minecraft's water current  `L`

**Как работает.** A fourth rod class (fly), whose weight-forward line's grams fit the Tackle Bench weight model you already have. After the cast the fly rides the local flow vector - Minecraft flowing water already carries one - and a drag meter climbs whenever the flow under the line is faster than the flow under the fly; a mend key resets it, on a cooldown, with a penalty for mending over a visible fish. The drift runs a few seconds until the fly reaches the end of the seam, then you re-cast, and the bite roll is gated on the fraction of the drift spent drag-free rather than on any click cadence - which is what makes it genuinely different from the existing retrieve. Fly type picks the layer: dry sits on the surface and only interests risers, nymph fishes a set depth under an indicator, streamer swings across the current and can reuse the click cadence. A hatch table keyed to season, time and biome names the matching pattern among about eight flies, and a mismatch is a multiplier rather than a hard block. Casting is the fixed-line, drag-free part of the sport, so the pull-out QTE logic from your reel-less poles largely transfers - and this is also where tenkara's high-stick, line-off-the-water drift belongs, rather than as its own rod class.

**Почему подходит.** This is the biggest gap in a mod called River Fishing: rivers currently play like ponds with a current label, and this is the only proposed flow that consumes the flow vector Minecraft hands you for free. It also ties three other ideas into a single loop - foraged mayfly bait, hatch windows, visible rising fish - and needs only a new blank, eight 16x16 fly sprites and a meter. Expect it to be the most expensive idea here and the most distinctive.

**Источник.** Real technique: drag is line sitting in faster water dragging the fly across the current unnaturally, fixed mid-drift by mending - flicking the belly of the line upstream, or downstream when the water between you and the fly is slower and the fly is getting ahead of the line (Orvis 'The Basics of Mending to Achieve a Drag-Free Drift'; Vail Valley Anglers). Games: Fly Fishing Simulator HD builds its loop on checking the hatch, viewing insect pictures, then choosing a matching pattern from 160 across 22 sizes, and treats line control as an input separate from the cast; Ultimate Fishing Simulator splits flies into dry, nymph, streamer and wet with a strip retrieve for streamers, and thejighead.com rates it the best fly-fishing sim; Fishing on the Fly's headline feature is real-world hatches that shift with the seasons; even Far Cry 5 ships a fly-fishing minigame.

---

## Бой, снасть и слабое звено

*32 идей.*

### A one-line loss autopsy that names the mechanical cause and the measured number, never the player  `S`

**Как работает.** Enumerate the loss modes you already roll for - STRUCK_EARLY, STRUCK_LATE, HOOK_PULLED, LINE_PARTED, LEADER_BITTEN, DRAG_LOCKED, ROD_OVERLOADED, SNAG, FOUL_HOOK_TORE - and make it a build rule that every lang string for them contains at least one numeric placeholder: "The hook pulled. You struck %s s after the float went under; this one needs the strike inside %s s." One action-bar line, never chat (no spam), same string appended to the session diary. Grammar rule: state, number, action. Never the words "you failed", never an imperative the player cannot obey - which is exactly the class of bug you already shipped once with the hold-the-button message.

**Почему подходит.** Two of your bug reports were comprehension failures, and both would have been self-diagnosing with one honest line. It costs a lang file and an enum, needs no art, no sound, no screen, and it is the single highest ratio of understanding gained per line of code in this whole list.

**Источник.** theHunter COTW's failed harvest check, which tells you precisely which criterion failed rather than that you failed; Baldur's Gate 3's combat-log breakdown, which is how players answer "why did that check fail" (bg3.wiki, "Dice rolls"); Fishing Planet, which does not just show tension but highlights secondary indicators naming which part of the tackle is overloaded (Fishing Planet Wiki, "Indicators").

### Gate all the new information behind a craftable wearable, so teaching is an item you own rather than a setting you toggle  `S`

**Как работает.** One 16x16 item, polarised glasses, head slot. Worn: the bite ledger, the spot moodles' labels, the weakest-link readout and the numeric tooltip lines all appear. Not worn: the mod behaves exactly as it does today. Add config information_tier = bare | goggles | always, and set the realistic preset to bare so purists keep the silence they came for. Craft from glass panes plus a dyed lens so it slots into the existing dyeing code.

**Почему подходит.** It resolves the actual tension in the design brief - the owner wants deliberate silence, the players want to understand - without either side losing. Create's goggles are the proof that Minecraft players accept "information is a tech tier", and UFS's realistic-mode switch is the proof that a fishing sim can ship a legibility aid without diluting its identity.

**Источник.** Create's Engineer's Goggles - a head-slot item that adds stress, RPM, capacity and fluid readouts to what you look at, colour-coded, and shows nothing when not worn (Create Wiki / Creators-of-Create "Engineer's Goggles"); Ultimate Fishing Simulator's underwater camera, a learning aid the game deliberately DISABLES in Realistic Mode (Steam store page; PC Gamer, "Realism be damned: Ultimate Fishing Simulator's underwater camera is great").

### Name the weakest link during the fight, with its load percentage and a pitch-mapped tone  `S`

**Как работает.** Each fight tick, compute loaded fraction for line, leader, hook, rod and drag; take the maximum; print that component's name and load on the action bar - "weakest link: 0.16 mm mono, 78%" - coloured on Fishing Planet's ladder (white under 60%, yellow 60-85%, red above 85%). Add a non-vocal audio channel that does not violate the silent-bite rule because it fires during the fight, not the bite: play block.note_block.bit every 10 ticks at pitch = 0.5 + 1.5 x load. Rising pitch is instantly legible and needs no recorded sound.

**Почему подходит.** You already render six rod-bend steps, so the player can see THAT tension is high but never WHICH of five components is about to give. That missing noun is why players cannot learn the drag curve, the diameter table or the leader rules from play. RF4 makes the same model teachable with one sentence, and the pitch ramp gives the fight a tension channel that works when the player is looking at the fish instead of the HUD.

**Источник.** Russian Fishing 4, where the tension bar explicitly indicates "load on the weakest element of your rig" and the weakest element - reel, line, hook or rod - is what actually breaks when it fills (Steam Guide, "Russian Fishing 4 Beginner guide", id 3252048013). Fishing Planet, whose tension indicator glows yellow, then red, then full-overload, and which shows SECONDARY indicators naming which part of the tackle is overloaded (Fishing Planet Wiki, "Indicators").

### A one-line negative-space tooltip on every tackle piece: what this thing cannot do, written as flavour that is simultaneously the rule  `S`

**Как работает.** One always-visible line per rig, lure, hook, groundbait and leader, in the register of a tackle-shop card, phrased as a foreclosure rather than a stat: "Feeder rig - sits where it lands. Retrieving it only drags a cage of crumb through the silt." "Steel leader - a pike cannot bite through it, and a wary roach will not come near it." Numbers stay behind Shift. Rule for writing them: the sentence must make one wrong action feel obviously wrong, so it works as instruction even for a player who reads it as flavour.

**Почему подходит.** A stat block tells a player what a thing is; a NOT-line tells them what to stop doing, which is the failure mode you actually observed. It is the cheapest possible fix - lang strings only, no code - and it fits a realism mod's voice better than a tutorial popup, because real tackle packaging talks exactly like this.

**Источник.** Dark Souls item descriptions, where the same sentence that tells the story of the knight who carried the shield also tells you how well it deflects magic - narrative and mechanical information in one line the player reads anyway (Game Developer, "Narrative Design in Dark Souls"; PC Gamer, "The art of flavour text"; the academic piece "Narration of Things - Storytelling in Dark Souls via Item Descriptions").

### A hook-hold model where slack line fills a tear meter instead of instantly losing the fish, and barb/size/wear finally matter  `S`

**Как работает.** Replace binary loss-on-slack with a 0-100 tear meter. Slack, a bad hookset, foul-hooking and every violent run add to it at a rate divided by hook quality: barbed vs barbless, hook size versus species mouth size, and accumulated hook wear. At 100 the hook pulls. Fish escapes therefore have a readable cause, and the counterplay is tackle choice, not twitch. Compensate by cutting bite frequency, exactly as those players proposed.

**Почему подходит.** The mod already ships hooks #1-#20 with wear and a tension-based fight model, but hook choice currently has no moment where the player feels it. This is wiring existing data into the existing fight loop — a float and a divide — and it makes the multi-rod pod setup viable instead of punishing, which is the specific thing Fishing Planet players quit over.

**Источник.** Fishing Planet 'Bottom fishing seems pointless' (steamcommunity.com/app/380600/discussions/0/2941371547507423777/). Players: "fish escape the second the line tension drops to zero. Which means that juggling rods is next to impossible", "Barbed hooks are now pointless" because fish escape regardless of hook type, and "even after a decent fight with fish on rod 1, i set it down, line is tight and not even 20 seconds go past and it escapes." Their ask is that fish stay hooked longer and bite rate drop to compensate.

### Stage the bite: species-specific nibble counts you must let run out, with a rod-tip tremble frame as the tell — two redundant cues for the same event, neither of them a sound or a text line  `S`

**Как работает.** ACNH and Sneaky Sasquatch both split the bite into approach → nibble(s) → commit, and both make the commit visually unmistakable (violent shake, heavy splash) so a beginner cannot miss it while a veteran can still strike early on a confident species. Graveyard Keeper's trick is different and better for you: it duplicates the cue on the player's own body, so if you are looking at your character instead of the float you still get the information. Port: add `nibble_count` and `nibble_interval` to the fish profile (cautious bream 3-4 taps, pike 0, carp 1 long draw). Each nibble twitches the float/quiver tip one pixel; striking during a nibble loses the fish. On the final tap the tell escalates — the float goes under and stays under, or the tip loads hard. Second redundant cue with no new sound and no rig: one extra rod sprite that alternates with the current bend frame at ~6Hz during the nibble phase, using the sprite-swap bend pipeline you already have for tension. The player learns species by rhythm, and premature strikes become their fault instead of RNG.

**Почему подходит.** You deliberately removed the bell and the text, which was right, but you left only ONE channel. Graveyard Keeper shows that the fix for a silent cue is a second silent cue, not a noisy one. And per-species nibble counts are free variety: it is one integer per profile across 79 species, and it makes the float minigame you already have into something learnable rather than a reaction test. Flagging the adjacency: this is not the float timing window you already ship — that is 'hit the moment', this is 'count the pattern, then hit the moment'.

**Источник.** Animal Crossing: New Horizons (per Game8's fishing guide: the fish nibbles a minimum of once and usually twice before biting down; striking on a nibble fails) + Sneaky Sasquatch (per Pocket Gamer's and Touch Tap Play's fishing guides: nibbles first, then the fish 'chows down' and shakes violently with heavy splashing, and only then is it hooked) + Graveyard Keeper (per the official wiki: the bite can be read EITHER from the float moving vertically OR from the Keeper's hand rhythm turning erratic — two tells, same event).

### Show tension on the fishing line itself — recolour the line you already render, and make the correct response to a red line a TAP, not a release  `S`

**Как работает.** Spiritfarer's whole fight is one variable with three input states (hold / tap / release) and it communicates the variable diegetically by colouring the rod and line rather than drawing a HUD bar. The third state is what makes it a skill: novices bounce between hold and release and lose fish either way; the tap state is the thing you discover. Port: you already draw the line in-world with GL_LINES and it is visible to other players — tint it by your existing tension value (slack grey → working amber → screaming red) and add a fourth state for the drag actually paying line. Then add the tap: currently drag-open always pays line, which means holding is punished and releasing is punished, so introduce a pumped retrieve — short taps gain line at reduced rate without adding tension, which is literally how you fight a fish on a light rod. Optional and near-free: at max tension, alternate the line between two shades at 10Hz so it reads as vibrating.

**Почему подходит.** You have six rod-bend steps, so tension is visualised on the rod but not on the thing that actually breaks. Moving the readout onto the line closes the loop between the number and the failure it causes, costs no new art (a colour multiply on an existing renderer), and works in third person and on rod pods where the bend is hard to read. The tap state gives your fight a mid-skill layer between mashing and waiting.

**Источник.** Spiritfarer — read via the Spiritfarer Wiki fishing page and Pro Game Guides' fishing guide: the line is yellow/orange when it is safe to hold the reel button, turns red at high tension, and holding through red snaps it — but fully releasing lets the fish escape, so the answer is to tap the button so the fish 'can't get too far away'.

### Offer the treasure DURING the fight as a greed decision, not after it as a loot roll  `S`

**Как работает.** Stardew's chest is the best five seconds in the whole minigame because it asks a question mid-action: take the safe fish, or risk it for the box. The Treasure Hunter tackle exists purely to change the answer, which makes a piece of gear feel like a decision-modifier rather than a stat. Port: mid-fight, roll a chance that the line has fouled something on the bottom (a sunken crate, a lost rig, someone else's snagged lure, a bottle). A prompt offers 'bring it up too' — accepting adds a flat tension penalty and lengthens the fight, refusing costs nothing. You already have treasure bycatch and snags, so the loot tables and the tension maths exist; this only moves the moment of decision from after the outcome to inside it. Then add one tackle item whose whole job is to reduce the tension penalty, giving you a Treasure-Hunter-shaped piece of gear that modifies a choice.

**Почему подходит.** Your bycatch is currently something that happens TO the player. Making it something the player chooses converts a dice roll into a story ('I lost a 6 kg bream trying to land a boot'), which is what people post about. It is also the cheapest possible use of loot content you have already written.

**Источник.** Stardew Valley — from the Stardew Valley Wiki 'Fishing' page: a treasure chest can appear inside the bobber-bar minigame and must be caught by parking the bar over it in addition to keeping the fish there, and the Treasure Hunter tackle is the only way to take a chest without forfeiting the 'perfect' catch.

### Make the fight cost daylight: a set amount of in-game time must pass to land a fish, and skill only buys that time back  `S`

**Как работает.** DREDGE deliberately refuses to fail you on reflexes. Instead every catch consumes a fixed chunk of the clock, skill compresses it, and the pressure comes from the fact that time itself is scarce — you can always land the fish, but landing it might cost you the daylight you needed to get home. Port: attach a real in-game-tick cost to each fight scaled by fish weight and fight pattern, and let good play (correct steering, clean pumping, working the drag instead of hauling) shave it. A trophy catfish then genuinely costs an evening, so choosing to fight it is choosing not to fish the dusk window for something else. This wires into your existing season/time-of-day bite gating: the opportunity cost is real because dusk is when other species feed.

**Почему подходит.** You already have the two ingredients — a time-of-day-driven bite engine and long fights — but they do not currently trade against each other, so a long fight is free. Adding a clock cost gives your fight an economy that needs no money, no market and no other players. It also means a merciful difficulty preset can exist without softening the sim: instead of making fish easier, missed inputs just cost more of the day.

**Источник.** DREDGE — the design is stated in the Can I Play That accessibility review and echoed in the Steam discussions: a set amount of time must pass before a fish is caught, hitting the timing prompts speeds it up and missing them slows it down, and there are 'no real setbacks' for missing, so the game is playable without engaging the prompts at all. Time is DREDGE's actual currency because the day ends and the dark is dangerous.

### A personal-best ledger per species, and celebrate a new PB by drawing the new fish next to your old best at true relative scale  `S`

**Как работает.** Twilight Princess's fishing hole has almost no reward economy — the hook is a scoreboard against yourself, per species, forever. Stardew's 'Perfect!' is the complementary trick: a single word, awarded for execution rather than luck, with a real mechanical payoff behind it. Port: store max weight and max length per species in the journal NBT you already have. On landing, if it beats your record, the catch panel shows the new fish's 256px icon beside the previous best at correct relative scale — you already size those icons to real fish size, so the comparison is literally free — with the delta in grams. Add a 'Perfect' style banner driven by the condition grade from idea 8 (clean fight, no foul hook, no max-tension seconds), and let a perfect landing be the only way to hit the top condition tier. The 'celebration' is a comparison, not a fanfare, which fits a silent mod.

**Почему подходит.** You have trophies and prime grading, but both are absolute thresholds — once a species is prime-graded there is no reason to catch it again, so 79 species is 79 checkboxes. A personal best is an infinite, self-scaling goal per species, it needs zero new art because the size-scaled icons already exist, and it makes the ordinary catch of an ordinary roach potentially the best moment of a session.

**Источник.** Twilight Princess — per the Zelda's Palace fishing guide, Hena's Fishing Hole records six species with your MAXIMUM SIZE CAUGHT, and boat-caught fish go into her aquarium; the records are the reason to keep fishing after the quest is done. Plus Stardew Valley's 'Perfect!' flash on a flawless catch (Stardew Valley Wiki 'Fishing': a perfect catch upgrades quality one tier and multiplies fishing XP by 2.4).

### Ship one piece of tackle that is objectively too good and let the world punish you for using it  `S`

**Как работает.** Twilight Princess put a cheat item in the game, wrote a character who objects to it, and gave it a real mechanical cost (indiscriminate bycatch). The result is that using it feels transgressive rather than broken, and choosing not to use it becomes a self-imposed rule the player is proud of. Port: add an unsporting tier — a heavily baited treble rig, a bait ball, a set-line, a gill net for your stocked ponds. It catches magnificently and it costs you: it slaughters the chunk's stock through the depletion system you already have (so the spot dies for seasons), it foul-hooks constantly (so condition grades tank and released fish die), and the fisherman villager refuses to trade with you for N days if you show up with net-caught fish. No morality text, no lecture — just consequences routed through systems that already exist. Optional: an advancement for never crafting one, and a bleak one for crafting three.

**Почему подходит.** Your depletion, foul-hooking and villager-trust systems are all sinks with nothing pushing hard against them. A deliberately overpowered item is the pressure test that makes them visible, and the sportfishing-vs-poaching tension is real subject matter for a realism mod rather than an invented one. It is also almost entirely data: one rig, one bycatch table, one depletion multiplier, one villager gate.

**Источник.** Twilight Princess — the Sinking Lure, per the Zelda Dungeon wiki, Zelda's Palace guide and the RPGClassics guide: it sinks fast, attracts fish from all over the lake with barely any working, and Hena 'deems the Sinking Lure to be somewhat of an unfair advantage' and will CONFISCATE it if she catches Link using it. Its documented downside is that it pulls in every fish in range, causing constant unwanted bycatch.

### A line clip that fixes a repeatable cast distance so you can hit your own baited spot  `S`

**Как работает.** Shift-right-click the reel to clip at the current line-out distance; store clip_m on the reel NBT. On the next cast the power bar hard-stops at that distance no matter how long you charge. Overshoot attempts make the line hit the clip and the rig cracks back down short, with a small tension spike and a splash (so abusing it has a cost). Then make accuracy pay: landing within ~1 block of a groundbait spot grants that spot's full attraction, 1-3 blocks a fraction, beyond that nothing. The clip has to be reset by hand after a fish takes line, which is the real-world annoyance and a nice little ritual.

**Почему подходит.** River Fishing already computes cast distance from the charged power bar and already has groundbait spots, but nothing rewards precision — max power is always fine. This closes the loop with a float and a boolean, and turns the existing weight/cast-window system into an aiming skill.

**Источник.** Russian Fishing 4 — the clip 'fixes the maximum distance of the throwing line', and players describe it as 'especially useful when you need to throw groundbait or boilies out with a separate tool so that you can put your rigs right into the area you are baiting' (RF4 Steam discussions 'Clip' and 'Can someone explain me what is clip', read via search extract).

### Friction-brake heat: a tight drag on a hard-running fish overheats and then fails  `S`

**Как работает.** Accumulate heat = drag_setting × metres_of_line_paid_per_second, bleeding off whenever the fish isn't taking line. Three states drawn as colour on the existing drag widget (cool / orange / red) plus a faint sizzle particle at the reel and a hiss. Orange doubles wear on the reel's drag component; red multiplies the existing break roll each tick. The counterplay is to back the drag off during a run and wind it up between runs, which is exactly what a real angler does and what the current set-once drag never asks for.

**Почему подходит.** The mod already has a drag curve, drag that pays line, gear wear and probabilistic breaks — every input exists. This makes the drag a live instrument during the fight instead of a pre-fight setting, for roughly one float and a colour swap.

**Источник.** Russian Fishing 4 — a flame mark means the friction brake is overheating; with a tight drag and a hard-hitting fish it lights orange, and in that state 'the reel's friction brake is consumed twice as much'; if it stays orange it can go red, 'in which case the fishing line will break' (RF4 forum/wiki material via search extract; drag scale is 0-29 with 15-20 the recommended working range per the yeoshin RF4 guide).

### At each in-game new year the journal auto-writes a bound almanac of the year: totals, personal bests, new species, the water that gave the most, the weather you fished in, and the biggest one that got away — and the shelf slowly fills with years  `S`

**Как работает.** Keep per-year counters in player NBT (they cost bytes): catches by species, best weight and best Wr, species first caught this year, hours by season/weather/time band, casts per fish, break-offs with their peak tension, waters fished, pins and slams earned. At the year boundary, generate a written book with a fixed set of templated lines filled from those counters, title it 'Angler's Almanac, Year N', and drop it into the player's inventory (or the existing guide shelf). Include the year's unverified 'one that got away' from the break-off ledger, and a single line comparing this year with last. Then reset the counters.

**Почему подходит.** It is the cheapest possible long-term reward — generated text, no art, no new systems — and it rewards *playing* rather than *completing*, which is the one thing pure checklists cannot do. It also makes a long-lived world visibly a long-lived world: a shelf of eight almanacs is a stronger keepsake than a full journal, and it gives the existing guide shelf and the year boundary something to hold.

**Источник.** IGFA's annual World Record Game Fishes book, in which every record and Slam/Trophy Club member is "permanently listed online and in the IGFA's World Record Game Fishes book" (igfa.org/slam-and-trophy-clubs). Volunteer angler-diary and logbook programmes behind cooperative tagging and creel data (NOAA Cooperative Tagging Center; USM Cooperative Sport Fish Tag and Release). Annual-cycle award programmes: Fish Ohio pins (2025: 10,127 pins, 1,009 Master Angler pins) and NY DEC's yearly Angler Achievement Awards.

### A shoal you can watch move and intercept — a drifting cluster of bubble particles you cast ahead of, not at  `M`

**Как работает.** One server-side moving point per water body: a shoal marker with a species group, a size class and a slow patrol path along the bank/thalweg. It renders as nothing but a loose knot of vanilla bubble/underwater particles at its position, only visible within ~30 blocks. Bite chance is heavily boosted within a radius of the marker and near-zero far from it. Because it moves at a walking pace, the correct play is to cast where it will be — leading a target, which is a skill, not a wait.

**Почему подходит.** Answers the most-repeated wish in the genre without a single entity model: it is a data point plus particle spawns, entirely within the project's stated asset ceiling. It also solves the mod's core tension — a rich hidden bite engine that the player experiences as a slot machine — by giving the RNG a visible body you can chase.

**Источник.** Fishing Planet '♥♥♥ this game is boring' thread (steamcommunity.com/app/380600/discussions/0/3183486320477047832/): the complaint is "it's literally as boring as real life fishing" and the specific wish is to "see the fish moving under the water." Ultimate Fishing Simulator 'Fish levels' (app/468920/discussions/0/1489992713702142636/): the player wants "schools of fish swimming around in a realistic manner until I manage to trick it into biting into a metal hook", and says pre-stocked ponds feel like "shooting fish in a barrel."

### Make the fight directional — the fish picks a heading, you must pull the opposite way, and hard pulls drain an angler stamina bar that only refills when you stop fighting  `M`

**Как работает.** Moonglow Bay turns a scalar tug-of-war into a 2D one for free: the fish has a heading, the correct input is the opposite heading, and the punishment for a wrong input is routed through the tension variable you already have, so it needs no new failure state. Reel Fishing adds the resource half — the player, not just the fish, has stamina, so 'pull hard' is a budgeted decision rather than a free action. Port: give each run a heading (left/right/away/under), drawn as the on-screen angle of your existing line plus a GL_LINES arrow at the rod tip. Steering with the opposite key reduces fish stamina faster; steering the wrong way adds tension at 2-3x. Add an angler stamina bar drained by pumping and by steering into a run, refilled while you let the drag work — which is exactly the real technique of letting the fish tire itself. Your seven fight patterns become a heading script per pattern (greyhounding = repeated away-runs, sounding = a long 'under' hold).

**Почему подходит.** Your fight is currently deep but one-dimensional, so a 40-minute catfish and a 40-second roach differ only in duration and numbers. Directions give the fight legible phrases without new content, and the angler-stamina resource is the only realism-true pressure your fight lacks — a real fight is limited by the angler as much as by the tackle. It also reuses the seven patterns you already authored instead of replacing them.

**Источник.** Moonglow Bay (Gamepur's 'How to fish in Moonglow Bay' and the Moonglow Bay Wiki): fish swim in different directions to escape, you use WASD to pull AGAINST the direction of the fish, pulling the wrong way or letting the fish charge raises line tension until it snaps, and a 'strike' hauls harder in a chosen direction but two strikes back to back tire you out and let strong fish pull away. Plus Reel Fishing: Road Trip Adventure (WayTooManyGames review): reeling and pulling against a fish depletes a stamina bar that refills when you stop fighting.

### Block the reel-in with discrete 'the fish has dug in' checkpoints that must be broken by pumping the rod, with the number of pumps set by your rod's backbone  `M`

**Как работает.** WEBFISHING splits a continuous progress bar into 2-6 gated segments. It reads instantly, it converts an abstract stat (Rod Power) into a visible number of clicks, and it gives every fight a rhythm of push–wall–push. Port: during a long fight, roll 1-5 hold points along the fight's progress. At a hold point the fish has gone into weed, under a snag, or simply sulked; retrieve stops and a small counter appears. Each rod pump knocks the counter down by the blank's lifting power, so your 13 blanks finally differ in a way the player can COUNT rather than infer from a tooltip. Overpumping past the counter spikes tension — so the wall is a decision, not a mash. Tie the hold-point count to your existing snag data per water body: weedy pond = 4 holds, clean gravel run = 1.

**Почему подходит.** Your fight model is rich but flat in shape — tension rises, stamina falls, you wait. Hold points give it structure and they make the blank choice legible, which is currently buried in cast-weight windows and tooltips. It also gives the bottom/carp classes something to do during the long middle of a fight where they currently just hold.

**Источник.** WEBFISHING — read the WEBFISHING Wiki 'Fishing' page directly: during the reel minigame the player meets between 1 and 5 'yank spots', drawn as a bar across the catch meter with a number above it; clicking to yank reduces that number by the player's Rod Power (base 1, upgradeable to 125) and the meter cannot advance until it hits zero. Reel Speed (0 → 1.0) is a separate upgradeable stat.

### Weakest-link tackle: the game decides WHICH component breaks, so under-rating the line on purpose is correct play  `M`

**Как работает.** Give rod, reel drag and line each their own kg rating (rod test already exists) and track three separate stress values during the fight instead of one tension. When peak load crosses a rating, THAT component is what fails: the line snaps (cheap — you lose the rig and some metres), the rod takes a heavy permanent durability hit or snaps outright (expensive), the reel's drag burns out (mid). Show the chain in the tackle bench as three bars with the intended breaking point marked, so a player can see they've built a setup that will cost them a rod instead of a hooklength.

**Почему подходит.** River Fishing already has 13 blanks with test windows, a drag curve, line strengths and probabilistic breaks — everything except the resolution rule that makes those numbers relate to each other. It turns the tackle bench from a compatibility checklist into an engineering decision with a stated failure mode.

**Источник.** Ultimate Fishing Simulator 2 beginner guide — gear is deliberately laddered (rod 4.5 kg, reel 4.2 kg, line 4 kg) because 'the line is cheaper to replace than the reel, which is cheaper to replace than the rod', and the fight shows three separate stress bars for line, rod and reel. RF4 guides say the same: 'never choose a line that is higher than the load-bearing capacity of the rod', 'line should be slightly weaker than the rod to prevent rod snapping'.

### Hook-hold quality that decays during the fight, so fish pull off without the line ever breaking  `M`

**Как работает.** At the strike, roll hold ∈ [0,1] from: strike timing accuracy, hook sharpness/wear, hook size vs the fish's mouth, whether you struck a tap or a real take, and rod class. Keep it hidden but hint it once ('solidly hooked' / 'just nicked'). During the fight hold degrades on tension spikes above a threshold, on slack line (a fish that stops and shakes its head), and on jumps. At zero the hook pulls: the rod springs straight, the line goes slack, the fish is gone — gear intact, bait gone, and no line-break excuse. Foul-hooked fish (already implemented) start with a low hold, which is finally *why* they feel wrong.

**Почему подходит.** Right now a lost fish is a broken line or a 50/50 roll; this adds the other real loss mode and makes hook choice, hook wear and strike timing all feed one visible consequence. It reuses the tension value the fight model already computes.

**Источник.** Fishing Sim World: Pro Tour — Dovetail list 'hook set quality, drag settings, and rod positioning' as what decides a fight's outcome. RF4 players: fish 'can get loose even when tired, and you never know when fish will get loose', with hook star rating and barb size cited as what prevents it (RF4 hook/fight discussions via search extract).

### Pump and wind: line comes in on the down stroke only, and rod angle is a live tension control  `M`

**Как работает.** Lifting the rod (hold a key) builds tension and drains fish stamina faster but gains no line; dropping it lets you crank, and only the down stroke recovers line. Straight hold-to-reel still works but at ~40% of the pump rate, so the efficient way is a cadence. Rod angle becomes a third input beside reel and drag: high rod = more cushion and more stamina drain but more tension; dropped rod = instant tension relief but the fish gains line. The existing six rod-bend sprites already cover the stroke, so it costs no art.

**Почему подходит.** The fight already has tension, stamina, runs and bend steps but no rhythm — it's a hold-and-hope. A pump cadence gives the fight the same input texture the mod already gave the lure retrieve (click gaps), for one keybind and a stamina multiplier.

**Источник.** Ultimate Fishing Simulator / UFS2 — 'pump your rod up using the right mouse button, and when you let go, hit your left mouse button and reel in a little bit... repeatedly to pull the fish to the surface so it will tire much faster'; and if the tension bar flashes red 'quickly lower your rod from 45 degrees to 90 degrees to lessen the tension'. Reel Fishing does a simplified version where button presses reduce tension.

### Turn resistance: steer the running fish away from the snag instead of just holding it  `M`

**Как работает.** Give the hooked fish an actual heading during a run — show it as an arrow or a left/right bias bar on the fight HUD. Holding side pressure (A/D or mouse yaw) bends the heading a few degrees per second at a tension cost that scales with fish weight. The world already contains kelp, lily pads and the mod's own snags: if the fish reaches one, you're snagged and the hook-hold and break rolls get ugly. The per-species run patterns already implemented (relentless, greyhounding, sounding) supply the headings for free.

**Почему подходит.** It converts the existing snag system from a random punishment into the thing you're fighting against, and it works with the existing tension/drag model — no new geometry, just a heading float and one HUD arrow.

**Источник.** Fishing Sim World (Dovetail's own Festive Update notes) added 'turn resistance that allows the player to influence the direction that the fish is swimming'. Rapala Fishing Pro Series does the console version: 'the fish will dart side to side as it tries to break free, and you need to follow its actions by moving the left stick in the opposite direction'.

### A finite spool of line — being spooled as a distinct way to lose the fish  `M`

**Как работает.** Turn the existing spool-diameter rule into metres of line actually loaded (diameter × a capacity table per reel size), tracked on the line item. Casting spends metres, the drag pays them out during runs, reeling recovers them. Both the maximum depth you can fish and the maximum distance you can cast are hard-capped by what's on the spool. If a big fish runs you to zero, the line parts at the spool knot and you lose everything past it — the classic 'spooled' loss, which punishes hooking a 14 kg fish on a 1000-size reel even when the line's kg rating is technically fine. A break now costs a measured length of line rather than an abstract durability tick, and re-spooling becomes a real errand.

**Почему подходит.** The mod already has reels 1000-14000, a spool-diameter rule, line by diameter, and drag that pays line — this is the missing unit that makes all four relate. It also gives reel size a downside, which currently it barely has.

**Источник.** Call of the Wild: The Angler bottom-fishing dev diary: 'you can only fish as deep as your line capacity will allow', with a HUD indicator for whether the bait reached bottom. RF4 sells reels by line capacity per diameter and the drag pays real metres off that spool (RF4 reels stats guide / maintenance guide).

### Per-species qualifying sizes that award a physical pin item, plus an annual meta-award for four different species in the same in-game year — progression that resets every year instead of ending  `M`

**Как работает.** Add `pin_length` / `pin_weight` to each species JSON, authored per species rather than as one flat 70% rule, and qualify on either — the easy species stay easy on purpose (the real programme hands out ten thousand pins a year; that is the anti-chore design). A qualifying catch spawns a pin item (16x16, one sprite per species tier or one sprite tinted per family). Pins are placeable on a pin-board block (a 3x3 grid of item frames, effectively). Track a per-year set of qualifying species; hit four distinct ones before the year rolls over and the villager hands you the year's Master Angler pin, stamped with the year. The set clears at new year, so the loop restarts forever.

**Почему подходит.** The mod already has prime grading at a flat ≥70% of species max; what it lacks is (a) data-authored per-species bars so a bream trophy is genuinely easy and a catfish trophy is brutal, (b) a yearly reset that gives a returning player something to do in a world where the journal is already full, and (c) collectibles you can actually hang on a wall. Serene Seasons is already wired in, so the year boundary exists.

**Источник.** Ohio DNR Fish Ohio programme (ohiodnr.gov, 'Celebrate a Trophy Catch with a Fish Ohio Pin'; 2026 news coverage): 26 species with per-species minimum lengths (25" inland walleye but 28" on Lake Erie, 18" smallmouth, 20" largemouth, 32" northern pike, 13" crappie), a commemorative pin for a first qualifying catch and a Master Angler pin for qualifiers of four different species in the same year — 10,127 pins and 1,009 Master Angler pins issued in 2025. New York DEC Angler Achievement Awards: 40 species with minimum qualifying lengths, a species-specific sticker per catch, plus a Youth category with lower thresholds. IGFA's 2025 Trophy Fish Clubs qualify on "either the required length or weight" (e.g. Red Snapper 25 lb or 35 in).

### Knots you actually choose, each retaining a published percentage of line strength, with breaks that name which link failed  `M`

**Как работает.** When tying a rig at the Tackle Station, the player picks the knot for each connection — main line to leader, leader to hook. Each knot carries a retention %, a line-type suitability (FG and PR only for braid-to-leader, Trilene only for mono and fluoro) and a difficulty gated behind angler level or a journal unlock, so knots become a progression track with no new items at all. The rig's effective breaking strain becomes min(line_kg x knot%, leader_kg x knot%, hook_wire_kg) — the weakest-link principle made literal. Resolve the existing probabilistic break roll against that number, and make the break MESSAGE name the failed link: "the clinch knot at the hook gave way" versus "the line parted mid-length — abrasion" versus "the hook straightened". Apply a dry-knot penalty (say 15%) unless the knot is tied within reach of water or with a water bucket in inventory, which teaches a real habit for the cost of one inventory check.

**Почему подходит.** The mod already has line strength by diameter, leaders, hook wear and probabilistic breaks — and right now a break is an unexplained bad roll. This is the one layer that makes all those numbers legible: every break becomes a lesson with a named cause, and improving your knots is a progression the player earns with knowledge rather than with loot.

**Источник.** Real mechanism: knots.fish, "Fishing Knot Strength Chart" — Palomar/Snell/San Diego Jam ~95%, Berkley Braid ~90%, Trilene and Improved Clinch ~85%, Uni ~80%, Davy ~75%, and the basic Clinch the weakest common option at ~65%; line-to-line, PR ~99% and FG ~98% for braid-to-leader, Double Uni and Alberto ~90%, Blood ~85%; Bimini Twist ~100% doubled. Two extra rules from the same source: "a dry knot generates friction heat that weakens the line during cinching", and "a well-tied 85% knot is stronger than a poorly tied 95% knot." SaltStrong's Palomar-vs-Uni braid pull tests corroborate the ranking.

### System compliance: rod action plus line stretch as a single number that trades hooksets against hook pulls and breaks  `M`

**Как работает.** Give each of the 13 blanks an `action` (fast/moderate/slow) and each line material a `stretch` (braid ~3%, fluoro ~10%, mono ~20%). Compliance = action_flex + line_stretch + leader_stretch, weighted by the length of line out (a long cast on mono is more compliant than a short one). Then run two OPPOSING rolls inside the existing fight loop: (a) HOOK PULL per lunge or head-shake, scaling with LOW compliance and worsened by treble hooks, small hooks and hook wear; (b) BREAK on a sudden run, also scaling with low compliance — while the HOOKSET quality roll scales the other way, with stiffness, so a slow rod on stretchy mono fails to set a big hook at distance. The result is a real loadout triangle with no dominant answer: braid on a fast blank hooks everything at range but sheds fish that shake their heads; mono on a moderate blank holds fish but misses hooksets on hard-mouthed species at distance; and the fix is a short mono shock leader on braid, which the mod already ships as an item class.

**Почему подходит.** River Fishing already has 13 blanks, three line materials by diameter, leaders, hook wear, per-species fight patterns (greyhounding, sounding, head-shaking) and six visible rod-bend steps — every input this needs is present. Today the blank is mostly a cast-weight window; this makes the blank matter during the fight, which is exactly where the player is looking, and it gives the existing fight patterns a mechanical counter rather than just a different animation.

**Источник.** Real mechanism: Shimano, "Rod action and power explained" and The Tackle Room, "Rod Action Explained: Fast vs Moderate vs Slow" — fast tips load and snap back fast and give control, but "can pull hooks when fishing treble hook bait or soft mouth fish", while a moderate action means "when a fish shakes its head, the rod loads and bends rather than maintaining rigid pressure, and that flex acts as a shock absorber, reducing the chance of hooks tearing free." Line side: Anglers' Hut, "Choosing Your Shock Leader" — a shock leader should stretch only about 10-15%, more than 15% "can affect the hook set"; Montana Casting Co. and the braid literature — braid has "virtually no stretch and consequently minimal shock absorption, which can lead to hooks tearing out during excessive exertions from the fish."

### Set the float's hook depth yourself: fish on the drop, at dead depth, or laying-on overdepth — each with a different strike window  `M`

**Как работает.** Give the float rig a player-set hook depth. The true depth has to be discovered — a plummet item, or the bare-lead cast from the feature-finding idea — and guessing wrong simply produces nothing. Three regimes with distinct engine effects. ON THE DROP (set shallower than depth): only species whose current `feeding_layer` is mid or upper will take, and bites arrive during the sink, which narrows the existing float strike-timing window into a fast one — strongest over a cloudy groundbait mix. DEAD DEPTH: bait just touching bottom, balanced. LAYING-ON / OVERDEPTH: bait and some shot on the bed, so the float sits lower and reacts LATE — widen and soften the strike window, cut line-bite false alarms and cut the line-visibility spook penalty, but raise the deep-hooking rate, which then costs you on release survival. In flowing water, overdepth also anchors the bait instead of letting it drag, so it works on a river where dead depth does not.

**Почему подходит.** The mod already has a float strike-timing minigame, shot and rig weights in grams, and per-species depth preferences — so this reuses the exact lever it already implements (window width and timing) and attaches it to the most important decision in real float fishing. It also creates a genuine tradeoff between catching more and harming fewer, which the catch-and-release idea then cashes in.

**Источник.** Real mechanism: the Maggotdrowners "Fishing Overdepth" and FishingMagic "Float fishing overdepth" threads — start overdepth by a float's length, and in a good tow go two or three feet overdepth with tell-tale shot dragging bottom to slow the drift; "dragging the weight and hook along the bottom delays the action of the float when you receive a bite"; "the more overdepth you fish the less chance of line bites or fish spooking off your line, though there is more chance of a deep hooked fish"; for tench and carp spooked by a vertical line, anglers "lay on" three to five inches with dust shot on the bed. Ivan Marks' dumpy-float-over-depth method for shallow-water roach is the classic worked example.

### A spate cycle after rain: rising, peak, then fining down — with a different species list and a different casting range in each phase  `M`

**Как работает.** Turn MC's rain event into a per-water-body river state machine whose phase PERSISTS for hours after the rain stops. RISING (during and just after rain): water cold and coloured, most species off, barbel/catfish/bream on, everything pushed tight to the bank so only short-range casts score, flow strength up — which the existing fight model can read as extra tension. PEAK: fish only in slacks, margins and cushions; mid-river casts dead. FINING DOWN (the hours after): the best window in the game — chub, roach and perch switch back on and the crease is the place to be. NORMAL. Cue it with water clarity and the flow readout, and put the named phase in the water-body info line so it is legible rather than mysterious. If it is snowing rather than raining, use a snowmelt variant with a longer dead stretch.

**Почему подходит.** It converts MC's existing rain event from a small hidden multiplier into a multi-hour narrative with a genuinely different correct answer per phase, using only fields the engine already reads — weather, water body type, cast distance, species. And it produces the mod's best emergent story beat: the morning after a storm is the session you do not miss.

**Источник.** Real mechanism: Angling Times, "How to read and fish flooded rivers" — as the river rises "fish like chub will go off the feed quite quickly, whereas in contrast, species like barbel will start to feed more heavily"; at the peak barbel keep feeding but stay "close to the bank" between forays while other species retreat to slacks; as it fines down "the chub will come more on the feed, while barbel appetites will drop off slightly", and fish work the crease line; snowmelt floods need several days before fishing. Fisheries.co.uk, "Catching barbel on a flooded river" — barbel feed hard when the water is coloured and above about 43 °F, and rising water is often poor because it is cold and carries dirt that clogs gills.

### Bait as foraging: a bug net, worms that surface when it rains, and worm grunting on damp soil  `M`

**Как работает.** One bug net, one grunting stob, about eight critter items, all 16x16. Bug net: right-click tall grass, flowers, leaves or the air over water; the table is gated on biome plus season (Serene Seasons is already wired) plus time of day plus weather, so grasshoppers and crickets come from summer grass, mayflies and caddis only inside a hatch window at dusk in late spring, dragonflies over still water in daylight, beetle grubs from breaking rotten logs. Worms: right-click grass bare-handed while it is raining or within 2 blocks of water, or work the stob on damp soil for a slower weather-independent trickle. Every critter is an ordinary bait item feeding the bait-match input the engine already has, with tight species affinities (mayfly to salmonids and risers, grub to chub and catfish, cricket to asp and chub) and a hard global cap so it cannot be farmed. Copy Terraria's boss hook: exactly one critter - a hatch-only mayfly available perhaps three minutes a day - is the only bait a particular legendary fish will take.

**Почему подходит.** Bait today comes from farms and a trap, which makes it a base-building chore. Foraging makes the overworld, the weather and the season part of fishing: rain stops being a bite modifier and becomes the reason you walk into a field with a net. It reuses the existing bait plumbing entirely, needs only sprites, and feeds straight into the fly-fishing hatch table.

**Источник.** Terraria - the Bug Net is "nearly required in order to go fishing, as it is the initial and primary method of obtaining bait"; worms fly out of broken grass and dirt piles, their spawn rate rises about 300% during rain, only 5 exist at once, jungle baits (Buggy/Grubby/Sluggy) are biome-locked with a shared cap of 8, fireflies spawn only at night near NPC housing and cluster on new moons, gold critter variants carry 50% bait power, and the 666%-power Truffle Worm exists solely to summon Duke Fishron (terraria.wiki.gg/wiki/Bait). Vintage Story 1.22 devlog - earthworms are harvested with a Worm Grunter whose availability depends on rainfall and temperature. Wikipedia 'Worm charming' - vibrating the soil mimics a digging mole, and worms only come up in damp soil.

### Fish learn a lure: per-chunk, per-lure 'they've seen this one' memory that forces you to rotate tackle  `M`

**Как работает.** FMB puts intelligence on the species and uses it to make the tension minigame harder for smarter fish — a static per-species difficulty knob. River Fishing: make it dynamic and spatial. Keep a small counter per (chunk, lure family, colour band, size band). Every hooked fish and every refusal on that combination increments it; the counter decays over a few in-game days. A high counter cuts bite chance for that exact combination only — switching lure family, dye colour or gram-weight resets it to near zero. The journal shows it as a phrase per spot: "they've seen this one".

**Почему подходит.** River Fishing has chunk pressure, but it is species- and tackle-blind, so the answer to a dead spot is always 'walk away'. This gives the tackle bench, the 11 rig types and the dyeing system a reason to exist mid-session, and it is the single most-cited real angling behaviour (burned-out spots, lure rotation). It rides entirely on the chunk data already being stored.

**Источник.** Fishing Made Better — each species has an explicit intelligence stat alongside speed and weight, and populations "recover through migration or reproduction" (author's Minecraft Forum thread, minecraftforum.net/.../2934749; stats corroborated on rlcraft.wiki.gg/wiki/Fishing_Made_Better)

### Catch and release at the bank: survival odds from how you fought it, and the same fish caught again later, heavier  `M`

**Как работает.** The live-fish mods' entire pitch is that the catch arrives alive, then they stop — you bucket it or gut it. River Fishing: at the end of the fight offer Release as a distinct action from keeping. Survival probability falls with total fight duration, peak tension time, foul-hooking, and seconds spent out of water before release; a hooked-deep fish is worse. A surviving release returns that specimen to the chunk's stock flagged with an ID, and a later catch of that ID reports "released before" with a slightly higher weight roll. Release grants journal XP and a record entry but no fillet and no sale.

**Почему подходит.** Honest overlap note: River Fishing's stocking already lets you release fish from inventory to settle a pond, but that is a stocking action, not the landing-moment ritual, and it has no handling-quality or recapture model. Catch-and-release is the defining behaviour of modern angling and the ethical counterweight to the depletion and pressure systems already in the mod — and it needs literally zero new assets.

**Источник.** Fishing Real (3.19M downloads — "fish up real entities instead of the items"), Actual Fishing ("when you reel in fish, you get fish"), Realistic Fishing ("the actual fish is reeled in instead of the dead item") — all three on Modrinth; every one of them hands you a live fish and none of them models letting it go

### Events inside the fight: a second fish on the same lure, a weed bed to pump through, a snag to steer around, a treasure the fish drags you over  `M`

**Как работает.** Starcatcher and Stardew Fishing both bolt a treasure target onto the minigame — hit it, get bonus loot, no interaction with the fight itself. River Fishing: put events inside the existing tension/stamina/drag model, each with a physical answer rather than a new bar. Weed bed: tension climbs on a ramp and only pumping (short lift, then wind on slack) clears it. Second fish: two stamina pools on one hook, tension doubles, you will likely lose one. Snag run: the fish makes for structure and the answer is to open the drag and let it run rather than hold. Treasure: the fish drags the line across an object; landing it hooks both, at a break-risk premium.

**Почему подходит.** The fight is already River Fishing's best-built system — six bend steps, stamina, runs, drag that pays line, per-species patterns. Honest overlap: snags and foul-hooking already fire during float/bottom strikes; this extends them into the fight with steering answers instead of coin flips, and it lets treasure be a decision rather than loot table noise. It is the deepening every competitor's minigame is asking for and none of them can do.

**Источник.** Starcatcher — treasure appears as rare red-line targets you can hit mid-minigame "without losing the catch" (minecraft-guides.com/mod/starcatcher and Starcatcher changelog); Stardew Fishing — treasure chests inside the minigame, with a closed feature request asking for the chest rate to be configurable and another for datapack-driven treasure loot (github.com/BrownBear85/stardewfishing/issues)

### Feature finding: cast a bare lead, count the sink for depth, drag it back to feel the bottom, then clip up so every later cast lands on the same spot  `L`

**Как работает.** Add a marker/bare-lead terminal item tied at the existing Tackle Station. Casting it does two readouts. (1) DEPTH: count the sink and report it in blocks on the actionbar. (2) BOTTOM: a slow retrieve — reuse the existing click-cadence retrieve flow verbatim — reports what it drags over, derived from the actual block beneath the water column: gravel gives "bumpy, juddering, a hard clean spot", sand/clay "it glides, almost nothing there", mud/clay with seagrass or kelp "weighed down, weed", mud/soul-soil "heavy and dead, deep silt". Then let the player CLIP UP: store that cast distance in the rod's NBT so subsequent casts snap to it within a tight tolerance, with a "clipped up at 23 m" tooltip, and let the journal save the spot per water body so a later session can return to it. Bottom type then feeds the bite engine: a bottom bait on gravel or sand presents perfectly; in deep silt it partly buries and takes a penalty unless the rig is a popup/zig or the bait is buoyant; over weed it needs a longer hooklink. Feed the same bottom type into hook point wear (see the hook-pattern idea).

**Почему подходит.** It is the single most-cited real skill in modern bottom fishing and no Minecraft fishing mod has it, yet it needs no new assets — one item, a chat readout, and the retrieve flow that already exists. It also gives the mod's depth, cast-distance and rig-weight systems a discovery loop, and "clipping up" is exactly the kind of small mastery ritual that makes a sim feel like a craft.

**Источник.** Real mechanism: Korda, "How to Use a Marker Float" — braid for zero stretch so the lakebed telegraphs through the tip; depth by pointing the rod at the lead, tightening, then "carefully pull 1ft at a time off of the spool" and counting; and the feel vocabulary: gravel is "bumpy"/juddering, silt is steady resistance that makes the lead feel "heavy", sand and clay feel "almost as if your lead has been cut off, it glides so smoothly", weed weighs it down, and a bar or plateau makes the rod "lock up". Then "clip up, reel in, and measure how many wraps it is." Angling Times, "How to find the best spots to fish on for carp" and Carpology's "Digging in the dirt" cover why silt vs gravel changes bait presentation. Angling Times, "How and when to use a line clip": feeder fishing "is all about accuracy... casting to the same spot every time to build the swim."

---

## Поклёвка: как её прочитать, не нарушая тишины

*19 идей.*

### Force a fork: a specimen can be sold OR mounted/donated, never both — and make the best fish the one both paths want  `S`

**Как работает.** Both games make the collection compete with the wallet for the same object, which is what stops the collection from being a passive side effect of grinding. ACNH also encodes fish size in the display footprint (1x1 / 2x1 / 2x2 tanks), so a big fish physically demands more of your museum. Port: your prime-grade specimens are currently both the villager's purchase target and the mount-worthy ones, but nothing stops a player from farming until they have one of each. Make the item single-use: sold, filleted, mounted, or aquarium-stocked — pick one, and the choice is irreversible. Then size the display footprint to the fish (a 20 kg catfish needs a bigger mount or a multi-block aquarium than a bleak), which reuses the size data you already store. The result: a great fish creates a genuine dilemma, and a display case becomes a record of what you gave up.

**Почему подходит.** You already have every piece — aquarium, mini-aquarium, trophy stand, fillets, a buying villager — but they draw from a shared pool with no scarcity, so the collection has no cost and therefore no weight. This is a rules change, not a feature: near-zero code, and it makes the whole existing back half of the mod mean something.

**Источник.** Animal Crossing (Nookipedia: fish go to Blathers for the museum or to Nook's for bells — the same individual cannot do both, and the museum tank sizes are 1x1 / 2x1 / 2x2 by fish size) + Fishing Break (Touch Arcade and GameGrin reviews: the loop is catch → sell fish OR donate to the museum → buy better gear, with a fish-collection tracker and rare elemental variants as the completion goal).

### Surface signs that tell you what is feeding there before you cast  `S`

**Как работает.** The bite engine already knows what's available in a chunk; render that as species-family signs on the water with vanilla particles, anchored to a position for 30-60 s and biasing what actually bites there: a slow ring of tiny bubbles = bottom feeders rooting in silt (bream/carp/tench), scattered dimples and small rings = surface silvers, one hard slash plus a burst of scattering bubbles = a predator smashing fry, glassy oily calm = nothing home. No text, no icons, no chat message. Optionally add a 'focus' key that narrows FOV to watch the float or a distant sign, straight from COTW 1.1.3's 'zoom in on the float during fishing'.

**Почему подходит.** It makes the right first move on arriving at a lake be 'stand still and watch the water' — which is the real skill and free of assets since it's all vanilla particles. It also makes the pond stocking and per-chunk depletion systems visible from the bank instead of inferred from failure.

**Источник.** Fishing Sim World: Pro Tour — Dovetail define 'Watercraft' as 'the skill of locating fish through observation of splashing, surface activity, and environmental factors', and their carp tips tell you to look for 'either the fish crashing out of the water or bubbles coming up to the surface'. COTW: The Angler leans the same way with its float-focus zoom and calmer water so surface behaviour is readable.

### The splash spooks fish — probabilistically, scaled by rig weight and depth  `S`

**Как работает.** On landing, roll per shoal/spot inside a radius derived from the rig's weight in grams (already the mod's central number) divided by water depth: a 3 g float on 3 m barely registers, a 120 g feeder dropped on a 1.5 m marginal shelf empties it. Spooked = that spot's bite chance is suppressed for 20-60 s, decaying back. Per-species timidity multiplier in the fish JSON. Take COTW's own correction as the design brief: probabilistic, small radius, and completely silent — no message, just no bites, and the observant player notices the bubbles stopped.

**Почему подходит.** It gives the existing gram-based weight system a second consequence beyond cast distance and blank test, punishes brute-force heavy rigs in shallow margins, and creates the reason to fish light — currently there's no downside to over-gunning.

**Источник.** Call of the Wild: The Angler patch 1.1.3 (developer notes) — they changed how fish react when bait, float or lure hits the water, 'reduced the radius of effect' and made spooking 'probability rather than certainty' because the original 'spooking behavior was found to be too aggressive and binary'. COTW also tags species with an 'Easily Spooked' trait.

### Bait that dies, spoils and changes state — maggots pupate, worms die, livebait suffocates  `S`

**Как работает.** Freshness timer in bait NBT, ticking faster in heat and daylight, slower in a cold bucket or an ice-packed bait box (craftable from ice — fits the mod's existing winter kit). Maggots pupate on a timer: fresh maggot → caster (a genuinely different bait with its own species profile, which is real coarse-fishing progression, not a downgrade) → useless fly. Worms in a dry pot weaken badly; a dirt-filled pot preserves them. The already-implemented livebait fish suffocates out of water on a timer, and a dead one only interests scavengers — catfish, eels, burbot. Caught fish can carry the same freshness value so the villager pays less for a fish you hauled around all day, and cooked fillet quality follows.

**Почему подходит.** Bait is currently a static consumable with no clock, so the bait farms and bait trap are just resource taps. A perishable stock changes the rhythm of a session (bait now, fish now) and gives the ice/winter kit a summer purpose — for two sprites, one long and an item swap on a timer.

**Источник.** Russian Fishing 4 tracks a freshness value on live bait and on your caught fish, drives its cafe-order payouts off it, and the devs have stated there is no refrigerator in the game yet (RF4 'Freshness livebait' and 'Cafe orders and fishes freshness' Steam threads, via search extract). Fishing: Barents Sea makes buying bait at port part of planning a trip.

### Bank disturbance: sprinting, wading, splashing a heavy lead or throwing your shadow across the shallows kills the swim for minutes  `S`

**Как работает.** One per-spot `disturbance` value, 0..1, decaying over a couple of minutes. Sources, all vanilla state: sprinting or jumping within N blocks (large), standing IN the water or wading (large, and it drops local clarity), a terminal weight splashing down with the magnitude scaled by the rig weight in grams the bench already tracks, several rapid casts to the same spot, breaking or placing blocks nearby, boats, and the player's shadow falling on shallow water at a low sun angle (computable from sun angle, player position and water depth). Sneaking cancels the footstep source entirely. Disturbance multiplies bite rate down AND biases the size roll smaller, because big fish spook first. Two rewards for good behaviour: a real bonus on the FIRST cast into an undisturbed spot ("first cast is the best cast"), and — the important one — the surface signs only render while disturbance is near zero, so spooking a swim literally blinds you to where the fish are. Follow The Angler's lesson and make every source probabilistic with a modest radius rather than a hard binary kill.

**Почему подходит.** It is the best realism-per-line-of-code in the whole list: sprint flag, sneak flag, sun angle, water depth, rig weight — all already available. And it retro-justifies the mod's entire silent-bite philosophy: the reason you sit still and stay back stops being an aesthetic choice and becomes a mechanical one.

**Источник.** Real mechanism: Orvis UK, "The Art of Stealth: Approaching Fish Without Being Detected" — "vibrations from heavy footsteps travel through the ground and into the water", be mindful of shadow and silhouette because fish evolved to fear predators from above, and study the water before the first cast; FishTalk, "10 Tips for Stealthy Fishing"; BassResource, "Watch Your Step When Bank Fishing". Game precedent with a documented tuning: Call of the Wild: The Angler patch notes 1.1.3 — "We've updated how fish react when the bait, float, or lure hits the water. The radius of effect has been reduced and there's now a probability rather than a certainty of fish getting spooked", after the first version proved "too aggressive and binary."

### Visible surface signs that tell you where the fish actually are right now  `S`

**Как работает.** Both mods spawn a marked, castable target in the water: Little Joys' spot guarantees a catch plus treasure, Lili's pools/rubble hold their own loot tables. Both are the most immediately readable thing about those mods. River Fishing: instead of a loot pinata, use signs as the visual channel for state the mod already tracks. Particle-only tells with short windows — rising rings over a feeding frenzy, a bait-fish flurry where a predator is holding, a slick or bubble trail over a deep hole, weed movement over a snag field. Cast within N blocks during the window to collect the frenzy/predator bonus that currently fires invisibly.

**Почему подходит.** River Fishing already has feeding frenzies and per-species depth/zone preferences that the player has no way to notice, so the good moments read as random. Signs turn the existing bite engine into something you watch the water for — which is exactly the fantasy — and they cost nothing but particles, which the mod already emits.

**Источник.** Little Joys — "Fishing Spots guaranteeing a catch with treasure" spawn randomly near shorelines (api.modrinth.com/v2/project/v0j2ftEp); Lili's Lucky Lures — "floating debris, fish pools, and rubble found across water bodies… cast your line into these special areas" (api.modrinth.com/v2/project/DMDVFZSF)

### A sounding lead / marker float that reads the spot and reports in graded angler language on the action bar  `M`

**Как работает.** New item, one 16x16 sprite. Right-click a water surface: scan the column and a 9x9 around it, then print two action-bar lines. Line 1 is hard fact - "Depth 4 blocks, gravel bottom, weed at 2 blocks". Line 2 is the graded read of the chunk's own hidden state (pressure/depletion, frenzy, resident species density) on TFC's exact six-step ladder reworded for angling: "nothing showing / the odd fish / a few fish / a good head of fish / a shoal / stacked up". Copy TFC's honesty contract literally: never report fish that are not there, but allow false negatives at low angler level, shrinking with level or a perk. Charge the tool a use (it re-casts, takes 2 s) so plumbing a spot is a decision, not a scan-spam.

**Почему подходит.** Plumbing the depth with a lead before you fish is a real technique, so the teaching tool is diegetic by construction - it is the same object a real angler uses to learn the same facts. TFC proves the pattern survives in Minecraft: the action bar carries graded prose fine, and the false-negative rule is what makes players sample several spots instead of trusting one reading, which is exactly the behaviour a stocking/pressure sim wants.

**Источник.** TerraFirmaCraft's prospector's pick, which scans a 25x25x25 volume and reports to the action bar on a six-step ladder - "nothing (may be false) / traces / a small sample / a medium sample / a large sample / a very large sample" - and which is documented as never giving a false positive, only false negatives that better tools remove (TerraFirmaCraft Field Guide, Advanced Mechanics > Prospecting, fetched).

### Moodles for the water: a small column of severity icons showing the SPOT's hidden state, not the player's  `M`

**Как работает.** While a session is live, draw up to five 16x16 icons in a column at the screen edge: PRESSURE (chunk depletion), FRENZY, OFF-SEASON FOR WHAT LIVES HERE, WEATHER, RIG-DEPTH MISMATCH. Get three severity tiers for free by drawing the same sprite with three colour multipliers - no new art. Minecraft cannot hover a HUD element, so substitute Zomboid's hover with a held key (sneak, or the same key as the ledger) that draws the label and one-line meaning next to each icon. Icons appear only when the state is actually off-neutral, so an empty column means "the water is not the problem, your tackle is" - itself a diagnosis.

**Почему подходит.** Your bite engine has a dozen world inputs and the player currently experiences all of them as "nothing is biting". Zomboid's design point is that a silent simulation becomes legible through a handful of ambient icons that cost nothing to glance at, and the severity tiers teach players that these are dials, not switches.

**Источник.** Project Zomboid's moodles - circular icons top-right, each with several severity levels shown by colour/icon change, hover for a text description; the community treats them as the game's whole diagnostic dashboard (PZwiki "Moodle"; Project Zomboid Wiki "Moodles"; the fan guide "Moodle Madness: Surviving Project Zomboid's Status Secrets").

### A dated session diary, plus an Outer-Wilds-style "there is more to learn here" mark on species and spots whose hidden factors you have never observed  `M`

**Как работает.** When a session ends (rod stowed, or the player sleeps), append a page to the journal: date, coordinates, water type, casts, taps versus takes, bait changes, and every loss cause from the autopsy line. Then the Outer Wilds move: when the bite engine applies a factor the player has never yet had a positive result from for that species or that spot, stamp the entry with a small glyph meaning "there is more to learn here". Clear the glyph on the first landing that exercised that factor. No text needed for the glyph - Outer Wilds proves the mark alone drives the behaviour.

**Почему подходит.** It converts the invisible simulation into a personal to-do list rather than a wiki lookup, which is the single biggest reason players of Russian Fishing 4 fish with a spreadsheet and a third-party heat map open instead of the game's own screens. It also gives your session-scale mechanics - pressure, depletion, frenzies, stocking - a written record, so the player can finally perceive changes that unfold slower than one visit.

**Источник.** Outer Wilds' Ship Log and its Rumor Mode, where entries link to each other and the layout differs per player depending on discovery order, and where an entry can carry the "There's more to explore here" mark that tells you a place still holds something you have not seen (Outer Wilds Wiki, "Computer"; New Horizons modding docs on Ship Log; the Archaeologist Achievement Helper mod, which surfaces the more-to-explore mark in the log).

### Surface signs that are honest: dimpling rings, bubble trails, scattering fry and a circling bird, each mapped to a real species group and depth at that spot  `M`

**Как работает.** The bite engine already knows, per chunk, which species are active and whether a frenzy is on. Emit a particle tell keyed to the active group: fine rings on the surface = small surface feeders; a slow rising bubble column = bream/carp grubbing the bottom; a burst of tiny particles skittering = fry under a predator; a mob-free 'bird' can be a single item-sprite particle circling. Each tell carries the depth band it implies. Casting within a few blocks of a live tell, at the implied depth, applies a real multiplier. Tells decay after ~60s.

**Почему подходит.** The mod's bites are deliberately silent and its feeding frenzies are invisible — so a dead spot and a hot spot look identical, which is precisely the RF4 complaint about dormant water. This is all vanilla particle spawns (no models, no sounds), it makes the existing frenzy system pay off, and it rewards looking at the water rather than at the HUD, which is the aesthetic the mod is already chasing.

**Источник.** Fishing Planet 'Random fish jumping and fish splashes' (steamcommunity.com/app/380600/discussions/0/1334600128975700035/). The player: "I am always tempted to cast where I see a random fish jump (like real life) but I don't know if it really means anything." When told the devs had added "Visual hints added to water surface - hatchlings, air bubbles, etc", the reply was "Means they actual are hints of where to cast??? Fabulous". Fishing Sim World is praised for "nice random events (fish jumping, surface feeding ripples, etc.)" in steamcommunity.com/app/834280/discussions/0/1732089092455670622/.

### Water clarity as a first-class, visible state that swings after rain — muddy water forgives thick line and changes which species feed  `M`

**Как работает.** Per-water-body clarity value, 0-1, driven by recent rainfall and river width; decays back over a few in-game days. It feeds three things at once: the existing line visibility coefficient is scaled down when murky (so 0.30 braid stops scaring fish), sight-feeding predators lose bite chance while scent/vibration feeders gain, and the water gets a subtle fog/tint tweak plus more suspended particles so the player can *see* it. Post-rain becomes a window you learn to exploit rather than weather noise.

**Почему подходит.** The mod already has a line visibility coefficient fish actually notice and already reads weather — clarity is the one missing term that makes both legible. It is a float per water body plus a colour multiplier; no new assets. It turns 'it rained' from an invisible modifier into a reason to go fishing right now with different tackle.

**Источник.** Fishing Sim World: Pro Tour critique thread (steamcommunity.com/app/834280/discussions/0/1732089092455670622/), where the missing-features list explicitly includes "No water clarity variation", "No wind or wind ripples", "No in-game clock", and "Repetitive surface effects lack authentic water physics." The Angler's criticism compilation (app/1408610/discussions/0/3422194223912378760/) asks for "realistic current/stream flow affecting bait and lures" and notes "No visible stream/flow representation."

### Put the fish's tell on the water surface before the cast: family-coded ripple sprites sized by the fish's size class, so a glance at a pool tells you what is in it  `M`

**Как работает.** ACNH never tells you what is in the river. It draws a shadow whose area narrows the species set to a handful, and whose tail-flick frequency separates the ambiguous pairs (sizes 3/4 and 5/6 are hard to tell apart by area alone, easy by wiggle rate). The player learns the whole fauna by looking at water. Port: for each loaded water chunk, spawn flat surface-sign sprites at the positions the bite engine already considers valid — one sprite per family archetype (carp = a rising bubble trail, bream = fine dimples, chub/asp = a hard swirl, pike = a bow-wave wedge, catfish = a slow boil). Scale the sprite to the resident fish's size class and set its animation period inversely to size (small = fast flicker, big = slow heave). These are real angler tells, they are flat quads or particles so no 3D model is needed, and they cost one 16x16 sprite per archetype. Cast within N blocks of a sign and that species' weight in the bite roll multiplies; the sign consumes and moves after a take, which also makes your existing per-chunk depletion legible.

**Почему подходит.** This is the direct answer to 'the mod explains nothing and the bites are silent'. It moves the information you already compute (depth preference, residency, stocking, depletion) out of the hidden engine and into the world, where a player learns it by watching instead of by reading a wiki. It is also the single most realism-flattering feature on this list — reading water IS the skill in real angling, and no Minecraft fishing mod does it.

**Источник.** Animal Crossing: New Horizons — fish shadow sizes 1-6 plus finned variants; read from Nookipedia's 'List of fish by shadow size' and Game8's ACNH fishing guide, which notes smaller shadows swerve their tails at a much higher frequency than larger ones, so size classes are distinguished by MOTION rate, not just area.

### The fisherman teaches one hidden rule per species, in exchange for being shown one — turn the NPC into the tooltip you refuse to write  `M`

**Как работает.** Blathers is the reason people know anything about ACNH's fauna: the facts are attached to an act, delivered one at a time, and coloured by a character, so they land. Sneaky Sasquatch goes further and makes the reference book itself a reward from the NPC, filled in by the act of showing him fish. Port: showing the fisherman villager a species for the first time — a distinct interaction from selling it, so it survives your prime-only buying rule — unlocks exactly ONE line of the mod's hidden ruleset for that species, in his voice: 'Bream tap four times before they commit. Wait for the fourth.' 'Asp won't look at a lure thicker than 0.2 — mono spooks them.' 'Catfish come up onto the shallows after rain.' Each unlocked line lands permanently in that species' journal page. You are drip-feeding the wiki you already wrote in three languages, gated behind play.

**Почему подходит.** You have a 3-language wiki that most players will never open and a journal that deliberately withholds hints. This is the middle path: the hints exist in-game, but they are earned per species and delivered by a character, so the mod keeps its mystery while stopping being opaque. It needs no new UI, no new block and no new art — a boolean per species plus one lang string each, which is a task your lang tooling already handles.

**Источник.** Animal Crossing — Blathers, per Nookipedia and the Animal Crossing Wiki: donating a creature triggers a curator monologue of jokes and facts, so the museum's information arrives as earned dialogue rather than a manual. Also Sneaky Sasquatch (Sneaky Sasquatch Wiki 'Fishing Guide'): the guide book is handed to you by the freshwater Fisher and fills in as you turn fish in to him, so the reference and the turn-in loop are the same object.

### Let weather and time attack the READABILITY of your tells rather than the odds — and make flowing water a visible, findable hotspot  `M`

**Как работает.** Twilight Princess makes weather and time change what you can PERCEIVE, not just what is available, so a rainy evening is a different skill problem rather than a different loot table. And it gives you one dead-simple, findable hotspot rule — oxygen near moving water — that a player discovers by walking around rather than by reading. Port, two halves. First: rain, dusk and murky water bodies dim or hide the surface signs from idea 1 and shorten the nibble tell from idea 2, so bad conditions demand the sonar, a rod pod, or actual patience — and polarised glasses become a genuinely useful item that only helps readability. Second: detect flowing water and waterfall-adjacent blocks (free in Minecraft) and treat them as an oxygenation bonus in the bite engine, with visibly denser bubble particles so the hotspot advertises itself. Your feeding frenzies already prove you can flag hotspots; this makes one of them permanent and geographic.

**Почему подходит.** Your bite engine already consumes weather and time, but only as invisible multipliers, which means bad weather currently feels like nothing. Routing them through perception instead makes them felt without making them punishing, and it gives you a reason to sell an information item. The waterfall rule is nearly free, it is real limnology, and it is the kind of thing a player figures out on their own and then tells someone about — which is exactly the teaching channel a mod with no tutorial needs.

**Источник.** Twilight Princess — per the Zelda's Palace fishing guide: at the Fishing Hole the water 'becomes more and more cloudy' as evening approaches and rain makes seeing fish 'almost impossible', while fish are drawn to oxygenated water so you should fish near waterfalls. The season also rerolls every time Link re-enters the area, changing which fish are present.

### Split the bite into taps and then the take — striking on the taps loses the bait  `M`

**Как работает.** Two phases per encounter. PRE-BITE: n taps, each a 1-2 px float dip or a single quiver-tip twitch, silent, spaced irregularly. TAKE: the float buries or slides away, or the tip pulls round. Striking during a tap gives at best ~15% hook-up, strips the bait off the hook, and pushes that fish out of the spot for 30-60 s. Tap count and boldness come from the species profile (a cautious bream taps five times, a chub takes first time), from bait size vs mouth size, and from how hard the fish is feeding. Add the drop-back bite too: the tip springs back or the float rises and lies flat because the fish picked the lead up and moved toward you — the signal that looks like nothing happening and is the most-missed real bite.

**Почему подходит.** This is the layer directly beneath the mod's existing silent-bite design and float timing minigame, and it's the one thing every deep sim has that a Minecraft fishing mod never does. It needs no assets — the float already animates and the tip already bends — and it converts patience from waiting into reading.

**Источник.** Russian Fishing 4 — 'Fish plays with the bait, often for a long time, before it really takes it. Often, but not always, this is visible in the way the fish suddenly acts more aggressive, and it is then you set the hook'; the community advice is to ignore the first nibbles and 'wait until you see a line drop or the line going away'. COTW: The Angler patch 1.1.3 went the other way and added an option to disable the strike cues entirely, which tells you how central the ambiguity is.

### Shoals that migrate on their own multi-day cycle, so a dead spot comes back to life  `M`

**Как работает.** Independent of the existing player-caused depletion, give each water body 2-4 roaming shoal markers per species group. Each walks between candidate positions on a multi-day timer, weighted by depth, substrate, structure and the current season, persisted in world data. Bite chance at a cast point is scaled by distance to the nearest relevant shoal marker. Key difference from depletion: this one is not the player's fault and it always comes back, so it rewards prospecting rather than punishing effort — 'the fish have moved off the shallows' instead of 'you fished this out'.

**Почему подходит.** Chunk depletion currently only ever makes the world worse. Autonomous rotation makes the marker cast, the surface signs and the log book all pay off, and it stops the optimal strategy from being 'park on one hole forever' without adding a grind. Pure world-data bookkeeping, no entities.

**Источник.** Russian Fishing 4 — 'fish are constantly migrating, and a spot which was very active one day might be empty the next day, but could reactivate a few days later', with the standard advice being to change spots often (RF4 beginner guide and migration guides, via search extract). Fishing: North Atlantic gates it harder still: 'Every fish species can only be fished within its season' (Misc Games KB).

### Book of the Lake: every water body accumulates its own record page  `M`

**Как работает.** Both mods keep one global book about the player. River Fishing already classifies water bodies, so flip the axis: keep a record per water body. Right-click the water with the journal to open that lake or stretch of river's own page — species actually caught there and their best weights, first date fished, total catches, current stock band, stocking history, which lures have burned out, whether it has ever produced a prime or a legendary. Pages persist in world data, so an abandoned pond still remembers.

**Почему подходит.** A river sim is about places, and River Fishing's per-chunk pressure, stocking and residency data are all place-keyed already — they just have no place-shaped readout. It converts depletion and stocking from statistics into a story the player wrote, which is the thing single-player has that no leaderboard can replace. Journal NBT keyed by water body, nothing more.

**Источник.** Starcatcher — guide "signing" that stores all catches at signing time as a durable record, plus first-catch timestamps per species (Starcatcher 2.2/2.3 changelogs); Tide 2 — journal showing completion progress, personal records and habitats (modrinth.com/mod/tide)

### A castable sonar / fish finder rendered as a 2D screen — depth contour, bottom hardness, and unlabelled fish blips — which doubles as the pre-cast odds readout  `L`

**Как работает.** Cat Goes Fishing is the extreme case — total information, and it is still hard, because the challenge is presentation of the lure rather than guessing. Graveyard Keeper is the pragmatic case: it shows you the odds table for each depth band before you commit, so the cast is an informed choice and the player learns the water model by reading it. Port: a castable sonar bobber (real gear — Deeper and Garmin sell exactly this) rendered as a 2D GUI: a side-scan column drawn with GL_LINES showing the depth profile out to your cast distance, a hardness band for the bottom (silt/gravel/weed, which your water-body data already implies), and blips sized by fish size class but NOT named. Second, cheaper tier: at the Tackle Station or behind an angler-level perk, a Graveyard-Keeper-style readout of the shallow/mid/deep bands for the spot you are standing on with the actual probabilities your bite engine will use for your current rig. No new sprites — lines, bars and numbers.

**Почему подходит.** You have built a genuinely deep water model that the player can only experience as a black box. Showing it is the highest-leverage fix for 'explains nothing in-game', and doing it through a real piece of tackle keeps it diegetic instead of a wiki page pasted into a GUI. Withholding species names keeps the mystery and makes the surface-signs skill (idea 1) still worth learning. You have already shipped JEI and Jade integration, so the 2D-panel muscle exists.

**Источник.** Cat Goes Fishing (its whole design is that fish are visible with per-species vision, speed and behaviour, and rod attachments tune 'how interested the fish are in your lure' and how fast it sinks — per the God Minded Gaming review and the NamuWiki entry) + DREDGE (fishing spots are visible on the water surface as disturbance patches, with a green glow marking aberrant spots) + Graveyard Keeper (per the official wiki: before committing a cast you browse three sub-areas per location split into shallow / medium / deep by vertical lines, with a PERCENTAGE table of what you can catch there with your current rod and bait combo).

### River lies computed from block state: the crease where fast meets slow, the cushion behind a boulder or bridge pillar, and the slack that holds fish in winter and flood  `L`

**Как работает.** Score the cast's landing position from vanilla block state alone. At the bobber's water column: is the water flowing (non-zero flow vector) with still or slower water within one or two blocks laterally → CREASE/SEAM, the top-tier feeding lie. Is there a solid block immediately upstream within one or two blocks → CUSHION, a resting lie: fewer bites but larger fish, and the fight opens with the fish using the flow against you. An inside bend or dead slack → REFUGE, the winter and flood lie. A deep slow glide → the winter holding pool (Angling Times, "Where fish go when our rivers get cold in winter": roach and bream form dense shoals in the deeper, warmer glides). Give each lie type per-species multipliers in the existing fish JSON — barbel and chub on the crease, roach and bream in the slack, pike in the margin and undercut — and print the lie in the cast readout: "the float is riding the crease", "you have dropped into the slack behind the pillar."

**Почему подходит.** This is free realism: Minecraft rivers already carry flow vectors, and players already build bridge pillars, place boulders and dig channels — so the world generates lies for you and even lets a player ENGINEER one. It also finally makes river fishing mechanically distinct from pond fishing, which a 79-species river-focused sim needs, and it gives the scent-plume cone something to aim at.

**Источник.** Real mechanism: Wired2Fish, "How To Read A River" — downstream of structure you get "a current shadow, a pocket of calmer water immediately behind the structure, followed by two converging seams", and "that downstream pocket is classic holding water" where a fish "can sit there burning almost no energy while food gets funneled toward them from both sides"; My Quest For Barbel, "Watercraft – Barbel" — a boulder creates pockets and a cushion out of the main flow; Life and Work, "Mastering Current Seams" — "a visible crease or seam on the surface marks where fast and slow water meet, one of the most reliable indicators of a feeding lie."

---

## Коллекция, рекорды, морфы

*19 идей.*

### Make lures modify WHICH size roll you keep, not what bites — 'big bait, big fish' as a literal roll-selection rule  `S`

**Как работает.** WEBFISHING gets enormous perceived depth from one line of code: roll the outcome N times and let the equipped item pick which roll you keep. It is instantly legible to players ('this one gets big ones'), it never distorts the species table, and it scales to any number of items by varying the selection rule and N. Port: your size distributions and lure-size filters already exist. Add a `roll_select` field to lures and baits — large deadbait or a big spoon rolls weight twice and keeps the higher; a size-20 hook with a single maggot rolls twice and keeps the lower (which is correct — fine tackle catches numbers, not monsters); a scent-heavy or flashy lure rolls the morph/rarity check twice and keeps the better. Three rules times your existing lure set gives a real tackle-selection game.

**Почему подходит.** Your tackle system is the deepest part of the mod but most of its parameters modify bite PROBABILITY, which players cannot perceive over a session — you cannot feel a 12% modifier. Roll-selection is perceptible immediately, it encodes an angling truism accurately, and it is the laziest high-impact change on this list: one integer, one comparison, and the pending 'do advanced tackle params do anything' question gets a concrete answer.

**Источник.** WEBFISHING — read the WEBFISHING Wiki 'Fishing' page directly: the Fly Hook 'chooses the roll with the smallest size', the Large Lure 'chooses the roll with the largest size', and the Sparkling Lure 'chooses the roll with the highest tier'. Bait separately sets the quality distribution (plain worms cap at Normal, the Gilded Worm gives up to a 12% chance of the top Alpha tier). Size is otherwise normally distributed around each species' average.

### Slams: catch three species of one fish family in a single in-game day for a Grand Slam, four for a Super, five for a Fantasy, and all of them across your lifetime for a Royal Slam  `S`

**Как работает.** Tag every species with one or more `slam_family` values in its JSON (carp/cyprinid, predator, salmonid, sea, sturgeon, catfish, coastal, pelagic…). Keep a rolling per-day catch set in player NBT, cleared at dawn. On each landing, intersect the day's set with each family list; 3/4/5 fire Grand/Super/Fantasy and mint a certificate item naming the family, the date, the waters and the weights. A lifetime accumulator per family fires the Royal Slam when every member has ever been landed. Optional hard mode mirroring the fly rule: a slam filed with everything caught on one rod class, or on ≤ a given line class, is stamped differently.

**Почему подходит.** It converts 79 already-shipped species into hundreds of session-scale goals with nothing but tags and a day-scoped set. It also fixes the classic sim problem where the player parks on one productive spot: a slam forces you to move between water body types, depths and baits inside one day, which is exactly what the existing bite engine is built to reward. Certificates are written-book/paper items — no art needed.

**Источник.** IGFA Grand Slam Clubs and Royal Slam Clubs (igfa.org/grand-slam-clubs, igfa.org/royal-slam-clubs): three species in a calendar day = Grand Slam, four = Super Grand Slam, five = Fantasy Slam, all listed species over a lifetime = Royal Slam; eight family lists (Bass, Billfish, Inshore, Offshore, Salmon, Shark, Trout, Tuna); fly-tackle slams additionally require tippet ≤20 lb. Members get a hand-signed embossed certificate and a permanent listing. Regional analogue: the Wyoming Cutt-Slam and Utah Cutthroat Slam (wgfd.wyo.gov, utahcutthroatslam.org) — four cutthroat subspecies each caught in its own native range, rewarded with a certificate, decal and medallion.

### The village fisherman keeps his own personal best per species and brags about it; you must beat it by a real margin, and each time you do he raises his bar — a record ladder that scales with you and never ends  `S`

**Как работает.** Give the fisherman villager a per-species `his_best` value in his NBT, seeded at ~50–60% of species max, with a couple of species he is genuinely proud of (seeded high). Talking to him quotes one at random, with the water he claims he caught it in. Show him a heavier specimen — beating him by the IGFA-style margin, not by a gram — and he pays out, unlocks a trade, or hands a stamp; then his bar re-rolls upward to just under what you brought. An exact tie gets a grudging tie dialogue and no reward. Because the ladder is per villager, a second village is a second ladder.

**Почему подходит.** It supplies what a fixed 'prime = 70%' threshold cannot: a moving target that keeps producing a next goal in an endgame world, from one integer per species in an NPC that already exists as your fish buyer. It also gives the villager a personality beyond a trade GUI, and it is the classic single-player way to have a rival without another player.

**Источник.** The Legend of Zelda: Ocarina of Time fishing hole (zelda.fandom.com/wiki/Golden_Scale, zeldadungeon.net): the pond owner holds a standing record — default 6 lb for young Link, 7 lb for adult — and Link must beat it by at least 4 lb (≈11–13 lb) to be handed the Golden Scale. IGFA's margin rule for the same feel (igfa.org/world-record-requirements): a claim must beat the standing record by 2 oz under 11.33 kg, or 0.5% above it, and an exact match is filed as a tie.

### Smallest-specimen records: each species needs a 'mini' as well as a 'giant', which finally makes #20 hooks, ultralight blanks and hair-thin line worth owning at endgame  `S`

**Как работает.** Two extra record slots per species keyed off the existing weight roll: a 'mini' slot for a specimen in the bottom few percent of the species range, and the existing max as the 'giant'. Gate the mini slot behind plausibility so it cannot be farmed accidentally — it counts only on a hook size and line class light enough to be credible for that fish, which the mod already models. The journal shows a small three-notch bar per species (mini / mid / giant) with your best at each end. A full set of minis is its own award, deliberately much rarer than the trophy set, and it should never gate content — MH keeps crowns as pure completionism precisely because they are luck-heavy.

**Почему подходит.** It inverts the mod's entire incentive gradient for free and gives the ultralight/stick/pole end of the 13 blanks and the #20 hooks a permanent endgame purpose, instead of being starter gear you abandon. It is also a size-class collection that needs no new species, no new art, and only a couple of NBT fields.

**Источник.** Monster Hunter crowns (monsterhunterworld.wiki.fextralife.com/Crowns; monsterhunter.fandom.com/wiki/Size): three crowns per monster — gold miniature for the smallest possible size, silver for next-to-largest, gold giant for the largest; only gold crowns count towards the Crown Collector achievements, and crowns are 'within a percentage of the min/max size' rather than fixed multipliers. Pokémon Scarlet/Violet Mini and Jumbo marks awarded at scale value 0 and 255 (bulbapedia.bulbagarden.net/wiki/Mark). Real-world analogue: the rough/micro species lifelist culture on roughfish.com, where the target is odd and tiny species (redhorse, suckers, micro species) rather than big ones.

### Rods that keep their own logbook, break in, and earn a name  `S`

**Как работает.** Treasure Seas counts catches on the rod and spends them on an enchantment-level upgrade at an anvil — progression, but the rod has no identity. River Fishing: each assembled rod's NBT accumulates catch count, heaviest fish and species, number of line breaks survived, and first-use date. At thresholds it becomes 'broken in': tiny bonuses (a few percent cast range, slower wear tick, one extra rod-bend step of forgiveness). Let the player name it once at the tackle bench; the name is then stamped into every journal record that rod produces, and shown on the rod pod.

**Почему подходит.** Every mechanism needed is already there — per-rod NBT, wear, the journal, the tackle bench. It is the cheapest possible single-player attachment loop, and it makes the 13 blanks feel like owned objects rather than tiers.

**Источник.** Treasure Seas — rods "automatically restore durability and track catches", upgrading at thresholds of 25 catches for level II up to 335 for level V (modrinth.com/mod/treasure-seas); Starcatcher — guide records first-catch timestamps and personal bests for speed, size and weight (Starcatcher 2.2/2.3 changelog via api.modrinth.com/v2/project/starcatcher/version)

### Four numbered reveal stages per species, tied to catch count, ending in an in-character technique note  `M`

**Как работает.** Give each species a study count (3 for rare, 10 for common) and copy Terraria's thresholds exactly. Stage 1 (first catch): name, icon, water body. Stage 2 (20%): weight/size range and season band. Stage 3 (50%): bait and rig list. Stage 4 (100%): the hard numbers - peak hours, depth band, strike window, fight pattern - plus one line of prose in the fisherman's voice that states the technique: "Takes the bait and runs. Let it. Strike when the line straightens." Render locked rows as "???" rather than hiding them, so the player can see the shape of what they do not yet know.

**Почему подходит.** You already gate the journal reveal; the upgrade is the numbered ladder and the technique note. Terraria's thresholds turn repeat-catching a species from grinding into a legible research loop, and Hollow Knight's trick - the last unlock is advice, not data - means the journal finally teaches HOW rather than only WHAT, which is precisely the gap that produced your bug reports.

**Источник.** Terraria's Bestiary, whose four stages are pinned to the enemy's banner kill count - 1 kill gives name, portrait, biome; 20% gives stats and coin drops; 50% gives the drop list; 100% gives exact drop rates and amounts (Terraria Wiki, "Bestiary", fetched). Hollow Knight's Hunter's Journal, where hitting the required kill count upgrades an entry from "encountered" to "discovered" and adds the Hunter's own combat advice in his voice (Hollow Knight Wiki, "Hunter's Journal"). Monster Hunter, where weakness stars are withheld until you accumulate research on that monster (Game8, "Monster Parts and Weaknesses").

### A personal catch log — one row per fish with weight, date, weather, clarity, bait, depth and spot — sortable, so the player derives the patterns themselves  `M`

**Как работает.** A bound-book item. Every landed fish appends a row: species, weight, length, date/season, time, weather, clarity, bait, rig, depth, and the nearest buoy or coordinates. The book UI sorts and filters by any column. That's it — no analysis, no hints, no 'best bait' summary. The player scrolls it and notices that every bream over 2 kg came at dawn after rain. It reuses the 256px journal icons for the row thumbnails.

**Почему подходит.** The mod's journal is a bestiary — species facts, shared by all players. This is the opposite artifact: a private, chronological record of one save's history, which is what the guide-dependency complaint is really asking for (give me the data, not the answer). It's an NBT list and a scrollable list widget over icons that already exist, and it makes the mod's dozen hidden environmental terms discoverable by observation instead of wiki.

**Источник.** Carp Fishing Simulator suggestions (steamcommunity.com/app/366290/discussions/0/1474222595307851711/), player Claes: "Carp fishers do take trophies! they are called photographs, and a nice interactive in game picture book and a better take picture option might be fun", and separately asks for "more biological info into the game for people to read through about the fish, stuff like species details, sizes, prefered baits per season." The Angler's catch-and-release thread (app/1408610/discussions/0/3415433633270143856/) asks for "A logbook system tracking sizes, weights, maybe even a animated picture."

### Palette-swap variants: give every species 1-3 real-world morphs and anomalies that occupy their own journal slots, so 79 species becomes ~200 collection entries with no new art  `M`

**Как работает.** DREDGE doubled its bestiary with recolours plus a distortion pass, gave the variants their own encyclopedia rows, hung a Pursuit off the first one, and marked the moment with a pitch-bent version of the normal catch sound. The variants are also the game's main foreshadowing device — content and narrative from a palette swap. Port, realism-safe: xanthic/golden tench and roach, albino catfish, leucistic bream, a fully scaled vs Hungarian-scaled vs koi-patterned common carp, natural hybrids (roach×bream, ide×chub), lamprey-scarred pike, a hooked-jaw old male, a stunted or hunchbacked specimen from an over-stocked pond. Each is a hue-shifted or masked copy of the 256px icon you already have — exactly the pipeline you built for lure dyeing. Gate the odds on state you already track: over-pressured chunks throw stunted fish, long-settled stocked ponds throw morphs, old individuals in a low-pressure chunk throw hook-jaws. For the DREDGE audio trick with no recording, pitch-shift an existing vanilla sound.

**Почему подходит.** Your asset ceiling is exactly what DREDGE and Fishing Vacation exploited. Morphs are the highest content-per-hour item available to you, they deepen the journal without new species research, and unlike a fantasy 'shiny' they are documented real phenomena, so they reinforce the realism claim rather than diluting it. They also give your pressure and stocking simulations something the player can SEE, which is currently invisible.

**Источник.** DREDGE Aberrations — read the DREDGE Wiki 'Aberrations' page and gamepressure's aberration guide: every regular fish has at least one aberrant variant (some have 2-3), they are worth significantly more, they are found in disturbed water with a green glow that gives a +35% aberration bonus, and the catch jingle plays 'corrupted' when you land one so the reward has its own audio signature. Also Fishing Vacation, whose Steam page lists '10+ color palettes' as a headline feature — palette work as shippable content.

### An automatic catch log that turns into your own statistics — the single-player answer to a leaderboard  `M`

**Как работает.** Log every catch to a per-world journal page: water body, distance, depth set, substrate, bait, rig, in-game hour, season, weather trend, weight. Then let the journal aggregate your own history — 'bream on this pond: best 04:00-06:00, 12 of 19 fish, 2.30 m over gravel' — and track per-water-body personal bests, best hour, best single-session bag. Never state a rule the player hasn't earned; only reflect their own logged data back at them. Optional self-set session goals (beat your own best bag for this pond in one day) give you competition with no second player anywhere near it.

**Почему подходит.** River Fishing has a deep data-driven bite engine whose knowledge currently lives in a wiki instead of in play. This makes the engine learnable through fishing, gives the 256px journal something new to hold, and delivers the 'tournament against yourself' brief without a single leaderboard.

**Источник.** RF4-STAT (en.rf4-stat.ru), the community stats site the whole RF4 playerbase runs on: it records per catch the waterbody, spot coordinates, depth set, bait, species, weight, fishing style and time window, precisely because the game itself never tells you. Sims like Fishing Planet and COTW instead hand you the answers in a tooltip.

### A line-class record grid: every species × line-strength band holds its own record slot, so a 2 kg bream on 0.08 mm line is a new record even after you've landed a 4 kg one on braid  `M`

**Как работает.** Bucket the mod's existing line strengths into ~6 classes (e.g. ≤1.5 / 3 / 5 / 8 / 12 / 20+ kg). On every landing, write {weight, date, water, rig} into journal NBT keyed by species+class, replacing only if heavier. The journal species page becomes a small grid: rows = classes, cells = your best, empty cells greyed. Enforce IGFA's ½:1 sanity rule so a 200 g roach cannot claim the 20 kg class (cell only fills if weight ≥ half the class rating). Score the book with class weighting — light classes worth more — and use the total as the input to one prestige rank, not to XP.

**Почему подходит.** The mod already models line diameter, strength and a visibility coefficient that fish notice, so light line is already a real risk/reward decision — this turns that decision into permanent collectible state. It makes the whole existing line inventory relevant forever instead of 'upgrade to braid and never look back', and it produces goals with no new species, no new art and no other players.

**Источник.** IGFA line-class and tippet-class world records (igfa.org/world-record-requirements, igfa.org/announcement/igfa-starts-freshwater-line-class-and-tippet-class-records). IGFA keeps records in All-Tackle, All-Tackle Length, Line Class, Tippet Class and Junior/Smallfry categories; freshwater tippet classes are 1/2/4/6/8/12/16/20 lb, and since 2019 the fish must weigh at least half the line class it is filed under (uniform ½:1 ratio).

### Ultra-rare colour morphs — xanthic, albino, melanistic, blue — as a second required entry per species, found via an escalating pity counter and a special bait rather than pure RNG  `M`

**Как работает.** Per-species `morphs` list in JSON, each with a palette op instead of an asset: hue-rotate / desaturate+tint / brighten applied to the existing 256px icon at render time, so no new art. Base chance ~1/300 per landing of that species, plus a stored per-species miss counter that adds up to +2% and resets on success (Dredge's pity bonus, and the single most important anti-chore mechanic I found). A craftable 'morph' groundbait doubles the roll for one session. Journal entry shows two silhouettes per species — normal and morph — and only the morph line stays greyed until landed. Morph specimens sell for a large multiple and are the only fish worth keeping in the existing aquarium.

**Почему подходит.** A completed 79-species journal instantly becomes a 158-slot journal with zero new species, zero new art and no new fight code. The escalating counter means the chase converges — a player who fishes a lot will get there, which is exactly how Dredge stops a 55%-aberration encyclopaedia from feeling like a slot machine. Palette-shifted sprites are squarely inside the project's asset limits.

**Источник.** DREDGE aberrations (dredge.wiki.gg/wiki/Aberrations): every regular fish has at least one aberrant variant (some 2–3), 127 of 230 entries are aberrations, each encyclopaedia entry carries "a small note informing you whether or not you've already caught this species' Aberrant variant"; spawn rate is stacked from green-glow spots (+35%), night (+3%), special rods (+5/+10%), an Aberrated Bait that guarantees them, and a hidden bonus that builds up to +35% while you catch normal fish and resets when an aberration is landed. Pokémon shiny odds for the tuning comparison (bulbapedia: 1/8192 → 1/4096, Shiny Charm triples, chaining down to ~1/100, sparkle + star/square animation as the tell). Real-world basis: xanthochromism (en.wikipedia.org/wiki/Xanthochromism) is documented across groupers, nine Sebastes rockfish, flatfish, cod and salmon.

### Grade specimens on condition (relative weight) as well as mass, so a short fat fish gets its own record slot — and make pond condition drop as the water gets fished out  `M`

**Как работает.** Give each species a length–weight curve (a,b in W = aL^b) alongside its existing max weight; roll length first, then weight with scatter, and derive Wr = 100·W/(a·L^b). The journal keeps two record slots per species: heaviest, and best-conditioned (highest Wr above a floor length, so 40 g fry can't win). Tie Wr to world state: a chunk's existing fishing-pressure/depletion value and stocking density shift the Wr distribution down, and a freshly stocked or long-rested pond shifts it up — so a Wr 115 carp is physical evidence of a healthy unfished water, and grinding one hole visibly ruins the fat-fish record you want. Optionally borrow Dave the Diver's other axis: a specimen fought for too long, foul-hooked, or carried around too long grades down.

**Почему подходит.** This is the one genuinely new *axis* rather than another threshold, and it is exactly the kind of realism the mod already sells: it makes the existing per-chunk depletion and stocking systems legible in the item tooltip instead of only in the bite rate. It also gives small-species fans a record they can win, and it needs nothing but arithmetic on data the mod already stores.

**Источник.** Relative weight and condition factor in fisheries science (en.wikipedia.org/wiki/Standard_weight_in_fish): Wr = actual weight / standard weight for that length × 100; Wr = 100 is the expected weight, >100 means better energy reserves and health, <100 signals poor forage or stress (juvenile rainbow trout under 80 were at high risk of dying); Fulton's condition factor K = 100(W/L³), proposed 1904. Games precedent for a second, non-size quality axis: Dave the Diver's 1–3 star fish quality determined by capture method — alive by net-gun/sleep dart = 3 stars, harpoon = 2, rifle damage = 1 (dave-the-diver.fandom.com/wiki/Fish).

### An ageing bench: keep a scale or otolith from a specimen, read its rings, and collect age classes — the oldest fish of each species is a record nobody can get by fishing carelessly  `M`

**Как работает.** Filleting or a new 'take sample' action on a specimen yields a Scale Sample / Otolith item (16x16) carrying species, weight, Wr, water id and date. Right-click it at the Tackle Station with a lens to reveal: age in years (derived from weight percentile + Wr + noise, seeded by the specimen so it is stable), a growth history as a row of GL_LINES ring spacings, and a 'natal water' line — which, for a fish you did not catch where it was born, reads as a different water body and hints that the species migrates or was stocked. Journal gains an age ladder per species with the oldest specimen recorded, and an 'ancient' band for fish near the species' longevity.

**Почему подходит.** It is a whole new collection dimension built from data the mod already has, rendered with GL_LINES and text — inside the asset limits. It also pays off the stocking system narratively: an otolith that says a fish was born in a water you stocked years ago is the strongest possible feedback for a long-running world, and it gives catch-and-keep a purpose beyond selling and eating.

**Источник.** Otolith science (en.wikipedia.org/wiki/Otolith): otoliths accrete calcium carbonate in layers like tree rings, "by counting the rings, it is possible to determine the age of the fish in years"; accretion alternates on a daily cycle in most species so age can be read in days; ring spacing tracks growth rate; and trace-element/isotope signatures "give insight to the water bodies fish have previously occupied", reconstructing migration and natal origin. Games precedent for evidence-driven codex entries: Monster Hunter's hunter's notes/field guide and crown tracking (monsterhunterworld.wiki.fextralife.com/Crowns).

### Gyotaku fish prints: ink a specimen, press paper on it, and hang a life-size print of that exact fish — with weight, water and date on the plaque — on your wall. The fish survives the process  `M`

**Как работает.** Recipe: specimen + paper + ink sac at the Tackle Station → Fish Print item, and the specimen is returned (historically accurate — the ink washes off), so printing never competes with cooking or selling. The print's sprite is the species' existing 256px icon scaled by that specimen's weight-to-max ratio, so a big fish literally makes a bigger print, plus a text plaque line (species, weight, water, date, mark/morph if any). Place it on a wall like a painting-sized block; a large fish needs a 2x2 or 2x3 wall area. Prints stack into a portfolio book for storage.

**Почему подходит.** The mod has a trophy stand and a mini-aquarium but no way to fill a wall with your whole history, and this is the cheapest possible route to it: the art already exists at 256px and 'sized to real fish size', which is exactly what gyotaku is. It also has real provenance as a *record-keeping* practice, so it sits naturally in a realism sim rather than reading as a gamey trophy case.

**Источник.** Gyotaku, the Japanese art of fish printing (en.wikipedia.org/wiki/Gyotaku; Atlas Obscura, 'How the Traditional Japanese Art of Fish Printing Inspired a Modern Art Form'; US Harbors, 'Record Your Catch Using Gyotaku'; Smithsonian Ocean, 'Educational Uses of Gyotaku'): invented in mid-1800s Japan — earliest surviving print dated February 1839, Tsuruoka City Library — specifically as a way for anglers to document and prove the size and appearance of a catch before photography; ink is brushed onto the fish, rice paper smoothed over it and peeled off to give a life-size impression, after which the ink is washed off and the fish is eaten.

### Every water body in the world keeps its own record board — biggest of each species caught in *this* pond — turning one world's dozens of waters into dozens of parallel record books  `M`

**Как работает.** The mod already detects water bodies for the bite engine (type, width, depth). Assign each a stable id and keep a per-water record map in world save data: species → {weight, Wr, date, line class}. New placeable Record Board block binds to the nearest water body when placed and renders that water's top entries, plus totals (species count seen here, biggest ever, days fished). Journal gets a 'waters' tab listing every water you have fished with its species count, so an unexplored lake shows as a blank page. Local records should use a *separate*, easier threshold from the global one, so a small farm pond is still worth a board.

**Почему подходит.** It multiplies goals purely from world geometry, and it rewards exploring your own Minecraft map rather than grinding the one chunk with the best spawn table — which also relieves pressure on the depletion system. Boards are a functional counter to 'I finished the journal': the journal is finite, a world's waters are not.

**Источник.** State record programmes as published: List of Wisconsin fishing records (en.wikipedia.org/wiki/List_of_Wisconsin_fishing_records) — species records list weight, length, date, water and county, which is where anglers' habit of chasing 'lake records' and per-venue personal bests comes from. RF4-STAT trophy-weight tables (en.rf4-stat.ru/weight/) publish per-species max weight with the date and the water it came from, updated from the game's own records feed. Fishing Planet organises its Unique/Trophy fish per species per waterbody (wiki.fishingplanet.com/Collecting; Steam guide 'Complete(almost) Uni/Trophy Guide to all different species/places').

### A species entry is only 'complete' when you have gathered every kind of evidence about it — caught it, landed a trophy, landed its morph, sampled its otolith, cooked it, printed it, recaptured a tagged one — eight checkboxes per species, not one  `M`

**Как работает.** Add a bitmask per species in the journal with ~8 evidence flags, each of which is already computable from an existing system: first catch; prime/trophy grade; colour morph; a marked specimen; a released-and-measured length record; an otolith read; a cooked fillet eaten; recaptured after tagging (or: settled in a pond you stocked). The journal species page shows the eight slots as small icons; filling all eight upgrades the page to a 'monograph' — a longer hand-written entry with the fish's real ecology, plus a modest permanent bonus for that species only (a small bite-rate or fight-read edge, since you now know the fish). Show the checklist from the start so the player can see the shape of the work, and never require all 79 for anything.

**Почему подходит.** It turns the existing journal from a checklist you finish in one long playthrough into an 8×79 lattice, and it does so by *wiring together the systems the mod already ships* rather than adding content — cooking, stocking, grading, the aquarium and the (new) tagging/otolith systems all become ways to finish a page. Per-species rewards also solve the chore problem: nothing waits on a 100% completion gate.

**Источник.** Monster Hunter's field guide / hunter's notes and crown tracking (monsterhunterworld.wiki.fextralife.com/Crowns; monsterhunter.fandom.com/wiki/Size). Sea of Thieves' Hunter's Call (seaofthieves.com/news/fishing-in-sea-of-thieves): 10 species × 5 varieties, all also available as "bumper-size Trophy fish", with commendations for the volume and variety you "catch, cook and sell" — the same fish must be landed *and* cooked to finish the set. DREDGE's per-entry note telling you whether you have caught that species' aberrant variant (dredge.wiki.gg/wiki/Aberrations). Guild Wars 2's regional fishing collections with per-region and 'Avid' tiers (wiki.guildwars2.com/wiki/Fishing).

### Scales and a measuring mat that produce a signed specimen slip — an object, not a stat  `M`

**Как работает.** Fishing Upgrades & More makes appraisal a paid NPC interaction that rolls a rarity tier — appraisal as a slot machine. Tide and Starcatcher record length/weight but only inside a book. River Fishing: a scale block plus a measuring mat. Place a fish on it and get a specimen slip item whose 256px art composites the existing hand-drawn fish icon with weight, length, date, water body name, rod name and grade. The slip is the framable/tradeable object; the fish itself can then be kept, cooked or released. An unweighed fish sells at a discount because nothing certifies it.

**Почему подходит.** River Fishing already computes prime grade at ≥70% of species max weight and already has a trophy stand and mini-aquarium with nothing meaningful to hold; this makes the grade into an artefact and gives catch-and-release its reward. It is also the concrete form of the mod's own pending 'specimen card on the catch tooltip' idea, using the 256px art pipeline that already exists.

**Источник.** Fishing Upgrades & More — shift-right-click a fisherman villager with a fish and pay 1 emerald to receive an *appraised* fish with a rolled rarity (modrinth.com/mod/fishing-upgrade, 409k downloads); Tide 2.0 tracks fish length in centimetres; Starcatcher's guide stores personal bests for size and weight (both via their Modrinth changelogs)

### Broodstock genetics on top of stocking: cross fish you release into a pond over seasons to fix a strain, and grow your own trophy bloodline  `L`

**Как работает.** Fish Tycoon's genetics are two independent 1-3 traits, deterministic crosses, and a survivability penalty on the rare combinations — that is the entire system, and it sustained a whole game plus a sequel. The player's real activity is keeping a lineage notebook. Port onto the stocking system you already have: a stocked pond's population carries two hidden 1-3 traits, say Growth (max-weight ceiling) and Vigour (fight strength / condition floor). Releasing a pristine specimen contributes its traits; the pond's traits drift toward the ones you keep releasing, over a fixed number of seasons. High-tier combinations need the pond to actually be suitable (depth, width, groundbait, water type — all things your bite engine already reads) or the strain reverts. Payoff: a pond that produces trophies you cannot find in the wild, and morph odds (idea 9) that you have deliberately bred for.

**Почему подходит.** Stocking currently ends when the species settles — there is nothing to do with a pond afterward. Genetics turns your ponds into a long single-player project with a several-season time horizon, which is exactly the pacing a Minecraft world supports. It is entirely data (two integers per pond) and it makes releasing a good fish a strategic act rather than a moral one.

**Источник.** Fish Tycoon — from the Fish Tycoon Wiki 'Breeding' and 'Game Mechanics' pages: body type and fin type each have a level 1-3 and inherit INDEPENDENTLY, crosses are deterministic (a Spotanus and a Fatfish always give a Goldshark regardless of fins), rarer level-3 combinations are more fragile and need higher environment research to survive, and the endgame is discovering 7 'magic fish' by cross-breeding.

### Tag-and-release: clip a numbered tag to a fish, release it, and recapture it weeks later to see how much it grew and how far it moved. The tag ledger is the long game  `L`

**Как работает.** Craftable numbered tags (a stack of identical items; the number is assigned on use). Apply to a live specimen and release it: instead of despawning, write a small record into the water body's saved data — {tag id, species, weight, length, tick, chunk}. That individual is now favoured to reappear from that water's roll (a modest weight in the bite engine) and keeps growing on a slow schedule while it sits there, faster in a low-pressure, well-stocked water. On recapture, the journal writes a growth entry: days at large, Δ weight, Δ Wr, and — if your ponds are connected or you stocked the fish elsewhere — distance moved. Also add release-only length records: a species' length record can only be claimed by a measured-and-released fish, never by a killed one.

**Почему подходит.** This is the strongest answer to 'why keep playing after the journal is full', and it lands on systems the mod already has: pond stocking with permanent settling, per-chunk pressure and depletion, and per-water species lists. It rewards tending one home water for in-game years rather than tourist-fishing, and it gives catch-and-release a mechanical payoff instead of being the option that gives you nothing.

**Источник.** Cooperative angler tagging programmes — NOAA Cooperative Tagging Center, Gray FishTag Research (grayfishtagresearch.org, fisheries.noaa.gov/southeast/atlantic-highly-migratory-species/cooperative-tagging-center), Georgia DNR and USM Gulf Coast Research Lab Cooperative Sport Fish Tag and Release: volunteer anglers tag and release fish with externally visible tags, and recapture reports supply date, length and location, so "by comparing lengths recorded at the time of initial tagging and again during subsequent recapture" growth is derived; recapture reporters are rewarded, including an ICCAT annual $500 lottery. IGFA's All-Tackle Length record category (launched 2011, 117 eligible species) likewise "requires the safe release of the fish to qualify".

---

## Обучение, диагностика, вскрытие ошибок

*14 идей.*

### A bite-chance ledger: every factor the bite engine used, listed as signed lines the player can read  `S`

**Как работает.** The bite engine already multiplies ~12 factors. Change its internal call to also append (Component label, float multiplier) pairs to a list, then render that list RimWorld-style: a base line, then right-aligned rows sorted by |log(value)| so the biggest lever is on top - "Bait match (worm / bream) .... x1.4", "Line 0.28 mm visible ....... x0.62", "Pressure, 40 casts here ..... x0.55", green >1, red <1, grey =1, capped at 6 rows plus "and 4 more". Lazy first version, one afternoon: no screen at all, just one action-bar line naming the single worst factor with its number when the player presses a key with a rod out. The full ledger can be the shift-tooltip of a Water Notes item or a tab in the existing journal.

**Почему подходит.** River Fishing's whole selling point is a sim nobody can see. XCOM's lesson is that a hidden multiplicative model becomes teachable the moment you print its terms - players stop filing bug reports and start optimising. It also costs almost no new content: the numbers already exist, they are just thrown away.

**Источник.** XCOM 2's hit-chance modifier list (StrategyWiki "XCOM 2/Aim Bonuses"); RimWorld's stat report, which derives a stat as (base + offsets) x factors and shows each contributing source (RimWorld Wiki, "Stat" / "Global Learning Factor"); Baldur's Gate 3, where hovering a check in the combat log expands the roll into its modifier breakdown (bg3.wiki, "Dice rolls").

### Super Guide escalation: after eight consecutive failures of the same kind, offer help once, never force it, and withdraw it the moment the player succeeds  `S`

**Как работает.** Keep a counter in player NBT keyed by (rod class, failure kind). At eight consecutive failures with no landing, print one action-bar offer: "Press [key] to see how this rod is fished", which opens the Ponder-lite scene for that class. Reset the counter on the first success. Fire at most once per class per world, and mark it in the journal so the player can re-open it deliberately later. Optionally follow Nintendo's dignity rule - if the player accepts help, the next catch is flagged in the diary as "assisted", nothing punitive, just honest bookkeeping.

**Почему подходит.** It targets help precisely at the players who are stuck, and only them, which is the opposite of the tutorial-wall approach the mod correctly refuses. The eight-failure threshold is also a free telemetry-free diagnostic for you: if you log how often it triggers per class, you learn which flow is unteachable without reading a single bug report.

**Источник.** New Super Mario Bros. Wii's Super Guide - after the player fails a level eight times a green block appears, hitting it plays the level on autopilot, the player can take back control at any time, and a Super-Guide clear does not count as a real win (Super Mario Wiki, "Super Guide" and "Super Guide Block"; the GameFAQs thread "The green box for 8 deaths").

### Move every mismatch warning out of the action window: warn at the bench, and once on the tick the cast lands - never mid-charge, mid-retrieve or mid-fight  `S`

**Как работает.** Audit every teaching string for when it fires, and move it. At the Tackle Station, when a rig is tied to a weight, print the verdict there and then: "42 g on a 10-30 g blank - overloaded." On the exact tick the cast lands, print at most one line comparing loaded weight to the blank's test window and cast distance achieved. During charging, retrieving and fighting, print nothing new at all. Enforce it structurally: give diagnostic messages a phase tag (BENCH / POST_CAST / POST_CATCH / POST_LOSS) and refuse to emit any tagged message during an active input phase.

**Почему подходит.** Your rod-test window and cast-weight rule are the mod's most invisible mechanic and the one players cannot infer, but the charge bar is the worst possible place to explain it - the player is executing, not reading. This is a pure re-timing of strings you already have, and it also structurally prevents shipping another impossible instruction like the hold-the-button message.

**Источник.** Celia Hodent (former Fortnite UX director) in the Game Developer piece on her GDC Masterclass: it is not advisable to teach when players are under threat, and "right after" is the way to go, when the player can actually pay attention and perform the action; she also covers working-memory limits on how much a tutorial or UI can carry (gamedeveloper.com, "A quick UX lesson from GDC Masterclass teacher Celia Hodent"; celiahodent.com, Gamer's Brain talks).

### An onboarding-only assist on the strike-timing windows: widen the window, and land the fish anyway after N seconds of missed strikes, at reduced grade  `S`

**Как работает.** Add an assist flag, on by default for the first N in-game days or until the first ten landings, off in the realistic preset. While active: widen the float/bottom strike window by a configured factor, and if the player misses every strike, hook the fish anyway after a timer - Dredge's exact trick - but grade the catch down and mark it "assisted" in the diary so it never pollutes prime grading, trophies or legendaries. Fade the assist out automatically as the player's success rate rises rather than making them find a settings screen.

**Почему подходит.** Your fishing flows are harder than Stardew's, and Stardew's own creator has publicly said his was mistuned at the start. Dredge is the counter-example that shipped: it protected the first thirty minutes without softening the game that follows. Grading assisted catches down keeps the simulation's integrity intact while making sure a new player never spends an hour learning that they were doing everything right except the last 400 ms.

**Источник.** Black Salt Games on Dredge: "One of our core design pillars was that fishing should not be frustrating", "You're already catching the fish whether you're pushing any buttons or not... you can either help it along or potentially hinder it", plus a dedicated accessibility setting that removes minigame penalties, and the designer's explicit complaint that Stardew's fishing "is just too finicky" (Game Developer, "Trawling in the deep: How Black Salt Games made spooky fishing RPG Dredge", fetched). Corroborated by ConcernedApe himself conceding Stardew's fishing minigame starts out too hard and needed a better difficulty curve (GoNintendo, "Stardew Valley creator defends the fishing mini-game").

### Let the daily commission print the full recipe — water body, depth band, season, time window and bait — so the quest board IS the manual  `S`

**Как работает.** Terraria's Angler is a tutorial disguised as a chore. Because the dialogue names the biome outright, every quest teaches one entry of the fish table, and after thirty quests the player has been walked through thirty habitats without reading anything. The guaranteed milestone ladder is the other half: the randomised rewards keep it fresh, the fixed milestones mean the player always knows what they are working toward. Port: your existing order-of-the-day should render the bite engine's own gating for the target as a checklist — 'River, 3-5 m, autumn, dawn, on worm or maggot, needs a float or feeder rig' — with the conditions you have already met ticked off live from the player's current position and rig. That single panel teaches water type, depth preference, seasonality and bait matching in one read. Add a fixed milestone ladder over the counter (every 5th order gives a named piece of gear) so the grind has a visible spine.

**Почему подходит.** It is the cheapest possible fix for 'explains nothing in-game' because it needs no new systems at all — it renders data the bite engine already has, in the one place the player is already looking. It also fixes the known bug where an order can name a fish the player's fisherman does not buy, because writing the checklist forces you to validate the order against the same gates.

**Источник.** Terraria — read the official Terraria Wiki 'Angler' page: the quest fish is picked at 4:30 AM, and 'the location of a given quest fish is stated directly underneath the Angler's quote'. No riddles. Guaranteed milestone rewards at quests 5/10/15/20/25/30 (Fuzzy Carrot → Angler Hat → Vest → Pants → Bottomless Water Bucket → Golden Fishing Rod at 30) sit on top of a randomised pool.

### Prebaiting: feed a spot on days you are not fishing it, and the fish there gradually drop their guard  `S`

**Как работает.** Per (water body, spot) persist `confidence` 0..1 and `last_fed`. Throwing or spodding free feed with no rig in the water raises confidence at a good rate; feeding while lines are out raises it far less; confidence decays a few percent per MC day. Effects at high confidence: shorter time-to-first-bite, a size-distribution shift toward larger fish (the wary big ones are the ones who relax), and a reduced spook penalty. Rate-cap it so a spot fed on three separate days clearly beats one giant dump on one day — that asymmetry is the whole lesson. Place a cheap bank marker item to read a spot's confidence as a tooltip, and let the journal list your cultivated spots.

**Почему подходит.** It is the purest single-player long game in the list and the natural partner of the pond stocking the mod already has: stock a pond, then cultivate a spot inside it across in-game weeks. It needs one persistent map and no new content, and it rewards the kind of player who plays a fishing mod for weeks rather than an evening.

**Источник.** Real mechanism: Anglers' Net, "The Power Of Prebaiting" and Sticky Baits, "A Guide to Pre-Baiting" — introducing about a pound of particles three times in the week before fishing conditions the fish to feed confidently; bait an area for up to four weeks before dropping a rig and "the carp learn to feed with no pressure, which results in confident takes". The key causal claim (DNA Baits, prebaiting tips): feeding while there are NO lines in the water is what makes them drop their guard.

### Make depletion perceptible: a stock survey readout and a visible recovery curve for the chunk you just hammered  `S`

**Как работает.** FMB stores a fish count per chunk, drains it as you fish, and lets the population go extinct; players discover this only by reading a wiki, and the mod's answer was a separate bait-box item to inspect and restore it. River Fishing: reuse the existing bait trap (or one survey cast) to return a banded verdict for the chunk — Untouched / Healthy / Thinned / Hammered — plus an estimated days-to-recover and which species have dropped out. Optionally a short in-world tell that decays with the stock (fewer surface rings, smaller bait-fish flurries).

**Почему подходит.** River Fishing already has per-chunk pressure and depletion; today a depleted spot is indistinguishable from bad luck, which reads as the bite engine being broken rather than as an ecosystem. This adds no new simulation, only the instrument that makes the existing one teachable — and it sets up stocking as the obvious remedy.

**Источник.** Fishing Made Better — per-chunk fish populations that can be fished to local extinction, with "bait boxes to monitor populations" and hatcheries to breed them back (RLCraft wiki page for Fishing Made Better, rlcraft.wiki.gg/wiki/Fishing_Made_Better, plus the author's forum thread describing recovery "through migration or reproduction")

### A catch card with four or five hoverable check marks that says what you did right and what cost you  `M`

**Как работает.** On a successful landing, add a 5-slot strip of 16x16 tick/cross sprites to the existing catch GUI: rod class suits the rig, loaded weight inside the blank's test window, hook size suits this species' mouth, bait on this species' list, line/leader never went above its rating. Hover each mark for one sentence with a number: "Hook #12 - this fish takes a #6 comfortably; small hooks cost you fish in the fight." Because it fires on SUCCESS it reads as a coach, not a scold, and the crosses explain the thing players actually complain about - why the fight was long, why the fish was small, why the same rig failed the next ten times.

**Почему подходит.** It attacks the confirmed weakness at the only moment the player is definitely looking at a GUI and definitely in a good mood. It also gives the prime-grade and trophy systems a visible causal chain ("this fish scored 62% because...") without adding a single mechanic to the sim.

**Источник.** theHunter: Call of the Wild's Harvest Screen and Harvest Check - four criteria (vital organ hit, correct ammunition class, two shots or fewer, trophy organ undamaged), shown as check marks you hover to be told which one failed and why (theHunter COTW Wiki, "Harvest Screen"; Steam discussions "What the hell is a harvest check?", "Harvest check - What am I missing?!?").

### Ponder-lite: hold the forward key on a rod in your inventory and watch a 2D animation of the exact input rhythm that rod wants  `M`

**Как работает.** Reuse Create's exact gesture (hover + hold forward + fill bar) because a large share of your players already know it. Open a plain Screen and animate in 2D with GL_LINES and the existing 256px art - no 3D, no fake level. ACTIVE class: a horizontal timeline with a sweeping marker and dots at the correct click gaps, so the player literally sees that the GAP is the lure action. FLOAT: a float bobbing, then diving, with the strike window drawn as a highlighted band. BOTTOM: a tip loading up and a hand deliberately not moving. Two or three scenes per class, A/D to step, Q to freeze. Zero prose required - the rhythm is the lesson, which also means zero translation work across your three languages.

**Почему подходит.** Your worst report was a player fishing a bottom rod like a spinning rod for hours; a five-second animation of a motionless tip is unmissable in a way a tooltip sentence is not. It is also the one teaching device that scales to the click-cadence mechanic, which is genuinely impossible to describe in text and trivial to show as moving dots.

**Источник.** Create's Ponder - hover an item in the inventory and hold W until a bar fills, which opens an animated scene; Q is Identify (pauses), S restarts, A/D step between scenes for that item (Create Wiki "Ponder"; Modrinth "Create Ponder"; the ACF-Team/Ponder port on GitHub documents the schematic-plus-storyboard structure).

### Behaviour-triggered tips with an explicit trigger / skip_trigger / dependencies data format, so the game notices misuse and speaks exactly once  `M`

**Как работает.** Ship tips as data, one JSON per tip, mirroring Factorio's shape: { id, trigger, skip_trigger, dependencies, text }. Triggers are counted gameplay events you already fire - retrieve_click, cast, strike_miss, fish_landed, rig_tied - with an AND/OR/COUNT composite. The bottom-rod case becomes: trigger = AND(count retrieve_click on BOTTOM >= 20, count fish_landed on BOTTOM == 0); skip_trigger = count fish_landed on BOTTOM >= 1. Fires one non-blocking toast plus one action-bar line, writes itself as read into player NBT, never fires again. Optionally point the tip at the Ponder-lite scene instead of prose. Ship about twelve, each mapped to a real support question you have already answered on Discord.

**Почему подходит.** This is the direct, general fix for the class of bug where the player's confusion is invisible to you until they file a report. skip_trigger is the part worth stealing hardest - it is what stops the system nagging competent players, which is the reason most Minecraft mods' first-join hint spam gets muted. And because it is data, your translators and your future self can add tips without touching code.

**Источник.** Factorio's TipsAndTricksItem prototype, which has trigger, skip_trigger, dependencies, starting_status and an optional simulation, with trigger types including BuildEntity, CraftItem, Kill, UnlockRecipe, TimeElapsed, CountBased, and And/Or/Sequence composites (lua-api.factorio.com, TipsAndTricksItem, fetched). Factorio's own devlog explains the pain they were solving: tips shown at the start of a game "when many of the tips are not relevant", and their fear that "Nobody will ever read them" (Friday Facts #208, fetched).

### A placeable marker buoy that remembers a spot's depth, bottom and catch history, and gives casts that land near it a small accuracy bonus  `M`

**Как работает.** A cheap floating block (cork + string). Placed on water it stores the sounded depth and bottom type of that column plus a running tally of what has been landed within ~6 blocks of it. Jade/tooltip shows the tally. Mechanically it does one small thing: a cast whose splashdown lands within 2 blocks of a buoy counts as an accurate cast (no distance-error penalty), so marking pays off. Buoys are per-world persistent, a few per player.

**Почему подходит.** Makes the mod's per-chunk pressure/depletion data player-readable and gives the sounder a payoff verb — find, mark, return. It is a JSON block model with a 16x16 texture and a BlockEntity holding an int and a map; no new asset class. Turns 'this swim was good last autumn' from something on a wiki into something in the world.

**Источник.** Fishing Planet fish-finder thread (steamcommunity.com/app/380600/discussions/0/1643168364661643201/): a player describes the loop that finally worked — "I couldn't catch an 11.1 lb catfish for a mission so I decided to try sonar and it worked", then after "marking a productive area with a buoy" they "caught a 12 lb catfish and completed the mission." Also RF4 'Current state of the game?' (app/766570/discussions/9/5943121463503654568/), where players complain active spots are "difficult to discover independently, forcing reliance on community guides."

### A craftable barometer whose needle predicts the pre-front feeding binge, because Minecraft already knows when it is going to rain  `M`

**Как работает.** MC's ServerLevel already stores rainTime/thunderTime countdowns, so the weather is known in advance — synthesize a deterministic pressure curve P(t) from them rather than rolling dice. Baseline ~1015 hPa; fall smoothly to ~995 across the last ~2 MC days before rain is scheduled to start; trough during the rain; steep rise for 24-48 in-game hours after it clears. Two separate bite modifiers: (a) LEVEL band — 1005-1030 neutral, >1032 gives -20% and pushes species' depth preference deeper, <1000 gives -15%; (b) TREND, the derivative — falling at 1.5+ hPa/hour gives +30-50%, the single biggest bite window in the game, and sharply rising after a front gives -40% that decays over 24-48 h. Add `pressure_sensitivity` to the fish JSON so deep benthic species (catfish, burbot, eelpout) barely react while shallow and pelagic species react hard. The barometer is a 16x16 item or a small block with a handful of needle-position sprites plus a tooltip: "1004 hPa, falling fast."

**Почему подходит.** Every other realism mod treats weather as a hidden multiplier applied after the fact. Because MC schedules its weather ahead of time, this is the rare case where a real angling instrument becomes genuinely predictive: the player reads a falling needle, drops everything, and fishes — then learns not to bother for two days after the storm clears. Zero new world state, one sprite sheet, and it turns a multiplier the engine already has into a decision.

**Источник.** Real mechanism: Kestrel Meters, "How Barometric Pressure Affects Fishing" — a table of inHg bands (high >30.50 = low/medium activity and fish go deep; medium 29.70-30.40 = normal; low <29.60 = slowed; FALLING = "excellent fishing conditions, highly active") plus the key timing line: after a low passes and high pressure rolls in, "it can take between 24-48 hours to see another round of active feeding." Mechanism is swim-bladder discomfort (Mercury Marine Dockline). Modelled in Russian Fishing 4, where Steam discussions confirm bite frequency is heavily weather/pressure driven.

### A spawning calendar per species: a pre-spawn binge that is the year's trophy window, a near-total shutdown while spawning, then a two-to-three week recovery slump  `M`

**Как работает.** Each species JSON gets `spawn_temp` and a `spawn_window` triggered by season AND water temperature, so a cold spring delays it and the calendar is not a fixed date the player memorises once. Four phases. PRE-SPAWN: bite rate well up and — the important part — the heaviest fish of the year are available because of egg weight, making this the game's trophy window. SPAWNING: that species' bite rate near zero, and the fish are visibly in the shallows and weed (emit the surface-sign particles in the margins so the player can SEE why they cannot catch them). POST-SPAWN: one to three in-game weeks ramping linearly from roughly 25% back to normal, average weight dips, and fish prefer small high-protein baits. NORMAL. Stagger species through spring so the player rotates targets rather than sitting the season out. Publish each species' schedule in the journal once observed, and hang a self-imposed closed-season quest or advancement off it — return every fish of a spawning species for the whole window.

**Почему подходит.** The mod already depends on Serene Seasons and already grades fish against a per-species max weight for prime grade and trophies — pre-spawn is precisely when those record weights should be physically reachable, which gives the entire trophy and prime-grade system a calendar instead of a flat dice roll. It also gives the journal something to teach that is not merely "where".

**Источник.** Real mechanism: Aquamarine Power, "Fish Spawning and How It Affects Fishing" — pre-spawn fish take in two to three times their normal food building reserves, and "complete feeding cessation characterizes many species during actual spawning". Baitshop.com, "Post-Spawn Bass Fishing" — recovery "suppresses feeding behavior for two to three weeks", because females shed eggs accounting for fifteen to twenty percent of body weight and metabolism is rebuilding, and "the recovery is gradual, not switch-on switch-off". Premium Carp Fishing, "After the Spawn" — post-spawn carp are cautious and selective.

### Water clarity (turbidity) as a real per-water-body variable that flips which lure colour and line work  `M`

**Как работает.** Clearer Water is a client tweak: people install it purely so they can see into the water while fishing. Nobody makes clarity mechanical. River Fishing: give each water body a turbidity value that rises for a day or two after rain, rises with mud/gravel/clay bottoms and with heavy groundbaiting, and falls in cold, settled weather. Clear water multiplies the existing line-visibility penalty and favours natural/subtle lure colours; muddy water cancels the line penalty entirely but kills bright-flash lures and shifts the advantage to scent — groundbait and oil cake.

**Почему подходит.** River Fishing already has a line visibility coefficient that fish notice and a lure-dyeing feature whose colours currently matter in a flat way. Turbidity is the one input that makes both of them situational instead of a fixed number, and it explains why the same rig works on Monday and not Thursday. Also: 200k people demonstrably care about how much they can see in the water.

**Источник.** "Clearer Water" — 207k downloads for nothing but "makes water 50% clearer for those hard days of fishing" (Modrinth downloads-sorted fishing index, api.modrinth.com/v2/search?query=fishing&index=downloads); no fishing mod I read models clarity as a gameplay input

---

## Вода и условия: погода, давление, прозрачность

*15 идей.*

### Cleaning up a swim actually improves it: junk you pull out raises the chunk's quality, fish you strip lowers it  `S`

**Как работает.** Hook the existing per-chunk pressure value to two new player verbs. Junk bycatch (boots, cans, tangled line) removed from a chunk and disposed of properly nudges its quality up a little each time, with a cap. Releasing undersized fish nudges it up; killing everything you land nudges it down faster than idle depletion does. A visual: a cleaned swim gets slightly clearer water and more surface tells, a hammered one gets floating debris particles.

**Почему подходит.** River Fishing has junk bycatch and per-chunk depletion but the player has no repair verb — pressure is a one-way tax, which is exactly what makes depletion systems feel like punishment rather than stewardship. This adds the missing direction using two systems already shipped, costs a couple of counters, and gives the mod a quiet ethic that fits a realism sim: your local water is yours to ruin or keep.

**Источник.** Carp Fishing Simulator suggestions (steamcommunity.com/app/366290/discussions/0/1474222595307851711/), player Claes: "give couple of free points if ppl pick up litter left behind by less considerate anglers." The counterpart friction is RF4's 'Punish new players' thread (steamcommunity.com/app/766570/discussions/9/599643064665912237/) and 'Current state', where depletion is felt only as punishment — "the grind has become painful when there are barely any 'decent' spots active."

### Barometric pressure trend, read straight off Minecraft's own scheduled weather, with a barometer item  `S`

**Как работает.** ServerLevel already stores rainTime and thunderTime — the game literally knows a front is coming before the player does. Derive a pressure scalar from (current weather state, ticks to next change), keep a rolling few-day history in world data, and expose a trend: falling / steady / rising. Falling into rain = a broad bite bonus and fish sitting higher in the column (which now matters because depth is player-set); the first day of high pressure after the sky clears = a slump and fish deeper. A crafted barometer item — 16x16 with a few needle frames — shows only the needle and the trend arrow, so the player learns the rule by correlation.

**Почему подходит.** The bite engine already reads weather as a state; this reads it as a derivative, which is the actual angling wisdom, and Minecraft is one of the few games where the engine genuinely knows the future weather so the prediction isn't faked. Cheapest big win on this list: a couple of fields plus one sprite.

**Источник.** Fishing Planet — 'Fish increase their activity in the days before a cold front moves in, but after the front passes through and for a few days after that, conditions worsen. The high pressure that comes after the cold fronts makes fish become lethargic, meaning they won't move as far or as near the surface' (Fishing Planet Basic fishing tips wiki, via search extract).

### A gut clock: a spot you have just fed heavily goes dead, and how fast it recovers depends on the water temperature  `S`

**Как работает.** Per spot keep `fed_units`. Every unit of groundbait, particles or spod adds to it; every fish caught there adds a little too (that fish ate). Scale the bite rate by a curve that PEAKS at moderate fed_units and then falls away — so over-feeding actively kills the spot rather than merely being wasteful. Drain `fed_units` at a rate proportional to water temperature: roughly double the drain per +10 °C, near zero under ice. The learnable rule falls straight out: in high summer you can and should feed heavily and often, in winter a single small ball is an entire session and a big dump means no bites for hours. Keep it honest — when fed_units is above the peak, the actionbar or the spot marker says "the fish here are still full", so it is a readable state and not a hidden roll.

**Почему подходит.** It is the direct antidote to the degenerate strategy the current groundbait system invites (dump everything, farm bites), and it does that without a cooldown timer or an artificial cap — the limit is a modelled fish stomach the player can reason about. It also finally makes the mod's season and water-temperature reads matter to the player's hands rather than to a coefficient they never see.

**Источник.** Real mechanism: "Effects of temperature on feeding and digestive processes in fish" (PMC/NCBI) — temperature sets metabolic rate and therefore feeding behaviour, with the widely quoted rule of thumb that a fish at 28 °C processes a meal roughly twice as fast as the same fish at 18 °C. The angling corollary is the standard match-fishing warning (Angling Direct groundbait guide): overfeed and the fish "become satiated from overconsuming the groundbait and thus no longer interested in swallowing the actual hookbait."

### Water clarity as a real field — the missing input that finally makes the existing lure dyeing and line-visibility systems into one coherent decision  `S`

**Как работает.** Per water body a `clarity` 0..1 driven by rain and spate phase (down hard), plankton or algae bloom (down, shared with the natural-food-glut calendar), silt stirred by wading or by a heavy lead landing, flow over mud versus gravel, biome (swamp low, mountain and ice lakes high), and season. Four effects, all learnable. (a) The mod's existing line visibility coefficient scales WITH clarity, so muddy water forgives thick line and heavy leaders while clear water punishes them — that alone converts an existing hidden coefficient into a reason to own more than one spool. (b) Lure colour, which players can already dye, is scored against clarity: chartreuse/white/orange win when stained, natural and translucent when clear, dark silhouettes in bright clear water. (c) At low clarity, colour weight drops and VIBRATION weight rises, so the click-cadence retrieve gap and the lure class (spinner or rattle versus subtle) carry the bite instead. (d) Clear water amplifies the disturbance/spook penalty. Show it with a "the water is heavily coloured" line and a subtle tint.

**Почему подходит.** The mod already ships dyeable lures whose colour affects bites, a line visibility coefficient fish genuinely notice, and a retrieve cadence system — clarity is the single missing field that ties all three into one decision the player can reason about instead of guessing. It is also the cheapest idea here that upgrades three existing features at once without adding any content.

**Источник.** Real mechanism: Kraken Bass, "Water Clarity Guide for Choosing Baits" and Douglas Outdoors, "Choosing The Best Lure Colors For Every Water Condition" — subtle translucent colours in clear water, bold high-visibility colours (chartreuse, white, orange) as it stains, black/blue silhouettes in the muddiest. MagBay Lures on clarity and lure action, plus the sensory point repeated across all of them: "in muddy water, fish are relying on lateral lines and vibrations more than eyesight, which means movement, thump, and noise matter more than anything else", so rattling baits earn their keep when colour cannot.

### Marker lead: a cast that returns depth, bottom material and how hard the spot has been fished, instead of a fish  `S`

**Как работает.** A marker float rig on any bottom blank. Cast it and instead of a bite session you get a one-shot readout at the landing point: depth in blocks, the actual bottom block it settled on (sand / gravel / clay / silt / kelp / lily), the water-body type your engine already classified, and a coarse three-step read of that chunk's fishing pressure. Optionally let the player pin the reading as a named swim so the same distance and bearing can be re-cast later - store the cast vector, not the coordinates, and draw the pin with GL_LINES. No new species, no new fight, no new loot: it is pure readout of state the bite engine already computes.

**Почему подходит.** The cheapest high-value idea in the list. The mod already models depth, width, water type, cast distance, depth preference and per-chunk depletion, and the player can see none of it, so a deep simulation currently reads as randomness. A marker lead turns hidden state into a learnable skill (reading water) without weakening the silent-bite design the way a sonar would, because it costs a cast and tells you nothing about fish - only about the ground.

**Источник.** Russian Fishing 4 - the Marker Rod was added specifically to determine the bottom texture and the depth of a water body at a given position, and can be used alongside your three fishing rods and the spod rod (RF4 patch notes 29.11.2019 / RF4 wiki). Vintage Story's Primitive Survival mod - hold a stick, right-click water, and you get an on-screen message telling you how good that water is for fishing, which is its depletion read. Real carp fishing: feeling the lead down and the donk that tells gravel from silt.

### A barometer and a pre-front feeding window, derived from Minecraft's own scheduled weather  `S`

**Как работает.** Minecraft schedules rain and thunder ahead of time as a tick counter, so a barometer is nearly free: map time-until-next-weather-change onto a pressure trend (falling as rain approaches, low and flat during it, rising sharply afterwards) and show it as a crafted barometer item or wall block with a needle sprite and a trend arrow. Then actually pay it out in the bite engine - a 10-20 minute feeding window as pressure falls before the first raindrop, a slump during and just after a thunderstorm, a slow recovery on rising pressure - and since Minecraft already tracks moon phase, add a small solunar multiplier on full and new moons, which also matches Terraria's precedent of moon-gated critter spawns.

**Почему подходит.** It changes WHEN players fish rather than adding another verb, which is the cheapest kind of depth, and it costs one item, one sprite and a handful of lines in an engine that already reads weather. It is also a claim no competitor can honestly make: every fishing sim displays a barometer and none of them make it matter. Keep your existing weather factor and make this the trend on top of it, not a rename of it.

**Источник.** Real angling consensus: 29.70-30.40 inHg is the normal band, outside it signals a change, and the classic hot bite is on falling pressure right before a storm as fish feed hard (Tempest.earth barometric-pressure guide, AcuRite, The Fisherman 'Pressure Points'). The useful game precedent is a negative one: Fishing Planet puts a barometer on the HUD between the weather icon and water temperature, and a player on the official Steam forum says flatly "It matters IRL but not in this game, or any fishing game I know" - the display exists, the simulation does not.

### One water overlay at a time, painted only within a radius of the crosshair, cycled by a single key  `M`

**Как работает.** One key while holding a rod cycles OFF -> DEPTH -> BOTTOM TYPE -> PRESSURE -> RESIDENTS, drawing coloured particles on water blocks within about 12 blocks of the crosshair only, with the active layer's name on the action bar. Take Factorio's community lesson seriously - radius-limited around the cursor, never the whole visible water - and ONI's two lessons: exactly one layer at a time, and gate the later layers behind angler level or a perk the way ONI gates the exosuit overlay behind research, so the overlay set doubles as progression.

**Почему подходит.** Your bite engine reads water body type, depth, width and per-chunk pressure, and none of it is visible from the bank; players cannot form a mental model of a river they cannot see into. Particles are the one drawing primitive Minecraft gives you for free in world space, and the radius limit keeps it from becoming the debug view that ruins the mod's atmosphere.

**Источник.** Oxygen Not Included's Overlays - discrete single-purpose modes (temperature, gas, power, pipe flow) where some are locked until the matching research is done, and where gas pressure reads as colour intensity (Oxygen Not Included Wiki, "Overlays"). Factorio's alt-mode and the community reaction to it: the overlay wallpapers the screen, which is why the Alternative Alt Mode mod restricts the information to entities near the cursor (mods.factorio.com "Alternative Alt Mode"; Factorio Forums, "integrating the alt-mode into the default graphics", "Unobtrusive Alt Mode").

### Let the player set the rig's fishing depth in centimetres, and make the leader length add to it  `M`

**Как работает.** One integer on the rod NBT: depth_cm, nudged with a keybind or the tackle bench. The float renders at the surface, the hook sits at surface_Y - depth. The engine compares the bait's actual depth against (a) the water column depth at the bob's XZ and (b) the species' preferred layer — reinterpret the existing depth_preference as a band expressed either off the surface or off the bottom. Bite chance is the overlap of the two bands. Overshoot the bottom and the bait lies on the deck: the float lies flat instead of standing (a visible tell), float presentation dies and only true bottom feeders bite. Set it 5-20 cm off bottom and you get the classic bream/carp presentation. Bottom rigs invert it: the hooklength off the lead sets how far off bottom the bait rides.

**Почему подходит.** The mod already carries per-species depth preference as a hidden bite modifier; this makes it the player's decision instead of a dice roll, costs one NBT int plus a keybind, and immediately gives the marker cast, the substrate system and the thermocline idea something to plug into. It is the biggest single realism gap in the current feature list.

**Источник.** Russian Fishing 4 — float depth is bound to +/- keys and community guides give exact figures ("18-30 cm with bloodworm by day, 80-100 cm at night"), plus the leader rule: if the marker reads 2.35 m and your leader is 60 cm, set the float to 2.95. Ultimate Fishing Simulator 2 does the same thing under the name 'leader length — the distance between float and hook'. Read via the RF4 float-tackle guide extract and the SteamAH UFS2 beginner guide.

### A marker/plumb cast that measures depth, distance and bottom type before you commit  `M`

**Как работает.** A Marker Float item (16x16 sprite) you clip on in place of the rig. Cast it, it sinks, and on landing the actionbar reports three numbers: distance in blocks, depth in cm, and the substrate block it landed on. Each probe drops a persistent client-side marker at that spot for the session. Store probes per water body in the journal and draw the results as a distance-vs-depth profile with GL_LINES — the mod already draws line geometry. Probing costs time and can itself spook the spot (see the splash idea), so mapping a swim is a real trade-off rather than free information.

**Почему подходит.** Depth-setting and substrate matching are only interesting if the depth and substrate are discoverable in-world instead of via F3. This is pure data already in the level (block below water, water column height), needs one sprite and one chart, and gives the existing per-chunk pressure and stocking systems a spatial face.

**Источник.** Carp Fishing Simulator ships a dedicated 'marker rod (for surveying the lake bottom)' alongside the spod rod; RF4 players plumb with a marker float and quote readings like 'the marker says 2.35'; COTW: The Angler's bottom-fishing dev diary describes a HUD indicator that tells you 'whether the bait has reached bottom or remains suspended'.

### A summer thermocline that makes deep water go dead, and oxygen refuges that become the only fishable places in a heatwave  `M`

**Как работает.** Per water body compute a thermocline depth Dt: stratify only if max depth is roughly 7+ blocks and the season is mid-to-late summer; Dt gets deeper with surface area and with recent wind/rain (mixing). Expose two derived fields at the bobber: temperature(depth) and O2(depth). Below Dt in high summer, O2 decays toward 2 ppm, and the rule is a clamp not a penalty: every species' depth preference is remapped into the oxygenated band, so a bottom rig dropped into the dead zone gets almost nothing. Spatially, add an O2 bonus near flowing water (rivers, waterfalls, weirs, dripstone drip), at inflows into a lake, on the wind-blown bank, and during and just after rain — so a small still pond in a hot biome goes hypoxic everywhere and the fish are only catchable at the inflow. Invert in winter: under ice the deepest water is the warmest, fish stack deep and slow. Tool: a thermometer on a line — one bare-lead cast reports the temperature at the depth it landed, and the player maps the band by hand over a few casts.

**Почему подходит.** The engine already reads depth, season and biome, so this costs one item, one chat readout and a clamp — but it converts "depth preference" from a static number into something the world overrides seasonally. It also gives summer and winter genuinely different geography instead of just different species lists, and it makes the mod's small player-dug ponds behave like small ponds actually do in July.

**Источник.** Real mechanism: Iowa DNR, "Fishing the Thermocline for Better Summer Success" and Angler's Pro Tackle, "The Thermocline: The Science And How To Use It" — the hypolimnion below the thermocline gets no sun and no wind mixing, is stripped of oxygen by decomposition, and the whole fish population compresses into a narrow band just above it (the "habitat squeeze"). Hard numbers from UF/IFAS Fact Sheet FA-27, "Dissolved Oxygen for Fish Production": poor feeding response at 4-5 ppm, and "acute stress, no feeding and inactivity" at 2-4 ppm. Ultimate Fishing Simulator and RF4 both expose water temperature by depth to the player.

### A daily wind direction, a weather-vane block, and the rule that the bank the wind blows INTO is the bank worth fishing  `M`

**Как работает.** Derive a per-day wind vector (direction + strength) deterministically from world seed + day number, so it is stable and learnable rather than random noise, and give it a thermal character from the biome it blows FROM (a wind off a snowy or icy biome is a cold easterly; off a jungle or desert, a warm one). Publish it three ways so it is never hidden: a craftable weather vane block with a few directional sprites, rain particles slanted along the vector, and a line in the water-body info. Rule: for each cast compute whether it lands on or near the LEEWARD shore — the one the wind is blowing into — and apply a bonus that grows with a counter of how many consecutive days the same wind has held (cap around 3 days); the windward margin takes a penalty. A cold-source wind applies a flat penalty on top; a warm-source wind after a cold spell, a bonus. Split by species: surface and midwater feeders (bleak, rudd, chub, summer carp) follow the wind hard, true bottom feeders barely notice.

**Почему подходит.** It makes WHICH BANK YOU STAND ON a decision — the cheapest possible way to give a static Minecraft pond a daily personality, and it rewards players who walk the whole shoreline before setting up. It is all arithmetic plus one block and one sprite set, and it hangs naturally off the wind/plankton logic the oxygen idea already needs.

**Источник.** Real mechanism: Angling Times, "How wind affects carp fishing" and Haith's UK, "Wind and its effects on fishing" — wave action pushes plankton onto the windward shore, baitfish follow the plankton, predators follow the baitfish; a warm south-westerly that has been pushing into a bank for a day or two "loads that area with everything carp want: food, oxygen and warmer temperatures", and the cold easterly is the classic killer. BassResource, "Fishing When The Wind Blows" says the same for bass. The folklore rhyme ("wind from the east, fish bite least") is the memorable version.

### Natural food gluts — a daphnia bloom or a bloodworm bed — that make every prepared bait fail, with imitation as the only counter  `M`

**Как работает.** Per water body, a seasonal natural-food calendar in JSON: `daphnia_bloom` (late spring/early summer, requires warmth plus a fertile water body — plenty of seagrass/kelp/mud), `bloodworm_bed` (silt and mud bottoms, cold months), `snail_mussel` (gravel plus weed), `fry_glut` (late summer — small fish everywhere, so predators ignore lures). While a glut is live: a large flat penalty to ALL prepared baits and groundbaits; a clarity drop if it is a plankton bloom (feeding the turbidity field); and a specific counter-play that unlocks — bloodworm or joker from the existing bait trap beats a bloodworm bed, a tiny bait fished a few inches off the lead beats a daphnia bloom, a livebait beats a fry glut. Announce the glut in the journal and the water-body info so it is diagnosable rather than mysterious.

**Почему подходит.** It gives the seasonal calendar teeth beyond "which species is in season", and it is the honest explanation for a good spot suddenly dying — which a realism sim needs to be able to express without the player concluding it is a bug. Best of all it makes the mod's humble local baits (bait trap, worm farm, maggots) the ONLY answer during certain weeks, which retroactively justifies content that is otherwise outclassed by shop bait.

**Источник.** Real mechanism: French Carp & Cats, "How natural food sources in a lake affect carp fishing" — "billions of daphnia" cloud the water in May, they may "form the basis of most of the carp's diet", populations are in constant flux "which is why carp fishing catch reports can rise and fall so dramatically from one week to the next at this time of year", and the counter when carp are on daphnia is a very small popup 6-12 inches off the lead. Brothercarp, "Carp Bait: What To Use And How Much": on nutrient-rich waters carp "can ignore anglers' bait for days and never go hungry". Total Carp, "The Bloodworm Banquet" for the silt-bed version.

### Portable echo sounder that draws a live sonar trace of the bottom, the thermocline and fish arches under your cast  `M`

**Как работает.** Tide's Fish Finder is a held item that replaces several right-click stat readouts with one always-on GUI overlay; FMB's tracker points you at where a rare species lives. Neither draws the water. In River Fishing: a craftable sounder, used at the bank or while a cast is out, opens a GL_LINES readout — bottom contour sampled per block along the cast line, a thermocline band from season+depth, and 'arches' for the chunk's resident fish, arch height scaled to the biggest resident species' max weight and arch count scaled by current per-chunk stock. It names nothing: you get "three marks, one heavy, 4 m down, bottom drops off at 12 m", never a species.

**Почему подходит.** River Fishing already computes water body type, depth, width, species residency and per-chunk stock — the player just cannot see any of it and has to brute-force spots. A sonar makes the existing simulation legible without handing over the answer, and a line-drawn sonar trace is the one UI that is literally native to GL_LINES.

**Источник.** Tide 2 — "Fish Finder item for viewing multiple stats simultaneously" (Tide 2.1 changelog via api.modrinth.com/v2/project/tide/version); Fishing Made Better — "fish trackers to locate rare species" (author's Minecraft Forum thread, minecraftforum.net/.../2934749)

### Weather radio and lunar calendar: a forecast and a moon phase you have to go read before you decide when to fish  `M`

**Как работает.** Tide ships these as pure information blocks: you place them at camp and read them. It does not, as far as I can tell, feed barometric trend or moon phase back into catch rates — they're flavour. River Fishing: add a pressure-trend term and a moon-phase term to the existing bite engine (falling pressure ahead of a storm = short pre-front feeding window; stable high = slow; full moon shifts bite weight toward night for predators), then make the radio block print tomorrow's weather plus a rising/falling/steady arrow and the calendar block print the phase and the next new/full date. The information is only available at the block, so planning a trip is an act.

**Почему подходит.** The bite engine already reads weather, season and time of day; what is missing is any way to plan around them, which is the whole loop of a realism fishing sim. Two JSON-model blocks and a text GUI, zero new art pipeline.

**Источник.** Tide 2 — "Placeable Lunar Calendar and Weather Radio blocks added" and informational items promoted to GUI overlays (Tide 2.1 changelog via api.modrinth.com/v2/project/tide/version)

### Roe, fry and caviar: a hatchery that turns stocking into husbandry, with strains that trend heavier  `L`

**Как работает.** Angling's loop is worm → feed fish → roe. Stardew's Fish Pond yields roe/products on a timer and levels up when you satisfy the fish's requests. FMB lets you bucket fish into a private hatchery to restore populations. River Fishing: a spawning tray fed a prime specimen caught in its own spawning season yields roe → fry → stockable fingerlings of that species, which then feed the existing stocking system. Roe of a few species also processes into caviar as a high-value sale item. Each generation carries a strain value: fingerlings from consistently prime parents produce fish that roll a slightly higher weight, so selective breeding for size becomes the long game.

**Почему подходит.** Stocking currently only decides whether a species settles; there is no husbandry above it and no reason to keep the aquarium blocks running. This closes the loop, gives the seasonal spawning data an in-game use, and 'breed a strain that beats the species record' is exactly the endgame a realism sim wants — with no assets beyond icons.

**Источник.** Angling — "dig up worms from the dirt or mud and feed it to fish to get roe", plus filter-feeding clams and oysters that clean tanks (modrinth.com/mod/angling, 348k downloads); Society: Sunlit Valley / Stardew Valley's Fish Pond — "cultivate fish for their roe and other valuables… complete quests for your demanding fish and level up your ponds" (api.modrinth.com/v2/project/FpghCeHO); Fishing Made Better — bucket live fish to build "personal hatcheries" (author's Minecraft Forum thread)

---

## Презентация: глубина, дно, точка, прикормка

*16 идей.*

### Set the float's fishing depth yourself, and be wrong about it  `S`

**Как работает.** The float rig gains a depth setting in grams-style UI at the Tackle Station or by scroll-while-crouched in the field: how far below the float the hook sits, in blocks and quarters. Fish profiles already carry a depth preference; a mismatch multiplies bite chance down hard, and setting it deeper than the actual water depth lays the bait on the bottom (changes which species can find it, adds a snag chance). The plummet/sounder is how you learn the real depth. 'Two inches over-depth on a hard bottom' becomes a real, learnable trick.

**Почему подходит.** Float fishing in the mod currently has a strike-timing minigame but no *setup* decision — the one thing real float anglers spend their first ten minutes on. It reuses depth preference data that already exists, needs no new assets at all, and gives the plummet/sounder something to be for. This is the cheapest depth-of-play win on the list.

**Источник.** Fishing Planet 'Some Game Mechanics I don´t understand' (steamcommunity.com/app/380600/discussions/0/3595590846018838309/) — a player asks "How exactly does the Echo Sounder display the fish position?" and complains "Most of the answers are like a secret and the developers ignore or evade these questions often." Same desire in the Depth Finder thread: players use depth to "understand the lake's topography before fishing."

### Rotating weekly feeding preferences: what each species is 'on' shifts week to week, and the journal records your own findings  `S`

**Как работает.** Hash (world seed, week index, species id) into a small bias: one bait/groundbait gets a bonus, one gets a penalty, magnitude modest so it re-solves the puzzle without invalidating knowledge. Crucially, the journal auto-writes what worked: a per-species row 'week 14: responded to maggot, refused corn', built only from the player's own catches. Over a year of play the player owns a hand-built table nobody handed them.

**Почему подходит.** The mod's bait-match term is currently a constant, so bait is solved once and then it's a checklist. This makes it a living question at near-zero cost — one hash, one multiplier, one journal row — and it uses the mod's existing Serene Seasons hookup. It's also the direct antidote to the guide-dependency that the same playerbase resents.

**Источник.** This is the single most-praised sentence I found in the genre, from the RF4 defence thread (steamcommunity.com/app/766570/discussions/9/4356745301339943684/): "Fish migrate, fish have different feeding preferences week in week out, the game has seasons, weather preferences, days and night cycles" — offered as why it is "the best simulation of real life fishing that there is available." The failure mode is in the same community's 'Current state' thread, where dormancy is opaque and players end up "forcing reliance on community guides rather than experimentation."

### Cast accuracy with a backcast hazard: wind drifts the cast laterally, and a block behind you catches the backcast and costs you the rig  `S`

**Как работает.** Two additions to the existing charged power bar. First, a lateral error term: the splashdown drifts perpendicular to aim, magnitude scaled by wind, cast weight versus blank test window, and reduced by the angler's casting skill — so a well-matched weight lands where you aim and a mismatched one doesn't. Second, at release, scan the 4-6 blocks behind the player: leaves or logs there mean the backcast catches, you lose the terminal rig, and you learn to check your swing. A crouch-held sidearm cast halves the backcast risk and halves the range.

**Почему подходит.** The mod already has cast weight driving a blank test window and cast distance, plus rigs that can be lost to snags — this makes the *place* you stand a decision instead of scenery, which is free content in a Minecraft world full of overhanging trees. Pure logic, no assets, and it gives the marker buoy and the sounder somewhere to matter: hitting the mark becomes a skill.

**Источник.** Call of the Wild: The Angler 'List of problems' (steamcommunity.com/app/1408610/discussions/0/3422194223897397194/): "the throwing of the line is sorely lacking in precision." The criticism compilation (app/1408610/discussions/0/3422194223912378760/) adds that you "Cannot cast sideways to retrieve alongside docks" and asks for lures that "drift with water current." Fishing Sim World is praised specifically for offering "overhand, sidearm, and flipping" casts, and criticised for "Lures cannot get snagged or lost" (app/834280/discussions/0/1732089092455670622/).

### A 'read the water' check that shows a five-bar confidence meter for your current setup at this spot — before you commit the cast  `S`

**Как работает.** Crouch-look at the water for a second. An overlay shows five bars, no numbers and no species names: Season, Time, Bait, Tackle, Spot. Each bar is the bucketed value of the corresponding multiplier the bite engine is already about to compute. It tells you *which* of your choices is the weak one, never what the right answer is. Gate the fifth bar behind a skill perk so reading water is something you get better at.

**Почему подходит.** River Fishing computes at least a dozen multipliers — water body, depth, width, season, time, weather, biome, distance, depth preference, level, groundbait, bait match, tackle match — and surfaces none of them, so a bad setup and bad luck are indistinguishable. That indistinguishability is the documented reason players quit sims and go read guides. This is a read-only view of numbers the engine already has: one overlay, five bars, zero new game state, and it preserves the mystery of *what to do* while killing the mystery of *whether anything is wrong*.

**Источник.** Palia 'Fishing, RNGs and how removing agency from players is bad actually' (steamcommunity.com/app/2707930/discussions/0/4330853789658998916/): "It takes away agency from the player and that's absolutelly frustrating", "I hate anything that depends on RNGs, instead of skill", and the concrete minimum ask — "Just show us the odds at least." Fishing Planet mechanics thread (app/380600/discussions/0/3595590846018838309/): "Most of the answers are like a secret and the developers ignore or evade these questions often", with players forced into "forums, spreadsheets, and videos to understand basic systems."

### Give the soak a job: a throwing stick / spod that primes your NEXT spot while the current rod waits, with bait that accumulates and decays  `S`

**Как работает.** A thrown groundbait item that deposits into a chunk-level bait bank rather than firing a one-shot bonus. The bank accumulates per throw, decays over in-game hours, and above thresholds raises bite chance and biases toward the species that groundbait suits. So the right thing to do while a bottom rod soaks is to feed the swim you'll fish in an hour, or top up the one you're on at the correct rhythm. Overfeeding past a cap inverts the bonus — fed-off fish.

**Почему подходит.** River Fishing already has groundbaits including oil cake and per-chunk state to hang the bank on, so this is a counter and a decay tick. It converts dead time into the genre's most authentic form of planning without adding minigames, and it's the honest answer to 'waiting is boring': not less waiting, but waiting that is an investment. Pairs directly with the marker buoy — you feed a marked spot and come back.

**Источник.** Coral Island (steamcommunity.com/app/1158160/discussions/0/3467235293767482163/): "the time and patience required to catch something, is dead boring to me", "this isnt real life its a video game and it takes way to long sometimes." Palia thread (app/2707930/discussions/0/4330853789658998916/): "players are implicitly stuck at the controls just to wait" with "so much down time between real life interaction with the fishing game." Euro Fishing 'Bored.' (app/314520/discussions/0/360670708773933388/): "Just hold down the mouse button and wait. It gets extremely boring after a short while."

### Bottom substrate (sand/gravel/clay/mud/weed) as a first-class bite and snag factor  `S`

**Как работает.** Read the block under the bob: sand, gravel, clay, dirt/mud, seagrass/kelp, stone, coral. Each rig gets a substrate affinity table in JSON (the mod is already data-driven). Over silt/mud a dense bait sinks in and loses most of its attraction unless you use a buoyant bait (pop-up boilie, foam ball, bread) that holds it up; over weed the hook fouls, so snag chance rises and the bite window shortens unless the rig is a weedless/helicopter variant; gravel is the neutral good case. Bait presentation, hook size and rig type all read the same field, so one lookup drives three consequences.

**Почему подходит.** The world data is already in the chunk — zero new assets, one JSON table per rig — and it gives players a reason to look at the riverbed and to run marker casts. It also finally differentiates the eleven existing rigs by something other than which species they unlock.

**Источник.** Fishing Sim World: Pro Tour — Dovetail's own glossary: 'The bed type is the surface that your bait is landing on at the bottom of the lake. There are three main bed types which are featured in game, these are gravel, silt and weed.' Their carp tips article: rigs 'are best used over one of the three bed types', and warns specifically against 'using large sinking bait on small hooks over weeds'.

### Fishy notes: torn pages that hand you the conditions for a catch without naming the fish  `S`

**Как работает.** Tide drops notes as crate loot; each one tells you where/when something bites but not what, so the note is a riddle you go test. River Fishing: bycatch and treasure occasionally yield a note describing one species' condition row in the mod's own vocabulary — "deep water, first light, after rain, small bait, heavy line" — and reading it permanently unlocks that row in the journal while the species name stays redacted until you actually catch it.

**Почему подходит.** River Fishing deliberately removed the journal's 'how to catch' hover hint to keep discovery real, which left the journal's condition data locked behind already having caught the fish. Notes restore a legitimate, earned path to that information without giving it away — the same instinct Tide shipped, but keyed to River Fishing's much richer condition set (water body, width, depth, season, weather, distance, groundbait, tackle match).

**Источник.** Tide 2.0 — "Fishy Notes revealing catch conditions without fish identity", found in crates (Tide 2.0 changelog via api.modrinth.com/v2/project/tide/version; also described on modrinth.com/mod/tide as location hints)

### Species-specific by-products so 79 fish differ by more than weight  `S`

**Как работает.** Starcatcher's version is a flat 'this fish converts to that item' recipe set, and it is the feature they chose to headline for the next release — the demand is evidently real. Fishing Paradise's is a potion-effect food. River Fishing: give roughly 15 species one distinctive output at the fillet knife or a drying rack, each feeding something the mod already has: pike skin → a leather substitute for the vest/waders, carp scales → isinglass for a lure varnish that slows hook and lure wear, catfish whiskers → a crude line, sturgeon roe → caviar, eel slime → a scent additive for groundbait, oily species → fish oil that folds into the existing oil-cake groundbait.

**Почему подходит.** River Fishing's 79 species currently differ only in weight, difficulty and where they live, so collecting them pays only in journal completion. One unique output each wires the collection into the tackle, bait and cooking systems already built, and it is nothing but JSON recipes on icons that already exist. Starcatcher shipping this as its flagship next feature is a strong signal about what players want from a big species list.

**Источник.** Starcatcher — v3.0 "added recipes to convert specific fish into related items", with "unique fish uses and characteristics" as the v2.4 roadmap item (Starcatcher changelog via api.modrinth.com/v2/project/starcatcher/version, and the roadmap in github.com/wdiscute/starcatcher); Aquaculture 2 — fish bones craft into bone meal (modrinth.com/mod/aquaculture); Fishing Paradise — anglerfish/octopus/serpent grant Night Vision/Strength/Speed when eaten (api.modrinth.com/v2/project/fishingparadise); Oceanic — biome fish with unique cooked effects (modrinth.com/mod/oceanic)

### A hand-held echo sounder that prints the cast lane's bottom profile as a sparkline — depth steps, bottom hardness, weed edges — instead of telling you where fish are  `M`

**Как работает.** Right-click the item while aiming at water. It samples the water column along the cast lane at ~8 sample points and draws a GL_LINES cross-section in an overlay: seabed height per sample, bottom-block type as a colour band (sand/gravel/clay/mud), and a marker on any depth step of 2+ blocks. It reports terrain only — never fish. The bite engine already reads depth and water-body type; this just exposes the same world data the engine is secretly using. Two tiers: a crafted lead plummet (single-point depth, early game, one sample) and the sounder (full profile, late game).

**Почему подходит.** River Fishing's bite engine already computes depth, width and cast distance, and the player has no way to see any of it — so every 'wrong depth' penalty currently reads as bad luck. Minecraft terrain is literally a heightmap, so the profile is free to compute and drawing it is GL_LINES, which the project can do. It converts the mod's biggest hidden input into the thing players say they enjoy most: finding the drop-off yourself.

**Источник.** Fishing Planet — Steam threads 'Fish Finders - Post Your Experience / Tips' (steamcommunity.com/app/380600/discussions/0/1643168364661643201/) and 'Depth Finder' (app/1072480/discussions/0/1658943011692845836/). Players: the sonar reveals "bottom contours" including "silt, rocks, sand and vegetation"; one veteran insists it isn't cheating because "just because you stumble upon some fish does not mean they will bite."

### Stocked populations that grow: under-harvest a pond for a few seasons and its mean fish size creeps up; strip it and it shrinks  `M`

**Как работает.** Each settled species in a stocked water carries a mean-size multiplier, default 1.0. Per in-game season, compare fish removed against a carrying capacity derived from water volume and species: under quota, the multiplier creeps up a few percent and the size roll skews larger; over quota, it drops faster. Feeding the water (groundbait, oil cake) raises capacity slightly. Cap it so a pond can eventually produce genuine specimens but only after years of restraint.

**Почему подходит.** The mod's stocking system (native 250 / settled 150 / temp) already models residency but not time — a stocked pond is finished the moment it settles. Adding one float per species per water turns a private pond into the long-game project that single-player fishing fans actually fantasise about, and it makes catch-and-release meaningful without any moralising. It's arithmetic on data the mod already persists.

**Источник.** RF4 suggestions megathread (steamcommunity.com/app/766570/discussions/9/1635292137555417026/) — players ask whether releasing undersized fish serves any purpose and propose that released fish should "spawn more frequently" to simulate growth over time. Ultimate Fishing Simulator 'Fish levels' (app/468920/discussions/0/1489992713702142636/): the anti-pattern is a pond that is "pre-stocked" so it feels like "shooting fish in a barrel." Minecraft Forum 'Better Fishing' thread, player dsmidnight, asks for fish breeding as "less tedious than Forestry bees."

### Rebuild the legendary fish as world puzzles with a physical prerequisite, not as harder stat checks  `M`

**Как работает.** Moonglow Bay's bosses are not damage races — they are environmental problems where the rod is the tool and the arena is the mechanic, and each one telegraphs its threat with a readable tell before it punishes you. Port: give each legendary a precondition in the world rather than a bigger stat block. A giant catfish that lives under a specific sunken tree and will always take you into it unless you have first cleared or marked the snag. An eel that only shows on a moonless night and only if a lit lantern is on the bank. A pike that has to be moved off a weed bed by chumming a clear channel over several days. A sturgeon that only feeds where two currents meet, so you must find flowing water joining still. Each one is a block check plus a flag — no new AI — and each one has a tell you can read before it costs you the fish.

**Почему подходит.** Your legendaries are currently one-per-world named fish with per-species patterns, so they are memorable as loot but not as events. Preconditions make them memorable as stories, and they force the player to engage the world systems you have already built (snags, water body types, time gating, groundbait) instead of just bringing heavier tackle. All the tools are in the mod; only the encounter design changes.

**Источник.** Moonglow Bay — per TechRaptor's boss guide, Twinfinite's Lightning Fish guide and the Moonglow Bay Wiki: each of five chapters ends in a legendary fish fought as a puzzle. The Lightning Fish requires you to pull scattered buoys upright with your rod to act as lightning rods, or the lightning breaks your line, and the fish circles knocking them back down and periodically calls a thunderclap that flattens several at once. The Barnacle Whale requires removing three harpoons, each harder than the last. The Betta Angel Fish requires separating a pair by luring one around a tall structure, telegraphed by an exclamation mark before it lunges.

### Feeding spots you can overfeed — attraction that peaks and then falls  `M`

**Как работает.** Replace the flat groundbait buff with a Spot object at a BlockPos holding amount (grams), radius (~2.5 blocks), a build-up delay before it works, and a decay rate. Attraction = a curve that rises with amount to a peak and then FALLS past a species-specific saturation point — big fish tolerate a heavy bed, small silvers scatter off it, so overfeeding doesn't just waste bait, it changes which species shows up and can kill the spot for the rest of the session. Little-and-often topping up scores highest. Surface it only through the water: bubble particles thinning as the spot dies, never a number.

**Почему подходит.** Groundbait exists but is almost certainly monotonic (more = better), which makes it a resource sink rather than a decision. A non-monotonic curve plus a positioned spot makes the bait crops, oil cake and the bait trap into a feeding *plan*, and it pairs directly with the line clip.

**Источник.** Russian Fishing 4 — 'feeding is always working in a radius of 2.5 m', groundbait timers run on game time, a SPOD 'leaves a groundbait trail' and dust cloud, and crucially 'it is easy to overfeed the spot, so you shouldn't throw more than a few balls of groundbait at one spot at the same time' (RF4 Groundbait and Feeding Steam guide, search extract). COTW: The Angler update 1.7.0 added hand and catapult baiting that 'will create a baited area that will attract many fish'.

### Trot a float down the current — river fishing as a moving drift instead of a static wait  `M`

**Как работает.** In flowing water, read Level.getFluidState().getFlow() at the bob and let the float ride the flow vector. While it drifts, the bait sweeps new water, so bite rolls happen continuously along the drift instead of once at a fixed point. Hold right-click to feed line so the drift runs further and the bait stays ahead of the float; hold too tight and the bait lifts and skates unnaturally and bites drop off. At the end of the run you retrieve and re-cast — a rhythm rather than a wait. Loose groundbait thrown upstream drifts too, forming a downstream feeding lane the drift passes through.

**Почему подходит.** It uses a vanilla vector the game already computes, gives rivers a mechanical identity distinct from ponds and the sea (which the mod's whole name promises), and finally gives the reel-less pole and stick rods a technique of their own beyond the pull-out QTE.

**Источник.** Russian Fishing 4 dedicates whole rod families (Bolognese and match rods) to drifting a float down flowing water, and RF4-STAT separates float catches as those 'with depth set'; Fishing Sim World likewise ships rivers and canals with flow as distinct venues from stillwaters.

### Groundbait becomes a two-axis mix — how wet you make it and what particles go in — and the mix decides whether you pull fish onto the bottom or up into the water  `M`

**Как работает.** At the Tackle Station, mixing a groundbait becomes a recipe with two dials: WATER added (0..n units → dry and cloudy vs wet and binding) and PARTICLE content (hemp/maggot/pellet → active; crumb/soil/breadcrumb → inert). The output item carries three numbers: `cloud` (attraction applied to the mid and upper water column, short duration, large radius), `bed` (attraction on the bottom layer, long duration, small radius), and `bind` (whether it survives a long cast and whether it stays in a feeder or on a Method frame). Give each species a `feeding_layer` in its JSON and score the bite bonus as the match between mix and layer. The wrong answer must be visibly wrong, not just weaker: a cloudy mix over a bream spot pulls a curtain of small roach that strip the hookbait before the bream get there, and a claggy inert mix on a roach spot simply does nothing.

**Почему подходит.** The mod already has groundbaits, feeders, a Method/flat-feeder rig and a bench that ties components to a weight in grams — this adds one axis and turns "use groundbait" into "use the RIGHT groundbait", with instant, legible feedback in the form of the wrong species turning up. It also gives the existing bait farms (maggot, worm) and the bait trap a second use as particle inputs.

**Источник.** Real mechanism: Angling Direct, "The Complete Beginner's Guide To Fishing Groundbait" — mixed dry it "creates a fast-exploding cloud perfect for a traditional cage feeder", mixed wet "it becomes heavy and clays together beautifully for balls or Method feeder work". Dynamite Baits, "Grant's Top Groundbait guide": ACTIVE mixes contain particles like hemp that "pop and fizz around" and pull species such as roach that feed up in the water, while INERT mixes "sit on the bottom and leach out scent along the bottom, not up in the water" for bream and tench — and you make an inert mix by soaking it the night before so every particle is fully wetted.

### A scent plume that takes minutes to build and drifts downstream — so patience is a modelled advantage and constant recasting is a modelled mistake  `M`

**Как работает.** Bait and groundbait carry `flavour_class` (sweet/fruit ester, savoury/marine amino, spice, oil) and `leach_rate`. A cast starts a plume whose radius grows over the first several minutes — faster and wider in warm water and in flow, slow and tight in cold still water — and the bite bonus rises with the radius, so the opening minutes at a fresh spot are legitimately quiet and every recast RESETS the plume to zero. Temperature match: savoury/amino scores in cold water, sweet/fruit in warm, and bait oils go effectively inert below roughly 10 °C (a real bait-shop rule that players will enjoy discovering). In flowing water make the plume a downstream cone rather than a circle, so a bait presented upstream of the fish works and the same bait downstream does not.

**Почему подходит.** This is the mechanic that makes the mod's deliberately silent, patient design feel intentional instead of punishing: waiting stops being an absence of feedback and becomes a rising number the player can see and protect. The downstream cone also gives rivers a direction the bite engine already reads, and it pairs exactly with the crease/cushion lies — you learn to feed above the lie.

**Источник.** Real mechanism: FishingPellets.com, "How Flavours and Attractants Work Underwater" — as a bait breaks down it releases a plume, and the flavour profile decides "how far that plume travels, how long it lasts"; the goal is slow leaching, and warm water needs extra binder because the attractant otherwise washes out instantly. Carp Austria, "Carp Attractants": garlic, mussel, krill, Scopex and butyric acid are more noticeable in COLD water while vanilla, strawberry, pineapple and caramel work strongly in WARM water; amino acids are the strongest triggers, which is why fishmeal has dominated for decades (Tor Baits, "Best Boilie Flavours for Carp Fishing").

### Functional bank furniture: a staked/cleared bank, a persistent baited pit, an umbrella  `M`

**Как работает.** Every fishing mod I read ships fishing furniture and every one of them makes it purely cosmetic — plaques, frames, nets on walls. River Fishing: make building a spot mechanical. A cleared/staked bank block removes or halves the snag/foul-hook penalty inside a small radius and adds a little cast accuracy, but takes real work to place along a stretch. A baiting pit holds groundbait as a *persistent* local attractant that decays over days instead of one thrown ball, and slowly raises the chunk's local stock draw toward it. A bank umbrella cancels the rain penalty on reading a float and shelters the bait box from heat.

**Почему подходит.** This is the clearest 'they do it badly' gap in the whole category: the genre has decided fishing blocks are decoration. River Fishing already has a Fishing Stall, rod pods and groundbait, so 'improve a spot and come back to it' fits its existing vocabulary, and preparing a swim over several sessions is precisely what real carp and feeder anglers do. All JSON block models.

**Источник.** Lili's Lucky Lures — hanging frames, trophy frames, fish nets, "decorative blocks to enhance fishing-themed builds" (api.modrinth.com/v2/project/DMDVFZSF); Fisherman's Haven — "new blocks allow players to construct themed fishing villages and displays" (modrinth.com/mod/fishermans-haven); Fish of Thieves — "customizable Fish Plaques in various wood and metal finishes" (9minecraft wiki page for Fish of Thieves)

---

## Прочее: снаряжение, экономика, мир

*6 идей.*

### A 'Free Water' world option that removes level gates on species entirely: any fish can bite any setup, and undergunned tackle simply loses  `S`

**Как работает.** A world-creation / config toggle. With it on, angler level stops filtering the species roll. Instead: an unknown species lands as an unidentified specimen — you can't sell it, log it, or read its stats until the journal entry unlocks, which happens by catching it. Progression becomes 'I know this water' rather than 'the game permits me'. Undersized tackle is not blocked; it just loses to the existing break/tear rolls, which produces the story players actually retell — the one that got away.

**Почему подходит.** River Fishing already gates species by angler level and already has a per-species journal with reveal-on-catch, so the machinery for knowledge-as-progression is built — this reroutes it. Zero new assets. It also directly de-risks the mod's biggest exposure: every sim in the genre is 50/50 on Steam almost entirely because of level-gated grind, and this ships the alternative as an option rather than a rewrite.

**Источник.** The single most consistent quit-reason across every board. Fishing Planet 'too boring and repetitive in later game' (steamcommunity.com/app/380600/discussions/0/3117032860240491447/): "the only way to actually get to the higher levels is to sit there for hours/days/weeks/months and do the same boring thing over and over", "This isn't a game or simulator at this point. It's just a waste of time", and the concession "If they sold the game as a simulator and everything was unlocked, that would be fine." Ultimate Fishing Simulator 'Fish levels' (app/468920/discussions/0/1489992713702142636/): "Let me try to catch what I want even if my gear can't handle it." Contrast with the RF4 defender (app/766570/discussions/9/4356745301339943684/): "Knowledge is key in this game, experience brings you more enjoyment then any items."

### Named tackle loadouts and a craftable 'rig card' that reconfigures a rod in one click, in the field, with no GUI  `S`

**Как работает.** At the Tackle Station, save the current rod's full assembly (blank, reel, line, leader, hook, rig, weight in grams, lure, dye) as a named loadout. Writing it to a paper item produces a rig card. Right-click a rod with the card and, if you're carrying the components, it swaps to that configuration and returns the displaced parts to your inventory. Cards stack and are craftable copies, so 'my winter roach setup' becomes an object you keep in your tackle box.

**Почему подходит.** River Fishing has thirteen blanks, reels 1000-14000, lines by diameter, leaders, hooks #1-#20, eleven rig types and gram-tied weights. That is a superb simulation and a brutal amount of clicking to re-enter every session — and the mod's own backlog already has 'Normalise the inconsistent English item names', which says the UI surface is felt. This is the highest value-per-line item here: it's a serialised NBT blob and one item, and it removes friction from the thing players do most.

**Источник.** RF4 suggestions megathread (steamcommunity.com/app/766570/discussions/9/1635292137555417026/) asks to "streamline the repetitive process of manually selecting the same ingredients repeatedly." Carp Fishing Simulator suggestions (steamcommunity.com/app/366290/discussions/0/1474222595307851711/), player Claes: "option to change rigs/baits etc, without a big menu screen, more... organic in the game." The Angler's compilation lists "Menu oversaturated with transitions and submenus for tackle assembly."

### Widen the strike windows for a player's first catches of each species, then tighten them — plus a toggle alternative for every click-and-hold input  `S`

**Как работает.** Two independent changes. (1) A per-species familiarity counter: the float strike window and the reel-less pull-out QTE are ~50% wider for your first three catches of a species, lerping to normal by the tenth. Learning happens on real fish, not on failure. (2) Every hold input gets a press-to-start / press-to-stop equivalent behind an accessibility option, including the charged cast bar and the retrieve cadence — and the cadence flow gets a visible metronome tick so the required rhythm is legible instead of inferred.

**Почему подходит.** The mod's minigames are its front door — the float strike timing, the pull-out QTE, the click-cadence retrieve — and a sim with 79 species and 13 blanks cannot afford to lose players in the first hour, which is documented to be exactly where fishing minigames shed people. Both halves are small: one counter that scales an existing window, and an input-mode branch. Neither makes the game easier for an experienced player, which is the usual objection.

**Источник.** Stardew Valley's creator conceded the minigame "starts out too hard and should have had a better learning curve, starting easy and gradually getting harder" (gonintendo.com/contents/48074). Steam 'I hate fishing.' thread (steamcommunity.com/app/413150/discussions/0/4363502064161255423/): the bar "flips out all over the place", it behaves on "a J curve, making precise movements nearly impossible", and the click-and-hold requirement "causes wrist pain, especially when the bar bounces off the top or bottom." Coral Island (app/1158160/discussions/0/3467235293767482163/): "i wait for the fish to bite then a picture of a bar pop up...that didnt work . how to fish!!!???" and "I hate fishing mini games...Mini games to catch fish are just annoying and make it unenjoyable."

### Every specimen silently records the conditions it was caught in as a rare 'mark' that becomes a title on the fish's name — 'Bream the Sodden', 'Pike the Early Riser' — and the mark set per species is a collection orthogonal to size  `S`

**Как работает.** At the moment of landing, the bite engine already knows season, time band, weather, biome, water type, depth, rig, bait and rod class. Roll once against a mark table (~1/40 for a condition mark, ~1/800 for a 'shy' rare mark) and store the winning mark id in the fish stack's NBT. The item name renders as `<species> <title>` with a tiny glyph in the journal; the journal species page shows a strip of mark slots, filled ones coloured. Add one late craftable 'mark charm' trinket that grants two extra rolls, exactly like the source. Marks must never gate rewards — they are pure texture, which is why they never feel like a chore.

**Почему подходит.** Near-zero cost for the mod's biggest missing thing: a reason for the 400th bream to be interesting. Every input already exists in the engine and the display is a name format plus a lang key per mark. It also makes the deliberately silent, atmospheric fishing loop (night rain, ice hole, first snow) leave permanent souvenirs of the weather you sat through, which is what people actually remember from a fishing session.

**Источник.** Pokémon Sword/Shield marks (bulbapedia.bulbagarden.net/wiki/Mark): 45+ marks, each granting a title — Fishing Mark = "the Catch of the Day", Rainy Mark = "the Sodden", Stormy = "the Thunderstruck", Snowy = "the Snow Frolicker", Blizzard = "the Shivering", Dawn = "the Early Riser", Sleepy-Time = "the Sleepy", Dusk = "the Dozy", Lunchtime = "the Peckish", Sandstorm = "the Sandswept"; weather/time marks roll at 1/50, Rare Mark ("the Recluse") at 1/1000, 28 personality marks at ~1/2803, and the Mark Charm adds two extra rolls (effectively triples the odds). Scarlet/Violet added Jumbo and Mini marks tied to a 0–255 scale value.

### Hook PATTERN, not just size: gape measured against bait thickness, and point type worn down by the bottom you are fishing over  `M`

**Как работает.** Add `pattern` to the existing #1-#20 hooks — wide_gape, beak, long_shank, needle_point, circle — and derive gape from size x pattern. Three rules. (1) GAPE vs BAIT: every bait gets a thickness; if gape < ~0.6 x bait thickness, hook-up chance collapses while BITES stay normal, which reproduces the most common real failure ("loads of bites, hit nothing"); an absurdly large gape on tiny fish prick-and-loses them. (2) HOLD vs SET: wide gape gets a bonus on hold-under-ejection rolls (including the existing self-hooking/pod roll) and on hook-pull rolls; long shank gets a bonus on the hookset roll and a penalty on hold; circle hooks self-set on a run and almost never deep-hook, which feeds the release-survival idea. (3) POINT WEAR vs BOTTOM: needle points blunt fast over gravel, stone and cobble — multiply the existing hook wear rate by the bottom block type the feature-finding cast already identifies — while beaked points resist gravel and wear normally in silt. Surface the diagnosis in the journal as an explicit line: "many bites, no hookups — the gape is choked by your bait."

**Почему подходит.** The mod already has 20 hook sizes with wear plus baits with sizes, so this is one data column and three comparisons. It matters because a fishing sim must be able to EXPRESS the classic beginner failure — bites without hookups — and right now it cannot; and tying point wear to the bottom block makes the feature-finding readout pay off twice.

**Источник.** Real mechanism: Discover Boating, "Freshwater Fishing Hooks" — the gape is the hook's "functional size" because it "dictates clearance", and it must be wide enough for the thickness of the bait PLUS the fish's mouth tissue under compression; generous gape and throat give deeper penetration and better holding power. Angling Times, "How to choose the right hook for carp fishing" — the wide gape is a short shank plus a generous gape and "that extra space increases the chances of the hook taking hold as a carp attempts to eject the bait"; "beaked points curve slightly inwards and are more resistant to blunting on gravel"; "longer shanks increase the turning effect". Master Fishing Mag's hook-size cheat sheet gives the rule of thumb: gape should be about 60-75% of bait thickness.

### Waders, a fishing vest and a polarised cap: gear that changes where you can stand and what you can see  `M`

**Как работает.** All three treat fishing armour as a stat-buff set you collect — luck, speed, water breathing. River Fishing: make it positional. Chest waders let you stand in up to two blocks of water without the swim/float penalty, which puts previously unreachable lies in cast range and adds effective distance from mid-river; they also make you vulnerable (slow escape, cold in winter without an under-layer). A vest adds four tackle quick-slots usable straight from the hotbar so you can swap lure or rig without opening the bench. A polarised cap reduces the surface-glare penalty on reading a float at long range or in bright sun. All stat-only, all 2D armour-layer textures.

**Почему подходит.** River Fishing is entirely bank-bound: every spot is judged from where the land ends. Wading is the one change that re-scores every river the player already knows, and the fisherman's apron already establishes both the slot and the art style.

**Источник.** Fishing Frontier — the "Fisherman's Set" armour is the progression ladder, fished up piece by piece (modrinth.com/datapack/fishing-frontier); Starcatcher — fisherman hats with fishing buffs found in shipwrecks (Starcatcher 2.3 changelog); Hybrid Aquatic — diving armour (modrinth.com/mod/hybrid-aquatic)

---

## Источники, которые агенты действительно прочитали

328 ссылок и страниц. Помеченные как «search extract» читались через выдержку поиска, потому что прямой запрос вернул 403 или ошибку сертификата.

- https://terrafirmacraft.github.io/Field-Guide/18/en_us/mechanics/prospecting.html (fetched in full)
- https://lua-api.factorio.com/latest/prototypes/TipsAndTricksItem.html (fetched in full)
- https://www.factorio.com/blog/post/fff-208 - Friday Facts #208, Tips and tricks improvement (fetched in full)
- https://www.gamedeveloper.com/design/trawling-in-the-deep-how-black-salt-games-made-spooky-fishing-rpg-i-dredge-i- (fetched in full)
- https://terraria.wiki.gg/wiki/Bestiary (fetched in full)
- Fishing Planet Wiki - "Indicators" (wiki.fishingplanet.com/Indicators; TLS error on fetch, read via search excerpt)
- theHunter COTW Wiki - "Harvest Screen" (thehuntercotw.fandom.com/wiki/Harvest_Screen; HTTP 402 on fetch, read via search excerpt)
- Steam Guide - "Russian Fishing 4 Beginner guide" (steamcommunity.com/sharedfiles/filedetails/?id=3252048013)
- Steam discussions - theHunter COTW "What the hell is a harvest check?" and "Harvest check - What am I missing?!?"
- Hollow Knight Wiki - "Hunter's Journal"
- Super Mario Wiki - "Super Guide" and "Super Guide Block"; GameFAQs "The green box for 8 deaths (Super Guide)"
- PZwiki - "Moodle" (403 on fetch, read via search excerpt); Project Zomboid Wiki - "Moodles"; pzfans "Moodle Madness"
- Oxygen Not Included Wiki - "Overlays"
- Create Wiki / Creators-of-Create - "Engineer's Goggles"
- Create Wiki - "Ponder"; Modrinth "Create Ponder"; github.com/ACF-Team/Ponder
- bg3.wiki - "Dice rolls"
- StrategyWiki - "XCOM 2/Aim Bonuses"
- RimWorld Wiki - "Stat" and "Global Learning Factor" (403 on fetch, read via search excerpt)
- Game Developer - "Narrative Design in Dark Souls"; PC Gamer - "The art of flavour text"
- PC Gamer - "Realism be damned: Ultimate Fishing Simulator's underwater camera is great"; Ultimate Fishing Simulator Steam store page
- Game Developer - "A quick UX lesson from GDC Masterclass teacher Celia Hodent"
- Outer Wilds Wiki - "Computer"; nh.outerwildsmods.com "Ship Log"; outerwildsmods.com "Custom Ship Log Modes"
- GoNintendo - "Stardew Valley creator defends the fishing mini-game, but knows a lot of people really hate it"
- Game8 - "Monster Parts and Weaknesses Guide (Monster Hunter World)"
- Factorio Mods - "Alternative Alt Mode"; Factorio Forums - "Unobtrusive Alt Mode", "integrating the alt-mode into the default graphics"
- Steam - Cities: Skylines "Not enough buyers for products" discussion
- Game Developer - "Diegesis and designing for immersion"; Wayline - "Beyond the HUD: The Power of Diegetic Interfaces in Game Design"
- Call of the Wild: The Angler - Game Update 1.2.7 release notes (cotwtheangler.com) and Steam "Line Tension Position" thread
- https://steamcommunity.com/app/1408610/discussions/0/3422194223912378760/ — 'CCC: Constructive Criticism Compilation', Call of the Wild: The Angler
- https://steamcommunity.com/app/1408610/discussions/0/3479616387703832102/ — 'What is your biggest disappointment?', The Angler
- https://steamcommunity.com/app/1408610/discussions/0/3422194223897397194/ — 'List of problems', The Angler
- https://steamcommunity.com/app/1408610/discussions/0/4766584664411881106/ — 'FLY FISHING', The Angler
- https://steamcommunity.com/app/1408610/discussions/0/3415433633270143856/ — 'Catch & Release? Really?', The Angler
- https://steamcommunity.com/app/380600/discussions/0/3117032860240491447/ — 'too boring and repetitive in later game', Fishing Planet
- https://steamcommunity.com/app/380600/discussions/0/3595590846018838309/ — 'Some Game Mechanics I don´t understand', Fishing Planet
- https://steamcommunity.com/app/380600/discussions/0/2941371547507423777/ — 'Bottom fishing seems pointless', Fishing Planet
- https://steamcommunity.com/app/380600/discussions/0/1643168364661643201/ — 'Fish Finders - Post Your Experience / Tips', Fishing Planet
- https://steamcommunity.com/app/380600/discussions/0/1334600128975700035/ — 'Random fish jumping and fish splashes', Fishing Planet
- https://steamcommunity.com/app/380600/discussions/0/3488627261052647950/ — 'Barometric Pressure', Fishing Planet
- https://steamcommunity.com/app/380600/discussions/0/3183486320477047832/ — 'this game is boring', Fishing Planet
- https://steamcommunity.com/app/380600/discussions/0/364041776194543569/ — 'Take Picture', Fishing Planet
- https://steamcommunity.com/app/1072480/discussions/0/1658943011692845836/ — 'Depth Finder', The Fisherman - Fishing Planet
- https://steamcommunity.com/app/766570/discussions/9/1635292137555417026/ — 'Suggestions' megathread, Russian Fishing 4
- https://steamcommunity.com/app/766570/discussions/9/4356745301339943684/ — 'For those looking to play the game but worried by the recent spate of negative reviews', RF4
- https://steamcommunity.com/app/766570/discussions/9/599643064665912237/ — 'Punish new players', RF4
- https://steamcommunity.com/app/766570/discussions/9/5943121463503654568/ — 'Current state of the game?', RF4
- https://steamcommunity.com/app/766570/discussions/9/3319736698844830539/ — 'Selling fish and other suggestions', RF4
- https://steamcommunity.com/app/766570/discussions/9/5446521858549147760/ — 'Any tips for a newbe?', RF4
- https://steamcommunity.com/app/766570/discussions/9/597393167077093136/ — 'How can this game make more money?', RF4
- https://steamcommunity.com/app/766570/reviews/?browsefilter=toprated — RF4 top-rated Steam reviews
- https://steambase.io/games/russian-fishing-4/reviews — RF4 review split (50,421 positive / 35,097 negative of 85,518)
- https://steamcommunity.com/app/834280/discussions/0/1732089092455670622/ — 'Potential to be the best fishing sim - but I just can't buy it yet', Fishing Sim World: Pro Tour
- https://steamcommunity.com/app/314520/discussions/0/360670708773933388/ — 'Bored.', Euro Fishing
- https://steamcommunity.com/app/366290/discussions/0/1474222595307851711/ — 'Direction/Advice/Suggestion/Recommendation/Potential', Carp Fishing Simulator
- https://steamcommunity.com/app/468920/discussions/0/1489992713702142636/ — 'Fish levels', Ultimate Fishing Simulator
- https://steamcommunity.com/app/1136380/discussions/2/601908461606798353/ — 'Fly fishing?', Ultimate Fishing Simulator 2
- https://steamcommunity.com/app/2707930/discussions/0/4330853789658998916/ — 'Fishing, RNGs and how removing agency from players is bad actually', Palia
- https://steamcommunity.com/app/1158160/discussions/0/3467235293767482163/ — 'Why is everyone moaning about the fishing?', Coral Island
- https://steamcommunity.com/app/413150/discussions/0/4363502064161255423/ — 'I hate fishing.', Stardew Valley
- https://gonintendo.com/contents/48074-stardew-valley-creator-defends-the-fishing-mini-game-but-knows-a-lot-of-people
- https://steamcommunity.com/app/1562430/discussions/0/3806155895399959049/ — Dredge fishing-loop discussion
- https://modrinth.com/mod/fishing+ — Fishing + (Minecraft mod page)
- https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/2880530-better-fishing-redefines-the-boring-fishing
- https://www.curseforge.com/minecraft/mc-mods/advanced-fishing
- https://www.curseforge.com/minecraft/mc-mods/fishing-made-better
- https://www.pcgamer.com/games/this-russian-fishing-sim-is-bizarrely-popular-on-steam-but-players-cant-decide-if-they-love-or-hate-it-perhaps-because-of-the-usd2000-microtransaction/
- https://dredge.wiki.gg/wiki/Minigames
- https://webfishing.wiki.gg/wiki/Fishing
- https://www.davideaversa.it/blog/game-design-taxonomy-fishing-mini-games/ (also mirrored as 'Taxonomy of Fishing Mini-games' on Game Developer)
- https://www.gamedeveloper.com/design/deep-dive-the-surprising-depth-of-spatial-inventories-in-dredge
- https://www.zeldaspalace.com/twilightprincess/fishingGuide.php
- https://terraria.wiki.gg/wiki/Angler
- https://www.carlsguides.com/stardewvalley/fishing/how-to-fish.php
- https://www.godmindedgaming.com/reviews/cat_goes_fishing.html
- https://store.steampowered.com/app/1918300/Fishing_Vacation/
- https://www.gamepressure.com/graveyard-keeper/fishing/zab40d
- https://en.wikipedia.org/wiki/Let%27s_Fish!_Hooked_On
- Stardew Valley Wiki — 'Fishing' (bobber bar, perfect catch, bar size 96px at level 0 to 176px at level 10, treasure chest, Treasure Hunter tackle)
- Stardew Valley Wiki — 'Fish' (the five behaviour types: mixed, dart, smooth, sinker, floater)
- Stardew Valley Wiki — 'Collections' (fish collection tab, silhouettes of uncaught fish)
- DREDGE Wiki — 'Aberrations' (1-3 variants per species, glowing spots +35%, corrupted catch jingle, Atrophy spell guarantees an incomplete encyclopedia entry)
- DREDGE Wiki — 'Fish'; StrategyWiki 'DREDGE/Fishing'; TV Tropes 'DREDGE' (7x9 grid hold, Tetris fish shapes, damage occupies cells)
- Can I Play That? — 'Dredge accessibility review' (fixed catch time; missing prompts only slows the catch, no real setback)
- Dave the Diver Wiki — 'Fish' and 'Harpoon Gun'; Steam discussion 'What's the meaning of rank and stars of the fish cards?' (1/2/3-star quality set by capture method; per-dive harpoon tips vs permanent iDiver upgrades; each tip has a different input verb)
- Moonglow Bay Wiki — 'Fishing' and 'Deep Sea Lightning Fish'; Gamepur 'How to fish in Moonglow Bay'; TechRaptor 'Moonglow Bay Boss Guide'; Twinfinite 'How to Beat the Lightning Fish'
- Spiritfarer Wiki — 'Fishing'; Pro Game Guides 'Spiritfarer Fishing Guide' (yellow/orange safe, red = tap not release)
- Graveyard Keeper Wiki — 'Fishing' and 'Talk:Fishing' (three depth sub-zones with a per-species percentage table; bobber movement AND the Keeper's hand rhythm as dual bite tells)
- Nookipedia — 'List of fish by shadow size' and 'Blathers'; Game8 'How to Catch Fish Easily | Fishing Guide | ACNH' (1-2 nibbles before the take; smaller shadows flick their tails faster; stronger rumble for rare fish)
- Animal Crossing Wiki — 'Fish tank' (1x1 / 2x1 / 2x2 display sizes by fish size)
- Sneaky Sasquatch Wiki — 'Fishing Guide' and 'Fish'; Pocket Gamer 'Some tips for fishing in the game'; Touch Tap Play 'How to fish' (shrinking aim circle cast, nibbles then violent shaking, guide book handed over by the Fisher and filled in by turn-ins, 24 freshwater + 24 saltwater)
- Cult of the Lamb fishing guides — Pro Game Guides, gamepressure, GameRevolution (cast power gauge, keep the hook in a randomly drifting green bar, forgiving recovery)
- Fish Tycoon Wiki — 'Breeding', 'Game Mechanics', 'Magic Fishes' (independent 1-3 body and fin traits, deterministic crosses, fragile level-3 combos, 7 magic fish)
- My Time at Sandrock Wiki — 'Sandfishing'; Gamerant 'Sand Fishing Guide' (stamina and bait consumed win or lose; king fish announced by a prompt sound)
- Zelda Dungeon Wiki / Zelda Wiki 'Hena's Fishing Hole' and RPGClassics TP fishing guide (five lures, the confiscated Sinking Lure, season rerolls on re-entry, journal of max size for six species)
- Zeldapedia — 'Fishing' (BOTW has no rod; bomb/shock-arrow/spear/Cryonis fishing, and Miyamoto confirming the planned minigame was cut)
- Touch Arcade 'Fishing Break Review – Zen Luring'; GameGrin 'Fishing Break Review'; Google Play listing (clockwise mouse/finger circling as the reel input; sell vs donate-to-museum fork; elemental fish variants)
- WayTooManyGames and TheGamer reviews of 'Reel Fishing: Road Trip Adventure' (two-ring sync on the bite, stamina bar that refills when you stop fighting, craftable rods/reels/lures)
- Pocket Tactics 'Fishing Vacation Switch review'; Sidequest review; Kotaku 'Game Jam Combines Fishing And Horror'
- aaagameartstudio.com 'Best Fishing Game Design and Art Creation Insights' and tinyfishing.co.uk 'How Fishing Games Work' (cast/react/reward/upgrade loop, ~30s sweet spot, the three stacked reward types)
- C:/Users/Qwazar/VS Code Projects/fishing mod/common/src/main/resources/assets/riverfishing/lang/en_us.json (existing message.* and fishdesc.* strings, used to verify what already ships)
- https://steamcommunity.com/sharedfiles/filedetails/?id=2966610806 (RF4 Equipment Repair and Maintenance)
- https://cotwtheangler.com/news/patch-notes-1-1-3/
- https://cotwtheangler.com/news/developer-diary-introducing-bottom-fishing
- https://cotwtheangler.com/news/bait-boat-update-1-7-0-now-live
- https://cotwtheangler.com/news/patch-2-1-0-is-now-live
- https://cotwtheangler.com/news/patch-1-9-0-is-now-available
- https://cotwtheangler.com/news/
- https://live.dovetailgames.com/live/fishing-sim-world/articles/article/fishing-sim-world-help (Fishing Terms Explained)
- https://live.dovetailgames.com/live/fishing-sim-world/articles/article/top-5-tips-carp
- https://live.dovetailgames.com/live/fishing-sim-world/articles/article/top-5-tips-predator
- https://live.dovetailgames.com/live/fishing-sim-world/tags/updates
- https://live.dovetailgames.com/live/fishing-sim-world/tags/help
- https://steamah.com/ultimate-fishing-simulator-2-beginners-guide-to-fishing/
- https://store.steampowered.com/app/1136380/Ultimate_Fishing_Simulator_2/
- https://blog.yeoshin.co.kr/en/russian-fishing-4-beginner-guide/
- https://en.rf4-stat.ru/baits/
- https://support.miscgames.com/kb/fishing-north-atlantic
- https://en.wikipedia.org/wiki/Russian_Fishing_4
- Steam guide 'RF4 The basics of fighting fish' id=3010590056 (read via search extract; page 429'd on direct fetch)
- Steam guide 'RF4 Groundbait and Feeding' id=3225629635 (search extract)
- Steam guide 'Russian Fishing 4 Beginner guide' id=3252048013 (search extract)
- Steam discussions 'Float fishing tips?' / 'Hate the new feeder fishing bite mechanic' / 'Clip' / 'Can someone explain me what is clip' (RF4 English forum, search extracts)
- Fishing Planet Wiki - Retrieval Techniques (search extract; fandom paywalled)
- Fishing Planet Wiki - Basic fishing tips /en (search extract; cert error on fetch)
- Fishing Planet Wiki - Licenses (search extract)
- gameinfoland.com 'How to Fish Using Float Tackle in Russian Fishing 4' (search extract; DNS failed on fetch)
- gameplay.tips 'RF4 Match Fishing Guide for Sturgeons' and 'Guide to Match Rod Fishing after Carp' (search extracts; 403 on fetch)
- Official Rules Bassmaster Opens (bassmaster.com PDF, search extract)
- Fishing: Barents Sea Wiki - Fishing / Crew (search extracts; fandom paywalled)
- Rapala Fishing: Pro Series reviews - PlayStation LifeStyle, Xbox Tavern, TheXboxHub (search extracts)
- Reel Fishing reviews - WayTooManyGames, Pure Nintendo, Kresnik258Gaming (search extracts)
- https://dredge.wiki.gg/wiki/Aberrations (full fetch)
- https://igfa.org/grand-slam-clubs/ (full fetch)
- https://igfa.org/slam-and-trophy-clubs/ (full fetch)
- https://igfa.org/2025/10/24/igfa-launches-new-world-records-and-trophy-clubs/ (full fetch)
- https://igfa.org/world-record-requirements/ (full fetch)
- https://bulbapedia.bulbagarden.net/wiki/Mark (full fetch)
- https://bulbapedia.bulbagarden.net/wiki/Shiny_Pok%C3%A9mon (full fetch)
- https://monsterhunterworld.wiki.fextralife.com/Crowns (full fetch)
- https://wiki.guildwars2.com/wiki/Fishing (full fetch)
- https://en.rf4-stat.ru/weight/ (full fetch — RF4 trophy / super-trophy / max weight per species)
- https://www.seaofthieves.com/news/fishing-in-sea-of-thieves (full fetch)
- https://dec.ny.gov/things-to-do/freshwater-fishing/angler-achievement-awards-program (full fetch)
- https://en.wikipedia.org/wiki/Otolith (full fetch)
- https://en.wikipedia.org/wiki/Standard_weight_in_fish (full fetch)
- https://en.wikipedia.org/wiki/Xanthochromism (full fetch)
- https://en.wikipedia.org/wiki/List_of_Wisconsin_fishing_records (full fetch)
- https://en.wikipedia.org/wiki/Fishing_licence (full fetch)
- https://gamewith.net/animal-crossing-new-horizons/article/show/16346 (full fetch — ACNH shadow-size classes; page does not document Critterpedia record sizes)
- Fishing Planet Wiki, 'Collecting' — fish forms young/common/trophy/unique, 100-unique and 1000-trophy challenges (read via search snippet; wiki.fishingplanet.com blocked WebFetch on a TLS error)
- Ohio DNR, 'Celebrate a Trophy Catch with a Fish Ohio Pin' + 2026 Fish Ohio press coverage — per-species minimum lengths, Master Angler pin for 4 species in a year, 10,127 pins in 2025 (read via search snippets; ohiodnr.gov not fetched)
- Stardew Valley Wiki, 'Fishing' and 'Legend' — 'New record!' animation on a new record length, legendary fish once per save, Challenge Bait allowing up to 3 (read via search snippets; stardewvalleywiki.com returned 403)
- Terraria Wiki (wiki.gg), 'Angler' / 'Angler/Quests' — quest-fish gating and escalating bait rewards by quest count (read via search snippet)
- Wyoming Game & Fish 'Wyoming Cutt-Slam' and utahcutthroatslam.org — four subspecies in native ranges, certificate/decal/medallion (read via search snippets)
- Atlas Obscura 'How the Traditional Japanese Art of Fish Printing Inspired a Modern Art Form', US Harbors 'Record Your Catch Using Gyotaku', Smithsonian Ocean 'Educational Uses of Gyotaku', en.wikipedia.org/wiki/Gyotaku — 1839 earliest print, used to prove catch size (read via search snippets)
- Dave the Diver Wiki, 'Fish' — 1–3 star quality set by capture method (read via search snippet)
- Zelda Wiki / Zelda Dungeon, 'Golden Scale' — fishing-hole owner's standing record and the 4 lb margin (read via search snippets)
- NOAA Cooperative Tagging Center, Gray FishTag Research, USM Cooperative Sport Fish Tag and Release, GA DNR Cooperative Angler Tagging — growth from tag-vs-recapture lengths, recapture rewards (read via search snippets)
- roughfish.com contest and universal-lifelist pages — rough/micro species lifelist culture (read via search snippet; contest-rules pages returned 404/403)
- https://kestrelmeters.com/blog/how-barometric-pressure-affects-fishing
- https://kordatackle.com/knowledge/how-to-use-a-marker-float-set-up
- https://dnabaits.com/how-to-read-the-water-signs-carp-are-in-your-swim/
- https://www.anglingtimes.co.uk/advice/tips/how-to-read-and-fish-flooded-rivers/
- https://www.anglingtimes.co.uk/advice/tips/how-to-land-every-fish-you-hook/
- https://knots.fish/guides/fishing-knot-strength-chart/
- https://www.frenchcarpandcats.com/how-natural-food-sources-affect-carp-fishing/
- Iowa DNR — Fishing the Thermocline for Better Summer Success
- Angler's Pro Tackle — The Thermocline: The Science And How To Use It To Catch More Fish
- UF/IFAS Fact Sheet FA-27 — Dissolved Oxygen for Fish Production (Francis-Floyd)
- US EPA CADDIS — Dissolved Oxygen
- Angling Times — How wind affects carp fishing
- Haith's UK — Wind and its effects on fishing
- BassResource — Fishing When The Wind Blows
- BassResource — Watch Your Step When Bank Fishing
- Farnham Angling Society — Tench (species page, bubble trails from gill-sifting)
- Maggotdrowners Forums — Fizzing Peg
- Maggotdrowners Forums — Fishing Overdepth
- FishingMagic Forums — Float fishing overdepth
- Angling Times — How and when to use a line clip when fishing
- Angling Times — How to make the perfect cast when feeder fishing (Steve Ringer)
- Angling Times — How to find the best spots to fish on for carp
- Angling Times — How to choose the right hook for carp fishing
- Angling Times — Where fish go when our rivers get cold in winter
- Anglers' Net — The Power Of Prebaiting
- Sticky Baits — A Guide to Pre-Baiting
- DNA Baits — Prebaiting for Carp: Tips to Maximise Your Catch Rates
- Angling Direct — The Complete Beginner's Guide To Fishing Groundbait
- Dynamite Baits — Grant's Top Groundbait guide
- Total Fishing forums — Ground Bait: Inert, or Active, When, Where & Why?
- FishingPellets.com — How Flavours and Attractants Work Underwater
- Carp Austria — Carp Attractants: which scents really attract carp
- Tor Baits — Best Boilie Flavours for Carp Fishing (And Why They Work)
- PMC/NCBI — Effects of temperature on feeding and digestive processes in fish
- SaltStrong — Palomar Knot vs. Uni Knot With Braided Line [Strength Test]
- Shimano — Rod action and power explained
- The Tackle Room — Rod Action Explained: Fast vs Moderate vs Slow
- Anglers' Hut — Choosing Your Shock Leader
- Montana Casting Co. — Fluorocarbon vs. Monofilament in Fly Fishing
- Discover Boating — Freshwater Fishing Hooks
- Master Fishing Mag — Fishing Hook Sizes Explained: Bait & Species Cheat Sheet
- Orvis UK — The Art of Stealth: Approaching Fish Without Being Detected
- FishTalk Magazine — 10 Tips for Stealthy Fishing
- Wired2Fish — How To Read A River: Breaking Down Current For Multi-Species Angling
- My Quest For Barbel — Watercraft: Barbel
- Life and Work — Mastering Current Seams: A Guide for River Fishing Success
- Fisheries.co.uk — Catching barbel on a flooded river
- Kraken Bass — Water Clarity Guide for Choosing Baits (Bass Fishing)
- Douglas Outdoors — Choosing The Best Lure Colors For Every Water Condition
- MagBay Lures — Water Clarity Tips: Choose the Right Lure
- Baitshop.com — Post-Spawn Bass Fishing: How to Target Bass During Recovery Season
- Aquamarine Power — Fish Spawning and How It Affects Fishing
- Premium Carp Fishing — After the Spawn: A Guide to Carp Fishing Tactics
- Bartholomew & Bohnsack — A Review of Catch-and-Release Angling Mortality with Implications for No-take Reserves
- Duluth News Tribune — Catch, release and dead? How and how long you handle fish determines their fate
- Pole Position Tackle — How Conditioned Carp React
- Carpology — Rigs for Pressured Carp
- Carpology — Digging in the dirt: Fishing in silt
- Saltwater Sportsman — Fishing By the Solunar Tables
- Island Fisherman Magazine — How the Moon Affects Fishing: Solunar Fishing Tables
- Brothercarp — Carp Bait: What To Use And How Much
- Barbel Fishing World Forums — Clutch or backwind?
- https://terraria.wiki.gg/wiki/Bait (fetched in full)
- https://dredge.wiki.gg/wiki/Crab_Pot (fetched in full)
- https://mods.vintagestory.at/primitivesurvival (fetched in full)
- https://www.vintagestory.at/blog.html/news/1220-fishing-mechanisms-metalworking-and-more-r441/ (fetched in full)
- https://wiki.vintagestory.at/Bait (fetched in full)
- https://en.wikipedia.org/wiki/Tip-up_(ice_fishing) (fetched in full)
- https://en.wikipedia.org/wiki/Bowfishing (fetched in full)
- https://en.wikipedia.org/wiki/Spearfishing (fetched in full)
- https://en.wikipedia.org/wiki/Fyke_net (fetched in full)
- https://en.wikipedia.org/wiki/Cast_net (fetched in full)
- https://en.wikipedia.org/wiki/Handline_fishing (fetched in full)
- https://en.wikipedia.org/wiki/Tenkara_fishing (fetched in full)
- https://en.wikipedia.org/wiki/Magnet_fishing (fetched in full)
- https://en.wikipedia.org/wiki/Worm_charming (fetched in full)
- https://news.orvis.com/fly-fishing/video-pro-tips-the-basics-of-mending-to-achieve-a-drag-free-drift (fetched in full)
- https://store.steampowered.com/app/393430/Ice_Lakes/ (fetched in full)
- https://store.steampowered.com/app/660560/Fishing_on_the_Fly/ (fetched in full)
- https://thejighead.com/2021/04/15/the-4-best-fly-fishing-video-games/ (fetched in full)
- Stardew Valley Wiki - Crab Pot, stardewvalleywiki.com/Crab_Pot (search snippet only; direct fetch returned 403 and the Fandom mirror 402)
- Moonglow Bay Wiki - Fishing, moonglow-bay.fandom.com/wiki/Fishing (search snippet)
- Dovetail Live - 'Fishing Terms Explained', Fishing Sim World: pegs, keepnets, three rods on a static peg (search snippet)
- Russian Fishing 4 - Steam patch notes 29.11.2019: Marker Rod added to determine bottom texture and depth (search snippet)
- Steam Community - 'Landing Net, how to use?', Russian Fishing 4: press SPACE to net the fish (search snippet)
- Steam Community guides 745479025 'Ice Fishing Equipment' and 667702621 'Visual Guide: Basics of Ice Fishing' for Ice Lakes (search snippets; direct fetch returned 429)
- PC Gamer - 'Realism be damned: Ultimate Fishing Simulator's underwater camera is great' (search snippet)
- gamepressure.com - 'Dave the Diver: How to use Tranquilizer and Net Gun' (search snippet)
- TheXboxHub - 'Freediving Hunter: Spearfishing The World' review, breath countdown and blackout (search snippet)
- FishUSA - 'Mastering Crappie Fishing at Night' and Bass Pro 1Source - 'Crappie Fishing After Dark': green submersible lights, 15-minute bait cloud, fish the edge of the light (search snippets)
- AnglingActive - 'Pike Fishing: Bite Indication' and Angling Times - 'How to avoid deep hooking pike while deadbaiting' (search snippets)
- Realtree - 'Bowfishing Shooting Tips' and Bass Pro 1Source - 'Bowfishing Basics': refraction aiming rules (search snippets)
- Wikipedia - Okie Noodling, plus Outside Online - 'Noodling for Catfish Is Now a Tourist Activity' (search snippets)
- Steam - Magnet Fishing Simulator store page: magnet tiers, clean/repair/sell, deeper waters (search snippet)
- CurseForge listings - Fisherman's Trap, Fish Traps, Giacomo's Fishing Net, Simple Fishing Nets, Fish Traps Reborn (existing Minecraft passive-fishing precedent and placement rules) (search snippets)
- Maine Sea Grant - Maine Seafood Guide, Vessel and Gear Guide: longlines, gillnets, lobster pots (search snippet)
- Steam Community - Call of the Wild: The Angler discussions on fly fishing and reel tension, plus the Gaming Nexus review (search snippets)
- Tempest.earth, AcuRite and The Fisherman on barometric pressure and fishing, plus the Fishing Planet Steam forum thread 'Barometric Pressure' (search snippets)
- https://modrinth.com/mod/aquaculture
- https://modrinth.com/mod/advanced-fishing
- https://modrinth.com/mod/tide
- https://api.modrinth.com/v2/project/tide/version
- https://github.com/Lightning-64/Tide-2/blob/main/README.md
- https://github.com/Lightning-64/Tide-2/issues?q=is%3Aissue
- https://lightning-64.github.io/tide-wiki/
- https://lightning-64.github.io/tide-wiki/items/hooks/
- https://modrinth.com/mod/starcatcher
- https://api.modrinth.com/v2/project/starcatcher/version
- https://github.com/wdiscute/starcatcher
- https://github.com/wdiscute/starcatcher/issues?q=is%3Aissue
- https://www.minecraft-guides.com/mod/starcatcher/
- https://www.mc-mod.net/skill-based-fishing-overhaul-with-100-fish-mod/
- https://rlcraft.wiki.gg/wiki/Fishing_Made_Better
- https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/2934749-fishing-made-better-a-mod-that-adds-a-new-complex
- https://modrinth.com/mod/angling
- https://modrinth.com/mod/actual-fishing
- https://modrinth.com/mod/always-a-bigger-fish
- https://modrinth.com/mod/reel
- https://modrinth.com/mod/treasure-seas
- https://modrinth.com/mod/fishing-upgrade
- https://modrinth.com/mod/fishing+
- https://modrinth.com/mod/fishing
- https://modrinth.com/datapack/fishing-frontier
- https://modrinth.com/mod/fishes-undead-rise-ye
- https://modrinth.com/mod/cthulhu-fishing
- https://modrinth.com/mod/aquamirae
- https://modrinth.com/mod/hybrid-aquatic
- https://modrinth.com/mod/fishermans-haven
- https://modrinth.com/mod/oceanic
- https://api.modrinth.com/v2/search?query=fishing&limit=40&index=downloads
- https://api.modrinth.com/v2/project/DMDVFZSF
- https://api.modrinth.com/v2/project/v0j2ftEp
- https://api.modrinth.com/v2/project/fishingparadise
- https://api.modrinth.com/v2/project/additional-fishing
- https://api.modrinth.com/v2/project/tactical-fishing
- https://api.modrinth.com/v2/project/FpghCeHO
- https://api.modrinth.com/v2/project/Rw1Ylmn4
- https://github.com/BrownBear85/stardewfishing/issues?q=is%3Aissue
- https://github.com/TeamMetallurgy/Aquaculture/issues
- https://github.com/TeamMetallurgy/Aquaculture/issues?q=is%3Aissue+is%3Aclosed+sort%3Acomments-desc
- https://www.minecraft-guides.com/mod/aquaculture-2/
- https://www.curseforge.com/minecraft/mc-mods/aquaculture
- https://www.curseforge.com/minecraft/mc-mods/create-fishery-industry
- https://www.curseforge.com/minecraft/mc-mods/giacomos-fishing-net
- https://www.planetminecraft.com/mod/angler-s-desire/
- https://www.9minecraft.net/fish-of-thieves-mod/wiki/
- https://www.curseforge.com/minecraft/mc-mods/fish-traps
- common/src/main/java/com/riverfishing/fishing/FeedZoneData.java
- common/src/main/java/com/riverfishing/item/WaterProbeItem.java
- common/src/main/java/com/riverfishing/fishing/FishingManager.java (analyzeWater, rollFish, frenzy, groundbait)
- common/src/main/java/com/riverfishing/engine/BiteEngine.java (skill-gate, feedBonus)
- common/src/main/java/com/riverfishing/item/RodData.java (setDepth)
- common/src/main/java/com/riverfishing/client/RodAssemblyScreen.java (float-depth slider)
- common/src/main/java/com/riverfishing/fishing/JournalData.java
- common/src/main/java/com/riverfishing/fishing/FishingPressureData.java
- common/src/main/java/com/riverfishing/rig/RigData.java
- common/src/main/java/com/riverfishing/config/RiverFishingConfig.java
- C:\Users\Qwazar\VS Code Projects\fishing mod\common\src\main\java\com\riverfishing\fishing\JournalData.java
- C:\Users\Qwazar\VS Code Projects\fishing mod\common\src\main\java\com\riverfishing\fishing\FishingManager.java (weight/length roll, lines 2140-2175; landing hooks, lines 1780-1870)
- C:\Users\Qwazar\VS Code Projects\fishing mod\common\src\main\java\com\riverfishing\item\FishItem.java (NBT fields: weight, length, legal, trophy)
- C:\Users\Qwazar\VS Code Projects\fishing mod\common\src\main\java\com\riverfishing\component\TackleCompat.java (line/reel compatibility, for the line-class bucketing check)
- C:\Users\Qwazar\VS Code Projects\fishing mod\common\src\main\java\com\riverfishing\item\WaterProbeItem.java
- C:\Users\Qwazar\VS Code Projects\fishing mod\common\src\main\java\com\riverfishing\engine\BarometricPressure.java
- C:\Users\Qwazar\VS Code Projects\fishing mod\common\src\main\java\com\riverfishing\fishing\FeedZoneData.java
- C:\Users\Qwazar\VS Code Projects\fishing mod\common\src\main\java\com\riverfishing\fishing\FishingManager.java (analyzeWater ~2503-2620, fight tick ~1595-1704, landFish ~1776-1930)
- C:\Users\Qwazar\VS Code Projects\fishing mod\common\src\main\java\com\riverfishing\fishing\FishingPressureData.java
- C:\Users\Qwazar\VS Code Projects\fishing mod\common\src\main\java\com\riverfishing\engine\BiteContext.java
- C:\Users\Qwazar\VS Code Projects\fishing mod\common\src\main\java\com\riverfishing\item\FishItem.java (header)
- repo grep: turbidity|clarity|moon|roe|caviar|freshness|spoil|lure_family|legal across common/src/main
