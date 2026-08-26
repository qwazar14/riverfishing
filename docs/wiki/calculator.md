# Catch calculator

Pick a fish and the weight you are after; the calculator answers **where, when, on what and with
what** — for that size, not for the species in general. A nine-kilo carp and a one-kilo carp want a
different line, a different feed and a different wait, and no table on this wiki can say that,
because a table has one row per species and the answer moves with the weight.

Everything it prints is read from that species' profile and computed with the game's own formulas:
the pull is `max(0.5, strength × (1 + fm) × 2)`, where `fm` is the weight itself up to 20 kg and
`20 × (kg/20)^0.55` above it — a giant pulls hard, not proportionally hard. Line strain is
`100 × d² × factor`, the livebait floor is six times the bait's weight and the lure floor eight, and
only feed coarser than half fraction shifts the size roll. The two bait floors are printed raw: in
the game neither can floor the roll above 60 % of the species' weight range, and the livebait one
only applies to species that rate livebait at 0.5 or better.

> **This page is interactive on the published wiki.** On GitHub you are reading the markdown source,
> where nothing can run — open the [published wiki](https://qwazar14.github.io/riverfishing/) for the
> working version.

<!-- CALCULATOR -->

## How to read it

- **Where** is habitat, not advice: a water shallower than the depth given, or narrower than the
  width, does not hold this fish at all.
- **When** names the best season, hour and weather. Fishing outside them is not forbidden, just
  slower — the numbers behind it are on [Species reference](species-reference.md).
- **What on** lists the top baits with their scores. Bait is 30 % of a bite, the single largest
  term — see [Fishing mechanics](fishing-mechanics.md).
- **Gear** turns your target weight into a line: the first figure is what the fish pulls, the second
  the thinnest line that holds it, the third the one that leaves you room to make mistakes.
- **Groundbait** gives the pair this species wants, and tells you whether your target is big enough
  to need a coarse table — see [Groundbait](groundbait.md).
