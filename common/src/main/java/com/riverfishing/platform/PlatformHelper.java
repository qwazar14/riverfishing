package com.riverfishing.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

/**
 * The platform-abstraction seam (§multiloader). Methods here are declared in {@code common} and
 * implemented per loader: Architectury's {@code @ExpectPlatform} rewrites each call at build time to
 * the matching {@code com.riverfishing.platform.<forge|fabric>.PlatformHelperImpl} static method.
 *
 * <p>This is the pattern every later stage uses for the Forge-only surfaces (registration lives in
 * Architectury's unified DeferredRegister, but things like "is a mod loaded" / config dir go here).
 */
public final class PlatformHelper {
    private PlatformHelper() {}

    /** "Forge" or "Fabric" — proves the @ExpectPlatform redirect resolves on each loader. */
    @ExpectPlatform
    public static String platformName() {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    /** Whether another mod is present (used later to gate Serene Seasons / Biomes O' Plenty features). */
    @ExpectPlatform
    public static boolean isModLoaded(String modId) {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }

    /**
     * §26.1: the vanilla {@code BlockEntityType} constructor and Builder went private — each loader
     * exposes its own factory (Fabric {@code FabricBlockEntityTypeBuilder} / NeoForge's widened ctor).
     */
    @ExpectPlatform
    public static <T extends net.minecraft.world.level.block.entity.BlockEntity>
    net.minecraft.world.level.block.entity.BlockEntityType<T> createBlockEntityType(
            java.util.function.BiFunction<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, T> factory,
            net.minecraft.world.level.block.Block... blocks) {
        throw new AssertionError("@ExpectPlatform stub — replaced per platform at build time");
    }
}
