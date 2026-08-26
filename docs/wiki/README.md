# River Fishing — Wiki

**River Fishing** turns fishing into a process instead of a click on the water. You assemble a rod from a blank, a reel, a line and a rig; you match bait, hook size, groundbait and leader to the fish you actually want; you read the water, the season, the hour and the barometer; and then you fight what takes.

Nothing bites by luck. A data-driven bite engine weighs your whole setup against every one of **79 species** under the current conditions, and decides both *what* takes and *how long you wait*. Mismatched gear catches nothing, a line that is too visible spooks small wary fish, big fish demand a near-perfect kit, and every water in the world holds its own species community fixed by the world seed.

- **Version 0.7.0**, on four Minecraft versions:
  - **1.20.1** — Fabric & Forge
  - **1.21.1** — Fabric & NeoForge
  - **26.1.2** — Fabric & NeoForge
  - **26.2** — Fabric & NeoForge
- **Required:** [Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api); on Fabric also Fabric API
- **Recommended:** Serene Seasons (unlocks the seasonal bite), Biomes O' Plenty (richer habitat model)
- **Also integrates with:** Farmer's Delight, Jade, JEI
- **Discord:** [discord.gg/Kk2nKvsuRh](https://discord.gg/Kk2nKvsuRh) — questions, bug reports, catches
- **This wiki also in:** [русском](ru/README.md) · [українською](uk/README.md)
- **Online, all three languages in one page:** [qwazar14.github.io/riverfishing](https://qwazar14.github.io/riverfishing/)

---

## Start here

| Page | What's in it |
|---|---|
| **[Getting started](getting-started.md)** | Bait, your first rod, your first cast, your first fish, and how to read the HUD |

## Gear

| Page | What's in it |
|---|---|
| [Rods](rods.md) | All 13 blanks — reel bands, cast-weight windows, reach, built-in rigs, durability, repair, recipes |
| [Reels and lines](reels-and-lines.md) | 11 reels and the spool-diameter rule, 23 lines with breaking strains, line visibility and wear, the three leaders |
| [Rigs and baits](rigs-and-baits.md) | 11 rigs and their slot layouts, 9 hooks, the float and its depth slider, 13 natural baits, 8 lures, 4 groundbaits |
| [Tackle Station](tackle-station.md) | The 0.6.0 bench: forms, the weight stepper, costs, dyeing, and which knobs do nothing yet |
| [Crafting](crafting.md) | Every recipe in the mod, in one place |
| [Tools and processing](tools.md) | Journal, fish finder, knife, whetstone, auger, alarms, fillets, live bait, pack integrations |
| [Keepnet](keepnet.md) | Four sizes, and why a fish takes up room in the shape it actually is |
| [Tackle box](tackle-box.md) | 9 to 36 tackle-only slots, named and dyed, plus the fisherman's four ready-made kits |
| [Blocks](blocks.md) | Rod pods, bait trap, worm and maggot farms, aquariums, the ice hole, bait crops |

## Playing

| Page | What's in it |
|---|---|
| [Fishing mechanics](fishing-mechanics.md) | The bite engine, casting, the three flows, hook-set timing, the fight, line breaks, snags, foul-hooking, gear wear, difficulty |
| [Water and conditions](water-and-conditions.md) | Water bodies, depth and width, biome groups, time, weather, seasons, barometric pressure, frenzies, depletion, communities |
| [Fish in the water](shoal.md) | The shoals you can see before you cast: how many are shown, how clearly, and how they react to you |
| [Ice fishing](ice-fishing.md) | Drilling, jigging, what bites under the ice |
| [Sea fishing](sea-fishing.md) | The saltwater tier, ocean zones, trolling, big-game fights |
| [Stocking](stocking.md) | Releasing fish, the residency model, settling, stock levels |

## Reference

| Page | What's in it |
|---|---|
| [Species](species.md) | All 79 species: sizes, home waters, level gates, best baits, ideal tackle, plus notes on the unusual ones |
| [Species reference](species-reference.md) | Habitat gates, season / time / weather tables, fight statistics |
| [Progression](progression.md) | Angler XP, levels and ranks, the skill tree, the 8-stage quest chain, all 22 advancements, the journal |
| [Villager](villager.md) | The Fisherman, the Fishing Stall, all five trade tiers, the prime-fish rule, the dynamic market |
| [Order board](order-board.md) | The journal panel that turns the order of the day into a recipe, and the six rewards it pays out |
| [Configuration](config.md) | `config/riverfishing.json` — the three presets and every knob, with its range |
| [Electrofisher](electrofisher.md) | Creative only: taking a species out of a body of water for good |

---

## The short version

1. **Assemble a rod.** Blank + (reel) + line + rig. Sneak + right-click a rod to open it. On reeled blanks the reel goes in **before** the line.
2. **Load the rig.** Hook and bait, or a leader and a lure. Predators only take artificial lures; peaceful fish only take natural bait.
3. **Match the fish, not the water.** Every species has ideal rods, rigs, hook sizes, line and baits. The bigger the fish, the more of that list has to be right.
4. **Cast on the bar.** Hold to charge, release at the peak.
5. **Watch, don't listen.** There is no bite sound and no bite text — the float plunges.
6. **Fight with the drag.** Crank when it's calm, ease off when it runs, crouch to open the drag when it jumps or dives.
7. **Release your trophies.** [Stocking](stocking.md) is how a water stays rich.

## Three flows, three games

| Flow | Blanks | The game |
|---|---|---|
| **Float** | Stick, Bamboo, Pole, Winter | Spot the plunge, then win the pull-out timing |
| **Bottom** | Feeder, Bottom, Carp, Surf, Boat | Long casts, long forgiving waits, rod pods and alarms |
| **Active** | Ultralight, Spinning, Sea spinning, Trolling | Work the lure by hand; cadence *is* the mechanic |

## Operator commands

| Command | Effect |
|---|---|
| `/rffish unlockall` | Fill the journal so every quest goal is met |
| `/rffish reset` | Wipe your records, XP, skills and quests |

Both require permission level 2. See [Progression](progression.md#operator-commands).

---

## Reading this offline

These pages are plain Markdown — GitHub and any editor render them as they are, and the species
table carries its sprites inline.

There is also a **single-page build** with everything in one file: a sidebar, a filter box, and every
crafting recipe drawn as a real 3×3 grid rather than written out as a pattern string. Build it from
the repo root:

```
python tools/gen_wiki_bundle.py --out build/wiki.html
python tools/gen_wiki_bundle.py --out build/wiki.html --mc-jar <a Minecraft client jar>
```

The grids are generated from the recipe JSON, so they cannot drift from what the game loads. Passing
`--mc-jar` makes vanilla ingredients show their real icons, read out of that jar at build time;
without it they render as labelled colour tiles and everything still works. Nothing from the jar is
stored in this repository — Mojang's art is not ours to redistribute — which is why the built page is
not committed to a source branch. The published copy lives on the generated `gh-pages` branch, put
there by `tools/publish_pages.py`.

## For pack makers

Every species is one JSON file at `data/riverfishing/fish_profiles/<species>.json`, and **all of it is datapack-overridable** — where a fish lives, what it eats, how it fights, how rare it is, what level it needs. The bite engine, the journal pages, the JEI entries and the villager gating all read the same file, so your changes stay consistent everywhere.

The annotated schema lives in [`docs/FISH_PROFILES.md`](../FISH_PROFILES.md).
