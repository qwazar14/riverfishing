# -*- coding: utf-8 -*-
"""§fly-2 (0.10.0): fly fishing, rebuilt to be read at a glance and to be fun.
  The cast: hold use and the rod false-casts on its own; every stop carries two more metres; release on the
  green forward stop (tight), a little early (open, short), or with the rod behind you (piled, a slap).
  A left-click on a stop hauls two metres more. Nothing else can go wrong.
  The rising fish: while a fly rod is in the hand the water in front shows feeding fish (a ring, a sip);
  a fly landing or drifting within 2.5 blocks of one is ON THE FISH and the take comes in a second or two.
  The drift: a status line under the crosshair (dead drift / dragging — mend / line straight), LMB mends,
  a tap of use strips, a HOLD of use picks the line up and goes straight into the next false casts.
  The take: the same species clock, a wider green, and the bar says SET.
FlyCast.java, FlyRises.java and FlyCastClient.java are written whole (from the main tree); the rest is
anchored edits. Idempotent; run on each tree.  py tools/patches/p_fly2.py [tree ...]"""
import io, json, os, re, shutil, sys
from collections import OrderedDict

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"
LANG = "common/src/main/resources/assets/riverfishing/lang"


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what, count=1):
    s = rd(path)
    if new in s: return
    assert old in s, what + " @ " + path
    wr(path, s.replace(old, new, count))


def resub(path, pattern, new, what):
    s = rd(path)
    if new.strip()[:60] in s: return
    s2, n = re.subn(pattern, lambda m: new, s, count=1, flags=re.S)
    assert n == 1, what + " @ " + path
    wr(path, s2)


FLY_LANDED = '''    /**
     * §fly-2: what the delivery did to the water, and whether it came down on a rising fish. A tight loop
     * lands soft; an open one is short; a piled one slaps the water and the spot is wary for a moment.
     */
    private static void flyLanded(ServerPlayer sp, FishingSession session, int quality) {
        if (session == null) return;
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if (quality == 0) {
            session.flyTight = true;   // §progression
            actionbar(sp, Component.translatable("message.riverfishing.fly_tight").withStyle(ChatFormatting.GREEN));
        } else if (quality == 1) {
            actionbar(sp, Component.translatable("message.riverfishing.fly_open").withStyle(ChatFormatting.YELLOW));
        } else {
            SpookTracker.onCastLanded(level, session.target, 0.15);
            actionbar(sp, Component.translatable("message.riverfishing.fly_pile").withStyle(ChatFormatting.RED));
        }
        flyCheckRise(sp, level, session, now);
        FlyCast.drift(sp, 0, flyMetres(sp, session), session.flyOnRise);
    }

    /** §fly-2: the fly over a feeding fish — that fish, and the take inside a couple of seconds. */
    private static void flyCheckRise(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        if (session.flyOnRise || session.bitten) return;
        FlyRises.Rise rise = FlyRises.take(sp, session.target);
        if (rise == null) return;
        session.species = rise.species;
        session.flyOnRise = true;
        session.biteAtTick = now + 15 + level.getRandom().nextInt(30);
        actionbar(sp, Component.translatable("message.riverfishing.fly_on_fish").withStyle(ChatFormatting.GREEN));
        level.playSound(null, session.target, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.6f);
        FlyCast.drift(sp, 0, flyMetres(sp, session), true);
    }

    private static int flyMetres(ServerPlayer sp, FishingSession session) {
        double dx = sp.getX() - (session.target.getX() + 0.5), dz = sp.getZ() - (session.target.getZ() + 0.5);
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

'''

FLY_DRIFT = '''    private static void flyDrift(ServerLevel level, ServerPlayer sp, FishingSession session, long now) {
        if (session.flyDriftEnd == 0) session.flyDriftEnd = now + 240;
        if (!session.flyStraight && now >= session.flyDriftEnd) {
            session.flyStraight = true;
            actionbar(sp, Component.translatable("message.riverfishing.fly_straight").withStyle(ChatFormatting.GRAY));
        }
        if (!session.flyStraight && now % 10 == 0) {
            BlockPos t = session.target;
            net.minecraft.world.phys.Vec3 flow = level.getFluidState(t).getFlow(level, t);
            double fl = Math.sqrt(flow.x * flow.x + flow.z * flow.z);
            if (fl >= 0.05) {
                // Still water leaves the line lying slack; only a current bows it.
                session.flyDrag = Math.min(100, session.flyDrag + 3);
                if (session.flyDrag > 60 && !session.flyDragWarned) {
                    session.flyDragWarned = true;
                    actionbar(sp, Component.translatable("message.riverfishing.fly_drag").withStyle(ChatFormatting.YELLOW));
                }
                BlockPos next = findWaterColumn(level, t.getX() + 0.5 + Math.round(flow.x / fl),
                        t.getY() + 1.0, t.getZ() + 0.5 + Math.round(flow.z / fl));
                if (next != null && !next.equals(t)) {
                    session.target = next;
                    ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, next, 0f,
                            session.lineColor, session.floatKind, false));
                    flyCheckRise(sp, level, session, now);   // §fly-2: the drift can carry the fly onto a fish
                }
            }
        }
        // §fly-2: the status line — sent when it changes, and every two seconds as a heartbeat
        int state = session.flyStraight ? 2 : session.flyDrag > 60 ? 1 : 0;
        if (state != session.flyDriftState || now % 40 == 0) {
            session.flyDriftState = state;
            FlyCast.drift(sp, state, flyMetres(sp, session), session.flyOnRise);
        }
        // A dragging fly and a straight line are the dead lure's rule: the take keeps getting pushed out —
        // unless the fly is already over a fish that has decided.
        if (!session.flyOnRise && (session.flyDrag > 60 || session.flyStraight) && now >= session.biteAtTick - 5) {
            session.biteAtTick = now + 25;
        }
    }

'''

FLY_USE = '''    /**
     * §fly-2: a left-click with the line on the water is the MEND — the line flipped upstream, the drag
     * gone; a third mend in one drift slaps the water.
     */
    private static void flyMend(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        session.flyDrag = 0;
        session.flyMends++;
        session.flyDragWarned = false;
        level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.35f, 1.6f);
        if (session.flyMends >= 3) SpookTracker.onCastLanded(level, session.target, 0.15);
        session.flyDriftState = session.flyStraight ? 2 : 0;
        FlyCast.drift(sp, session.flyDriftState, flyMetres(sp, session), session.flyOnRise);
    }

    /**
     * §fly-2: a TAP of use strips a metre of line in — the fly comes toward you — and works the fly: a
     * streamer or a shrimp is fished by the strip (each one brings the take closer, and a fish shows
     * behind it now and then); a dry fly or a nymph only twitches. At your feet the cast is over.
     */
    private static void flyStrip(ServerPlayer sp, ServerLevel level, FishingSession session, long now) {
        double dx = sp.getX() - (session.target.getX() + 0.5), dz = sp.getZ() - (session.target.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dz * dz);
        BlockPos next = dist > 2.5
                ? findWaterColumn(level, session.target.getX() + 0.5 + dx / dist, session.target.getY() + 1.0, session.target.getZ() + 0.5 + dz / dist)
                : null;
        if (next == null) {
            endSession(sp, session);
            actionbar(sp, Component.translatable("message.riverfishing.fly_pickup"));
            return;
        }
        session.target = next;
        session.flyDrag = Math.max(0, session.flyDrag - 30);   // a strip straightens the line a little
        session.flyDragWarned = false;
        boolean stripFly = session.ctx != null && session.ctx.tied != null
                && (session.ctx.tied.template() == com.riverfishing.tackle.TiedDesign.Template.STREAMER
                || session.ctx.tied.template() == com.riverfishing.tackle.TiedDesign.Template.SHRIMP);
        if (!session.flyOnRise && session.biteAtTick > now) {
            session.biteAtTick = Math.max(now + 8, session.biteAtTick - (stripFly ? 20 : 6));
            if (stripFly && level.getRandom().nextInt(3) == 0) {   // the follow: a swirl behind the fly
                level.sendParticles(ParticleTypes.BUBBLE_POP, next.getX() + 0.5 - dx / dist * 0.8, next.getY() + 0.95,
                        next.getZ() + 0.5 - dz / dist * 0.8, 4, 0.2, 0.0, 0.2, 0.0);
            }
        }
        level.playSound(null, next, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.25f, 1.4f);
        ModNetwork.toTracking(sp, new LineSyncPacket(sp.getId(), true, next, 0f, session.lineColor, session.floatKind, false));
        flyCheckRise(sp, level, session, now);
        FlyCast.drift(sp, session.flyDriftState, flyMetres(sp, session), session.flyOnRise);
    }

    /** §fly-2: a fly line on the water with nothing biting — the state in which a click is a hold. */
    public static boolean flyCalm(ServerPlayer sp) {
        FishingSession s = SESSIONS.get(sp.getUUID());
        return s != null && s.ctx != null && s.ctx.rod == RodType.FLY && !s.bitten && !s.fighting;
    }

    /** §fly-2: use let go after a short hold on a calm fly line — the strip. True when it was ours. */
    public static boolean flyTap(ServerPlayer sp) {
        if (!flyCalm(sp)) return false;
        FishingSession s = SESSIONS.get(sp.getUUID());
        if (s.flyPickedUp) return true;   // the hold already picked the line up; nothing to strip
        ServerLevel level = sp.serverLevel();
        flyStrip(sp, level, s, level.getGameTime());
        return true;
    }

    /** §fly-2: the left-click — a haul while the rod false-casts, a mend while the line drifts. */
    public static void flyBeat(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if (FlyCast.isCasting(sp)) { FlyCast.beat(sp, now); return; }
        if (!flyCalm(sp)) return;
        flyMend(sp, level, SESSIONS.get(sp.getUUID()), now);
    }

    private static boolean isFlyRod(ItemStack stack) {
        return stack.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY;
    }

'''

TICK_ADD = '''    public static void tick(ServerPlayer sp) {
        // §fly-2: the rising fish, while a fly rod is in the hand — session or no session
        if (sp.tickCount % 20 == 0 && (isFlyRod(sp.getMainHandItem()) || isFlyRod(sp.getOffhandItem()))) {
            ServerLevel lv = (ServerLevel) sp.level();
            FlyRises.tick(lv, sp, lv.getGameTime());
        }
'''

PICKUP = '''        long now = level.getGameTime();
        // §fly-2: holding use on a calm fly line picks it up and goes straight into the false casts; the
        // release then delivers the next cast — one motion, the way it is done
        if (session.ctx != null && session.ctx.rod == RodType.FLY && !session.bitten && !session.fighting && !session.flyPickedUp
                && sp.isUsingItem() && isFlyRod(sp.getUseItem()) && sp.getTicksUsingItem() >= 6) {
            session.flyPickedUp = true;
            endSession(sp, session);
            FlyCast.begin(sp, now);
            return;
        }
'''

LANG_NEW = {
    "en_us": OrderedDict([
        ("message.riverfishing.fly_tight", "Tight loop — released on the forward stop"),
        ("message.riverfishing.fly_open", "Loop opened — a little short"),
        ("message.riverfishing.fly_pile", "Piled up — the line slapped the water"),
        ("message.riverfishing.fly_on_fish", "On the fish! Wait for the take…"),
        ("message.riverfishing.fly_drag", "Drag — left-click to mend"),
        ("hud.riverfishing.fly_dead_drift", "Dead drift"),
        ("hud.riverfishing.fly_drag", "Dragging — left-click to mend"),
        ("hud.riverfishing.fly_straight", "Line's straight below — hold right-click to pick up and cast"),
        ("hud.riverfishing.fly_on_fish", "On the fish — wait for the take"),
        ("hud.riverfishing.fly_controls", "LMB mend · tap RMB strip · hold RMB pick up"),
        ("hud.riverfishing.fly_set", "SET! Click in the green"),
        ("gui.riverfishing.fly_hint", "Hold — the rod false-casts on its own · release in the green"),
        ("gui.riverfishing.fly_haul", "Left-click on a stop: haul, +2 m"),
        ("gui.riverfishing.fly_full", "Line's all out — release in the green"),
        ("gui.riverfishing.fly_hauls", "%s hauls"),
    ]),
    "ru_ru": OrderedDict([
        ("message.riverfishing.fly_tight", "Тугая петля — отпустили на переднем стопе"),
        ("message.riverfishing.fly_open", "Петля раскрылась — легло чуть ближе"),
        ("message.riverfishing.fly_pile", "Шнур лёг кучей и шлёпнул по воде"),
        ("message.riverfishing.fly_on_fish", "Мушка над рыбой! Ждите поклёвку…"),
        ("message.riverfishing.fly_drag", "Тянет — ЛКМ: перекладка шнура"),
        ("hud.riverfishing.fly_dead_drift", "Свободный проплыв"),
        ("hud.riverfishing.fly_drag", "Тянет — ЛКМ: перекладка шнура"),
        ("hud.riverfishing.fly_straight", "Шнур вытянулся — держите ПКМ: подъём и новый заброс"),
        ("hud.riverfishing.fly_on_fish", "Над рыбой — ждите поклёвку"),
        ("hud.riverfishing.fly_controls", "ЛКМ перекладка · ПКМ коротко — подтяжка · ПКМ держать — подъём"),
        ("hud.riverfishing.fly_set", "ПОДСЕЧКА! Клик на зелёном"),
        ("gui.riverfishing.fly_hint", "Держите — удилище само машет · отпустите на зелёном"),
        ("gui.riverfishing.fly_haul", "ЛКМ на стопе: подтяг, +2 м"),
        ("gui.riverfishing.fly_full", "Весь шнур в воздухе — отпустите на зелёном"),
        ("gui.riverfishing.fly_hauls", "подтягов: %s"),
    ]),
    "uk_ua": OrderedDict([
        ("message.riverfishing.fly_tight", "Туга петля — відпустили на передньому стопі"),
        ("message.riverfishing.fly_open", "Петля розкрилась — лягло трохи ближче"),
        ("message.riverfishing.fly_pile", "Шнур ліг купою й ляснув по воді"),
        ("message.riverfishing.fly_on_fish", "Мушка над рибою! Чекайте на поклювання…"),
        ("message.riverfishing.fly_drag", "Тягне — ЛКМ: перекладання шнура"),
        ("hud.riverfishing.fly_dead_drift", "Вільний сплав"),
        ("hud.riverfishing.fly_drag", "Тягне — ЛКМ: перекладання шнура"),
        ("hud.riverfishing.fly_straight", "Шнур витягнувся — тримайте ПКМ: підйом і новий закид"),
        ("hud.riverfishing.fly_on_fish", "Над рибою — чекайте на поклювання"),
        ("hud.riverfishing.fly_controls", "ЛКМ перекладання · ПКМ коротко — підтяжка · ПКМ тримати — підйом"),
        ("hud.riverfishing.fly_set", "ПІДСІЧКА! Клік на зеленому"),
        ("gui.riverfishing.fly_hint", "Тримайте — вудилище само махає · відпустіть на зеленому"),
        ("gui.riverfishing.fly_haul", "ЛКМ на стопі: підтяг, +2 м"),
        ("gui.riverfishing.fly_full", "Увесь шнур у повітрі — відпустіть на зеленому"),
        ("gui.riverfishing.fly_hauls", "підтягів: %s"),
    ]),
}
LANG_DROP = ["message.riverfishing.fly_splash", "message.riverfishing.fly_knot", "gui.riverfishing.fly_hint2", "gui.riverfishing.fly_beats"]


def client_dialect(name, s):
    if name != "rf26": return s
    s = s.replace("import net.minecraft.client.gui.GuiGraphics;", "import net.minecraft.client.gui.GuiGraphicsExtractor;")
    s = s.replace("GuiGraphics g", "GuiGraphicsExtractor g")
    s = s.replace("net.minecraft.resources.ResourceLocation", "net.minecraft.resources.Identifier")
    s = s.replace("        if (mc.level == null || mc.options.hideGui) return;\n",
                  "        //? if <26.2 {\n        if (mc.level == null || mc.options.hideGui) return;\n        //?} else {\n        /*if (mc.level == null || mc.gui.hud.isHidden()) return;\n        *///?}\n")
    s = s.replace("g.pose().pushPose();", "g.pose().pushMatrix();").replace("g.pose().popPose();", "g.pose().popMatrix();")
    s = re.sub(r"g\.pose\(\)\.translate\(([^;]*), 0\);", r"g.pose().translate(\1);", s)
    s = re.sub(r"g\.pose\(\)\.scale\(([^;]*), 1f\);", r"g.pose().scale(\1);", s)
    s = s.replace("g.blit(BAR, x, y, FW, FH, 0f, 0f, FW, FH, 128, 48);", "g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, x, y, 0f, 0f, FW, FH, 128, 48);")
    s = s.replace("g.blit(BAR, px, py, 48, 16, 0f, 32f, 48, 16, 128, 48);", "g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, px, py, 0f, 32f, 48, 16, 128, 48);")
    s = s.replace("g.drawCenteredString(", "g.centeredText(").replace("g.drawString(", "g.text(")
    return s


def run(tree):
    name = os.path.basename(tree.rstrip("/\\"))
    j = lambda *a: os.path.join(tree, *a)
    d26 = (lambda t: t.replace("sp.serverLevel()", "sp.level()")) if name == "rf26" else (lambda t: t)
    # ---- whole files ----
    if tree != MAIN:
        shutil.copyfile(os.path.join(MAIN, J, "fishing/FlyCast.java"), j(J, "fishing/FlyCast.java"))
        fr = rd(os.path.join(MAIN, J, "fishing/FlyRises.java"))
        if name == "rf26": fr = fr.replace("net.minecraft.resources.ResourceLocation", "net.minecraft.resources.Identifier").replace("ResourceLocation", "Identifier")
        wr(j(J, "fishing/FlyRises.java"), fr)
        wr(j(J, "client/FlyCastClient.java"), client_dialect(name, rd(os.path.join(MAIN, J, "client/FlyCastClient.java"))))
    # ---- FishingManager ----
    fm = j(J, "fishing/FishingManager.java")
    sub(fm, "    private static BlockPos findWaterColumn(ServerLevel level, double x, double yStart, double z) {",
        "    static BlockPos findWaterColumn(ServerLevel level, double x, double yStart, double z) {   // §fly-2: FlyRises places its fish with it", "findWaterColumn")
    resub(fm, r"    /\*\*\n     \* §fly: what the delivery did to the water\..*?\n    private static void flyLanded\(ServerPlayer sp, FishingSession session, int quality\) \{.*?\n    \}\n\n", d26(FLY_LANDED), "flyLanded")
    resub(fm, r"    private static void flyDrift\(ServerLevel level, ServerPlayer sp, FishingSession session, long now\) \{.*?\n    \}\n\n", d26(FLY_DRIFT), "flyDrift")
    resub(fm, r"    /\*\*\n     \* §fly-strip: a click on a drifting fly line STRIPS.*?\n    private static void flyUse\(ServerPlayer sp, ServerLevel level, FishingSession session, long now\) \{.*?\n    \}\n\n", d26(FLY_USE), "flyUse")
    sub(fm, "                flyUse(sp, level, session, now);               // §fly: mend, or sneak to pick up\n",
        "                flyStrip(sp, level, session, now);             // §fly-2: a click that reached here is a strip\n", "handleRodUse fly")
    sub(fm, "    public static void tick(ServerPlayer sp) {\n", TICK_ADD, "tick rises")
    s = rd(fm)
    if "session.flyPickedUp = true;\n            endSession(sp, session);" not in s:
        i = s.index("    public static void tick(ServerPlayer sp) {")
        k = s.index("        long now = level.getGameTime();\n", i)
        s = s[:k] + PICKUP + s[k + len("        long now = level.getGameTime();\n"):]
        wr(fm, s)
    sub(fm, "        TROLL_LAST.remove(uuid);\n", "        TROLL_LAST.remove(uuid);\n        FlyRises.forget(uuid);   // §fly-2\n", "clear rises")
    sub(fm, "        if (session.iceFishing) FlyCast.cancel(sp);   // §ice-rhythm: the needle goes with the line\n",
        "        if (session.iceFishing) FlyCast.cancel(sp);   // §ice-rhythm: the needle goes with the line\n"
        "        if (session.ctx != null && session.ctx.rod == RodType.FLY) FlyCast.driftOff(sp);   // §fly-2: the status line goes with it\n", "endSession drift off")
    sub(fm, "        int delayMin = (int) Math.round(4 + (1 - aggression) * 8);\n        int delayMax = delayMin + 8 + (int) Math.round((1 - aggression) * 6);\n        int window = delayMax + 6;\n",
        "        FlyCast.driftOff(sp);   // §fly-2: the strike bar takes the status line's place\n"
        "        int delayMin = (int) Math.round(5 + (1 - aggression) * 6);\n        int delayMax = delayMin + 12 + (int) Math.round((1 - aggression) * 6);   // §fly-2: a wider green\n        int window = delayMax + 8;\n", "rise clock")
    # ---- FishingSession ----
    sub(j(J, "fishing/FishingSession.java"), "    public boolean flyDragWarned;",
        "    public boolean flyDragWarned;\n    public boolean flyOnRise;      // §fly-2: the fly is over a fish that has decided\n    public boolean flyPickedUp;    // §fly-2: the hold took the line up — the release is a cast, not a strip\n    public int flyDriftState = -1; // §fly-2: the last status sent to the HUD", "session fields")
    # ---- FlyBeatPacket ----
    fb = j(J, "network/FlyBeatPacket.java")
    s = rd(fb)
    s2 = re.sub(r"FlyCast\.beat\(sp, sp\.(?:serverLevel|level)\(\)\.getGameTime\(\)\);", "com.riverfishing.fishing.FishingManager.flyBeat(sp);   // §fly-2: a haul or a mend", s)
    if s2 != s: wr(fb, s2)
    # ---- Hatch: the rings are real fish now ----
    sub(j(J, "engine/Hatch.java"), "        if (r.nextInt(4) != 0) return;\n", "        if (true) return;   // §fly-2: the rise rings are FlyRises' — real fish, not decoration\n", "hatch ring")
    # ---- FloatTimingClient: the bar says SET on a fly rod ----
    sub(j(J, "client/FloatTimingClient.java"), 'Component label = Component.translatable("hud.riverfishing.strike_timing");',
        'Component label = Component.translatable(FlyCastClient.flyHeld() ? "hud.riverfishing.fly_set" : "hud.riverfishing.strike_timing");   // §fly-2', "strike label")
    sub(j(J, "client/FlyCastClient.java"), "    /** A fly line is on the water and the drift status is showing. */",
        "    /** A fly rod is in the hand — the strike bar labels itself for it. */\n    public static boolean flyHeld() {\n        return !heldFlyRod(Minecraft.getInstance()).isEmpty();\n    }\n\n    /** A fly line is on the water and the drift status is showing. */", "flyHeld")
    # ---- ClientLineState.selfCalm ----
    sub(j(J, "client/ClientLineState.java"), "    /** Whether OUR OWN line is out — drives rod hold behaviour and the cast-power HUD. */",
        "    /** §fly-2: our own line is out and nothing is happening on it — a click on the fly rod is a hold. */\n"
        "    public static boolean selfCalm() {\n        var mc = Minecraft.getInstance();\n        if (mc.player == null) return false;\n"
        "        Line l = LINES.get(mc.player.getId());\n        return l != null && !l.biting && !l.fighting;\n    }\n\n"
        "    /** Whether OUR OWN line is out — drives rod hold behaviour and the cast-power HUD. */", "selfCalm")
    # ---- RodItem ----
    ri = j(J, "item/RodItem.java")
    consume = "InteractionResult.CONSUME" if name == "rf26" else "InteractionResultHolder.consume(rod)"
    client_side = "level.isClientSide()" if name == "rf26" else "level.isClientSide"
    sub(ri, "        boolean sessionAction;\n",
        "        // §fly-2: with a fly line on the water and nothing biting, the click is a HOLD — a tap strips, a hold\n"
        "        // picks the line up and false-casts; both resolve in releaseUsing and the server tick\n"
        "        if (rodType == RodType.FLY && lineOut) {\n"
        "            boolean calm = !%s\n"
        "                    ? player instanceof ServerPlayer fsp && FishingManager.flyCalm(fsp)\n"
        "                    : dev.architectury.utils.EnvExecutor.getEnvSpecific(\n"
        "                            () -> () -> com.riverfishing.client.ClientLineState.selfCalm(), () -> () -> false);\n"
        "            if (calm) {\n"
        "                player.startUsingItem(hand);\n"
        "                return %s;\n"
        "            }\n"
        "        }\n"
        "        boolean sessionAction;\n" % (client_side, consume), "rod use hold")
    ret = "return true;" if name == "rf26" else "return;"
    sub(ri, "        if (FishingManager.hasSession(sp)) {\n            // Was holding a retrieve",
        "        if (FishingManager.hasSession(sp)) {\n            if (FishingManager.flyTap(sp)) %s   // §fly-2: a short hold on a calm fly line is the strip\n            // Was holding a retrieve" % ret, "rod release tap")
    # ---- lang ----
    for code, entries in LANG_NEW.items():
        p = j(LANG, code + ".json"); data = json.loads(rd(p), object_pairs_hook=OrderedDict)
        for k in LANG_DROP: data.pop(k, None)
        out = OrderedDict()
        for k, v in data.items():
            if k in entries: continue
            out[k] = v
            if k == "message.riverfishing.fly_pickup":
                for ek, ev in entries.items(): out[ek] = ev
        assert "gui.riverfishing.fly_hauls" in out, "lang anchor " + code
        wr(p, json.dumps(out, ensure_ascii=False, indent=2) + "\n")
    print("  patched", name)


if __name__ == "__main__":
    for t in (sys.argv[1:] or TREES): run(t)
