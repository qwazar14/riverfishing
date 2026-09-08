# -*- coding: utf-8 -*-
"""Fly fishing is removed, whole.

Five rebuilds in 0.10 and it still was not the thing the author wanted, so it goes — the rod, the rig,
the cast, the drift, the rises, the strike, the hatch, the quests and the achievements. Not hidden
behind a flag: deleted, because git remembers it and the next attempt should start from a clean sheet
rather than from four layers of somebody's earlier compromise.

What SURVIVES, because it was never the fly's:
  * the winter rod's jig, which happened to live in the fly cast's classes — it moves out into
    JigRhythm / JigClient / JigBeatPacket / JigGaugePacket and keeps its gauge, its accents and its combo;
  * the tied lures (ant, nymph, streamer, dry fly, shrimp), which are tied on the Tackle Station and
    fish as mormyshkas and lures on every other rod — the `fly_*` bait scores in the species profiles
    are theirs, not the fly rod's.

Idempotent; run on each tree.  py -X utf8 tools/patches/p_fly_gone.py [tree ...]"""
import io, json, os, re, shutil, sys

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"
A = "common/src/main/resources/assets/riverfishing/"
D = "common/src/main/resources/data/riverfishing/"


def rd(p):
    return io.open(p, encoding="utf-8").read()


def wr(p, s):
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what, opt=False):
    """Replace once. Idempotent: if the new text is already there, nothing happens.

    opt=True for text a tree may legitimately not have (the packet dialects differ)."""
    s = rd(path)
    if new in s:
        return
    if opt and old not in s:
        return
    assert s.count(old) == 1, "%s @ %s (%d matches)" % (what, path, s.count(old))
    wr(path, s.replace(old, new, 1))


MISSES = []


def cut(path, old, what, count=1):
    """Delete a block. Idempotent: gone is gone (and reported, so a silent dialect miss is visible)."""
    s = rd(path)
    if old not in s:
        MISSES.append((what, os.path.basename(path)))
        return
    assert s.count(old) == count, "%s @ %s (%d matches)" % (what, path, s.count(old))
    wr(path, s.replace(old, "", count))


def drop(tree, *rels):
    for rel in rels:
        p = os.path.join(tree, rel)
        if os.path.isdir(p):
            shutil.rmtree(p)
        elif os.path.exists(p):
            os.remove(p)


def drop_glob(tree, folder, pattern):
    d = os.path.join(tree, folder)
    if not os.path.isdir(d):
        return
    rx = re.compile(pattern)
    for name in os.listdir(d):
        if rx.match(name):
            os.remove(os.path.join(d, name))


def dialect26(name, s):
    """The 26.x GUI spelling (the same map p_fly2 carried, kept here so this script stands alone)."""
    if name != "rf26":
        return s
    s = s.replace("import net.minecraft.client.gui.GuiGraphics;", "import net.minecraft.client.gui.GuiGraphicsExtractor;")
    s = s.replace("GuiGraphics g", "GuiGraphicsExtractor g")
    s = s.replace("net.minecraft.resources.ResourceLocation", "net.minecraft.resources.Identifier")
    s = s.replace("        if (mc.level == null || mc.options.hideGui) return;\n",
                  "        //? if <26.2 {\n        if (mc.level == null || mc.options.hideGui) return;\n"
                  "        //?} else {\n        /*if (mc.level == null || mc.gui.hud.isHidden()) return;\n        *///?}\n")
    s = s.replace("g.pose().pushPose();", "g.pose().pushMatrix();").replace("g.pose().popPose();", "g.pose().popMatrix();")
    s = re.sub(r"g\.pose\(\)\.translate\(([^;]*), 0\);", r"g.pose().translate(\1);", s)
    s = re.sub(r"g\.pose\(\)\.scale\(([^;]*), 1f\);", r"g.pose().scale(\1);", s)
    s = s.replace("g.blit(BAR, x, y, FW, FH, 0f, 0f, FW, FH, 128, 48);",
                  "g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BAR, x, y, 0f, 0f, FW, FH, 128, 48);")
    s = s.replace("g.drawCenteredString(", "g.centeredText(").replace("g.drawString(", "g.text(")
    return s


# --------------------------------------------------------------------------- the jig's own classes
JIG_RHYTHM = '''package com.riverfishing.fishing;

import com.riverfishing.network.JigGaugePacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * \u00a7ice-rhythm: the winter rod's jig.
 *
 * <p>Hold use over the hole and the rod works the mormyshka on its own — a stop every half period, the
 * lift and the drop. A left click that lands ON a stop is an accent: it pulls the bite harder and grows
 * the combo, up to {@link #JIG_MAX}. The gauge the client draws is drawn from what this class sends.
 *
 * <p>(It used to live inside the fly rod's cast, which shared the same needle. The fly is gone; the jig
 * was never its.)
 */
public final class JigRhythm {
    public static final int JIG_PERIOD = 16, JIG_MAX = 8;
    public static final float JIG_ZONE_HALF = 0.22f;

    private static final class State {
        long start;
        int beats;          // accents landed
        int lastEnd = -1;   // the stroke index of the last accent
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private JigRhythm() {}

    /** Triangle wave 0..1 across one full period. The client draws the same. */
    public static float marker(long elapsed, int period) {
        if (period <= 0) return 0.5f;
        float phase = (Math.floorMod(elapsed, period)) / (float) period;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    /** The hold began over the hole: the rod starts jigging and the client is told to draw it. */
    public static void beginJig(ServerPlayer sp, long now) {
        State s = new State();
        s.start = now;
        STATES.put(sp.getUUID(), s);
        send(sp, s, true);
    }

    public static boolean isJigging(ServerPlayer sp) {
        return STATES.containsKey(sp.getUUID());
    }

    /** The stroke the jig is on — a new number every stop; -1 when it is not jigging. */
    public static int jigStroke(ServerPlayer sp, long now) {
        State s = STATES.get(sp.getUUID());
        return s == null ? -1 : (int) Math.floorDiv(now - s.start, JIG_PERIOD / 2L);
    }

    /** The accents landed so far — the combo. */
    public static int jigCombo(ServerPlayer sp) {
        State s = STATES.get(sp.getUUID());
        return s == null ? 0 : s.beats;
    }

    /** A left click on a jig stop: an accent. Anywhere else it is nothing, and costs nothing. */
    public static boolean jigAccent(ServerPlayer sp, long now) {
        State s = STATES.get(sp.getUUID());
        if (s == null) return false;
        long el = now - s.start;
        float m = marker(el, JIG_PERIOD);
        float off = Math.min(m, 1f - m);
        int st = (int) Math.floorDiv(el, JIG_PERIOD / 2L);
        if (off <= JIG_ZONE_HALF && st != s.lastEnd && s.beats < JIG_MAX) {
            s.beats++;
            s.lastEnd = st;
            send(sp, s, true);
            return true;
        }
        return false;
    }

    /** The hold ended: forget the jig and take the gauge off the screen. */
    public static void cancel(ServerPlayer sp) {
        State s = STATES.remove(sp.getUUID());
        send(sp, s == null ? new State() : s, false);
    }

    private static void send(ServerPlayer sp, State s, boolean active) {
        ModNetwork.toPlayer(sp, new JigGaugePacket(active, s.start, JIG_PERIOD, JIG_ZONE_HALF,
                s.beats, JIG_MAX, (byte) s.lastEnd));
    }
}
'''

JIG_CLIENT = '''package com.riverfishing.client;

import com.riverfishing.component.RodType;
import com.riverfishing.item.RodItem;
import com.riverfishing.network.JigBeatPacket;
import com.riverfishing.network.JigGaugePacket;
import com.riverfishing.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * \u00a7ice-rhythm: the winter rod's jig gauge — the one bar this mod draws while you fish.
 *
 * <p>The rod works the mormyshka on its own while use is held: the needle sweeps between \u25b2 the lift and
 * \u25bc the drop, and a left click that lands on a stop is an accent — the bar flashes, the pips above it
 * fill, and the take comes sooner.
 */
public final class JigClient {
    private static boolean active;
    private static long startTick;
    private static int period;
    private static float zoneHalf;
    private static int beats;
    private static int maxBeats;
    private static boolean attackWas;
    private static long hitNanos = -1L;

    private static final net.minecraft.resources.ResourceLocation BAR =
            com.riverfishing.RiverFishing.id("textures/gui/cast_bar.png");

    private JigClient() {}

    public static void accept(JigGaugePacket p) {
        Minecraft mc = Minecraft.getInstance();
        boolean hit = p.active && p.beats > beats;
        if (!active && p.active) hitNanos = -1L;
        active = p.active;
        startTick = p.startTick;
        period = p.period;
        zoneHalf = p.zoneHalf;
        beats = p.beats;
        maxBeats = p.maxBeats;
        if (hit) {
            hitNanos = System.nanoTime();
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 0.9f + 0.09f * Math.min(beats, 12));
            }
        }
    }

    /** The jig gauge is up — the charge bar yields to it. */
    public static boolean isActive() {
        return active;
    }

    private static float marker(float t) {
        if (period <= 0) return 0.5f;
        float phase = (t % period) / period;
        if (phase < 0) phase += 1f;
        return phase < 0.5f ? phase * 2f : 2f - phase * 2f;
    }

    private static ItemStack heldWinterRod(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof RodItem ri && ri.rodType() == RodType.WINTER) return main;
        ItemStack off = mc.player.getOffhandItem();
        return off.getItem() instanceof RodItem ri2 && ri2.rodType() == RodType.WINTER ? off : ItemStack.EMPTY;
    }

    /** Client tick: while the rod jigs, the left button is the accent and never an arm swing. */
    public static void tick(Minecraft mc) {
        if (!active) {
            attackWas = false;
            return;
        }
        if (heldWinterRod(mc).isEmpty() || !mc.player.isUsingItem()) {
            active = false;   // the hold ended without a release (a slot switch): the gauge comes down
            return;
        }
        boolean down = mc.options.keyAttack.isDown();
        if (down && !attackWas) ModNetwork.toServer(new JigBeatPacket());
        attackWas = down;
        while (mc.options.keyAttack.consumeClick()) { /* drained: no arm swing, no block hit */ }
    }

    private static float since(long nanos) {
        return nanos < 0 ? 99f : (System.nanoTime() - nanos) / 1.0e9f;
    }

    private static int lerpRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    public static void render(GuiGraphics g, int screenW, int screenH, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        if (heldWinterRod(mc).isEmpty()) return;
        float t = (mc.level.getGameTime() - startTick) + partialTick;

        float punch = (float) Math.exp(-since(hitNanos) * 6.0);
        float fill = maxBeats > 0 ? Mth.clamp(beats / (float) maxBeats, 0f, 1f) : 0f;
        float scale = (1f + 0.05f * Math.min(beats, 8)) * (1f + 0.3f * punch);
        int stopRgb = lerpRgb(0x5FA84E, 0xFFF4C0, fill * 0.9f);
        int haloRgb = lerpRgb(0x5FA84E, 0xFFD34A, fill);

        final int FW = 120, FH = 16, TW = 112, TH = 8;
        int x = (screenW - FW) / 2, y = screenH - 70;
        int cx = x + FW / 2, cy = y + FH / 2;

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-cx, -cy, 0);

        int haloA = (int) (40 + 110 * fill + 70 * punch);
        int spread = 3 + (int) (4 * fill + 8 * punch);
        g.fill(x - spread, y - spread, x + FW + spread, y + FH + spread, (Math.min(255, haloA) << 24) | haloRgb);

        int tx = x + 4, ty = y + 4;
        g.blit(BAR, x, y, FW, FH, 0f, 0f, FW, FH, 128, 48);

        int zw = Math.max(2, (int) (zoneHalf * TW));
        g.fill(tx, ty, tx + zw, ty + TH, 0xC8000000 | stopRgb);
        g.fill(tx + TW - zw, ty, tx + TW, ty + TH, 0xC8000000 | stopRgb);
        g.drawCenteredString(mc.font, Component.literal("\u25b2"), tx + zw / 2, ty, 0xFF1C1814);
        g.drawCenteredString(mc.font, Component.literal("\u25bc"), tx + TW - zw / 2, ty, 0xFF1C1814);
        if (punch > 0.05f) g.fill(tx, ty, tx + TW, ty + TH, ((int) (110 * punch) << 24) | 0xFFFFFF);

        int mx = tx + (int) (marker(t) * TW);
        g.fill(mx - 2, y - 2, mx + 3, y + FH + 2, 0xC0231A10);
        g.fill(mx - 1, y - 1, mx + 2, y + FH + 1, 0xFFFFE8A8);

        int px0 = x + (FW - maxBeats * 6) / 2, py0 = y - 7;
        for (int i = 0; i < maxBeats; i++) {
            int px = px0 + i * 6;
            boolean lit = i < beats;
            int grow = lit && i == beats - 1 ? (int) (2 * punch) : 0;
            int c = lit ? lerpRgb(0xFFC83C, 0xFFFFFF, punch) | 0xFF000000 : 0x60231A10;
            g.fill(px - grow, py0 - grow, px + 4 + grow, py0 + 4 + grow, c);
        }
        g.pose().popPose();
    }
}
'''

# --------------------------------------------------------------------------------- the two packets
BEAT_DOC = '''/**
 * Client \u2192 server: a left click while the winter rod jigs (\u00a7ice-rhythm) — an accent, if it landed on a
 * stop. Empty on purpose: the server judges it against its own needle when it arrives, and a click
 * outside a live jig is simply ignored, so there is nothing here to trust.
 */'''
GAUGE_DOC = '''/**
 * Server \u2192 client: the jig gauge (\u00a7ice-rhythm) — on or off, where the needle's sweep started, its
 * period, how wide the stops are, and how many accents have landed (of the most the combo holds).
 * Re-sent after every accent; the server is authoritative.
 */'''


def jig_packets(tree):
    """The fly cast's two packets carried the jig as a second mode. Renamed, with the mode dropped."""
    beat_old, beat_new = os.path.join(tree, J, "network/FlyBeatPacket.java"), os.path.join(tree, J, "network/JigBeatPacket.java")
    if os.path.exists(beat_old):
        s = rd(beat_old)
        i, k = s.index("/**"), s.index("*/") + 2
        s = s[:i] + BEAT_DOC + s[k:]
        s = s.replace("FlyBeatPacket", "JigBeatPacket").replace('RiverFishing.id("fly_beat")', 'RiverFishing.id("jig_beat")')
        s = s.replace("import com.riverfishing.fishing.FlyCast;\n", "")
        s = s.replace("com.riverfishing.fishing.FishingManager.flyBeat(sp);   // \u00a7fly-2: a haul or a mend",
                      "com.riverfishing.fishing.FishingManager.jigBeat(sp);   // \u00a7jig-2: the accent")
        wr(beat_new, s)
        os.remove(beat_old)

    old, new = os.path.join(tree, J, "network/FlyCastPacket.java"), os.path.join(tree, J, "network/JigGaugePacket.java")
    if os.path.exists(old):
        s = rd(old)
        i, k = s.index("/**"), s.index("*/") + 2
        s = s[:i] + GAUGE_DOC + s[k:]
        s = s.replace("FlyCastPacket", "JigGaugePacket").replace('RiverFishing.id("fly_cast")', 'RiverFishing.id("jig_gauge")')
        s = s.replace("    public final boolean openLoop;\n", "")
        s = s.replace("    /** The end the last good beat landed on (0 left, 1 right), -1 when either is next. */\n"
                      "    public final byte lastEnd;\n"
                      "    /** 0 = the fly cast, 1 = the winter rod's jig (\u00a7ice-rhythm). */\n"
                      "    public final byte mode;\n",
                      "    /** The stroke the last accent landed on, -1 when none has. */\n"
                      "    public final byte lastEnd;\n")
        s = s.replace("    public JigGaugePacket(boolean active, long startTick, int period, float zoneHalf, int beats,\n"
                      "                         int maxBeats, boolean openLoop, byte lastEnd, byte mode) {",
                      "    public JigGaugePacket(boolean active, long startTick, int period, float zoneHalf, int beats,\n"
                      "                          int maxBeats, byte lastEnd) {")
        s = s.replace("        this.openLoop = openLoop;\n", "").replace("        this.mode = mode;\n", "")
        s = s.replace("        buf.writeBoolean(openLoop);\n", "").replace("        buf.writeByte(mode);\n", "")
        s = s.replace("                buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readByte(), buf.readByte());",
                      "                buf.readInt(), buf.readInt(), buf.readByte());")
        s = s.replace("com.riverfishing.client.FlyCastClient.accept(this)", "com.riverfishing.client.JigClient.accept(this)")
        wr(new, s)
        os.remove(old)


def rename_idents(tree):
    """Whatever still names the fly's classes now names the jig's."""
    pairs = [("FlyCastClient", "JigClient"), ("FlyCastPacket", "JigGaugePacket"),
             ("FlyBeatPacket", "JigBeatPacket"), ("FlyCast", "JigRhythm")]
    for base, _dirs, files in os.walk(os.path.join(tree, "common/src/main/java")):
        for f in files:
            if not f.endswith(".java"):
                continue
            p = os.path.join(base, f)
            s = t = rd(p)
            for old, new in pairs:
                t = t.replace(old, new)
            if t != s:
                wr(p, t)


# ------------------------------------------------------------------------------------------ lang
LANG_EXACT = {
    "quest.riverfishing.q_fly_first", "quest.riverfishing.q_fly_ten", "quest.riverfishing.q_fly_thirty",
    "item.riverfishing.fly_rod", "item.riverfishing.rig_fly", "tooltip.riverfishing.rod_class.fly",
    "message.riverfishing.fly_threw_hook",
}
LANG_PREFIX = ("advancement.riverfishing.fly_first.", "advancement.riverfishing.fly_tight.",
               "advancement.riverfishing.fly_fifty.", "hatch.riverfishing.")
STAGE7 = [("Stage 7 - Cold water and the fly", "Stage 7 - Cold water"),
          ("\u0421\u0442\u0430\u0434\u0438\u044f 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0430\u044f \u0432\u043e\u0434\u0430 \u0438 \u043d\u0430\u0445\u043b\u044b\u0441\u0442",
           "\u0421\u0442\u0430\u0434\u0438\u044f 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0430\u044f \u0432\u043e\u0434\u0430"),
          ("\u0415\u0442\u0430\u043f 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0430 \u0432\u043e\u0434\u0430 \u0456 \u043d\u0430\u0445\u043b\u0438\u0441\u0442",
           "\u0415\u0442\u0430\u043f 7 \u2014 \u0425\u043e\u043b\u043e\u0434\u043d\u0430 \u0432\u043e\u0434\u0430")]


def lang(tree):
    for f in ("en_us", "ru_ru", "uk_ua"):
        p = os.path.join(tree, A, "lang/%s.json" % f)
        out = []
        for line in rd(p).split("\n"):
            key = line.strip().split('"')[1] if line.strip().startswith('"') else None
            if key and (key in LANG_EXACT or key.startswith(LANG_PREFIX)):
                continue
            for old, new in STAGE7:
                line = line.replace(old, new)
            out.append(line)
        # the last key must not carry a comma
        for i in range(len(out) - 1, -1, -1):
            if out[i].strip().startswith('"'):
                out[i] = out[i].rstrip().rstrip(",")
                break
        s = "\n".join(out)
        json.loads(s)   # it still parses, or nothing is written
        wr(p, s)


# ------------------------------------------------------------------------------------------ assets
def assets(tree):
    # rod_line_paths.json and rod_physics.json lose their fly row in p_fly_gone4.py — as text, because
    # re-serialising them here reformatted every other rod in the file for one deletion.
    drop(tree, A + "models/item/fly_rod.json", A + "models/item/rig_fly.json",
         A + "models/item/rod/rig_fly.json", A + "models/item/rod_m/rig_fly.json",
         A + "textures/item/rod/rig_fly.png", A + "textures/item/rod_m/rig_fly.png",
         D + "advancement/riverfishing/fly_first.json", D + "advancement/riverfishing/fly_tight.json",
         D + "advancement/riverfishing/fly_fifty.json", D + "recipe/fly_rod.json",
         D + "recipes/fly_rod.json", D + "advancements/riverfishing/fly_first.json",
         D + "advancements/riverfishing/fly_tight.json", D + "advancements/riverfishing/fly_fifty.json",
         "3D/rods/fly")
    # 26.x files the same models under items/ and rod_layer/ instead of rod/
    drop(tree, A + "items/fly_rod.json", A + "items/rig_fly.json",
         A + "models/item/rod_layer/rig_fly.json", A + "models/item/rod_layer_m/rig_fly.json")
    for folder in (A + "models/item/rod", A + "models/item/rod_m",
                   A + "models/item/rod_layer", A + "models/item/rod_layer_m"):
        drop_glob(tree, folder, r"blank_fly.*\.json$")
    for folder in (A + "textures/item/rod", A + "textures/item/rod_m"):
        drop_glob(tree, folder, r"blank_fly.*\.png$")


# -------------------------------------------------------------------------------------- the java
def java(tree, name):
    j = lambda *a: os.path.join(tree, *a)
    d26 = (lambda t: t.replace("sp.serverLevel()", "sp.level()")) if name == "rf26" else (lambda t: t)

    def dia(t):
        """26.x spells a few of these differently; the anchors have to follow."""
        if name != "rf26":
            return t
        return (t.replace("!level.isClientSide &&", "!level.isClientSide() &&")
                 .replace("tooltip.add(", "tooltip.accept("))

    # ---- FishingManager: every fly branch out of the flow ----
    fm = j(J, "fishing/FishingManager.java")
    cut(fm, "        FlyRises.forget(uuid);   // \u00a7fly-2\n", "rises forget")
    cut(fm, "        // \u00a7fly-nofloat: a fly rod runs the FLOAT flow but there is nothing on the water but the fly\n"
            "        if (RigData.rigType(rig) == com.riverfishing.component.RigType.FLY) return 0;\n", "floatKind")
    cut(fm, "            } else if (session.fly != null) {\n"
            "                // \u00a7fly-3: on a fly rod the right button is the strip, and the strip-set when a fish has taken\n"
            "                if (!FlyStrike.tryStrike(sp, FlyStrike.Input.STRIP)) FlyDrift.strip(sp, level, session, now);\n",
        "rod use branch")
    sub(fm, "        // \u00a7fly: on a fly rod the rhythm decides the power, not the charge \u2014 and the delivery's\n"
            "        // quality (tight / splash / wind knot) lands with the line.\n"
            "        ItemStack held = sp.getItemInHand(hand);\n"
            "        boolean fly = held.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY;\n"
            "        if (fly) power = FlyCast.release(sp, held, level.getGameTime());\n"
            "        if (SESSIONS.containsKey(sp.getUUID())) {\n"
            "            if (fly) FlyCast.cancel(sp);\n"
            "            return false;\n"
            "        }\n"
            "        boolean cast = startCast(sp, level, hand, level.getGameTime(), Mth.clamp(power, 0.05f, 1.0f));\n"
            "        if (fly) {\n"
            "            if (cast) flyLanded(sp, SESSIONS.get(sp.getUUID()), FlyCast.takeQuality(sp));\n"
            "            else FlyCast.cancel(sp);\n"
            "        }\n"
            "        return cast;\n",
        "        if (SESSIONS.containsKey(sp.getUUID())) return false;\n"
        "        return startCast(sp, level, hand, level.getGameTime(), Mth.clamp(power, 0.05f, 1.0f));\n",
        "chargedCast")
    # flyCastBegin + flyLanded + flyBeat -> one jig accent entry point
    i = rd(fm).find("    /** \u00a7fly: the hold on a fly rod began")
    if i >= 0:
        s = rd(fm)
        k = s.index("    /** \u00a7jig-2: a winter line down the hole")
        wr(fm, s[:i] + d26('''    /** \u00a7jig-2: the left click while the winter rod jigs \u2014 the accent, if it landed on a stop. */
    public static void jigBeat(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        FishingSession s = SESSIONS.get(sp.getUUID());
        if (s != null && s.iceFishing && !s.bitten && !s.fighting && FlyCast.jigAccent(sp, now)) {
            iceStroke(sp, level, s, now, true);
        }
    }

''') + s[k:])
    # the helpers the fly classes reached in through
    i = rd(fm).find("    /** \u00a7fly-3: the player's live session")
    if i >= 0:
        s = rd(fm)
        k = s.index("    private static boolean startCast(")
        wr(fm, s[:i] + s[k:])
    cut(fm, "        if (type == RodType.FLY) {   // \u00a7fly-aim: the fly lands where the crosshair meets the water, as far as the line reaches\n"
            "            net.minecraft.world.phys.Vec3 eye = sp.getEyePosition();\n"
            "            net.minecraft.world.phys.BlockHitResult aimHit = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(28.0)),\n"
            "                    net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY, sp));\n"
            "            if (aimHit.getType() == HitResult.Type.BLOCK) {\n"
            "                double ax = aimHit.getLocation().x - sp.getX(), az = aimHit.getLocation().z - sp.getZ();\n"
            "                double aim = Math.sqrt(ax * ax + az * az);\n"
            "                if (aim >= 2.0) throwDist = Math.min(throwDist, aim);\n"
            "            }\n"
            "        }\n", "fly aim")
    cut(fm, "        // \u00a7fly-aim: a fly that would come down on the bank drops onto the last water under the line instead\n"
            "        for (double d = throwDist - 1.0; type == RodType.FLY && waterPos == null && d >= 2.0; d -= 1.0) {\n"
            "            waterPos = findWaterColumn(level, sp.getX() + (look.x / hl) * d, sp.getEyeY() + 2.0, sp.getZ() + (look.z / hl) * d);\n"
            "        }\n", "fly aim fallback")
    cut(fm, "        // \u00a7fly-2: the rising fish, while a fly rod is in the hand \u2014 session or no session\n"
            "        if (sp.tickCount % 20 == 0 && (isFlyRod(sp.getMainHandItem()) || isFlyRod(sp.getOffhandItem()))) {\n"
            "            ServerLevel lv = (ServerLevel) sp.level();\n"
            "            FlyRises.tick(lv, sp, lv.getGameTime());\n"
            "        }\n", "rises tick")
    cut(fm, "            } else if (session.fly != null) {\n"
            "                visProgress = (float) session.fly.reel;   // \u00a7fly-3\n", "vis progress")
    cut(fm, "        // \u00a7fly-3: a fly line is its own loop \u2014 the drift carries it, the rise is watched for, the fish is\n"
            "        // seen coming and the take opens its own window. None of the float flow below applies.\n"
            "        if (session.fly != null) {\n"
            "            if (session.ctx != null && session.biteAtTick > now && now % 300 == 0) reEvaluate(level, session, now);\n"
            "            if (now % 20 == 0 && session.ctx != null && session.ctx.hatch != null) {\n"
            "                session.ctx.hatch.particles(level, session.fly.spot);\n"
            "            }\n"
            "            FlyDrift.tick(level, sp, session, now);\n"
            "            return;\n"
            "        }\n\n", "fly drift tick")
    cut(fm, "                if (session.ctx != null && session.ctx.hatch != null) session.ctx.hatch.particles(level, session.target);   // \u00a7fly: the water shows the hatch\n",
        "hatch particles")
    cut(fm, "        ctx.hatch = ctx.rod == RodType.FLY ? Hatch.now(ctx.season, ctx.time, ctx.weather, ctx.water) : null;   // \u00a7fly\n",
        "hatch ctx", count=2)
    cut(fm, "import com.riverfishing.engine.Hatch;   // \u00a7fly\n", "hatch import")
    sub(fm, "                : session.fly != null ? Mth.clamp(session.fly.reel, 0.0, 0.85)   // \u00a7fly-3: the fight starts where the fly was\n"
            "                : 0.0;", "                : 0.0;", "land progress")
    cut(fm, "        // \u00a7fly-set: on a fly rod the set is the show \u2014 the fish comes out of the water with a boil and a\n"
            "        // slap, and the whole bank sees it (the breach is drawn by the line sync for the next 16 ticks)\n"
            "        if (session.fly != null) {\n"
            "            session.flyFight = true;   // \u00a7fly-3: a fish on a fly jumps\n"
            "            session.flyJumpAt = now + 60 + random.nextInt(60);\n"
            "            session.showFishUntil = now + 16;\n"
            "            net.minecraft.world.phys.Vec3 fa = session.fly.flyAt(sp);   // \u00a7fly-3: the spray where the fly is\n"
            "            double sx = fa.x, sy = fa.y + 1.1, sz = fa.z;\n"
            "            level.sendParticles(ParticleTypes.SPLASH, sx, sy, sz, 70 + session.lengthCm, 0.7, 0.5, 0.7, 0.45);\n"
            "            level.sendParticles(ParticleTypes.BUBBLE_POP, sx, sy - 0.1, sz, 20, 0.5, 0.2, 0.5, 0.1);\n"
            "            level.playSound(null, session.target, SoundEvents.DOLPHIN_JUMP, SoundSource.PLAYERS, 1.0f, 0.9f);\n"
            "            level.playSound(null, session.target, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0f, 0.8f);\n"
            "        }\n", "fly set show")
    cut(fm, "        // \u00a7fly-3: a fish on a fly jumps, and often \u2014 it is the picture the whole method is for. Winding\n"
            "        // into one rips the hook out (reelPulse knows the window), and a hook the strike set badly can\n"
            "        // simply be thrown here.\n"
            "        if (session.flyFight && session.runTicksLeft == 0 && session.landProgress > 0.05\n"
            "                && now >= session.jumpWindowEnd && now >= session.flyJumpAt) {\n"
            "            session.flyJumpAt = now + 80 + random.nextInt(71);\n"
            "            session.jumpWindowEnd = now + 15;\n"
            "            session.tension += session.runTensionPulse * 0.8;\n"
            "            level.playSound(null, session.target, SoundEvents.DOLPHIN_JUMP, SoundSource.PLAYERS, 0.9f, 1.05f);\n"
            "            level.sendParticles(ParticleTypes.SPLASH, session.target.getX() + 0.5, session.target.getY() + 1.2,\n"
            "                    session.target.getZ() + 0.5, 30, 0.45, 0.4, 0.45, 0.35);\n"
            "            actionbar(sp, Component.translatable(\"message.riverfishing.fish_jumps\").withStyle(ChatFormatting.RED));\n"
            "            if (session.hookStrength == 0 && random.nextFloat() < 0.18f) {\n"
            "                level.playSound(null, session.target, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.7f, 0.6f);\n"
            "                endSession(sp, session);\n"
            "                actionbar(sp, Component.translatable(\"message.riverfishing.fly_threw_hook\").withStyle(ChatFormatting.RED));\n"
            "                GuideNudge.failure(sp, session.rodClass, GuideNudge.MISSED);\n"
            "                return;\n"
            "            }\n"
            "        }\n", "fly jump")
    sub(fm, " || now <= session.showFishUntil || now - session.fightStartTick < 2) {   // \u00a7fly-set",
        " || now - session.fightStartTick < 2) {   // \u00a7hooked-fish", "sync cadence")
    sub(fm, "                    now < session.jumpWindowEnd || now < session.showFishUntil, session.runTicksLeft > 0 && !session.course.isRun(),   // \u00a7fly-set",
        "                    now < session.jumpWindowEnd, session.runTicksLeft > 0 && !session.course.isRun(),",
        "sync breach flag")
    cut(fm, "                if (rodNow.getItem() instanceof RodItem ri && ri.rodType() == RodType.FLY) JournalData.addFlyCatch(sp);\n",
        "fly catch counter")
    cut(fm, "        if (rodType == RodType.FLY) {\n"
            "            com.riverfishing.quest.AnglerAdvancements.grant(sp, \"fly_first\");\n"
            "            if (session.flyTight) com.riverfishing.quest.AnglerAdvancements.grant(sp, \"fly_tight\");\n"
            "        }\n", "fly advancements")
    cut(fm, "        if (jr.getInt(JournalData.FLY) >= 50) com.riverfishing.quest.AnglerAdvancements.grant(sp, \"fly_fifty\");\n",
        "fly fifty")
    # the comments that named the fly
    for old, new in (("    static BlockPos findWaterColumn(ServerLevel level, double x, double yStart, double z) {   // \u00a7fly-2: FlyRises places its fish with it",
                      "    static BlockPos findWaterColumn(ServerLevel level, double x, double yStart, double z) {"),
                     ("    static void endSession(ServerPlayer sp, FishingSession session) {   // \u00a7fly-3",
                      "    static void endSession(ServerPlayer sp, FishingSession session) {"),
                     ("    static void actionbar(ServerPlayer sp, Component message) {   // \u00a7fly-3: the fly classes talk too",
                      "    static void actionbar(ServerPlayer sp, Component message) {")):
        s = rd(fm)
        if old in s:
            wr(fm, s.replace(old, new, 1))

    # ---- the session's own fields ----
    fs = j(J, "fishing/FishingSession.java")
    cut(fs, "    public boolean flyTight;   // \u00a7progression: this cast unrolled without a splash\n", "flyTight")
    cut(fs, "    /** \u00a7fly-3: the fly cast's own state \u2014 null on every other rod. */\n"
            "    public FlySession fly;\n"
            "    /** \u00a7fly-3: 0 weak, 1 normal, 2 solid \u2014 what the strike earned; a weak hook can be thrown. */\n"
            "    public int hookStrength = 1;\n"
            "    /** \u00a7fly-3: the tick the fish on a fly may next come out of the water. */\n"
            "    public long flyJumpAt;\n"
            "    /** \u00a7fly-3: this fish was hooked on a fly \u2014 it jumps far more than a fish on any other rod. */\n"
            "    public boolean flyFight;\n", "fly fields")
    cut(fs, "    public long showFishUntil;   // \u00a7fly-set: the fish drawn breaching at the set, on its own timer\n", "showFishUntil")

    # ---- the rod ----
    ri = j(J, "item/RodItem.java")
    cut(ri, dia("        // \u00a7fly: on a fly rod the hold is the RHYTHM, not a charge \u2014 the needle starts here.\n"
                "        if (!level.isClientSide && rodType == RodType.FLY && player instanceof ServerPlayer sp) FishingManager.flyCastBegin(sp);\n"),
        "fly cast begin")
    sub(ri, dia("        if (rodType == com.riverfishing.component.RodType.FLY) {\n"
                "            // \u00a7fly: FLOAT flow underneath, but \"never reel\" is not what a fly rod wants to hear\n"
                "            tooltip.add(Component.translatable(\"tooltip.riverfishing.rod_class.fly\").withStyle(ChatFormatting.GOLD));\n"
                "        } else if (rodType != com.riverfishing.component.RodType.WINTER) {"),
        "        if (rodType != com.riverfishing.component.RodType.WINTER) {", "rod class tooltip")
    sub(ri, "        // \u00a7fly-2: with a fly line on the water and nothing biting, the click is a HOLD \u2014 a tap strips, a hold\n"
            "        // picks the line up and false-casts; both resolve in releaseUsing and the server tick\n"
            "        if (rodType == RodType.WINTER && lineOut) {   // \u00a7jig-2: the jig is a hold; a fly line's click is a click (\u00a7fly-3)",
        "        if (rodType == RodType.WINTER && lineOut) {   // \u00a7jig-2: over a hole the click is a HOLD, not a strike",
        "winter hold comment")
    ret = "return true;" if name == "rf26" else "return;"   # 26.x's use handler returns a result
    sub(ri, "            if (FishingManager.winterTap(sp)) %s   // \u00a7fly-2: a short hold on a calm fly line is the strip" % ret,
        "            if (FishingManager.winterTap(sp)) %s   // \u00a7jig-2: the hold over the hole let go \u2014 the pause" % ret,
        "winter tap comment")

    # ---- the bite engine: the hatch and the fly rod's own bonuses ----
    bc = j(J, "engine/BiteContext.java")
    cut(bc, "    /** \u00a7fly: what is hatching over this water right now \u2014 null off a fly rod, or when nothing is. */\n"
            "    public Hatch hatch;\n", "hatch field")
    be = j(J, "engine/BiteEngine.java")
    cut(be, "        // \u00a7fly-bait: on a fly rod the fly is the bait. The tied lure's bait id is the winter jig's, which\n"
            "        // most species never scored \u2014 so the baseline is the fly itself, and the template says the rest.\n"
            "        if (c.tied != null && c.rod == com.riverfishing.component.RodType.FLY) best = Math.max(best, 0.8);\n",
        "fly bait baseline")
    cut(be, "        // \u00a7fly: match the hatch \u2014 the right kind at the right size is the fly they are taking today\n"
            "        if (c.tied != null && c.rod == com.riverfishing.component.RodType.FLY) best *= Hatch.factor(c.hatch, c.tied);\n",
        "hatch factor")
    sub(be, "            return (c.rig == com.riverfishing.component.RigType.PREDATOR\n"
            "                    || c.rig == com.riverfishing.component.RigType.WINTER\n"
            "                    || c.rig == com.riverfishing.component.RigType.FLY) ? 0.85 : 0.0;   // \u00a7fly: the fly carries its own hook",
        "            return (c.rig == com.riverfishing.component.RigType.PREDATOR\n"
        "                    || c.rig == com.riverfishing.component.RigType.WINTER) ? 0.85 : 0.0;", "hook score")

    # ---- the journal counter and its command ----
    jd = j(J, "fishing/JournalData.java")
    cut(jd, "    public static final String FLY = \"fly\";   // \u00a7progression: fish landed on the fly rod\n\n"
            "    /** \u00a7progression: a fish landed on the fly rod \u2014 the counter the stage-7 quests read. */\n"
            "    public static void addFlyCatch(Player player) {\n"
            "        CompoundTag root = get(player);\n"
            "        root.putInt(FLY, root.getInt(FLY) + 1);\n"
            "        PlayerData.root(player).put(TAG, root);\n"
            "        PlayerData.markDirty(player);\n"
            "    }\n\n", "journal fly counter")
    cut(j(J, "command/JournalCommand.java"),
        "        root.putInt(JournalData.FLY, Math.max(root.getInt(JournalData.FLY), 60));\n", "unlockall fly")

    # ---- the quests: stage 7 keeps the cold water, loses the fly ----
    q = j(J, "quest/Quests.java")
    cut(q, "    private static Goal fly(int n) { return counter(JournalData.FLY, n); }\n", "fly goal")
    sub(q, "            // Stage 7 \u2014 cold water and the fly (\u00a7fly): the salmonids, and the rod that was made for them\n"
           "            new Quest(\"q_fly_first\", 7, fly(1), emeralds(10), 40),\n",
        "            // Stage 7 \u2014 cold water: the salmonids, wherever the water is cold enough for them\n", "stage 7 head")
    cut(q, "            new Quest(\"q_fly_ten\", 7, fly(10), emeralds(24), 90),\n", "q_fly_ten")
    cut(q, "            new Quest(\"q_fly_thirty\", 7, fly(30), emeralds(30), 120),\n", "q_fly_thirty")

    # ---- the rod and rig types ----
    rt = j(J, "component/RodType.java")
    sub(rt, "    TROLLING  (\"trolling\",  12,   true,     10000,  14000,  150,    600,    false),\n"
            "    // \u00a7fly: the line is the weight \u2014 no cast range, distance comes from the rhythm cast. The\n"
            "    // small reels (1000\u20132000) stand in for a fly reel in phase 1.\n"
            "    FLY       (\"fly\",        9,   true,     1000,   6000,   0,      0,      false);   // \u00a7fly-reels: up to 6000 \u2014 the salmon and the sea fish want a real drag\n",
        "    TROLLING  (\"trolling\",  12,   true,     10000,  14000,  150,    600,    false);\n", "rod enum")
    sub(rt, "            case STICK, BAMBOO, POLE, WINTER, FLY -> RodClass.FLOAT;   // \u00a7fly: wait, then strike",
        "            case STICK, BAMBOO, POLE, WINTER -> RodClass.FLOAT;", "rod class switch")
    cut(rt, "            case FLY -> RigType.FLY;                     // \u00a7fly: tippet + a tied fly\n", "rig for rod")
    sub(rt, "    /** \u00a7fly: the rod whose sprites and 3D blank this rod is drawn with \u2014 its own, since \u00a7fly-3d. */\n"
            "    public String modelKey() { return jsonKey; }",
        "    /** The rod whose sprites and 3D blank this rod is drawn with. */\n"
        "    public String modelKey() { return jsonKey; }", "model key doc")
    sub(j(J, "component/RigType.java"),
        "    CATFISH   (\"catfish\",     95,   1,   true),\n"
        "    FLY       (\"fly\",          2,   1,   true);   // \u00a7fly: a fly weighs nothing; the leader is the tippet\n",
        "    CATFISH   (\"catfish\",     95,   1,   true);\n", "rig enum")
    cut(j(J, "rig/RigLayout.java"), "            case FLY -> new SlotRole[]{LEADER, LURE};      // \u00a7fly: tippet + fly\n", "rig layout")
    cut(j(J, "menu/RigMenu.java"),
        "            // \u00a7fly: the fly rig's lure slot takes ONLY a tied fly \u2014 a spoon on a tippet casts nothing\n"
        "            if (type == RigType.FLY && role == SlotRole.LURE) return stack.getItem() instanceof com.riverfishing.item.TiedLureItem;\n",
        "rig menu")
    cut(j(J, "registry/ModItems.java"),
        "        if (\"fly\".equals(key)) return 144;          // \u00a7fly: as light a blank as the ultralight it borrows\n", "rod durability")
    sub(j(J, "client/RodModelLayers.java"), "\"trolling\", \"fly\"};", "\"trolling\"};", "model layers")

    # ---- the client: the gauge stays with the jig, the airborne loop goes ----
    ch = j(J, "client/ClientHud.java")
    sub(ch, "        // \u00a7fly: the rhythm gauge sits where the charge bar would \u2014 that one yields (below).\n"
            "        FlyCastClient.render(",
        "        // \u00a7ice-rhythm: the jig gauge sits where the charge bar would \u2014 that one yields (below).\n"
        "        FlyCastClient.render(", "hud comment")
    sub(ch, "        if (FlyCastClient.isActive()) return;   // \u00a7fly: the fly rod's hold is a rhythm, not a charge",
        "        if (FlyCastClient.isActive()) return;   // \u00a7ice-rhythm: the jig's hold is a rhythm, not a charge", "hud yield")
    sub(j(J, "client/ClientInit.java"),
        "        ClientTickEvent.CLIENT_POST.register(FlyCastClient::tick);   // \u00a7fly: the sneak taps on the beats",
        "        ClientTickEvent.CLIENT_POST.register(FlyCastClient::tick);   // \u00a7ice-rhythm: the accents on the jig's stops",
        "client init comment")

    lr = j(J, "client/LineRenderer.java")
    s = rd(lr)
    if "drawCastLine" in s:
        s = s.replace("        boolean casting = FlyCastClient.isCasting();   // \u00a7fly-5: a cast in the air draws even with no line out\n"
                      "        if (!casting && ClientLineState.lines().isEmpty()) return;",
                      "        if (ClientLineState.lines().isEmpty()) return;", 1)
        s = s.replace("        if (casting) {\n            drawCastLine(mc, buffers, m, nrm, pt);\n            drew = true;\n        }\n", "", 1)
        i = s.index("    /**\n     * \u00a7fly-5: the line in the air.")
        k = s.index("    private static void renderLine(Minecraft mc, MultiBufferSource buffers,")
        s = s[:i] + s[k:]
        wr(lr, s)

    anim = j(J, "client/RodItemRenderer.java") if name != "rf26" else j(J, "mixin/ItemInHandRendererMixin.java")
    partial = {"fishing mod": "mc.getTimer().getGameTimeDeltaPartialTick(false)",
               "rf1201": "mc.getFrameTime()",
               "rf26": "mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)"}[name]
    sub(anim, "            // \u00a7fly-5: a fly rod's pose follows its own swing, not a power bar\n"
              "            chargePower = FlyCastClient.isCasting()\n"
              "                    ? FlyCastClient.loadFraction(%s)\n"
              "                    : RodItem.castPower(used);" % partial,
        "            chargePower = RodItem.castPower(used);", "cast pose")
    if name == "rf26":
        cut(anim, "import com.riverfishing.client.FlyCastClient;\n", "mixin import")
    rr = j(J, "client/RodChain.java" if name == "rf26" else "client/RodItemRenderer.java")
    cut(rr, "                    // \u00a7fly-3d: three sections on a cork handle, the lightest chain in the fleet\n"
            "                    java.util.Map.entry(\"fly\", new float[]{19.0f, 11.0f, 2.975f}),\n", "blank joints")
    sub(rr, "java.util.Map.entry(\"trolling\", -5.7f), java.util.Map.entry(\"fly\", -5.225f));",
        "java.util.Map.entry(\"trolling\", -5.7f));", "blank tip", opt=True)
    sub(rr, "            \"boat\", new float[]{2.75f, 0.8f}, \"trolling\", new float[]{4.15f, 0f},\n"
            "            \"fly\", new float[]{4.65f, 0f});   // \u00a7fly-3d: the trolling handle's seat, half a unit further up the shifted blank",
        "            \"boat\", new float[]{2.75f, 0.8f}, \"trolling\", new float[]{4.15f, 0f});", "reel seat", opt=True)
    sub(rr, "        String rodKey = rod.rodType().modelKey();   // \u00a7fly: the fly rod borrows the ultralight blank",
        "        String rodKey = rod.rodType().modelKey();", "model key 1", opt=True)
    sub(rr, "        String rodKey = stack.getItem() instanceof RodItem r ? r.rodType().modelKey() : \"bamboo\";   // \u00a7fly",
        "        String rodKey = stack.getItem() instanceof RodItem r ? r.rodType().modelKey() : \"bamboo\";", "model key 2", opt=True)

    # ---- the network's own comments ----
    nw = j(J, "network/ModNetwork.java")
    sub(nw, "        // \u00a7fly: a sneak tap on a stop of the fly cast's rhythm.", "        // \u00a7ice-rhythm: a click on a stop of the jig.", "c2s comment")
    sub(nw, "            NetworkManager.registerS2CPayloadType(FlyCastPacket.TYPE, FlyCastPacket.STREAM_CODEC);   // \u00a7fly",
        "            NetworkManager.registerS2CPayloadType(FlyCastPacket.TYPE, FlyCastPacket.STREAM_CODEC);", "s2c payload", opt=True)
    sub(nw, "        // \u00a7fly: the rhythm gauge, on/off and after every beat.", "        // \u00a7ice-rhythm: the jig gauge, on/off and after every accent.", "s2c comment")


def run(tree):
    name = os.path.basename(tree.rstrip("/\\"))
    java(tree, name)
    wr(os.path.join(tree, J, "fishing/JigRhythm.java"), JIG_RHYTHM)
    wr(os.path.join(tree, J, "client/JigClient.java"), dialect26(name, JIG_CLIENT))
    jig_packets(tree)
    drop(tree, J + "fishing/FlyCast.java", J + "fishing/FlyDrift.java", J + "fishing/FlyRises.java",
         J + "fishing/FlySession.java", J + "fishing/FlyStrike.java", J + "client/FlyCastClient.java",
         J + "engine/Hatch.java",
         "tools/check_fly_a.py", "tools/check_fly_b.py", "tools/check_fly_c.py")
    rename_idents(tree)
    assets(tree)
    lang(tree)
    print("  fly removed:", name)
    if MISSES:
        print("  anchors not found (already gone, or this tree spells them differently):")
        for what, f in MISSES:
            print("    -", what, "@", f)
    del MISSES[:]


for t in (sys.argv[1:] or TREES):
    run(t)
