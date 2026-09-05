# -*- coding: utf-8 -*-
"""§aqua-view: the tank shows its water and its modules.

    py -X utf8 tools/patches/p_aquaview.py <root> [1211|1201|26]

The author: "модули должно быть видно; цвет воды должен меняться когда она ухудшается". The water was
an element of the block model — one stained-glass texture, the same at 100% and at 0% — and the two
module slots (10, 11) were invisible: an aerator in a tank looked exactly like no aerator.

Now the renderer draws both. The water is a translucent box the size the model's element was, in a
colour read off be.getWater(): clear blue at 100, green murk at the 50 the fish refuse to spawn in,
brown at nothing (waterColor, one function, every tree). The modules are the block items themselves,
drawn FIXED at a quarter block in the two back corners of the gravel, left slot left, right slot right.
Both are drawn before the "no fish, no roe" early return — an empty tank still has water in it.

Dialects: 1.21.1 and 1.20.1 draw in render() and differ in the vertex chain (addVertex/setColor vs
vertex/color/…/endVertex); the tinted-vertex helper is DERIVED from each file's own roe-quad helper, so
the chain is never spelled by hand. 26.x extracts into State and submits custom geometry.
"""
import io, os, re, sys

ROOT = sys.argv[1]
D = sys.argv[2] if len(sys.argv) > 2 else "1211"
J = os.path.join(ROOT, "common/src/main/java/com/riverfishing")

# ---- the block entity tells its water ------------------------------------------------------------
p = os.path.join(J, "block/AquariumBlockEntity.java")
s = io.open(p, encoding="utf-8").read()
if "§aqua-view" not in s:
    old = "    public ItemStack getRoe() {"
    assert old in s
    s = s.replace(old, """    /** §aqua-view: 0..100, what the renderer colours the water by. */
    public int getWater() {
        return water;
    }

    public ItemStack getRoe() {""", 1)
    io.open(p, "w", encoding="utf-8", newline="\n").write(s)
    print("  AquariumBlockEntity: getWater()")

# ---- the renderer ---------------------------------------------------------------------------------
p = os.path.join(J, "client/AquariumRenderer.java")
s = io.open(p, encoding="utf-8").read()
if "§aqua-view" in s:
    print("  AquariumRenderer: already patched"); sys.exit(0)

RL = "Identifier" if D == "26" else "ResourceLocation"
RT = "RenderTypes.entityTranslucent" if D == "26" else "RenderType.entityTranslucent"

# the tinted vertex helper, derived from the file's own roe-quad helper so the chain is this tree's
helper = re.search(r"    private static void vertex\(Matrix4f m, VertexConsumer vc, float x, float y, float u, float v, int light(?:, int overlay)?\) \{.*?\n    \}", s, re.S)
assert helper, "the roe vertex() helper moved"
tv = helper.group(0)
tv = tv.replace("private static void vertex(Matrix4f m, VertexConsumer vc, float x, float y, float u, float v, int light, int overlay)",
                "private static void tv(Matrix4f m, VertexConsumer vc, float x, float y, float z, float u, float v, int r, int g, int b, int a, float nx, float ny, float nz, int light, int overlay)")
tv = tv.replace("private static void vertex(Matrix4f m, VertexConsumer vc, float x, float y, float u, float v, int light)",
                "private static void tv(Matrix4f m, VertexConsumer vc, float x, float y, float z, float u, float v, int r, int g, int b, int a, float nx, float ny, float nz, int light)")
tv = tv.replace("(m, x, y, 0f)", "(m, x, y, z)").replace("(255, 255, 255, 255)", "(r, g, b, a)").replace("(0f, 1f, 0f)", "(nx, ny, nz)")
tv = re.sub(r"\n\s*//[^\n]*", "", tv)   # drop the roe helper's comments; this one has its own
assert "(r, g, b, a)" in tv and "(nx, ny, nz)" in tv and "(m, x, y, z)" in tv, "could not derive the tinted vertex helper"
OV = ", overlay" if D != "26" else ""

COMMON = """
    // §aqua-view: the water is drawn here, not by the block model, so its colour can say how the tank
    // is doing. The box is the size the model's water element was: 0.6/16 in from the glass, 2/16 to
    // 15/16 up the upper cell, across both cells.
    private static final %(RL)s WATER_TEX = RiverFishing.id("textures/block/aquarium_water.png");
    private static final RenderType WATER_LAYER = %(RT)s(WATER_TEX);
    private static final float W_HX = 1f - 0.6f / 16f, W_HZ = 0.5f - 0.6f / 16f, W_Y0 = 1f + 2f / 16f, W_Y1 = 1f + 15f / 16f;
    /** §aqua-view: the two module slots, drawn as their block items in the back corners of the gravel. */
    private static final float MOD_X = 0.72f, MOD_Z = -0.28f, MOD_Y = 1f + 2f / 16f + 0.13f, MOD_SCALE = 0.5f;

    /**
     * §aqua-view: ARGB for a water percentage. Three stops — clear blue at 100, green murk at 50 (the
     * line under which nothing spawns, so the colour crosses into "wrong" exactly there), brown at 0 —
     * and the alpha climbs as it fouls: dirty water is also water you cannot see through.
     */
    public static int waterColor(int water) {
        float t = Mth.clamp(water / 100f, 0f, 1f);
        int[] good = {0x6F, 0xC0, 0xF0, 0x6C}, mid = {0x7A, 0xB4, 0x8A, 0x8C}, bad = {0x6E, 0x58, 0x2E, 0xC0};
        int[] from = t >= 0.5f ? mid : bad, to = t >= 0.5f ? good : mid;
        float k = t >= 0.5f ? (t - 0.5f) * 2f : t * 2f;
        int r = Math.round(from[0] + (to[0] - from[0]) * k), g = Math.round(from[1] + (to[1] - from[1]) * k);
        int b = Math.round(from[2] + (to[2] - from[2]) * k), a = Math.round(from[3] + (to[3] - from[3]) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** §aqua-view: five faces of the water box, wound outward once — entityTranslucent does not cull, and a second
     *  coplanar pass z-fought the first: the tank flickered. */
    private static void waterBox(Matrix4f m, VertexConsumer vc, int r, int g, int b, int a, int light%(OVDECL)s) {
        float[][] faces = {
                {-W_HX, W_Y1, -W_HZ, -W_HX, W_Y1, W_HZ, W_HX, W_Y1, W_HZ, W_HX, W_Y1, -W_HZ, 0f, 1f, 0f},
                {-W_HX, W_Y0, W_HZ, W_HX, W_Y0, W_HZ, W_HX, W_Y1, W_HZ, -W_HX, W_Y1, W_HZ, 0f, 0f, 1f},
                {W_HX, W_Y0, -W_HZ, -W_HX, W_Y0, -W_HZ, -W_HX, W_Y1, -W_HZ, W_HX, W_Y1, -W_HZ, 0f, 0f, -1f},
                {-W_HX, W_Y0, -W_HZ, -W_HX, W_Y0, W_HZ, -W_HX, W_Y1, W_HZ, -W_HX, W_Y1, -W_HZ, -1f, 0f, 0f},
                {W_HX, W_Y0, W_HZ, W_HX, W_Y0, -W_HZ, W_HX, W_Y1, -W_HZ, W_HX, W_Y1, W_HZ, 1f, 0f, 0f},
        };
        float[] us = {0f, 1f, 1f, 0f}, vs = {1f, 1f, 0f, 0f};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) tv(m, vc, f[i * 3], f[i * 3 + 1], f[i * 3 + 2], us[i], vs[i], r, g, b, a, f[12], f[13], f[14], light%(OV)s);
        }
    }

%(TV)s
""" % {"RL": RL, "RT": RT, "TV": tv, "OV": OV, "OVDECL": ", int overlay" if D != "26" else ""}

if D != "26":
    old = """        java.util.List<ItemStack> fishes = be.getFishes();
        ItemStack roe = be.getRoe();
        if (fishes.isEmpty() && roe.isEmpty()) return;
"""
    assert old in s, "render() head moved"
    s = s.replace(old, """        java.util.List<ItemStack> fishes = be.getFishes();
        ItemStack roe = be.getRoe();
""", 1)
    old = """        double tankX = 0.5 + cw.getStepX() * 0.5;
        double tankZ = 0.5 + cw.getStepZ() * 0.5;
"""
    assert s.count(old) == 1, "tank centre moved"
    s = s.replace(old, old + """
        // §aqua-view: the water and the modules first — an empty tank still has water in it.
        renderWater(be, facing, tankX, tankZ, pose, buffers, light, overlay);
        renderModules(be, facing, tankX, tankZ, pose, buffers, light, overlay);
        if (fishes.isEmpty() && roe.isEmpty()) return;
""", 1)
    methods = COMMON + """
    private void renderWater(AquariumBlockEntity be, Direction facing, double tankX, double tankZ,
                             PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int water = be.getWater();
        if (water <= 0) return;
        int c = waterColor(water);
        pose.pushPose();
        pose.translate(tankX, 0, tankZ);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        waterBox(pose.last().pose(), buffers.getBuffer(WATER_LAYER),
                (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF, light, overlay);
        pose.popPose();
    }

    private void renderModules(AquariumBlockEntity be, Direction facing, double tankX, double tankZ,
                               PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        for (int slot = 10; slot <= 11; slot++) {
            ItemStack m = be.getItem(slot);
            if (m.isEmpty()) continue;
            pose.pushPose();
            pose.translate(tankX, MOD_Y, tankZ);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            pose.translate(slot == 10 ? -MOD_X : MOD_X, 0, MOD_Z);
            pose.scale(MOD_SCALE, MOD_SCALE, MOD_SCALE);
            itemRenderer.renderStatic(m, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }
    }
"""
    anchor = "    private void renderNameplate("
    assert anchor in s
    s = s.replace(anchor, methods + "\n" + anchor, 1)
else:
    old = """        s.roeFrame = -1;
        List<ItemStack> fishes = be.getFishes();
        ItemStack roe = be.getRoe();
        if (fishes.isEmpty() && roe.isEmpty()) return;
"""
    assert old in s, "extractRenderState head moved"
    s = s.replace(old, """        s.roeFrame = -1;
        s.modules.clear();
        s.moduleLeft.clear();
        s.waterArgb = 0;
        List<ItemStack> fishes = be.getFishes();
        ItemStack roe = be.getRoe();
""", 1)
    old = """        double tankX = 0.5 + cw.getStepX() * 0.5;
        double tankZ = 0.5 + cw.getStepZ() * 0.5;
"""
    assert s.count(old) == 1, "tank centre moved"
    s = s.replace(old, old + """
        // §aqua-view: the water and the modules first — an empty tank still has water in it.
        s.tankX = tankX; s.tankZ = tankZ; s.tankYRot = -facing.toYRot();
        s.waterArgb = be.getWater() <= 0 ? 0 : waterColor(be.getWater());
        for (int slot = 10; slot <= 11; slot++) {
            ItemStack m = be.getItem(slot);
            if (m.isEmpty()) continue;
            ItemStackRenderState st = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(st, m, ItemDisplayContext.FIXED, be.getLevel(), null, slot);
            s.modules.add(st);
            s.moduleLeft.add(slot == 10);
        }
        if (fishes.isEmpty() && roe.isEmpty()) return;
""", 1)
    old = "        float plateYRot;"
    assert old in s, "State.plateYRot moved"
    s = s.replace(old, """        float plateYRot;
        int waterArgb;                                    // §aqua-view: 0 = no water to draw
        float tankYRot;
        double tankX, tankZ;
        final List<ItemStackRenderState> modules = new ArrayList<>();
        final List<Boolean> moduleLeft = new ArrayList<>();""", 1)
    old = """    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.roeFrame >= 0) submitRoe(s, pose, collector);"""
    assert old in s, "submit() head moved"
    s = s.replace(old, """    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        submitWater(s, pose, collector);
        submitModules(s, pose, collector);
        if (s.roeFrame >= 0) submitRoe(s, pose, collector);""", 1)
    methods = COMMON + """
    private static void submitWater(State s, PoseStack pose, SubmitNodeCollector collector) {
        if (s.waterArgb == 0) return;
        int c = s.waterArgb, light = s.lightCoords;
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF, a = (c >>> 24) & 0xFF;
        pose.pushPose();
        pose.translate(s.tankX, 0, s.tankZ);
        pose.mulPose(Axis.YP.rotationDegrees(s.tankYRot));
        collector.submitCustomGeometry(pose, WATER_LAYER, (p, vc) -> waterBox(p.pose(), vc, r, g, b, a, light));
        pose.popPose();
    }

    private static void submitModules(State s, PoseStack pose, SubmitNodeCollector collector) {
        for (int i = 0; i < s.modules.size(); i++) {
            pose.pushPose();
            pose.translate(s.tankX, MOD_Y, s.tankZ);
            pose.mulPose(Axis.YP.rotationDegrees(s.tankYRot));
            pose.translate(s.moduleLeft.get(i) ? -MOD_X : MOD_X, 0, MOD_Z);
            pose.scale(MOD_SCALE, MOD_SCALE, MOD_SCALE);
            s.modules.get(i).submit(pose, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
"""
    anchor = "    private static void submitFry("
    assert anchor in s
    s = s.replace(anchor, methods + "\n" + anchor, 1)

io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("  AquariumRenderer: water coloured by quality, modules drawn (%s)" % D)
print("done")
