package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.riverfishing.network.FishGonePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * §fight-moves (1.1.0): the last second of a fight, seen. A landed fish is lifted out of the water to the angler's
 * hands, dripping and kicking; one that got away turns from the rod and goes down. The server has already decided
 * (and filled the inventory, or not) — this only draws it, from where the body was last drawn on the line.
 */
public final class FishExitRenderer {
    private FishExitRenderer() {}

    private static final float LIFT_TICKS = 14f, AWAY_TICKS = 22f;

    private record Exit(int playerId, boolean landed, ItemStack stack, int lengthCm, Vec3 from, Vec3 away, long start) {}

    private static final List<Exit> EXITS = new ArrayList<>();

    public static void accept(FishGonePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ItemStack stack = HookedFishRenderer.stackOf(p.species, 0, p.lengthCm);
        if (stack == null) return;
        ClientLineState.Line line = ClientLineState.lines().get(p.playerId);
        LineFx.gone(line, p.landed, p.broke);   // §rod-anim: a snapped piece, or the line whipping back
        if (mc.player != null && p.playerId == mc.player.getId()) RodAnim.gone(p.landed, p.broke);
        Vec3 from = line != null && line.lastFishAt != null ? line.lastFishAt : new Vec3(p.x, p.y, p.z);
        // away: straight out from the angler, the way a freed fish bolts
        Entity angler = mc.level.getEntity(p.playerId);
        Vec3 out = angler == null ? new Vec3(1, 0, 0) : from.subtract(angler.position()).multiply(1, 0, 1);
        out = out.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : out.normalize();
        EXITS.add(new Exit(p.playerId, p.landed, stack, p.lengthCm, from, out, mc.level.getGameTime()));
        if (!p.landed) {   // the boil it leaves
            for (int i = 0; i < 8 + p.lengthCm / 12; i++) {
                mc.level.addParticle(ParticleTypes.SPLASH, from.x + (mc.level.random.nextDouble() - 0.5) * 0.8, from.y + 0.1,
                        from.z + (mc.level.random.nextDouble() - 0.5) * 0.8, 0, 0.12, 0);
            }
        }
    }

    static boolean any() {
        return !EXITS.isEmpty();
    }

    /** Draws the fish on their way; true if it drew anything. Called in the hooked fish's pass. */
    static boolean draw(Minecraft mc, PoseStack pose, MultiBufferSource buffers, float pt) {
        if (EXITS.isEmpty() || mc.level == null) return false;
        long now = mc.level.getGameTime();
        boolean drew = false;
        for (Iterator<Exit> it = EXITS.iterator(); it.hasNext(); ) {
            Exit e = it.next();
            float t = ((now - e.start) + pt) / (e.landed ? LIFT_TICKS : AWAY_TICKS);
            Entity angler = mc.level.getEntity(e.playerId);
            if (t >= 1f || t < 0f || angler == null) { it.remove(); continue; }
            Vec3 at;
            float heading, pitch, roll;
            float kick = Mth.sin((now + pt) * 1.9f) * (e.landed ? 22f : 9f);
            if (e.landed) {
                // up out of the water and over to the hands, in an arc — kicking all the way
                Vec3 hands = angler.getEyePosition(pt).add(angler.getViewVector(pt).scale(0.7)).add(0, -0.45, 0);
                at = e.from.lerp(hands, t).add(0, Math.sin(Math.PI * t) * (0.8 + e.lengthCm / 150.0), 0);
                heading = (float) Math.atan2(hands.z - e.from.z, hands.x - e.from.x);
                pitch = -60f + 60f * t;   // hangs from the mouth, nose up, and levels off as it comes in
                roll = kick;
                if (mc.level.random.nextInt(3) == 0) {
                    mc.level.addParticle(ParticleTypes.FALLING_WATER, at.x, at.y - 0.1, at.z, 0, 0, 0);
                }
            } else {
                // it turns from the rod, bolts and goes down
                at = e.from.add(e.away.scale(3.5 * t)).add(0, -1.6 * t * t, 0);
                heading = (float) Math.atan2(e.away.z, e.away.x);
                pitch = 20f * t;
                roll = 0f;
            }
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(Axis.YP.rotationDegrees(180f - (float) Math.toDegrees(heading) + kick * 0.3f));
            if (roll != 0f) pose.mulPose(Axis.XP.rotationDegrees(roll));
            pose.mulPose(Axis.ZP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            FishItemRenderer.gridScale = ShoalRenderer.itemSize(e.lengthCm);
            pose.translate(FishItemRenderer.gridScale * -0.5, 0, 0);
            int light = e.landed ? LightTexture.pack(15, 15) : HookedFishRenderer.depthLight(e.from.y - at.y);
            mc.getItemRenderer().renderStatic(e.stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, mc.level, 0);
            FishItemRenderer.gridScale = 0f;
            pose.popPose();
            drew = true;
        }
        return drew;
    }
}
