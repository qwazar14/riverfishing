# River Fishing

A river-fishing **simulator** for Minecraft **1.20.1 / Forge 47.x**, built from the game-design
document in this repo. Fishing is a *process*, not a click on the water: you assemble a rod from a
blank + reel + line + rig + hook, match bait and groundbait to the fish you want, feed your spot,
cast, and wait for the bite. Success = how well the whole setup matches a given fish under the
current conditions.

> **Status: v0.1 — Stage 1 (frame) + the core of Stage 2 (bite engine).** The architectural spine
> and the full catch loop work end to end. Advanced systems (fight mini-game, gear wear, signalers,
> villager, journal, cooking) are scaffolded for later stages — see the roadmap below.

---

## Building

This project needs a **JDK 17**. If you don't have a system JDK, PrismLauncher's bundled
`java-runtime-gamma` is a full JDK 17 and works:

```sh
# from the project root, in PowerShell:
$env:JAVA_HOME = "C:\Users\<you>\AppData\Roaming\PrismLauncher\java\java-runtime-gamma"
.\gradlew.bat build
```

The toolchain path is also pinned in `gradle.properties` (`org.gradle.java.installations.paths`),
so IDEs that respect it will find the JDK automatically. The built mod jar lands in
`build/libs/riverfishing-0.1.0.jar`.

Useful tasks: `.\gradlew.bat compileJava`, `.\gradlew.bat runClient` (launches a dev client).

---

## How to play (the v0.1 loop)

1. **Get tackle.** Dig dirt/grass with a shovel for **worms** (~10%). Craft a **Stick Rod**
   (3 sticks, diagonal), a **Hook** (2 iron nuggets → 2), a **Primitive Rig** (string + hook),
   and a **thick mono line** (2 string + slime ball). More refined gear comes from crafts/the
   fisherman trader in later stages.
2. **Assemble the rod.** Hold the rod and **sneak + right-click** to open the assembly GUI. Drop a
   reel (if the rod takes one), line, rig and hook into the four slots. The rod stores them in NBT.
3. **Bait the hook.** Hold bait in your **off-hand** (e.g. a worm). Predators only take artificial
   lures (spinner/spoon/wobbler/soft-plastic).
4. **(Optional) Feed the spot.** Right-click water while holding **groundbait** to create a 3×3 fed
   zone. Freshness decays over ~10 minutes; re-feed to top it up. The groundbait type decides which
   fish the spot pulls in.
5. **Cast.** Right-click water with the assembled rod. The bite engine computes an expected
   time-to-bite from your whole setup and the conditions.
6. **Strike.** When you see **"Bite! Strike!"**, right-click again within the window to set the hook.
   Miss the window and it gets away.
7. **Fight it (§7).** A boss bar shows the fish. **Tap right-click to reel** (raises the bar +
   tension); **wait to give line** (tension relaxes). When the bar turns **red the fish is running** —
   reeling then spikes tension, and if it exceeds what your line + drag can take for that fish, the
   line **snaps** and you lose the rig. Land it (bar full) and you get a unique item carrying species,
   weight and length. Pike & zander **bite through a leaderless line** on the strike — fit a predator
   rig (steel leader).

---

## How the bite engine works (GDD §1)

For every fish that could be present, the engine computes:

```
W = base · M · E · G
T = T_min / Σ W        (expected time to bite; actual time is exponential around T)
```

- **M — match coefficient (§1.1):** weighted sub-scores for bait (0.30), groundbait (0.15),
  rig (0.13), rod (0.12), line (0.12), hook (0.10), reel (0.08). Categorical pieces score 1.0/partial/0;
  graded pieces (line diameter, hook & reel size) fall off from the ideal. A hard filter kills the
  weight if the bait is badly wrong.
- **E — environment (§1.2):** product of water-body, season, time-of-day, weather, biome and
  distance factors. A 0 anywhere (e.g. catfish in a puddle) means the fish isn't there.
- **G — fed-spot bonus (§1.3):** 1.0–2.0 from the zone's freshness.

When a bite fires, the species is drawn weighted by each fish's `W`.

Everything is **data-driven**: fish live in `data/riverfishing/fish_profiles/*.json` (schema in
GDD §13). Edit a profile and `/reload` to re-balance with no rebuild.

---

## Architecture map

| Area | Package / file | GDD |
|------|----------------|-----|
| Mod bootstrap, registries | `RiverFishing`, `registry/Mod*` | §16 |
| Tackle types | `component/{RodType,LineType,RigType,ComponentSlot}` | §3 |
| Items | `item/{RodItem,ReelItem,LineItem,RigItem,HookItem,BaitItem,GroundbaitItem,FishItem}` | §3, §2.1 |
| Rod NBT | `item/RodData` | §3.1, §12 |
| Fish profiles | `fish/{FishProfile,FishProfileManager}` | §13 |
| Bite engine | `engine/{BiteEngine,BiteContext,Season,TimeOfDay,Weather}` | §1 |
| Water detection | `water/{WaterBodyDetector,WaterBodyCache,WaterBody,WaterType,ModBiomeTags}` | §10.2, §4.1, §12 |
| Fed spots | `fishing/FeedZoneData` | §5 |
| Fishing loop | `fishing/{FishingManager,FishingSession}` | §4, §7 |
| Assembly GUI | `menu/RodAssemblyMenu`, `client/RodAssemblyScreen` | §3.1, §12 |
| Serene Seasons (soft) | `integration/SeasonProvider` | §10.1 |
| Events, drops | `event/ModEvents` | §3.6, §9.6 |

`tools/generate_assets.ps1` regenerates the placeholder textures, item models and en/ru lang files.
(Run it from PowerShell; the script is saved UTF-8 **with BOM** so PS 5.1 parses the Cyrillic.)

---

## Roadmap status (GDD §16)

- **Stage 1 — frame:** ✅ items, assembly GUI + NBT, base crafts.
- **Stage 2 — bite core:** ✅ JSON-driven engine, water detection (cached + bounded), casting,
  bite → catch with weight/length. *(Cast slider mini-game is still a simple click; placeholder.)*
- **Stage 3 — simulator depth:** ✅ fed spots, fight mini-game (tension boss bar + runs),
  line breaking-strain + drag → real line breaks, leader bite-off for pike/zander, rig lost on break.
  ⏳ remaining: gear wear (line/hook), snags near shore, foul-hooking.
- **Stage 4 — surroundings:** ⏳ rod-pods, bite alarms, keepnets, cooking → stackable fillets.
- **Stage 5 — integrations:** ⏳ Serene Seasons reads season (soft dep done); BoP swamp tag shipped;
  Farmer's Delight recipes pending.
- **Stage 6 — content & balance:** ⏳ fisherman villager, journal/records, difficulty presets.

Soft deps declared in `mods.toml`: `sereneseasons`, `biomesoplenty`, `farmersdelight`. The mod runs
without any of them.
