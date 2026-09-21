# -*- coding: utf-8 -*-
"""§fly-reel: the strip works exactly like the spinning retrieve. The fly's water spot stays where the cast
put it (the drift still carries it), and every strip adds a metre's worth to a 0..1 reel fraction the line
sync already carries as `progress` — the client draws the end lerped from the spot to the bank, the way
it draws a lure coming in. The rise check, the metres and the set's spray use the lerped position; the
fight starts from where the fly was (landProgress = the reel fraction, capped at 0.85, as on spinning).
Idempotent; run on each tree.  py tools/patches/p_fly_strip_reel.py [tree ...]"""
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


STRIP = '''    private static void flyStrip(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        // §fly-reel: the spot stays; the strip is a fraction of the way in, exactly as the spinning retrieve
        double dx = sp.getX() - (session.target.getX() + 0.5), dz = sp.getZ() - (session.target.getZ() + 0.5);
        double dist = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        session.flyReel = Math.min(1.0, session.flyReel + 1.0 / dist);
        if (session.flyReel >= 0.85) {
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.fly_pickup"));
            return;
        }
        session.flyDrag = Math.max(0, session.flyDrag - 30);   // a strip straightens the line a little
        session.flyDragWarned = false;
        net.minecraft.world.phys.Vec3 at = flyAt(sp, session);
        boolean stripFly = session.ctx != null && session.ctx.tied != null
                && (session.ctx.tied.template() == com.riverfishing.tackle.TiedDesign.Template.STREAMER
                || session.ctx.tied.template() == com.riverfishing.tackle.TiedDesign.Template.SHRIMP);
        if (!session.flyOnRise && session.biteAtTick > now) {
            session.biteAtTick = Math.max(now + 8, session.biteAtTick - (stripFly ? 20 : 6));
            if (stripFly && level.getRandom().nextInt(3) == 0) {   // the follow: a swirl behind the fly
                level.sendParticles(ParticleTypes.BUBBLE_POP, at.x - dx / dist * 0.8, at.y + 0.95, at.z - dz / dist * 0.8, 4, 0.2, 0.0, 0.2, 0.0);
            }
        }
        level.playSound(null, BlockPos.containing(at), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.25f, 1.4f);
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, session.target, (float) session.flyReel, session.lineColor, session.floatKind, false));
        flyCheckRise(sp, level, session, now);
        FlyCast.drift(sp, session.flyDriftState, flyMetres(sp, session), session.flyOnRise);
    }
'''

FLY_AT = '''    /** §fly-reel: where the fly actually is — the water spot, pulled toward the angler by what has been stripped. */
    private static net.minecraft.world.phys.Vec3 flyAt(ServerPlayer sp, FishingSession session) {
        double x = Mth.lerp(session.flyReel, session.target.getX() + 0.5, sp.getX());
        double z = Mth.lerp(session.flyReel, session.target.getZ() + 0.5, sp.getZ());
        return new net.minecraft.world.phys.Vec3(x, session.target.getY(), z);
    }

    private static int flyMetres(ServerPlayer sp, FishingSession session) {
        net.minecraft.world.phys.Vec3 at = flyAt(sp, session);
        double dx = sp.getX() - at.x, dz = sp.getZ() - at.z;
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }
'''


def run(tree):
    j = lambda *a: os.path.join(tree, *a)
    sub(j(FS), "    public int flyDriftState = -1;", "    public double flyReel;         // §fly-reel: how far the strips have brought the fly in, 0..1 of the cast\n    public int flyDriftState = -1;", "session")
    fm = j(FM); s = rd(fm)
    if "§fly-reel: the spot stays" not in s:
        a = s.index("    private static void flyStrip(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {")
        b = s.index("\n    }\n", a) + len("\n    }\n")
        s = s[:a] + STRIP + s[b:]
        a = s.index("    private static int flyMetres(ServerPlayer sp, FishingSession session) {")
        b = s.index("\n    }\n", a) + len("\n    }\n")
        s = s[:a] + FLY_AT + s[b:]
        wr(fm, s)
    sub(fm, "        FlyRises.Rise rise = FlyRises.take(sp, session.target);", "        FlyRises.Rise rise = FlyRises.take(sp, BlockPos.containing(flyAt(sp, session)));   // §fly-reel", "rise at")
    sub(fm, "            } else if (session.rodClass == RodClass.ACTIVE && session.retrieveMax > 0) {\n                visProgress = Mth.clamp((float) session.retrieveTicks / session.retrieveMax, 0f, 1f);\n            } else {\n                visProgress = 0f;\n            }",
        "            } else if (session.rodClass == RodClass.ACTIVE && session.retrieveMax > 0) {\n                visProgress = Mth.clamp((float) session.retrieveTicks / session.retrieveMax, 0f, 1f);\n            } else if (session.ctx != null && session.ctx.rod == RodType.FLY) {\n                visProgress = (float) session.flyReel;   // §fly-reel\n            } else {\n                visProgress = 0f;\n            }", "refresh")
    sub(fm, "        session.landProgress = (session.rodClass == RodClass.ACTIVE && session.retrieveMax > 0)\n                ? Mth.clamp((double) session.retrieveTicks / session.retrieveMax, 0.0, 0.85)\n                : 0.0;",
        "        session.landProgress = (session.rodClass == RodClass.ACTIVE && session.retrieveMax > 0)\n                ? Mth.clamp((double) session.retrieveTicks / session.retrieveMax, 0.0, 0.85)\n                : session.ctx != null && session.ctx.rod == RodType.FLY ? Mth.clamp(session.flyReel, 0.0, 0.85)   // §fly-reel: the fight starts where the fly was\n                : 0.0;", "hook-up progress")
    sub(fm, "            double sx = session.target.getX() + 0.5, sy = session.target.getY() + 1.1, sz = session.target.getZ() + 0.5;\n",
        "            net.minecraft.world.phys.Vec3 fa = flyAt(sp, session);   // §fly-reel: the spray where the fly is, not where it landed\n            double sx = fa.x, sy = fa.y + 1.1, sz = fa.z;\n", "set spray")
    print("  patched", os.path.basename(tree.rstrip("/\\")))


for t in (sys.argv[1:] or TREES): run(t)
