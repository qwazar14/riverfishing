package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import com.riverfishing.component.LineType;
import com.riverfishing.component.RigType;
import com.riverfishing.component.RodType;
import com.riverfishing.item.AlarmItem;
import com.riverfishing.item.AlarmType;
import com.riverfishing.item.BaitItem;
import com.riverfishing.item.FilletKnifeItem;
import com.riverfishing.item.FishItem;
import com.riverfishing.item.GroundbaitItem;
import com.riverfishing.item.HookItem;
import com.riverfishing.item.IngredientItem;
import com.riverfishing.item.JournalItem;
import com.riverfishing.item.LeaderItem;
import com.riverfishing.item.LineItem;
import com.riverfishing.item.ReelItem;
import com.riverfishing.item.RigItem;
import com.riverfishing.item.RodItem;
import com.riverfishing.item.WhetstoneItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** All items the mod registers. The {@link #ALL} list feeds the creative tab and asset generation. */
public final class ModItems {
    public static final DeferredRegister<Item> REGISTER =
            DeferredRegister.create(RiverFishing.MODID, Registries.ITEM);

    /** Bind all queued items to the active platform's registry (ÃÂ§multiloader) Ã¢ÂÂ called from init. */
    public static void init() {
        REGISTER.register();
    }

    /** Registration order = creative-tab order. */
    public static final List<RegistrySupplier<Item>> ALL = new ArrayList<>();

    /** §hook-pick: the hooks in {@link com.riverfishing.tackle.TackleForm#HOOK_SIZES} order — the bench
     *  picks one by index rather than being handed the item. */
    public static final List<RegistrySupplier<Item>> HOOKS = new ArrayList<>();

    // ---- Rods ----
    public static final List<RegistrySupplier<Item>> RODS = new ArrayList<>();
    // ---- Caught fish: one item + texture per species (Module 8; ÃÂ§ecology adds habitat-bound species) ----
    public static final String[] FISH_SPECIES = {
            // §carp-kin (0.8.1): two more of the family — a Caspian roach that grew up, and a carp
            // that forgot to put its scales on.
            "kutum", "naked_carp",
            // §giants-and-minnows (0.8.0): the top of the ladder and the bottom of it — six fish that outgrow
            // every rod in the shop, and five you can catch with a stick and a maggot.
            "arapaima", "beluga", "piraiba", "goliath_grouper",
            "bull_shark", "frilled_shark", "golden_dorado", "golden_crucian",
            "gorchak", "verkhovka", "sculpin", "tubenose_goby",
            // §florida-nine (0.7.0): the US/Florida wave, from a player who also
            // found the §session-guard bug that 0.6.1 fixes.
            "peacock_bass", "bullseye_snakehead", "mayan_cichlid", "oscar",
            "striped_bass", "bluefish", "jack_crevalle", "tarpon", "snook",
            "bream", "crucian_carp", "roach", "rudd", "white_bream",
            "carp", "catfish", "perch", "pike", "zander",
            "gudgeon", "ruffe", "bleak", "ide", "chub", "asp",
            "tench", "burbot", "eel", "grayling", "trout", "sterlet",
            // ÃÂ§carp-update: the wild sazan + the mirror strain, plus the koi collectibles.
            "wild_carp", "mirror_carp", "grass_carp",
            "carp_koi_kohaku", "carp_koi_tancho_sanke", "carp_koi_showa_sanke",
            "carp_koi_asagi", "carp_koi_bekko",
            // ÃÂ§america-pack (0.4.0): bluegill/bass/rainbow/channel cat Ã¢ÂÂ the community-requested US four.
            "bluegill", "largemouth_bass", "rainbow_trout", "channel_catfish",
            // Â§ru-fish (0.4.0): ÃÂÃÂ¾ÃÂ»ÃÂÃÂÃÂ¾ÃÂ»ÃÂ¾ÃÂ±ÃÂ¸ÃÂº / ÃÂÃÂµÃÂÃÂ¾ÃÂ½ÃÂ / ÃÂÃÂ¸ÃÂ½ÃÂµÃÂ â the RU trio.
            "silver_carp", "sabrefish", "blue_bream",
            // ocean (0.5.0): the coastal + shelf wave.
            "mackerel", "herring", "garfish", "seabass", "flounder",
            "cod", "saithe", "conger", "ray",
            // ocean (0.5.0): the pelagic four.
            "mahi", "wahoo", "yellowfin_tuna", "barracuda",
            // ocean (0.5.0): the billfish/shark trophies.
            "blue_marlin", "sailfish", "swordfish", "mako",
            // north-wave (0.5.0): taiga rivers, the salmon run and the two bottom giants.
            "rotan", "nase", "vimba", "smelt", "whitefish", "char",
            "lenok", "taimen", "salmon", "pink_salmon", "sturgeon", "halibut",
            // §river-four (0.6.0): the community-requested RU river wave — dace, Volga zander,
            // white-eye bream and the round goby.
            "common_dace", "volga_zander", "white_eye_bream", "round_goby"
    };
    public static final Map<Identifier, RegistrySupplier<Item>> FISH_ITEMS = new HashMap<>();
    /** §groundbait-one-jar: the one groundbait item there is. What it DOES lives in its NBT. */
    public static final RegistrySupplier<Item> GROUNDBAIT;
    // ---- Baits referenced by event drops ----
    public static final RegistrySupplier<Item> WORM;
    public static final RegistrySupplier<Item> CHICKEN_LIVER;
    // ---- In-rig components (Module 4): referenced by slot validation ----
    /**
     * §farm-feed: the crop seeds, held onto because they have to be registered as compostable once
     * they exist. Vanilla wheat, beetroot, melon and pumpkin seeds all sit at 0.30 and these are the
     * same kind of thing, so they sit there too.
     */
    public static final RegistrySupplier<Item> CORN_SEEDS, PEA_SEEDS, BARLEY_SEEDS;

    public static final RegistrySupplier<Item> LEADER;
    public static final RegistrySupplier<Item> LEADER_FLUORO;
    public static final RegistrySupplier<Item> LEADER_TITANIUM;
    public static final RegistrySupplier<Item> FLOAT;
    // ---- Bite alarms (Module 3) ----
    public static final RegistrySupplier<Item> BELL_ALARM;
    public static final RegistrySupplier<Item> DIGITAL_ALARM;
    // ---- Processing (ÃÂ§11) ----
    public static final RegistrySupplier<Item> FILLET_KNIFE;
    /** §one-fillet: what the knife cuts. Bait, groundbait component and food, all one item. */
    public static final RegistrySupplier<Item> FISH_STRIP;
    public static final RegistrySupplier<Item> GROUNDBAIT_SOIL;
    public static final RegistrySupplier<Item> COOKED_FILLET;
    // ---- Maintenance (ÃÂ§3.8) ----
    public static final RegistrySupplier<Item> WHETSTONE;

    private ModItems() {}

    /** §contracts-b1: the paper taken off a fisherman's board. */
    public static final RegistrySupplier<Item> CONTRACT = reg("contract",
            () -> new com.riverfishing.item.ContractItem(props("contract")));

    private static RegistrySupplier<Item> reg(String name, Supplier<Item> supplier) {
        RegistrySupplier<Item> obj = REGISTER.register(name, supplier);
        ALL.add(obj);
        return obj;
    }

    // §26.1: every Item.Properties must carry its registry id (the Item ctor throws without it).
    private static Item.Properties props(String name) {
        return new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.ITEM, RiverFishing.id(name)));
    }

    /** §26.1: anvil repair moved into Item.Properties.repairable — the priciest recipe ingredient. */
    private static Item rodRepairItem(RodType type) {
        String key = type.jsonKey();
        if ("stick".equals(key)) return net.minecraft.world.item.Items.STICK;
        if ("bamboo".equals(key)) return net.minecraft.world.item.Items.BAMBOO;
        if ("feeder".equals(key) || "bottom".equals(key)) return net.minecraft.world.item.Items.GOLD_INGOT;
        // §tackle-craft: the carp blank and the whole saltwater tier are diamond-built. Their prismarine /
        // nautilus tips are the TIER marker in the recipe, not repair stock you can farm.
        if ("carp".equals(key) || "sea_spin".equals(key) || "surf".equals(key)
                || "boat".equals(key) || "trolling".equals(key)) {
            return net.minecraft.world.item.Items.DIAMOND;
        }
        return net.minecraft.world.item.Items.IRON_INGOT; // pole / ultralight / spinning / winter
    }

    /** Rod blank durability by tier (ÃÂ§rod-durability). Plain if-chain: no synthetic switch classes. */
    private static int rodDurability(RodType type) {
        String key = type.jsonKey();
        if ("stick".equals(key)) return 32;
        if ("bamboo".equals(key)) return 64;
        if ("winter".equals(key)) return 96;       // short, reel-less, and ice fish are small
        if ("pole".equals(key)) return 128;
        if ("ultralight".equals(key)) return 144;
        if ("spinning".equals(key)) return 192;
        if ("feeder".equals(key)) return 224;
        if ("bottom".equals(key)) return 256;
        if ("carp".equals(key)) return 320;
        // §sea-durability: these five used to fall through to the 128 default, so a surf rod —
        // diamond-built, prismarine-tipped, rated to a 250 g cast and fish an order of magnitude
        // heavier — wore out faster than a gold-guided feeder. Saltwater now sits above the
        // freshwater top and climbs with the blank's test window.
        if ("sea_spin".equals(key)) return 320;
        if ("surf".equals(key)) return 384;
        if ("boat".equals(key)) return 448;
        if ("trolling".equals(key)) return 512;
        return 128; // only reached by a blank added without a durability decision
    }

    static {
        // ----- Rods (each RodType is its own item; components live in NBT). Blanks wear out and are
        // anvil-repaired with the priciest ingredient of their recipe (ÃÂ§rod-durability). -----
        for (RodType type : RodType.values()) {
            RegistrySupplier<Item> rod = reg(type.jsonKey() + "_rod",
                    () -> new RodItem(type, props(type.jsonKey() + "_rod").durability(rodDurability(type))
                            .repairable(rodRepairItem(type))));
            RODS.add(rod);
        }

        // ----- Reels -----
        for (int size : new int[]{1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10000, 12000, 14000}) {
            final int s = size;
            reg("reel_" + size, () -> new ReelItem(s, props("reel_" + size)));
        }

        // ----- Lines (ÃÂ§line-update): mono = all-rounder, braid = thin & strong, fluoro = clear/finesse.
        // Thick fluoro (0.40/0.50) dropped Ã¢ÂÂ impractical in reality; thin mono/fluoro + heavy braid added. -----
        registerLines(LineType.MONO, new double[]{0.10, 0.14, 0.18, 0.25, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80});
        // Braid tops out at 0.30 Ã¢ÂÂ the catfish line (ÃÂ§strain-recompute: 0.30 braid Ã¢ÂÂ 27 kg, enough to
        // duel the 40 kg monster catfish with a 7000 reel's drag on top).
        registerLines(LineType.BRAID, new double[]{0.16, 0.20, 0.25, 0.30, 0.40, 0.50, 0.60});
        registerLines(LineType.FLUORO, new double[]{0.14, 0.16, 0.20, 0.25, 0.30, 0.40});

        // ----- Rigs -----
        for (RigType type : RigType.values()) {
            reg("rig_" + type.jsonKey(), () -> new RigItem(type, props("rig_" + type.jsonKey())));
        }

        // ----- In-rig components (Module 4) -----
        LEADER = reg("leader", () -> new LeaderItem(1.0, 0.1, props("leader")));            // steel: bomb-proof, but very visible (spooks bites)
        LEADER_FLUORO = reg("leader_fluoro", () -> new LeaderItem(0.85, 0.9, props("leader_fluoro"))); // fluorocarbon: near-invisible, strong (rarely bitten through)
        LEADER_TITANIUM = reg("leader_titanium", () -> new LeaderItem(1.0, 0.6, props("leader_titanium"))); // titanium: bomb-proof AND fairly stealthy (trade-only)
        FLOAT = reg("float", () -> new Item(props("float")));

        // ----- Hooks (angling sizes; bigger number = smaller hook) -----
        // The size list itself lives in TackleForm, because the bench prices tackle off it (§hook-pick).
        // Keeping the literal here too is how you end up with a size the bench can charge for and not
        // register, or register and not be able to tie.
        for (int size : com.riverfishing.tackle.TackleForm.HOOK_SIZES) {
            final int s = size;
            HOOKS.add(reg("hook_" + size, () -> new HookItem(s, props("hook_" + size))));
        }

        // ----- Natural baits -----
        // §sea-tackle (0.5.0): cut fish strip — the universal saltwater hook bait.
        // Held here because the filleting knife cuts them (§one-cutter) and needs the item.
        // §one-fillet: cut fish is ONE item. A piece off a caught fish goes on a hook, into a groundbait
        // mix, or in a furnace — that is three USES, and it was three items' worth of confusion for no
        // reason: the same knife on the same fish made "fish strip" standing up and "raw fillet"
        // crouching, and nothing but the yield told them apart.
        //
        // The id stays `fish_strip` because 24 fish profiles, the groundbait pantry and its diet mapping
        // all point at it, and a rename that misses one of those fails SILENTLY — the fish simply stops
        // wanting the bait. The NAME is Raw Fish Fillet, which is what pairs with Cooked Fish Fillet.
        FISH_STRIP = reg("fish_strip", () -> new BaitItem("fish_strip", false, props("fish_strip").food(
                new FoodProperties.Builder().nutrition(2).saturationModifier(0.2f).build())));
        // §groundbait-mix: inert ballast. NOT a bait — it goes in the bowl, not on a hook, and it is
        // the only thing in the pantry that feeds nothing at all.
        GROUNDBAIT_SOIL = reg("groundbait_soil", () -> new IngredientItem("tooltip.riverfishing.groundbait_soil", props("groundbait_soil")));
        registerBait("maggot", false);
        WORM = registerBait("worm", false);
        registerBait("bloodworm", false);
        registerBait("corn", false);
        registerBait("pea", false);
        registerBait("pearl_barley", false);
        registerBait("dough", false);
        registerBait("bread", false);
        registerBait("boilie", false);
        registerBait("livebait", false);
        CHICKEN_LIVER = registerBait("chicken_liver", false);
        // Mormyshka / "Ice Jig" (ÃÂ§ice-fishing): a tiny weighted winter JIG Ã¢ÂÂ artificial for gate purposes, but
        // SlotRole.BAIT admits it (fished tipped with a grub in the ice rig). Its tooltip is the ice-rod
        // descriptor, not the generic "artificial lure (predators only)" line.
        registerBait("mormyshka", true, "tooltip.riverfishing.bait_ice_jig");
        // ÃÂ§bait-crops: seeds for the plant baits Ã¢ÂÂ plantable on farmland (vanilla wheat-style seeds).
        CORN_SEEDS = reg("corn_seeds", () -> new net.minecraft.world.item.BlockItem(ModBlocks.CORN_CROP.get(), props("corn_seeds").useItemDescriptionPrefix()));
        PEA_SEEDS = reg("pea_seeds", () -> new net.minecraft.world.item.BlockItem(ModBlocks.PEA_CROP.get(), props("pea_seeds").useItemDescriptionPrefix()));
        BARLEY_SEEDS = reg("barley_seeds", () -> new net.minecraft.world.item.BlockItem(ModBlocks.BARLEY_CROP.get(), props("barley_seeds").useItemDescriptionPrefix()));

        // ----- Artificial baits (predators only) -----
        registerBait("spinner", true);
        registerBait("spoon", true);
        registerBait("wobbler", true);
        registerBait("silicone", true);
        // ÃÂ§more-lures (ÃÂ§8): topwater popper, mid-running crankbait, deep soft-jig, long-cast castmaster.
        // All artificial Ã¢ÂÂ dyeable/tintable + condition-colour like the others. (Placeholder textures for now.)
        registerBait("popper", true);
        registerBait("crankbait", true);
        registerBait("jig", true);
        registerBait("castmaster", true);
        // §trolling-lures (0.7.0): heavy skirted jig and big trolling spoon.
        registerBait("octopus_jig", true);
        registerBait("giant_spoon", true);

        // ----- Groundbait -----
        // §groundbait-one-jar: ONE. Grain, pellet and oil cake are gone, and so is the separate base —
        // four items that between them said nothing a player could act on, because the composition
        // stamped on the stack says all of it. This jar is that base: neutral, throwable, and the thing
        // every mix is built on top of.
        GROUNDBAIT = reg("groundbait_powder", () -> new GroundbaitItem(props("groundbait_powder")));

        // ----- Bite alarms (Module 3) -----
        BELL_ALARM = reg("bell_alarm", () -> new AlarmItem(AlarmType.BELL, props("bell_alarm")));
        DIGITAL_ALARM = reg("digital_alarm", () -> new AlarmItem(AlarmType.DIGITAL, props("digital_alarm")));

        // ----- Processing: knife + fillets (ÃÂ§11) -----
        FILLET_KNIFE = reg("fillet_knife", () -> new FilletKnifeItem(props("fillet_knife").durability(128)));
        COOKED_FILLET = reg("cooked_fillet", () -> new Item(props("cooked_fillet").food(
                new FoodProperties.Builder().nutrition(5).saturationModifier(0.6f).build())));

        // ----- Maintenance: whetstone (ÃÂ§3.8) -----
        WHETSTONE = reg("whetstone", () -> new WhetstoneItem(props("whetstone").durability(128)));

        // ----- Ice fishing (ÃÂ§ice-fishing): the auger drills a hole through an ice sheet -----
        reg("ice_auger", () -> new com.riverfishing.item.IceAugerItem(props("ice_auger").durability(64)));

        // ----- Records: fishing journal (ÃÂ§15) -----
        reg("fishing_journal", () -> new JournalItem(props("fishing_journal").stacksTo(1)));

        // ----- Water analysis (ÃÂ§QoL): player fish finder + admin probe -----
        reg("fish_finder", () -> new com.riverfishing.item.WaterProbeItem(false, props("fish_finder").stacksTo(1)));
        // §keepnet (0.7.0): the spatial catch box, four tiers of it. Each is its own item so the upgrade
        // path is a crafting recipe rather than a hidden NBT field.
        for (com.riverfishing.item.KeepnetTier t : com.riverfishing.item.KeepnetTier.values()) {
            reg(t.id(), () -> new com.riverfishing.item.KeepnetItem(t, props(t.id()).stacksTo(1)));
        }
        // §tackle-box (0.7.0): four sizes of box for line, hooks, rigs, lures and bait. Each is the
        // BlockItem of its own block, so one item both opens in the hand and stands on the bank.
        // §26.1: a BlockItem no longer inherits the block translation key — ask for the block prefix,
        // because the box is named under block.riverfishing.tackle_box_*.
        for (com.riverfishing.item.TackleBoxTier t : com.riverfishing.item.TackleBoxTier.values()) {
            reg(t.id(), () -> new com.riverfishing.item.TackleBoxItem(
                    t, ModBlocks.TACKLE_BOXES.get(t).get(),
                    props(t.id()).useBlockDescriptionPrefix().stacksTo(1)));
        }
        reg("hydro_probe", () -> new com.riverfishing.item.WaterProbeItem(true, props("hydro_probe").stacksTo(1)));
        // §cull (0.7.0): the electrofisher — a world-editing tool. No recipe anywhere: it is creative-only
        // by design, and it refuses to fire outside creative mode as well as being uncraftable.
        reg("electro_rod", () -> new com.riverfishing.item.ElectroRodItem(props("electro_rod").stacksTo(1)));

        // ----- Caught fish: a distinct item + texture per species (Module 8) -----
        for (String sp : FISH_SPECIES) {
            Identifier id = RiverFishing.id(sp);
            FISH_ITEMS.put(id, reg(sp, () -> new FishItem(id, props(sp).stacksTo(1))));
        }
    }

    /** The item representing a given fish species (Module 8). */
    public static Item fishItem(Identifier species) {
        RegistrySupplier<Item> obj = FISH_ITEMS.get(species);
        return (obj != null ? obj : FISH_ITEMS.values().iterator().next()).get();
    }

    /**
     * The alarm item for a type, or null (Module 3; also called from the pod RENDERER every frame).
     * Plain if-chain on purpose: an enum switch compiles to a synthetic ModItems$1 class, and a stale
     * incremental build once shipped a jar without it Ã¢ÂÂ crashing the render thread (see crash
     * 2026-07-03). An if-chain cannot lose its class.
     */
    public static Item alarmItem(AlarmType type) {
        if (type == AlarmType.BELL) return BELL_ALARM.get();
        if (type == AlarmType.DIGITAL) return DIGITAL_ALARM.get();
        return null;
    }

    private static void registerLines(LineType type, double[] diameters) {
        for (double d : diameters) {
            final double dia = d;
            String suffix = String.format("%03d", Math.round(d * 100)); // 0.14 -> "014"
            reg("line_" + type.jsonKey() + "_" + suffix, () -> new LineItem(type, dia, props("line_" + type.jsonKey() + "_" + suffix)));
        }
    }

    private static RegistrySupplier<Item> registerBait(String id, boolean artificial) {
        return reg(id, () -> new BaitItem(id, artificial, props(id)));
    }

    /** Bait with an explicit tooltip key override (e.g. the ice jig's own descriptor). */
    private static RegistrySupplier<Item> registerBait(String id, boolean artificial, String tooltipKey) {
        return reg(id, () -> new BaitItem(id, artificial, tooltipKey, props(id)));
    }
}
