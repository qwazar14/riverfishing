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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.server.level.ServerLevel;
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
    /**
     * §market-live: what each species is bought at before the market moves it — {emeralds, xp}. Both,
     * because §order-slot has to be able to mint the very same offer the pool would have minted.
     */
    private static final java.util.Map<String, int[]> BASE_PRICE = new java.util.HashMap<>();

    /** The lowest fisherman level that buys this species, or 0 if none does. */
    public static int buyTier(String species) {
        return BUY_TIER.getOrDefault(species, 0);
    }

    /** Prime-grade threshold (§prime-fish): the buyer only takes the top of the species' size range. */
    public static final double PRIME_FRACTION = 0.7;

    /** How many rotating fish-buy listings each tier gets (§trade-pool). Two, so a fisherman reliably
     *  buys SOMETHING without the pool turning back into a fish market. */
    // §trade-pool 0.7.0: four, up from two. A tier registers between eight and twenty-six species and
    // used to put TWO of them on any one counter, frozen there for that villager's life — so an angler
    // with seventy-nine species in the journal could sell five of them.
    private static final int FISH_LISTINGS_PER_TIER = 4;

    /** Vanilla's own per-level offer count — a private constant in {@code Villager}, mirrored here. */
    private static final int VANILLA_TRADES_PER_LEVEL = 2;

    /** §trade-pool: a fishing shop needs counter space for bait, line, a rod AND a fish buy, so our
     *  fisherman gets one more offer per level than vanilla hands out. Raised by the mixin. */
    // ...and the counter has to have room for them: two more than vanilla's two, not one.
    public static final int TRADES_PER_LEVEL = 4;

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
        // §groundbait-one-jar: the second groundbait trade is BALLAST now, not a second groundbait. It is
        // the dial the whole mixing system turns on — the way you make a blend leaner and bulkier without
        // making it worse — and it was the one pantry item with no shop leg at all.
        sell(t, 2, "groundbait_soil", 1, 8, 3);
        sell(t, 2, "bait_trap", 3, 1, 4);           // the trap slowly farms livebait (§livebait)
        sell(t, 2, "minecraft:oak_boat", 4, 1, 5);  // §vanilla-stock: trolling needs a boat under you
        // §internal-rig: the float rig lives INSIDE the float rods (JournalScreen.isInternalRig) and is
        // never tied on its own, so the stall stopped selling it — it was a component with a price tag.
        sellStack(t, 2, 9, ModVillagers::assembledBambooRod, 8);

        // Level 3 — Journeyman: lures (also craftable now, §lure-recipes) + a ready spinning setup.
        // §tackle-craft: shop lures ship bench-stamped, so their grams count toward the cast.
        oneOf(t, 3, sellTackleOf(TackleForm.SPINNER, 3, 1, 8), sellTackleOf(TackleForm.SPOON, 4, 1, 8),
                sellTackleOf(TackleForm.SILICONE, 2, 2, 6));
        oneOf(t, 3, sellOf("line_braid_016", 5, 1, 10), sellOf("line_fluoro_020", 5, 1, 10));
        sell(t, 3, "leader_fluoro", 3, 2, 6);
        sell(t, 3, "fish_finder", 14, 1, 12);        // §QoL: read the swim before you cast
        // §keepnet + §tackle-box: somewhere to put the catch, and somewhere to put the tackle. Both were
        // asked for by players, and neither is worth a crafting detour when you are already at the stall.
        sell(t, 3, "keepnet_small", 5, 1, 6);
        // §kit-price: each kit is priced at ~80% of what its contents cost piece by piece at this same
        // stall, plus the box. A kit that cost MORE than the parts is a kit nobody buys.
        oneOf(t, 3, sellStackOf(7, ModVillagers::floatKit, 10), sellStackOf(18, ModVillagers::pikeKit, 16));
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
        oneOf(t, 4, sellOf("keepnet_medium", 9, 1, 10), sellOf("keepnet_large", 14, 1, 14));
        sellStack(t, 4, 21, ModVillagers::carpKit, 18);
        sellStack(t, 4, 5, ModVillagers::houseBlend, 6);   // §house-blend: the stall's own mix
        // §vanilla-stock + §tackle-craft: the saltwater reels are gated on ocean drops. Selling the
        // INPUTS keeps the gate (you still pay for it) without making it hinge on guardian RNG.
        sell(t, 4, "minecraft:prismarine_shard", 5, 4, 10);
        sellStack(t, 4, 18, ModVillagers::assembledFeederRod, 18);
        sellStack(t, 4, 14, ModVillagers::assembledWinterRod, 14);

        // Level 5 — Master: the trade-only prestige gear (§progression).
        sell(t, 5, "digital_alarm", 10, 1, 25);
        sell(t, 5, "keepnet_huge", 20, 1, 24);
        sellStack(t, 5, 51, ModVillagers::seaKit, 34);
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

        // §giants-and-minnows (0.8.0): the giants pay like the taimen tier they sit beside,
        // the minnows like the bleak they swim with.
        buyPrime(fish, 3, "kutum", 5, 9);
        buyPrime(fish, 4, "naked_carp", 8, 14);

        // §giants-and-minnows (0.8.0): the giants pay like the taimen tier they sit beside,
        // the minnows like the bleak they swim with.
        buyPrime(fish, 5, "arapaima", 26, 38);
        buyPrime(fish, 5, "beluga", 30, 44);
        buyPrime(fish, 5, "piraiba", 22, 34);
        buyPrime(fish, 5, "goliath_grouper", 24, 36);
        buyPrime(fish, 5, "bull_shark", 24, 36);
        buyPrime(fish, 5, "frilled_shark", 26, 38);
        buyPrime(fish, 5, "golden_dorado", 12, 22);
        buyPrime(fish, 2, "golden_crucian", 2, 3);
        buyPrime(fish, 1, "gorchak", 1, 1);
        buyPrime(fish, 1, "verkhovka", 1, 1);
        buyPrime(fish, 1, "sculpin", 1, 2);
        buyPrime(fish, 1, "tubenose_goby", 1, 1);

        // §florida-nine: these nine shipped catchable in 0.7.0 and were never given a buyer here —
        // you could land a tarpon and have nowhere on earth to sell it. Levels, prices and xp are
        // copied from the 26.x datapack rather than invented, so the two branches cannot disagree
        // about what a fish is worth.
        buyPrime(fish, 4, "bluefish", 6, 12);
        buyPrime(fish, 4, "bullseye_snakehead", 6, 11);
        buyPrime(fish, 5, "jack_crevalle", 12, 22);
        buyPrime(fish, 3, "mayan_cichlid", 2, 3);
        buyPrime(fish, 3, "oscar", 2, 4);
        buyPrime(fish, 4, "peacock_bass", 7, 13);
        buyPrime(fish, 5, "snook", 11, 21);
        buyPrime(fish, 5, "striped_bass", 12, 23);
        buyPrime(fish, 5, "tarpon", 20, 32);

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

    /**
     * §market-live: re-price this stall's fish buys from the base, every time the counter opens.
     *
     * <p>The base is read from where the trade was DEFINED, never from the offer's current count —
     * feeding the output back in would multiply ×2.5 on top of ×2.5 every time the player looked.
     */
    public static void repriceFishBuys(Villager villager, net.minecraft.world.entity.player.Player player) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (villager.getVillagerData().getProfession() != FISHERMAN.get()) return;
        MerchantOffers offers = villager.getOffers();
        orderSlot(villager, level, offers);
        var market = com.riverfishing.fishing.MarketData.get(level);
        for (MerchantOffer offer : offers) {
            if (!(offer.getCostA().getItem() instanceof FishItem fish)) continue;
            String species = fish.species().getPath();
            int[] spec = BASE_PRICE.get(species);
            if (spec == null) continue;
            int base = spec[0];
            ItemStack pay = offer.getResult();
            // A stack is the ceiling: blue marlin at ×2.5 is 70 emeralds, which will not fit in a slot.
            pay.setCount(Math.min(pay.getMaxStackSize(), market.price(level, species, base)));
        }
        sendOrder(level, offers, player);
    }

    /**
     * §order-panel: tell this player what today's order is, and what THIS counter will pay for it.
     *
     * <p>The pay is read off the finished offer rather than recomputed, so the sign and the row beneath
     * it cannot disagree — they are the same number, read once. Nothing is sent when the stall does not
     * buy today's species: the panel would be advertising a trade the player cannot make here, and that
     * silence doubles as the "not our fisherman" signal, because no other profession reaches this far.
     *
     * <p>It goes out during startTrading HEAD, which is before openTradingScreen builds the screen
     * packet on the same connection — so the client always has the order before it has a window.
     */
    private static void sendOrder(ServerLevel level, MerchantOffers offers,
                                  net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        String order = com.riverfishing.fishing.MarketData.orderOfTheDay(level);
        if (order.isEmpty()) return;
        int base = BASE_PRICE.getOrDefault(order, new int[]{0, 0})[0];
        if (base <= 0) return;
        // The pay comes off the finished offer when this counter has one, so the sign and the row under
        // it are the same number read once. When it has none the stall is too junior: say so with the
        // rank instead of saying nothing.
        for (MerchantOffer offer : offers) {
            if (!(offer.getCostA().getItem() instanceof FishItem fish)) continue;
            if (!fish.species().getPath().equals(order)) continue;
            com.riverfishing.network.ModNetwork.toPlayer(sp,
                    new com.riverfishing.network.OrderPacket(fish.species(),
                            offer.getResult().getCount(), base, buyTier(order)));
            return;
        }
        com.riverfishing.network.ModNetwork.toPlayer(sp,
                new com.riverfishing.network.OrderPacket(RiverFishing.id(order), 0, base, buyTier(order)));
    }

    /**
     * §order-slot: every species some fisherman buys, sorted, so the daily rotation cannot name a fish
     * nobody takes. The trade table IS the answer, so there is no second list to drift.
     */
    public static java.util.List<String> buyableSpecies() {
        return BUY_TIER.keySet().stream().sorted().toList();
    }

    /**
     * §order-slot: one seat on this counter always shows today's order.
     *
     * <p>It REPLACES rather than appends, and that is the whole design. An extra offer would have to be
     * found and reclaimed the next day, and there is nothing on a vanilla MerchantOffer to mark it with
     * that survives a save — so every scheme for finding it again came down to counting the counter,
     * which breaks the moment a villager gains a level and vanilla appends behind our seat. Overwriting
     * a seat in place has none of that: the counter never changes size, so there is nothing to count,
     * nothing to mark, and nothing to get wrong after a restart.
     *
     * <p>The seat is the last fish buy on the counter. On the first day that costs one drawn species,
     * and from then on it is the same seat every day — which is what makes it a permanent slot that
     * changes rather than a new trade appearing and disappearing.
     *
     * <p>Nothing happens when the stall is too junior to buy today's species, or when it already buys
     * it: in the second case the player can sell it anyway, and taking the seat would only cost them a
     * second species for nothing.
     */
    private static void orderSlot(Villager villager, ServerLevel level, MerchantOffers offers) {
        String order = com.riverfishing.fishing.MarketData.orderOfTheDay(level);
        int[] spec = BASE_PRICE.get(order);
        if (spec == null || BUY_TIER.getOrDefault(order, 0) > villager.getVillagerData().getLevel()) return;
        int seat = -1;
        for (int i = 0; i < offers.size(); i++) {
            if (!(offers.get(i).getCostA().getItem() instanceof FishItem fish)) continue;
            if (fish.species().getPath().equals(order)) return;   // already on the counter
            seat = i;
        }
        if (seat < 0) return;
        MerchantOffer offer = buyPrimeOf(order, spec[0], spec[1]).getOffer(villager, villager.getRandom());
        if (offer == null) return;
        // The bottom row, every day. Overwriting a seat in the middle of the counter is invisible: the
        // price moves and nothing tells the player WHICH row the mod is talking about. Taking the seat
        // and then moving it to the end costs nothing — the counter is the same length either way — and
        // gives the slot a fixed place to be looked for. It also keeps finding itself: the last fish buy
        // on the counter tomorrow is this one.
        offers.remove(seat);
        offers.add(offer);
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
        // §1.20.1: MerchantOffer costs are plain ItemStacks here — there is no ItemCost record yet.
        return (trader, random) -> new MerchantOffer(
                new ItemStack(Items.EMERALD, emeraldCost), result.copy(), 12, xp, 0.05f);
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
            return new MerchantOffer(new ItemStack(Items.EMERALD, emeraldCost), out, 8, xp, 0.05f);
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
        // §market-live: the same call that knows the tier knows the price. Two facts, one owner.
        BASE_PRICE.put(path, new int[]{emeralds, xp});
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
            // §prime-fish (1.20.1): the buy-cost carries the Grade/MinW NBT gradePrime writes at the
            // catch; vanilla's subset tag-matching does the gating and shows the "accepts from N" legend.
            ItemStack cost = new ItemStack(i);
            cost.getOrCreateTag().putString(FishItem.TAG_GRADE, FishItem.GRADE_PRIME);
            cost.getOrCreateTag().putInt(FishItem.TAG_MIN_WEIGHT, threshold);
            // §market-live: minted at BASE. This lambda runs once per villager-level and the count it
            // writes is then saved, so a price decided here would be frozen on whatever day the stall
            // happened to level up. VillagerRepriceMixin owns the price from the first screen open.
            return new MerchantOffer(cost, new ItemStack(Items.EMERALD, emeralds), 12, xp, 0.05f);
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
            // §lure-color: the stall paints what it ties. A rack of identical silver blades is not a tackle
            // shop, and the colour is a real parameter here — it feeds the bite engine's condition fit —
            // so a shop lure arrives with an opinion about the water rather than as a blank.
            if (form.dyeable) out = paint(out, random);
            return new MerchantOffer(new ItemStack(Items.EMERALD, emeraldCost), out, 12, xp, 0.05f);
        };
    }

    /**
     * §tackle-box kits (0.7.0): a named, dyed box that arrives with the tackle for one kind of fishing
     * already in it. Asked for as "готовые наборы... лут внутри — логичен (карповый набор, на щуку и тп)",
     * and it is the best thing the fisherman sells: a box that IS the answer to "what do I need for pike".
     *
     * <p>Everything inside is bench-graded like the rest of his stock, so the grams count toward a cast the
     * moment you open it. A kit that had to be re-tied to be usable would be a souvenir.
     */
    private record Part(String id, int count, int minG, int maxG, boolean dyed) {}

    private static Part p(String id, int count) {
        return new Part(id, count, 0, 0, false);
    }

    /** Bench tackle with a weight rolled inside {@code minG..maxG} and a random dye mix. */
    private static Part p(String id, int count, int minG, int maxG) {
        return new Part(id, count, minG, maxG, true);
    }

    /** A rig: rolled weight, no dye — rigs are hardware, not something you paint. */
    private static Part rig(String id, int minG, int maxG) {
        return new Part(id, 1, minG, maxG, false);
    }

    private static ItemStack kit(String boxId, String nameKey, int colour, Part... contents) {
        Item boxItem = item(boxId);
        if (boxItem == null || boxItem == Items.AIR) return ItemStack.EMPTY;
        RandomSource rng = RandomSource.create();
        ItemStack box = new ItemStack(boxItem);
        NonNullList<ItemStack> inside =
                NonNullList.withSize(com.riverfishing.item.TackleBoxData.tierOf(box).slots(), ItemStack.EMPTY);
        int slot = 0;
        for (Part part : contents) {
            Item item = item(part.id());
            if (item == null || item == Items.AIR) return ItemStack.EMPTY;   // a typo voids the kit, loudly
            ItemStack stack = new ItemStack(item, part.count());
            TackleForm form = TackleForm.byItemId(part.id());
            if (form != null) {
                // §tackle-craft: stamped like everything else the stall sells, so the grams count toward a
                // cast the moment the box is opened. The weight is ROLLED rather than stock, inside the
                // form's own ladder — two pike kits are not the same pike kit.
                int grams = part.maxG() > 0 ? rollWeight(form, rng, part.minG(), part.maxG())
                        : form.stockWeight();
                TackleForm.stamp(stack, form, grams, null, form.defaultLinkCm, 1);
            }
            if (part.dyed()) stack = paint(stack, rng);
            if (!com.riverfishing.item.TackleBoxData.isTackle(stack)) return ItemStack.EMPTY;
            if (slot >= inside.size()) break;
            inside.set(slot++, stack);
        }
        com.riverfishing.item.TackleBoxData.save(box, inside);
        box.setHoverName(net.minecraft.network.chat.Component.translatable(nameKey)
                .withStyle(net.minecraft.ChatFormatting.WHITE));
        com.riverfishing.item.DyeUtil.setColor(box, colour);
        return box;
    }

    /**
     * A weight inside the requested window, clamped to what the BENCH can actually tie. The window is the
     * kit's intent ("3–35 g of lure"); the ladder is the form's reality — a spinner tops out at 14 g, and a
     * kit holding one heavier than anything craftable would be a lie about the crafting system.
     */
    private static int rollWeight(TackleForm form, RandomSource rng, int minG, int maxG) {
        int floor = form.weights[0];
        int ceil = form.weights[form.weights.length - 1];
        int lo = Math.max(minG, floor);
        int hi = Math.min(maxG, ceil);
        if (hi <= lo) return Mth.clamp(lo, floor, ceil);
        return lo + rng.nextInt(hi - lo + 1);
    }

    /** One to three random dyes, mixed the leather-armour way — no two shop lures look alike. */
    private static ItemStack paint(ItemStack stack, RandomSource rng) {
        java.util.List<net.minecraft.world.item.DyeItem> dyes = new java.util.ArrayList<>();
        net.minecraft.world.item.DyeColor[] all = net.minecraft.world.item.DyeColor.values();
        for (int i = 0, n = 1 + rng.nextInt(3); i < n; i++) {
            dyes.add(net.minecraft.world.item.DyeItem.byColor(all[rng.nextInt(all.length)]));
        }
            stack = com.riverfishing.item.DyeUtil.applyDyes(stack, dyes);
        return stack;
    }

    /** Float kit: the everyday small-fish box — floats, fine hooks, worms and maggots. */
    private static ItemStack floatKit() {
        return kit("tackle_box_small", "kit.riverfishing.float", 0xC8D8E8,
                p("float", 2), p("hook_10", 4), p("hook_12", 4), p("worm", 8), p("maggot", 8));
    }

    /** Pike kit: wire and hardware. Every lure comes out a different weight and a different colour. */
    private static ItemStack pikeKit() {
        return kit("tackle_box_medium", "kit.riverfishing.pike", 0x4A7A3A,
                p("leader", 2), p("spinner", 1, 3, 35), p("spoon", 1, 3, 35), p("wobbler", 1, 3, 35));
    }

    /** Carp kit: a session box — bait by the handful, big hooks and a flat feeder to put it out on. */
    private static ItemStack carpKit() {
        return kit("tackle_box_medium", "kit.riverfishing.carp", 0xB0863C,
                p("boilie", 16), p("hook_6", 4), p("hook_8", 4), p("corn", 16),
                p("line_mono_030", 1), rig("rig_flat_feeder", 40, 60));
    }

    /**
     * §house-blend: the stall's own groundbait, already mixed.
     *
     * <p>Base, barley, chopped worm and a spoon of maggot — the plain river blend everybody starts from,
     * and it is deliberately GOOD RATHER THAN RIGHT. It fishes well for the silver fish it was built for
     * and merely adequately for anything else, so it is worth the emeralds on the day you buy it and worth
     * beating a week later. That is the whole promise of the mixing system in one trade: a ready recipe is
     * a floor, not a ceiling.
     */
    private static ItemStack houseBlend() {
        com.riverfishing.groundbait.GroundbaitMix mix = com.riverfishing.groundbait.GroundbaitMix.of(
                java.util.List.of(new com.riverfishing.groundbait.GroundbaitMix.Part("groundbait_powder", 3),
                        new com.riverfishing.groundbait.GroundbaitMix.Part("pearl_barley", 2),
                        new com.riverfishing.groundbait.GroundbaitMix.Part("worm", 2),
                        new com.riverfishing.groundbait.GroundbaitMix.Part("maggot", 1)));
        ItemStack out = new ItemStack(ModItems.GROUNDBAIT.get(), 8);
        com.riverfishing.groundbait.GroundbaitNbt.write(out, mix);
        return out;
    }

    /** Sea kit: the saltwater end, sold late because the rest of that ladder is late (§progression). */
    private static ItemStack seaKit() {
        return kit("tackle_box_large", "kit.riverfishing.sea", 0x2E5E8A,
                p("leader_titanium", 2), p("octopus_jig", 1, 100, 200), p("giant_spoon", 1, 100, 200),
                rig("rig_ground", 100, 200), p("hook_2", 3), p("hook_4", 3),
                p("line_braid_040", 1), p("livebait", 4));
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
