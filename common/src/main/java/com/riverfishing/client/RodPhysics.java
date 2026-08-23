package com.riverfishing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * §rod-physics: the rod has mass, so it lags the hand. Turning the view kicks the blank, a spring
 * pulls it back, and it overshoots once or twice on the way — which is what makes a 3 m carbon pole
 * read as 3 m of carbon rather than a decal stuck to the camera.
 *
 * <p>Two independent damped springs, one per view axis. Each frame the change in view angle is fed
 * in as an IMPULSE (the hand moved, the tip did not yet) and the spring integrates back toward zero:
 *
 * <pre>  v += (-k*x - c*v) * dt  -  dAngle * drive
 *   x += v * dt</pre>
 *
 * <p>The output is an angle, not a position, so it can be handed straight to the pose and to the
 * bone chain. The chain is what makes the whip look right: {@link RodChain} spreads this across the
 * joints with the same tip-heavy weights the bend uses, so the tip travels several times further
 * than the butt without any extra state here.
 *
 * <p>Ported from 1.21.1 unchanged — nothing here touches a renderer API, only angles and time.
 * Client-side and local-player only: this is how the rod feels in YOUR hands, and nothing about it
 * needs to agree with the server or with what other players see.
 */
public final class RodPhysics {
    private RodPhysics() {}

    /** §default-3d: on by default alongside the 3D blanks — the fight-jerk display needs it
     * (§fight-jerk); {@code /rfrod phys off} restores the eased course-lean. */
    public static boolean ENABLED = true;

    // ===== tunables, all live through /rfrod phys =====
    /** Spring constant: how hard the blank is pulled back straight. Higher = stiffer, faster. */
    public static float STIFFNESS = 90f;
    /** Damping: how fast the wobble dies. Below ~2*sqrt(k) it visibly oscillates, above it just eases. */
    public static float DAMPING = 8f;
    /**
     * How much of a view movement becomes lag. 0 = rod welded to the camera. At 0.9 a 90° flick peaks
     * the rigid lag near 5° and a 180° spin near 10°, which the whip then roughly doubles at the tip —
     * measured, not guessed, and identical at 60 and 144 fps.
     */
    public static float DRIVE = 1.0f;
    /** Hard cap on the lag angle, degrees — a fast mouse flick must not fold the rod in half. */
    public static float MAX_DEG = 200f;

    // ===== §fight-jerk: the fish drives the same springs the camera does =====
    /**
     * Impulse per unit of load jump, degrees/second of spring velocity. A run or head-shake arrives
     * as a STEP in the synced rod load; multiplying the step by this and slamming it into the spring
     * is what makes the rod visibly YANK the moment the fish hits — and the spring's own overshoot
     * supplies the recovery wobble for free.
     */
    public static float JERK_GAIN = 220f;
    /** Sustained drag while the fish runs, degrees/s² at full load — holds the rod pulled over. */
    public static float PULL_GAIN = 160f;
    private static float lastTension;

    private static float swingYaw, swingPitch;      // current lag, degrees
    private static float velYaw, velPitch;
    private static float lastYaw, lastPitch;
    private static long lastNanos;
    private static boolean primed;

    // ===== §rod-physics-per-rod: every blank has its own spring =====
    /**
     * Per-rod {stiffness, damping, whip}, loaded from {@code assets/riverfishing/rod_physics.json} —
     * a 60 cm winter stub and a 3 m pole are not the same spring, and the data file is where that
     * difference lives. A rod absent from the file falls back to the global tunables above.
     */
    private static java.util.Map<String, float[]> profiles;

    public static float[] profileFor(String rodKey) {
        if (profiles == null) {
            profiles = new java.util.HashMap<>();
            try {
                var res = Minecraft.getInstance().getResourceManager()
                        .getResourceOrThrow(com.riverfishing.RiverFishing.id("rod_physics.json"));
                try (var reader = new java.io.InputStreamReader(res.open(), java.nio.charset.StandardCharsets.UTF_8)) {
                    var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    for (var e : root.entrySet()) {
                        if (!e.getValue().isJsonObject()) continue;   // skips the __comment string
                        var o = e.getValue().getAsJsonObject();
                        profiles.put(e.getKey(), new float[]{
                                o.get("stiffness").getAsFloat(), o.get("damping").getAsFloat(),
                                o.get("whip").getAsFloat()});
                    }
                }
            } catch (Exception ignored) {
                // asset absent or malformed: every rod runs on the global spring
            }
        }
        return profiles.computeIfAbsent(rodKey,
                k -> new float[]{STIFFNESS, DAMPING, RodChain.WHIP_GAIN});
    }

    /** The rod key currently in the player's hands, or null — the spring the fight is played on. */
    public static String heldRodKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        var main = mc.player.getMainHandItem();
        var off = mc.player.getOffhandItem();
        if (main.getItem() instanceof com.riverfishing.item.RodItem r) return r.rodType().jsonKey();
        if (off.getItem() instanceof com.riverfishing.item.RodItem r) return r.rodType().jsonKey();
        return null;
    }

    /**
     * Advances the springs. Safe to call more than once a frame: the second call sees dt≈0 and no
     * angle change, so it neither integrates nor kicks.
     */
    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { reset(); return; }

        long now = System.nanoTime();
        // Real time, not ticks: the view moves with the mouse, which is a per-frame thing. Clamped so
        // a stall (chunk load, alt-tab, pause) cannot integrate one enormous step and launch the rod.
        float dt = primed ? Math.min((now - lastNanos) / 1.0e9f, 0.05f) : 0f;
        lastNanos = now;

        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        if (!primed) { lastYaw = yaw; lastPitch = pitch; primed = true; return; }

        float dYaw = Mth.degreesDifference(lastYaw, yaw);
        float dPitch = pitch - lastPitch;
        lastYaw = yaw; lastPitch = pitch;
        if (dt <= 0f) return;

        if (!ENABLED) { swingYaw = swingPitch = velYaw = velPitch = 0f; lastTension = 0f; return; }

        // §rod-physics-per-rod: the spring constants are the HELD rod's own — resolved here rather
        // than passed in, because update() is reached from every render context (hotbar icons
        // included) and only the rod in the hands is the one being waved around.
        String held = heldRodKey();
        float[] prof = held != null ? profileFor(held) : new float[]{STIFFNESS, DAMPING, 0f};
        velYaw += (-prof[0] * swingYaw - prof[1] * velYaw) * dt - dYaw * DRIVE;
        velPitch += (-prof[0] * swingPitch - prof[1] * velPitch) * dt - dPitch * DRIVE;

        // §fight-jerk: the fish is the second hand on the rod. Course picks the direction the fish
        // is going (the rod is DRAGGED that way — same sign convention the old lean display used);
        // a step up in synced load is a jerk and kicks the spring, a running fish keeps a steady
        // drag on it. No course (a plain bite-shake) reads as straight down-and-away.
        ClientLineState.Line own = ClientLineState.lines().get(mc.player.getId());
        if (own != null && own.fighting) {
            float dirYaw = own.course == 1 ? 1f : own.course == 2 ? -1f : 0f;
            float dirPitch = own.course == 3 ? 1f : own.course == 4 ? -1f : dirYaw == 0f ? 1f : 0f;
            // §rod-load: the springs work off the BLANK's load, not the line's break-risk — over-gunned
            // gear keeps break-risk near zero all fight (§tackle-margin), which left the trolling rod
            // limp over a hooked bass. The fight syncs every FIVE ticks, so the load arrives in small
            // steps — any real step kicks; the gain scales it, so small steps give small knocks and a
            // slam still slams.
            float dT = own.rodLoad - lastTension;
            if (dT > 0.005f) {
                velYaw += dirYaw * dT * JERK_GAIN;
                velPitch += dirPitch * dT * JERK_GAIN;
            }
            // The fish never stops pulling: a steady down-and-away drag all fight long, doubled and
            // aimed down the course while it RUNS. This is the baseline that makes a hooked rod feel
            // loaded even between runs.
            velPitch += own.rodLoad * PULL_GAIN * 0.5f * dt;
            if (own.running) {
                velYaw += dirYaw * own.rodLoad * PULL_GAIN * dt;
                velPitch += dirPitch * own.rodLoad * PULL_GAIN * dt;
            }
            lastTension = own.rodLoad;
        } else {
            lastTension = 0f;
        }
        swingYaw = Mth.clamp(swingYaw + velYaw * dt, -MAX_DEG, MAX_DEG);
        swingPitch = Mth.clamp(swingPitch + velPitch * dt, -MAX_DEG, MAX_DEG);
        // clamping the angle without clamping the velocity would let it keep charging into the wall
        if (Math.abs(swingYaw) >= MAX_DEG) velYaw = 0f;
        if (Math.abs(swingPitch) >= MAX_DEG) velPitch = 0f;
    }

    /** Sideways lag in degrees (view yaw), 0 when disabled. */
    public static float yaw() { return ENABLED ? swingYaw : 0f; }

    /** Up/down lag in degrees (view pitch), 0 when disabled. */
    public static float pitch() { return ENABLED ? swingPitch : 0f; }

    /** True while the blank is still visibly moving — used by the debug readout, not by rendering. */
    public static boolean settling() {
        return Math.abs(velYaw) > 0.5f || Math.abs(velPitch) > 0.5f;
    }

    public static void reset() {
        swingYaw = swingPitch = velYaw = velPitch = 0f;
        primed = false;
    }

    /** Sets one tunable by name. Returns the new value, or NaN if the name is unknown. */
    public static float edit(String field, float value) {
        switch (field.toLowerCase()) {
            case "stiffness": return STIFFNESS = value;
            case "damping": return DAMPING = value;
            case "drive": return DRIVE = value;
            case "max": return MAX_DEG = value;
            case "jerk": return JERK_GAIN = value;
            case "pull": return PULL_GAIN = value;
            default: return Float.NaN;
        }
    }

    public static String describe() {
        return String.format("§ephys %s §fstiffness=%.1f damping=%.1f drive=%.2f max=%.1f jerk=%.0f pull=%.0f §7| lag %.1f/%.1f deg%s",
                ENABLED ? "§aON" : "§cOFF", STIFFNESS, DAMPING, DRIVE, MAX_DEG, JERK_GAIN, PULL_GAIN,
                swingYaw, swingPitch, settling() ? " §7(settling)" : "");
    }
}
