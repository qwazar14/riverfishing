package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;

/**
 * The rod's in-hand display transform (§rod-debug), owned in code so it can be tuned LIVE with the
 * {@code /rfrod} command. The rod model's hand display is identity, so these values ARE the whole
 * transform — what you see in game maps 1:1 to what you'd bake into a model's display block.
 *
 * <p>Fields are {@code {tx, ty, tz, rx, ry, rz, scale}}: translation in 1/16-block units (like a model
 * JSON), rotation in degrees (XYZ order), uniform scale. Every hand has its OWN explicit values —
 * left is NOT auto-mirrored (the arm's own mirror makes negation misbehave), so tune each directly.
 *
 * <p>To bake a tuned pose: run {@code /rfrod show}, then paste the printed numbers over the DEFAULT
 * arrays below and rebuild the jar.
 */
public final class RodHandTransform {
    private RodHandTransform() {}

    public static final String[] FIELDS = {"tx", "ty", "tz", "rx", "ry", "rz", "s"};

    // ===== TUNED HAND TRANSFORM — paste /rfrod show values here, then rebuild =====
    public static final float[] TP  = {0f, 3f,   3.5f, 0f,  -90f, -48f, 0.85f}; // third person, right
    public static final float[] TPL = {0f, 3f,   3.5f, 90f, -90f,  40f, 0.85f}; // third person, left
    public static final float[] FP  = {0f, 3.1f, 2.8f, 0f,  -90f,  10f, 0.68f}; // first person, right
    public static final float[] FPL = {0f, 2.3f, 2.8f, 0f,  -90f,   0f, 0.68f}; // first person, left
    // ==============================================================================

    private static final float[] TP_DEFAULT = TP.clone();
    private static final float[] TPL_DEFAULT = TPL.clone();
    private static final float[] FP_DEFAULT = FP.clone();
    private static final float[] FPL_DEFAULT = FPL.clone();

    // ===== CAST ANIMATION (§cast-anim) — tunable live with /rfrod cast load|whip <deg> =====
    /** Degrees the rod loads BACK as the cast charges (wind-up); tracks the power bar. */
    public static float CAST_LOAD = 22f;
    /** Peak degrees of the forward WHIP on release, driven by the swing. Flip the sign to flip direction. */
    public static float CAST_WHIP = -55f;
    private static final float CAST_LOAD_DEFAULT = CAST_LOAD;
    private static final float CAST_WHIP_DEFAULT = CAST_WHIP;
    // =======================================================================================

    /**
     * Extra pitch (degrees) added to the FIRST-PERSON rod pose for the casting motion (§cast-anim):
     * a wind-up that loads the rod back while charging (grows with {@code chargePower} 0..1), plus a
     * quick forward whip as the swing plays out ({@code swingProgress} 0..1). 0 when nothing animates.
     */
    public static float castPitch(float chargePower, float swingProgress) {
        float c = Math.max(0f, Math.min(1f, chargePower));
        float s = Math.max(0f, Math.min(1f, swingProgress));
        return CAST_LOAD * c + CAST_WHIP * (float) Math.sin(s * Math.PI);
    }

    public static boolean isHand(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }

    /** Applies the hand transform for a hand context (no-op otherwise). Each hand uses its own array. */
    public static void apply(PoseStack pose, ItemDisplayContext ctx) {
        float[] a = switch (ctx) {
            case THIRD_PERSON_RIGHT_HAND -> TP;
            case THIRD_PERSON_LEFT_HAND -> TPL;
            case FIRST_PERSON_RIGHT_HAND -> FP;
            case FIRST_PERSON_LEFT_HAND -> FPL;
            default -> null;
        };
        if (a == null) return;
        pose.translate(a[0] / 16f, a[1] / 16f, a[2] / 16f);
        pose.mulPose(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(a[3]), (float) Math.toRadians(a[4]), (float) Math.toRadians(a[5])));
        pose.scale(a[6], a[6], a[6]);
    }

    // ---- edit API for the /rfrod command ----

    private static float[] array(String ctx) {
        return switch (ctx.toLowerCase()) {
            case "tp" -> TP;
            case "tpl" -> TPL;
            case "fp" -> FP;
            case "fpl" -> FPL;
            default -> null;
        };
    }

    private static int index(String field) {
        for (int i = 0; i < FIELDS.length; i++) {
            if (FIELDS[i].equalsIgnoreCase(field)) return i;
        }
        return -1;
    }

    /** Sets or adds a single field. Returns the new value, or NaN if ctx/field is unknown. */
    public static float edit(String ctx, String field, float value, boolean add) {
        float[] a = array(ctx);
        int i = index(field);
        if (a == null || i < 0) return Float.NaN;
        a[i] = add ? a[i] + value : value;
        return a[i];
    }

    /** Sets or adds a cast-animation param ({@code load} or {@code whip}). NaN if the field is unknown. */
    public static float castEdit(String field, float value, boolean add) {
        switch (field.toLowerCase()) {
            case "load": return CAST_LOAD = add ? CAST_LOAD + value : value;
            case "whip": return CAST_WHIP = add ? CAST_WHIP + value : value;
            default: return Float.NaN;
        }
    }

    public static void reset() {
        System.arraycopy(TP_DEFAULT, 0, TP, 0, TP.length);
        System.arraycopy(TPL_DEFAULT, 0, TPL, 0, TPL.length);
        System.arraycopy(FP_DEFAULT, 0, FP, 0, FP.length);
        System.arraycopy(FPL_DEFAULT, 0, FPL, 0, FPL.length);
        CAST_LOAD = CAST_LOAD_DEFAULT;
        CAST_WHIP = CAST_WHIP_DEFAULT;
    }

    /** Human-readable current values, ready to paste back into the DEFAULT arrays above. */
    public static java.util.List<String> showLines() {
        return java.util.List.of(
                "§e/rfrod §7— rod hand transform (1/16 units, degrees; tx ty tz rx ry rz s):",
                fmt("TP ", TP),
                fmt("TPL", TPL),
                fmt("FP ", FP),
                fmt("FPL", FPL),
                String.format("§bCAST §fload=%s whip=%s §7(/rfrod cast load|whip <deg>)", n(CAST_LOAD), n(CAST_WHIP)),
                "§8paste over TP/TPL/FP/FPL (and CAST_LOAD/CAST_WHIP) in RodHandTransform.java, then rebuild");
    }

    private static String fmt(String name, float[] a) {
        return String.format("§b%s §f{%s, %s, %s, %s, %s, %s, %s} §7(tx ty tz rx ry rz s)",
                name, n(a[0]), n(a[1]), n(a[2]), n(a[3]), n(a[4]), n(a[5]), n(a[6]));
    }

    private static String n(float v) {
        return (v == Math.rint(v)) ? String.valueOf((int) v) + "f" : String.valueOf(v) + "f";
    }
}
