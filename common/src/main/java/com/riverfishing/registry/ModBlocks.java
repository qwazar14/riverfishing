package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import com.riverfishing.block.RodPodBlock;
import com.riverfishing.block.TrophyStandBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Blocks: rod-pods (Module 2). Their BlockItems are registered into {@link ModItems} for the tab. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(RiverFishing.MODID, Registries.BLOCK);

    /** Bind all queued blocks to the active platform's registry (§multiloader) — called from init. */
    public static void init() {
        BLOCKS.register();
    }

    public static final List<RegistrySupplier<Block>> POD_BLOCKS = new ArrayList<>();

    public static final RegistrySupplier<Block> ROD_POD_1 = registerPod("rod_pod_1", 1);
    public static final RegistrySupplier<Block> ROD_POD_3 = registerPod("rod_pod_3", 3);

    // Bait trap (§livebait): stands in water and slowly gathers live bait.
    public static final RegistrySupplier<Block> BAIT_TRAP = registerSimple("bait_trap",
            () -> new com.riverfishing.block.BaitTrapBlock(
                    blockProps("bait_trap").strength(0.6f).sound(SoundType.SCAFFOLDING).noOcclusion()));

    // Worm farm (§bait-farm): a composter-style crate on soil — feed organics, the worms eat through it.
    public static final RegistrySupplier<Block> WORM_FARM = registerSimple("worm_farm",
            () -> new com.riverfishing.block.WormFarmBlock(
                    blockProps("worm_farm").strength(0.6f).sound(SoundType.WOOD).noOcclusion()));

    // Maggot farm (§bait-farm): load rotten flesh, each piece breeds into 4 maggots over time.
    public static final RegistrySupplier<Block> MAGGOT_FARM = registerSimple("maggot_farm",
            () -> new com.riverfishing.block.MaggotFarmBlock(
                    blockProps("maggot_farm").strength(0.6f).sound(SoundType.WOOD).noOcclusion()));

    // Fisherman's workstation / POI job-site block (§8). noOcclusion: the model is a stall, not a cube.
    // §tackle-station (round 5): the stall is the tackle bench too — profession POI + tying UI.
    public static final RegistrySupplier<Block> FISHING_STALL = registerSimple("fishing_stall",
            // §26.x: Properties must carry a registry id, so the stall keeps the blockProps() helper.
            () -> new com.riverfishing.block.FishingStallBlock(
                    blockProps("fishing_stall").strength(2.0f).sound(SoundType.WOOD).noOcclusion()));

    // §g §breeding (0.9.0): water-body upgrades — marks on the water that the ecosystem reads
    // (fishing/WaterUpgrades). One class, five kinds; the kind string is what Ecosystem asks for.
    // The bed ones may stand IN the water (waterloggable); the bank ones may not.
    public static final RegistrySupplier<Block> AERATOR = registerSimple("aerator",
            () -> new com.riverfishing.block.WaterUpgradeBlock("aerator", "aerator", true,
                    BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.METAL)));
    public static final RegistrySupplier<Block> SNAG_PILE = registerSimple("snag_pile",
            () -> new com.riverfishing.block.WaterUpgradeBlock("snag_pile", "snags", true,
                    BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.WOOD)));
    public static final RegistrySupplier<Block> GRAVEL_BED = registerSimple("gravel_bed",
            () -> new com.riverfishing.block.WaterUpgradeBlock("gravel_bed", "gravel", true,
                    BlockBehaviour.Properties.of().strength(0.6f).sound(SoundType.GRAVEL)));
    public static final RegistrySupplier<Block> WARM_OUTFLOW = registerSimple("warm_outflow",
            () -> new com.riverfishing.block.WaterUpgradeBlock("warm_outflow", "warm_outflow", false,
                    BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.COPPER)));
    public static final RegistrySupplier<Block> FEEDING_STATION = registerSimple("feeding_station",
            () -> new com.riverfishing.block.WaterUpgradeBlock("feeding_station", "feeding_station", false,
                    BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.WOOD)));

    // Trophy stand (§15.5) — mounts a caught fish.
    public static final RegistrySupplier<Block> TROPHY_STAND = registerSimple("trophy_stand",
            () -> new TrophyStandBlock(blockProps("trophy_stand").strength(1.0f).sound(SoundType.WOOD).noOcclusion()));

    // Aquarium (§aquarium) — a 2×2 glass-and-wood display that mounts a caught fish with a nameplate.
    public static final RegistrySupplier<Block> AQUARIUM = registerSimple("aquarium",
            () -> new com.riverfishing.block.AquariumBlock(
                    blockProps("aquarium").strength(1.2f).sound(SoundType.GLASS).noOcclusion()));

    // Drilled ice hole (§ice-fishing) — the auger makes one; right-click it with a winter rod to fish.
    // §26.1: no ofFullCopy (copied Properties can carry the source block's state lambdas — see the crop
    // note below) — vanilla ICE's physics rebuilt explicitly: slip, melt-by-light, break-to-water.
    public static final RegistrySupplier<Block> ICE_HOLE = registerSimple("ice_hole",
            () -> new com.riverfishing.block.IceHoleBlock(
                    blockProps("ice_hole")
                            .mapColor(net.minecraft.world.level.material.MapColor.ICE)
                            .friction(0.98f)
                            .randomTicks()
                            .strength(0.5f)
                            .sound(SoundType.GLASS)
                            .noOcclusion()));

    // §bait-crops: farmland crops for the plant baits (corn / pea / barley→pearl barley). No BlockItem —
    // their ITEM is the seed (a BlockItem in ModItems), exactly like vanilla wheat.
    // §26.1: NO ofFullCopy(WHEAT)! Wheat's Properties now carry a state lambda that reads wheat's
    // AGE_7 — eagerly evaluated on OUR states (AGE_3) it crashes at registration ("Cannot get property
    // age..."). Build the standard crop property set from scratch instead.
    private static BlockBehaviour.Properties cropProps(String name) {
        return blockProps(name)
                .mapColor(net.minecraft.world.level.material.MapColor.PLANT)
                .noCollision()
                .randomTicks()
                .instabreak()
                .sound(SoundType.CROP)
                .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY);
    }

    public static final RegistrySupplier<Block> CORN_CROP = BLOCKS.register("corn_crop",
            () -> new com.riverfishing.block.BaitCropBlock("corn_seeds", cropProps("corn_crop")));
    public static final RegistrySupplier<Block> PEA_CROP = BLOCKS.register("pea_crop",
            () -> new com.riverfishing.block.BaitCropBlock("pea_seeds", cropProps("pea_crop")));
    public static final RegistrySupplier<Block> BARLEY_CROP = BLOCKS.register("barley_crop",
            () -> new com.riverfishing.block.BaitCropBlock("barley_seeds", cropProps("barley_crop")));

    /**
     * §tackle-box (0.7.0): four sizes of set-down tackle box. Their ITEM is a {@link
     * com.riverfishing.item.TackleBoxItem} (registered in ModItems) rather than a plain BlockItem, because
     * the same object has to open in the hand as well as stand on the bank.
     */
    public static final java.util.Map<com.riverfishing.item.TackleBoxTier, RegistrySupplier<Block>>
            TACKLE_BOXES = new java.util.EnumMap<>(com.riverfishing.item.TackleBoxTier.class);

    static {
        for (com.riverfishing.item.TackleBoxTier t : com.riverfishing.item.TackleBoxTier.values()) {
            // §26.x: Properties MUST carry their registry id — BlockBehaviour's constructor calls
            // effectiveDrops(), which dereferences it, so a bare Properties.of() throws "Block id not
            // set" the moment the block is built. That is what blockProps() is for; the tackle boxes
            // were the one place that missed it.
            TACKLE_BOXES.put(t, BLOCKS.register(t.id(),
                    () -> new com.riverfishing.block.TackleBoxBlock(t,
                            blockProps(t.id()).strength(1.0f).sound(SoundType.WOOD).noOcclusion())));
        }
    }

    private ModBlocks() {}

    // §26.1: every Block/Item Properties must carry its registry id (the ctors throw without it).
    static BlockBehaviour.Properties blockProps(String name) {
        return BlockBehaviour.Properties.of().setId(net.minecraft.resources.ResourceKey.create(
                Registries.BLOCK, RiverFishing.id(name)));
    }

    private static Item.Properties itemProps(String name) {
        // §26.1: BlockItems no longer inherit the block translation key — request the block.<ns>.<path>
        // description prefix explicitly (otherwise the item shows a raw item.riverfishing.* key).
        return new Item.Properties().useBlockDescriptionPrefix().setId(net.minecraft.resources.ResourceKey.create(
                Registries.ITEM, RiverFishing.id(name)));
    }

    private static RegistrySupplier<Block> registerSimple(String name, Supplier<Block> supplier) {
        RegistrySupplier<Block> block = BLOCKS.register(name, supplier);
        RegistrySupplier<Item> item = ModItems.REGISTER.register(name,
                () -> new BlockItem(block.get(), itemProps(name)));
        ModItems.ALL.add(item);
        return block;
    }

    private static RegistrySupplier<Block> registerPod(String name, int slots) {
        RegistrySupplier<Block> block = BLOCKS.register(name, () -> new RodPodBlock(slots,
                blockProps(name).strength(1.5f).sound(SoundType.WOOD).noOcclusion()));
        POD_BLOCKS.add(block);
        RegistrySupplier<Item> item = ModItems.REGISTER.register(name,
                () -> new BlockItem(block.get(), itemProps(name)));
        ModItems.ALL.add(item);
        return block;
    }
}
