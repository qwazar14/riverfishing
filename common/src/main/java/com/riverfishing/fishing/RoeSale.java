package com.riverfishing.fishing;

import com.riverfishing.fish.FishGroup;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.item.RoeItem;
import com.riverfishing.registry.ModVillagers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * §e §breeding (0.9.0): roe at the fisherman's counter.
 *
 * <p>A trade cannot match the species inside the roe's NBT, so there is no "buys roe" slot: the clutch
 * is sold the way a contract is handed in — right-click the fisherman holding it. Priced off the same
 * {@link ModVillagers#baseEmeralds} the fish itself is bought at, so a species nobody buys has roe
 * nobody buys either: sturgeon and salmonid roe is the delicacy (×3), everything else is bait (÷2).
 */
public final class RoeSale {
    private RoeSale() {}

    /** Emeralds one clutch of this species fetches, 0 when no fisherman wants it. */
    public static int price(ResourceLocation species) {
        int base = ModVillagers.baseEmeralds(species.getPath());
        if (base <= 0) return 0;
        FishProfile p = FishProfileManager.get().byId(species);
        String g = p == null ? "" : p.group;
        return FishGroup.STURGEON.equals(g) || FishGroup.SALMONID.equals(g) ? base * 3 : Math.max(1, base / 2);
    }

    /** The stack in hand goes over the counter; one roe item is one clutch, so the count is the multiplier. */
    public static void sell(ServerPlayer sp, ItemStack roe) {
        ResourceLocation species = RoeItem.species(roe);
        int each = species == null ? 0 : price(species);
        if (each <= 0) {
            sp.displayClientMessage(Component.translatable("message.riverfishing.roe_unwanted",
                    species == null ? roe.getHoverName() : RoeItem.speciesName(species)).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        int n = roe.getCount(), em = each * n;
        roe.shrink(n);
        ItemStack pay = new ItemStack(Items.EMERALD, em);
        if (!sp.getInventory().add(pay)) sp.drop(pay, false);
        sp.displayClientMessage(Component.translatable("message.riverfishing.roe_sold",
                RoeItem.speciesName(species), em).withStyle(ChatFormatting.GREEN), true);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.8f, 1.1f);
    }
}
