# -*- coding: utf-8 -*-
"""§cull-gate: a species the electrofisher took out of a water cannot be thrown back in by anyone.

    py -X utf8 tools/patches/p_cullgate.py <root>

The `cull` guide page has said since 0.8.0 that a removed species "will no longer settle when stocked —
one check rather than four". The 0.9.0 stocking rework broke that without meaning to: release() judges
a fish by habitatContext(), which deliberately sets communityFactor to null so the fit is the WATER's
answer alone — and the cull check lives inside that lambda, so it never ran. A culled species went on
the ledger, ran the settle clock, and tickSettle() called markStocked(), which clears the cull. Any
player with a pair of roach could undo the operator's decision, and the operator would never know.

The second half of the same hole: stockedPresence() falls through to the TEMPORARY stock for a species
that is not stocked here, and setCulled() only drops the species from the stocked set. So a recently
stocked species that was then culled kept biting at up to a quarter rate off its surplus until it
decayed. "The fish stops biting" was not quite true either.

Two guards, one per shared function, both messages already in the lang files:

  release()          — a culled species is refused at the bank, red "X no longer lives in this water",
                       before the fit is even computed. Same shape as the hostile-water refusal.
  stockedPresence()  — a culled species has no presence, temporary or not.

Every bite path, the net, the finder and the shoal all read through stockedPresence(), so one line
covers the lot — the audit found the net carrying its own copy of the cull check, and it can keep it.
"""
import io, os, sys

ROOT = sys.argv[1]
P = os.path.join(ROOT, "common/src/main/java/com/riverfishing/fishing/FishingManager.java")

s = io.open(P, encoding="utf-8").read()
if "cull-gate" in s:
    print("  already patched")
    sys.exit(0)

# ---- 1. the bank refuses a culled species ---------------------------------------------------------
old = """        net.minecraft.network.chat.Component name = fishName(p.id);
        double fit = BiteEngine.environmentScore(p, habitatContext(level, pos, body));
        if (fit <= 0) {"""
assert old in s, "release() moved — the fit line is not where it was"
s = s.replace(old, """        net.minecraft.network.chat.Component name = fishName(p.id);
        // §cull-gate: a species the electrofisher took out of this water is refused at the bank, the
        // way hostile water refuses one. habitatContext() nulls the community factor on purpose (the
        // fit is the water's answer alone), so the cull check inside it never runs here — and without
        // this, a culled species could be stocked back in by anyone, settle, and clear its own cull.
        if (StockedData.get(level).isCulled(region, id)) {
            if (thrower != null) {
                thrower.displayClientMessage(Component.translatable("message.riverfishing.cull_done", name)
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        double fit = BiteEngine.environmentScore(p, habitatContext(level, pos, body));
        if (fit <= 0) {""", 1)

# ---- 2. …and it has no presence, temporary or otherwise ------------------------------------------
old = """        return id -> {
            String s = id.getPath();
            if (!stocked.isStocked(region, s)) return Math.min(1.0, pd.surplusAround(cx, cz, s, level.getGameTime()));"""
assert old in s, "stockedPresence() lambda moved"
s = s.replace(old, """        return id -> {
            String s = id.getPath();
            // §cull-gate: culled is culled. setCulled() only drops the species from the stocked set, so
            // without this line a species stocked and THEN culled kept biting off its temporary surplus.
            if (stocked.isCulled(region, s)) return 0.0;
            if (!stocked.isStocked(region, s)) return Math.min(1.0, pd.surplusAround(cx, cz, s, level.getGameTime()));""", 1)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("  FishingManager: release() refuses a culled species; stockedPresence() gives it none")
print("done")
