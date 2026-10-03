package com.riverfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.riverfishing.item.RodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * §cast-anim (§26.1): the first-person casting motion — the rod loads BACK while the throw charges
 * (tracking the power bar) and WHIPS forward on release (riding the vanilla swing) — and §rod-bend-3d, the
 * segmented blank drawn as a bone chain in place of the flat model. The old BEWLR applied this inside the
 * item renderer; data-driven models can't, so it is applied to the arm frame right before the in-hand item
 * is submitted. 26.2 reaches here from ItemInHandRenderer.renderItem, 26.3 from
 * FirstPersonHandsAndItemsRenderer.submitArmWithItem — the two mixins are thin doors onto this one body.
 */
public final class RodFirstPerson {
    private RodFirstPerson() {}

    /** 26.3's door: it knows the hand, not the display context. */
    public static boolean submit(ItemStack stack, InteractionHand hand, PoseStack pose, SubmitNodeCollector collector, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? mc.player.getMainArm() : mc.player.getMainArm().getOpposite();
        ItemDisplayContext ctx = arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        return submit(mc.player, stack, ctx, pose, collector, light);
    }

    /**
     * Pose the rod for the first-person hand and, for a segmented blank, draw it here.
     *
     * @return true when the rod was drawn as its 3D chain and the item model must not be stamped over it
     */
    public static boolean submit(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                                 SubmitNodeCollector collector, int light) {
        if (!(stack.getItem() instanceof RodItem)) return false;
        if (ctx != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && ctx != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity != mc.player) return false;

        RodPhysics.update();   // §rod-anim: this frame's motion before it is applied, not the last frame's
        float chargePower = 0f;
        // Wind-up only while actively charging a cast (holding, no line out yet) — not during a retrieve.
        if (mc.player.isUsingItem() && mc.player.getUseItem() == stack && !ClientLineState.active()) {
            int used = stack.getUseDuration(mc.player) - mc.player.getUseItemRemainingTicks();
            chargePower = RodItem.castPower(used);
        }
        float swing = com.riverfishing.compat.Mc.attackAnim(mc.player, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        float pitch = RodHandTransform.castPitch(chargePower, swing);
        if (pitch != 0f) {
            com.riverfishing.compat.Mc.rotate(pose, com.mojang.math.Axis.XP.rotationDegrees(pitch));
        }
        RodAnim.applyFirstPerson(pose);   // §rod-anim: equip, retrieve, strike, fight holds — the hands acting
        // §rod-bend-3d: a segmented blank is drawn HERE as a bone chain, and vanilla must not then
        // stamp the flat model over it. The 3D pose set is the true-scale one — the sprite poses shrink
        // the rod because a sprite blank is 16 units wide, and these blanks are modelled at full length.
        String rodKey = RodModelLayers.rodKey(stack);
        if (rodKey != null && RodChain.has(rodKey)) {
            // push BEFORE the 3D pose: when submit() refuses, the pop leaves the pose exactly as it
            // was, and the sprite fallback below applies its own. Popping before the item is skipped is
            // safe: retained submission snapshots the matrices per node.
            pose.pushPose();
            RodHandTransform.apply(pose, ctx, true, rodKey);
            float load = FlyLineClient.active() ? FlyLineClient.load() : RodAnim.displayLoad(ClientLineState.ownRodLoad());
            boolean drew = RodChain.submit(stack, rodKey, load, ctx, pose, collector, light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
            if (drew) return true;   // the chain IS the rod now
        }
        // §rod-debug: the whole first-person hand pose lives in code so /rfrod tunes it LIVE —
        // the model's hand display only carries the per-layer depth lift.
        RodHandTransform.apply(pose, ctx);
        return false;
    }
}
