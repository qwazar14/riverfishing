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
    public static final RegistrySupplier<Block> FISHING_STALL = registerSimple("fishing_stall",
            () -> new Block(blockProps("fishing_stall").strength(2.0f).sound(SoundType.WOOD).noOcclusion()));

    // Trophy stand (§15.5) — mounts a caught fish.
    public static final RegistrySupplier<Block> TROPHY_STAND = registerSimple("trophy_stand",
            () -> new TrophyStandBlock(blockProps("trophy_stand").strength(1.0f).sound(SoundType.WOOD).noOcclusion()));

    // Aquarium (§aquarium) — a 2×2 glass-and-wood display that mounts a caught fish with a nameplate.
    public static final RegistrySupplier<Block> AQUARIUM = registerSimple("aquarium",
            () -> new com.riverfishing.block.AquariumBlock(
                    blockProps("aquarium").strength(1.2f).sound(SoundType.GLASS).noOcclusion()));

    // Drilled ice hole (§ice-fishing) — the auger makes one; right-click it with a winter rod to fish.
    // Copies vanilla ICE properties wholesale so the physics match exactly (slip, melt, break-to-water).
    public static final RegistrySupplier<Block> ICE_HOLE = registerSimple("ice_hole",
            () -> new com.riverfishing.block.IceHoleBlock(
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ICE)
                            .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, RiverFishing.id("ice_hole")))));

    // §bait-crops: farmland crops for the plant baits (corn / pea / barley→pearl barley). No BlockItem —
    // their ITEM is the seed (an ItemNameBlockItem in ModItems), exactly like vanilla wheat.
    public static final RegistrySupplier<Block> CORN_CROP = BLOCKS.register("corn_crop",
            () -> new com.riverfishing.block.BaitCropBlock("corn_seeds",
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WHEAT)
                            .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, RiverFishing.id("corn_crop")))));
    public static final RegistrySupplier<Block> PEA_CROP = BLOCKS.register("pea_crop",
            () -> new com.riverfishing.block.BaitCropBlock("pea_seeds",
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WHEAT)
                            .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, RiverFishing.id("pea_crop")))));
    public static final RegistrySupplier<Block> BARLEY_CROP = BLOCKS.register("barley_crop",
            () -> new com.riverfishing.block.BaitCropBlock("barley_seeds",
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WHEAT)
                            .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, RiverFishing.id("barley_crop")))));

    private ModBlocks() {}

    // §26.1: every Block/Item Properties must carry its registry id (the ctors throw without it).
    static BlockBehaviour.Properties blockProps(String name) {
        return BlockBehaviour.Properties.of().setId(net.minecraft.resources.ResourceKey.create(
                Registries.BLOCK, RiverFishing.id(name)));
    }

    private static Item.Properties itemProps(String name) {
        return new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(
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
