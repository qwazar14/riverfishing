package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import com.riverfishing.block.AquariumBlockEntity;
import com.riverfishing.block.BaitTrapBlockEntity;
import com.riverfishing.block.RodPodBlockEntity;
import com.riverfishing.block.TrophyStandBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, RiverFishing.MODID);

    public static final RegistryObject<BlockEntityType<RodPodBlockEntity>> ROD_POD =
            REGISTER.register("rod_pod", () -> BlockEntityType.Builder.of(
                    RodPodBlockEntity::new,
                    ModBlocks.ROD_POD_1.get(), ModBlocks.ROD_POD_3.get()
            ).build(null));

    public static final RegistryObject<BlockEntityType<BaitTrapBlockEntity>> BAIT_TRAP =
            REGISTER.register("bait_trap", () -> BlockEntityType.Builder.of(
                    BaitTrapBlockEntity::new, ModBlocks.BAIT_TRAP.get()
            ).build(null));

    public static final RegistryObject<BlockEntityType<com.riverfishing.block.WormFarmBlockEntity>> WORM_FARM =
            REGISTER.register("worm_farm", () -> BlockEntityType.Builder.of(
                    com.riverfishing.block.WormFarmBlockEntity::new, ModBlocks.WORM_FARM.get()
            ).build(null));

    public static final RegistryObject<BlockEntityType<com.riverfishing.block.MaggotFarmBlockEntity>> MAGGOT_FARM =
            REGISTER.register("maggot_farm", () -> BlockEntityType.Builder.of(
                    com.riverfishing.block.MaggotFarmBlockEntity::new, ModBlocks.MAGGOT_FARM.get()
            ).build(null));

    public static final RegistryObject<BlockEntityType<TrophyStandBlockEntity>> TROPHY_STAND =
            REGISTER.register("trophy_stand", () -> BlockEntityType.Builder.of(
                    TrophyStandBlockEntity::new, ModBlocks.TROPHY_STAND.get()
            ).build(null));

    public static final RegistryObject<BlockEntityType<AquariumBlockEntity>> AQUARIUM =
            REGISTER.register("aquarium", () -> BlockEntityType.Builder.of(
                    AquariumBlockEntity::new, ModBlocks.AQUARIUM.get()
            ).build(null));

    private ModBlockEntities() {}
}
