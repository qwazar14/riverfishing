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
import com.riverfishing.tackle.TackleForm;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
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
 *
 * <p><b>§trade-pool</b> — vanilla draws exactly TWO offers per villager level out of that level's
 * listing pool, so pool SIZE is the real currency here: every listing added dilutes every other one.
 * 0.5.0 shipped 47 listings on level 5 (22 of them fish buys), which made a maxed fisherman ~half
 * fish-buyer and dropped any one specific rod to a ~4% chance. Variants of the same thing are now
 * folded into ONE rotating listing via {@link #oneOf} — the trick vanilla itself uses for enchanted
 * books — so a pool entry means "a reel appears", not "the 12000 appears".
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

    /**
     * §order-tier: which fisherman level buys each species, recorded as the trades are built so it cannot
     * drift from them. The order-of-the-day board prints it — the standing complaint that an order can
     * name a fish the player's own fisherman will not take is, at bottom, that the requirement was never
     * stated anywhere.
     */
    private static final java.util.Map<String, Integer> BUY_TIER = new java.util.HashMap<>();

    /** The lowest fisherman level that buys this species, or 0 if none does. */
    public static int buyTier(String species) {
        return BUY_TIER.getOrDefault(species, 0);
    }

    /** Prime-grade threshold (§prime-fish): the buyer only takes the top of the species' size range. */
    public static final double PRIME_FRACTION = 0.7;

    /** How many rotating fish-buy listings each tier gets (§trade-pool). Two, so a fisherman reliably
     *  buys SOMETHING without the pool turning back into a fish market. */
    private static final int FISH_LISTINGS_PER_TIER = 2;

    /** Vanilla's own per-level offer count — a private constant in {@code Villager}, mirrored here. */
    private static final int VANILLA_TRADES_PER_LEVEL = 2;

    /** §trade-pool: a fishing shop needs counter space for bait, line, a rod AND a fish buy, so our
     *  fisherman gets one more offer per level than vanilla hands out. Raised by the mixin. */
    public static final int TRADES_PER_LEVEL = 3;

    /** The pool the mixin draws the extra offer from — the same lists given to the platform registry. */
    private static Int2ObjectMap<List<VillagerTrades.ItemListing>> pool;

    /** §starter-fish: the ubiquitous smalls a level-1 fisherman ALWAYS buys. These four live in every
     *  water (see the community guide), so the guarantee is reachable wherever the player spawned. */
    private static final List<VillagerTrades.ItemListing> STARTER_FISH = new ArrayList<>();

    private ModVillagers() {}

    /** Build the fisherman's five trade tiers, then register them through the platform seam (§multiloader). */
    private static void registerTrades() {
        Int2ObjectMap<List<VillagerTrades.ItemListing>> t = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
        for (int lvl = 1; lvl <= 5; lvl++) {
            t.put(lvl, new ArrayList<>());
        }
        // Fish buys are collected here and folded into FISH_LISTINGS_PER_TIER rotating listings at the
        // end — registering one listing per species is what drowned the gear (§trade-pool).
        Int2ObjectMap<List<VillagerTrades.ItemListing>> fish = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
        for (int lvl = 1; lvl <= 5; lvl++) {
            fish.put(lvl, new ArrayList<>());
        }

        // Level 1 — Novice: starter consumables + buys the beginner fish.
        sell(t, 1, "worm", 1, 12, 1);
        sell(t, 1, "bloodworm", 1, 8, 1);
        sell(t, 1, "float", 1, 2, 2);
        sell(t, 1, "groundbait_powder", 1, 6, 2);
        // §bait-crops: seeds for the plant baits — the "buy from traders" leg of the seed economy.
        oneOf(t, 1, sellOf("corn_seeds", 1, 3, 1), sellOf("pea_seeds", 1, 3, 1), sellOf("barley_seeds", 1, 3, 1));
        sell(t, 1, "line_mono_014", 2, 1, 3);       // thin starter line
        sell(t, 1, "worm_farm", 4, 1, 4);           // §bait-farm: breed your own worms early
        // §vanilla-stock: every reeled rod recipe now wants string for the guide wraps (§tackle-craft),
        // and string is a miserable early grind — the village shop is the honest way out.
        sell(t, 1, "minecraft:string", 1, 4, 1);

        // Level 2 — Apprentice: float-fishing kit, including READY-MADE tackle (§assembled-trades).
        sell(t, 2, "maggot", 1, 10, 2);
        oneOf(t, 2, sellOf("reel_2000", 4, 1, 5), sellOf("reel_3000", 6, 1, 6));
        sell(t, 2, "line_mono_018", 2, 1, 4);
        sell(t, 2, "groundbait_grain", 1, 6, 3);
        sell(t, 2, "bait_trap", 3, 1, 4);           // the trap slowly farms livebait (§livebait)
        sell(t, 2, "minecraft:oak_boat", 4, 1, 5);  // §vanilla-stock: trolling needs a boat under you
        sellStack(t, 2, 4, ModVillagers::assembledFloatRig, 6);
        sellStack(t, 2, 9, ModVillagers::assembledBambooRod, 8);

        // Level 3 — Journeyman: lures (also craftable now, §lure-recipes) + a ready spinning setup.
        // §tackle-craft: shop lures ship bench-stamped, so their grams count toward the cast.
        oneOf(t, 3, sellTackleOf(TackleForm.SPINNER, 3, 1, 8), sellTackleOf(TackleForm.SPOON, 4, 1, 8),
                sellTackleOf(TackleForm.SILICONE, 2, 2, 6));
        oneOf(t, 3, sellOf("line_braid_016", 5, 1, 10), sellOf("line_fluoro_020", 5, 1, 10));
        sell(t, 3, "leader_fluoro", 3, 2, 6);
        sell(t, 3, "fish_finder", 14, 1, 12);        // §QoL: read the swim before you cast
        sellStack(t, 3, 16, ModVillagers::assembledSpinningRod, 14);

        // Level 4 — Expert: serious predator/carp gear + winter tackle + a ready feeder setup.
        oneOf(t, 4, sellTackleOf(TackleForm.WOBBLER, 7, 1, 15), sellTackleOf(TackleForm.CRANKBAIT, 7, 1, 15),
                sellTackleOf(TackleForm.POPPER, 6, 1, 14));
        sell(t, 4, "livebait", 2, 3, 8);
        sell(t, 4, "boilie", 3, 8, 10);
        oneOf(t, 4, sellOf("reel_5000", 10, 1, 15), sellOf("reel_6000", 13, 1, 16));
        sell(t, 4, "line_fluoro_030", 6, 1, 12);
        sell(t, 4, "ice_auger", 9, 1, 14);           // §ice-fishing: drill your first hole
        sell(t, 4, "mormyshka", 3, 2, 8);
        sell(t, 4, "maggot_farm", 5, 1, 8);          // §bait-farm
        sell(t, 4, "groundbait_cake", 4, 3, 6);      // жмых (sunflower+piston)
        // §vanilla-stock + §tackle-craft: the saltwater reels are gated on ocean drops. Selling the
        // INPUTS keeps the gate (you still pay for it) without making it hinge on guardian RNG.
        sell(t, 4, "minecraft:prismarine_shard", 5, 4, 10);
        sellStack(t, 4, 18, ModVillagers::assembledFeederRod, 18);
        sellStack(t, 4, 14, ModVillagers::assembledWinterRod, 14);

        // Level 5 — Master: the trade-only prestige gear (§progression).
        sell(t, 5, "digital_alarm", 10, 1, 25);
        sell(t, 5, "leader_titanium", 8, 1, 20);
        // §vanilla-stock + §tackle-craft: the 14000 reel and the trolling rod each want a nautilus
        // shell, which is the single worst piece of RNG in the ladder. Master tier sells it.
        sell(t, 5, "minecraft:nautilus_shell", 10, 1, 22);
        oneOf(t, 5, sellOf("reel_7000", 16, 1, 26), sellOf("reel_8000", 18, 1, 28),
                sellOf("reel_10000", 22, 1, 30), sellOf("reel_12000", 26, 1, 32),
                sellOf("reel_14000", 30, 1, 34));
        // §sea-lines-2: the heavy tier for the 8000-14000 reels — one mono slot, one braid/fluoro slot.
        oneOf(t, 5, sellOf("line_mono_050", 8, 1, 20), sellOf("line_mono_060", 10, 1, 22),
                sellOf("line_mono_070", 12, 1, 24), sellOf("line_mono_080", 14, 1, 26));
        oneOf(t, 5, sellOf("line_braid_030", 10, 1, 22), sellOf("line_braid_040", 14, 1, 24),
                sellOf("line_braid_050", 16, 1, 26), sellOf("line_braid_060", 18, 1, 28),
                sellOf("line_fluoro_040", 12, 1, 24));
        oneOf(t, 5, sellOf("hook_2", 3, 3, 10), sellOf("hook_1", 4, 3, 12));
        sellStack(t, 5, 30, ModVillagers::assembledCarpRod, 30);
        sellStack(t, 5, 28, ModVillagers::assembledBottomRod, 28);
        // sea-tackle (0.5.0): the saltwater counter — master-tier gate to the ocean, one pool slot.
        oneOf(t, 5, sellStackOf(30, ModVillagers::assembledSeaSpinRod, 28),
                sellStackOf(34, ModVillagers::assembledSurfRod, 30),
                sellStackOf(34, ModVillagers::assembledBoatRod, 30),
                sellStackOf(40, ModVillagers::assembledTrollingRod, 34));

        // ---- fish buys. Prices unchanged; only the POOL shape changed (§trade-pool). ----
        buyPrime(fish, 1, "bleak", 1, 1);
        buyPrime(fish, 1, "gudgeon", 1, 1);
        buyPrime(fish, 1, "roach", 1, 1);
        buyPrime(fish, 1, "bluegill", 1, 1);
        buyPrime(fish, 1, "round_goby", 1, 1);        // §river-four
        buyPrime(fish, 1, "common_dace", 1, 1);
        buyPrime(fish, 1, "rotan", 1, 2);
        buyPrime(fish, 1, "smelt", 1, 3);

        buyPrime(fish, 2, "crucian_carp", 2, 2);
        buyPrime(fish, 2, "perch", 2, 2);
        buyPrime(fish, 2, "ruffe", 1, 2);
        buyPrime(fish, 2, "rudd", 2, 2);
        buyPrime(fish, 2, "sabrefish", 2, 2);
        buyPrime(fish, 2, "white_eye_bream", 2, 3);   // §river-four
        buyPrime(fish, 2, "nase", 2, 4);
        buyPrime(fish, 2, "vimba", 3, 5);
        buyPrime(fish, 2, "white_bream", 2, 2);       // was unsellable — an oversight, not a design

        buyPrime(fish, 3, "bream", 3, 4);
        buyPrime(fish, 3, "ide", 3, 5);
        buyPrime(fish, 3, "chub", 3, 5);
        buyPrime(fish, 3, "tench", 4, 5);
        buyPrime(fish, 3, "blue_bream", 2, 3);
        buyPrime(fish, 3, "pike", 5, 8);
        buyPrime(fish, 3, "volga_zander", 4, 6);      // §river-four: a lighter, cheaper zander
        buyPrime(fish, 3, "pink_salmon", 4, 8);
        buyPrime(fish, 3, "whitefish", 4, 8);
        buyPrime(fish, 3, "asp", 6, 9);               // was unsellable — a hard-won 8 kg predator

        buyPrime(fish, 4, "carp", 6, 12);
        buyPrime(fish, 4, "mirror_carp", 7, 13);  // was unsellable; the koi stay uncommercial on purpose
        buyPrime(fish, 4, "grass_carp", 9, 14);   // §grass-carp: a hard-fighting prize, pays well
        buyPrime(fish, 4, "zander", 6, 10);
        buyPrime(fish, 4, "trout", 6, 12);
        buyPrime(fish, 4, "largemouth_bass", 7, 12);
        buyPrime(fish, 4, "rainbow_trout", 7, 12);
        buyPrime(fish, 4, "grayling", 7, 12);
        buyPrime(fish, 4, "burbot", 5, 10);
        buyPrime(fish, 4, "mackerel", 3, 6);
        buyPrime(fish, 4, "herring", 2, 4);
        buyPrime(fish, 4, "garfish", 3, 6);
        buyPrime(fish, 4, "flounder", 4, 8);
        buyPrime(fish, 4, "char", 6, 12);
        buyPrime(fish, 4, "lenok", 6, 12);
        buyPrime(fish, 4, "salmon", 10, 18);

        buyPrime(fish, 5, "catfish", 12, 25);
        buyPrime(fish, 5, "eel", 8, 15);
        buyPrime(fish, 5, "channel_catfish", 10, 20);
        buyPrime(fish, 5, "sterlet", 16, 30);
        buyPrime(fish, 5, "silver_carp", 14, 26);
        buyPrime(fish, 5, "seabass", 7, 14);
        buyPrime(fish, 5, "cod", 9, 18);
        buyPrime(fish, 5, "saithe", 7, 14);
        buyPrime(fish, 5, "conger", 13, 24);
        buyPrime(fish, 5, "ray", 12, 22);
        buyPrime(fish, 5, "mahi", 10, 20);
        buyPrime(fish, 5, "wahoo", 14, 26);
        buyPrime(fish, 5, "yellowfin_tuna", 20, 34);
        buyPrime(fish, 5, "barracuda", 8, 16);
        buyPrime(fish, 5, "blue_marlin", 28, 40);
        buyPrime(fish, 5, "sailfish", 18, 30);
        buyPrime(fish, 5, "swordfish", 24, 36);
        buyPrime(fish, 5, "mako", 22, 34);
        buyPrime(fish, 5, "wild_carp", 14, 28);
        buyPrime(fish, 5, "taimen", 24, 36);
        buyPrime(fish, 5, "sturgeon", 26, 38);
        buyPrime(fish, 5, "halibut", 22, 34);

        // Slice each tier's species into FISH_LISTINGS_PER_TIER disjoint groups, one rotating listing
        // each: the fisherman ends up buying two DIFFERENT species per tier, never a duplicate offer.
        for (int lvl = 1; lvl <= 5; lvl++) {
            List<VillagerTrades.ItemListing> all = fish.get(lvl);
            int groups = Math.min(FISH_LISTINGS_PER_TIER, all.size());
            for (int g = 0; g < groups; g++) {
                int from = g * all.size() / groups;
                int to = (g + 1) * all.size() / groups;
                oneOf(t, lvl, all.subList(from, to));
            }
        }

        // §starter-fish: the level-1 guarantee draws from the four smalls that live in EVERY water.
        STARTER_FISH.clear();
        for (String common : new String[]{"bleak", "roach", "gudgeon", "rotan"}) {
            STARTER_FISH.add(buyPrimeOf(common, 1, 1));
        }

        pool = t;
        com.riverfishing.platform.VillagerTradeRegistry.register(FISHERMAN, t);
    }

    /**
     * §trade-pool: called from {@link com.riverfishing.mixin.VillagerTradePoolMixin} the moment vanilla
     * has filled a freshly unlocked level with its {@link #VANILLA_TRADES_PER_LEVEL} offers. Adds the
     * extra counter space, then makes sure level 1 can always buy a common fish — that first emerald is
     * the whole point of the profession and must not depend on a dice roll.
     */
    public static void topUpOffers(Villager villager) {
        if (pool == null || villager.level().isClientSide) return;
        if (villager.getVillagerData().getProfession() != FISHERMAN.get()) return;
        int level = villager.getVillagerData().getLevel();
        List<VillagerTrades.ItemListing> listings = pool.get(level);
        if (listings == null || listings.isEmpty()) return;

        MerchantOffers offers = villager.getOffers();
        RandomSource random = villager.getRandom();

        int want = TRADES_PER_LEVEL - VANILLA_TRADES_PER_LEVEL;
        // Walk the pool from a random start instead of shuffling a copy: same fairness, no allocation,
        // and it naturally stops once the pool is exhausted of things we don't already offer.
        int start = random.nextInt(listings.size());
        for (int i = 0; i < listings.size() && want > 0; i++) {
            MerchantOffer offer = listings.get((start + i) % listings.size()).getOffer(villager, random);
            if (offer == null || duplicates(offers, offer)) continue;
            offers.add(offer);
            want--;
        }

        if (level == 1 && !buysFish(offers)) {
            MerchantOffer fish = starterFishOffer(villager, random);
            // Swap rather than append, so a level-1 stall always shows exactly TRADES_PER_LEVEL offers.
            if (fish != null) {
                for (int i = offers.size() - 1; i >= 0; i--) {
                    if (!(offers.get(i).getCostA().getItem() instanceof FishItem)) {
                        offers.set(i, fish);
                        break;
                    }
                }
            }
        }
    }

    /** Same thing already on the counter? Compare both sides — a buy and a sell can share an item. */
    private static boolean duplicates(MerchantOffers offers, MerchantOffer candidate) {
        for (MerchantOffer o : offers) {
            if (o.getCostA().getItem() == candidate.getCostA().getItem()
                    && o.getResult().getItem() == candidate.getResult().getItem()) {
                return true;
            }
        }
        return false;
    }

    private static boolean buysFish(MerchantOffers offers) {
        for (MerchantOffer o : offers) {
            if (o.getCostA().getItem() instanceof FishItem) return true;
        }
        return false;
    }

    private static MerchantOffer starterFishOffer(Villager villager, RandomSource random) {
        if (STARTER_FISH.isEmpty()) return null;
        int start = random.nextInt(STARTER_FISH.size());
        for (int i = 0; i < STARTER_FISH.size(); i++) {
            MerchantOffer offer = STARTER_FISH.get((start + i) % STARTER_FISH.size()).getOffer(villager, random);
            if (offer != null) return offer;
        }
        return null;
    }

    /** A bare mod path ("worm") or a full id ("minecraft:string") — the stall carries vanilla stock too. */
    private static Item item(String path) {
        ResourceLocation id = path.indexOf(':') >= 0 ? ResourceLocation.tryParse(path) : RiverFishing.id(path);
        return id == null ? Items.AIR : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
    }

    /**
     * ONE pool entry that rolls among {@code variants} when the villager generates its trades
     * (§trade-pool) — vanilla does exactly this for enchanted books. A variant may decline (return
     * null) if its item never registered, so the roll walks the list rather than wasting the slot.
     */
    private static void oneOf(Int2ObjectMap<List<VillagerTrades.ItemListing>> t, int level,
                             List<VillagerTrades.ItemListing> variants) {
        List<VillagerTrades.ItemListing> group = new ArrayList<>();
        for (VillagerTrades.ItemListing l : variants) {
            if (l != null) group.add(l);
        }
        if (group.isEmpty()) return;
        t.get(level).add((trader, random) -> {
            int start = random.nextInt(group.size());
            for (int i = 0; i < group.size(); i++) {
                MerchantOffer offer = group.get((start + i) % group.size()).getOffer(trader, random);
                if (offer != null) return offer;
            }
            return null;
        });
    }

    private static void oneOf(Int2ObjectMap<List<VillagerTrades.ItemListing>> t, int level,
                             VillagerTrades.ItemListing... variants) {
        // Arrays.asList, NOT List.of — sellOf() returns null for an unregistered item and List.of
        // would throw at mod-init, turning a typo'd item id into a startup crash.
        oneOf(t, level, java.util.Arrays.asList(variants));
    }

    /** Villager sells {@code count}× item for {@code emeraldCost} emeralds (§multiloader: a plain listing). */
    private static void sell(Int2ObjectMap<List<VillagerTrades.ItemListing>> t, int level, String path,
                             int emeraldCost, int count, int xp) {
        VillagerTrades.ItemListing l = sellOf(path, emeraldCost, count, xp);
        if (l != null) t.get(level).add(l);
    }

    /** The listing {@link #sell} would add, for folding into a rotating {@link #oneOf} entry. */
    private static VillagerTrades.ItemListing sellOf(String path, int emeraldCost, int count, int xp) {
        Item i = item(path);
        if (i == null || i == Items.AIR) return null;
        ItemStack result = new ItemStack(i, count);
        return (trader, random) -> new MerchantOffer(
                new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, emeraldCost), result.copy(), 12, xp, 0.05f);
    }

    /** Villager sells a LAZILY built NBT stack (assembled rig/rod, §assembled-trades). */
    private static void sellStack(Int2ObjectMap<List<VillagerTrades.ItemListing>> t, int level,
                                  int emeraldCost, Supplier<ItemStack> result, int xp) {
        t.get(level).add(sellStackOf(emeraldCost, result, xp));
    }

    private static VillagerTrades.ItemListing sellStackOf(int emeraldCost, Supplier<ItemStack> result, int xp) {
        return (trader, random) -> {
            ItemStack out = result.get();
            if (out.isEmpty()) return null;
            return new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, emeraldCost), out, 8, xp, 0.05f);
        };
    }

    /**
     * Villager buys ONE prime-grade fish for {@code emeralds} (§prime-fish): only specimens from the
     * top {@code 1 - PRIME_FRACTION} of the species' weight range carry the Grade/MinW NBT written at
     * the catch, and vanilla's subset tag-matching does the gating. The cost item's tooltip shows the
     * "принимает от N" legend.
     */
    private static void buyPrime(Int2ObjectMap<List<VillagerTrades.ItemListing>> t, int level,
                                 String path, int emeralds, int xp) {
        BUY_TIER.merge(path, level, Math::min);
        t.get(level).add(buyPrimeOf(path, emeralds, xp));
    }

    private static VillagerTrades.ItemListing buyPrimeOf(String path, int emeralds, int xp) {
        return (trader, random) -> {
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
            // market (0.5.0): the pay is DYNAMIC - glut cuts it to x0.5, the daily order pays x2.5.
            int pay = trader.level() instanceof net.minecraft.server.level.ServerLevel sl
                    ? com.riverfishing.fishing.MarketData.get(sl).price(sl, path, emeralds) : emeralds;
            return new MerchantOffer(cost, new ItemStack(Items.EMERALD, pay), 12, xp, 0.05f);
        };
    }

    // ---- assembled tackle builders (§assembled-trades). No custom "(в сборе)" name — they read as
    // the plain gear, and the socketed parts already show in the tooltip. Consumables (bait, groundbait)
    // are left empty on purpose: the angler picks those. §assembled-only — the fisherman sells NO bare
    // blanks, so every rod that leaves the stall can be cast on the spot.

    /** Fills a rig's slots in layout order; trailing slots stay empty. Any missing item voids the rig. */
    private static ItemStack rig(RigType type, String... contents) {
        String rigId = "rig_" + type.jsonKey();
        Item rigItem = item(rigId);
        if (rigItem == null || rigItem == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(rigItem);
        NonNullList<ItemStack> c = NonNullList.withSize(RigLayout.rolesFor(type).length, ItemStack.EMPTY);
        for (int i = 0; i < contents.length && i < c.size(); i++) {
            if (contents[i] == null) continue;
            Item part = item(contents[i]);
            if (part == null || part == Items.AIR) return ItemStack.EMPTY;
            c.set(i, benchGrade(new ItemStack(part), contents[i]));
        }
        RigData.save(stack, c);
        return benchGrade(stack, rigId);
    }

    /**
     * §tackle-craft: shop tackle IS bench tackle. Anything the Tackle Station can tie gets the same
     * {@code TackleWeightG} stamp the station would write — without it a bought spinner reads as 0 g to
     * {@code RigData.lureTackleWeightG} and casts like nothing, while a station-tied one of the same
     * name has real mass. The maker's mark is left off on purpose: it is a raw, untranslated string
     * baked into the stack forever, and "no line" beats "an English line on a Russian client".
     * Built-in rod rigs (float/predator/winter) aren't bench forms, so they keep their type mass.
     */
    private static ItemStack benchGrade(ItemStack stack, String id) {
        TackleForm form = TackleForm.byItemId(id);
        if (form != null) {
            TackleForm.stamp(stack, form, form.stockWeight(), null, form.defaultLinkCm, 1);
        }
        return stack;
    }

    /** Sells a bench-grade lure/rig at its stock weight (§tackle-craft). */
    private static VillagerTrades.ItemListing sellTackleOf(TackleForm form, int emeraldCost, int count, int xp) {
        Item i = form.item();
        if (i == null || i == Items.AIR) return null;
        return (trader, random) -> {
            ItemStack out = benchGrade(new ItemStack(i, count), form.id);
            return new MerchantOffer(
                    new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, emeraldCost), out, 12, xp, 0.05f);
        };
    }

    /** Bamboo's built-in light float rig, loaded with a float and a №10 hook — cast-ready for the beginner. */
    private static ItemStack assembledFloatRig() {
        return rig(RigType.FLOAT_LIGHT, "float", "hook_10");        // FLOAT, HOOK, (bait)
    }

    /** Predator rig with a steel leader and a spinner in the lure slot. */
    private static ItemStack assembledPredatorRig() {
        return rig(RigType.PREDATOR, "leader", "spinner");          // LEADER, LURE
    }

    /** Feeder rig with a №8 hook — groundbait/bait are up to the angler. */
    private static ItemStack assembledFeederRig() {
        return rig(RigType.FEEDER, "hook_8");                       // HOOK, (bait), (groundbait)
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

    /** Reel-less ice rod: the mormyshka IS the tackle, so it ships fitted or the rod is useless. */
    private static ItemStack assembledWinterRod() {
        return assembledRod("winter_rod", null, "line_mono_014", rig(RigType.WINTER, "mormyshka"));
    }

    private static ItemStack assembledCarpRod() {
        return assembledRod("carp_rod", "reel_6000", "line_braid_030", rig(RigType.CARP, "hook_4"));
    }

    private static ItemStack assembledBottomRod() {
        return assembledRod("bottom_rod", "reel_7000", "line_braid_030",
                rig(RigType.CATFISH, "leader", "hook_2"));           // LEADER, HOOK, (bait)
    }

    // Saltwater: reel size must sit inside the blank's band and the line inside the reel's spool limit
    // (§tackle-compat, maxLineDiameter = 0.15 + size/1000 * 0.05) — otherwise the stall would sell gear
    // the assembly GUI itself refuses to socket.
    private static ItemStack assembledSeaSpinRod() {
        return assembledRod("sea_spin_rod", "reel_8000", "line_braid_040", assembledPredatorRig());
    }

    private static ItemStack assembledSurfRod() {
        return assembledRod("surf_rod", "reel_8000", "line_mono_050", rig(RigType.GROUND, "hook_2"));
    }

    private static ItemStack assembledBoatRod() {
        return assembledRod("boat_rod", "reel_10000", "line_braid_050",
                rig(RigType.CATFISH, "leader", "hook_1"));
    }

    private static ItemStack assembledTrollingRod() {
        return assembledRod("trolling_rod", "reel_12000", "line_braid_060",
                rig(RigType.PREDATOR, "leader_titanium", "wobbler"));
    }
}
