package com.riverfishing.block;

import com.riverfishing.engine.Calendar;
import com.riverfishing.fish.CatchCard;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.fish.Genome;
import com.riverfishing.item.BaitItem;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.FryItem;
import com.riverfishing.item.GroundbaitItem;
import com.riverfishing.item.RoeItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * §breeding (0.9.0): the rules that make the display aquarium a live tank.
 *
 * <p>Kept beside the block entity rather than inside it so the entity stays what it was — a list of
 * fish and four counters — and the rules read top to bottom in one place. The counters are
 * package-private fields on the entity and this class is the only thing that touches them.
 *
 * <p>Everything is a right-click on the tank (no screen): food feeds it, roe goes in to hatch, an
 * empty hand takes the roe slot before it takes a fish, and a SNEAKING empty hand only asks the tank
 * what it is doing — the same click without sneaking would pull a fish out, and "no pair" as the
 * fish leaves your tank would be a joke at your expense.
 *
 * <p>Days are the world's day counter ({@code dayTime / 24000}), the same clock the catch card writes.
 */
final class AquariumBreeding {
    /** Three game days of unbroken conditions make a clutch. */
    static final int SPAWN_TICKS = 3 * 24000;
    // §tank-days: the counters hold the WORLD TIME the run started (0 = not running), and progress is
    // world time minus that. Ticks-while-loaded looked the same in a test that sat by the tank and
    // stuck at "1/3" for anyone who slept, set the time, or walked off — days the tank never saw.
    /** The ticker looks once a second: the shortest thing it times is days long. */
    static final int STEP = 20;
    private static final int DAY = 24000;
    private static final Random RNG = new Random();

    private AquariumBreeding() {}

    // ---- the ticker (master cell, server) ----

    static void tick(Level level, AquariumBlockEntity be) {
        if (level.getGameTime() % STEP != 0) return;
        long day = level.getDayTime() / DAY;
        if (be.getFishes().isEmpty()) {
            be.spawnTicks = 0;
            // A tank with no adults incubates whatever roe it holds; adults would have eaten it.
            if (be.roe.getItem() instanceof RoeItem) {
                if (be.incubate == 0) { be.incubate = Math.max(1, level.getDayTime()); be.setChanged(); }
                else if (level.getDayTime() - be.incubate >= incubateTicks(level, be)) hatch(be);
            }
            return;
        }
        if (!be.roe.isEmpty()) return;          // one clutch at a time: the slot is emptied by hand
        ItemStack[] pair = pair(be);
        FishProfile p = pair == null ? null : profile(FishItem.getSpecies(pair[0]));
        if (p == null || !mature(pair) || !Calendar.inWindow(level, p) || day >= be.fedUntil) {
            // Continuous conditions, or none: a missed feeding starts the three days over.
            if (be.spawnTicks != 0) { be.spawnTicks = 0; be.setChanged(); }
            return;
        }
        if (be.spawnTicks == 0) { be.spawnTicks = Math.max(1, level.getDayTime()); be.setChanged(); return; }
        if (level.getDayTime() - be.spawnTicks < SPAWN_TICKS) return;
        ItemStack mother = pair[0];
        String genome = Genome.cross(Genome.of(mother), Genome.of(pair[1]), RNG);
        int eggs = Genome.clutch(Genome.of(mother), FishItem.getWeightG(mother), p, RNG);
        be.roe = RoeItem.of(FishItem.getSpecies(mother), genome, eggs, day);
        be.spawnTicks = 0;
        be.sync();
    }

    /** Vigour is what survives the egg: VV nine in ten, vv one in two. Never fewer than one fry. */
    private static void hatch(AquariumBlockEntity be) {
        String g = RoeItem.genome(be.roe);
        double survival = !Genome.dominant(g, 'V') ? 0.5 : Genome.pure(g, 'V') ? 0.9 : 0.7;
        int n = Math.max(1, (int) Math.round(RoeItem.count(be.roe) * survival));
        be.roe = FryItem.of(RoeItem.species(be.roe), g, n);
        be.incubate = 0;
        be.sync();
    }

    /**
     * Four days where the climate suits the species, eight where it does not. Only the climate group
     * is read (cold / temperate / warm, the same thresholds as {@code FishingManager.addBiomeGroups})
     * because the terrain groups (taiga, river_biome, beach…) describe where the WATER lies, and a
     * tank stands in a house. A profile that lists no biomes lives anywhere.
     */
    private static int incubateTicks(Level level, AquariumBlockEntity be) {
        FishProfile p = profile(RoeItem.species(be.roe));
        float t = level.getBiome(be.getBlockPos()).value().getBaseTemperature();
        String climate = t < 0.3f ? "cold" : t > 0.95f ? "warm" : "temperate";
        boolean suits = p == null || p.biomes.isEmpty() || p.biomes.getOrDefault(climate, 0.0) > 0;
        return (suits ? 4 : 8) * DAY;
    }

    // ---- right-clicks (before the block's own add-a-fish / take-a-fish) ----

    /** True when the click was the tank's business and the block should stop there. */
    static boolean use(Level level, AquariumBlockEntity be, Player player, ItemStack held) {
        boolean food = held.getItem() instanceof GroundbaitItem
                || (held.getItem() instanceof BaitItem b && !b.artificial());
        if (held.getItem() instanceof RoeItem) {
            if (!be.getFishes().isEmpty() || !be.roe.isEmpty()) { say(player, "tank_busy"); return true; }
            be.roe = held.copyWithCount(1);
            held.shrink(1);
            be.incubate = 0;
            be.sync();
            player.displayClientMessage(status(level, be), true);
            return true;
        }
        if (held.getItem() instanceof FishItem && !be.roe.isEmpty()) {
            say(player, "tank_busy");
            return true;
        }
        if (food && !be.getFishes().isEmpty()) {
            be.fedUntil = level.getDayTime() / DAY + 1;
            held.shrink(1);
            be.setChanged();
            level.playSound(null, be.getBlockPos(), SoundEvents.GENERIC_EAT, SoundSource.BLOCKS, 0.5f, 1.4f);
            player.displayClientMessage(status(level, be), true);
            return true;
        }
        if (held.isEmpty()) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(status(level, be), true);
                return true;
            }
            if (!be.roe.isEmpty()) {
                // Roe before fish. Pulling incubating roe out forgets its days (ponytail: the item
                // could carry the counter; nobody has asked).
                ItemStack out = be.roe;
                be.roe = ItemStack.EMPTY;
                be.incubate = 0;
                be.sync();
                if (!player.getInventory().add(out)) player.drop(out, false);
                level.playSound(null, be.getBlockPos(), SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 0.7f, 1.2f);
                return true;
            }
        }
        return false;
    }

    /** What the tank is doing, or the first thing it lacks — in the order the ticker checks them. */
    static Component status(Level level, AquariumBlockEntity be) {
        if (be.roe.getItem() instanceof FryItem) return msg("tank_fry_ready");
        if (be.roe.getItem() instanceof RoeItem) {
            if (!be.getFishes().isEmpty()) return msg("tank_roe_ready");
            int days = incubateTicks(level, be) / DAY;
            long run = be.incubate == 0 ? 0 : level.getDayTime() - be.incubate;
            return msg("tank_incubating", (int) Math.min(days, run / DAY + 1), days);
        }
        if (be.getFishes().isEmpty()) return msg("tank_empty");
        ItemStack[] pair = pair(be);
        FishProfile p = pair == null ? null : profile(FishItem.getSpecies(pair[0]));
        if (p == null) return msg("tank_no_pair");
        if (!mature(pair)) return msg("tank_not_mature");
        if (!Calendar.inWindow(level, p)) {
            return msg("tank_out_of_season", RoeItem.speciesName(p.id), Calendar.name(p.spawnSeason, p.spawnSub));
        }
        if (level.getDayTime() / DAY >= be.fedUntil) return msg("tank_hungry");
        long run = be.spawnTicks == 0 ? 0 : level.getDayTime() - be.spawnTicks;
        return msg("tank_spawning", (int) Math.min(3, run / DAY + 1), 3);
    }

    // ---- helpers ----

    /**
     * A ♀ and a ♂ of one species, mother first; null when the tank holds no such pair. Only fish with
     * a catch card have a sex — a netted fish is nobody's parent. Maturity is checked apart so the
     * message can say "too young" rather than "no pair".
     */
    private static ItemStack[] pair(AquariumBlockEntity be) {
        for (ItemStack f : be.getFishes()) {
            if (!CatchCard.has(f) || CatchCard.of(f).getByte("Sex") != 0) continue;
            ResourceLocation sp = FishItem.getSpecies(f);
            if (sp == null) continue;
            for (ItemStack m : be.getFishes()) {
                if (m != f && CatchCard.has(m) && CatchCard.of(m).getByte("Sex") == 1
                        && sp.equals(FishItem.getSpecies(m))) {
                    return new ItemStack[]{f, m};
                }
            }
        }
        return null;
    }

    /** Both at least an adult (Card.Size 2): babies and juveniles keep growing, they do not spawn. */
    private static boolean mature(ItemStack[] pair) {
        return CatchCard.of(pair[0]).getByte("Size") >= 2 && CatchCard.of(pair[1]).getByte("Size") >= 2;
    }

    private static FishProfile profile(ResourceLocation species) {
        return species == null ? null : FishProfileManager.get().byId(species);
    }

    private static Component msg(String key, Object... args) {
        return Component.translatable("message.riverfishing." + key, args);
    }

    private static void say(Player player, String key) {
        player.displayClientMessage(msg(key), true);
    }
}
