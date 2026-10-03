package com.riverfishing.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * §26.3: the calls 26.3 renamed or re-shaped, answered once here so the rest of the tree is written one way
 * for both game versions. Every body is a Stonecutter switch; nothing else in this class is allowed to grow.
 */
public final class Mc {
    private Mc() {}

    /** A rotation onto the pose: 26.3 calls it rotate, 26.2 mulPose. */
    public static void rotate(PoseStack pose, Quaternionf q) {
        //? if >=26.3 {
        /*pose.rotate(q);
        *///?} else {
        pose.mulPose(q);
        //?}
    }

    /** 26.3 asks whether the client already predicted the drop; the mod's drops are the vanilla default. */
    public static ItemEntity drop(Player player, ItemStack stack, boolean thrown) {
        //? if >=26.3 {
        /*return player.drop(stack, thrown, net.minecraft.util.Prediction.PREDICTED);
        *///?} else {
        return player.drop(stack, thrown);
        //?}
    }

    /** 26.3 swings with an animation of the item's; the mod swings the default one. */
    public static void swing(LivingEntity entity, InteractionHand hand, boolean broadcast) {
        //? if >=26.3 {
        /*entity.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, broadcast);
        *///?} else {
        entity.swing(hand, broadcast);
        //?}
    }

    public static void placeBack(Inventory inv, ItemStack stack) {
        //? if >=26.3 {
        /*inv.placeItemBackInInventory(stack, net.minecraft.util.Prediction.PREDICTED);
        *///?} else {
        inv.placeItemBackInInventory(stack);
        //?}
    }

    /** How far through its swing the arm is, 0..1: 26.3 keeps it on the swing state. */
    public static float attackAnim(LivingEntity entity, float partialTick) {
        //? if >=26.3 {
        /*return entity.getSwingAnimation(partialTick);
        *///?} else {
        return entity.getAttackAnim(partialTick);
        //?}
    }
}
