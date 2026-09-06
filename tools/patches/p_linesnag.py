# -*- coding: utf-8 -*-
"""§line-snag: a fish that takes the line across a block is held there until you move to clear it.

    py -X utf8 tools/patches/p_linesnag.py <root>

Asked for by name (idkwho0457_07869): "fish can wrap around structure and terrain". Since §hooked-fish
the fish has a position; this gives the LINE one. Every fourth tick of a fight the server estimates
where the fish is (the same course arithmetic the client draws it by, un-eased) and clips the segment
from the angler's eye to it through the world's colliders. A hit more than two and a half blocks from
the angler is a snag:
  * the line CHAFES — line wear every check, and a running fish rubs tension on;
  * the fish is HELD — a crank gains nothing and loads the line 1.6x;
  * it clears itself when the segment does: move along the bank, change the side you hold, or wait
    for the fish's next course.
No text. A scrape every second, and on the client the string is drawn KINKED over the block it is
caught on (the client clips the same segment), which is the whole read.
"""
import io, os, sys

ROOT = sys.argv[1]
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")


def patch(path, pairs):
    s = io.open(path, encoding="utf-8").read()
    for old, new in pairs:
        assert s.count(old) == 1, (path, old[:70])
        s = s.replace(old, new, 1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(s)


S = os.path.join(J, "fishing/FishingSession.java")
if "lineSnagged" in io.open(S, encoding="utf-8").read():
    print("  already patched"); sys.exit(0)
patch(S, [("    public boolean outclassed;",
           "    public boolean lineSnagged;     // §line-snag: the string lies across a block between the tip and the fish\n"
           "    public boolean outclassed;")])

M = os.path.join(J, "fishing/FishingManager.java")
patch(M, [
    ("        session.landProgress = Math.max(0.0, session.landProgress - 0.0008);\n",
     "        session.landProgress = Math.max(0.0, session.landProgress - 0.0008);\n"
     "        tickSnag(sp, level, session, now);   // §line-snag\n"),
    ("        session.tension += (inRun ? session.runTensionPulse : session.calmTensionPulse) * tired * wrongWay\n",
     "        session.tension += (inRun ? session.runTensionPulse : session.calmTensionPulse) * tired * wrongWay\n"
     "                * (session.lineSnagged ? 1.6 : 1.0)   // §line-snag: winding against a block rubs\n"),
    ("(session.outclassed ? 0.35 : 1.0), 0.0, 1.0);",
     "(session.outclassed ? 0.35 : 1.0)\n"
     "                        * (session.lineSnagged ? 0.0 : 1.0), 0.0, 1.0);   // §line-snag: held — nothing comes"),
    ("                    (float) session.fatigue));\n",
     "                    (float) session.fatigue, session.lineSnagged));\n"),
    ("    private static void tickFight(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {\n",
     '''    /**
     * §line-snag: where the fish is, as the server reckons it — the same arithmetic the client eases
     * its drawing toward ({@code ClientLineState.Line.tickFish}), without the easing. Good enough to
     * ask the world whether a block stands between the rod and it.
     */
    private static net.minecraft.world.phys.Vec3 fishEstimate(ServerPlayer sp, FishingSession session) {
        BlockPos t = session.target;
        net.minecraft.world.phys.Vec3 water = new net.minecraft.world.phys.Vec3(t.getX() + 0.5, t.getY() + 0.95, t.getZ() + 0.5);
        net.minecraft.world.phys.Vec3 bank = sp.position().add(sp.getViewVector(1f).scale(1.2)).add(0, 0.1, 0);
        net.minecraft.world.phys.Vec3 end = water.lerp(bank, Mth.clamp(session.landProgress * 0.85, 0.0, 0.9));
        double fx = water.x - sp.getX(), fz = water.z - sp.getZ(), fl = Math.sqrt(fx * fx + fz * fz);
        if (fl > 1e-3) { fx /= fl; fz /= fl; } else { fx = 1; fz = 0; }
        double sx = -fz, sz = fx;
        double reach = Mth.clamp(2.5 + session.lengthCm / 50.0, 2.0, 6.0) * (1.0 - 0.45 * session.fatigue);
        boolean running = session.runTicksLeft > 0;
        double ox = fx * 0.6, oy = -0.35, oz = fz * 0.6;
        if (running) {
            switch (session.course) {
                case LEFT -> { ox = -sx * reach; oz = -sz * reach; oy = -0.5; }
                case RIGHT -> { ox = sx * reach; oz = sz * reach; oy = -0.5; }
                case DOWN -> { ox = fx * reach * 0.5; oz = fz * reach * 0.5; oy = -reach * 0.8; }
                case UP -> { ox = fx * reach * 0.4; oz = fz * reach * 0.4; oy = -0.1; }
                default -> { ox = fx * reach * 0.7; oz = fz * reach * 0.7; oy = -0.6; }
            }
        }
        return end.add(ox, oy, oz);
    }

    /**
     * §line-snag: every fourth tick, is there a block between the rod and the fish? A hit within two
     * and a half blocks of the angler is the pier under his own feet and does not count — a line
     * always leaves over the edge of something. Past that it is a snag: the line chafes, a running
     * fish rubs it, and until the segment clears (feet, the side you hold, the fish's next course)
     * a crank gains nothing. A scrape once a second says so; the client draws the kink.
     */
    private static void tickSnag(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        if (now % 4 != 0) return;
        net.minecraft.world.phys.Vec3 from = sp.getEyePosition(), to = fishEstimate(sp, session);
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
                from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
        boolean snagged = hit.getType() == HitResult.Type.BLOCK
                && hit.getLocation().subtract(sp.getX(), hit.getLocation().y, sp.getZ()).horizontalDistanceSqr() > 2.5 * 2.5
                && !level.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER);
        session.lineSnagged = snagged;
        if (!snagged) return;
        addLineWear(sessionRod(sp, session), (int) Math.max(1, Math.round(2 * lineWearScaled())));
        if (session.runTicksLeft > 0) session.tension += 0.015;
        if (now % 20 == 0) {
            level.playSound(null, hit.getBlockPos(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.35f, 1.5f);
        }
    }

    private static void tickFight(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
'''),
])

P = os.path.join(J, "network/LineSyncPacket.java")
patch(P, [
    ("    public final float fatigue;\n", "    public final float fatigue;\n    public final boolean snagged;   // §line-snag: the string lies across a block\n"),
    ("                running, course, \"\", 0, 0, false, false, 0f);\n", "                running, course, \"\", 0, 0, false, false, 0f, false);\n"),
    ("                          String species, int weightG, int lengthCm, boolean jumping, boolean shaking,\n                          float fatigue) {\n",
     "                          String species, int weightG, int lengthCm, boolean jumping, boolean shaking,\n                          float fatigue, boolean snagged) {\n        this.snagged = snagged;\n"),
    ("        buf.writeFloat(fatigue);\n", "        buf.writeFloat(fatigue);\n        buf.writeBoolean(snagged);\n"),
    ("                buf.readFloat());\n", "                buf.readFloat(), buf.readBoolean());\n"),
])

C = os.path.join(J, "client/ClientLineState.java")
patch(C, [
    ("        public boolean jumping, shaking;\n", "        public boolean jumping, shaking, snagged;\n"),
    ("        line.fatigue = p.fatigue;\n", "        line.fatigue = p.fatigue;\n        line.snagged = p.snagged;\n"),
    ("            float k = Math.min(1f, dt * (running ? 2.6f : 1.6f));\n",
     "            // §line-snag: held on a block — the body stays where the line stopped it, and strains\n"
     "            float k = snagged ? 0f : Math.min(1f, dt * (running ? 2.6f : 1.6f));\n"),
    ("            if (shaking) {   // a head-shake: a hard sideways shudder, eight a second\n",
     "            if (shaking || snagged) {   // a head-shake, or straining on a snag: a hard sideways shudder\n"),
])

L = os.path.join(J, "client/LineRenderer.java")
s = io.open(L, encoding="utf-8").read()
is26 = "line(vc, m, nrm, prev, p, cr, cg, cb, alpha, width);" in s
call = "line(vc, m, nrm, a, b, cr, cg, cb, alpha, width);" if is26 else "line(sv, m, nrm, a, b, cr, cg, cb, alpha);"
old = "            Vec3 prev = end.add(0, hangOffset(state, dy, 0.0, time), 0);\n"
assert s.count(old) == 1
s = s.replace(old, '''            // §line-snag: the string is caught on a block — draw it KINKED over the point it rubs,
            // two straight legs, which is exactly what a snagged line looks like from the bank.
            Vec3 kink = state.snagged ? snagPoint(mc, tip, end) : null;
            if (kink != null) {
                Vec3 a = end, b = kink; ''' + call + '''
                a = kink; b = tip; ''' + call + '''
            } else {
''' + old, 1)
old = "                prev = p;\n            }\n"
assert s.count(old) == 1
s = s.replace(old, old + "            }   // §line-snag\n", 1)
old = "    static Vec3 lineEnd(Minecraft mc, Player player, ClientLineState.Line state, float pt) {\n"
assert s.count(old) == 1
s = s.replace(old, '''    /** §line-snag: where the string meets the block, clipped the way the server clipped it. */
    static Vec3 snagPoint(Minecraft mc, Vec3 tip, Vec3 end) {
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(tip, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit.getLocation() : null;
    }

''' + old, 1)
io.open(L, "w", encoding="utf-8", newline="\n").write(s)
print("  line-snag: the server clips the line, the crank is held, the client draws the kink (%s)" % ("26" if is26 else "1.x"))
