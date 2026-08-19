package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * §rod-bend-3d on 26.x: a segmented blank drawn as a bone chain.
 *
 * <p>Each joint's rotation is applied to the shared pose and LEFT there, so every segment past it
 * inherits the ones before it — forward kinematics, which the model format cannot express (flat element
 * list, rotation baked at load, only 0/±22.5/±45 allowed) and a renderer can, at any angle, every
 * frame. That half is arithmetic and comes over from 1.21.1 unchanged, joint tables and all.
 *
 * <p>What is 26.x's own is how a segment gets drawn. There is no BakedModel and no ItemRenderer here:
 * a model is fetched from the model manager as an {@link ItemModel}, asked to fill an
 * {@link ItemStackRenderState}, and that state is submitted under OUR pose. The same three steps the
 * engine takes for any item — the only difference is whose PoseStack it lands on.
 *
 * <p>A rod absent from {@link #JOINTS} has no segment models. {@link #submit} says so by returning
 * false, and the caller keeps the flat model rather than drawing nothing.
 */
public final class RodChain {
    private RodChain() {}

    /**
     * Joint positions along each segmented blank, in model units, butt to tip — one per bending
     * section, sitting at that section's own rotation origin.
     *
     * <p>Listed here rather than read from the models because a baked model has thrown its element
     * origins away by the time a renderer sees it. Regenerate with tools/split_rod.js, which prints
     * the array it just cut the geometry along. Kept identical to 1.21.1's table on purpose: both
     * lines must bend the same rod the same way, or a screenshot from one proves nothing about the other.
     */
    private static final java.util.Map<String, float[]> JOINTS = java.util.Map.ofEntries(
            java.util.Map.entry("feeder", new float[]{14f, 3f, -5f, -10f}),
            java.util.Map.entry("pole", new float[]{15.2f, 7.4f, -0.4f, -8.2f}),
            java.util.Map.entry("spinning", new float[]{14f, 3f, -5f, -10f}),
            java.util.Map.entry("ultralight", new float[]{14.8f, 5.5f, -0.8f, -4.1f}),
            java.util.Map.entry("surf", new float[]{12.9f, 5.3f, -2.3f, -9.9f}),
            java.util.Map.entry("carp", new float[]{13.8f, 6.46f, -0.88f, -8.21f}),
            java.util.Map.entry("boat", new float[]{10.94f, 5.39f, -0.16f, -5.66f, -11.16f}),
            java.util.Map.entry("bottom", new float[]{12.99f, 5.59f, -1.81f, -9.21f}),
            java.util.Map.entry("trolling", new float[]{10.31f, 2.21f, -2.54f}),
            java.util.Map.entry("sea_spin",
                    new float[]{9.15f, 3.15f, -3.15f, -7.95f, -10.95f, -12.95f, -14.58f}),
            java.util.Map.entry("bamboo", new float[]{19.667f, 13.333f, 7f, 0.667f, -5.667f}));

    /** Both joints and blank sit on this axis in model units; the chain hinges about Z through it. */
    private static final float AXIS_Y = 10.5f, AXIS_Z = 8.5f;

    /** Total tip deflection at full load. Public so /rfrod can tune it live, as on 1.21.1. */
    public static float MAX_BEND_DEG = 80f;

    /** Master switch, ON by default — /rfrod blank off drops back to the flat model. */
    public static boolean ENABLED = true;

    /**
     * Share of the total bend taken by joint i of n, butt to tip: a triangular split,
     * 2(i+1)/(n(n+1)), so a rod loads progressively and the tip joint swings furthest. Sums to 1 for
     * ANY joint count, which is why it is a formula and not a table — a fixed table gained a fifth
     * joint once and summed to 1.4, bending the rod past its own maximum.
     */
    private static float jointShare(int i, int n) {
        return 2f * (i + 1) / (n * (n + 1));
    }

    /** Does this rod have a chain to draw at all? Cheap, and free of any render state. */
    public static boolean has(String rodKey) {
        return ENABLED && JOINTS.containsKey(rodKey);
    }

    /**
     * Draws the blank butt-to-tip under {@code pose}. Returns false without drawing anything if this
     * rod has no segments or the handle is missing, so the caller can fall back cleanly instead of
     * leaving half a rod on screen.
     *
     * <p>The caller owns push/pop: this leaves the pose where the TIP is, which is exactly the frame
     * the line anchor has to be captured in.
     */
    public static boolean submit(ItemStack stack, String rodKey, float load, ItemDisplayContext ctx,
                                 PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
        float[] joints = JOINTS.get(rodKey);
        if (!ENABLED || joints == null) return false;
        if (!segment(stack, ctx, RodModelLayers.segmentItemModel(rodKey, 0), pose, collector, light, overlay)) {
            return false;   // no handle model — this rod is not really segmented; do not half-draw it
        }
        float jy = AXIS_Y / 16f - 0.5f, jz = AXIS_Z / 16f - 0.5f;
        for (int i = 0; i < joints.length; i++) {
            // A model coord e lands at e/16 - 0.5 in this frame, so the joint pivots about THAT point
            // and not about e/16. Getting it wrong hinges the rod about a spot beside itself.
            float jx = joints[i] / 16f - 0.5f;
            float bendDeg = load * MAX_BEND_DEG * jointShare(i, joints.length);
            pose.translate(jx, jy, jz);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(bendDeg));
            pose.translate(-jx, -jy, -jz);
            segment(stack, ctx, RodModelLayers.segmentItemModel(rodKey, i + 1), pose, collector, light, overlay);
        }
        return true;
    }

    /**
     * One rigid piece. The render state is reused rather than allocated per segment per frame — the
     * sea spin alone draws eight of these every frame, and the class exists to be filled and cleared.
     */
    private static final ItemStackRenderState STATE = new ItemStackRenderState();

    private static boolean segment(ItemStack stack, ItemDisplayContext ctx, Identifier id,
                                   PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        // ASK FIRST. getItemModel answers a miss with the MISSING model, which is a real model with real
        // geometry — that is what it is for — so there is nothing about the value it returns that says
        // "this did not resolve". isEmpty() is false for it, which is how the purple cube got drawn.
        // The definition's json is either in the pack or it is not, and that is knowable without asking
        // the resolver anything.
        Identifier json = Identifier.fromNamespaceAndPath(id.getNamespace(), "items/" + id.getPath() + ".json");
        if (mc.getResourceManager().getResource(json).isEmpty()) return false;
        ItemModel model = mc.getModelManager().getItemModel(id);
        if (model == null) return false;
        STATE.clear();
        model.update(STATE, stack, mc.getItemModelResolver(), ctx, mc.level, mc.player, 0);
        if (STATE.isEmpty()) return false;
        STATE.submit(pose, collector, light, overlay, 0);
        return true;
    }
}
