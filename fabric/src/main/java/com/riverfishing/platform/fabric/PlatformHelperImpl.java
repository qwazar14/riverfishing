package com.riverfishing.platform.fabric;

import net.fabricmc.loader.api.FabricLoader;

/** Fabric implementation of {@link com.riverfishing.platform.PlatformHelper} (§multiloader). */
public final class PlatformHelperImpl {
    private PlatformHelperImpl() {}

    public static String platformName() {
        return "Fabric";
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /** §26.1: the vanilla BlockEntityType ctor is private — Fabric's builder is the blessed path. */
    public static <T extends net.minecraft.world.level.block.entity.BlockEntity>
    net.minecraft.world.level.block.entity.BlockEntityType<T> createBlockEntityType(
            java.util.function.BiFunction<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, T> factory,
            net.minecraft.world.level.block.Block... blocks) {
        return net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder
                .create(factory::apply, blocks).build();
    }
}
