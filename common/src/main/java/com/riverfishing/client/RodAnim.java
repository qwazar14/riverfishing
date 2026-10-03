package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * §rod-anim (1.1.0): the rod in your hands ACTS. Every action is drawn the way Actions &amp; Stuff draws a
 * sword — a little anticipation, a snap, and a follow-through that overshoots and settles — instead of the
 * one flat vanilla bob the rod used to dip through on every click.
 *
 * <p>What it owns, all local-player and client-only:
 * <ul>
 *   <li><b>equip</b>: the rod swings up into the hands and its tip whips once; taken off a pod mid-bite it
 *       comes up already fighting (a yank);</li>
 *   <li><b>the reel</b>: the handle turns when YOU wind — each click a turn — and on a run it ticks backward
 *       and chatters while the drag gives line;</li>
 *   <li><b>the retrieve</b>: the tip goes down to the water and sways with the handle, every click flicks
 *       it (and darts the lure), a pause lets the line belly;</li>
 *   <li><b>strikes and bites</b>: a hookset sweeps the rod up; a take on a lure or a bottom rig slams the tip
 *       down and kicks the line aside;</li>
 *   <li><b>the fight</b>: the tip beats with the fish's tail, head-shakes knock it, a jump slackens the line
 *       and the landing yanks it, the arrow keys hold the rod over, and near the bank it rises high;</li>
 *   <li><b>the end</b>: a snapped line or a thrown hook throws the tip up and the rod shakes out;</li>
 *   <li><b>walking</b>: the tip bounces with your steps, a landing whips it, sprinting carries it high.</li>
 * </ul>
 *
 * <p>Hand motions come out as {@link #pitch()}/{@link #yaw()}/{@link #roll()} in {@link RodPhysics}'s own
 * convention (pitch + = tip DOWN, yaw + = tip LEFT) and are applied at the grip beside the springs; knocks and
 * whips are fed INTO the springs ({@link RodPhysics#kick}), so the blank flexes through them for free.
 * {@code /rfrod anim off} switches all of it off; {@code /rfrod anim flip} turns the hand pitches over in case a
 * frame disagrees with the one this was written against.
 */
public final class RodAnim {
    private RodAnim() {}

    public static boolean ENABLED = true;
    /**
     * The hand pitches are written in the springs' convention (+ = tip down), but they are applied as a rigid
     * turn in the arm frame, where + about X lifts the tip (the cast's wind-up is +22 for that reason; the
     * springs read as + = down only through the blank's flex). So they go over negated; {@code /rfrod anim
     * flip} turns this over should a frame ever disagree.
     */
    public static float POSE_SIGN = 1f;

    // ---- clock ----
    private static long lastNanos;
    private static double clock;   // seconds; wrapped, so hours of play keep the sines smooth

    // ---- equip ----
    private static net.minecraft.world.item.Item heldItem;
    private static int heldSlot = -2;
    private static float equipT = 1f;
    private static boolean yank;

    // ---- the reel ----
    private static float crankDeg, crankVel, crankJitter;
    private static long lastClickNanos;
    private static long lastRetrieveClickNanos;

    // ---- one-shot timelines, 0..1, >= 1 when idle ----
    private static float strikeT = 1f, twitchT = 1f, pumpT = 1f, nodT = 1f, recoilT = 1f;
    private static float twitchYaw, nodSide, recoilGain;

    // ---- eased blends ----
    private static float retrieve, sprint, lift;
    private static float holdYaw, holdYawV, holdPitch, holdPitchV;

    // ---- edges ----
    private static boolean wasBiting, wasInAir, wasGround = true, wasSwing, wasShaking;
    private static float shakeClock, stepPhase;
    private static double lastVy;

    // ---- this frame's output ----
    private static float outPitch, outYaw, outRoll, outDrop, outBack;

    // =====================================================================================
    // events
    // =====================================================================================

    /**
     * A click with the line out — the client half of RodItem.use. What it means is what the server makes of
     * it (a crank, a twitch, a hookset); what it LOOKS like is decided here from what the client knows.
     */
    public static void click() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ENABLED) return;
        ClientLineState.Line own = own();
        long now = System.nanoTime();
        lastClickNanos = now;
        boolean active = heldActive();
        if (own != null && own.fighting) {
            crankVel = Math.min(1500f, crankVel + 820f);   // a turn of the handle
            pumpT = 0f;                                     // and the rod lifts a little with it
            RodPhysics.kick(0f, -70f);
        } else if (active && own != null && !own.biting) {
            crankVel = Math.min(1500f, crankVel + 700f);
            lastRetrieveClickNanos = now;
            twitchT = 0f;                                   // the tip flicks, the lure darts
            twitchYaw = (mc.player.getRandom().nextFloat() - 0.5f) * 5f;
            RodPhysics.kick(twitchYaw * 12f, -210f);
        } else {
            strikeT = 0f;                                   // a hookset: the rod sweeps up
        }
    }

    /** The fish on the local line is gone: landed, thrown the hook, or snapped the line. */
    public static void gone(boolean landed, boolean broke) {
        if (!ENABLED) return;
        if (landed) {
            RodPhysics.kick(0f, -220f);
            return;
        }
        recoilT = 0f;
        recoilGain = broke ? 1f : 0.65f;
        RodPhysics.kick(0f, -(broke ? 760f : 460f));   // the load is gone at once: the tip flies up
    }

    // =====================================================================================
    // the frame
    // =====================================================================================

    /** Advances everything by one frame. Driven from RodPhysics.update, so both share one clock. */
    static void update(float dt) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || dt <= 0f) return;
        if (!ENABLED) {
            outPitch = outYaw = outRoll = outDrop = outBack = 0f;
            return;
        }
        long now = System.nanoTime();
        boolean stale = lastNanos == 0L || now - lastNanos > 200_000_000L;   // not drawn a moment ago: picked up afresh
        lastNanos = now;
        clock = (clock + dt) % 3600.0;
        ClientLineState.Line own = own();

        // ---- equip: a rod came into the hands — another slot, another item, or not drawn a moment ago ----
        ItemStack held = heldRod(p);
        int slot = held == p.getMainHandItem() ? p.getInventory().getSelectedSlot() : -1;
        if (!held.isEmpty() && (stale || held.getItem() != heldItem || slot != heldSlot)) {
            equipT = 0f;
            yank = false;
        }
        heldItem = held.isEmpty() ? null : held.getItem();
        heldSlot = slot;
        if (equipT < 1f) {
            // lifted off a pod on a bite: the fight is on before the rod is even up
            if (equipT < 0.3f && own != null && own.fighting) yank = true;
            float was = equipT;
            equipT = Math.min(1f, equipT + dt / (yank ? 0.38f : 0.55f));
            if (was < 0.5f && equipT >= 0.5f) {
                // it arrives — and the tip keeps going: the follow-through
                RodPhysics.kick(0f, (yank ? 320f : -260f));
            }
        }

        // ---- the reel ----
        crankVel *= (float) Math.exp(-dt * 5.0);
        boolean clicking = now - lastClickNanos < 260_000_000L;
        boolean dragging = own != null && own.fighting && own.running && own.jumpT < 0f && !clicking;
        if (dragging) {
            // the drag is giving line: the handle ticks BACKWARD and chatters — the reel is losing, and it shows
            crankDeg -= dt * 150f * (0.5f + own.smoothRodLoad);
            crankJitter = (float) Math.sin(clock * 95.0) * 6f;
        } else {
            crankJitter *= (float) Math.exp(-dt * 20.0);
        }
        crankDeg = (crankDeg + crankVel * dt) % 360f;

        // ---- timelines ----
        float was;
        was = strikeT;
        strikeT = Math.min(1f, strikeT + dt / 0.45f);
        if (was < 0.3f && strikeT >= 0.3f) RodPhysics.kick(0f, -340f);
        twitchT = Math.min(1f, twitchT + dt / 0.24f);
        pumpT = Math.min(1f, pumpT + dt / 0.28f);
        nodT = Math.min(1f, nodT + dt / 0.55f);
        recoilT = Math.min(1f, recoilT + dt / 0.9f);

        // ---- a take: on a lure or a bottom rig the TIP tells you (a float tells you itself) ----
        boolean biting = own != null && own.biting && !own.fighting;
        if (biting && !wasBiting && own.floatKind == 0) {
            nodT = 0f;
            nodSide = p.getRandom().nextBoolean() ? 1f : -1f;
            RodPhysics.kick(nodSide * 90f, 330f);
        }
        wasBiting = biting;

        // ---- the fight ----
        if (own != null && own.fighting) {
            // head-shakes knock the tip, one knock a beat
            if (own.shaking) {
                shakeClock += dt * 9f;
                if (shakeClock >= 1f) {
                    shakeClock -= 1f;
                    RodPhysics.kick((p.getRandom().nextFloat() - 0.5f) * 110f, 95f);
                }
            } else {
                shakeClock = 0.7f;   // the first knock of the next shake comes at once
            }
            if (own.shaking && !wasShaking) RodPhysics.kick(0f, 60f);
            wasShaking = own.shaking;
            // a breach: the line slackens while it flies (ClientLineState) and the landing yanks the rod
            boolean inAir = own.jumpT >= 0f;
            if (wasInAir && !inAir) {
                RodPhysics.kick(0f, 300f);
                LineFx.landingSplash(mc, own);
            }
            wasInAir = inAir;
        } else {
            wasInAir = wasShaking = false;
        }

        // ---- walking: the tip bounces with the steps; a landing whips it ----
        var v = p.getDeltaMovement();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z) * 20.0;
        boolean ground = p.onGround();
        if (ground && speed > 0.6) {
            float before = stepPhase;
            stepPhase = (stepPhase + (float) (dt * speed * 1.7)) % 1000f;
            if ((int) (before * 2f) != (int) (stepPhase * 2f)) {
                RodPhysics.kick(0f, (float) (14.0 * Math.min(1.6, speed / 4.3)));
            }
        }
        if (ground && !wasGround && lastVy < -0.25) {
            RodPhysics.kick(0f, (float) Math.min(520.0, -lastVy * 20.0 * 26.0));
        }
        wasGround = ground;
        lastVy = v.y;

        // ---- the cast going out: the whip's follow-through ----
        float sw = com.riverfishing.compat.Mc.attackAnim(p, 1f);
        boolean swinging = sw > 0f;
        if (swinging && !wasSwing && own == null && !held.isEmpty()) RodPhysics.kick(0f, 340f);
        wasSwing = swinging;

        // ---- blends ----
        boolean retrieving = own != null && !own.fighting && heldActive() && now - lastRetrieveClickNanos < 750_000_000L;
        retrieve = ease(retrieve, retrieving ? 1f : 0f, dt, retrieving ? 8f : 3f);
        sprint = ease(sprint, p.isSprinting() && own == null ? 1f : 0f, dt, 6f);
        float liftTo = own != null && own.fighting ? Mth.clamp((own.smoothProgress - 0.72f) / 0.2f, 0f, 1f) : 0f;
        lift = ease(lift, liftTo, dt, 4f);
        // the arrow keys hold the rod over — on a spring, so it arrives with a little overshoot
        float hy = 0f, hp = 0f;
        if (own != null && own.fighting && noScreen(mc)) {
            if (FightKeys.PULL_LEFT.isDown()) hy = 22f;
            else if (FightKeys.PULL_RIGHT.isDown()) hy = -22f;
            if (FightKeys.LIFT.isDown()) hp = -20f;
            else if (FightKeys.PUSH.isDown()) hp = 18f;
        }
        holdYawV += ((hy - holdYaw) * 140f - holdYawV * 13f) * dt;
        holdYaw += holdYawV * dt;
        holdPitchV += ((hp - holdPitch) * 140f - holdPitchV * 13f) * dt;
        holdPitch += holdPitchV * dt;

        compose(own);
    }

    private static void compose(ClientLineState.Line own) {
        float pitch = 0f, yaw = 0f, roll = 0f, drop = 0f, back = 0f;
        // breathing: the hands are never quite still
        pitch += (float) Math.sin(clock * 1.35) * 0.8f;
        yaw += (float) Math.sin(clock * 0.77 + 1.3) * 0.55f;
        // equip: up from low and rolled, overshooting into place
        if (equipT < 1f) {
            float k = easeOutBack(equipT, yank ? 2.4f : 1.8f);
            float inv = 1f - k;
            pitch += (yank ? -38f : 62f) * inv;
            roll += (yank ? 12f : -38f) * inv;
            yaw += (yank ? 0f : 18f) * inv;
            drop += (yank ? 0.12f : 0.55f) * inv;
            back += (yank ? -0.1f : 0.12f) * inv;
        }
        // the retrieve: tip down to the water, swaying with the handle
        float cr = (float) Math.toRadians(crankDeg);
        pitch += retrieve * (11f + (float) Math.sin(cr) * 1.8f);
        yaw += retrieve * (float) Math.cos(cr) * 1.3f;
        // sprinting: the rod rides high and back
        pitch -= sprint * 13f;
        roll += sprint * 7f;
        // the bank: rod held high to bring it to the net
        pitch -= lift * 30f;
        // the arrow keys
        pitch += holdPitch;
        yaw += holdYaw;
        // strike: a small dip, then the sweep up, then easing back
        if (strikeT < 1f) {
            float s = strikeT;
            pitch += s < 0.1f ? 7f * (s / 0.1f)
                    : s < 0.3f ? Mth.lerp((s - 0.1f) / 0.2f, 7f, -34f)
                    : Mth.lerp(smooth((s - 0.3f) / 0.7f), -34f, 0f);
        }
        // twitch: the flick
        if (twitchT < 1f) {
            float s = twitchT;
            float c = s < 0.25f ? -9f * (s / 0.25f) : Mth.lerp(easeOut((s - 0.25f) / 0.75f), -9f, 0f);
            pitch += c;
            yaw += twitchYaw * (1f - s);
        }
        // pump: the rod lifts with each turn of the handle
        if (pumpT < 1f) pitch -= (float) Math.sin(pumpT * Math.PI) * 5f;
        // the take: slammed down, shivering out
        if (nodT < 1f) {
            float s = nodT;
            float c = (float) (Math.sin(s * Math.PI * 3.0) * Math.exp(-s * 3.5));
            pitch += 13f * c;
            yaw += nodSide * 4f * c;
        }
        // the end of a fish: thrown up, shaking out
        if (recoilT < 1f) {
            float s = recoilT;
            float c = (float) (Math.sin(s * Math.PI * 4.0) * Math.exp(-s * 4.0));
            pitch -= 10f * recoilGain * c;
            roll += 6f * recoilGain * c;
        }
        outPitch = -pitch * POSE_SIGN;   // springs convention in, arm-frame rigid turn out (see POSE_SIGN)
        outYaw = yaw;
        outRoll = roll;
        outDrop = drop;
        outBack = back;
    }

    // =====================================================================================
    // what the renderers read
    // =====================================================================================

    /**
     * First person only: the hand motion, applied at the grip before the hand pose — the same place and the
     * same order RodHandTransform puts the springs, so the two add like one motion.
     */
    public static void applyFirstPerson(PoseStack pose) {
        if (!ENABLED) return;
        if (outDrop != 0f || outBack != 0f) pose.translate(0f, -outDrop, outBack);
        if (outPitch == 0f && outYaw == 0f && outRoll == 0f) return;
        float[] pv = RodHandTransform.PIVOT;
        pose.translate(pv[0] / 16f, pv[1] / 16f, pv[2] / 16f);
        com.riverfishing.compat.Mc.rotate(pose, new org.joml.Quaternionf().rotationXYZ(
                (float) Math.toRadians(outPitch), (float) Math.toRadians(outYaw), (float) Math.toRadians(outRoll)));
        pose.translate(-pv[0] / 16f, -pv[1] / 16f, -pv[2] / 16f);
    }

    /**
     * The blank's load as it should be DRAWN for the local rod: the synced load, beating with the fish's tail
     * through a fight, and bowed by a take on the tip.
     */
    public static float displayLoad(float load) {
        if (!ENABLED) return load;
        ClientLineState.Line own = own();
        float out = load;
        if (own != null && own.fighting && load > 0.01f) {
            float beat = (float) Math.sin(own.tail) * (0.035f + 0.04f * (1f - own.fatigue));
            out += beat * Math.min(1f, load * 3f);
        }
        if (nodT < 1f) out += 0.32f * (float) (Math.max(0.0, Math.sin(nodT * Math.PI * 3.0)) * Math.exp(-nodT * 3.0));
        if (twitchT < 1f) out += 0.06f * (float) Math.sin(twitchT * Math.PI);
        return Mth.clamp(out, 0f, 1f);
    }

    /** The local reel's handle angle: turned by the winding, backed off by the drag. */
    public static float crankDeg() {
        return crankDeg + crankJitter;
    }

    /** The lure's dart toward the angler on a retrieve click, blocks. */
    public static float dart() {
        if (!ENABLED || twitchT >= 1f) return 0f;
        return 0.32f * (float) Math.sin(Math.min(1f, twitchT * 1.6f) * Math.PI) * (1f - twitchT);
    }

    /** The line's end kicked aside by a take, blocks along the angler's left. */
    public static float lineKick() {
        if (!ENABLED || nodT >= 1f) return 0f;
        return nodSide * 0.22f * (float) (Math.sin(nodT * Math.PI * 2.5) * Math.exp(-nodT * 3.0));
    }

    /**
     * §line-retrieve: the local line on a retrieve — 1 while winding (drawn straight toward the lure), 2 on a
     * pause (it bellies: the classic "on the drop" moment), 0 when it is not a retrieve at all.
     */
    public static int retrieveLine() {
        if (!ENABLED) return 0;
        ClientLineState.Line own = own();
        if (own == null || own.fighting || own.biting || !heldActive()) return 0;
        long since = System.nanoTime() - lastRetrieveClickNanos;
        if (lastRetrieveClickNanos == 0L || since > 8_000_000_000L) return 0;
        return since < 320_000_000L ? 1 : since > 650_000_000L ? 2 : 0;
    }

    public static String describe() {
        return String.format("§eanim %s §fpitch sign %s §7| equip %.2f retrieve %.2f lift %.2f crank %.0f",
                ENABLED ? "§aON" : "§cOFF", POSE_SIGN > 0 ? "+" : "-", equipT, retrieve, lift, crankDeg);
    }

    // =====================================================================================

    private static ClientLineState.Line own() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : ClientLineState.lines().get(mc.player.getId());
    }

    private static ItemStack heldRod(net.minecraft.world.entity.player.Player p) {
        if (p.getMainHandItem().getItem() instanceof com.riverfishing.item.RodItem) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof com.riverfishing.item.RodItem) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** A spinning rod — the retrieve is the game on it. */
    private static boolean heldActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack rod = heldRod(mc.player);
        return rod.getItem() instanceof com.riverfishing.item.RodItem r && r.rodType().activeRetrieve();
    }

    private static boolean noScreen(Minecraft mc) {
        //? if <26.2 {
        /*return mc.screen == null;
        *///?} else {
        return mc.gui.screen() == null;
        //?}
    }

    private static float ease(float from, float to, float dt, float rate) {
        return from + (to - from) * (1f - (float) Math.exp(-dt * rate));
    }

    private static float easeOutBack(float t, float s) {
        float u = t - 1f;
        return 1f + (s + 1f) * u * u * u + s * u * u;
    }

    private static float easeOut(float t) {
        float u = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - u * u * u;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
