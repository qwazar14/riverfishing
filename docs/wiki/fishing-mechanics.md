# Fishing mechanics

Nothing bites by luck. This page is the full model: how the engine decides *what* takes and *how soon*, how the cast and the strike work, and how the fight is won or lost.

## Contents

- [The bite engine](#the-bite-engine)
- [Casting](#casting)
- [The three flows](#the-three-flows)
- [Setting the hook](#setting-the-hook)
- [The fight](#the-fight)
- [Fight patterns](#fight-patterns)
- [Losing the fish](#losing-the-fish)
- [Snags, foul-hooking and bycatch](#snags-foul-hooking-and-bycatch)
- [What you land](#what-you-land)
- [Gear wear](#gear-wear)
- [Difficulty](#difficulty)

---

## The bite engine

For every one of the 70 species, the engine computes an attractiveness weight **W**. Species with W above zero go into a weighted lottery for *which* fish bites; the **sum** of all their weights decides *how long you wait*.

### Match coefficient M — your tackle

```
M = 0.30 × bait
  + 0.15 × groundbait
  + 0.13 × rig
  + 0.12 × rod
  + 0.12 × line
  + 0.10 × hook
  + 0.08 × reel
```

| Component | How it scores |
|---|---|
| **bait** | The best-scoring bait on the rig, taken straight from the species' profile (0 … ~1.2). |
| **groundbait** | 1.0 for the right category in a fresh fed spot, 0.3 for the wrong one, 0.4 for an unfed spot. |
| **rig** | 1.0 if the rig is one of the species' ideal rigs, otherwise 0.15. |
| **rod** | 1.0 if the blank is one of the species' ideal rods, otherwise 0.35. |
| **line** | `typeMatch × diameterFalloff`, where typeMatch is 1.0 for the right material and 0.6 for the wrong one. |
| **hook** | The best-fitting hook loaded. No hook at all: 0.85 on a Predator or Winter rig (the treble counts), otherwise 0. |
| **reel** | Falloff around the species' ideal size. No reel: 1.0 if the species wants no reel, else 0.25. If the species is reel-agnostic: 0.6. |

The falloff used for hook size, line diameter and reel size is the same everywhere:

```
score = max(0, 1 − 0.25 × |actual − ideal| / tolerance)
```

So at exactly ±1 tolerance you still score 0.75, and the score only reaches zero at ±4 tolerances. Tolerance is a *steepness* setting, not a cut-off.

### Environment score E — the world

```
E = waterFactor × season^1.5 × time^1.4 × weather × biome^1.3 × distance × community
```

Five **hard gates** zero it out entirely — the fish simply is not there:

1. The water-body type's factor is 0 (a pike has `"sea": 0.0`).
2. The water column is shallower than `depth_min` or deeper than `depth_max`.
3. The water body is narrower than `width_min` or wider than `width_max`.
4. No listed biome group matches the spot.
5. This patch of water's [community](water-and-conditions.md#every-water-is-its-own) does not hold the species.

The season, time and biome factors are raised to powers greater than 1, which **amplifies** the swings the profiles describe — when and where you fish is strongly felt.

The **distance** factor:

| Situation | Factor |
|---|---|
| Long-range blank on water narrower than 12 blocks | 0.4 |
| Cast shorter than the species' `distance_pref.min` | `0.6 + 0.4 × (d / min)` |
| Cast inside the preferred band | **1.1** |
| Cast beyond `distance_pref.max` | 0.85 |

A species **stocked** into water that fails its natural gates survives at a quarter strength: `E = max(natural, 0.25 × stockedPresence)`. See [Stocking](stocking.md).

### Species weight W

```
sizeExp = 1 + min(3, meanWeightKg / 2)

W = base × M^sizeExp × E × feedBonus × localStock × pressureFactor × (1 + naturalistBonus)
```

The `M^sizeExp` term is the heart of the difficulty curve. A bleak (30 g mean) has an exponent near 1, so a mediocre setup still catches it. A catfish (7 kg mean) sits at the exponent cap of 4 — halve your match score and its bite weight drops sixteenfold. **Big fish demand a near-perfect kit.**

Then a chain of multipliers, applied in this order:

| Modifier | Effect |
|---|---|
| Bait is the selector | `×(0.30 + 0.70 × min(1.3, baitScore))` — a favourite bait earns a small bonus; a barely-tolerated one slows the fish to a crawl. |
| [Line visibility](reels-and-lines.md#line-visibility) | Up to ×0.4 for a thick opaque line on a small wary fish. |
| Bait score below 0.15 | ×0.1 |
| Species needs a leader and you have none | ×0.15 |
| A leader is fitted | `×(0.85 + stealth × 0.30)` |
| [Float depth](rigs-and-baits.md#the-float) set | ×1.3 on the species' horizon, ×0.55 on the wrong one |
| Ultralight rod | `×clamp(1.6 − meanKg × 0.6, 0.4, 1.6)` |
| Spinning rod | `×min(1.2, 0.85 + meanKg × 0.15)` |
| [Lure size](tackle-station.md#2-the-lure-size-filter-lures-only) | `×max(0.05, 2/(ratio + 1/ratio))` |
| [Lure colour](rigs-and-baits.md#dyeing-lures) | ×0.75 … ×1.35 |
| Angler level below the species' `min_angler_level` | `×max(0.03, 0.6^levelsShort)` |

And two more **hard gates** on top of the environment ones:

- **No bait the fish wants** on the rig → W = 0.
- **Hook score below 0.34** → W = 0. The wrong hook size band means the fish will not take, full stop.

### Time to bite

```
W_total = Σ W over all species
effective = W_total ≤ 1.5 ? W_total : 1.5 + (W_total − 1.5) × 0.3
T = 160 ticks / effective
ticks = −T × ln(1 − random)          (minimum 40)
```

The compression above 1.5 is the **swarm cap**: a huge shoal of small fish would otherwise floor the wait and turn a swim into a bite-per-few-seconds conveyor.

The sampled wait is then adjusted:

```
delay = ticks / spotDepletion × keenSenseMultiplier × underloadPenalty
```

and floored per flow:

| Flow | Floor |
|---|---|
| Float | at least **140 ticks** (7 s) |
| Bottom | at least **660 ticks** (33 s), or 1.5× the sampled delay, **plus a random 0–900 ticks** so several rods cast in a row don't all fire at once |
| Active | at least 40 ticks — but the clock only runs while you are actually retrieving |
| Ice | clamped to **200–2400 ticks** (10 s – 2 min) |

Then a [feeding frenzy](water-and-conditions.md#feeding-frenzy) divides it by 3, and a fresh fed spot takes off up to 40 %.

If the result still exceeds 2400 ticks (2 minutes) you are warned: *"The fish are wary of this setup — it could be a very long wait"*. There is **no upper cap** — a barely-viable setup really is a long wait.

### Live re-evaluation

A waiting Float or Bottom line **re-reads the world every 300 ticks** (15 seconds). Dusk falling, rain arriving, a frenzy starting, groundbait thrown after the cast — all of it rescales the *remaining* wait and re-picks which species will bite. A cast is not a frozen snapshot. (A koi decided at the cast stays a koi, so a long wait cannot compound its rarity into a certainty.)

If the water goes completely dead (night or season gating everything out) the line simply sits until a later re-evaluation revives it.

---

## Casting

**Hold** right-click to charge, **release** to cast. There is no click-cast.

```
power(ticksHeld) = 0.15 + 0.85 × triangle(ticksHeld, period 40)
```

The bar is a triangle wave: power climbs from 0.15 to full over 20 ticks (1 second), falls back to 0.15 over the next 20, and repeats. Overcharging weakens the throw, so distance is a timing skill. Tick marks sit at 50 % and 85 %.

```
throw distance = 2 + power × (maxRange − 2)
```

`maxRange` is the blank's reach (see [Rods](rods.md)) multiplied by the [cast-weight factor](rods.md#loading-the-blank-the-test-window). When the factor is below 1 the bar's far end is drawn as a **red dead band with a hard cut-off marker** — the fill physically cannot enter it.

The rig lands in the first water block found scanning down a 24-block column from two blocks above your eye level, along your look direction. If there is no water there: *"No water to cast into"*.

Two refusals worth knowing:

- Water capped by a solid ice sheet is rejected: *"Drill a hole with an ice auger"*.
- A reel-less rod aiming past 6 blocks: *"A pole cannot reach past 6 blocks"*.

Casting an **Active** rod costs hunger — one whole food point every 4 casts.

---

## The three flows

### Float (Stick, Bamboo, Pole, Winter)

Cast, then watch. **There is no "Bite!" text and no sound** — the bobber plunges on your screen and spotting it is the game. The bite window is **72 ticks** (3.6 s).

Reeled float rods run the strike-timing bar. Reel-less ones (all three wooden blanks) do **not** — they save their single timing challenge for the [pull-out](#the-pull-out-reel-less-poles).

### Bottom (Feeder, Bottom, Carp, Surf, Boat)

A long cast and a long wait, with a very forgiving **200-tick** (10 s) bite window and a plain click to set the hook — no timing bar. These are the rods you can leave on a [rod pod](blocks.md#rod-pods) with a [bite alarm](tools.md#bite-alarms).

### Active (Ultralight, Spinning, Sea spinning, Trolling)

The bite clock does not even start until your first crank. Each right-click advances the lure 4 ticks and its **gap from the previous click is the lure action** — see [working the lure](rigs-and-baits.md#working-the-lure).

```
retrieve length (ticks) = clamp(castDistance × coeff, 80, cap)
  Spinning : coeff 20, cap 340
  others   : coeff 10, cap 220
```

Reach the end of the retrieve with nothing on and the cast ends: *"Nothing. Recast."*

A take fires the strike-timing bar with a **60-tick** window (35 for a topwater blowup). Either click again or **release** the retrieve button — both count as the hook-set.

[Trolling](sea-fishing.md#trolling) is the exception: the boat's momentum sets the hook itself, with no timing at all.

---

## Setting the hook

### The timing bar

A marker sweeps back and forth across a bar as a triangle wave. Somewhere on it sits a **green zone** (a certain hook-up) flanked by an **orange band** (a 25 % chance). Outside both, the fish is gone. The green zone's position is random each time.

```
orange half-width = min(0.47, greenHalf + 0.11)
```

The **Finesse** skill widens the green zone by +1 % per rank.

| Bar | Sweep period | Green half-width |
|---|---|---|
| Reeled float rod | `clamp(30 − aggression × 12, 16, 30)` ticks | `clamp(0.19 − aggression × 0.05 − meanKg × 0.012, 0.07, 0.19)` |
| Lure rod | `clamp(40 − aggression × 6, 30, 40)` ticks | `clamp(0.32 − meanKg × 0.008, 0.24, 0.32)` |

The lure bar is deliberately easy — roughly half the bar is green — because it stands in for a hook-set, not a reaction test. The float bar is tighter, and an aggressive or heavy fish tightens it further.

Miss it and you get *"Mistimed! You missed the strike window"* or, on a timeout, *"The fish got away…"*.

### The pull-out (reel-less poles)

A wooden rod has no reel and therefore no tension fight. After the strike comes one decisive timing window: the **pull-out**. Land it in the green and the fish comes flying out; miss and it throws the hook.

First, a hard check — a fish too big for the line snaps it instantly:

```
if weightKg × 1.4 > max(0.4, lineStrainKg) → the line parts
```

Then the sweep and zone are set by the fish's weight, with the **rod tier softening the curve**:

| Blank | Period | Floor | Zone shrink | Zone floor |
|---|---|---|---|---|
| Stick Rod | `30 − kg × 6.0` | 10 ticks | `0.20 − kg × 0.045` | 0.035 |
| Bamboo Rod | `30 − kg × 4.5` | 12 ticks | `0.20 − kg × 0.038` | 0.050 |
| Pole Rod | `30 − kg × 4.0` | 14 ticks | `0.20 − kg × 0.033` | 0.060 |

A stick can never realistically land a trophy; a true pole can. A foul-hooked fish tightens the window further (−4 ticks period, −0.04 zone).

You get `period × 2 + 10` ticks to click.

---

## The fight

A boss bar appears. **It does not name the fish** — you learn what it was when you land it. Its fill is your landing progress; its colour is your tension.

| Bar colour | Meaning |
|---|---|
| Green | Tension comfortable |
| Yellow | Tension above two thirds of the break point |
| Red | At the break point **or** the fish is running |

The bar's title also reports the state: *"🎣 Player"*, *"🎣 Player — running!"*, or *"🎣 Player — tiring"* once fatigue passes 70 %.

Under the bar sits the **fight coach** cue:

| Cue | When |
|---|---|
| `⇪ reel` | Calm — crank it in |
| `⇩ ease off!` | The fish is running |
| `⚠ open the drag!` | Tension above 85 % of the break point |

### The three drag positions

This is the whole fight, expressed with no new inputs:

| Position | How | What happens |
|---|---|---|
| **Locked** | Right-click (crank) | You gain line. Tension climbs. |
| **Working** | Standing, not cranking | Tension bleeds off slowly. |
| **Open** | **Crouch** | The spool slips. Tension drops **3× faster** and the line **cannot snap** — but cranks gain nothing at all, and the fish takes line back. |

Open the drag for jumps and runs you cannot hold. You only gain standing up. Crouch-and-spam is not an exploit: with the drag open the handle just spins against a slipping spool.

While crouched you also **pay out line** even between runs (−0.0025 progress per tick, −0.004 during a run), so camping in the open-drag position is not free immunity.

### Tension and progress

```
requiredKg      = max(0.5, fightStrength × (1 + weightKg) × 2)
effectiveStrain = lineStrainKg + 0.5 × fightDrag
breakTension    = clamp(effectiveStrain / requiredKg, 0.2, 1) / breakSensitivity
                        × overloadPenalty × steadyHandMultiplier      (clamped 0.1 … 1.0)
```

That single number is your margin. Thin or worn line, a heavy fish, a small reel and an overloaded blank all shrink it.

Per crank:

```
tension  += (running ? runPulse : calmPulse) × (1 − 0.55 × fatigue)
progress += landPulse × (running ? 0.2 : 1.0) × (1 + 0.6 × fatigue)
```

Cranking into a run spikes tension and gains almost nothing — that is the lesson.

The pulses themselves:

```
sensitivity = reel-less ? 1.3 : clamp(1 + (4000 − reelSize)/4000 × 0.5, 0.6, 1.5)
weightStress = clamp(weightKg / 5, 0.2, 2.0)
smallDamp    = min(1, 0.25 + weightKg / 1.5)

runPulse   = 0.18 × sensitivity × (0.7 + 0.6 × weightStress) × smallDamp
calmPulse  = 0.07 × sensitivity × smallDamp
landPulse  = 0.05 / (0.7 + 0.6 × weightStress) × (0.9 + reelSize/14000)
relaxTick  = 0.010 + clamp(fightDrag/10, 0, 0.5) × 0.02
```

`smallDamp` is why a 50 g perch no longer loads the rod like a kilo fish.

Passively, tension falls by `relaxTick` per tick and progress bleeds back by 0.0008 per tick — leave the rod alone entirely and you make no headway.

### Runs

A **running** fish loads the tackle by itself: `tension += runPulse × 0.12 × (1 − 0.55 × fatigue)` every tick, unless you have the drag open. Riding out a run with a locked drag is not free.

Runs are scheduled from a pattern-specific chance, duration and interval (see [fight patterns](#fight-patterns)), all damped by fatigue:

```
runChance   = (1 − 0.65 × fatigue) × patternChance
runDuration = max(6, patternDuration × (1 − 0.5 × fatigue))
```

The number of runs a fish has in it:

```
runs = max(1, profile.runs)
     + pattern bonus (aggressive +2, relentless +3, sounding +3, greyhounding +2; burst floors at 2)
     + 1 if the specimen is over 2 kg
     + predator bonus: 1 + round(clamp(weightKg/4, 0, 1.5))
     + 2 if foul-hooked
```

### Fatigue

The fight wears the fish down — fast while it runs, slowly between:

```
fatigue += running ? 1/(20 × (4 + 2.5 × weightKg)) : one fifth of that      (capped at 1)
```

Full burn-out takes about **4 + 2.5 × kg seconds of running**. A perch gasses out in seconds; a carp holds for half a minute; big game outlasts your drag instead. A tired fish pulls softer, comes in faster, runs less often and for less long — the boss bar says *"— tiring"* and that is your cue it is ready for the net.

### Predator fights

Any fish caught on an **Active** rod, or any species that **requires a leader**, fights fast and mean. With `wAmp = clamp(weightKg / 4, 0, 1.5)`:

| Change | Value |
|---|---|
| Run pulse | `× (1.35 + 0.25 × wAmp)` |
| Break tolerance | × 0.92 |
| Landing speed | × 0.85 |
| Calm pulse | × 1.1 |
| Tension relief | × 0.92 |
| Extra runs | `1 + round(wAmp)` |
| Head-shake chance | `0.008 + 0.011 × wAmp` per tick |
| Fight timeout | +300 ticks |

A **head-shake** is a brief violent thrash between runs: a 6–11 tick mini-run, `+1.25 × runPulse` tension and −0.03 progress. Keep cranking through it and it snaps you off; the answer is to ease off for a moment.

An **Ultralight** rod stacks on top: run pulse ×1.15, break tolerance ×0.92, landing ×0.88, head-shake chance +0.006. Fragile finesse tackle is the hardest fight in the game.

### The final surge

The moment progress reaches **85 %**, one guaranteed run fires — 28–41 ticks, or 38–51 for a trophy — announced as *"A last dash at the bank!"* with a drag scream. Ease off or snap.

### Timeout

```
fightTimeout = clamp(700 + weightKg × 80 + patternBonus, 700, 3000) ticks
patternBonus: burst 300, greyhounding 400, relentless 500, sounding 700
             (+300 more for any predator fight)
```

Run out the clock and the fish is simply gone.

### Fishing together

- Anyone within **12 blocks** is added to your boss bar and watches the fight.
- A friend can **net your fish**: empty main hand, crouching, within about 3.5 blocks, with the fish at 85 %+ and not running. It counts for you and earns the helper **+5 angler XP**. *"%s netted your fish!"*

### Session guards

Your line drops if you switch to a different stack in that hand, or if you walk more than **40 blocks** from where the rig landed.

---

## Fight patterns

Seven patterns, set per species in its profile.

| Pattern | Run chance | Run length (ticks) | Gap between runs | Extra runs | Signature |
|---|---|---|---|---|---|
| **steady** | 0.60 | 25–44 | 50–99 | — | The honest slog. |
| **active_then_passive** | 0.90 early / 0.25 past halfway | 30–49 early / 14–23 late | 30–59 / 90–149 | — | Fights hard early, tires late. |
| **burst** | 0.70 | 50–89 | 80–159 | floors at 2 | Long, powerful surges with real breathing space. |
| **aggressive** | 0.95 | 22–39 | 25–54 | +2 | Constant short, sharp lunges. |
| **relentless** | 0.97 | 40–74 | 20–44 | +3 | Barely a breath between charges; breaks the surface with a big boil and a leap on every run. |
| **greyhounding** | 0.85 | 18–31 | 35–64 | +2 | **Jumps.** See below. |
| **sounding** | 0.92 | 60–109 | 70–129 | +3 | **Dives.** See below. |

### Sounding (deep dives)

While the fish is down, **it takes line**: progress drains 0.0035 per tick. Every 25 run ticks the long drag scream plays and you are told *"Sounding — pump it back between dives!"* You cannot fight a dive; you wait it out and pump the line back in the gap.

Species: **Yellowfin tuna, Swordfish, Sturgeon, Halibut**.

### Greyhounding (jump series)

Between runs, once progress is past 5 %, a **1.2 % chance per tick** of a full-body breach. *"It jumps — give slack, do not reel!"* opens a **15-tick** window. Crank inside that window and there is a **35 % chance the hook rips straight out**: *"Thrown the hook on the jump..."*. The answer to a jump is the open drag.

Species: **Mahi-mahi, Blue marlin, Sailfish, Mako shark, Atlantic salmon**.

---

## Losing the fish

### Tackle stress (the line break)

Crossing the tension limit does **not** snap the line instantly. Every tick over the limit rolls a break chance that grows with how far over you are and how long you have held it there:

```
overshoot   = (tension − breakTension) / breakTension
overStress += 0.015 + 0.02 × overshoot            (capped at 2.0)
chance      = min(0.5, (0.008 + 0.055 × overshoot + 0.028 × overStress) × breakSensitivity)
```

Ease off and `overStress` recovers at 0.02 per tick (0.05 with the drag open). **With the drag open the line cannot break at all.**

The first tick over the limit warns you once — *"Tackle at its limit — ease off!"* plus a rod creak. Surviving the abuse still frays the line: +1 wear every 15 over-limit ticks.

Brief spikes are survivable; cranking through a whole run is not.

### What a break costs you

```
loseRigChance = clamp(0.30 × (weightKg × 1.5 / lineStrainKg), 0.05, 0.30)
```

- **Rig kept** (the common case with a strong line) — *"Off! The fish threw the hook"*. Fish lost, tackle intact.
- **Rig lost** — *"Snapped under ~N kg! Fish and rig lost"*. The message names the load in kilograms (`tension × requiredKg`), which is the best tackle lesson in the game. Your rig slot is emptied.

A strong line against a light fish nearly always just throws the hook (5 % floor). A weak line against a heavy fish loses the whole rig at the 30 % cap.

### Leader bite-off

Rolled once, at the moment of hooking, for the seven toothy species:

```
chance = 0.75 × (1 − leaderProtection)
```

A bite-off **always** loses the rig: *"Bitten through the line — use a leader!"* See [leaders](reels-and-lines.md#leaders).

### Catastrophic failure

A flat **0.3 %** chance on every hook-up that the line simply parts and the whole rig is gone, fish and all — independent of weight or strain. It grants the hidden advancement *"It Was DEFINITELY Huge"*.

### Blunt-hook slip

Before the fight starts, a blunt hook can fail to set: `(bluntness/100) × 0.5`. *"Empty strike — the hook was blunt"*.

---

## Snags, foul-hooking and bycatch

### Snags

On the default preset, per fishing action:

- **3 %** — a dead snag. The rig is **lost**: *"Snagged solid — lost the rig"* (+6 line wear).
- **7 %** — a recoverable snag. You tug it free: *"Snagged! Pulled it free"* (+3 line wear).

For still tackle (float and bottom) the roll happens at the moment of the "bite". For lure rods it is rolled up front at the cast and strikes somewhere in the **second half** of the retrieve, as the lure nears the bank.

Ice fishing into a clean hole almost never snags: a flat **1 %**, and always the recoverable kind.

### Foul-hooking

A moving lure can snag a passing fish in the body. **1 % per retrieve**, lure rods only — a snag on still tackle can never be a foul-hook. Rolled up front and struck somewhere across the retrieve.

*"Foul-hooked! Snagged by the body — this'll be tough"*. The consequences:

- +2 runs and run pulse ×1.3 — a sideways fish fights harder and longer.
- On a pole, a tighter and faster pull-out window.
- The fish **does not count**: no journal record, no XP, no quest progress, no advancements. The item is tagged *"Foul-hooked — not counted"*.

### Bycatch

On still tackle only (a moving lure doesn't pick up bottom junk), a "bite" can turn out to be something else:

- **4.5 %** — junk: Leather Boots, Bone, Kelp ×1–2, Ink Sac, or Stick ×1–3.
- **1.3 %** — treasure: Name Tag, Saddle, Experience Bottle ×3–5, Gold Ingot ×1–3, or Emerald ×2–4. Worth **+15 angler XP**.

It does not surface instantly. It hangs on the line as a short heavy pull — one dead-weight tug and about 1.5–2 seconds of dragging — indistinguishable from a big lazy fish until it breaks the surface. **The line cannot snap on it.** On a pole it uses the same pull-out timing as a fish.

Fishing up the Leather Boots earns the advancement *"Catch of the Decade"*.

---

## What you land

Every catch is a unique item carrying its own species, weight and length, so no two stack.

### Weight

```
k = log((mean − min)/(max − min)) / log(0.5)        clamped 0.5 … 8.0
k += max(0, 0.85 − matchScore) × 2                  (a crude setup catches the small end)
u = random^k
weight = min + (max − min) × u
```

Solving the exponent from the profile's `mean` makes it the true **median catch** — half of all your fish of that species land under it. Profiles with no explicit mean fall back to the classic `k = 2.4` big-fish-are-rare curve.

Two floors can raise the roll: a weighed [live baitfish](rigs-and-baits.md#live-bait-carries-a-weight) (≈6×) and a [tied lure's weight](tackle-station.md#2-the-lure-size-filter-lures-only) (≈8×), both capped at 60 % of the range.

### Length

Length follows weight by the real allometric law — mass grows with volume, so length tracks the **cube root** of weight, anchored to the species' own length range, with ±2 % natural variation. The endpoints map exactly: min weight → min length, max weight → max length.

The fish's in-world icon scales from its length alone: `clamp(length/50, 0.45, 8.0)`, so a 50 cm fish renders one block long and a 380 cm mako nearly eight.

### Trophies

```
chance = 0.04 × clamp(matchScore / 0.85, 0.2, 1.0) + 0.01 per Angler's Luck rank
```

A trophy is rolled from the **top 15 %** of the species' weight range. It shimmers like enchanted gear, prefixes its name with ★, gives **triple XP**, fights accordingly, and needs a near-ideal kit to appear at all.

### Prime grade

```
prime threshold = ceil(species maxWeight × 0.7)
```

Any legal catch at or above 70 % of its species' maximum is graded **prime** — *"Prime specimen — the buyer wants this"*. Only prime fish can be sold to the [fisherman](villager.md), and every prime landing nudges that species' market price down.

### Legendary fish

Seven species hide **one named specimen per server**, rolled at the moment of landing:

| Species | Name | Weight | Chance per landing |
|---|---|---|---|
| Pike | Queen of the Snags | 14 kg | 0.6 % |
| Wild Carp | Grandfather Sazan | 17.5 kg | 0.6 % |
| Catfish | Master of the Pit | 150 kg | 0.5 % |
| Yellowfin tuna | Old Ridgeback | 140 kg | 0.6 % |
| Blue marlin | The Leviathan | 380 kg | 0.8 % |
| Sturgeon | The Tsar-Fish | 145 kg | 0.4 % |
| Mako shark | The Megalodon | 390 kg | 0.4 % |

The actual weight varies ±3 % around the listed figure, the length is the species maximum, and it is always a trophy. The catch is **broadcast to the whole server** in bold gold and recorded forever — there will never be another. A foul-hooked fish can never be the legendary one.

---

## Gear wear

| Part | Wears from | Effect | Fix |
|---|---|---|---|
| **Line** (0–100 %) | Casts, snags, breaks, over-limit ticks | `strain × (1 − 0.55 × wear/100)` — 45 % of strain at 100 % | None. Replace it. |
| **Hook** (0–100 %) | 1 per hook-set, on your sharpest hook | `(wear/100) × 0.5` chance of an empty strike | **Whetstone** resets it to 0 |
| **Rod blank** (durability) | 1 per hook-set; a third of the bar if the blank cracks under an overweight rig | Breaks at zero | **Anvil**, with the rod's own material |

Line wear per cast is fractional (0.1, or 0.06 on fluorocarbon) — a single cast usually adds nothing at all, and the remainder becomes a probability, so wear creeps up over many casts. Full details in [Reels and lines](reels-and-lines.md#line-wear).

The engine always reads and dulls your **sharpest** hook — you fish with your best one.

---

## Difficulty

The mod ships with four presets. The **realism** preset is the default and the one every number on this page assumes.

| Setting | arcade | realism | hardcore |
|---|---|---|---|
| Phantom bite rate (alarms) | ×0.2 | ×1.0 | ×1.6 |
| Line-break sensitivity | ×0.3 | ×1.0 | ×1.7 |
| Spot depletion | ×0.3 | ×1.0 | ×1.6 |
| Leader bite-off chance | 0.30 | 0.75 | 0.95 |
| Line wear rate | ×0.3 | ×1.0 | ×1.7 |
| Hook wear rate | ×0.3 | ×1.0 | ×1.7 |
| Snag chance | ×0.3 | ×1.0 | ×1.6 |
| Foul-hook chance | ×0.4 | ×1.0 | ×1.6 |

Values not driven by the preset: trophy chance 4 %, frenzy speed ×3, bait and groundbait consumption on, junk bycatch 4.5 %, treasure bycatch 1.3 %.

> There is currently **no config file** — the values are plain defaults in code, fixed at *realism*, and a per-platform loader is still to come. A modpack cannot change the preset yet without patching.

## See also

- [Water and conditions](water-and-conditions.md) — the world half of the bite engine
- [Species](species.md) · [Progression](progression.md)
- [Rods](rods.md) · [Reels and lines](reels-and-lines.md) · [Rigs and baits](rigs-and-baits.md)
