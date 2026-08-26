package com.riverfishing.registry;

import com.riverfishing.RiverFishing;
import com.riverfishing.block.AquariumBlockEntity;
import com.riverfishing.block.BaitTrapBlockEntity;
import com.riverfishing.block.RodPodBlockEntity;
import com.riverfishing.block.TrophyStandBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(RiverFishing.MODID, Registries.BLOCK_ENTITY_TYPE);

    public static void init() {
        REGISTER.register();
    }

    // §tackle-station (0.6.0, round 5): the STALL keeps the bench's material slots while it stands.
    public static final RegistrySupplier<BlockEntityType<com.riverfishing.block.TackleStationBlockEntity>> TACKLE_STATION =
            // §26.x: the BlockEntityType ctor went private, so every type in this file goes through
            // the platform seam instead of Builder.of(...).build(null).
            REGISTER.register("tackle_station", () -> com.riverfishing.platform.PlatformHelper
                    .createBlockEntityType(com.riverfishing.block.TackleStationBlockEntity::new,
                            ModBlocks.FISHING_STALL.get()));

    public static final RegistrySupplier<BlockEntityType<RodPodBlockEntity>> ROD_POD =
            REGISTER.register("rod_pod", () -> com.riverfishing.platform.PlatformHelper.createBlockEntityType(RodPodBlockEntity::new, ModBlocks.ROD_POD_1.get(), ModBlocks.ROD_POD_3.get()));

    public static final RegistrySupplier<BlockEntityType<BaitTrapBlockEntity>> BAIT_TRAP =
            REGISTER.register("bait_trap", () -> com.riverfishing.platform.PlatformHelper.createBlockEntityType(BaitTrapBlockEntity::new, ModBlocks.BAIT_TRAP.get()));

    public static final RegistrySupplier<BlockEntityType<com.riverfishing.block.WormFarmBlockEntity>> WORM_FARM =
            REGISTER.register("worm_farm", () -> com.riverfishing.platform.PlatformHelper.createBlockEntityType(com.riverfishing.block.WormFarmBlockEntity::new, ModBlocks.WORM_FARM.get()));

    public static final RegistrySupplier<BlockEntityType<com.riverfishing.block.MaggotFarmBlockEntity>> MAGGOT_FARM =
            REGISTER.register("maggot_farm", () -> com.riverfishing.platform.PlatformHelper.createBlockEntityType(com.riverfishing.block.MaggotFarmBlockEntity::new, ModBlocks.MAGGOT_FARM.get()));

    public static final RegistrySupplier<BlockEntityType<TrophyStandBlockEntity>> TROPHY_STAND =
            REGISTER.register("trophy_stand", () -> com.riverfishing.platform.PlatformHelper.createBlockEntityType(TrophyStandBlockEntity::new, ModBlocks.TROPHY_STAND.get()));

    public static final RegistrySupplier<BlockEntityType<AquariumBlockEntity>> AQUARIUM =
            REGISTER.register("aquarium", () -> com.riverfishing.platform.PlatformHelper.createBlockEntityType(AquariumBlockEntity::new, ModBlocks.AQUARIUM.get()));

    // §tackle-box (0.7.0): holds the box ITEM, so the placed box and the carried box are one object.
    public static final RegistrySupplier<BlockEntityType<com.riverfishing.block.TackleBoxBlockEntity>> TACKLE_BOX =
            // §26.x: same platform seam as the types above — Builder.of(...).build(null) is gone.
            REGISTER.register("tackle_box", () -> com.riverfishing.platform.PlatformHelper.createBlockEntityType(
                    com.riverfishing.block.TackleBoxBlockEntity::new,
                    ModBlocks.TACKLE_BOXES.values().stream()
                            .map(RegistrySupplier::get).toArray(net.minecraft.world.level.block.Block[]::new)));

    private ModBlockEntities() {}
}
