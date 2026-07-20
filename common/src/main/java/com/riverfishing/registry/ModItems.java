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
import net.minecraft.resources.ResourceLocation;
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

    // ---- Rods ----
    public static final List<RegistrySupplier<Item>> RODS = new ArrayList<>();
    // ---- Caught fish: one item + texture per species (Module 8; ÃÂ§ecology adds habitat-bound species) ----
    public static final String[] FISH_SPECIES = {
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
            "lenok", "taimen", "salmon", "pink_salmon", "sturgeon", "halibut"
    };
    public static final Map<ResourceLocation, RegistrySupplier<Item>> FISH_ITEMS = new HashMap<>();
    // ---- Baits referenced by event drops ----
    public static final RegistrySupplier<Item> WORM;
    public static final RegistrySupplier<Item> CHICKEN_LIVER;
    // ---- In-rig components (Module 4): referenced by slot validation ----
    public static final RegistrySupplier<Item> LEADER;
    public static final RegistrySupplier<Item> LEADER_FLUORO;
    public static final RegistrySupplier<Item> LEADER_TITANIUM;
    public static final RegistrySupplier<Item> FLOAT;
    // ---- Bite alarms (Module 3) ----
    public static final RegistrySupplier<Item> BELL_ALARM;
    public static final RegistrySupplier<Item> DIGITAL_ALARM;
    // ---- Processing (ÃÂ§11) ----
    public static final RegistrySupplier<Item> FILLET_KNIFE;
    public static final RegistrySupplier<Item> RAW_FILLET;
    public static final RegistrySupplier<Item> COOKED_FILLET;
    // ---- Maintenance (ÃÂ§3.8) ----
    public static final RegistrySupplier<Item> WHETSTONE;

    private ModItems() {}

    private static RegistrySupplier<Item> reg(String name, Supplier<Item> supplier) {
        RegistrySupplier<Item> obj = REGISTER.register(name, supplier);
        ALL.add(obj);
        return obj;
    }

    private static Item.Properties props() {
        return new Item.Properties();
    }

    /** Rod blank durability by tier (ÃÂ§rod-durability). Plain if-chain: no synthetic switch classes. */
    private static int rodDurability(RodType type) {
        String key = type.jsonKey();
        if ("stick".equals(key)) return 32;
        if ("bamboo".equals(key)) return 64;
        if ("pole".equals(key)) return 128;
        if ("ultralight".equals(key)) return 144;
        if ("spinning".equals(key)) return 192;
        if ("feeder".equals(key)) return 224;
        if ("bottom".equals(key)) return 256;
        if ("carp".equals(key)) return 320;
        return 128;
    }

    static {
        // ----- Rods (each RodType is its own item; components live in NBT). Blanks wear out and are
        // anvil-repaired with the priciest ingredient of their recipe (ÃÂ§rod-durability). -----
        for (RodType type : RodType.values()) {
            RegistrySupplier<Item> rod = reg(type.jsonKey() + "_rod",
                    () -> new RodItem(type, props().durability(rodDurability(type))));
            RODS.add(rod);
        }

        // ----- Reels -----
        for (int size : new int[]{1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 10000, 12000, 14000}) {
            final int s = size;
            reg("reel_" + size, () -> new ReelItem(s, props()));
        }

        // ----- Lines (ÃÂ§line-update): mono = all-rounder, braid = thin & strong, fluoro = clear/finesse.
        // Thick fluoro (0.40/0.50) dropped Ã¢ÂÂ impractical in reality; thin mono/fluoro + heavy braid added. -----
        registerLines(LineType.MONO, new double[]{0.10, 0.14, 0.18, 0.25, 0.30, 0.40, 0.50, 0.60});
        // Braid tops out at 0.30 Ã¢ÂÂ the catfish line (ÃÂ§strain-recompute: 0.30 braid Ã¢ÂÂ 27 kg, enough to
        // duel the 40 kg monster catfish with a 7000 reel's drag on top).
        registerLines(LineType.BRAID, new double[]{0.16, 0.20, 0.25, 0.30, 0.40, 0.50});
        registerLines(LineType.FLUORO, new double[]{0.14, 0.16, 0.20, 0.25, 0.30, 0.40});

        // ----- Rigs -----
        for (RigType type : RigType.values()) {
            reg("rig_" + type.jsonKey(), () -> new RigItem(type, props()));
        }

        // ----- In-rig components (Module 4) -----
        LEADER = reg("leader", () -> new LeaderItem(1.0, 0.1, props()));            // steel: bomb-proof, but very visible (spooks bites)
        LEADER_FLUORO = reg("leader_fluoro", () -> new LeaderItem(0.85, 0.9, props())); // fluorocarbon: near-invisible, strong (rarely bitten through)
        LEADER_TITANIUM = reg("leader_titanium", () -> new LeaderItem(1.0, 0.6, props())); // titanium: bomb-proof AND fairly stealthy (trade-only)
        FLOAT = reg("float", () -> new Item(props()));

        // ----- Hooks (angling sizes; bigger number = smaller hook) -----
        for (int size : new int[]{16, 14, 12, 10, 8, 6, 4, 2, 1}) {
            final int s = size;
            reg("hook_" + size, () -> new HookItem(s, props()));
        }

        // ----- Natural baits -----
        // §sea-tackle (0.5.0): cut fish strip — the universal saltwater hook bait.
        registerBait("fish_strip", false);
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
        reg("corn_seeds", () -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.CORN_CROP.get(), props()));
        reg("pea_seeds", () -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.PEA_CROP.get(), props()));
        reg("barley_seeds", () -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.BARLEY_CROP.get(), props()));

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

        // ----- Groundbaits -----
        for (String cat : new String[]{"powder", "grain", "pellet", "cake"}) {
            reg("groundbait_" + cat, () -> new GroundbaitItem(cat, props()));
        }

        // ----- Bite alarms (Module 3) -----
        BELL_ALARM = reg("bell_alarm", () -> new AlarmItem(AlarmType.BELL, props()));
        DIGITAL_ALARM = reg("digital_alarm", () -> new AlarmItem(AlarmType.DIGITAL, props()));

        // ----- Processing: knife + fillets (ÃÂ§11) -----
        FILLET_KNIFE = reg("fillet_knife", () -> new FilletKnifeItem(new Item.Properties().durability(128)));
        RAW_FILLET = reg("raw_fillet", () -> new Item(props().food(
                new FoodProperties.Builder().nutrition(2).saturationModifier(0.2f).build())));
        COOKED_FILLET = reg("cooked_fillet", () -> new Item(props().food(
                new FoodProperties.Builder().nutrition(5).saturationModifier(0.6f).build())));

        // ----- Maintenance: whetstone (ÃÂ§3.8) -----
        WHETSTONE = reg("whetstone", () -> new WhetstoneItem(new Item.Properties().durability(128)));

        // ----- Ice fishing (ÃÂ§ice-fishing): the auger drills a hole through an ice sheet -----
        reg("ice_auger", () -> new com.riverfishing.item.IceAugerItem(new Item.Properties().durability(64)));

        // ----- Records: fishing journal (ÃÂ§15) -----
        reg("fishing_journal", () -> new JournalItem(props().stacksTo(1)));

        // ----- Water analysis (ÃÂ§QoL): player fish finder + admin probe -----
        reg("fish_finder", () -> new com.riverfishing.item.WaterProbeItem(false, props().stacksTo(1)));
        reg("hydro_probe", () -> new com.riverfishing.item.WaterProbeItem(true, props().stacksTo(1)));

        // ----- Caught fish: a distinct item + texture per species (Module 8) -----
        for (String sp : FISH_SPECIES) {
            ResourceLocation id = RiverFishing.id(sp);
            FISH_ITEMS.put(id, reg(sp, () -> new FishItem(id, props().stacksTo(1))));
        }
    }

    /** The item representing a given fish species (Module 8). */
    public static Item fishItem(ResourceLocation species) {
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
            reg("line_" + type.jsonKey() + "_" + suffix, () -> new LineItem(type, dia, props()));
        }
    }

    private static RegistrySupplier<Item> registerBait(String id, boolean artificial) {
        return reg(id, () -> new BaitItem(id, artificial, props()));
    }

    /** Bait with an explicit tooltip key override (e.g. the ice jig's own descriptor). */
    private static RegistrySupplier<Item> registerBait(String id, boolean artificial, String tooltipKey) {
        return reg(id, () -> new BaitItem(id, artificial, tooltipKey, props()));
    }
}
