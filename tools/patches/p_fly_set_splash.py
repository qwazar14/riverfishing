# -*- coding: utf-8 -*-
"""§fly-set: the set on a fly rod is the show — the fish comes out of the water with a boil and a slap the
whole bank sees. The hook-up throws a burst of spray at the fly and marks the fish as breaching for 16
ticks, which the line sync already knows how to draw (the greyhounding jump's own flag, on its own timer,
so the jump's "do not reel" rule stays out of it). Idempotent; run on each tree."""
import io, os, sys

TREES = [r"C:/Users/Qwazar/VS Code Projects/fishing mod", r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
FM = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"
FS = "common/src/main/java/com/riverfishing/fishing/FishingSession.java"


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what):
    s = rd(path)
    if new in s: return
    assert s.count(old) == 1, "%s @ %s (%d)" % (what, path, s.count(old))
    wr(path, s.replace(old, new, 1))


SET = '''        session.fightStartTick = now;
        session.nextRunAt = now + 30 + random.nextInt(40);
        // §fly-set: on a fly rod the set is the show — the fish comes out of the water with a boil and a
        // slap, and the whole bank sees it (the breach is drawn by the line sync for the next 16 ticks)
        if (session.ctx != null && session.ctx.rod == RodType.FLY) {
            session.showFishUntil = now + 16;
            double sx = session.target.getX() + 0.5, sy = session.target.getY() + 1.1, sz = session.target.getZ() + 0.5;
            level.sendParticles(ParticleTypes.SPLASH, sx, sy, sz, 70 + session.lengthCm, 0.7, 0.5, 0.7, 0.45);
            level.sendParticles(ParticleTypes.BUBBLE_POP, sx, sy - 0.1, sz, 20, 0.5, 0.2, 0.5, 0.1);
            level.playSound(null, session.target, SoundEvents.DOLPHIN_JUMP, SoundSource.PLAYERS, 1.0f, 0.9f);
            level.playSound(null, session.target, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
'''


def run(tree):
    j = lambda *a: os.path.join(tree, *a)
    sub(j(FS), "    public long jumpWindowEnd;", "    public long jumpWindowEnd;\n    public long showFishUntil;   // §fly-set: the fish drawn breaching at the set, on its own timer", "session")
    fm = j(FM)
    sub(fm, "        session.fightStartTick = now;\n        session.nextRunAt = now + 30 + random.nextInt(40);\n", SET, "hook-up")
    sub(fm, "        if (now % 5 == 0 || now <= session.jumpWindowEnd || now - session.fightStartTick < 2) {",
        "        if (now % 5 == 0 || now <= session.jumpWindowEnd || now <= session.showFishUntil || now - session.fightStartTick < 2) {   // §fly-set", "cadence")
    sub(fm, "                    now < session.jumpWindowEnd, session.runTicksLeft > 0 && !session.course.isRun(),",
        "                    now < session.jumpWindowEnd || now < session.showFishUntil, session.runTicksLeft > 0 && !session.course.isRun(),   // §fly-set", "jumping flag")
    print("  patched", os.path.basename(tree.rstrip("/\\")))


for t in (sys.argv[1:] or TREES): run(t)
