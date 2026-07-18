package com.riverfishing.registry;

import com.google.common.collect.ImmutableSet;
import com.riverfishing.RiverFishing;
import com.riverfishing.component.ComponentSlot;
import com.riverfishing.component.RigType;
import com.riverfishing.fish.FishProfile;
import com.riverfishing.fish.FishProfileManager;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.RodData;
import com.riverfishing.rig.RigData;
import com.riverfishing.rig.RigLayout;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Fisherman villager (§8): a custom profession with its own POI job-site block ("Fishing Stall") and
 * five trade tiers. Trades are added on the forge bus via {@link VillagerTradesEvent}.
 */
public final class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(RiverFishing.MODID, Registries.POINT_OF_INTEREST_TYPE);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(RiverFishing.MODID, Registries.VILLAGER_PROFESSION);

    /** Register POI + profession, then hand the trade table to the platform seam (§multiloader). */
    public static void init() {
        POI_TYPES.register();
        PROFESSIONS.register();
        registerTrades();
    }

    public static final RegistrySupplier<PoiType> FISHERMAN_POI = POI_TYPES.register("fisherman",
            () -> new PoiType(Set.copyOf(ModBlocks.FISHING_STALL.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final RegistrySupplier<VillagerProfession> FISHERMAN = PROFESSIONS.register("fisherman",
            () -> new VillagerProfession("river_fisherman",
                    holder -> holder.is(FISHERMAN_POI.getKey()),
                    holder -> holder.is(FISHERMAN_POI.getKey()),
                    ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_FISHERMAN));

    /** Prime-grade threshold (§prime-fish): the buyer only takes the top of the species' size range. */
    public static final double PRIME_FRACTION = 0.7;

    private ModVillagers() {}

    /** Build the fisherman's five trade tiers, then register them through the platform seam (§multiloader). */
    private static void registerTrades() {
        Int2ObjectMap<List<VillagerTrades.ItemListing>> t = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
        for (int lvl = 1; lvl <= 5; lvl++) {
            t.put(lvl, new ArrayList<>());
        }

        // Level 1 — Novice: starter consumables + buys the beginner fish.
        sell(t, 1, "worm", 1, 12, 1);
        sell(t, 1, "bloodworm", 1, 8, 1);
        sell(t, 1, "float", 1, 2, 2);
        sell(t, 1, "groundbait_powder", 1, 6, 2);
        // §bait-crops: seeds for the plant baits — the "buy from traders" leg of the seed economy.
        sell(t, 1, "corn_seeds", 1, 3, 1);
        sell(t, 1, "pea_seeds", 1, 3, 1);
        sell(t, 1, "barley_seeds", 1, 3, 1);
        sell(t, 1, "line_mono_014", 2, 1, 3);       // thin starter line
        sell(t, 1, "worm_farm", 4, 1, 4);           // §bait-farm: breed your own worms early
        buyPrime(t, 1, "bleak", 1, 1);
        buyPrime(t, 1, "gudgeon", 1, 1);
        buyPrime(t, 1, "roach", 1, 1);
        buyPrime(t, 1, "bluegill", 1, 1);

        // Level 2 — Apprentice: float-fishing kit, including READY-MADE tackle (§assembled-trades).
        sell(t, 2, "maggot", 1, 10, 2);
        sell(t, 2, "reel_2000", 4, 1, 5);
        sell(t, 2, "reel_3000", 6, 1, 6);
        sell(t, 2, "line_mono_018", 2, 1, 4);
        sell(t, 2, "groundbait_grain", 1, 6, 3);
        sell(t, 2, "bait_trap", 3, 1, 4);           // the trap slowly farms livebait (§livebait)
        sellStack(t, 2, 4, ModVillagers::assembledFloatRig, 6);
        sellStack(t, 2, 9, ModVillagers::assembledBambooRod, 8);
        buyPrime(t, 2, "crucian_carp", 2, 2);
        buyPrime(t, 2, "perch", 2, 2);
        buyPrime(t, 2, "ruffe", 1, 2);
        buyPrime(t, 2, "rudd", 2, 2);
        buyPrime(t, 2, "sabrefish", 2, 2);

        // Level 3 — Journeyman: lures (also craftable now, §lure-recipes) + a ready spinning setup.
        sell(t, 3, "spinner", 3, 1, 8);
        sell(t, 3, "spoon", 4, 1, 8);
        sell(t, 3, "silicone", 2, 2, 6);
        sell(t, 3, "line_braid_016", 5, 1, 10);
        sell(t, 3, "line_fluoro_020", 5, 1, 10);
        sell(t, 3, "leader_fluoro", 3, 2, 6);
        sell(t, 3, "fish_finder", 14, 1, 12);        // §QoL: read the swim before you cast
        sellStack(t, 3, 16, ModVillagers::assembledSpinningRod, 14);
        buyPrime(t, 3, "bream", 3, 4);
        buyPrime(t, 3, "ide", 3, 5);
        buyPrime(t, 3, "chub", 3, 5);
        buyPrime(t, 3, "tench", 4, 5);
        buyPrime(t, 3, "blue_bream", 2, 3);
        buyPrime(t, 3, "pike", 5, 8);

        // Level 4 — Expert: serious predator/carp gear + winter tackle + a ready feeder setup.
        sell(t, 4, "wobbler", 7, 1, 15);
        sell(t, 4, "livebait", 2, 3, 8);
        sell(t, 4, "boilie", 3, 8, 10);
        sell(t, 4, "reel_5000", 10, 1, 15);
        sell(t, 4, "reel_6000", 13, 1, 16);
        sell(t, 4, "line_fluoro_030", 6, 1, 12);
        sell(t, 4, "ice_auger", 9, 1, 14);           // §ice-fishing: drill your first hole
        sell(t, 4, "winter_rod", 8, 1, 12);
        sell(t, 4, "mormyshka", 3, 2, 8);
        sell(t, 4, "maggot_farm", 5, 1, 8);          // §bait-farm
        sell(t, 4, "groundbait_cake", 4, 3, 6);      // жмых (sunflower+piston)
        sellStack(t, 4, 18, ModVillagers::assembledFeederRod, 18);
        buyPrime(t, 4, "carp", 6, 12);
        buyPrime(t, 4, "grass_carp", 9, 14);   // §grass-carp: a hard-fighting prize, pays well
        buyPrime(t, 4, "zander", 6, 10);
        buyPrime(t, 4, "trout", 6, 12);
        buyPrime(t, 4, "largemouth_bass", 7, 12);
        buyPrime(t, 4, "rainbow_trout", 7, 12);
        buyPrime(t, 4, "grayling", 7, 12);
        buyPrime(t, 4, "burbot", 5, 10);

        // Level 5 — Master: the trade-only prestige gear (§progression).
        sell(t, 5, "digital_alarm", 10, 1, 25);
        sell(t, 5, "leader_titanium", 8, 1, 20);
        sell(t, 5, "reel_7000", 16, 1, 26);
        sell(t, 5, "line_braid_030", 10, 1, 22); // the catfish braid (§strain-recompute)
        sell(t, 5, "carp_rod", 18, 1, 30);
        sell(t, 5, "bottom_rod", 16, 1, 28);
        // sea-tackle (0.5.0): the saltwater counter — master-tier gate to the ocean.
        sell(t, 5, "surf_rod", 20, 1, 30);
        sell(t, 5, "sea_spin_rod", 17, 1, 28);
        sell(t, 5, "boat_rod", 19, 1, 30);
        sell(t, 5, "reel_8000", 18, 1, 28);
        sell(t, 5, "reel_10000", 22, 1, 30);
        sell(t, 5, "reel_12000", 26, 1, 32);
        sell(t, 5, "reel_14000", 30, 1, 34);
        sell(t, 5, "line_mono_050", 8, 1, 20);
        sell(t, 5, "line_braid_040", 14, 1, 24);
        sell(t, 5, "hook_2", 3, 3, 10);
        sell(t, 5, "hook_1", 4, 3, 12);
        buyPrime(t, 5, "catfish", 12, 25);
        buyPrime(t, 5, "eel", 8, 15);
        buyPrime(t, 5, "channel_catfish", 10, 20);
        buyPrime(t, 5, "sterlet", 16, 30);
        buyPrime(t, 5, "silver_carp", 14, 26);
        buyPrime(t, 5, "wild_carp", 14, 28);

        com.riverfishing.platform.VillagerTradeRegistry.register(FISHERMAN, t);
    }

    private static Item item(String path) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(RiverFishing.id(path));
    }

    /** Villager sells {@code count}× item for {@code emeraldCost} emeralds (§multiloader: a plain listing). */
    private static void sell(Int2ObjectMap<List<VillagerTrades.ItemListing>> t, int level, String path,
                             int emeraldCost, int count, int xp) {
        Item i = item(path);
        if (i == null || i == Items.AIR) return;
        ItemStack result = new ItemStack(i, count);
        t.get(level).add((trader, random) ->
                new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, emeraldCost), result.copy(), 12, xp, 0.05f));
    }

    /** Villager sells a LAZILY built NBT stack (assembled rig/rod, §assembled-trades). */
    private static void sellStack(Int2ObjectMap<List<VillagerTrades.ItemListing>> t, int level,
                                  int emeraldCost, Supplier<ItemStack> result, int xp) {
        t.get(level).add((trader, random) -> {
            ItemStack out = result.get();
            if (out.isEmpty()) return null;
            return new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, emeraldCost), out, 8, xp, 0.05f);
        });
    }

    /**
     * Villager buys ONE prime-grade fish for {@code emeralds} (§prime-fish): only specimens from the
     * top {@code 1 - PRIME_FRACTION} of the species' weight range carry the Grade/MinW NBT written at
     * the catch, and vanilla's subset tag-matching does the gating. The cost item's tooltip shows the
     * "принимает от N" legend.
     */
    private static void buyPrime(Int2ObjectMap<List<VillagerTrades.ItemListing>> t, int level,
                                 String path, int emeralds, int xp) {
        t.get(level).add((trader, random) -> {
            Item i = item(path);
            if (!(i instanceof FishItem)) return null;
            // §prime-fish (1.21): gate the buy-cost on the registered PRIME component, whose value is the
            // species' min accepted weight (≥70% of max, set via FishItem.gradePrime at catch). Expecting the
            // exact threshold both restricts the trade to prime specimens AND makes the cost slot show the
            // "accepts from N" legend — the client rebuilds the display stack from this predicate.
            FishProfile profile = FishProfileManager.get().byId(RiverFishing.id(path));
            if (profile == null) return null;
            int threshold = FishItem.primeThresholdG(profile.weightMax);
            net.minecraft.world.item.trading.ItemCost cost =
                    new net.minecraft.world.item.trading.ItemCost(i)
                            .withComponents(b -> b.expect(ModComponents.PRIME.get(), threshold));
            return new MerchantOffer(cost, new ItemStack(Items.EMERALD, emeralds), 12, xp, 0.05f);
        });
    }

    // ---- assembled tackle builders (§assembled-trades). No custom "(в сборе)" name — they read as
    // the plain gear, and the socketed parts already show in the tooltip.

    /** Bamboo's built-in light float rig, loaded with a float and a №10 hook — cast-ready for the beginner. */
    private static ItemStack assembledFloatRig() {
        Item rigItem = item("rig_float_light");
        Item hook = item("hook_10");
        Item floatItem = item("float");
        if (rigItem == null || hook == null || floatItem == null) return ItemStack.EMPTY;
        ItemStack rig = new ItemStack(rigItem);
        NonNullList<ItemStack> c = NonNullList.withSize(RigLayout.rolesFor(RigType.FLOAT_LIGHT).length, ItemStack.EMPTY);
        c.set(0, new ItemStack(floatItem)); // FLOAT
        c.set(1, new ItemStack(hook));      // HOOK
        RigData.save(rig, c);
        return rig;
    }

    /** Predator rig with a steel leader and a spinner in the lure slot. */
    private static ItemStack assembledPredatorRig() {
        Item rigItem = item("rig_predator");
        Item leader = item("leader");
        Item lure = item("spinner");
        if (rigItem == null || leader == null || lure == null) return ItemStack.EMPTY;
        ItemStack rig = new ItemStack(rigItem);
        NonNullList<ItemStack> c = NonNullList.withSize(RigLayout.rolesFor(RigType.PREDATOR).length, ItemStack.EMPTY);
        c.set(0, new ItemStack(leader));    // LEADER
        c.set(1, new ItemStack(lure));      // LURE
        RigData.save(rig, c);
        return rig;
    }

    /** Feeder rig with a №8 hook — groundbait/bait are up to the angler. */
    private static ItemStack assembledFeederRig() {
        Item rigItem = item("rig_feeder");
        Item hook = item("hook_8");
        if (rigItem == null || hook == null) return ItemStack.EMPTY;
        ItemStack rig = new ItemStack(rigItem);
        NonNullList<ItemStack> c = NonNullList.withSize(RigLayout.rolesFor(RigType.FEEDER).length, ItemStack.EMPTY);
        c.set(0, new ItemStack(hook));      // HOOK
        RigData.save(rig, c);
        return rig;
    }

    private static ItemStack assembledRod(String rodId, String reelId, String lineId, ItemStack rig) {
        Item rodItem = item(rodId);
        Item lineItem = item(lineId);
        if (rodItem == null || lineItem == null || rig.isEmpty()) return ItemStack.EMPTY;
        ItemStack rod = new ItemStack(rodItem);
        if (reelId != null) {
            Item reel = item(reelId);
            if (reel == null) return ItemStack.EMPTY;
            RodData.set(rod, ComponentSlot.REEL, new ItemStack(reel));
        }
        RodData.set(rod, ComponentSlot.LINE, new ItemStack(lineItem));
        RodData.set(rod, ComponentSlot.RIG, rig);
        return rod;
    }

    private static ItemStack assembledBambooRod() {
        return assembledRod("bamboo_rod", null, "line_mono_018", assembledFloatRig());
    }

    private static ItemStack assembledSpinningRod() {
        return assembledRod("spinning_rod", "reel_2000", "line_braid_016", assembledPredatorRig());
    }

    private static ItemStack assembledFeederRod() {
        return assembledRod("feeder_rod", "reel_5000", "line_mono_025", assembledFeederRig());
    }
}
