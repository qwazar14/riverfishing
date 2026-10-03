# River Fishing 1.1.0 — beta test

Thanks for helping test 1.1.0. This build changes how fishing works at its core, so what matters most is
**balance**: did catching get harder or easier than in 1.0.0, and does it still feel fair and fun? After that,
the **new screens** (journal, fish finder, fish card, aquarium) and the new rod and line animations.

Everything below is compared to **1.0.0**, the last release.

---

## Before you start

- **Back up your world.** 1.1.0 converts old data the first time it loads (details below), and a beta can have bugs.
- **Main build: Minecraft 26.3** (Fabric or NeoForge) — it has everything first. Builds for 26.2, 1.21.1 and
  1.20.1 carry the same content.
- Needs **Architectury API** (and **Fabric API** on Fabric). Serene Seasons is optional and supported.
- **What an old world converts on load:**
  - private ponds keep every fish they had;
  - corn, pea and barley seeds turn into corn, peas and pearl barley (there are no seeds any more);
  - an old "fry: perch, 10" item turns into 10 stackable fry;
  - corn that was already ripe needs one more growth stage.

---

## What to test first: balance

The old bite engine asked "which fish would bite here?" every time you cast. Now every water holds **real
shoals and single trophy fish** that live on their own — they get hungry, move with the hour and the season,
eat, learn baits, spawn, age and get eaten, whether you are there or not. The bite comes from the fish that are
actually near your bait. This is the change we most need your eyes on.

**Please tell us, compared to 1.0.0:**

- **Time to the first bite** in a fresh spot — river, lake, pond, sea; dawn, day, dusk, night; different seasons.
  Is it too long anywhere? Is some place always dead?
- **Fish per hour** once you have found fish, and their **sizes**. How often do trophies come?
- **Finding fish:** the fish finder now shows the fish that are really there. Can you find a shoal? Is the
  "why no bite" hint useful, or wrong?
- **Groundbait:** a fed spot pulls fish from the zones around it, but a shoal that has eaten its fill now bites
  less — **overfeeding can hurt**. Does it feel right?
- **Prebaiting:** a week of feeding one bait teaches a shoal to come back to it. Noticeable?
- **Fishing pressure:** a landed fish is gone from its shoal; a lost or released one makes its shoal warier for a
  couple of days. A hammered spot thins out and recovers slowly (strays arrive about every ten days, so a water
  can't be fished completely dead). Too harsh? Too soft?
- **Levels:** every species has a recommended angler level from 0 to 50. Below it the fish bites less often (never
  less than 15 %), it is never forbidden. Does progression feel right?
- **Boilies:** the right boilie for the conditions is worth about six of the wrong one. Can you feel the difference?
  Is the builder in the journal enough to find a good one?
- **The fight:** every fight pattern now has a signature move, at most twice a fight — zig-zag, into the weeds,
  torpedo, sulk, charge, tail-walk, plank. The fish starts where it took the bait and fights near the bottom until
  it tires. Too hard, too easy, unclear what to press? How many fish do you lose, and why (line break, hook pulled,
  time ran out)?
- **Private ponds:** roe hatches in 3 days, fry grow into young fish in about 16 days, and a young fish takes a long
  time to mature (a well-fed carp about a game year and a quarter). A pond's size sets how many fish it can hold, and
  it only grows on what you feed it. Does a pond feel alive, or stuck?
- **Money:** fisherman prices, prime fish, contracts, the order of the day (now 2.5× the market price). A net used in
  your own pond now gives trophies and prime fish by weight, like the rod. Is anything too profitable or not worth it?
- **Bait crops:** corn, peas and barley no longer come from grass — villages grow them and the fisherman sells them.
  Is bait too hard to get early on?

**Numbers help most.** For balance reports please add where (water type, biome, depth), when (time of day, season,
weather), what (rod, rig, bait, groundbait), minutes to the first bite, and how many fish and what sizes per session.

---

## Then: the new screens

Please check each one at your usual GUI scale and screen size, and report anything cut off, overlapping, unreadable
or confusing.

- **Journal** — four bookmarks (Fish, Gear, Angler, Guide) instead of eight tabs; a species page in three sheets;
  a boilie page with a flavour table, a boilie builder that shows the bite factor the game really uses, and the
  recipe drawn as items.
- **Fish finder** — redrawn as a device with three views (Section, Bed map, Water sample). The small sonar in the
  corner shows the fish that are really there. New: three keys at the bottom of the finder's screen switch the
  corner sonar and the direction dial on or off, and **Move** lets you drag both anywhere.
- **Fish card** — a new catch record: big weight (pounds too), the fish's place on a scale of its kind, length on a
  measuring board (inches too), stamps (TROPHY, PRIME, NET…), region and water ("Palearctic · river"). Hold Shift for
  conditions and genes.
- **Aquarium** — a new window: sex rings on the fish, the breeding pair joined by a line, the breeding run as five
  steps, an incubator for roe. **Fry now stack**: one item is one fry.
- **Tackle Station** — a new workbench look.
- **Contracts** — a paper order form.
- **Rod pod** — an empty hand takes **the rod you aim at** (the rod it would take lifts a little), a rod with a fish
  on it always comes first; you dock a rod or an alarm on the rest you click by.

---

## Also worth a look

- **Rod and line animations:** the rod swings up when you pick it up, the reel turns when you wind, the tip flicks on
  a retrieve, a take slams the tip down, the tip beats with the fish's tail, a jump slackens the line, a snapped line
  falls off the tip. On the rod pod the rods bend on a take and the alarms flash or swing. The rod no longer dips
  down and up on every click. If the rod ever moves the wrong way up or down, type `/rfrod anim flip`;
  `/rfrod anim off` switches the new motion off so you can compare.
- **The world:** snags on river beds (cover for fish), reeds and cattails by the water (they stand in water one
  block deep, and only shears gather them), bait crops in village farms, corn two blocks tall.
- **33 new species** (295 in all). Are they where they should be, do the names, pictures and descriptions look right?
  - Fresh water: Amur false gudgeon, Amur grayling, Amur pike, Arctic grayling, Bighead carp, Black carp, Comet
    goldfish, Common minnow, Eastern bream, Golden trout, Humpback crucian carp, Humpback gibel carp, Lake minnow,
    Lena sturgeon, Muksun, Redfin pickerel, Sevan trout, Siberian roach.
  - Rivers and the sea: Caspian roach, Masu salmon, Russian sturgeon, Taran.
  - The sea: Albacore, Atlantic saury, Atlantic wolffish, Bigeye tuna, Common two-banded seabream, Cusk (tusk),
    European hake, Goblin shark, Greenland shark, Haddock, Opah.

---

## What's new since 1.0.0 — the short list

- **Living water:** real shoals and trophy fish in every water; groundbait moves fish, overfeeding hurts, fish learn
  baits, catches really thin a shoal. Water you are away from catches up when you return.
- **The wild balances itself:** old age, starvation, predators and anglers take fish; only spawning adds new ones.
- **Private ponds rebuilt:** every fish is a record of its own — release a trophy and catch that same fish again; real
  spawning (roe, fry, young fish); room set by the water's volume; a hopper can feed the feeding station.
- **A cast is never refused**; predators strike on reflex.
- **Boilies:** paste boiled in a cauldron over a campfire, 14 flavours, sinker / wafter / pop-up / snowman, 10-24 mm,
  dips, prebaiting; two flavours show as two colours.
- **The fight:** signature moves per fight pattern, the fish at the depth it was hooked, a lift-out to your hands at
  the bank; "into the weeds" only where there is weed, reeds or snags.
- **Journal, fish finder, fish card, aquarium, Tackle Station, contracts:** all redesigned.
- **33 new species.**
- **Plants and crops:** reeds, cattails, snags, 3D crops, no seeds (plant the crop itself), corn cobs and pea pods.
- **Competition commands** for operators: `/rffish spawn`, `/rffish clear`, `/rffish census`.
- **Many fixes:** two item duplications closed, a rare crash with Sodium/Embeddium near rivers, shaders no longer
  throw the line around, keepnets and tackle boxes keep their contents when upgraded, and more.

The full patch notes list every change.

---

## Known limitations

- Ice fishing still uses the old bite engine; it will be rebuilt separately.
- The 33 new species have no cooked picture yet — a cooked one shows the raw fish icon.
- On 1.21.1 and 1.20.1 the fisherman does not buy the species added in the last two waves yet.
- Some of this build is very fresh: the rod animations, the rod pod aiming and the new species have had the least
  play so far. Rough edges are expected — tell us about them.

---

## How to report

- **Version and loader** (for example "26.3 NeoForge"), and your other mods — especially shaders, Sodium, Iris,
  Embeddium.
- **What you did, what you expected, what happened.** A screenshot or a short video helps a lot.
- **For a crash:** the crash report or `logs/latest.log`.
- **For balance:** the numbers listed above.

Useful commands: `/rffish hints on|off` (the "why no bite" lines), `/rfrod anim` (animation state),
`/rffish census` (operators: what swims in the water around you).
