package com.riverfishing.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * §contracts (0.9.0): the paper you take off the fisherman's board.
 *
 * <p>A contract is an ITEM because that is the only way it can be a promise: it sits in the bag, it
 * reads out its own terms, it counts its own progress, and it is handed back to the fisherman who
 * posted it. The terms live in its NBT under the same keys the board sends them with, so one method
 * ({@link #terms}) reads a post on the board, a tooltip in the bag and a row in the journal.
 *
 * <p>Keys: {@code Id} (villager+day+slot, so the same post cannot be taken twice), {@code Sp}, {@code N},
 * {@code W} grams, and the terms — {@code Water}, {@code Rod}, {@code Bait}, {@code Time}, each empty
 * when the contract does not care — then {@code Em}/{@code Xp}/{@code Rep} pay, {@code Caught} so far
 * and {@code Exp}, the world day it stops being worth anything.
 */
public class ContractItem extends Item {
    public ContractItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static CompoundTag tag(ItemStack stack) {
        return StackNbt.get(stack);
    }

    /** The head line: "3 x bream, from 500 g". */
    public static net.minecraft.network.chat.MutableComponent headline(CompoundTag t) {
        return Component.translatable("journal.riverfishing.contract_row", t.getIntOr("N", 0),
                Component.translatable("fish.riverfishing." + t.getStringOr("Sp", "")), grams(t.getIntOr("W", 0)));
    }

    /** The conditions beyond the fish itself, one line each, in the order the board prints them. */
    public static List<net.minecraft.network.chat.MutableComponent> terms(CompoundTag t) {
        List<net.minecraft.network.chat.MutableComponent> out = new ArrayList<>();
        String water = t.getStringOr("Water", ""), rod = t.getStringOr("Rod", ""), bait = t.getStringOr("Bait", ""),
                time = t.getStringOr("Time", "");
        if (!water.isEmpty()) out.add(Component.translatable("contract.riverfishing.term.water",
                Component.translatable("water.riverfishing." + water)));
        if (!rod.isEmpty()) out.add(Component.translatable("contract.riverfishing.term.rod",
                Component.translatable("contract.riverfishing.rod." + rod)));
        if (!bait.isEmpty()) out.add(Component.translatable("contract.riverfishing.term.bait",
                Component.translatable("item.riverfishing." + bait)));
        if (!time.isEmpty()) out.add(Component.translatable("contract.riverfishing.term.time",
                Component.translatable("time.riverfishing." + time)));
        return out;
    }

    /** A weight bar the way an angler says it: grams under a kilo, kilos above. */
    public static String grams(int g) {
        return g >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f kg", g / 1000f) : g + " g";
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tip, TooltipFlag flag) {
        CompoundTag t = tag(stack);
        if (!t.contains("Sp")) return;
        tip.accept(headline(t).withStyle(ChatFormatting.GOLD));
        for (var c : terms(t)) tip.accept(c.withStyle(ChatFormatting.GRAY));
        // §catch-card: progress is read off the fish, not kept on the paper
        tip.accept(Component.translatable("contract.riverfishing.pay", t.getIntOr("Em", 0), t.getIntOr("Xp", 0))
                .withStyle(ChatFormatting.DARK_GREEN));
        // The client has no world day to compare against on a server, so the paper prints the day it
        // expires on and the journal, which is told the day, prints "n days left".
        tip.accept(Component.translatable("contract.riverfishing.expires", t.getLongOr("Exp", 0L))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

// §ported
