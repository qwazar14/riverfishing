package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.riverfishing.component.ComponentSlot;
import com.riverfishing.item.LineItem;
import com.riverfishing.item.ReelItem;
import com.riverfishing.item.RodData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * §rod-bend-3d on 26.x: a segmented blank drawn as a bone chain — plus everything that lives ON the
 * chain: the seated reel with its spinning crank (§reel-3d/§reel-crank), the line threaded from the
 * spool through every guide ring to the tip (§line-thru-guides), the tip capture the water line
 * anchors to (§rod-tip-3d), and the first-person water line itself (§hand-line).
 *
 * <p>Each joint's rotation is applied to the shared pose and LEFT there, so every segment past it
 * inherits the ones before it — forward kinematics, which the model format cannot express (flat element
 * list, rotation baked at load, only 0/±22.5/±45 allowed) and a renderer can, at any angle, every
 * frame. That half is arithmetic and came over from 1.21.1 unchanged, joint tables and all.
 *
 * <p>What is 26.x's own is how anything gets drawn. There is no BakedModel and no ItemRenderer here:
 * a model is fetched from the model manager as an {@link ItemModel}, asked to fill an
 * {@link ItemStackRenderState}, and that state is submitted under OUR pose. Custom geometry (the
 * threaded line, the first-person string) rides {@code submitCustomGeometry} — the vertices are
 * pre-multiplied into render space, so the pose handed over is identity.
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

    /**
     * §rod-tip-3d: the tip of each 3D blank in model units — the point the line leaves from. The
     * blanks are not one 16-unit sprite: they run from x=2.5 (stick) to x=-16, so a single
     * hand-tuned anchor cannot serve them. Identical to 1.21.1's table.
     */
    private static final java.util.Map<String, Float> BLANK_TIP_X = java.util.Map.ofEntries(
            java.util.Map.entry("feeder", -16f), java.util.Map.entry("pole", -16f),
            java.util.Map.entry("bamboo", -12.1f), java.util.Map.entry("stick", 2.5f),
            java.util.Map.entry("spinning", -16f), java.util.Map.entry("ultralight", -8.5f),
            java.util.Map.entry("winter", 21.9f), java.util.Map.entry("sea_spin", -16f),
            java.util.Map.entry("bottom", -16f), java.util.Map.entry("carp", -16f),
            java.util.Map.entry("surf", -16f), java.util.Map.entry("boat", -15.7f),
            java.util.Map.entry("trolling", -5.7f));

    /**
     * §reel-3d: how far each rod's reel-seat centre sits from the feeder's {@code {dx, dy}}, in model
     * units — the reel master is authored docked into the FEEDER's seat (centre x 19.5, underside
     * 9.45), so mounting it on another rod is one translate along (and up) the blank. A rod absent
     * here takes no reel (RodType.takesReel is false). Identical to 1.21.1's table; the audit's
     * §seat-sync check cross-verifies it against every model's own seat geometry.
     */
    private static final java.util.Map<String, float[]> REEL_SEAT_DX = java.util.Map.of(
            "feeder", new float[]{0f, 0f}, "spinning", new float[]{0f, 0f},
            "ultralight", new float[]{0.8f, 0.4f},
            "sea_spin", new float[]{1.25f, 0f}, "bottom", new float[]{3f, 0.52f},
            "carp", new float[]{4.25f, 0.4f}, "surf", new float[]{4f, 0.6f},
            "boat", new float[]{2.75f, 0.8f}, "trolling", new float[]{4.15f, 0f});

    /** Both joints and blank sit on this axis in model units; the chain hinges about Z through it. */
    private static final float AXIS_Y = 10.5f, AXIS_Z = 8.5f;

    /** Total tip deflection at full load. Public so /rfrod can tune it live, as on 1.21.1. */
    public static float MAX_BEND_DEG = 80f;

    /**
     * §rod-physics: how much of the spring lag becomes blank FLEX rather than the whole rod swinging.
     * The per-rod profiles (rod_physics.json) override this; it is the fallback and the /rfrod knob.
     */
    public static float WHIP_GAIN = 2.0f;

    /**
     * §hand-line: the FOV the first-person hand pass projects at, degrees. Vanilla 1.21.1 used a
     * CONSTANT 70; if 26.x turns out to project the hand at the world fov, set this to 0 in game
     * ({@code /rfrod handfov 0}) and the warp collapses to 1. A knob because the only way to know is
     * to look — the 1.20.1 port taught that the hard way.
     */
    public static float HAND_FOV = 70f;

    /**
     * §hand-space: WHICH space the hand pass leaves in {@code pose.last()} — the one thing about
     * 26.x's hand pipeline that the bytecode does not settle. The pass multiplies in the inverse of
     * one matrix and pushes that same matrix onto the model-view stack, so a submitted node lands
     * either in eye space (0) or in world-relative-to-camera space (1) depending on WHICH matrix
     * that is. Guessing it wrong points the far end of the line behind the angler, which is exactly
     * what it did.
     *
     * <p>So it is MEASURED, not assumed — see {@link #sampleHandSpace}. -1 = measure and latch;
     * 0 or 1 pins it ({@code /rfrod handspace view|world|auto}).
     */
    public static int HAND_SPACE = -1;
    private static int handSpaceLatch = 0;
    private static boolean spaceDecided, spaceHasPrev;
    private static final org.joml.Vector3f spaceTipPrev = new org.joml.Vector3f();
    private static final org.joml.Quaternionf spaceCamPrev = new org.joml.Quaternionf();
    /** What the deciding turn saw, for /rfrod tipinfo to show its work. */
    private static float spaceEvidenceView, spaceEvidenceWorld;

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

    /** §pod-3d diagnostics: rods whose pod fallback has already been explained in the log. */
    private static final java.util.Set<String> podComplained = new java.util.HashSet<>();

    /** A 3D blank with no joints listed: one rigid piece, drawn but never bent (stick, winter). */
    private static final float[] NO_JOINTS = new float[0];

    /**
     * Is this stack the LOCAL player's held rod? Reference equality first; component equality as the
     * fallback because 26.x render STATES may carry copies of the held stack, and a copy must still
     * count as ours or the third-person tip capture never fires. A byte-identical rod in another
     * player's hands could false-positive, but per-rod NBT (line wear, hook wear) diverges fast.
     */
    public static boolean localHeld(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack main = mc.player.getMainHandItem(), off = mc.player.getOffhandItem();
        return stack == main || stack == off
                || ItemStack.isSameItemSameComponents(stack, main)
                || ItemStack.isSameItemSameComponents(stack, off);
    }

    /**
     * Does this rod have a chain to draw at all? Every rod with a handle model does — a rod absent
     * from {@link #JOINTS} is a one-piece blank, which is a chain of zero joints, not a sprite.
     */
    public static boolean has(String rodKey) {
        return ENABLED;
    }

    // ===== §rod-tip-3d: where the drawn tip landed, captured while the rod renders =====
    /**
     * First person: the tip in NDC — projected with the HAND pass's own fov, read by
     * {@link LineRenderer} the same frame as the world-pass fallback anchor. The primary FP string is
     * §hand-line below, which needs no projection agreement at all.
     */
    public static final float[] TIP_NDC = new float[2];
    public static long tipNdcFrame = Long.MIN_VALUE;

    public static boolean tipNdcFresh() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && tipNdcFrame >= mc.level.getGameTime() - 1;
    }

    /**
     * Third person: the local player's drawn tip in VIEW space — entity rendering poses are
     * camera-rotated, so this is what pose.last() naturally yields; {@link LineRenderer} rotates it
     * back to world with the camera quaternion. Without this an F5 fight ran the line from the old
     * shoulder anchor while the bent 3D tip waved two blocks away.
     */
    public static final float[] TIP_VIEW = new float[3];
    public static long tipViewFrame = Long.MIN_VALUE;

    public static boolean tipViewFresh() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && tipViewFrame >= mc.level.getGameTime() - 1;
    }

    /** §hand-line: the frame the FP string last drew — the world pass skips it while this is fresh. */
    public static long handLineFrame = Long.MIN_VALUE;

    public static boolean handLineFresh() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && handLineFrame >= mc.level.getGameTime() - 1;
    }

    /**
     * §bend-plane: where the line's pull sits relative to the angler's view, -1 = hard left,
     * +1 = hard right, 0 = straight ahead. The cast point off-centre tilts the whole bend toward it
     * (the line leaves the tip TOWARD the fish, so that is where the blank is loaded), and a
     * directed run drags it further the way the fish is going. This is what turns "the rod bends"
     * into "the rod bends AT the fish".
     */
    private static float fightLateral() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0f;
        ClientLineState.Line own = ClientLineState.lines().get(mc.player.getId());
        if (own == null) return 0f;
        var d = net.minecraft.world.phys.Vec3.atCenterOf(own.target).subtract(mc.player.position());
        float yawTo = (float) Math.toDegrees(net.minecraft.util.Mth.atan2(-d.x, d.z));
        float off = net.minecraft.util.Mth.degreesDifference(mc.player.getYRot(), yawTo);
        // saturates by 45° off-view: countering a run (fish left, camera swung right) should put the
        // blank FLAT on its side, not politely diagonal
        float lat = net.minecraft.util.Mth.clamp(off / 45f, -1f, 1f);
        if (own.fighting) {
            // fish running LEFT drags the tip further left — same sign language the lean spoke
            lat += own.course == 1 ? -0.5f : own.course == 2 ? 0.5f : 0f;
        }
        return net.minecraft.util.Mth.clamp(lat, -1f, 1f);
    }

    /**
     * §bend-plane vertical: where the line's pull sits against the view PITCH, +1 = clearly below
     * (the normal stance — bend down, the classic arc), -1 = above the view axis (the camera dragged
     * DOWN past the line). Without the sign, pulling the camera down while the rod also bent down
     * folded the two into one ugly crumple at the bottom of the screen; signed, the blank rolls over
     * and bows UP toward the line, which is where the pull genuinely is.
     */
    private static float fightVertical() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 1f;
        ClientLineState.Line own = ClientLineState.lines().get(mc.player.getId());
        if (own == null) return 1f;
        var d = net.minecraft.world.phys.Vec3.atCenterOf(own.target).subtract(mc.player.getEyePosition());
        // MC pitch runs positive-DOWN; so does this, so "pull below view" comes out positive.
        float pitchTo = (float) Math.toDegrees(net.minecraft.util.Mth.atan2(-d.y,
                Math.sqrt(d.x * d.x + d.z * d.z)));
        float off = pitchTo - mc.player.getXRot();
        // The hand pose carries the blank ~25° above the view axis, so the pull is still below the
        // ROD well after it crosses the view line — the +25 bias keeps the flip where the rod is,
        // not where the crosshair is. Saturates over 40°, same spirit as the lateral's 45.
        float vert = net.minecraft.util.Mth.clamp((off + 25f) / 40f, -1f, 1f);
        if (own.fighting) {
            // a sounding fish drags the tip deeper, one coming up to jump lifts it — same sign
            // language the course lean speaks
            vert += own.course == 3 ? 0.4f : own.course == 4 ? -0.4f : 0f;
        }
        return net.minecraft.util.Mth.clamp(vert, -1f, 1f);
    }

    /**
     * Draws the blank butt-to-tip under {@code pose}, then everything riding it: reel + crank,
     * threaded line, tip captures, and (first person) the water string itself. Returns false without
     * drawing anything if the handle model is missing, so the caller can fall back cleanly instead of
     * leaving half a rod on screen.
     *
     * <p>The caller owns push/pop: this leaves the pose where the TIP is, which is exactly the frame
     * the line anchor has to be captured in.
     */
    public static boolean submit(ItemStack stack, String rodKey, float load, ItemDisplayContext ctx,
                                 PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
        if (!ENABLED) return false;
        RodPhysics.update();   // idempotent within a frame; this is the one place every context passes
        float[] joints = JOINTS.getOrDefault(rodKey, NO_JOINTS);
        if (!piece(stack, RodModelLayers.segmentItemModel(rodKey, 0), pose, collector, light, overlay)) {
            return false;   // no handle model — this rod is not really segmented; do not half-draw it
        }
        // §reel-3d: seated on s0, which never bends — drawn in the base frame before any joint turns.
        submitReel(stack, rodKey, load, pose, collector, light, overlay);
        // §line-thru-guides: thread points are captured stage by stage as the chain bends, so the
        // line rides each segment's rings instead of cutting a chord across the bend.
        float[][] lp = guideLinePoints(stack, rodKey);
        org.joml.Vector3f[] thread = lp == null ? null : new org.joml.Vector3f[lp.length];
        captureLineStage(thread, lp, pose, joints, 0);

        // §bend-plane: the chain bends TOWARD the pull. The lateral fraction tilts the bend plane;
        // the vertical is SIGNED by where the pull sits against the view pitch, so dragging the
        // camera down past the line bows the blank UP toward it. The yaw sign flips because a
        // positive Y rotation swings the tip screen-LEFT while the lateral is positive to the RIGHT;
        // the 1.4 gain buys back the width the hand pose's rz tilt takes from an on-axis yaw.
        float lat = load > 0f ? fightLateral() : 0f;
        float vert = load > 0f ? fightVertical() : 1f;
        float bendVert = Math.max(0.2f, (float) Math.sqrt(Math.max(0f, 1f - lat * lat))) * vert;
        float bendYaw = -lat * 1.4f;
        // §rod-physics-per-rod: how much a blank whips is the blank's own number.
        float whipGain = RodPhysics.profileFor(rodKey)[2];

        float jy = AXIS_Y / 16f - 0.5f, jz = AXIS_Z / 16f - 0.5f;
        for (int i = 0; i < joints.length; i++) {
            float share = jointShare(i, joints.length);
            // A model coord e lands at e/16 - 0.5 in this frame, so the joint pivots about THAT point
            // and not about e/16. Getting it wrong hinges the rod about a spot beside itself.
            float jx = joints[i] / 16f - 0.5f;
            // §rod-physics: the whip. The rigid lag is already in the hand pose; this spreads more of
            // it down the joints with the same tip-heavy weights the bend uses, so the tip trails the
            // butt instead of the whole rod swinging as one board. Z is the bend plane, Y is sideways.
            float whipPitch = RodPhysics.pitch() * whipGain * share;
            float whipYaw = RodPhysics.yaw() * whipGain * share;
            float bendDeg = load * MAX_BEND_DEG * share;
            pose.translate(jx, jy, jz);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(bendDeg * bendVert + whipPitch));
            float yawDeg = bendDeg * bendYaw + whipYaw;
            if (yawDeg != 0f) pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yawDeg));
            pose.translate(-jx, -jy, -jz);
            piece(stack, RodModelLayers.segmentItemModel(rodKey, i + 1), pose, collector, light, overlay);
            captureLineStage(thread, lp, pose, joints, i + 1);
        }
        submitThread(collector, thread, lineStyle(stack));
        // Captured HERE, at the end of the chain: every joint rotation is still applied, which is the
        // frame the tip segment was just drawn in.
        captureTipNdc(pose, rodKey, ctx);
        captureTipView(pose, rodKey, ctx, stack);
        drawHandLine(stack, ctx, collector);
        return true;
    }

    /** §pod-3d: where this rod's 3D blank ends, in model units — null for rods with no entry. */
    public static Float blankTipX(String rodKey) {
        return BLANK_TIP_X.get(rodKey);
    }

    /**
     * §pod-3d: the docked rod as a STRAIGHT chain — every segment in one shared frame, the fitted
     * reel with a resting crank, and the line threaded along the unbent blank. A podded rod is not
     * being fought, so no joints turn; drawing the pieces without rotations reassembles the source
     * model exactly. Returns false (drawing nothing) when the handle model is absent, so the caller
     * keeps its sprite fallback.
     */
    public static boolean submitPod(ItemStack stack, PoseStack pose, SubmitNodeCollector collector,
                                    int light, int overlay) {
        if (!ENABLED) return false;
        if (!(stack.getItem() instanceof com.riverfishing.item.RodItem rod)) return false;
        String rodKey = rod.rodType().jsonKey();
        if (!piece(stack, RodModelLayers.segmentItemModel(rodKey, 0), pose, collector, light, overlay)) {
            // §pod-3d: the pod fell back to the flat sprite in testing and nothing said why. piece()
            // refuses for four different reasons; say which, ONCE per rod, so the log answers it
            // instead of another round of guessing.
            if (podComplained.add(rodKey)) {
                Minecraft mc = Minecraft.getInstance();
                Identifier id = RodModelLayers.segmentItemModel(rodKey, 0);
                Identifier json = Identifier.fromNamespaceAndPath(
                        id.getNamespace(), "items/" + id.getPath() + ".json");
                com.riverfishing.RiverFishing.LOGGER.warn(
                        "[riverfishing] pod 3D refused for {}: definition {} present={}, level={}, model={}",
                        rodKey, json, mc.getResourceManager().getResource(json).isPresent(),
                        mc.level != null, mc.getModelManager().getItemModel(id));
            }
            return false;
        }
        int joints = JOINTS.getOrDefault(rodKey, NO_JOINTS).length;
        for (int i = 1; i <= joints; i++) {
            piece(stack, RodModelLayers.segmentItemModel(rodKey, i), pose, collector, light, overlay);
        }
        submitReel(stack, rodKey, -1f, pose, collector, light, overlay);   // resting crank
        float[][] lp = guideLinePoints(stack, rodKey);
        if (lp != null) {
            org.joml.Vector3f[] thread = new org.joml.Vector3f[lp.length];
            captureLineStage(thread, lp, pose, NO_JOINTS, 0);   // straight rod: one stage holds all
            submitThread(collector, thread, lineStyle(stack));
        }
        return true;
    }

    private static boolean piece(ItemStack stack, Identifier id,
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
        // A FRESH state per piece, on purpose. Submission is RETAINED: the node keeps a reference to
        // this state and reads it when the frame flushes — a shared reusable state left every
        // segment's node pointing at the LAST-filled one, so all five pieces drew the TIP. Unbent,
        // their poses coincide and the bug hid as "only the tip renders"; a fast swing spread the
        // poses and fanned five tips across the screen.
        ItemStackRenderState state = new ItemStackRenderState();
        // NONE, not the real ctx — canon's contract. A display block on a segment would be applied
        // per-piece INSIDE the chain's own pose and bend the maths; NONE pins the transform to
        // identity whatever the jsons grow later, and sidesteps the left-hand mirror entirely.
        model.update(state, stack, mc.getItemModelResolver(), ItemDisplayContext.NONE, mc.level, mc.player, 0);
        if (state.isEmpty()) return false;
        state.submit(pose, collector, light, overlay, 0);
        return true;
    }

    // ===== §reel-crank: the handle spins while the fight is being reeled =====
    /** Crank speed at zero and full load, degrees per second. */
    private static final float CRANK_MIN_DPS = 140f, CRANK_MAX_DPS = 520f;
    private static float crankDeg;
    private static long crankNanos;

    /**
     * Advances and returns the crank angle. Load drives the speed — a loaded rod is being pumped
     * and wound hard, slack means the handle rests wherever it stopped. Negative reads the resting
     * pose without touching the accumulator (podded reels are not being cranked).
     */
    private static float crankAngle(float load) {
        if (load < 0f) return 0f;
        long now = System.nanoTime();
        float dt = crankNanos == 0 ? 0f : Math.min((now - crankNanos) / 1.0e9f, 0.05f);
        crankNanos = now;
        if (load > 0.01f) {
            crankDeg = (crankDeg + dt * (CRANK_MIN_DPS + (CRANK_MAX_DPS - CRANK_MIN_DPS) * load)) % 360f;
        }
        return crankDeg;
    }

    /**
     * §reel-3d: the fitted reel as a solid model, seated on this rod's own seat. No-op when the rod
     * carries no reel, has no seat, or the size's model is absent — the caller loses nothing but the
     * reel. The handle is its own model, turned about the crank axis by the fight load (§reel-crank),
     * and the knob rides the lever's end but spins free on its own bearing: it ORBITS with the crank
     * yet stays level, so it counter-rotates about its own centre.
     */
    private static void submitReel(ItemStack stack, String rodKey, float load,
                                   PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
        float[] off = REEL_SEAT_DX.get(rodKey);
        if (off == null) return;
        ItemStack reel = RodData.get(stack, ComponentSlot.REEL);
        if (!(reel.getItem() instanceof ReelItem ri)) return;
        pose.pushPose();
        pose.translate(off[0] / 16f, off[1] / 16f, 0);
        if (piece(stack, RodModelLayers.reel3d(ri.size()), pose, collector, light, overlay)) {
            // Pivots from the master, scaled about the foot anchor (19.3, 9.55) with the same cube
            // root the geometry uses — tools/gen_reels.js prints them, tools/check_rod_assets.js
            // verifies them against every size's geometry. A model coord e maps to e/16 - 0.5.
            float s = (float) Math.cbrt(ri.size() / 4000.0);
            float ax = (19.3f + 0.15f * s) / 16f - 0.5f;   // crank axis: the gear boss (19.45, 7.0)
            float ay = (9.55f - 2.55f * s) / 16f - 0.5f;
            float deg = crankAngle(load);
            pose.pushPose();
            pose.translate(ax, ay, 0);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(deg));
            pose.translate(-ax, -ay, 0);
            if (piece(stack, RodModelLayers.reel3dHandle(ri.size()), pose, collector, light, overlay)) {
                float ky = (9.55f - 3.8f * s) / 16f - 0.5f; // knob centre: master (19.45, 5.75)
                pose.translate(ax, ky, 0);
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-deg));
                pose.translate(-ax, -ky, 0);
                piece(stack, RodModelLayers.reel3dKnob(ri.size()), pose, collector, light, overlay);
            }
            pose.popPose();
        }
        pose.popPose();
    }

    // ===== §line-thru-guides: the line runs from the spool through every ring to the tip =====
    /**
     * Per-rod guide ring centres + tip exit, model units, loaded from the generated asset
     * {@code rod_line_paths.json} (tools/gen_line_paths.js) — the renderer never restates model
     * geometry by hand. Null-tolerant: a missing or broken asset just means no on-blank line.
     */
    private static java.util.Map<String, float[][]> linePaths;

    private static float[][] linePath(String rodKey) {
        if (linePaths == null) {
            linePaths = new java.util.HashMap<>();
            try {
                var res = Minecraft.getInstance().getResourceManager()
                        .getResourceOrThrow(com.riverfishing.RiverFishing.id("rod_line_paths.json"));
                try (var reader = new java.io.InputStreamReader(res.open(), java.nio.charset.StandardCharsets.UTF_8)) {
                    var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    for (var e : root.entrySet()) {
                        var o = e.getValue().getAsJsonObject();
                        var gs = o.getAsJsonArray("guides");
                        float[][] pts = new float[gs.size() + 1][];
                        for (int i = 0; i < gs.size(); i++) {
                            var p = gs.get(i).getAsJsonArray();
                            pts[i] = new float[]{p.get(0).getAsFloat(), p.get(1).getAsFloat()};
                        }
                        var t = o.getAsJsonArray("tip");
                        pts[gs.size()] = new float[]{t.get(0).getAsFloat(), t.get(1).getAsFloat()};
                        linePaths.put(e.getKey(), pts);
                    }
                }
            } catch (Exception ignored) {
                // asset absent or malformed: rods simply draw no threaded line
            }
        }
        return linePaths.get(rodKey);
    }

    /**
     * The full thread: spool lip first (scaled with the fitted reel, shifted to this rod's seat),
     * then the guide train, then the tip. Null when the rod has no seat, no reel, or no line — a
     * bare blank carries nothing to thread.
     */
    private static float[][] guideLinePoints(ItemStack stack, String rodKey) {
        float[] off = REEL_SEAT_DX.get(rodKey);
        if (off == null) return null;
        if (!(RodData.get(stack, ComponentSlot.LINE).getItem() instanceof LineItem)) return null;
        if (!(RodData.get(stack, ComponentSlot.REEL).getItem() instanceof ReelItem ri)) return null;
        float[][] path = linePath(rodKey);
        if (path == null) return null;
        float s = (float) Math.cbrt(ri.size() / 4000.0);
        float[][] pts = new float[path.length + 1][];
        pts[0] = new float[]{19.3f - 3.75f * s + off[0], 9.55f - 1.5f * s + off[1]};  // the spool's front lip
        System.arraycopy(path, 0, pts, 1, path.length);
        return pts;
    }

    /**
     * Captures the points belonging to chain stage {@code stage} in the CURRENT pose — a point's
     * stage is how many joints sit butt-ward of it, so feeder rings past a hinge travel with their
     * bent segment and the line follows the blank instead of cutting a chord across the bend.
     */
    private static void captureLineStage(org.joml.Vector3f[] out, float[][] pts, PoseStack pose,
                                         float[] joints, int stage) {
        if (out == null) return;
        for (int i = 0; i < pts.length; i++) {
            int st = 0;
            for (float j : joints) if (pts[i][0] < j) st++;
            if (st != stage) continue;
            org.joml.Vector4f v = new org.joml.Vector4f(
                    pts[i][0] / 16f - 0.5f, pts[i][1] / 16f - 0.5f, AXIS_Z / 16f - 0.5f, 1f);
            v.mul(pose.last().pose());
            out[i] = new org.joml.Vector3f(v.x(), v.y(), v.z());
        }
    }

    /**
     * §line-strand: how the fitted line looks — {r, g, b, alpha, line width px}. Colour and alpha
     * come from the MATERIAL (braid is opaque woven dyneema, fluoro is nearly invisible — the same
     * identity LineType's visibility factors encode); width comes from the item's actual diameter,
     * so 0.8 mm carp mono visibly outweighs 0.1 mm ultralight braid. On 26.x width is a per-vertex
     * attribute ({@code setLineWidth}), so no custom RenderType family is needed.
     */
    static float[] strandStyle(com.riverfishing.component.LineType type, double diameterMm) {
        float w = (float) Math.max(1.0, Math.min(6.0, 1.0 + diameterMm * 5.0));
        return switch (type) {
            case BRAID -> new float[]{58, 82, 52, 255, w};
            case FLUORO -> new float[]{210, 226, 235, 110, w};
            case MONO -> new float[]{232, 228, 208, 255, w};
        };
    }

    static float[] lineStyle(ItemStack stack) {
        if (!(RodData.get(stack, ComponentSlot.LINE).getItem() instanceof LineItem li)) return null;
        return strandStyle(li.lineType(), li.diameterMm());
    }

    /** Submits the captured thread. Points are already in render space, so the pose is identity. */
    private static void submitThread(SubmitNodeCollector collector, org.joml.Vector3f[] pts, float[] style) {
        if (pts == null || style == null) return;
        int r = (int) style[0], g = (int) style[1], b = (int) style[2], a = (int) style[3];
        float w = style[4];
        collector.submitCustomGeometry(new PoseStack(),
                net.minecraft.client.renderer.rendertype.RenderTypes.lines(), (posePose, vc) -> {
            org.joml.Matrix4f id = new org.joml.Matrix4f();
            for (int i = 0; i + 1 < pts.length; i++) {
                var p = pts[i];
                var q = pts[i + 1];
                if (p == null || q == null) continue;
                float dx = q.x - p.x, dy = q.y - p.y, dz = q.z - p.z;
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1.0e-5f) continue;
                dx /= len; dy /= len; dz /= len;
                vc.addVertex(id, p.x, p.y, p.z).setColor(r, g, b, a).setNormal(dx, dy, dz).setLineWidth(w);
                vc.addVertex(id, q.x, q.y, q.z).setColor(r, g, b, a).setNormal(dx, dy, dz).setLineWidth(w);
            }
        });
    }

    // ===== §rod-tip-3d: capture where the tip was drawn =====

    /**
     * First person: projects the blank's tip to NDC with the hand pass's fov so {@link LineRenderer}
     * can start the world-pass line there when the hand line has not drawn.
     */
    private static void captureTipNdc(PoseStack pose, String rodKey, ItemDisplayContext ctx) {
        Float tipX = BLANK_TIP_X.get(rodKey);
        if (tipX == null || !ctx.firstPerson()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        org.joml.Vector4f p = new org.joml.Vector4f(
                tipX / 16f - 0.5f, AXIS_Y / 16f - 0.5f, AXIS_Z / 16f - 0.5f, 1f);
        p.mul(pose.last().pose());
        // §hand-space: projecting divides by -z, which is an EYE-space statement. When the nodes turn
        // out to live in world-relative space, un-rotate first or the anchor is nonsense.
        if (effectiveHandSpace() == 1) {
            org.joml.Vector3f e = new org.joml.Vector3f(p.x(), p.y(), p.z());
            cameraRot(mc).transformInverse(e);
            p.set(e.x(), e.y(), e.z(), 1f);
        }
        if (p.z >= -1.0e-4f) return;                      // behind the camera; nothing to report
        double fovDeg = HAND_FOV > 0f ? HAND_FOV : worldFov(mc);
        float t = (float) Math.tan(Math.toRadians(fovDeg) / 2.0);
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        TIP_NDC[0] = p.x / (-p.z * t * aspect);
        TIP_NDC[1] = p.y / (-p.z * t);
        tipNdcFrame = mc.level.getGameTime();
    }

    private static void captureTipView(PoseStack pose, String rodKey, ItemDisplayContext ctx,
                                       ItemStack stack) {
        Float tipX = BLANK_TIP_X.get(rodKey);
        if (tipX == null) return;
        if (ctx != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                && ctx != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                && !ctx.firstPerson()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // only the LOCAL player's own held rod — the chain also draws for everyone else's
        if (!localHeld(stack)) return;
        org.joml.Vector4f p = new org.joml.Vector4f(
                tipX / 16f - 0.5f, AXIS_Y / 16f - 0.5f, AXIS_Z / 16f - 0.5f, 1f);
        p.mul(pose.last().pose());
        TIP_VIEW[0] = p.x();
        TIP_VIEW[1] = p.y();
        TIP_VIEW[2] = p.z();
        tipViewFrame = mc.level.getGameTime();
    }

    /** The fov the WORLD pass is actually drawn at this frame — the camera knows, ask it. */
    private static double worldFov(Minecraft mc) {
        //? if <26.2 {
        return mc.gameRenderer.getMainCamera().getFov();
        //?} else {
        /*return mc.gameRenderer.mainCamera().getFov();
        *///?}
    }

    // Package-private on purpose: LineRenderer and RodDebugCommand read the camera through these, so
    // the 26.1↔26.2 accessor renames (getMainCamera→mainCamera, getPosition→position) live HERE once.
    static net.minecraft.world.phys.Vec3 cameraPos(Minecraft mc) {
        //? if <26.2 {
        return mc.gameRenderer.getMainCamera().position();
        //?} else {
        /*return mc.gameRenderer.mainCamera().position();
        *///?}
    }

    static org.joml.Quaternionf cameraRot(Minecraft mc) {
        //? if <26.2 {
        return new org.joml.Quaternionf(mc.gameRenderer.getMainCamera().rotation());
        //?} else {
        /*return new org.joml.Quaternionf(mc.gameRenderer.mainCamera().rotation());
        *///?}
    }

    // ===== §hand-line: the first-person water line draws WITH the rod, not a pass later =====
    /**
     * The world (where {@link LineRenderer} lives) renders from state that can lag the hand a frame,
     * and with live springs the tip visibly outruns its own string. The only exact attachment is to
     * submit the first-person line IN the hand pass, from the same matrix that just drew the tip:
     * zero lag by construction, for every rod length, through bend, whip and jerk alike. The world
     * pass then skips the local player's string (it still draws the float — that is a world object).
     *
     * <p>The hand pass may project at its own fov ({@link #HAND_FOV}) while the world projects at
     * the camera's; a world point pushed through the hand projection lands on the wrong pixel, so
     * world-derived points get their view-space x/y scaled by the tangent ratio — and because the
     * two projections genuinely disagree about where the TIP is on screen, that delta is RAMPED
     * along the whole line instead of switched at the last segment (a hard switch put a visible
     * kink right under the tip on 1.21.1).
     */
    private static void drawHandLine(ItemStack stack, ItemDisplayContext ctx, SubmitNodeCollector collector) {
        if (!ctx.firstPerson()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !tipViewFresh()) return;
        if (!localHeld(stack)) return;
        // §hand-space: while the space is still being measured, ANY line drawn here is drawn on a guess,
        // and the frame the verdict lands on is a visible jump across the screen. Draw nothing until it
        // is known: the world pass keeps the line on screen meanwhile, and the switch never shows.
        if (HAND_SPACE < 0 && !spaceDecided) {
            sampleHandSpace(new org.joml.Vector3f(TIP_VIEW[0], TIP_VIEW[1], TIP_VIEW[2]), cameraRot(mc));
            return;
        }
        ClientLineState.Line own = ClientLineState.lines().get(mc.player.getId());
        if (own == null) return;

        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        net.minecraft.world.phys.Vec3 end = LineRenderer.lineEnd(mc, mc.player, own, pt);
        net.minecraft.world.phys.Vec3 cp = cameraPos(mc);
        org.joml.Quaternionf q = cameraRot(mc);

        double worldFov = worldFov(mc);
        float warp = HAND_FOV <= 0f ? 1f
                : (float) (Math.tan(Math.toRadians(HAND_FOV) / 2.0)
                        / Math.tan(Math.toRadians(worldFov) / 2.0));

        float[] style = lineStyle(stack);
        int cr = (own.color >> 16) & 0xFF, cg = (own.color >> 8) & 0xFF, cb = own.color & 0xFF;
        int alpha = style != null ? (int) style[3] : 255;
        float width = style != null ? style[4] : 2.0f;

        org.joml.Vector3f tipV = new org.joml.Vector3f(TIP_VIEW[0], TIP_VIEW[1], TIP_VIEW[2]);
        // §hand-space: TIP_VIEW is by definition already in the node space — whatever that space is.
        // Only the WORLD points need converting into it, so the whole question reduces to this one
        // number, and it is measured rather than believed.
        sampleHandSpace(tipV, q);
        int space = effectiveHandSpace();
        // the sag SHAPE is computed in world space (gravity hangs in world-down, not camera-down),
        // anchored on the tip's world position; the on-screen TIP endpoint stays the captured point
        net.minecraft.world.phys.Vec3 tipW = tipWorld(tipV, cp, q, space);
        double dx = tipW.x - end.x, dy = tipW.y - end.y, dz = tipW.z - end.z;

        org.joml.Vector3f tipWarped = toNode(tipW, cp, q, warp, space);
        float dtx = tipV.x() - tipWarped.x(), dty = tipV.y() - tipWarped.y(), dtz = tipV.z() - tipWarped.z();

        double time = mc.level.getGameTime() + pt;
        // computed eagerly, drawn deferred — the callback runs when the frame flushes
        org.joml.Vector3f[] pts = new org.joml.Vector3f[17];
        pts[0] = toNode(end.add(0, LineRenderer.hangOffset(own, dy, 0.0, time), 0), cp, q, warp, space);
        for (int k = 1; k <= 16; k++) {
            double f = k / 16.0;
            pts[k] = toNode(new net.minecraft.world.phys.Vec3(
                    end.x + dx * f,
                    end.y + LineRenderer.hangOffset(own, dy, f, time),   // §line-taut
                    end.z + dz * f), cp, q, warp, space)
                    .add((float) (dtx * f), (float) (dty * f), (float) (dtz * f));
        }
        collector.submitCustomGeometry(new PoseStack(),
                net.minecraft.client.renderer.rendertype.RenderTypes.lines(), (posePose, vc) -> {
            org.joml.Matrix4f id = new org.joml.Matrix4f();
            for (int k = 0; k + 1 < pts.length; k++) {
                float sx = pts[k + 1].x() - pts[k].x(), sy = pts[k + 1].y() - pts[k].y(),
                        sz = pts[k + 1].z() - pts[k].z();
                float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (len <= 1.0e-5f) continue;
                sx /= len; sy /= len; sz /= len;
                vc.addVertex(id, pts[k].x(), pts[k].y(), pts[k].z())
                        .setColor(cr, cg, cb, alpha).setNormal(sx, sy, sz).setLineWidth(width);
                vc.addVertex(id, pts[k + 1].x(), pts[k + 1].y(), pts[k + 1].z())
                        .setColor(cr, cg, cb, alpha).setNormal(sx, sy, sz).setLineWidth(width);
            }
        });
        handLineFrame = mc.level.getGameTime();
    }

    /**
     * §hand-space: puts a WORLD point into whatever space the hand pass's nodes live in.
     *
     * <p>Both readings start the same way — camera-relative, un-rotated into eye space, then scaled
     * by the fov warp (which is an eye-space scale, so it belongs here either way). Space 1 rotates
     * the result back out to world-relative, because that is what those nodes want.
     */
    private static org.joml.Vector3f toNode(net.minecraft.world.phys.Vec3 w,
                                            net.minecraft.world.phys.Vec3 cp,
                                            org.joml.Quaternionf q, float warp, int space) {
        org.joml.Vector3f v = new org.joml.Vector3f(
                (float) (w.x - cp.x), (float) (w.y - cp.y), (float) (w.z - cp.z));
        q.transformInverse(v);
        v.x *= warp;
        v.y *= warp;
        if (space == 1) q.transform(v);
        return v;
    }

    /** §hand-space: the captured tip as a WORLD point, under the given reading of the node space. */
    private static net.minecraft.world.phys.Vec3 tipWorld(org.joml.Vector3f tipV,
                                                          net.minecraft.world.phys.Vec3 cp,
                                                          org.joml.Quaternionf q, int space) {
        org.joml.Vector3f w = space == 0 ? q.transform(new org.joml.Vector3f(tipV))
                                         : new org.joml.Vector3f(tipV);
        return new net.minecraft.world.phys.Vec3(cp.x + w.x(), cp.y + w.y(), cp.z + w.z());
    }

    /**
     * §hand-space: decides the reading by MEASURING what the camera does to the captured tip — and
     * decides it ONCE. The two spaces differ in exactly one way: turn the head, and a tip held in eye
     * space does not move (the hand is pinned to the screen), while a tip in world-relative space
     * swings with the camera. So sample the tip and the camera, wait for a real turn, and ask which
     * of the two the tip actually did.
     *
     * <p>The first cut of this scored the two readings by how far FORWARD each put the tip and re-ran
     * every second. Forward-ness is weak evidence — facing some directions the two readings agree —
     * so the verdict flipped from second to second and the line jumped between two places. This test
     * cannot tie: either the tip followed the camera or it did not, and once answered it is latched.
     */
    private static void sampleHandSpace(org.joml.Vector3f tipV, org.joml.Quaternionf q) {
        if (spaceDecided || HAND_SPACE >= 0) return;
        if (!spaceHasPrev) {
            spaceTipPrev.set(tipV);
            spaceCamPrev.set(q);
            spaceHasPrev = true;
            return;
        }
        org.joml.Quaternionf turn = new org.joml.Quaternionf(q)
                .mul(new org.joml.Quaternionf(spaceCamPrev).invert());
        float turnDeg = (float) Math.toDegrees(2.0 * Math.acos(Math.min(1f, Math.abs(turn.w()))));
        // Under ~12 degrees the two predictions sit inside the noise the springs alone put on the tip.
        if (turnDeg < 12f) return;
        float dView = spaceTipPrev.distance(tipV);                                   // tip stayed put
        float dWorld = turn.transform(new org.joml.Vector3f(spaceTipPrev)).distance(tipV); // tip turned
        spaceTipPrev.set(tipV);
        spaceCamPrev.set(q);
        // Only a CLEAR answer counts; a close call means the turn was not telling, so wait for a better one.
        if (Math.min(dView, dWorld) * 2f > Math.max(dView, dWorld)) return;
        handSpaceLatch = dWorld < dView ? 1 : 0;
        spaceDecided = true;
        spaceEvidenceView = dView;
        spaceEvidenceWorld = dWorld;
        // Persist it: the answer cannot change for a given version, so measuring it once per launch
        // only buys one avoidable wobble per launch.
        HAND_SPACE = handSpaceLatch;
        RodClientSettings.save();
    }
    /** §hand-space: re-open the question — /rfrod handspace auto starts the measurement over. */
    public static void resetHandSpace() {
        spaceDecided = false;
        spaceHasPrev = false;
    }

    /** §hand-space: the reading in force right now — pinned value, else the latched measurement. */
    public static int effectiveHandSpace() {
        return HAND_SPACE >= 0 ? HAND_SPACE : handSpaceLatch;
    }

    /** §hand-space: what the last measurement saw — /rfrod tipinfo prints it. */
    public static String handSpaceReport() {
        return String.format("space=%s %s  turn moved the tip by: stay %.2f / follow %.2f",
                effectiveHandSpace() == 0 ? "view" : "world",
                HAND_SPACE >= 0 ? "(pinned)" : spaceDecided ? "(measured)" : "(still watching)",
                spaceEvidenceView, spaceEvidenceWorld);
    }
}
