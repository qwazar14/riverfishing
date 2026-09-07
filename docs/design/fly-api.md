# §fly — contracts between the three build streams (0.10, phase 1)

Read `docs/design/fly-fishing.md` first: it is the design. This file is the split. Three streams build at
once in the main tree (1.21.1 dialect); the integrator applies every patch script in order A → B → C,
merges lang, builds all three trees and ports. **Nobody runs gradle. Nobody edits an existing file by
hand.** The decisions that changed since the design: the cast is a RHYTHM (§B below), the fly rod
takes the small reels (1000–2000) as its fly reel, there is no fly-line item in phase 1, and a fly is a
`TiedLureItem` from the Tie page — no new fly items.

## Rules (the same as the breeding build)
- New classes go in NEW files. Edits to EXISTING files go through an idempotent python patch script
  `tools/patches/p_fly_<a|b|c>.py <repo root>` using anchor replacement: `sub1(old, new)` asserts the
  anchor occurs exactly once, prints the missing anchor and exits 1 otherwise, and a `§fly` marker in
  every inserted block makes a rerun a no-op (skip the edit when the marker is already present).
  Write the script with the Write tool (heredocs mangle backslashes). Run it once yourself with
  `py -X utf8` to prove the anchors match; do not run gradle.
- Lang keys: never edit the lang JSONs. Write `tools/patches/lang_fly_<x>.json` as
  `{"en_us": {...}, "ru_ru": {...}, "uk_ua": {...}}` with identical key sets, real translations.
- Comments explain WHY; `ponytail:` marks a known ceiling. Java 21, no new dependencies.
- Each stream leaves ONE runnable check `tools/check_fly_<x>.py` (no framework) that greps its own
  anchors and asserts its rules; it must pass after the patch is applied.
- Anchors below are quoted from the current files; use them verbatim. When two streams insert after
  the same anchor, each uses `sub1(anchor, anchor + own_block)` and never asserts on the other's text.

## Stream A — tackle (`tools/patches/p_fly_a.py`)
Owns: `component/RodType.java`, `component/RigType.java`, `rig/RigLayout.java`, `menu/RigMenu.java`,
`registry/ModItems.java`, `item/RodItem.java` (tooltip only), `client/RodModelLayers.java`,
`client/RodItemRenderer.java` (model-key alias only), recipe + models + lang.

1. `RodType.FLY("fly", 9, true, 1000, 2000, 0, 0, false)` after `TROLLING`. `rodClass()` → `RodClass.FLOAT`
   (the wait-for-the-bite flow with the strike QTE; B and C special-case FLY inside it). `nativeRig()` →
   `RigType.FLY`. Add `public String modelKey()` returning `"ultralight"` for FLY and `jsonKey` for the
   rest; switch `RodModelLayers` and `RodItemRenderer` lookups that are keyed by `jsonKey()` for the
   3D blank to `modelKey()` (the fly rod borrows the ultralight blank in phase 1; `ponytail:` note).
   `isValidRepairItem`: fly repairs with iron (falls through already).
2. `RigType.FLY("fly", 2, 1, true)`; `RigLayout`: `case FLY -> new SlotRole[]{LEADER, LURE}`.
   `RigMenu.RoleSlot.mayPlace`: on a FLY rig the LURE slot accepts ONLY `TiedLureItem`; the leader slot is
   unchanged (the tippet). Register the rig item `rig_fly` the way the other native rigs are registered
   (find the loop/list in ModItems; `RodData.ensureNativeRig` looks up `rig_` + jsonKey).
3. `fly_rod` item: `new RodItem(RodType.FLY, ...)` beside the other rods with a durability like the
   ultralight's; recipe `data/riverfishing/recipe/fly_rod.json` = bamboo ×3 diagonal + string + iron
   nugget (cheaper than a spinning rod, more than a stick); item model `models/item/fly_rod.json` = a copy
   of `ultralight_rod.json` (same particle texture). Lang: `item.riverfishing.fly_rod`,
   `item.riverfishing.rig_fly`, and `tooltip.riverfishing.rod_class.fly` — add a `RodItem.appendHoverText`
   branch: for FLY show that key instead of the FLOAT class line (anchor: the `if (rodType != RodType.WINTER)`
   tooltip block).
4. `FishingManager`: the `ctx.reelSize == 0 && castDistance > 6.0` pole gate is fine (the fly rod has a
   reel). Nothing else.
5. Check `tools/check_fly_a.py`: FLY present in RodType/RigType/RigLayout, `modelKey` used by the two
   client files, rig item + rod item registered, recipe and model files exist.

## Stream B — the rhythm cast and the drift (`tools/patches/p_fly_b.py`)
Owns NEW: `fishing/FlyCast.java`, `network/FlyBeatPacket.java`, `network/FlyCastPacket.java`,
`client/FlyCastClient.java`. Patches: `item/RodItem.java` (use → begin), `fishing/FishingManager.java`
(chargedCast, handleRodUse, tick), `fishing/FishingSession.java` (fields), `network/ModNetwork.java`
(receivers), `client/ClientHud.java` (render), `client/ClientInit.java` (tick).

**The rhythm.** Holding use on a fly rod with no line out starts the cast: the rod goes back and a
needle sweeps a bar (triangle wave, `FishingManager.marker(elapsed, period)` — the float QTE's wave;
copy the 4-line function into FlyCast, it is private). Period by rod: 18 ticks. The **beats** are the
two ends of the sweep. The player taps **sneak** on each end (the stop of the backcast, the stop of the
forward cast): a tap with the needle within `zoneHalf = 0.16` of an end that has not been tapped yet
this sweep = a good beat (+1 false cast); a tap elsewhere or on the same end twice = a bad beat.
**Releasing use** (the delivery) is judged the same way against the nearest end.
Distance = `castRangeBase(FLY)` + 1.0 block per good beat, capped at `castRangeMax(rod)`; a bad beat
at any point resets the beat count to 0 and marks the loop open. Delivery quality: release on a beat
and no open loop = **tight** (full distance, no penalty); released off the beat or open loop =
**splash** (distance ×0.6, `SpookData` stamped at the landing spot — find its add/stamp method — so the
fish there are spooked for ~5 s); released more than 0.35 off an end = **wind knot** (splash + line wear
`addLineWear(rod, 6)`; the private helper is in FishingManager — call it from a patched
`FishingManager.flyLanded(...)` you add, not from FlyCast).

Server state: `FlyCast` keeps a `Map<UUID, State>` (start tick, period, beats, open loop, last tapped
end). API:
```java
package com.riverfishing.fishing;
public final class FlyCast {
    public static void begin(ServerPlayer sp, long now);            // sends FlyCastPacket(active=true)
    public static void beat(ServerPlayer sp, long now);             // from FlyBeatPacket
    /** Ends the cast: returns power 0..1 for chargedCast (distance / castRangeMax) and remembers quality. */
    public static float release(ServerPlayer sp, ItemStack rod, long now);
    /** 0 tight, 1 splash, 2 wind knot — read once after startCast succeeded, then cleared. */
    public static int takeQuality(ServerPlayer sp);
    public static void cancel(ServerPlayer sp);                     // sends active=false
}
```
Hooks: `RodItem.use` — before the final `player.startUsingItem(hand); return InteractionResultHolder.consume(rod);`
(the no-session charge; anchor is that pair followed by `    }\n\n    /**\n     * Anvil repair`) insert
`if (!level.isClientSide && rodType == RodType.FLY && player instanceof ServerPlayer sp) FishingManager.flyCastBegin(sp);`
(a one-line public wrapper you add to FishingManager that calls `FlyCast.begin`). `chargedCast`
(anchor: `    public static boolean chargedCast(ServerPlayer sp, InteractionHand hand, float power) {`): first
statement — if the rod in hand is FLY, `power = FlyCast.release(sp, rod, now)`; after the existing cast
call succeeds, `flyLanded(sp, session, FlyCast.takeQuality(sp))` applies splash/knot. Cancel the cast
(HUD off) when the charge ends without a cast.

Packets, registered in `ModNetwork` beside `FightInputPacket` (copy its shape exactly — the 1.21.1
`CustomPacketPayload.Type` + `STREAM_CODEC` form): `FlyBeatPacket` (C2S, empty payload) →
`FlyCast.beat(sp, now)`; `FlyCastPacket` (S2C: `active, startTick, period, zoneHalf, beats, maxBeats,
openLoop`) → `FlyCastClient.accept`.

Client `FlyCastClient`: `accept(FlyCastPacket)`, `tick(Minecraft mc)` (called from ClientInit's client
tick — find the existing tick registration and add the call; detect the sneak KEY going down via
`mc.options.keyShift.isDown()` edge, only while `active` and the player is using a fly rod; send
`FlyBeatPacket`), `render(GuiGraphics g, int sw, int sh, float pt)` (called from `ClientHud.render`
before `renderCastPower(graphics, mc);` — and make `renderCastPower` return early when FlyCastClient is
active so the two bars do not overlap). The bar: the same `textures/gui/cast_bar.png` sheet and
geometry as `FloatTimingClient` (frame 120×16 at `(sw-120)/2, sh-70`, recess 112×8 at +4,+4), green
zones `zoneHalf` wide at BOTH ends, the needle, beat pips above the bar (one per good beat, red when
the loop is open), the metres on the plaque like `renderCastPower` does (metres = base + beats).

**The drift.** `FishingSession` fields (insert after the anchor line
`    public boolean outclassed;      // §outclassed: the line is weaker than the pull — play it out, never reel it`):
`public int flyDrag;` (0..100), `public long flyDriftEnd;` (tick), `public boolean flyStraight;`.
In `tick()`, at the anchor `        // FLOAT / BOTTOM: wait for the bite, then a window to strike.\n        if (!session.bitten) {`
insert at the top of that block a call `flyDrift(level, sp, session, now)` (guarded by
`session.ctx != null && session.ctx.rod == RodType.FLY`) that: on the first tick sets
`flyDriftEnd = now + 240`; every 10 ticks moves `session.target` one block along the water's flow
(`level.getFluidState(target).getFlow(level, target)` — if its horizontal length < 0.05 no drift;
the new block must be water: use `findWaterColumn`) and re-sends the line
(`ModNetwork.toTracking(sp, new LineSyncPacket(...))` — the same 7-arg constructor the bite uses);
raises `flyDrag` by 3 per 10 ticks while drifting on flowing water, by 0 on still water; while
`flyDrag > 60` pushes `session.biteAtTick` out the way the dead-lure rule does
(`if (now >= session.biteAtTick - 5) session.biteAtTick = now + 25;`); when `now >= flyDriftEnd` sets
`flyStraight = true` once, shows `message.riverfishing.fly_straight` on the action bar, and from then
pushes the bite the same way (a straight line under you catches nothing — recast).
`handleRodUse`: insert a FLY branch before the anchor
`            } else if (session.iceFishing && session.rodClass != RodClass.ACTIVE) {`:
`} else if (session.ctx != null && session.ctx.rod == RodType.FLY) {` → if `sp.isShiftKeyDown()`:
`endSession` + `message.riverfishing.fly_pickup`; else **mend**: `flyDrag = 0`, `flyMends++` (add the
field), a soft sound; a third mend in one drift stamps SpookData at the target (spook).
Lang: `message.riverfishing.fly_straight` ("Line's straight below you — pick up and cast again" /
ru/uk), `fly_pickup`, `fly_tight` ("Tight loop"), `fly_splash` ("Splash — open loop"), `fly_knot`
("Wind knot"), `gui.riverfishing.fly_beats` ("false casts").
Check `tools/check_fly_b.py`: packets registered both ways, FlyCast API present, the four anchors
patched, marker function identical to the float QTE's (parse both, compare).

## Stream C — the rise, the set and the hatch (`tools/patches/p_fly_c.py`)
Owns NEW: `engine/Hatch.java`, wiki page. Patches: `engine/BiteContext.java`, `engine/BiteEngine.java`,
`fishing/FishingManager.java` (buildContext, the bite branch, activeStrike, particles),
`network/LineSyncPacket.java` only if needed, `client/HookedFishRenderer.java`,
`client/ClientLineState.java`, `docs/wiki` (+ GROUPS/README), `docs/patchnotes/0.9.1.md` → no: write
`docs/patchnotes/0.10.0.md` fresh with a "Fly fishing" section.

**Hatch.** `public enum Hatch { MIDGE, STONEFLY, MAYFLY, CADDIS, TERRESTRIAL, SCUD, BAITFISH }` with
`static Hatch now(Season s, TimeOfDay t, Weather w, WaterType water)` per the design's table (null when
nothing is hatching; CLEAR/RAIN both allowed except TERRESTRIAL needs CLEAR day; SCUD only on still
water: LAKE/POND/SWAMP; BAITFISH in autumn or on THUNDER), `boolean matches(TiedDesign.Template t)`
(MIDGE/MAYFLY/CADDIS ↔ DRY_FLY; STONEFLY ↔ NYMPH; TERRESTRIAL ↔ ANT; SCUD ↔ SHRIMP; BAITFISH ↔
STREAMER), `int sizeMm()` (midge 4, mayfly 10, caddis 8, stonefly 14, terrestrial 8, scud 8, baitfish 22)
and `double factor(TiedDesign.Analysis a)`: right kind and |sizeMm−a.sizeMm()| ≤ 3 → 1.5; right kind
→ 1.0; wrong kind → 0.6. With no hatch: DRY_FLY 0.7, STREAMER 0.9, else 1.0.
`BiteContext`: `public Hatch hatch;` (insert after `    public com.riverfishing.tackle.TiedDesign.Analysis tied;`).
`buildContext` (anchor `        ctx.weather = level.isThundering() ? Weather.THUNDER : (level.isRaining() ? Weather.RAIN : Weather.CLEAR);`
— it occurs TWICE, in buildContext and reEvaluate; patch BOTH with a following line
`ctx.hatch = ctx.rod == RodType.FLY ? Hatch.now(ctx.season, ctx.time, ctx.weather, ctx.water) : null;   // §fly`
using `s.replace` on the exact line, asserting count == 2 before and the marker after).
`BiteEngine.baitScore`: after `        if (c.tied != null) best *= c.tied.affinity(p.group);` add
`if (c.tied != null && c.rod == RodType.FLY) best *= Hatch.factor(c.hatch, c.tied);` (make `factor`
static taking a nullable hatch).
Particles: in `tick()`, at the anchor `            } else if (now % 20 == 0) {\n                level.sendParticles(ParticleTypes.FISHING,`
insert after the `{` a call `Hatch.particles(level, session, now)` (in Hatch; guarded by
`session.ctx != null && session.ctx.hatch != null`): a few `ParticleTypes.WHITE_ASH`-style motes over
the water within 6 blocks of the target and, one time in four, a `FISHING` splash ring at a random water
block nearby — the rise of a fish that is not yours. Keep it to ≤ 8 particles per call.

**The rise.** At the anchor
`                if (session.rodClass == RodClass.FLOAT && session.reelSize > 0) {\n                    startFloatTiming(sp, session, now);`
replace with: `if (session.ctx != null && session.ctx.rod == RodType.FLY) { startFlyRise(sp, session, now); } else if (…unchanged…) {`.
`startFlyRise`: the set window in ticks from the species (`FishProfile.fightAggression`, 0..1):
`delayMin = round(4 + (1 − aggression) × 8)`, `delayMax = delayMin + 8 + round((1 − aggression) × 6)`;
`window = delayMax + 6`; `floatPeriod = 2 × window` so the marker climbs 0→1 over the window and is
TIME; `floatZoneCenter = (delayMin + delayMax) / 2 / window`, `floatZoneHalf = (delayMax − delayMin) / 2 / window`,
`floatOrangeHalf = floatZoneHalf + 0.06`; `biteWindowEnd = now + window`; send the timing packet
yourself with `FloatTimingPacket(true, now, window, floatPeriod, …)` (do NOT call `beginTiming` — it
randomises the centre). Also send the fish: `ModNetwork.toTracking(sp, new LineSyncPacket(…))` using the
constructor that carries `species` (look at the overloads at the top of LineSyncPacket; pick the one
the hooked-fish send uses) with `fighting=false, biting=true` so the client can draw the rise.
`activeStrike` miss branch (anchor `            eatBait(sp, session);   // §consumables: a mistimed strike still loses the bait`):
before it, for FLY sessions choose the message: marker < zone → `message.riverfishing.fly_too_fast`
("Too fast — pulled it out of its mouth"), else `fly_too_slow` ("Too slow — it spat the fly"); stamp
SpookData at the target so the spot is quiet ~20 s; then fall through to the existing end.
`HookedFishRenderer.draw`: relax the gate to `(state.fighting || state.biting) && !state.species.isEmpty()`
and, when `biting && !fighting`, draw the body rising: pitch −35°, at `fy = −0.4 → −0.05` over the
first 8 ticks of the bite (use `state.jumpT`-style local timing; keep it small). `ClientLineState`:
ensure `species` is taken from the packet even when `fighting` is false (check `accept`).
Lang: `message.riverfishing.fly_too_fast`, `fly_too_slow`, `hatch.riverfishing.<midge|stonefly|mayfly|caddis|terrestrial|scud|baitfish>`
(names, for the wiki and a later journal line).

**Wiki.** `docs/wiki/fly-fishing.md` + `ru/fly-fishing.md` + `uk/fly-fishing.md` (a language is
complete-or-absent: write all three), in the voice of `ice-fishing.md`: tackle, the rhythm cast (sneak
on the beats, release on a beat, false casts, roll cast is NOT in phase 1 — say the backcast needs no
room in this version), the drift and mend, the rise and the delayed set, the hatch table, which fish.
Add the page to `GROUPS` in `tools/gen_wiki_bundle.py` (the fishing-methods group beside ice-fishing)
and a row in each README. Run `py -X utf8 tools/wiki_anchors.py` and `tools/check_wiki_vanilla.py`
if they exist and fix what they flag. `docs/patchnotes/0.10.0.md`: a short "Fly fishing" section in
the 0.9.1 notes' style.
Check `tools/check_fly_c.py`: Hatch table covers every season×time at least once, factor rules,
the four anchors patched, wiki page in GROUPS and all three languages present.

## Integrator (after all three)
Apply A, B, C; merge lang; `./gradlew build` on 1.21.1; fix; port to rf1201 and rf26 (dialects per the
breeding contract); install; commit per tree.
