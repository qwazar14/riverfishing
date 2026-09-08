# §fly-4 — the rod stops talking (2026-09-08, after the first play)

Three things came back from the first session with §fly-3, and all three were fair.

1. **Two quick right clicks picked the line up.** A streamer is fished with fast strips, so the gesture
   fought the mechanic it was attached to. Gone: the only ways a cast ends are stripping the fly to your
   feet and switching hotbar slot, which is how every other rod is abandoned.
2. **Too much text.** A status line, a strike prompt, landing messages, hook messages — on a mod whose own
   rule (§silent-bite, §catch-the-moment) is that a float going under is the entire cue and no text is
   printed for it. Every fly string is deleted. What tells the player now: the fly sitting still versus
   cutting a wake (with a sound), bubbles and a bulge for a fish coming up, a boil for the take, the ring
   on the water going away when a cast lands on a rise.
3. **The left click did nothing visible** — it reset a drag counter that was itself invisible. It is a
   FLICK now: `FlyDrift.flick` moves the fly's spot two blocks upstream, draws the arc of spray, resets the
   drag and clears the straight-line flag. You watch the fly move.

With the prompt gone the strike takes either button (`FlyStrike.tryStrike` no longer cares which arrives,
only which the fly wanted): the fly's own hand gives `hookStrength` 2 or 1, the other gives 0, and a 0 is
what a jump can throw. `FlyCastPacket` mode 2 and `FlyDrift.push` are gone with the status line;
`FlyCastClient` keeps only the cast gauge and listens for the left button whenever a fly rod is held and a
line is out.

---

# §fly-3 — the 0.10.0 rebuild, to the written spec

Fly fishing has been rebuilt twice. The first pass (§fly, 2026-09-07) was a rhythm game: taps on both ends
of a fast needle, a release rule nobody could infer, a drag number. The second (§fly-2, 2026-09-08) made it
legible but kept the taps for distance and hooked the fish for you. This one is built to the design
specification the author wrote, and the principle it opens with:

> **Easy to Learn — Hard to Master.**
> A player who has never held a fly rod should get the loop in seconds:
> *see a fish → cast near it → let the fly drift → see the bite → set the hook.*

Nothing on the screen is a percentage, a tick count or a coefficient.

## The shape

```
IDLE → CASTING → DRIFTING → STRIKING → FIGHTING → IDLE
```

One class per state, and `FishingManager` keeps only the glue:

| Class | Owns |
|---|---|
| `fishing/FlySession.java` | the state a cast is in, where the fly is, the rise it is fishing to, the fly's kind |
| `fishing/FlyCast.java` | the swing cycle, distance, release quality (and, on the same needle, the winter jig) |
| `fishing/FlyDrift.java` | the current, drag, mend, strip, the pick-up, the streamer's rhythm, the rise check |
| `fishing/FlyStrike.java` | the approach, the take, the window, the input, weak/normal/solid hooks |
| `fishing/FlyRises.java` | the feeding fish: where they show, which species, sip or slash |

The three kinds a player has to tell apart are `FlySession.Kind`: **DRY** (dry fly, ant), **NYMPH**
(nymph, shrimp, everything else), **STREAMER**. Only the last one strikes on the right button.

## CAST — the right button, and only it

`SWING_PERIOD 30` (a full back-and-forward), `BASE_METERS 6`, `METERS_PER_CYCLE 3`, capped at the rod's
reach. The release is judged against the forward stop: `RELEASE_WINDOW 3` ticks → **perfect** (100 % of the
metres, a quiet landing), `NORMAL_WINDOW 9` → **normal** (80 %), anything else → **bad** (50 %, a slap, a
spook of 0.18). The metres go back through `castDistance`'s inverse so the fly lands exactly where the HUD
said.

The left button does nothing during a cast; `FlyCastClient` does not even send the packet in that mode.

## RISES — the target

`MIN_REACH 6`, `MAX_REACH 18`, `MAX_RISES 3`, life 300–500 ticks, a new one every 50–140. One in three is a
**slash** (a wide ring, spray, a loud smack) rather than a **sip** (a small ring, a quiet kiss), and the
species is drawn from the bite engine's own weights at that spot × `flyAppetite(diet, slash)` — insect
eaters sip, predators slash.

`RISE_HIT_RADIUS 2.0`. A fly that lands or drifts inside it takes the rise off the water, becomes that
fish, and the take is 10–25 ticks away (a dry fly a little sooner). Everything else fishes at the engine's
ordinary pace — **no rise is ever required**.

## DRIFT — leave it alone

`DRAG_RATE 2` per ten ticks of current, `DRAG_CRITICAL 70`, 100 = the line is straight and the drift is
over. Over the critical the fly skates: particles off the fly, a wake, and the careful kinds have their
take pushed back (a streamer does not — a fleeing baitfish is a fleeing baitfish). **The number is never
shown**; the status line says *natural drift* / *the line is pulling* / *line's straight below*.

- **LMB — mend.** Drag to zero, drops thrown upstream, `QUIET_MENDS 2` free; every one after that spooks by
  `0.08 × (mends − 2)`. Not a hard limit, a growing consequence.
- **RMB — strip.** `STRIP_BLOCKS 1.75`, applied to the same 0..1 reel fraction the spinning retrieve uses,
  so the client draws the fly gliding home rather than hopping between blocks. A streamer stripped
  `RHYTHM_MIN 10`–`RHYTHM_MAX 25` ticks apart pulls the take in 22 ticks and shows a follow one time in
  three; a nymph 6; a dry 3 and a small spook, because a dragged dry fly has been seen to move.
- **RMB twice inside `DOUBLE_TAP 10` ticks — pick up.** No press-duration rule anywhere: one press is
  always one clear strip.

## STRIKE — two acts, four outcomes

`APPROACH_MIN 10` + a stable 0–10 lead before the take: bubbles and a bulge under the fly, the HUD says
*something's coming up*. Then the take: a boil, a splash, `STRIKE_WINDOW 35` ticks and the button on the
screen.

| | Result |
|---|---|
| Right button, within `CLEAN_STRIKE 20` | solid hook (`hookStrength 2`) |
| Right button, later | normal (1) |
| Wrong button | weak (0) |
| During the approach | 30 % a weak hook, 70 % the fly pulled away and the fish put down |
| Nothing | it spits the fly |

Never binary: a mistake is a worse hook, not a lost fish.

## FIGHT — the mod's own, plus jumps

No fly-only combat. `session.flyFight` schedules a jump every 80–150 ticks: `jumpWindowEnd` is the
existing greyhounding window, so winding into one rips the hook out through code that already existed. A
weak hook is thrown on a jump 18 % of the time. The fight starts from the fly's real position, capped at
0.85 exactly as the spinning retrieve does.

## Gone from the old implementation

LMB hauls during the cast; distance from hitting ±0.18 stop zones; the `<6 ticks strip / >6 ticks pickup`
hold rule; the automatic `hookUp` with no player input; the numeric drag; the rise as a requirement.

## Kept

`RodType.FLY`, `RigType.FLY`, leader + fly, `Hatch`, `TiedDesign` affinities, `FlyRises`, the line sync,
the jumping-fish render, `hookUp` and the whole fight.

---

# §fly-2 — the 0.10.0 rebuild (what shipped)

Phase 1 shipped the §3 mini-game below and it was **crooked, dull and opaque**: a needle at 0.45 s per stop that wanted a tap on every end, a release rule ("the lit end") nobody could infer, a twelve-second drift with nothing to look at but a drag number, and a strike clock with no label. The rebuild keeps the bones (the rod, the rig, the hatch, the species clock) and replaces the loop with three legible ideas:

1. **The fish show themselves** (`fishing/FlyRises.java`). While a fly rod is in the hand, feeding fish rise in front of the angler — a ring, a splash, a sip — five to sixteen blocks out, three at a time, each holding its lie for ten seconds. A fly landing or drifting within 2.5 blocks is *on the fish*: `session.species` becomes that fish and the take comes in 15–45 ticks. The species is drawn from the bite engine's own weights at that spot × `flyAppetite(diet)` (insectivore 1.4, peaceful 1.0, omnivore 0.9, predator 0.35). Blind casts still fish the slow way.
2. **The cast is a hold and one release** (`fishing/FlyCast.java`). Hold use: the rod false-casts on its own, a stop every 16 ticks, +2 m per stop from 6 up to the rod's reach. Release on the forward stop (the green right end, ±0.18) = tight; within 0.42 of it = open (85 %); further = piled (60 %, a slap, spook). A left-click on either stop = a haul, +2 m, once per stop; a click anywhere else does nothing. There is no way to fail by clicking.
3. **The drift reads itself** (`FlyCastPacket` mode 2 → `FlyCastClient.renderDrift`). One line under the crosshair: *dead drift* / *dragging — LMB to mend* / *line's straight — hold RMB* / *on the fish — wait*. LMB = mend (FlyBeatPacket routed by `FishingManager.flyBeat`). Tap RMB = strip (a streamer or shrimp is fished by it: −20 ticks to the take per strip, a swirl behind the fly one strip in three; a dry fly only twitches −6). **Hold RMB ≥ 6 ticks = pick up and go straight into the false casts** (`FishingManager.tick` → `endSession` + `FlyCast.begin`; the release delivers). `RodItem.use` turns a calm fly line's click into an item hold (`FishingManager.flyCalm` / `ClientLineState.selfCalm`); `releaseUsing` → `flyTap` strips on a short hold.
4. **The take says SET.** Same species clock, wider: green opens at `5 + (1 − aggr) × 6`, lasts `12 + (1 − aggr) × 6`, window +8; the strike bar's label reads *SET! Click in the green* on a fly rod.

Gone: wind knots, the "lit end" rule, sneak+RMB mend, the hatch's decorative rise rings (every ring is a fish now). Kept for later: backcast obstruction, the roll cast, the nymph indicator, a drawn following fish.

Everything server-side is particles and the existing line sync, so spectators see the rises and the rise-to-take the same as the angler.

---

# §fly — fly fishing (0.10 proposal)

A separate way to fish, with its own loop. Spinning is a rhythm game, float and bottom are a waiting
game, the fight is a tug-of-war. Fly fishing in real life is none of those: it is **timing** (the cast),
**reading** (where the fish holds, what it eats today) and **restraint** (the set). That is the mini-game.

## 1. How it works on a real river — the parts worth simulating

| Real thing | What actually matters | Simulated as |
|---|---|---|
| **The line is the weight.** A fly weighs nothing; the thick fly line carries it. Casts are short (5–20 m) and precise. | Cast distance comes from the rod/line class and the cast's timing, not from a sinker. | `RodType.FLY`, castMin/castMax = 0, distance from the cast gauge |
| **The cast is a rhythm of two stops.** Backcast, pause while the loop unrolls behind you, forward cast, stop the tip high. Too early or late = open loop, tailing loop, a "wind knot" in the leader. False casts lengthen the line. No room behind = roll cast. | A timed release with a narrow window; false casts trade risk for distance; the world behind you matters. | the **loop gauge** (§3.1) |
| **Presentation.** A dry fly must drift *drag-free*; the current bows the line and drags the fly, which trout refuse. You **mend** (flip line upstream) to reset it. A nymph drifts under an indicator. A streamer is **stripped** by hand — pull, pull, pause. | Each drift is a short active phase with a drag meter and one input; different flies want different inputs. | the **drift** (§3.2) |
| **The take and the set.** A trout *rises* and turns down with the fly — lift too fast and you pull it away; too slow and it spits. Nymph takes are a twitch of the indicator, set instantly. Streamer takes are a jolt — **strip-set**, do not lift. | Three set rules; the delay is per species. | the **rise** (§3.3) |
| **Match the hatch.** Insects hatch by season and hour; fish feed selectively and rise in rings. Wrong size or wrong kind is refused. | A hatch table by season/time/weather; visible tells on the water; a multiplier for the fly's kind and size. | the **hatch** (§4) |
| **Light tippet, fish on the reel.** 2–6 lb tippets; the reel is a drag; salmonids jump. | Low break tension, long runs, more jumps; `outclassed` earlier. | fight tweaks (§5) |

## 2. Tackle

- **Fly rod** — one new `RodType.FLY(base 9, takesReel=true, minReel=FLY, castMin 0, castMax 0)` in three
  classes by line weight: **#4** (grayling, dace, small trout; 12 blocks), **#6** (all-round; 16 blocks),
  **#8** (salmon, taimen, pike, sea; 20 blocks). Crafted like the other rods; one 3D model through the
  rod pipeline (long, thin, cork grip, the reel below the hand).
- **Fly reel** — a new reel kind with no line capacity game: only a drag rating (1500 / 2500 / 4000 to
  pair with the classes). Existing reels do not fit a fly rod and vice versa.
- **Fly line** — a new rig item in three kinds: **floating** (surface / dries), **intermediate**
  (sub-surface / nymph, wet), **sinking** (streamers deep). Sets the fly's depth the way `floatDepth`
  does for a float.
- **Tippet** — reuse the existing line items: the fly rig accepts only the two thinnest ratings; the
  rig's `breakTension` is the tippet's.
- **Flies** — the Tie page already makes them. A new `SlotRole.FLY` and a **Fly rig** form at the Tackle
  Station: fly line + tippet + fly. The stencils map to presentations: `dry_fly`, `ant` → surface;
  `nymph`, `shrimp` → dead drift under; `streamer`, `devil`, `drop`, `pellet` → strip. The tied lure
  keeps its winter-jig role on the ice rig; the same item is a fly on a fly rig.

## 3. The mini-game

### 3.1 The cast — the loop gauge
Hold use: the rod goes back and a gauge on the fight-bar spot swings up and down at the rod's period
(0.9 s on #4, 1.1 s on #6, 1.3 s on #8). **Release near a peak** = tight loop: full distance, a soft
landing. Off the peak = open loop: 60 % distance, a splash (a *spook*: −30 % bite in 3 blocks for 5 s).
Well past the peak = tailing loop: a **wind knot** — line wear, and a 10 % chance the tippet parts on
the next fish. **Holding through peaks = false casts**: each peak adds 2 blocks up to the class max, each
is another chance to blow it. The landing point is where the crosshair meets the water at that
distance, so distance control IS aim.

**Behind you counts.** A solid block within 3 blocks behind and above eye level catches the backcast:
the fly snags (grindstone sound, line wear, the cast is lost). **Sneak + use = roll cast**: no backcast,
no risk, 60 % distance. Trees on the bank are the reason you learn the roll cast, as in life.

### 3.2 The drift — presentation
The fly lands and the drift starts; it lasts 8–12 s, then the line comes tight below you and you pick up
for the next cast. On flowing water the fly moves with the flow (vanilla flow vector, ~0.6 blocks/s);
on still water it sits, and a tap twitches it.

- **Drag meter** (the tension bar's slot): rises while the line bows across the current — fast on a
  cast straight across, slow on one upstream. Bite chance each tick × (1 − drag). **Left-click = mend**:
  drag resets; a third mend in one drift spooks.
- **Dry fly / ant**: dead drift on the surface. Best under a hatch.
- **Nymph / shrimp**: dead drift under; an **indicator** (a float item on the rig) shows the take;
  without one it is tight-line nymphing: no float to watch, +20 % bite, a harder set.
- **Streamer**: **strip** = taps; the fish wants 2–3 strips 5–12 ticks apart, then a pause of 15–30
  ticks; the take comes on the pause or the first strip after it. Constant stripping = follows, no take.
  The hooked-fish renderer draws the **follow**: a fish trailing the fly during a good retrieve — the
  tell that says keep going.

### 3.3 The rise — the take and the set
- **Dry fly**: a *rise* — the fish comes up under the fly (drawn by the hooked-fish renderer for half a
  second), a ring of particles, a "slurp". The set window opens after a **species delay**: grayling
  0.2–0.6 s (fast), trout 0.3–0.9 s, chub/ide 0.5–1.2 s (they take slowly). Lift (use) before it =
  *"too fast — pulled it away"*; after = *"too slow — spat it"*. Inside = hooked, into the fight.
- **Nymph**: the indicator dips or the line tip stops; set within 0.5 s.
- **Streamer**: a jolt mid-strip; **strip-set** = tap again within 0.4 s. Lifting instead is a trout
  set on a predator: 50 % miss.

Every miss ends the drift; the fish is *put down* (that spot is quiet for 20 s).

## 4. The hatch
A table by season × time of day × weather. When a hatch is on, the water shows it: insect particles on
the surface and rise rings from fish that are not yet yours. That is the in-game tell; the NATURALIST
skill names the hatch in the journal.

| Hatch | When | Fly kind, size |
|---|---|---|
| Midge | winter, early spring; any hour | dry, tiny (#18–22) |
| Stonefly | early spring, day | nymph, large |
| Mayfly | late spring–early summer, evening | dry #12–14 / nymph |
| Caddis | summer, evening | dry #14–16 |
| Terrestrials | summer, afternoon, wind | ant / hopper |
| Scud | all year, still water | shrimp |
| Baitfish | autumn, cold or high water | streamer |

Match = kind × size band from the tied fly's `sizeMm`: right kind and within one size = **×1.5**; right
kind, wrong size = ×1.0; wrong kind = ×0.6. No hatch on: nymph ×1.0, streamer ×0.9, dry ×0.7 — nymphs
catch most fish most of the time, which is true.

## 5. The fight, on fly gear
- Tippet break tension is low; `outclassed` arrives sooner on #4 — a 3 kg trout on 3 lb tippet is a
  20-minute fight in life, here it is the outclassed mode by design.
- Runs longer (course gain ×1.3), salmonid jumps ×2, the reel's crank gain low — you *give* line.
- The rod tip is kept high: the rod-load gauge reads bend, and a low tip (looking down) costs tension.

## 6. Species
Profile field: `"fly": {"dry": 1.2, "nymph": 1.0, "streamer": 0.5, "set_delay": [6, 18]}` (ticks).
Missing = the species does not take a fly.

- **Salmonids**: trout, rainbow trout, grayling (the dry-fly king), char, lenok, whitefish, smelt on
  dries and nymphs; salmon, taimen, nelma, pink salmon on streamers.
- **Cyprinids**: chub, ide, asp, dace, rudd, bleak — dries and nymphs; asp on streamers.
- **Predators**: pike, perch, largemouth bass, peacock bass, bluegill, golden dorado — streamers.
- **Sea**: mackerel, garfish, seabass, bluefish, jack crevalle, snook, tarpon — streamers and shrimp.

## 7. Progression and the rest
- Unlocks with the angler journal (level 8 suggested); the fisherman sells a **Fly kit** (rod #6, reel,
  floating line, 3 flies) at expert.
- No fly fishing through ice; the tied lure stays a winter jig there.
- Journal firsts: first fish on a dry fly, first on a self-tied fly, a grayling on a midge.
- Wiki page `fly-fishing.md` in three languages.

## 8. Build plan
| Phase | What | Size |
|---|---|---|
| **1 — core** | rod, reel, fly line, fly rig + `SlotRole.FLY`, the loop gauge cast, the drift with drag and mend, the dry-fly rise with the delayed set, the hatch table with particles, species field on ~30 profiles, wiki | the bulk; three parallel streams (tackle / cast+drift / rise+hatch) |
| **2 — the other two presentations** | streamer strip and strip-set, the follow render, nymph indicator and tight-line, roll cast, backcast snag | medium |
| **3 — polish** | fly rod 3D model with the line loop in the air during the cast, journal firsts, fisherman kit, sounds | small |
