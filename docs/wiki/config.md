# Configuration

River Fishing reads one file: **`config/riverfishing.json`**. It is written for you on first launch with every knob at its default, and it is read **once, during mod init** — a change needs a game restart, and nothing reloads it in play.

## Where the file lives

The loader's own config folder — the ordinary `config/` next to the client instance or the server jar, the same one every other mod uses. Both loaders and every supported Minecraft version use that one path.

- Missing file → written at the defaults, with a line in the log. A config nobody can find is barely better than none.
- Unreadable file (a stray comma, a broken bracket) → one warning in the log and the mod runs on defaults. It is *not* rewritten: delete it and restart to get a clean one.
- Every number is **clamped** to its range on load, and the clamp is logged with the key, your value and what it became. A typo cannot produce a negative chance or a multiplier that breaks the fight maths.
- An unknown `preset` name → warning, and `realism` is used.

Missing keys simply keep their defaults, so a three-line file with nothing but `preset` in it is perfectly valid.

---

## The preset

`preset` sets all nine of the frustrating mechanics at once, and it is the only line most packs ever need to touch. Four values: `arcade`, `realism`, `hardcore`, `custom`.

**`realism` is the default**, and it is what every number elsewhere in this wiki assumes. `arcade` cuts the harsh multipliers to roughly a third. `hardcore` raises them by 60–70 %. `custom` ignores the table below and reads the nine individual values instead.

| Knob | Key | arcade | realism | hardcore |
|---|---|---|---|---|
| Phantom bite rate | `phantom` | ×0.2 | ×1.0 | ×1.6 |
| Line-break sensitivity | `break_sensitivity` | ×0.3 | ×1.0 | ×1.7 |
| Spot depletion | `depletion` | ×0.3 | ×1.0 | ×1.6 |
| Leader bite-off chance | `leader_biteoff` | 0.30 | 0.75 | 0.95 |
| Line wear rate | `line_wear` | ×0.3 | ×1.0 | ×1.7 |
| Hook wear rate | `hook_wear` | ×0.3 | ×1.0 | ×1.7 |
| Snag chance | `snag` | ×0.3 | ×1.0 | ×1.6 |
| Foul-hook chance | `foul` | ×0.4 | ×1.0 | ×1.6 |
| Spook rate | `spook` | ×0.35 | ×1.0 | ×1.6 |

> **Higher is harsher for all nine.** That is the direction to remember when you write your own numbers.

---

## The nine custom values

Read **only** when `preset` is `"custom"`. Anything else and the file's numbers here are ignored entirely.

| Key | Default | Range | What it changes |
|---|---|---|---|
| `phantom` | 1.0 | 0 – 5 | The per-tick false-alarm chance on a [rod pod](blocks.md#rod-pods) with a bite alarm fitted. At 0 an alarm only ever sounds for a real take. |
| `break_sensitivity` | 1.0 | 0 – 5 | Both halves of losing a fish: it **divides** the tackle's break tolerance, and it multiplies the per-tick snap chance while you are over the limit. The single biggest knob for how forgiving the [fight](fishing-mechanics.md#the-fight) is. |
| `depletion` | 1.0 | 0 – 5 | How much fishing pressure a cast and a kept fish add to the chunk. At 0 a spot never [fishes out](water-and-conditions.md#spot-depletion). |
| `leader_biteoff` | 0.75 | 0 – 1 | A **chance, not a multiplier**: how often a species that requires a leader bites clean through the line. Reduced by the leader you actually fitted. |
| `line_wear` | 1.0 | 0 – 5 | Line wear per cast, base 0.1 points × this. Fluorocarbon wears at 0.6 of the rate. A fraction of a point becomes a probability, so wear still accumulates over many casts. |
| `hook_wear` | 1.0 | 0 – 5 | How much the sharpest hook blunts on a hooked fish (and on a strike a blunt hook slipped). `round(2 × rate ÷ 1.5)` — so at ×1.0 it is 1 point, at hardcore 2, and at arcade the rounding lands on **0: hooks never blunt**. |
| `snag` | 1.0 | 0 – 5 | Scales both snag odds per fishing action: 3 % dead (you lose the rig) and 10 % total (the rest tug free). Ice fishing keeps its flat, unscaled 1 %. |
| `foul` | 1.0 | 0 – 5 | Scales the flat 1 % per spinning retrieve of [foul-hooking](fishing-mechanics.md#foul-hooking) a fish in the body. Lure rods only. |
| `spook` | 1.0 | 0 – 5 | How sharply fish react to the angler — it scales every noise source: footsteps, sprinting, jumping, wading, a moving boat, your shadow on the water, a block broken nearby, and the cast landing. **At 0 the mechanic is switched off entirely.** |

---

## Values the preset never touches

These are read directly whatever the preset is.

| Key | Default | Range | What it changes |
|---|---|---|---|
| `trophy_fraction` | 0.90 | 0.5 – 0.999 | **Where the trophy bar sits** in a species' weight range — not a chance. 0.90 means the top tenth of the range, and a specimen at or above it is a trophy. **Raise it for rarer trophies.** |
| `frenzy_speed` | 3.0 | 1.0 – 20.0 | How much a [feeding frenzy](water-and-conditions.md#feeding-frenzy) speeds things up: it divides the bite wait and multiplies the bite speed the [Fish Finder](tools.md#fish-finder) reports. Higher is *kinder*. |
| `bycatch_junk` | 0.045 | 0 – 1 | Chance a still-tackle bite turns out to be junk instead of a fish. |
| `bycatch_treasure` | 0.013 | 0 – 1 | Chance of a treasure [bycatch](fishing-mechanics.md#bycatch). Rolled on the same dice, immediately after junk. Neither applies to lure rods or to a foul-hooked fish. |
| `consume_bait` | `true` | — | Whether natural bait is eaten on the strike. Lures are never consumed either way. |
| `consume_groundbait` | `true` | — | Whether a feeder cage empties one groundbait per cast to feed the spot. |
| `update_check` | `true` | — | The client-side version digest printed in chat on joining a world. Off is silent. |

---

## The file as written

This is exactly what lands on disk on first launch, header comment and all:

```json
{
  "_comment": [
    "River Fishing config. Delete this file to regenerate it with the defaults.",
    "'preset' picks the multipliers for the frustrating mechanics and is the only knob most",
    "packs need: arcade | realism | hardcore | custom. The nine values below it are used",
    "ONLY when preset is 'custom'. Everything from 'trophy_chance' down is read directly and",
    "applies whatever the preset is.",
    "Higher is harsher for: phantom, break_sensitivity, depletion, leader_biteoff, line_wear,",
    "hook_wear, snag, foul, spook. 'spook' at 0 switches off the fish reacting to you at all.",
    "'trophy_fraction' is WHERE the trophy bar sits in a species' weight range, not a chance:",
    "0.90 means the top tenth of the range. Raise it for rarer trophies."
  ],

  "preset": "realism",

  "phantom": 1.0,
  "break_sensitivity": 1.0,
  "depletion": 1.0,
  "leader_biteoff": 0.75,
  "line_wear": 1.0,
  "hook_wear": 1.0,
  "snag": 1.0,
  "foul": 1.0,
  "spook": 1.0,

  "trophy_fraction": 0.90,
  "frenzy_speed": 3.0,
  "bycatch_junk": 0.045,
  "bycatch_treasure": 0.013,

  "consume_bait": true,
  "consume_groundbait": true,
  "update_check": true
}
```

> The comment says *"everything from `trophy_chance` down"*. There is no `trophy_chance` key — it means `trophy_fraction`, the next line but one. The comment is cosmetic; the loader never reads it.

## See also

- [Fishing mechanics](fishing-mechanics.md) — what the nine multipliers are multiplying
- [Water and conditions](water-and-conditions.md) — spot depletion and the frenzy
- [Reels and lines](reels-and-lines.md) · [Tools](tools.md)
