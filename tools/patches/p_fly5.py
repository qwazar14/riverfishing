# -*- coding: utf-8 -*-
"""§fly-5: the cast gauge leaves the screen and becomes the cast.

The needle at the bottom of the screen was the last thing left that was a QTE bar rather than fishing. It
is gone. What tells the angler where the rod is now:

  * **the rod itself** — the blank loads back as the swing goes behind and comes up as it goes forward.
    The mod already had that animation for the charge cast (`RodHandTransform.castPitch`); the fly rod's
    swing drives it instead of a power bar.
  * **the line in the air** — a loop drawn from the rod tip, sweeping from behind the angler to out in
    front once a cycle and growing by three metres every swing. The loop IS the distance readout: there is
    nothing to read off a plaque, you look at how much line is in the air.
  * **the swish** at each stop, which was already there.

Release as the loop comes forward. The winter jig keeps its gauge — nobody complained about that one.
Idempotent; run on each tree.  py tools/patches/p_fly5.py [tree ...]"""
import io, os, sys

MAIN = r"C:/Users/Qwazar/VS Code Projects/fishing mod"
TREES = [MAIN, r"C:/Users/Qwazar/wt/rf1201", r"C:/Users/Qwazar/wt/rf26"]
J = "common/src/main/java/com/riverfishing/"
sys.path.insert(0, os.path.join(MAIN, "tools", "patches"))
from p_fly2 import client_dialect

# how each tree spells "the partial tick of this frame"
PARTIAL = {
    "fishing mod": "mc.getTimer().getGameTimeDeltaPartialTick(false)",
    "rf1201": "mc.getFrameTime()",
    "rf26": "mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)",
}
# and where each tree poses the rod in the hand
CAST_ANIM = {
    "fishing mod": "client/RodItemRenderer.java",
    "rf1201": "client/RodItemRenderer.java",
    "rf26": "mixin/ItemInHandRendererMixin.java",
}

CAST_LINE = '''    /**
     * §fly-5: the line in the air. While the rod false-casts there is no line on the water and nothing on
     * the screen — the loop is the gauge. It sweeps from behind the angler to out in front once a cycle
     * and grows three metres with every swing, so how much line is in the air is something you look at.
     * Let it go as the loop comes forward and that is the cast.
     */
    private static void drawCastLine(Minecraft mc, MultiBufferSource buffers, Matrix4f m, Matrix3f nrm, float pt) {
        Player player = mc.player;
        double metres = FlyCastClient.airMetres();
        if (metres <= 0) return;
        Vec3 tip = rodTipAnchor(mc, player, pt);
        float load = FlyCastClient.loadFraction(pt);        // 1 at the back stop, 0 at the forward stop
        Vec3 look = player.getViewVector(pt);
        double hl = Math.sqrt(look.x * look.x + look.z * look.z);
        Vec3 fwd = hl < 1e-3 ? new Vec3(0, 0, 1) : new Vec3(look.x / hl, 0, look.z / hl);
        Vec3 dir = fwd.scale(1.0 - 2.0 * load);             // out in front at one stop, behind at the other
        double len = metres * 0.9;
        Vec3 far = tip.add(dir.scale(len)).add(0, 0.6 + len * 0.10, 0);
        Vec3 ctrl = tip.add(dir.scale(len * 0.45)).add(0, 1.4 + len * 0.28, 0);

        float[] style = null;
        var held = player.getMainHandItem().getItem() instanceof com.riverfishing.item.RodItem
                ? player.getMainHandItem() : player.getOffhandItem();
        if (held.getItem() instanceof com.riverfishing.item.RodItem
                && com.riverfishing.item.RodData.get(held, com.riverfishing.component.ComponentSlot.LINE)
                        .getItem() instanceof com.riverfishing.item.LineItem li) {
            style = RodRenderTypes.strandStyle(li.lineType(), li.diameterMm());
        }
        VertexConsumer sv = buffers.getBuffer(
                style == null ? RenderType.lines() : RodRenderTypes.lineStrand(style[4]));
        int alpha = style == null ? 255 : (int) style[3];
        int cr = 0xE8, cg = 0xE4, cb = 0xD0;

        Vec3 prev = tip;
        for (int k = 1; k <= 20; k++) {
            double f = k / 20.0, g = 1.0 - f;
            Vec3 p = new Vec3(g * g * tip.x + 2 * g * f * ctrl.x + f * f * far.x,
                    g * g * tip.y + 2 * g * f * ctrl.y + f * f * far.y,
                    g * g * tip.z + 2 * g * f * ctrl.z + f * f * far.z);
            line(sv, m, nrm, prev, p, cr, cg, cb, alpha);
            prev = p;
        }
    }

'''

CLIENT_API = '''    /** §fly-5: the rod is swinging a cast right now — the hand pose and the airborne loop read this. */
    public static boolean isCasting() {
        return active && mode == 0;
    }

    /** 0 at the forward stop, 1 at the back stop: how far behind the angler the rod is loaded. */
    public static float loadFraction(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!isCasting() || mc.level == null) return 0f;
        return 1f - marker((mc.level.getGameTime() - startTick) + partialTick);
    }

    /** Metres of line in the air this instant — the length the loop is drawn at. */
    public static double airMetres() {
        Minecraft mc = Minecraft.getInstance();
        if (!isCasting() || mc.level == null) return 0;
        return FlyCast.lineOut(mc.level.getGameTime() - startTick, maxBeats);
    }

'''


GAUGE_OFF = "        if (mode == 0) return;   // \u00a7fly-5: the cast is drawn in the world, not here\n"


def rd(p): return io.open(p, encoding="utf-8").read()
def wr(p, s): io.open(p, "w", encoding="utf-8", newline="\n").write(s)


def sub(path, old, new, what):
    s = rd(path)
    if new in s: return
    assert s.count(old) == 1, "%s @ %s (%d)" % (what, path, s.count(old))
    wr(path, s.replace(old, new, 1))


def run(tree):
    name = os.path.basename(tree.rstrip("/\\"))
    j = lambda *a: os.path.join(tree, *a)
    # ---- the client: the accessors the world needs, and no gauge for the cast ----
    if tree == MAIN:
        c = j(J, "client/FlyCastClient.java")
        sub(c, "    /** A fly rod is in the hand. */", CLIENT_API + "    /** A fly rod is in the hand. */", "client api")
        sub(c, "        ItemStack rod = heldRhythmRod(mc);\n        if (rod.isEmpty()) return;",
            "        if (mode == 0) return;   // §fly-5: the cast is drawn in the world, not here\n"
            "        ItemStack rod = heldRhythmRod(mc);\n        if (rod.isEmpty()) return;", "no cast gauge")
    else:
        c = client_dialect(name, rd(os.path.join(MAIN, J, "client/FlyCastClient.java")))
        if name == "rf26":
            # no airborne loop on that tree yet, so it keeps the gauge rather than showing nothing
            c = c.replace(GAUGE_OFF, "")
        wr(j(J, "client/FlyCastClient.java"), c)
    # ---- the rod loads back and comes forward with the swing ----
    anim = j(J, CAST_ANIM[name])
    sub(anim, "            chargePower = RodItem.castPower(used);",
        "            // §fly-5: a fly rod's pose follows its own swing, not a power bar\n"
        "            chargePower = FlyCastClient.isCasting()\n"
        "                    ? FlyCastClient.loadFraction(%s)\n"
        "                    : RodItem.castPower(used);" % PARTIAL[name], "cast anim")
    if "import com.riverfishing.client.FlyCastClient;" not in rd(anim) and "mixin" in CAST_ANIM[name]:
        sub(anim, "import com.riverfishing.client.ClientLineState;",
            "import com.riverfishing.client.ClientLineState;\nimport com.riverfishing.client.FlyCastClient;", "mixin import")
    # ---- the line in the air ----
    # 26.x draws the line twice (26.1 immediate, 26.2 submit) behind Stonecutter comments, so the loop is
    # not ported there yet — and until it is, that tree keeps its HUD gauge rather than nothing at all.
    if name == "rf26":
        print("  patched", name, "(rod swing only; 26.x keeps the gauge until the airborne line is ported)")
        return
    lr = j(J, "client/LineRenderer.java")
    sub(lr, "        if (mc.level == null || mc.player == null || ClientLineState.lines().isEmpty()) return;",
        "        if (mc.level == null || mc.player == null) return;\n"
        "        boolean casting = FlyCastClient.isCasting();   // §fly-5: a cast in the air draws even with no line out\n"
        "        if (!casting && ClientLineState.lines().isEmpty()) return;", "renderer guard")
    sub(lr, "        if (drew) {\n            buffers.endBatch();",
        "        if (casting) {\n            drawCastLine(mc, buffers, m, nrm, pt);\n            drew = true;\n        }\n"
        "        if (drew) {\n            buffers.endBatch();", "cast line call")
    sub(lr, "    private static void renderLine(Minecraft mc, MultiBufferSource buffers,", CAST_LINE
        + "    private static void renderLine(Minecraft mc, MultiBufferSource buffers,", "cast line body")
    print("  patched", name)


for t in (sys.argv[1:] or TREES): run(t)
