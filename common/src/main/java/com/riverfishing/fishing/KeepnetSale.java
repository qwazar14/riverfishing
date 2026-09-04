package com.riverfishing.fishing;

import com.riverfishing.fish.CatchCard;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.KeepnetData;
import com.riverfishing.registry.ModVillagers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Iterator;

/**
 * §k §breeding (0.9.0): the whole keepnet over the counter at once.
 *
 * <p>The trade slots buy PRIME fish one at a time; a farmer's margin is volume, so a keepnet held out
 * to the fisherman sells everything in it that any fisherman buys ({@link ModVillagers#baseEmeralds}):
 * prime at the market price, a carded fish that is not prime at half, a netted fish with no card at a
 * third — nobody saw it caught. Fish nobody buys stay in the net. Every fish sold also feeds the
 * market glut, the same as a counter sale: dumping a farm on the stall is exactly when the price
 * should fall.
 */
public final class KeepnetSale {
    private KeepnetSale() {}

    /** Emeralds for one of this fish, 0 when no fisherman wants the species. */
    public static int price(ServerLevel level, ItemStack fish) {
        ResourceLocation species = FishItem.getSpecies(fish);
        if (species == null) return 0;
        int base = ModVillagers.baseEmeralds(species.getPath());
        if (base <= 0) return 0;
        int market = MarketData.get(level).price(level, species.getPath(), base);
        // §koi-genes: a koi's variety is most of what it is worth — a tancho is not just a big carp.
        market = (int) Math.max(1, Math.round(market * com.riverfishing.fish.Genome.varietyValue(
                CatchCard.of(fish).getString("Variety"))));
        if (FishItem.isPrime(fish)) return market;
        // §netted-card: a netted fish carries a card now, but it is still a third — nobody saw it bite.
        boolean netted = !CatchCard.has(fish) || CatchCard.of(fish).getBoolean("Net");
        return Math.max(1, netted ? market / 3 : market / 2);
    }

    public static void sell(ServerPlayer sp, ItemStack net) {
        ServerLevel level = sp.serverLevel();
        KeepnetData data = KeepnetData.read(net);
        MarketData market = MarketData.get(level);
        int count = 0, em = 0;
        Iterator<KeepnetData.Placed> it = data.items().iterator();
        while (it.hasNext()) {
            ItemStack fish = it.next().stack();
            int each = price(level, fish);
            if (each <= 0) continue;
            em += each * fish.getCount();
            count += fish.getCount();
            for (int i = 0; i < fish.getCount(); i++) market.addSupply(FishItem.getSpecies(fish).getPath());
            it.remove();
        }
        if (count == 0) {
            sp.displayClientMessage(Component.translatable("message.riverfishing.keepnet_unwanted").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        data.write(net);
        ItemStack pay = new ItemStack(Items.EMERALD, em);
        if (!sp.getInventory().add(pay)) sp.drop(pay, false);
        sp.displayClientMessage(Component.translatable("message.riverfishing.keepnet_sold", count, em).withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, sp.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.8f, 1.1f);
    }
}
