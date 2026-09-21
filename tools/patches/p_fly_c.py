# -*- coding: utf-8 -*-
"""§fly, stream C: the rise, the set and the hatch — every edit to an EXISTING file.

    py -X utf8 tools/patches/p_fly_c.py <repo root>

Idempotent: every inserted block carries a `§fly` marker and is skipped when the marker is already
there, so a rerun is a no-op. `sub1` asserts its anchor occurs exactly once and exits 1 naming the
anchor otherwise. New files (engine/Hatch.java, the wiki pages, the patch notes, the check, the lang
JSON) are written directly and are not this script's business.

Reads `com.riverfishing.component.RodType.FLY` as if it exists — stream A adds it.
"""
import io, os, sys

if len(sys.argv) < 2:
    sys.exit("usage: p_fly_c.py <repo root>")
ROOT = sys.argv[1]
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")
changed = []


def rd(p):
    return io.open(p, encoding="utf-8").read()


def wr(p, s):
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub1(s, old, new, what):
    n = s.count(old)
    if n != 1:
        print("p_fly_c: anchor for %s occurs %d times, not once:\n%s" % (what, n, old))
        sys.exit(1)
    return s.replace(old, new, 1)


def patch(rel, marker, edits):
    """edits: list of (old, new, what). Skipped whole when `marker` is already in the file."""
    p = os.path.join(ROOT, rel)
    s = rd(p)
    if marker in s:
        print("  %s: already patched" % rel)
        return
    for old, new, what in edits:
        s = sub1(s, old, new, what)
    wr(p, s)
    changed.append(rel)
    print("  %s: patched" % rel)


# ---- 1. the context carries the hatch ----------------------------------------------------------
patch("common/src/main/java/com/riverfishing/engine/BiteContext.java", "§fly", [(
    "    public com.riverfishing.tackle.TiedDesign.Analysis tied;\n",
    "    public com.riverfishing.tackle.TiedDesign.Analysis tied;\n"
    "    /** §fly: what is hatching over this water right now — null off a fly rod, or when nothing is. */\n"
    "    public Hatch hatch;\n",
    "BiteContext.tied")])

# ---- 2. the engine scores the fly against it ----------------------------------------------------
patch("common/src/main/java/com/riverfishing/engine/BiteEngine.java", "§fly", [(
    "        if (c.tied != null) best *= c.tied.affinity(p.group);\n",
    "        if (c.tied != null) best *= c.tied.affinity(p.group);\n"
    "        // §fly: match the hatch — the right kind at the right size is the fly they are taking today\n"
    "        if (c.tied != null && c.rod == com.riverfishing.component.RodType.FLY) best *= Hatch.factor(c.hatch, c.tied);\n",
    "BiteEngine.baitScore tied affinity")])

# ---- 3. FishingManager: the hatch is read, shown, and the rise replaces the float QTE -------------
FM = "common/src/main/java/com/riverfishing/fishing/FishingManager.java"
p = os.path.join(ROOT, FM)
s = rd(p)
if "startFlyRise" in s:
    print("  %s: already patched" % FM)
else:
    # the weather line sits in buildContext AND reEvaluate; the hatch follows it in both
    WEATHER = "        ctx.weather = level.isThundering() ? Weather.THUNDER : (level.isRaining() ? Weather.RAIN : Weather.CLEAR);\n"
    HATCH = WEATHER + "        ctx.hatch = ctx.rod == RodType.FLY ? Hatch.now(ctx.season, ctx.time, ctx.weather, ctx.water) : null;   // §fly\n"
    n = s.count(WEATHER)
    if n != 2:
        print("p_fly_c: the weather line occurs %d times, expected 2:\n%s" % (n, WEATHER))
        sys.exit(1)
    s = s.replace(WEATHER, HATCH)

    s = sub1(s, "import com.riverfishing.engine.BiteEngine;\n",
             "import com.riverfishing.engine.BiteEngine;\n"
             "import com.riverfishing.engine.Hatch;   // §fly\n", "FishingManager import BiteEngine")

    # the rise instead of the float strike QTE on a fly rod
    s = sub1(s,
             "                if (session.rodClass == RodClass.FLOAT && session.reelSize > 0) {\n"
             "                    startFloatTiming(sp, session, now);\n",
             "                if (session.ctx != null && session.ctx.rod == RodType.FLY) {   // §fly: the rise — a timed set, not a random zone\n"
             "                    startFlyRise(sp, session, now);\n"
             "                } else if (session.rodClass == RodClass.FLOAT && session.reelSize > 0) {\n"
             "                    startFloatTiming(sp, session, now);\n",
             "FishingManager float timing branch")

    # the hatch on the water while the line waits
    s = sub1(s,
             "            } else if (now % 20 == 0) {\n"
             "                level.sendParticles(ParticleTypes.FISHING,\n",
             "            } else if (now % 20 == 0) {\n"
             "                if (session.ctx != null && session.ctx.hatch != null) session.ctx.hatch.particles(level, session.target);   // §fly: the water shows the hatch\n"
             "                level.sendParticles(ParticleTypes.FISHING,\n",
             "FishingManager waiting particles")

    # the miss: which way the set was wrong, and the fish is put down
    s = sub1(s,
             "            eatBait(sp, session);   // §consumables: a mistimed strike still loses the bait\n"
             "            endSession(sp, session);\n"
             "            actionbar(sp, Component.translatable(\"message.riverfishing.mistimed\")",
             "            String miss = \"message.riverfishing.mistimed\";\n"
             "            if (session.ctx != null && session.ctx.rod == RodType.FLY) {\n"
             "                // §fly: before the green the fly came out of its mouth, after it the fish spat it —\n"
             "                // and either way it is put down: that lie is quiet for twenty seconds or so\n"
             "                miss = m < session.floatZoneCenter - session.floatZoneHalf\n"
             "                        ? \"message.riverfishing.fly_too_fast\" : \"message.riverfishing.fly_too_slow\";\n"
             "                SpookData.of(level).disturb(level, session.target, 0.5, 3.0, now);\n"
             "            }\n"
             "            eatBait(sp, session);   // §consumables: a mistimed strike still loses the bait\n"
             "            endSession(sp, session);\n"
             "            actionbar(sp, Component.translatable(miss)",
             "FishingManager activeStrike miss")

    # startFlyRise, beside the other timing starters
    s = sub1(s,
             "    private static void clearFloatTiming(ServerPlayer sp) {\n",
             "    /**\n"
             "     * §fly: the rise. A trout comes up under the fly and turns down with it; lift before it has\n"
             "     * turned and the fly comes out of its mouth, lift after it has felt the hook and it has spat it.\n"
             "     * So the strike bar is TIME here, not a sweep: the marker climbs once across the window and the\n"
             "     * green is the species' own delay — a grayling turns fast, a chub slowly. The centre is never\n"
             "     * random (beginTiming's is), which is why this does not call it.\n"
             "     */\n"
             "    private static void startFlyRise(ServerPlayer sp, FishingSession session, long now) {\n"
             "        FishProfile p = FishProfileManager.get().byId(session.species);\n"
             "        double aggression = p != null ? p.fightAggression : 0.5;\n"
             "        int delayMin = (int) Math.round(4 + (1 - aggression) * 8);\n"
             "        int delayMax = delayMin + 8 + (int) Math.round((1 - aggression) * 6);\n"
             "        int window = delayMax + 6;\n"
             "        session.floatPeriod = 2 * window;                 // marker(t) = t / window: the bar is a clock\n"
             "        float c = (delayMin + delayMax) / 2f / window;\n"
             "        // §skills FINESSE widens the green here as it does on the float\n"
             "        float g = (delayMax - delayMin) / 2f / window * (1f + (float) AnglerSkills.strikeZoneBonus(sp));\n"
             "        float o = g + 0.06f;\n"
             "        session.floatZoneCenter = c;\n"
             "        session.floatZoneHalf = g;\n"
             "        session.floatOrangeHalf = o;\n"
             "        session.floatStart = now;\n"
             "        session.biteWindowEnd = now + window;\n"
             "        ModNetwork.toPlayer(sp, new FloatTimingPacket(true, now, window, session.floatPeriod, c - g, c + g, c - o, c + o));\n"
             "        // the fish itself, so every client can draw it coming up under the fly\n"
             "        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, session.target, 0f, session.lineColor,\n"
             "                session.floatKind, true, 0f, 0f, false, false, (byte) 0,\n"
             "                session.species == null ? \"\" : session.species.getPath(), session.weightG, session.lengthCm,\n"
             "                false, false, 0f, false));\n"
             "        // the ring: the take is on the surface, and everyone on the bank sees it\n"
             "        sp.serverLevel().sendParticles(ParticleTypes.FISHING, session.target.getX() + 0.5, session.target.getY() + 1.0,\n"
             "                session.target.getZ() + 0.5, 8, 0.3, 0.0, 0.3, 0.05);\n"
             "    }\n"
             "\n"
             "    private static void clearFloatTiming(ServerPlayer sp) {\n",
             "FishingManager clearFloatTiming")
    wr(p, s)
    changed.append(FM)
    print("  %s: patched" % FM)

# ---- 4. the client keeps the fish through the rise and faces it away from the angler --------------
patch("common/src/main/java/com/riverfishing/client/ClientLineState.java", "§fly", [
    ("        public boolean wasInAir;         // for the splash on the way out and the way back\n",
     "        public boolean wasInAir;         // for the splash on the way out and the way back\n"
     "        /** §fly: client game time the rise began, -1 when none is on — the body climbs over its first eight ticks. */\n"
     "        public long riseStart = -1;\n",
     "ClientLineState.Line wasInAir"),
    ("            if (!fighting || species.isEmpty()) {\n"
     "                fx *= Math.max(0f, 1f - dt * 4f);",
     "            if (!fighting || species.isEmpty()) {\n"
     "                if (biting && !species.isEmpty()) heading = (float) Math.atan2(fwdZ, fwdX);   // §fly: a rising fish faces away from the angler, under the fly\n"
     "                fx *= Math.max(0f, 1f - dt * 4f);",
     "ClientLineState.tickFish idle branch"),
    ("        line.species = p.species;        // §hooked-fish\n",
     "        // §fly: the 40-tick refresh names no fish; during a rise it must not wipe the one the rise sent\n"
     "        if (!p.species.isEmpty() || !p.biting || p.fighting) line.species = p.species;   // §hooked-fish\n"
     "        long t = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;\n"
     "        if (p.biting && !p.fighting) { if (line.riseStart < 0) line.riseStart = t; } else line.riseStart = -1;\n",
     "ClientLineState.accept species"),
])

# ---- 5. the renderer draws the rise ---------------------------------------------------------------
patch("common/src/main/java/com/riverfishing/client/HookedFishRenderer.java", "§fly", [
    ("        if (!state.fighting || state.species.isEmpty() || mc.level == null) return;\n",
     "        if (!(state.fighting || state.biting) || state.species.isEmpty() || mc.level == null) return;   // §fly: drawn on the rise too\n",
     "HookedFishRenderer.draw gate"),
    ("        float time = mc.level.getGameTime() + pt;\n",
     "        float time = mc.level.getGameTime() + pt;\n"
     "        // §fly: the rise — the body comes up under the fly, nose up, over the first eight ticks of the take\n"
     "        boolean rising = state.biting && !state.fighting;\n"
     "        double riseY = 0.0;\n"
     "        float risePitch = 0f;\n"
     "        if (rising) {\n"
     "            float rt = state.riseStart < 0 ? 1f : Mth.clamp((time - state.riseStart) / 8f, 0f, 1f);\n"
     "            riseY = Mth.lerp(rt, -0.4f, -0.05f);\n"
     "            risePitch = -35f;\n"
     "        }\n",
     "HookedFishRenderer.draw time"),
    ("        pose.translate(at.x, at.y, at.z);\n",
     "        pose.translate(at.x, at.y + riseY, at.z);   // §fly\n",
     "HookedFishRenderer.draw translate"),
    ("        pose.mulPose(Axis.ZP.rotationDegrees(state.pitch + Mth.sin(time * 0.05f) * 2f));\n",
     "        pose.mulPose(Axis.ZP.rotationDegrees((rising ? risePitch : state.pitch) + Mth.sin(time * 0.05f) * 2f));   // §fly\n",
     "HookedFishRenderer.draw pitch"),
])

# ---- 6. the wiki: the page is published, and each README lists it ---------------------------------
patch("tools/gen_wiki_bundle.py", '"fly-fishing"', [(
    '"shoal", "ice-fishing",\n',
    '"shoal", "ice-fishing", "fly-fishing",   # §fly\n',
    "gen_wiki_bundle GROUPS ice-fishing")])

ROWS = {
    "docs/wiki/README.md": (
        "| [Ice fishing](ice-fishing.md) | Drilling, jigging, what bites under the ice |\n",
        "| [Fly fishing](fly-fishing.md) | The fly rod, the rhythm cast, the drift and the mend, the rise and the delayed set, the hatch table, which fish take a fly |\n"),
    "docs/wiki/ru/README.md": (
        "| [Подлёдная ловля](ice-fishing.md) | Бурение, игра мормышкой, кто берёт из-подо льда |\n",
        "| [Нахлыст](fly-fishing.md) | Нахлыстовое удилище, заброс в ритме, проводка и мендинг, выход рыбы и подсечка с задержкой, таблица вылета, кто берёт мушку |\n"),
    "docs/wiki/uk/README.md": (
        "| [Підлідна ловля](ice-fishing.md) | Буріння, гра мормишкою, хто бере з-під льоду |\n",
        "| [Нахлист](fly-fishing.md) | Нахлистове вудилище, закид у ритмі, проводка й мендинг, вихід риби та підсічка із затримкою, таблиця вильоту, хто бере мушку |\n"),
}
for rel, (anchor, row) in ROWS.items():
    # the link itself is the marker: a table row has nowhere to carry a comment
    patch(rel, "(fly-fishing.md)", [(anchor, anchor + row, rel + " ice-fishing row")])

print("done: %d file(s) changed" % len(changed))
