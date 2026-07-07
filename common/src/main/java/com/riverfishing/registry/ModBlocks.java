package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import com.riverfishing.block.RodPodBlock;
import com.riverfishing.block.TrophyStandBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Blocks: rod-pods (Module 2). Their BlockItems are registered into {@link ModItems} for the tab. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, RiverFishing.MODID);

    public static final List<RegistryObject<Block>> POD_BLOCKS = new ArrayList<>();

    public static final RegistryObject<Block> ROD_POD_1 = registerPod("rod_pod_1", 1);
    public static final RegistryObject<Block> ROD_POD_3 = registerPod("rod_pod_3", 3);

    // Bait trap (§livebait): stands in water and slowly gathers live bait.
    public static final RegistryObject<Block> BAIT_TRAP = registerSimple("bait_trap",
            () -> new com.riverfishing.block.BaitTrapBlock(
                    BlockBehaviour.Properties.of().strength(0.6f).sound(SoundType.SCAFFOLDING).noOcclusion()));

    // Worm farm (§bait-farm): a composter-style crate on soil — feed organics, the worms eat through it.
    public static final RegistryObject<Block> WORM_FARM = registerSimple("worm_farm",
            () -> new com.riverfishing.block.WormFarmBlock(
                    BlockBehaviour.Properties.of().strength(0.6f).sound(SoundType.WOOD).noOcclusion()));

    // Maggot farm (§bait-farm): load rotten flesh, each piece breeds into 4 maggots over time.
    public static final RegistryObject<Block> MAGGOT_FARM = registerSimple("maggot_farm",
            () -> new com.riverfishing.block.MaggotFarmBlock(
                    BlockBehaviour.Properties.of().strength(0.6f).sound(SoundType.WOOD).noOcclusion()));

    // Fisherman's workstation / POI job-site block (§8). noOcclusion: the model is a stall, not a cube.
    public static final RegistryObject<Block> FISHING_STALL = registerSimple("fishing_stall",
            () -> new Block(BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.WOOD).noOcclusion()));

    // Trophy stand (§15.5) — mounts a caught fish.
    public static final RegistryObject<Block> TROPHY_STAND = registerSimple("trophy_stand",
            () -> new TrophyStandBlock(BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.WOOD).noOcclusion()));

    // Aquarium (§aquarium) — a 2×2 glass-and-wood display that mounts a caught fish with a nameplate.
    public static final RegistryObject<Block> AQUARIUM = registerSimple("aquarium",
            () -> new com.riverfishing.block.AquariumBlock(
                    BlockBehaviour.Properties.of().strength(1.2f).sound(SoundType.GLASS).noOcclusion()));

    // Drilled ice hole (§ice-fishing) — the auger makes one; right-click it with a winter rod to fish.
    // Copies vanilla ICE properties wholesale so the physics match exactly (slip, melt, break-to-water).
    public static final RegistryObject<Block> ICE_HOLE = registerSimple("ice_hole",
            () -> new com.riverfishing.block.IceHoleBlock(
                    BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.ICE)));

    private ModBlocks() {}

    private static RegistryObject<Block> registerSimple(String name, Supplier<Block> supplier) {
        RegistryObject<Block> block = BLOCKS.register(name, supplier);
        RegistryObject<Item> item = ModItems.REGISTER.register(name,
                () -> new BlockItem(block.get(), new Item.Properties()));
        ModItems.ALL.add(item);
        return block;
    }

    private static RegistryObject<Block> registerPod(String name, int slots) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> new RodPodBlock(slots,
                BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD).noOcclusion()));
        POD_BLOCKS.add(block);
        RegistryObject<Item> item = ModItems.REGISTER.register(name,
                () -> new BlockItem(block.get(), new Item.Properties()));
        ModItems.ALL.add(item);
        return block;
    }
}
